package br.com.saqz.groups.testing

import java.nio.file.Files
import java.nio.file.Path

fun accessMigrationLocation(): String = "filesystem:" + accessMigrationDirectory()
fun groupMigrationLocation(): String = "filesystem:" + migrationDirectory("groups")
fun allGroupFeatureMigrationLocations(): Array<String> =
    arrayOf(accessMigrationLocation(), groupMigrationLocation())

private fun accessMigrationDirectory(): Path {
    return migrationDirectory("access")
}

private fun migrationDirectory(feature: String): Path {
    val workingDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath()
    val candidates = listOf(
        workingDirectory.resolve("backend/features/$feature/src/main/resources/db/migration"),
        workingDirectory.resolve("features/$feature/src/main/resources/db/migration"),
        workingDirectory.resolve("../$feature/src/main/resources/db/migration").normalize(),
    )
    candidates.firstOrNull(Files::isDirectory)?.let { return it }
    error("Cannot find $feature migrations from working directory $workingDirectory")
}
