package br.com.saqz.groups.presentation.setup

import androidx.lifecycle.SavedStateHandle
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.presentation.FakeGroupGateway
import br.com.saqz.groups.presentation.FakeGroupProfileGateway
import br.com.saqz.groups.presentation.FakeGroupSystemTimeZonePort
import br.com.saqz.groups.presentation.sampleGroup
import br.com.saqz.groups.presentation.sampleVersionedGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Pix do grupo (adendo): o que hoje já vale e continua valendo com o tipo de chave. */
@OptIn(ExperimentalCoroutinesApi::class)
class GroupSetupPixCharacterizationTest {
    private val mainDispatcher = UnconfinedTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(mainDispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `salvar o grupo sem mexer no Pix envia os dois valores como vieram`() = runTest(mainDispatcher) {
        val group = sampleGroup().copy(
            profile = sampleGroup().profile?.copy(pixKey = "chave antiga em texto livre", pixLabel = "Lucas Prado · Nubank"),
        )
        val gateway = FakeGroupProfileGateway(readResult = SaqzResult.Success(sampleVersionedGroup(group)))
        val viewModel = GroupSetupViewModel(
            initialState = GroupSetupState(mode = GroupSetupMode.Edit("grp-1")),
            savedState = SavedStateHandle(),
            groupGateway = FakeGroupGateway(),
            profileGateway = gateway,
            timeZonePort = FakeGroupSystemTimeZonePort(),
        )
        runCurrent()

        viewModel.onIntent(GroupSetupIntent.Submit)
        runCurrent()

        assertEquals("chave antiga em texto livre", gateway.lastUpdateCommand?.form?.pixKey)
        assertEquals("Lucas Prado · Nubank", gateway.lastUpdateCommand?.form?.pixLabel)
    }
}
