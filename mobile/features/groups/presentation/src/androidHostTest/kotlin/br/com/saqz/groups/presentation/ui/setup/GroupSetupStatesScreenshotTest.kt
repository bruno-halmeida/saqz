package br.com.saqz.groups.presentation.ui.setup

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzOfflineBanner
import br.com.saqz.designsystem.SaqzSectionHeader
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.setup.GroupSetupDefaults
import br.com.saqz.groups.presentation.setup.GroupSetupMode
import br.com.saqz.groups.presentation.setup.GroupSetupState
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.group_setup_error_venue_address
import br.com.saqz.groups.resources.group_system_creating
import br.com.saqz.groups.resources.group_system_offline
import br.com.saqz.groups.resources.group_system_offline_title
import br.com.saqz.groups.resources.group_system_save_failure_title
import br.com.saqz.groups.resources.group_system_sending_title
import br.com.saqz.groups.resources.group_system_session_expired_title
import br.com.saqz.groups.resources.group_system_toast
import br.com.saqz.groups.resources.group_system_toast_title
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.jetbrains.compose.resources.stringResource
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * `2h` e o pé do formulário, que não cabe na altura da tela. Estado que não está na
 * cena não está sendo conferido (AGENTS.md §11).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [35],
    qualifiers = RobolectricDeviceQualifiers.Pixel7,
    application = Application::class,
)
class GroupSetupStatesScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun lowerCards() = capture("2a-cards-abaixo-da-dobra") {
        LowerCards(hasCapacityError = false, hasSlotsError = false, withAddressError = false)
    }

    @Test
    fun lowerCardsWithErrors() = capture("2g-cards-abaixo-da-dobra") {
        LowerCards(hasCapacityError = true, hasSlotsError = true, withAddressError = true)
    }

    @Test
    fun systemStates() = capture("2h-estados-de-sistema") {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SaqzTheme.colors.background)
                .padding(SaqzTheme.metrics.horizontalPadding),
            verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.horizontalPadding),
        ) {
            SaqzSectionHeader(title = stringResource(Res.string.group_system_offline_title))
            SaqzOfflineBanner(message = stringResource(Res.string.group_system_offline))
            SaqzSectionHeader(title = stringResource(Res.string.group_system_save_failure_title))
            GroupSaveFailureCard(onRetry = {}, onSaveDraft = {})
            SaqzSectionHeader(title = stringResource(Res.string.group_system_sending_title))
            SaqzButton(
                label = stringResource(Res.string.group_system_creating),
                onClick = {},
                loading = true,
                fullWidth = true,
            )
            SaqzSectionHeader(title = stringResource(Res.string.group_system_session_expired_title))
            GroupSessionExpiredCard()
            SaqzSectionHeader(title = stringResource(Res.string.group_system_toast_title))
            GroupErrorToast(
                visible = true,
                message = stringResource(Res.string.group_system_toast),
                onRetry = {},
                onDismiss = {},
            )
        }
    }

    @Test
    fun offlineAndSaveFailure() = capture("2h-formulario-offline-e-falha") {
        GroupSetupScreen(
            state = GroupSetupState(
                mode = GroupSetupMode.Create,
                form = PreviewCourtForm,
                isOffline = true,
                saveFailed = true,
            ),
            onIntent = {},
            onBack = {},
        )
    }

    @Composable
    private fun LowerCards(hasCapacityError: Boolean, hasSlotsError: Boolean, withAddressError: Boolean) {
        val venue = PreviewCourtForm.defaultVenue
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SaqzTheme.metrics.horizontalPadding),
            verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
        ) {
            GroupCapacitySection(
                capacity = if (hasCapacityError) 1 else GroupSetupDefaults.Capacity,
                isBeach = false,
                hasError = hasCapacityError,
                onChange = {},
            )
            GroupDurationSection(minutes = GroupSetupDefaults.DurationMinutes, onSelect = {})
            GroupConfirmationLeadSection(
                minutes = GroupSetupDefaults.ConfirmationLeadMinutes,
                onSelect = {},
            )
            GroupVenueSection(
                name = venue?.name.orEmpty(),
                address = if (withAddressError) "" else venue?.address.orEmpty(),
                nameError = null,
                addressError = if (withAddressError) {
                    stringResource(Res.string.group_setup_error_venue_address)
                } else {
                    null
                },
                onNameChange = {},
                onAddressChange = {},
            )
            GroupRecurrenceCard(
                recurring = true,
                slots = if (hasSlotsError) emptyList() else PreviewCourtForm.regularSlots,
                hasError = hasSlotsError,
                onRecurringChange = {},
                onAddSlot = {},
                onRemoveSlot = {},
            )
        }
    }

    private fun capture(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            SaqzTheme {
                Box(modifier = Modifier.fillMaxSize().background(SaqzTheme.colors.background)) {
                    content()
                }
            }
        }
        compose.onRoot().captureRoboImage("screenshots/vul-68/$name.png")
    }
}
