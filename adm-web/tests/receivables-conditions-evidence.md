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
