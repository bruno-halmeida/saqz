package br.com.saqz.subscriptions.testing

import java.nio.file.Files
import java.nio.file.Path

fun subscriptionsMigrationLocation(): String = "filesystem:" + migrationDirectory()

private fun migrationDirectory(): Path {
    val workingDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath()
    val candidates = listOf(
        workingDirectory.resolve("backend/features/subscriptions/src/main/resources/db/migration"),
        workingDirectory.resolve("features/subscriptions/src/main/resources/db/migration"),
        workingDirectory.resolve("../subscriptions/src/main/resources/db/migration").normalize(),
    )
    candidates.firstOrNull(Files::isDirectory)?.let { return it }
    error("Cannot find subscriptions migrations from working directory $workingDirectory")
}
