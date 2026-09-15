# Verificação independente — backend de notificações

## Rodada 2 — PASS no escopo backend

Revalidação de AC1–4 e porção backend de AC6–7 no snapshot do workspace baseado em `b28d0263862970945302d540bfb2aca1a77a5b24`, com alterações ainda não commitadas de V71/V72, resolução do link, cadastro, convite e testes. A rodada inicial abaixo fica preservada como histórico; seus dois gaps foram resolvidos. AC5 e navegação/retomada mobile de AC6–7 ficam com o verificador de mobile/integração. **Não é declaração de conclusão da feature inteira.**

### Execução independente

Cópia isolada, sem diretórios build/cache, em `/var/folders/y5/qpphs0gs7558vcgg6pjplblh0000gn/T/saqz-notification-reverify-lyqe_7xh/backend`:

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew \
  :features:groups:test --tests '*UazapiNotificationSenderTest' \
  --tests '*Attendance*Test' --tests '*RedeemInviteTest' \
  :features:groups:integrationTest --tests '*NotificationChannelsIntegrationTest' \
  --tests '*ChargeReminder*' --tests '*GroupCommunication*' \
  --tests '*Attendance*Test' --tests '*Invite*Test' \
  :bootstrap:test --tests '*AttendanceShareEndpointIntegrationTest' \
  --tests '*GroupCommunicationEndpointIntegrationTest' --console=plain
```

**PASS: 160 unitários + 199 JDBC + 18 HTTP = 377 testes, zero falhas/ignores.** As três tasks de teste executaram de fato. Resultados XML preservados na pasta `baseline-results` ao lado da cópia isolada.

### Evidência por critério

| AC | Resultado backend | Evidência |
|---|---|---|
| AC1 | PASS | Spec explicita agora 408 como transitório, alinhada ao teste/implementação. Quatro testes do SDK real contra loopback passam. M1 da rodada anterior continua sendo evidência discriminante de Retry-After, com fontes do adaptador inalteradas. |
| AC2–3 | PASS | Reexecutados os testes de independência, defaults, replay, retry, estados terminais, opt-out, associação e cancelamento. Nenhuma regressão com V71/V72. |
| AC4 | PASS | Novo `chat push delivers without WhatsApp and respects its own preference` testa destinatário, conteúdo, ID e opt-out. `presence sends app link...` testa envio positivo do push REMINDER e mensagem WhatsApp com link. M2 agora é morto. |
| AC6 | PASS no backend | Novo teste faz replay do lembrete e mantém um único link, resolve jogo/grupo sem criar attendance, exige associação ativa e rejeita estranho/inativo. Resolução é POST autenticado; confirmação continua no PUT self existente, cujo DTO só aceita requestId/intent. `AttendanceControllerTest`, `AttendanceTransitionPolicyTest`, `JdbcAttendanceCommandRepositoryIntegrationTest` e sensor de concorrência exercitam identidade, prazo, capacidade, fila e replay sem nova linha/evento/cobrança. HTTP de resolução preserva códigos de erro e limitação de tentativas. GET não é caminho de confirmação. |
| AC7 | PASS no backend | Mesmo código de notificação é aceito por `RedeemInvite`, aplicando aprovação e limite existentes. Teste integrado verifica novo vínculo, registrationRequired=true, salvamento do próprio perfil, registrationRequired=false para o mesmo grupo/jogo, pending sem acesso e ausência de attendance durante cadastro/resolução. Checks adicionais independentes abaixo confirmam limite no código real do lembrete e migração histórica. Retomar o link após formulário depende da validação mobile. |

### Checks adicionais do verificador (somente scratch)

1. No teste do código de notificação, criar `RedeemInvite` com limite de atletas zero e tentar um novo usuário: esperado `AthleteLimitExceeded`, duas associações originais preservadas, sem criar nova associação. **PASS**.
2. Migrar banco até V71, criar cadastro histórico, migrar até V72: histórico continua concluído. Criar novo vínculo após V72: cadastro começa não concluído. **PASS**.

Executados por `:features:groups:integrationTest --tests '*NotificationChannelsIntegrationTest' --tests '*AttendanceShareMigrationIntegrationTest'`: **22 testes, zero falhas**. XML em `independent-results` ao lado da cópia. Estes checks foram adicionados apenas na cópia temporária, não nas fontes reais.

### Sensor M2 repetido — KILLED

Na cópia, substituir `WHEN 'CHAT' THEN coalesce(p.push_messages, true)` por `WHEN 'CHAT' THEN false` em V70, repetindo exatamente o defeito da primeira rodada. Executar nove testes de `NotificationChannelsIntegrationTest`: uma falha esperada em `chat push delivers without WhatsApp and respects its own preference`, linha 162, porque não houve entrega. O restante passa. Gap discriminante resolvido. A mutação de produção foi restaurada após o teste.

Nenhum novo gap backend confirmado. Não foram usadas credenciais reais nem feitas chamadas externas de mensagens. Artefatos do provedor, configuração de app links e entrega real continuam fora desta validação. A retomada mobile após login/cadastro/aprovação será verificada separadamente.

---

# Histórico — primeira rodada

## Veredicto: FAIL (cobertura discriminante e precisão da especificação)

Escopo: AC1–4 de `spec.md`; diff `3f0d63fa^..97b1d5e1`. Verificador independente do autor. AC5/mobile e AC6/identidade do link não foram avaliados; este relatório não conclui a feature inteira.

Nenhum defeito funcional foi confirmado no código normal nesta revisão. Os testes existentes passam, mas o sensor M2 demonstra que podem passar sem o push de conversa exigido em AC4. Há também divergência explícita entre AC1 e a classificação de HTTP 408.

## Gates executados

Com JDK 21, no diretório `backend`:

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew \
  :features:groups:test --tests '*UazapiNotificationSenderTest' \
  :features:groups:integrationTest --tests '*NotificationChannelsIntegrationTest' \
  --tests '*ChargeReminder*' --tests '*GroupCommunication*' \
  :bootstrap:test --tests '*GroupCommunicationEndpointIntegrationTest' --console=plain
```

Resultado: BUILD SUCCESSFUL. SDK: 4 testes; JDBC: 22 testes (7 comunicação, 8 cobrança, 7 multicanal), todos sem falhas/ignores. HTTP: task UP-TO-DATE, XML de 6 testes sem falhas/ignores; não reivindico uma nova execução desses seis testes nesta rodada. A primeira tentativa foi rejeitada pelo gate do projeto porque o shell usava JDK 17; a execução acima corrigiu o ambiente.

Evidências legíveis por máquina: `backend/features/groups/build/test-results/{test,integrationTest}/TEST-*.xml` e `backend/bootstrap/build/test-results/test/TEST-br.com.saqz.bootstrap.GroupCommunicationEndpointIntegrationTest.xml`.

Nenhuma credencial real foi usada e nenhuma mensagem externa foi enviada. SDK exercitado contra HTTP em loopback; JDBC usa PostgreSQL local de teste e senders falsos.

## Resultado por critério

| AC | Resultado | Evidência e limites |
|---|---|---|
| AC1 | SPEC_DEVIATION | `UazapiNotificationSenderTest` verifica autenticação de instância, número sem `+`, texto, `track_id`, aceite, 429/7200s, erros HTTP e rede. Sensor M1 morto. Porém `UazapiNotificationSender.kt:20` e o teste classificam 408 como transitório, enquanto AC1 diz demais 4xx permanentes. Recomendo explicitar 408 na spec se essa é a decisão pretendida. |
| AC2 | PASS | Testes de opt-in/default, independência app/push/WhatsApp, persistência e cobrança na central. Endpoint testa payload antigo preservando WhatsApp; V70 migra escolhas antigas para push e inicia WhatsApp desligado. |
| AC3 | PASS nos caminhos verificados | V70 cria filas por trigger AFTER INSERT na mesma transação; PK por notification_id impede duplicação local. Teste de replay cria um job; falha WhatsApp mantém central e envio push; testes cancelam por opt-out, saída, mudança de telefone, prazo vencido e cobrança cancelada. Retry-After e limite de 10 tentativas verificados. Workers reconsultam elegibilidade; WhatsApp tem estados terminais, push permite consultar conclusão/limite por completed_at/attempts. Não há simulação de queda após aceite remoto; trackId não garante exactly-once do provedor. |
| AC4 | FAIL — sensor sobrevivente | Opt-in, exclusão de conversa no WhatsApp, ausência de telefone e destinatário privado têm testes. Push NOTICE tem teste positivo; cobrança tem cobertura anterior de entrega/destinatário/retentativa. Porém M2 elimina push CHAT e todos os 22 testes JDBC continuam verdes. O teste REMINDER verifica cancelamento por prazo, sem envio positivo. |

## Sensores de discriminação

Executados em cópia isolada obtida por `git archive 97b1d5e1 backend`, em `/var/folders/y5/qpphs0gs7558vcgg6pjplblh0000gn/T/saqz-notification-verifier-th65dy94`. Fontes reais não foram alteradas. Mutações foram restauradas depois da execução; relatórios de teste da cópia permanecem disponíveis.

- **M1 — KILLED:** substituir o atraso de 429 no adaptador por `WhatsAppDelivery.Retry(60)`. `:features:groups:test --tests '*UazapiNotificationSenderTest'` falha em `rate limit preserves provider minimum delay`, linha 43: esperado 7200, recebido 60. Quatro testes executados, uma falha esperada. Confirma que o teste detecta perda do mínimo do provedor.
- **M2 — SURVIVED:** após restaurar M1, substituir exclusivamente `WHEN 'CHAT' THEN coalesce(p.push_messages, true)` por `WHEN 'CHAT' THEN false` em V70. `:features:groups:integrationTest --tests '*NotificationChannelsIntegrationTest' --tests '*ChargeReminder*' --tests '*GroupCommunication*'` executa 22 testes, zero falhas. O mutante viola diretamente AC4 removendo todos os pushes de conversa.

## Correções necessárias para novo gate

1. **P2 — AC4:** adicionar testes positivos de entrega de push para CHAT e REMINDER, exercitando preferências do canal/categoria, destinatário e conteúdo sem valores financeiros. O teste de CHAT precisa falhar contra M2. Manter o teste de WhatsApp que exclui conversa.
2. **P2 — AC1:** alinhar spec e implementação sobre HTTP 408. A exceção transitória é razoável, mas o teste atual afirma um resultado contrário à frase literal do critério.

Lição candidata fundamentada em M2, para consolidação pelo autor: “Teste a entrega positiva de cada combinação de evento e canal suportada, além dos casos de cancelamento e exclusão.” Não alterei arquivos de lições compartilhados nesta revisão delimitada; o relatório consolidado deve registrar esse sinal via o script da skill.
