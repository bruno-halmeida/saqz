package br.com.saqz.composeapp.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import androidx.savedstate.serialization.SavedStateConfiguration
import br.com.saqz.access.navigation.AccessRoute
import br.com.saqz.groups.presentation.navigation.GroupsRoute
import br.com.saqz.groups.presentation.navigation.FinanceRoute
import br.com.saqz.profile.presentation.navigation.ProfileRoute
import br.com.saqz.subscriptions.presentation.navigation.SubscriptionsRoute
import kotlinx.serialization.KSerializer
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

/**
 * C1: the app-owned [SavedStateConfiguration] for the single acesso→shell back stack,
 * replacing `:navigation`'s product-wide one (that module dies in C3). Reflection-based
 * route serialization is unavailable on iOS, so every concrete key is registered
 * explicitly under [NavKey] — omit a leaf and restoration fails for that route.
 */
val saqzLocalNavConfiguration: SavedStateConfiguration = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(AccessRoute.Starting::class, AccessRoute.Starting.serializer())
            subclass(AccessRoute.Login::class, AccessRoute.Login.serializer())
            subclass(AccessRoute.Register::class, AccessRoute.Register.serializer())
            subclass(AccessRoute.IdentityCompletion::class, AccessRoute.IdentityCompletion.serializer())
            subclass(AccessRoute.ForgotPassword::class, AccessRoute.ForgotPassword.serializer())
            subclass(AccessRoute.ResetCode::class, AccessRoute.ResetCode.serializer())
            subclass(AccessRoute.NewPassword::class, AccessRoute.NewPassword.serializer())
            subclass(AccessRoute.PasswordChanged::class, AccessRoute.PasswordChanged.serializer())
            subclass(AccessRoute.Bootstrap::class, AccessRoute.Bootstrap.serializer())
            subclass(SaqzShellDestination::class, SaqzShellDestination.serializer())
            // As rotas de grupo entram no VUL-72: agora que o stack tem profundidade, é
            // este registro que faz um `Details` sobreviver à rotação e ao Recents.
            subclass(GroupsRoute.Create::class, GroupsRoute.Create.serializer())
            subclass(GroupsRoute.Details::class, GroupsRoute.Details.serializer())
            subclass(GroupsRoute.Edit::class, GroupsRoute.Edit.serializer())
            subclass(GroupsRoute.Members::class, GroupsRoute.Members.serializer())
            subclass(GroupsRoute.Schedule::class, GroupsRoute.Schedule.serializer())
            subclass(GroupsRoute.Invite::class, GroupsRoute.Invite.serializer())
            subclass(GroupsRoute.InviteMessagePreview::class, GroupsRoute.InviteMessagePreview.serializer())
            subclass(GroupsRoute.InviteQr::class, GroupsRoute.InviteQr.serializer())
            subclass(GroupsRoute.InviteLanding::class, GroupsRoute.InviteLanding.serializer())
            subclass(GroupsRoute.AthleteRegistration::class, GroupsRoute.AthleteRegistration.serializer())
            subclass(GroupsRoute.MemberEditor::class, GroupsRoute.MemberEditor.serializer())
            // VUL-151: rotas de jogo — registro incondicional para sobreviver à rotação.
            subclass(GroupsRoute.GameEditor::class, GroupsRoute.GameEditor.serializer())
            subclass(GroupsRoute.GameDetail::class, GroupsRoute.GameDetail.serializer())
            // VUL-108: registro incondicional (AGENTS.md) mesmo sem nenhuma tela ainda —
            // sem isso a rota não sobrevive à rotação quando VUL-109..111 a empilharem.
            subclass(SubscriptionsRoute.PlanSelection::class, SubscriptionsRoute.PlanSelection.serializer())
            subclass(SubscriptionsRoute.Payment::class, SubscriptionsRoute.Payment.serializer())
            subclass(SubscriptionsRoute.PlanActive::class, SubscriptionsRoute.PlanActive.serializer())
            // VUL-112: mesmo registro incondicional — 8e ainda não tem tela ligada ao
            // NavDisplay, mas a rota precisa sobreviver à rotação assim que alguém a empilhar.
            subclass(SubscriptionsRoute.MyPlan::class, SubscriptionsRoute.MyPlan.serializer())
            subclass(ProfileRoute.Edit::class, ProfileRoute.Edit.serializer())
            subclass(ProfileRoute.Exit::class, ProfileRoute.Exit.serializer())
            subclass(FinanceRoute.GroupCashbox::class, FinanceRoute.GroupCashbox.serializer())
            subclass(FinanceRoute.Statement::class, FinanceRoute.Statement.serializer())
            subclass(FinanceRoute.GameSettlement::class, FinanceRoute.GameSettlement.serializer())
        }
    }
}

/** The stack a cold start begins with, and the fallback for an unreadable saved stack. */
internal fun defaultAccessBackStack(): NavBackStack<NavKey> = NavBackStack(AccessRoute.Starting)

/**
 * Tolerant restore (VUL-35). A retained Android task can hold a back stack encoded by an
 * older build whose entries no longer exist: `Registration`/`PasswordReset` from this slice,
 * every `GroupsRoute`/`ProductRoute` since C1, e agora `Verification`, `NameCompletion` e
 * `PhoneCompletion`, que o VUL-84 tirou do conjunto de chaves.
 * Polymorphic decoding of an unregistered key throws inside the back stack restore, which
 * runs *before* [reconcileAccessStack] can canonicalize the stack — the app would fail to
 * reopen from Recents with no way out.
 *
 * So restoration is tolerant rather than exhaustive: any payload that does not decode under
 * the current key set is discarded for [defaultAccessBackStack], which the session
 * reconciliation immediately replaces with the real destination. The deleted routes are
 * *not* re-registered — this reset does not keep a legacy decode path alive.
 */
internal val saqzAccessBackStackSerializer: KSerializer<NavBackStack<NavKey>> =
    TolerantNavBackStackSerializer(NavBackStackSerializer(PolymorphicSerializer(NavKey::class)))

private class TolerantNavBackStackSerializer(
    private val delegate: KSerializer<NavBackStack<NavKey>>,
) : KSerializer<NavBackStack<NavKey>> {
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: NavBackStack<NavKey>) =
        delegate.serialize(encoder, value)

    // Deliberately broad: an unreadable saved stack has no partial recovery worth
    // attempting, and the failure shape varies (unknown polymorphic discriminator,
    // missing field, wrong container). Anything that does not decode is a cold start.
    override fun deserialize(decoder: Decoder): NavBackStack<NavKey> =
        runCatching { delegate.deserialize(decoder) }.getOrElse { defaultAccessBackStack() }
}
