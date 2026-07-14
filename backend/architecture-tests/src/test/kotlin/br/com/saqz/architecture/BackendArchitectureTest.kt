package br.com.saqz.architecture

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackendArchitectureTest {
    private val repositoryRoot: Path = Path.of("../..").toAbsolutePath().normalize()

    @Test
    fun `ARCH-01 exposes separate bootstrap shared kernel and identity modules`() {
        val settings = repositoryRoot.resolve("settings.gradle.kts").readText()

        assertTrue(settings.contains("include(\":backend:bootstrap\")"))
        assertTrue(settings.contains("include(\":backend:shared-kernel\")"))
        assertTrue(settings.contains("include(\":backend:features:identity\")"))
    }

    @Test
    fun `ARCH-02 keeps identity domain and application free of frameworks and other features`() {
        noClasses()
            .that().resideInAnyPackage(
                "br.com.saqz.identity.domain..",
                "br.com.saqz.identity.application..",
            )
            .should().dependOnClassesThat().resideInAnyPackage(
                "org.springframework..",
                "com.google.firebase..",
                "jakarta.persistence..",
                "javax.persistence..",
                "java.sql..",
                "br.com.saqz.groups..",
                "br.com.saqz.athletes..",
                "br.com.saqz.games..",
                "br.com.saqz.finance..",
                "br.com.saqz.subscriptions..",
            )
            .check(ClassFileImporter().importPackages("br.com.saqz.identity"))
    }

    @Test
    fun `ARCH-03 prevents domain and application from referencing adapters`() {
        noClasses()
            .that().resideInAnyPackage(
                "br.com.saqz.identity.domain..",
                "br.com.saqz.identity.application..",
            )
            .should().dependOnClassesThat().resideInAnyPackage("br.com.saqz.identity.adapter..")
            .check(ClassFileImporter().importPackages("br.com.saqz.identity"))
    }

    @Test
    fun `ARCH-04 limits bootstrap to entry point wiring and configuration packages`() {
        val sourceRoot = repositoryRoot.resolve("backend/bootstrap/src/main/kotlin")
        val forbiddenSegments = setOf("domain", "application", "usecase", "port", "adapter")
        val sourcePaths = kotlinSources(sourceRoot).map(sourceRoot::relativize)

        assertTrue(sourcePaths.isNotEmpty())
        assertTrue(sourcePaths.none { path -> path.any { it.toString() in forbiddenSegments } })
    }

    @Test
    fun `ARCH-05 keeps identity contracts provider neutral`() {
        val contractRoots = listOf("api", "application", "domain")
            .map { repositoryRoot.resolve("backend/features/identity/src/main/kotlin/br/com/saqz/identity/$it") }
            .filter(Path::isDirectory)
        val imports = contractRoots
            .flatMap(::kotlinSources)
            .flatMap { source -> source.readText().lineSequence().filter { it.startsWith("import ") }.toList() }

        assertTrue(imports.all { it.startsWith("import br.com.saqz.identity.") || it.startsWith("import kotlin.") })
        assertFalse(repositoryRoot.resolve("backend/features/identity/build.gradle.kts").readText().contains("spring"))
        assertFalse(repositoryRoot.resolve("backend/features/identity/build.gradle.kts").readText().contains("firebase"))
    }

    @Test
    fun `ARCH-06 enforces the dependency direction documented for backend features`() {
        val sharedBuild = repositoryRoot.resolve("backend/shared-kernel/build.gradle.kts").readText()
        val identityBuild = repositoryRoot.resolve("backend/features/identity/build.gradle.kts").readText()

        assertFalse(sharedBuild.contains("project("))
        assertFalse(identityBuild.contains(":backend:bootstrap"))
        assertFalse(identityBuild.contains(":backend:features:"))
    }

    @Test
    fun `ARCH-07 keeps identity as the only backend feature`() {
        val featuresRoot = repositoryRoot.resolve("backend/features")
        val featureDirectories = Files.list(featuresRoot).use { paths ->
            paths.filter(Path::isDirectory).map(Path::name).sorted().toList()
        }

        assertEquals(listOf("identity"), featureDirectories)
    }

    @Test
    fun `ARCH-08 separates backend and client build graphs`() {
        val backendBuilds = gradleBuilds(repositoryRoot.resolve("backend"))
        val forbiddenClients = listOf(":mobile", ":app-android", "app-ios", "app-web")
        val clientRoots = listOf("mobile", "app-android", "app-ios", "app-web")
            .map(repositoryRoot::resolve)
            .filter(Path::isDirectory)

        assertTrue(backendBuilds.none { build -> forbiddenClients.any(build.readText()::contains) })
        assertTrue(clientRoots.flatMap(::gradleBuilds).none { it.readText().contains(":backend:") })
    }

    private fun kotlinSources(root: Path): List<Path> =
        if (root.isDirectory()) {
            Files.walk(root).use { paths ->
                paths.filter { it.extension == "kt" }.sorted().toList()
            }
        } else {
            emptyList()
        }

    private fun gradleBuilds(root: Path): List<Path> =
        if (root.isDirectory()) {
            Files.walk(root).use { paths ->
                paths.filter { it.name == "build.gradle.kts" }.sorted().toList()
            }
        } else {
            emptyList()
        }
}
