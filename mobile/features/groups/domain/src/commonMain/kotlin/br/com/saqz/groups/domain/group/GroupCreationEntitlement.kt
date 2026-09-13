package br.com.saqz.groups.domain.group

/**
 * Porta para o Fluxo 8: quem sabe se o dono tem plano ativo com vaga de grupo é o
 * módulo de assinaturas; o `:compose-app` liga as duas pontas (mesmo padrão do
 * `SessionCustomerInfoProvider`). O backend revalida no POST — isto é só roteamento.
 */
fun interface GroupCreationEntitlement {
    suspend fun canCreateGroup(): Boolean

    /** Permite abrir a entrada, que revalida o acesso e oferece retry quando a consulta falha. */
    suspend fun canOpenCreationFlow(): Boolean = canCreateGroup()
}
