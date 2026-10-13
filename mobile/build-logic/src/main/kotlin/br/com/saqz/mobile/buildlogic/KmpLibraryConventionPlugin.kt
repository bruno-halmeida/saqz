package br.com.saqz.mobile.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

internal const val JVM_TOOLCHAIN = 21

internal val Project.libs: VersionCatalog
    get() = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")

internal fun VersionCatalog.intVersion(alias: String): Int =
    findVersion(alias).orElseThrow().requiredVersion.toInt()

internal fun VersionCatalog.library(alias: String): Provider<MinimalExternalModuleDependency> =
    findLibrary(alias).orElseThrow()

/**
 * Base de todo módulo KMP: targets, hierarquia de source sets, toolchain e SDKs Android.
 * O `namespace` fica no módulo — é a única parte legitimamente diferente entre eles.
 */
class KmpLibraryConventionPlugin : Plugin<Project> {
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    override fun apply(target: Project) = with(target) {
        val compileSdkVersion = libs.intVersion("compile-sdk")
        val minSdkVersion = libs.intVersion("min-sdk")

        extensions.configure(KotlinMultiplatformExtension::class.java) {
            jvmToolchain(JVM_TOOLCHAIN)
            iosArm64()
            iosSimulatorArm64()
            // Web app (Kotlin/Wasm): mesmo commonMain dos apps Android e iOS; o hospedeiro
            // web implementa as mesmas portas nativas em `:web-app`.
            wasmJs {
                browser {
                    // Os testes comuns já rodam no simulador iOS e na JVM; no wasm eles pediriam um Chrome
                    // headless em cada módulo. Os testes do web app (portas do navegador) ficam no :web-app.
                    testTask { enabled = false }
                }
            }
            applyDefaultHierarchyTemplate()
            targets.withType(KotlinMultiplatformAndroidLibraryTarget::class.java).configureEach {
                compileSdk = compileSdkVersion
                minSdk = minSdkVersion
            }
        }
    }
}

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        val compileSdkVersion = libs.intVersion("compile-sdk")
        val minSdkVersion = libs.intVersion("min-sdk")

        extensions.configure(KotlinAndroidProjectExtension::class.java) {
            jvmToolchain(JVM_TOOLCHAIN)
        }
        extensions.configure(ApplicationExtension::class.java) {
            compileSdk = compileSdkVersion
            defaultConfig {
                minSdk = minSdkVersion
                targetSdk = compileSdkVersion
            }
        }
    }
}
