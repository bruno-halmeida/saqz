package br.com.saqz.mobile.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class KmpComposeLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        extensions.configure(KotlinMultiplatformExtension::class.java) {
            jvmToolchain(21)
        }
    }
}
