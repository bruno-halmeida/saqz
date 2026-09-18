# GM2 · Mobile: estado do convidado na tela do jogo + strings (VUL-XXX)

**Onda 2 · depende de: GM1 (na main) · bloqueia: GM3 · paralelo com: GB2**

## Objetivo

`GameDetailContract`/`GameDetailViewModel` aprendem o convidado: linhas do roster com dono, a folha de
adicionar, a confirmação de tirar, as regras A/C/D do `CONTRATO.md`. **Nenhum composable muda** (GM3).
Depois de adicionar/tirar com sucesso a tela faz `load()` inteiro — sem otimismo (ponytail: a fila e as
contagens mudam juntas; recarregar é a versão correta mais curta).

`PRES` = `mobile/features/groups/presentation/src/commonMain/kotlin/br/com/saqz/groups/presentation`
`TPRES` = `mobile/features/groups/presentation/src/commonTest/kotlin/br/com/saqz/groups/presentation`
`RES` = `mobile/features/groups/presentation/src/commonMain/composeResources/values`
`G` = `JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile`

## Arquivos (lista FECHADA)

- EDITAR `PRES/gamedetail/GameDetailContract.kt`, `PRES/gamedetail/GameDetailViewModel.kt`
- NOVO `RES/strings_game_guest.xml`
- EDITAR `TPRES/gamedetail/GameDetailViewModelTest.kt`

Fora da lista = PARE, SendMessage para `main`, aguarde. `when (intent)` exaustivo em outro arquivo que quebre
(ex.: teste de tela): relate — não edite.

## Passo 0

`git fetch origin && git switch -c vul-XXX-convidado-estado origin/main`;
`git grep -c "fun addGuest" -- mobile/features/groups/domain` tem que dar >= 1 (GM1 na main), senão PARE. Commit cedo.

## Passo 1 · `RES/strings_game_guest.xml` (arquivo inteiro)

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="game_guest_add">Levar convidado</string>
    <string name="game_guest_hint_default">Entra na lista de espera. Você responde por ele.</string>
    <string name="game_guest_hint_need_answer">Responda “Vou” para poder levar alguém.</string>
    <string name="game_guest_hint_closed">As confirmações estão encerradas. Fale com o gestor.</string>
    <string name="game_guest_sheet_title">Levar convidado</string>
    <string name="game_guest_name_label">Nome do convidado</string>
    <string name="game_guest_name_placeholder">Como a galera vai chamar</string>
    <string name="game_guest_info_queue">Entra no fim da lista de espera e sobe quando abrir vaga.</string>
    <string name="game_guest_info_fee">Se ele entrar no jogo, a taxa de %1$s vem para você.</string>
    <string name="game_guest_info_scope">Não vira membro do grupo. Vale só para este jogo.</string>
    <string name="game_guest_submit">Adicionar à lista de espera</string>
    <string name="game_guest_submitting">Adicionando…</string>
    <string name="game_guest_add_failed">Não foi possível adicionar. Tente de novo.</string>
    <string name="game_guest_yours">Convidado seu</string>
    <string name="game_guest_yours_fee">Convidado seu · %1$s na sua conta</string>
    <string name="game_guest_of">Convidado de %1$s</string>
    <string name="game_guest_remove">Tirar</string>
    <string name="game_guest_remove_title">Tirar %1$s do jogo?</string>
    <string name="game_guest_remove_waitlisted">Ele sai da lista de espera. Nada é cobrado.</string>
    <string name="game_guest_remove_confirmed">Ele sai dos confirmados e a vaga vai para o próximo da lista.</string>
    <string name="game_guest_remove_confirmed_fee">Ele sai dos confirmados, a vaga vai para o próximo da lista e a cobrança de %1$s em seu nome é cancelada.</string>
    <string name="game_guest_remove_keep">Manter</string>
    <string name="game_guest_remove_confirm">Tirar</string>
    <string name="game_guest_remove_failed">Não foi possível tirar. Tente de novo.</string>
    <string name="game_guest_joined">%1$s entrou na lista de espera.</string>
    <string name="game_guest_removed">%1$s saiu do jogo.</string>
</resources>
```
(As aspas escapadas seguem o padrão dos outros `strings_*.xml`; se o módulo usa `&quot;` ou aspas curvas em
string existente com aspas — `grep -n '"Vou"\|&quot;' RES/*.xml` — copie o MESMO padrão.)

## Passo 2 · `GameDetailContract.kt`

2a. `GameDetailState` ganha, no fim: `val guest: GameGuestUi = GameGuestUi(),`.

2b. `GameDetailConfirmedUi` e `GameDetailWaitlistUi` ganham, no fim, o MESMO campo:

```kotlin
    /** `null` = membro. Convidado: de quem é e o que quem olha pode fazer com ele. */
    val guest: GameGuestRowUi? = null,
```
O `id` das duas passa a ser o `rowKey` do domínio (passo 3).

2c. Tipos novos, depois de `GameDetailWaitlistUi`:

```kotlin
@Immutable
data class GameGuestRowUi(
    val hostId: String,
    val guestSeq: Int,
    val hostName: String,
    /** O convidado é de quem está olhando. */
    val isYours: Boolean,
    /** Anfitrião com confirmações abertas, ou gestor com jogo publicado. */
    val canRemove: Boolean,
)

enum class GameGuestHint { Default, NeedAnswer, Closed }

@Immutable
data class GameGuestRemovalUi(
    val rowId: String,
    val hostId: String,
    val guestSeq: Int,
    val name: String,
    val confirmed: Boolean,
)

@Immutable
data class GameGuestUi(
    /** Botão aparece só com jogo publicado e quem olha sendo membro que joga (tem `memberId`). */
    val visible: Boolean = false,
    val enabled: Boolean = false,
    val hint: GameGuestHint = GameGuestHint.Default,
    /** "R$ 25,00" quando o jogo tem taxa; `null` = a folha não fala de cobrança. */
    val feeLabel: String? = null,
    val sheetOpen: Boolean = false,
    val name: String = "",
    val adding: Boolean = false,
    val addFailed: Boolean = false,
    val removal: GameGuestRemovalUi? = null,
    val removing: Boolean = false,
    val removeFailed: Boolean = false,
    /** Nome de quem acabou de entrar (`joined = true`) ou sair; a UI mostra o toast e manda `DismissGuestNotice`. */
    val noticeName: String? = null,
    val noticeJoined: Boolean = true,
) {
    val canSubmit: Boolean get() = name.trim().length in MIN_NAME..MAX_NAME && !adding

    private companion object {
        const val MIN_NAME = 2
        const val MAX_NAME = 80
    }
}
```

2d. `GameDetailIntent` ganha:

```kotlin
    data object OpenGuestSheet : GameDetailIntent
    data class UpdateGuestName(val value: String) : GameDetailIntent
    data object SubmitGuest : GameDetailIntent
    data object DismissGuestSheet : GameDetailIntent
    data class RequestRemoveGuest(val rowId: String) : GameDetailIntent
    data object ConfirmRemoveGuest : GameDetailIntent
    data object DismissRemoveGuest : GameDetailIntent
    data object DismissGuestNotice : GameDetailIntent
```
e `Promote` vira `data class Promote(val memberId: String, val reason: String, val guestSeq: Int = 0)`.

## Passo 3 · `GameDetailViewModel.kt`

3a. Campo novo ao lado de `versionToken`: `private var selfId: String? = null`.

3b. `onIntent`: acrescente os ramos (antes do `}` do `when`):

```kotlin
            GameDetailIntent.OpenGuestSheet -> if (state.value.guest.enabled) {
                updateGuest { it.copy(sheetOpen = true, name = "", addFailed = false) }
            }
            is GameDetailIntent.UpdateGuestName -> updateGuest { it.copy(name = intent.value.take(MAX_GUEST_NAME), addFailed = false) }
            GameDetailIntent.SubmitGuest -> submitGuest()
            GameDetailIntent.DismissGuestSheet -> if (!state.value.guest.adding) updateGuest { it.copy(sheetOpen = false, addFailed = false) }
            is GameDetailIntent.RequestRemoveGuest -> requestRemoveGuest(intent.rowId)
            GameDetailIntent.ConfirmRemoveGuest -> confirmRemoveGuest()
            GameDetailIntent.DismissRemoveGuest -> if (!state.value.guest.removing) updateGuest { it.copy(removal = null, removeFailed = false) }
            GameDetailIntent.DismissGuestNotice -> updateGuest { it.copy(noticeName = null) }
```
e troque o ramo do `Promote` por `is GameDetailIntent.Promote -> promote(intent.memberId, intent.reason, intent.guestSeq)`.

3c. `promote(memberId, reason, guestSeq: Int)`: o id da LINHA é `rowId = if (guestSeq > 0) "$memberId#$guestSeq" else memberId`;
troque as três comparações `it.id == memberId` / `member.id == memberId` por `rowId`, `promotingMemberId = rowId`, e o comando por
`AttendancePromotionCommand(Uuid.random().toString(), memberId, reason, guestSeq)`.

3d. Em `loadAttendance`, no ramo de sucesso, ANTES do `update {`:

```kotlin
                selfId = detail.ownAttendance?.memberId
                val open = game.toHeader().confirmationOpen
```
e dentro do `it.copy(...)`: troque os dois maps por
`confirmedRoster = roster.confirmed.map { member -> member.toConfirmed(athletes[member.memberId], isAdmin, open) },`
`waitlist = roster.waitlisted.map { member -> member.toWaitlist(athletes[member.memberId], isAdmin, open) },`
e acrescente `guest = guestUi(game, detail, open, it.guest),`.

Em `refreshRoster`, os dois maps ganham os mesmos argumentos, usando
`state.value.isAdmin` e `state.value.header?.confirmationOpen == true`.

3e. Mapeadores (substitua os dois existentes e acrescente os novos, perto deles):

```kotlin
    private fun AttendanceRosterMember.toConfirmed(athlete: AthleteRosterEntry?, isAdmin: Boolean, open: Boolean) =
        GameDetailConfirmedUi(
            id = rowKey,
            name = displayName,
            isYou = !isGuest && memberId == selfId,
            position = if (isGuest) "" else athlete?.position?.name.orEmpty(),
            guest = guestRow(isAdmin, open),
        )

    private fun AttendanceRosterMember.toWaitlist(athlete: AthleteRosterEntry?, isAdmin: Boolean, open: Boolean) =
        GameDetailWaitlistUi(
            id = rowKey,
            name = displayName,
            queuePosition = waitlistPosition,
            athletePosition = if (isGuest) null else athlete?.position,
            isMensalista = !isGuest && athlete?.membershipType == AthleteMembershipType.MENSALISTA,
            guest = guestRow(isAdmin, open),
        )

    private fun AttendanceRosterMember.guestRow(isAdmin: Boolean, open: Boolean): GameGuestRowUi? {
        if (!isGuest) return null
        val yours = memberId == selfId
        val published = state.value.header?.statusTone != GameDetailStatusTone.Cancelled &&
            state.value.header?.statusTone != GameDetailStatusTone.Completed
        return GameGuestRowUi(
            hostId = memberId,
            guestSeq = guestSeq,
            hostName = hostDisplayName.orEmpty(),
            isYours = yours,
            canRemove = (yours && open) || (isAdmin && published),
        )
    }

    /** Regras A e C: só quem já disse "Vou" (confirmado ou na fila), e só com confirmações abertas. */
    private fun guestUi(game: Game, detail: AttendanceDetail, open: Boolean, previous: GameGuestUi): GameGuestUi {
        val going = detail.ownAttendance?.status == AttendanceStatus.Confirmed ||
            detail.ownAttendance?.status == AttendanceStatus.Waitlisted
        return previous.copy(
            visible = game.status == GameStatus.Published,
            enabled = open && going,
            hint = when {
                !open -> GameGuestHint.Closed
                !going -> GameGuestHint.NeedAnswer
                else -> GameGuestHint.Default
            },
            feeLabel = game.gameFeeCents?.let(::formatBrl),
            sheetOpen = false,
            adding = false,
            removal = null,
            removing = false,
        )
    }

    private fun updateGuest(block: (GameGuestUi) -> GameGuestUi) = update { it.copy(guest = block(it.guest)) }
```
ATENÇÃO à ordem no `loadAttendance`: `guestRow` lê `state.value.header`; como o header novo só entra no
`update`, calcule `val tone = game.toHeader().statusTone` antes e passe-o — troque a assinatura para
`guestRow(isAdmin: Boolean, open: Boolean, tone: GameDetailStatusTone?)` e o `published` para
`tone != GameDetailStatusTone.Cancelled && tone != GameDetailStatusTone.Completed`; em `refreshRoster` passe `state.value.header?.statusTone`.
(`toConfirmed`/`toWaitlist` repassam o `tone`.) Import: `br.com.saqz.core.common.formatting.formatBrl`.

3f. Ações (perto de `promote`):

```kotlin
    private fun submitGuest() {
        val guest = state.value.guest
        if (!guest.enabled || !guest.canSubmit) return
        val name = guest.name.trim()
        val loadAtStart = loadGeneration
        updateGuest { it.copy(adding = true, addFailed = false) }
        viewModelScope.launch {
            val result = attendanceGateway.addGuest(GroupId(groupId), gameId, AddGuestCommand(Uuid.random().toString(), name))
            if (loadAtStart != loadGeneration) return@launch
            when (result) {
                is SaqzResult.Success -> {
                    updateGuest { it.copy(sheetOpen = false, adding = false, name = "", noticeName = name, noticeJoined = true) }
                    load()
                }
                is SaqzResult.Failure -> updateGuest { it.copy(adding = false, addFailed = true) }
            }
        }
    }

    private fun requestRemoveGuest(rowId: String) {
        val confirmed = state.value.confirmedRoster.firstOrNull { it.id == rowId }
        val waiting = state.value.waitlist.firstOrNull { it.id == rowId }
        val row = confirmed?.guest ?: waiting?.guest ?: return
        if (!row.canRemove) return
        updateGuest {
            it.copy(
                removal = GameGuestRemovalUi(rowId, row.hostId, row.guestSeq, confirmed?.name ?: waiting?.name.orEmpty(), confirmed != null),
                removeFailed = false,
            )
        }
    }

    private fun confirmRemoveGuest() {
        val removal = state.value.guest.removal ?: return
        if (state.value.guest.removing) return
        val loadAtStart = loadGeneration
        updateGuest { it.copy(removing = true, removeFailed = false) }
        viewModelScope.launch {
            val result = attendanceGateway.removeGuest(GroupId(groupId), gameId, removal.hostId, removal.guestSeq)
            if (loadAtStart != loadGeneration) return@launch
            when (result) {
                is SaqzResult.Success -> {
                    updateGuest { it.copy(removal = null, removing = false, noticeName = removal.name, noticeJoined = false) }
                    load()
                }
                is SaqzResult.Failure -> updateGuest { it.copy(removing = false, removeFailed = true) }
            }
        }
    }
```
`private const val MAX_GUEST_NAME = 80` junto das outras constantes do arquivo. O `guestUi(...)` preserva
`noticeName` de propósito (o `load()` que vem depois do sucesso não pode apagar o toast).

## Passo 4 · testes (`GameDetailViewModelTest.kt`, no estilo e com os fakes que já estão no arquivo)

1. `guestButtonIsHiddenUntilTheGameIsPublished` · 2. `guestButtonNeedsTheViewersOwnAnswer` (sem resposta → `NeedAnswer`, desabilitado; `Declined` idem)
· 3. `guestButtonClosesWithTheDeadline` (`Closed`) · 4. `waitlistedHostCanBringAGuest`
· 5. `submitGuestCallsTheGatewayReloadsAndAnnounces` (nome com espaços nas pontas é enviado aparado; `noticeName`, `noticeJoined`)
· 6. `submitGuestFailureKeepsTheSheetAndTheName` · 7. `nameShorterThanTwoLettersCannotBeSubmitted` (gateway não é chamado)
· 8. `guestRowsCarryHostAndOwnership` (dois convidados do MESMO anfitrião têm `id` diferentes; `isYours`; `hostName`)
· 9. `onlyHostAndOrganizerCanRemove` (outro membro: `canRemove = false` e `RequestRemoveGuest` não abre nada)
· 10. `hostCannotRemoveAfterTheDeadlineButOrganizerCan` · 11. `confirmRemoveCallsTheGatewayWithHostAndSeq`
· 12. `removeFailureKeepsTheConfirmationOpen` · 13. `promoteGuestSendsGuestSeqAndUsesTheRowId`
· 14. `staleGuestResultAfterAReloadIsIgnored` (dispare `Retry` antes de o fake responder).

Se o fake de `AttendanceGateway` do arquivo não tiver como programar `addGuest`/`removeGuest`, acrescente a ELE
(no próprio arquivo de teste) dois campos de resultado e os dois overrides — é o único fake que você edita.

## Gates

```
G :features:groups:presentation:detektAll
G :features:groups:presentation:iosSimulatorArm64Test --tests "*GameDetailViewModelTest*"
G :features:groups:presentation:iosSimulatorArm64Test
G :features:groups:presentation:recordRoborazziAndroidHostTest     # nenhum PNG pode mudar
G :compose-app:iosSimulatorArm64Test
G :android-app:testDevDebugUnitTest
```
`GameDetailViewModel` já tem `@Suppress("LargeClass")`: nenhum `@Suppress` novo. Se o detekt acusar
`CyclomaticComplexMethod` no `onIntent`, extraia os 8 ramos novos para
`private fun onGuestIntent(intent: GameDetailIntent): Boolean` (devolve `true` se tratou) e chame-o no `else`.

## PR

Título: `feat(groups): estado do convidado na tela do jogo (VUL-XXX)`. Sem prints (UI é a GM3). Commits PT-BR
terminando com `Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>`; PR ready com
`GH_TOKEN=$(gh auth token --user bruno-halmeida)`, corpo terminando com
`🤖 Generated with [Claude Code](https://claude.com/claude-code)`. PARE depois do PR; depois só `CORRECAO:`.
