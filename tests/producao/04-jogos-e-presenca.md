# Jogos e presença

Elenco e regras em [README.md](README.md). Os textos entre aspas são os da tela.

**Antes de testar presença:** aplique o contorno do PC-01 (PROD-GR03 desliga "Prioridade de
mensalista"). Sem isso, todo "Vou" de avulso cai na lista de espera mesmo com vaga.

Ordem dentro do bloco: JG01, JG07 (antes de alguém responder), JG02, JG03, JG04, JG05, JG06. O jogo de
JG11 é criado logo no começo da rodada.

## PROD-JG01 · Marcar um jogo e a galera ser avisada · P0

**Quem:** ORG; ATL e ATL2 conferem nos aparelhos. **Deixa pronto:** o jogo de amanhã.

1. No grupo, tocar "Marcar primeiro jogo" (ou "Marcar jogo").
2. Em "Data e horário", escolher amanhã às 20:00 e tocar "Salvar data e horário".
3. Conferir a quadra (vem a do grupo), "Duração" 2h, "Vagas neste jogo" 2 e "Confirmar presença até"
   6h antes.
4. Tocar "Marcar jogo".

**Conferir**
- O grupo mostra o jogo em "PRÓXIMO JOGO", e a Início de ATL e ATL2 também.
- Em uns 15 s, todos os membros (ORG inclusive) recebem o push "O jogo está liberado. Abra o app para
  confirmar sua presença." com os botões "Confirmar" e "Não vou".

## PROD-JG02 · Confirmar presença pela Início · P0

**Quem:** ATL.

1. Na Início, no cartão do jogo, tocar "Vou".

**Conferir**
- Aparece "Presença confirmada. Bom jogo!", e o cartão passa a mostrar a confirmação com "Alterar".
- No grupo, ORG vê "Vão" com 1 e ATL em "Confirmados" na tela do jogo.

## PROD-JG03 · Responder pelo botão da notificação · P0

**Quem:** ATL2, com o push de PROD-JG01 ainda na bandeja e logado no app.

1. No push, tocar "Não vou" sem abrir o app.

**Conferir**
- **Android:** o próprio push vira "Ausência registrada.".
- **iPhone:** chega uma notificação nova com o mesmo texto.
- Ao abrir o app, o jogo mostra ATL2 como "Não vai".

## PROD-JG04 · Mudar a resposta · P1

**Quem:** ATL.

1. No grupo, no cartão do jogo, tocar "Alterar" e depois "Não vou".
2. Tocar "Alterar" de novo e depois "Vou".

**Conferir**
- Primeiro aparece "Sua vaga foi liberada."; depois, "Presença confirmada. Bom jogo!".
- Os números de "Vão" e "Não vão" acompanham as trocas.

## PROD-JG05 · Lista de espera e vaga liberada · P0

**Quem:** ORG, ATL e ATL2, no jogo de 2 vagas.

1. ORG toca "Vou" no grupo; ATL já está confirmado (PROD-JG04).
2. ATL2 toca "Alterar" → "Vou".
3. ATL toca "Alterar" → "Não vou".
4. ATL2 reabre o grupo.
5. ORG abre o jogo → "Ajustar vagas" → 3 → "Salvar vagas"; ATL volta a "Vou".

**Conferir**
- Com as 2 vagas ocupadas, ATL2 vê "As vagas acabaram. Você entrou na lista de espera.".
- Quando ATL desiste, ATL2 passa a confirmado, sem aviso: é o PC-02.
- Com 3 vagas, os três ficam confirmados e o jogo nunca passa da capacidade.

## PROD-JG06 · Levar um convidado · P1

**Quem:** ATL, confirmado.

1. Abrir o jogo ("Ver jogo") e tocar "Levar convidado".
2. Preencher "Nome do convidado" com `QA Convidado` e tocar "Adicionar à lista de espera".
3. Na linha do convidado, tocar "Tirar" e confirmar "Tirar".

**Conferir**
- Aparece "QA Convidado entrou na lista de espera." e a linha "Convidado seu".
- ORG vê "Convidado de …".
- Depois de tirar, aparece "QA Convidado saiu do jogo." e a linha some.

## PROD-JG07 · Avisar quem ainda não respondeu · P1

**Quem:** ORG, logo depois de PROD-JG01, antes de qualquer resposta.

1. No grupo, em "Esperando você", tocar "Avisar".

**Conferir**
- Aparece "Lembrete enviado no Saqz para N pessoa(s).", com N contando só quem não respondeu.
- ATL e ATL2 recebem o push "Confirme sua presença no próximo jogo. Abra o app para conferir." e o
  aviso na central; ORG não recebe cópia.

## PROD-JG08 · "Repetir toda semana" e publicar um rascunho · P1

**Quem:** ORG.

1. Tocar "Marcar jogo", escolher outro dia da semana, ligar "Repetir toda semana" e tocar "Marcar jogo".
2. Em "Próximos jogos", abrir um jogo com "Rascunho" e tocar "Publicar jogo".

**Conferir**
- O editor avisa que os próximos jogos nascem como rascunho.
- O jogo marcado sai publicado e os das semanas seguintes ficam em rascunho, visíveis só para ORG.
- O rascunho publicado vira "Jogo criado" e aparece para ATL em "Próximos jogos".

## PROD-JG09 · Editar um jogo · P1

**Quem:** ORG, num jogo futuro publicado.

1. Abrir o jogo → "Editar jogo".
2. Mudar o horário e "Vagas neste jogo" e tocar "Salvar alterações".

**Conferir**
- O jogo mostra os valores novos, e ATL vê o horário novo ao reabrir. Não há aviso de mudança.

## PROD-JG10 · Cancelar um jogo · P1

**Quem:** ORG, no jogo publicado em PROD-JG08.

1. Abrir o jogo → "Cancelar jogo".
2. Em "Cancelar o jogo?", tocar "Cancelar jogo".

**Conferir**
- O jogo some das agendas de ORG e ATL.
- Ninguém recebe aviso, apesar do texto da confirmação: é o PC-02.

## PROD-JG11 · O jogo encerra sozinho · P0

**Quem:** ORG e ATL. O jogo é criado logo no começo da rodada.

1. ORG marca um jogo para hoje, começando pelo menos 3h20 depois (o prazo mínimo é "3h antes"), com
   "Duração" 1h.
2. ATL toca "Vou" antes do prazo.
3. Esperar o fim do jogo e mais 5 minutos, e abrir o jogo.

**Conferir**
- A tela do jogo mostra "Encerrado", e ORG vê o botão de acerto.
- O jogo sai de "PRÓXIMO JOGO" e das listas.
- No Perfil de ATL, "Jogos" aumentou em 1.

## PROD-JG12 · Acerto do jogo encerrado · P1

**Quem:** ORG, no jogo de PROD-JG11.

1. No jogo, tocar "Encerrar jogo e acertar".
2. Em "Acerto do jogo", tocar "Registrar aluguel da quadra", preencher o valor (R$ 120,00) e tocar
   "Salvar lançamento".
3. Tocar "Encerrar acerto".

**Conferir**
- A tela mostra "JOGO ENCERRADO" e os mensalistas como cobertos pela mensalidade.
- A despesa de R$ 120,00 aparece no extrato do grupo e no saldo.
- No primeiro jogo encerrado do grupo, ORG também vê o cartão "Primeiro jogo encerrado. E o acerto?",
  que leva à mesma tela.
