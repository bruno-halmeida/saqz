# ADM — publicação de condições, 2026-09-13

Implementação restrita a `adm-web/index.html`, sem novo framework. POSTs reais preparados para `/admin/receivables/terms`, `/fees` e `/fees/simulate`, usando `saqzAdmin.fetchAdmin` e envelopes `{value,requestId}`. Não existe GET novo de histórico de tarifas; o histórico anterior continua sendo de rollout.

## Verificação executada

- `node --test adm-web/tests/*.test.cjs`: 45/45 aprovados (35 anteriores + 10 testes comportamentais novos). Log: `/tmp/saqz-payment-adm-node.txt`.
- Chromium **visível**, HTML/DC real servido em `http://127.0.0.1:8124`; Firebase/sessão e API **explicitamente mockados** por interceptação, tráfego externo bloqueado. Script: `/tmp/playwright-test-payment-adm.js`, URL substituível por `TARGET_URL`; executor `/Users/bruno_almeida/.agents/skills/playwright-skill/run.js`.
- Navegador: entrada pela navegação, formulário vazio não publica, termos publicados explicitamente, perda de rede e retry byte a byte, campos bloqueados no resultado incerto, percentual `1,23456789` enviado como fração exata `0.0123456789`, simulação com seis valores vindos do mock, edição invalida a prévia, PIX/CARD, conflito 409 visível, nova publicação explícita e logout limpa campos. Nenhum erro JavaScript.
- Node: precisão em centavos até Long.MAX_VALUE, rejeição de negativos/notação exponencial/excesso de casas/limites/ausências, versões/conteúdo/vigência, ambos os meios, timeout controlado, identidade do retry, respostas atrasadas, 409, envelope inválido, 503, limpeza de sessão, rejeição de valores de resposta fora de Number.isSafeInteger.

## Revisão visual

Inspecionadas capturas reais em desktop (1440×1100) e tablet (800×1000). Ajustado o conteúdo dos termos para largura integral e altura de 150px; campos comerciais em duas colunas, passando a uma abaixo de 650px. Rótulos, vigência, ações explícitas, estado do pedido e seis resultados legíveis; custos do provedor e comissão apresentados separadamente.

- `/tmp/saqz-payment-adm-desktop.png`
- `/tmp/saqz-payment-adm-tablet.png`
- `/tmp/saqz-payment-adm-simulation.png`
- `/tmp/saqz-payment-adm-retry.png`
- `/tmp/saqz-payment-adm-conflict.png`

## Limites do gate

Mocks não homologam persistência/JDBC, autorização, Firebase ou provedor real. Valores financeiros de entrada são strings decimais exatas (BigDecimal no controller); nenhuma operação financeira usa ponto flutuante e não existe gross-up local. Respostas JSON numéricas acima do inteiro seguro do JavaScript são recusadas, sem mostrar valor arredondado; para exibir tais montantes seria necessário transporte decimal textual pelo backend ou parser JSON lossless. Vigência e vínculo dos termos continuam validados pelo servidor; a tela não inventa uma consulta administrativa inexistente. Sem defaults comerciais, deploy, staging, branch, commit ou push.

## Gate final do coordenador

Após revisão independente aprovada, rótulos ajustados para Pix/Cartão, Simular tarifas e
Taxas de serviço e pagamento. 45 testes Node passaram novamente. Chromium visível reexecutado
com o mesmo mock e asserts de jornada, sem erros JavaScript, usando
`/tmp/playwright-test-payment-adm-final.js`; imagens finais copiadas para
`docs/receivables/evidence/payments/adm-*.png`. Revisão independente e probe HTTP real
registrados em `docs/receivables/evidence/payments/adm-review.md`.

## Atualização — disponibilidade e origem das tarifas (2026-09-14)

- A toggle Ativo/Inativo está no topo de Recebimentos. Salvar ativa o mesmo público em BACKEND/MOBILE ou grava OFF nos dois, com motivo, versão, auditoria e retentativa preservados. Configurações antigas com liberação parcial são identificadas na tela.
- Removidos os campos editáveis de tarifas do provedor. Somente a comissão Saqz é enviada nos pedidos de publicação/simulação; a API rejeita tentativas de enviar tarifas Asaas. Detalhes do contrato: [origem das tarifas](../provider-fees.md).
- ADM: 66 testes Node aprovados (`/tmp/saqz-receipts-adm-tests.log`). Chromium visível com HTML real, sessão/API simuladas e tráfego externo bloqueado: toggle por teclado, gravação conjunta, público selecionado, estado confirmado, publicação, valores decimais, retry, conflito e logout, sem erros JavaScript (`/tmp/saqz-receipts-browser.log`).
- Backend: 74 testes unitários de Recebimentos, 8 testes JDBC de condições comerciais e 59 testes de integração de endpoints/fluxos aprovados. Incluem leitura HTTP simulada do Asaas, chave da conta recebedora, descontos, Pix com limites, precisão, indisponibilidade sem fallback, invalidação de prévias e preservação de ordens aprovadas. Build `:bootstrap:bootJar` gerado com JDK 21.
- Capturas desktop/tablet inspecionadas: `/tmp/saqz-receipts-toggle-desktop.png`, `/tmp/saqz-receipts-toggle-tablet.png`, `/tmp/saqz-receipts-adm-tablet.png`. Executor: `/tmp/playwright-test-receipts-toggle.js`, URL parametrizável por `TARGET_URL`.
- Mobile: 4 testes de gateway e 3 testes de tela aprovados; compilação Android/iOS e Detekt dos módulos alterados passaram. Capturas com/sem limites Asaas inspecionadas em `mobile/build/reports/receivables-configuration/tarifas-asaas-com-limites.png` e `tarifas-e-precos.png`. Logs: `/tmp/saqz-asaas-fees-mobile-tests.log` e `/tmp/saqz-asaas-fees-screenshots.log`.

Asaas real e produção não foram acionados. Sem deploy ou migração de banco.
