# Saqz

Saqz é um SaaS mobile-first para gestão de grupos de vôlei amador: organização
de jogos, presença, escalação e cobrança mensal dos membros. O app é feito em
Compose Multiplatform (Android e iOS), com um backend Kotlin/Spring Boot como
fonte autoritativa dos dados e uma landing page estática para pré-lançamento.

O monorepo tem workspaces independentes — `mobile/`, `backend/` e a landing.

## Como rodar

- JDK 21.
- Docker via Colima, com `DOCKER_HOST` configurado — os testes de integração
  do backend sobem PostgreSQL via Testcontainers.
- Node para o `firebase-tools` usado pelo ambiente de dev local.

```bash
backend/gradlew -p backend check      # build + testes do backend
mobile/gradlew -p mobile detektAll    # lint do mobile
```

## Browser

[Lightpanda](https://lightpanda.io/docs/quickstart) é o browser padrão do
repositório — headless, compatível com CDP. `npm ci` baixa o binário em
`~/.cache/lightpanda-node/lightpanda`.

```bash
npm run browser                 # servidor CDP em ws://127.0.0.1:9222
```

Puppeteer/Playwright conectam via `puppeteer-core`/`playwright-core` nesse
endpoint (`connect({ browserWSEndpoint })` / `chromium.connectOverCDP()`), sem
baixar Chromium. Para agentes, `.mcp.json` registra o MCP server do Lightpanda —
use-o no lugar do Chrome para navegação, DOM e teste de página.

**Lightpanda não pinta.** Ele automatiza DOM e JS, mas não tem engine de layout
visual: `lightpanda --help` oferece `agent · fetch · mcp · run · serve · version`
e nenhum comando de screenshot. Para captura de pixel — comparar um render
contra o design, por exemplo — use Chrome headless direto:

```bash
"/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" \
  --headless=new --disable-gpu --hide-scrollbars \
  --screenshot=out.png --window-size=1440,4200 --virtual-time-budget=20000 \
  http://127.0.0.1:PORTA/pagina.html
```

> **CI em reconstrução.** Os gates de shell em `scripts/` e os workflows de gate
> foram removidos para serem redesenhados do zero. Só `deploy-pages.yml`
> permanece. Até a nova CI existir, nada é verificado automaticamente em PR —
> nem boundaries de módulo, nem design tokens, nem varredura de credenciais.

## Specs e tasks

Specs, tasks e o histórico de decisão do produto vivem no Linear, não neste
repositório:

- Projeto ativo: [Reset da Apresentação Mobile](https://linear.app/vulkz/project/reset-da-apresentacao-mobile-70d5787f8536)
- Decisões arquiteturais: [Architecture Decisions (ADs) — estado vivo](https://linear.app/vulkz/document/architecture-decisions-ads-estado-vivo-f94c40811c16)

## Documentação de arquitetura

O contrato operacional dos agentes de código mobile está em
[`mobile/AGENTS.md`](mobile/AGENTS.md).
