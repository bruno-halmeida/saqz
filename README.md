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
