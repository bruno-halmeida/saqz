# Correções backend — F1/F2

Base inspecionada: de4707ef3ff1b864bc4693f445c460a3959db7d0, checkout compartilhado /Users/bruno_almeida/Private/saqz. Foram alterados somente três arquivos de backend, sem stage, commit, branch ou push. Li os três documentos solicitados, os probes independentes e o relatório final /tmp/saqz-payment-backend-review-final.md; não alterei docs, mobile, adm ou specs.

## Estratégia e comportamento

**F1 — comissão devolvida:** JdbcPaymentExecution só lança crédito contra débito efetivo de comissão já registrado a partir de split DONE, dentro da transação e dos locks existentes. O valor usa movimentos efetivos, nunca a comissão projetada no quote. Na primeira observação REFUNDED + split REFUNDED + refundedSplits DONE, sem débito anterior, não há movimento de comissão e é registrada REVERSAL_SPLIT_PENDING; pagamento e reversão do caixa continuam registrados uma única vez. Devolução acima do débito, valor negativo ou total devolvido divergente de crédito anterior também permanecem pendentes, sem crédito fabricado. Repetições não duplicam crédito; evidência tardia de split DONE permite registrar o débito e uma consulta posterior da devolução permite compensá-lo. Split com valor negativo não pode gerar crédito por inversão de sinal.

Esta implementação escolhe a alternativa conservadora aceita pelo revisor: não reconstrói um débito apenas porque o split aparece REFUNDED. Não inventa custo residual do provedor e conserva REVERSAL_PROVIDER_COST_PENDING. Não implementa recomposição de estornos parciais cumulativos nem reparo de dados históricos eventualmente já corrompidos pelo código anterior; divergência posterior fica explícita na ocorrência, sem lançar crédito adicional. As ocorrências existentes são histórico e não são apagadas por uma consulta posterior.

**F2 — Pix e QR:** HttpAsaasPayments constrói a observação financeira antes de enriquecer dados visuais. Cancelamento consulta o pagamento autenticado sem buscar QR ou invoiceUrl e só envia DELETE após validar externalReference, paymentId conhecido, meio e valor; resposta do DELETE ainda deve confirmar deleted=true e o mesmo id. Recuperação/criação preservam fatos financeiros quando QR ou outros dados visuais falham, e o armazenamento mantém os artefatos anteriores quando a consulta não traz novos. Assim o scheduler também consegue recuperar cancelamento incerto durante indisponibilidade do QR. Não há caminho novo de POST nem renovação de Pix.

## Arquivos

- backend/features/receivables/src/main/kotlin/br/com/saqz/receivables/adapter/output/asaas/HttpAsaasPayments.kt
- backend/features/receivables/src/main/kotlin/br/com/saqz/receivables/adapter/output/jdbc/JdbcPaymentExecution.kt
- backend/bootstrap/src/test/kotlin/br/com/saqz/bootstrap/OneOffPaymentsIntegrationTest.kt

## Regressões permanentes

Seis métodos novos reutilizam integralmente a fixture PostgreSQL e o servidor HTTP local existentes:

1. QR 503 após Pix criado: cancela sem outra consulta de QR, DELETE no mesmo pay_local, um único POST e baixa manual liberada.
2. QR 503 desde a criação: persiste ACTIVE/paymentId sem QR; DELETE incerto mantém reserva e retry após o prazo recupera o mesmo pagamento sem novo POST/instrumento.
3. Divergências de id, externalReference, meio e valor: cada variante impede DELETE e conserva CANCEL_PENDING/reserva mesmo com QR fora.
4. Primeira observação REFUNDED: soma e contagem dos movimentos de comissão são zero, ocorrência pendente explícita, pagamento/reversão únicos após três conciliações.
5. Split DONE antes do refund e evidência DONE tardia após primeiro refund: débito -300 e retorno +300 únicos, soma zero após repetição.
6. Retorno 301 contra débito 300 fica pendente sem crédito; consulta corrigida para 300 zera a comissão.

## Validação executada

JDK21: /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home. Testes usam PostgreSQL real embarcado e HTTP Asaas simulado exclusivamente em 127.0.0.1, sem chamadas Asaas reais.

Comando focado principal (JAVA_HOME acima):

```sh
./backend/gradlew -p backend --offline --no-build-cache :bootstrap:test --tests br.com.saqz.bootstrap.OneOffPaymentsIntegrationTest :features:receivables:test --tests br.com.saqz.receivables.domain.PaymentFactsTest --console=plain
```

Uma primeira tentativa de compilação encontrou parêntese excedente na fixture nova, corrigido antes da execução da classe. PaymentFactsTest executou nessa tentativa: 1 teste aprovado. A execução seguinte da classe de integração executou 28 testes: 27 aprovados e somente a fixture nova de retry falhou por não avançar next_attempt_at com o relógio fixo; os dois cenários reportados pelo revisor passaram. Ajustei apenas essa fixture com a mesma liberação de prazo usada pelo teste de cancelamento existente e reexecutei somente o método falho:

```sh
./backend/gradlew -p backend --offline --no-build-cache :bootstrap:test --tests '*OneOffPaymentsIntegrationTest.QR outage preserves*' --console=plain
```

Resultado: BUILD SUCCESSFUL, 1 teste aprovado. Cobertura final agregada: **28 testes de integração + 1 PaymentFactsTest aprovados**, nenhum erro/skip, sem repetir a suíte inteira ou a classe inteira após gates suficientes. Os 27 testes previamente verdes não foram reexecutados após o ajuste exclusivo de prazo do método falho. Não executei os probes na cópia do revisor; seus dois casos foram incorporados e ampliados na fixture permanente.

Evidências: /tmp/saqz-payment-backend-fix-tests.log (execução 27/28), /tmp/saqz-payment-backend-fix-retry.log (retry verde), /tmp/saqz-payment-backend-fix-xml/initial-integration.xml, /tmp/saqz-payment-backend-fix-xml/retry-integration.xml e /tmp/saqz-payment-backend-fix-xml/PaymentFactsTest.xml. git diff --check -- backend passou; diff revisado integralmente, sem migrations, contratos HTTP ou módulos adicionais.

Falta o gate independente do delta e a integração pelo coordenador. Homologação financeira real permanece fora deste passe; wallet, recorrência, UI e renovação Pix não foram ampliados.
