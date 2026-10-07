# Problemas conhecidos em produção

Defeitos que já conhecemos, com a decisão tomada para cada um. Um cenário de teste que esbarrar
num item daqui aponta para ele em vez de reprovar de novo o mesmo defeito. Quando um item for
corrigido, ele sai desta lista no mesmo commit da correção.

"A triar" é achado confirmado no levantamento de 07/10/2026 que ainda espera a decisão de corrigir,
aceitar ou só documentar.

| ID | Problema | Decisão | Desde |
| --- | --- | --- | --- |
| [PC-01](#pc-01--em-grupo-novo-todo-vou-vai-para-a-lista-de-espera) | Em grupo novo, todo "Vou" vai para a lista de espera | Risco aceito por enquanto | 07/10/2026 |
| [PC-02](#pc-02--cancelar-jogo-e-liberar-vaga-da-espera-não-avisam-ninguém) | Cancelar jogo e liberar vaga da espera não avisam ninguém | Fica sem aviso por enquanto | 07/10/2026 |
| [PC-03](#pc-03-a-pc-16--achados-a-triar) | Foto do perfil só aparece para o dono | A triar | 07/10/2026 |
| PC-04 | "Trocar senha" com a conta logada pode prender na tela de entrada | A triar | 07/10/2026 |
| PC-05 | "Salvar rascunho" ao criar grupo não salva nada | A triar | 07/10/2026 |
| PC-06 | Aba Grupos sem chip de presença e "0 pedindo confirmação" fixo | A triar | 07/10/2026 |
| PC-07 | QR do convite diz "vale por 7 dias", mas o link não expira | A triar | 07/10/2026 |
| PC-08 | "Recado para a galera" do jogo não aparece para ninguém | A triar | 07/10/2026 |
| PC-09 | Promover ou rebaixar admin é bloqueado durante o teste grátis | A triar | 07/10/2026 |
| PC-10 | Lista de membros não atualiza depois de editar jogador ou aprovar pedido | A triar | 07/10/2026 |
| PC-11 | Aprovado na fila de entrada não é avisado nem passa pelo cadastro de atleta | A triar | 07/10/2026 |
| PC-12 | Android 13+ pede permissão de notificação uma vez só por instalação | A triar | 07/10/2026 |
| PC-13 | Extrato do grupo sem seletor de mês | A triar | 07/10/2026 |
| PC-14 | Recibos do Google Play aparecem com valor "—" | A triar | 07/10/2026 |
| PC-15 | "Remover do grupo" na lista de membros não pede confirmação | A triar | 07/10/2026 |
| PC-16 | "Termos de recebimentos" no site lê os termos de staging | A triar | 07/10/2026 |

## PC-01 · Em grupo novo, todo "Vou" vai para a lista de espera

**O que a pessoa vê.** O organizador cria o grupo e o jogo, a galera toca "Vou" e todo mundo
entra na lista de espera, mesmo com vaga sobrando. O toast diz "As vagas acabaram. Você entrou na
lista de espera." O próprio dono, ao tocar "Vou", também vai para a espera.

**Por quê.** Todo membro nasce avulso (`membership_type` padrão `AVULSO`) e a "Prioridade de
mensalista" nasce ligada (`access_groups.mensalista_priority` padrão `true`). Com prioridade
ligada, avulso que responde "Vou" vai para a espera mesmo com vaga
(`Attendance.confirmationTarget`, em `backend/features/groups/.../domain/attendance/Attendance.kt`).
A espera só anda quando um confirmado desiste ou quando o organizador salva "Ajustar vagas". O dono
não consegue se tornar mensalista: ninguém edita o cartão do dono.

**Contorno para o organizador.**
- Desligar "Prioridade de mensalista" em Editar grupo → "Como preencher as vagas"; ou
- tornar os atletas mensalistas em Membros e permissões → Editar jogador → "Tornar mensalista"; ou
- abrir o jogo e salvar "Ajustar vagas" para puxar quem está na espera.

**Nos testes de produção.** Antes de testar presença, aplicar um dos contornos e anotar qual.

## PC-02 · Cancelar jogo e liberar vaga da espera não avisam ninguém

**O que o app promete e não faz.**
- A confirmação de "Cancelar jogo" diz: "Os confirmados recebem um aviso e o jogo some da agenda."
  O backend não envia notificação nenhuma no cancelamento: os efeitos do cancelamento só congelam a
  presença e cancelam as cobranças pendentes (`GameMutation.CANCEL` em
  `backend/features/groups/.../application/game/GameCommands.kt`).
- Para quem está na espera, a Início diz "Se alguém desistir, você entra e a gente avisa na hora."
  e "Avisamos você se abrir vaga até …". Quando a vaga abre, a pessoa é promovida, mas não recebe
  push nem notificação no app.

**Impacto.** Um confirmado pode ir à quadra num jogo cancelado, e quem foi promovido da espera pode
não saber que tem vaga.

**Contorno.** O organizador avisa a galera por fora (Avisos do grupo ou WhatsApp).

**Nos testes de produção.** Conferir só o estado no app (jogo cancelado some da agenda; promovido
aparece como confirmado). A ausência de aviso não reprova o cenário enquanto este item estiver aqui.

## PC-03 a PC-16 · Achados a triar

- **PC-03 · Foto do perfil só para o dono.** A foto é servida só em `/api/session/photo`; os outros
  membros veem as iniciais, embora a dica diga "Aparece nas listas de presença e nos grupos.".
- **PC-04 · "Trocar senha" logado.** Em "Digite o código.", o link "Lembrou a senha? Entrar ›" leva à
  tela de entrada com a sessão ainda ativa, e entrar de novo com a mesma conta não sai dali
  (`SaqzNavHost.kt`). Contorno: recuperar a senha deslogado (PROD-AC06) ou reabrir o app.
- **PC-05 · "Salvar rascunho".** No erro ao criar grupo, "Salvar rascunho" só fecha a tela e descarta
  os dados (`GroupSetupViewModel.onSaveDraft`).
- **PC-06 · Aba Grupos.** Os cartões não mostram a presença da pessoa e o cabeçalho sempre diz
  "0 pedindo confirmação" (`GroupListNextGame.kt` ignora a resposta da API).
- **PC-07 · QR do convite.** A tela do QR diz "O convite vale por 7 dias", mas os links novos não
  expiram.
- **PC-08 · "Recado para a galera".** O campo do editor de jogo é gravado, mas não aparece em nenhuma
  tela nem notificação.
- **PC-09 · Admin durante o teste grátis.** Promover ou rebaixar responde 403 enquanto o teste está
  ativo, e a lista troca para "Você não tem acesso a este grupo". Testar com plano pago.
- **PC-10 · Lista de membros desatualizada.** Depois de "Editar jogador" ou de aprovar um pedido, a
  lista só atualiza saindo e entrando de novo.
- **PC-11 · Aprovação de pedido.** "Pedido enviado" promete aviso quando aceito, mas ninguém é
  avisado, e o aprovado não passa por "Cadastro de atleta". O horário do pedido aparece em UTC.
- **PC-12 · Permissão no Android.** No Android 13+ o app pede permissão de notificação uma vez só por
  instalação; quem recusou precisa ligar nos ajustes do sistema.
- **PC-13 · Extrato.** Mostra só o mês atual; o seletor de período existe só na "Caixa geral".
- **PC-14 · Recibos do Play.** A API do Google não devolve preço, e os recibos aparecem com "—".
- **PC-15 · Remover sem confirmação.** Na lista de membros, "Remover do grupo" age na hora. Em
  "Editar jogador" a remoção pede confirmação.
- **PC-16 · Termos de recebimentos.** `saqz.app/termos/recebimentos/` busca os termos na API de staging.
