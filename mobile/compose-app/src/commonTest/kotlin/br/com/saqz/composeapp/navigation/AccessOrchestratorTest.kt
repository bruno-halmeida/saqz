package br.com.saqz.composeapp.navigation

import br.com.saqz.access.domain.port.NativeUser
import br.com.saqz.access.presentation.AuthTransition
import br.com.saqz.access.presentation.AuthScreen
import br.com.saqz.access.presentation.SessionAccessState
import br.com.saqz.access.presentation.SessionIntent
import br.com.saqz.composeapp.di.stopSaqzKoin
import br.com.saqz.composeapp.di.startSaqzKoin
import br.com.saqz.composeapp.testSaqzPlatformDependencies
import br.com.saqz.groups.domain.group.Group
import br.com.saqz.groups.domain.group.GroupGateway
import br.com.saqz.groups.domain.group.CreateGroupCommand
import br.com.saqz.groups.domain.group.UpdateGroupSettingsCommand
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.domain.group.VersionedGroup
import br.com.saqz.groups.presentation.GroupSelectionState
import br.com.saqz.access.domain.session.AccessSession
import br.com.saqz.access.domain.session.SessionGateway
import br.com.saqz.access.domain.session.AccessMembership
import br.com.saqz.access.domain.session.AccessMembershipRole
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.access.domain.session.AccessUser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.koin.core.context.loadKoinModules
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module
import org.koin.mp.KoinPlatformTools

class AccessOrchestratorTest {
    @Test
    fun `ready session reconciles the selected group into administration`() {
        stopSaqzKoin()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            startSaqzKoin(testSaqzPlatformDependencies())
            loadKoinModules(module {
                single<SessionGateway> { ReadySessionGateway }
                single<GroupGateway> { SelectedGroupGateway }
            })
            val orchestrator = KoinPlatformTools.defaultContext().get()
                .get<AccessOrchestrator> { parametersOf(scope) }

            orchestrator.onIntent(
                AccessRuntimeIntent.Session(
                    SessionIntent.Accept(
                        AuthTransition.Authenticated(NativeUser("user-id", "person@example.test", true, "Person")),
                    ),
                ),
            )

            assertEquals(SessionAccessState.Ready(readySession), orchestrator.state.value.session)
            assertEquals(GroupSelectionState.Selected(selectedGroup), orchestrator.state.value.selection)
            assertEquals(selectedGroup, orchestrator.state.value.administration.group)
        } finally {
            scope.cancel()
            stopSaqzKoin()
        }
    }

    @Test
    fun `signed out session follows a ready session through orchestration`() {
        stopSaqzKoin()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            startSaqzKoin(testSaqzPlatformDependencies())
            loadKoinModules(module {
                single<SessionGateway> { ReadySessionGateway }
                single<GroupGateway> { SelectedGroupGateway }
            })
            val orchestrator = KoinPlatformTools.defaultContext().get()
                .get<AccessOrchestrator> { parametersOf(scope) }

            orchestrator.onIntent(
                AccessRuntimeIntent.Session(
                    SessionIntent.Accept(
                        AuthTransition.Authenticated(NativeUser("user-id", "person@example.test", true, "Person")),
                    ),
                ),
            )
            orchestrator.onIntent(AccessRuntimeIntent.Session(SessionIntent.Logout))

            assertEquals(SessionAccessState.SignedOut, orchestrator.state.value.session)
        } finally {
            scope.cancel()
            stopSaqzKoin()
        }
    }

    @Test
    fun `start observes auth and reconciles signed out state into login`() {
        stopSaqzKoin()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            startSaqzKoin(testSaqzPlatformDependencies())
            val orchestrator = KoinPlatformTools.defaultContext().get()
                .get<AccessOrchestrator> { parametersOf(scope) }

            assertFalse(orchestrator.state.value.authObserved)

            orchestrator.onIntent(AccessRuntimeIntent.Start)

            assertTrue(orchestrator.state.value.authObserved)
            assertEquals(AuthScreen.LOGIN, orchestrator.state.value.authentication.screen)
        } finally {
            scope.cancel()
            stopSaqzKoin()
        }
    }

    private object ReadySessionGateway : SessionGateway {
        override suspend fun bootstrap() = SaqzResult.Success(readySession)
    }

    private object SelectedGroupGateway : GroupGateway {
        override suspend fun create(command: CreateGroupCommand) =
            error("not used by this test")

        override suspend fun read(groupId: GroupId) = SaqzResult.Success(selectedGroup)

        override suspend fun update(command: UpdateGroupSettingsCommand) =
            error("not used by this test")
    }

    private companion object {
        val readySession = AccessSession(
            user = AccessUser("user-id", "person@example.test", "Person"),
            memberships = listOf(AccessMembership(GroupId("group-id"), "Group", AccessMembershipRole("OWNER"))),
        )
        val selectedGroup = VersionedGroup(
            group = Group("group-id", "Group", "UTC", 7, GroupRole.OWNER),
            versionToken = "\"7\"",
        )
    }
}
