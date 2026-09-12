# Estado de implementação

## Decisões

- O plano do usuário de 2026-09-12 prevalece sobre context.md e direcionamento.md.
- Execução e verificação individuais autorizadas pelo usuário.
- Receivables permanece separado de subscriptions; manutenção financeira independe de plano/grupo.
- Nenhuma tarifa ou condição comercial real foi inventada.

## Entregas

- `feat/receivables-core`: domínio, schema, cifra, operações persistidas e cálculo de tarifas.
- `feat/receivables-foundation`: entrega dependente com onboarding, delegação e elegibilidade central.
- Base atualizada para origin/main 6f86f8f6, incluindo trial central.
- V48 resolve colisão preexistente do trial com V46 de grupos; V49 cria recebimentos; V50 adiciona data remota.
- Criação do PR recusada pelo GitHub: `must be a collaborator (createPullRequest)`.
  Restrição de PR superada pela autorização explícita do usuário para merge direto na main.

## Progresso e pendências

- T01–T03 implementadas e verificadas.
- T04 parcial: cadastro voluntário PF/PJ, aceite, cifra, retomada sem duplicação, documentos e HTTP.
  Pendentes correção cadastral e recuperação operacional de chave perdida.
- T05 parcial: concessão/revogação/diretório, revogação transacional ao remover/rebaixar admin.
  Permissões nas futuras operações serão aplicadas quando essas rotas existirem.
- T06 parcial: composição com trial e assinatura centrais, inclusive corte efetivo de downgrade.
  Ativação por grupo ainda pendente.
- T07 parcial: núcleo decimal, gross-up, split fixo, simulação HTTP e consulta de termos; publicação administrativa implementada; emissão pendente.
- T08–T17 e T19–T20 pendentes. T18 parcial: APIs de publicação/preview administrativo implementadas; interface adm-web pendente. Sem pagamentos, carteira, recorrência ou mobile implementados.
- T21 parcial: revisão individual e sete mutações detectadas em cópia temporária.
- 51 testes novos: 12 domínio/cifra/tarifas, 24 PostgreSQL/HTTP, 8 delegação, 4 elegibilidade, 3 HTTP administrativo.
- Homologação real Asaas e liberação do piloto não realizadas.

- `feat/receivables-quotes`: terceira entrega dependente com catálogo vigente e simulação HTTP; três testes PostgreSQL/HTTP novos passaram.

- `feat/receivables-conditions`: publicação administrativa idempotente de termos/tarifas e preview; V51 adiciona auditoria imutável (18 tabelas financeiras). Painel visual ainda pendente.

## Integração em main

- Usuário autorizou merge direto das quatro entregas. Merge local realizado sem conflitos,
  sobre origin/main 6f86f8f6; publicação remota será verificada ao concluir a operação.
- Não há bloqueio de informação para continuar a implementação. Antes de liberar o piloto,
  serão necessárias habilitação BaaS, condições comerciais reais, termos e homologação Asaas.
