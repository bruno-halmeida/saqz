# Roteiro 08 · Avisos e notificações (central, preferências, jogo, pessoas, dinheiro, mural e conversa, WhatsApp)

Regras do cofre cobertas: NOT-001 a NOT-007. Tabela de um aparelho: `tests/roteiros/avisos-e-notificacoes.md`.
Se a nota e o app divergirem, vale a nota: registre o texto que apareceu. Divergências **já conhecidas** (débitos do cofre, esperadas neste estado do app):
DEB-064 (desligar uma chave tira o aviso da central, mas a nota manda a central mostrar tudo), DEB-065 (jogo cancelado e mudança de horário chegam por push mesmo com "Avisos dos grupos" desligado) e DEB-071 (mudança de horário no WhatsApp). Marque ✘ nos passos que dependem delas e anote "DEB-0xx".

O stack local **não tem push** (FCM/APNs). Nas regras com push, a conferência aqui é a **central de avisos** (sino "Notificações" da Início) e o banco; o push em si fica `[push: só produção]`.
O stack liga `saqz.notifications.reminder.enabled`; o WhatsApp é desligado (o Uazapi falso de `ferramentas/uazapi-falso.mjs` registra as mensagens que o backend mandaria, quando ligado; ver README da pasta).

Papéis: **ORG** (dono), **ATL** (avulso), **ATL2** (avulso), **ATL3** (atleta). Contas, senha, celulares e nomes iguais ao [roteiro 01](01-dono-e-atletas.md); `<data>` = `DD/MM`, `<tag>` = `DDMM`.
Preparação: `stack.sh reset` + `up`, apps limpos, **passos 1 a 8 do roteiro 01** (grupo `QA Vôlei <data>`, ATL, ATL2 e ATL3 dentro), e um jogo para amanhã às 19:00 com capacidade 4 (`J1`).

```sh
PSQL='docker exec saqz-multidev-db psql -U saqz -d saqz -Atc'
G="(select id from access_groups where name='QA Vôlei <data>')"
U() { echo "(select id from access_users where display_name='QA $1 <data>')"; }   # U Org, U Atl, U Atl2, U Atl3
N() { $PSQL "select m.channel,left(m.body,60),n.read_at is not null from group_notifications n join group_messages m on m.id=n.message_id where n.recipient_id=$(U $1) order by n.sequence desc limit ${2:-5}"; }   # N Atl 5: últimos avisos da pessoa
```

"Reabrir a tela": trocar de aba Grupos → Início e tocar no sino "Notificações" (as telas não recarregam sozinhas).

---

## NOT-001 · Como um aviso chega: central, push e WhatsApp [MULTI] [push: só produção]

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | ORG | Grupo → "Avisos" (mural) → escrever `Aviso de teste <data>` → "Enviar" | Aviso aparece no mural | `select channel,recipient_count from group_messages where group_id=$G and channel='NOTICE' order by created_at desc limit 1` = `NOTICE|3` (ATL, ATL2, ATL3; o autor não conta) | ☐ | ☐ |
| 2 | ATL, ATL2, ATL3 | Cada um reabre a tela e abre "Notificações" | Linha com `Aviso de teste <data>` (o texto aparece na central; o push em produção não leva o texto: `Você recebeu um aviso do grupo. Abra o app para conferir.`) | `N Atl 1` mostra `NOTICE` e `f` (não lida) | ☐ | ☐ |
| 3 | ORG | Abrir "Notificações" | **Sem** o aviso de teste (o autor não recebe o próprio aviso) | `select count(*) from group_notifications where recipient_id=$(U Org) and message_id in (select id from group_messages where body like 'Aviso de teste%')` = `0` | ☐ | ☐ |
| 4 | ATL | Tocar no aviso | Marca como lida (a faixa `Não lida` some) | `N Atl 1` → `t` | ☐ | ☐ |
| 5 | ORG | Cancelar o J1 (JOGO-005, roteiro 07) | Aviso `O jogo de … às … foi cancelado.` na central de quem estava confirmado ou na espera (push com o texto em produção) | `select body from group_messages where group_id=$G order by created_at desc limit 1` | ☐ | ☐ |
| 6 | ATL3 | Grupo → "Sair do grupo" → confirmar | Sai do grupo | `select active from group_memberships where user_id=$(U Atl3)` = `f` | ☐ | ☐ |
| 7 | ORG | Publicar outro aviso do mural `Segundo aviso <data>` | — | `recipient_count` = `2` (ATL3 não conta mais) | ☐ | ☐ |
| 8 | ATL3 | Abrir "Notificações" | **Sem** o `Segundo aviso <data>` | `N Atl3 3` não tem `Segundo aviso` | ☐ | ☐ |
| 9 | ORG (toque duplo) | Escrever `Aviso duplo <data>` e tocar "Enviar" duas vezes seguidas | Um aviso só no mural | `select count(*) from group_messages where body='Aviso duplo <data>'` = `1`; ATL com `count=1` em `group_notifications` | ☐ | ☐ |
| 10 | rede | ORG desliga a rede do aparelho (modo avião), escreve `Aviso sem rede <data>` e envia; religa a rede e toca "Enviar" de novo | Primeiro erro `Não foi possível confirmar o envio. Tente enviar novamente.`; depois publica uma vez só | `select count(*) from group_messages where body='Aviso sem rede <data>'` = `1` | ☐ | ☐ |
| 11 | ATL | Perfil → "Notificações" (configurações) → desligar `Avisos dos grupos` → "Salvar preferências" (`Preferências salvas.`) | — | `select notices,push_notices from group_notification_preferences where user_id=$(U Atl)` = `f|f` | ☐ | ☐ |
| 12 | ORG | Publicar `Aviso com chave desligada <data>` | — | — | ☐ | ☐ |
| 13 | ATL | Reabrir a tela e abrir "Notificações" | **Esperado pela nota:** o aviso **continua na central**. Se não estiver, registrar ✘ `DEB-064` | `N Atl 1` (esperado: o aviso aparece) | ☐ | ☐ |
| 14 | ATL | Religar `Avisos dos grupos` e salvar | — | `notices=t` | ☐ | ☐ |
| 15 | push (produção) | Push do mural sem texto, push do cancelamento com texto, retentativa após falha do serviço, token recusado removendo o aparelho | `[push: só produção]` | — | ☐ | ☐ |

## NOT-002 · As preferências de cada pessoa [MULTI] [push: só produção]

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | ATL2 (conta nova) | Perfil → "Notificações" | Quatro chaves ligadas: `Avisos dos grupos`, `Mensagens das conversas`, `Lembretes de presença`, `Lembretes de cobrança`; no WhatsApp, `Cobranças` ligada | `select notices,messages,reminders,push_notices,push_messages,push_reminders,push_charges,whatsapp_charges from group_notification_preferences where user_id=$(U Atl2)` = `t|t|t|t|t|t|t|t` (ou sem linha: padrão ligado; `whatsapp_charges` é `t` por padrão da nota) | ☐ | ☐ |
| 2 | ATL2 | Desligar `Avisos dos grupos` → "Salvar preferências" | `Preferências salvas.` | `push_notices=f` | ☐ | ☐ |
| 3 | ORG | Publicar mural `Mural pref <data>` | — | — | ☐ | ☐ |
| 4 | ATL2 | Reabrir "Notificações" | Mural: **pela nota**, aparece na central (a chave só controla push). Divergência `DEB-064` se sumir | `N Atl2 1` | ☐ | ☐ |
| 5 | ORG | Mudar o horário do J1 (JOGO-003, roteiro 07) | — | — | ☐ | ☐ |
| 6 | ATL2 | Reabrir "Notificações" | Aviso de mudança aparece (push chega mesmo com a chave desligada, `DEB-065`; aqui a central) | `N Atl2 1` | ☐ | ☐ |
| 7 | ATL2 | Religar `Avisos dos grupos`; desligar `Mensagens das conversas`; salvar | — | `push_messages=f` | ☐ | ☐ |
| 8 | ORG | Escrever na `Conversa` do grupo `Oi conversa <data>` | — | — | ☐ | ☐ |
| 9 | ATL2 | Abrir "Notificações" | Aviso de conversa conforme DEB-064 (esperado na central); sem push (produção) | `N Atl2 1` | ☐ | ☐ |
| 10 | ATL2 | Religar `Mensagens das conversas`; desligar `Lembretes de cobrança`; salvar | — | `push_charges=f` | ☐ | ☐ |
| 11 | ORG | Cobrar a diária de ATL2 (roteiro 07, FIN-005) | — | — | ☐ | ☐ |
| 12 | ATL2 | Abrir "Notificações" | Cobrança aparece na central (`Cobranças ficam registradas na central do app.`) | `N Atl2 1` (`CHARGE`) | ☐ | ☐ |
| 13 | ATL2 | Religar `Lembretes de cobrança`; desligar WhatsApp `Cobranças`; salvar | — | `whatsapp_charges=f`, `push_charges=t` | ☐ | ☐ |
| 14 | produção | WhatsApp: sem mensagem quando a chave está desligada, push segue a chave dele | `[push: só produção]` (WhatsApp também desligado aqui) | — | ☐ | ☐ |

## NOT-003 · Avisos do jogo [CRÍTICO] [MULTI] [push: só produção]

Os horários reais do lembrete são 9h, 12h30 e 18h (São Paulo). Para não esperar o relógio, o roteiro usa o botão **Avisar** do organizador (lembrete manual) e, se a rodada cair perto de 9h ou 18h, a conferência do disparo automático. Estado: J1 publicado amanhã, ATL e ATL2 e ATL3 sem resposta.

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | ATL, ATL2, ATL3 | Reabrir "Notificações" | Aviso `O jogo está liberado. Confirme sua presença.` (jogo publicado) | `select channel,count(*) from group_messages where group_id=$G group by channel` tem `GAME_OPEN` | ☐ | ☐ |
| 2 | ORG | Abrir o J1 → "Avisar" (lembrete manual) | Confirma o envio | `select count(*), max(recipient_count) from group_messages where channel='REMINDER' and game_id=<J1>` | ☐ | ☐ |
| 3 | ATL, ATL2, ATL3 | Reabrir "Notificações" | Lembrete de presença com a lista do jogo | `N Atl 1` = `REMINDER` | ☐ | ☐ |
| 4 | ATL | "Vou" no J1 | `Você está confirmado.` | — | ☐ | ☐ |
| 5 | ORG | "Avisar" de novo | — | `recipient_count` do lembrete novo = `2` (ATL3 e ATL2; ATL respondeu) | ☐ | ☐ |
| 6 | ATL | Reabrir "Notificações" | **Sem** lembrete novo | `N Atl 1` continua o antigo | ☐ | ☐ |
| 7 | ATL2 | "Não vou" no J1 | `Tudo bem, na próxima.` | — | ☐ | ☐ |
| 8 | ORG | "Avisar" de novo | `recipient_count` = `1` (só ATL3, que não respondeu) | idem | ☐ | ☐ |
| 9 | ORG (cancelado) | Cancelar o J1 | Confirmados e espera avisados (ATL); ATL2 e ATL3, não | `recipient_count` do aviso de cancelamento = `1` | ☐ | ☐ |
| 10 | ORG | Marcar jogo novo `J5` amanhã 21:00, **mudar o horário** depois para `21:30` | Aviso de mudança para todos, menos "Não vou": ATL3 (sem resposta) recebe; ATL2 (se disse "Não vou" no J5) não | `select recipient_count from group_messages where group_id=$G order by created_at desc limit 1` | ☐ | ☐ |
| 11 | banco | Rascunho: `update games set status='DRAFT' where id=<J5>`; esperar o lembrete do relógio ou clicar "Avisar" | Rascunho **não** gera aviso nem "Avisar" | `select count(*) from group_messages where game_id=<J5> and channel='REMINDER'` não aumenta | ☐ | ☐ |
| 12 | banco | Já começou: `update games set status='PUBLISHED', starts_at=now()-interval '5 minutes' where id=<J5>` | Sem "Avisar"; nenhum lembrete novo | idem | ☐ | ☐ |
| 13 | relógio (9h ou 18h) | Se a rodada passar de 9h ou 18h de São Paulo, conferir depois desse horário | Lembrete automático chegou uma vez; no máximo 2 por dia | `select created_at from group_messages where channel='REMINDER' and game_id=<J5> order by created_at` | ☐ | ☐ |
| 14 | produção | Push de lembrete, de cancelamento (com texto) e de mudança (com horário novo) | `[push: só produção]` | — | ☐ | ☐ |

## NOT-004 · Avisos de pessoas [MULTI] [push: só produção]

Preparação: grupo `QA Aprovação <data>` do ORG com "Aprovar pedidos de entrada" ligado (Editar grupo) ou, em banco, `update access_groups set entry_requires_approval=true where id=$G2`; ORG promove ATL a administrador. Convite novo pelo link (roteiro 01 passo 4).

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | ATL2 | Abrir o link do convite, concluir cadastro, "Pedir para entrar" (ou o rótulo da listagem) | Pedido enviado (`Pedido cancelado.` aparece só se cancelar) | `select count(*) from group_entry_requests where group_id=$G2` = `1` | ☐ | ☐ |
| 2 | ORG | Reabrir "Notificações" | `<ATL2> pediu para entrar no QA Aprovação <data>.` | `N Org 1` = `ENTRY_REQUEST` | ☐ | ☐ |
| 3 | ATL (admin) | Reabrir "Notificações" | O mesmo aviso (admin recebe) | `N Atl 1` = `ENTRY_REQUEST` | ☐ | ☐ |
| 4 | ATL2 | Reabrir "Notificações" | **Sem** aviso do próprio pedido | `N Atl2 3` sem `ENTRY_REQUEST` | ☐ | ☐ |
| 5 | ATL (admin) | Início/Grupo → "Pedidos para entrar" → aprovar o de ATL2 | Aprovado | `select active from group_memberships where user_id=$(U Atl2)` = `t` | ☐ | ☐ |
| 6 | ATL2 | Reabrir "Notificações" | `Você entrou no QA Aprovação <data>.` | `N Atl2 1` | ☐ | ☐ |
| 7 | ATL (admin) | Reabrir "Notificações" | **Sem** o aviso de aprovação (quem causou não recebe) | `N Atl 1` não tem `Você entrou` | ☐ | ☐ |
| 8 | ORG | Reabrir "Notificações" | Aviso de primeiro atleta (`… entrou no QA Aprovação <data>. A galera começou a chegar.`), uma vez só | `select count(*) from group_messages where group_id=$G2 and channel='FIRST_MEMBER'` = `1` | ☐ | ☐ |
| 9 | ATL3 | Mesmo caminho: pedir para entrar | Aviso `ENTRY_REQUEST` novo para ORG e ATL; sem novo `FIRST_MEMBER` | `count(FIRST_MEMBER)` segue `1` | ☐ | ☐ |
| 10 | ORG | Recusar o pedido de ATL3 | — | `N Atl3 3` sem aviso de recusa; ORG e ATL sem aviso novo | ☐ | ☐ |
| 11 | banco | Pedido parado há 2 dias: ATL3 pede de novo e `update group_entry_requests set requested_at=requested_at-interval '3 days' where group_id=$G2` (a coluna é `requested_at`) | Depois do próximo ciclo do job (até ~5 min), ORG e ATL recebem o **segundo** aviso do pedido, uma única vez | `select count(*) from group_messages where group_id=$G2 and channel='ENTRY_REQUEST'` → 1 por pedido + 1 aviso do segundo; repetir o job não cria outro | ☐ | ☐ |
| 12 | ORG | Membros → ATL2 → "Tornar administrador" | — | — | ☐ | ☐ |
| 13 | ATL2 | Reabrir "Notificações" | `Você agora é administrador de QA Aprovação <data>.` | `N Atl2 1` | ☐ | ☐ |
| 14 | ORG | Membros → ATL2 → "Tornar atleta" | — | — | ☐ | ☐ |
| 15 | ATL2 | Reabrir "Notificações" | **Sem** aviso de rebaixamento | `N Atl2 1` segue o de promoção | ☐ | ☐ |
| 16 | ORG | Num jogo com ATL2 confirmado: "Tirar" ATL2 (`game_member_remove`: `Tirar ATL2 do jogo?` · `<ATL2> vai receber um aviso, ok?`) | — | — | ☐ | ☐ |
| 17 | ATL2 | Reabrir "Notificações" | `Sua vaga no jogo de … foi removida pela organização.` | `N Atl2 1` | ☐ | ☐ |
| 18 | ORG | Primeira confirmação do grupo: ATL confirma "Vou" no primeiro jogo | ORG recebe `… confirmou presença no jogo de …. A lista começou a fechar sozinha.` uma vez | `count(FIRST_CONFIRMATION)` = `1` | ☐ | ☐ |
| 19 | ORG | Excluir o grupo `QA Aprovação <data>` (Gestão → "Excluir grupo") | — | — | ☐ | ☐ |
| 20 | ATL, ATL2 | Reabrir "Notificações" | `Um dos seus grupos foi excluído pelo organizador.` (ORG, autor, não recebe) | `N Atl 1`, `N Atl2 1` | ☐ | ☐ |
| 21 | produção | Push de todos os acima, 2º aviso de pedido, WhatsApp | `[push: só produção]` | — | ☐ | ☐ |

## NOT-005 · Avisos de dinheiro [MULTI] [push: só produção]

Estado (roteiro 07): grupo `QA Vôlei <data>` com 3 cobranças pendentes (ATL, ATL2, ATL3), uma delas já cobrada há 2 h (`update group_messages set created_at=now()-interval '2 hours' where charge_id=<cobrança de ATL3>` se já foi cobrada).

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | ORG | Caixa do grupo → `Cobrar` → `Cobrar todos` → `Enviar (3)` | `Avisos enviados: 3` | `select count(*) from group_messages where charge_id is not null` aumentou em 3 | ☐ | ☐ |
| 2 | ATL, ATL2, ATL3 | Reabrir "Notificações" | `Você recebeu um lembrete de cobrança.` (push em produção); WhatsApp de cobrança com valor, vencimento e Pix só em produção | `N Atl 1` = `CHARGE` | ☐ | ☐ |
| 3 | ORG | Cobrar de novo (a de ATL3 foi cobrada há 2 h) | A de ATL3 aparece como `Já cobrado hoje` e não envia | só 2 cobranças novas | ☐ | ☐ |
| 4 | ORG | `Recebi` em ATL2 antes de cobrar; cobrar ATL e ATL2 | ATL2 não aparece para cobrar | sem `CHARGE` novo para ATL2 | ☐ | ☐ |
| 5 | ATL2 | Desligar push e WhatsApp de cobrança (`Lembretes de cobrança` e WhatsApp `Cobranças`) | — | `push_charges=f`, `whatsapp_charges=f` | ☐ | ☐ |
| 6 | ORG | Cobrar ATL2 (devolver a cobrança dele a pendente com "Desfazer") | — | — | ☐ | ☐ |
| 7 | ATL2 | Reabrir "Notificações" | Cobrança só na central | `N Atl2 1` = `CHARGE` | ☐ | ☐ |
| 8 | ORG (toque duplo) | `Enviar (1)` duas vezes seguidas (mesmo pedido) | Um aviso por cobrança | `select count(*)… charge_id=<c>` não duplica | ☐ | ☐ |
| 9 | ORG | Selecionar mais de 200 cobranças | Não é prático no app; `[BLOQUEADO]`: recusa vem do servidor (400/422) | — | ☐ | ☐ |
| 10 | ATL (permissão) | Procurar "Cobrar" | Não existe para o atleta | — | ☐ | ☐ |
| 11 | ORG (convidado) | Cobrar a diária do convidado de ATL3 | ATL3 (anfitrião) recebe o aviso | `N Atl3 1` | ☐ | ☐ |
| 12 | produção | Push e WhatsApp com valor, vencimento e chave Pix; pagar antes da entrega e não sair | `[push: só produção]` | — | ☐ | ☐ |

## NOT-006 · Mural e conversas [MULTI] [push: só produção]

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | ATL | Grupo → "Avisos" | Campo de mensagem ausente; texto `Somente administradores publicam avisos.` | — | ☐ | ☐ |
| 2 | ATL | Grupo → "Conversa" → `Oi galera <data>` → "Enviar" | A mensagem aparece | `select channel,author_name from group_messages where body='Oi galera <data>'` = `CHAT|QA Atl <data>` | ☐ | ☐ |
| 3 | ORG, ATL2 | Reabrir "Conversa" | Veem `Oi galera <data>` | — | ☐ | ☐ |
| 4 | ATL | Conversa: enviar texto vazio | O botão "Enviar" não envia | `count(*)` não muda | ☐ | ☐ |
| 5 | ATL | Conversa: colar 2.001 caracteres (`python3 -c "print('a'*2001)" | pbcopy`; no Android, colar do clipboard) | Fica limitado a 2.000 (`Mensagem (até 2.000 caracteres)`) ou é recusado | `select max(char_length(body)) from group_messages where channel='CHAT'` ≤ `2000` | ☐ | ☐ |
| 6 | ATL | Conversa: enviar uma palavra proibida (usar `porra` ou outra da lista de `ForbiddenWords`) | `Esse texto tem palavras que não são permitidas no Saqz. Revise e tente de novo.`; nada é gravado | `count(*)` não muda | ☐ | ☐ |
| 7 | ATL (repetição) | Enviar `Repeti <data>` com dois toques rápidos | Uma mensagem só | `select count(*) where body='Repeti <data>'` = `1` | ☐ | ☐ |
| 8 | ORG | Bloquear ATL: em "Conversa" tocar na mensagem de ATL → `Bloquear QA Atl <data>` → `Bloquear` | `Pessoa bloqueada.` | `select count(*) from user_blocks where blocker_id=$(U Org) and blocked_id=$(U Atl)` = `1` | ☐ | ☐ |
| 9 | ORG | Abrir "Conversa" e "Notificações" | Não vê mais as mensagens de ATL | — | ☐ | ☐ |
| 10 | ATL | Escrever `Depois do bloqueio <data>` | Funciona normalmente, **sem sinal** de bloqueio | `select count(*) from group_messages where body='Depois do bloqueio <data>'` = `1` | ☐ | ☐ |
| 11 | ORG | Ver notificações | Sem aviso de `Depois do bloqueio` (corta notificação escrita por quem bloqueou) | `N Org 3` | ☐ | ☐ |
| 12 | ATL2 | Denunciar: tocar na mensagem `Oi galera <data>` de ATL → `Denunciar aviso` → motivo `Spam ou propaganda` → `Enviar denúncia` | `Denúncia enviada. Nossa equipe analisa em até 24 horas.` | e-mail no Mailpit (`http://127.0.0.1:18025`) para a equipe | ☐ | ☐ |
| 13 | ATL2 | Reabrir "Conversa" | A mensagem denunciada **some para ATL2**; ORG e ATL3 ainda veem | — | ☐ | ☐ |
| 14 | ATL | Tentar denunciar a **própria** mensagem | Não há opção `Denunciar` na própria mensagem (ou recusado) | — | ☐ | ☐ |
| 15 | ATL3 | Sair do grupo (Gestão → "Sair do grupo") e tentar denunciar uma mensagem antiga (link/atalho antigo, se houver) | 404 / `Não foi possível concluir. Tente novamente.` | — | ☐ | ☐ |
| 16 | rede | ORG: modo avião, envia `Mural sem rede <data>`, volta a rede | Erro `Não foi possível confirmar o envio. Tente enviar novamente.` e nenhum duplicado | `count(*)=1` | ☐ | ☐ |
| 17 | API (mesmo `requestId`, texto diferente) | `curl -X POST …/api/groups/<G>/messages` com o mesmo `requestId` e texto diferente (token do ATL) | 409 Conflito | `[BLOQUEADO]` se o token do Auth Emulator não for obtido; ver `tests/multi-aparelhos/rodada-2026-10-13.md` | ☐ | ☐ |
| 18 | produção | Push de mural/conversa sem texto; bloqueado sem push | `[push: só produção]` | — | ☐ | ☐ |

## NOT-007 · O grupo do WhatsApp [MULTI] [push: só produção]

No stack local o WhatsApp está desligado; o roteiro cobre o que dá para ver no app, o Uazapi falso (`ferramentas/uazapi-falso.mjs`) registra o que o backend mandaria, e o resto fica para produção. Preparação opcional: subir o Uazapi falso (variáveis no topo do arquivo) antes de `stack.sh up`.

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | ATL (permissão) | Grupo → Gestão | Nenhum item `Grupo do WhatsApp` para atleta | — | ☐ | ☐ |
| 2 | ORG | Gestão → "Grupo do WhatsApp" → "Configurar" | Tela com `Nenhum grupo vinculado` · `Cole o link de convite do grupo do WhatsApp para começar.` | — | ☐ | ☐ |
| 3 | ORG | Colar `https://exemplo.com/x` → "Vincular grupo" | `Esse link não é de um grupo do WhatsApp. Cole o link de convite do grupo, no formato https://chat.whatsapp.com/…` | — | ☐ | ☐ |
| 4 | ORG | Colar `https://chat.whatsapp.com/QA<tag>` → "Vincular grupo" (Uazapi falso com admin igual ao telefone do ORG) | `Grupo vinculado: …` e status `Ativo` | `select enabled,broken_at is null from group_whatsapp_bindings where group_id=$G` = `t|t` | ☐ | ☐ |
| 5 | ORG | Mesmo link num segundo grupo Saqz (`QA Outro <data>`) | `Este grupo do WhatsApp já está vinculado a outro grupo do Saqz. …` | — | ☐ | ☐ |
| 6 | ORG | Link cujo admin do WhatsApp não bate com ninguém do Saqz (Uazapi falso com admin `55119999999`) | Recusado | — | ☐ | ☐ |
| 7 | ORG | Link de grupo com aprovação de entrada pendente | Status `Aguardando aprovação` | — | ☐ | ☐ |
| 8 | ORG | "Desabilitar" e depois "Reabilitar" | `Vínculo atualizado.` com status `Desabilitado` e depois `Ativo` | `select enabled from group_whatsapp_bindings where group_id=$G` = `f` e depois `t` | ☐ | ☐ |
| 9 | ORG | Mural `Para o WhatsApp <data>` | Uazapi falso registra o texto completo | arquivo/log do Uazapi falso | ☐ | ☐ |
| 10 | ORG | "Avisar" no J1 | Uazapi falso registra o lembrete com botões `Vou` e `Não vou` | idem | ☐ | ☐ |
| 11 | ORG | Mudar o horário do J1 | Mudança de horário no WhatsApp (alvo `DEB-071`; ✘ se não sair) | idem | ☐ | ☐ |
| 12 | ORG | Jogo liberado / pedido de entrada / cobrança / conversa | **Não** aparecem no WhatsApp | idem | ☐ | ☐ |
| 13 | Uazapi falso | Remover o número do Saqz do grupo (`DELETE` no falso) | Status `Quebrado` + `Vincular de novo` · `O número do Saqz não está mais no grupo do WhatsApp. …`; pendentes canceladas | `select broken_at is not null from group_whatsapp_bindings where group_id=$G` = `t`; `select status,count(*) from notification_whatsapp_group_queue group by 1` sem `PENDING` | ☐ | ☐ |
| 14 | Uazapi falso | Provedor fora do ar: desligar o falso e publicar mural | O app publica normalmente; ao religar, a mensagem sai (nova tentativa) | — | ☐ | ☐ |
| 15 | produção | Grupo de WhatsApp real | `[push: só produção]` | — | ☐ | ☐ |

---

## Rodadas

**Rodada 1: ORG no iPhone, ATL no Android.**
ORG = iPhone 17, ATL = `Saqz_QA_A`, ATL2 = iPhone 17e, ATL3 = `Saqz_QA_B`. Todas as regras, na ordem: NOT-001, 002, 003, 004, 005, 006, 007.

**Rodada 2: ORG no Android, ATL no iPhone.**
Reset do stack, apps limpos, `<tag>` novo, preparação de novo. ORG = `Saqz_QA_A`, ATL = iPhone 17, ATL2 = `Saqz_QA_B`, ATL3 = iPhone 17e. Mesmos passos; marcar iOS ou Android conforme o aparelho que executou o "Faça" (para a central de cada pessoa, o aparelho dela).

No relato: tempo total, passos com adb/simctl, o que ficou BLOQUEADO (push, API sem token, WhatsApp real) e as divergências DEB-064/065/071 vistas.
