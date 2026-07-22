package br.com.saqz.access.domain.session

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.domain.ValidationDetails
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class AccessSessionTest {
    @Test fun `user preserves identity email and display name`() {
        assertEquals(
            AccessUser("user-1", "person@example.test", "Person"),
            AccessUser("user-1", "person@example.test", "Person"),
        )
    }

    @Test fun `user accepts absent email`() {
        assertNull(AccessUser("user-1", null, "Person").email)
    }

    @Test fun `session accepts empty memberships`() {
        assertEquals(emptyList(), AccessSession(user(), emptyList()).memberships)
    }

    @Test fun `session preserves multiple memberships in order`() {
        val memberships = listOf(membership("one", "OWNER"), membership("two", "ATHLETE"))

        assertEquals(memberships, AccessSession(user(), memberships).memberships)
    }

    @Test fun `membership role preserves access owned raw value`() {
        assertEquals("CUSTOM_ROLE", AccessMembershipRole("CUSTOM_ROLE").value)
    }

    @Test fun `membership preserves group identity and name`() {
        val membership = membership("group-1", "ADMIN")

        assertEquals(GroupId("group-1"), membership.groupId)
        assertEquals("Group group-1", membership.groupName)
    }

    @Test fun `gateway success carries exact access session`() {
        val session = AccessSession(user(), listOf(membership("group-1", "OWNER")))
        val result: SaqzResult<AccessSession, AccessError> = SaqzResult.Success(session)

        assertEquals(session, assertIs<SaqzResult.Success<AccessSession>>(result).value)
    }

    @Test fun `unauthenticated is an explicit access error`() {
        assertEquals(AccessError.Unauthenticated, failure(AccessError.Unauthenticated))
    }

    @Test fun `forbidden is an explicit access error`() {
        assertEquals(AccessError.Forbidden, failure(AccessError.Forbidden))
    }

    @Test fun `email not verified is an explicit access error`() {
        assertEquals(AccessError.EmailNotVerified, failure(AccessError.EmailNotVerified))
    }

    @Test fun `validation error preserves safe global and field messages`() {
        val details = ValidationDetails(
            globalMessages = listOf("Check the form"),
            fieldMessages = mapOf("email" to listOf("Invalid email")),
        )

        assertEquals(details, assertIs<AccessError.Validation>(failure(AccessError.Validation(details))).details)
    }

    @Test fun `data failure preserves exact shared error`() {
        assertEquals(
            DataError.Connectivity,
            assertIs<AccessError.DataFailure>(failure(AccessError.DataFailure(DataError.Connectivity))).error,
        )
    }

    private fun failure(error: AccessError): AccessError {
        val result: SaqzResult<AccessSession, AccessError> = SaqzResult.Failure(error)
        return assertIs<SaqzResult.Failure<AccessError>>(result).error
    }

    private fun user() = AccessUser("user-1", "person@example.test", "Person")

    private fun membership(id: String, role: String) = AccessMembership(
        groupId = GroupId(id),
        groupName = "Group $id",
        role = AccessMembershipRole(role),
    )
}
