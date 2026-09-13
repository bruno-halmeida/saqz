# Execução
1. [x] T1: leitura, agregação e endpoint administrativo. Arquivos novos AdminCouponAnalytics.kt, JdbcAdminCouponAnalytics.kt, AdminCouponAnalyticsController.kt, wiring PlatformAdminConfiguration.kt e AdminCouponAnalyticsIntegrationTest.kt. Gate JDK21 bootstrap:test --tests '*Coupon*' --tests '*Trial*', subscriptions:test. AC1–7.
2. [x] T2: painel em adm-web/index.html e tests/coupon-analytics.test.cjs. Gate node --test adm-web/tests/*.test.cjs. AC8.
3. [x] T3: validação visual com fixtures de ambos os tipos, loading/error/empty e viewport desktop/tablet; documentação de métricas e evidências. Verificação visual concluída.
4. [x] Verificador independente PASS (AC1–8, 348 testes, 6/6 mutações) e publicação por fast-forward em origin/main 35073c76. Checkout main local com trabalho concorrente preservado.
