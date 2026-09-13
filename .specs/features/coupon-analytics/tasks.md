# Execução
1. [x] T1: leitura, agregação e endpoint administrativo. Arquivos novos AdminCouponAnalytics.kt, JdbcAdminCouponAnalytics.kt, AdminCouponAnalyticsController.kt, wiring PlatformAdminConfiguration.kt e AdminCouponAnalyticsIntegrationTest.kt. Gate JDK21 bootstrap:test --tests '*Coupon*' --tests '*Trial*', subscriptions:test. AC1–7.
2. [x] T2: painel em adm-web/index.html e tests/coupon-analytics.test.cjs. Gate node --test adm-web/tests/*.test.cjs. AC8.
3. T3: validação visual com fixtures de ambos os tipos, loading/error/empty e viewport desktop/tablet; documentação de métricas e evidências. Verificador independente e sensor após commits, publicação na main conforme autorização da sessão. AC1–8.
