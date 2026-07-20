package br.com.saqz.mobile.buildlogic

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

private const val COMPOSE_UI_TOOLING = "org.jetbrains.compose.ui:ui-tooling:1.11.1"

class KmpComposeLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        extensions.configure(KotlinMultiplatformExtension::class.java) {
            jvmToolchain(21)
            targets.withType(KotlinMultiplatformAndroidLibraryTarget::class.java).configureEach {
                androidResources.enable = true
            }
        }
        dependencies.add("androidRuntimeClasspath", COMPOSE_UI_TOOLING)
        Unit
    }
}

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        extensions.configure(KotlinAndroidProjectExtension::class.java) {
            jvmToolchain(21)
        }
    }
}
