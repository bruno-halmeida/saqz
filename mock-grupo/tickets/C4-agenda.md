# C4 · Próximos jogos (agenda do grupo)

**Onda 3 · depende de: S, B, T, V2 (e A para o dado real do chip) · bloqueia: D · paralelo com: C5, H**

## Objetivo

Preencher o bloco vazio `GroupAgendaBlock` com a seção "Próximos jogos" do detalhe do grupo: a lista `state.agenda` (V2) num card flush, uma linha `UpcomingGameRow` (B) por jogo com o chip da resposta do próprio usuário, toque na linha abre o jogo, "Ver mais" expande no lugar e, para o gestor, a ação "Marcar jogo" no cabeçalho da seção.

Decisões já tomadas (o worker não reabre nenhuma):

1. O bloco **só emite com `state.agenda.isNotEmpty()`**. Gestor com agenda vazia também não vê a seção: o "Marcar jogo" de quem não tem jogo já mora no hero (C1).
2. "Gestor" = `state.isAdmin` (dono ou admin; é `role != ATHLETE` no ViewModel).
3. Mostra as **3 primeiras** linhas. Havendo mais, a última linha do card é o botão "Ver mais N jogos", que expande **todas** no lugar e some. O estado mora em `rememberSaveable` dentro do bloco (decisão do orquestrador para este bloco: sobrevive à rotação; continua fora do ViewModel).
4. O botão "Ver mais" é um `SaqzButton` Ghost/Sm de largura total, **sem o chevron do mock**: `SaqzIcons` não tem `ChevronDown` e o design system está fora da lista de arquivos. Onde o PNG e a receita divergem, vale a receita (CONTRATO §6).
5. A tag `AgendaCreate` vai no `SaqzSectionHeader` e **só quando `isAdmin`** (o componente não expõe modifier para a ação). O clicável "Marcar jogo" é DESCENDENTE desse nó — é assim que o teste o acha.
6. Tons do chip: `Going` → Success com ponto · `Waitlisted` → Warning com ponto · `Draft` → Brand · `Pending`/`Out` → Neutral. O texto é sempre `row.statusLabel` (quem conjuga é o V2).

## Fora do escopo

- Não montar `title`/`meta`/`statusLabel`/`contentDescription`: chegam prontos do V2. As chaves `group_details_agenda_meta*` e `group_details_agenda_status_draft` são do ViewModel, não deste bloco.
- Não tocar em `UpcomingGameRow.kt`, `HomeUpcomingSection.kt`, no Contract, no ViewModel, em `GroupDetailsScreen.kt`, `GroupDetailsPreviewData.kt`, `GroupDetailsTags.kt`, nos suportes de teste/captura nem em qualquer `strings*.xml`.
- Não criar `@Preview` (a preview da tela é do D; a conferência visual deste bloco é a suíte Roborazzi).
- Não acrescentar ícone ao design system.

## Arquivos

| Ação | Arquivo |
|---|---|
| editar | `DET/GroupAgendaBlock.kt` |
| criar | `DET/GroupAgendaPreviewData.kt` |
| criar | `TDET/GroupAgendaBlockTest.kt` |
| criar | `SDET/GroupAgendaScreenshotTest.kt` |

**Tocar em arquivo fora desta lista = parar e avisar o orquestrador.**

## Passo a passo

### 0. Conferir o que as ondas anteriores exportaram (só leitura)

Antes de editar, rodar da raiz do worktree. Os quatro comandos têm de imprimir pelo menos uma linha cada; se algum não imprimir, **parar e avisar o orquestrador** (a dependência não mergeou).

```sh
grep -n "val agenda: List<GroupAgendaRowUi>" mobile/features/groups/presentation/src/commonMain/kotlin/br/com/saqz/groups/presentation/details/GroupDetailsContract.kt
grep -n "data class OpenAgendaGame" mobile/features/groups/presentation/src/commonMain/kotlin/br/com/saqz/groups/presentation/details/GroupDetailsContract.kt
grep -n "internal fun UpcomingGameRow" mobile/features/groups/presentation/src/commonMain/kotlin/br/com/saqz/groups/presentation/ui/components/UpcomingGameRow.kt
grep -n "group_details_agenda_more_one" mobile/features/groups/presentation/src/commonMain/composeResources/values/strings_group_details.xml
```

### 1. Substituir `DET/GroupAgendaBlock.kt` pelo arquivo completo abaixo

Âncora (o arquivo inteiro de hoje, deixado pelo T):

```kotlin
/** Andaime (T): "Próximos jogos" chega no ticket C4, junto com `state.agenda` do V2. */
@Suppress("UnusedParameter")
@Composable
internal fun GroupAgendaBlock(
    state: GroupDetailsState,
    onIntent: (GroupDetailsIntent) -> Unit,
    modifier: Modifier = Modifier,
) = Unit
```

Arquivo novo completo (somem o `@Suppress` e o KDoc de andaime):

```kotlin
package br.com.saqz.groups.presentation.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonSize
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzChipTone
import br.com.saqz.designsystem.SaqzDivider
import br.com.saqz.designsystem.SaqzSectionHeader
import br.com.saqz.designsystem.SaqzStatusChip
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.details.GroupAgendaStatus
import br.com.saqz.groups.presentation.details.GroupDetailsIntent
import br.com.saqz.groups.presentation.details.GroupDetailsState
import br.com.saqz.groups.presentation.ui.components.UpcomingGameRow
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.group_details_agenda_more
import br.com.saqz.groups.resources.group_details_agenda_more_one
import br.com.saqz.groups.resources.home_admin_shortcuts_create_game
import br.com.saqz.groups.resources.home_upcoming_title
import org.jetbrains.compose.resources.stringResource

/** Linhas à vista antes do "Ver mais". */
private const val AgendaCollapsedCount = 3

/**
 * "Próximos jogos" do grupo, sem o jogo do hero: data, hora, quantos confirmaram e a resposta
 * do próprio usuário. Toque abre o jogo; "Ver mais" expande no lugar e some. Sem jogos a seção
 * não existe — o "Marcar jogo" de quem não tem agenda mora no hero. Para o gestor, a mesma
 * ação fica no cabeçalho.
 */
@Composable
internal fun GroupAgendaBlock(
    state: GroupDetailsState,
    onIntent: (GroupDetailsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.agenda.isEmpty()) return
    val metrics = SaqzTheme.metrics
    // Só visual: não é dado do grupo, então não sobe para o ViewModel.
    var expanded by rememberSaveable { mutableStateOf(false) }
    val visible = if (expanded) state.agenda else state.agenda.take(AgendaCollapsedCount)
    val hidden = state.agenda.size - visible.size
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(metrics.blockGap)) {
        SaqzSectionHeader(
            title = stringResource(Res.string.home_upcoming_title),
            // O header não expõe modifier para a ação: a tag fica nele e o clicável é descendente.
            modifier = if (state.isAdmin) Modifier.testTag(GroupDetailsTags.AgendaCreate) else Modifier,
            action = if (state.isAdmin) stringResource(Res.string.home_admin_shortcuts_create_game) else null,
            onAction = { onIntent(GroupDetailsIntent.CreateNextGame) },
        )
        SaqzCard(modifier = Modifier.testTag(GroupDetailsTags.Agenda), padded = false) {
            visible.forEachIndexed { index, row ->
                if (index > 0) SaqzDivider()
                UpcomingGameRow(
                    day = row.day,
                    month = row.month,
                    title = row.title,
                    meta = row.meta,
                    contentDescription = row.contentDescription,
                    onClick = { onIntent(GroupDetailsIntent.OpenAgendaGame(row.gameId)) },
                    tag = GroupDetailsTags.agendaGame(row.gameId),
                ) {
                    SaqzStatusChip(
                        text = row.statusLabel,
                        tone = when (row.status) {
                            GroupAgendaStatus.Going -> SaqzChipTone.Success
                            GroupAgendaStatus.Waitlisted -> SaqzChipTone.Warning
                            GroupAgendaStatus.Draft -> SaqzChipTone.Brand
                            GroupAgendaStatus.Pending, GroupAgendaStatus.Out -> SaqzChipTone.Neutral
                        },
                        dot = row.status == GroupAgendaStatus.Going || row.status == GroupAgendaStatus.Waitlisted,
                    )
                }
            }
            if (hidden > 0) {
                SaqzDivider()
                SaqzButton(
                    label = if (hidden == 1) {
                        stringResource(Res.string.group_details_agenda_more_one)
                    } else {
                        stringResource(Res.string.group_details_agenda_more, hidden)
                    },
                    onClick = { expanded = true },
                    // O Sm nasce com 44; o alvo de toque da casa é 48.
                    modifier = Modifier
                        .testTag(GroupDetailsTags.AgendaMore)
                        .heightIn(min = metrics.minimumTouchTarget),
                    variant = SaqzButtonVariant.Ghost,
                    size = SaqzButtonSize.Sm,
                    fullWidth = true,
                )
            }
        }
    }
}
```

Fatos conferidos no código (não re-decidir):
- `SaqzSectionHeader(title, modifier, icon, action, onAction)` só desenha a ação quando `action != null && onAction != null` — por isso `onAction` vai sempre e quem esconde é o `action = null` do membro.
- `SaqzButton` Ghost/Sm: rótulo em `support` + peso do `label` (600) na cor `primary`, fundo transparente — é o botão de texto do mock. `modifier` entra ANTES do `sizeIn(minHeight = 44)` interno, então o `heightIn(min = 48)` de fora vence.
- `SaqzCard(padded = false)` tem `spacedBy(0)`: as divisórias são a única separação.
- O `return` antes do `rememberSaveable` é o mesmo padrão de `HomeUpcomingSection`.

### 2. Criar `DET/GroupAgendaPreviewData.kt` (arquivo completo)

```kotlin
package br.com.saqz.groups.presentation.ui.details

import br.com.saqz.groups.presentation.details.GroupAgendaRowUi
import br.com.saqz.groups.presentation.details.GroupAgendaStatus

/**
 * Os estados da agenda nas capturas e nos testes do bloco. Derivam dos estados-base com
 * `.copy(...)`; o jogo do hero (`game-1`) nunca aparece aqui, como no ViewModel.
 */
internal object GroupAgendaPreviewData {
    val pending = GroupAgendaRowUi(
        gameId = "game-2",
        day = "30",
        month = "JUL",
        title = "Quinta · 19h30",
        meta = "6 de 12 confirmados",
        status = GroupAgendaStatus.Pending,
        statusLabel = "Sem resposta",
        contentDescription = "Quinta, 30/07 às 19h30, Sem resposta",
    )
    val going = GroupAgendaRowUi(
        gameId = "game-3",
        day = "04",
        month = "AGO",
        title = "Terça · 19h30",
        meta = "3 de 12 · Arena Mooca",
        status = GroupAgendaStatus.Going,
        statusLabel = "Você vai",
        contentDescription = "Terça, 04/08 às 19h30, Você vai",
    )
    val waitlisted = GroupAgendaRowUi(
        gameId = "game-4",
        day = "06",
        month = "AGO",
        title = "Quinta · 19h30",
        meta = "Lotado · 12 de 12",
        status = GroupAgendaStatus.Waitlisted,
        statusLabel = "Na espera",
        contentDescription = "Quinta, 06/08 às 19h30, Na espera",
    )
    val out = GroupAgendaRowUi(
        gameId = "game-5",
        day = "11",
        month = "AGO",
        title = "Terça · 19h30",
        meta = "2 de 12 confirmados",
        status = GroupAgendaStatus.Out,
        statusLabel = "Não vai",
        contentDescription = "Terça, 11/08 às 19h30, Não vai",
    )
    val later = GroupAgendaRowUi(
        gameId = "game-6",
        day = "13",
        month = "AGO",
        title = "Quinta · 19h30",
        meta = "0 de 12 confirmados",
        status = GroupAgendaStatus.Pending,
        statusLabel = "Sem resposta",
        contentDescription = "Quinta, 13/08 às 19h30, Sem resposta",
    )
    val draft = GroupAgendaRowUi(
        gameId = "game-7",
        day = "06",
        month = "AGO",
        title = "Quinta · 19h30",
        meta = "Só você vê até publicar",
        status = GroupAgendaStatus.Draft,
        statusLabel = "Rascunho",
        contentDescription = "Quinta, 06/08 às 19h30, Rascunho",
    )

    /** Membro, três linhas, três respostas: sem resposta, vai e na espera. */
    val member = GroupDetailsPreviewData.member.copy(agenda = listOf(pending, going, waitlisted))

    /** Membro com cinco jogos: três à vista e "Ver mais 2 jogos". */
    val memberMore = GroupDetailsPreviewData.member.copy(agenda = listOf(pending, going, waitlisted, out, later))

    /** Gestor: "Marcar jogo" no cabeçalho e o rascunho que só ele vê. */
    val adminDraft = GroupDetailsPreviewData.admin.copy(agenda = listOf(pending, going, draft))
}
```

### 3. Criar `TDET/GroupAgendaBlockTest.kt` (arquivo completo)

```kotlin
package br.com.saqz.groups.presentation.ui.details

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import br.com.saqz.groups.presentation.details.GroupDetailsIntent
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class GroupAgendaBlockTest {
    @Test
    fun emptyAgendaEmitsNothingForMemberOrAdmin() = runComposeUiTest {
        setDetailsScreen(GroupDetailsPreviewData.admin)

        onAllNodesWithTag(GroupDetailsTags.Agenda).assertCountEquals(0)
        onAllNodesWithTag(GroupDetailsTags.AgendaCreate).assertCountEquals(0)
        onAllNodesWithTag(GroupDetailsTags.AgendaMore).assertCountEquals(0)
    }

    @Test
    fun twoRowsHaveNoMoreButton() = runComposeUiTest {
        val agenda = listOf(GroupAgendaPreviewData.pending, GroupAgendaPreviewData.going)
        setDetailsScreen(GroupDetailsPreviewData.member.copy(agenda = agenda))

        onNodeWithTag(GroupDetailsTags.Agenda).assertExists()
        onNodeWithTag(GroupDetailsTags.agendaGame("game-2")).assertExists()
        onNodeWithTag(GroupDetailsTags.agendaGame("game-3")).assertExists()
        onAllNodesWithTag(GroupDetailsTags.AgendaMore).assertCountEquals(0)
    }

    @Test
    fun fiveRowsShowThreeAndExpandInPlace() = runComposeUiTest {
        setDetailsScreen(GroupAgendaPreviewData.memberMore)

        onNodeWithTag(GroupDetailsTags.agendaGame("game-4")).assertExists()
        onAllNodesWithTag(GroupDetailsTags.agendaGame("game-5")).assertCountEquals(0)
        onAllNodesWithTag(GroupDetailsTags.agendaGame("game-6")).assertCountEquals(0)
        onNodeWithTag(GroupDetailsTags.AgendaMore)
            .assertTextEquals("Ver mais 2 jogos")
            .assertHeightIsAtLeast(48.dp)
            .performScrollTo()
            .performClick()

        listOf("game-2", "game-3", "game-4", "game-5", "game-6").forEach {
            onNodeWithTag(GroupDetailsTags.agendaGame(it)).assertExists()
        }
        onAllNodesWithTag(GroupDetailsTags.AgendaMore).assertCountEquals(0)
    }

    @Test
    fun fourRowsUseTheSingular() = runComposeUiTest {
        val agenda = GroupAgendaPreviewData.memberMore.agenda.take(4)
        setDetailsScreen(GroupDetailsPreviewData.member.copy(agenda = agenda))

        onNodeWithTag(GroupDetailsTags.AgendaMore).assertTextEquals("Ver mais 1 jogo")
    }

    @Test
    fun rowTapOpensThatGame() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setDetailsScreen(GroupAgendaPreviewData.member) { intents += it }

        onNodeWithTag(GroupDetailsTags.agendaGame("game-3")).performScrollTo().performClick()

        assertEquals(listOf<GroupDetailsIntent>(GroupDetailsIntent.OpenAgendaGame("game-3")), intents)
    }

    @Test
    fun adminCreateActionEmitsCreateNextGame() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setDetailsScreen(GroupAgendaPreviewData.adminDraft) { intents += it }

        onNode(hasText("Marcar jogo") and hasAnyAncestor(hasTestTag(GroupDetailsTags.AgendaCreate)))
            .performScrollTo()
            .performClick()

        assertEquals(listOf<GroupDetailsIntent>(GroupDetailsIntent.CreateNextGame), intents)
    }

    @Test
    fun memberHasNoCreateAction() = runComposeUiTest {
        setDetailsScreen(GroupAgendaPreviewData.member)

        onNodeWithTag(GroupDetailsTags.Agenda).assertExists()
        onAllNodesWithTag(GroupDetailsTags.AgendaCreate).assertCountEquals(0)
    }

    @Test
    fun chipShowsTheStatusLabelOfEachRow() = runComposeUiTest {
        val agenda = GroupAgendaPreviewData.memberMore.agenda.take(4) + GroupAgendaPreviewData.draft
        setDetailsScreen(GroupDetailsPreviewData.admin.copy(agenda = agenda))
        onNodeWithTag(GroupDetailsTags.AgendaMore).performScrollTo().performClick()

        chipOf("game-2", "Sem resposta").assertExists()
        chipOf("game-3", "Você vai").assertExists()
        chipOf("game-4", "Na espera").assertExists()
        chipOf("game-5", "Não vai").assertExists()
        chipOf("game-7", "Rascunho").assertExists()
    }

    // A linha é clicável e funde os filhos: o chip só é nó próprio na árvore não fundida.
    private fun ComposeUiTest.chipOf(gameId: String, label: String) = onNode(
        hasText(label) and hasAnyAncestor(hasTestTag(GroupDetailsTags.agendaGame(gameId))),
        useUnmergedTree = true,
    )
}
```

Arranjo e asserções, teste a teste (o código acima é a fonte; isto é a leitura):

| Teste | Arranjo | Asserções |
|---|---|---|
| `emptyAgendaEmitsNothingForMemberOrAdmin` | `GroupDetailsPreviewData.admin` (agenda vazia, gestor — o caso mais forte; o membro é o mesmo `if`) | zero nós com `Agenda`, `AgendaCreate`, `AgendaMore` |
| `twoRowsHaveNoMoreButton` | `member` + 2 linhas | card e as 2 linhas existem; zero `AgendaMore` |
| `fiveRowsShowThreeAndExpandInPlace` | `memberMore` (5) | `game-4` existe, `game-5`/`game-6` não; botão com texto exato "Ver mais 2 jogos" e altura ≥ 48dp; depois do toque as 5 existem e zero `AgendaMore` |
| `fourRowsUseTheSingular` | `member` + 4 linhas | botão com texto exato "Ver mais 1 jogo" |
| `rowTapOpensThatGame` | `GroupAgendaPreviewData.member` | toque em `agendaGame("game-3")` → intents == `[OpenAgendaGame("game-3")]` |
| `adminCreateActionEmitsCreateNextGame` | `adminDraft` | toque em "Marcar jogo" descendente de `AgendaCreate` → intents == `[CreateNextGame]` |
| `memberHasNoCreateAction` | `GroupAgendaPreviewData.member` | `Agenda` existe; zero `AgendaCreate` |
| `chipShowsTheStatusLabelOfEachRow` | `admin` + pending, going, waitlisted, out, draft; expande | o `statusLabel` de cada linha é descendente da tag da própria linha (árvore não fundida) |

### 4. Criar `SDET/GroupAgendaScreenshotTest.kt` (arquivo completo)

`captureDetails` monta e fotografa de uma vez; a cena expandida precisa de um toque entre a montagem e a foto, e `setContent` só pode ser chamado uma vez por teste. Por isso "ver mais" e "expandido" saem do MESMO teste: a primeira foto pelo suporte, o toque, a segunda foto direto.

```kotlin
package br.com.saqz.groups.presentation.ui.details

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// "Próximos jogos": os cinco tons do chip, o "Ver mais" fechado e aberto e a ação do gestor.
// Estado fora da cena é estado não conferido (AGENTS.md §11).
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [35],
    qualifiers = RobolectricDeviceQualifiers.Pixel7,
    application = Application::class,
)
class GroupAgendaScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    @Config(qualifiers = "+h2400dp")
    fun member() = compose.captureDetails("group-details-agenda-member", GroupAgendaPreviewData.member, "agenda")

    @Test
    @Config(qualifiers = "+h2400dp")
    fun memberMoreThenExpanded() {
        compose.captureDetails("group-details-agenda-more", GroupAgendaPreviewData.memberMore, "agenda")
        compose.onNodeWithTag(GroupDetailsTags.AgendaMore).performClick()
        compose.onRoot().captureRoboImage("screenshots/agenda/group-details-agenda-expanded.png")
    }

    @Test
    @Config(qualifiers = "+h2400dp")
    fun adminDraft() = compose.captureDetails("group-details-agenda-admin-draft", GroupAgendaPreviewData.adminDraft, "agenda")
}
```

### 5. Conferência mecânica

```sh
git status --porcelain            # exatamente 4 caminhos: 1 M + 3 ??
grep -n "Suppress\|Andaime" mobile/features/groups/presentation/src/commonMain/kotlin/br/com/saqz/groups/presentation/ui/details/GroupAgendaBlock.kt   # não imprime nada
```

## Testes

Arquivo único: `TDET/GroupAgendaBlockTest.kt`, 8 testes, nomes e asserções no passo 3 (código + tabela). Rodam em `iosSimulatorArm64Test`. Ciclo rápido:

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile :features:groups:presentation:iosSimulatorArm64Test --tests "br.com.saqz.groups.presentation.ui.details.GroupAgendaBlockTest"
```

Nenhum teste existente muda: `GroupDetailsScreenTest` e as suítes dos outros blocos usam estados com `agenda` vazia, onde este bloco não emite nada.

## Cenas de screenshot e prints do PR

Capturas Roborazzi (lista fechada; saem em `mobile/features/groups/presentation/screenshots/agenda/`, pasta ignorada pelo git):

| PNG | Estado | O que conferir ao abrir |
|---|---|---|
| `group-details-agenda-member.png` | membro, 3 linhas | sem ação no cabeçalho; chips "Sem resposta" neutro, "Você vai" verde com ponto, "Na espera" âmbar com ponto; sem "Ver mais" |
| `group-details-agenda-more.png` | membro, 5 jogos, fechado | 3 linhas + divisória + "Ver mais 2 jogos" azul, centrado, dentro do card |
| `group-details-agenda-expanded.png` | o mesmo, depois do toque | 5 linhas, a 4ª com "Não vai" neutro; o botão sumiu e a última linha encosta no raio do card |
| `group-details-agenda-admin-draft.png` | gestor, 3 linhas | "Marcar jogo" azul à direita do título; chip "Rascunho" Brand sem ponto; meta "Só você vê até publicar" |

Em todas: a seção fica entre o bloco anterior e "Mural" com respiro de 24, e 12 entre o título e o card. Referência: `_mock-grupo/png/Main.png` (membro) e `_mock-grupo/png/GestorPendencias.png` (gestor, com "Ver mais"). Divergência conhecida e aceita: o botão não tem o chevron do mock.

Prints do PR: empurrar os 4 PNGs para a branch órfã `screenshots` em `vul-XXX/` (o `README.md` da raiz dela explica como) e embutir os 4 no corpo com o raw `https://raw.githubusercontent.com/bruno-halmeida/saqz/screenshots/vul-XXX/<nome>.png`, cada um com a legenda da coluna "Estado".

## Gates

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile :features:groups:presentation:detektAll
JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile :features:groups:presentation:iosSimulatorArm64Test
JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile :features:groups:presentation:recordRoborazziAndroidHostTest
JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile :android-app:testDevDebugUnitTest
```

## Critérios de aceite

- [ ] Diff com exatamente 4 arquivos (1 editado, 3 novos); nenhum `strings*.xml`, Contract, ViewModel, `GroupDetailsScreen.kt` ou suporte tocado.
- [ ] `GroupAgendaBlock.kt` sem `@Suppress` e sem o KDoc "Andaime (T)"; nenhum `dp` cru no arquivo.
- [ ] Agenda vazia não emite nó nenhum, para membro e para gestor.
- [ ] Até 3 jogos: sem botão. Mais de 3: 3 linhas + "Ver mais N jogos" (singular com 1), alvo ≥ 48dp; o toque mostra todas e o botão some.
- [ ] Toque na linha emite `OpenAgendaGame(gameId)`; "Marcar jogo" só para `isAdmin` e emite `CreateNextGame`.
- [ ] Tags `Agenda`, `AgendaCreate`, `AgendaMore` e `agendaGame(id)` vêm de `GroupDetailsTags`; cada uma no máximo uma vez na árvore.
- [ ] Os 8 testes verdes; os 4 PNGs gravados, abertos e conferidos contra a tabela de cenas; os 4 embutidos no PR.
- [ ] Os quatro gates verdes. Diff estimado: ~400 adições + ~10 remoções (bloco ~105, preview ~85, teste ~135, captura ~50) — longe do teto de 2000.

## Protocolo do worker

1. Branch a partir de `origin/main`: `git fetch origin && git switch -c vul-XXX-agenda-do-grupo origin/main` (XXX = número do ticket no Linear).
2. Commits pequenos em PT-BR no padrão do repo (`feat(groups): …`, `test(groups): …`).
3. Rodar TODOS os gates antes de abrir o PR; colar a saída resumida no corpo do PR. Ticket de UI: **abrir e olhar os PNGs gravados** antes de abrir o PR e embutir os prints obrigatórios.
4. PR contra `main`, aberto como ready (não draft), título `feat(groups): próximos jogos no detalhe do grupo (VUL-XXX)`. Teto: 2000 linhas de adições+remoções.
5. `gh` desta máquina: prefixar `GH_TOKEN=$(gh auth token --user bruno-halmeida)` nos comandos `gh`.
6. Depois de abrir o PR: PARAR. Não fazer triagem de review. Só executar mensagens do orquestrador que comecem com `CORRECAO:`.
