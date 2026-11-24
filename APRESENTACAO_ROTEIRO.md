# Roteiro de Apresentação — Trabalho 2 (15 minutos)

**Tema:** Barbie Dreamworld API + Assistente de Estilo com IA Generativa
**Categoria:** Geração de Conteúdo
**Duração total:** 2 + 3 + 5 + 3 + 2 = **15 minutos**

> Dica geral: deixe as abas abertas antes de começar (API, Swagger, frontend e
> este roteiro). O Render free dorme — acesse
> `https://projeto2-apibarbie.onrender.com/actuator/health` ~2 minutos antes
> para “acordar” o serviço.

---

## Parte 1 — Abertura e problema (2 min)

**Objetivo:** contextualizar e prender a atenção.

- [ ] Saudação e nome/dupla.
- [ ] Frase de abertura: _“E se a estilista da Barbie pudesse criar um look
      completo a partir do próprio acervo dela, em segundos?”_
- [ ] Mostrar o problema que motivou o trabalho: escolher look para uma ocasião
      é repetitivo; gerar imagem aleatória não ajuda porque não combina com o
      que a boneca realmente tem.
- [ ] Anunciar a categoria: **Geração de Conteúdo com IA em produção**.
- [ ] Mostrar a URL do frontend já carregada:
      `https://isaave.github.io/Front_apibarbie/`

**Frase de transição:** _“Vou mostrar a arquitetura, a API e a demo ao vivo.”_

---

## Parte 2 — Arquitetura e decisões (3 min)

**Objetivo:** explicar como as peças se conectam sem aprofundar código.

- [ ] Mostrar o diagrama do README (seção **Arquitetura**).
  - Frontend GitHub Pages → API Spring Boot 4 no Render (Docker, Java 25).
  - H2 em memória com dados de exemplo.
  - Gemini para texto; foto realista via Gemini → Pollinations/FLUX;
    último recurso: SVG desenhado a partir do próprio look.
- [ ] Destacar 3 decisões de produção:
  1. **Chaves de API com hash SHA-256** — a chave bruta só aparece na criação.
  2. **Rate limit por IP** com `Retry-After` e JWT não usado (chave mesmo).
  3. **CORS explícito** para `https://isaave.github.io`, nunca `*`.
- [ ] Mostrar o Swagger em
      `https://projeto2-apibarbie.onrender.com/swagger-ui/index.html` e citar
      que o contrato está documentado com schemas e exemplos.

**Frase de transição:** _“Agora vou rodar o fluxo principal na API.”_

---

## Parte 3 — Demo ao vivo da API e da IA (5 min) ⭐

**Objetivo:** provar que funciona de ponta a ponta. Esta é a parte central.

### 3.1 Saúde e dados (1 min)
- [ ] `GET /actuator/health` → `{"status":"UP"}`.
- [ ] `GET /api/barbies` → mostrar que há Barbies cadastradas com profissão e
      acessórios.

### 3.2 Chave de API (1 min)
- [ ] `POST /api/auth/api-keys` com
      `{"owner":"Banca","accessLevel":"WRITE"}`.
- [ ] Mostrar a chave gerada e reforçar: _“ela só aparece agora; consultas
      depois não devolvem o valor”_.
- [ ] (Opcional, se sobrar tempo) mostrar que `ADMIN` pela rota pública
      retorna `422`.

### 3.3 Geração do look (2 min)
- [ ] `POST /api/ia/assistente-estilo` com `X-API-Key` e body:

```json
{ "barbieId": 1, "ocasiao": "festa no jardim", "preferencias": "tons pastel" }
```

- [ ] Apontar no retorno: `tituloLook`, `paletaCores`, `itensSugeridos` e
      **acessórios que realmente pertencem à Barbie**.
- [ ] Abrir `imagem.dataUrl` no navegador: **foto realista** gerada a partir do
      look (quando o Gemini não tem cota, entra o FLUX). Se cair no último
      recurso, é uma ilustração vetorial coerente com as cores e peças do texto.
- [ ] Mostrar `aviso` e `avisoImagem` e explicar com naturalidade:
      _“tentamos a foto pelo Gemini; sem cota, usamos um gerador de fotos
      alternativo e, se ele falhar, desenhamos a partir do próprio look — nunca
      uma imagem aleatória. Os campos `aviso` e `avisoImagem` deixam isso
      transparente”_.

### 3.4 Dicas e nova imagem (1 min)
- [ ] `POST /api/ia/assistente-estilo/dicas` com o contexto do look → mostrar
      as dicas e o `toqueFinal`.
- [ ] `POST /api/ia/assistente-estilo/regerar-imagem` → mostrar que a pose muda
      mas as cores/peças continuam as mesmas.

**Frase de transição:** _“A mesma experiência está disponível no frontend, sem
digitar nada.”_

---

## Parte 4 — Frontend e fluxo do usuário (3 min)

**Objetivo:** mostrar a aplicação completa, não só a API.

- [ ] Abrir `https://isaave.github.io/Front_apibarbie/`.
- [ ] Mostrar que a chave é gerada automaticamente (`localStorage`) — sem
      passo manual para o usuário.
- [ ] Aba **✨ Assistente**: escolher uma Barbie, digitar a ocasião, clicar em
      **Gerar look com IA**.
- [ ] Mostrar o resultado renderizado: imagem, título, paleta, itens e
      acessórios.
- [ ] Clicar em **💡 Dicas aprimoradas** e depois em **🔄 Outra imagem**.
- [ ] Mostrar rapidamente o CRUD (Barbies/Eventos) e o tratamento de erros:
      loading, mensagens amigáveis para 429/503 e renovação da chave após
      401.

**Frase de transição:** _“Para fechar, vou resumir testes, segurança e o que
fica como próximo passo.”_

---

## Parte 5 — Qualidade, testes e encerramento (2 min)

**Objetivo:** fechar com confiança e honestidade sobre limitações.

- [ ] `mvn test` passando: mencionar os testes de hash de chave, bloqueio de
      `ADMIN`, não exposição da chave e validade do SVG.
- [ ] Segurança em produção: hash SHA-256, rate limit, CORS explícito,
      `X-Forwarded-For` só atrás de proxy confiável.
- [ ] Limitações com transparência: H2 em memória (dados voltam ao restart),
      plano free do Render suspende após inatividade, tier gratuito do Gemini
      sem cota de imagem/texto (mitigado por fallback local + foto FLUX + SVG).
- [ ] Convidar perguntas e deixar Swagger + frontend abertos.

**Frase final:** _“A aplicação está publicada, documentada e pronta para
teste — obrigado!”_

---

## Checklist rápido antes da banca

- [ ] `GEMINI_API_KEY` ativa no Render (testar uma geração de texto).
- [ ] `CORS_ALLOWED_ORIGINS=https://isaave.github.io` no Render.
- [ ] `APP_TRUST_PROXY_HEADERS=true` no Render.
- [ ] `GET /actuator/health` → `UP` (acessar antes para acordar o free).
- [ ] `/api-docs` contém `promptImagem` e a rota `/regerar-imagem`.
- [ ] Frontend abre e gera chave sozinho no primeiro acesso.
- [ ] Abas do Swagger, frontend e este roteiro já abertas.
