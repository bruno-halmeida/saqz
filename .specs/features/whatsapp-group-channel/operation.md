# Notificações: canal de grupo de WhatsApp — operação

## Modelo

- Dois canais de WhatsApp: **grupo vinculado** (avisos NOTICE e presença REMINDER, controle do
  gestor do grupo Saqz) e **DM individual** (somente cobrança CHARGE, opt-in por usuário). CHAT
  nunca sai do app.
- Com vínculo ativo e canal habilitado, avisos e presença vão **somente** ao grupo (um envio por
  mensagem, não N DMs). Sem vínculo, essas categorias não têm WhatsApp.
- REMINDER em grupo é broadcast: todo o grupo WhatsApp vê, inclusive quem já confirmou e quem não
  é do Saqz. O link de presença continua por mensagem; a confirmação exige conta autenticada e
  associação ativa (GET/preview nunca confirma).
- Cobrança permanece exatamente como o fluxo DM original: opt-in (`whatsapp_charges`), telefone no
  perfil, retry/backoff, cancelamento por opt-out/saída/troca de telefone/cobrança cancelada.
  `whatsapp_notices`/`whatsapp_reminders` ficam inertes (compatibilidade de API).
- Usuário comum só vê o switch de WhatsApp de cobrança no app; avisos/presença viraram configuração
  do gestor (configurações avançadas do grupo).

## Vínculo (binding)

- Criado pelo gestor em **configurações avançadas** colando `https://chat.whatsapp.com/<código>`
  (ou código puro). Um vínculo por grupo Saqz; colar novo link substitui (sem `leave` do antigo).
- Sequência do backend: validar gestor → `inviteInfo` (desembrulha `{"group":...}`) →
  **anti-sequestro**: nenhum admin do grupo WhatsApp com telefone resolvível que bata com membro
  ativo do grupo Saqz → 422 → instância conectada? (`instance/status`) → `join` → **reconfirmar com
  `group/info` force** (o retorno do join é vazio e nunca confiável) → persistir
  `group_whatsapp_bindings` com `enabled=true`.
- Estados: `ACTIVE` (canal ligado), `DISABLED` (gestor desligou; vínculo e link preservados),
  `BROKEN` (revalidação detectou que a instância saiu do grupo — `broken_at` preenchido).
- Reabilitar retoma sem reenvio de histórico. Não existe unbind/leave na v1.

## Entrega e recuperação (fila de grupo)

`notification_whatsapp_group_queue` — **um job por mensagem** (PK `message_id`), enfileirado por
trigger `AFTER INSERT ON group_messages` quando `channel IN ('NOTICE','REMINDER')` e vínculo
`enabled`/não-quebrado/grupo vivo. Replay do publish não duplica (idempotência por message_id).

Worker (a cada `saqz.notifications.whatsapp.group-delay-ms`, default 15s, 20/rodada):

1. Job `PENDING` com `FOR UPDATE SKIP LOCKED`.
2. Sem vínculo, desabilitado ou quebrado → `CANCELLED`.
3. REMINDER com jogo não publicado/prazo vencido/início passado → `CANCELLED`.
4. Revalidação `group/info`: `NotInGroup` → **vínculo quebrado** + cancela todos os pendentes do
   grupo + `CANCELLED`. Indisponibilidade transitória → retry.
5. Membership: participante com `PhoneNumber` dígitos == `instance_jid` — ausente → idem passo 4.
6. Envio `sendText` ao JID (`track_id group-<messageId>`): `ACCEPTED` conclui; erro permanente
   `FAILED`; transitório retry com `maxOf(Retry-After, min(3600, 60·2^attempts))`, máx 10.

**`NotInGroup` na prática**: o Uazapi responde HTTP 500 `{"error":"that group does not exist"}` —
o `UazapiGroupDirectory` traduz esse 500 específico para quebra de vínculo e **nunca** para retry
(a política de DM trataria 5xx como transitório e o worker ficaria em loop). Todos os contratos de
parser validados no T0 estão em `evidence.md`.

Corpos (NOTICE sem valor financeiro, sem nome de pessoa ou telefone; REMINDER lista os nomes por situação):
- NOTICE: `Saqz · {grupo}\n{texto}`
- REMINDER: `Saqz · {grupo}\n{corpo}` + botão `Confirmar presença` → `{link}` (fallback de texto com a URL se o botão for recusado)
- `{corpo}`: `*{título}*` + `✅ Confirmados:`, `🕒 Lista de espera:` e `❌ Fora:` (um nome por linha, nesta ordem; seção vazia omitida; corte em 2000 caracteres no último nome, com `…`)

## Inspeção operacional

```sql
-- vínculos e estados
SELECT group_id, whatsapp_jid, group_name, enabled, broken_at FROM group_whatsapp_bindings;
-- fila de grupo
SELECT message_id, status, attempts, next_attempt_at FROM notification_whatsapp_group_queue
  ORDER BY next_attempt_at DESC LIMIT 50;
-- pendências de grupo paradas (vínculo quebrado/desabilitado gera CANCELLED na próxima rodada)
```

Não reative vínculos quebrados às cegas: confira no WhatsApp se a instância continua no grupo e
re-vincule pelo app (o fluxo faz join + verificação). Habilitar/desabilitar não reenvia histórico.

## Configuração

Mesmas variáveis do canal WhatsApp (`SAQZ_NOTIFICATIONS_WHATSAPP_*`); adicional:
`saqz.notifications.whatsapp.group-delay-ms` (default 15000). Instância única atende todos os
grupos Saqz; enviar 1 mensagem ao grupo é mais barato que N DMs. Sem exactly-once remoto:
`track_id` aceita duplicados; `ACCEPTED` não prova leitura.

## Verificação

- T0 (evidence.md): homologação real contra grupo de teste criado/removido pela própria instância
  (parser, 500-does-not-exist, PhoneNumber como JID, join vazio).
- Suítes: `:features:groups:test`, `:features:groups:integrationTest`, `:bootstrap:test`,
  `:architecture-tests:test` — verdes com T1–T7 integradas.
- Limites conhecidos: sem webhook/SSE (revalidação só na entrega), comunidades fora da v1, grupos
  incógnito rejeitados, `Owner*` do Uazapi não é usado (anti-sequestro por `Participants.IsAdmin`).
