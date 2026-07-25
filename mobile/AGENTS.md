# AGENTS.md — Saqz Mobile (KMP + Compose Multiplatform)

Arquitetura-alvo desta pasta (`mobile/`). Vale para Android e iOS: **todo código de produto vive em
`commonMain`**; `androidMain`/`iosMain` existem só para o que a plataforma exige (fontes, engine
HTTP, keystore, ports nativos).

## Como ler este documento

Este é o **alvo**, não o retrato do código atual. Ele parte das skills `android-*`
(`.claude/skills/`) e incorpora os padrões do repo que provaram valor em produção.

- Código **novo** segue este documento, sem exceção.
- Código **existente** que diverge não é bug — é dívida catalogada em §16. Ao tocar num arquivo
  divergente, migre-o. Não faça migração em massa fora de um ticket dedicado.
- Onde o repo hoje faz diferente e **continua certo**, está marcado como decisão consciente com o
  motivo. Não "corrija" para o texto genérico da skill.

---

## 1. Conceito central

Clean Architecture fatiada por feature, camadas em três anéis:

```
ui  ────────────►  presentation  ────────────►  domain  ◄──────────  data
(Compose)          (MVI ViewModels)             (puro)               (gateways)
```

Cinco regras que não se negociam:

1. **`domain` é puro.** Sem Compose, sem Ktor, sem Koin, sem Android. Só Kotlin + `:core:domain`.
   Verificado por script (`scripts/check-mobile-boundaries`), não por disciplina.
2. **`presentation` nunca importa `data`.** Depende da *interface* do gateway, que mora em
   `domain`. A implementação chega por Koin.
3. **Features não se conhecem.** `groups` não importa `access`. O compartilhado desce para
   `core:*`; a coordenação sobe para o app-shell.
4. **A UI é burra.** Composable renderiza estado e emite intents. Zero regra de negócio, zero
   decisão sobre papel de usuário, zero formatação de dado.
5. **Código mora na feature até uma segunda feature precisar dele.** Aí — e só aí — sobe para o
   `core:*` correspondente. Módulo novo em `:core` só para preocupação autocontida com API real
   (várias classes, configuração); nunca para uma classe solta.

---

## 2. Módulos

### Layout-alvo

```
:core:domain                    ← SaqzResult, SaqzError, DataError, value types (GroupId)
:core:data                      ← HttpClient factory, safe-call, transporte, token storage
:core:presentation              ← UiText, ObserveAsEvents, utilidades de UI compartilhadas
:features:<x>:domain            ← modelos, comandos, erros, interfaces de gateway
:features:<x>:data              ← Ktor<X>Gateway, DTOs de transporte, mappers
:features:<x>:presentation      ← ViewModels MVI + Composables (ui/) + ports
:navigation                     ← hosts Nav3, back stacks, autorização de rota
:compose-app                    ← app-shell: startKoin, composition root, ports nativos
:android-app  /  ios-app        ← entrypoints de plataforma
```

Features hoje: `access` (login/cadastro/recuperação), `groups` (grupos, atletas, jogos, presença,
financeiro).

### Regras de dependência (aplicadas por gate)

| De | Pode depender de |
|---|---|
| `<x>:domain` | **só** `:core:domain` |
| `<x>:data` | `<x>:domain`, `:core:domain`, `:core:data` |
| `<x>:presentation` | `<x>:domain`, `:core:domain`, `:core:presentation` |
| `:compose-app` | tudo — único módulo que enxerga `:features:*:data` |

Nenhum módulo de feature depende de outro, de `:navigation`, ou importa `androidx.navigation3.*`.

### Gradle

Version catalog (`gradle/libs.versions.toml`) para **toda** versão — zero versão hardcoded em
`build.gradle.kts`. Config não-trivial vira convention plugin em `:build-logic`:

| Plugin | Estado |
|---|---|
| `saqz.kmp-compose-library` | existe |
| `saqz.android-application` | existe |
| `saqz.detekt` | existe |
| `saqz.domain-module` | **a criar** — KMP puro, sem Android, sem Compose |
| `saqz.feature-data` / `saqz.feature-presentation` | **a criar** — bundle de deps por camada |

---

## 3. Camada `domain`

Um arquivo por agregado (`game/Game.kt`), contendo modelos, comandos, o tipo de erro e a interface
do gateway:

```kotlin
data class Game(val id: String, val groupId: GroupId, /* ... */, val version: Long)
data class GameWriteCommand(val requestId: String? = null, /* ... */)

sealed interface GameError : SaqzError {
    data class Validation(val error: DataError.Validation) : GameError
    data object HiddenResource : GameError   // 404 e 403 colapsam: não vaze existência de recurso
    data object Conflict : GameError
    data class Data(val error: DataError) : GameError
}

interface GameGateway {
    suspend fun list(groupId: GroupId): SaqzResult<List<Game>, GameError>
    suspend fun create(groupId: GroupId, command: GameWriteCommand): SaqzResult<VersionedGame, GameError>
}
```

**Gateway, não `Repository`.** A skill separa "data source = uma fonte" de "repository = coordena
fontes". Aqui a fonte única é sempre a API remota, e o nome canônico de Clean Architecture para
essa porta é *gateway*. A regra da skill continua valendo: no dia em que existir cache local,
nasce um `<X>Repository` que **compõe** gateways — não se infla o gateway.

Toda porta usada por um ViewModel tem interface em `domain`. Sem exceção: é o que impede
`presentation → data` e o que torna o ViewModel testável com fake.

Erro é sempre tipado e por feature. `DataError` (`:core:domain`) é o vocabulário genérico de
transporte; `InvalidLifecycle`, `Conflict` e afins pertencem à feature.

---

## 4. Camada `data`

Um `Ktor<X>Gateway` por agregado. Responsabilidades, nesta ordem:

1. **DTOs `internal`** com sufixo `Transport`, nunca visíveis fora do módulo.
2. **Mappers** como extensões privadas: `GameTransport.toDomain()`, `GameWriteCommand.toRequest()`.
3. **Chamada** pelo cliente compartilhado de `:core:data`.
4. **Tradução de erro**: `NetworkError` → `<X>Error`. HTTP morre aqui. Nenhuma camada acima vê
   status code, exceção ou tipo de rede.

```kotlin
override suspend fun list(groupId: GroupId): SaqzResult<List<Game>, GameError> =
    retryTransport(RetrySafety.Read, delayMillis = retryDelay) {
        network.execute(HttpMethod.Get, "api/groups/${groupId.value}/games", ListSerializer(GameTransport.serializer()))
    }.map { games -> games.map(GameTransport::toDomain) }
```

Três disciplinas do repo que **ficam** — nenhuma skill cobre e todas evitaram bug real:

- **Retry explícito por segurança**: `retryTransport(RetrySafety.Read | IdempotentWrite | Never)`.
  Escrita só entra em retry se carregar `requestId`; sem ele, `Never`.
- **Concorrência otimista**: leitura captura o `ETag` num `<X>VersionToken`; escrita devolve o
  token em `If-Match`. Resposta sem ETag onde ele é esperado é `InvalidResponse`, não crash.
- **Idempotência**: `requestId` gerado na apresentação (`CommandKeyFactory`) e enviado no corpo,
  para o backend deduplicar reenvio.

O `HttpClient` é criado **uma vez** em `:core:data`, recebendo o engine por parâmetro para que o
teste injete `MockEngine`. Auth e refresh de token ficam no plugin `Auth` do Ktor, não espalhados
pelos gateways.

---

## 5. Camada `presentation` — MVI

Toda tela tem quatro peças e uma base comum:

```kotlin
abstract class MviViewModel<S, I, E>(initialState: S) : ViewModel() {
    val state: StateFlow<S>
    val effects: Flow<E>              // Channel.BUFFERED + receiveAsFlow
    protected fun update(transform: (S) -> S)
    protected fun emit(effect: E)
    abstract fun onIntent(intent: I)
}
```

**A base é obrigatória** (decisão do repo, mantida): a skill mostra `MutableStateFlow` + `Channel`
recriados em cada ViewModel — isso é boilerplate replicado dezenas de vezes e uma chance a cada vez
de esquecer `.update {}` e sobrescrever estado concorrente.

**`Intent`/`Effect`/`onIntent`**, não `Action`/`Event`/`onAction`. É o vocabulário canônico de MVI
(o "I" da sigla) e já é o do código.

Por tela, em `presentation/<área>/<tela>/`:

| Arquivo | Conteúdo |
|---|---|
| `<X>State.kt` | `data class` com todo o estado, defaults preenchidos |
| `<X>Intent.kt` | `sealed interface` das ações do usuário |
| `<X>Effect.kt` | `sealed interface` dos efeitos únicos (navegar, compartilhar, snackbar) |
| `<X>ViewModel.kt` | `MviViewModel<State, Intent, Effect>` |

Estado sempre via `update { it.copy(...) }` — nunca atribuição direta do flow.

### Disciplinas obrigatórias

- **Guarda de geração**: carga assíncrona carrega um contador; a resposta é descartada se o
  contexto mudou (`if (operation != generation) return@launch`). É o que impede a lista do grupo A
  aparecer depois de trocar para o grupo B.
- **Efeito de navegação idempotente**: um `Set` de chaves emitidas evita duplo push por duplo toque.
- **Intent inválido retorna cedo**: não emite efeito, não altera estado.
- **Regra densa sai do ViewModel**: validação, totais e permissão vivem em arquivos próprios e
  testáveis.
- **Process death**: formulário longo persiste os campos que importam em `SavedStateHandle` — só
  os que importam, não o estado inteiro.
- **Dispatcher não se injeta** em ViewModel (`viewModelScope` basta). Injete só em classe que
  despacha para `IO` **e** é testada direto (ex.: compressor de imagem).

### UI models

Domínio que precisa de formatação (data, moeda, unidade) vira modelo de apresentação sufixado
`Ui`, mapeado por extensão: `fun Game.toGameUi(): GameUi`. Composable não formata.

---

## 6. Camada `ui` — Compose

Três níveis, e o do meio é o que costuma ser esquecido:

```
Root        → liga ViewModel à tela: resolve DI, coleta state, observa effects
ScreenState → estado puramente visual (aba ativa, dialog aberto) que não interessa ao domínio
Screen      → função pura (state, onIntent) → UI. Previewável e testável sem DI.
```

```kotlin
// GamesScreen.kt — Root e Screen no mesmo arquivo
@Composable
fun GamesRoot(
    onOpenGame: (String) -> Unit,
    viewModel: GamesViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.effects) { effect ->
        when (effect) {
            is GamesEffect.OpenGame -> onOpenGame(effect.gameId)
        }
    }
    GamesScreen(state = GamesScreenState(state), onIntent = { /* ... */ })
}

@Composable
fun GamesScreen(state: GamesScreenState, onIntent: (GamesScreenIntent) -> Unit, modifier: Modifier = Modifier)
```

Regras:

- **Tokens, sempre.** `SaqzTheme.colors/typography/metrics/motion`. `12.dp` e `Color(0xFF...)` em
  feature são bloqueados por gate com teto (`scripts/check-design-tokens`).
- **Componentes `Saqz*`** antes de Material cru. Não existe mais um módulo de design system:
  o que sobrou vive em `features:access/ui` (`SaqzTheme`, `SaqzButton`, `SaqzInput`,
  `SaqzLoadingState`) e o novo nasce componente a componente na jornada que o exigir —
  o segundo uso concreto é que justifica extrair.
- **`testTag` via `object <Tela>Tags`** com constantes — testes de UI e journeys dependem disso.
- **Nada de `remember` para estado de aplicação.** Só estado de composição (`LazyListState`,
  `PagerState`). `derivedStateOf` só quando a derivação parte desse estado; se dá para derivar no
  ViewModel, deriva lá.
- **Efeitos** via `ObserveAsEvents(viewModel.effects)` no Root. `LaunchedEffect` só quando não há
  equivalente no ViewModel, e extraído para um composable dedicado.
- **`CompositionLocal` custom é proibido** — só os cinco `LocalSaqz*` passam no detekt.
- **`key` em lazy list** quando há id óbvio. **`contentDescription`** significativo em tudo que é
  interativo ou informativo; `null` no decorativo.
- **Animação abaixo da recomposição**: `graphicsLayer`, `offset { }`, `Canvas`. Leitura de estado
  que dirige animação passa como lambda, não como valor.
- **`@Preview` por Screen**, envolto em `SaqzTheme`, com dado realista.
- **Strings em PT-BR** via `composeResources`.

---

## 7. Navegação — Navigation 3

Um único `NavDisplay` para o produto (`ProductNavigationHost` em `:navigation`), com cinco back
stacks (`access`, `home`, `groups`, `notices`, `more`) e um `NavigationMode` (`ACCESS` /
`AUTHENTICATED`). Autenticar **troca o modo**, não empilha sobre o stack de acesso.

- Stacks, aba selecionada e `onBack` são hoisted no composition root; o host só compõe.
- Todas as abas ficam decoradas sempre (`rememberDecoratedNavEntries`) para sobreviver a restauração.
- `scopeEntryProvider` namespaceia identidade por stack: mesma chave em dois stacks não compartilha
  ViewModel nem saved state.
- Rotas são `NavKey` `@Serializable` com argumentos **escalares**. Objeto complexo não navega:
  passe o id e carregue no destino.
- Feature expõe destinos e Roots; quem conhece Nav3 é `:navigation` + `:compose-app`.
- **Navegação entre features é callback**, nunca import de rota alheia.

---

## 8. DI — Koin, e somente Koin

**Koin é o único mecanismo de injeção do projeto.** Se um objeto tem dependência, ele é construído
por um módulo Koin. Não existe segunda via.

### Proibido

- `<X>ViewModelFactory` escrito à mão.
- Default de parâmetro com implementação concreta (`port: TimeZonePort = DefaultTimeZonePort()`).
- `remember { AlgumaImpl() }` ou `by lazy { ... }` para obter dependência.
- Repassar dependência manualmente pela árvore de composables ou pelo composition root.
- Singleton via `object` para algo que tem colaborador.

### Módulos

Um módulo por camada de feature, **declarado dentro do próprio módulo Gradle** — não centralizado:

```kotlin
// :features:groups:data
val groupsDataModule = module {
    singleOf(::KtorGameGateway) { bind<GameGateway>() }
    singleOf(::KtorGroupGateway) { bind<GroupGateway>(); bind<GroupProfileGateway>() }
}

// :features:groups:presentation
val groupsPresentationModule = module {
    viewModelOf(::GamesViewModel)
    viewModelOf(::GameDetailViewModel)
}
```

Prefira a forma `singleOf` / `viewModelOf` / `factoryOf`. Caia para o lambda (`single { }`) só
quando não dá para expressar com referência de construtor: método fábrica, qualifier nomeado,
setup pós-construção.

Uma implementação que serve várias interfaces recebe múltiplos `bind` — uma instância, não duas.

### Montagem

Só o app-shell monta. Nenhum módulo de feature registra outro:

```kotlin
startKoin {
    modules(
        coreDataModule,
        accessDataModule, accessPresentationModule,
        groupsDataModule, groupsPresentationModule,
    )
}
```

### ViewModels

`koinViewModel()` como default do parâmetro no Root — e **só** no Root:

```kotlin
@Composable
fun GamesRoot(onOpenGame: (String) -> Unit, viewModel: GamesViewModel = koinViewModel())
```

Isso resolve pelo `ViewModelStoreOwner` do `NavEntry` (escopo correto no Nav3) e ainda deixa teste
e preview passarem um fake pelo parâmetro. Argumento de rota entra via `parametersOf(groupId)`.
Nunca passe ViewModel adiante na árvore de composables.

Teste **não sobe container Koin** — instancia o ViewModel com fakes direto. A exceção é o teste de
bootstrap, que existe justamente para verificar que todo grafo resolve.

---

## 9. Erros — `SaqzResult`

```kotlin
interface SaqzError
sealed interface SaqzResult<out T, out E : SaqzError> {
    data class Success<out T>(val value: T)
    data class Failure<out E : SaqzError>(val error: E)
}
typealias EmptyResult<E> = SaqzResult<Unit, E>
```

Com `map`, `mapError`, `onSuccess`, `onFailure`, `asEmptyResult` — todos encadeáveis.

Nunca lance exceção para falha esperada. Quem é dono da exceção a captura e a converte:

| Origem | Captura em | Vira |
|---|---|---|
| HTTP / socket | `data` | `<X>Error` da feature |
| Regra de negócio, validação | `domain` | erro tipado da feature |
| Exibição | `presentation` | `UiText` |

`Result` não é privilégio da camada de dados: use em qualquer função com sucesso e falha tipados
(validação de formulário, política de autorização). Um erro por `Result` — não lista de erros.

### `UiText`

Erro que chega ao usuário vira `UiText` na apresentação, via extensão `.toUiText()`:

```kotlin
sealed interface UiText {
    data class Dynamic(val value: String) : UiText
    data class Resource(val id: StringResource, val args: List<Any> = emptyList()) : UiText
}
```

`.toUiText()` de erro compartilhado mora em `:core:presentation`; de erro de feature, na
`presentation` da feature. Erro puramente interno (sinal de retry, marcador de estado) não precisa
de mapeamento.

Valor que **sempre** é dinâmico e nunca vem de recurso — nome de pessoa, data formatada, valor em
reais — é `String` no estado, não `UiText`.

---

## 10. Ports nativos

Capacidade que só a plataforma entrega (timezone do sistema, storage de rascunho, câmera) é
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

## 11. Testes

`commonTest` com **`kotlin.test`**. A skill pede JUnit5 — que é JVM-only e portanto impossível em
`commonTest` de KMP. JUnit4 aparece só em `:android-app` (instrumentado e Roborazzi).

`Turbine` e `AssertK` são multiplataforma e valem adoção quando o assert em `state`/`effects`
começar a ficar verboso (§16). Coroutines: `kotlinx-coroutines-test` + `UnconfinedTestDispatcher`
com `Dispatchers.setMain()` no setup.

| Alvo | Onde | Como |
|---|---|---|
| Regra de domínio | `<x>:domain/commonTest` | teste puro, sem fake |
| Gateway | `<x>:data/commonTest` | `MockEngine`; cobrir mapeamento de erro e ETag |
| ViewModel | `<x>:presentation/commonTest` | **fake** do gateway, assert em `state` e `effects` |
| Screen | `<x>:presentation/commonTest` | `ComposeTestRule` + `testTag` do objeto `Tags` |
| Journey Android | `android-app/androidTest` | fluxo real ponta a ponta |
| Adapter nativo iOS | `ios-app/SaqzIOSTests` | Swift, direto contra o adapter |
| Visual | `android-app/src/test` | Roborazzi — **olhe os PNGs** antes de entregar UI |

**Fake > mock, sempre.** Fake é implementação em memória da interface, com um `var shouldFail` para
o caminho triste. `SavedStateHandle` se instancia direto, sem mock.

Tela com 3+ casos de teste ou fluxo multi-passo usa **robot pattern**: uma classe que encapsula as
interações com `composeTestRule`, cada função devolvendo `this` para encadear.

---

## 12. Gates antes de abrir PR

```sh
scripts/check-gradle          # backend + mobile + detekt + instrumentado
```

Encadeia, entre outros: `check-mobile-boundaries` (dependências entre módulos),
`check-design-tokens` (teto de dp/cor cru), `allTests` de todos os módulos, `detektAll`,
`connectedDevDebugAndroidTest`, e falha se alguma suíte reportar zero teste descoberto.

Local: JDK 21, `DOCKER_HOST` do Colima, emulador `Saqz_API_30` ligado.
Detekt usa **baseline por módulo**: dívida antiga congelada, código novo falha. Não regenere
baseline para calar erro seu.

---

## 13. Convenções de nome

| Coisa | Padrão | Exemplo |
|---|---|---|
| Gateway (interface) | `<Agregado>Gateway` | `GameGateway` |
| Gateway (impl) | descreve o que a torna única | `KtorGameGateway` |
| Repository (só multi-fonte) | descreve o comportamento | `OfflineFirstGameRepository` |
| DTO | `<Modelo>Transport`, `internal` | `GameTransport` |
| Mapper | extensão privada | `GameTransport.toDomain()` |
| ViewModel | `<Tela>ViewModel` | `GamesViewModel` |
| MVI | `<Tela>State` / `Intent` / `Effect` | `GamesState`, `GamesIntent` |
| Estado visual da tela | `<Tela>ScreenState` / `ScreenIntent` | `GamesScreenState` |
| UI model | `<Modelo>Ui` | `GameUi` |
| Composables | `<Tela>Root` + `<Tela>Screen` | `GamesRoot`, `GamesScreen` |
| Test tags | `object <Tela>Tags` | `GamesTags.List` |
| Port | `<Capacidade>Port` | `GroupDraftStorePort` |
| Módulo Koin | `<feature><Camada>Module` | `groupsDataModule` |

**Sufixo `Impl` é proibido** — nomeie pelo que distingue (`Ktor…`, `Room…`, `Default…`).

---

## 14. Checklists

**Nova tela**
- [ ] `State` / `Intent` / `Effect` em `presentation/<área>/<tela>/`
- [ ] `ViewModel : MviViewModel<...>`, com guarda de geração se houver carga assíncrona
- [ ] `SavedStateHandle` para campos de formulário que precisam sobreviver a process death
- [ ] `Screen` puro + `object Tags` + tokens `SaqzTheme` + `@Preview`
- [ ] `Root` com `viewModel: X = koinViewModel()`, `ObserveAsEvents`, callbacks de navegação
- [ ] `viewModelOf(::XViewModel)` no `<feature>PresentationModule`
- [ ] Destino em `:navigation`; navegação entre features por callback
- [ ] Testes: ViewModel com fake + Screen com `testTag`

**Novo gateway**
- [ ] Modelos, comandos, `<X>Error` e `interface <X>Gateway` em `<feature>:domain`
- [ ] `Ktor<X>Gateway` + DTOs `Transport` `internal` + mappers em `<feature>:data`
- [ ] `RetrySafety` correto; `requestId` em escrita; `If-Match`/ETag onde houver versão
- [ ] Todo `NetworkError` mapeado — sem `else ->` preguiçoso
- [ ] `singleOf(::KtorXGateway) { bind<XGateway>() }` no `<feature>DataModule`
- [ ] `.toUiText()` para os erros que chegam ao usuário
- [ ] Teste com `MockEngine`: sucesso, validação, conflito

**Nova feature**
- [ ] Três módulos: `:features:<x>:domain`, `:data`, `:presentation`
- [ ] Convention plugin certo em cada um
- [ ] Zero dependência de outra feature
- [ ] Módulos Koin declarados na feature, registrados no `startKoin` do app-shell

---

## 15. Divergência e simplificação deliberada

Duas marcações, propósitos diferentes. Ambas exigem justificativa; sem ela, não conta.

**`SPEC_DEVIATION:`** — o código contraria uma spec ou este documento. Exige `Reason:` explicando
por que a regra não se aplica aqui.

```kotlin
// SPEC_DEVIATION: data class, não @JvmInline value class.
// Reason: Kotlin/Native apaga membros de value class; Swift não consegue construir o modelo.
```

**`ponytail:`** — o código está deliberadamente mais simples do que poderia, com teto conhecido.
Nomeia o teto e o caminho de upgrade, e é colhível por `/ponytail-debt`.

```kotlin
// ponytail: filtro em memória; paginar no servidor se a lista passar de ~500 jogos
```

**O que "lazy" significa aqui — e o que não significa.** A preguiça vale *dentro* da arquitetura:
não invente abstração especulativa, não crie interface com uma implementação só, reuse o
componente `Saqz*` que já existe, prefira deletar a adicionar, resolva em uma linha se der.

A preguiça **não** vale contra a estrutura. As quatro peças de MVI por tela, a interface do gateway
em `domain`, o split Root/Screen e o módulo Koin são mais código que o caminho curto — de
propósito. Esse custo já foi pago e comprado: é o que dá teste sem emulador, feature isolada e
troca de implementação sem tocar em tela. Um agente que "simplifica" inlinando o gateway no
ViewModel ou fundindo Root e Screen não está sendo eficiente, está furando o gate e criando dívida
que outro vai pagar às 3 da manhã.

A estrutura tem exatamente duas válvulas de escape, para que ela não vire ritual:

- **Tela sem estado próprio não ganha ViewModel.** Placeholder, estado vazio, tela institucional:
  é só um `Screen` recebendo parâmetros, renderizado pelo Root da tela que o contém. State, Intent
  e Effect vazios existindo "por simetria" são boilerplate, não arquitetura.
- **Tipo vazio não se cria.** Tela que não emite efeito é `MviViewModel<State, Intent, Nothing>` —
  não uma `sealed interface XEffect` sem implementação.

Fora dessas duas, a estrutura vale integral. Resumindo: **preguiçoso na solução, rigoroso na
estrutura.**

---

## 16. Backlog de reestruturação

Dívida conhecida entre o código atual e este documento. Cada item é ticket próprio; ao encostar num
arquivo afetado, migre o arquivo.

| # | Hoje | Alvo | Peso |
|---|---|---|---|
| 1 | Módulos Koin centralizados em `compose-app/di/` | um módulo por camada, dentro da própria feature | alto |
| 2 | `single { Impl(get()) }` + `single<I> { get<Impl>() }` | `singleOf(::Impl) { bind<I>() }` | baixo |
| 3 | `GameDetailViewModelFactory` e roots recebendo VM por parâmetro | `koinViewModel()` como default no Root | médio |
| 4 | `:features:<x>` mistura presentation e ui | renomear para `:features:<x>:presentation` | baixo |
| 5 | `:core:common` + `:core:network` | `:core:presentation` + `:core:data` (nomes das skills) | baixo |
| 6 | Erro traduzido para PT-BR dentro do `ui/` | `UiText` + `.toUiText()` na presentation | alto |
| 7 | Sem UI models — Screen formata data e moeda | `<Modelo>Ui` mapeado na presentation | médio |
| 8 | Convention plugins só para library/app/detekt | `domain-module`, `feature-data`, `feature-presentation` | médio |
| 9 | 258 `dp` crus e 2 `Color(0x…)` sob teto no gate | zero — baixar o teto a cada remoção | contínuo |
| 10 | Assert manual de `state`/`effects` com `kotlin.test` | avaliar Turbine + AssertK (ambos KMP) | baixo |
