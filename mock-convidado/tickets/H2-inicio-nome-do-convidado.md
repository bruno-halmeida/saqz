# H2 · Início mostra o nome do convidado na cobrança própria (VUL-XXX)

Follow-up do convidado de jogo (VUL-239). O ticket de "cobrança própria" da Início mostra a competência da
cobrança pendente MAIS ANTIGA do grupo (`oldest`). Quando ela é de um convidado, hoje diz "Jogo avulso";
passa a dizer "Convidado: Rafa Moreira". Backend + mobile, campo aditivo (`null` = como hoje).

`G` = `backend/features/groups/src/main/kotlin/br/com/saqz/groups`
`DOM` = `mobile/features/groups/domain/src/commonMain/kotlin/br/com/saqz/groups/domain`
`DATA` = `mobile/features/groups/data/src/commonMain/kotlin/br/com/saqz/groups/data`
`PRES` = `mobile/features/groups/presentation/src/commonMain/kotlin/br/com/saqz/groups/presentation`
`GM` = `JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile`
`GB` = `cd backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) DOCKER_HOST=unix://$HOME/.colima/default/docker.sock TESTCONTAINERS_RYUK_DISABLED=true ./gradlew`

## Arquivos (lista FECHADA)
Backend: `G/application/home/Home.kt`, `G/adapter/output/jdbc/home/JdbcHomeRepository.kt`, `G/adapter/input/http/MyHomeController.kt`,
`backend/features/groups/src/integrationTest/.../home/JdbcHomeRepositoryIntegrationTest.kt` (1 teste novo).
Mobile: `DOM/home/Home.kt`, `DATA/home/KtorHomeGateway.kt`, `PRES/home/HomeViewModel.kt`,
`mobile/features/groups/data/src/commonTest/.../home/KtorHomeGatewayTest.kt`, `mobile/features/groups/presentation/src/commonTest/.../home/HomeViewModelTest.kt`
(nesses dois SÓ a troca `HomeOwnChargeOldest.Game` → `HomeOwnChargeOldest.Game()` onde deixar de compilar, + 1 teste novo cada).
Fora da lista = pergunte e aguarde. Se OUTRO arquivo deixar de compilar pela troca `Game` object→class, pergunte com a lista.

## Backend
1. `Home.kt`: `HomeOwnChargeOldest.Game` ganha no fim `val guestDisplayName: String? = null,`.
2. `JdbcHomeRepository.kt`, const `OWN_CHARGES`: no subselect que projeta `charges.game_id` (a CTE/derivada `oldest`),
   acrescente `charges.guest_display_name`; no SELECT externo, ao lado de `oldest.game_id AS oldest_game_id`, acrescente
   `oldest.guest_display_name AS oldest_guest_display_name`. No mapeador (linha `?: HomeOwnChargeOldest.Game(`), passe
   `guestDisplayName = rs.getString("oldest_guest_display_name")`.
3. `MyHomeController.kt`: `HomeOwnChargeOldestResponse` ganha no fim `val guestDisplayName: String? = null,`; no
   `HomeOwnChargeOldest.toResponse()`, o ramo `is HomeOwnChargeOldest.Game ->` passa `guestDisplayName` como último argumento
   (o ramo Monthly passa `null` explícito se o construtor for posicional sem default — siga o estilo do arquivo).
4. IT novo em `JdbcHomeRepositoryIntegrationTest`: `oldestGuestChargeCarriesTheGuestName` — fixture copiada do teste vizinho que
   já monta cobrança GAME pendente; insira a cobrança com `guest_seq = 1, guest_display_name = 'Rafa Moreira'` (e uma linha
   `game_attendance` correspondente com `guest_seq = 1` se a FK/CHECK exigir); assert `oldest is Game` e `guestDisplayName == "Rafa Moreira"`.

Gates: `GB :features:groups:test --tests '*MyHomeControllerTest'` · `GB :features:groups:integrationTest --tests '*JdbcHomeRepositoryIntegrationTest'` · `GB check`.

## Mobile
5. `DOM/home/Home.kt`: `data object Game : HomeOwnChargeOldest` vira
   `data class Game(val guestDisplayName: String? = null) : HomeOwnChargeOldest`.
6. `DATA/home/KtorHomeGateway.kt`: `HomeOwnChargeOldestTransport` ganha `val guestDisplayName: String? = null,`;
   no `toDomain()`, `"GAME" -> HomeOwnChargeOldest.Game(guestDisplayName)`.
7. `PRES/home/HomeViewModel.kt`, no `competence = when (val competence = oldest)`: troque o ramo
   `HomeOwnChargeOldest.Game -> getString(Res.string.own_charges_game)` por
   `is HomeOwnChargeOldest.Game -> competence.guestDisplayName?.let { getString(Res.string.own_charges_guest, it) } ?: getString(Res.string.own_charges_game)`
   (+ import `br.com.saqz.groups.resources.own_charges_guest` — a chave já existe em `strings_minhas_cobrancas.xml`).
8. Testes: `KtorHomeGatewayTest` — `oldestGameCarriesTheGuestName` (payload com `"guestDisplayName":"Rafa Moreira"`) e o assert
   antigo vira `HomeOwnChargeOldest.Game()`; `HomeViewModelTest` — `ownChargeOfAGuestIsTitledWithTheGuestName`
   (competence == "Convidado: Rafa Moreira") e as referências antigas viram `HomeOwnChargeOldest.Game()`.

Gates: `GM :features:groups:domain:detektAll :features:groups:data:detektAll :features:groups:presentation:detektAll` ·
`GM :features:groups:data:iosSimulatorArm64Test` · `GM :features:groups:presentation:iosSimulatorArm64Test` ·
`GM :features:groups:presentation:recordRoborazziAndroidHostTest` (nenhum PNG muda) · `GM :compose-app:iosSimulatorArm64Test` · `GM :android-app:testDevDebugUnitTest`.

## PR
Um PR só. Título: `feat(home): cobrança própria da Início mostra o nome do convidado (VUL-XXX)`. Sem prints.
Commits PT-BR terminando com `Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>`; corpo terminando com
`🤖 Generated with [Claude Code](https://claude.com/claude-code)`.
