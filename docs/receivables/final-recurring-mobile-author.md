# Relatório de autoria mobile — recorrência, renovação Pix e comprovante

**Data:** 2026-09-13\
**Frente:** F3 / F4 / T16\
**Contrato:** `docs/receivables/final-recurring-mobile-contract.md`\
**Scratch final:** `/tmp/saqz-recurring-final.g10y5i/mobile`

## Resultado

A jornada compartilhada Android/iOS agora descobre a recorrência do próprio pagador, revisa valores
literais e termos reais, exige aceite explícito, cancela com estado intermediário remoto e retoma
somente após nova revisão/novo aceite. A recorrência Pix é apresentada como cobrança mensal manual;
cartão abre apenas checkout hospedado Asaas e retorno de navegador nunca confirma pagamento.

O pagamento existente ganhou renovação de Pix vencido preservando obrigação, instrumento, pagamento
do provedor, quote e snapshot financeiro. Tentativas são registradas antes da rede apenas com
ator/conta/grupo/recurso/operação/request; nome e documento ficam em memória, e a recuperação por
`requestId` é somente leitura. O comprovante textual só é exportável após estado remoto final e a UI
só anuncia sucesso ou falha depois do callback real de `ReceiptExportPort`; o adapter sobre
`NativeSharePort` e o wiring de navegação são propriedade do coordenador.

O implementador backend B (`dispatch:ctx_dd29ce0ad892`) confirmou durante a execução as rotas de
descoberta própria e as duas recuperações por `requestId` documentadas no contrato mobile; portanto
nenhuma retomada após timeout depende de repetir POST nem de rota inferida.

Não foi adicionada ação de solicitar, aprovar ou executar reembolso. Estados externos históricos de
reembolso, disputa, cancelamento e liquidação continuam prevalecendo na apresentação.

## Evidência por requisito

| Requisito | Implementação | Teste principal |
|---|---|---|
| Descoberta própria e recovery por request | `KtorRecurrenceGateway.kt:13-86` | `KtorRecurringPaymentsGatewayTest.kt:17-103` |
| Dinheiro literal, termos e fingerprint | `Recurrence.kt`, `RecurrenceViewModel.kt:63-112` | `RecurringPaymentsTest.kt:15-35`; `RecurrenceViewModelTest.kt:35-67` |
| Marker antes da rede, PII só memória | `RecurrenceViewModel.kt:100-199`; `MemberPaymentViewModel.kt:177-238` | `RecurrenceViewModelTest.kt:69-99`; `MemberPaymentViewModelTest.kt:220-258` |
| Cancelamento e retomada explícita | `RecurrenceViewModel.kt:100-154` | `RecurrenceViewModelTest.kt:101-124` |
| Geração/sessão stale | `RecurrenceViewModel.kt:160-216`; `MemberPaymentViewModel.kt:267+` | `RecurrenceViewModelTest.kt:126-139`; `MemberPaymentViewModelTest.kt:120-139` |
| Pix expirado, mesma obrigação, plano cortado | `PixRenewal.kt`; `KtorPixRenewalGateway.kt`; `MemberPaymentViewModel.kt:177-238` | `RecurringPaymentsTest.kt:37-48`; `KtorRecurringPaymentsGatewayTest.kt:105-143`; `MemberPaymentViewModelTest.kt:220-258` |
| Checkout Asaas/callback não confirma | `Recurrence.kt`; `MemberPaymentContract.kt`; telas comuns | `KtorRecurringPaymentsGatewayTest.kt:94-103`; `MemberPaymentViewModelTest.kt:160-218,285-295` |
| Comprovante e callback real | `ReceiptExport.kt:3-10`; `ReceiptExportText.kt:8+`; `MemberPaymentViewModel.kt:240-258` | `RecurringPaymentsTest.kt:50-58`; `MemberPaymentViewModelTest.kt:260-276` |
| UI Android/iOS | `RecurrenceScreen.kt:34+`; `MemberPaymentScreens.kt:71-85` | `RecurrenceScreenTest.kt:12-49`; `RecurrenceScreenshotTest.kt:25+`; `MemberPaymentScreenshotTest.kt` |

## Gates executados em scratch

| Gate | Resultado |
|---|---|
| `:features:receivables:domain:testAndroidHostTest :features:receivables:data:testAndroidHostTest` | PASS; 65 testes combinados, 0 falhas/erros/skips |
| `:features:receivables:domain:iosSimulatorArm64Test :features:receivables:data:iosSimulatorArm64Test` | PASS; 65 testes combinados, 0 falhas/erros/skips |
| `:features:receivables:presentation:iosSimulatorArm64Test` | PASS; 105 testes integrados; após refatoração do lint, 103/103 no scratch de autoria (2 testes concorrentes ausentes) |
| `:android-app:testDevDebugUnitTest --tests '*RecurrenceScreenshotTest*' --tests '*MemberPaymentScreenshotTest*'` | PASS; composição Android integrada; 5 testes, 0 falhas |
| `:android-app:recordRoborazziDevDebug` com os mesmos filtros | PASS; 42 PNGs (7 recorrência, 35 pagamento) |
| detekt dos módulos | PASS no gate integrado final; zero suppressions nesta frente |
| whitespace/diff | PASS nos arquivos tracked alterados (`git diff --check`) e sem whitespace final nos novos fontes Kotlin/XML |
| gate integrado do coordenador (`/tmp/saqz-final-mobile-runtime.log`) | PASS em 1m21s: runtime/DI, `SaqzKoinBootstrapTest`, restauração, data/presentation `allTests`, detekt de todos os módulos alterados e Android completo |

Os builds ocorreram somente no scratch, com `local.properties` copiado para descoberta do SDK e sem
segredos, publicação, chamada Asaas real ou operação git mutante.

## Capturas inspecionadas

Foram abertas e inspecionadas visualmente as capturas abaixo no scratch final:

- `recorrencia-revisao.png`: valores literais R$ 100,00 / R$ 4,90 / R$ 104,90, termos, pagador e aceite desmarcado/botão desabilitado.
- `recorrencia-cancelamento-pendente.png`: incerteza explícita, `Cancelamento em confirmação`, cutoff com timezone e ação apenas de atualizar.
- `recorrencia-encerrada-retomada.png`: linha anterior encerrada e instrução de nova revisão/novo aceite.
- `recorrencia-cartao-checkout.png`: estado de autorização e CTA textual `Abrir checkout seguro do Asaas`.
- `prazo-pix-vencido-renovacao.png`: Pix expirado, mesma identificação de pagamento, dinheiro preservado e novo vencimento.
- `comprovante-compartilhado.png` e `comprovante-falha.png`: feedback distinto após callback real, sem sucesso otimista.

As demais capturas geradas cobrem vazio/loading/aceite, termos ausentes, documento incorreto, tentativa
incerta, QR/copiar/fallback, checkout e todos os estados remotos conhecidos/desconhecidos. A captura
de `REFUNDED` preserva o fato externo apenas como histórico/status; não existe CTA de reembolso.

## Arquivos de autoria

Novos: `Recurrence*`, `PixRenewal*`, `ReceiptExport*` em domain/data/presentation e testes; recurso
`recurrence.xml`; `RecurrenceScreenshotTest.kt`; este relatório e o contrato mobile. Alterados:
`MemberPaymentContract.kt`, `MemberPaymentViewModel.kt`, `MemberPaymentRoots.kt`,
`MemberPaymentScreens.kt`, `strings_member_payment.xml`, testes comuns/iOS de MemberPayment e
`MemberPaymentScreenshotTest.kt`.

O coordenador recebeu cedo a assinatura de `ReceiptExportPort` e o patch exato de wiring:
`RecurrenceRoute(accountId, groupId)`, `RecurrenceRoot(accountId, groupId, onBack)` e callback
`MemberPaymentRoot.onOpenRecurrence(accountId, groupId)`. Nenhum arquivo de backend, módulo DI,
nav host, Wallet, FinancialOnboarding, Management, NativeAuth, bootstrap ou plataforma foi alterado
por esta autoria.

## Pendência deliberada

A revisão fresca `autor != verificador` permanece separada para o reviewer que o coordenador deve
despachar depois desta entrega. Homologação real com provedor também permanece fora do gate local.

## Fechamento integrado posterior à autoria

O relatório acima conserva a evidência da autoria. A revisão independente final encontrou e o
coordenador corrigiu os casos de HTTP202, POST automático após timeout, datas divergentes/inválidas,
callback de share durante refresh, sessão enfileirada, consentimento após preview recusado, cancelamento
não confirmado e overflow monetário. As regressões independentes foram retidas nos testes do produto.
A UI relata “Compartilhamento aberto.” após callback do launcher, sem afirmar entrega ao destinatário.
Resultado final PASS em `evidence/final-recurring-mobile-review.md`; commits `6f700201`/`777abef4`.
