# Barbie Dreamworld API — Assistente de Estilo (IA Generativa)

API de gerenciamento do universo **Barbie Dreamworld** (Barbies, Profissões,
Dreamhouses, Acessórios e Eventos), estendida com uma funcionalidade de
**Geração de Conteúdo via IA generativa (Google Gemini)**: o **Assistente de
Estilo**, que sugere um look completo para uma Barbie combinando os dados
reais dela (profissão, coleção, acessórios já cadastrados) com a ocasião
informada pelo usuário.

Categoria do trabalho: **Geração de Conteúdo**.

---

## Sumário

- [Como rodar o projeto](#como-rodar-o-projeto)
- [Variáveis de ambiente](#variáveis-de-ambiente)
- [Autenticação (X-API-Key)](#autenticação-x-api-key)
- [Rotas da API](#rotas-da-api)
- [Assistente de Estilo — detalhes](#assistente-de-estilo--detalhes)
- [Engenharia de prompt](#engenharia-de-prompt)
- [Tratamento de erros e resiliência](#tratamento-de-erros-e-resiliência)
- [Frontend](#frontend)

---

## Como rodar o projeto

Pré-requisitos: **Java 25** e **Maven** (ou use o wrapper `./mvnw` se estiver
presente).

```bash
# 1. Defina a chave da IA generativa (veja como gerar em "Variáveis de ambiente")
export GEMINI_API_KEY="sua-chave-aqui"

# 2. Rode a aplicação
mvn spring-boot:run
```

A API sobe em `http://localhost:8080`.

- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- Console H2 (banco em memória): `http://localhost:8080/h2-console`

O banco é H2 em memória — os dados são recriados a cada restart (ver
`infrastructure/LoadDatabase.java` para os dados de exemplo carregados).

---

## Variáveis de ambiente

| Variável          | Obrigatória | Padrão                | Descrição                                                                 |
|-------------------|:-----------:|------------------------|----------------------------------------------------------------------------|
| `GEMINI_API_KEY`  | Sim*        | (vazio)                | Chave da API do Gemini. Gere uma gratuitamente no [Google AI Studio](https://aistudio.google.com/apikey). |
| `GEMINI_MODEL`    | Não         | `gemini-3.6-flash`     | Modelo do Gemini usado. Veja os modelos disponíveis atualmente em [ai.google.dev/gemini-api/docs/models](https://ai.google.dev/gemini-api/docs/models) — o Google descontinua modelos com poucas semanas de aviso. |

\* Só é obrigatória para usar o endpoint `/api/ia/assistente-estilo`. O resto
da API funciona normalmente sem ela. Se a chave não estiver configurada, o
endpoint responde `503` com uma mensagem clara em vez de quebrar.

**A chave nunca é commitada no repositório** — ela é lida via
`${GEMINI_API_KEY:}` em `application.properties` e o `.gitignore` já
ignora arquivos `.env`.

---

## Autenticação (X-API-Key)

A API já possui seu próprio esquema de segurança (não é algo criado para
este trabalho, mas se aplica também ao endpoint de IA):

- `GET` são públicos.
- `POST` / `PUT` / `PATCH` / `DELETE` exigem o header `X-API-Key`, com nível
  `WRITE` ou `ADMIN`.
- Gere uma chave em `POST /api/auth/api-keys` (rota pública).

Como o Assistente de Estilo é um `POST`, ele também é protegido por esse
mesmo mecanismo e sujeito ao rate limiting global da API (5 req/min por IP
para métodos de escrita) — reaproveitando a infraestrutura de segurança já
existente em vez de duplicar lógica.

---

## Rotas da API

| Recurso                     | Rotas                                                                 |
|------------------------------|------------------------------------------------------------------------|
| Barbies                     | `GET/POST /api/barbies`, `GET/PUT/DELETE /api/barbies/{id}`, `GET /api/barbies/search` |
| Profissões                  | `GET/POST /api/profissoes`, `GET/PUT/DELETE /api/profissoes/{id}`      |
| Dreamhouses                 | `GET/POST /api/dreamhouses`, `GET/PUT/DELETE /api/dreamhouses/{id}`    |
| Acessórios                  | `GET/POST /api/acessorios`, `GET/PUT/DELETE /api/acessorios/{id}`      |
| Eventos                     | `GET/POST /api/eventos`, `GET/PUT/DELETE /api/eventos/{id}`            |
| Chaves de API               | `POST /api/auth/api-keys`, `GET /api/auth/api-keys`, `DELETE /api/auth/api-keys/{id}` |
| **Assistente de Estilo (IA)** | **`POST /api/ia/assistente-estilo`**                                  |

A documentação completa e interativa de todas as rotas (schemas, exemplos,
códigos de erro) está no Swagger UI.

---

## Assistente de Estilo — detalhes

**`POST /api/ia/assistente-estilo`**

Gera, via IA generativa, uma sugestão de look para uma Barbie cadastrada,
considerando a profissão dela, a coleção, a dreamhouse e os acessórios que
ela já possui, para uma ocasião informada (um evento já cadastrado **ou**
uma descrição livre).

Requisição:
```json
{
  "barbieId": 1,
  "eventoId": 2,
  "ocasiao": null,
  "preferencias": "Gosto de tons pastel e prefiro evitar saltos muito altos"
}
```
- `barbieId`: obrigatório.
- Informe **`eventoId`** (evento já cadastrado) **ou** **`ocasiao`** (texto
  livre, até 300 caracteres). Se nenhum dos dois vier, a API responde `422`.
- `preferencias`: opcional, até 300 caracteres.

Resposta (`200`):
```json
{
  "barbieId": 1,
  "nomeBarbie": "Barbie Cientista",
  "ocasiao": "Evento cadastrado \"Gala das Estrelas\"...",
  "tituloLook": "Look Laboratório Glamouroso",
  "resumo": "Um visual que une sofisticação e praticidade...",
  "paletaCores": ["Rosa pastel", "Branco", "Prateado"],
  "itensSugeridos": [
    { "nome": "Sandália prateada com salto bloco", "categoria": "Calçado", "motivo": "..." }
  ],
  "acessoriosDoAcervoRecomendados": ["Bolsa holográfica"]
}
```

---

## Engenharia de prompt

O prompt (montado em `AssistenteEstiloService.montarPrompt`) segue estas
diretrizes:

- **Contexto real e específico**: nome, coleção, profissão, dreamhouse e a
  lista exata de acessórios que a Barbie já possui são injetados no prompt
  — a IA não "inventa" o contexto, ela recebe dados reais do banco.
- **Delimitação de comportamento**: regras explícitas no prompt (responder
  em PT-BR, nunca citar marcas reais, não fugir do tema, limite de 5 itens
  sugeridos e 4 cores).
- **Redução de alucinação nos dados reais**: a IA é instruída a só citar,
  em `acessoriosDoAcervoRecomendados`, itens que existam **exatamente** na
  lista de acessórios informada.
- **Saída estruturada garantida**: em vez de pedir "responda em JSON" no
  texto do prompt (frágil — o modelo pode envolver em ```json ou adicionar
  texto solto), a requisição usa o campo nativo do Gemini
  `generationConfig.responseMimeType = "application/json"` **com um
  `responseSchema` explícito** (ver `AssistenteEstiloService.buildResponseSchema`
  e `GeminiClient`). Isso resolve de forma robusta o parsing da resposta da
  IA — o próprio provedor garante a estrutura do JSON antes de ele chegar
  na aplicação. Mesmo assim, o parsing em `parseResposta` é defensivo
  (campos ausentes têm valores padrão) para o caso de uma resposta parcial.

---

## Tratamento de erros e resiliência

| Cenário                                              | Status HTTP | Onde é tratado |
|-------------------------------------------------------|:-----------:|----------------|
| `barbieId` inexistente / `eventoId` inexistente        | 404         | `ResourceNotFoundException` |
| Nem `eventoId` nem `ocasiao` informados                | 422         | `BusinessException` |
| Corpo da requisição inválido (campo faltando, etc.)    | 400         | `MethodArgumentNotValidException` (Bean Validation) |
| `GEMINI_API_KEY` não configurada                       | 503         | `GeminiClient` — falha rápido, sem tentar chamar a IA |
| Timeout ou API do Gemini fora do ar (5xx)              | 503         | `GeminiClient` — 1 retentativa automática com pequeno atraso antes de desistir |
| Rate limit do Gemini (429)                             | 429         | `GeminiClient` — repassado ao cliente sem retry (evita piorar o rate limit) |
| IA rejeita a requisição / bloqueio de segurança (4xx)  | 502         | `GeminiClient` |
| Resposta da IA vazia ou em formato inesperado          | 502         | `AssistenteEstiloService.parseResposta` |
| Chave de API do projeto (`X-API-Key`) ausente/inválida | 401/403     | `ApiKeyFilter` (mecanismo já existente na API, reaproveitado) |
| Muitas requisições do mesmo IP                         | 429         | `RateLimitFilter` (mecanismo já existente na API, reaproveitado) |

Todos os erros seguem o mesmo envelope `ErrorResponse` já usado no resto da
API (`timestamp`, `status`, `error`, `message`, `path`), então o frontend
trata o erro de IA exatamente como trata qualquer outro erro da API.

---

## Frontend

O frontend estático (`Front - ApiBarbie/index.html`) tem uma aba
**✨ Assistente**, onde é possível escolher uma Barbie e um evento (ou
digitar uma ocasião livre), disparar a geração e ver o resultado formatado,
com feedback de carregamento enquanto a IA responde e mensagens de erro
amigáveis em caso de falha. O frontend está num **repositório separado** e é
publicado como Static Site.

---

## Deploy no Render (backend)

Este repositório contém um `render.yaml` (Blueprint) e um `Dockerfile`
multi-stage (Java 25). O Render **não tem runtime Java nativo**, por isso o
deploy é feito via Docker.

### 1. Preparar o repositório

```bash
git init
git add .
git commit -m "Trabalho 2 - API com IA generativa"
git remote add origin <URL_DO_SEU_REPO>
git push -u origin main
```

### 2. Criar a API key do Render

Em https://dashboard.render.com/u/settings#api-keys e exporte:

```bash
export RENDER_API_KEY="sua-key-aqui"
```

### 3. Aplicar o Blueprint

No dashboard: **New → Blueprint → selecionar o repo**. O Render lê o
`render.yaml` e cria o serviço web.

Alternativamente, via API:

```bash
curl -X POST https://api.render.com/v1/blueprints/render \
  -H "Authorization: Bearer $RENDER_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"repositoryUrl":"<URL_DO_SEU_REPO>","branch":"main"}'
```

### 4. Variáveis de ambiente no Render

O Blueprint já declara as variáveis. No primeiro deploy, o Render pede os
valores de `GEMINI_API_KEY` e `CORS_ALLOWED_ORIGINS` (marcados como
`sync: false`). Preencha:

| Variável | Valor |
|---|---|
| `GEMINI_API_KEY` | chave do Google AI Studio |
| `CORS_ALLOWED_ORIGINS` | URL pública do frontend (ex.: `https://front-apibarbie.onrender.com`) |

### 5. Verificar

- Healthcheck: `GET https://minha-api-da-barbie-1.onrender.com/actuator/health`
- Swagger: `https://minha-api-da-barbie-1.onrender.com/swagger-ui/index.html`
- A API responde `503` no endpoint de IA se a chave não estiver configurada —
  isso é esperado e indicado no corpo do erro.

> **Nota sobre o plano Free**: o Render suspende serviços free após
> inatividade e a primeira requisição pode demorar ~50 s. Para a apresentação,
> use um plano pago (`0.5c-512mb` ou superior) ou acesse a URL pouco antes
> da demo para "acordar" o serviço.

### Segurança em produção

- CORS restrito às origens configuradas (sem `*`).
- H2 Console desligado por padrão (`H2_CONSOLE_ENABLED=false`).
- Chave da IA apenas em variável de ambiente; nunca no código.
- Atenção: as rotas `POST/GET /api/auth/api-keys` ainda são públicas e
  permitem criar chave `ADMIN`. Recomenda-se protegê-las antes da avaliação
  final de segurança.
