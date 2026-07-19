package br.com.saqz.groups.testing

import java.nio.file.Files
import java.nio.file.Path

fun accessMigrationLocation(): String = "filesystem:" + accessMigrationDirectory()

private fun accessMigrationDirectory(): Path {
    val workingDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath()
    val candidates = listOf(
        workingDirectory.resolve("backend/features/access/src/main/resources/db/migration"),
        workingDirectory.resolve("features/access/src/main/resources/db/migration"),
        workingDirectory.resolve("../access/src/main/resources/db/migration").normalize(),
    )
    candidates.firstOrNull(Files::isDirectory)?.let { return it }
    error("Cannot find access migrations from working directory $workingDirectory")
}
