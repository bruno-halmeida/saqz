# V2 · Contrato das listas — agenda, esperando você, galera e saldo

**Onda 2 · depende de: V1, S · bloqueia: C4, C5, D · paralelo com: C1, C2, C3**

(O ticket A entrega o dado real do chip da agenda. Este PR compila e passa sem ele: `ownAttendance` é opcional no transporte e, enquanto o backend mandar `null`, todo chip sai "Sem resposta".)

## Objetivo

Deixar o `GroupDetailsViewModel` entregando, já formatado, tudo que os blocos "Próximos jogos" (C4), "Esperando você" (C5) e "Galera" (C2) vão desenhar — **sem mexer em nenhum composable**.

1. `Game.ownAttendance` no domain + data: a listagem de jogos passa a carregar a resposta de quem olha.
2. `agenda`: os próximos jogos do grupo (publicados e rascunhos), sem o jogo do hero, no máximo 12, no vocabulário da Início.
3. `waiting`: as três linhas do gestor — pedidos para entrar, mensalidades a receber (só o mês corrente) e o jogo a acertar.
4. `memberCount`, `memberPreview` e `scheduleSummary`, que existem no estado e hoje ficam vazios em produção.
5. `cashbox.summary` vira só o saldo (`"Saldo R$ 380,00"`, chave `group_details_cash_balance`): a contagem de mensalidades saiu da frase e foi para `waiting.monthly`.
6. A volta de `GameDetail`, `Members` e `Invite` recarrega o detalhe (hoje mostra dado velho).

A tela **não muda visualmente**, exceto o texto da linha do caixa.

## Fora do escopo

- **Nenhum arquivo em `ui/`** (C1, C2 e C3 rodam em paralelo neles). `GroupDetailsPreviewData` e `GroupDetailsScreenTest` continuam com o literal antigo `"Saldo R$ 380,00 · 8 mensalidades em aberto"`: é estado fixo de preview, não passa pelo ViewModel, e o dono é o D.
- Nenhuma string nova, renomeada ou editada: só USO das chaves do S e das `home_*`.
- Não tocar em `home/` (o `HomeViewModel` migra para `GameLabels.kt` no ticket H).
- Os rótulos de dia da semana do `scheduleSummary` continuam sendo os `GroupWeekday.label()` privados e hardcoded do fim do `GroupDetailsViewModel.kt` (dívida conhecida): entram na regra pura por parâmetro. O " e " e o "19h30" do resumo seguem a mesma dívida do `toSummaryChips` que já existe.
- Singular de "1 pedidos para entrar" / "1 mensalidades a receber": as chaves `home_admin_waiting_*` não têm variante singular e a Início já fala assim. Não é deste ticket.
- Não paralelizar a carga; não criar tela de agenda própria; não paginar.
- Botão voltar **do sistema** não passa pelo `onBack` do Root (o `NavDisplay` chama `pop` direto): é o mesmo limite que o retorno do caixa já tem hoje. Fora deste ticket.

## Arquivos

Bases: `PRES`, `TEST` e `E2E` como no CONTRATO · `DOM = mobile/features/groups/domain/src/commonMain/kotlin/br/com/saqz/groups/domain` · `DATA = mobile/features/groups/data/src/commonMain/kotlin/br/com/saqz/groups/data` · `TDATA = mobile/features/groups/data/src/commonTest/kotlin/br/com/saqz/groups/data` · `APP = mobile/compose-app/src`.

| Ação | Arquivo |
|---|---|
| editar | `DOM/game/Game.kt` |
| editar | `DATA/game/KtorGameGateway.kt` |
| editar | `TDATA/game/KtorGameGatewayTest.kt` |
| editar | `PRES/details/GroupDetailsContract.kt` |
| criar | `PRES/details/GroupAgenda.kt` |
| criar | `PRES/details/GroupWaiting.kt` |
| editar | `PRES/details/GroupDetailsViewModel.kt` |
| editar | `PRES/di/GroupsPresentationModule.kt` |
| editar | `APP/commonMain/kotlin/br/com/saqz/composeapp/navigation/SaqzNavHost.kt` |
| editar | `APP/commonTest/kotlin/br/com/saqz/composeapp/di/SaqzKoinModulesTest.kt` |
| editar | `E2E/FinancialFlowsE2eTest.kt` |
| editar | `TEST/GroupsGatewayFakes.kt` |
| criar | `TEST/details/GroupAgendaTest.kt` |
| criar | `TEST/details/GroupWaitingTest.kt` |
| editar | `TEST/details/GroupDetailsViewModelTest.kt` |
| editar | `TEST/ui/details/GroupDetailsRootTest.kt` (só o construtor da ViewModel) |
| editar | `TEST/ui/finance/groupcash/GroupCashboxRootTest.kt` (construtor + texto do saldo) |
| editar | `TEST/ui/finance/settlement/GameSettlementRootTest.kt` (construtor + texto do saldo) |

**Tocar em arquivo fora desta lista = parar e avisar o orquestrador.**

Fatos do código que fecham decisões desta receita:

- O transporte de `ownAttendance` **reutiliza** `AttendanceEntryTransport` (`DATA/attendance/KtorAttendanceGateway.kt`): é `internal` do mesmo módulo Gradle, tem exatamente os quatro campos do contrato do ticket A (`memberId`, `status`, `waitlistPosition`, `version`) e custa um import + um campo. O `toDomain()` dele é `private` naquele arquivo, então o mapeamento aqui lê só o `status`, no idioma que o arquivo já usa (`GameStatus.entries[ordinal]`).
- O único fake de `GroupEntryRequestGateway` que existe (`FakeEntryGateway` em `TEST/invite/GroupInviteViewModelsTest.kt`) é `private class` aninhada: não dá para reutilizar. O fake novo nasce em `TEST/GroupsGatewayFakes.kt`.
- `GroupDetailsViewModel(` é construído à mão em QUATRO testes (`GroupDetailsViewModelTest`, `GroupDetailsRootTest`, `GroupCashboxRootTest`, `GameSettlementRootTest`). Default com implementação concreta é proibido (AGENTS.md §7), então os quatro ganham o argumento.
- O grafo de `SaqzKoinModulesTest.groupsPresentationModuleResolvesWithTheRouteArguments` **não** instala `inviteManagementDataModule()` (quem provê `GroupEntryRequestGateway`; o app real instala em `SaqzKoinBootstrap.kt:84`). Sem o passo 9, `koin.get<GroupDetailsViewModel>` estoura `NoDefinitionFoundException`.
- `GroupCashboxRootTest` provava a recarga do detalhe pela contagem ("1 → 0 mensalidades"). A contagem saiu da frase, então a prova passa a ser o saldo: o `onMutationSuccess` do teste troca também o extrato (passo 12).
- A classe dentro de `FinancialFlowsE2eTest.kt` é `PaymentE2eTest`; o cenário dela em `tests/e2e/android/guard.mjs` é `payments`. O source set e2e entra no `androidTest` com `-Psaqz.e2e=true` (`mobile/android-app/build.gradle.kts:98-99`), e o runner usa `:android-app:connectedDevDebugAndroidTest`; a task de compilação correspondente é `:android-app:compileDevDebugAndroidTestKotlin -Psaqz.e2e=true`.
- O `now` padrão do helper `viewModel(...)` é `2026-08-01T00:00:00Z`, que em `America/Sao_Paulo` ainda é **31/07** (competência `2026-07`). Todo teste novo que fala de mensalidade passa `now` explícito em meados de agosto.
- `groupOnboarding` só devolve `ReviewFinances` com EXATAMENTE um jogo `Completed`; com dois, o guia some. Os testes da linha "acertar" usam dois jogos concluídos quando querem a linha e um quando querem a supressão.

## Contrato exportado

C4 e C5 são escritos contra isto (é a seção 3 do CONTRATO, sem desvio):

```kotlin
// GroupDetailsState — campos novos (com default; ninguém quebra)
val agenda: List<GroupAgendaRowUi> = emptyList()   // próximos jogos SEM o do hero; ordem de início; no máximo 12
val waiting: GroupWaitingUi? = null                // só gestor; null = nenhuma das três linhas

enum class GroupAgendaStatus { Pending, Going, Out, Waitlisted, Draft }

@Immutable data class GroupAgendaRowUi(
    val gameId: String,
    val day: String,                 // "6" (sem zero à esquerda)
    val month: String,               // "AGO"
    val title: String,               // "Quinta · 19h30"
    val meta: String,                // "8 de 12 confirmados" | "8 de 12 · Arena Mooca" | "Lotado · 12 de 12" | rascunho: "Só você vê…"
    val status: GroupAgendaStatus,
    val statusLabel: String,         // "Sem resposta" | "Você vai" | "Não vai" | "Na espera" | "Rascunho"
    val contentDescription: String,  // "Quinta, 06/08 às 19h30, Você vai"
)

@Immutable data class GroupWaitingRowUi(val title: String, val meta: String, val contentDescription: String, val count: Int = 0)
@Immutable data class GroupSettleRowUi(val gameId: String, val title: String, val meta: String, val contentDescription: String)
@Immutable data class GroupWaitingUi(
    val entryRequests: GroupWaitingRowUi? = null,   // "3 pedidos para entrar" / "Ana, Bia e mais 1" / count = 3
    val monthly: GroupWaitingRowUi? = null,         // "2 mensalidades a receber" / "R$ 140,00 · AGO" / count = 2
    val settle: GroupSettleRowUi? = null,           // "Acertar o jogo de 28/07" / "2 avulsos · R$ 50,00 a receber"
)

// GroupDetailsIntent — novos
data class OpenAgendaGame(val gameId: String) : GroupDetailsIntent   // emite GroupDetailsEffect.OpenGame(groupId, gameId)
data class OpenSettlement(val gameId: String) : GroupDetailsIntent   // emite GroupDetailsEffect.OpenSettlement(groupId, gameId)

// Regras puras (internal, pacote br.com.saqz.groups.presentation.details)
internal suspend fun groupAgenda(games: List<Game>, heroGameId: String?, now: Instant, defaultVenueName: String?): List<GroupAgendaRowUi>
internal fun groupScheduleSummary(slots: List<GroupRegularSlot>, weekdayLabel: (GroupWeekday) -> String): String?
internal suspend fun groupWaiting(
    charges: List<Charge>, games: List<Game>, entryRequests: List<GroupEntryRequest>, monthKey: String, reviewingGameId: String?,
): GroupWaitingUi?

// Domain
data class Game(/* … */, val financeReviewRequired: Boolean = false, val ownAttendance: AttendanceStatus? = null)
```

Regras que a UI pode assumir:

- `waiting != null` ⇒ pelo menos uma das três linhas é não nula. Atleta recebe sempre `null`.
- `contentDescription` das linhas de `waiting` = `"$title. $meta"`.
- `waiting.settle` some enquanto `onboarding` é `ReviewFinances` do MESMO jogo (o guia já fala dele).
- Finanças falharam ⇒ `cashbox = CashboxUi(summary = null)` e `waiting` só carrega `entryRequests`.
- `agenda` e `waiting` mantêm o valor anterior durante uma recarga (`isLoading = true`); a tela já troca o conteúdo pelo skeleton nesse estado.
- Campos que já existiam e agora chegam preenchidos: `memberCount` (tamanho do roster de atletas), `memberPreview` (os 4 primeiros: `id = userId`, `name = displayName`, `meta = ""`, `status = null`), `scheduleSummary` (`"Terça e Quinta · 19h30"`; `null` sem agenda fixa), `cashbox.summary` (`"Saldo R$ 380,00"`).
- Rascunho na agenda: o backend já não manda `Draft` para atleta; o mapper não filtra por papel.

## Passo a passo

### 1. Editar `DOM/game/Game.kt`

1.1. Nos imports, logo depois de `import br.com.saqz.domain.GroupId`, acrescentar:

```kotlin
import br.com.saqz.groups.domain.attendance.AttendanceStatus
```

1.2. Em `data class Game`, trocar a linha

```kotlin
    val financeReviewRequired: Boolean = false,
)
```

(a que fecha a `data class Game` — a linha seguinte é `data class VersionedGame`) por

```kotlin
    val financeReviewRequired: Boolean = false,
    /** A resposta de QUEM OLHA neste jogo, vinda da listagem; `null` = ainda não respondeu. */
    val ownAttendance: AttendanceStatus? = null,
)
```

### 2. Editar `DATA/game/KtorGameGateway.kt`

2.1. Nos imports, logo depois de `import br.com.saqz.domain.ValidationDetails`, acrescentar:

```kotlin
import br.com.saqz.groups.data.attendance.AttendanceEntryTransport
import br.com.saqz.groups.domain.attendance.AttendanceStatus
```

2.2. Em `internal data class GameTransport`, logo depois da linha `    val financeReviewRequired: Boolean = false,`, acrescentar:

```kotlin
    val ownAttendance: AttendanceEntryTransport? = null,
```

2.3. Substituir a função `GameTransport.toDomain()` inteira por:

```kotlin
private fun GameTransport.toDomain() = Game(
    id, GroupId(groupId), title, venue.toDomain(), localDate, localTime, zoneId, startsAt,
    durationMinutes, capacity, confirmationDeadline, gameFeeCents, notes, status.toDomain(),
    version, confirmedCount, availableSpots, waitlistCount, financeReviewRequired,
    ownAttendance?.status?.let { AttendanceStatus.entries[it.ordinal] },
)
```

### 3. Editar `PRES/details/GroupDetailsContract.kt` (código como fica DEPOIS do V1)

3.1. Em `data class GroupDetailsState`, logo depois da linha `    val pixCopied: Boolean = false,` (criada pelo V1), acrescentar:

```kotlin
    /** Próximos jogos do grupo SEM o jogo do hero, em ordem de início; no máximo 12. */
    val agenda: List<GroupAgendaRowUi> = emptyList(),
    /** "Esperando você" — só gestor. `null` = nenhuma das três linhas. */
    val waiting: GroupWaitingUi? = null,
```

3.2. Trocar o KDoc de `CashboxUi`

```kotlin
/** A linha de caixa do 2f — saldo e mensalidades já num texto só. */
```

por

```kotlin
/** A linha de caixa do gestor — só o saldo ("Saldo R$ 380,00"). As mensalidades a receber moram em [GroupWaitingUi]. */
```

3.3. Logo depois da linha `enum class MemberStatusUi { Admin, Going, Maybe }`, acrescentar:

```kotlin

/** O chip de uma linha da agenda. [Draft] vence a resposta: rascunho ninguém respondeu ainda. */
enum class GroupAgendaStatus { Pending, Going, Out, Waitlisted, Draft }

/**
 * Uma linha de "Próximos jogos", no molde da linha da Início: bloco de data ([day] "6" e
 * [month] "AGO"), [title] "Quinta · 19h30", [meta] com a lotação e o chip [statusLabel].
 * Tudo no fuso DO JOGO.
 */
@Immutable
data class GroupAgendaRowUi(
    val gameId: String,
    val day: String,
    val month: String,
    val title: String,
    val meta: String,
    val status: GroupAgendaStatus,
    val statusLabel: String,
    val contentDescription: String,
)

/** Uma linha de "Esperando você" que só abre um destino do grupo. [count] alimenta o chip. */
@Immutable
data class GroupWaitingRowUi(
    val title: String,
    val meta: String,
    val contentDescription: String,
    val count: Int = 0,
)

/** A linha "Acertar o jogo de 28/07": carrega o [gameId] porque o destino é o acerto DAQUELE jogo. */
@Immutable
data class GroupSettleRowUi(
    val gameId: String,
    val title: String,
    val meta: String,
    val contentDescription: String,
)

/** As três pendências do gestor. O bloco inteiro é `null` no estado quando as três são nulas. */
@Immutable
data class GroupWaitingUi(
    val entryRequests: GroupWaitingRowUi? = null,
    val monthly: GroupWaitingRowUi? = null,
    val settle: GroupSettleRowUi? = null,
)
```

3.4. Em `sealed interface GroupDetailsIntent`, logo depois da linha `    data object Invite : GroupDetailsIntent`, acrescentar:

```kotlin

    /** Linha da agenda: abre o jogo tocado. O hero continua em [ViewGame]. */
    data class OpenAgendaGame(val gameId: String) : GroupDetailsIntent

    /** Linha "Acertar o jogo": abre o acerto daquele jogo. */
    data class OpenSettlement(val gameId: String) : GroupDetailsIntent
```

### 4. Criar `PRES/details/GroupAgenda.kt` (arquivo completo)

```kotlin
package br.com.saqz.groups.presentation.details

import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.domain.game.Game
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.group.GroupRegularSlot
import br.com.saqz.groups.domain.group.GroupWeekday
import br.com.saqz.groups.presentation.game.gameDateLabel
import br.com.saqz.groups.presentation.game.gameShortMonthLabel
import br.com.saqz.groups.presentation.game.gameTimeLabel
import br.com.saqz.groups.presentation.game.gameTimeZone
import br.com.saqz.groups.presentation.game.gameWeekdayLabel
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.group_details_agenda_meta
import br.com.saqz.groups.resources.group_details_agenda_meta_draft
import br.com.saqz.groups.resources.group_details_agenda_meta_full
import br.com.saqz.groups.resources.group_details_agenda_meta_venue
import br.com.saqz.groups.resources.group_details_agenda_status_draft
import br.com.saqz.groups.resources.home_admin_score_pending
import br.com.saqz.groups.resources.home_upcoming_cd_row
import br.com.saqz.groups.resources.home_upcoming_row_title
import br.com.saqz.groups.resources.home_upcoming_status_going
import br.com.saqz.groups.resources.home_upcoming_status_out
import br.com.saqz.groups.resources.home_upcoming_status_waitlisted
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import kotlin.time.Instant

// ponytail: teto de 12 linhas — a agenda é um `Column` não-lazy dentro da tela mais aberta do
// app e a listagem de jogos não pagina. Grupo que passar disso ganha uma tela de agenda
// própria (lista lazy + paginação no servidor); até lá o "ver mais" da tela abre só estas.
private const val GROUP_AGENDA_LIMIT = 12

/**
 * "Próximos jogos" do detalhe do grupo: publicados e rascunhos que ainda vão começar, SEM o
 * jogo que já está no hero, em ordem de início. Rascunho só chega aqui para gestor — quem
 * filtra por papel é o backend, não este mapper. Jogo com `startsAt` ilegível fica de fora,
 * como em `nextPublishedGame`.
 */
internal suspend fun groupAgenda(
    games: List<Game>,
    heroGameId: String?,
    now: Instant,
    defaultVenueName: String?,
): List<GroupAgendaRowUi> = games
    .filter { it.id != heroGameId && (it.status == GameStatus.Published || it.status == GameStatus.Draft) }
    .mapNotNull { game -> runCatching { Instant.parse(game.startsAt) }.getOrNull()?.let { it to game } }
    .filter { it.first >= now }
    .sortedBy { it.first }
    .take(GROUP_AGENDA_LIMIT)
    .map { (startsAt, game) -> game.toAgendaRow(startsAt, defaultVenueName) }

/**
 * "Terça e Quinta · 19h30": os dias distintos na ordem da semana e, só quando TODOS os
 * horários fixos começam na mesma hora, a hora. Os rótulos de dia entram por parâmetro porque
 * ainda são os hardcoded do `GroupDetailsViewModel.kt` (dívida conhecida).
 */
internal fun groupScheduleSummary(slots: List<GroupRegularSlot>, weekdayLabel: (GroupWeekday) -> String): String? {
    if (slots.isEmpty()) return null
    val days = slots.map { it.weekday }.distinct().sorted().map(weekdayLabel)
    val daysLabel = if (days.size == 1) days.single() else "${days.dropLast(1).joinToString(", ")} e ${days.last()}"
    // "19:30" → "19h30". O `take` corta segundos que um payload antigo possa trazer.
    val time = slots.map { it.startTime }.distinct().singleOrNull()?.take(HOUR_MINUTE_LENGTH)?.replace(':', 'h')
    return listOfNotNull(daysLabel, time).joinToString(" · ")
}

private const val HOUR_MINUTE_LENGTH = 5

private suspend fun Game.toAgendaRow(startsAt: Instant, defaultVenueName: String?): GroupAgendaRowUi {
    val local = startsAt.toLocalDateTime(gameTimeZone(zoneId))
    val weekday = local.date.dayOfWeek.gameWeekdayLabel()
    val time = local.gameTimeLabel()
    val status = agendaStatus()
    val statusLabel = getString(status.labelResource())
    return GroupAgendaRowUi(
        gameId = id,
        day = local.day.toString(),
        month = gameShortMonthLabel(local.date.month.ordinal + 1),
        title = getString(Res.string.home_upcoming_row_title, weekday, time),
        meta = agendaMeta(defaultVenueName),
        status = status,
        statusLabel = statusLabel,
        contentDescription = getString(Res.string.home_upcoming_cd_row, weekday, local.date.gameDateLabel(), time, statusLabel),
    )
}

// A ordem é a regra: rascunho não tem lotação para contar; lotado importa mais que o local.
// Sem quadra padrão no grupo (`null`), todo jogo mostra o próprio local.
private suspend fun Game.agendaMeta(defaultVenueName: String?): String = when {
    status == GameStatus.Draft -> getString(Res.string.group_details_agenda_meta_draft)
    availableSpots <= 0 -> getString(Res.string.group_details_agenda_meta_full, confirmedCount, capacity)
    venue.name != defaultVenueName ->
        getString(Res.string.group_details_agenda_meta_venue, confirmedCount, capacity, venue.name)
    else -> getString(Res.string.group_details_agenda_meta, confirmedCount, capacity)
}

private fun Game.agendaStatus(): GroupAgendaStatus = when {
    status == GameStatus.Draft -> GroupAgendaStatus.Draft
    ownAttendance == AttendanceStatus.Confirmed -> GroupAgendaStatus.Going
    ownAttendance == AttendanceStatus.Declined -> GroupAgendaStatus.Out
    ownAttendance == AttendanceStatus.Waitlisted -> GroupAgendaStatus.Waitlisted
    else -> GroupAgendaStatus.Pending
}

private fun GroupAgendaStatus.labelResource(): StringResource = when (this) {
    GroupAgendaStatus.Pending -> Res.string.home_admin_score_pending
    GroupAgendaStatus.Going -> Res.string.home_upcoming_status_going
    GroupAgendaStatus.Out -> Res.string.home_upcoming_status_out
    GroupAgendaStatus.Waitlisted -> Res.string.home_upcoming_status_waitlisted
    GroupAgendaStatus.Draft -> Res.string.group_details_agenda_status_draft
}
```

### 5. Criar `PRES/details/GroupWaiting.kt` (arquivo completo)

```kotlin
package br.com.saqz.groups.presentation.details

import br.com.saqz.core.common.formatting.formatBrl
import br.com.saqz.groups.domain.finance.Charge
import br.com.saqz.groups.domain.finance.ChargeKind
import br.com.saqz.groups.domain.finance.ChargeStatus
import br.com.saqz.groups.domain.game.Game
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.membership.GroupEntryRequest
import br.com.saqz.groups.presentation.game.gameDateLabel
import br.com.saqz.groups.presentation.game.gameShortMonthLabel
import br.com.saqz.groups.presentation.game.gameTimeZone
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.group_details_names_more
import br.com.saqz.groups.resources.group_details_names_two
import br.com.saqz.groups.resources.home_admin_waiting_entry_requests
import br.com.saqz.groups.resources.home_admin_waiting_monthly
import br.com.saqz.groups.resources.home_admin_waiting_monthly_meta
import br.com.saqz.groups.resources.home_admin_waiting_settle
import br.com.saqz.groups.resources.home_admin_waiting_settle_meta
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.getString
import kotlin.time.Instant

/**
 * "Esperando você" do gestor, com as mesmas três linhas e as mesmas chaves da Início:
 *
 * - **mensalidades a receber**: só as pendentes da competência [monthKey] (o mês corrente no
 *   fuso de cobrança do grupo) — a mesma regra da Início; atraso de mês anterior é assunto do
 *   caixa, não desta linha;
 * - **acertar o jogo**: o jogo concluído MAIS RECENTE que ainda tem cobrança avulsa pendente.
 *   Some enquanto o guia de onboarding `ReviewFinances` fala desse mesmo jogo
 *   ([reviewingGameId]): duas chamadas para o mesmo acerto na mesma tela é ruído;
 * - **pedidos para entrar**: quem pediu, pelo nome.
 *
 * Nenhuma linha → `null`, e o bloco não existe.
 */
internal suspend fun groupWaiting(
    charges: List<Charge>,
    games: List<Game>,
    entryRequests: List<GroupEntryRequest>,
    monthKey: String,
    reviewingGameId: String?,
): GroupWaitingUi? {
    val pending = charges.filter { it.status == ChargeStatus.Pending }
    val waiting = GroupWaitingUi(
        entryRequests = entryRequests.toEntryRequestsRow(),
        monthly = pending.toMonthlyRow(monthKey),
        settle = games.toSettleRow(pending, reviewingGameId),
    )
    return waiting.takeIf { it.entryRequests != null || it.monthly != null || it.settle != null }
}

private suspend fun List<Charge>.toMonthlyRow(monthKey: String): GroupWaitingRowUi? {
    val open = filter { it.kind == ChargeKind.Monthly && it.month == monthKey }
    if (open.isEmpty()) return null
    val month = runCatching { LocalDate.parse("$monthKey-01") }.getOrNull()?.let { gameShortMonthLabel(it.month.ordinal + 1) }.orEmpty()
    val title = getString(Res.string.home_admin_waiting_monthly, open.size)
    val meta = getString(Res.string.home_admin_waiting_monthly_meta, formatBrl(open.sumOf { it.amountCents }), month)
    return GroupWaitingRowUi(title = title, meta = meta, contentDescription = "$title. $meta", count = open.size)
}

private suspend fun List<Game>.toSettleRow(pending: List<Charge>, reviewingGameId: String?): GroupSettleRowUi? {
    val dayCharges = pending.filter { it.kind == ChargeKind.Game }
    val latest = filter { game -> game.status == GameStatus.Completed && dayCharges.any { it.gameId == game.id } }
        .mapNotNull { game -> runCatching { Instant.parse(game.startsAt) }.getOrNull()?.let { it to game } }
        .maxByOrNull { it.first }
    if (latest == null || latest.second.id == reviewingGameId) return null
    val (startsAt, game) = latest
    val open = dayCharges.filter { it.gameId == game.id }
    val date = startsAt.toLocalDateTime(gameTimeZone(game.zoneId)).date.gameDateLabel()
    val title = getString(Res.string.home_admin_waiting_settle, date)
    val meta = getString(Res.string.home_admin_waiting_settle_meta, open.size, formatBrl(open.sumOf { it.amountCents }))
    return GroupSettleRowUi(gameId = game.id, title = title, meta = meta, contentDescription = "$title. $meta")
}

private suspend fun List<GroupEntryRequest>.toEntryRequestsRow(): GroupWaitingRowUi? {
    if (isEmpty()) return null
    val names = map { it.displayName }
    val title = getString(Res.string.home_admin_waiting_entry_requests, size)
    val meta = when (size) {
        1 -> names.single()
        2 -> getString(Res.string.group_details_names_two, names[0], names[1])
        else -> getString(Res.string.group_details_names_more, names[0], names[1], size - 2)
    }
    return GroupWaitingRowUi(title = title, meta = meta, contentDescription = "$title. $meta", count = size)
}
```

### 6. Editar `PRES/details/GroupDetailsViewModel.kt` (código como fica DEPOIS do V1)

6.1. Imports — acrescentar cada um logo depois da linha citada (sem reordenar o que existe):

- depois de `import br.com.saqz.groups.domain.athlete.AthleteGateway`:

```kotlin
import br.com.saqz.groups.domain.athlete.AthleteRosterFilter
```

- depois de `import br.com.saqz.groups.domain.membership.GroupDepartureGateway`:

```kotlin
import br.com.saqz.groups.domain.membership.GroupEntryRequest
import br.com.saqz.groups.domain.membership.GroupEntryRequestGateway
```

- depois de `import br.com.saqz.groups.resources.Res`:

```kotlin
import br.com.saqz.groups.resources.group_details_cash_balance
```

6.2. No construtor, trocar

```kotlin
    private val communications: CommunicationGateway,
) : MviViewModel<GroupDetailsState, GroupDetailsIntent, GroupDetailsEffect>(GroupDetailsState()) {
```

por

```kotlin
    private val communications: CommunicationGateway,
    private val entryRequests: GroupEntryRequestGateway,
) : MviViewModel<GroupDetailsState, GroupDetailsIntent, GroupDetailsEffect>(GroupDetailsState()) {
```

6.3. Em `onIntent`, logo depois da linha `            is GroupDetailsIntent.ToggleAutoConfirmation -> toggleAutoConfirmation(intent.enabled)`, acrescentar:

```kotlin
            is GroupDetailsIntent.OpenAgendaGame -> emit(GroupDetailsEffect.OpenGame(groupId, intent.gameId))
            is GroupDetailsIntent.OpenSettlement -> emit(GroupDetailsEffect.OpenSettlement(groupId, intent.gameId))
```

6.4. Em `load()`, trocar

```kotlin
                            loadNextGame(generation, group, gamesResult.value)
                            loadAdminCashbox(generation, group)
                            loadOwnCharges(generation, group)
                            loadLatestNotice(generation)
```

por

```kotlin
                            val games = gamesResult.value
                            loadNextGame(generation, group, games)
                            loadAgenda(generation, group, games)
                            loadAdminCashbox(generation, group, games)
                            loadOwnCharges(generation, group)
                            loadPeople(generation)
                            loadLatestNotice(generation)
```

`load()` NÃO zera `agenda` nem `waiting` no primeiro `update`: o valor anterior fica até a carga nova publicar o seu.

6.5. Logo ANTES da linha `    private suspend fun loadLatestNotice(generation: Int) {`, acrescentar:

```kotlin
    /**
     * "Próximos jogos" sem o jogo do hero. Roda depois de [loadNextGame], que acabou de
     * publicar o hero desta mesma geração no estado — é dele que sai o id a pular.
     */
    private suspend fun loadAgenda(generation: Int, group: Group, games: List<Game>) {
        if (generation != loadGeneration) return
        val agenda = groupAgenda(games, state.value.nextGame?.gameId, now.now(), group.profile?.defaultVenue?.name)
        // Formatar suspende (`getString`): a guarda é re-checada depois, não antes.
        if (generation != loadGeneration) return
        update { it.copy(agenda = agenda) }
    }

    /**
     * "Galera": a contagem e os quatro primeiros do roster de atletas. Falha aqui não derruba
     * a tela nem apaga o que já estava nela — o bloco só fica com o que tinha.
     */
    private suspend fun loadPeople(generation: Int) {
        if (generation != loadGeneration) return
        val result = athleteGateway.roster(GroupId(groupId), AthleteRosterFilter())
        if (generation != loadGeneration) return
        val roster = (result as? SaqzResult.Success)?.value ?: return
        update {
            it.copy(
                memberCount = roster.size,
                memberPreview = roster.take(MEMBER_PREVIEW_LIMIT).map { member ->
                    MemberPreviewUi(id = member.userId, name = member.displayName, meta = "")
                },
            )
        }
    }

```

6.6. Substituir a função `loadAdminCashbox` inteira (do `@Suppress("ReturnCount")` que a antecede até o `}` que a fecha — o trecho antigo termina em `update { it.copy(cashbox = cashbox) }` + `}`) por:

```kotlin
    /**
     * Caixa e "Esperando você" do gestor saem das mesmas leituras. Falha de finanças degrada
     * sem derrubar a tela: o caixa fica sem saldo e a espera só com o que não depende de
     * finanças (pedidos para entrar). Atleta não tem nenhum dos dois.
     */
    @Suppress("ReturnCount")
    private suspend fun loadAdminCashbox(generation: Int, group: Group, games: List<Game>) {
        if (generation != loadGeneration) return
        if (group.role == GroupRole.ATHLETE) {
            update { it.copy(cashbox = null, waiting = null) }
            return
        }
        val monthKey = currentDate(group.timeZone.id).monthKey()
        val statementResult = statementGateway.statement(
            GroupId(groupId),
            FinanceStatementQuery(month = monthKey),
        )
        if (generation != loadGeneration) return
        val chargesResult = organizerFinanceGateway.charges(GroupId(groupId))
        if (generation != loadGeneration) return
        val requests = loadEntryRequests(group)
        if (generation != loadGeneration) return
        val cashbox: CashboxUi
        val charges: List<Charge>
        if (statementResult is SaqzResult.Success && chargesResult is SaqzResult.Success) {
            val balance = formatBrl(statementResult.value.summary.accumulatedBalanceCents)
            cashbox = CashboxUi(summary = getString(Res.string.group_details_cash_balance, balance))
            charges = chargesResult.value.charges
        } else {
            cashbox = CashboxUi()
            charges = emptyList()
        }
        // O guia `ReviewFinances` desta geração já foi publicado por `loadNextGame`.
        val reviewingGameId = (state.value.onboarding as? GroupOnboarding.ReviewFinances)?.gameId
        val waiting = groupWaiting(charges, games, requests, monthKey, reviewingGameId)
        // Formatar suspende (`getString`): a guarda é re-checada depois, não antes.
        if (generation != loadGeneration) return
        update { it.copy(cashbox = cashbox, waiting = waiting) }
    }

    /** Pedido só existe em grupo com aprovação. Falha vira lista vazia: nunca derruba a tela. */
    private suspend fun loadEntryRequests(group: Group): List<GroupEntryRequest> {
        if (!group.entryRequiresApproval) return emptyList()
        return (entryRequests.list(GroupId(groupId)) as? SaqzResult.Success)?.value.orEmpty()
    }
```

(O `@Suppress("ReturnCount")` já existia nesta função — não é supressão nova.)

6.7. Em `private fun GroupDetailsState.from(group: Group)`, logo depois da linha `        venue = profile?.defaultVenue?.let { VenueUi(it.name, it.address) },`, acrescentar:

```kotlin
        scheduleSummary = profile?.regularSlots?.let { slots -> groupScheduleSummary(slots) { it.label() } },
```

6.8. No fim do bloco de constantes, logo depois da linha `private const val PIX_COPIED_DWELL_MILLIS = 2_000L` (criada pelo V1), acrescentar:

```kotlin
private const val MEMBER_PREVIEW_LIMIT = 4
```

### 7. Editar `PRES/di/GroupsPresentationModule.kt`

Trocar a linha

```kotlin
        GroupDetailsViewModel(params.get(), get(), get(), get(), get(), get(), get(), get(), get<GroupNowPort>(), get(), get())
```

por

```kotlin
        GroupDetailsViewModel(params.get(), get(), get(), get(), get(), get(), get(), get(), get<GroupNowPort>(), get(), get(), get())
```

(134 colunas: cabe no `MaxLineLength` de 140.)

### 8. Editar `APP/commonMain/kotlin/br/com/saqz/composeapp/navigation/SaqzNavHost.kt`

Três trocas; cada âncora aparece UMA vez no arquivo. O molde é o `onBack` do caixa que já existe (`onBack = { groupCashboxRefreshVersion++; groupDetailsRefreshVersion++; pop() },`).

8.1. Em `entry<GroupsRoute.Invite>`, trocar

```kotlin
                GroupInviteRoot(
                    groupId = route.groupId,
                    onBack = pop,
```

por

```kotlin
                GroupInviteRoot(
                    groupId = route.groupId,
                    // Pedido aprovado muda "Esperando você" e a contagem da galera do detalhe.
                    onBack = { groupDetailsRefreshVersion++; pop() },
```

8.2. Em `entry<GroupsRoute.Members>`, trocar

```kotlin
                GroupMembersRoot(
                    groupId = route.groupId,
                    onBack = pop,
```

por

```kotlin
                GroupMembersRoot(
                    groupId = route.groupId,
                    // Membro removido muda a contagem e a prévia da galera do detalhe.
                    onBack = { groupDetailsRefreshVersion++; pop() },
```

8.3. Em `entry<GroupsRoute.GameDetail>`, trocar

```kotlin
                GameDetailRoot(
                    groupId = route.groupId,
                    gameId = route.gameId,
                    onBack = pop,
```

por

```kotlin
                GameDetailRoot(
                    groupId = route.groupId,
                    gameId = route.gameId,
                    // Resposta dada no jogo muda o placar do hero e o chip da agenda do detalhe.
                    onBack = { groupDetailsRefreshVersion++; pop() },
```

### 9. Editar `APP/commonTest/kotlin/br/com/saqz/composeapp/di/SaqzKoinModulesTest.kt`

9.1. Nos imports, logo depois de `import br.com.saqz.groups.data.di.groupsDataModule`, acrescentar:

```kotlin
import br.com.saqz.groups.data.di.inviteManagementDataModule
```

9.2. Em `groupsPresentationModuleResolvesWithTheRouteArguments`, trocar

```kotlin
                groupsDataModule(),
                groupsPresentationModule(),
```

por

```kotlin
                groupsDataModule(),
                // O detalhe do grupo lista os pedidos de entrada: o binding mora no módulo do convite.
                inviteManagementDataModule(),
                groupsPresentationModule(),
```

### 10. Editar `E2E/FinancialFlowsE2eTest.kt`

O texto do saldo muda neste PR, então o roteiro muda no mesmo PR. Três trocas:

10.1. Trocar a linha (aparece uma vez)

```kotlin
        waitText("Saldo $amount · 0 mensalidades em aberto")
```

por

```kotlin
        waitText("Saldo $amount")
```

10.2. Trocar

```kotlin
        waitText("Saldo R$ 3,45 · 0 mensalidades em aberto")
        ui.onNodeWithText("Saldo R$ 3,45 · 0 mensalidades em aberto").performScrollTo().assertIsDisplayed()
```

por

```kotlin
        waitText("Saldo R$ 3,45")
        ui.onNodeWithText("Saldo R$ 3,45").performScrollTo().assertIsDisplayed()
```

### 11. Editar `TEST/GroupsGatewayFakes.kt`

11.1. Imports: logo ANTES de `import br.com.saqz.domain.GroupId`, acrescentar

```kotlin
import br.com.saqz.domain.EmptyResult
```

e, logo depois de `import br.com.saqz.groups.domain.membership.ChangeMembershipRoleCommand`, acrescentar

```kotlin
import br.com.saqz.groups.domain.membership.EntryRequestError
import br.com.saqz.groups.domain.membership.GroupEntryRequest
import br.com.saqz.groups.domain.membership.GroupEntryRequestGateway
```

11.2. Logo ANTES da linha `class FakeGroupSystemTimeZonePort : GroupSystemTimeZonePort {`, acrescentar:

```kotlin
class FakeGroupEntryRequestGateway(
    var listResult: SaqzResult<List<GroupEntryRequest>, EntryRequestError> = SaqzResult.Success(emptyList()),
) : GroupEntryRequestGateway {
    var listCalls = 0
    var listDeferred: CompletableDeferred<SaqzResult<List<GroupEntryRequest>, EntryRequestError>>? = null

    override suspend fun list(groupId: GroupId): SaqzResult<List<GroupEntryRequest>, EntryRequestError> {
        listCalls++
        return listDeferred?.await() ?: listResult
    }

    override suspend fun approve(groupId: GroupId, userId: String): SaqzResult<GroupMembership, EntryRequestError> =
        error("not used in this screen")

    override suspend fun reject(groupId: GroupId, userId: String): EmptyResult<EntryRequestError> =
        error("not used in this screen")
}

```

### 12. Editar os três testes de Root que constroem a ViewModel à mão

12.1. `TEST/ui/details/GroupDetailsRootTest.kt` — em `private fun detailsViewModel(`, logo depois da linha `        communications = br.com.saqz.groups.presentation.FakeCommunicationGateway(),`, acrescentar:

```kotlin
        entryRequests = br.com.saqz.groups.presentation.FakeGroupEntryRequestGateway(),
```

12.2. `TEST/ui/finance/settlement/GameSettlementRootTest.kt`:

- no `GroupDetailsViewModel(` do teste `recebi diarist then back reloads the group details cashbox summary`, logo depois da linha `            communications = br.com.saqz.groups.presentation.FakeCommunicationGateway(),`, acrescentar:

```kotlin
            entryRequests = br.com.saqz.groups.presentation.FakeGroupEntryRequestGateway(),
```

- trocar `        onNodeWithText("Saldo R$ 70,00 · 0 mensalidades em aberto").assertExists()` por:

```kotlin
        onNodeWithText("Saldo R$ 70,00").assertExists()
```

(O saldo aqui já mudava de 0 para 70 entre antes e depois da recarga: a prova continua a mesma.)

12.3. `TEST/ui/finance/groupcash/GroupCashboxRootTest.kt`:

- no `GroupDetailsViewModel(`, logo depois da linha `            communications = br.com.saqz.groups.presentation.FakeCommunicationGateway(),`, acrescentar:

```kotlin
            entryRequests = br.com.saqz.groups.presentation.FakeGroupEntryRequestGateway(),
```

- trocar

```kotlin
                            onMutationSuccess = {
                                detailsGateway.chargesResult = SaqzResult.Success(ChargeList(emptyList()))
                                refreshVersion++
                            },
```

por

```kotlin
                            onMutationSuccess = {
                                detailsGateway.chargesResult = SaqzResult.Success(ChargeList(emptyList()))
                                // A frase do caixa do detalhe agora é só o saldo: é ele que prova a recarga.
                                statementGateway.result = SaqzResult.Success(
                                    FinanceStatementPage(
                                        month = "2026-08",
                                        items = emptyList(),
                                        summary = FinanceStatementSummary(0L, 0L, 0L, 7_000L),
                                        limit = 20,
                                        offset = 0,
                                        hasMore = false,
                                    ),
                                )
                                refreshVersion++
                            },
```

- trocar `        onNodeWithText("Saldo R$ 0,00 · 0 mensalidades em aberto").assertExists()` por:

```kotlin
        onNodeWithText("Saldo R$ 70,00").assertExists()
```

(`FinanceStatementPage` e `FinanceStatementSummary` já estão importados neste arquivo: o teste monta o extrato inicial com eles.)

## Testes

Todos em `commonTest` (rodam em `iosSimulatorArm64Test`). Em todo literal monetário dos testes o espaço é ` ` (o `formatBrl` usa NBSP) — escrever o escape, nunca um espaço digitado.

### `TDATA/game/KtorGameGatewayTest.kt`

Nos imports, logo depois de `import br.com.saqz.domain.SaqzResult`, acrescentar:

```kotlin
import br.com.saqz.groups.domain.attendance.AttendanceStatus
```

Logo depois do teste `` `list preserves null venue id` `` (o que termina em `assertNull(successList().single().venue.venueId)` + `}`), acrescentar:

```kotlin

    @Test fun `list maps confirmed own attendance`() = ownAttendanceCase(ownAttendanceJson("CONFIRMED"), AttendanceStatus.Confirmed)
    @Test fun `list maps declined own attendance`() = ownAttendanceCase(ownAttendanceJson("DECLINED"), AttendanceStatus.Declined)
    @Test fun `list maps waitlisted own attendance`() =
        ownAttendanceCase(ownAttendanceJson("WAITLISTED", position = "2"), AttendanceStatus.Waitlisted)
    @Test fun `list keeps explicit null own attendance as no response`() = ownAttendanceCase("null", null)
    @Test fun `list without the own attendance key means no response`() = ownAttendanceCase(null, null)
```

E, logo ANTES de `    private suspend fun successList() =`, acrescentar:

```kotlin
    /** [raw] é o valor JSON de `ownAttendance`; `null` deixa a chave AUSENTE do payload. */
    private fun ownAttendanceCase(raw: String?, expected: AttendanceStatus?) = runTest {
        val body = if (raw == null) GAME_JSON else GAME_JSON.dropLast(1) + ",\"ownAttendance\":$raw}"
        val result = gateway { respond("[$body]", headers = jsonHeaders()) }.list(GROUP)
        val games = assertIs<SaqzResult.Success<List<br.com.saqz.groups.domain.game.Game>>>(result).value
        assertEquals(expected, games.single().ownAttendance)
    }

    private fun ownAttendanceJson(status: String, position: String = "null") =
        """{"memberId":"member-1","status":"$status","waitlistPosition":$position,"version":1}"""

```

### `TEST/details/GroupAgendaTest.kt` (arquivo completo)

```kotlin
package br.com.saqz.groups.presentation.details

import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.domain.game.Game
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.game.GameVenue
import br.com.saqz.groups.domain.group.GroupRegularSlot
import br.com.saqz.groups.domain.group.GroupWeekday
import br.com.saqz.groups.presentation.sampleGame
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class GroupAgendaTest {
    private val now = Instant.parse("2026-08-01T00:00:00Z")
    private val weekdays = mapOf(
        GroupWeekday.MONDAY to "Segunda",
        GroupWeekday.TUESDAY to "Terça",
        GroupWeekday.WEDNESDAY to "Quarta",
        GroupWeekday.THURSDAY to "Quinta",
        GroupWeekday.FRIDAY to "Sexta",
    )

    @Test
    fun `agenda keeps upcoming published and draft games without the hero sorted by start`() = runTest {
        val games = listOf(
            game("late", "2026-08-13T19:30:00-03:00"),
            game("hero", "2026-08-04T19:30:00-03:00"),
            game("draft", "2026-08-11T19:30:00-03:00").copy(status = GameStatus.Draft),
            game("next", "2026-08-06T19:30:00-03:00"),
            game("past", "2026-07-28T19:30:00-03:00"),
            game("cancelled", "2026-08-07T19:30:00-03:00").copy(status = GameStatus.Cancelled),
            game("completed", "2026-08-08T19:30:00-03:00").copy(status = GameStatus.Completed),
            game("broken", "sem data"),
        )

        assertEquals(listOf("next", "draft", "late"), groupAgenda(games, "hero", now, "CERET").map { it.gameId })
    }

    @Test
    fun `row is written in the game time zone with the home keys`() = runTest {
        // 01h30 UTC de sexta ainda é quinta, 22h30, em São Paulo.
        val game = game("game-2", "2026-08-07T01:30:00Z").copy(ownAttendance = AttendanceStatus.Confirmed)

        assertEquals(
            GroupAgendaRowUi(
                gameId = "game-2",
                day = "6",
                month = "AGO",
                title = "Quinta · 22h30",
                meta = "8 de 12 confirmados",
                status = GroupAgendaStatus.Going,
                statusLabel = "Você vai",
                contentDescription = "Quinta, 06/08 às 22h30, Você vai",
            ),
            groupAgenda(listOf(game), null, now, "CERET").single(),
        )
    }

    @Test
    fun `meta prefers draft then full then another venue then the plain count`() = runTest {
        val base = game("g", "2026-08-06T19:30:00-03:00")
        val elsewhere = base.copy(venue = GameVenue(name = "Arena Mooca", address = "Av. Paes de Barros, 1000"))
        val full = elsewhere.copy(confirmedCount = 12, availableSpots = 0)

        assertEquals("Só você vê até publicar", meta(full.copy(status = GameStatus.Draft)))
        assertEquals("Lotado · 12 de 12", meta(full))
        assertEquals("8 de 12 · Arena Mooca", meta(elsewhere))
        assertEquals("8 de 12 confirmados", meta(base))
    }

    @Test
    fun `group without a default venue always names the game venue`() = runTest {
        val row = groupAgenda(listOf(game("g", "2026-08-06T19:30:00-03:00")), null, now, defaultVenueName = null).single()

        assertEquals("8 de 12 · CERET", row.meta)
    }

    @Test
    fun `status follows the own attendance and draft wins`() = runTest {
        val base = game("g", "2026-08-06T19:30:00-03:00")

        assertEquals(GroupAgendaStatus.Pending to "Sem resposta", status(base))
        assertEquals(GroupAgendaStatus.Going to "Você vai", status(base.copy(ownAttendance = AttendanceStatus.Confirmed)))
        assertEquals(GroupAgendaStatus.Out to "Não vai", status(base.copy(ownAttendance = AttendanceStatus.Declined)))
        assertEquals(GroupAgendaStatus.Waitlisted to "Na espera", status(base.copy(ownAttendance = AttendanceStatus.Waitlisted)))
        assertEquals(
            GroupAgendaStatus.Draft to "Rascunho",
            status(base.copy(status = GameStatus.Draft, ownAttendance = AttendanceStatus.Confirmed)),
        )
    }

    @Test
    fun `agenda stops at twelve rows`() = runTest {
        val games = (1..14).map { game("g$it", "2026-08-${(it + 1).toString().padStart(2, '0')}T19:30:00-03:00") }

        assertEquals((1..12).map { "g$it" }, groupAgenda(games, null, now, "CERET").map { it.gameId })
    }

    @Test
    fun `schedule summary joins distinct days in week order and appends a shared time`() {
        assertEquals("Terça · 19h30", summary(slot(GroupWeekday.TUESDAY)))
        assertEquals(
            "Terça e Quinta · 19h30",
            summary(slot(GroupWeekday.THURSDAY), slot(GroupWeekday.TUESDAY), slot(GroupWeekday.TUESDAY)),
        )
        assertEquals(
            "Segunda, Quarta e Sexta · 19h30",
            summary(slot(GroupWeekday.FRIDAY), slot(GroupWeekday.MONDAY), slot(GroupWeekday.WEDNESDAY)),
        )
    }

    @Test
    fun `schedule summary drops the time when slots disagree and is null without slots`() {
        assertEquals("Terça e Quinta", summary(slot(GroupWeekday.TUESDAY), slot(GroupWeekday.THURSDAY, "20:00")))
        assertNull(summary())
    }

    private suspend fun meta(game: Game) = groupAgenda(listOf(game), null, now, "CERET").single().meta

    private suspend fun status(game: Game) =
        groupAgenda(listOf(game), null, now, "CERET").single().let { it.status to it.statusLabel }

    private fun summary(vararg slots: GroupRegularSlot) = groupScheduleSummary(slots.toList()) { weekdays.getValue(it) }

    private fun slot(weekday: GroupWeekday, startTime: String = "19:30") =
        GroupRegularSlot(weekday = weekday, startTime = startTime, durationMinutes = 120)

    // `sampleGame()`: CERET, 8 de 12, 4 vagas, America/Sao_Paulo, publicado.
    private fun game(id: String, startsAt: String) = sampleGame().copy(id = id, startsAt = startsAt)
}
```

### `TEST/details/GroupWaitingTest.kt` (arquivo completo)

```kotlin
package br.com.saqz.groups.presentation.details

import br.com.saqz.domain.GroupId
import br.com.saqz.groups.domain.finance.Charge
import br.com.saqz.groups.domain.finance.ChargeKind
import br.com.saqz.groups.domain.finance.ChargeStatus
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.membership.GroupEntryRequest
import br.com.saqz.groups.presentation.sampleGame
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GroupWaitingTest {
    @Test
    fun `nothing pending means no block`() = runTest {
        assertNull(groupWaiting(emptyList(), emptyList(), emptyList(), "2026-08", reviewingGameId = null))
    }

    @Test
    fun `monthly row sums only the pending charges of the current month`() = runTest {
        val charges = listOf(
            monthly("a", "2026-08", 7_000L),
            monthly("b", "2026-08", 6_500L),
            monthly("julho", "2026-07", 7_000L),
            monthly("paga", "2026-08", 7_000L, ChargeStatus.Paid),
        )

        val waiting = assertNotNull(groupWaiting(charges, emptyList(), emptyList(), "2026-08", reviewingGameId = null))

        assertEquals(
            GroupWaitingRowUi(
                title = "2 mensalidades a receber",
                meta = "R$ 135,00 · AGO",
                contentDescription = "2 mensalidades a receber. R$ 135,00 · AGO",
                count = 2,
            ),
            waiting.monthly,
        )
        assertNull(waiting.settle)
        assertNull(waiting.entryRequests)
    }

    @Test
    fun `settle row points at the most recent completed game that still has pending day charges`() = runTest {
        val games = listOf(
            completed("old", "2026-07-21T19:30:00-03:00"),
            completed("done", "2026-07-28T19:30:00-03:00"),
            completed("clean", "2026-07-30T19:30:00-03:00"),
            sampleGame(),
        )
        val charges = listOf(
            dayCharge("c1", "old"),
            dayCharge("c2", "done"),
            dayCharge("c3", "done", 3_000L),
            dayCharge("c4", "clean", status = ChargeStatus.Paid),
            dayCharge("c5", sampleGame().id),
        )

        val waiting = assertNotNull(groupWaiting(charges, games, emptyList(), "2026-08", reviewingGameId = null))

        assertEquals(
            GroupSettleRowUi(
                gameId = "done",
                title = "Acertar o jogo de 28/07",
                meta = "2 avulsos · R$ 55,00 a receber",
                contentDescription = "Acertar o jogo de 28/07. 2 avulsos · R$ 55,00 a receber",
            ),
            waiting.settle,
        )
    }

    @Test
    fun `settle row steps aside while the onboarding guide reviews that same game`() = runTest {
        val games = listOf(completed("done", "2026-07-28T19:30:00-03:00"))
        val charges = listOf(dayCharge("c1", "done"))

        assertNull(groupWaiting(charges, games, emptyList(), "2026-08", reviewingGameId = "done"))
        assertNotNull(groupWaiting(charges, games, emptyList(), "2026-08", reviewingGameId = "other")?.settle)
    }

    @Test
    fun `entry requests name one two or many people`() = runTest {
        val one = assertNotNull(waitingFor("Ana").entryRequests)
        val two = assertNotNull(waitingFor("Ana", "Bia").entryRequests)
        val many = assertNotNull(waitingFor("Ana", "Bia", "Caio", "Duda").entryRequests)

        assertEquals("Ana" to 1, one.meta to one.count)
        assertEquals("Ana e Bia" to 2, two.meta to two.count)
        assertEquals(
            GroupWaitingRowUi(
                title = "4 pedidos para entrar",
                meta = "Ana, Bia e mais 2",
                contentDescription = "4 pedidos para entrar. Ana, Bia e mais 2",
                count = 4,
            ),
            many,
        )
    }

    @Test
    fun `the three rows come together`() = runTest {
        val waiting = assertNotNull(
            groupWaiting(
                charges = listOf(monthly("a", "2026-08", 7_000L), dayCharge("c1", "done")),
                games = listOf(completed("done", "2026-07-28T19:30:00-03:00")),
                entryRequests = listOf(request("Ana")),
                monthKey = "2026-08",
                reviewingGameId = null,
            ),
        )

        assertNotNull(waiting.entryRequests)
        assertNotNull(waiting.monthly)
        assertEquals("done", waiting.settle?.gameId)
    }

    private suspend fun waitingFor(vararg names: String) = assertNotNull(
        groupWaiting(emptyList(), emptyList(), names.map(::request), "2026-08", reviewingGameId = null),
    )

    private fun request(name: String) =
        GroupEntryRequest(userId = name.lowercase(), displayName = name, requestedAt = "2026-08-01T12:00:00Z")

    private fun completed(id: String, startsAt: String) =
        sampleGame().copy(id = id, status = GameStatus.Completed, startsAt = startsAt)

    private fun monthly(id: String, month: String, cents: Long, status: ChargeStatus = ChargeStatus.Pending) = Charge(
        id = id,
        groupId = GroupId("group-1"),
        memberId = "member-$id",
        kind = ChargeKind.Monthly,
        month = month,
        amountCents = cents,
        dueDate = "$month-10",
        status = status,
        version = 1,
        audit = emptyList(),
    )

    private fun dayCharge(id: String, gameId: String, cents: Long = 2_500L, status: ChargeStatus = ChargeStatus.Pending) = Charge(
        id = id,
        groupId = GroupId("group-1"),
        memberId = "member-$id",
        kind = ChargeKind.Game,
        gameId = gameId,
        amountCents = cents,
        dueDate = "2026-07-28",
        status = status,
        version = 1,
        audit = emptyList(),
    )
}
```

### `TEST/details/GroupDetailsViewModelTest.kt`

**A. Imports** — acrescentar junto dos demais `br.com.saqz.groups…` (não duplicar o que o V1 já trouxe):

```kotlin
import br.com.saqz.groups.domain.athlete.AthleteRosterFilter
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.membership.EntryRequestError
import br.com.saqz.groups.domain.membership.GroupEntryRequest
import br.com.saqz.groups.presentation.FakeGroupEntryRequestGateway
import br.com.saqz.groups.presentation.sampleRosterEntry
```

**B. Helper `viewModel(...)`** — trocar

```kotlin
        communications: br.com.saqz.groups.presentation.FakeCommunicationGateway = br.com.saqz.groups.presentation.FakeCommunicationGateway(),
    ) = GroupDetailsViewModel(
```

por

```kotlin
        communications: br.com.saqz.groups.presentation.FakeCommunicationGateway = br.com.saqz.groups.presentation.FakeCommunicationGateway(),
        entryRequests: FakeGroupEntryRequestGateway = FakeGroupEntryRequestGateway(),
    ) = GroupDetailsViewModel(
```

e, na lista de argumentos, trocar

```kotlin
        departureGateway,
        communications,
    )
```

por

```kotlin
        departureGateway,
        communications,
        entryRequests,
    )
```

**C. Os dois testes do saldo mudam de expectativa — e por quê.** A frase do caixa deixou de contar mensalidades: a contagem foi para `waiting.monthly`, com a regra de produto da Início (só o mês corrente). Por isso:

C.1. No teste `admin details expose cashbox summary from finance gateways`, trocar

```kotlin
        assertEquals("Saldo R$ 380,00 · 3 mensalidades em aberto", viewModel.state.value.cashbox?.summary)
```

por

```kotlin
        assertEquals("Saldo R$ 380,00", viewModel.state.value.cashbox?.summary)
        // A contagem saiu da frase do caixa: mora em "Esperando você", só com o mês corrente
        // (grupo em UTC + `now` de 01/08 ⇒ competência 2026-08; a de julho fica de fora).
        assertEquals("2 mensalidades a receber", viewModel.state.value.waiting?.monthly?.title)
```

C.2. O teste `admin cashbox counts pending monthly charges from previous months` afirmava uma regra que deixou de existir (contar mês anterior na frase do caixa). Trocar o NOME do teste

```kotlin
    fun `admin cashbox counts pending monthly charges from previous months`() = runTest {
```

por

```kotlin
    fun `admin cashbox summary carries only the balance`() = runTest {
```

e a asserção

```kotlin
        assertEquals("Saldo R$ 0,00 · 1 mensalidades em aberto", viewModel.state.value.cashbox?.summary)
```

por

```kotlin
        assertEquals("Saldo R$ 0,00", viewModel.state.value.cashbox?.summary)
```

(O arranjo do teste fica igual. A regra "mês anterior não entra" ganha teste próprio abaixo, com `now` explícito — neste arranjo o `now` padrão ainda é julho em São Paulo.)

**D. Testes novos** — acrescentar logo ANTES de `    private fun waitlistedDetail(` (helper criado pelo V1):

```kotlin
    @Test
    fun `agenda lists the upcoming games after the hero and keeps the drafts the backend sent`() = runTest {
        val games = listOf(
            sampleGame(),
            sampleGame().copy(id = "draft-1", status = GameStatus.Draft, startsAt = "2026-08-11T19:30:00-03:00"),
            sampleGame().copy(
                id = "game-2",
                startsAt = "2026-08-06T19:30:00-03:00",
                ownAttendance = AttendanceStatus.Confirmed,
            ),
        )
        val viewModel = viewModel(gameGateway = FakeGameGateway(listResult = SaqzResult.Success(games)))

        assertEquals("game-1", viewModel.state.value.nextGame?.gameId)
        val agenda = viewModel.state.value.agenda
        assertEquals(listOf("game-2", "draft-1"), agenda.map { it.gameId })
        assertEquals(
            GroupAgendaRowUi(
                gameId = "game-2",
                day = "6",
                month = "AGO",
                title = "Quinta · 19h30",
                meta = "8 de 12 confirmados",
                status = GroupAgendaStatus.Going,
                statusLabel = "Você vai",
                contentDescription = "Quinta, 06/08 às 19h30, Você vai",
            ),
            agenda.first(),
        )
        assertEquals(GroupAgendaStatus.Draft, agenda.last().status)
    }

    @Test
    fun `without a hero every upcoming game stays in the agenda`() = runTest {
        val draft = sampleGame().copy(id = "draft-1", status = GameStatus.Draft)
        val viewModel = viewModel(gameGateway = FakeGameGateway(listResult = SaqzResult.Success(listOf(draft))))

        assertNull(viewModel.state.value.nextGame)
        assertEquals(listOf("draft-1"), viewModel.state.value.agenda.map { it.gameId })
    }

    @Test
    fun `organizer waiting block carries entry requests monthly charges and the game to settle`() = runTest {
        val games = listOf(
            sampleGame().copy(id = "old", status = GameStatus.Completed, startsAt = "2026-07-21T19:30:00-03:00"),
            sampleGame().copy(id = "done", status = GameStatus.Completed, startsAt = "2026-07-28T19:30:00-03:00"),
        )
        val viewModel = viewModel(
            groupGateway = approvalGroupGateway(),
            gameGateway = FakeGameGateway(listResult = SaqzResult.Success(games)),
            organizerFinanceGateway = FakeOrganizerFinanceGateway(
                chargesResult = SaqzResult.Success(
                    ChargeList(
                        listOf(
                            ownCharge("m1", month = "2026-08"),
                            ownCharge("m2", month = "2026-08"),
                            dayCharge("g1", "done"),
                            dayCharge("g2", "done"),
                            dayCharge("g3", "old"),
                        ),
                    ),
                ),
            ),
            now = midAugust,
            entryRequests = FakeGroupEntryRequestGateway(
                listResult = SaqzResult.Success(listOf(entryRequest("Ana"), entryRequest("Bia"), entryRequest("Caio"))),
            ),
        )

        val waiting = assertNotNull(viewModel.state.value.waiting)
        assertEquals("3 pedidos para entrar", waiting.entryRequests?.title)
        assertEquals("Ana, Bia e mais 1", waiting.entryRequests?.meta)
        assertEquals(3, waiting.entryRequests?.count)
        assertEquals("2 mensalidades a receber", waiting.monthly?.title)
        assertEquals("R$ 140,00 · AGO", waiting.monthly?.meta)
        assertEquals(
            GroupSettleRowUi(
                gameId = "done",
                title = "Acertar o jogo de 28/07",
                meta = "2 avulsos · R$ 50,00 a receber",
                contentDescription = "Acertar o jogo de 28/07. 2 avulsos · R$ 50,00 a receber",
            ),
            waiting.settle,
        )
    }

    @Test
    fun `monthly row ignores pending charges from other months`() = runTest {
        val viewModel = viewModel(
            organizerFinanceGateway = FakeOrganizerFinanceGateway(
                chargesResult = SaqzResult.Success(
                    ChargeList(listOf(ownCharge("julho", month = "2026-07", dueDate = "2026-07-10"))),
                ),
            ),
            now = midAugust,
        )

        assertNull(viewModel.state.value.waiting)
    }

    @Test
    fun `settle row steps aside while the onboarding guide reviews the same game`() = runTest {
        val done = sampleGame().copy(id = "done", status = GameStatus.Completed, startsAt = "2026-07-28T19:30:00-03:00")
        val viewModel = viewModel(
            gameGateway = FakeGameGateway(listResult = SaqzResult.Success(listOf(done))),
            organizerFinanceGateway = FakeOrganizerFinanceGateway(
                chargesResult = SaqzResult.Success(ChargeList(listOf(dayCharge("g1", "done")))),
            ),
            now = midAugust,
        )

        assertEquals(GroupOnboarding.ReviewFinances("done"), viewModel.state.value.onboarding)
        assertNull(viewModel.state.value.waiting)
    }

    @Test
    fun `finance failure keeps only the entry requests and never breaks the screen`() = runTest {
        val viewModel = viewModel(
            groupGateway = approvalGroupGateway(),
            statementGateway = FakeFinanceStatementGateway(
                result = SaqzResult.Failure(FinanceError.Data(DataError.Connectivity)),
            ),
            organizerFinanceGateway = FakeOrganizerFinanceGateway(
                chargesResult = SaqzResult.Failure(FinanceError.Data(DataError.Connectivity)),
            ),
            now = midAugust,
            entryRequests = FakeGroupEntryRequestGateway(listResult = SaqzResult.Success(listOf(entryRequest("Ana")))),
        )

        assertFalse(viewModel.state.value.loadFailed)
        assertNull(viewModel.state.value.cashbox?.summary)
        val waiting = assertNotNull(viewModel.state.value.waiting)
        assertEquals("Ana", waiting.entryRequests?.meta)
        assertNull(waiting.monthly)
        assertNull(waiting.settle)
    }

    @Test
    fun `entry request failure degrades to the finance rows`() = runTest {
        val viewModel = viewModel(
            groupGateway = approvalGroupGateway(),
            organizerFinanceGateway = FakeOrganizerFinanceGateway(
                chargesResult = SaqzResult.Success(ChargeList(listOf(ownCharge("m1", month = "2026-08")))),
            ),
            now = midAugust,
            entryRequests = FakeGroupEntryRequestGateway(
                listResult = SaqzResult.Failure(EntryRequestError.DataFailure(DataError.Connectivity)),
            ),
        )

        assertFalse(viewModel.state.value.loadFailed)
        val waiting = assertNotNull(viewModel.state.value.waiting)
        assertNull(waiting.entryRequests)
        assertEquals(1, waiting.monthly?.count)
    }

    @Test
    fun `entry requests are only fetched when the group requires approval`() = runTest {
        val open = FakeGroupEntryRequestGateway()
        viewModel(entryRequests = open)
        assertEquals(0, open.listCalls)

        val gated = FakeGroupEntryRequestGateway()
        viewModel(groupGateway = approvalGroupGateway(), entryRequests = gated)
        assertEquals(1, gated.listCalls)
    }

    @Test
    fun `athlete never receives the waiting block nor asks for entry requests`() = runTest {
        val entry = FakeGroupEntryRequestGateway(listResult = SaqzResult.Success(listOf(entryRequest("Ana"))))
        val viewModel = viewModel(
            groupGateway = approvalGroupGateway(GroupRole.ATHLETE),
            organizerFinanceGateway = FakeOrganizerFinanceGateway(
                chargesResult = SaqzResult.Success(ChargeList(listOf(ownCharge("m1", month = "2026-08")))),
            ),
            now = midAugust,
            entryRequests = entry,
        )

        assertFalse(viewModel.state.value.isAdmin)
        assertNull(viewModel.state.value.waiting)
        assertEquals(0, entry.listCalls)
    }

    @Test
    fun `stale entry requests from a superseded load are discarded`() = runTest {
        val stale = CompletableDeferred<SaqzResult<List<GroupEntryRequest>, EntryRequestError>>()
        val entry = FakeGroupEntryRequestGateway(listResult = SaqzResult.Success(listOf(entryRequest("Ana"))))
            .apply { listDeferred = stale }
        val viewModel = viewModel(groupGateway = approvalGroupGateway(), entryRequests = entry)
        assertNull(viewModel.state.value.waiting)

        entry.listDeferred = null
        viewModel.onIntent(GroupDetailsIntent.Retry)
        assertEquals(1, viewModel.state.value.waiting?.entryRequests?.count)

        stale.complete(SaqzResult.Success(listOf(entryRequest("Ana"), entryRequest("Bia"), entryRequest("Caio"))))
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.waiting?.entryRequests?.count)
    }

    @Test
    fun `people block gets the roster count the first four members and the schedule summary`() = runTest {
        val athletes = FakeAthleteGateway(
            rosterResult = SaqzResult.Success(
                listOf("Ana", "Bia", "Caio", "Duda", "Edu").mapIndexed { index, name ->
                    sampleRosterEntry(userId = "member-$index").copy(displayName = name)
                },
            ),
        )
        val viewModel = viewModel(groupGateway = athleteGroupGateway(), athleteGateway = athletes)

        assertEquals(5, viewModel.state.value.memberCount)
        assertEquals(
            listOf(
                MemberPreviewUi("member-0", "Ana", ""),
                MemberPreviewUi("member-1", "Bia", ""),
                MemberPreviewUi("member-2", "Caio", ""),
                MemberPreviewUi("member-3", "Duda", ""),
            ),
            viewModel.state.value.memberPreview,
        )
        assertEquals("Terça · 19h30", viewModel.state.value.scheduleSummary)
        assertEquals(AthleteRosterFilter(), athletes.lastRosterFilter)
    }

    @Test
    fun `athlete roster failure keeps the screen up with an empty people block`() = runTest {
        val viewModel = viewModel(
            groupGateway = athleteGroupGateway(),
            athleteGateway = FakeAthleteGateway(
                rosterResult = SaqzResult.Failure(AthleteError.DataFailure(DataError.Connectivity)),
            ),
        )

        assertFalse(viewModel.state.value.loadFailed)
        assertFalse(viewModel.state.value.isLoading)
        assertEquals(0, viewModel.state.value.memberCount)
        assertTrue(viewModel.state.value.memberPreview.isEmpty())
    }

    @Test
    fun `agenda and settlement rows emit the existing game effects`() = runTest {
        val viewModel = viewModel()

        viewModel.onIntent(GroupDetailsIntent.OpenAgendaGame("game-2"))
        assertEquals(GroupDetailsEffect.OpenGame(GROUP_ID, "game-2"), viewModel.effects.first())

        viewModel.onIntent(GroupDetailsIntent.OpenSettlement("done"))
        assertEquals(GroupDetailsEffect.OpenSettlement(GROUP_ID, "done"), viewModel.effects.first())
    }

    // 10/08 às 12h em São Paulo: competência 2026-08 sem ambiguidade de fuso.
    private val midAugust = GroupNowPort { kotlin.time.Instant.parse("2026-08-10T15:00:00Z") }

    private fun approvalGroupGateway(role: GroupRole = GroupRole.ADMIN) = FakeGroupGateway(
        readResult = SaqzResult.Success(
            sampleVersionedGroup(sampleGroup(role = role).copy(entryRequiresApproval = true)),
        ),
    )

    private fun entryRequest(name: String) =
        GroupEntryRequest(userId = name.lowercase(), displayName = name, requestedAt = "2026-08-01T12:00:00Z")

    private fun dayCharge(id: String, gameId: String) =
        ownCharge(id, kind = ChargeKind.Game).copy(gameId = gameId, amountCents = 2_500L)

```

Notas de arranjo (fatos do código, não opções): `sampleGame()` é publicado, começa em `2026-08-04T19:30:00-03:00` (terça), CERET, 8 de 12, 4 vagas; 06/08/2026 é quinta; a quadra padrão de `sampleGroup()` é "CERET", então a meta da agenda não repete o local; `sampleGroup()` tem um único horário fixo (terça, `"19:30"`) e papel `ADMIN`; `ownCharge(...)` vale 7.000 centavos; `FakeAthleteGateway.rosterResult` é lista vazia por default, por isso nenhum teste antigo muda de `memberCount`; com DOIS jogos concluídos o `groupOnboarding` devolve `null` e a linha "acertar" aparece, com UM devolve `ReviewFinances` e ela some; `getString` resolve de forma síncrona sob o `UnconfinedTestDispatcher` (é o que os testes de `ownCharges` já assumem), por isso não há `advanceUntilIdle()` depois de construir a ViewModel.

Contagem: `KtorGameGatewayTest` +5 · `GroupAgendaTest` 8 · `GroupWaitingTest` 6 · `GroupDetailsViewModelTest` +13 novos e 2 com expectativa atualizada · 2 testes de Root com o texto do saldo atualizado.

## Cenas de screenshot e prints do PR

Este ticket não é de UI: nenhuma cena Roborazzi nova e nenhum print no corpo do PR. As cenas existentes usam estado fixo de preview (não passam pelo ViewModel), então o gate de captura tem de terminar **sem nenhuma cena alterada** — é a prova de que nada visual mudou. A única mudança que o usuário vê (a frase do caixa vira `"Saldo R$ 380,00"`) está coberta por `GroupCashboxRootTest`, `GameSettlementRootTest` e pelo cenário e2e `payments`. Dizer isso explicitamente no corpo do PR.

## Gates

Rodar da raiz do worktree, nesta ordem, e colar o resumo (BUILD SUCCESSFUL + contagem de testes) no corpo do PR:

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile :features:groups:domain:detektAll :features:groups:data:detektAll :features:groups:presentation:detektAll :compose-app:detektAll
JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile :features:groups:data:iosSimulatorArm64Test --tests "br.com.saqz.groups.data.game.*"
JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile :features:groups:presentation:iosSimulatorArm64Test --tests "br.com.saqz.groups.presentation.details.*"
JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile :features:groups:domain:iosSimulatorArm64Test :features:groups:data:iosSimulatorArm64Test :features:groups:presentation:iosSimulatorArm64Test
JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile :compose-app:iosSimulatorArm64Test
JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile :features:groups:presentation:recordRoborazziAndroidHostTest
JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile :android-app:testDevDebugUnitTest
JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile :android-app:compileDevDebugAndroidTestKotlin -Psaqz.e2e=true
node tests/e2e/android/run.mjs --serial <serial> --scenario payments
```

O segundo e o terceiro são o ciclo rápido; o quarto é o gate completo dos três módulos. O e2e exige o ambiente de `tests/e2e/android/README.md` (emulador dedicado `emulator-NNNN`, Docker do Colima, backend local). Sem o ticket A mergeado o cenário `payments` passa igual: ele não depende de `ownAttendance`.

## Critérios de aceite

- [ ] Nenhum arquivo de `ui/` (em `commonMain`), de `home/` nem de `composeResources/` no diff.
- [ ] `Game.ownAttendance` é o ÚLTIMO parâmetro, com default `null`; nenhum outro construtor de `Game` foi tocado.
- [ ] `GroupDetailsContract.kt` bate campo a campo com a seção 3 do CONTRATO.
- [ ] `groupAgenda`, `groupScheduleSummary` e `groupWaiting` vivem fora do ViewModel, cada regra com teste puro.
- [ ] Toda publicação nova no estado (`agenda`, `waiting`, `cashbox`, `memberCount`/`memberPreview`) re-checa `generation != loadGeneration` depois do último `suspend`, inclusive depois de `getString`.
- [ ] `GroupDetailsViewModelTest`: 13 testes novos verdes; os 2 do saldo com a expectativa nova; todos os demais (antigos + os 13 do V1) verdes sem mudança.
- [ ] `GroupAgendaTest` (8), `GroupWaitingTest` (6) e os 5 novos de `KtorGameGatewayTest` verdes.
- [ ] `SaqzKoinModulesTest` verde (o grafo resolve `GroupDetailsViewModel` com `GroupEntryRequestGateway`).
- [ ] `grep -rn "mensalidades em aberto" mobile/features/groups/presentation/src/commonMain/kotlin/br/com/saqz/groups/presentation/details mobile/android-app/src/e2e` não imprime nada.
- [ ] `detektAll` dos quatro módulos verde, sem baseline novo e sem `@Suppress` novo.
- [ ] `recordRoborazziAndroidHostTest` sem cena alterada; cenário e2e `payments` verde.
- [ ] Diff ≤ 1500 linhas (estimativa: ~1150 — ~330 de produção, ~820 de teste).

## Protocolo do worker

1. Branch a partir de `origin/main`: `git fetch origin && git switch -c vul-XXX-contrato-listas origin/main` (XXX = número deste ticket). O V1 e o S têm de estar mergeados na `origin/main` antes: se `PRES/game/GameLabels.kt` ou `strings_group_details.xml` não existirem, parar e avisar o orquestrador.
2. Commits pequenos em PT-BR no padrão do repo (`feat(groups): …`, `test(groups): …`). Ordem dos commits: domain+data (1–2 + teste de data) → contrato e regras puras (3–5 + testes puros) → ViewModel, DI e fakes (6–7, 9, 11–12 + testes do ViewModel) → NavHost (8) → e2e (10). Este módulo compila inteiro de uma vez: rodar o ciclo rápido antes de cada commit.
3. Rodar TODOS os gates antes de abrir o PR; colar a saída resumida no corpo do PR.
4. PR contra `main`, aberto como ready (não draft), título `feat(groups): contrato das listas do detalhe do grupo — agenda, esperando você, galera e saldo (VUL-XXX)`. Teto: 2000 linhas de adições+remoções.
5. `gh` desta máquina: prefixar `GH_TOKEN=$(gh auth token --user bruno-halmeida)` nos comandos `gh`.
6. Depois de abrir o PR: PARAR. Não fazer triagem de review. Só executar mensagens do orquestrador que comecem com `CORRECAO:`.
