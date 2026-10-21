package br.com.saqz.groups.presentation.photo

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.SaqzAvatarInitials
import br.com.saqz.designsystem.SaqzTheme

/**
 * Foto de perfil de outro membro do grupo (MEM-006). Só existe com [tag]: sem foto, ou
 * para quem bloqueou a pessoa, o backend manda `photoTag` nulo e a tela fica nas iniciais.
 * O tag entra na URL como chave de cache: trocar a foto troca a URL.
 */
@Immutable
data class MemberPhoto(val groupId: String, val userId: String, val tag: String) {
    val url: String get() = "/api/groups/$groupId/members/$userId/photo?v=$tag"
}

fun memberPhoto(groupId: String?, userId: String?, tag: String?): MemberPhoto? =
    if (groupId.isNullOrBlank() || userId.isNullOrBlank() || tag.isNullOrBlank()) null else MemberPhoto(groupId, userId, tag)

/**
 * Miolo do `SaqzAvatar`: a foto, com as iniciais enquanto carrega e quando falha (sem
 * mensagem de erro). Nulo sem foto, para o avatar seguir só com as iniciais.
 */
@Composable
fun MemberPhoto?.avatarSlot(
    name: String,
    size: Dp = 40.dp,
    initialsColor: Color = SaqzTheme.colors.textPrimary,
): (@Composable () -> Unit)? {
    val photo = this ?: return null
    return {
        GroupRemotePhotoRoot(photo.url, Modifier.fillMaxSize()) {
            SaqzAvatarInitials(name = name, size = size, color = initialsColor)
        }
    }
}
