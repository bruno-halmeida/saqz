# Evidências — coupon-analytics

## T1 cobertura e necessidade
Todos os caminhos citados abaixo estão em `backend/bootstrap/src/test/kotlin/br/com/saqz/bootstrap/AdminCouponAnalyticsIntegrationTest.kt` (HTTP real e PostgreSQL). Cada teste é necessário para os critérios indicados; nenhum teste anterior alterado ou ignorado.

| Critério / teste | Linha e assertion | Resultado da spec |
|---|---|---|
| AC1/2: catálogo completo, seleção não é uso | :85 `assertEquals(2,r["coupons"].size())`; :87 IDs distintos; :90 status; :91 users=0 e taxa null | Ambos os tipos, metadados e nenhum uso inventado |
| AC2/3: conversão e cobrança única | :103 `assertEquals(66.67,m["conversionPercent"].doubleValue())`; :104 receita=2000, pagamentos=3 | 2/3 pagantes, duplicata não soma; pendente/overdue/futuro excluídos |
| AC4: histórico/participação/resumo | :102 código alterado e desativado; :112 cada linha2990; :113 resumo2990 e1pessoa/1pagamento | Atribuição preservada e resumo não soma linhas |
| AC3/6: duplicata anterior, dados inválidos | :121 payers=0, receita=0, pagamentos=0, incompletos=1; :122 taxa0 | Sem falsa conversão posterior ou valores inventados |
| AC6: falta de recibo/status não prova valor | :134 users=3, pagantes=0, incompletos=1, receita=0 | Recuperação sinalizada, ACTIVE e zero não são pagantes |
| AC1/3/5/6: limites/maturidade/arredondamento | :143 EXHAUSTED/ongoing1; :144 ended0 e taxa null; :145 100% e1235centavos; :146 incompletos1 | Limite, timestamp inclusivo, valor textual e dados parciais coexistem |
| AC5: prazo encerrado | :105 ongoing1, ended2, taxa50 | ends_at=agora encerra, só coorte encerrada na taxa |
| AC7: autorização/vazio/privacidade | :149 coupons0/taxa null; :150 403/401; :152 sem owner/payment/payload | Contrato e proteção administrativa |

Gate e contagens finais registrados após execução. Código de preparação inicialmente omitiu email_verified obrigatório; corrigido fixture sem alterar assertions.

T1 gate JDK21: bootstrap:test --tests *Coupon* --tests *Trial* (44) e subscriptions:test (247): PASS, zero falhas/erros/ignorados. Log /tmp/coupon-analytics-backend.log. Adequação: AC1–7 cobertos pelos sete testes novos, sem assertions enfraquecidas.
