package br.com.saqz.bootstrap.operations

import br.com.saqz.receivables.application.CredentialRecoveryResult
import br.com.saqz.receivables.application.RecoverFinancialCredential
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor
import java.io.ByteArrayInputStream
import java.util.Base64
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CredentialRecoveryCommandLineTest {
    private val ids = List(5) { UUID.randomUUID().toString() }
    private val arguments = listOf("--request-id", ids[0], "--account-id", ids[1], "--owner-id", ids[2],
        "--creation-operation-id", ids[3], "--operator-id", ids[4], "--provider-account-id", "subaccount", "--wallet-id", "wallet")

    @Test
    fun `secret is read privately command identity is preserved and buffer is erased`() {
        val secret = "test-only-credential".toCharArray()
        val output = mutableListOf<String>()
        var calls = 0
        val exit = runCredentialRecoveryCommand(arguments, { secret }, output::add) { command, credential ->
            calls++
            assertEquals(ids[1], command.accountId.toString())
            assertEquals(ids[4], command.operatorId.toString())
            assertEquals("subaccount", command.providerAccountId)
            assertEquals("test-only-credential", credential.value)
            CredentialRecoveryResult.RECOVERED
        }
        assertEquals(0, exit)
        assertEquals(1, calls)
        assertEquals(listOf("RECOVERED"), output)
        assertTrue(secret.all { it == '\u0000' })
    }

    @Test
    fun `unknown duplicate and secret arguments fail before reading or executing`() {
        val malformed = listOf(arguments + listOf("--api-key", "never-print-this"),
            arguments.map { if (it == "--owner-id") "--account-id" else it },
            arguments.map { if (it == ids[0]) "1-1-1-1-1" else it })
        malformed.forEach { args ->
            val output = mutableListOf<String>()
            val exit = runCredentialRecoveryCommand(args, { error("must not read secret") }, output::add) { _, _ ->
                error("must not initialize recovery")
            }
            assertEquals(2, exit)
            assertFalse(output.single().contains("never-print-this"))
        }
    }

    @Test
    fun `failures never print the exception credential and always erase input`() {
        val secret = "private-key-test".toCharArray()
        val output = mutableListOf<String>()
        assertEquals(1, runCredentialRecoveryCommand(arguments, { secret }, output::add) { _, credential ->
            throw IllegalStateException(credential.value)
        })
        assertEquals(listOf("UNAVAILABLE"), output)
        assertTrue(secret.all { it == '\u0000' })
    }

    @Test
    fun `stdin is bounded and whitespace or absent secrets never execute`() {
        assertEquals("valid-test-key", String(requireNotNull(readRecoverySecret(ByteArrayInputStream("valid-test-key\n".toByteArray())))))
        listOf("", "has spaces", "x".repeat(8193)).forEach { value ->
            val output = mutableListOf<String>()
            assertEquals(2, runCredentialRecoveryCommand(arguments,
                { readRecoverySecret(ByteArrayInputStream(value.toByteArray())) }, output::add) { _, _ ->
                error("invalid input must not execute")
            })
        }
    }

    @Test
    fun `explicit command context wires recovery without web scheduling or component discovery`() {
        val encoded = Base64.getEncoder().encodeToString(ByteArray(32) { 7 })
        ApplicationContextRunner().withUserConfiguration(CredentialRecoveryContext::class.java).withPropertyValues(
            "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never-connect",
            "saqz.receivables.encryption-key-id=test",
            "saqz.receivables.encryption-key=$encoded",
            "saqz.receivables.identity-lookup-key=$encoded",
            "saqz.receivables.asaas-base-url=https://api-sandbox.asaas.com/v3",
            "saqz.receivables.asaas-platform-key=platform-test-only",
        ).run { context ->
            assertNotNull(context.getBean(RecoverFinancialCredential::class.java))
            assertTrue(context.getBeansOfType(ScheduledAnnotationBeanPostProcessor::class.java).isEmpty())
            assertFalse(context.beanDefinitionNames.any { it.contains("Controller") || it.contains("SaqzApplication") })
        }
        val candidates = ClassPathScanningCandidateComponentProvider(true)
            .findCandidateComponents("br.com.saqz.bootstrap.operations")
        assertTrue(candidates.isEmpty(), "operational beans must not be discovered by normal server startup")
    }
}
