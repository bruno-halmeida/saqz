package br.com.saqz.groups.presentation.ui

import androidx.compose.runtime.Composable
import br.com.saqz.groups.domain.athlete.AthletePosition
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.position_label_central
import br.com.saqz.groups.resources.position_label_levantador
import br.com.saqz.groups.resources.position_label_libero
import br.com.saqz.groups.resources.position_label_oposto
import br.com.saqz.groups.resources.position_label_ponta
import br.com.saqz.groups.resources.position_plural_central
import br.com.saqz.groups.resources.position_plural_levantador
import br.com.saqz.groups.resources.position_plural_libero
import br.com.saqz.groups.resources.position_plural_oposto
import br.com.saqz.groups.resources.position_plural_ponta
import org.jetbrains.compose.resources.stringResource

/** "Ponta" (seletor, linha de pessoa). */
@Composable
internal fun AthletePosition.singularLabel(): String = stringResource(
    when (this) {
        AthletePosition.LIBERO -> Res.string.position_label_libero
        AthletePosition.PONTA -> Res.string.position_label_ponta
        AthletePosition.CENTRAL -> Res.string.position_label_central
        AthletePosition.OPOSTO -> Res.string.position_label_oposto
        AthletePosition.LEVANTADOR -> Res.string.position_label_levantador
    },
)

/** "pontas" (cabeçalho do grupo, "2/2 pontas"). */
@Composable
internal fun AthletePosition.pluralLabel(): String = stringResource(
    when (this) {
        AthletePosition.LIBERO -> Res.string.position_plural_libero
        AthletePosition.PONTA -> Res.string.position_plural_ponta
        AthletePosition.CENTRAL -> Res.string.position_plural_central
        AthletePosition.OPOSTO -> Res.string.position_plural_oposto
        AthletePosition.LEVANTADOR -> Res.string.position_plural_levantador
    },
)
