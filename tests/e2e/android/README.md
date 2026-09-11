# E2E do app instalado — Android

Jornadas reais pela `MainActivity`, com autenticação Firebase nativa contra o Auth Emulator,
injeção de dependências do app, Ktor, Spring e PostgreSQL descartável. Não usam gateways falsos
nem substituem a composição da tela. O painel da plataforma não faz parte desta suíte.

Os arquivos `.feature` continuam sendo roteiros manuais, sem executor Cucumber. Esta suíte
automatiza os recortes abaixo; a contagem de testes não significa features integralmente cobertas.

## Cenário por cenário

| Teste | Roteiro / recorte automatizado | Resultado exigido |
| --- | --- | --- |
| `AccessE2eTest.loginRecreationLogoutAndAccountIsolation` | APP-R01 | Login por senha; grupos G1/G2; mesma identidade após recriar Activity; logout remove sessão e conteúdo protegido; outra conta só vê G2. |
| `AccessE2eTest.invalidPasswordCannotOpenProtectedContentAndCorrectionWorks` | APP-R02, credenciais | Senha errada mostra erro e não autentica; correção permite entrar com a identidade esperada. Não cobre indisponibilidade de rede. |
| `GroupLeaveE2eTest.cancellationDepartureAndOwnerProtectionPersistAcrossSessions` | APP-L01/L02/L03; repetição de L04; acesso de N04 | Cancelar mantém vínculo; confirmar remove apenas G1; resultado persiste após recriação; jogo/chat ficam inacessíveis; repetir DELETE é seguro; dono não pode sair e mantém o jogo original. Não cobre histórico financeiro nem revogação da caixa de notificações. |
| `AttendanceE2eTest.fullGameWaitlistsAthleteAndWithdrawalPromotesThemWithoutOverbooking` | APP-R08 | Terceira confirmação entra na reserva, posição 1; desistência libera vaga; atleta promovido vê confirmação após novo login; elenco permanece com exatamente duas confirmações. |
| `AttendanceOrderE2eTest.withdrawalPromotesFirstWaitingAthleteAndKeepsSecondInQueue` | APP-R08, ordem FIFO | Com dois reservas ordenados, desistência promove o primeiro; o segundo continua aguardando. Detecta promoção do último no lugar do primeiro. |
| `CommunicationE2eTest.chatAndNoticesPersistForOtherAccountsWithCorrectPublishingPermissions` | APP-C01/C02 | Chat do atleta e aviso do dono persistem uma vez, com texto, autor, grupo e canal corretos; outra conta lê; atleta não publica aviso; G2 não recebe conteúdo de G1. |
| `MonthlyGenerationE2eTest.reviewedMonthlyChargeIsIsolatedAndRepeatCannotDuplicateOrRepriceIt` | APP-MG01, dono/seleção/revisão/repetição; APP-MG02, seleção vazia; APP-F01, cobrança própria | Sem seleção automática; seleção vazia bloqueia revisão; avulso não é destinatário; abrir/revisar/editar não cria cobrança; confirmar cria somente R$123,45 para o mensalista selecionado; repetir com R$200 mantém ID, versão, valor e vencimento originais; geração não é receita nem mensagem; atleta vê a própria pendência. |
| `NotificationsE2eTest` | APP-N01/N03, preferências e caixa de notificações | Desativar chat persiste após reabrir; só futuras notificações desse canal deixam de chegar; aviso/lembrete abrem o grupo/jogo correto; leitura não altera a caixa alheia. Não cobre push nem falhas de rede. |
| `MessagePaginationE2eTest` | APP-C04, paginação e limites | 53 mensagens em páginas de 50+3, IDs/textos/autores/ordem exatos e sem duplicatas; fim remove Carregar mais; vazio não envia; limite de 2.000 caracteres; atualizar retorna à primeira página; G2 isolado. Não induz falha/retry da página seguinte. |
| `ReminderE2eTest` | APP-C05, destinatários e permissões | Lembrete pela UI chega somente ao membro ativo sem resposta, excluindo remetente; confirmados, recusados, inativos e não membros não recebem; presença, cobranças e extrato não mudam; atleta e jogos inválidos não podem enviar. Não cobre perda de resposta/repetição de requestId originada na UI. |
| `SportsProfileE2eTest` | APP-P01, cadastro esportivo | Alterar Ponta para Central retorna ao Perfil e persiste após reabrir, recriar Activity e novo login; demais campos, cadastro de praia em G2 e outro membro permanecem intactos. |
| `MemberPrivacyE2eTest` | APP-P02, visibilidade NOBODY | Atleta recebe 403 nas estatísticas alheias, mas lê o perfil básico correto; dono lê estatísticas exatas; telefone privado fica oculto para ambos; mensalidade alheia não aparece. Não cobre outras políticas de telefone nem falha de rede. |
| `MonthlyHistoryE2eTest` | APP-F01/F02, histórico longo e vazio | Todas as 12 mensalidades aparecem com valores, vencimentos e quatro estados corretos; exclui cobranças GAME e de outro membro; G2 exibe vazio; abrir pagamento leva ao Pix de G1; leitura não muda o financeiro. Não induz falha parcial por grupo. |
| `PaymentE2eTest` | APP-R10, recebimento; APP-R12, despesa | Receber R$123,45 pela UI registra PAID/PIX e um único evento; saída de quadra R$120,00 gera um lançamento; caixa/extrato conciliam R$3,45, persistem na reabertura e não afetam G2; atleta vê a mensalidade paga. Geração é precondição, não teste do job. |
| `ChargeLifecycleE2eTest` | APP-R11, somente negativa de autorização | Atleta não vê o caixa administrativo; tentativas autenticadas de isenção/cancelamento com If-Match válido retornam 403, preservando cobranças, versões, eventos, pendências e extrato. **Não cobre sucesso pela UI: essas ações ainda não existem no app.** |
| `SettlementE2eTest` | APP-AC01/R12, pendência e reabertura | Cobrança de jogo R$70 bloqueia encerrar; recebimento pela UI habilita conclusão; dois acionamentos não criam nova escrita; reabrir mostra acerto encerrado, mantendo pagamento único, elenco, jogo e extrato. Não induz resposta perdida. |

Fontes dos resultados esperados: [regressão](../../acceptance/app-regressao.feature),
[ligações](../../acceptance/app-ligacoes.feature) e [financeiro](../../acceptance/financeiro-final.feature).
Testes: [fontes Kotlin](../../../mobile/android-app/src/e2e/kotlin/br/com/saqz/androidapp).

## Executar

Pré-requisitos: JDK 21 no `JAVA_HOME`/`PATH`, Node (execução local verificada com 26.8.1),
Docker funcionando, Android SDK/`adb` no `PATH` e emulador Android já iniciado.
Use um AVD dedicado; a referência local é `Saqz_API_30`, Android 11 / API 30.

Na raiz do repositório, instale a versão fixa do Firebase CLI em uma pasta separada:

```sh
SAQZ_E2E_TOOLS="$(mktemp -d)"
npm install --prefix "$SAQZ_E2E_TOOLS" firebase-tools@15.25.1
export SAQZ_E2E_FIREBASE_BIN="$SAQZ_E2E_TOOLS/node_modules/firebase-tools/lib/bin/firebase.js"
adb devices
node tests/e2e/android/run.mjs --serial emulator-5554
```

Troque o serial pelo AVD mostrado pelo `adb`. O runner recusa aparelho físico, serial ausente,
serviços já ocupando as portas 18080/9099/4400/4500 e versão diferente do Firebase CLI.
Não configure credenciais de produção. A primeira execução pode baixar dependências e a imagem
`postgres:16-alpine`; as seguintes reutilizam caches, mas sempre criam banco e usuários novos.

Para repetir apenas um recorte, use `--scenario NOME`. Nomes disponíveis:
`access`, `leave`, `attendance`, `attendance-order`, `communication`, `finance`,
`notification-settings`, `message-pagination`, `reminders`, `sports-profile`, `member-privacy`,
`monthly-history`, `payments`, `charge-lifecycle` e `settlement`. Exemplo:

```sh
node tests/e2e/android/run.mjs --serial emulator-5554 --scenario finance
```

Sem a opção, todos rodam. Nome desconhecido ou vazio é erro. A saída identifica os recortes e a
quantidade exigida; um `PASS` focal não aprova os demais cenários.

O comando constrói o backend, aplica todas as migrations em banco novo, prepara os dados e instala
`app.saqz.e2e`, separado de `app.saqz`. O opt-in `-Psaqz.e2e=true` força Firebase `saqz-local` e
API `http://10.0.2.2:18080`. Rode pelo runner: chamar o Gradle isoladamente não prepara os serviços.

Grupos e vínculos iniciais são inseridos somente no banco descartável. Usuários são criados pelo
Auth Emulator e pelo bootstrap real da API; jogos e presenças iniciais usam a API. As ações em
verificação são feitas na UI; consultas autenticadas independentes conferem o resultado persistido.
Nenhum teste de criação de grupo/plano é inferido do seed.

## Resultado e evidências

Na execução completa, sucesso exige 16 casos executados, sem falhas, erros ou skips, em XML novo.
Na execução focal, exige a contagem exata do recorte (dois em `access`, um nos demais). Build verde sem
testes não conta. O processo retorna código diferente de zero em falha de preparação, teste ou limpeza.

- HTML: `mobile/android-app/build/reports/androidTests/connected/`.
- JUnit/logcat: `mobile/android-app/build/outputs/androidTest-results/connected/`.
- Logs de Spring/Firebase e cópia do JUnit: pasta temporária `saqz-e2e-*` anunciada pelo runner.
- Últimos resultados registrados: [evidencias.md](../../acceptance/evidencias.md).

No fim, o runner tenta encerrar todos os serviços e remover somente o container/banco que criou,
mesmo se o force-stop do APK ou outra etapa da limpeza falhar. Falha de limpeza é reportada e
retorna código não zero. Metadados de comandos vêm somente de stdout; stderr continua visível
como diagnóstico, sem contaminar IDs de recursos. Mantém logs,
emulador e APK de teste. Não restaura uma base compartilhada nem apaga volumes de desenvolvimento.
As fixtures dentro do APK de teste contêm apenas contas descartáveis; não contêm bearer tokens.
Revise/redija logs antes de compartilhá-los. Para teste manual, prepare um ambiente próprio:
os serviços desta execução deixam de existir ao terminar.

Verificações complementares:

```sh
node --test tests/e2e/android/guard.test.mjs tests/e2e/android/process.test.mjs
mobile/gradlew -p mobile detektAll
mobile/gradlew -p mobile :compose-app:iosSimulatorArm64Test
mobile/gradlew -p mobile :android-app:testDevDebugUnitTest --tests '*FirebaseAuthBootstrapTest'
```

O último comando usa a configuração dev padrão, sem o opt-in E2E. Testes Compose de `commonTest`
rodam no simulador iOS conforme `mobile/AGENTS.md`; isso não é uma jornada instalada no iPhone.

## Ainda fora desta suíte

Cadastro/reset de senha, OAuth, orientação, assinatura/criação de grupo, convites e links nativos,
edição de grupo/jogo, prazo de presença encerrado, job automático de mensalidades, isenção/cancelamento
pela UI, foto de perfil, outras políticas de privacidade, mapa, falhas/retry de notificações e paginação,
concorrência e perda de resposta após commit ainda precisam de E2E instalado. Isenção/cancelamento
também dependem da implementação das ações no app; testar a API não fecha essa lacuna.
Alguns desses recortes já têm testes de domínio, UI controlada ou API;
essas camadas não são contadas aqui como E2E do aplicativo.

Também não estão cobertos iOS instalado, aparelhos físicos, infraestrutura remota de homologação,
checkout/provedores reais, ADM da plataforma, WhatsApp ou denúncias/moderação.
