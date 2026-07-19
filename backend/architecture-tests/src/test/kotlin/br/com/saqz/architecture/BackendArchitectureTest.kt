package br.com.saqz.architecture

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackendArchitectureTest {
    private val workspaceRoot: Path = Path.of("../..").toAbsolutePath().normalize()
    private val backendRoot: Path = workspaceRoot.resolve("backend")

    @Test
    fun `ARCH-01 exposes separate bootstrap shared kernel identity and groups modules`() {
        val settings = backendRoot.resolve("settings.gradle.kts").readText()
        val modules = Regex("include\\(\"([^\"]+)\"\\)")
            .findAll(settings)
            .map { it.groupValues[1] }
            .toSet()

        assertEquals(
            setOf(":shared-kernel", ":features:access", ":features:groups", ":features:identity", ":bootstrap", ":architecture-tests"),
            modules,
        )
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
        val sourceRoot = backendRoot.resolve("bootstrap/src/main/kotlin")
        val forbiddenSegments = setOf("domain", "application", "usecase", "port", "adapter")
        val sourcePaths = kotlinSources(sourceRoot).map(sourceRoot::relativize)

        assertTrue(sourcePaths.isNotEmpty())
        assertTrue(sourcePaths.none { path -> path.any { it.toString() in forbiddenSegments } })
    }

    @Test
    fun `ARCH-05 keeps identity contracts provider neutral`() {
        val contractRoots = listOf("api", "application", "domain")
            .map { backendRoot.resolve("features/identity/src/main/kotlin/br/com/saqz/identity/$it") }
            .filter(Path::isDirectory)
        val imports = contractRoots
            .flatMap(::kotlinSources)
            .flatMap { source -> source.readText().lineSequence().filter { it.startsWith("import ") }.toList() }

        assertTrue(
            imports.all {
                it.startsWith("import br.com.saqz.identity.") ||
                    it.startsWith("import br.com.saqz.sharedkernel.") ||
                    it.startsWith("import kotlin.")
            },
        )
    }

    @Test
    fun `ARCH-06 enforces the dependency direction documented for backend features`() {
        val sharedBuild = backendRoot.resolve("shared-kernel/build.gradle.kts").readText()
        val identityBuild = backendRoot.resolve("features/identity/build.gradle.kts").readText()

        assertFalse(sharedBuild.contains("project("))
        assertFalse(identityBuild.contains(":bootstrap"))
        assertFalse(identityBuild.contains(":features:"))
    }

    @Test
    fun `ARCH-07 keeps the approved backend feature inventory`() {
        val featuresRoot = backendRoot.resolve("features")
        val featureDirectories = Files.list(featuresRoot).use { paths ->
            paths.filter(Path::isDirectory).map(Path::name).sorted().toList()
        }

        assertEquals(listOf("access", "groups", "identity"), featureDirectories)
    }

    @Test
    fun `ARCH-08 separates backend and client build graphs`() {
        val rootGradlePaths = listOf(
            "gradlew",
            "gradlew.bat",
            "gradle/libs.versions.toml",
            "gradle/wrapper/gradle-wrapper.jar",
            "gradle/wrapper/gradle-wrapper.properties",
            "settings.gradle.kts",
            "build.gradle.kts",
            "build-logic/settings.gradle.kts",
            "build-logic/build.gradle.kts",
            "build-logic/src/main/kotlin/br/com/saqz/buildlogic/JvmBackendConventionPlugin.kt",
        )
        val requiredBackendPaths = rootGradlePaths.map(backendRoot::resolve)
        val settings = backendRoot.resolve("settings.gradle.kts").readText()
        val configurationFiles = gradleBuilds(backendRoot) + listOf(
            backendRoot.resolve("settings.gradle.kts"),
            backendRoot.resolve("gradle/libs.versions.toml"),
            backendRoot.resolve("build-logic/settings.gradle.kts"),
        ) + kotlinSources(backendRoot.resolve("build-logic/src/main/kotlin"))
        val configuration = configurationFiles.joinToString("\n") { it.readText() }
        val includedBuilds = Regex("includeBuild\\(\\s*\"([^\"]+)\"\\s*\\)")
            .findAll(settings)
            .map { it.groupValues[1] }
            .toList()
        val allowedProjects = setOf(":shared-kernel", ":features:access", ":features:groups", ":features:identity", ":bootstrap")
        val projectDependencies = Regex("project\\(\\s*\"([^\"]+)\"\\s*\\)")
            .findAll(configuration)
            .map { it.groupValues[1] }
            .toSet()
        val forbiddenBackendTooling = listOf(
            "org.jetbrains.compose",
            "kotlin-multiplatform",
            "com.android",
            "android-gradle",
            "mobile",
            "ios",
        )
        val siblingArtifact = Regex("(?:\\.\\./)+mobile(?:/|\\\")|br\\.com\\.saqz(?::|\\.)mobile")

        assertTrue(rootGradlePaths.none { workspaceRoot.resolve(it).exists() })
        assertTrue(requiredBackendPaths.all(Path::exists))
        assertEquals(listOf("build-logic"), includedBuilds)
        assertTrue(projectDependencies.all(allowedProjects::contains))
        assertFalse(siblingArtifact.containsMatchIn(configuration.lowercase()))
        assertTrue(forbiddenBackendTooling.none(configuration.lowercase()::contains))
    }

    @Test
    fun `ARCH-09 keeps shared request identity provider neutral`() {
        val source = backendRoot
            .resolve("shared-kernel/src/main/kotlin/br/com/saqz/sharedkernel/RequestIdentity.kt")

        assertTrue(source.exists())
        assertTrue(
            source.readText().lineSequence()
                .filter { it.startsWith("import ") }
                .none { it.contains("springframework") || it.contains("firebase") },
        )
    }

    @Test
    fun `ARCH-10 removes the feature owned authenticated principal`() {
        val legacyPrincipal = backendRoot
            .resolve("features/identity/src/main/kotlin/br/com/saqz/identity/api/AuthenticatedPrincipal.kt")
        val productionSources = kotlinSources(backendRoot)
            .filter { it.toString().replace('\\', '/').contains("/src/main/kotlin/") }

        assertFalse(legacyPrincipal.exists())
        assertTrue(productionSources.none { it.readText().contains("AuthenticatedPrincipal") })
    }

    @Test
    fun `ARCH-11 exposes exactly access groups and identity backend features`() {
        assertEquals(listOf("access", "groups", "identity"), featureDirectories().map(Path::name))
    }

    @Test
    fun `ARCH-12 every backend feature depends on shared kernel`() {
        featureDirectories().forEach { feature ->
            assertTrue(feature.resolve("build.gradle.kts").readText().contains("project(\":shared-kernel\")"))
        }
    }

    @Test
    fun `ARCH-13 backend feature builds never depend on another feature`() {
        featureDirectories().forEach { feature ->
            assertFalse(feature.resolve("build.gradle.kts").readText().contains("project(\":features:"))
        }
    }

    @Test
    fun `ARCH-14 backend feature code never imports another feature`() {
        val featurePackages = featureDirectories().map { "br.com.saqz.${it.name}." }

        featureDirectories().forEach { feature ->
            kotlinSources(feature.resolve("src/main/kotlin")).forEach { source ->
                val ownPackage = "br.com.saqz.${feature.name}."
                assertTrue(
                    source.readText().lineSequence()
                        .filter { it.startsWith("import ") }
                        .none { imported -> featurePackages.any { imported.startsWith("import $it") && it != ownPackage } },
                )
            }
        }
    }

    @Test
    fun `ARCH-15 all feature domain and application code stays framework and JDBC free`() {
        val forbidden = listOf("org.springframework.", "com.google.firebase.", "jakarta.persistence.", "java.sql.")

        featureLayerSources("domain", "application").forEach { source ->
            assertTrue(forbidden.none(source.readText()::contains), source.toString())
        }
    }

    @Test
    fun `ARCH-16 all feature domain and application code stays adapter free`() {
        featureLayerSources("domain", "application").forEach { source ->
            assertFalse(source.readText().contains(".adapter."), source.toString())
        }
    }

    @Test
    fun `ARCH-17 backend feature code never references bootstrap`() {
        featureDirectories().flatMap { kotlinSources(it.resolve("src/main/kotlin")) }.forEach { source ->
            assertFalse(source.readText().contains("br.com.saqz.bootstrap"), source.toString())
        }
    }

    @Test
    fun `ARCH-18 access and groups have isolated test and integration test suites`() {
        val accessBuild = backendRoot.resolve("features/access/build.gradle.kts").readText()
        val groupsBuild = backendRoot.resolve("features/groups/build.gradle.kts").readText()
        val bootstrapBuild = backendRoot.resolve("bootstrap/build.gradle.kts").readText()

        assertTrue(accessBuild.contains("sourceSets.create(\"integrationTest\")"))
        assertTrue(accessBuild.contains("val integrationTest by tasks.registering(Test::class)"))
        assertTrue(groupsBuild.contains("sourceSets.create(\"integrationTest\")"))
        assertTrue(groupsBuild.contains("val integrationTest by tasks.registering(Test::class)"))
        assertTrue(bootstrapBuild.contains("project(\":features:access\")"))
    }

    @Test
    fun `ARCH-19 groups depends only on shared kernel among backend projects`() {
        val groupsBuild = backendRoot.resolve("features/groups/build.gradle.kts").readText()

        assertTrue(groupsBuild.contains("project(\":shared-kernel\")"))
        assertFalse(groupsBuild.contains("project(\":features:"))
        assertFalse(groupsBuild.contains("project(\":bootstrap"))
    }

    @Test
    fun `ARCH-20 groups has the approved hexagonal source roots`() {
        val groupsRoot = backendRoot.resolve("features/groups/src/main/kotlin/br/com/saqz/groups")

        assertTrue(groupsRoot.exists())
        assertTrue(groupsRoot.resolve("domain").exists() || groupsRoot.resolve("application").exists() || groupsRoot.resolve("adapter").exists())
    }

    private fun featureDirectories(): List<Path> {
        val featuresRoot = backendRoot.resolve("features")
        return Files.list(featuresRoot).use { paths ->
            paths.filter(Path::isDirectory).sorted().toList()
        }
    }

    private fun featureLayerSources(vararg layers: String): List<Path> =
        featureDirectories().flatMap { feature ->
            layers.flatMap { layer ->
                kotlinSources(feature.resolve("src/main/kotlin/br/com/saqz/${feature.name}/$layer"))
            }
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
