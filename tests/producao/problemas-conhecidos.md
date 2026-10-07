# Problemas conhecidos em produção

Defeitos que já conhecemos, com a decisão tomada para cada um. Um cenário de teste que esbarrar
num item daqui aponta para ele em vez de reprovar de novo o mesmo defeito. Quando um item for
corrigido, ele sai desta lista no mesmo commit da correção.

| ID | Problema | Decisão | Desde |
| --- | --- | --- | --- |
| [PC-01](#pc-01--em-grupo-novo-todo-vou-vai-para-a-lista-de-espera) | Em grupo novo, todo "Vou" vai para a lista de espera | Risco aceito por enquanto | 07/10/2026 |
| [PC-02](#pc-02--cancelar-jogo-e-liberar-vaga-da-espera-não-avisam-ninguém) | Cancelar jogo e liberar vaga da espera não avisam ninguém | Fica sem aviso por enquanto | 07/10/2026 |

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
