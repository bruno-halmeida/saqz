package br.com.saqz.composeapp.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import androidx.savedstate.serialization.SavedStateConfiguration
import br.com.saqz.access.navigation.AccessRoute
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
            subclass(AccessRoute.Verification::class, AccessRoute.Verification.serializer())
            subclass(AccessRoute.NameCompletion::class, AccessRoute.NameCompletion.serializer())
            subclass(AccessRoute.PhoneCompletion::class, AccessRoute.PhoneCompletion.serializer())
            subclass(AccessRoute.Bootstrap::class, AccessRoute.Bootstrap.serializer())
            subclass(SaqzShellDestination::class, SaqzShellDestination.serializer())
        }
    }
}

/** The stack a cold start begins with, and the fallback for an unreadable saved stack. */
internal fun defaultAccessBackStack(): NavBackStack<NavKey> = NavBackStack(AccessRoute.Starting)

/**
 * Tolerant restore (VUL-35). A retained Android task can hold a back stack encoded by an
 * older build whose entries no longer exist: `Registration`/`PasswordReset` from this slice,
 * and every `GroupsRoute`/`ProductRoute` since C1 cut the registered key set from 23 to 9.
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
