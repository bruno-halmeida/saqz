package br.com.saqz.groups

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class GroupsModuleIntegrationTest {
    @Test
    fun `executes the groups PostgreSQL integration source set`() {
        assertEquals("br.com.saqz.groups", javaClass.packageName)
    }
}
