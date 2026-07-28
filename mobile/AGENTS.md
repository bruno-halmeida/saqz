# AGENTS.md — Saqz Mobile (KMP + Compose Multiplatform)

Arquitetura desta pasta (`mobile/`). Vale para Android e iOS: **todo código de produto vive em
`commonMain`**; `androidMain`/`iosMain` existem só para o que a plataforma exige (fontes, engine
HTTP, keystore, ports nativos).

## Como ler este documento

Este documento descreve o **código que existe**, não um alvo aspiracional. A versão anterior
documentava um alvo (MVI obrigatório por rota, módulo `:navigation` com cinco back stacks, camada
`ScreenState`/`ScreenIntent`, design system central) que o projeto **Reset da Apresentação Mobile**
desfez. Se você encontrar divergência entre este texto e o código, o **código** é a fonte da verdade
e este arquivo é o bug.

As decisões vivas moram no Linear, não no repositório: **[Architecture Decisions (ADs) — estado
vivo](https://linear.app/vulkz/document/architecture-decisions-ads-estado-vivo-f94c40811c16)**.
Não existe mais `.specs/` nem `AGENTS.md` na raiz. O gate que bloqueava a reintrodução dos dois
(`scripts/check-scope`) foi removido com a CI antiga e ainda não tem substituto.

### O que o reset fez

A apresentação autenticada foi removida integralmente. Sobreviveram login, verificação de sessão,
domínio, dados, rede e as integrações nativas. Depois do login o usuário cai num **shell vazio**.

- **AD-031** (supersede AD-025 e AD-029) é a decisão que governa o que se reconstrói e como.
- **AD-030** (fronteiras `presentation → domain ← data`) foi **mantido** — o reset não mexeu nas
  fronteiras de compilação.

---

## 1. Módulos

```
:core:common                ← MviViewModel, SaqzUiState, formatadores (moeda, data, timezone)
:core:domain                ← SaqzResult, SaqzError, DataError, GroupId
:core:network               ← NetworkClient, HttpTransport, retry, mapeamento de erro de rede
:features:access            ← login + verificação de sessão: presentation/, ui/, navigation/
:features:access:domain     ← AccessSession, ports nativos de acesso
:features:access:data       ← KtorSessionGateway
:features:groups            ← contratos, formulários e ports nativos de grupo
:features:groups:domain     ← agregados: grupo, atleta, jogo, presença, financeiro, foto
:features:groups:data       ← Ktor*Gateway de cada agregado
:features:groups:presentation ← rotas e apresentação compartilhada da jornada de grupos
:compose-app                ← composition root: Koin, NavDisplay, shell vazio, ports nativos
:android-app  /  ios-app    ← entrypoints de plataforma
```

São **13 módulos Gradle**. **Não existe `:navigation`.** O módulo raiz de `groups` guarda contratos,
formulários e ports; a apresentação da jornada vive em `:features:groups:presentation`. O domínio e
os gateways permanecem intactos.

### Regras de dependência (hoje sem gate)

| De | Pode depender de |
|---|---|
| `<x>:domain` | **só** `:core:domain` |
| `<x>:data` | `<x>:domain`, `:core:domain`, `:core:network` |
| `<x>` (raiz) | `<x>:domain`, `:core:domain`, `:core:common`, Compose, Nav3 runtime |
| `<x>:presentation` | `<x>:domain`, `<x>` (raiz), `:core:common`, `:core:design-system`, Compose, Nav3 runtime |
| `:compose-app` | tudo — único módulo que enxerga `:features:*:data` |

Nenhuma feature depende de outra. O gate que verificava isso (`scripts/check-mobile-boundaries`,
que descobria as features no disco e cruzava todas contra todas) foi removido com a CI antiga.
Até existir substituto, a tabela acima depende de disciplina.

Version catalog (`gradle/libs.versions.toml`) para **toda** versão. Config não-trivial vira
convention plugin em `:build-logic` (`saqz.kmp-compose-library`, `saqz.android-application`,
`saqz.detekt`).

---

## 2. Camada `domain`

Um arquivo por agregado (`game/Game.kt`), contendo modelos, comandos, o tipo de erro e a interface
do gateway. Puro: sem Compose, sem Ktor, sem Koin, sem Android — só Kotlin + `:core:domain`.

```kotlin
data class Game(val id: String, val groupId: GroupId, /* ... */, val version: Long)

sealed interface GameError : SaqzError {
    data class Validation(val error: DataError.Validation) : GameError
    data object HiddenResource : GameError   // 404 e 403 colapsam: não vaze existência de recurso
    data object Conflict : GameError
    data class Data(val error: DataError) : GameError
}

interface GameGateway {
    suspend fun list(groupId: GroupId): SaqzResult<List<Game>, GameError>
}
```

**Gateway, não `Repository`.** A fonte é sempre a API remota, e o nome canônico de Clean Architecture
para essa porta é *gateway*. No dia em que existir cache local, nasce um `<X>Repository` que
**compõe** gateways — não se infla o gateway.

Toda porta usada por um ViewModel tem interface em `domain`. É o que impede `presentation → data` e o
que torna o ViewModel testável com fake. Erro é sempre tipado e por feature.

---

## 3. Camada `data`

Um `Ktor<X>Gateway` por agregado:

1. **DTOs `internal`** com sufixo `Transport`, nunca visíveis fora do módulo.
2. **Mappers** como extensões privadas: `GameTransport.toDomain()`.
3. **Chamada** pelo `NetworkClient` compartilhado de `:core:network`.
4. **Tradução de erro**: `NetworkError` → `<X>Error`. HTTP morre aqui.

Três disciplinas que **ficam** — cada uma evitou bug real:

- **Retry explícito por segurança**: `retryTransport(RetrySafety.Read | IdempotentWrite | Never)`.
  Escrita só entra em retry se carregar `requestId`; sem ele, `Never`.
- **Concorrência otimista**: leitura captura o `ETag` num `<X>VersionToken`; escrita devolve o token
  em `If-Match`. Resposta sem ETag onde ele é esperado é `InvalidResponse`, não crash.
- **Idempotência**: `requestId` gerado na apresentação e enviado no corpo, para o backend deduplicar
  reenvio.

O `HttpClient` é criado **uma vez** em `:core:network`, recebendo o engine por parâmetro para que o
teste injete `MockEngine`.

---

## 4. Camada `presentation`

**ViewModel só quando há estado assíncrono, persistência ou comportamento real a coordenar
(AD-031).** Não existe mais obrigação de ViewModel por rota. Tela sem estado próprio — placeholder,
estado vazio, tela institucional, o shell autenticado atual — é um `Composable` puro recebendo
parâmetros. `State`/`Intent`/`Effect` vazios "por simetria" são boilerplate, não arquitetura.

Quando o ViewModel se justifica, a base está em `:core:common`:

```kotlin
abstract class MviViewModel<S, I, E>(initialState: S) : ViewModel() {
    val state: StateFlow<S>
    val effects: Flow<E>              // Channel.BUFFERED + receiveAsFlow
    protected fun update(transform: (S) -> S)
    protected fun emit(effect: E)
    abstract fun onIntent(intent: I)
}
```

**`Intent`/`Effect`/`onIntent`**, não `Action`/`Event`/`onAction` — é o vocabulário do código. State,
Intent e Effect da tela ficam num **único** `<Tela>Contract.kt`, não em quatro arquivos. Estado
sempre via `update { it.copy(...) }`.

Tela que não emite efeito usa `MviViewModel<State, Intent, Nothing>` — não uma `sealed interface` sem
implementação. (`LoginEffect` hoje é uma sealed interface vazia e documentada: é dívida, não padrão a
copiar.)

### Disciplinas obrigatórias

- **Guarda de geração** em carga assíncrona: a resposta é descartada se o contexto mudou.
- **Intent inválido retorna cedo**: não emite efeito, não altera estado.
- **Regra densa sai do ViewModel**: validação e permissão vivem em arquivos próprios e testáveis
  (`AccessValidators.kt` é o exemplo no código).
- **Process death**: formulário longo persiste em `SavedStateHandle` só os campos que importam.
- **Dispatcher não se injeta** em ViewModel (`viewModelScope` basta). Injete só em classe que
  despacha para `IO` **e** é testada direto.

---

## 5. Camada `ui` — Compose

Dois níveis:

```
Root    → liga ViewModel à tela: resolve DI, coleta state, observa effects
Screen  → função pura (state, onIntent) → UI. Previewável e testável sem DI.
```

```kotlin
@Composable
fun LoginRoot(viewModel: LoginViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LoginScreen(state = state, onIntent = viewModel::onIntent)
}
```

A camada intermediária `ScreenState`/`ScreenIntent` do texto antigo **não existe mais** — o Screen
recebe o State do ViewModel direto. Estado puramente visual mora em `remember` no próprio Screen.

**O design system compartilhado é o export oficial, e nada além dele (AD-031, revisto em
2026-07-27).** `:core:design-system` existe porque o kit de handoff do cliente **já entregou um
design system pronto** — tokens, componentes e uma implementação Compose de referência —, não
porque alguém extraiu componentes de uma tela. O **fluxo 10 do export** ("Componentes comuns") é a
fronteira:

- o que o fluxo 10 lista **mora no módulo compartilhado** desde o primeiro uso, porque não é
  generalização nossa: é o inventário do que o próprio design diz que se repete nas telas — os 14
  primitivos do `_ds_manifest.json` **mais** as peças que o fluxo marca como "a criar" (Switch,
  Stepper, Segmented, chips, Avatar, Skeleton, Spinner, Progress, banner offline, divisória);
- o que **não** está no fluxo 10 nasce **dentro da jornada que o usa** e só sobe no **segundo uso
  concreto** — nunca antes, nunca "para padronizar".

A fronteira é o fluxo 10 e não o `_ds_manifest.json` porque a etiqueta "a criar" do fluxo significa
*"aparece nas telas mas ainda não virou componente no design"* — é repetição documentada, não
ausência de decisão. O manifesto é um recorte do que já foi empacotado, não do que existe.

A segunda regra é a original e continua valendo na íntegra: extrair no primeiro uso foi exatamente o
que produziu o design system que o reset apagou. O que mudou foi só a origem — antes o módulo
compartilhado era invenção nossa, agora ele é o desenho que recebemos.

Regras que continuam:

- **`testTag` via `object <Tela>Tags`** com constantes — testes de UI e journeys dependem disso.
- **Nada de `remember` para estado de aplicação.** Só estado de composição (`LazyListState`).
- **Efeitos** via observação do `effects` no Root; `LaunchedEffect` só quando não há equivalente no
  ViewModel.
- **`CompositionLocal` custom é proibido** — o detekt reprova.
- **`key` em lazy list** quando há id óbvio; **`contentDescription`** significativo em tudo que é
  interativo, `null` no decorativo.
- **`@Preview` por Screen**, com dado realista.
- **Strings em PT-BR** via `composeResources`.
- `dp` e `Color(0x…)` crus em `mobile/features/*/src/commonMain` são proibidos por convenção. O
  gate com teto (`scripts/check-design-tokens`) foi removido com a CI antiga; o último teto medido
  era 258 `dp` crus e 2 cores cruas, útil como ponto de partida ao reconstruir.

---

## 6. Navegação

**Navigation3 é biblioteca, não módulo.** Um único `NavDisplay` (`SaqzNavHost` em `:compose-app`)
sobre **um** back stack de acesso→shell, sempre com exatamente uma entrada.

- A UI **não navega**. `reconcileAccessStack` deriva o stack do estado de sessão autoritativo; cada
  `SessionAccessState` canonicaliza para o seu destino único, incluindo `Ready` → shell vazio.
- Rotas são `NavKey` `@Serializable` com argumentos **escalares**, declaradas na feature
  (`AccessRoute` em `:features:access`). Objeto complexo não navega: passe o id.
- A feature depende só de `navigation3-runtime` (o contrato `NavKey`), nunca de `navigation3-ui`.
- Quem conhece `NavDisplay` é `:compose-app`. **Navegação entre features é callback.**

Quando as jornadas voltarem e o stack precisar de profundidade real, ele cresce aqui — não num módulo
novo.

---

## 7. DI — Koin, e somente Koin

**Koin é o único mecanismo de injeção.** Se um objeto tem dependência, ele é construído por um módulo
Koin. Não existe segunda via.

### Proibido

- `<X>ViewModelFactory` escrito à mão.
- Default de parâmetro com implementação concreta (`port: TimeZonePort = DefaultTimeZonePort()`).
- `remember { AlgumaImpl() }` ou `by lazy { ... }` para obter dependência.
- Repassar dependência manualmente pela árvore de composables.
- Singleton via `object` para algo que tem colaborador.

Prefira `singleOf` / `viewModelOf` / `factoryOf`; caia para `single { }` só quando não dá para
expressar com referência de construtor. Uma implementação que serve várias interfaces recebe
múltiplos `bind` — uma instância, não duas.

Só o app-shell monta (`SaqzKoinBootstrap` em `:compose-app`). `koinViewModel()` como default do
parâmetro **no Root e só no Root** — resolve pelo `ViewModelStoreOwner` do `NavEntry` e deixa teste e
preview passarem um fake. Nunca passe ViewModel adiante na árvore.

Teste **não sobe container Koin** — instancia o ViewModel com fakes direto. A exceção é
`SaqzKoinModulesTest`, que existe justamente para verificar que todo grafo resolve.

---

## 8. Erros — `SaqzResult`

```kotlin
interface SaqzError
sealed interface SaqzResult<out T, out E : SaqzError> {
    data class Success<out T>(val value: T)
    data class Failure<out E : SaqzError>(val error: E)
}
typealias EmptyResult<E> = SaqzResult<Unit, E>
```

Com `map`, `mapError`, `onSuccess`, `onFailure`, `asEmptyResult` — encadeáveis.

Nunca lance exceção para falha esperada. Quem é dono da exceção a captura e a converte:

| Origem | Captura em | Vira |
|---|---|---|
| HTTP / socket | `data` | `<X>Error` da feature |
| Regra de negócio, validação | `domain` | erro tipado da feature |
| Exibição | `presentation` | texto de UI |

`SaqzResult` não é privilégio da camada de dados: use em qualquer função com sucesso e falha tipados
(validação de formulário, política de autorização). Um erro por resultado — não lista de erros.

Erro que chega ao usuário é traduzido na apresentação (`AuthUiErrorMapper` é o exemplo no código).
Valor que **sempre** é dinâmico — nome, data formatada, valor em reais — é `String` no estado.

---

## 9. Ports nativos

Capacidade que só a plataforma entrega (timezone, storage de rascunho, câmera, auth Firebase) é
declarada como **port** no pacote `port/` da feature e implementada no `compose-app`/plataforma:

```kotlin
fun interface GroupSystemTimeZonePort {
    fun detect(done: (GroupSystemTimeZoneResult) -> Unit)
}
```

Ports usam **callback, não `suspend`**, e tipos exportáveis para Swift. Motivo real: `suspend` e
`@JvmInline value class` atravessam mal a bridge Kotlin/Native — o Swift recebe um `id` opaco e não
consegue construir o modelo. Pelo mesmo motivo, tipo que o Swift precisa construir é `data class`.
Implementação registrada em Koin como qualquer outra dependência.

---

## 10. Testes

`commonTest` com **`kotlin.test`** (JUnit5 é JVM-only, portanto impossível em `commonTest` de KMP).
JUnit4 aparece em `:android-app` (instrumentado e Roborazzi) e nas suítes visuais
`androidHostTest` explicitamente declaradas por módulos de apresentação. Coroutines:
`kotlinx-coroutines-test` + `UnconfinedTestDispatcher` com `Dispatchers.setMain()` no setup.

| Alvo | Onde | Como |
|---|---|---|
| Regra de domínio | `<x>:domain/commonTest` | teste puro, sem fake |
| Gateway | `<x>:data/commonTest` | `MockEngine`; cobrir mapeamento de erro e ETag |
| ViewModel | `<x>/commonTest` | **fake** do gateway, assert em `state` e `effects` |
| Screen | `<x>/commonTest` | `ComposeTestRule` + `testTag` do objeto `Tags` |
| Journey Android | `android-app/androidTest` | fluxo real ponta a ponta |
| Adapter nativo iOS | `ios-app/SaqzIOSTests` | Swift, direto contra o adapter |
| Visual integrado | `android-app/src/test` | Roborazzi para telas e jornadas montadas |
| Visual de componente | `<feature>:presentation/src/androidHostTest` | Roborazzi local ao módulo — **olhe os PNGs** antes de entregar UI |

**Teste de Compose que mora em `commonTest` roda no iOS, nunca na JVM.** `runComposeUiTest` exige o
runner do Robolectric, e esse runner é `@RunWith` de JUnit4 — anotação JVM-only, impossível em
`commonTest`, que também compila para iOS. Na JVM sem o runner, todo `runComposeUiTest` estoura
`NullPointerException` em `Build.FINGERPRINT`. Por isso o gate dessas suítes é
`iosSimulatorArm64Test`, e a compilação `androidHostTest` de `:features:groups:presentation` carrega
**só** a suíte de captura (VUL-100). Se você precisa de Robolectric, o arquivo vai para
`androidHostTest` com `@RunWith(RobolectricTestRunner::class)`, não para `commonTest`.

**Fake > mock, sempre.** Fake é implementação em memória da interface, com um `var shouldFail` para o
caminho triste. `SavedStateHandle` se instancia direto, sem mock.

---

## 11. Gates antes de abrir PR

A CI antiga (`scripts/` + workflows de gate) foi removida para ser redesenhada do zero. Até a nova
existir, rode as tasks direto:

```sh
mobile/gradlew -p mobile detektAll                                  # lint
mobile/gradlew -p mobile :android-app:testDevDebugUnitTest          # testes integrados que rodam na JVM
mobile/gradlew -p mobile :features:groups:presentation:recordRoborazziAndroidHostTest # visuais locais de Grupos
mobile/gradlew -p mobile :android-app:connectedDevDebugAndroidTest  # precisa de emulador
```

Local: JDK 21, `DOCKER_HOST` do Colima, emulador `Saqz_API_30` ligado.

Atenção ao redesenhar: `allTests` é NO-SOURCE nos módulos `core/*` e `features/*` que não declaram
host tests, porque o plugin `com.android.kotlin.multiplatform.library` os desabilita por padrão.
`:features:groups:presentation` é a exceção deliberada: faz opt-in com `withHostTest { }` para manter
as capturas dos componentes compartilhados junto de quem os possui; seu comando dedicado acima é
parte do gate visual e não é alcançado por `:android-app:testDevDebugUnitTest`. Os arquivos de
`commonTest` só rodam como `iosSimulatorArm64Test`, em macOS — num runner Linux essas suítes passam
sem executar nada. **Isso vale também dentro dessa exceção:** a hierarquia padrão do KMP faria
`androidHostTest` depender de `commonTest` e recompilá-lo na JVM, onde todo `runComposeUiTest`
reprova (§10), então o `build.gradle.kts` do módulo restringe a compilação de host test à suíte de
captura (VUL-100). `testAndroidHostTest` ali executa exatamente as suítes `androidHostTest` — se
ficar vermelho, é falha real. Quem declarar `withHostTest { }` num módulo novo herda o mesmo
problema e precisa da mesma restrição.

Detekt usa **baseline por módulo**: dívida antiga congelada, código novo falha. **Não regenere
baseline para calar erro seu.** Quando a regeneração é legítima (o código baselinado foi apagado),
rode `detektBaselineAll` no módulo: ela executa as tasks de todos os source sets, escreve um parcial
por source set em `build/detekt-baselines/` e só então funde tudo em `detekt-baseline.xml`. Módulo
sem nenhum smell fica **sem** arquivo de baseline, de propósito.

### Prints no corpo do PR

**PR que mexe em componente ou tela leva os prints no corpo.** Todos os estados possíveis daquilo
que você mudou: habilitado, desabilitado, carregando, erro, vazio, selecionado, cada variante e cada
tom. Gere com `recordRoborazziDevDebug`, empurre os PNGs para a branch `screenshots` e embuta o raw:

```markdown
![segmented desabilitado](https://raw.githubusercontent.com/bruno-halmeida/saqz/screenshots/vul-NN/segmented-desabilitado.png)
```

`screenshots` é uma branch **órfã**, sem ancestral comum com a `main`, e **não se mergeia**. O
`README.md` da raiz dela explica o padrão de caminho (`<ticket>/<nome>.png`) e como empurrar.
`mobile/android-app/screenshots/` está no `.gitignore`: PR de código não toca em binário, então dois
PRs paralelos não têm mais como conflitar em pixel.

**Estado que não está na cena não está sendo conferido.** Um contraste de 1,01:1 no
`AttendanceSelector` desabilitado atravessou revisão visual inteira porque a captura só tinha
estados habilitados — ninguém mentiu, ninguém desatendeu; o defeito simplesmente não estava na foto.
O VUL-43 achou mais seis estados nunca olhados pelo mesmo motivo. Se você mudou um estado, ele vai
para a cena, mesmo que seja o quinto tom do mesmo chip.

Duas armadilhas de comando, ambas já custaram tempo real:

- `recordRoborazziDevDebug` **regrava o catálogo inteiro**, não só a cena que você mexeu. Com os
  PNGs fora do git isso deixou de causar conflito, mas saiba o que o comando faz antes de olhar o
  diff de sete arquivos e achar que quebrou alguma coisa.
- O render da referência (Chrome headless, comando no [`README.md`](../README.md) da raiz) sai com
  **exit 144**: ele grava o PNG e não encerra sozinho. **Não é falha.** Confira que o arquivo existe
  e siga — perseguir esse código de saída já custou 17 minutos a um agente.

---

## 12. Convenções de nome

| Coisa | Padrão | Exemplo |
|---|---|---|
| Gateway (interface) | `<Agregado>Gateway` | `GameGateway` |
| Gateway (impl) | descreve o que a torna única | `KtorGameGateway` |
| Repository (só multi-fonte) | descreve o comportamento | `OfflineFirstGameRepository` |
| DTO | `<Modelo>Transport`, `internal` | `GameTransport` |
| Mapper | extensão privada | `GameTransport.toDomain()` |
| ViewModel | `<Tela>ViewModel` | `LoginViewModel` |
| MVI | `<Tela>Contract.kt` com State/Intent/Effect | `LoginContract.kt` |
| UI model | sufixo `Ui` | `GroupMemberUi` |
| Composables | `<Tela>Root` + `<Tela>Screen` | `LoginRoot`, `LoginScreen` |
| Test tags | `object <Tela>Tags` | `LoginTags.Email` |
| Port | `<Capacidade>Port` | `GroupDraftStorePort` |
| Módulo Koin | `<feature><Camada>Module` | `accessDataModule` |
| Rota | `<Feature>Route`, `NavKey` `@Serializable` | `AccessRoute.Login` |

**Sufixo `Impl` é proibido** — nomeie pelo que distingue (`Ktor…`, `Room…`, `Default…`).

---

## 13. Marcações

Duas marcações, propósitos diferentes. Ambas exigem justificativa; sem ela, não conta.

**`SPEC_DEVIATION:`** — o código contraria um AD ou este documento. Exige `Reason:`.

```kotlin
// SPEC_DEVIATION: data class, não @JvmInline value class.
// Reason: Kotlin/Native apaga membros de value class; Swift não consegue construir o modelo.
```

**`ponytail:`** — o código está deliberadamente mais simples do que poderia, com teto conhecido.
Nomeia o teto e o caminho de upgrade, e é colhível por `/ponytail-debt`.

```kotlin
// ponytail: filtro em memória; paginar no servidor se a lista passar de ~500 jogos
```

**O que "lazy" significa aqui.** Depois do AD-031 a preguiça vale mais largo do que antes: não há
estrutura obrigatória por rota e não há módulo de navegação para registrar destino. Um `Composable`
puro é resposta completa para uma tela sem estado. O design system compartilhado existe, mas ele se
**consome**, não se alimenta: componente novo só entra em `:core:design-system` se estiver no export
oficial — fora disso, nasce na jornada.

O que a preguiça **não** dissolve — e o gate confirma:

- as fronteiras `presentation → domain ← data` e a interface do gateway em `domain`;
- `SaqzResult` em vez de exceção para falha esperada;
- `RetrySafety`, `requestId` e ETag na camada de dados;
- callback em port nativo, porque a bridge K/N exige;
- Koin como via única de injeção.

Essas cinco não são cerimônia: são o que dá teste sem emulador e troca de implementação sem tocar em
tela. Inlinar o gateway no ViewModel não é ser eficiente, é furar o gate.
