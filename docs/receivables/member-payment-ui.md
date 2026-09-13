# Jornada do membro — pagamento avulso mobile

Base: gateway e listagem aprovados em 0a2cf201. T16 parcial: implementar histórico e pagamento avulso
 de ordens já aprovadas. Recorrência, aprovação pelo gestor, renovação Pix e comprovante exportável
 permanecem nas tarefas existentes. O acompanhamento mostra confirmação do servidor e identificação
 da transação; não inventa saldo nem liquidação.

## Passos atômicos

1. Contract/policies, HistoryViewModel, MemberPaymentViewModel e testes commonTest em receivables
   presentation; gate presentation Android/iOS e detekt; commit próprio.
2. Screens/Roots, QR nativo, strings, DI e rotas no compose-app; CTA em OwnMonthlyPaymentsRoot;
   testes de navegação, DI e UI/capturas no android-app e iosTest; compilações Android/iOS,
   detekt dos módulos alterados, screenshots inspecionadas; commit próprio.
3. Verificador independente, sensor financeiro >=5 mutações em cópia temporária, evidências/handoff.

## Critérios

- UI1: histórico autenticado paginado; erro/vazio/loading e retry; mais páginas sem duplicatas;
  abrir somente ID listado. Entrada não usa rollout/elegibilidade nem exige grupos carregados.
- UI2: ordem original do pagador, escolha explícita de meio aprovado, base/taxas/total e termos
  da versão exata antes do aceite. Mudança de meio invalida aceite; termos falhos impedem emissão.
  Nome 2–120 sem controles e CPF/CNPJ com 11/14 dígitos são obrigatórios; dados ficam apenas em memória.
- UI3: criação exige aceite e nova revisão válida, trava cliques concorrentes. Resultado incerto
  conserva comando/requestId para replay explícito; refresh/retorno consulta/concilia, nunca cria.
  Marker não sensível request/user/order e IDs dos instrumentos anteriores é gravado antes da rede; process death recupera por consulta,
  nunca recria automaticamente. Se instrumento novo ainda não aparece, continua pendente; instrumentos antigos cancelados não resolvem a tentativa atual. Voltar fica
  bloqueado apenas enquanto uma emissão não tem resultado conhecido, para preservar a recuperação.
- UI4: Pix ACTIVE mostra QR/copia e cola, expiração e feedback ao copiar. Expirado (prazo vencido ou
  status EXPIRED) não copia/exibe QR ativo nem permite nova emissão; orienta atualização. Falha de
  imagem mantém copia e cola. CARD ACTIVE abre URL HTTPS hospedada no Asaas, sem coletar cartão;
  falha ao abrir tem mensagem. Retorno e atualização só conciliam.
- UI5: confirmar pagamento depende do status atual do servidor (CONFIRMED/SETTLED/AVAILABLE).
  UNKNOWN/CREATING, CANCEL_PENDING, CANCELLED, REFUNDED, DISPUTED, RECOVERY_PENDING e estado desconhecido
  têm apresentação sem falso sucesso; flags históricas nunca substituem status. Rejeição definitiva
  (instrumento CANCELLED com ordem ISSUED) permite nova revisão/aceite; EXPIRED não renova neste lote.
- UI6: guarda de geração e sessão em toda resposta/efeito; troca de sessão limpa dados, mesmo usuário
  com nova session key descarta resposta velha. Rota restaurável só IDs, logout limpa stack.
  Estado salvo de outro ator descartado. CPF/nome/aceite não persistem; marker incerto não some em erro.
- UI7: telas usam design system, strings PT-BR/testTags; estados principais capturados e inspecionados;
  caminho Perfil → Minhas mensalidades → Pagar pelo app → ordem conectado no NavHost/Koin.

## Gates

JDK21. `mobile/gradlew -p mobile :features:receivables:presentation:testAndroidHostTest
:features:receivables:presentation:iosSimulatorArm64Test :features:receivables:presentation:detektAll`.
Depois DI Android/iOS, compilação app Android/iOS, detekt compose-app/groups presentation,
UI Roborazzi no android-app e UI iOS de Receivables. Os dois testes gerais Android previamente
falhos não são evidência desta entrega; não haverá chamada Asaas real nem liberação de produção.

## Gate passo 1

2026-09-13, exit 0 em /tmp/saqz-member-ui-vm.log: presentation 38 Android host e 39 iOS,
sem falhas; detekt passou. São 15 testes novos (11 pagamento + 4 histórico), preservando os anteriores.

Evidência do autor: MemberPaymentViewModelTest.kt verifica versão exata dos termos (:31),
comando completo (:38–41), bloqueio por dados/termos inválidos (:46–50), marker e replay (:55–65),
restauração sem criação (:70–77), ator estrangeiro (:83–84), geração/sessão (:92–105),
expiração/status (:112–116), nova revisão após rejeição (:121–124), efeitos (:135–137) e URL (:143–147).
MemberPaymentHistoryViewModelTest.kt verifica IDs/paginação (:17–24), retry e seleção (:29–35),
resposta obsoleta (:44) e nova sessão (:50–51). Matriz independente será consolidada no relatório final.

## Gate passo 2

2026-09-13: compilação Android e iOS, detekt presentation/compose-app/groups passaram.
Execuções aprovadas no escopo: presentation 38 Android + 40 iOS, DI 6 Android + 6 iOS,
navegação/restauração/logout 1 iOS, interface 4 Android e entrada de mensalidades 1 Android.
Total: 96 execuções, sem falhas ou skips. Logs locais em /tmp/saqz-member-ui-{build,final-gates,captures}.log.
As capturas Roborazzi estão em mobile/build/reports/member-payment-ui/ e member-payment-entry/;
inspecionadas revisão/aceite, Pix/QR, cartão, incerteza, reembolso e entrada com erro de rede.
A captura usa o nó da tela; o cabeçalho permanece visível ao rolar a revisão. Fixtures de QR e termos
são apenas de teste. Homologação real e UAT humano permanecem pendentes.

## Correção UI3/UI6 — recuperação com histórico cancelado

A revisão independente reproduziu em ff870197 o descarte prematuro do marker ao receber somente
um instrumento CANCELLED anterior. A criação agora salva os IDs anteriores antes da rede; consulta
só resolve a incerteza ao observar um instrumento novo ou uma ordem terminal. Marcadores legados
sem baseline permanecem conservadores diante de instrumentos cancelados/expirados.
Dois testes novos cobrem execução ao vivo, restauração, erro de leitura, replay idêntico e ordem terminal.
Gate presentation Android40 + iOS42 e detekt aprovado em /tmp/saqz-member-ui-recovery-fix.log.

## Correção UI1/UI4/UI6 e reforço UI2 — efeitos e evidência financeira

A revisão reproduziu uma ação de copiar já enfileirada sendo aceita após consulta de reembolso.
Efeitos de pagamento e navegação agora carregam a geração e são revalidados pelo Root no consumo.
Pagamento exige instrumento ainda utilizável e prazo atual; histórico exige ID ainda listado.
O feedback de cópia só é aplicado após gravar o clipboard. Três testes novos cobrem fila/retorno,
reembolso, troca de sessão, relógio no prazo exato e navegação após atualização da lista.
As mutações sobreviventes de prazo e total exibido motivaram asserções adicionais: nenhum efeito
no vencimento, valores literais de base/taxas/total e botão para Pix e cartão com tarifas diferentes.

Gate do snapshot corrigido: presentation Android43 + iOS45, quatro testes de UI Android,
compilações app Android/iOS e detekt presentation aprovados em /tmp/saqz-member-ui-effects-fix.log.
Os demais gates de integração anteriores seguem válidos; total agregado do autor: 106 execuções.
Captura revisao-cartao inspecionada com total R$ 109,00 e taxas R$ 9,00 (fixtures de teste).
Revisão independente final aprovada em 06d70c7b; não representa homologação Asaas nem UAT humano.

## Fechamento do lote

Revisão fresca independente aprovada: UI1–UI7 no escopo automatizado/leitura de código,
106 execuções de testes sem falhas/erros/skips, 13 falhas comportamentais distintas detectadas
em 15 tentativas de mutação. Os três probes independentes que reproduziram os defeitos iniciais
passaram no snapshot final. Evidência completa, asserções por critério e limites em
[evidence/member-payment-ui-review.md](evidence/member-payment-ui-review.md).
T16 continua parcial além deste lote; aprovação pelo gestor, recorrência, renovação e homologação
seguem em tasks.md. Commit local, sem push, publicação ou pagamento real.
