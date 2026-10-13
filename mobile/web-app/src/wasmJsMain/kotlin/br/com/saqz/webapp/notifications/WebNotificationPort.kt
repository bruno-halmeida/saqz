package br.com.saqz.webapp.notifications

import br.com.saqz.groups.domain.communication.NativeNotificationPort
import br.com.saqz.groups.domain.communication.NotificationDevice
import br.com.saqz.groups.domain.communication.NotificationSubscription

/**
 * Sem push no navegador por enquanto: `device` devolve nulo e o app segue sem registrar aparelho,
 * como um Android sem permissão. O push web (FCM + service worker) entra numa etapa própria.
 */
internal class WebNotificationPort : NativeNotificationPort {
    override fun device(done: (NotificationDevice?) -> Unit) = done(null)
    override fun clear(done: (Boolean) -> Unit) = done(true)
    override fun dismissAll() = Unit
    override fun observe(changed: () -> Unit): NotificationSubscription = NotificationSubscription { }
}
