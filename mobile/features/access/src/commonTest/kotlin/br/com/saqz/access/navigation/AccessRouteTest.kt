package br.com.saqz.access.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AccessRouteTest {

    // VUL-84: o inventário é exaustivo — exatamente estas nove chaves, uma por tela do
    // fluxo 1 mais as duas sem tela (Starting e Bootstrap). `Verification` saiu; nome e
    // telefone viraram `IdentityCompletion`.
    private val allRoutes: List<AccessRoute> = listOf(
        AccessRoute.Starting,
        AccessRoute.Login,
        AccessRoute.Register,
        AccessRoute.IdentityCompletion,
        AccessRoute.ForgotPassword,
        AccessRoute.ResetCode(EMAIL),
        AccessRoute.NewPassword(EMAIL, TOKEN),
        AccessRoute.PasswordChanged,
        AccessRoute.Bootstrap,
    )

    @Test
    fun `route inventory contains exactly the nine specified keys`() {
        assertEquals(9, allRoutes.size)
        assertEquals(9, allRoutes.distinct().size)
    }

    @Test
    fun `every route is a NavKey`() {
        allRoutes.forEach { route -> assertTrue(route is NavKey) }
    }

    @Test
    fun `exhaustive when over AccessRoute covers every key without an else branch`() {
        allRoutes.forEach { route ->
            val label = when (route) {
                AccessRoute.Starting -> "Starting"
                AccessRoute.Login -> "Login"
                AccessRoute.Register -> "Register"
                AccessRoute.IdentityCompletion -> "IdentityCompletion"
                AccessRoute.ForgotPassword -> "ForgotPassword"
                is AccessRoute.ResetCode -> "ResetCode"
                is AccessRoute.NewPassword -> "NewPassword"
                AccessRoute.PasswordChanged -> "PasswordChanged"
                AccessRoute.Bootstrap -> "Bootstrap"
            }
            assertTrue(label.isNotBlank())
        }
    }

    @Test
    fun `each route key is equal to itself and unequal to every other key`() {
        allRoutes.forEachIndexed { index, route ->
            assertEquals(route, route)
            allRoutes.forEachIndexed { otherIndex, other ->
                if (index != otherIndex) assertNotEquals(route, other)
            }
        }
    }

    // As duas rotas com argumento não são intercambiáveis: e-mails ou tokens diferentes
    // são chaves diferentes, senão o back stack confundiria dois pedidos de código.
    @Test
    fun `argument routes are distinguished by their arguments`() {
        assertNotEquals(AccessRoute.ResetCode(EMAIL), AccessRoute.ResetCode("outra@exemplo.com"))
        assertNotEquals(AccessRoute.NewPassword(EMAIL, TOKEN), AccessRoute.NewPassword(EMAIL, "outro"))
    }

    @Test
    fun `each concrete route serializes and deserializes to an equal instance`() {
        allRoutes.forEach { route ->
            assertEquals(
                route,
                Json.decodeFromString(AccessRoute.serializer(), Json.encodeToString(AccessRoute.serializer(), route)),
            )
        }
    }

    private companion object {
        const val EMAIL = "ana@exemplo.com"
        const val TOKEN = "reset-token"
    }
}
