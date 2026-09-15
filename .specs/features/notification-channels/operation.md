# Notificações: app, push e WhatsApp

## Modelo

- Uma notificação por mensagem e destinatário, com leitura persistida no app.
- Categorias de app independentes de push e WhatsApp. Cobrança sempre permanece na central.
- Push: avisos, conversa, presença e cobrança. Credenciais FCM/APNs e permissão do sistema continuam necessárias.
- WhatsApp: avisos, presença e cobrança individuais, somente após ativação explícita no perfil. Conversa não é encaminhada.
- Usa o telefone do perfil no formato internacional. Ausência de telefone impede criar o envio; troca do telefone cancela o envio pendente.
- Lembretes de presença incluem link HTTPS Branch. O link identifica o jogo, não o número do visitante. Conta autenticada e associação ativa autorizam a confirmação; o link pode ser encaminhado a outro membro, que confirma somente a própria presença.
- Para quem ainda não faz parte do grupo, o mesmo código permite resgatar entrada pela política de convites existente, incluindo aprovação do administrador e limite de atletas. Novo atleta completa o cadastro vinculado ao grupo antes de confirmar; depois do salvamento, o app retoma o link. Cadastros históricos são preservados.
- Abrir o link sem login preserva o destino até autenticar. O app resolve o jogo e envia a confirmação pela API autenticada existente. Capacidade/prazo/lista de espera permanecem controlados pelo servidor. GET/preview do WhatsApp nunca confirmam presença.
- Reabertura/retry usa requestId estável por link; não repete a operação já registrada para a mesma conta/jogo.

## Configuração

SDK: `com.github.thoughtbruno:uazapi-connector-sdk:0.1.0`, dependency de produção.
O artefato é publicado pelo JitPack a partir do release da tag no GitHub
(`https://github.com/thoughtbruno/uazapi-connector-sdk/releases/tag/0.1.0`).
`backend/settings.gradle.kts` usa o JitPack **exclusivamente** para esse módulo e Maven Central para os
demais, então o build remoto (Docker/CI) resolve o SDK sem depender do Maven local da máquina.

Variáveis Spring Boot:

```text
SAQZ_NOTIFICATIONS_WHATSAPP_ENABLED=true
SAQZ_NOTIFICATIONS_WHATSAPP_BASEURL=https://saqzapp.uazapi.com
SAQZ_NOTIFICATIONS_WHATSAPP_TOKEN=<segredo da instância>
SAQZ_NOTIFICATIONS_WHATSAPP_DELAYMS=15000
```

A propriedade canônica é `saqz.notifications.whatsapp.base-url` (e `delay-ms`); use as formas acima ao configurar variáveis de ambiente. O token não é armazenado no repositório. A instância deve estar conectada. Desabilitado por padrão; não há conexão/pareamento ou envio real nos testes.
O domínio `saqz.branch.domain` precisa corresponder aos App Links/Universal Links já configurados no Android/iOS.

## Entrega e recuperação

Enfileiramento ocorre na mesma transação de criação da notificação. Replay do comando de origem não cria duplicatas. Workers revalidam grupo ativo, associação, escolhas de canal e validade do evento.

`notification_whatsapp_queue`:

- `PENDING`: aguardando tentativa; `next_attempt_at` e `attempts` indicam a próxima execução.
- `ACCEPTED`: Uazapi aceitou o envio; não prova entrega ao celular nem leitura.
- `FAILED`: erro permanente (4xx exceto 408/429) ou dez tentativas transitórias.
- `CANCELLED`: destinatário/evento/preferências/telefone deixaram de ser elegíveis.

429 respeita o maior entre Retry-After do provedor e backoff local; rede, 408 e 5xx retentam. O rastreio `notification-<id>` não é garantia de deduplicação da Uazapi. Se o provedor aceitar e a resposta se perder, a retentativa pode duplicar a mensagem externa. Não há promessa de exactly-once remoto.

Push preserva recibos por instalação, revoga tokens inválidos e limita retentativas a dez. Falha no WhatsApp não elimina o registro no app nem o job de push. Nenhum valor de cobrança vai no texto do push.

Não reative indiscriminadamente jobs FAILED: primeiro corrija credencial/conexão, avalie entrega incerta e confira destinatário/evento. Habilitar WhatsApp não reenvia histórico anterior ao opt-in. Desligar o worker pausa jobs pendentes; os controles do usuário continuam sendo reavaliados quando retomar.

## Verificação

Testes HTTP do SDK usam servidor local. Testes de filas usam PostgreSQL descartável. Evidências em `.specs/features/notification-channels/`.
Homologação em dispositivo e entrega real continuam necessárias antes da ativação em produção.
