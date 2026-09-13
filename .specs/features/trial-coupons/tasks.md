# Execução
Base: 702f3fe7 (main incorporada antes da implementação).

1. [x] T1: persistência, elegibilidade e concessão transacional; arquivos de subscriptions/application, jdbc, migration V56, shared-kernel/GroupCreationTrial, groups/CreateGroup e teste bootstrap/TrialCampaignIntegrationTest. Gate: testes de trials e grupos backend. AC1–4.
2. [x] T2: HTTP e wiring; controllers de trial e admin, configuração e testes HTTP. Gate: bootstrap testes de endpoints trial/campaign/admin. AC2, AC5.
3. [x] T3: administrativo; adm-web/index.html e tests/trial-coupons.test.cjs. Gate: node --test adm-web/tests/*.test.cjs e verificação visual. AC6.
4. [x] T4: entrada mobile; TrialAccess, KtorTrialGateway, nova apresentação TrialEntry, DI e navegação da criação/entitlement, strings e testes; cópia de adm-web/comecar sem oferta automática. Gate: iosSimulatorArm64Test dos módulos afetados, compilação Android, detekt e captura visual. AC7.
5. Verificador independente após commits, correções se necessárias e merge/push main.

Cada entrega funcional tem commit após gate. Evidências em validation.md. Nenhuma alteração na frente financeira recebida da main.
