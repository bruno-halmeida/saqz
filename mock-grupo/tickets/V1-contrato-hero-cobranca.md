# V1 · Contrato do hero e da cobrança — rótulos do jogo, espera, mapa, toast e dívida

**Onda 1 · depende de: nada · bloqueia: V2, C1, C3 · paralelo com: S, A, B, T**

## Objetivo

Deixar o `GroupDetailsViewModel` entregando, já formatado, tudo que o hero azul (C1) e o ticket de cobrança (C3) vão desenhar — **sem mexer em nenhum composable**. A tela continua igual depois deste PR: só o estado ganha campos e o ViewModel ganha quatro regras que hoje só a Início tem.

1. Rótulos do próximo jogo no molde da Início: `"Terça, 19h30"`, `"4 de agosto · CERET — Quadra 2"`, a frase do prazo, o prazo curto e o rótulo do sino.
2. Endereço **do jogo** no estado, e o mapa passa a abrir esse endereço (hoje abre a quadra padrão do grupo, que pode ser outra).
3. Lista de espera completa no estado (tipo + fila com a linha "Você") e as duas guardas da Início: quem já está na fila não pode tocar "Vou" de novo, e o otimista prevê a fila quando o jogo está lotado ou o avulso joga sob prioridade de mensalista.
4. `toast` (resposta confirmada / recusada / fila / chave copiada) e `pixCopied` por 2 s com contador monotônico.
5. `OwnChargesUi.debt`: o resumo da dívida no molde do ticket da Início (competência e vencimento da pendência mais antiga, soma das pendentes, contagem, recebedor).

## Fora do escopo

- **Nenhum arquivo em `ui/`** (nem `ui/details`, nem `ui/home`, nem `ui/components`): o ticket T é dono de `ui/details` e o B de `ui/home`/`ui/components`, e rodam em paralelo com este.
- **Não tocar em `home/HomeViewModel.kt` nem em `home/HomeContract.kt`.** As funções de rótulo nascem em arquivo novo; a Início passa a usá-las no ticket H (onda 3). Até lá a duplicação é deliberada.
- Nenhuma string nova: tudo aqui reaproveita chaves que já existem (`home_*`, `own_charges_*`).
- Nada de agenda, "Esperando você", roster de atletas, pedidos de entrada, saldo do caixa: é o V2.
- Não paralelizar a carga nem rebaixar falhas de `roster`/`ownProfile`: fora deste projeto.

## Arquivos

Base: `PRES = mobile/features/groups/presentation/src/commonMain/kotlin/br/com/saqz/groups/presentation` e `TEST = mobile/features/groups/presentation/src/commonTest/kotlin/br/com/saqz/groups/presentation`.

| Ação | Arquivo |
|---|---|
| criar | `PRES/game/GameLabels.kt` |
| criar | `PRES/details/GroupOwnDebt.kt` |
| editar | `PRES/details/GroupDetailsContract.kt` |
| editar | `PRES/details/GroupDetailsViewModel.kt` |
| criar | `TEST/game/GameLabelsTest.kt` |
| criar | `TEST/details/GroupOwnDebtTest.kt` |
| editar | `TEST/details/GroupDetailsViewModelTest.kt` (só acrescentar testes no fim da classe, antes de `private fun ownCharge(`) |

**Tocar em arquivo fora desta lista = parar e avisar o orquestrador.**

## Contrato exportado (os tickets V2, C1 e C3 são escritos contra isto)

```kotlin
// GroupDetailsState — campos novos (todos com default; ninguém quebra)
val waitlist: GroupWaitlistUi? = null
val toast: GroupDetailsToast? = null
val pixCopied: Boolean = false

// NextGameUi — campos novos
val display: String = ""        // "Terça, 19h30"
val meta: String = ""           // "4 de agosto · CERET — Quadra 2"
val address: String = ""        // endereço DO JOGO; vazio esconde a linha e o mapa
val deadlineLine: String = ""   // "As confirmações encerram hoje às 12h00." (só a frase de prazo ABERTO)
val deadlineShort: String = ""  // "Encerra 04/08 · 12h00"
val bellLabel: String = ""      // "Avisamos você se abrir vaga até 12h00 de 04/08."

@Immutable data class GroupWaitlistUi(val kind: HomeWaitlistKind, val rows: List<HomeWaitlistRowUi> = emptyList())
enum class GroupDetailsToast { Confirmed, Declined, Waitlisted, PixCopied }

// OwnChargesUi — campo novo
val debt: GroupOwnDebtUi? = null
@Immutable data class GroupOwnDebtUi(
    val eyebrow: String, val totalLabel: String, val dueLabel: String,
    val overdue: Boolean, val countLabel: String? = null, val receiverLabel: String? = null,
)

// GroupDetailsIntent — novo
data object DismissToast : GroupDetailsIntent
```

Regras que a UI pode assumir:
- Prazo encerrado: a UI mostra `game_response_deadline_closed` quando `nextGame.confirmationOpen == false`; `deadlineLine` só carrega a frase de prazo aberto.
- `waitlist != null` ⇔ `memberResponse?.status == Waitlisted`.
- `ownCharges.debt != null` ⇔ `ownCharges.pending.isNotEmpty()`.
- Contagens ("9 de 12 confirmados", "Restam 3 vagas") continuam sendo formatadas no composable a partir de `confirmedCount`/`capacity`/`availableSpots`, como hoje.

## Passo a passo

### 1. Criar `PRES/game/GameLabels.kt` (arquivo completo)

```kotlin
package br.com.saqz.groups.presentation.game

import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.home_admin_hero_deadline
import br.com.saqz.groups.resources.home_date
import br.com.saqz.groups.resources.home_deadline_date
import br.com.saqz.groups.resources.home_deadline_today
import br.com.saqz.groups.resources.home_deadline_tomorrow
import br.com.saqz.groups.resources.home_game_display
import br.com.saqz.groups.resources.home_game_meta
import br.com.saqz.groups.resources.home_month_april
import br.com.saqz.groups.resources.home_month_april_long
import br.com.saqz.groups.resources.home_month_august
import br.com.saqz.groups.resources.home_month_august_long
import br.com.saqz.groups.resources.home_month_december
import br.com.saqz.groups.resources.home_month_december_long
import br.com.saqz.groups.resources.home_month_february
import br.com.saqz.groups.resources.home_month_february_long
import br.com.saqz.groups.resources.home_month_january
import br.com.saqz.groups.resources.home_month_january_long
import br.com.saqz.groups.resources.home_month_july
import br.com.saqz.groups.resources.home_month_july_long
import br.com.saqz.groups.resources.home_month_june
import br.com.saqz.groups.resources.home_month_june_long
import br.com.saqz.groups.resources.home_month_march
import br.com.saqz.groups.resources.home_month_march_long
import br.com.saqz.groups.resources.home_month_may
import br.com.saqz.groups.resources.home_month_may_long
import br.com.saqz.groups.resources.home_month_november
import br.com.saqz.groups.resources.home_month_november_long
import br.com.saqz.groups.resources.home_month_october
import br.com.saqz.groups.resources.home_month_october_long
import br.com.saqz.groups.resources.home_month_september
import br.com.saqz.groups.resources.home_month_september_long
import br.com.saqz.groups.resources.home_time
import br.com.saqz.groups.resources.home_waitlist_reserva_bell
import br.com.saqz.groups.resources.home_weekday_friday
import br.com.saqz.groups.resources.home_weekday_monday
import br.com.saqz.groups.resources.home_weekday_saturday
import br.com.saqz.groups.resources.home_weekday_sunday
import br.com.saqz.groups.resources.home_weekday_thursday
import br.com.saqz.groups.resources.home_weekday_tuesday
import br.com.saqz.groups.resources.home_weekday_wednesday
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

/**
 * Os rótulos de data e prazo de um jogo, no vocabulário da Início (VUL-218): "Terça, 19h30",
 * "4 de agosto · CERET", "As confirmações encerram hoje às 12h00.". Nasceram privados no
 * `HomeViewModel`; o detalhe do grupo é o segundo uso, então moram aqui para as duas telas
 * dizerem a mesma coisa com as mesmas chaves `home_*`.
 *
 * Tudo recebe data/hora JÁ convertida para o fuso do jogo: quem chama resolve o fuso com
 * [gameTimeZone] e converte o instante uma vez.
 */
internal fun gameTimeZone(zoneId: String): TimeZone =
    runCatching { TimeZone.of(zoneId) }.getOrDefault(TimeZone.UTC)

private fun Int.twoDigits(): String = toString().padStart(2, '0')

/** "19h30" */
internal suspend fun LocalDateTime.gameTimeLabel(): String =
    getString(Res.string.home_time, hour.twoDigits(), minute.twoDigits())

/** "04/08" */
internal suspend fun LocalDate.gameDateLabel(): String =
    getString(Res.string.home_date, day.twoDigits(), (month.ordinal + 1).twoDigits())

/** "Terça" — a chave é minúscula ("terça"); quem capitaliza é este rótulo. */
internal suspend fun DayOfWeek.gameWeekdayLabel(): String =
    getString(longWeekdayResource()).replaceFirstChar { it.titlecase() }

/** "AGO" — [month] de 1 a 12. Fora disso cai em dezembro, como as tabelas da Início. */
internal suspend fun gameShortMonthLabel(month: Int): String = getString(month.shortMonthResource())

/** "agosto" — minúsculo, como a chave; [month] de 1 a 12. */
private suspend fun gameLongMonthLabel(month: Int): String = getString(month.longMonthResource())

/** "Terça, 19h30" */
internal suspend fun LocalDateTime.gameHeroDisplay(): String =
    getString(Res.string.home_game_display, date.dayOfWeek.gameWeekdayLabel(), gameTimeLabel())

/** "4 de agosto · CERET — Quadra 2" */
internal suspend fun LocalDateTime.gameHeroMeta(place: String): String =
    getString(Res.string.home_game_meta, day.toString(), gameLongMonthLabel(month.ordinal + 1), place)

/** A frase do prazo ABERTO, relativa a [today] (hoje no fuso do jogo). */
internal suspend fun LocalDateTime.gameDeadlineSentence(today: LocalDate): String = when (date) {
    today -> getString(Res.string.home_deadline_today, gameTimeLabel())
    today.plus(DatePeriod(days = 1)) -> getString(Res.string.home_deadline_tomorrow, gameTimeLabel())
    else -> getString(Res.string.home_deadline_date, date.gameDateLabel(), gameTimeLabel())
}

/** "Encerra 04/08 · 12h00" */
internal suspend fun LocalDateTime.gameDeadlineShort(): String =
    getString(Res.string.home_admin_hero_deadline, date.gameDateLabel(), gameTimeLabel())

/** "Avisamos você se abrir vaga até 12h00 de 04/08." */
internal suspend fun LocalDateTime.gameBellLabel(): String =
    getString(Res.string.home_waitlist_reserva_bell, gameTimeLabel(), date.gameDateLabel())

private fun DayOfWeek.longWeekdayResource(): StringResource = when (this) {
    DayOfWeek.MONDAY -> Res.string.home_weekday_monday
    DayOfWeek.TUESDAY -> Res.string.home_weekday_tuesday
    DayOfWeek.WEDNESDAY -> Res.string.home_weekday_wednesday
    DayOfWeek.THURSDAY -> Res.string.home_weekday_thursday
    DayOfWeek.FRIDAY -> Res.string.home_weekday_friday
    DayOfWeek.SATURDAY -> Res.string.home_weekday_saturday
    DayOfWeek.SUNDAY -> Res.string.home_weekday_sunday
}

private fun Int.shortMonthResource(): StringResource = when (this) {
    1 -> Res.string.home_month_january
    2 -> Res.string.home_month_february
    3 -> Res.string.home_month_march
    4 -> Res.string.home_month_april
    5 -> Res.string.home_month_may
    6 -> Res.string.home_month_june
    7 -> Res.string.home_month_july
    8 -> Res.string.home_month_august
    9 -> Res.string.home_month_september
    10 -> Res.string.home_month_october
    11 -> Res.string.home_month_november
    else -> Res.string.home_month_december
}

private fun Int.longMonthResource(): StringResource = when (this) {
    1 -> Res.string.home_month_january_long
    2 -> Res.string.home_month_february_long
    3 -> Res.string.home_month_march_long
    4 -> Res.string.home_month_april_long
    5 -> Res.string.home_month_may_long
    6 -> Res.string.home_month_june_long
    7 -> Res.string.home_month_july_long
    8 -> Res.string.home_month_august_long
    9 -> Res.string.home_month_september_long
    10 -> Res.string.home_month_october_long
    11 -> Res.string.home_month_november_long
    else -> Res.string.home_month_december_long
}
```

### 2. Criar `PRES/details/GroupOwnDebt.kt` (arquivo completo)

```kotlin
package br.com.saqz.groups.presentation.details

import br.com.saqz.core.common.formatting.formatBrl
import br.com.saqz.groups.domain.finance.Charge
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.home_own_charge_pix_receiver
import br.com.saqz.groups.resources.home_own_charges_count
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.getString

/**
 * O resumo da dívida do próprio usuário neste grupo, no molde do ticket da Início (VUL-220):
 * competência e vencimento são os da pendência MAIS ANTIGA, o valor é a soma das pendentes.
 *
 * [pending] e [pendingUi] chegam na mesma ordem (vencimento crescente) — a primeira de cada
 * é a mesma cobrança. Sem pendência não há dívida: devolve `null` e o ticket não existe.
 */
internal suspend fun groupOwnDebt(
    pending: List<Charge>,
    pendingUi: List<OwnChargeUi>,
    today: LocalDate,
    pixLabel: String?,
    hasPixKey: Boolean,
): GroupOwnDebtUi? {
    val oldest = pending.firstOrNull()
    val oldestUi = pendingUi.firstOrNull()
    if (oldest == null || oldestUi == null) return null
    return GroupOwnDebtUi(
        eyebrow = oldestUi.title,
        totalLabel = formatBrl(pending.sumOf { it.amountCents }),
        dueLabel = oldestUi.dueLabel,
        overdue = oldest.dueDate < today.toString(),
        countLabel = pending.size.takeIf { it > 1 }?.let { getString(Res.string.home_own_charges_count, it) },
        receiverLabel = pixLabel?.trim()?.takeIf { it.isNotEmpty() && hasPixKey }
            ?.let { getString(Res.string.home_own_charge_pix_receiver, it) },
    )
}
```

### 3. Editar `PRES/details/GroupDetailsContract.kt`

3.1. Nos imports, logo depois de `import br.com.saqz.groups.presentation.GroupUiError`, acrescentar:

```kotlin
import br.com.saqz.groups.presentation.home.HomeWaitlistKind
import br.com.saqz.groups.presentation.home.HomeWaitlistRowUi
```

3.2. Em `data class GroupDetailsState`, logo depois da linha `    val athleteShareFailed: Boolean = false,`, acrescentar:

```kotlin
    /** Espera do próprio usuário no próximo jogo; `null` fora da lista de espera. */
    val waitlist: GroupWaitlistUi? = null,
    /** Retorno de uma ação recém-concluída; some em [GroupDetailsIntent.DismissToast]. */
    val toast: GroupDetailsToast? = null,
    /** A chave Pix acabou de ser copiada: o ticket troca o botão por "Chave copiada" por 2 s. */
    val pixCopied: Boolean = false,
```

3.3. Em `data class NextGameUi`, logo depois da linha `    val hasGameFee: Boolean = false,`, acrescentar:

```kotlin
    /** "Terça, 19h30" — título do hero. */
    val display: String = "",
    /** "4 de agosto · CERET — Quadra 2" — linha abaixo do título. */
    val meta: String = "",
    /** Endereço DO JOGO, não o da quadra padrão do grupo. Vazio esconde a linha e o mapa. */
    val address: String = "",
    /** A frase do prazo ABERTO. Encerrado, a tela usa `game_response_deadline_closed`. */
    val deadlineLine: String = "",
    /** "Encerra 04/08 · 12h00" — a meta da linha de quórum do gestor. */
    val deadlineShort: String = "",
    /** "Avisamos você se abrir vaga até 12h00 de 04/08." — o card do sino da reserva. */
    val bellLabel: String = "",
```

3.4. Logo depois da linha `enum class GroupDetailsResponseStatus { Confirmed, Declined, Waitlisted }`, acrescentar:

```kotlin

/**
 * A espera do próprio usuário, com as mesmas peças da Início: [kind] escolhe o texto
 * (reserva × lista do avulso) e [rows] é a fila, com `isSelf` na linha de quem olha. Aqui o
 * casamento é por `memberId` — o roster do detalhe traz o id, o da Início não.
 */
@Immutable
data class GroupWaitlistUi(
    val kind: HomeWaitlistKind,
    val rows: List<HomeWaitlistRowUi> = emptyList(),
)

enum class GroupDetailsToast { Confirmed, Declined, Waitlisted, PixCopied }
```

3.5. Em `data class OwnChargesUi`, logo depois da linha `    val pix: PixUi? = null,`, acrescentar:

```kotlin
    /** O resumo do ticket: só existe com pendência. Ver [GroupOwnDebtUi]. */
    val debt: GroupOwnDebtUi? = null,
```

3.6. Logo depois da linha `enum class OwnChargeStatusUi { Pending, Paid, Waived, Cancelled }`, acrescentar:

```kotlin

/**
 * O ticket de cobrança no molde da Início: [eyebrow] e [dueLabel] são da pendência mais
 * antiga, [totalLabel] é a soma. [countLabel] só existe com mais de uma pendência.
 * [receiverLabel] ("Pix de Lucas Prado") some sem rótulo do recebedor ou sem chave.
 */
@Immutable
data class GroupOwnDebtUi(
    val eyebrow: String,
    val totalLabel: String,
    val dueLabel: String,
    val overdue: Boolean,
    val countLabel: String? = null,
    val receiverLabel: String? = null,
)
```

3.7. Em `sealed interface GroupDetailsIntent`, logo depois da linha `    data object CopyPix : GroupDetailsIntent`, acrescentar:

```kotlin

    data object DismissToast : GroupDetailsIntent
```

### 4. Editar `PRES/details/GroupDetailsViewModel.kt`

4.1. Imports — acrescentar, respeitando a ordem que o arquivo já usa (sem reordenar o que existe):

```kotlin
import br.com.saqz.groups.presentation.game.gameBellLabel
import br.com.saqz.groups.presentation.game.gameDeadlineSentence
import br.com.saqz.groups.presentation.game.gameDeadlineShort
import br.com.saqz.groups.presentation.game.gameHeroDisplay
import br.com.saqz.groups.presentation.game.gameHeroMeta
import br.com.saqz.groups.presentation.game.gameTimeZone
import br.com.saqz.groups.presentation.home.HomeWaitlistKind
import br.com.saqz.groups.presentation.home.HomeWaitlistRowUi
import kotlinx.coroutines.delay
```

4.2. Logo depois da linha `    private var ownChargesGeneration = 0L`, acrescentar:

```kotlin
    private var pixCopiedGeneration = 0L
```

4.3. Em `onIntent`, logo depois da linha `            GroupDetailsIntent.CopyPix -> copyPix()`, acrescentar:

```kotlin
            GroupDetailsIntent.DismissToast -> update { it.copy(toast = null) }
```

4.4. Substituir a função `openMap` inteira por:

```kotlin
    // O mapa abre o endereço DO JOGO: a quadra padrão do grupo pode ser outra. Sem jogo (ou
    // com jogo sem endereço) vale a quadra padrão, que é o que a tela mostra nesse caso.
    private fun openMap() {
        val current = state.value
        val address = current.nextGame?.address?.takeIf(String::isNotBlank)
            ?: current.venue?.address?.takeIf(String::isNotBlank)
        update { it.copy(mapFailed = address == null) }
        if (address != null) emit(GroupDetailsEffect.OpenMap(address))
    }
```

4.5. Substituir a função `copyPix` inteira por:

```kotlin
    private fun copyPix() {
        val pix = state.value.ownCharges?.pix ?: return
        emit(GroupDetailsEffect.CopyPix(pix.key))
        // Contador monotônico, não igualdade de valor: dois toques seguidos não podem deixar
        // o delay do primeiro apagar o estado do segundo (mesma regra da Início, VUL-220).
        val generation = ++pixCopiedGeneration
        update { it.copy(pixCopied = true, toast = GroupDetailsToast.PixCopied) }
        viewModelScope.launch {
            delay(PIX_COPIED_DWELL_MILLIS)
            if (generation == pixCopiedGeneration) update { it.copy(pixCopied = false) }
        }
    }
```

4.6. Em `load()`, dentro do primeiro `update { it.copy(`, trocar a linha

```kotlin
            notifying = false, notificationFailed = false, notifiedCount = null,
```

por

```kotlin
            notifying = false, notificationFailed = false, notifiedCount = null,
            pixCopied = false,
```

e, logo depois da linha `        ownChargesGeneration++` (no topo de `load()`), acrescentar:

```kotlin
        pixCopiedGeneration++
```

4.7. Em `toOwnCharges`, substituir do `val pending = filter {` até o fim da função por:

```kotlin
        val pendingCharges = filter { it.status == ChargeStatus.Pending }.sortedBy { it.dueDate }
        val pending = pendingCharges.map { it.toOwnCharge(today) }
        // ponytail: histórico cortado nas 6 mais recentes — a lista não pagina e isto é um
        // `Column` não-lazy dentro da tela mais aberta do app; um mensalista de dois anos
        // comporia 24 linhas toda vez. Quem quiser tudo vai pelo extrato (VUL-176).
        val history = filterNot { it.status == ChargeStatus.Pending }
            .sortedByDescending { it.dueDate }
            .take(OWN_CHARGES_HISTORY_LIMIT)
            .map { it.toOwnCharge(today) }
        val pixKey = group.profile?.pixKey?.trim()?.takeIf { it.isNotEmpty() && pending.isNotEmpty() }
        return OwnChargesUi(
            pending = pending,
            history = history,
            pix = pixKey?.let { PixUi(it, group.profile?.pixLabel) },
            debt = groupOwnDebt(pendingCharges, pending, today, group.profile?.pixLabel, pixKey != null),
        )
    }
```

4.8. Em `loadNextGame`, no ramo `if (game == null)`, dentro do `it.copy(`, logo depois da linha `                    autoConfirmationVisible = false,`, acrescentar:

```kotlin
                    waitlist = null,
```

4.9. Ainda em `loadNextGame`, substituir do `            else -> {` (o último ramo do `when`) até o `}` que fecha esse ramo por:

```kotlin
            else -> {
                val detail = (detailResult as SaqzResult.Success).value
                val roster = (rosterResult as SaqzResult.Success).value
                val membershipType = (profileResult as SaqzResult.Success).value.memberships
                    .firstOrNull { it.groupId == GroupId(groupId) }
                    ?.membershipType
                val nextGame = game.toNextGame(detail, roster)
                // Formatar suspende (`getString`): a guarda é re-checada depois, não antes.
                if (generation != loadGeneration) return
                val response = detail.ownAttendance?.toResponse(roster)
                update {
                    it.copy(
                        isLoading = false,
                        loadFailed = false,
                        error = null,
                        nextGame = nextGame,
                        onboarding = onboarding,
                        attendance = detail.toAttendance(),
                        memberResponse = response,
                        waitlist = response.toWaitlist(roster, emptyList(), membershipType),
                        responding = false,
                        responseFailed = false,
                        membershipType = membershipType,
                        // Dono e admin também são atletas do grupo: quem é mensalista tem
                        // direito à auto-confirmação independente do papel administrativo.
                        autoConfirmationVisible = membershipType == AthleteMembershipType.MENSALISTA &&
                            group.gameConfig.autoConfirmEnabled,
                        autoConfirmationEnabled = detail.autoConfirmEnabled,
                        autoConfirmationUpdating = false,
                        autoConfirmationFailed = false,
                        rosterStale = false,
                        rosterRefreshing = false,
                    ).from(group)
                }
            }
```

4.10. Substituir a função `respond` inteira por:

```kotlin
    private fun respond(intent: AttendanceIntent) {
        val current = state.value
        val game = current.nextGame ?: return
        // Quem já está na fila só pode sair dela: um segundo "Vou" não muda nada no servidor
        // e o otimista piscaria "confirmado" (mesma guarda da Início).
        val alreadyQueued = current.memberResponse?.status == GroupDetailsResponseStatus.Waitlisted
        if (!game.confirmationOpen || current.responding || (alreadyQueued && intent == AttendanceIntent.Confirm)) return
        if (!game.confirmationIsOpen()) {
            update { it.copy(nextGame = game.copy(confirmationOpen = false)) }
            return
        }
        val generation = ++responseGeneration
        rosterGeneration++
        val loadAtStart = loadGeneration
        val previousResponse = current.memberResponse
        val previousWaitlist = current.waitlist
        val optimistic = current.optimisticStatus(game, intent)
        update {
            it.copy(
                memberResponse = GroupDetailsResponseUi(optimistic),
                waitlist = GroupDetailsResponseUi(optimistic)
                    .toWaitlist(null, it.waitlist?.rows.orEmpty(), it.membershipType),
                responding = true,
                athleteIntroVisible = false,
                responseFailed = false,
            )
        }
        viewModelScope.launch {
            val result = attendanceGateway.respond(
                GroupId(groupId),
                game.gameId,
                SelfAttendanceCommand(Uuid.random().toString(), intent),
            )
            if (generation != responseGeneration || loadAtStart != loadGeneration) return@launch
            when (result) {
                is SaqzResult.Success -> {
                    val rosterResult = attendanceGateway.roster(GroupId(groupId), game.gameId)
                    if (generation != responseGeneration || loadAtStart != loadGeneration) return@launch
                    val roster = (rosterResult as? SaqzResult.Success)?.value
                    val detail = result.value.value.detail
                    val response = result.value.value.attendance.toResponse(roster)
                    val reconciled = if (roster != null) response.reconcileRoster(roster) else response
                    val showIntroduction = !state.value.isAdmin && !athleteIntroShown
                    if (showIntroduction) athleteIntroShown = true
                    update {
                        it.copy(
                            nextGame = it.nextGame?.reconcile(detail, roster),
                            attendance = detail.toAttendance(),
                            memberResponse = reconciled,
                            waitlist = reconciled.toWaitlist(roster, it.waitlist?.rows.orEmpty(), it.membershipType),
                            toast = reconciled.status.toToast(),
                            responding = false,
                            athleteIntroVisible = it.athleteIntroVisible || showIntroduction,
                            responseFailed = false,
                            rosterStale = rosterResult is SaqzResult.Failure,
                            rosterRefreshing = false,
                        )
                    }
                }
                is SaqzResult.Failure -> update {
                    it.copy(
                        memberResponse = previousResponse,
                        waitlist = previousWaitlist,
                        responding = false,
                        responseFailed = result.error != AttendanceError.Frozen,
                        nextGame = if (result.error == AttendanceError.DeadlinePassed || result.error == AttendanceError.Frozen) {
                            it.nextGame?.copy(confirmationOpen = false)
                        } else it.nextGame,
                    )
                }
            }
        }
    }
```

4.11. Em `retryRoster`, substituir o bloco

```kotlin
                    update {
                        it.copy(
                            nextGame = it.nextGame?.reconcile(detail.value, roster.value),
                            attendance = detail.value.toAttendance(),
                            memberResponse = it.memberResponse?.reconcileRoster(roster.value),
                            rosterStale = false,
                            rosterRefreshing = false,
                        )
                    }
```

por

```kotlin
                    update {
                        val reconciled = it.memberResponse?.reconcileRoster(roster.value)
                        it.copy(
                            nextGame = it.nextGame?.reconcile(detail.value, roster.value),
                            attendance = detail.value.toAttendance(),
                            memberResponse = reconciled,
                            waitlist = reconciled.toWaitlist(roster.value, emptyList(), it.membershipType),
                            rosterStale = false,
                            rosterRefreshing = false,
                        )
                    }
```

4.12. Substituir a função `toNextGame` inteira por:

```kotlin
    private suspend fun Game.toNextGame(detail: AttendanceDetail, roster: AttendanceRoster): NextGameUi {
        val zone = gameTimeZone(zoneId)
        val startsAtLocal = runCatching { Instant.parse(startsAt).toLocalDateTime(zone) }.getOrNull()
        val deadlineLocal = runCatching { Instant.parse(confirmationDeadline).toLocalDateTime(zone) }.getOrNull()
        val place = listOfNotNull(venue.name, venue.court).joinToString(" — ")
        return NextGameUi(
            gameId = id,
            date = displayDate(),
            venue = place,
            deadline = displayDeadline(),
            confirmationDeadline = confirmationDeadline,
            confirmedCount = detail.confirmedCount,
            capacity = detail.capacity,
            confirmedNames = roster.confirmed.map { it.displayName },
            availableSpots = detail.availableSpots,
            confirmationOpen = status == GameStatus.Published && deadlineIsOpen(),
            hasGameFee = gameFeeCents != null,
            display = startsAtLocal?.gameHeroDisplay() ?: displayDate(),
            meta = startsAtLocal?.gameHeroMeta(place) ?: place,
            address = venue.address,
            deadlineLine = deadlineLocal?.gameDeadlineSentence(now.now().toLocalDateTime(zone).date).orEmpty(),
            deadlineShort = deadlineLocal?.gameDeadlineShort().orEmpty(),
            bellLabel = deadlineLocal?.gameBellLabel().orEmpty(),
        )
    }
```

4.13. Logo depois da função `AttendanceStatus.toResponseStatus()` (a que termina em `AttendanceStatus.Waitlisted -> GroupDetailsResponseStatus.Waitlisted` + `}`), acrescentar:

```kotlin

    private fun GroupDetailsResponseStatus.toToast() = when (this) {
        GroupDetailsResponseStatus.Confirmed -> GroupDetailsToast.Confirmed
        GroupDetailsResponseStatus.Declined -> GroupDetailsToast.Declined
        GroupDetailsResponseStatus.Waitlisted -> GroupDetailsToast.Waitlisted
    }

    // A mesma previsão da Início: recusar é sempre recusa; quem já está confirmado continua;
    // avulso sob prioridade de mensalista e jogo lotado caem na fila. O servidor confirma.
    private fun GroupDetailsState.optimisticStatus(game: NextGameUi, intent: AttendanceIntent) = when {
        intent == AttendanceIntent.Decline -> GroupDetailsResponseStatus.Declined
        memberResponse?.status == GroupDetailsResponseStatus.Confirmed -> GroupDetailsResponseStatus.Confirmed
        waitlistKind(membershipType) == HomeWaitlistKind.AvulsoList -> GroupDetailsResponseStatus.Waitlisted
        game.confirmedCount >= game.capacity -> GroupDetailsResponseStatus.Waitlisted
        else -> GroupDetailsResponseStatus.Confirmed
    }

    /** Ponto único do tipo de espera, com a regra da Início: avulso + prioridade ⇒ lista do avulso. */
    private fun waitlistKind(membershipType: AthleteMembershipType?): HomeWaitlistKind =
        if (membershipType == AthleteMembershipType.AVULSO && loadedGroup?.gameConfig?.mensalistaPriority == true) {
            HomeWaitlistKind.AvulsoList
        } else {
            HomeWaitlistKind.Reserva
        }

    /** Fora da fila não há espera. Sem roster novo (otimista), a fila anterior é mantida. */
    private fun GroupDetailsResponseUi?.toWaitlist(
        roster: AttendanceRoster?,
        fallbackRows: List<HomeWaitlistRowUi>,
        membershipType: AthleteMembershipType?,
    ): GroupWaitlistUi? {
        if (this?.status != GroupDetailsResponseStatus.Waitlisted) return null
        val rows = roster?.waitlisted?.mapIndexed { index, member ->
            HomeWaitlistRowUi(name = member.displayName, position = index + 1L, isSelf = member.memberId == memberId)
        } ?: fallbackRows
        return GroupWaitlistUi(kind = waitlistKind(membershipType), rows = rows)
    }
```

4.14. No fim do arquivo, logo depois da linha `private const val OWN_CHARGES_HISTORY_LIMIT = 6`, acrescentar:

```kotlin
private const val PIX_COPIED_DWELL_MILLIS = 2_000L
```

## Testes

Todos em `commonTest` (rodam em `iosSimulatorArm64Test`). Os ~51 testes que já existem em `GroupDetailsViewModelTest` **não mudam de expectativa**; se algum quebrar, o erro está neste PR.

### `TEST/game/GameLabelsTest.kt` (arquivo completo)

```kotlin
package br.com.saqz.groups.presentation.game

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class GameLabelsTest {
    // 04/08/2026 é uma terça-feira.
    private val start = LocalDateTime(2026, 8, 4, 19, 30)
    private val deadline = LocalDateTime(2026, 8, 4, 12, 0)

    @Test
    fun `hero display capitalizes the weekday and uses the h time`() = runTest {
        assertEquals("Terça, 19h30", start.gameHeroDisplay())
    }

    @Test
    fun `hero meta writes the month in lowercase before the place`() = runTest {
        assertEquals("4 de agosto · CERET — Quadra 2", start.gameHeroMeta("CERET — Quadra 2"))
    }

    @Test
    fun `deadline sentence is relative to today in the game zone`() = runTest {
        assertEquals("As confirmações encerram hoje às 12h00.", deadline.gameDeadlineSentence(LocalDate(2026, 8, 4)))
        assertEquals("As confirmações encerram amanhã às 12h00.", deadline.gameDeadlineSentence(LocalDate(2026, 8, 3)))
        assertEquals("As confirmações encerram em 04/08 às 12h00.", deadline.gameDeadlineSentence(LocalDate(2026, 8, 1)))
    }

    @Test
    fun `short deadline and bell reuse the home keys`() = runTest {
        assertEquals("Encerra 04/08 · 12h00", deadline.gameDeadlineShort())
        assertEquals("Avisamos você se abrir vaga até 12h00 de 04/08.", deadline.gameBellLabel())
    }

    @Test
    fun `short month and date labels`() = runTest {
        assertEquals("AGO", gameShortMonthLabel(8))
        assertEquals("04/08", start.date.gameDateLabel())
    }

    @Test
    fun `unknown zone falls back to utc instead of throwing`() {
        assertEquals(TimeZone.UTC, gameTimeZone("Marte/Olympus"))
    }
}
```

### `TEST/details/GroupOwnDebtTest.kt` (arquivo completo)

```kotlin
package br.com.saqz.groups.presentation.details

import br.com.saqz.domain.GroupId
import br.com.saqz.groups.domain.finance.Charge
import br.com.saqz.groups.domain.finance.ChargeKind
import br.com.saqz.groups.domain.finance.ChargeStatus
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GroupOwnDebtTest {
    private val today = LocalDate(2026, 8, 1)

    @Test
    fun `no pending charge means no debt`() = runTest {
        assertNull(groupOwnDebt(emptyList(), emptyList(), today, "Lucas Prado", hasPixKey = true))
    }

    @Test
    fun `debt takes competence and due label from the oldest and sums the rest`() = runTest {
        val debt = groupOwnDebt(
            pending = listOf(charge("mensal", 7_000L, "2026-07-10"), charge("avulso", 2_500L, "2026-08-28")),
            pendingUi = listOf(ui("mensal", "Mensalidade · Julho", "Venceu em 10/07"), ui("avulso", "Jogo avulso", "Vence em 28/08")),
            today = today,
            pixLabel = " Lucas Prado ",
            hasPixKey = true,
        )

        assertEquals(
            GroupOwnDebtUi(
                eyebrow = "Mensalidade · Julho",
                totalLabel = "R$ 95,00",
                dueLabel = "Venceu em 10/07",
                overdue = true,
                countLabel = "2 cobranças em aberto",
                receiverLabel = "Pix de Lucas Prado",
            ),
            debt,
        )
    }

    @Test
    fun `single pending charge not yet due has no count and is not overdue`() = runTest {
        val debt = groupOwnDebt(
            pending = listOf(charge("avulso", 2_500L, "2026-08-28")),
            pendingUi = listOf(ui("avulso", "Jogo avulso", "Vence em 28/08")),
            today = today,
            pixLabel = null,
            hasPixKey = true,
        )

        assertEquals(false, debt?.overdue)
        assertNull(debt?.countLabel)
        assertNull(debt?.receiverLabel)
    }

    @Test
    fun `receiver is hidden when the group has no pix key`() = runTest {
        val debt = groupOwnDebt(
            pending = listOf(charge("mensal", 7_000L, "2026-07-10")),
            pendingUi = listOf(ui("mensal", "Mensalidade · Julho", "Venceu em 10/07")),
            today = today,
            pixLabel = "Lucas Prado",
            hasPixKey = false,
        )

        assertNull(debt?.receiverLabel)
    }

    private fun charge(id: String, cents: Long, dueDate: String) = Charge(
        id = id,
        groupId = GroupId("group-1"),
        memberId = "me",
        kind = ChargeKind.Monthly,
        amountCents = cents,
        dueDate = dueDate,
        status = ChargeStatus.Pending,
        version = 1,
        audit = emptyList(),
    )

    private fun ui(id: String, title: String, due: String) =
        OwnChargeUi(id = id, title = title, dueLabel = due, amountLabel = "", status = OwnChargeStatusUi.Pending)
}
```

### `TEST/details/GroupDetailsViewModelTest.kt` — acrescentar, logo ANTES de `    private fun ownCharge(`

Acrescentar estes imports no topo (junto dos demais `br.com.saqz.groups…`):

```kotlin
import br.com.saqz.groups.domain.attendance.AttendanceDetail
import br.com.saqz.groups.domain.game.GameVenue
import br.com.saqz.groups.presentation.home.HomeWaitlistKind
import br.com.saqz.groups.presentation.home.HomeWaitlistRowUi
import kotlinx.coroutines.test.advanceTimeBy
```

(`AttendanceDetail` já pode estar importado por `sampleAttendanceDetail`; se o import já existir, não duplicar.)

E estes testes:

```kotlin
    @Test
    fun `next game exposes the hero labels in the game time zone`() = runTest {
        val viewModel = viewModel(gameGateway = FakeGameGateway(listResult = SaqzResult.Success(listOf(sampleGame()))))

        val game = assertNotNull(viewModel.state.value.nextGame)
        assertEquals("Terça, 19h30", game.display)
        assertEquals("4 de agosto · CERET", game.meta)
        assertEquals("Rua Canuto Abreu", game.address)
        assertEquals("As confirmações encerram em 04/08 às 12h00.", game.deadlineLine)
        assertEquals("Encerra 04/08 · 12h00", game.deadlineShort)
        assertEquals("Avisamos você se abrir vaga até 12h00 de 04/08.", game.bellLabel)
    }

    @Test
    fun `deadline sentence turns relative on the day of the game`() = runTest {
        val viewModel = viewModel(
            gameGateway = FakeGameGateway(listResult = SaqzResult.Success(listOf(sampleGame()))),
            now = GroupNowPort { kotlin.time.Instant.parse("2026-08-04T13:00:00Z") },
        )

        assertEquals("As confirmações encerram hoje às 12h00.", viewModel.state.value.nextGame?.deadlineLine)
    }

    @Test
    fun `map prefers the game address over the group default venue`() = runTest {
        val elsewhere = sampleGame().copy(venue = GameVenue(name = "Arena Mooca", address = "Av. Paes de Barros, 1000"))
        val viewModel = viewModel(gameGateway = FakeGameGateway(listResult = SaqzResult.Success(listOf(elsewhere))))

        viewModel.onIntent(GroupDetailsIntent.OpenVenueMap)

        assertEquals(GroupDetailsEffect.OpenMap("Av. Paes de Barros, 1000"), viewModel.effects.first())
    }

    @Test
    fun `waitlist rows mark the own line by member id and default to the reserve kind`() = runTest {
        val viewModel = viewModel(
            gameGateway = FakeGameGateway(listResult = SaqzResult.Success(listOf(sampleGame()))),
            attendanceGateway = FakeAttendanceGateway(detailResult = SaqzResult.Success(waitlistedDetail("wait-2", 2))),
        )

        assertEquals(
            GroupWaitlistUi(
                kind = HomeWaitlistKind.Reserva,
                rows = listOf(HomeWaitlistRowUi("Caio", 1, false), HomeWaitlistRowUi("Duda", 2, true)),
            ),
            viewModel.state.value.waitlist,
        )
    }

    @Test
    fun `a waitlisted member cannot confirm again but can leave the queue`() = runTest {
        val attendance = FakeAttendanceGateway(detailResult = SaqzResult.Success(waitlistedDetail("wait-1", 1)))
        val viewModel = viewModel(
            gameGateway = FakeGameGateway(listResult = SaqzResult.Success(listOf(sampleGame()))),
            attendanceGateway = attendance,
        )

        viewModel.onIntent(GroupDetailsIntent.Respond(AttendanceIntent.Confirm))
        assertEquals(0, attendance.respondCalls)

        viewModel.onIntent(GroupDetailsIntent.Respond(AttendanceIntent.Decline))
        assertEquals(1, attendance.respondCalls)
    }

    @Test
    fun `optimistic response predicts the queue when the game is already full`() = runTest {
        val attendance = FakeAttendanceGateway(
            detailResult = SaqzResult.Success(sampleAttendanceDetail().copy(confirmedCount = 12, availableSpots = 0)),
        )
        attendance.respondDeferred = CompletableDeferred()
        val viewModel = viewModel(
            gameGateway = FakeGameGateway(listResult = SaqzResult.Success(listOf(sampleGame()))),
            attendanceGateway = attendance,
        )

        viewModel.onIntent(GroupDetailsIntent.Respond(AttendanceIntent.Confirm))

        assertEquals(GroupDetailsResponseStatus.Waitlisted, viewModel.state.value.memberResponse?.status)
        assertEquals(HomeWaitlistKind.Reserva, viewModel.state.value.waitlist?.kind)
    }

    @Test
    fun `day member under monthly priority is predicted into the day member list`() = runTest {
        val attendance = FakeAttendanceGateway()
        attendance.respondDeferred = CompletableDeferred()
        val viewModel = viewModel(
            gameGateway = FakeGameGateway(listResult = SaqzResult.Success(listOf(sampleGame()))),
            attendanceGateway = attendance,
            athleteGateway = dayMemberAthleteGateway(),
        )

        viewModel.onIntent(GroupDetailsIntent.Respond(AttendanceIntent.Confirm))

        assertEquals(GroupDetailsResponseStatus.Waitlisted, viewModel.state.value.memberResponse?.status)
        assertEquals(HomeWaitlistKind.AvulsoList, viewModel.state.value.waitlist?.kind)
    }

    @Test
    fun `failed response restores the previous waitlist`() = runTest {
        val attendance = FakeAttendanceGateway(
            detailResult = SaqzResult.Success(waitlistedDetail("wait-1", 1)),
            respondResult = SaqzResult.Failure(AttendanceError.Conflict),
        )
        val viewModel = viewModel(
            gameGateway = FakeGameGateway(listResult = SaqzResult.Success(listOf(sampleGame()))),
            attendanceGateway = attendance,
        )
        val before = assertNotNull(viewModel.state.value.waitlist)

        viewModel.onIntent(GroupDetailsIntent.Respond(AttendanceIntent.Decline))

        assertEquals(before, viewModel.state.value.waitlist)
        assertTrue(viewModel.state.value.responseFailed)
    }

    @Test
    fun `successful response raises the matching toast and dismiss clears it`() = runTest {
        // O roster padrão do fake deixa "wait-1" na fila; aqui ele precisa estar confirmado para
        // a reconciliação não rebaixar a resposta e o toast ser o de presença confirmada.
        val attendance = FakeAttendanceGateway(
            rosterResult = SaqzResult.Success(
                AttendanceRoster(confirmed = listOf(AttendanceRosterMember("wait-1", "Caio")), waitlisted = emptyList()),
            ),
        )
        val viewModel = viewModel(
            gameGateway = FakeGameGateway(listResult = SaqzResult.Success(listOf(sampleGame()))),
            attendanceGateway = attendance,
        )

        viewModel.onIntent(GroupDetailsIntent.Respond(AttendanceIntent.Confirm))
        assertEquals(GroupDetailsToast.Confirmed, viewModel.state.value.toast)

        viewModel.onIntent(GroupDetailsIntent.DismissToast)
        assertNull(viewModel.state.value.toast)
    }

    @Test
    fun `copied pix flips the ticket for two seconds and toasts`() = runTest {
        val viewModel = viewModel(
            groupGateway = pixGroupGateway(),
            athleteFinanceGateway = FakeAthleteFinanceGateway(
                ownChargesResult = SaqzResult.Success(ChargeList(listOf(ownCharge("mensal", month = "2026-08")))),
            ),
        )

        viewModel.onIntent(GroupDetailsIntent.CopyPix)

        assertTrue(viewModel.state.value.pixCopied)
        assertEquals(GroupDetailsToast.PixCopied, viewModel.state.value.toast)
        advanceTimeBy(2_001)
        assertFalse(viewModel.state.value.pixCopied)
    }

    @Test
    fun `second copy inside the dwell keeps the copied state until its own dwell ends`() = runTest {
        val viewModel = viewModel(
            groupGateway = pixGroupGateway(),
            athleteFinanceGateway = FakeAthleteFinanceGateway(
                ownChargesResult = SaqzResult.Success(ChargeList(listOf(ownCharge("mensal", month = "2026-08")))),
            ),
        )

        viewModel.onIntent(GroupDetailsIntent.CopyPix)
        advanceTimeBy(1_500)
        viewModel.onIntent(GroupDetailsIntent.CopyPix)
        advanceTimeBy(1_000)

        assertTrue(viewModel.state.value.pixCopied)
        advanceTimeBy(1_001)
        assertFalse(viewModel.state.value.pixCopied)
    }

    @Test
    fun `own charges carry the debt summary of the oldest pending charge`() = runTest {
        val viewModel = viewModel(
            groupGateway = pixGroupGateway(),
            athleteFinanceGateway = FakeAthleteFinanceGateway(
                ownChargesResult = SaqzResult.Success(
                    ChargeList(
                        listOf(
                            ownCharge("avulso", kind = ChargeKind.Game, dueDate = "2026-08-28"),
                            ownCharge("mensal", month = "2026-07", dueDate = "2026-07-10"),
                        ),
                    ),
                ),
            ),
        )

        val debt = assertNotNull(viewModel.state.value.ownCharges?.debt)
        assertEquals("Mensalidade · Julho", debt.eyebrow)
        assertEquals("R$ 140,00", debt.totalLabel)
        assertEquals("Venceu em 10/07", debt.dueLabel)
        assertTrue(debt.overdue)
        assertEquals("2 cobranças em aberto", debt.countLabel)
        assertEquals("Pix de Lucas Prado", debt.receiverLabel)
    }

    @Test
    fun `settled charges expose no debt`() = runTest {
        val viewModel = viewModel(
            groupGateway = athleteGroupGateway(),
            athleteFinanceGateway = FakeAthleteFinanceGateway(
                ownChargesResult = SaqzResult.Success(
                    ChargeList(listOf(ownCharge("paga", month = "2026-07", status = ChargeStatus.Paid))),
                ),
            ),
        )

        assertNull(viewModel.state.value.ownCharges?.debt)
    }

    private fun waitlistedDetail(memberId: String, position: Long): AttendanceDetail = sampleAttendanceDetail().copy(
        ownAttendance = AttendanceEntry(memberId, AttendanceStatus.Waitlisted, position, version = 1),
    )

    private fun dayMemberAthleteGateway() = FakeAthleteGateway(
        ownProfileResult = SaqzResult.Success(
            OwnAthleteProfile(
                userId = "me",
                displayName = "Member",
                phone = null,
                memberships = listOf(
                    OwnAthleteMembership(
                        groupId = GroupId(GROUP_ID),
                        groupName = "Vôlei do CERET",
                        role = GroupRole.ATHLETE,
                        position = null,
                        membershipType = AthleteMembershipType.AVULSO,
                        active = true,
                    ),
                ),
            ),
        ),
    )
```

Notas de arranjo (fatos do código, não opções): `sampleVersionedAttendanceMutation()` devolve `AttendanceEntry(memberId = "wait-1", Confirmed)` e `reconcileRoster` rebaixa para a fila quem aparece em `roster.waitlisted` — por isso o teste do toast troca o roster; `sampleGame()` começa em `2026-08-04T19:30:00-03:00` com prazo `12:00-03:00`; o `now` padrão do helper `viewModel(...)` é `2026-08-01T00:00:00Z`; `sampleAttendanceRoster()` tem `wait-1 Caio` e `wait-2 Duda`; `sampleGroup()` tem `gameConfig.mensalistaPriority = true` por default; `ownCharge(...)` vale 7.000 centavos; `pixGroupGateway()` devolve atleta com `pixLabel = "Lucas Prado"`.

## Gates

Rodar da raiz do worktree, nesta ordem, e colar o resumo (BUILD SUCCESSFUL + contagem de testes) no corpo do PR:

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile :features:groups:presentation:detektAll
JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile :features:groups:presentation:iosSimulatorArm64Test --tests "br.com.saqz.groups.presentation.game.*" --tests "br.com.saqz.groups.presentation.details.*"
JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile :features:groups:presentation:iosSimulatorArm64Test
JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile :features:groups:presentation:recordRoborazziAndroidHostTest
```

O último gate existe para provar que nada visual mudou: depois dele, `git status` continua limpo (a pasta `screenshots/` é ignorada) e nenhuma cena falha.

## Critérios de aceite

- [ ] Nenhum arquivo de `ui/`, `home/` ou `composeResources/` no diff.
- [ ] `GroupDetailsViewModelTest`: todos os testes antigos verdes sem mudança de expectativa + os 13 novos verdes.
- [ ] `GameLabelsTest` (6) e `GroupOwnDebtTest` (4) verdes.
- [ ] `detektAll` do módulo verde sem baseline novo e sem `@Suppress` novo.
- [ ] Diff ≤ 900 linhas (estimativa: ~780).
- [ ] O PR não tem print de tela (nada visual mudou) — dizer isso explicitamente no corpo.

## Protocolo do worker

1. Branch a partir de `origin/main`: `git fetch origin && git switch -c vul-XXX-contrato-hero-cobranca origin/main` (XXX = número deste ticket).
2. Commits pequenos em PT-BR no padrão do repo (`feat(groups): …`, `test(groups): …`).
3. Rodar TODOS os gates antes de abrir o PR; colar a saída resumida no corpo.
4. PR contra `main`, aberto como ready (não draft), título `feat(groups): contrato do hero e da cobrança no detalhe do grupo (VUL-XXX)`. Teto: 2000 linhas de adições+remoções.
5. `gh` desta máquina: prefixar `GH_TOKEN=$(gh auth token --user bruno-halmeida)` nos comandos `gh`.
6. Depois de abrir o PR: PARAR. Não fazer triagem de review. Só executar mensagens do orquestrador que comecem com `CORRECAO:`.
