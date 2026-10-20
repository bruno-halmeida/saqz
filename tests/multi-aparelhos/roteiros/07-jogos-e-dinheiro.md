# Roteiro 07 · Jogos e dinheiro (editar, publicar, cancelar, quadra, valor, horários, cobranças)

Regras do cofre cobertas: JOGO-003, JOGO-004, JOGO-005, JOGO-007, JOGO-008, JOGO-009, FIN-001, FIN-002, FIN-004, FIN-005, FIN-008.
Tabela de um aparelho com as mesmas regras: `tests/roteiros/jogos.md` e `tests/roteiros/financeiro.md` (as colunas iOS e Android daqui são o contrato de resultado).
Se a nota do cofre e o app divergirem, vale a nota: registre o texto que apareceu.

Papéis: **ORG** (dono), **ATL** (avulso), **ATL2** (avulso), **ATL3** (mensalista). Contas, senha, celulares e nomes iguais ao
[roteiro 01](01-dono-e-atletas.md): `<data>` = `DD/MM`, `<tag>` = `DDMM`. Os aparelhos de cada rodada estão no fim do arquivo
(Rodada 1: ORG no iPhone, ATL no Android; Rodada 2: invertido).

Atalhos usados nas colunas **Banco** (rode no Mac):

```sh
PSQL='docker exec saqz-multidev-db psql -U saqz -d saqz -Atc'
G="(select id from access_groups where name='QA Vôlei <data>')"
U() { echo "(select id from access_users where display_name='QA $1 <data>')"; }   # U Atl, U Atl2, U Atl3, U Org
```

Os passos que chamam um aparelho de "reabrir a tela" querem dizer: trocar de aba Grupos → Início e voltar, ou abrir "Ver o jogo" de novo
(as telas não recarregam sozinhas; sem push no stack local, README da pasta).

## Preparação (uma vez por rodada)

Banco limpo (`stack.sh reset` + `stack.sh up`, app limpo nos 4 aparelhos), depois **passos 1 a 8 do roteiro 01** (contas, grupo
`QA Vôlei <data>`, ATL, ATL2 e ATL3 dentro). Em seguida:

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| P1 | banco | ATL3 vira mensalista: `update group_memberships set membership_type='MENSALISTA' where group_id=$G and user_id=$(U Atl3)` | — | `select user_id,membership_type from group_memberships where group_id=$G` → só ATL3 `MENSALISTA` | ☐ | ☐ |
| P2 | banco | `ATL` e `ATL2` seguem `AVULSO` (padrão). Não há jogo no grupo | — | `select count(*) from games where group_id=$G` = `0` | ☐ | ☐ |

---

## JOGO-007 · Local e quadra [MULTI]

Primeiro jogo do grupo, que ainda não tem quadra padrão.

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | ORG | Início → "Marcar jogo"; Data amanhã; Horário `19:00`; deixar "Quadra" em branco e o endereço em branco; "Marcar jogo" | Erro na folha: `Confira as informações` · `Corrija … para continuar.` e no campo `Informe o nome da quadra.`; nada é criado | `select count(*) from games where group_id=$G` = `0` | ☐ | ☐ |
| 2 | ORG | Quadra `Quadra QA`; endereço `abc` (3 caracteres) | No campo do endereço: `O endereço, se informado, precisa de pelo menos 5 caracteres.` | — | ☐ | ☐ |
| 3 | ORG | Apagar o endereço (vazio) e "Marcar jogo" | Jogo criado, sem erro (endereço em branco é aceito) | `select venue_name,venue_address from games where group_id=$G` → `Quadra QA` e endereço `` (vazio; a coluna é NOT NULL) | ☐ | ☐ |
| 4 | banco | Conferir que a quadra do jogo virou a do grupo | — | `select v.name from access_groups g join group_venues v on v.id=g.default_venue_id where g.id=$G` = `Quadra QA` | ☐ | ☐ |
| 5 | ATL | Reabrir a tela; abrir o jogo ("Ver o jogo") | `Quadra QA`; sem botão `Ver no mapa` (não há endereço) | — | ☐ | ☐ |
| 6 | ORG | Grupo → "Editar" → mudar a quadra do grupo para `Quadra QA Nova` → salvar | Volta ao grupo | `select v.name from access_groups g join group_venues v on v.id=g.default_venue_id where g.id=$G` = `Quadra QA Nova`; `select venue_name from games where group_id=$G` segue `Quadra QA` | ☐ | ☐ |
| 7 | ATL2 | Reabrir a tela e abrir o jogo | Segue `Quadra QA` (jogo antigo mantém a quadra antiga) | — | ☐ | ☐ |
| 8 | ORG | "Marcar jogo" novo (depois de 22:00 do primeiro, para não bater com ele; veja JOGO-009) sem trocar a quadra | `Usamos a quadra do grupo. Você pode escolher outra só para este jogo.` e a quadra vem `Quadra QA Nova` | — | ☐ | ☐ |
| 9 | ORG | Cancelar esse segundo jogo antes de salvar (sair do editor sem "Marcar jogo") | Volta sem criar | `select count(*) from games where group_id=$G` = `1` | ☐ | ☐ |
| 10 | ATL (permissão) | Início/grupo do atleta | Não existe "Marcar jogo" nem "Editar jogo" para ATL | — | ☐ | ☐ |

## JOGO-008 · Valor do jogo (e recado removido) [MULTI]

Jogo de teste para valor: o mesmo jogo `Quadra QA` do passo anterior, que vira o **J1** da suíte (amanhã 19:00).

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | ORG | Abrir o J1 → "Editar jogo"; "Valor por jogo (opcional)" vazio; "Salvar alterações" | Salvo sem erro | `select game_fee_cents from games where group_id=$G` = vazio | ☐ | ☐ |
| 2 | ATL | Reabrir a tela; no cartão do J1 tocar "Vou" | `Você está confirmado.` (sem cobrança) | `select count(*) from group_charges where group_id=$G` = `0` | ☐ | ☐ |
| 3 | ORG | "Editar jogo"; valor `0,00`; "Salvar alterações" | Erro no campo: `Informe um valor maior que R$ 0,00.`; o jogo não muda | `select game_fee_cents from games…` segue vazio | ☐ | ☐ |
| 4 | ORG | Valor `20,00`; salvar | Salvo | `select game_fee_cents from games where group_id=$G` = `2000` | ☐ | ☐ |
| 5 | ATL2 | Reabrir a tela; abrir o J1 | Aparece o valor `R$ 20,00` e, junto do "Vou", `Ao confirmar, a cobrança deste jogo será gerada.` | — | ☐ | ☐ |
| 6 | ORG | Procurar no editor o campo "Recado": não existe (campo removido em 17/10) | Nenhum campo de recado nem o texto "aparece no convite do jogo" | — | ☐ | ☐ |
| 7 | banco (app antigo) | Simular recado: `update games set notes='recado velho' where group_id=$G` e reabrir o J1 em ORG e ATL | Nenhum dos dois mostra `recado velho` em lugar nenhum (aceito e ignorado) | `select notes from games where group_id=$G` = `recado velho` | ☐ | ☐ |

Observação: a confirmação de ATL no passo 2 foi feita antes de existir valor; a diária dele **não** nasce depois (FIN-004, "Valor do jogo muda depois").

## JOGO-003 · Editar o jogo [MULTI] [push: só produção]

J1 já tem valor `20,00`, ATL confirmado, nenhum outro.

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | ATL2 | "Vou" no J1 | `Você está confirmado.` (ATL2 avulso, 1 de 12 vagas) | `select member_display_name,status from game_attendance order by created_at` → ATL e ATL2 `CONFIRMED` | ☐ | ☐ |
| 2 | ATL3 | "Não vou" no J1 | `Tudo bem, na próxima.` (ATL3 não deve receber o aviso do passo 4) | `select status from game_attendance where member_display_name='QA Atl3 <data>'` = `DECLINED` | ☐ | ☐ |
| 3 | ORG | J1 → "Editar jogo"; mudar **só o horário** para `20:00`; "Salvar alterações" | Volta ao jogo com `20:00` | `select local_time from games where group_id=$G` = `20:00:00` | ☐ | ☐ |
| 4 | ATL | Reabrir a tela; abrir "Notificações" (sino da Início) | Aviso novo `O jogo de … mudou:` com o novo horário `20:00` (e o local) | `select body from group_messages where group_id=$G and game_id is not null order by created_at desc limit 1` contém `20:00` | ☐ | ☐ |
| 5 | ATL2 | Reabrir a tela; abrir "Notificações" | O mesmo aviso de mudança | `select count(*) from group_notifications where recipient_id=$(U Atl2)` aumentou em 1 | ☐ | ☐ |
| 6 | ATL3 | Abrir "Notificações" | **Sem** o aviso de mudança (quem disse "Não vou" não recebe) | `select count(*) from group_notifications n join group_messages m on m.id=n.message_id where n.recipient_id=$(U Atl3) and m.game_id is not null` sem o aviso novo | ☐ | ☐ |
| 7 | ORG | Abrir a lista do J1 | Presenças mantidas: ATL e ATL2 `Confirmados`; ATL3 em `Não vão` | — | ☐ | ☐ |
| 8 | ORG | Mudar só o **valor** (`25,00`) e salvar | Salvo; **nenhum** aviso novo (vagas, valor e recado não avisam) | `select count(*) from group_messages where group_id=$G` não muda | ☐ | ☐ |
| 9 | ATL | Reabrir a tela e abrir o J1 | Segue `Você está confirmado.`; a diária dele (se existir) segue `20,00` (FIN-004) | `select amount_cents from group_charges where member_user_id=$(U Atl2)` = `2000` | ☐ | ☐ |
| 10 | ORG | Mudar só o **local** (quadra do jogo para `Quadra QA B`) e salvar | Salvo; aviso de mudança com o novo local nas Notificações de ATL e ATL2 | `select count(*) from group_notifications where recipient_id=$(U Atl)` +1 | ☐ | ☐ |
| 11 | ORG (conflito de versão) | No ORG, abrir "Editar jogo" e **não salvar**. No outro aparelho de organizador (ver Rodada: promover ATL a administrador, `Tornar administrador` em Membros), editar o horário para `20:30` e salvar. Voltar ao ORG e salvar com o editor que já estava aberto | O ORG recebe conflito: `Não foi possível salvar o jogo` · `Tente novamente.` (ou o aviso de jogo atualizado); recarregar mostra `20:30` | `select local_time,version from games where group_id=$G` = `20:30:00` | ☐ | ☐ |
| 12 | ATL3 (permissão) | Abrir o J1 | Sem o botão "Editar jogo" (atleta) | — | ☐ | ☐ |
| 13 | banco + ORG (depois do início) | `update games set starts_at=now()-interval '10 minutes' where group_id=$G` (a hora local acompanha) e ORG tenta "Editar jogo" | Sem edição (botão ausente ou recusa) | `select status from games` segue `PUBLISHED` | ☐ | ☐ |
| 14 | banco | Voltar o horário para amanhã: `update games set starts_at=now()+interval '1 day' where group_id=$G` e reabrir | — | — | ☐ | ☐ |
| 15 | ORG (início no passado) | "Editar jogo"; escolher um horário que já passou hoje (se hoje) | Recusado; o jogo não muda | `select starts_at from games` segue futuro | ☐ | ☐ |
| 16 | ORG (vagas, fila, ordem de chegada) | Dar ao grupo "prioridade de mensalista" desligada (roteiro 01 passo 12). "Editar jogo" → capacidade `2`; ATL e ATL2 seguem confirmados; ATL3 muda para "Vou" e entra na espera (`WAITLISTED`, sequência 1). ORG sobe a capacidade para `4` pelo editor | Sobem os da fila na ordem; ATL3 passa a `Você está confirmado.` | `select member_display_name,status,waitlist_sequence from game_attendance` → ATL3 `CONFIRMED|` (sem fila) | ☐ | ☐ |

> Passos 13 a 15 mexem no relógio pelo banco. Para o passo 15 em horário que já passou, só vale se a hora atual permitir; senão relate BLOQUEADO.

## JOGO-004 · Publicar [CRÍTICO] [MULTI]

Regra: rascunho vira publicado e abre a presença; aviso "O jogo está liberado. Confirme sua presença." só se for o próximo do grupo.

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | ORG + banco | Cancelar o J1 pelo app (JOGO-005 passos 1 e 2 abaixo, ou `update games set status='CANCELLED' where group_id=$G`). O editor não grava rascunho: marcar um jogo novo amanhã 19:00 e transformar em rascunho: `update games set status='DRAFT' where group_id=$G and status='PUBLISHED'` | ORG reabre o jogo: status `Rascunho` e `Só você vê este jogo até publicar.` | `select status from games order by created_at desc limit 1` = `DRAFT` | ☐ | ☐ |
| 2 | ATL | Reabrir a tela | O rascunho **não** aparece em "Próximos jogos" nem em "Ver o jogo" | — | ☐ | ☐ |
| 3 | ATL (permissão) | Tentar publicar (nenhum botão) | Sem `Publicar jogo` para atleta | — | ☐ | ☐ |
| 4 | ORG | Abrir o rascunho; ver `Só você vê este jogo até publicar.`; "Publicar jogo" | Status `Aberto para confirmações` | `select status from games where id='<jogo>'` = `PUBLISHED` | ☐ | ☐ |
| 5 | ATL | Reabrir a tela e abrir "Notificações" | Cartão `PRÓXIMO JOGO` com `Vou` e `Não vou`; aviso `O jogo está liberado. Confirme sua presença.` na central (é o próximo do grupo) | `select body from group_messages where group_id=$G and channel='REMINDER' order by created_at desc limit 1` = `O jogo está liberado. Confirme sua presença.` (ou o texto do banco) | ☐ | ☐ |
| 6 | ATL2 | Mesma conferência | Mesmo aviso e cartão | — | ☐ | ☐ |
| 7 | ORG (já publicado) | Abrir o jogo publicado | Sem botão `Publicar jogo` (transição inválida) | — | ☐ | ☐ |
| 8 | banco + ORG (início passado) | Criar rascunho e `update games set starts_at=now()-interval '1 hour' where id='<rascunho 2>'`; ORG reabre o jogo e toca "Publicar jogo" | Recusado: `Não foi possível publicar o jogo. Tente novamente.` e o status segue `DRAFT` | `select status from games where id='<rascunho 2>'` = `DRAFT` | ☐ | ☐ |
| 9 | ORG (não é o próximo) | Criar rascunho para **daqui a 3 dias** 19:00 e publicar | Publicado; **sem** aviso novo na central de ATL (o próximo do grupo ainda é o de amanhã) | `select count(*) from group_messages where group_id=$G and channel='REMINDER'` não aumenta | ☐ | ☐ |

## JOGO-005 · Cancelar [CRÍTICO] [MULTI] [push: só produção]

Com o jogo de amanhã publicado, ATL (avulso) e ATL2 (avulso) confirmados com diária pendente, ATL3 (mensalista) na espera ou sem resposta, ORG com um rascunho.
Se o jogo ainda tem diária, o ORG dá "Recebi" a ATL2 antes (FIN-002) para ter uma cobrança paga.

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | ORG | Jogo de amanhã → "Cancelar jogo" | Folha `Cancelar o jogo?` · `Os confirmados recebem um aviso e o jogo some da agenda. Isso não pode ser desfeito.` | — | ☐ | ☐ |
| 2 | ORG | Confirmar o cancelamento | Status `Cancelado`; aparece o bloco de revisão `Este jogo foi cancelado com cobranças já pagas ou dispensadas. Confira se precisa devolver algum valor.` com `Ver cobranças` | `select status,finance_review_required from games where id='<jogo>'` = `CANCELLED|t` | ☐ | ☐ |
| 3 | ATL | Reabrir a tela; abrir "Notificações" | Aviso de cancelamento com o texto `O jogo de … às … foi cancelado.`; no cartão da Início o jogo some | `select body from group_messages where group_id=$G order by created_at desc limit 1` | ☐ | ☐ |
| 4 | ATL2 | Idem (confirmado) | Mesmo aviso | — | ☐ | ☐ |
| 5 | ATL3 | Abrir "Notificações" (ATL3 estava na espera: recebe; se estava "Não vou", não recebe) | Aviso, só se confirmado ou na espera | `select recipient_count from group_messages where …` = número de confirmados + espera | ☐ | ☐ |
| 6 | ATL | Tentar responder ao jogo cancelado (link de presença antigo ou cartão) | `Esse jogo foi cancelado.` | `select status from game_attendance …` congelado (nenhuma linha muda) | ☐ | ☐ |
| 7 | ORG | Caixa do grupo → cobranças | A diária pendente de ATL sumiu (cancelada); a diária recebida de ATL2 aparece com `Revisar` | `select member_user_id,status,review_required from group_charges where game_id='<jogo>'` → ATL `CANCELLED|f`, ATL2 `PAID|t` | ☐ | ☐ |
| 8 | ATL | Grupo → "Minhas cobranças" | A diária dele aparece como `Cancelada` (histórico), não mais em `Em aberto`; o aviso da Início some | — | ☐ | ☐ |
| 9 | ORG | Cancelar o rascunho do passo preparatório | Cancelado; ninguém recebe aviso | `select count(*) from group_messages where group_id=$G` não muda | ☐ | ☐ |
| 10 | ORG (repetição) | Abrir o jogo já cancelado | Sem botão `Cancelar jogo` | — | ☐ | ☐ |
| 11 | ATL (permissão) | Abrir qualquer jogo | Sem `Cancelar jogo` | — | ☐ | ☐ |
| 12 | ORG (toque duplo) | Num jogo publicado novo, tocar "Cancelar jogo" e confirmar com dois toques rápidos | Um cancelamento só: um aviso na central | `select count(*) from group_messages where group_id=$G and body like '%foi cancelado%'` aumenta em 1 | ☐ | ☐ |

## JOGO-009 · Um jogo por vez no grupo [MULTI]

Estado: nenhum jogo `DRAFT`/`PUBLISHED` no grupo (`update games set status='CANCELLED' where group_id=$G and status in ('DRAFT','PUBLISHED')`).

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | ORG | Marcar jogo daqui a 2 dias 19:00; depois fixar duração em 2h: `update games set duration_minutes=120 where group_id=$G and status='PUBLISHED'` | Jogo criado | `select local_time,duration_minutes from games where group_id=$G and status='PUBLISHED'` = `19:00:00|120` | ☐ | ☐ |
| 2 | ORG | Marcar jogo no mesmo dia **20:00** | Erro `Já existe um jogo nesse horário` com `Outro horário` e `Ver jogo` | `select count(*) from games where group_id=$G and status in ('DRAFT','PUBLISHED')` = `1` | ☐ | ☐ |
| 3 | ORG | `Ver jogo` | Abre o de 19:00 | — | ☐ | ☐ |
| 4 | ORG | Marcar jogo no mesmo dia **21:00** | Criado (encostado: o primeiro termina às 21:00) | `…` = `2` | ☐ | ☐ |
| 5 | ATL | Reabrir a tela | "Próximos jogos" lista 19:00 e 21:00 | — | ☐ | ☐ |
| 6 | ORG | Cancelar o de 21:00 e marcar outro às 21:00 | Permitido (jogo cancelado libera o horário) | `select count(*) from games where group_id=$G and status in ('DRAFT','PUBLISHED') and local_time='21:00'` = `1` | ☐ | ☐ |
| 7 | ORG | Editar o jogo das 21:00 para `20:00` | Conflito (`Já existe um jogo nesse horário`); o horário continua 21:00 | `select local_time from games …` = `21:00:00` | ☐ | ☐ |
| 8 | ORG + ADMIN | Promover ATL a administrador (`Tornar administrador` em Membros). No mesmo minuto, ORG e ATL, cada um no seu editor, criam jogo no mesmo horário novo (daqui a 4 dias 19:00): preencher os dois, tocar "Marcar jogo" quase juntos | Um passa; o outro recebe `Já existe um jogo nesse horário` | `select count(*) from games where group_id=$G and local_date=<dia> and status in ('DRAFT','PUBLISHED')` = `1` | ☐ | ☐ |
| 9 | ATL (admin) | Rebaixar ATL de volta a atleta (Membros → Rebaixar) para os próximos roteiros | — | `select role from group_memberships where user_id=$(U Atl)` = `ATHLETE` | ☐ | ☐ |
| 10 | banco | Conferir que a série (JOGO-010) respeita o horário: não há outro teste neste roteiro; basta o passo 2 do roteiro 02 | — | — | ☐ | ☐ |

---

## FIN-004 · A diária do jogo [CRÍTICO] [MULTI]

Estado: nenhum jogo ativo; `update games set status='CANCELLED' where group_id=$G and status in ('DRAFT','PUBLISHED')`. ORG marca o **J2** daqui a 3 dias às 19:00, valor `20,00`, capacidade `2`, prioridade de mensalista **ligada** (padrão). Contas ATL, ATL2 avulsas; ATL3 mensalista; ORG é dono (conta de organizador, sem diária).

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | ATL | "Vou" no J2 | `Você está confirmado.` | `select status,amount_cents,due_date from group_charges where member_user_id=$(U Atl) and game_id='<J2>'` = `PENDING|2000|<data do jogo>` | ☐ | ☐ |
| 2 | ATL | Grupo → "Minhas cobranças" | Em `Em aberto`: `Jogo avulso` · `Vence em <data do jogo>` · `R$ 20,00` e o botão de copiar a chave Pix, se o grupo tiver Pix | — | ☐ | ☐ |
| 3 | ATL3 (mensalista) | "Vou" no J2 | `Você está confirmado.` (ou na espera, conforme a prioridade); **sem** cobrança | `select count(*) from group_charges where member_user_id=$(U Atl3) and game_id='<J2>'` = `0` | ☐ | ☐ |
| 4 | ATL2 | "Vou" no J2 (já tem 2 confirmados: cheio) | `Você entrou na lista de espera.` | `select count(*) from group_charges where member_user_id=$(U Atl2) and game_id='<J2>'` = `0` | ☐ | ☐ |
| 5 | ATL | "Alterar" → "Não vou" | `Sua vaga foi liberada.` | ATL2 vira `CONFIRMED` (ordem de chegada) | ☐ | ☐ |
| 6 | ATL2 | Reabrir a tela; "Minhas cobranças" | Diária nasceu na promoção: `Jogo avulso` · `R$ 20,00` · `Em aberto` | `select status from group_charges where member_user_id=$(U Atl2) and game_id='<J2>'` = `PENDING` | ☐ | ☐ |
| 7 | ORG | Caixa do grupo → ver cobranças pendentes | A de ATL aparece com `Desistiu` (a de ATL2 não); a de ATL segue pendente | `select status from group_charges where member_user_id=$(U Atl) and game_id='<J2>'` = `PENDING` (desistência não cancela a diária; FIN-002 decide) | ☐ | ☐ |
| 8 | ATL3 | Levar convidado: "Levar convidado", nome `Convidado QA`, confirmar | O convidado entra na lista (ou na espera) e `Se o convidado entrar no jogo, a taxa de R$ 20,00 vem para você.` | — | ☐ | ☐ |
| 9 | banco | Subir a capacidade para 4 (`update games set capacity=4 where id='<J2>'`) e reabrir; o convidado deve estar confirmado | — | `select guest_seq,status from game_attendance where game_id='<J2>' and member_user_id=$(U Atl3)` → `0|CONFIRMED`, `1|CONFIRMED` | ☐ | ☐ |
| 10 | ATL3 | Grupo → "Minhas cobranças" | Uma cobrança `Convidado: Convidado QA` · `R$ 20,00` · `Em aberto`; a diária própria não existe | `select guest_seq… from group_charges` (ou conferir: `select count(*) from group_charges where member_user_id=$(U Atl3) and game_id='<J2>'` = `1`) | ☐ | ☐ |
| 11 | ATL3 | Tirar o convidado (cartão do jogo → convidado → remover) | `Sai dos confirmados, a vaga vai para o próximo da lista e a cobrança de R$ 20,00 em seu nome é cancelada.` (confirmar) | `select status from group_charges where member_user_id=$(U Atl3) and game_id='<J2>'` = `CANCELLED` | ☐ | ☐ |
| 12 | ORG | Mudar o valor do J2 para `30,00` (JOGO-008) e ATL2 sai e volta ("Não vou" → "Vou") | A diária antiga de ATL2 segue `R$ 20,00` e a nova de quem confirmar depois nasce `R$ 30,00` | `select amount_cents from group_charges where game_id='<J2>' order by created_at` → `2000`, `2000`… e a nova `3000` | ☐ | ☐ |
| 13 | ORG | Marcar jogo **sem valor** (J3, daqui a 5 dias) e ATL confirma | Sem cobrança | `select count(*) from group_charges where game_id='<J3>'` = `0` | ☐ | ☐ |
| 14 | ORG (cancelado, pendente) | Cancelar o J2 (JOGO-005): pendentes viram canceladas, as pagas ficam | Diárias pendentes `Cancelada`; qualquer `PAID` com `Revisar` | `select status,review_required from group_charges where game_id='<J2>'` | ☐ | ☐ |

## FIN-001 · As cobranças: tipos, estados e quem vê [CRÍTICO] [MULTI]

Estado: um jogo novo **J4** com valor `20,00`, ATL e ATL2 avulsos confirmados (duas diárias pendentes); ATL3 com uma mensalidade criada pelo organizador ("Criar cobranças do mês" no caixa, `Criar cobranças`, valor `50,00`).

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | ORG | Grupo → "Caixa do grupo" | Vê **as três** cobranças (ATL, ATL2, ATL3) e totais (`SALDO DO GRUPO`, a receber) | `select member_user_id,kind,status from group_charges where group_id=$G` → 3 linhas `PENDING` | ☐ | ☐ |
| 2 | ATL | Grupo → "Minhas cobranças" | Só a diária dele em `Em aberto`; nenhum nome de outra pessoa nem totais | — | ☐ | ☐ |
| 3 | ATL3 | "Minhas cobranças" | `Mensalidade · <mês>` · `R$ 50,00`; a diária de ATL e ATL2 não aparece | — | ☐ | ☐ |
| 4 | ATL (permissão) | Tentar abrir "Caixa do grupo" | O item não existe para atleta (backend devolve 403; `Só quem administra o grupo mexe em lançamentos.` se chegar) | — | ☐ | ☐ |
| 5 | pessoa de fora | Conta sem ser membro (criar `qa-fora+<tag>@saqz.local`) abre o grupo | Sem acesso: não vê o grupo nem cobranças | — | ☐ | ☐ |
| 6 | ORG | Dispensar a diária de ATL2 (`Dispensar` → `Dispensar`), depois "Desfazer" | Estado muda para `Dispensada` e volta a `Em aberto` | `select status from group_charges…` `WAIVED` → `PENDING` | ☐ | ☐ |
| 7 | ORG | Cobrança cancelada (de um jogo cancelado, FIN-004 passo 14) | Sem botão de desfazer na cobrança `Cancelada` | `select status from group_charges…` segue `CANCELLED` | ☐ | ☐ |
| 8 | ATL, ATL2 | Reabrir "Minhas cobranças" depois de cada mudança do ORG | Mostra o estado novo (`Paga`, `Isenta`, `Em aberto`, `Cancelada`) | — | ☐ | ☐ |

## FIN-002 · Dar baixa: Recebi, Dispensar e Cancelar [CRÍTICO] [MULTI]

Estado: as duas diárias de J4 pendentes (ATL, ATL2).

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | ORG | Caixa do grupo → diária de ATL → `Recebi`; folha `Confirmar recebimento`; **sem mexer** em "Como recebeu"; `Recebi` | Cobrança `Recebida`; sem pergunta extra | `select status,paid_method from group_charges where member_user_id=$(U Atl) and game_id='<J4>'` = `PAID|PIX` | ☐ | ☐ |
| 2 | ATL | Reabrir "Minhas cobranças" | `Paga` no `Histórico`; o aviso da Início `Você tem R$ 20,00 em aberto` some | — | ☐ | ☐ |
| 3 | ORG | Diária de ATL2 → `Recebi` → "Como recebeu" `Dinheiro` → `Recebi` | `Recebida` | `select paid_method from group_charges where member_user_id=$(U Atl2) and game_id='<J4>'` = `CASH` | ☐ | ☐ |
| 4 | ORG | Na mesma cobrança já recebida, tentar `Recebi` de novo | Sem o botão `Recebi` (só `Desfazer`); se chegar ao servidor: recusado | `select status…` segue `PAID` | ☐ | ☐ |
| 5 | ORG | "Desfazer" na de ATL2 com motivo `x` (1 letra) | `Use pelo menos 2 letras.` | — | ☐ | ☐ |
| 6 | ORG | "Desfazer" na de ATL2 com motivo `erro de lançamento` | Volta a `Em aberto`; o valor sai do caixa (`SALDO DO GRUPO` cai R$ 20,00) | `select status from group_charges…` = `PENDING`; `select note from group_charge_events order by occurred_at desc limit 1` = `erro de lançamento` | ☐ | ☐ |
| 7 | ATL2 | Reabrir "Minhas cobranças" | `Em aberto` outra vez | — | ☐ | ☐ |
| 8 | ORG | Cancelar uma pendente (ATL3 mensalidade): `Cancelar cobrança` (`A cobrança sai do que falta receber e não entra no caixa. A pessoa não recebe aviso. Não dá para desfazer.`) | `Cancelada` | `…` = `CANCELLED`; sem linha nova em `group_notifications` para ATL3 | ☐ | ☐ |
| 9 | ORG | Desfazer na cobrança cancelada | Não há botão; recusado | — | ☐ | ☐ |
| 10 | ATL (permissão) | Procurar `Recebi`/`Dispensar` nas cobranças dele | Nenhum botão de baixa | — | ☐ | ☐ |
| 11 | ORG + ADMIN (conflito) | Promover ATL a admin (`Tornar administrador`). Os dois abrem a mesma cobrança pendente (ATL2). ADMIN dá `Recebi`; ORG, ainda na folha aberta, dá `Dispensar` | Um passa; o outro recebe erro/conflito (`Não foi possível dispensar a cobrança. Tente novamente.`) e, ao recarregar, vê o estado do primeiro | `select status from group_charges…` = só um dos estados | ☐ | ☐ |
| 12 | ATL (volta a atleta) | Rebaixar ATL | — | `select role…` = `ATHLETE` | ☐ | ☐ |
| 13 | ORG | Nota com 1 caractere: no `Desfazer` e na baixa que aceitar nota, digitar `a` | Recusada (`Use pelo menos 2 letras.`) | — | ☐ | ☐ |

> A nota só existe no "Desfazer" no app atual; a baixa por `Recebi` e por `Dispensar` não tem campo de nota (registrar se a folha mostrar).

## FIN-005 · "Cobrar": o lembrete ao devedor [MULTI] [push: só produção]

Estado: duas diárias pendentes (ATL e ATL2), uma mensalidade pendente (ATL3). O push em si só em produção; aqui a **central de avisos** é a conferência.

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | ORG | Caixa do grupo → `Cobrar` (ou `Cobrar quem falta`); folha `Escolha quem vai receber a cobrança`; marcar só ATL; `Enviar (1)` | `Avisos enviados: 1` | `select count(*) from group_messages where charge_id is not null` = `1` | ☐ | ☐ |
| 2 | ATL | Reabrir a tela; abrir "Notificações" | Aviso `Você recebeu um lembrete de cobrança.` (push em produção) | `select count(*) from group_notifications where recipient_id=$(U Atl)` +1 | ☐ | ☐ |
| 3 | ORG | Cobrar ATL de novo logo em seguida | A pessoa aparece como `Já cobrado hoje` na folha e não envia; mensagem com o tempo restante se chegar a enviar | `select count(*) from group_messages where charge_id is not null` segue `1` | ☐ | ☐ |
| 4 | banco | Passar 13 h: `update group_messages set created_at=created_at-interval '13 hours' where charge_id is not null` | — | — | ☐ | ☐ |
| 5 | ORG | Cobrar ATL de novo | `Avisos enviados: 1` | `select count(*) from group_messages where charge_id is not null` = `2` | ☐ | ☐ |
| 6 | ORG | Cobrar uma cobrança recebida (dar `Recebi` em ATL2 antes) | A cobrança recebida não aparece na lista de seleção | — | ☐ | ☐ |
| 7 | ORG | Cobrar ATL2 sem chave Pix no grupo | Aviso `Cadastre o Pix do grupo em Editar grupo` (FIN-008) | — | ☐ | ☐ |
| 8 | ATL (permissão) | Procurar o botão "Cobrar" | Não existe | — | ☐ | ☐ |
| 9 | ORG (convidado) | Cobrar a cobrança do convidado de ATL3 (se existir) | O aviso vai para ATL3 (anfitrião); ATL3 vê na central | `select recipient_id…` = ATL3 | ☐ | ☐ |

## FIN-008 · O que o atleta vê [MULTI]

Estado: J4 com valor `20,00`, ATL e ATL2 confirmados (duas pendentes), ATL3 com mensalidade e um convidado cobrado; grupo sem chave Pix.

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | ATL | Reabrir Início | Banner `Você tem R$ 20,00 em aberto` (não dá para dispensar) | — | ☐ | ☐ |
| 2 | ATL2 | Reabrir Início e dar a segunda cobrança (outro jogo, `20,00`) | Banner `Você tem R$ 40,00 em aberto` (ou `… em N grupos`); `2 cobranças em aberto` na lista | — | ☐ | ☐ |
| 3 | banco | Deixar uma vencida: `update group_charges set due_date=current_date-3 where id='<cobrança de ATL>'` | — | — | ☐ | ☐ |
| 4 | ATL | Reabrir "Minhas cobranças" | `Venceu em <data>` na cobrança e o banner destaca o atraso | — | ☐ | ☐ |
| 5 | ORG | `Recebi` nas duas de ATL | Sem pendências | — | ☐ | ☐ |
| 6 | ATL | Reabrir Início e "Minhas cobranças" | Sem banner; as duas em `Histórico` como `Paga` | — | ☐ | ☐ |
| 7 | ATL (caixa) | Procurar a "Caixa do grupo" | Não existe para o atleta (backend 403) | — | ☐ | ☐ |
| 8 | ATL3 | Início → "Minhas cobranças" | `Convidado: Convidado QA` no nome do anfitrião | — | ☐ | ☐ |
| 9 | ORG | Com cobrança pendente e o grupo sem Pix, abrir o caixa/`Cobrar` | `Cadastre o Pix do grupo em Editar grupo` | — | ☐ | ☐ |
| 10 | ORG | Cadastrar o Pix do grupo (Editar grupo → "Pix do grupo": "Tipo de chave" `E-mail`, chave `pix@qa.test`) | O aviso some | `select pix_key, pix_label from access_groups where id='$GID'` = `pix@qa.test\|E-mail` | ☐ | ☐ |
| 11 | ATL | Reabrir "Minhas cobranças" | `Pagamento é manual: copie a chave Pix e pague fora do app — a baixa acontece no caixa do grupo.` e botão de copiar | — | ☐ | ☐ |

---

## Rodadas

**Rodada 1: ORG no iPhone, ATL no Android.**
ORG = iPhone 17, ATL = `Saqz_QA_A`, ATL2 = iPhone 17e, ATL3 = `Saqz_QA_B`. Roteiro inteiro (JOGO-007, 008, 003, 004, 005, 009; FIN-004, 001, 002, 005, 008).

**Rodada 2: ORG no Android, ATL no iPhone.**
Reset do stack (`stack.sh reset` + `up`), apps limpos, `<tag>` novo. ORG = `Saqz_QA_A`, ATL = iPhone 17, ATL2 = `Saqz_QA_B`, ATL3 = iPhone 17e.
Rodar de novo os passos de **todas** as regras e marcar a coluna da plataforma do ORG; a coluna da plataforma de ATL, ATL2 e ATL3 marca o que cada atleta viu. Em cada passo, marque iOS ou Android conforme o aparelho que **executou o "Faça"**.

No relato, informe por rodada: tempo total, passos em que usou adb/simctl, o que ficou BLOQUEADO (push em produção, passos que dependem de coluna que a consulta não achou) e as divergências entre nota e app (ver README dos roteiros).
