package br.com.saqz.composeapp.navigation

/**
 * O primeiro acesso depois da instalação é a tela de entrada (login ou cadastro) sem nenhum link
 * já guardado: só então vale procurar o link que a página de links copiou. Com convite ou presença
 * pendentes, o link já chegou por outro caminho e a área de transferência não é consultada.
 */
internal fun shouldReadClipboardLink(
    signedOut: Boolean,
    hasPendingInvite: Boolean,
    hasPendingAttendance: Boolean,
): Boolean = signedOut && !hasPendingInvite && !hasPendingAttendance
