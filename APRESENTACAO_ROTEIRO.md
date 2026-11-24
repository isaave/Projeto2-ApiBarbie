# Roteiro de Apresentação — Trabalho 2 (15 minutos)

**Tema:** Barbie Dreamworld API + Assistente de Estilo com IA Generativa
**Categoria:** Geração e Produtividade (conforme o PDF: formulário parametrizado
para criação e cópia de peças de texto, com deploy real e IA generativa)
**Duração total:** 2 + 3 + 5 + 3 + 2 = **15 minutos**

> Dica geral: deixe as abas abertas antes de começar (frontend, Swagger,
> `/actuator/health` e este roteiro). O Render free dorme — acesse
> `https://projeto2-apibarbie.onrender.com/actuator/health` ~2 minutos antes
> para “acordar” o serviço.

---

## Parte 1 — Visão Geral e Evolução da Solução (2 min)

**Objetivo:** contexto do problema, público-alvo, categoria e evolução em relação
ao Trabalho 1.

- [ ] Saudação e names/dupla.
- [ ] Problema: escolher um look coerente com o acervo real da Barbie é
      repetitivo; imagem aleatória não ajuda porque não combina com o que a
      boneca realmente possui.
- [ ] Público-alvo: fãs, colecionadores e quem quer testar estilo com
      personagens do universo Dreamworld.
- [ ] Categoria escolhida: **Geração e Produtividade** — o usuário preenche um
      formulário, recebe look + dicas + foto e pode copiar/exportar o texto.
- [ ] Evolução: **Trabalho 1** = 5 entidades + CRUD (Barbies, Profissões,
      Dreamhouses, Acessórios, Eventos). **Trabalho 2** = camada de IA
      generativa, deploy real, autenticação por chave, rate limit, fallback e
      geração de foto.
- [ ] Mostrar a URL pública já carregada:
      `https://isaave.github.io/Front_apibarbie/`

**Frase de transição:** _“Vou mostrar a arquitetura de produção e como a IA é
chamada com segurança.”_

---

## Parte 2 — Arquitetura Full Stack e Deploy (3 min)

**Objetivo:** demonstrar a arquitetura de produção e a estratégia de
prompts/regras de negócio, sem aprofundar código.

- [ ] Mostrar o diagrama do README (seção **Arquitetura**):
  - Frontend em **GitHub Pages** → API em **Render** (Docker multi-stage,
    Java 25, HTTPS) → **Gemini** (texto) e **Gemini/Pollinations** (foto).
  - **H2 em memória** com dados de exemplo.
  - Cadeia de imagem: Gemini → Pollinations/FLUX (foto realista) → SVG
    determinístico desenhado a partir do próprio look.
- [ ] Comunicação segura com a LLM:
  - `GEMINI_API_KEY` **só** nas variáveis de ambiente do Render; nada no
    frontend nem no repositório.
  - O frontend gera uma `X-API-Key` WRITE automaticamente e a renova após 401.
  - CORS explícito para `https://isaave.github.io`, nunca `*`.
- [ ] Estratégia de prompts e regras:
  - `systemInstruction` com papel de estilista, tom e escopo limitados.
  - Resposta em **JSON estruturado** (título, resumo, paleta, itens,
    acessórios, prompt de imagem).
  - Acessórios recomendados **somente** os que pertencem à Barbie escolhida.
  - Histórico do chat limitado a 6 mensagens para controlar tokens.
- [ ] Abrir o Swagger em
      `https://projeto2-apibarbie.onrender.com/swagger-ui/index.html` e citar
      schemas, exemplos e `OPENAPI_SERVER_URL` apontando para produção.

**Frase de transição:** _“Agora vou usar a aplicação pública, do jeito que o
usuário usa.”_

---

## Parte 3 — Demonstração Prática em Produção (5 min) ⭐

**Objetivo:** demonstração ao vivo pela **URL pública no navegador**, com
interação completa, tempo de resposta e fluidez da interface.

- [ ] Abrir `https://isaave.github.io/Front_apibarbie/` (nada de localhost).
- [ ] Mostrar que a chave aparece sozinha na barra (`localStorage`) — sem
      passo manual.
- [ ] Aba **✨ Assistente**: escolher uma Barbie, informar uma ocasião e clicar
      em **Gerar look com IA**. Comentar o spinner enquanto a IA responde.
- [ ] Mostrar o resultado renderizado:
  - **foto realista**, título, resumo, paleta, itens e acessórios do acervo;
  - `aviso` / `avisoImagem` indicando quando houve fallback;
  - coerência entre imagem e texto (nunca imagem aleatória).
- [ ] No chat, enviar uma mensagem de ajuste (“quero algo mais discreto”) e
      mostrar que o contexto usa só as **6 mensagens recentes**.
- [ ] Clicar em **💡 Dicas aprimoradas** e em **🔄 Outra imagem** — a foto muda,
      mas cores, peças e acessórios continuam os mesmos.
- [ ] Clicar em **📋 Copiar look** para mostrar a exportação do texto.
- [ ] Se sobrar tempo: `GET /actuator/health` → `UP` e `/api-docs` com
      `promptImagem`, `historico` e `/regerar-imagem`.

**Frase de transição:** _“Agora vou mostrar o que acontece quando algo dá
errado e como isso fica visível para o usuário.”_

---

## Parte 4 — Resiliência, Segurança e Limites (3 min)

**Objetivo:** demonstrar comportamento em casos de erro em produção, validação,
timeouts/rate-limit da LLM e gestão de variáveis de ambiente.

- [ ] **Validação de formulários:** enviar ocasião vazia e um histórico com
      7 mensagens → erro amigável / `400` com `fieldErrors`.
- [ ] **Limites de caracteres:** `ocasiao` até 300, `preferencias` até 300,
      mensagens do chat até 1000 no input e 400 no histórico.
- [ ] **Timeout e rate limit:** frontend usa 120 s (IA 240 s) com retry;
      `429` traz `Retry-After`; `401` renova a chave; `403`, `503`, `5xx` e
      falha de rede têm mensagens próprias — a tela nunca trava.
- [ ] **Segurança:** chaves com hash SHA-256 (a bruta só na criação), rota
      pública não cria `ADMIN` (`422`), rate limit por IP, CORS explícito,
      `X-Forwarded-For` só com `APP_TRUST_PROXY_HEADERS=true`.
- [ ] **Variáveis de ambiente:** mostrar `.env.example` (19 variáveis) e
      `.env.example`/README alinhados; `GEMINI_API_KEY` e
      `IMAGE_POLLINATIONS_*` configuradas no Render.
- [ ] **Fallbacks com transparência:** texto local (`aviso`), foto FLUX e SVG
      (`avisoImagem`) — a IA pode falhar sem quebrar a demo.

**Frase de transição:** _“Para fechar, um resumo rápido de qualidade e das
limitações conhecidas.”_

---

## Parte 5 — Perguntas e Encerramento (2 min)

**Objetivo:** espaço para arguição e encerramento seguro.

- [ ] Resumo em 3 frases: aplicação publicada com HTTPS, código documentado,
      18 testes passando com JDK 25.
- [ ] Limitações com honestidade: H2 em memória (dados voltam ao restart),
      plano free do Render suspende após inatividade, tier gratuito do Gemini
      sem cota de imagem/texto (mitigado por fallback local + foto FLUX + SVG).
- [ ] Convidar perguntas e deixar **frontend, Swagger e health** abertos.

**Frase final:** _“A aplicação está publicada, documentada e pronta para
teste — obrigado!”_

---

## Checklist rápido antes da banca

- [ ] `GEMINI_API_KEY` ativa no Render (testar uma geração de texto).
- [ ] `CORS_ALLOWED_ORIGINS=https://isaave.github.io` no Render.
- [ ] `APP_TRUST_PROXY_HEADERS=true` no Render.
- [ ] `GET /actuator/health` → `UP` (acessar antes para acordar o free).
- [ ] `/api-docs` contém `promptImagem`, `historico` e a rota `/regerar-imagem`.
- [ ] Frontend abre e gera chave sozinho no primeiro acesso.
- [ ] `mvn clean package` passando (18 testes, JDK 25).
- [ ] Abas do Swagger, frontend, health e este roteiro já abertas.
