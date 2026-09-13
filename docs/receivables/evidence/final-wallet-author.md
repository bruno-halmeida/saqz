# Evidência do autor — carteira e saques (F1/T11)

Data: 2026-09-13. Autor: worker `task_fb8b1c2458d8`. Base original da onda: `384b5894`;
o gate inclui a dependência central já integrada em `91410d94` (`auth_time` verificado no principal).

## Entrega conectada

- Contrato HTTP e critérios: `docs/receivables/final-wallet-contract.md`.
- Modelos, portas e orquestração: `Wallets.kt:13-100` e `ManageWallet` em `Wallets.kt:102-226`.
  Leituras não consultam elegibilidade comercial; autorização de titular/delegado atual é refeita em
  toda chamada. Saque persiste/reserva antes do POST e recupera `REQUESTED`/`UNKNOWN`, inclusive pela
  chave estável `requestId` usada pelo mobile.
- HTTP: `WalletController.kt:67-171`, com resumo, extrato, destinos, saques e recuperação GET por
  requestId; cursor HMAC opaco e vinculado à conta em `WalletController.kt:18-42`; `auth_time` máximo
  de 5 minutos em `WalletController.kt:138-139`; `Cache-Control: no-store` e códigos tipados em
  `:154-171`.
- Asaas: `HttpAsaasWallet.kt:18-130`. Saldo e recebíveis vêm separadamente de `/finance/balance` e
  `/finance/payment/statistics?status=PENDING` (`:30-34`); extrato usa `/financialTransactions`
  (`:36-47`); saque envia decimal exato e `externalReference=operationId` (`:49-73`); recuperação só
  lista por essa referência (`:75-83`). Conversão recusa subcentavo/overflow (`:126-129`).
- Persistência: `JdbcWalletStore.kt:14-226`. Destino cifrado e idempotente (`:27-61`); operação e
  transferência são criadas na mesma transação sob lock, com reserva concorrente (`:63-104`);
  lease/`SKIP LOCKED` e distinção execute/recover (`:106-124`); conclusão atômica preserva reserva
  apenas no estado incerto (`:126-156`) e busca por requestId é isolada por conta (`:158-167`).
- Wiring real: `ReceivablesWalletConfiguration.kt`, usando `FinancialAccountRepository`,
  `FinancialOnboardingStore`, diretório atual de administradores, datasource, `FinancialSecrets`,
  relógio e URL configurada do Asaas.
- Banco: V56 adiciona idempotência/auditoria aos destinos existentes; V59 adiciona autorização,
  reserva, falha e atualização às transferências. Linhas históricas não recebem uma falsa marca de
  autorização explícita; a nova aplicação sempre a grava antes do I/O.

Não foram editados `PaymentProvider`, `OneOffPayments`, `HttpAsaasPayments`, `JdbcPaymentStore`,
`JdbcPaymentExecution`, `IdentitySecurityConfiguration`, `context.md` ou `direcionamento.md`. Não há
rota administrativa de saque e nenhuma funcionalidade de solicitação/execução de reembolso.

## Gate isolado

Scratch limpo: `/tmp/saqz-wallet-final.n7aAjJ`, criado com `git archive HEAD` e somente os 12 arquivos
de implementação/teste desta frente, sem `local.properties`, credenciais ou dados reais.

Comando (JDK 21):

```text
./gradlew :features:receivables:test \
  --tests br.com.saqz.receivables.application.ManageWalletTest \
  --tests br.com.saqz.receivables.adapter.output.asaas.HttpAsaasWalletTest \
  --tests br.com.saqz.receivables.adapter.input.http.WalletControllerTest \
  :features:receivables:integrationTest \
  --tests br.com.saqz.receivables.JdbcWalletStoreIntegrationTest \
  :bootstrap:compileKotlin :architecture-tests:test
```

Resultado final combinado: `BUILD SUCCESSFUL in 7s`, 32 tarefas Gradle (8 executadas, 24 atualizadas),
zero falhas, incluindo `:architecture-tests:test` e zero violações arquiteturais.

Testes exatos (19):

- `ManageWalletTest`: 6/6 — dinheiro após corte, autenticação recente, consentimento explícito,
  delegado removido, timeout/recuperação sem segundo POST, requestId e reserva insuficiente.
- `HttpAsaasWalletTest`: 7/7 — endpoints separados, centavos assinados/exatos, payload bancário,
  externalReference estável, recuperação única/ambígua, restrição por saldo e rejeição de subcentavo.
- `WalletControllerTest`: 2/2 — Bearer antigo rejeitado e cursor cruzado/adulterado rejeitado.
- `JdbcWalletStoreIntegrationTest`: 4/4 em PostgreSQL — cifra/idempotência, isolamento entre contas,
  reserva mantida no incerto/backoff e corrida concorrente com exatamente um vencedor.

Testes discriminantes cobrem mais de cinco falhas financeiras: arredondamento subcentavo, ausência de
consentimento, autenticação vencida, delegado removido, cursor de outra conta, conteúdo diferente no
mesmo requestId, timeout, recuperação ambígua, destino de outra conta e saque concorrente acima do
saldo observado.

## Limites reais

- Nenhuma chamada Asaas real ou homologação foi executada. O adaptador foi exercitado contra HTTP
  simulado segundo a documentação oficial consultada; credenciais/condições comerciais de sandbox
  continuam sendo pré-requisito externo de T20.
- O Asaas expõe data (não instante) no extrato usado; por isso o contrato retorna `occurredOn`.
- A reserva local fecha corridas entre requisições Saqz com o mesmo saldo remoto observado; débitos
  iniciados fora do Saqz ainda são arbitrados pelo próprio provedor e retornam rejeição, nunca sucesso
  presumido.
- A taxa nasce em zero e somente recebe o `transferFee` efetivamente devolvido pelo provedor; nenhum
  valor comercial foi inventado.
- O teste compartilhado `ReceivablesSchemaIntegrationTest` contém uma contagem literal antiga de
  migrações. O coordenador foi avisado para atualizar uma única vez após integrar também V57/V58/V60/V61;
  este worker não alterou o arquivo fora do ownership.

## Integração pelo coordenador

Após a entrega do autor, a integração corrigiu a consulta de saques PROCESSING até COMPLETED:
`ManageWallet.recover/recoverByRequest` e o lease JDBC agora aceitam esse estado como recuperação,
sem repetir POST. Timestamp futuro, mesmo com um segundo de diferença, é recusado; o limite exato
de 300 segundos continua válido. Testes HTTP, de aplicação e JDBC adicionados comprovam esses casos.
Gate em `/tmp/saqz-wallet-final.n7aAjJ/backend`: 18 testes de unidade/HTTP/Asaas + 5 JDBC,
zero falhas/erros/skips. Log `/tmp/saqz-final-wallet-backend-integration.log`, BUILD SUCCESSFUL.
