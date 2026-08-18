package br.com.saqz.profile.presentation.edit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import coil3.ImageLoader
import coil3.compose.LocalPlatformContext
import coil3.compose.SubcomposeAsyncImage
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzChoiceChip
import br.com.saqz.designsystem.SaqzDivider
import br.com.saqz.designsystem.SaqzEmptyState
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIconButton
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzInput
import br.com.saqz.designsystem.SaqzInputKind
import br.com.saqz.designsystem.PhoneVisualTransformation
import br.com.saqz.designsystem.SaqzTopAppBar
import br.com.saqz.designsystem.rememberSaqzFormScope
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.profile.domain.PhoneVisibility
import br.com.saqz.profile.fake.FakeProfileGateway
import br.com.saqz.profile.presentation.edit.EditProfileFieldError
import br.com.saqz.profile.presentation.edit.EditProfileForm
import br.com.saqz.profile.presentation.edit.EditProfileIntent
import br.com.saqz.profile.presentation.edit.EditProfileState
import br.com.saqz.profile.presentation.photo.profilePhotoImageRequest
import br.com.saqz.profile.resources.Res
import br.com.saqz.profile.resources.profile_edit_city
import br.com.saqz.profile.resources.profile_edit_email
import br.com.saqz.profile.resources.profile_edit_error_city
import br.com.saqz.profile.resources.profile_edit_error_name
import br.com.saqz.profile.resources.profile_edit_error_nickname
import br.com.saqz.profile.resources.profile_edit_error_phone
import br.com.saqz.profile.resources.profile_edit_error_visibility
import br.com.saqz.profile.resources.profile_edit_load_error
import br.com.saqz.profile.resources.profile_edit_membership_note
import br.com.saqz.profile.resources.profile_edit_name
import br.com.saqz.profile.resources.profile_edit_nickname
import br.com.saqz.profile.resources.profile_edit_phone
import br.com.saqz.profile.resources.profile_edit_phone_visibility
import br.com.saqz.profile.resources.profile_edit_phone_visibility_hint
import br.com.saqz.profile.resources.profile_edit_photo_action
import br.com.saqz.profile.resources.profile_edit_photo_hint
import br.com.saqz.profile.resources.profile_edit_photo_title
import br.com.saqz.profile.resources.profile_edit_retry
import br.com.saqz.profile.resources.profile_edit_save
import br.com.saqz.profile.resources.profile_edit_save_error
import br.com.saqz.profile.resources.profile_edit_title
import br.com.saqz.profile.resources.profile_edit_visibility_admins
import br.com.saqz.profile.resources.profile_edit_visibility_everyone
import br.com.saqz.profile.resources.profile_edit_visibility_nobody
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

internal object EditProfileTags {
    const val Photo = "profile-edit-photo"
    const val DisplayName = "profile-edit-display-name"
    const val Nickname = "profile-edit-nickname"
    const val Phone = "profile-edit-phone"
    const val Email = "profile-edit-email"
    const val City = "profile-edit-city"
    const val Save = "profile-edit-save"
    const val LoadError = "profile-edit-load-error"
    const val SaveError = "profile-edit-save-error"

    fun visibility(value: PhoneVisibility) = "profile-edit-visibility-${value.name.lowercase()}"
}

@Composable
fun EditProfileScreen(
    state: EditProfileState,
    onIntent: (EditProfileIntent) -> Unit,
    onPickPhoto: () -> Unit,
    onBack: () -> Unit,
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier,
) {
    val metrics = SaqzTheme.metrics
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SaqzTheme.colors.background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SaqzTopAppBar(
                title = stringResource(Res.string.profile_edit_title),
                onBack = onBack,
            )
            when {
                state.isLoading -> EditProfileLoading()
                state.loadFailed -> EditProfileLoadError(onRetry = { onIntent(EditProfileIntent.Retry) })
                else -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(
                            start = metrics.horizontalPadding,
                            end = metrics.horizontalPadding,
                            top = metrics.blockGap,
                            bottom = metrics.blockGap,
                        ),
                        verticalArrangement = Arrangement.spacedBy(metrics.blockGap),
                    ) {
                        item(key = "photo") {
                            EditProfilePhotoCard(
                                form = state.form,
                                photoUrl = state.photoUrl,
                                imageLoader = imageLoader,
                                onPickPhoto = onPickPhoto,
                            )
                        }
                        item(key = "fields") {
                            EditProfileFieldsCard(state = state, onIntent = onIntent)
                        }
                    }
                }
            }
            if (!state.isLoading && !state.loadFailed) {
                EditProfileFooter(state = state, onIntent = onIntent)
            }
        }
    }
}

@Composable
private fun EditProfileLoading() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            color = SaqzTheme.colors.primary,
            modifier = Modifier.testTag(EditProfileTags.LoadError),
        )
    }
}

@Composable
private fun EditProfileLoadError(onRetry: () -> Unit) {
    SaqzEmptyState(
        title = stringResource(Res.string.profile_edit_load_error),
        action = stringResource(Res.string.profile_edit_retry),
        onAction = onRetry,
        modifier = Modifier.testTag(EditProfileTags.LoadError),
    )
}

@Composable
private fun EditProfilePhotoCard(
    form: EditProfileForm,
    photoUrl: String?,
    imageLoader: ImageLoader,
    onPickPhoto: () -> Unit,
) {
    val metrics = SaqzTheme.metrics
    SaqzCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                EditProfileAvatar(
                    photoUrl = photoUrl,
                    initials = form.displayName.initials(),
                    imageLoader = imageLoader,
                )
                SaqzIconButton(
                    onClick = onPickPhoto,
                    contentDescription = stringResource(Res.string.profile_edit_photo_action),
                    filled = true,
                    size = metrics.photoBadgeSize,
                    modifier = Modifier.testTag(EditProfileTags.Photo),
                ) {
                    SaqzIcon(
                        icon = SaqzIcons.Camera,
                        tint = SaqzTheme.colors.onPrimary,
                        size = metrics.photoIconSize,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid)) {
                Text(
                    text = stringResource(Res.string.profile_edit_photo_title),
                    style = SaqzTheme.typography.subtitle,
                    color = SaqzTheme.colors.textPrimary,
                )
                Text(
                    text = stringResource(Res.string.profile_edit_photo_hint),
                    style = SaqzTheme.typography.body,
                    color = SaqzTheme.colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun EditProfileAvatar(
    photoUrl: String?,
    initials: String,
    imageLoader: ImageLoader,
) {
    val colors = SaqzTheme.colors
    val context = LocalPlatformContext.current
    val imageRequest = photoUrl?.let { url ->
        remember(context, url) { profilePhotoImageRequest(context, url) }
    }
    Box(
        modifier = Modifier
            .size(SaqzTheme.metrics.avatarSize)
            .clip(CircleShape)
            .background(colors.surfaceSoft),
        contentAlignment = Alignment.Center,
    ) {
        if (imageRequest == null) {
            EditProfileAvatarFallback(initials)
        } else {
            SubcomposeAsyncImage(
                model = imageRequest,
                imageLoader = imageLoader,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                loading = { EditProfileAvatarFallback(initials) },
                error = { EditProfileAvatarFallback(initials) },
            )
        }
    }
}

@Composable
private fun EditProfileAvatarFallback(initials: String) {
    Text(
        text = initials,
        style = SaqzTheme.typography.title,
        color = SaqzTheme.colors.textPrimary,
    )
}

@Composable
private fun EditProfileFieldsCard(
    state: EditProfileState,
    onIntent: (EditProfileIntent) -> Unit,
) {
    val form = state.form
    val enabled = !state.isSaving
    val ime = rememberSaqzFormScope(onSubmit = { onIntent(EditProfileIntent.Submit) })
    SaqzCard {
        SaqzInput(
            value = form.displayName,
            onValueChange = { onIntent(EditProfileIntent.UpdateDisplayName(it)) },
            label = stringResource(Res.string.profile_edit_name),
            errorText = state.fieldError(
                Res.string.profile_edit_error_name,
                EditProfileFieldError.NameRequired,
                EditProfileFieldError.NameInvalid,
            ),
            enabled = enabled,
            leadingContent = { SaqzIcon(SaqzIcons.User, tint = SaqzTheme.colors.primary) },
            ime = ime.imeNext(),
            modifier = Modifier.testTag(EditProfileTags.DisplayName),
        )
        SaqzInput(
            value = form.nickname,
            onValueChange = { onIntent(EditProfileIntent.UpdateNickname(it)) },
            label = stringResource(Res.string.profile_edit_nickname),
            errorText = state.fieldError(
                Res.string.profile_edit_error_nickname,
                EditProfileFieldError.NicknameInvalid,
            ),
            enabled = enabled,
            ime = ime.imeNext(),
            modifier = Modifier.testTag(EditProfileTags.Nickname),
        )
        SaqzInput(
            value = form.phone,
            onValueChange = { onIntent(EditProfileIntent.UpdatePhone(it)) },
            label = stringResource(Res.string.profile_edit_phone),
            kind = SaqzInputKind.Phone,
            visualTransformation = PhoneVisualTransformation(),
            errorText = state.fieldError(
                Res.string.profile_edit_error_phone,
                EditProfileFieldError.PhoneRequired,
                EditProfileFieldError.PhoneInvalid,
            ),
            enabled = enabled,
            leadingContent = { SaqzIcon(SaqzIcons.Phone, tint = SaqzTheme.colors.primary) },
            ime = ime.imeNext(),
            modifier = Modifier.testTag(EditProfileTags.Phone),
        )
        SaqzInput(
            value = form.email,
            onValueChange = {},
            label = stringResource(Res.string.profile_edit_email),
            kind = SaqzInputKind.Email,
            enabled = false,
            leadingContent = { SaqzIcon(SaqzIcons.Mail, tint = SaqzTheme.colors.disabledForeground) },
            ime = ime.imeNext(),
            modifier = Modifier.testTag(EditProfileTags.Email),
        )
        SaqzInput(
            value = form.city,
            onValueChange = { onIntent(EditProfileIntent.UpdateCity(it)) },
            label = stringResource(Res.string.profile_edit_city),
            errorText = state.fieldError(
                Res.string.profile_edit_error_city,
                EditProfileFieldError.CityInvalid,
            ),
            enabled = enabled,
            ime = ime.imeDone(),
            modifier = Modifier.testTag(EditProfileTags.City),
        )
        EditProfilePrivacy(
            selected = form.phoneVisibility,
            enabled = enabled,
            onSelect = { onIntent(EditProfileIntent.SelectPhoneVisibility(it)) },
            errorText = state.fieldError(
                Res.string.profile_edit_error_visibility,
                EditProfileFieldError.PhoneVisibilityInvalid,
            ),
        )
        if (state.saveFailed) {
            Text(
                text = stringResource(Res.string.profile_edit_save_error),
                color = SaqzTheme.colors.errorForeground,
                style = SaqzTheme.typography.support,
                modifier = Modifier.testTag(EditProfileTags.SaveError),
            )
        }
    }
}

@Composable
private fun EditProfilePrivacy(
    selected: PhoneVisibility,
    enabled: Boolean,
    onSelect: (PhoneVisibility) -> Unit,
    errorText: String?,
) {
    val metrics = SaqzTheme.metrics
    Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid)) {
        Text(
            text = stringResource(Res.string.profile_edit_phone_visibility),
            style = SaqzTheme.typography.label,
            color = SaqzTheme.colors.textPrimary,
        )
        Text(
            text = stringResource(Res.string.profile_edit_phone_visibility_hint),
            style = SaqzTheme.typography.support,
            color = SaqzTheme.colors.textSecondary,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(metrics.grid),
        ) {
            visibilityOptions().forEach { option ->
                SaqzChoiceChip(
                    label = stringResource(option.label),
                    selected = selected == option.value,
                    onClick = { if (enabled) onSelect(option.value) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag(EditProfileTags.visibility(option.value)),
                )
            }
        }
        if (errorText != null) {
            Text(
                text = errorText,
                style = SaqzTheme.typography.caption,
                color = SaqzTheme.colors.errorForeground,
            )
        }
        Text(
            text = stringResource(Res.string.profile_edit_membership_note),
            style = SaqzTheme.typography.support,
            color = SaqzTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun EditProfileFooter(
    state: EditProfileState,
    onIntent: (EditProfileIntent) -> Unit,
) {
    val metrics = SaqzTheme.metrics
    Column {
        SaqzDivider()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(SaqzTheme.colors.background)
                .padding(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap),
        ) {
            SaqzButton(
                label = stringResource(Res.string.profile_edit_save),
                onClick = { onIntent(EditProfileIntent.Submit) },
                enabled = state.hasChanges && !state.isLoading,
                loading = state.isSaving,
                fullWidth = true,
                modifier = Modifier.testTag(EditProfileTags.Save),
            )
        }
    }
}

private data class VisibilityOption(
    val value: PhoneVisibility,
    val label: StringResource,
)

private fun visibilityOptions() = listOf(
    VisibilityOption(PhoneVisibility.EVERYONE, Res.string.profile_edit_visibility_everyone),
    VisibilityOption(PhoneVisibility.ADMINS, Res.string.profile_edit_visibility_admins),
    VisibilityOption(PhoneVisibility.NOBODY, Res.string.profile_edit_visibility_nobody),
)

@Composable
private fun EditProfileState.fieldError(
    resource: StringResource,
    vararg errors: EditProfileFieldError,
): String? = if (errors.any { it in fieldErrors }) stringResource(resource) else null

private fun String.initials(): String = trim()
    .split(Regex("\\s+"))
    .filter(String::isNotEmpty)
    .take(2)
    .joinToString("") { it.take(1).uppercase() }
    .ifEmpty { "?" }

@Preview
@Composable
private fun EditProfilePreview() = SaqzTheme {
    val context = LocalPlatformContext.current
    val imageLoader = remember(context) { ImageLoader.Builder(context).build() }
    EditProfileScreen(
        state = EditProfileState.loaded(FakeProfileGateway().profile),
        onIntent = {},
        onPickPhoto = {},
        onBack = {},
        imageLoader = imageLoader,
    )
}
