# C2 · Topo, mural, galera/gestão, quadra, skeleton e sair

**Onda 2 · depende de: S, B, T, C1 (ordem de merge) · bloqueia: D · desenvolve em paralelo com: V2, C1, C3**

## Objetivo

Trocar a "casca" do detalhe do grupo — tudo que não é jogo nem cobrança — pelo desenho novo, usando só `WaitingRow` (ticket B) dentro de cards flush:

1. **Top bar**: nome do grupo + "Editar" (só gestor, só com a tela carregada) no slot `actions` do `SaqzTopAppBar`.
2. **Topo da coluna**: só o banner da foto. O card de cabeçalho antigo sai (o nome fica só na barra).
3. **Loading**: skeleton que espelha o layout, no lugar do spinner.
4. **Mural**: duas linhas (Avisos com a prévia do último aviso, Conversa). O atalho "Jogos" e o card "Aviso recente" saem.
5. **Galera** (membro): uma linha com pilha de avatares, contagem e nomes. O card de convite do membro sai.
6. **Gestão** (gestor): quatro linhas — membros, jogos e horários, convite por link, caixa.
7. **Onde a gente joga**: a quadra padrão, só quando não há jogo marcado.
8. **Sair do grupo**: texto discreto centralizado; dono não vê.

Este ticket **não depende do contrato do V2**: lê só campos que já existem (`header.name`, `latestNotice`, `memberPreview`, `memberCount`, `scheduleSummary`, `cashbox.summary`, `venue`, `nextGame`, `mapFailed`, `isAdmin`, `isOwner`, `isLoading`, `loadFailed`). Hoje `memberPreview`, `memberCount` e `scheduleSummary` chegam vazios em produção (quem preenche é o V2): a UI fica correta com eles vazios ("Membros" sem meta, linhas de gestão sem meta).

## Fora do escopo

- `GroupDetailsScreen.kt`, `GroupDetailsSections.kt`, `GroupDetailsTags.kt`, `GroupDetailsPreviewData.kt`, Contract/ViewModel, qualquer `strings*.xml`, e2e, design system, `ui/components/`.
- **Não apagar** `GroupHeaderCard`, `GroupShortcutTiles`, `GroupLatestNoticeCard`, `GroupMemberPreview`, `GroupInviteCard`, `GroupCashboxRow`, `GroupManageList`, `GroupLeaveButton` de `GroupDetailsSections.kt`: este ticket só **para de chamar**. Quem apaga é o D.
- Não criar glifo: `SaqzIcons` não tem "link" nem "log-out". Convidar por link usa `SaqzIcons.Mail`; "Sair do grupo" fica só com o texto.
- O "+22" da pilha de avatares do mock não existe: `SaqzAvatarStack` só conta os nomes que recebe (no máximo 4 do `memberPreview`). Vale a receita.
- O banner da foto fica como está (o mock redesenha; não é deste ticket).

## Arquivos

Base: `DET = mobile/features/groups/presentation/src/commonMain/kotlin/br/com/saqz/groups/presentation/ui/details`, `TDET = mobile/features/groups/presentation/src/commonTest/kotlin/br/com/saqz/groups/presentation/ui/details`, `SDET = mobile/features/groups/presentation/src/androidHostTest/kotlin/br/com/saqz/groups/presentation/ui/details`.

| Ação | Arquivo |
|---|---|
| editar (arquivo inteiro) | `DET/GroupTopBlock.kt` |
| editar (arquivo inteiro) | `DET/GroupMuralBlock.kt` |
| editar (arquivo inteiro) | `DET/GroupPeopleBlock.kt` |
| criar | `DET/GroupShellPreviewData.kt` |
| editar (arquivo inteiro) | `TDET/GroupShellBlocksTest.kt` |
| criar | `SDET/GroupShellScreenshotTest.kt` |

Tocar em arquivo fora desta lista = parar e avisar o orquestrador.

## Passo a passo

### 0. Pré-condições (antes de editar qualquer coisa)

O C1 tem de estar mergeado: o "Editar" da barra duplicaria o texto "Editar" do `GroupVenueCard` do andaime e quebraria um teste do C1.

```sh
git fetch origin && git rebase origin/main
grep -c GroupVenueCard mobile/features/groups/presentation/src/commonMain/kotlin/br/com/saqz/groups/presentation/ui/details/GroupHeroBlock.kt
```

O `grep -c` tem de imprimir `0`. Imprimiu outro número = **parar e avisar o orquestrador** (o C1 ainda não entrou). Repetir o `fetch + rebase` imediatamente antes de rodar os gates.

Conferir também que as três peças de que este ticket depende estão na `main` (cada comando tem de imprimir um caminho/linha; senão, parar e avisar):

```sh
ls mobile/features/groups/presentation/src/commonMain/kotlin/br/com/saqz/groups/presentation/ui/components/WaitingRow.kt
grep -n "group_details_mural_notice_preview" mobile/features/groups/presentation/src/commonMain/composeResources/values/strings_group_details.xml
grep -n "const val HomeCourtMap" mobile/features/groups/presentation/src/commonMain/kotlin/br/com/saqz/groups/presentation/ui/details/GroupDetailsTags.kt
```

### 1. Substituir `DET/GroupTopBlock.kt` pelo arquivo completo abaixo

Âncora (versão do T que está sendo substituída): o arquivo começa com o KDoc `/** Andaime (T): hoje só o título; o C2 põe "Editar" no slot de ações e passa a usar [onIntent]. */` e contém `SaqzSpinner()` e `GroupHeaderCard(`. Depois da troca, nenhum dos três existe no arquivo.

```kotlin
package br.com.saqz.groups.presentation.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonSize
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzDivider
import br.com.saqz.designsystem.SaqzSkeleton
import br.com.saqz.designsystem.SaqzTopAppBar
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.details.GroupDetailsIntent
import br.com.saqz.groups.presentation.details.GroupDetailsState
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.group_details_created_photo_failed
import br.com.saqz.groups.resources.group_details_created_photo_failed_title
import br.com.saqz.groups.resources.group_details_venue_edit
import org.jetbrains.compose.resources.stringResource

// Medidas do skeleton que a grade não nomeia (mock `skeleton()` do _mock-grupo/build.mjs).
private val SkeletonHeroHeight = 256.dp
private val SkeletonHeaderHeight = 20.dp
private val SkeletonTitleHeight = 14.dp
private val SkeletonMetaHeight = 12.dp
private val SkeletonLineGap = 6.dp

/**
 * A barra: o nome do grupo (uma vez só na tela) e "Editar" para o gestor. A ação some enquanto
 * carrega e na falha de carga — não há grupo para editar.
 */
@Composable
internal fun GroupTopBar(
    state: GroupDetailsState,
    onBack: () -> Unit,
    onIntent: (GroupDetailsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val canEdit = state.isAdmin && !state.isLoading && !state.loadFailed
    SaqzTopAppBar(
        modifier = modifier,
        title = state.header?.name,
        onBack = onBack,
        actions = {
            if (canEdit) {
                SaqzButton(
                    label = stringResource(Res.string.group_details_venue_edit),
                    onClick = { onIntent(GroupDetailsIntent.EditGroup) },
                    modifier = Modifier.testTag(GroupDetailsTags.EditGroup),
                    variant = SaqzButtonVariant.Ghost,
                    size = SaqzButtonSize.Sm,
                    labelStyle = SaqzTheme.typography.label,
                )
            }
        },
    )
}

/** O skeleton espelha o layout real — bloco do jogo e duas listas — para a troca não saltar. */
@Composable
internal fun GroupDetailsLoading(modifier: Modifier = Modifier) {
    val metrics = SaqzTheme.metrics
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap)
            .testTag(GroupDetailsTags.Skeleton),
        verticalArrangement = Arrangement.spacedBy(metrics.sectionGap),
    ) {
        SaqzSkeleton(height = SkeletonHeroHeight, radius = metrics.blockRadius)
        GroupSkeletonList(
            headerWidth = metrics.grid * 16,
            titleWidth = metrics.grid * 19,
            metaWidth = metrics.grid * 25,
            circle = false,
        )
        GroupSkeletonList(
            headerWidth = metrics.grid * 10,
            titleWidth = metrics.grid * 11,
            metaWidth = metrics.grid * 27,
            circle = true,
        )
    }
}

@Composable
private fun GroupSkeletonList(headerWidth: Dp, titleWidth: Dp, metaWidth: Dp, circle: Boolean) {
    val metrics = SaqzTheme.metrics
    Column(verticalArrangement = Arrangement.spacedBy(metrics.blockGap)) {
        SaqzSkeleton(width = headerWidth, height = SkeletonHeaderHeight)
        SaqzCard(padded = false) {
            repeat(2) { index ->
                if (index > 0) SaqzDivider()
                Row(
                    modifier = Modifier.padding(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(metrics.blockGap),
                ) {
                    if (circle) {
                        SaqzSkeleton(width = metrics.grid * 5, height = metrics.grid * 5, circle = true)
                    } else {
                        SaqzSkeleton(
                            width = metrics.iconButtonSize,
                            height = metrics.iconButtonSize,
                            radius = metrics.inputRadius,
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(SkeletonLineGap)) {
                        SaqzSkeleton(width = titleWidth, height = SkeletonTitleHeight)
                        SaqzSkeleton(width = metaWidth, height = SkeletonMetaHeight)
                    }
                }
            }
        }
    }
}

/**
 * O topo da coluna rolável: só o banner da foto (pós-criação). [state] e [onIntent] ficam na
 * assinatura porque `GroupDetailsScreen` (fechado até o fecho) chama com os três; o D enxuga a
 * assinatura e apaga o `@Suppress` junto.
 */
@Suppress("UnusedParameter")
@Composable
internal fun ColumnScope.GroupTopContent(
    state: GroupDetailsState,
    onIntent: (GroupDetailsIntent) -> Unit,
    photoFailed: Boolean,
) {
    if (photoFailed) GroupPhotoFailedBanner()
}

@Composable
private fun GroupPhotoFailedBanner(modifier: Modifier = Modifier) {
    val colors = SaqzTheme.colors
    SaqzCard(modifier = modifier.testTag(GroupDetailsTags.PhotoFailed)) {
        Text(
            text = stringResource(Res.string.group_details_created_photo_failed_title),
            color = colors.textPrimary,
            style = SaqzTheme.typography.body,
        )
        Text(
            text = stringResource(Res.string.group_details_created_photo_failed),
            color = colors.textSecondary,
            style = SaqzTheme.typography.support,
        )
    }
}
```

Fatos do código que sustentam o trecho: `SaqzTopAppBar(modifier, title, logo, onBack, windowInsets, actions: @Composable RowScope.() -> Unit)` — `mobile/core/design-system/.../SaqzNavigation.kt:48-55` (precedente do slot em `PRES/ui/setup/GroupSetupScreen.kt:138`); `SaqzButton(label, onClick, modifier, variant, size, fullWidth, enabled, loading, labelStyle, …)` — `SaqzButton.kt:92-106`, `Ghost` = fundo transparente + texto `primary`; `SaqzSkeleton(modifier, width: Dp?, height: Dp = 16.dp, radius: Dp = 8.dp, circle: Boolean)` — `SaqzProgress.kt:76-82`.

O `@Suppress("UnusedParameter")` do `GroupTopContent` é o **único** do ticket e é herdado do andaime (o T já o usa nos blocos de assinatura fixa): é a exceção deliberada à regra "sem `@Suppress` novo", porque a assinatura é chamada por um arquivo fechado. Nenhum outro `@Suppress` entra.

### 2. Substituir `DET/GroupMuralBlock.kt` pelo arquivo completo abaixo

Âncora (versão do T): o arquivo contém `GroupShortcutTiles(onIntent = onIntent)` e `GroupLatestNoticeCard(notice = it)`. Depois da troca, nenhum dos dois existe no arquivo.

```kotlin
package br.com.saqz.groups.presentation.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzDivider
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzSectionHeader
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.details.GroupDetailsIntent
import br.com.saqz.groups.presentation.details.GroupDetailsState
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.group_details_chat
import br.com.saqz.groups.resources.group_details_mural_chat_meta
import br.com.saqz.groups.resources.group_details_mural_notice_preview
import br.com.saqz.groups.resources.group_details_mural_notices_empty
import br.com.saqz.groups.resources.group_details_mural_title
import br.com.saqz.groups.resources.group_details_notices
import org.jetbrains.compose.resources.stringResource

private const val NoticePreviewLines = 2

/**
 * Mural: as duas portas de conversa do grupo, para todo mundo. A linha de avisos já mostra o
 * último aviso (autor, texto e hora em até duas linhas) — é o que substitui o card "Aviso
 * recente". As tags das duas linhas são contrato do e2e.
 */
@Composable
internal fun GroupMuralBlock(
    state: GroupDetailsState,
    onIntent: (GroupDetailsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val notice = state.latestNotice
    val noticeMeta = if (notice != null) {
        stringResource(Res.string.group_details_mural_notice_preview, notice.author, notice.body, notice.timestamp)
    } else {
        stringResource(Res.string.group_details_mural_notices_empty)
    }
    Column(
        modifier = modifier.fillMaxWidth().testTag(GroupDetailsTags.Mural),
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
    ) {
        SaqzSectionHeader(title = stringResource(Res.string.group_details_mural_title))
        SaqzCard(padded = false) {
            GroupShellRow(
                icon = SaqzIcons.Megaphone,
                title = stringResource(Res.string.group_details_notices),
                meta = noticeMeta,
                tag = GroupDetailsTags.ShortcutNotices,
                onClick = { onIntent(GroupDetailsIntent.OpenNotices) },
                metaMaxLines = NoticePreviewLines,
            )
            SaqzDivider()
            GroupShellRow(
                icon = SaqzIcons.MessageSquare,
                title = stringResource(Res.string.group_details_chat),
                meta = stringResource(Res.string.group_details_mural_chat_meta),
                tag = GroupDetailsTags.ShortcutChat,
                onClick = { onIntent(GroupDetailsIntent.OpenChat) },
            )
        }
    }
}
```

(`GroupShellRow` nasce no passo 3, no mesmo pacote. Aplicar os passos 2 e 3 juntos antes de compilar.)

### 3. Substituir `DET/GroupPeopleBlock.kt` pelo arquivo completo abaixo

Âncora (versão do T): o arquivo contém `GroupCashboxRow(cashbox = it, onIntent = onIntent)`, `GroupInviteCard(onIntent = onIntent)`, `GroupLeaveButton(onIntent = onIntent, modifier = modifier)` e o `GroupHomeCourtBlock` com `= Unit`. Depois da troca, nenhum dos quatro existe no arquivo.

Mapa das tags (aprovado pelo orquestrador — `WaitingRow` põe a tag no PRÓPRIO nó clicável, e o e2e exige `ViewAllMembers` num ANCESTRAL do clicável):

| Visão | `People` | Card | Linha(s) |
|---|---|---|---|
| membro | na linha (`WaitingRow`) | `ViewAllMembers` | — |
| gestor | no contêiner do bloco | `Manage` | `ManageMembers`, `ManageSchedule`, `ManageInviteLink`, `Cashbox` |

`People` existe exatamente uma vez nas duas visões.

```kotlin
package br.com.saqz.groups.presentation.ui.details

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import br.com.saqz.designsystem.SaqzAvatarStack
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzDivider
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzSectionHeader
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.details.GroupDetailsIntent
import br.com.saqz.groups.presentation.details.GroupDetailsState
import br.com.saqz.groups.presentation.ui.components.WaitingRow
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.group_details_group_cash
import br.com.saqz.groups.resources.group_details_home_court_title
import br.com.saqz.groups.resources.group_details_invite_link
import br.com.saqz.groups.resources.group_details_leave
import br.com.saqz.groups.resources.group_details_manage_invite_meta
import br.com.saqz.groups.resources.group_details_manage_members
import br.com.saqz.groups.resources.group_details_manage_schedule
import br.com.saqz.groups.resources.group_details_manage_title
import br.com.saqz.groups.resources.group_details_map_failure
import br.com.saqz.groups.resources.group_details_names_more
import br.com.saqz.groups.resources.group_details_names_two
import br.com.saqz.groups.resources.group_details_people_count
import br.com.saqz.groups.resources.group_details_people_count_one
import br.com.saqz.groups.resources.group_details_people_fallback
import br.com.saqz.groups.resources.group_details_people_title
import br.com.saqz.groups.resources.group_details_venue_map
import org.jetbrains.compose.resources.stringResource

private const val CrowdAvatarMax = 4

/**
 * A linha da casca do detalhe (mural, galera, gestão): um `WaitingRow` clicável com chevron. A
 * descrição para o leitor de tela é o título seguido da meta.
 */
@Composable
internal fun GroupShellRow(
    icon: ImageVector,
    title: String,
    meta: String?,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    metaMaxLines: Int = 1,
    leading: (@Composable () -> Unit)? = null,
) {
    WaitingRow(
        icon = icon,
        title = title,
        meta = meta,
        contentDescription = listOfNotNull(title, meta).joinToString(". "),
        onClick = onClick,
        tag = tag,
        modifier = modifier,
        metaMaxLines = metaMaxLines,
        leading = leading,
    ) {
        SaqzIcon(SaqzIcons.ChevronRight, tint = SaqzTheme.colors.textSecondary)
    }
}

/** Membro vê a "Galera" (uma linha que abre a lista); gestor vê a "Gestão" (quatro portas). */
@Composable
internal fun GroupPeopleBlock(
    state: GroupDetailsState,
    onIntent: (GroupDetailsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.isAdmin) {
        GroupManageSection(state = state, onIntent = onIntent, modifier = modifier)
    } else {
        GroupCrowdSection(state = state, onIntent = onIntent, modifier = modifier)
    }
}

/**
 * `ViewAllMembers` fica no card e o clicável é a linha, DESCENDENTE dele: é assim que o e2e
 * (`ProfileFlowsE2eTest`) acha o alvo. `WaitingRow` exige uma tag no próprio nó, então a linha
 * leva `People`.
 */
@Composable
private fun GroupCrowdSection(
    state: GroupDetailsState,
    onIntent: (GroupDetailsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val names = state.memberPreview.map { it.name }.filter { it.isNotBlank() }
    val avatars: (@Composable () -> Unit)? = if (names.isEmpty()) {
        null
    } else {
        { SaqzAvatarStack(names = names, max = CrowdAvatarMax) }
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
    ) {
        SaqzSectionHeader(title = stringResource(Res.string.group_details_people_title))
        SaqzCard(modifier = Modifier.testTag(GroupDetailsTags.ViewAllMembers), padded = false) {
            GroupShellRow(
                icon = SaqzIcons.Users,
                title = peopleCount(state.memberCount) ?: stringResource(Res.string.group_details_people_fallback),
                meta = crowdNames(names = names, memberCount = state.memberCount),
                tag = GroupDetailsTags.People,
                onClick = { onIntent(GroupDetailsIntent.ViewAllMembers) },
                leading = avatars,
            )
        }
    }
}

/** "1 pessoa" / "26 pessoas"; `null` enquanto a contagem não chegou. */
@Composable
private fun peopleCount(memberCount: Int): String? = when (memberCount) {
    0 -> null
    1 -> stringResource(Res.string.group_details_people_count_one)
    else -> stringResource(Res.string.group_details_people_count, memberCount)
}

/** Um nome inteiro; dois ou mais, primeiros nomes — "Lucas e Bia", "Lucas, Bia e mais 24". */
@Composable
private fun crowdNames(names: List<String>, memberCount: Int): String? {
    val first = names.map { it.substringBefore(' ') }
    val others = memberCount - 2
    return when {
        first.isEmpty() -> null
        first.size == 1 -> names.single()
        first.size == 2 || others <= 0 -> stringResource(Res.string.group_details_names_two, first[0], first[1])
        else -> stringResource(Res.string.group_details_names_more, first[0], first[1], others)
    }
}

/**
 * A linha do caixa existe sempre que `cashbox != null`, mesmo sem resumo: o resumo vem de outra
 * chamada e a falha dela não pode esconder a porta. A tag `group-details-cashbox` só existe aqui.
 */
@Composable
private fun GroupManageSection(
    state: GroupDetailsState,
    onIntent: (GroupDetailsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().testTag(GroupDetailsTags.People),
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
    ) {
        SaqzSectionHeader(title = stringResource(Res.string.group_details_manage_title))
        SaqzCard(modifier = Modifier.testTag(GroupDetailsTags.Manage), padded = false) {
            GroupShellRow(
                icon = SaqzIcons.Users,
                title = stringResource(Res.string.group_details_manage_members),
                meta = peopleCount(state.memberCount),
                tag = GroupDetailsTags.ManageMembers,
                onClick = { onIntent(GroupDetailsIntent.ManageMembers) },
            )
            SaqzDivider()
            GroupShellRow(
                icon = SaqzIcons.Calendar,
                title = stringResource(Res.string.group_details_manage_schedule),
                meta = state.scheduleSummary,
                tag = GroupDetailsTags.ManageSchedule,
                onClick = { onIntent(GroupDetailsIntent.ManageSchedule) },
            )
            SaqzDivider()
            GroupShellRow(
                icon = SaqzIcons.Mail,
                title = stringResource(Res.string.group_details_invite_link),
                meta = stringResource(Res.string.group_details_manage_invite_meta),
                tag = GroupDetailsTags.ManageInviteLink,
                onClick = { onIntent(GroupDetailsIntent.InviteByLink) },
            )
            state.cashbox?.let { cashbox ->
                SaqzDivider()
                GroupShellRow(
                    icon = SaqzIcons.CreditCard,
                    title = stringResource(Res.string.group_details_group_cash),
                    meta = cashbox.summary,
                    tag = GroupDetailsTags.Cashbox,
                    onClick = { onIntent(GroupDetailsIntent.OpenCashbox) },
                )
            }
        }
    }
}

/**
 * "Onde a gente joga": a quadra padrão, só quando NÃO há jogo marcado — com jogo, o endereço e
 * o mapa são do hero. A falha do mapa aparece logo abaixo do card.
 */
@Composable
internal fun GroupHomeCourtBlock(
    state: GroupDetailsState,
    onIntent: (GroupDetailsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val venue = state.venue
    if (state.nextGame != null || venue == null) return
    val colors = SaqzTheme.colors
    val mapLabel = stringResource(Res.string.group_details_venue_map)
    val address = venue.address.takeIf { it.isNotBlank() }
    Column(
        modifier = modifier.fillMaxWidth().testTag(GroupDetailsTags.HomeCourt),
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
    ) {
        SaqzSectionHeader(title = stringResource(Res.string.group_details_home_court_title))
        SaqzCard(padded = false) {
            WaitingRow(
                icon = SaqzIcons.Pin,
                title = venue.name,
                meta = address,
                contentDescription = listOfNotNull(mapLabel, venue.name, address).joinToString(". "),
                onClick = { onIntent(GroupDetailsIntent.OpenVenueMap) },
                tag = GroupDetailsTags.HomeCourtMap,
            ) {
                Text(
                    text = mapLabel,
                    style = SaqzTheme.typography.support.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.primary,
                )
            }
        }
        if (state.mapFailed) {
            Text(
                text = stringResource(Res.string.group_details_map_failure),
                style = SaqzTheme.typography.support,
                color = colors.textPrimary,
            )
        }
    }
}

/** Sair é discreto de propósito: texto centralizado, sem card. Dono não sai do grupo. */
@Composable
internal fun GroupLeaveBlock(
    state: GroupDetailsState,
    onIntent: (GroupDetailsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.isOwner) return
    val metrics = SaqzTheme.metrics
    val label = stringResource(Res.string.group_details_leave)
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClickLabel = label, role = Role.Button) { onIntent(GroupDetailsIntent.Leave) }
                .testTag(GroupDetailsTags.Leave)
                .heightIn(min = metrics.minimumTouchTarget)
                .padding(horizontal = metrics.horizontalPadding),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = label, style = SaqzTheme.typography.label, color = SaqzTheme.colors.textSecondary)
        }
    }
}

@Preview
@Composable
private fun GroupShellMemberPreview() = SaqzTheme {
    GroupDetailsScreen(state = GroupShellPreviewData.memberNoGame, onBack = {}, onIntent = {})
}

@Preview
@Composable
private fun GroupShellManagerPreview() = SaqzTheme {
    GroupDetailsScreen(state = GroupShellPreviewData.manager, onBack = {}, onIntent = {})
}
```

Fatos do código: `WaitingRow(icon, title, meta, contentDescription, onClick, tag, modifier, metaMaxLines, leading, trailing)` — "Contrato exportado" do ticket B (`PRES/ui/components/WaitingRow.kt`); `SaqzAvatarStack(names, modifier, size = 30.dp, max = 3, …)` — `SaqzMemberRow.kt:105-113`; `SaqzSectionHeader(title, modifier, icon, action, onAction)` — `SaqzCard.kt:90-96`; `SaqzCard(modifier, tone, padded, cornerRadius, content)` — `SaqzCard.kt:44-50`. Detekt `TooManyFunctions` ignora privadas e `@Composable`; o arquivo tem 4 funções não privadas.

### 4. Criar `DET/GroupShellPreviewData.kt` (arquivo completo)

```kotlin
package br.com.saqz.groups.presentation.ui.details

import br.com.saqz.groups.presentation.details.CashboxUi
import br.com.saqz.groups.presentation.details.GroupDetailsState

/**
 * Os estados da casca do detalhe (topo, mural, galera/gestão, quadra, sair) para previews,
 * testes e capturas. Tudo deriva de [GroupDetailsPreviewData] com `.copy(...)`.
 */
internal object GroupShellPreviewData {
    val member = GroupDetailsPreviewData.member

    val memberNoGame = GroupDetailsPreviewData.memberNoGame

    val memberNoGameMapFailed = memberNoGame.copy(mapFailed = true)

    /** Produção de hoje, antes do V2: sem aviso, sem prévia de membros e sem contagem. */
    val memberBare = member.copy(latestNotice = null, memberPreview = emptyList(), memberCount = 0)

    /** 40 caracteres: o nome corta com reticências na barra. */
    val memberLongName = member.copy(
        header = GroupDetailsPreviewData.memberHeader.copy(name = "Vôlei Misto de Amigos do CERET Tatuapé 2"),
    )

    /** O gestor com as metas no formato que o V2 produz. */
    val manager = GroupDetailsPreviewData.admin.copy(
        cashbox = CashboxUi(summary = "Saldo R$ 380,00"),
        scheduleSummary = "Terça e Quinta · 19h30",
    )

    /** O resumo financeiro falhou: a linha do caixa continua, sem meta. */
    val managerNoCashSummary = manager.copy(cashbox = CashboxUi())

    val managerNewGroup = GroupDetailsPreviewData.adminNoGame.copy(
        memberCount = 1,
        scheduleSummary = null,
        cashbox = CashboxUi(summary = "Saldo R$ 0,00"),
    )

    /** Admin que não é dono: o único gestor que vê "Sair do grupo". */
    val managerNotOwner = manager.copy(isOwner = false)

    val loading = GroupDetailsState()

    val loadFailed = GroupDetailsState(isLoading = false, loadFailed = true)
}
```

### 5. Substituir `TDET/GroupShellBlocksTest.kt` — ver a seção Testes

### 6. Criar `SDET/GroupShellScreenshotTest.kt` — ver a seção Cenas

### 7. Conferência mecânica

```sh
D=mobile/features/groups/presentation/src/commonMain/kotlin/br/com/saqz/groups/presentation/ui/details
grep -n "GroupHeaderCard\|GroupShortcutTiles\|GroupLatestNoticeCard\|GroupMemberPreview\|GroupInviteCard\|GroupCashboxRow\|GroupManageList\|GroupLeaveButton\|SaqzSpinner\|Andaime" $D/GroupTopBlock.kt $D/GroupMuralBlock.kt $D/GroupPeopleBlock.kt
grep -c "@Suppress" $D/GroupTopBlock.kt $D/GroupMuralBlock.kt $D/GroupPeopleBlock.kt
git diff --stat origin/main
```

O primeiro `grep` não imprime nada. O segundo imprime `1`, `0`, `0`. O `--stat` lista exatamente os 6 arquivos da seção Arquivos.

## Testes

### `TDET/GroupShellBlocksTest.kt` — substituir pelo arquivo completo (30 testes)

Rodam em `iosSimulatorArm64Test`. As linhas são clicáveis e fundem os filhos: título e meta são achados por `onNodeWithText` na árvore fundida (o nó da linha carrega os dois textos). "Editar" é sempre procurado pela TAG, nunca pelo texto.

```kotlin
package br.com.saqz.groups.presentation.ui.details

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import br.com.saqz.groups.presentation.details.CashboxUi
import br.com.saqz.groups.presentation.details.GroupDetailsIntent
import br.com.saqz.groups.presentation.details.GroupDetailsState
import kotlin.test.Test
import kotlin.test.assertEquals

/** Topo, mural, galera/gestão, quadra e sair: os blocos do ticket C2. */
@OptIn(ExperimentalTestApi::class)
class GroupShellBlocksTest {
    private val managerOnly = listOf(
        GroupDetailsTags.EditGroup,
        GroupDetailsTags.Manage,
        GroupDetailsTags.ManageMembers,
        GroupDetailsTags.ManageSchedule,
        GroupDetailsTags.ManageInviteLink,
        GroupDetailsTags.Cashbox,
    )

    private val memberOnly = listOf(GroupDetailsTags.ViewAllMembers)

    private val everyone = listOf(
        GroupDetailsTags.Mural,
        GroupDetailsTags.ShortcutNotices,
        GroupDetailsTags.ShortcutChat,
        GroupDetailsTags.People,
    )

    private val gone = listOf(
        GroupDetailsTags.ShortcutSchedule,
        GroupDetailsTags.ShortcutCashbox,
        GroupDetailsTags.Notice,
        GroupDetailsTags.Invite,
    )

    private val goneTexts = listOf("Marcar próximo jogo", "Editar grupo", "Aviso recente", "Convidar mais gente", "Gerenciar")

    @Test
    fun managerViewHasManagementAndNoCrowdRow() = runComposeUiTest {
        setDetailsScreen(GroupShellPreviewData.manager)

        (managerOnly + everyone).forEach { onAllNodesWithTag(it).assertCountEquals(1) }
        memberOnly.forEach { onAllNodesWithTag(it).assertCountEquals(0) }
    }

    @Test
    fun memberViewHasTheCrowdRowAndNoManagement() = runComposeUiTest {
        setDetailsScreen(GroupShellPreviewData.member)

        (memberOnly + everyone).forEach { onAllNodesWithTag(it).assertCountEquals(1) }
        managerOnly.forEach { onAllNodesWithTag(it).assertCountEquals(0) }
    }

    @Test
    fun oldShellPiecesAreGoneForTheManager() = runComposeUiTest {
        setDetailsScreen(GroupShellPreviewData.manager)

        gone.forEach { onAllNodesWithTag(it).assertCountEquals(0) }
        goneTexts.forEach { onAllNodesWithText(it).assertCountEquals(0) }
    }

    @Test
    fun oldShellPiecesAreGoneForTheMember() = runComposeUiTest {
        setDetailsScreen(GroupShellPreviewData.member)

        gone.forEach { onAllNodesWithTag(it).assertCountEquals(0) }
        goneTexts.forEach { onAllNodesWithText(it).assertCountEquals(0) }
        onAllNodesWithText("Membros").assertCountEquals(0)
    }

    // ---- topo ----

    @Test
    fun editLivesInTheTopBarAndAsksToEditTheGroup() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setDetailsScreen(GroupShellPreviewData.manager) { intents += it }

        onNodeWithTag(GroupDetailsTags.EditGroup).performClick()

        assertEquals(listOf<GroupDetailsIntent>(GroupDetailsIntent.EditGroup), intents)
    }

    @Test
    fun loadingShowsTheSkeletonAndNoEditAction() = runComposeUiTest {
        setDetailsScreen(GroupDetailsState(isAdmin = true))

        onNodeWithTag(GroupDetailsTags.Skeleton).assertExists()
        onAllNodesWithTag(GroupDetailsTags.EditGroup).assertCountEquals(0)
    }

    @Test
    fun loadFailureHasNoSkeletonAndNoEditAction() = runComposeUiTest {
        setDetailsScreen(GroupDetailsState(isLoading = false, loadFailed = true, isAdmin = true))

        onAllNodesWithTag(GroupDetailsTags.Skeleton).assertCountEquals(0)
        onAllNodesWithTag(GroupDetailsTags.EditGroup).assertCountEquals(0)
    }

    @Test
    fun loadedScreenHasNoSkeleton() = runComposeUiTest {
        setDetailsScreen(GroupShellPreviewData.member)

        onAllNodesWithTag(GroupDetailsTags.Skeleton).assertCountEquals(0)
        onNodeWithText("Vôlei do CERET").assertExists()
    }

    @Test
    fun createdPhotoFailedBannerExplainsTheGroupExists() = runComposeUiTest {
        setDetailsScreen(GroupShellPreviewData.managerNewGroup, photoFailed = true)

        onNodeWithTag(GroupDetailsTags.PhotoFailed).assertExists()
        onNodeWithText("Grupo criado").assertExists()
        onNodeWithText("A foto não carregou. Você pode tentar de novo em Editar grupo.").assertExists()
    }

    @Test
    fun withoutPhotoFailureThereIsNoBanner() = runComposeUiTest {
        setDetailsScreen(GroupShellPreviewData.managerNewGroup)

        onAllNodesWithTag(GroupDetailsTags.PhotoFailed).assertCountEquals(0)
    }

    // ---- mural ----

    @Test
    fun muralRowsOpenNoticesAndChat() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setDetailsScreen(GroupShellPreviewData.member) { intents += it }

        onNodeWithTag(GroupDetailsTags.ShortcutNotices).performScrollTo().performClick()
        onNodeWithTag(GroupDetailsTags.ShortcutChat).performScrollTo().performClick()

        assertEquals(listOf(GroupDetailsIntent.OpenNotices, GroupDetailsIntent.OpenChat), intents)
    }

    @Test
    fun muralShowsTheLatestNoticePreview() = runComposeUiTest {
        setDetailsScreen(GroupShellPreviewData.member)

        onNodeWithText("Mural").assertExists()
        onNodeWithText("Lucas: Cheguem 15 min antes para montar a rede. · Hoje, 10h30").assertExists()
        onNodeWithText("Fale com a galera do grupo").assertExists()
        onAllNodesWithText("Nenhum aviso por enquanto").assertCountEquals(0)
    }

    @Test
    fun muralWithoutNoticeSaysThereIsNone() = runComposeUiTest {
        setDetailsScreen(GroupShellPreviewData.memberBare)

        onNodeWithTag(GroupDetailsTags.ShortcutNotices).assertExists()
        onNodeWithText("Nenhum aviso por enquanto").assertExists()
    }

    // ---- galera ----

    @Test
    fun crowdRowShowsCountAndTheFirstTwoNamesPlusTheRest() = runComposeUiTest {
        setDetailsScreen(GroupShellPreviewData.member)

        onNodeWithText("Galera").assertExists()
        onNodeWithText("26 pessoas").assertExists()
        onNodeWithText("Lucas, Bia e mais 24").assertExists()
    }

    @Test
    fun crowdRowWithTwoPeopleJoinsTheNames() = runComposeUiTest {
        val member = GroupShellPreviewData.member
        setDetailsScreen(member.copy(memberPreview = member.memberPreview.take(2), memberCount = 2))

        onNodeWithText("2 pessoas").assertExists()
        onNodeWithText("Lucas e Bia").assertExists()
    }

    @Test
    fun crowdRowWithOnePersonShowsTheWholeName() = runComposeUiTest {
        val member = GroupShellPreviewData.member
        setDetailsScreen(member.copy(memberPreview = member.memberPreview.take(1), memberCount = 1))

        onNodeWithText("1 pessoa").assertExists()
        onNodeWithText("Lucas Prado").assertExists()
    }

    @Test
    fun crowdRowWithoutDataFallsBackAndStillOpensMembers() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setDetailsScreen(GroupShellPreviewData.memberBare) { intents += it }

        onNodeWithText("Membros").assertExists()
        // O mesmo seletor do e2e: o clicável é DESCENDENTE do nó com a tag.
        onNode(
            hasClickAction() and hasAnyAncestor(hasTestTag(GroupDetailsTags.ViewAllMembers)),
        ).performScrollTo().performClick()

        assertEquals(listOf<GroupDetailsIntent>(GroupDetailsIntent.ViewAllMembers), intents)
    }

    // ---- gestão ----

    @Test
    fun manageRowsEmitTheirIntents() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setDetailsScreen(GroupShellPreviewData.manager) { intents += it }

        listOf(
            GroupDetailsTags.ManageMembers,
            GroupDetailsTags.ManageSchedule,
            GroupDetailsTags.ManageInviteLink,
            GroupDetailsTags.Cashbox,
        ).forEach { onNodeWithTag(it).performScrollTo().performClick() }

        assertEquals(
            listOf(
                GroupDetailsIntent.ManageMembers,
                GroupDetailsIntent.ManageSchedule,
                GroupDetailsIntent.InviteByLink,
                GroupDetailsIntent.OpenCashbox,
            ),
            intents,
        )
    }

    @Test
    fun manageRowsShowCountScheduleInviteAndBalance() = runComposeUiTest {
        setDetailsScreen(GroupShellPreviewData.manager)

        onNodeWithText("Gestão").assertExists()
        onNodeWithText("26 pessoas").assertExists()
        onNodeWithText("Terça e Quinta · 19h30").assertExists()
        onNodeWithText("Link, QR e pedidos de entrada").assertExists()
        onNodeWithText("Saldo R$ 380,00").assertExists()
    }

    @Test
    fun manageRowsSurviveEmptyMetas() = runComposeUiTest {
        setDetailsScreen(GroupShellPreviewData.manager.copy(memberCount = 0, scheduleSummary = null))

        onNodeWithText("Membros e permissões").assertExists()
        onNodeWithText("Jogos e horários").assertExists()
        onAllNodesWithText("26 pessoas").assertCountEquals(0)
    }

    @Test
    fun cashboxRowWithoutSummaryStaysClickable() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setDetailsScreen(GroupShellPreviewData.managerNoCashSummary) { intents += it }

        onNodeWithText("Caixa do grupo").assertExists()
        onAllNodesWithText("Saldo R$ 380,00").assertCountEquals(0)
        onNodeWithTag(GroupDetailsTags.Cashbox).performScrollTo().performClick()

        assertEquals(listOf<GroupDetailsIntent>(GroupDetailsIntent.OpenCashbox), intents)
    }

    @Test
    fun managerWithoutCashboxHasNoCashRow() = runComposeUiTest {
        setDetailsScreen(GroupShellPreviewData.manager.copy(cashbox = null))

        onAllNodesWithTag(GroupDetailsTags.Cashbox).assertCountEquals(0)
        onNodeWithTag(GroupDetailsTags.ManageInviteLink).assertExists()
    }

    @Test
    fun memberNeverSeesTheCashboxEvenFromStaleState() = runComposeUiTest {
        setDetailsScreen(GroupShellPreviewData.member.copy(cashbox = CashboxUi(summary = "Saldo R$ 380,00")))

        onAllNodesWithTag(GroupDetailsTags.Cashbox).assertCountEquals(0)
        onAllNodesWithText("Caixa do grupo").assertCountEquals(0)
        onAllNodesWithText("Saldo R$ 380,00").assertCountEquals(0)
    }

    // ---- quadra ----

    @Test
    fun homeCourtIsHiddenWhileThereIsAGame() = runComposeUiTest {
        setDetailsScreen(GroupShellPreviewData.member)

        onAllNodesWithTag(GroupDetailsTags.HomeCourt).assertCountEquals(0)
        onAllNodesWithTag(GroupDetailsTags.HomeCourtMap).assertCountEquals(0)
    }

    @Test
    fun homeCourtWithoutGameOpensTheMap() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setDetailsScreen(GroupShellPreviewData.memberNoGame) { intents += it }

        onNodeWithTag(GroupDetailsTags.HomeCourt).assertExists()
        onNodeWithTag(GroupDetailsTags.HomeCourtMap).performScrollTo().performClick()

        assertEquals(listOf<GroupDetailsIntent>(GroupDetailsIntent.OpenVenueMap), intents)
    }

    @Test
    fun homeCourtWithoutVenueIsHidden() = runComposeUiTest {
        setDetailsScreen(GroupShellPreviewData.memberNoGame.copy(venue = null))

        onAllNodesWithTag(GroupDetailsTags.HomeCourt).assertCountEquals(0)
    }

    @Test
    fun homeCourtShowsTheMapFailureBelowTheCard() = runComposeUiTest {
        setDetailsScreen(GroupShellPreviewData.memberNoGameMapFailed)

        onNode(
            hasText("Não foi possível abrir o mapa. Consulte o endereço acima ou tente novamente.") and
                hasAnyAncestor(hasTestTag(GroupDetailsTags.HomeCourt)),
        ).assertExists()
    }

    // ---- sair ----

    @Test
    fun ownerCannotLeave() = runComposeUiTest {
        setDetailsScreen(GroupShellPreviewData.manager)

        onAllNodesWithTag(GroupDetailsTags.Leave).assertCountEquals(0)
    }

    @Test
    fun adminWhoIsNotTheOwnerCanRequestDeparture() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setDetailsScreen(GroupShellPreviewData.managerNotOwner) { intents += it }

        onNodeWithTag(GroupDetailsTags.Leave).performScrollTo().performClick()

        assertEquals(listOf<GroupDetailsIntent>(GroupDetailsIntent.Leave), intents)
    }

    @Test
    fun memberCanRequestDeparture() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setDetailsScreen(GroupShellPreviewData.member) { intents += it }

        onNodeWithText("Sair do grupo").assertExists()
        onNodeWithTag(GroupDetailsTags.Leave).performScrollTo().performClick()

        assertEquals(listOf<GroupDetailsIntent>(GroupDetailsIntent.Leave), intents)
    }
}
```

Se um teste falhar com "expected at most 1 node" em `"26 pessoas"`, `"Membros"` ou `"Sair do grupo"` por causa de um bloco de OUTRO ticket já mergeado (texto igual em outro bloco): **parar e avisar o orquestrador** com o nome do teste e a saída; não trocar a asserção por conta própria.

### Testes de outros donos que têm de continuar verdes SEM edição

- `TDET/GroupDetailsScreenTest.kt` (D): `Mural` e `People` existem para `member`; loading não compõe `Content`.
- `TDET/GroupOwnDebtBlockTest.kt` (C3): usa `Mural` como sentinela.
- `TDET/GroupHeroBlockTest.kt` (C1), `GroupLeaveSheetTest`, `GroupDetailsRootTest`.
- `mobile/compose-app/src/commonTest/.../navigation/SaqzNavHostViewModelScopeTest.kt:230` e `:234` — `onNodeWithTag("group-details-leave").performScrollTo().performClick()`: o novo "Sair" mantém a tag no nó clicável dentro da coluna rolável.

### e2e — nenhum arquivo muda; o que cada um exige e por que continua válido

`E2eSupport.click(tag, scroll)` (`E2E/E2eSupport.kt:58-65`) espera EXATAMENTE um nó com a tag, rola, exige `isEnabled` e clica no próprio nó. `WaitingRow` põe `testTag` e `clickable` no mesmo nó — é o que atende.

| Tag | Onde o e2e usa | Como o C2 atende |
|---|---|---|
| `group-details-shortcut-chat` | `CommunicationE2eTest.kt:22`, `:31`, `:55`; `CommunicationDetailsE2eTest.kt:46`, `:71`, `:173`; `GroupLeaveE2eTest.kt:45` (`waitTag`) | linha Conversa do mural, para todo mundo |
| `group-details-shortcut-notices` | `CommunicationE2eTest.kt:35`, `:44`; `CommunicationDetailsE2eTest.kt:50` | linha Avisos do mural, para todo mundo |
| `group-details-manage-members` | `ProfileFlowsE2eTest.kt:138-142` (`performScrollTo` + `assertIsEnabled` + `performClick` no nó) | linha "Membros e permissões", só gestor |
| `group-details-view-all-members` | `ProfileFlowsE2eTest.kt:144-147` (`hasClickAction() and hasAnyAncestor(hasTestTag(...))` — tem de casar UM nó) | tag no card; a única linha clicável dentro dele é a da galera |
| `group-details-cashbox` | `FinancialFlowsE2eTest.kt:28`, `:43`, `:183` (membro: `assertDoesNotExist`); `MonthlyGenerationE2eTest.kt:21` | linha "Caixa do grupo", só gestor, uma vez na árvore |
| `group-details-leave` | `GroupLeaveE2eTest.kt:23`, `:29`, `:53` (dono: `assertDoesNotExist`) | texto clicável com a tag; dono não emite o bloco |

## Cenas de screenshot e prints do PR

### `SDET/GroupShellScreenshotTest.kt` (arquivo completo — 11 cenas, pasta `shell`)

```kotlin
package br.com.saqz.groups.presentation.ui.details

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// A casca do detalhe (C2): barra, skeleton, quadra, mural, galera/gestão e sair, um estado
// por cena. Estado fora da cena é estado não conferido (AGENTS.md §11).
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [35],
    qualifiers = RobolectricDeviceQualifiers.Pixel7,
    application = Application::class,
)
class GroupShellScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    @Config(qualifiers = "+h2400dp")
    fun member() = compose.captureDetails("group-shell-member", GroupShellPreviewData.member, "shell")

    @Test
    @Config(qualifiers = "+h2000dp")
    fun memberNoGame() = compose.captureDetails("group-shell-member-no-game", GroupShellPreviewData.memberNoGame, "shell")

    @Test
    @Config(qualifiers = "+h2000dp")
    fun memberNoGameMapFailed() =
        compose.captureDetails("group-shell-member-map-failed", GroupShellPreviewData.memberNoGameMapFailed, "shell")

    @Test
    @Config(qualifiers = "+h2400dp")
    fun memberBare() = compose.captureDetails("group-shell-member-bare", GroupShellPreviewData.memberBare, "shell")

    @Test
    @Config(qualifiers = "+h2400dp")
    fun memberLongName() = compose.captureDetails("group-shell-member-long-name", GroupShellPreviewData.memberLongName, "shell")

    @Test
    @Config(qualifiers = "+h2400dp")
    fun manager() = compose.captureDetails("group-shell-manager", GroupShellPreviewData.manager, "shell")

    @Test
    @Config(qualifiers = "+h2400dp")
    fun managerNoCashSummary() =
        compose.captureDetails("group-shell-manager-no-cash-summary", GroupShellPreviewData.managerNoCashSummary, "shell")

    @Test
    @Config(qualifiers = "+h2000dp")
    fun managerNewGroup() =
        compose.captureDetails("group-shell-manager-new-group", GroupShellPreviewData.managerNewGroup, "shell", photoFailed = true)

    @Test
    @Config(qualifiers = "+h2400dp")
    fun managerNotOwner() =
        compose.captureDetails("group-shell-manager-not-owner", GroupShellPreviewData.managerNotOwner, "shell")

    @Test
    fun loading() = compose.captureDetails("group-shell-loading", GroupShellPreviewData.loading, "shell")

    @Test
    fun loadFailed() = compose.captureDetails("group-shell-load-failed", GroupShellPreviewData.loadFailed, "shell")
}
```

Os PNGs saem em `mobile/features/groups/presentation/screenshots/shell/`.

### O que olhar em cada PNG antes de abrir o PR (referência: `_mock-grupo/png/`)

| Cena | Mock de referência | Conferir |
|---|---|---|
| `group-shell-member` | `Main.png` | barra só com o nome, SEM "Editar"; sem card de cabeçalho; Mural com 2 linhas e a prévia do aviso em até 2 linhas; Galera com 3 avatares, "26 pessoas", "Lucas, Bia e mais 24"; "Sair do grupo" centralizado sem card; sem atalho "Jogos", sem "Aviso recente", sem "Convidar mais gente" |
| `group-shell-member-no-game` | `MembroSemJogo.png` | "Onde a gente joga" entre o jogo e o Mural, com pino, nome, endereço e "Ver no mapa" azul |
| `group-shell-member-map-failed` | `MembroMapaFalhou.png` (só o texto) | a frase da falha logo abaixo do card da quadra |
| `group-shell-member-bare` | — | Avisos com "Nenhum aviso por enquanto"; Galera com ícone de pessoas no círculo, "Membros", sem meta |
| `group-shell-member-long-name` | `MembroNomeLongo.png` | nome de 40 caracteres em UMA linha, cortado com reticências, sem empurrar o voltar |
| `group-shell-manager` | `GestorPendencias.png` | "Editar" azul à direita na barra; Gestão com 4 linhas e metas ("26 pessoas", "Terça e Quinta · 19h30", "Link, QR e pedidos de entrada", "Saldo R$ 380,00"); sem "Sair" (dono) |
| `group-shell-manager-no-cash-summary` | `GestorFinancasFalha.png` | "Caixa do grupo" presente, sem meta, centralizada na linha |
| `group-shell-manager-new-group` | `GestorGrupoNovo.png` | banner da foto no topo; "1 pessoa"; "Jogos e horários" sem meta; "Saldo R$ 0,00"; "Onde a gente joga" presente |
| `group-shell-manager-not-owner` | — | igual ao gestor, COM "Sair do grupo" no fim |
| `group-shell-loading` | `MembroCarregando.png` | barra só com o voltar; bloco grande de raio 20 + duas listas de 2 linhas (quadrado na 1ª, círculo na 2ª) |
| `group-shell-load-failed` | `MembroErro.png` | estado de falha, barra sem "Editar" |

Diferenças esperadas contra o mock (vale a receita): sem o "+22" na pilha de avatares; "Convidar por link" com ícone de envelope; "Sair do grupo" sem ícone; banner da foto no desenho antigo; o skeleton do bloco do jogo é um bloco único, sem linhas internas.

### Prints obrigatórios no corpo do PR

Empurrar para a branch órfã `screenshots` em `vul-XXX/` e embutir o raw (`https://raw.githubusercontent.com/bruno-halmeida/saqz/screenshots/vul-XXX/<nome>.png`) das **11** cenas acima, com a legenda de cada uma igual ao nome da cena.

## Gates

Rodar da raiz do worktree, nesta ordem, **depois** de repetir o passo 0 (`git fetch origin && git rebase origin/main` + `grep -c GroupVenueCard` = 0):

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile :features:groups:presentation:detektAll
JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile :features:groups:presentation:iosSimulatorArm64Test --tests "br.com.saqz.groups.presentation.ui.details.*"
JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile :features:groups:presentation:iosSimulatorArm64Test
JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile :features:groups:presentation:recordRoborazziAndroidHostTest
JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile :android-app:testDevDebugUnitTest
JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile :compose-app:iosSimulatorArm64Test --tests "br.com.saqz.composeapp.navigation.*"
```

O último roda o `SaqzNavHostViewModelScopeTest`, que clica `group-details-leave`. Todos têm de ficar verdes: não existe "vermelho esperado" neste ticket. O e2e Android não roda no gate do PR (nenhum arquivo de `E2E/` muda); a tabela da seção Testes é a conferência.

## Critérios de aceite

- [ ] `git diff --stat origin/main` lista exatamente os 6 arquivos da seção Arquivos.
- [ ] `GroupTopBlock.kt`, `GroupMuralBlock.kt` e `GroupPeopleBlock.kt` não chamam nenhuma peça de `GroupDetailsSections.kt` e não têm mais KDoc "Andaime (T)"; o único `@Suppress` é o do `GroupTopContent`.
- [ ] Barra: "Editar" (tag `EditGroup`, intent `EditGroup`) só para gestor com a tela carregada; ausente em loading e em falha de carga.
- [ ] Loading mostra `Skeleton` (sem spinner); o card de cabeçalho não existe mais.
- [ ] Mural: `ShortcutNotices` → `OpenNotices`, `ShortcutChat` → `OpenChat`; prévia do aviso em até 2 linhas; "Nenhum aviso por enquanto" sem aviso. `ShortcutSchedule`, `ShortcutCashbox`, `Notice` e `Invite` não existem na árvore.
- [ ] Membro: `ViewAllMembers` no card, clicável descendente emite `ViewAllMembers`; contagem 0/1/N e nomes 0/1/2/3+ corretos.
- [ ] Gestor: 4 linhas com as tags e intents do desenho; caixa sem resumo continua clicável; sem `cashbox`, sem linha; membro nunca vê `Cashbox`.
- [ ] Quadra só com `nextGame == null && venue != null`; `HomeCourtMap` emite `OpenVenueMap`; falha do mapa abaixo do card.
- [ ] Dono sem `Leave`; admin não-dono e membro com `Leave` → intent `Leave`.
- [ ] Cada tag do contrato do e2e existe exatamente uma vez na árvore (os testes de tabela usam `assertCountEquals(1)`).
- [ ] Nenhuma chave de string criada/editada; nenhum `dp` cru fora dos `private val` do topo de `GroupTopBlock.kt`.
- [ ] 11 PNGs em `screenshots/shell/` abertos e conferidos contra a tabela; os 11 embutidos no corpo do PR.
- [ ] Os 6 gates verdes. Diff ≤ 2000 linhas (estimativa: ~1000 adições + ~330 remoções ≈ 1330).

## Protocolo do worker

1. Branch a partir de `origin/main`: `git fetch origin && git switch -c vul-XXX-casca-detalhe-grupo origin/main` (XXX = número do ticket no Linear). **Só abrir o PR depois do C1 mergeado** (passo 0): antes dos gates, `git fetch origin && git rebase origin/main` e `grep -c GroupVenueCard …/GroupHeroBlock.kt` = 0; senão, parar e avisar o orquestrador.
2. Commits pequenos em PT-BR no padrão do repo (`feat(groups): …`, `test(groups): …`).
3. Rodar TODOS os gates antes de abrir o PR; colar a saída resumida no corpo do PR. Ticket de UI: **abrir e olhar os PNGs gravados** antes de abrir o PR e embutir os prints obrigatórios.
4. PR contra `main`, aberto como ready (não draft), título `feat(groups): topo, mural, galera/gestão, quadra, skeleton e sair no detalhe do grupo (VUL-XXX)`. Teto: 2000 linhas de adições+remoções.
5. `gh` desta máquina: prefixar `GH_TOKEN=$(gh auth token --user bruno-halmeida)` nos comandos `gh`.
6. Depois de abrir o PR: PARAR. Não fazer triagem de review. Só executar mensagens do orquestrador que comecem com `CORRECAO:`.
