# Problemas conhecidos em produção

Defeitos que já conhecemos, com a decisão tomada para cada um. Um cenário de teste que esbarrar
num item daqui aponta para ele em vez de reprovar de novo o mesmo defeito. Quando um item for
corrigido, ele sai desta lista no mesmo commit da correção e passa para "Corrigidos", no fim, com o
commit.

"A triar" é achado confirmado no levantamento de 07/10/2026 que ainda espera a decisão de corrigir,
aceitar ou só documentar.

| ID | Problema | Decisão | Desde |
| --- | --- | --- | --- |
| [PC-01](#pc-01--em-grupo-novo-todo-vou-vai-para-a-lista-de-espera) | Em grupo novo, o "Vou" do avulso vai para a lista de espera até o prazo | Risco aceito por enquanto | 07/10/2026 |
| [PC-02](#pc-02--quem-sobe-da-lista-de-espera-não-recebe-aviso-individual) | Quem sobe da lista de espera não recebe aviso individual (cancelar jogo já avisa) | Decisão do dono: a lista vai ao grupo do WhatsApp a cada 3 h | 07/10/2026 |
| [PC-03](#pc-03-a-pc-16--achados-abertos) | Foto do perfil só aparece para o dono | A triar | 07/10/2026 |
| PC-05 | "Salvar rascunho" ao criar grupo não salva nada | A triar | 07/10/2026 |
| PC-06 | Aba Grupos sem chip de presença e "0 pedindo confirmação" fixo | A triar | 07/10/2026 |
| PC-08 | "Recado para a galera" do jogo não aparece para ninguém | A triar | 07/10/2026 |
| PC-11 | Quem teve o pedido de entrada aprovado não recebe aviso individual | Decisão do dono: nenhum texto promete aviso | 07/10/2026 |
| PC-12 | Android 13+ pede permissão de notificação uma vez só por instalação | A triar | 07/10/2026 |
| PC-13 | Extrato do grupo sem seletor de mês | A triar | 07/10/2026 |
| PC-14 | Recibos do Google Play aparecem com valor "—" | A triar | 07/10/2026 |
| PC-16 | "Termos de recebimentos" no site lê os termos de staging | A triar | 07/10/2026 |

## PC-01 · Em grupo novo, todo "Vou" vai para a lista de espera

**O que a pessoa vê.** O organizador cria o grupo e o jogo, a galera toca "Vou" e todo avulso
entra na lista de espera, mesmo com vaga sobrando, até o prazo de confirmação. A Início mostra "Os
mensalistas entram primeiro." O próprio dono, enquanto for avulso, também vai para a espera.

**Por quê.** Todo membro nasce avulso (`membership_type` padrão `AVULSO`) e a "Prioridade de
mensalista" nasce ligada (`access_groups.mensalista_priority` padrão `true`). Com prioridade
ligada, avulso que responde "Vou" vai para a espera mesmo com vaga
(`Attendance.confirmationTarget`, em `backend/features/groups/.../domain/attendance/Attendance.kt`).
É a regra decidida pelo dono em 13/10: o "Vou" segue a configuração do grupo, não a existência de
mensalistas, e a prioridade continua nascendo ligada. Depois do prazo, a espera anda pela ordem de
chegada (ou pela escolha do organizador, no modo manual). Dono e admin já podem ser mensalistas
(`fcb6e619d`).

**Contorno para o organizador.**
- Desligar "Prioridade de mensalista" em Editar grupo → "Como preencher as vagas"; ou
- tornar os atletas (e o dono) mensalistas em Membros e permissões → Editar jogador → "Tornar mensalista"; ou
- abrir o jogo e salvar "Ajustar vagas" ou usar "Colocar no jogo" para puxar quem está na espera.

**Nos testes de produção.** Antes de testar presença, aplicar um dos contornos e anotar qual.

## PC-02 · Quem sobe da lista de espera não recebe aviso individual

**O que o app não faz.** Quando a vaga abre, a pessoa da espera é promovida, mas não recebe push nem
notificação no app. Decisão do dono (13/10): nenhum texto promete esse aviso, e a lista de convocados
vai ao grupo do WhatsApp vinculado a cada 3 h, das 8h às 22h, até o início do jogo. O card da reserva diz
"Se você subir, aparece aqui como confirmado. Confira antes do jogo."

**Já corrigido.** Cancelar um jogo publicado avisa confirmados e espera, na central, no push e no grupo
do WhatsApp vinculado (`b6b86f7eb`).

**Impacto.** Quem foi promovido pode não saber que tem vaga até abrir o app ou ver a lista no WhatsApp.

**Contorno.** O organizador avisa a galera por fora (Avisos do grupo ou WhatsApp).

**Nos testes de produção.** Conferir só o estado no app (promovido aparece como confirmado). A ausência de
aviso individual não reprova o cenário enquanto este item estiver aqui.

## PC-03 a PC-16 · Achados abertos
- **PC-03 · Foto do perfil só para o dono.** A foto é servida só em `/api/session/photo`; os outros
  membros veem as iniciais, embora a dica diga "Aparece nas listas de presença e nos grupos.".
- **PC-05 · "Salvar rascunho".** No erro ao criar grupo, "Salvar rascunho" só fecha a tela e descarta
  os dados (`GroupSetupViewModel.onSaveDraft`).
- **PC-06 · Aba Grupos.** Os cartões não mostram a presença da pessoa e o cabeçalho sempre diz
  "0 pedindo confirmação" (`GroupListNextGame.kt` ignora a resposta da API).
- **PC-08 · "Recado para a galera".** O campo do editor de jogo é gravado, mas não aparece em nenhuma
  tela nem notificação.
- **PC-11 · Aprovação de pedido.** Quem teve o pedido aprovado não recebe aviso individual, por decisão do
  dono; o texto de "Pedido enviado" já não promete aviso. O cadastro de atleta agora abre ao pedir entrada.
- **PC-12 · Permissão no Android.** No Android 13+ o app pede permissão de notificação uma vez só por
  instalação; quem recusou precisa ligar nos ajustes do sistema.
- **PC-13 · Extrato.** Mostra só o mês atual; o seletor de período existe só na "Caixa geral".
- **PC-14 · Recibos do Play.** A API do Google não devolve preço, e os recibos aparecem com "—".
- **PC-16 · Termos de recebimentos.** `saqz.app/termos/recebimentos/` busca os termos na API de staging.

## Corrigidos

Saíram da lista acima. Se um cenário ainda reproduzir o defeito, é regressão: reprova.

| ID | Correção | Commit |
| --- | --- | --- |
| PC-02 (cancelamento) | Cancelar jogo publicado avisa confirmados e espera (central, push e WhatsApp vinculado) | `b6b86f7eb` |
| PC-07 | O QR do convite diz "O convite vale até você trocar o código" | `a99f2389c` |
| PC-09 | Admin liberado durante o teste grátis; plano vencido abre a folha da assinatura | `b6b86f7eb`, `414ee347b` |
| PC-10 | Toda tela, inclusive Membros, recarrega ao voltar a aparecer | `6fe2d98ca` |
| PC-11 (cadastro) | Pedir entrada abre o "Cadastro de atleta" antes da aprovação, aplicado na aprovação; hora do pedido no fuso do grupo | `8a48fdf3d`, `457b21784`, `414ee347b` |
| PC-15 | "Remover do grupo" pede confirmação na lista de membros | `414ee347b` |
| PC-04 | Logado, "Lembrou a senha? Entrar" na recuperação volta para onde a pessoa estava (Perfil) | `4c6868543` |
