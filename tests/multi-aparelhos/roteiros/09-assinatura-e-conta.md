# Roteiro 09 · Grupo congelado, confirmação de contato e conta suspensa

Regras do cofre cobertas: ASS-003 (sem plano ativo, grupo congelado), ACE-002 (confirmar e-mail e telefone), ACE-005 (conta suspensa pela equipe).
Tabelas de um aparelho: `tests/roteiros/assinatura-e-teste-gratis.md` (ASS-003) e `tests/roteiros/acesso-e-conta.md` (ACE-002, ACE-005).
Se a nota e o app divergirem, vale a nota: registre o texto que apareceu. Divergências **já conhecidas**:
a nota ASS-003 diz "O plano deste grupo está congelado…", o app mostra `O plano deste grupo está pausado. Avise o organizador para voltar a confirmar presença.`;
a nota ACE-002 prevê a folha `Confirme seu e-mail ou telefone para confirmar sua presença` com o botão `Reenviar confirmação`, o app hoje tem a faixa `Confirme seu e-mail para não perder o acesso à sua conta.` com `Reenviar` / `Reenviar e-mail`;
a nota ACE-005 marca a tela de conta suspensa como "(alvo)": o app trata a suspensão só no primeiro acesso.
Anote ✘ nesses passos com o texto real e o código, sem tentar de outro jeito.

Papéis: **ORG** (dono), **ATL**, **ATL2**, **ATL3**. Contas, senha, celulares e nomes iguais ao [roteiro 01](01-dono-e-atletas.md); `<data>` = `DD/MM`, `<tag>` = `DDMM`.
Preparação: `stack.sh reset` + `up`, apps limpos, **passos 1 a 8 do roteiro 01** (ORG, ATL, ATL2 e ATL3 dentro de `QA Vôlei <data>`) e um jogo `J1` amanhã às 19:00, capacidade 4, publicado (ORG, "Marcar jogo").
Neste roteiro **não** abra o `oobLink` das contas ATL2 e ATL3 antes dos passos de ACE-002 (elas ficam sem e-mail confirmado).

```sh
PSQL='docker exec saqz-multidev-db psql -U saqz -d saqz -Atc'
G="(select id from access_groups where name='QA Vôlei <data>')"
U() { echo "(select id from access_users where display_name='QA $1 <data>')"; }   # U Org, U Atl, U Atl2, U Atl3
```

"Reabrir a tela": trocar de aba Grupos → Início ou abrir "Ver o jogo" de novo; sem push no stack local.

---

## ASS-003 · Sem plano ativo: o grupo fica congelado [CRÍTICO] [MULTI]

Estado inicial: ORG em teste do Organizador (14 dias). J1 publicado, ATL **confirmado**, ATL2 e ATL3 sem resposta, uma cobrança de diária pendente de ATL (valor do jogo `20,00` definido no editor antes de ATL confirmar).
Vencer o teste no banco (é a forma de "reduzir o plano" neste stack):

```sh
$PSQL "update organizer_trials set started_at=now()-interval '20 days', ends_at=now()-interval '6 days' where owner_user_id=$(U Org)"
$PSQL "select started_at,ends_at from organizer_trials where owner_user_id=$(U Org)"   # ends_at no passado
```

(Se ORG ainda tiver `app_store_subscriptions` do `.storekit`, apague essa linha antes: o roteiro parte de **sem assinatura**.)

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | ORG | Fechar e abrir o app; Início | Início abre normalmente; o grupo e o J1 continuam visíveis (só leitura) | — | ☐ | ☐ |
| 2 | ORG | Início → "Marcar jogo" (ou o botão do grupo) | Folha `Seu teste do Organizador terminou` · `Seus grupos e o histórico continuam aqui. Para marcar jogos e mexer no grupo, assine um plano.` com `Ver planos` e `Agora não`; nenhum jogo é criado | `select count(*) from games where group_id=$G` = `1` | ☐ | ☐ |
| 3 | ORG | `Agora não`; Perfil → "Meu plano" | Mostra `TESTE ENCERRADO` e `Seu teste do Organizador terminou em <data>` | — | ☐ | ☐ |
| 4 | ORG | J1 → "Editar jogo" → mudar o horário → "Salvar alterações" | Recusado com o mesmo aviso de plano (`Seu teste do Organizador terminou` / `Seu plano não está ativo`); horário não muda | `select local_time from games where group_id=$G` = `19:00:00` | ☐ | ☐ |
| 5 | ATL2 | Reabrir a tela; no cartão do J1 tocar "Vou" | Recusado: `O plano deste grupo está pausado. Avise o organizador para voltar a confirmar presença.` (a nota diz "congelado": registrar o texto real); não entra na lista | `select count(*) from game_attendance where member_display_name='QA Atl2 <data>'` = `0` | ☐ | ☐ |
| 6 | ATL | "Alterar" → "Não vou" (ATL estava confirmado) | Funciona: `Tudo bem, na próxima. Sua vaga já foi liberada.` | `select status from game_attendance where member_display_name='QA Atl <data>'` = `DECLINED` | ☐ | ☐ |
| 7 | ATL | Tentar voltar a "Vou" | Recusado, mesmo aviso do passo 5 | `status` segue `DECLINED` | ☐ | ☐ |
| 8 | ATL, ATL3 | Grupo → abrir o grupo, jogos, lista e "Minhas cobranças" | Mostra tudo (jogo, lista, cobranças) sem botões de ação que mudam algo | — | ☐ | ☐ |
| 9 | ORG | Grupo → "Caixa do grupo" | Mostra saldo, cobranças e extrato; **sem** `Recebi`, `Dispensar`, `Cobrar` ativos (ou recusados com o aviso de plano); nenhuma baixa é gravada | `select status from group_charges where member_user_id=$(U Atl)` = `PENDING` | ☐ | ☐ |
| 10 | ORG | Tentar "Convidar por link" → "Criar novo link" | Não cria link novo (recusado com o aviso de plano) | `select count(*) from group_invites` não aumenta (link antigo, se havia, segue) | ☐ | ☐ |
| 11 | conta nova | Criar `qa-atl4+<tag>@saqz.local` (nome `QA Atl4 <data>`) e abrir o link do convite **antigo** do grupo | Convite recusado (grupo congelado não aceita entrada) | `select count(*) from group_memberships where group_id=$G` não aumenta (continua 4) | ☐ | ☐ |
| 12 | ORG | Aprovar pedidos de entrada (se houver pedido): grupo com aprovação ligada e um pedido pendente (`update access_groups set entry_requires_approval=true…` antes do passo 11, ATL4 pede) | Aprovar é recusado com o aviso de plano | `select count(*) from group_memberships…` não aumenta | ☐ | ☐ |
| 13 | relógio | Esperar o próximo lembrete (9h ou 18h São Paulo) ou, no organizador, tocar "Avisar" | **Alvo:** nenhum lembrete de presença nem "jogo liberado" em grupo congelado; "Avisar" ausente/recusado | `select count(*) from group_messages where channel in ('REMINDER','GAME_OPEN') and group_id=$G` não aumenta depois do passo 1 | ☐ | ☐ |
| 14 | banco | Mensalidade automática e série: `select count(*) from group_charges where kind='MONTHLY'` e `select count(*) from games where series_id is not null` antes e depois de um ciclo de job (~5 min) | Nada novo (mensalidade e série param) | contagens iguais | ☐ | ☐ |
| 15 | ATL3 | Grupo → "Sair do grupo" → "Sair do grupo" | Funciona em grupo congelado | `select active from group_memberships where user_id=$(U Atl3)` = `f` | ☐ | ☐ |
| 16 | ORG (assinar) | Em produção seria "Ver planos" → assinar. No stack: iOS pelo `.storekit` do scheme SaqzDev (README da pasta); em qualquer plataforma, o banco: `update organizer_trials set ends_at=now()+interval '10 days'` | Reabrir: sem aviso de plano; "Marcar jogo" funciona | `select ends_at>now() from organizer_trials where owner_user_id=$(U Org)` = `t` | ☐ | ☐ |
| 17 | ATL2 | Reabrir a tela; "Vou" no J1 | `Você está confirmado.` (tudo volta na hora) | `status` = `CONFIRMED` | ☐ | ☐ |
| 18 | ORG | Convite novo (Convidar por link → "Criar novo link") | Link gerado (`https://links.saqz.app/?saqz_invite=…`) | — | ☐ | ☐ |
| 19 | permissão | ATL (atleta) tenta "Marcar jogo" | O botão não existe para atleta, com ou sem plano | — | ☐ | ☐ |

## ACE-002 · Confirmar e-mail e telefone [CRÍTICO] [MULTI]

Estado: grupo `QA Vôlei <data>` com ORG (e-mail confirmado ao abrir o `oobLink`), ATL, ATL2 e ATL3 **sem e-mail confirmado** (contas criadas por e-mail e senha no stack). J1 publicado amanhã 19:00, capacidade 4, plano do ORG ativo. Se vem do passo 17 do bloco anterior, mantenha o plano ativo.
Para confirmar e-mail: `curl -s http://127.0.0.1:9099/emulator/v1/projects/saqz-local/oobCodes` e abrir com curl o `oobLink` da conta; depois mandar o app para o plano de fundo e trazer de volta (recarrega o usuário, VUL-91).

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | ATL2 | Início: ver a faixa; "Vou" no J1 | Faixa `Confirme seu e-mail para não perder o acesso à sua conta.`; ao tocar "Vou": a folha/aviso que pede confirmar (nota: `Confirme seu e-mail ou telefone para confirmar sua presença` + `Reenviar confirmação`; copiar o texto real); **a presença não é gravada** | `select count(*) from game_attendance where member_display_name='QA Atl2 <data>'` = `0` | ☐ | ☐ |
| 2 | ATL3 | "Não vou" no J1 (sem confirmar nada) | Funciona: `Tudo bem, na próxima.` | `status` = `DECLINED` | ☐ | ☐ |
| 3 | ATL2 | Tocar no botão de reenviar (faixa: `Reenviar`; verificação: `Reenviar e-mail`) | Confirmação de envio (`E-mail enviado. Aguarde antes de reenviar`) | `curl` em oobCodes mostra 1 `VERIFY_EMAIL` novo para ATL2 | ☐ | ☐ |
| 4 | ATL2 | Tocar de novo antes de 60 s | Recusado com o tempo restante (`Aguarde antes de reenviar` / `Reenviar … em Ns`); `oobCodes` não ganha outro código | contagem de `VERIFY_EMAIL` de ATL2 = 1 | ☐ | ☐ |
| 5 | ATL2 | Abrir o `oobLink` de ATL2; plano de fundo → voltar ao app | A faixa de confirmação some | — | ☐ | ☐ |
| 6 | ATL2 | "Vou" no J1 | `Você está confirmado.` | `status` = `CONFIRMED` | ☐ | ☐ |
| 7 | ORG | Abrir o J1 | Vê ATL2 entre os confirmados | — | ☐ | ☐ |
| 8 | banco + ATL | Confirmar só o telefone de ATL: `update access_users set verified_phone=phone where id=$(U Atl)`; ATL reabre o app (plano de fundo → voltar) e toca "Vou" | `Você está confirmado.`; o e-mail segue **não** confirmado (a faixa `Confirme seu e-mail…` continua) | `select phone=verified_phone from access_users where id=$(U Atl)` = `t`; `status` = `CONFIRMED` | ☐ | ☐ |
| 9 | ATL2 | Perfil → editar o telefone (`11900000099`) → salvar | Telefone novo salvo | `select phone,verified_phone from access_users where id=$(U Atl2)` → `+5511900000099` e `verified_phone` antigo/nulo (diferentes) | ☐ | ☐ |
| 10 | ATL | Perfil → editar o telefone para outro número | Volta a "não confirmado" | `select phone=verified_phone from access_users where id=$(U Atl)` = `f` | ☐ | ☐ |
| 11 | ATL (só telefone novo não confirmado, e-mail ainda não confirmado) | "Vou" em um jogo novo (`J6`, ORG marca) | Recusado como no passo 1 | — | ☐ | ☐ |
| 12 | Google ou Apple | Conta Google/Apple no stack local não existe (Firebase Auth Emulator, só e-mail e senha) | `[BLOQUEADO]`: coberto na rodada de produção (PROD-AC) | — | ☐ | ☐ |
| 13 | ORG | Cobrar ATL (roteiro 07, FIN-005) com telefone **não** confirmado; conferir na central de ATL | Push (produção) e central; **sem** WhatsApp para telefone não confirmado | `select count(*) from notification_whatsapp_queue` não aumenta para ATL | ☐ | ☐ |
| 14 | API (rede) | Obter token de ATL2 pelo Auth Emulator: `curl -s -X POST 'http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=x' -H 'content-type: application/json' -d '{"email":"qa-atl2+<tag>@saqz.local","password":"SenhaQA2026","returnSecureToken":true}'`; repetir `curl -s -o /dev/null -w '%{http_code}\n' -X POST http://127.0.0.1:18080/api/email-verification/request -H "Authorization: Bearer <idToken>"` a cada 61 s, 9 vezes | Os 8 primeiros devolvem `2xx`; o **9º em 10 min** devolve `429` com o tempo restante | — | ☐ | ☐ |
| 15 | rede | ATL3 em modo avião toca "Reenviar" | Erro `Não foi possível reenviar agora. Tente de novo em instantes.` | — | ☐ | ☐ |
| 16 | repetição | Dois toques rápidos em "Reenviar" | Um envio só | contagem de `VERIFY_EMAIL` +1 | ☐ | ☐ |

## ACE-005 · Conta suspensa pela equipe [MULTI]

Estado: ATL com "Vou" no J1 (e-mail confirmado), ATL2 e ORG com sessão aberta. Suspender é feito pela equipe no painel (`POST /admin/users/{id}/suspend`); como o painel pede credencial de administrador da plataforma, o roteiro usa o banco, que é o que o endpoint grava.

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | banco (equipe) | `update access_users set suspended_at=now() where id=$(U Atl)` | — | `select suspended_at is not null from access_users where id=$(U Atl)` = `t` | ☐ | ☐ |
| 2 | painel (opcional) | Se tiver credencial, abrir `http://127.0.0.1:18080/admin/users` e procurar `QA Atl <data>` | Estado `Suspenso` | — | ☐ | ☐ |
| 3 | ATL (sessão aberta) | Sem sair do app, reabrir a tela e tocar "Alterar" → "Não vou" | Recusado (`ACCOUNT_SUSPENDED` no servidor); o app mostra erro/volta ao login; o estado não muda | `select status from game_attendance where member_display_name='QA Atl <data>'` segue `CONFIRMED` | ☐ | ☐ |
| 4 | ATL (sessão aberta) | Perfil → editar o apelido → salvar | Recusado; nada muda | `select display_name from access_users where id=$(U Atl)` igual | ☐ | ☐ |
| 5 | ATL | Sair da conta e entrar de novo com `qa-atl+<tag>@saqz.local` / `SenhaQA2026` | Login recusado com a tela `Sua conta foi suspensa. Para entender o motivo ou pedir revisão, escreva para contato@egysis.com.` (alvo: se aparecer só erro genérico, registrar o texto) | — | ☐ | ☐ |
| 6 | ORG, ATL2 | Reabrir o grupo | ATL continua na lista de membros e no J1 (a conta suspensa não sai dos grupos) | `select active from group_memberships where user_id=$(U Atl)` = `t` | ☐ | ☐ |
| 7 | banco (equipe) | `update access_users set suspended_at=null where id=$(U Atl)` | — | `suspended_at` nulo | ☐ | ☐ |
| 8 | ATL | Entrar de novo | Entra; grupo `QA Vôlei <data>`, J1 e histórico intactos | `status` = `CONFIRMED` | ☐ | ☐ |
| 9 | banco (dono suspenso) | `update access_users set suspended_at=now() where id=$(U Org)` | — | — | ☐ | ☐ |
| 10 | ORG | Reabrir a tela / entrar de novo | Recusado como no passo 5 | — | ☐ | ☐ |
| 11 | ATL, ATL2 | Reabrir o grupo e o J1 | O grupo segue existindo, com o J1 e a lista; "Vou"/"Não vou" funcionam como antes | `select count(*) from access_groups where id=$G and deleted_at is null` = `1` | ☐ | ☐ |
| 12 | banco | `update access_users set suspended_at=null where id=$(U Org)`; ORG entra | Tudo como antes | — | ☐ | ☐ |

---

## Rodadas

**Rodada 1: ORG no iPhone, ATL no Android.**
ORG = iPhone 17, ATL = `Saqz_QA_A`, ATL2 = iPhone 17e, ATL3 = `Saqz_QA_B`. Ordem: ACE-002 primeiro (as contas precisam estar sem e-mail confirmado), depois ASS-003 e, por fim, ACE-005. Antes de ASS-003, confirmar o e-mail de ATL3 (passo 15 sai do grupo sem precisar).

**Rodada 2: ORG no Android, ATL no iPhone.**
`stack.sh reset` + `up`, apps limpos, `<tag>` novo, preparação de novo. ORG = `Saqz_QA_A`, ATL = iPhone 17, ATL2 = `Saqz_QA_B`, ATL3 = iPhone 17e. Mesma ordem; marcar iOS ou Android conforme o aparelho que executou o "Faça".

No relato: tempo total, passos com adb/simctl, o que ficou BLOQUEADO (conta Google/Apple, painel sem credencial, push), e as divergências de texto citadas no início (pausado × congelado, folha de confirmação, tela de conta suspensa).
