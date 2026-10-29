package br.com.saqz.groups.adapter.output.jdbc.game

import br.com.saqz.groups.adapter.output.jdbc.attendance.FilaHarness
import br.com.saqz.groups.adapter.output.jdbc.group.read.JdbcGroupReadRepository
import br.com.saqz.groups.adapter.output.jdbc.group.settings.JdbcGroupSettingsRepository
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.game.CreateGame
import br.com.saqz.groups.application.game.EditGame
import br.com.saqz.groups.application.game.GameCommandResult
import br.com.saqz.groups.application.game.series.withGroupFee
import br.com.saqz.groups.application.settings.UpdateGroupProfileInput
import br.com.saqz.groups.application.settings.UpdateGroupSettings
import br.com.saqz.groups.application.settings.UpdateGroupSettingsResult
import br.com.saqz.groups.domain.GroupAccessPolicy
import br.com.saqz.groups.domain.game.CreateGameInput
import br.com.saqz.groups.domain.game.GameDraftInput
import br.com.saqz.groups.domain.game.GameVenueInput
import br.com.saqz.groups.domain.game.NullableGameFeeOverride
import br.com.saqz.groups.domain.group.GroupComposition
import br.com.saqz.groups.domain.group.GroupModality
import br.com.saqz.groups.domain.group.GroupProfileDefaultsInput
import br.com.saqz.groups.domain.group.PromotionMode
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** DEB-146 (JOGO-008): "Sem diária" = valor vazio + marca; app antigo sem os dois leva o padrão do grupo. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ValorPorJogoIntegrationTest {
    private lateinit var h: FilaHarness
    private val games get() = JdbcGameOccurrenceRepository(h.dataSource)
    private val tx get() = JdbcTransactionRunner(h.dataSource)
    private val zone = ZoneId.of("America/Sao_Paulo")

    @BeforeEach fun reset() { h = FilaHarness(this); h.cenario(capacity = 8) }

    private fun grupo(fee: Long?, noFee: Boolean = false) =
        h.execute("UPDATE access_groups SET default_game_fee_cents=${fee ?: "NULL"}, default_no_game_fee=$noFee WHERE id='${h.group}'")

    private var dias = 4L

    private fun criar(fee: NullableGameFeeOverride): GameCommandResult {
        // Um dia por jogo: dois jogos no mesmo horário do grupo são conflito de agenda.
        val startsAt = Instant.now().plus(++dias, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES)
        val local = startsAt.atZone(zone)
        return CreateGame(tx, games).execute(
            h.dono, h.group, UUID.randomUUID(),
            CreateGameInput(
                "Jogo", GameVenueInput(null, "Arena", "Rua Central 100"), local.toLocalDate(), local.toLocalTime(), zone.id,
                startsAt, 90, 8, startsAt.minusSeconds(3600), fee,
            ),
        )
    }

    private fun editar(fee: Long?, noFee: Boolean): GameCommandResult {
        val atual = checkNotNull(games.find(h.group, h.game)).snapshot
        val local = atual.startsAt.atZone(zone)
        val input = GameDraftInput(
            atual.title, GameVenueInput(null, atual.venue.name, atual.venue.address), local.toLocalDate(), local.toLocalTime(), zone.id,
            atual.startsAt, atual.durationMinutes, atual.capacity, atual.confirmationDeadline, fee, null, noFee,
        )
        return EditGame(tx, games, { _, _, _ -> }).execute(h.dono, h.group, h.game, checkNotNull(games.find(h.group, h.game)).version, input)
    }

    private fun alinharJogo() = h.execute(
        "UPDATE games SET starts_at = date_trunc('minute', starts_at), " +
            "local_date = (date_trunc('minute', starts_at) AT TIME ZONE 'America/Sao_Paulo')::date, " +
            "local_time = (date_trunc('minute', starts_at) AT TIME ZONE 'America/Sao_Paulo')::time WHERE id='${h.game}'",
    )

    @Test fun `criar sem valor e sem marca leva o valor do grupo`() {
        grupo(2000)
        val game = assertIs<GameCommandResult.Success>(criar(NullableGameFeeOverride.of(null, null))).game
        assertEquals(2000, game.snapshot.gameFeeCents); assertFalse(game.snapshot.noGameFee)
    }

    @Test fun `criar sem valor e sem marca num grupo Sem diaria fica Sem diaria`() {
        grupo(null, noFee = true)
        val game = assertIs<GameCommandResult.Success>(criar(NullableGameFeeOverride.of(null, null))).game
        assertNull(game.snapshot.gameFeeCents); assertTrue(game.snapshot.noGameFee)
        assertTrue(checkNotNull(games.find(h.group, game.id)).snapshot.noGameFee, "a marca é gravada")
    }

    @Test fun `criar sem padrao no grupo segue nao definido, e com valor ou marca explicitos vale o pedido`() {
        grupo(null)
        val indefinido = assertIs<GameCommandResult.Success>(criar(NullableGameFeeOverride.of(null, null))).game.snapshot
        assertNull(indefinido.gameFeeCents); assertFalse(indefinido.noGameFee)
        grupo(2000)
        assertTrue(assertIs<GameCommandResult.Success>(criar(NullableGameFeeOverride.of(null, true))).game.snapshot.noGameFee)
        assertEquals(1500, assertIs<GameCommandResult.Success>(criar(NullableGameFeeOverride.of(1500, null))).game.snapshot.gameFeeCents)
    }

    @Test fun `valor e marca juntos recusam em noGameFee`() {
        alinharJogo()
        val result = editar(fee = 1000, noFee = true)
        assertEquals(listOf("noGameFee"), assertIs<GameCommandResult.Invalid>(result).errors.map { it.field })
    }

    @Test fun `editar jogo Sem diaria sem os dois (app antigo) mantem a marca`() {
        alinharJogo(); grupo(2000)
        h.execute("UPDATE games SET game_fee_cents=NULL, no_game_fee=true WHERE id='${h.game}'")
        val snapshot = assertIs<GameCommandResult.Success>(editar(fee = null, noFee = false)).game.snapshot
        assertNull(snapshot.gameFeeCents); assertTrue(snapshot.noGameFee)
    }

    @Test fun `editar jogo nao definido sem os dois leva o padrao do grupo, e a marca explicita grava`() {
        alinharJogo(); grupo(2000)
        h.execute("UPDATE games SET game_fee_cents=NULL WHERE id='${h.game}'")
        assertEquals(2000, assertIs<GameCommandResult.Success>(editar(fee = null, noFee = false)).game.snapshot.gameFeeCents)
        val semDiaria = assertIs<GameCommandResult.Success>(editar(fee = null, noFee = true)).game.snapshot
        assertNull(semDiaria.gameFeeCents); assertTrue(semDiaria.noGameFee)
        assertTrue(checkNotNull(games.find(h.group, h.game)).snapshot.noGameFee)
    }

    @Test fun `o banco recusa a marca com valor`() {
        val erro = runCatching { h.execute("UPDATE games SET no_game_fee=true WHERE id='${h.game}'") }.exceptionOrNull()
        assertTrue(erro != null && "ck_games_no_game_fee" in erro.message.orEmpty(), erro?.message)
    }

    private fun atualizarGrupo(
        fee: Long? = null,
        noFee: Boolean? = null,
        mode: PromotionMode? = null,
        advance: Boolean? = null,
    ): UpdateGroupSettingsResult {
        val version = h.int("SELECT version FROM access_groups WHERE id='${h.group}'").toLong()
        return UpdateGroupSettings(tx, JdbcGroupReadRepository(h.dataSource), JdbcGroupSettingsRepository(h.dataSource), GroupAccessPolicy(), gameFeePropagation = JdbcGameFeePropagation(h.dataSource))
            .execute(
                h.dono, h.group, version,
                UpdateGroupProfileInput(
                    GroupProfileDefaultsInput(
                        name = "Grupo", modality = GroupModality.COURT_VOLLEYBALL, composition = GroupComposition.MIXED,
                        defaultGameFeeCents = fee, defaultNoGameFee = noFee, promotionMode = mode, advancePaymentRequired = advance,
                    ),
                ),
            )
    }

    private fun noGameFee(game: UUID) = h.string("SELECT no_game_fee::text FROM games WHERE id='$game'")
    private fun fee(game: UUID) = h.string("SELECT coalesce(game_fee_cents::text,'null') FROM games WHERE id='$game'")

    @Test fun `grupo que deixa de estar nao definido leva o valor aos jogos nao comecados sem valor, e a nenhum outro`() {
        h.execute("UPDATE games SET game_fee_cents=NULL WHERE id='${h.game}'")
        val comValor = UUID.randomUUID(); val passado = UUID.randomUUID(); val cancelado = UUID.randomUUID()
        fun jogo(id: UUID, quando: String, fee: String, status: String) = h.execute(
            "INSERT INTO games (id,group_id,title,local_date,local_time,zone_id,starts_at,duration_minutes,confirmation_deadline,venue_name,venue_address,capacity,game_fee_cents,status,created_at,updated_at) " +
                "VALUES ('$id','${h.group}','Treino',CURRENT_DATE,TIME '10:00','America/Sao_Paulo',$quando,60,$quando,'Arena','Rua Central 100',8,$fee,'$status',now(),now())",
        )
        jogo(comValor, "now() + interval '5 days'", "3000", "PUBLISHED")
        jogo(passado, "now() - interval '5 days'", "NULL", "PUBLISHED")
        jogo(cancelado, "now() + interval '6 days'", "NULL", "CANCELLED")

        assertIs<UpdateGroupSettingsResult.Success>(atualizarGrupo(fee = 1000))
        assertEquals("1000", fee(h.game)); assertEquals("3000", fee(comValor))
        assertEquals("null", fee(passado)); assertEquals("null", fee(cancelado))
        assertEquals(0, h.int("SELECT count(*) FROM group_charges WHERE game_id='${h.game}'"), "nenhuma diária nasce sozinha")
    }

    @Test fun `grupo que escolhe Sem diaria leva a marca aos jogos nao definidos`() {
        h.execute("UPDATE games SET game_fee_cents=NULL WHERE id='${h.game}'")
        assertIs<UpdateGroupSettingsResult.Success>(atualizarGrupo(noFee = true))
        assertEquals("true", noGameFee(h.game)); assertEquals("true", h.string("SELECT default_no_game_fee::text FROM access_groups WHERE id='${h.group}'"))
    }

    @Test fun `app antigo que manda o valor vazio num grupo Sem diaria mantem a marca, e valor grava a marca false`() {
        grupo(null, noFee = true)
        assertIs<UpdateGroupSettingsResult.Success>(atualizarGrupo())
        assertEquals("true", h.string("SELECT default_no_game_fee::text FROM access_groups WHERE id='${h.group}'"))
        assertIs<UpdateGroupSettingsResult.Success>(atualizarGrupo(fee = 1500))
        assertEquals("false", h.string("SELECT default_no_game_fee::text FROM access_groups WHERE id='${h.group}'"))
    }

    @Test fun `marca e valor juntos no grupo recusam em defaultNoGameFee`() {
        val result = atualizarGrupo(fee = 1000, noFee = true)
        assertEquals(listOf("defaultNoGameFee"), assertIs<UpdateGroupSettingsResult.InvalidProfile>(result).errors.map { it.field })
    }

    @Test fun `gameFeeUndefined so com valor vazio e sem marca`() {
        val read = JdbcGroupReadRepository(h.dataSource)
        fun defaults() = checkNotNull(read.find(br.com.saqz.groups.application.read.GroupReadKey(h.dono, h.group))).financeDefaults!!
        grupo(null); assertTrue(defaults().let { it.defaultGameFeeCents == null && !it.defaultNoGameFee })
        grupo(null, noFee = true); assertTrue(defaults().defaultNoGameFee)
    }

    @Test fun `pagamento antecipado so com Escolha manual, e Ordem de chegada desliga`() {
        assertEquals(
            listOf("advancePaymentRequired"),
            assertIs<UpdateGroupSettingsResult.InvalidProfile>(atualizarGrupo(advance = true)).errors.map { it.field },
            "grupo em Ordem de chegada recusa",
        )
        assertIs<UpdateGroupSettingsResult.Success>(atualizarGrupo(mode = PromotionMode.MANUAL, advance = true))
        assertEquals("true", h.string("SELECT advance_payment_required::text FROM access_groups WHERE id='${h.group}'"))
        assertIs<UpdateGroupSettingsResult.Success>(atualizarGrupo())
        assertEquals("true", h.string("SELECT advance_payment_required::text FROM access_groups WHERE id='${h.group}'"), "ausente não muda")
        assertTrue(checkNotNull(games.find(h.group, h.game)).advancePaymentRequired, "o jogo lê a opção do grupo")
        assertIs<UpdateGroupSettingsResult.Success>(atualizarGrupo(mode = PromotionMode.FIFO))
        assertEquals("false", h.string("SELECT advance_payment_required::text FROM access_groups WHERE id='${h.group}'"))
        assertEquals(
            listOf("advancePaymentRequired"),
            assertIs<UpdateGroupSettingsResult.InvalidProfile>(atualizarGrupo(mode = PromotionMode.FIFO, advance = true)).errors.map { it.field },
        )
    }

    @Test fun `horario da serie sem valor e sem marca leva o padrao do grupo e copia a marca`() {
        val slot = br.com.saqz.groups.domain.game.recurrence.WeeklySlotRule(
            UUID.randomUUID(), java.time.DayOfWeek.MONDAY, java.time.LocalTime.of(19, 0), 60,
            br.com.saqz.groups.domain.game.GameVenueSnapshot(null, "Arena", "Rua Central 100", null), 8, 60, null, "Jogo",
        )
        val rule = br.com.saqz.groups.domain.game.recurrence.WeeklySeriesRule(h.group, UUID.randomUUID(), UUID.randomUUID(), zone.id, java.time.LocalDate.now(), slots = listOf(slot))
        val semDiaria = rule.withGroupFee(br.com.saqz.groups.application.game.series.GameFeeDefault(null, true)).slots.single()
        assertTrue(semDiaria.noGameFee); assertNull(semDiaria.gameFeeCents)
        assertEquals(2000, rule.withGroupFee(br.com.saqz.groups.application.game.series.GameFeeDefault(2000, false)).slots.single().gameFeeCents)
        val explicito = rule.copy(slots = listOf(slot.copy(gameFeeCents = 900)))
        assertEquals(900, explicito.withGroupFee(br.com.saqz.groups.application.game.series.GameFeeDefault(null, true)).slots.single().gameFeeCents)
        assertEquals(br.com.saqz.groups.application.game.series.GameFeeDefault(null, false), JdbcWeeklySeriesRepository(h.dataSource).gameFeeDefault(h.group))
    }
}
