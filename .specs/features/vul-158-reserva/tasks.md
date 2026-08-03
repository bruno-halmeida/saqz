# Tasks — VUL-158

## T1 — Contrato e transporte de roster/promoção

**Onde**: domínio e `KtorAttendanceGateway`.

**Done when**: roster usa `GET .../attendance/roster`; promoção usa `POST .../attendance/promote`
com `requestId`, `memberId` e `reason`; ambos mapeiam o contrato existente e as falhas; os
testes de transporte verificam rota, body, ETag e retry.

**Gate**: `cd mobile && ./gradlew :features:groups:data:iosSimulatorArm64Test`

## T2 — Estado e coordenação do detalhe

**Onde**: Contract, ViewModel, fakes e módulo de apresentação.

**Done when**: a carga combina os gateways, preserva a ordem do backend, usa config do grupo,
expõe estados de promoção/capacidade e aplica optimistic update/rollback com geração; conflito
de capacidade dispara reload.

**Gate**: `cd mobile && ./gradlew :features:groups:presentation:iosSimulatorArm64Test`

## T3 — Seção e controles visuais

**Onde**: `GameWaitlistSection.kt`, strings próprias e pontos mínimos do Screen.

**Done when**: seção vazia é omitida; filas, posição, iniciais, atleta, faixa, promoção MANUAL,
ação de capacidade e bottom-sheet PT-BR aparecem com componentes do DS; FIFO não mostra promover.

**Gate**: `cd mobile && ./gradlew :features:groups:presentation:iosSimulatorArm64Test`

## T4 — Cobertura visual e gate final

**Onde**: testes da tela/ViewModel, screenshot test e PNGs Roborazzi.

**Done when**: estados de carga, erro, vazio, fila, FIFO/MANUAL, rollback e conflito têm
assertions; PNGs são regravados e inspecionados; gates finais passam.

**Gate**: `cd mobile && ./gradlew :features:groups:presentation:iosSimulatorArm64Test :features:groups:presentation:detektAll :compose-app:iosSimulatorArm64Test :features:groups:data:iosSimulatorArm64Test`
