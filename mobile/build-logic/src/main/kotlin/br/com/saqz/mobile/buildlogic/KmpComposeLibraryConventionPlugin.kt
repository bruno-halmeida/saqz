package br.com.saqz.mobile.buildlogic

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class KmpComposeLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(KmpLibraryConventionPlugin::class.java)

        extensions.configure(KotlinMultiplatformExtension::class.java) {
            targets.withType(KotlinMultiplatformAndroidLibraryTarget::class.java).configureEach {
                androidResources.enable = true
            }
        }
        // Só no runtime do Android: o @Preview do Studio precisa do inspector, o compile não.
        dependencies.add("androidRuntimeClasspath", libs.library("compose-ui-tooling"))
        Unit
    }
}
