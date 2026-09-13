# Recheck independente — backend pagamentos F1/F2

Data: 2026-09-13. **Gate aprovado para o delta backend: F1 e F2 resolvidos, sem bloqueadores remanescentes demonstrados nesta revisão.** Os 11 probes originais passaram sem alteração, assim como os 28 testes de OneOffPaymentsIntegrationTest e o teste de PaymentFactsTest: **40 testes executados, zero falhas, erros ou skips**.

## Escopo e isolamento

HEAD avaliado: `564028ab87fd0ebf0e3780b86c8ecca388f517e5`, contra `de4707ef3ff1b864bc4693f445c460a3959db7d0`. Li `/tmp/saqz-payment-backend-review-final.md` e `/tmp/saqz-payment-backend-fix.md`; revisei o delta completo dos três arquivos backend (151 inserções, 21 exclusões), a aplicação das observações, locks, transações, unicidade dos movimentos e transições de PaymentFacts. Não há AGENTS.md aplicável nos ancestrais ou backend; mobile/AGENTS.md fica fora do escopo. Usei a skill orchestration e o guia fornecido pelo CLI para comunicação com o coordenador.

Criei `/tmp/saqz-payment-recheck-snapshot` por `git archive` do HEAD, incluindo **todos os 665 arquivos backend rastreados**, não somente o delta. Copiei a classe IndependentPaymentGateProbeTest original de `/tmp/saqz-payment-final-snapshot/backend/bootstrap/src/test/kotlin/br/com/saqz/bootstrap/IndependentPaymentGateProbeTest.kt` para a mesma posição no novo snapshot. Igualdade byte a byte confirmada, 11 métodos @Test, SHA-256 `97c1c35136d6470478ccc2cb32d98253bbad0633d82979f736181c8085561a7c`.

O manifesto `/tmp/saqz-payment-recheck-manifest.json` registra SHA-256 dos 665 arquivos antes do build. Após os testes, todos continuaram iguais ao manifesto; comparei também cada arquivo com seu blob Git. A única diferença do archive é LF/CRLF em backend/gradlew.bat, com igualdade após normalização. O HEAD permaneceu igual no final. Nenhuma fonte do checkout ou do snapshot foi editada; a única fonte acrescentada foi a cópia intacta dos probes. Não houve mutation Git, alterações em mobile/adm/docs/specs nem chamada à API Asaas real. As alterações concorrentes de documentação no checkout pertencem ao coordenador.

## F1 — resolvido

O probe original `probe first observation refunded must not create unearned commission credit` agora passa: REFUNDED + split REFUNDED + refundedSplits DONE, sem observação anterior DONE, produz soma de comissão zero.

Em `JdbcPaymentExecution.kt:150`, débito de comissão exige split liquidado e valor positivo. O helper em `:168` consulta débito e crédito efetivamente persistidos para aquele instrumento, dentro da transação e dos locks já existentes; retorno acima do débito, negativo ou divergente de crédito anterior gera REVERSAL_SPLIT_PENDING e não lança crédito. Os valores não são inferidos do quote. A unicidade `(account_id, kind, provider_reference)` e as referências distintas de débito/devolução mantêm idempotência.

A classe permanente passou também nas asserções de zero movimentos de comissão e ocorrência explícita após três conciliações da primeira observação REFUNDED; pagamento e reversão do caixa aparecem uma vez cada. Passaram as sequências DONE antes do refund, DONE tardio depois do refund inicial, repetição da devolução e retorno 301 contra débito 300 seguido de valor corrigido. A estratégia conservadora atende à alternativa expressamente aceita pelo gate anterior: impedir crédito desacompanhado de débito com pendência explícita.

## F2 — resolvido

O probe original `probe QR outage does not prevent cancellation of authenticated existing Pix` agora passa: Pix existente com QR 503 é cancelado e a baixa manual volta a ser permitida.

Em `HttpAsaasPayments.kt:89`, cancel usa recuperação sem enriquecimento visual. A leitura continua autenticada pela credencial da conta; externalReference é validada em `:104`, e meio, valor e paymentId conhecido são comparados em `:92–93` antes de qualquer DELETE. Somente ACTIVE autoriza exclusão; a resposta ainda deve conter deleted=true e o mesmo id. Os quatro casos de divergência (id, externalReference, billingType e value) passaram preservando CANCEL_PENDING/reserva, sem DELETE e sem novo POST.

Passaram também cancelamento sem consulta adicional de QR, criação durante QR 503 preservando ACTIVE/paymentId, DELETE incerto conservando reserva e recuperação posterior do mesmo pagamento ainda com QR indisponível. `enrichVisuals` mantém validação de URL de checkout e trata falhas apenas no enriquecimento, depois de construir os fatos financeiros; a aplicação preserva payload anterior quando o novo não está disponível.

## Revisão de regressões

Não encontrei regressão concreta bloqueadora no delta. Os outros nove probes originais também passaram: revogação do delegado em replay, customer ausente sem novo POST, divergência de valor mantendo inbox pendente, cancelamento real de jogo sem instrumento, webhook whitespace 400, consulta ACTIVE atrasada concorrendo com confirmação, cancelamento durante IO de customer impedindo payment POST, isolamento entre contas e falha SQL do inbox não reconhecida como sucesso/400.

Não houve alteração de migrations, contratos, algoritmo de locks, lease/fence, autorização da aplicação ou transições de PaymentFacts. O teste focado de fatos confirma que observação antiga não desfaz disponibilidade nem ressuscita instrumento estornado. A expansão de tolerância a falha visual pode deixar instrumento ACTIVE temporariamente sem QR/checkout, comportamento declarado pela correção; recuperações posteriores podem preencher esses campos e a reserva continua impedindo emissão concorrente.

Permanecem os limites declarados, sem novo bloqueador deste delta: a correção não repara créditos históricos indevidos, não implementa recomposição automática de devoluções parciais cumulativas e mantém ocorrências como histórico. Custo residual do provedor continua pendente de conciliação. Homologação com configuração e credenciais reais, produção, carga e durabilidade contra crash não foram atestadas; wallet, recorrência, UI e renovação Pix continuam fora do escopo. Os esclarecimentos documentais são trabalho paralelo do coordenador.

## Comandos e resultados

Preparação efetiva por Python/subprocess, equivalente aos comandos abaixo; extração usou tarfile e cópia usou shutil.copyfile, com assert de igualdade:

```sh
git rev-parse HEAD
git archive 564028ab87fd0ebf0e3780b86c8ecca388f517e5 backend
# Archive extraído integralmente em /tmp/saqz-payment-recheck-snapshot.
git diff de4707ef 564028ab87fd0ebf0e3780b86c8ecca388f517e5 -- backend
# Delta preservado em /tmp/saqz-payment-recheck-delta.diff.
git diff --check de4707ef 564028ab87fd0ebf0e3780b86c8ecca388f517e5 -- backend
```

Diff check passou. Execução única dos gates solicitados, sem cache de resultados e sem repetir suíte ampla:

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
/tmp/saqz-payment-recheck-snapshot/backend/gradlew \
-p /tmp/saqz-payment-recheck-snapshot/backend \
--offline --no-build-cache \
:bootstrap:test --tests '*IndependentPaymentGateProbeTest' \
--tests '*OneOffPaymentsIntegrationTest' \
:features:receivables:test --tests '*PaymentFactsTest' \
--rerun-tasks --console=plain \
> /tmp/saqz-payment-backend-recheck-tests.log 2>&1
```

Resultado: exit 0, **BUILD SUCCESSFUL in 30s**, 29 tasks executadas. Java conferido: OpenJDK Homebrew 21.0.12.1. XML registra PostgreSQL embarcado real **16.9**, localhost, e aplicação de **55 migrations**; o helper desliga durabilidade para bancos descartáveis. Provedor HTTP das fixtures aponta somente a servidor fake em 127.0.0.1, com access_token sintético; nenhuma URL de invoice foi acessada.

| Classe | Testes | Falhas | Erros | Skips |
|---|---:|---:|---:|---:|
| IndependentPaymentGateProbeTest, fonte original intacta | 11 | 0 | 0 | 0 |
| OneOffPaymentsIntegrationTest, HEAD corrigido | 28 | 0 | 0 | 0 |
| PaymentFactsTest | 1 | 0 | 0 | 0 |
| Total | 40 | 0 | 0 | 0 |

Evidências preservadas: `/tmp/saqz-payment-recheck-xml/` (três XMLs completos), `/tmp/saqz-payment-recheck-counts.json`, `/tmp/saqz-payment-backend-recheck-tests.log`, `/tmp/saqz-payment-recheck-manifest.json`, `/tmp/saqz-payment-recheck-delta.diff` e snapshot completo. Não houve retry de teste nem suite adicional. **Decisão: os dois bloqueadores do gate de de4707ef estão fechados neste HEAD; nenhuma correção adicional de backend é exigida por este recheck.**
