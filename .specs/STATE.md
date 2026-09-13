# Estado de implementação

## Decisões

- O plano do usuário de 2026-09-12 prevalece sobre context.md e direcionamento.md.
- Execução inicialmente individual; em 2026-09-13 o usuário autorizou três frentes paralelas e supervisão pelo Orca.
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
- T08–T13, T15–T17 e T19–T20 pendentes. T14 parcial: módulos KMP de recebimentos e disponibilidade conectada à sessão/app.
- T18 parcial: APIs de publicação/preview e controles de rollout por sistema/usuário com tela no adm; operação de pagamentos e painel de tarifas ainda pendentes.
- Sem pagamentos, carteira ou recorrência implementados.
- T21 parcial: revisão individual e sete mutações detectadas em cópia temporária.
- Gate histórico anterior ao rollout: 58 testes novos: 12 domínio/cifra/tarifas, 24 PostgreSQL/HTTP, 8 delegação, 4 elegibilidade, 3 HTTP administrativo e 7 ativação de grupos.
- Subconta sandbox criada diretamente no Asaas após alteração da conta principal para PJ;
  consulta cadastral retornou APPROVED. Credenciais cifradas no servidor, sem vínculo no banco Saqz.
  Homologação de pagamentos e liberação do piloto ainda não realizadas.

- `feat/receivables-quotes`: terceira entrega dependente com catálogo vigente e simulação HTTP; três testes PostgreSQL/HTTP novos passaram.

- `feat/receivables-conditions`: publicação administrativa idempotente de termos/tarifas e preview; V51 adiciona auditoria imutável (18 tabelas financeiras). Painel visual ainda pendente.

## Integração em main

- Quatro entregas anteriores integradas e publicadas em main (30691c5e; documentação 4bfe4666).
- Entrega T06 integrada e publicada em main e55fa717, baseada em c3d1802d.
- Controles backend/mobile por sistema e usuário integrados e publicados em main 00dd7b35, com revisão independente final aprovada.
- Gate T06: 391 bootstrap, 645 grupos, 12 + 24 receivables e 20 arquitetura, sem falhas.
- V52 registra revisão imutável por grupo; V53 adiciona rollout e auditoria (23 tabelas financeiras).
- Próximo: liberação auditada do piloto e ordens/instrumentos de pagamento; depois conciliação.
- Não há bloqueio de informação para continuar a implementação. Antes de liberar o piloto,
  serão necessárias confirmação das condições BaaS de produção, tarifas reais, termos e homologação financeira Asaas.

## Handoff

- 2026-09-13: lote de toggles implementado em paralelo via Orca, conforme docs/receivables/rollout-contract.md.
- Branch de entrega feat/receivables-rollout integrada em main, base e55fa717. Commits: 3954c2a1 contrato, e887b298 adm, 23bd2252 mobile, b8c97387 backend; 11b56e33 correção de logout.
- Run run_f38959a437de: três workers concluídos e liberados. A primeira revisão independente encontrou corrida no logout mobile; correção implementada por outro worker e verificação final task_e65069bc7c44 / ctx_3a30b079f00e aprovada: 106 testes executados, incluindo probe independente anteriormente falho.
- Gates reportados: backend 393 bootstrap + 15 domínio + 28 PostgreSQL + 20 arquitetura; rodada final 22 bootstrap focados. Adm 35 Node + Chromium com API simulada. Mobile 24 testes feature Android/iOS + 7 DI/sessão iOS + 266 Android; compilação e detekt aprovados.
- Evidências em docs/receivables/evidence/ e adm-web/tests/receivables-evidence.md. Nenhuma alteração visual mobile nesta entrega (913 linhas incluindo a correção).
- Correção: chave autoritativa de sessão revogada no início do logout, incluindo geração para novo login da mesma pessoa. Gates: 67 testes de sessão iOS; 12 binding/logout/DI em cada plataforma; gateway/coordinator Android/iOS; 186 testes Android integrados, compilações e detekt. Relatório em docs/receivables/evidence/mobile-logout-fix.md.
- Revisão final aprovada e main publicada: nenhum bloqueio confirmado restante no lote de controles. Relatório docs/receivables/evidence/verification-final.md. Próximo lote: pagamentos avulsos e conciliação; preparação em docs/receivables/payment-next-wave.md.
- Preservados context.md/direcionamento.md não rastreados. Produção permanece sem liberação automática.

## Onda de pagamentos em andamento — 2026-09-13

- Usuário confirmou: titular escolhe Pix/cartão/ambos e ativação exige ao menos um meio.
- Branch feat/receivables-payments, base main 3a9f6b6d. Contrato de divisão em docs/receivables/payment-wave.md.
- Run Orca run_f939f361277e: backend task_eaaf5ca56cdb / ctx_002c48133a7a; mobile task_8224e1dd64bf / ctx_8a203aae795f; adm task_ee454554457c / ctx_dcb9b14b5bb4.
- Backend: pagamentos avulsos/conciliação e guardas de caixa; mobile: escolha de meios/revisão/ativação; adm: termos/tarifas/simulação. Workers não fazem git; coordenador integra após gates.
- GET de configuração independente de preview solicitado para permitir leitura/desativação sem tarifa vigente. Nenhuma liberação de produção ou chamada Asaas real nesta onda.

- Adm desta onda implementado em d5ea8368 e 456d8b03: publicação de termos/tarifas, simulação e reenvio idempotente. Revisão independente task_a52994883930 / ctx_ecda05788946 aprovada; 45 testes Node, quatro probes HTTP (um temporário para decimais textuais) e quatro JDBC. Evidências de navegador com API simulada em docs/receivables/evidence/payments; sem homologação Asaas.
- Auditoria inicial backend task_2be4fe454598 iniciada enquanto o autor resolve checklist C1–C8; não representa gate final. Mobile em validação de configuração, incluindo seleção vazia, Pix/cartão/ambos, retomada de operação incerta e logout.

- Adm integrado e publicado em main 45d10d0d. Mobile implementado no commit ce18d907 (1435 linhas), autor concluído/liberado; revisão independente task_0e3a5228db83 / ctx_9608ae52f015 executando gates após correções de recuperação M1/M2. Relatório do autor:44 host Android,4 visuais e173 iOS,31 capturas; ainda não é aprovação final.
