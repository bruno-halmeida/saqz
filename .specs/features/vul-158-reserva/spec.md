# VUL-158 — Reserva na tela do jogo

## Objetivo

Dar ao administrador e aos atletas uma leitura clara da reserva no detalhe do jogo,
mantendo a ordem calculada pelo backend e permitindo promoção manual e ajuste de vagas
com concorrência otimista.

## Requisitos

- **R1 — Lista**: quando o roster de reserva não estiver vazio, a tela mostra uma seção
  `Reserva` com a posição recebida, iniciais/avatar, nome e posição do atleta.
- **R2 — Ordem e prioridade**: a apresentação preserva exatamente a ordem recebida pelo
  endpoint. Quando a prioridade de mensalistas estiver ligada, mensalistas recebem uma
  indicação visual; a apresentação não calcula nem reordena faixas.
- **R3 — Promoção**: administradores veem a ação `Promover` apenas quando a configuração
  do grupo é `MANUAL`. A ação envia `memberId` e `requestId`; em `FIFO` ela não é renderizada.
- **R4 — Capacidade**: administradores podem abrir um bottom-sheet com o stepper de vagas.
  O salvamento envia o ETag atual em `If-Match` e, em conflito, recarrega o detalhe.
- **R5 — Estados**: roster vazio não renderiza a seção; falha de carga mostra o estado
  existente de erro/retry; promoção e capacidade atualizam a UI de forma otimista e fazem
  rollback em falha, protegidas por contador de geração.
- **R6 — Strings**: todas as strings novas ficam em `strings_game_waitlist.xml`, em PT-BR,
  sem hardcode no Kotlin.

## Casos de borda

- respostas antigas de roster sem posição continuam válidas e mostram posição não informada;
- a resposta de capacidade pode promover automaticamente mais de uma pessoa, então a tela
  refaz a leitura do roster depois de uma escrita bem-sucedida;
- uma resposta de leitura ou escrita de uma geração anterior não pode sobrescrever o estado
  de uma tentativa mais recente.

## Fora de escopo

- reimplementar a ordenação de reserva no mobile;
- alterar regras ou payloads do backend já mergeado;
- adicionar fotos remotas: o avatar usa o componente do design system com iniciais.
