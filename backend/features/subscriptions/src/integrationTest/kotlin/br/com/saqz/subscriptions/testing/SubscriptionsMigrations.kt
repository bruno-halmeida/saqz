package br.com.saqz.subscriptions.testing

import java.nio.file.Files
import java.nio.file.Path

fun accessMigrationLocation(): String = "filesystem:" + migrationDirectory("access")
fun subscriptionsMigrationLocation(): String = "filesystem:" + migrationDirectory("subscriptions")
/** Com o módulo de grupos: para quem precisa de `access_groups.deleted_at` (cortesia em lote, DEB-097). */
fun subscriptionsWithGroupsMigrationLocations(): Array<String> =
    allSubscriptionsFeatureMigrationLocations() + ("filesystem:" + migrationDirectory("groups"))
fun allSubscriptionsFeatureMigrationLocations(): Array<String> =
    arrayOf(accessMigrationLocation(), subscriptionsMigrationLocation())

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
