# Estado de implementação

## Decisões

- O plano do usuário de 2026-09-12 prevalece sobre context.md e direcionamento.md.
- Execução e verificação individuais autorizadas pelo usuário.
- Receivables permanece separado de subscriptions; manutenção financeira independe de plano/grupo.
- Nenhuma tarifa ou condição comercial real foi inventada.

## Entregas

- `feat/receivables-core`: domínio, schema, cifra, operações persistidas e cálculo de tarifas.
- `feat/receivables-foundation`: entrega dependente com onboarding, delegação e elegibilidade central.
- Base atualizada para origin/main c3d1802d, incluindo trial central e onboarding mobile paralelo.
- V48 resolve colisão preexistente do trial com V46 de grupos; V49 cria recebimentos; V50 adiciona data remota.
- Criação do PR recusada pelo GitHub: `must be a collaborator (createPullRequest)`.
  Restrição de PR superada pela autorização explícita do usuário para merge direto na main.

## Progresso e pendências

- T01–T03 implementadas e verificadas.
- T04 parcial: cadastro voluntário PF/PJ, aceite, cifra, retomada sem duplicação, documentos e HTTP.
  Pendentes correção cadastral e recuperação operacional de chave perdida.
- T05 parcial: concessão/revogação/diretório, revogação transacional ao remover/rebaixar admin.
  Permissões nas futuras operações serão aplicadas quando essas rotas existirem.
- T06 implementada: elegibilidade central, revisão de preços/tarifas, aceite explícito,
  ativação idempotente e desativação independente de plano/grupo. Interrupção remota permanece em T13.
- T07 parcial: núcleo decimal, gross-up, split fixo, simulação HTTP e consulta de termos; publicação administrativa implementada; emissão pendente.
- T08–T17 e T19–T20 pendentes. T18 parcial: APIs de publicação/preview administrativo implementadas; interface adm-web pendente. Sem pagamentos, carteira, recorrência ou mobile implementados.
- T21 parcial: revisão individual e sete mutações detectadas em cópia temporária.
- 58 testes novos: 12 domínio/cifra/tarifas, 24 PostgreSQL/HTTP, 8 delegação, 4 elegibilidade, 3 HTTP administrativo e 7 ativação de grupos.
- Subconta sandbox criada diretamente no Asaas após alteração da conta principal para PJ;
  consulta cadastral retornou APPROVED. Credenciais cifradas no servidor, sem vínculo no banco Saqz.
  Homologação de pagamentos e liberação do piloto ainda não realizadas.

- `feat/receivables-quotes`: terceira entrega dependente com catálogo vigente e simulação HTTP; três testes PostgreSQL/HTTP novos passaram.

- `feat/receivables-conditions`: publicação administrativa idempotente de termos/tarifas e preview; V51 adiciona auditoria imutável (18 tabelas financeiras). Painel visual ainda pendente.

## Integração em main

- Quatro entregas anteriores integradas e publicadas em main (30691c5e; documentação 4bfe4666).
- Entrega T06 em feat/receivables-group-activation, baseada em c3d1802d.
- Gate T06: 391 bootstrap, 645 grupos, 12 + 24 receivables e 20 arquitetura, sem falhas.
- V52 registra revisão imutável por grupo; 19 tabelas financeiras.
- Próximo: liberação auditada do piloto e ordens/instrumentos de pagamento; depois conciliação.
- Não há bloqueio de informação para continuar a implementação. Antes de liberar o piloto,
  serão necessárias confirmação das condições BaaS de produção, tarifas reais, termos e homologação financeira Asaas.

## Handoff

- 2026-09-13: usuário autorizou execução paralela supervisionada pelo Orca, substituindo escolha anterior de execução individual.
- Branch feat/receivables-rollout, base e55fa717. Contrato compartilhado docs/receivables/rollout-contract.md.
- Run Orca run_f38959a437de. Backend task_1d78b9588c74 / ctx_881a9eb5ff87; adm task_a131c1593f7e / ctx_0e41da9d0325; mobile task_c3bdfb520b73 / ctx_0253116c4152.
- Workers na árvore atual, propriedade exclusiva backend/**, adm-web/** e mobile/** respectivamente. Coordenador faz commits e integração; workers não mudam branch/staging.
- Primeiro lote: toggles e segmentação ponta a ponta. Próximo lote: pagamentos avulsos e conciliação, após gates e contrato financeiro.
- Não tocar context.md/direcionamento.md não rastreados. Nenhuma liberação de produção autorizada nesta etapa.
