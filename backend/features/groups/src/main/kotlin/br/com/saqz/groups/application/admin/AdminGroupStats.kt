package br.com.saqz.groups.application.admin

import java.time.Instant

/**
 * Contagens administrativas de grupos e jogos (adm-web · visão geral).
 * Janelas são [from, to); from null significa "desde o início".
 * Jogo "realizado" = publicado ou completado, com início já passado.
 */
interface AdminGroupStats {
    fun activeGroups(): Long

    fun groupsCreated(from: Instant?, to: Instant): Long

    fun gamesPlayed(from: Instant?, to: Instant): Long
}
