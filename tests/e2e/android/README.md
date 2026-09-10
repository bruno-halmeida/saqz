# E2E do app instalado — Android

Jornadas reais pela `MainActivity`, com autenticação Firebase nativa contra o Auth Emulator,
injeção de dependências do app, Ktor, Spring e PostgreSQL descartável. Não usam gateways falsos
nem substituem a composição da tela. O painel da plataforma não faz parte desta suíte.

Os arquivos `.feature` continuam sendo roteiros manuais, sem executor Cucumber. Esta suíte
automatiza os recortes abaixo; sete testes não significam sete features integralmente cobertas.

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

Para repetir apenas um recorte, use `--scenario access|leave|attendance|attendance-order|communication|finance`
(escolha um nome, sem as barras). Exemplo:

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

Na execução completa, sucesso exige sete casos executados, sem falhas, erros ou skips, em XML novo.
Na execução focal, exige a contagem exata do recorte (dois em `access`, um nos demais). Build verde sem
testes não conta. O processo retorna código diferente de zero em falha de preparação ou teste.

- HTML: `mobile/android-app/build/reports/androidTests/connected/`.
- JUnit/logcat: `mobile/android-app/build/outputs/androidTest-results/connected/`.
- Logs de Spring/Firebase e cópia do JUnit: pasta temporária `saqz-e2e-*` anunciada pelo runner.
- Últimos resultados registrados: [evidencias.md](../../acceptance/evidencias.md).

No fim, o runner encerra os serviços e remove somente o container/banco que criou. Mantém logs,
emulador e APK de teste. Não restaura uma base compartilhada nem apaga volumes de desenvolvimento.
As fixtures dentro do APK de teste contêm apenas contas descartáveis; não contêm bearer tokens.
Revise/redija logs antes de compartilhá-los. Para teste manual, prepare um ambiente próprio:
os serviços desta execução deixam de existir ao terminar.

Verificações complementares:

```sh
node --test tests/e2e/android/guard.test.mjs
mobile/gradlew -p mobile detektAll
mobile/gradlew -p mobile :compose-app:iosSimulatorArm64Test
mobile/gradlew -p mobile :android-app:testDevDebugUnitTest --tests '*FirebaseAuthBootstrapTest'
```

O último comando usa a configuração dev padrão, sem o opt-in E2E. Testes Compose de `commonTest`
rodam no simulador iOS conforme `mobile/AGENTS.md`; isso não é uma jornada instalada no iPhone.

## Ainda fora desta suíte

Cadastro/reset de senha, OAuth, orientação, assinatura/criação de grupo, convites e links nativos,
edição de grupo/jogo, prazo encerrado, job automático de mensalidades, recebimento/isenção/cancelamento,
despesas/acerto, perfil/privacidade, mapa, notificações, paginação, concorrência e perda de resposta
após commit ainda precisam de E2E instalado. Alguns já têm testes de domínio, UI controlada ou API;
essas camadas não são contadas aqui como E2E do aplicativo.

Também não estão cobertos iOS instalado, aparelhos físicos, infraestrutura remota de homologação,
checkout/provedores reais, ADM da plataforma, WhatsApp ou denúncias/moderação.
