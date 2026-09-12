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
- T04 parcial: cadastro voluntário, aceite atômico, PF/PJ, credenciais, retomada, documentos e endpoints HTTP implementados e testados.
- Endpoints só registrados quando proteção financeira configurada; BaaS criação desligado por padrão.
- Gate atual: 8 unitários, 17 PostgreSQL/HTTP, 20 arquitetura passam. Bootstrap agregado migra V47.
- T04 pendente: correção dos dados cadastrais, recuperação operacional da chave perdida e caminhos HTTP restantes; não marcar completo.
- Próximo: concluir T04, depois T05 delegações e T06 ativação/contrato de elegibilidade.
- T07 parcial: núcleo independente de tarifas e split fixo implementado; quatro testes passam.
- T05–T06 e T08–T21 ainda não implementados; nenhuma mudança mobile ou adm-web.
- Suíte geral bootstrap: 337/338 na execução ampla; único EOF de health passou no rerun isolado.
- Trial central ainda ausente neste checkout; não foi reimplementado.
