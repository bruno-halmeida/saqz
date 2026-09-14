# Contexto e decisões
Pedido em português: criar painel e rastrear todos os cupons. Inclui trials e descontos, qualquer status e histórico já registrado. Permissão de integração na main mantida na sessão.

Decisões comunicadas antes de implementar: atribuição por participação (pagamento após uso), resumo deduplicado, separação de maturidade de trial, receita bruta sem taxas/estornos e indicação de confirmações sem recibo. Sem bloqueio por questões de implementação: fluxo somente leitura e uso das fontes já existentes, conforme autonomia autorizada.

Arquitetura: DTO/porta em subscriptions/application; adaptador JDBC em transação read-only repeatable-read lê cupons, usos e fatos financeiros. Jackson existente tolera payload histórico inválido; agregação deduplica antes de atribuir. Controller administrativo e fiação explícita. Sem migrations, escrita de pagamentos ou dependências novas. UI no runtime DC existente e API autenticada com época de sessão.
