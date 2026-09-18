# GM3 · Mobile: UI do convidado na tela do jogo (VUL-XXX)

**Onda 3 · depende de: GM2 (na main) · bloqueia: GD · paralelo com: GM4**

## Objetivo

Desenhar o que o GM2 já deixou no estado: botão "Levar convidado" com a dica, a folha de adicionar, as linhas
de convidado (confirmados e fila) com "Tirar", a folha de confirmação e o toast. Referência visual: pranchas
1–13 e 15 do canvas (`../preview/*.html` / `../png/`). Leia `CONTRATO.md`.

`UI` = `mobile/features/groups/presentation/src/commonMain/kotlin/br/com/saqz/groups/presentation/ui/gamedetail`
`TUI` = `mobile/features/groups/presentation/src/commonTest/kotlin/br/com/saqz/groups/presentation/ui/gamedetail`
`SUI` = `mobile/features/groups/presentation/src/androidHostTest/kotlin/br/com/saqz/groups/presentation/ui/gamedetail`
`G` = `JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile`

## Arquivos (lista FECHADA)

- NOVO `UI/GameGuestSection.kt`, NOVO `UI/GameGuestPreviewData.kt`
- EDITAR `UI/GameDetailScreen.kt`, `UI/GameWaitlistSection.kt`
- NOVO `TUI/GameGuestSectionTest.kt`, NOVO `SUI/GameGuestScreenshotTest.kt`

Fora da lista = PARE, SendMessage para `main`, aguarde. Strings: TODAS já existem em `strings_game_guest.xml`
(GM2) — faltou alguma? PARE e relate; não crie chave, não escreva texto literal.

## Passo 0

`git fetch origin && git switch -c vul-XXX-convidado-ui origin/main`;
`git grep -c "class GameGuestUi" -- mobile/features/groups/presentation` >= 1 (GM2 na main), senão PARE. Commit cedo.

## Passo 1 · `UI/GameGuestSection.kt` (arquivo inteiro; ajuste SÓ imports que o compilador pedir)

```kotlin
package br.com.saqz.groups.presentation.ui.gamedetail

internal object GameGuestTags {
    const val Add = "game-guest-add"
    const val Hint = "game-guest-hint"
    const val Sheet = "game-guest-sheet"
    const val Name = "game-guest-name"
    const val Submit = "game-guest-submit"
    const val AddFailed = "game-guest-add-failed"
    const val RemoveSheet = "game-guest-remove-sheet"
    const val RemoveConfirm = "game-guest-remove-confirm"
    const val RemoveFailed = "game-guest-remove-failed"
    const val Notice = "game-guest-notice"

    fun remove(rowId: String) = "game-guest-remove-$rowId"
    fun row(rowId: String) = "game-guest-row-$rowId"
}

/** Botão "Levar convidado" + a dica do porquê (regras A e C). Some fora de jogo publicado. */
@Composable
internal fun GameGuestAction(guest: GameGuestUi, onIntent: (GameDetailIntent) -> Unit, modifier: Modifier = Modifier) {
    if (!guest.visible) return
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SaqzButton(
            label = stringResource(Res.string.game_guest_add),
            onClick = { onIntent(GameDetailIntent.OpenGuestSheet) },
            modifier = Modifier.testTag(GameGuestTags.Add),
            variant = SaqzButtonVariant.Secondary,
            fullWidth = true,
            enabled = guest.enabled,
            leadingContent = { tint -> SaqzIcon(SaqzIcons.Users, tint = tint, size = GuestButtonIconSize) },
        )
        Text(
            text = stringResource(
                when (guest.hint) {
                    GameGuestHint.Default -> Res.string.game_guest_hint_default
                    GameGuestHint.NeedAnswer -> Res.string.game_guest_hint_need_answer
                    GameGuestHint.Closed -> Res.string.game_guest_hint_closed
                },
            ),
            color = SaqzTheme.colors.textSecondary,
            style = SaqzTheme.typography.caption,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag(GameGuestTags.Hint),
        )
    }
}

/** A segunda linha de uma pessoa da lista quando ela é convidada. `null` = não é convidado. */
@Composable
internal fun GameGuestRowUi.metaLabel(feeLabel: String?, confirmed: Boolean): String = when {
    isYours && confirmed && feeLabel != null -> stringResource(Res.string.game_guest_yours_fee, feeLabel)
    isYours -> stringResource(Res.string.game_guest_yours)
    else -> stringResource(Res.string.game_guest_of, hostName)
}

/** Avatar do convidado: círculo tracejado na cor da marca, para não se passar por membro. */
@Composable
internal fun GameGuestAvatar(modifier: Modifier = Modifier) {
    val color = SaqzTheme.colors.primary
    Box(
        modifier = modifier.size(GuestAvatarSize).drawBehind {
            drawCircle(
                color = color,
                radius = size.minDimension / 2 - GuestAvatarStroke.toPx(),
                style = Stroke(
                    width = GuestAvatarStroke.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(GuestAvatarDash.toPx(), GuestAvatarDash.toPx())),
                ),
            )
        },
        contentAlignment = Alignment.Center,
    ) {
        SaqzIcon(SaqzIcons.Users, tint = color, size = GuestButtonIconSize)
    }
}

@Composable
internal fun GameGuestRemoveAction(rowId: String, guest: GameGuestRowUi, onIntent: (GameDetailIntent) -> Unit) {
    if (!guest.canRemove) return
    SaqzButton(
        label = stringResource(Res.string.game_guest_remove),
        onClick = { onIntent(GameDetailIntent.RequestRemoveGuest(rowId)) },
        modifier = Modifier.testTag(GameGuestTags.remove(rowId)),
        variant = SaqzButtonVariant.Ghost,
        size = SaqzButtonSize.Sm,
        contentColor = SaqzTheme.colors.textSecondary,
    )
}

@Composable
internal fun GameGuestAddSheet(guest: GameGuestUi, onIntent: (GameDetailIntent) -> Unit) = SaqzBottomSheet(
    open = true,
    onClose = { onIntent(GameDetailIntent.DismissGuestSheet) },
    modifier = Modifier.testTag(GameGuestTags.Sheet),
    title = stringResource(Res.string.game_guest_sheet_title),
    footer = {
        SaqzButton(
            label = stringResource(if (guest.adding) Res.string.game_guest_submitting else Res.string.game_guest_submit),
            onClick = { onIntent(GameDetailIntent.SubmitGuest) },
            modifier = Modifier.testTag(GameGuestTags.Submit),
            fullWidth = true,
            enabled = guest.canSubmit,
            loading = guest.adding,
        )
    },
) {
    SaqzInput(
        value = guest.name,
        onValueChange = { onIntent(GameDetailIntent.UpdateGuestName(it)) },
        label = stringResource(Res.string.game_guest_name_label),
        placeholder = stringResource(Res.string.game_guest_name_placeholder),
        enabled = !guest.adding,
        modifier = Modifier.testTag(GameGuestTags.Name),
    )
    GuestInfoLine(SaqzIcons.Clock, stringResource(Res.string.game_guest_info_queue))
    guest.feeLabel?.let { GuestInfoLine(SaqzIcons.CreditCard, stringResource(Res.string.game_guest_info_fee, it)) }
    GuestInfoLine(SaqzIcons.Users, stringResource(Res.string.game_guest_info_scope))
    if (guest.addFailed) {
        Text(
            text = stringResource(Res.string.game_guest_add_failed),
            color = SaqzTheme.colors.errorForeground,
            style = SaqzTheme.typography.support,
            modifier = Modifier.testTag(GameGuestTags.AddFailed),
        )
    }
}

@Composable
private fun GuestInfoLine(icon: ImageVector, text: String) = Row(
    horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid),
    verticalAlignment = Alignment.Top,
) {
    SaqzIcon(icon, tint = SaqzTheme.colors.textSecondary, size = GuestButtonIconSize)
    Text(text = text, color = SaqzTheme.colors.textSecondary, style = SaqzTheme.typography.support, modifier = Modifier.weight(1f))
}

@Composable
internal fun GameGuestRemoveSheet(guest: GameGuestUi, removal: GameGuestRemovalUi, onIntent: (GameDetailIntent) -> Unit) =
    SaqzBottomSheet(
        open = true,
        onClose = { onIntent(GameDetailIntent.DismissRemoveGuest) },
        modifier = Modifier.testTag(GameGuestTags.RemoveSheet),
        title = stringResource(Res.string.game_guest_remove_title, removal.name),
        description = when {
            guest.removeFailed -> stringResource(Res.string.game_guest_remove_failed)
            !removal.confirmed -> stringResource(Res.string.game_guest_remove_waitlisted)
            guest.feeLabel != null -> stringResource(Res.string.game_guest_remove_confirmed_fee, guest.feeLabel)
            else -> stringResource(Res.string.game_guest_remove_confirmed)
        },
        splitFooter = {
            SaqzButton(
                stringResource(Res.string.game_guest_remove_keep),
                { onIntent(GameDetailIntent.DismissRemoveGuest) },
                Modifier.weight(1f),
                SaqzButtonVariant.Secondary,
                enabled = !guest.removing,
            )
            SaqzButton(
                stringResource(Res.string.game_guest_remove_confirm),
                { onIntent(GameDetailIntent.ConfirmRemoveGuest) },
                Modifier.weight(1f).testTag(GameGuestTags.RemoveConfirm),
                loading = guest.removing,
                enabled = !guest.removing,
            )
        },
    ) {}

@Composable
internal fun GameGuestNotice(guest: GameGuestUi, onIntent: (GameDetailIntent) -> Unit, modifier: Modifier = Modifier) {
    val name = guest.noticeName ?: return
    SaqzToast(
        visible = true,
        onDismiss = { onIntent(GameDetailIntent.DismissGuestNotice) },
        modifier = modifier.padding(SaqzTheme.metrics.horizontalPadding).testTag(GameGuestTags.Notice),
    ) {
        SaqzToastText(
            text = stringResource(if (guest.noticeJoined) Res.string.game_guest_joined else Res.string.game_guest_removed, name),
        )
    }
}

private val GuestButtonIconSize = 20.dp
private val GuestAvatarSize = 40.dp
private val GuestAvatarStroke = 1.5.dp
private val GuestAvatarDash = 4.dp
```

Se `SaqzButtonVariant.Ghost` não existir, use a variante "texto"/sem borda que o módulo já usa no `GroupOwnDebtBlock.kt`
(`grep -n "SaqzButtonVariant\." UI/../details/GroupOwnDebtBlock.kt`). Se `SaqzIcon(..., size = )` tiver outro nome de
parâmetro, copie a chamada de `GroupOwnDebtBlock.kt`. Se `SaqzBottomSheet` exigir conteúdo não vazio, troque o `{}` final
por `{ Spacer(Modifier.height(0.dp)) }`. Se o tamanho do avatar do `SaqzMemberRow` não for 40.dp, copie o dele
(`grep -n "dp" .../designsystem/SaqzMemberRow.kt | head`). Linha > 140 colunas: quebre sem mudar a lógica.

## Passo 2 · `UI/GameDetailScreen.kt`

2a. Na coluna rolável, logo DEPOIS de `state.attendance?.let { GameDetailAttendance(it) }`:
`GameGuestAction(state.guest, onIntent)`.

2b. `GameDetailConfirmedList(confirmed)` passa a receber também `feeLabel: String?` e `onIntent` (atualize a chamada:
`GameDetailConfirmedList(it, state.guest.feeLabel, onIntent)`). Dentro do `forEachIndexed`, o `SaqzMemberRow(...)` ganha:

```kotlin
                modifier = person.guest?.let { Modifier.testTag(GameGuestTags.row(person.id)) } ?: Modifier,
                meta = person.guest?.metaLabel(feeLabel, confirmed = true) ?: person.position,
                photo = person.guest?.let { { GameGuestAvatar() } },
                trailing = person.guest?.let { guest -> { GameGuestRemoveAction(person.id, guest, onIntent) } },
```
(o `meta = person.position` antigo sai; `name` fica como está.)

2c. No fim do `Box` raiz, junto dos outros `if (...) Sheet`:

```kotlin
        if (state.guest.sheetOpen) GameGuestAddSheet(state.guest, onIntent)
        state.guest.removal?.let { GameGuestRemoveSheet(state.guest, it, onIntent) }
        GameGuestNotice(state.guest, onIntent, Modifier.align(Alignment.BottomCenter))
```

## Passo 3 · `UI/GameWaitlistSection.kt`

3a. `onPromote`: troque por
`onPromote = { onIntent(GameDetailIntent.Promote(person.guest?.hostId ?: person.id, promotionReason, person.guest?.guestSeq ?: 0)) },`
e passe também `onIntent = onIntent` para o `GameWaitlistRow` (novo parâmetro `onIntent: (GameDetailIntent) -> Unit`).

3b. Em `GameWaitlistRow`: o `Row` raiz ganha `.testTag(GameGuestTags.row(person.id))` quando `person.guest != null`
(encadeie com `.then(...)`) e fundo `SaqzTheme.colors.surfaceSoft` nesse caso (se o token tiver outro nome, use o que o
`SaqzCard(soft = true)`/bloco "soft" do módulo usa — `grep -rn "surfaceSoft\|softSurface" mobile/core/design-system/src/commonMain | head -3`).
O `SaqzAvatar(...)` vira `if (person.guest != null) GameGuestAvatar() else SaqzAvatar(...)`. A 2ª linha de texto vira
`person.guest?.metaLabel(state.guest.feeLabel, confirmed = false) ?: stringResource(person.athletePosition.positionResource())`
com cor `primary` quando é convidado (senão `textSecondary`). Depois do botão "Promover" (fora do `if` dele), acrescente
`person.guest?.let { GameGuestRemoveAction(person.id, it, onIntent) }`.

## Passo 4 · `UI/GameGuestPreviewData.kt`

Object `GameGuestPreviewData` derivado de `GameDetailPreviewData.admin.copy(isAdmin = false)` (chame de `base`):
- `guestUi = GameGuestUi(visible = true, enabled = true, feeLabel = "R$ 25,00")`
- `mine = GameGuestRowUi("1", 1, "Bia Souza", isYours = true, canRemove = true)`; `theirs = mine.copy(isYours = false, canRemove = false)`
- estados: `empty` (base + `guest = guestUi`), `sheetEmpty`, `sheetFilled` (`name = "Rafa Moreira"`), `sheetAdding`, `sheetFailed`,
  `inQueue` (fila do base + `GameDetailWaitlistUi("1#1", "Rafa Moreira", 3, null, false, guest = mine)` + `noticeName = "Rafa Moreira"`),
  `twoGuests`, `promoted` (confirmados + `GameDetailConfirmedUi("1#1", "Rafa Moreira", false, "", guest = mine)`),
  `removeWaitlisted`, `removeConfirmed` (`removal` preenchido), `othersView` (`guest = theirs`), `closed`
  (`enabled = false, hint = Closed`), `needAnswer` (`enabled = false, hint = NeedAnswer`), `noFee` (`feeLabel = null`, folha aberta),
  `organizerQueue` (`isAdmin = true`, `promotionMode = MANUAL`, convidado `theirs.copy(canRemove = true)`).

## Passo 5 · testes

`TUI/GameGuestSectionTest.kt` (mesmo arranjo do vizinho `GameDetailScreenTest.kt`: `setContent { SaqzTheme { GameDetailScreen(state, {}, intents::add) } }`):
1. `buttonIsAbsentWhenNotVisible` · 2. `disabledButtonShowsTheReason` (NeedAnswer e Closed; clique não emite intent)
· 3. `tappingTheButtonAsksToOpenTheSheet` · 4. `sheetSubmitIsDisabledUntilTheNameIsValid`
· 5. `typingEmitsUpdateGuestName` · 6. `feeLineOnlyAppearsWhenTheGameHasAFee`
· 7. `myGuestShowsRemoveAndOthersDoNot` · 8. `removeAsksWithTheRowId` (`RequestRemoveGuest("1#1")`)
· 9. `promoteOnAGuestSendsHostIdAndSeq` (`Promote("1", reason, 1)`) · 10. `removeSheetCopyDependsOnStatusAndFee`
· 11. `noticeShowsJoinedAndRemovedCopy` · 12. `everyGuestTagIsUnique` (`onAllNodesWithTag` de cada tag → 1 nó; harness e2e exige).

`SUI/GameGuestScreenshotTest.kt`: copie a armação e o helper `capture` do vizinho `GameDetailScreenshotTest.kt`
(diretório `"game-guest"`, `+h1400dp`), uma cena por estado do passo 4 (15 cenas) — a classe leva
`@Suppress("TooManyFunctions")` como o vizinho (única exceção de `@Suppress`).

## Prints

`G :features:groups:presentation:recordRoborazziAndroidHostTest`, ABRA os 15 PNGs de `screenshots/game-guest/` e confira
contra as pranchas: botão com dica centralizada; avatar tracejado; "Convidado seu" azul; "Tirar" cinza só onde pode;
folha com 3 avisos (2 sem taxa); nada truncado em 390dp. Empurre para `screenshots` em `vul-XXX/` e embuta no PR
lado a lado com `https://raw.githubusercontent.com/bruno-halmeida/saqz/screenshots/mock-convidado/<Prancha>.png`
(se a pasta `mock-convidado/` não existir na branch, embuta só os seus e diga isso).

## Gates

```
G :features:groups:presentation:detektAll
G :features:groups:presentation:iosSimulatorArm64Test --tests "*gamedetail*"
G :features:groups:presentation:iosSimulatorArm64Test
G :features:groups:presentation:recordRoborazziAndroidHostTest
G :compose-app:iosSimulatorArm64Test
G :android-app:testDevDebugUnitTest
G :android-app:compileDevDebugAndroidTestKotlin -Psaqz.e2e=true
```

## PR

Título: `feat(groups): convidado na tela do jogo — botão, folha, linhas e tirar (VUL-XXX)`. Commits PT-BR terminando com
`Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>`; PR ready com `GH_TOKEN=$(gh auth token --user bruno-halmeida)`,
corpo terminando com `🤖 Generated with [Claude Code](https://claude.com/claude-code)`. PARE depois do PR; depois só `CORRECAO:`.
