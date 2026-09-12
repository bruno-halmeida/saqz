Cria a base de recebimentos separada das assinaturas Saqz: contratos de autorização por ação, persistência financeira aditiva, segredos cifrados, operações idempotentes com recuperação após timeout e cálculo decimal de tarifas com split fixo sobre o valor base.

O modelo preserva identidade e histórico financeiro independentemente do grupo. Operações abandonadas passam a permitir somente recuperação; workers antigos não sobrescrevem resultados novos. Tarifas comerciais permanecem sem configuração.

Esta é a primeira entrega de infraestrutura do plano de recebimentos, ainda sem piloto operacional. A migração do trial foi renumerada para V48 para resolver a colisão já existente com V46 de convites permanentes; a base financeira usa V49.

Validação local em JDK 21: 22 testes de recebimentos (12 unitários e 10 PostgreSQL), 20 testes de arquitetura e migração agregada do bootstrap. Testes de falha em cópia temporária detectaram violações de autorização, cifra, recuperação e cálculo de tarifas.

Próxima entrega: onboarding Asaas, documentação, delegações e composição com o contrato central de trial/assinatura. Pagamentos, carteira, recorrência, mobile e painel permanecem fora desta primeira entrega.
