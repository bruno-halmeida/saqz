package br.com.saqz.groups.application.game.series

import br.com.saqz.groups.application.game.ChangeGameLifecycle
import br.com.saqz.groups.domain.game.GameMutation
import br.com.saqz.sharedkernel.subscription.GameCreationHorizon
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** Quem libera o próximo jogo de cada horário da série: um grupo depois da sincronização, todos na rotina. */
interface SeriesOccurrenceRelease {
    fun release(groupId: UUID)
    fun releaseAll()

    companion object {
        val None: SeriesOccurrenceRelease = object : SeriesOccurrenceRelease {
            override fun release(groupId: UUID) = Unit
            override fun releaseAll() = Unit
        }
    }
}

/** Ocorrência da série ainda em rascunho e o dono do grupo, que responde pela publicação automática. */
data class ReleasableOccurrence(val groupId: UUID, val gameId: UUID, val version: Long, val ownerId: UUID)

interface ReleasableOccurrenceRepository {
    /**
     * O próximo rascunho de cada horário (grupo, série, slot) que começa depois de [after] e até
     * [until], em grupo ativo. Um por horário: o seguinte só conta depois que este começar.
     */
    fun nextOccurrences(after: Instant, until: Instant, groupId: UUID?): List<ReleasableOccurrence>
}

/**
 * Liberação da série: os jogos nascem mês a mês em rascunho e ficam invisíveis até aqui. O próximo
 * jogo de cada horário é publicado [lead] antes de começar, pelo mesmo caminho do "Publicar" manual
 * (presença aberta, mensalistas confirmados, aviso aos membros). É por estado: repetir não publica
 * duas vezes e um rascunho já publicado à mão não é tocado.
 */
class ReleaseSeriesOccurrences(
    private val repository: ReleasableOccurrenceRepository,
    private val lifecycle: ChangeGameLifecycle,
    private val horizon: GameCreationHorizon,
    private val lead: Duration,
    private val clock: Clock,
    private val onFailure: (String, Throwable) -> Unit = { _, _ -> },
) : SeriesOccurrenceRelease {
    init { require(!lead.isNegative) { "lead must not be negative" } }

    override fun release(groupId: UUID) = releaseWithin(groupId)
    override fun releaseAll() = releaseWithin(groupId = null)

    private fun releaseWithin(groupId: UUID?) {
        val now = clock.instant()
        repository.nextOccurrences(now, now.plus(lead), groupId)
            .groupBy { it.groupId }
            // Trial expirado sem assinatura: a rotina cancela o futuro da série; nada a liberar.
            .filterKeys { horizon.until(it) != null }
            .values
            .flatten()
            .forEach { occurrence ->
                // Um jogo com problema não segura os outros; o resultado que não é sucesso
                // (versão mudou, já publicado) converge sozinho na próxima rodada.
                runCatching {
                    lifecycle.execute(
                        actor = occurrence.ownerId,
                        groupId = occurrence.groupId,
                        gameId = occurrence.gameId,
                        expectedVersion = occurrence.version,
                        mutation = GameMutation.PUBLISH,
                    )
                }.onFailure { onFailure("game ${occurrence.gameId}", it) }
            }
    }
}
