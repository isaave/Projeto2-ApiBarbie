# Barbie Dreamworld API — Assistente de Estilo com IA Generativa

API REST para gerenciar o universo **Barbie Dreamworld** (Barbies, Profissões,
Dreamhouses, Acessórios e Eventos), estendida com o **Assistente de Estilo**:
um módulo de **Geração e Produtividade com IA generativa (Google Gemini)** que
cria looks completos a partir dos dados reais cadastrados, devolve dicas de
refinamento, permite copiar/exportar o resultado e gera uma **foto realista** da
Barbie com aquele look (com fallback para ilustração vetorial).

**Categoria do trabalho (conforme o PDF):** Geração e Produtividade — ferramenta
web com formulários parametrizados para criação e cópia de peças de texto, com
deploy real e IA generativa.

---

## Sumário

- [Arquitetura](#arquitetura)
- [URLs públicas](#urls-públicas)
- [Rodando localmente](#rodando-localmente)
- [Variáveis de ambiente](#variáveis-de-ambiente)
- [Autenticação](#autenticação)
- [Rotas principais](#rotas-principais)
- [Assistente de Estilo](#assistente-de-estilo)
- [Engenharia de prompt e segurança da IA](#engenharia-de-prompt-e-segurança-da-ia)
- [Resiliência e tratamento de erros](#resiliência-e-tratamento-de-erros)
- [Testes](#testes)
- [Deploy](#deploy)
- [Limitações conhecidas](#limitações-conhecidas)

---

## Arquitetura

```
┌──────────────────────────┐        HTTPS / JSON        ┌──────────────────────────────┐
│  Frontend (GitHub Pages) │ ──────────────────────────▶ │  API Spring Boot 4 (Render)  │
│  index.html (single file)│ ◀────────────────────────── │  Docker multi-stage, Java 25 │
└──────────────────────────┘        X-API-Key            └──────────────┬───────────────┘
                                                                       │
                        ┌──────────────────────────────────────────────┼────────────────────┐
                        ▼                                              ▼                    ▼
              ┌──────────────────┐                        ┌────────────────────┐  ┌──────────────────┐
              │ H2 (em memória)  │                        │ Gemini (texto)     │  │ Gemini (imagem)  │
              │ dados de exemplo │                        │ generateContent    │  │ interactions API │
              └──────────────────┘                        └─────────┬──────────┘  └─────────┬────────┘
                                                                    │  429 / sem cota       │
                                                                    ▼                       ▼
                                                         ┌────────────────────────────────────┐
                                                         │ LookLocalClient (texto local)     │
                                                         │ Pollinations/FLUX (foto realista) │
                                                         │ IlustracaoLookClient (SVG local)  │
                                                         └────────────────────────────────────┘
```

Camadas relevantes do código:

| Pacote | Responsabilidade |
|---|---|
| `controllers` | CRUD das 5 entidades + rotas de autenticação |
| `apikey`, `ratelimit` | Segurança: chaves com hash SHA-256, limites por IP |
| `ia.controller`, `ia.service` | Orquestração do Assistente de Estilo |
| `ia.client` | `GeminiClient` (texto), `GeminiImageClient` (foto), `PollinationsImageClient` (foto FLUX), `LookLocalClient` (texto local), `IlustracaoLookClient` (SVG) |
| `ia.dto` | Contratos de entrada/saída com schemas e exemplos no Swagger |
| `infrastructure` | CORS, OpenAPI e carga inicial do banco |
| `exceptions` | Envelope de erro único + escrita JSON nos filtros |

---

## URLs públicas

| Recurso | URL |
|---|---|
| API (base) | `https://projeto2-apibarbie.onrender.com` |
| Swagger UI | `https://projeto2-apibarbie.onrender.com/swagger-ui/index.html` |
| OpenAPI JSON | `https://projeto2-apibarbie.onrender.com/api-docs` |
| Healthcheck | `https://projeto2-apibarbie.onrender.com/actuator/health` |
| Frontend | `https://isaave.github.io/Front_apibarbie/` |
| Repositório da API | `https://github.com/isaave/Projeto2-ApiBarbie` |
| Repositório do frontend | `https://github.com/isaave/Front_apibarbie` |

---

## Rodando localmente

### Pré-requisitos

- **JDK 25** (o Java 27 padrão de algumas máquinas quebra o Lombok/compilador).
- Maven instalado (ou use `./mvnw`).
- Uma chave gratuita do [Google AI Studio](https://aistudio.google.com/apikey).

### Passo a passo

```bash
# 1. Entre na pasta da API
cd "/Users/isabellaavelina/Downloads/ApiDreamWord---with-a-personal-outfit-assistant-main"

# 2. Use o JDK 25 (ajuste o caminho se o seu for diferente)
export JAVA_HOME=/Users/isabellaavelina/Library/Java/JavaVirtualMachines/ms-25.0.3/Contents/Home

# 3. Configure a chave da IA (nunca commite este valor)
export GEMINI_API_KEY="sua-chave-aqui"

# 4. (Opcional) copie o modelo de variáveis e ajuste o que precisar
cp .env.example .env        # o .env já está no .gitignore

# 5. Compile, rode os testes e gere o jar
mvn clean package

# 6. Suba a aplicação
mvn spring-boot:run
# Alternativa: java -jar target/dreamworld-api-0.0.1-SNAPSHOT.jar
```

A API sobe em `http://localhost:8080`.

- Swagger: `http://localhost:8080/swagger-ui/index.html`
- Health: `http://localhost:8080/actuator/health`

### Teste rápido de ponta a ponta

```bash
# 1) Gerar uma chave WRITE (rota pública)
curl -s -X POST http://localhost:8080/api/auth/api-keys \
  -H 'Content-Type: application/json' \
  -d '{"owner":"Teste Local","accessLevel":"WRITE"}'
# Copie o campo "apiKey" da resposta — ele só aparece aqui.

# 2) Consultar dados de exemplo (GET é público)
curl -s http://localhost:8080/api/barbies

# 3) Gerar um look com IA (substitua a chave)
curl -s -X POST http://localhost:8080/api/ia/assistente-estilo \
  -H 'Content-Type: application/json' \
  -H 'X-API-Key: <CHAVE_AQUI>' \
  -d '{"barbieId":1,"ocasiao":"festa no jardim","preferencias":"tons pastel"}'
```

O campo `imagem.dataUrl` tenta, nesta ordem:

1. **Gemini Nano Banana** — foto realista, quando a chave tem cota de imagem;
2. **Pollinations/FLUX** — foto realista sem chave, usada quando o Gemini
   devolve `429` (comportamento normal no tier gratuito);
3. **Ilustração vetorial** (`data:image/svg+xml;base64,...`) — último recurso,
   feita com as cores e peças do próprio look e sem depender de rede.

Quando um fallback é usado, `avisoImagem` explica qual caminho gerou a imagem.
Basta colar o `dataUrl` na barra do navegador para vê-la.

---

## Variáveis de ambiente

Todas as variáveis abaixo estão listadas em `.env.example`. Valores padrão
permitem subir a aplicação sem configurar nada além de `GEMINI_API_KEY`.

| Variável | Obrigatória | Padrão | Descrição |
|---|:---:|---|---|
| `GEMINI_API_KEY` | Sim (IA) | *(vazio)* | Chave do Google AI Studio. Sem ela, os endpoints de IA respondem `503` com mensagem clara; o CRUD continua funcionando. |
| `GEMINI_MODEL` | Não | `gemini-3.6-flash` | Modelo de texto. Confira a lista atual em [ai.google.dev](https://ai.google.dev/gemini-api/docs/models) — o Google descontinua modelos com pouco aviso. |
| `GEMINI_IMAGE_MODEL` | Não | `gemini-nano-banana-2.1` | Modelo de imagem usado na `Interactions API`. |
| `GEMINI_TIMEOUT_MS` | Não | `30000` | Timeout da chamada de texto, em ms. |
| `GEMINI_IMAGE_TIMEOUT_MS` | Não | `90000` | Timeout da chamada de imagem, em ms. |
| `IMAGE_POLLINATIONS_ENABLED` | Não | `true` | Usa uma **foto realista** (FLUX) quando o Gemini não tem cota de imagem. `false` volta direto para o SVG local. |
| `IMAGE_POLLINATIONS_BASE_URL` | Não | `https://image.pollinations.ai` | Endpoint do gerador de fotos alternativo. |
| `IMAGE_POLLINATIONS_TIMEOUT_MS` | Não | `90000` | Timeout da foto alternativa, em ms. |
| `IMAGE_POLLINATIONS_MODEL` | Não | `flux` | Modelo usado pelo gerador alternativo. |
| `CORS_ALLOWED_ORIGINS` | Não | `https://isaave.github.io` | Origens permitidas, separadas por vírgula. **Nunca use `*`**: a API usa `allowCredentials(true)`. |
| `OPENAPI_SERVER_URL` | Não | `https://projeto2-apibarbie.onrender.com` | URL exibida no botão “Execute” do Swagger. |
| `PORT` | Não | `8080` | Porta HTTP. O Render injeta `10000` automaticamente. |
| `APP_TRUST_PROXY_HEADERS` | Não | `false` | Só `true` atrás de proxy reverso confiável (Render). Controla se `X-Forwarded-For` é usado no rate limit. |
| `RATE_LIMIT_READ_CAPACITY` | Não | `10` | Requisições GET por IP na janela. |
| `RATE_LIMIT_WRITE_CAPACITY` | Não | `5` | Requisições de escrita por IP na janela. |
| `RATE_LIMIT_WINDOW_SECONDS` | Não | `60` | Duração da janela do rate limit, em segundos. |
| `H2_CONSOLE_ENABLED` | Não | `false` | Habilita `/h2-console` apenas para diagnóstico local. |
| `JPA_SHOW_SQL` | Não | `false` | Loga SQL — útil ao depurar, ruim em produção. |
| `JAVA_TOOL_OPTIONS` | Não | *(vazio)* | Ajustes de JVM usados pelo Docker no Render (`-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0`). |

> **Nunca commite chaves.** O `.gitignore` cobre `.env`, `._*`, `__MACOSX/` e
> `*.log`. O GitHub Push Protection bloqueia segredos acidentais.

---

## Autenticação

- `GET` é público.
- `POST` / `PUT` / `PATCH` / `DELETE` exigem o header `X-API-Key`.
- Gere uma chave em `POST /api/auth/api-keys` (rota pública):

```json
{ "owner": "Frontend Barbie Dreamworld", "accessLevel": "WRITE" }
```

- `ADMIN` **não** pode ser criado pela rota pública (resposta `422`).
- A chave completa só aparece na criação. `GET /api/auth/api-keys` devolve
  máscara/`apiKey: null`. Internamente só o hash SHA-256 é persistido.
- Revogação (`DELETE /api/auth/api-keys/{id}`) exige `ADMIN`.
- O frontend gera uma chave `WRITE` automaticamente e a guarda em
  `localStorage.barbie_api_key`, renovando-a após um `401`.

---

## Rotas principais

| Recurso | Rotas |
|---|---|
| Barbies | `GET/POST /api/barbies`, `GET/PUT/DELETE /api/barbies/{id}`, `GET /api/barbies/search` |
| Profissões | `GET/POST /api/profissoes`, `GET/PUT/DELETE /api/profissoes/{id}` |
| Dreamhouses | `GET/POST /api/dreamhouses`, `GET/PUT/DELETE /api/dreamhouses/{id}` |
| Acessórios | `GET/POST /api/acessorios`, `GET/PUT/DELETE /api/acessorios/{id}` |
| Eventos | `GET/POST /api/eventos`, `GET/PUT/DELETE /api/eventos/{id}` |
| Chaves | `POST /api/auth/api-keys`, `GET /api/auth/api-keys`, `DELETE /api/auth/api-keys/{id}` |
| **IA — look** | **`POST /api/ia/assistente-estilo`** |
| **IA — dicas** | **`POST /api/ia/assistente-estilo/dicas`** |
| **IA — imagem** | **`POST /api/ia/assistente-estilo/regerar-imagem`** |

Documentação completa (schemas, exemplos e códigos de erro) no Swagger UI.

---

## Assistente de Estilo

### 1. Gerar o look — `POST /api/ia/assistente-estilo`

```json
{
  "barbieId": 1,
  "eventoId": 2,
  "ocasiao": null,
  "preferencias": "Gosto de tons pastel e prefiro evitar saltos muito altos"
}
```

- Informe **`eventoId`** **ou** **`ocasiao`** (texto livre, até 300 chars).
- Resposta `200`: `tituloLook`, `resumo`, `paletaCores`, `itensSugeridos`,
  `acessoriosDoAcervoRecomendados`, `promptImagem`, `imagem` e, quando o
  gerador de fotos falha, `avisoImagem` + `imagem.dataUrl` em SVG.

### 2. Refinar com dicas — `POST /api/ia/assistente-estilo/dicas`

Recebe o look já gerado como contexto e devolve de 3 a 5 dicas práticas
(caimento, cores, calçado, acessórios, cabelo) mais um `toqueFinal`.
O campo `foco` permite pedir, por exemplo, dicas só de calçado.

### 3. Outra imagem do mesmo look — `POST /api/ia/assistente-estilo/regerar-imagem`

Mantém o texto intacto e devolve uma nova variação determinística da
ilustração: pose, penteado e cenário mudam, mas **cores, peças e acessórios
continuam sendo exatamente os do look**. Não consome Gemini nem serviço
externo. O campo `variacaoImagem` (0–1000) controla a variação.

### Por que a imagem é um SVG e não uma foto aleatória

O tier gratuito do Gemini não tem cota de geração de imagem (`429`), e o
fallback anterior baseado em geradores públicos devolvia imagens sem relação
com o look. Agora o backend desenha a Barbie localmente a partir das **cores,
itens e acessórios do próprio look**, garantindo coerência entre texto e imagem
e eliminando dependência de cota externa. O frontend exibe o aviso
`avisoImagem` para deixar isso transparente ao usuário.

---

## Engenharia de prompt e segurança da IA

- **Contexto real:** nome, coleção, profissão, dreamhouse e a lista exata de
  acessórios da Barbie são injetados no prompt — a IA não inventa o catálogo.
- **Escopo e tom:** `systemInstruction` fixa português do Brasil, tom alegre e
  elegante, proibição de marcas reais e conteúdo ofensivo/sexual/político.
- **Saída estruturada:** `responseMimeType=application/json` +
  `responseSchema` com `maxItems` nos arrays. O parsing defensivo cobre
  respostas parciais.
- **Custo/latência:** `thinkingConfig.thinkingLevel=minimal`, limites de
  `maxOutputTokens` e verificação de `finishReason` (`MAX_TOKENS`/`SAFETY`).
- **Anti-alucinação:** acessórios recomendados são filtrados no servidor
  contra o acervo real da Barbie antes de chegar ao cliente.
- **Prompt de imagem herda o look:** o visual vem do `promptImagem` gerado
  pela própria IA, com a restrição explícita “no text, no logos, no
  watermarks”.

---

## Resiliência e tratamento de erros

| Cenário | Status | Tratamento |
|---|:---:|---|
| `barbieId`/`eventoId` inexistente | 404 | `ResourceNotFoundException` |
| Nem `eventoId` nem `ocasiao` | 422 | `BusinessException` |
| Corpo inválido (Bean Validation) | 400 | `MethodArgumentNotValidException` |
| `ADMIN` pedido na rota pública | 422 | `ApiKeyController` |
| `X-API-Key` ausente/inválida | 401 | `ApiKeyFilter` |
| Chave sem permissão | 403 | `ApiKeyFilter` |
| Muitas requisições do mesmo IP | 429 (+ `Retry-After`) | `RateLimitFilter` |
| `GEMINI_API_KEY` vazia | 503 | `GeminiClient` (falha rápido) |
| Timeout / 5xx do Gemini | 503 | 1 retentativa com 500 ms |
| Rate limit do Gemini | 429 | Sem retry, falha rápido |
| Resposta de IA inválida | 502 | `parseResposta` / `extrairTexto` |
| Sem cota / erro do Gemini (texto) | 200 | Look e dicas montados localmente + campo `aviso` |
| Sem cota de imagem | 200 | Ilustração SVG + `avisoImagem` |

Todos os erros usam o envelope único `timestamp`, `status`, `error`, `message`,
`path`, inclusive os erros lançados dentro dos filtros (`ErrorJsonWriter`).

---

## Testes

```bash
export JAVA_HOME=/Users/isabellaavelina/Library/Java/JavaVirtualMachines/ms-25.0.3/Contents/Home
mvn test
```

Cobertura crítica incluída:

- `ApiKeyHasherTest` — hash determinístico de 64 hex e máscara sem vazar a chave.
- `ApiKeySecurityIntegrationTest` — rota pública não cria `ADMIN`, a chave bruta
  nunca aparece em consultas, a chave gerada autentica escrita e sem chave o
  filtro barra com `401`.
- `IlustracaoLookClientTest` — SVG válido em `data:` URL, variações diferentes,
  resiliência a look nulo.
- `LookLocalClientTest` — fallback de texto usa acessórios reais e sinaliza
  origem no campo `aviso`.
- `DreamworldApiApplicationTests` — sobe o contexto Spring.

---

## Deploy

### Backend (Render)

O repositório contém `render.yaml` e um `Dockerfile` multi-stage (build com
JDK 25, runtime enxuto). O Render não tem runtime Java nativo, por isso o
deploy é via Docker.

1. Conecte o repositório em **New → Web Service → From a Git repository**, ou
   aplique o Blueprint (`New → Blueprint`). O nome do serviço é
   `projeto2-apibarbie`, preservando a URL pública.
2. No dashboard, preencha os segredos `GEMINI_API_KEY` e
   `CORS_ALLOWED_ORIGINS`. Confira também `APP_TRUST_PROXY_HEADERS=true`.
3. Valide:

```bash
curl -s https://projeto2-apibarbie.onrender.com/actuator/health
curl -s https://projeto2-apibarbie.onrender.com/api-docs | grep promptImagem
```

### Frontend (GitHub Pages)

Repositório separado `isaave/Front_apibarbie`, publicado em
`https://isaave.github.io/Front_apibarbie/`. O `BASE` do `index.html` aponta
para a API do Render. A chave é gerada automaticamente na primeira carga.

---

## Limitações conhecidas

- **H2 em memória:** dados e chaves são recriados a cada restart do Render.
- **Plano free do Render:** suspende após inatividade; a primeira requisição
  pode levar ~50 s. Para a apresentação, acesse a URL pouco antes ou use um
  plano pago.
- **Tier gratuito do Gemini:** não gera imagens e pode esgotar a cota de texto.
  O sistema degrada em três camadas: texto montado a partir do acervo real
  (campo `aviso`), **foto realista via Pollinations/FLUX** e, por fim,
  ilustração SVG determinística (`avisoImagem` diz qual caminho foi usado).
  Configure uma chave com cota antes da banca para demonstrar o caminho
  completo com o Gemini.
- **Gerador de fotos externo:** o Pollinations é público e não exige chave,
  mas pode devolver `429` em picos de uso. Nesse caso a API cai para o SVG,
  sem quebrar a demonstração.
- **Sem persistência de chaves:** reiniciar a API invalida as chaves; o
  frontend renova automaticamente.
