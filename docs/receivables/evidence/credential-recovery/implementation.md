# T04 — entrega de recuperação operacional de credencial

2026-09-13. Escopo decidido pelo coordenador: importação validada/cifrada de chave obtida manualmente no Asaas; nenhum POST de emissão de chave ou subconta. Arquivos anteriores de onboarding foram usados como evidência e ficaram intactos por este worker, sem Git mutations, chamadas Asaas reais ou outros agentes.

## Conclusão e contrato

A API oficial permite emitir nova chave para a mesma subconta, mas exige janela manual de duas horas, whitelist e elegibilidade da operação. A chave perdida não pode ser consultada novamente; listar chaves retorna metadados. Isso é dependência operacional documentada, não ausência de integração local. [Gestão de chaves](https://docs.asaas.com/docs/gerenciamento-de-chaves-de-api-de-subcontas), [emissão](https://docs.asaas.com/reference/criar-chave-de-api-para-uma-subconta), [listagem](https://docs.asaas.com/reference/listar-chaves-de-api-de-uma-subconta).

`RecoverFinancialCredential(store, provider, clock).recover(command, RecoveryCredential(secret))` não devolve segredo. Comando identifica request, conta local, titular, CREATE_ACCOUNT original, operador, id remoto e wallet. Resultados: RECOVERED, ALREADY_RECOVERED, CONFLICT, NOT_ELIGIBLE, IDENTITY_MISMATCH, UNAVAILABLE. O entrypoint é operacional e sua autenticação/controle de acesso ao deployment pertence ao chamador; a aplicação não publica endpoint HTTP dessa importação.

`HttpAsaasCredentialRecovery(URI, platformApiKey)` executa somente GET: listagem filtrada de subcontas com chave da conta-pai, dados comerciais e carteira com chave candidata. Exige lista completa única, mesmos CPF/CNPJ/id/wallet, e bloqueia redirects. Documentos malformados/ambíguos e respostas de erro não autorizam attach; erros não propagam bodies/headers. [Subcontas](https://docs.asaas.com/reference/listar-subcontas), [titular](https://docs.asaas.com/reference/recuperar-dados-comerciais), [carteira](https://docs.asaas.com/reference/recuperar-walletid).

`JdbcFinancialCredentialRecovery(DataSource, FinancialSecrets)` reserva solicitação durável com candidata cifrada, valida owner e HMAC de identidade, exige CREATE_ACCOUNT UNKNOWN sem credencial, protege request com lock e reconfere versão/estado após HTTP. Uma transação anexa chave cifrada por conta/finalidade, resolve a operação original e grava auditoria IMPORTED imutável. V63 contém duas tabelas: receivable_credential_recoveries e receivable_credential_recovery_events; esta última rejeita UPDATE/DELETE. Não muda aprovação financeira nem habilita novos negócios.

Uma intenção PENDING pode ser consultada/validada novamente com request e segredo originais; outro conteúdo conflita. Importação distinta somente pode vencer enquanto não existe credencial local, e nenhum caminho emite recurso remoto. Retentativa exata após commit retorna ALREADY_RECOVERED antes de consultar o provedor. A janela de criação original é comprovada pelo teste com POST inicial cuja resposta omite apiKey: UNKNOWN, três GETs da importação, SUCCEEDED, replay e provisionamento posteriores sem outro POST.

## Validação reproduzível

Scratch exclusivo: `/tmp/saqz-credential-recovery-worker/backend`, copiado do working tree excluindo build/.gradle/.kotlin. JDK21: `/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`. Comando final:

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
  ./gradlew :features:receivables:integrationTest \
  --tests '*FinancialCredentialRecoveryIntegrationTest' \
  --tests '*FinancialOnboardingIntegrationTest' \
  :architecture-tests:test --offline --console=plain
```

**42/42 passaram, zero falhas/erros:** 12 recuperação nova, 10 onboarding existente e 20 arquitetura. Log: `/tmp/saqz-credential-recovery-worker/tests-final.log`; XMLs sob `backend/features/receivables/build/test-results/integrationTest` e `backend/architecture-tests/build/test-results/test` dentro do scratch.

Cobertura significativa: resposta original perdida sem duplicar subconta; replay durável após reinicializar objetos; conteúdo/operador/segredo diferente; proprietário/operação estrangeiros; estados não elegíveis; credencial existente; id/carteira/referência conhecidos divergentes; lista ausente/ambígua/truncada/malformada/estrangeira; credencial com outro titular/carteira; falha HTTP sanitizada; cifragem; evento imutável; rollback de attach/operação/evento em falha de escrita e falha no evento final; duas importações concorrentes com um vencedor; versão alterada durante validação.

Sem testes reais de credencial, contrato BaaS, whitelist ou ambiente Asaas. A prova HTTP usa MockWebServer em localhost e credenciais sintéticas; persistência usa PostgreSQL de testes.

## Arquivos de propriedade deste worker

- `backend/features/receivables/src/main/kotlin/br/com/saqz/receivables/application/FinancialCredentialRecovery.kt`
- `backend/features/receivables/src/main/kotlin/br/com/saqz/receivables/adapter/output/asaas/HttpAsaasCredentialRecovery.kt`
- `backend/features/receivables/src/main/kotlin/br/com/saqz/receivables/adapter/output/jdbc/JdbcFinancialCredentialRecovery.kt`
- `backend/features/receivables/src/main/resources/db/migration/V63__financial_credential_recovery.sql`
- `backend/features/receivables/src/integrationTest/kotlin/br/com/saqz/receivables/FinancialCredentialRecoveryIntegrationTest.kt`
- `docs/receivables/credential-recovery-runbook.md`
- Este relatório.

## Integração restante de coordenação

O coordenador adicionou separadamente comando NON_WEB e parser/entrada secreta por stdin/console, e é responsável pelos seus gates e pela atualização de inventário de schema/T04. Este relatório não reivindica autoria ou validação desse entrypoint. O [runbook](../../credential-recovery-runbook.md) contém o comando integrado e decisões de recuperação; habilitação e emissão manual no Asaas/homologação continuam dependências externas explícitas.
