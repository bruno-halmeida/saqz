package br.com.saqz.bootstrap

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import br.com.saqz.bootstrap.operations.CREDENTIAL_RECOVERY_COMMAND
import br.com.saqz.bootstrap.operations.runCredentialRecoveryCommand
import kotlin.system.exitProcess

@SpringBootApplication(proxyBeanMethods = false)
class SaqzApplication

fun main(args: Array<String>) {
    if (args.firstOrNull() == CREDENTIAL_RECOVERY_COMMAND) {
        exitProcess(runCredentialRecoveryCommand(args.drop(1)))
    }
    runApplication<SaqzApplication>(*args)
}
