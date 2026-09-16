# Canal de grupo de WhatsApp (avisos e presença)

## Decisões confirmadas

- **Dois canais de WhatsApp**: **grupo vinculado** (NOTICE + REMINDER, controle do gestor do grupo
  Saqz) e **DM individual** (somente CHARGE, opt-in por usuário, exatamente como hoje). CHAT nunca.
- **Vínculo**: gestor (owner/admin do grupo Saqz) cola o link de convite (`chat.whatsapp.com/...`)
  em **configurações avançadas** do grupo. O link é a credencial de vínculo; a API do WhatsApp não
  expõe "quem convidou", então a autoria é o gestor autenticado que cadastrou.
- **Sequência de vínculo** (transação lógica, nesta ordem): validar gestor → `inviteInfo`
  (devolve JID/nome/participantes) → **anti-sequestro** (ao menos um **admin** do grupo WhatsApp
  com `PhoneNumber` resolvível cujos dígitos batem com o telefone de um membro ativo do grupo Saqz;
  normalização = remover tudo que não é dígito) → checar instância **conectada** → `join` →
  **confirmar entrada** (instância consta em `Participants` via `group/info`; join pendente de
  aprovação = rejeição) → persistir vínculo `JID ↔ access_group`.
- **Uma vinculação por grupo Saqz.** Colar um novo link **substitui** o vínculo existente (sem
  `leave` no grupo antigo na v1) e cancela jobs de grupo pendentes do vínculo anterior. O mesmo
  JID não pode estar vinculado a dois grupos Saqz (UNIQUE). **Não existe unbind na v1** — apenas
  desabilitar.
- **Canal nasce habilitado** no vínculo. Desabilitar preserva o vínculo e interrompe envios
  (jobs pendentes viram `CANCELLED`); reabilitar retoma sem novo `join` se a instância continuar
  no grupo. Reabilitar **não reenvia** histórico.
- **Com vínculo ativo e habilitado, NOTICE e REMINDER vão somente ao grupo** — o DM dessas
  categorias deixa de existir (não é fallback, não é complemento). Sem vínculo válido, avisos e
  presença simplesmente não têm canal WhatsApp.
- **REMINDER em grupo é broadcast**: a mensagem vai a todo o grupo WhatsApp, incluindo quem já
  confirmou presença e quem não faz parte do grupo Saqz. É decisão consciente de canal, não bug.
  O link de presença continua por mensagem e a confirmação exige conta autenticada + associação
  ativa (modelo atual inalterado).
- **CHARGE permanece 100% no fluxo DM atual**: opt-in individual, telefone no perfil, retry,
  cancelamento. As colunas `whatsapp_notices`/`whatsapp_reminders` ficam **inertes** (mantidas por
  compatibilidade de API; o mobile preserva seus valores ao salvar preferências).
- **Texto da mensagem de grupo** (templates exatos, nada de valor financeiro, pessoa ou telefone):
  - NOTICE: `Saqz · {group_name}\n{body}`
  - REMINDER: `Saqz · {group_name}\n{body}\nConfirmar minha presença no Saqz: {link}`
  (o link é o `notification_attendance_links.code` da mensagem, via `BranchAttendanceLinkFactory`).
- **Worker de grupo**: antes de enviar cada mensagem revalida, via `group/info`: (1) vínculo ativo
  e habilitado; (2) instância consta em `Participants`; (3) para REMINDER, jogo ainda publicado,
  prazo aberto e no futuro. Falha de rede/timeout → retry (mesma política do DM: até 10 tentativas,
  backoff, 429 respeita Retry-After). `group/info` respondendo grupo inexistente/fora (404/403) ou
  instância ausente de `Participants` → vínculo marcado **quebrado** (`broken_at`), jobs pendentes
  `CANCELLED`, push/central seguem normais. Quebrado ≠ falha de envio: só a revalidação quebra.
- **Sem eventos (webhook/SSE) na v1**: a revalidação é na entrega. Reconciliação passiva é futura.
- **Auto-adicionar atletas ao grupo do WhatsApp: fora do escopo.** Evolução futura: enviar o
  `invite_link` por DM.
- **Deeplink de presença inalterado** — os 3 casos (membro / conta sem vínculo / sem conta) já
  existem para o grupo Saqz.
- **SDK** `com.github.thoughtbruno:uazapi-connector-sdk:0.1.0` (JitPack). `/send/text` aceita JID
  `@g.us`. `GroupService` retorna `JsonNode` — o parse (participants, PhoneNumber, Owner*) fica
  isolado numa única porta (`WhatsAppGroupDirectory`); os desconhecidos U1–U5 são resolvidos no T0
  e refletidos só nessa porta, sem tocar no resto do código.

## Aceitação

- AC1 Vínculo: gestor autenticado cadastra o link; sistema valida, aplica anti-sequestro, entra
  com `join`, confirma a instância em `Participants` e persiste com canal habilitado. Rejeita com
  erro específico: link inválido/expirado (422), JID já vinculado a outro grupo Saqz (409), grupo
  sem admin resolvível (422), entrada pendente de aprovação (409), instância desconectada (502),
  não-gestor (403).
- AC2 Preferência do gestor: canal nasce habilitado; desabilitar interrompe envios e cancela
  pendentes preservando o vínculo; reabilitar retoma sem reenvio. Estado
  `ACTIVE/DISABLED/BROKEN` visível em configurações avançadas, restritas a owner/admin do grupo.
- AC3 Enfileiramento: INSERT em `group_messages` de canal NOTICE/REMINDER com vínculo ativo cria
  **um job por mensagem** na fila de grupo, na mesma transação; replay com o mesmo `requestId` não
  duplica (idempotência já existente no publish). CHARGE/CHAT nunca enfileiram ao grupo; grupo sem
  vínculo ou desabilitado/quebrado não enfileira.
- AC4 Entrega e revalidação: worker revalida presença antes de enviar; REMINDER usa o link da
  mensagem. Rede/timeout → retry até 10; não-membro/grupo inexistente → vínculo quebrado + jobs
  `CANCELLED`, sem perder a notificação no app nem o push.
- AC5 Cobrança DM preservada: `NotificationChannelsIntegrationTest` e
  `UazapiNotificationSenderTest` adaptados e verdes; cobrança continua DM idêntica.
- AC6 Privacidade: nenhum valor, nome de pessoa ou telefone no texto do grupo; central e push
  permanecem individuais e inalterados.
- AC7 Mobile: tela de preferências remove os switches de WhatsApp de avisos/presença e mantém o de
  cobrança; configurações avançadas do gestor (vincular com confirmação do nome do grupo, estado,
  desabilitar/reabilitar) preservam carregamento/erro/salvar com testes.
- AC8 Homologação (T0): spike contra grupo de teste real cobrindo U1–U5, registrado em
  `evidence.md`, antes de qualquer task que parseie respostas reais do Uazapi (T2 em diante).

## Limites

- Credenciais fora do git/logs; testes com servidor local e fakes, sem grupo real (exceto T0).
- Sem exactly-once remoto: `track_id` aceita duplicados; `ACCEPTED` não prova leitura.
- Instância única atende todos os grupos Saqz; grupo reduz volume (1 envio vs N DMs).
- Comunidades (`IsParent`, subgrupos) fora da v1; grupos incógnito rejeitados (anti-sequestro).
- Revalidação custa 1 chamada `group/info` por mensagem; cache é otimização futura.
- Push e central de notificações: **nenhuma mudança**.
