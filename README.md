# Saqz

Saqz é um SaaS mobile-first para gestão de grupos de vôlei amador: organização
de jogos, presença, escalação e cobrança mensal dos membros. O app é feito em
Compose Multiplatform (Android e iOS), com um backend Kotlin/Spring Boot como
fonte autoritativa dos dados e uma landing page estática para pré-lançamento.

O monorepo tem workspaces independentes — `mobile/`, `backend/` e a landing —
cada um com seus próprios gates de CI.

## Como rodar

- JDK 21 (`scripts/check-ios` usa `SAQZ_JAVA_HOME` para apontar o JDK certo).
- Docker via Colima, com `DOCKER_HOST` configurado — os testes de integração
  do backend sobem PostgreSQL via Testcontainers.
- Node para o `firebase-tools` usado pelo ambiente de dev local.

Gates locais em `scripts/`:

```bash
./scripts/check-all      # todos os gates
./scripts/check-gradle   # build + testes JVM/Android (pesado, precisa de emulador)
./scripts/check-ios      # build + testes iOS
```

Veja os demais scripts em `scripts/` para gates específicos (design tokens,
boundaries, credenciais, etc.).

## Specs e tasks

Specs, tasks e o histórico de decisão do produto vivem no Linear, não neste
repositório:

- Projeto ativo: [Reset da Apresentação Mobile](https://linear.app/vulkz/project/reset-da-apresentacao-mobile-70d5787f8536)
- Decisões arquiteturais: [Architecture Decisions (ADs) — estado vivo](https://linear.app/vulkz/document/architecture-decisions-ads-estado-vivo-f94c40811c16)

## Documentação de arquitetura

O contrato operacional dos agentes de código mobile está em
[`mobile/AGENTS.md`](mobile/AGENTS.md).
