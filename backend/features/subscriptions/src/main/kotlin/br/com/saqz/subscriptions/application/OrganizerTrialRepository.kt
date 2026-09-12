package br.com.saqz.subscriptions.application

import br.com.saqz.subscriptions.domain.OrganizerTrial
import java.util.UUID

interface OrganizerTrialRepository {
    fun find(ownerUserId: UUID): OrganizerTrial?

    /** Insert once; never extend or replace a previously consumed trial. */
    fun insert(trial: OrganizerTrial)
}
