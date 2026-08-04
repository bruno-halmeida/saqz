package br.com.saqz.access.testing

import java.nio.file.Files
import java.nio.file.Path

/**
 * O diretório administrativo junta tabelas de access, groups (deleted_at de grupos) e
 * subscriptions (plano). Estes locais espelham o helper equivalente dos outros módulos.
 */
fun allAdminDirectoryMigrationLocations(): Array<String> = arrayOf(
    "filesystem:" + migrationDirectory("access"),
    "filesystem:" + migrationDirectory("groups"),
    "filesystem:" + migrationDirectory("subscriptions"),
)

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
