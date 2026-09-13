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

- Backend candidato de4707ef (1824 linhas) concluído/liberado; 1646 testes de regressão no penúltimo snapshot,32 bootstrap direcionados+20 arquitetura no snapshot final após correção webhook400. Revisão independente task_485cb2adb63b / ctx_c99120267142 em execução. Contrato final em docs/receivables/payment-http-contract.md; homologação Asaas e renovação Pix ainda pendentes.

- Gate independente backend de de4707ef encontrou dois defeitos reproduzidos por probes: comissão devolvida sem débito anterior quando REFUNDED é a primeira observação; cancelamento Pix bloqueado por falha da consulta QR. Correção delegada em task_bdc066c5093f; backend ainda não integrado em main. Gate Android AVD teve duas falhas em testes de cadastro/sessão existentes; revisão compara baseline antes de atribuir ao lote.

- Mobile ce18d907 aprovado funcionalmente por revisão independente:44 host,173 iOS,188 host integrados,4 visuais,2 probes host e1 probe instrumentado de Recebimentos passaram. Android geral42/44; os mesmos dois testes de cadastro/sessão falharam identicamente na base45d10d0d isolada. Gate geral permanece vermelho por falhas preexistentes, não regressões deste lote. AVD criado para teste foi encerrado pelo coordenador.

- Mobile e evidências publicados em main 469af9a3; entrega total de 1743 linhas, capturas na branch screenshots 8577de1e. Correções financeiras F1/F2 implementadas em 564028ab por task_bdc066c5093f, autor liberado; 28 regressões de integração e 1 teste de fatos aprovados em execuções agregadas. Nova revisão independente task_ee5379abb0fb verifica o delta e os 11 probes originais antes de integrar backend.

- Gate final backend aprovado por task_ee5379abb0fb / ctx_6c88acb67d7c, autor diferente do corretor: 40 testes (11 probes originais intactos, 28 integração, 1 fatos) passaram no snapshot completo 564028ab. F1/F2 resolvidos, worker liberado. Evidência permanente em docs/receivables/evidence/payments/backend-review-final.md. Lote pronto para integração; piloto completo permanece pendente conforme tasks.md e payment-operation.md.

- Integração concluída: main remota publicada em b377e853, incluindo backend de4707ef/564028ab e evidências finais. Todos os nove tasks do run Orca run_f939f361277e concluídos e respectivos workers liberados; checkout retornou à main. Nenhuma liberação de produção realizada. Próxima entrega funcional: pagamento do membro no mobile e jornada web; carteira/saques/reembolsos solicitados e recorrência continuam no plano. Dois testes Android gerais preexistentes permanecem falhos e documentados.

## Continuação em nova sessão — 2026-09-13

- A sessão anterior permaneceu somente leitura. Base autoritativa encontrada: main 702f3fe7,
  com context.md/direcionamento.md não rastreados preservados.
- Descoberta necessária para a jornada do membro implementada em 658649a5 e 095044d6:
  GET /api/receivables/orders, paginação 50, ator da sessão e cursor isolados por pagador;
  consulta local continua após corte/saída/exclusão e inclui estados terminais.
- Gateway KMP MemberPaymentsGateway implementado em 8c3e939a: lista, detalhe, instrumento Pix/cartão
  e conciliação, snapshots em centavos, erros tipados, reenvio idempotente e validação da resposta.
  Registrado no Koin. Nenhuma tela foi alterada; T16 ainda não é uma jornada disponível no app.
- Gates do autor: 32 testes backend pagamentos + 20 arquitetura; 22 gateway Android e 22 iOS (após reforço de cobertura 97855e20);
  6 testes DI em cada plataforma; compilação Android/iOS e detekt dos módulos tocados passaram.
  Evidências e critérios em docs/receivables/member-payment-foundation.md.
- Revisão independente do delta 702f3fe7..97855e20 aprovada; commits locais, sem push/liberação
  ou chamadas Asaas reais. Próximo: UI do membro com aceite, CPF/nome, Pix/cartão e recuperação;
  seleção/aprovação pelo gestor e renovação Pix continuam pendentes.
- Verificador novo e independente verify_member_payment_foundation: 10 mutações distintas,
  todas detectadas após reforço dos fixtures em 97855e20. Nenhum defeito de produção encontrado;
  gaps de teste de identidade/snapshot corrigidos com três casos novos. Relatório em
  docs/receivables/evidence/member-payment-foundation-review.md. Não foram retomados workers
  nem provider session anteriores. O gate Android instrumentado geral não foi reexecutado:
  suas duas falhas preexistentes continuam registradas no lote anterior.

## Jornada do membro — continuação autorizada

- Histórico, revisão/aceite, CPF/nome em memória, Pix/QR/copia e cola e checkout hospedado
  conectados em Perfil → Minhas mensalidades → Pagar pelo app. A entrada permanece disponível
  mesmo sem grupos carregados ou sem elegibilidade para novas ordens.
- Commits locais d4204d36 (estado/recuperação) e ff870197 (UI/rotas/DI). Testes do autor no escopo:
  106 execuções Android/iOS, sem falhas ou skips; compilações e detekt aprovados. Capturas inspecionadas.
- Revisão independente fresca verify_member_payment_ui aprovada em 06d70c7b: 106 execuções, 13 falhas comportamentais distintas injetadas e detectadas, nenhuma sobrevivente.
  Sem push, produção ou chamadas Asaas reais. Contrato: docs/receivables/member-payment-ui.md.
- T16 permanece parcial: não inclui recorrência, renovação Pix, aprovação de pendências pelo gestor,
  nem comprovante exportável. Homologação sandbox e UAT humano ainda não realizados.
- Correções 6d2bd9f9/06d70c7b: tentativas antigas canceladas não resolvem emissão nova incerta;
  efeitos enfileirados revalidam geração/sessão/prazo antes de copiar/abrir/navegar; feedback de cópia
  após execução. Cobertura reforçada para relógio e valores literais Pix/cartão.
- Próximo lote funcional: seleção/aprovação das cobranças pelo gestor no mobile, para alimentar
  a jornada do membro sem depender de operação direta da API. Os demais itens seguem em tasks.md.
- Evidência final: docs/receivables/evidence/member-payment-ui-review.md. Três regressões
  reproduzidas pelo revisor passaram após as correções. Quatro lições candidatas registradas pelo script
  do skill; nenhuma delas é tratada como regra confirmada ainda. Capturas permanecem locais em
  mobile/build/reports/member-payment-ui/ e member-payment-entry/.
- Limites: testes automatizados e leitura de código não substituem o walkthrough nativo de navegador,
  clipboard/voltar nem a jornada completa em sandbox. Os dois testes gerais Android preexistentes
  continuam fora deste gate e pendentes no projeto.


## Liberação e cancelamento pelo gestor — 2026-09-13

- Continuação autorizada por “Siga pra finalizar”. Base f765e19d; consulta local de ordem por cobrança
  em 01044670, gateway KMP em 6812cd11, caixa→revisão/termos/aceite/liberação e cancelamento em
  5419b961. Correções de recuperação e cobertura 47bf3b0b; retorno nativo 6001480d.
- Gestor escolhe cobrança e conta autorizada; vê valores por meio e todas as versões de termos;
  libera expressamente. Consulta/manutenção permanece após corte, rollout OFF e exclusão/transferência.
- Tentativas incertas persistem comando não sensível por ator/grupo/cobrança/conta. Recuperação consulta
  antes de repetir exatamente o comando. Voltar preserva pendência e recarrega caixa após resolução.
  PAID/REFUNDED/CHARGEBACK encerram retry de cancelamento exibindo estado real; somente CANCELLED
  confirma cancelamento. Sem mudança nas regras de reserva/reembolso do backend.
- Revisão independente verify_charge_approval: 256 execuções sem falhas/erros/skips, 14 falhas distintas
  injetadas em scratch e detectadas, nenhuma sobrevivente; 17 tentativas totais de mutação.
  Relatório docs/receivables/evidence/charge-approval-review.md; contrato docs/receivables/charge-approval.md.
- T10 concluído. T14/T16/T21 continuam parciais no plano maior. Nenhuma chamada Asaas real,
  publicação remota ou liberação de produção nesta continuação. Capturas locais em
  mobile/build/reports/charge-approval/. Teste do Voltar usa Root/VM reais em ComponentActivity
  Robolectric; não equivale a walkthrough no AVD. Os dois testes gerais Android antigos continuam abertos.
- Lições registradas via script: L005–L010 candidatas; L003 (valores literais na UI financeira)
  confirmado por recorrência independente na UI do membro e do gestor. Candidatas não viram regras.
- Investigação Pix: HttpAsaasPayments.recover consulta o QR do mesmo paymentId; não há renovação
  remota automática. Documentação oficial distingue validade do QR e vencimento da dívida; para o
  fallback sem chave, a atualização da cobrança exige validação própria. Não afirmar que GET QR renova.
- Próxima dependência para um titular novo: cadastro financeiro no mobile. FinancialAccountsController
  possui me/create/documents/upload; faltam descoberta de termos vigentes (só GET terms/{version}),
  recuperação do provisionamento pela conta existente sem reenviar dados pessoais e telas/port de
  documentos. OnboardFinancialAccount.provision preserva criação UNKNOWN sem repetir POST quando
  a chave remota é desconhecida. Nenhuma implementação dessa próxima etapa foi iniciada.
- Carteira/saque, reembolso solicitado, recorrência, operação/comunicação e homologação integrada
  permanecem abertos conforme tasks.md. Não são pendências apenas de deploy.

## Cadastro financeiro no mobile — continuação de 2026-09-13

- Backend 16427b58: termos publicados/vigentes e recuperação da conta própria, com sessão,
  requestId e no-store. Não repete criação remota UNKNOWN quando falta credencial.
- Gateway e multipart tipado em 16746aa0: PF/PJ, centavos exatos, aceite e envelopes validados,
  consulta por ator, documentos e upload sem retry cego. Cadastro/documentos pessoais só em memória.
- Ports Android OpenDocument e iOS UIDocumentPicker em 84ade378. Compilação Swift6 e Xcode
  arm64 simulator aprovada; limites/cancelamento testados no Android, DI em ambas as plataformas.
- Jornada shared 19fba8f4: Perfil → Recebimentos permanente, formulário voluntário, termos e aceite,
  situação real, documentos por link Asaas ou seleção/confirmar envio. Resultado incerto conserva
  marker não sensível e impede duplicação; recuperação lê antes de replay de criação em memória.
  Restauração não persiste formulário/arquivo; logout descarta callbacks e efeitos antigos.
- T04/T14/T15/T17 continuam parciais no plano maior: correção cadastral remota, delegação mobile,
  carteira/saque e solicitação de reembolso não foram implementados nesta onda.
- Testes instrumentados antigos corrigidos em 03e6f2e3: seletores/formulário atual e expectativa
  coerente com retirada do bloqueio de e-mail (VUL-84). Gate completo no AVD Saqz_API_30:
  44 testes, 0 falhas/erros/skips. Evidência docs/receivables/evidence/android-legacy-lifecycle-fix.md.
  Esta dívida antiga deixa de ficar aberta; não alterar os registros históricos dos gates anteriores.
- Sem push, deploy, termos/tarifas inventados ou chamadas Asaas reais. Nenhum provider session
  antigo retomado. context.md/direcionamento.md continuam não rastreados e preservados.
- Carteira/saque, reembolso solicitado, recorrência/corte, renovação Pix, operação/comunicação e
  homologação integrada seguem abertos. A implementação completa do plano ainda não terminou.
- Revisão independente fresca verify_financial_onboarding aprovada em 19fba8f4: FO1–FO7,
  10 falhas comportamentais injetadas e detectadas em cópia temporária, nenhuma sobrevivente
  ou inconclusiva. Relatório docs/receivables/evidence/financial-onboarding-mobile-review.md;
  inclui apêndice independente de inspeção de 03e6f2e3 e do XML Android44.
- Compilação final Android/framework iOS e detekt Android aprovados no HEAD 03e6f2e3;
  Xcode SaqzDev com o framework final também BUILD SUCCEEDED. Sem walkthrough do picker
  nativo iOS, sem UAT humano e sem homologação financeira real.
