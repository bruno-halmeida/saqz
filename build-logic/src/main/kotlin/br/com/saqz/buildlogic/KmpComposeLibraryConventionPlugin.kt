package br.com.saqz.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class KmpComposeLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        pluginManager.apply("org.jetbrains.compose")
        pluginManager.apply("com.android.kotlin.multiplatform.library")
        extensions.configure(KotlinMultiplatformExtension::class.java) {
            jvmToolchain(21)
        }
    }
}
