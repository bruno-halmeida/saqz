# Entrega ADM — condições de recebimentos

Implementados publicação explícita de termos versionados e tabelas PIX/CARD, vigência local convertida para Instant, simulação pelo backend e seis valores (base/taxas agregadas/total/líquido/comissão/custos). Mantido o framework DC e APIs existentes, distinguindo custos globais da seleção de meios por grupo.

Validação exata por strings/BigInt: reais para centavos, percentual para fração com dez casas máximas; rejeita inválidos antes da chamada. Campos começam vazios. Publicações incertas preservam UUID, URL e corpo integral, bloqueiam edição e permitem retry idêntico; 409 aparece explicitamente. Transição de sessão limpa rascunhos, resultados e pedidos e invalida respostas antigas.

Arquivos: adm-web/index.html; adm-web/tests/receivables-conditions.test.cjs; adm-web/tests/receivables-conditions-evidence.md.

45 testes Node passaram. Chromium visível com API e sessão mockadas passou validação, publicação, precisão, retry, conflito, simulação, PIX/CARD e logout sem erros JS. Evidência e comandos completos em adm-web/tests/receivables-conditions-evidence.md; screenshots /tmp/saqz-payment-adm-{desktop,tablet,simulation,retry,conflict}.png, script /tmp/playwright-test-payment-adm.js e log /tmp/saqz-payment-adm-node.txt.

Revisão visual efetuada e layout ajustado: termos em largura integral e tarifas em duas colunas adaptáveis. Não houve alteração de backend/mobile/docs/.specs, staging/branch/commit/push ou serviço externo; mudanças de docs observadas pertencem à outra frente.

Limitações: gate é mock, não integração real. Controller BigDecimal recebe strings decimais; revisar esse transporte no gate HTTP real. Respostas monetárias numéricas acima de Number.MAX_SAFE_INTEGER são recusadas para evitar arredondamento, enquanto entradas suportam Long.MAX_VALUE. Backend segue autoridade sobre disponibilidade dos termos e vigência; não foi criado GET de histórico de tarifas inexistente. Resta revisão independente e integração do coordenador.
