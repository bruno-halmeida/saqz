package br.com.saqz.mobile.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

private const val COPY_COMPOSE_ANDROID_ASSETS = "copyAndroidMainComposeResourcesToAndroidAssets"
private const val COMPOSE_ANDROID_ASSETS_DIR = "composeAndroidAssets"
private const val COMPOSE_UI_TOOLING = "org.jetbrains.compose.ui:ui-tooling:1.11.1"

// Compose library modules whose resources must reach the app APK. Kept here (not
// per-app) so a module gaining composeResources needs no android-app edit.
private val COMPOSE_RESOURCE_MODULES = listOf(":core:design-system", ":features:access", ":features:groups", ":compose-app")

class KmpComposeLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        extensions.configure(KotlinMultiplatformExtension::class.java) {
            jvmToolchain(21)
        }
        dependencies.add("androidRuntimeClasspath", COMPOSE_UI_TOOLING)
        // AGP's KMP-library plugin leaves the Compose "copy resources to Android
        // assets" task without an outputDirectory, so compose resources never
        // reach the APK. Point it at a build dir; the application module adds
        // that dir to its assets. Task type is internal -> reflective setter.
        tasks.matching { it.name.contains("ComposeResourcesToAndroidAssets") }.configureEach {
            val outputDirectory = javaClass.getMethod("getOutputDirectory").invoke(this)
                as DirectoryProperty
            outputDirectory.set(layout.buildDirectory.dir(COMPOSE_ANDROID_ASSETS_DIR))
        }
    }
}

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        extensions.configure(KotlinAndroidProjectExtension::class.java) {
            jvmToolchain(21)
        }
        // Pull each compose-library module's generated Android assets (see
        // KmpComposeLibraryConventionPlugin) into this application so they reach
        // the APK — AGP's KMP-library plugin doesn't package them itself.
        val android = extensions.getByName("android") as ApplicationExtension
        COMPOSE_RESOURCE_MODULES.forEach { path ->
            android.sourceSets.getByName("main").assets.srcDir(
                project(path).layout.buildDirectory.dir(COMPOSE_ANDROID_ASSETS_DIR).get().asFile,
            )
            tasks.matching { it.name.startsWith("merge") && it.name.contains("Assets") }
                .configureEach { dependsOn("$path:$COPY_COMPOSE_ANDROID_ASSETS") }
        }
    }
}
