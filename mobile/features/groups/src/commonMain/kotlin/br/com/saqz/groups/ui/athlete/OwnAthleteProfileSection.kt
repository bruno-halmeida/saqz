package br.com.saqz.groups.ui.athlete

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.component.SaqzBadge
import br.com.saqz.designsystem.component.SaqzBadgeVariant
import br.com.saqz.designsystem.component.SaqzErrorState
import br.com.saqz.designsystem.component.SaqzListItem
import br.com.saqz.designsystem.component.SaqzLoadingState
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.athlete.AthleteMembershipType
import br.com.saqz.groups.presentation.athlete.OwnAthleteProfileIntent
import br.com.saqz.groups.presentation.athlete.OwnAthleteProfileState
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.own_profile_no_position
import br.com.saqz.groups.resources.own_profile_phone
import br.com.saqz.groups.resources.own_profile_title
import br.com.saqz.groups.resources.roster_filter_avulso
import br.com.saqz.groups.resources.roster_filter_mensalista
import br.com.saqz.groups.resources.roster_inactive
import org.jetbrains.compose.resources.stringResource

object OwnAthleteProfileTags {
    const val Section = "own-athlete-profile"
}

@Composable
fun OwnAthleteProfileSection(
    state: OwnAthleteProfileState,
    onIntent: (OwnAthleteProfileIntent) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().testTag(OwnAthleteProfileTags.Section),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(Res.string.own_profile_title),
            style = SaqzTheme.typography.bodyStrong,
            color = SaqzTheme.colors.textPrimary,
        )
        when {
            state.loading -> SaqzLoadingState()
            state.failed || state.profile == null -> SaqzErrorState(
                onRetry = { onIntent(OwnAthleteProfileIntent.Reload) },
            )
            else -> {
                val profile = state.profile
                Text(profile.displayName, style = SaqzTheme.typography.body, color = SaqzTheme.colors.textPrimary)
                profile.phone?.let {
                    Text(
                        stringResource(Res.string.own_profile_phone, it),
                        style = SaqzTheme.typography.caption,
                        color = SaqzTheme.colors.textSecondary,
                    )
                }
                profile.memberships.forEach { membership ->
                    SaqzListItem(
                        headline = membership.groupName,
                        supportingContent = {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                SaqzBadge(
                                    label = stringResource(
                                        when (membership.membershipType) {
                                            AthleteMembershipType.MENSALISTA -> Res.string.roster_filter_mensalista
                                            AthleteMembershipType.AVULSO -> Res.string.roster_filter_avulso
                                        },
                                    ),
                                    variant = SaqzBadgeVariant.Info,
                                )
                                SaqzBadge(
                                    label = membership.position
                                        ?.let { stringResource(it.labelRes()) }
                                        ?: stringResource(Res.string.own_profile_no_position),
                                    variant = SaqzBadgeVariant.Neutral,
                                )
                                if (!membership.active) {
                                    SaqzBadge(stringResource(Res.string.roster_inactive), SaqzBadgeVariant.Error)
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}
