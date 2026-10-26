package br.com.saqz.groups.presentation.ui.setup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable

/**
 * O recebimento pelo app no Editar grupo (FIN-009, FIN-010). A feature de grupos não enxerga a de recebimentos
 * (AGENTS.md §1): quem monta isto é o `:compose-app`, e cada peça é desenhada por lá.
 *
 * - [active]: o grupo recebe pelo app; a chave Pix pessoal fica "Escondida dos atletas".
 * - [receiving]: "Como o grupo recebe" (Gestor-Pix).
 * - [values]: a linha "Taxas de serviço e pagamento · Mudar" e o aviso do reajuste, no cartão da mensalidade.
 * - [feePreview]: "Atleta paga · você recebe" logo abaixo de um valor (Gestor-Valores).
 */
@Immutable
class GroupSetupReceivables(
    val active: Boolean,
    val receiving: @Composable () -> Unit,
    val values: @Composable () -> Unit,
    val feePreview: @Composable (cents: Long?) -> Unit,
)
