package br.com.saqz.groups.domain.moderation

import br.com.saqz.domain.DataError
import br.com.saqz.domain.EmptyResult
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.domain.ValidationDetails

/**
 * Denúncia e bloqueio: o mínimo que a App Store (diretriz 1.2) exige de conteúdo gerado por
 * usuário. Tudo no Saqz é privado ao grupo, então toda denúncia carrega o grupo de onde veio.
 */
enum class ReportTargetType { MESSAGE, USER, GROUP }

enum class ReportReason { SPAM, OFFENSIVE, HARASSMENT, OTHER }

/**
 * Para [ReportTargetType.GROUP], [targetId] é o próprio [groupId]; para USER, outro membro do
 * grupo; para MESSAGE, um aviso do grupo.
 */
data class ContentReport(
    val groupId: GroupId,
    val targetType: ReportTargetType,
    val targetId: String,
    val reason: ReportReason,
    val details: String? = null,
) {
    companion object {
        /** Teto do campo livre no backend. */
        const val MAX_DETAILS_LENGTH = 1000
    }
}

/** Quem o usuário bloqueou; [blockedAt] é o instante ISO-8601 que o servidor devolve. */
data class BlockedPerson(val userId: String, val displayName: String, val blockedAt: String)

sealed interface ModerationError : SaqzError {
    data class Validation(val details: ValidationDetails) : ModerationError
    data class DataFailure(val error: DataError) : ModerationError
}

/**
 * Os nomes são únicos no framework de propósito: o domínio é exportado para o Swift e dois
 * métodos com o mesmo seletor ObjC e tipos diferentes fazem o Kotlin/Native renomear um deles
 * (foi o que quebrou o `read(groupId:done:)` do onboarding).
 */
interface ModerationGateway {
    suspend fun fileContentReport(report: ContentReport): EmptyResult<ModerationError>

    suspend fun listBlockedPeople(): SaqzResult<List<BlockedPerson>, ModerationError>

    /** Idempotente; o servidor também abre uma denúncia para a equipe. */
    suspend fun blockPerson(userId: String, groupId: GroupId): EmptyResult<ModerationError>

    /** Idempotente. */
    suspend fun unblockPerson(userId: String): EmptyResult<ModerationError>
}
