# Evidências e limites — 10/09/2026

As ligações anteriores de saída, cadastro esportivo, perfis, mensalidades próprias, mapa e comunicação foram complementadas por encerramento do acerto, paginação do ADM e geração manual de mensalidades. **Isso não equivale a homologação E2E de todo o produto.**

O catálogo tem **58 declarações de cenários** (21 ligações, 13 regressão, 18 ADM, 6 financeiro final), além das variações em Exemplos. Continuam `NÃO EXECUTADOS` manualmente; Suporte está bloqueado por definição/implementação. O runner Gherkin não está instalado.

## Verificação desta rodada

Os números vêm dos relatórios locais e incluem testes existentes. Não somar execuções focais e amplas como casos únicos; tarefas podem usar cache. Nenhum teste ignorado foi contado como aprovado.

| Gate | Casos | Resultado |
| --- | ---: | --- |
| Groups/presentation — Kotlin iOS simulator, suíte completa | 516 | PASS |
| Compose-app — Kotlin iOS simulator, suíte completa | 122 | PASS |
| Acerto — VM/Screen/Root (subconjunto dos 516) | 22 | PASS |
| Geração manual — VM/Screen/Root (subconjunto dos 516) | 11 | PASS |
| Foto + editor de membro (subconjunto dos 516) | 25 | PASS |
| KtorFinanceGateways — contrato financeiro mobile | 45 | PASS |
| Transações de cobrança e geração automática — Postgres | 33 | PASS |
| Paginação ADM — Node, lógica real com rede controlada | 27 | PASS |
| PasswordResetEndpointIntegrationTest — HTTP, SMTP e Postgres | 17 | PASS |
| Decodificação MIME — quoted-printable, multipart e ausência de texto | 3 | PASS |
| Comunicação/saída — HTTP com Postgres | 5 | PASS |
| XCTest Swift — suíte nativa, simulador iPhone | 119 | PASS |
| ChangePlanScreen + ViewModel | 12 | PASS; mapper anual pendente abaixo |
| Detekt global | — | PASS, sem regenerar baseline |

A sequência HTTP de saída agora prepara um jogo persistido, comprova acesso antes do DELETE, nega lista e detalhe depois do DELETE e mantém ambos disponíveis ao dono. O verificador de identidade é de teste; a API HTTP e o banco são reais.

A geração manual também atravessa o NavHost e a injeção de dependências reais: caixa de G1 → geração → falha → retry com o mesmo conteúdo → retorno ao caixa de G1 com nova consulta e cobrança visível. Há resolução da VM pelo módulo real e restauração serializada da rota. Dois testes adicionais disparam o evento de voltar no dispatcher real: não saem enquanto a resposta está pendente e comprovam que voltar funciona antes de confirmar e depois de falha. O teste reproduziu o defeito antes de acrescentar a proteção no Root. Os gateways desses testes são controlados; não substituem a jornada com autenticação e API de homologação nem gestos físicos em aparelhos.

No reset de senha, o helper passou a ler o texto MIME decodificado, inclusive multipart/quoted-printable. Foram mantidas as verificações de comportamento dos 17 cenários. As dez falhas anteriores dessa suíte foram resolvidas; isso não é uma execução de todo o backend.

O XCTest foi recuperado corrigindo UTF-8 de comentários e fixtures incompatíveis com os modelos/callbacks Kotlin atuais. A execução ampla também encontrou e corrigiu reautenticação Firebase caindo em erro genérico e ausência do entitlement de Universal Links. Os 119 testes passaram também com a configuração padrão dos módulos Swift, sem o override usado no diagnóstico. As quatro alterações iOS que já estavam no worktree foram preservadas e não entraram nos commits desta rodada. Universal Links em dispositivo ainda dependem de AASA, domínio e provisioning corretos do ambiente.

## Interface e revisão

Capturas executadas: 30 estados dos fluxos conectados, 6 do acerto, 2 de troca de plano e 8 de geração mensal/caixa. Foram inspecionadas as imagens novas e alteradas. Os PNGs são ignorados pelo Git e reproduzíveis pelos testes de captura; não representam dados de clientes.

O smoke do ADM dirigiu navegador visível nas três listas, com API controlada: primeira/última página, query, retorno, erro/retry e bloqueio de controles durante resposta retida. Não é E2E com a API de homologação.

Revisão independente, conforme o processo `tlc-spec-driven`, encontrou e fechou lacunas que uma contagem verde de testes não mostrava:

- Acerto: 22 testes e três mutações detectadas.
- Paginação: 27 testes, smoke e duas mutações detectadas na rodada final, incluindo limpeza indevida dos filtros. Foram corrigidos total reduzido deixando página inválida e lacunas dos testes de logout/retorno/bloqueio.
- MIME/editor/saída: 25 testes backend e 20 do editor reexecutados; mutações de MIME e posição duplicada detectadas. As 30 capturas reorganizadas permaneceram idênticas às referências.
- Foto: cinco testes aprovados, incluindo Root com Koin real e dimensões efetivas. Foram detectadas as duas mutações que removem o loader ou descartam o tamanho; a primeira sobrevivia antes de acrescentar a cobertura do Root.
- Geração manual: revisão final aprovada, 20 testes focais reexecutados e quatro mutações detectadas (valor, chave de retry, recarga do caixa e proteção do voltar). A revisão encontrou a saída sistêmica durante confirmação; corrigida e comprovada pelo dispatcher real, com saída normal preservada fora da escrita.
- Layout da troca de plano: 12 testes e lint focal reexecutados, duas capturas inspecionadas e mutação do callback de confirmação detectada. Aprovação restrita ao layout; não inclui o mapper anual pendente abaixo nem o checkout real.
- Swift: inspeção das correções e reexecução independente dos 119 casos com `test-without-building`. Usa o bundle do build padrão do autor e inclui as alterações iOS preexistentes do worktree; não é um segundo build limpo nem validação de links em infraestrutura live.

## O que ainda não está aprovado

- **Suporte/moderação:** permanece demonstrativo. VUL-171 exige definição de origem, privacidade e consequência da resolução; não foi inventada uma regra de sanção nem persistência fictícia.
- **Um teste antigo de preço anual:** `ChangePlanMappersTest` espera R$89,90, mas a fixture define 89.900 centavos (R$899,00), que é o valor formatado pelo aplicativo. A suíte ChangePlan ampla teve 16 aprovações e essa falha. A expectativa não foi alterada, aguardando confirmação do usuário; preços e regra de cobrança do aplicativo também não foram alterados.
- **E2E real/manual:** aparelhos Android/iOS com autenticação e API de homologação, painel e checkout sandbox ainda precisam da execução dos roteiros. Gateways controlados, testes HTTP isolados e capturas não substituem isso.
- **Deploy/configuração:** nada foi publicado; migration V45 e configurações nativas/serviços devem seguir o processo normal do ambiente. A rodada não cria integração WhatsApp, push ou WebSocket.

## Reproduzir

Use JDK 21. Na raiz:

```sh
node --test adm-web/tests/pagination.test.cjs
mobile/gradlew -p mobile :features:groups:presentation:iosSimulatorArm64Test :compose-app:iosSimulatorArm64Test
mobile/gradlew -p mobile :features:groups:data:iosSimulatorArm64Test --tests '*KtorFinanceGatewaysTest'
backend/gradlew -p backend :bootstrap:test --tests '*PasswordResetEndpointIntegrationTest' --tests '*MailTestBodiesTest' --tests '*GroupCommunicationEndpointIntegrationTest'
backend/gradlew -p backend :features:groups:integrationTest --tests '*JdbcChargeTransactionRepositoryIntegrationTest' --tests '*MonthlyChargeScheduleIntegrationTest'
mobile/gradlew -p mobile :features:subscriptions:presentation:iosSimulatorArm64Test --tests '*ChangePlanScreenTest' --tests '*ChangePlanViewModelTest'
mobile/gradlew -p mobile detektAll
mobile/gradlew -p mobile :features:groups:presentation:recordRoborazziAndroidHostTest --tests '*Connected*FlowsScreenshotTest' --tests '*GameSettlementScreenshotTest' --tests '*MonthlyGenerationScreenshotTest' --tests '*GroupCashboxScreenshotTest'
```

XCTest, ajustando o destino para um simulador disponível:

```sh
xcodebuild -project mobile/ios-app/SaqzIOS.xcodeproj -scheme SaqzDev -configuration Debug \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' -jobs 1 \
  CODE_SIGNING_ALLOWED=NO ONLY_ACTIVE_ARCH=YES \
  -only-testing:SaqzIOSTests test
```

Na rodada anterior também passaram Groups/data 249, Profile/presentation 35, Android DevDebug 171 e comunicação JDBC 7; esses resultados são históricos, não uma nova execução ampla desta rodada. Os comandos acima executam suítes independentes, não os arquivos `.feature`.

## Rodada adicional — app Android instalado (10/09/2026)

Foi acrescentado um [runner E2E local](../e2e/android/README.md), com APK separado `app.saqz.e2e`,
Firebase Auth Emulator, Spring e PostgreSQL descartável. As ações são feitas no app; consultas
autenticadas independentes verificam persistência e permissões. Não usa gateways falsos.

| Execução | Casos | Resultado |
| --- | ---: | --- |
| Acesso, saída, capacidade, FIFO, comunicação e geração mensal — repetição independente após sincronização | 7 | PASS, sem falhas ou skips |
| Geração manual, repetição e mensalidade própria — execução focal anterior | 1 | PASS, sem skips; também incluído no lote de sete |
| Proteções Node do runner, incluindo seleção explícita de cenário | 5 | PASS |
| Compose-app/commonTest no simulador iOS, incluindo duas regressões de navegação | 124 | PASS |
| Groups/data — suíte completa no simulador iOS após corrigir URL | 250 | PASS; inclui 11 de KtorAthleteGatewayTest |
| Firebase Android na configuração dev padrão | 3 | PASS |
| Detekt global após correção do gateway | — | PASS |

Os sete casos passaram juntos na primeira execução completa do autor, com dados novos,
em 4min24s de Gradle. **A primeira repetição independente reprovou: seis PASS e uma falha no teste FIFO**,
que leu `WAITLISTED` quando esperava `CONFIRMED`. O texto da desistência é atualizado de forma
otimista, antes de terminar a gravação; aguardar somente esse texto não comprova a conclusão.
Uma reprodução focal inalterada passou, mas não anula a falha do lote. Com aprovação do usuário,
os dois testes de presença agora aguardam o botão novamente habilitado e reconferem o texto final
antes de consultar a API. Todas as verificações de status, capacidade, identidade e ordem foram
mantidas. Ambos passaram isoladamente após a correção, sem falhas ou skips. O novo lote conjunto
também passou: sete testes, zero falhas/skips, em 43s de Gradle. A repetição independente confirmou
esse resultado, também em 43s. Foram reexecutados independentemente 389 testes: sete instalados,
cinco Node, 250 de dados, 124 de composição/navegação e três de Firebase; todos sem falhas/skips,
com lint aprovado.

Cinco mutações de produto em cópia temporária foram detectadas: preservar tela protegida sem
sessão, perder a aba Grupos na atualização de sessão, omitir filtro de mensalistas, não encerrar
a sessão Firebase e acrescentar um centavo à cobrança. As três primeiras atingem código de produto
alterado nesta rodada; as duas últimas atingem código preexistente exercitado pelos novos E2E.
O teste FIFO corrigido também passou com resposta de desistência artificialmente atrasada.
Ao fim, o APK E2E original foi reinstalado por uma execução financeira da árvore real, aprovada.

**O aceite final do runner permanece pendente por dois achados ENV:**

- Se o emulador desconectar durante cancelamento, a falha no `adb force-stop` impede a tentativa
  de encerrar Spring/Firebase e remover o banco descartável. Reproduzido com processos inertes;
  nenhuma base real foi deixada aberta pelo diagnóstico.
- `command()` mistura stdout e stderr no valor retornado. Um diagnóstico inerte comprovou que
  avisos contaminam o metadado; isso pode corromper o ID retornado por `docker create` ao baixar uma
  imagem ausente. O impacto no Docker sem cache é inferido desse fluxo e do pull padrão documentado
  pelo CLI instalado; não houve remoção de imagem nem pull real para o diagnóstico.

Esses ajustes de infraestrutura aguardam aprovação. Eles não invalidam os resultados dos sete
cenários executados, mas impedem declarar o runner finalizado. Fontes reais não foram mutadas.

Uma tentativa anterior desse lote foi interrompida após três casos concluídos, com o emulador
sem progresso e comandos ADB excedendo seus limites. Ela não conta como aprovação. O reinício
do Android ficou offline; reiniciar o processo do AVD, sem apagar dados, recuperou o ambiente.
A suíte de 43s usou o mesmo código e novas fixtures. Os registros da tentativa incompleta foram
preservados; nenhum resultado esperado foi alterado para passar.
O runner focal anuncia o recorte executado; não anuncia sucesso da suíte completa.

Defeitos reproduzidos:

- Ao sair do grupo, a atualização da sessão substituía a lista de grupos pela tela Início.
  Corrigido preservando a raiz autenticada escolhida, com logout/sessão inválida ainda removendo
  telas protegidas. Lote de seis passou após a correção, commit local `3b8cdfe6`.
- A consulta de mensalistas enviava `athletes%3Ftype=MENSALISTA` como caminho, sem query, e recebia
  erro 500. A URL correta retornava 200. O gateway agora usa `NetworkRequest.query`; a regressão
  específica falhou antes e passou depois, assim como a jornada financeira instalada.

Uma instabilidade do teste de avisos foi resolvida aguardando o botão habilitado antes do toque.
O teste financeiro também teve seu localizador corrigido para o cartão Caixa existente. Não houve
alteração das verificações de mensagens, valores, vínculo ou persistência para contornar falhas.

O teste antigo dos filtros comparava a URL inteira e exigia `%20` para espaços. O encoder normal
do Ktor usa `+`; os valores decodificados são equivalentes. Com aprovação do usuário, o teste passou
a conferir caminho e os cinco parâmetros decodificados exatos, preservando método GET e sucesso
da chamada. Acrescentar a verificação de sucesso revelou que a falha da assertion no MockEngine
antes era convertida em resultado de erro, que o teste não conferia. Os 11 casos agora passam.

Evidências locais desta rodada: `/tmp/saqz-critical-e2e.Dq3Cs5/`, logs `seven-final.log`,
`nav-green.log`, `athlete-query-red.log`, `athlete-query-approved.log`, `groups-data-final.log`,
`finance-fixed.log`, `approved-final-gates.log`, `verifier-full-e2e.log` e `verifier-fifo-repro.log`.
Correção de sincronização: `attendance-sync-fixed.log`, `attendance-order-sync-fixed.log`,
`attendance-sync-lint.log` e `seven-after-emulator-restart.log`. Tentativa incompleta:
`seven-attendance-sync-fixed.log`; reinício: `emulator-cold-restart.log`.
Revisão: `validation.md`, `verifier-full-after-sync.log`, `verifier-native-default-lint.log`,
`verifier-gate-counts.log`, logs `mutant-M1` a `mutant-M5`, `timing-H2-delayed-withdrawal.log`,
`verifier-original-apk-restored.log`, `cleanup-diagnostic.log` e `command-metadata-diagnostic.log`.
O runner também retém JUnit/logcat em pasta temporária anunciada na saída; esses artefatos são
locais e podem expirar. Não houve push ou deploy nesta rodada. O escopo não inclui ADM da
plataforma, iOS instalado, WhatsApp, denúncias/moderação ou provedores de pagamento.

## Rodada paralela supervisionada — 11/09/2026 (em andamento)

As frentes de comunicação, perfil e financeiro foram distribuídas pelo Orca. O coordenador
executa cada entrega no Android instalado antes de aceitá-la. Os **16 casos passaram juntos**,
sem falhas, erros ou skips; **o verificador independente desta rodada ainda está pendente**.
Os resultados históricos acima não são novas execuções desta rodada.
O novo agente foi criado pelo Orca, mas a inicialização parou no aviso de atualização do Codex;
o CLI recusou a seleção automática de Skip. Nenhuma revisão ou mutação desse verificador foi
executada. Essa etapa depende de selecionar Skip no terminal e retomar o dispatch.

| Gate observado pelo coordenador | Casos únicos | Resultado |
| --- | ---: | --- |
| Regressão dos sete E2E anteriores | 7 | PASS em conjunto, zero falhas/erros/skips |
| Preferências/notificações, paginação e lembretes | 3 | PASS em execuções focais |
| Cadastro esportivo e histórico mensal longo | 2 | PASS em execuções focais |
| Recusa de isenção/cancelamento ao atleta | 1 | PASS; somente autorização negativa |
| Privacidade e acesso ao perfil de membro | 1 | PASS após correção das ligações e sincronização da rolagem |
| Recebimento e despesa de quadra | 1 | PASS após ajuste de interação; zero falhas/erros/skips |
| Acerto final do jogo | 1 | PASS após selecionar a linha exata de Resultado; zero falhas/erros/skips |
| Regressões de perfil e navegação (commonTest no iOS) | 123 | PASS após correção; 96 grupos + 27 navegação |
| Guardas e processos do runner (Node) | 16 | PASS, zero falhas/skips; repetido após últimos ajustes |
| Regressão conjunta dos 16 instalados | 16 | PASS, zero falhas/erros/skips; Gradle 8m8s; não somar como casos adicionais |

Os dois achados ENV da rodada anterior receberam correção em `626d3bd9`: stdout de metadados
separado de stderr; limpeza tenta todos os recursos próprios mesmo após uma falha e informa
saída não zero se incompleta. Os 11 testes novos dos processos e os cinco guardas passaram.
As execuções instaladas também encerraram seus serviços/bancos descartáveis. A revisão
independente final dessas correções ainda não foi concluída.

O teste de privacidade encontrou duas ligações ausentes: a lista de membros do atleta dependia
de uma prévia não carregada; o menu do gestor oferecia editar o atleta, mas não ver seu perfil.
A correção foi autorizada sem alterar permissões de gestão ou privacidade. Papéis, dados e
verificações foram preservados.
Antes da correção, 47 testes focais executaram com sete falhas comportamentais nas ligações
ausentes. Após o ajuste, passaram os 96 testes de grupos e 27 de navegação selecionados.
Na primeira reexecução instalada, a etapa do atleta passou, mas a do dono parou no botão de
membros existente e fora da área visível após a rolagem. A interação passou a rolar até o botão
estar visível, dentro dos mesmos 20 segundos, mantendo visibilidade/habilitação como exigências.
O cenário inteiro então passou, para atleta e dono, sem mudar nenhuma verificação de privacidade.

Foram geradas 15 capturas (10 de detalhe e cinco de membros), com inspeção das novas entradas e
menus do dono/admin; não há card vazio de prévia, e as ações preservadas continuam visíveis.
O lint global passou novamente depois dos ajustes. Capturas são locais, ignoradas pelo Git.

No recebimento, a primeira tentativa teve timeout de resposta e saltos de relógio do ambiente.
A repetição recebeu HTTP 200 em 167 ms e comprovou cobrança paga e receita única, mas falhou ao
esperar o detalhe: a árvore ainda mostrava o caixa, com o saldo atualizado. Isso ainda não prova
defeito no resumo do detalhe. Despesa e visão posterior do atleta não foram alcançadas.
Após esperar a saída do conteúdo de confirmação e tocar o botão Voltar real do caixa, o cenário
inteiro passou, incluindo despesa, saldo de R$3,45 e mensalidade paga para o atleta. Nenhuma regra
financeira foi alterada. Essa execução não comprova o comportamento do botão Voltar sistêmico
durante o fechamento da folha. As duas primeiras tentativas de R11 falharam na preparação (Auth/Spring), antes de executar
testes; a repetição estável passou. Falhas e artefatos foram preservados.

No acerto, o primeiro teste encontrou dois valores de R$70,00 como irmãos de “Resultado”.
O localizador passou a exigir o valor à direita do rótulo, na mesma linha do card de resumo.
Com as verificações financeiras preservadas, passou toda a jornada: pendência bloqueia,
recebimento habilita, reabertura mostra encerrado e não há novas cobranças, despesas ou eventos.

Isenção/cancelamento com sucesso pela UI continua sem cobertura porque as ações não existem
no app. Não foi criada essa feature neste lote. Demais limites por cenário estão no
[inventário do runner](../e2e/android/README.md); 16 testes não equivalem a todas as features.

Evidências locais: `/tmp/saqz-parallel-e2e.ie8AD7/results.md`, `baseline-seven.log`,
`notification-settings.log`, `message-pagination-sdk.log`, `reminders.log`,
`sports-profile-ready.log`, `monthly-history.log`, `member-privacy.log`, `payments.log`,
`payments-stable.log`, `payments-interaction.log`, `charge-lifecycle-stable.log`,
`settlement-result-row.log`, `member-privacy-visible.log`, `p02-red-typed.log`,
`p02-green.log`, `p02-captures-lint.log`, `pre-full-node.log`, `pre-full-lint.log` e
`full-sixteen.log` (JUnit novo em `saqz-e2e-N5rMlb/junit`, saída 0 após limpeza).
JUnit e logcat de cada execução ficam na pasta `saqz-e2e-*` anunciada no respectivo log.
