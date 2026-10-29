package br.com.saqz.composeapp.navigation

import br.com.saqz.groups.presentation.navigation.GroupsRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** DEB-146: o aviso do grupo sem valor leva ao "Valor por jogo" de Editar grupo; o resto segue como antes. */
class GameFeeNoticeNavigationTest {
    @Test fun theGameFeeNoticeOpensTheGameFeeBlockOfEditGroup() {
        assertEquals(GroupsRoute.Edit("ceret", focusGameFee = true), gameFeeNoticeDestination("GAME_FEE_UNDEFINED", "ceret"))
    }

    @Test fun otherNoticesAndOldServersKeepTheirWay() {
        assertNull(gameFeeNoticeDestination(null, "ceret"))
        assertNull(gameFeeNoticeDestination("SOMETHING_ELSE", "ceret"))
        assertNull(gameFeeNoticeDestination("GAME_FEE_UNDEFINED", null))
    }
}
