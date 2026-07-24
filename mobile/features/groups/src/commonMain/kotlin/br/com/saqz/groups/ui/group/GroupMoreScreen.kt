package br.com.saqz.groups.ui.group

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import br.com.saqz.designsystem.component.SaqzButton
import br.com.saqz.designsystem.component.SaqzButtonVariant
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.GroupFinanceVisibility
import br.com.saqz.groups.presentation.GroupRouteAccess
import br.com.saqz.groups.presentation.navigation.GroupsNavigationTags
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.groups_finance
import br.com.saqz.groups.resources.groups_own_charges
import br.com.saqz.groups.resources.groups_people
import org.jetbrains.compose.resources.stringResource

/**
 * Mais tab content backed by the policy-derived [GroupRouteAccess] projection
 * (T25: replaces the legacy private More screen that read the shared destination state).
 * Same buttons, labels, and testTags as the legacy screen.
 */
@Composable
fun GroupMoreScreen(
    access: GroupRouteAccess,
    onOpenPeople: () -> Unit,
    onOpenFinance: () -> Unit,
    athleteProfile: (@Composable () -> Unit)? = null,
) {
    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(SaqzTheme.metrics.horizontalPadding)
            .testTag(GroupsNavigationTags.More),
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid),
    ) {
        if (access.peopleVisible) {
            SaqzButton(
                label = stringResource(Res.string.groups_people),
                onClick = onOpenPeople,
                modifier = Modifier.fillMaxWidth().testTag(GroupsNavigationTags.MorePeople),
                variant = SaqzButtonVariant.Secondary,
            )
        }
        SaqzButton(
            label = stringResource(
                if (access.financeVisibility == GroupFinanceVisibility.ORGANIZER) {
                    Res.string.groups_finance
                } else {
                    Res.string.groups_own_charges
                },
            ),
            onClick = onOpenFinance,
            modifier = Modifier.fillMaxWidth().testTag(GroupsNavigationTags.MoreFinance),
            variant = SaqzButtonVariant.Secondary,
        )
        athleteProfile?.invoke()
    }
}
