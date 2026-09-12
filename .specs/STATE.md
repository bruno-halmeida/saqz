# Estado de implementação

## Decisions

- 2026-09-12: o plano fornecido pelo usuário é a fonte vigente para recebimentos Asaas.
  Propostas antigas de absorção opcional das tarifas ou continuidade de recorrências
  após perda de elegibilidade foram substituídas pelo plano.
- Preservar o contrato central de trial em desenvolvimento paralelo; não recriar sua regra temporal.
- Separar receivables de subscriptions e preservar manutenção financeira após corte comercial.

## Handoff

- Execução individual autorizada pelo usuário; verificação final também individual conforme preferência.
- Branch: feat/receivables-foundation.
- T01 concluída: módulo, contratos e política de autorização; 5 testes de domínio e 20 de arquitetura passam.
- Corrigida dependência preexistente de exceções Asaas em subscriptions; suíte subscriptions passa.
- T02 concluída: migração V47, 17 tabelas; seis testes PostgreSQL e migração do bootstrap passam.
- T03 concluída: AES-GCM com contexto de conta/finalidade, HMAC para identidade; operações com leases e fencing.
- Gate: 8 testes unitários, 10 PostgreSQL, 20 arquitetura passam.
- Próximo: T04 onboarding e integração Asaas, ainda sem endpoints ou liberação operacional.
- Trial central ainda ausente neste checkout; não foi reimplementado.
