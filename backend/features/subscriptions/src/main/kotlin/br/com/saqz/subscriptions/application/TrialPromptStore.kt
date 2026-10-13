package br.com.saqz.subscriptions.application

import java.time.LocalDate
import java.util.UUID

/** Guarda o último dia em que a folha de contagem do teste foi mostrada ao dono (uma por dia, em qualquer aparelho). */
interface TrialPromptStore {
    fun lastPromptDate(ownerUserId: UUID): LocalDate?

    /** Marca [date] como mostrado; false quando o dono não tem teste. */
    fun markPrompted(ownerUserId: UUID, date: LocalDate): Boolean
}
