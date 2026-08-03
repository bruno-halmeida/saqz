# Design — VUL-158

O detalhe do jogo continua sendo o dono do carregamento e dos efeitos. A porta de domínio
`AttendanceGateway` ganha `roster` e `promote`; a implementação Ktor preserva o padrão de
retry, requestId e ETag já usado por `capacity`.

`GameDetailViewModel` carrega game, grupo, attendance, roster de attendance e roster de
atletas. O roster de attendance é autoritativo para a ordem e para a identidade exibida; o
roster de atletas só completa posição e tipo de vínculo. Operações de promoção/capacidade
usam um contador de operação, removem/ajustam dados imediatamente e restauram o snapshot se
a mesma geração falhar.

A seção visual inteira nasce em `GameWaitlistSection.kt`, reutilizando `SaqzCard`,
`SaqzAvatar`, `SaqzStatusChip`, `SaqzButton`, `SaqzBottomSheet` e `SaqzStepper`. O arquivo
`GameDetailScreen.kt` recebe somente pontos de composição: seção da reserva, ação de vagas e
sheet. O arquivo de strings é exclusivo da reserva.
