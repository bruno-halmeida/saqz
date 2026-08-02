import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    id("saqz.kmp-compose-library")
    id("saqz.detekt")
}

kotlin {
    android {
        namespace = "br.com.saqz.composeapp"
    }

    // Os targets iOS vêm do saqz.kmp-library; aqui só o framework que eles publicam.
    targets.withType<KotlinNativeTarget>().configureEach {
        binaries.framework {
            baseName = "SaqzMobile"
            isStatic = true
            export(project(":core:design-system"))
            export(project(":features:access"))
            export(project(":features:access:domain"))
            export(project(":features:groups"))
            export(project(":features:groups:domain"))
            export(project(":features:profile:domain"))
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core:design-system"))
            api(project(":features:access"))
            api(project(":features:access:domain"))
            api(project(":features:groups"))
            api(project(":features:groups:domain"))
            api(project(":features:profile:domain"))
            api(project(":features:subscriptions:domain"))
            implementation(project(":features:groups:presentation"))
            implementation(project(":features:access:data"))
            implementation(project(":features:subscriptions:data"))
            implementation(project(":features:subscriptions:presentation"))
            implementation(project(":core:common"))
            implementation(project(":core:domain"))
            implementation(project(":core:network"))
            implementation(libs.bundles.compose)
            implementation(libs.compose.ui.backhandler)
            implementation(libs.navigation3.runtime)
            implementation(libs.navigation3.ui)
            implementation(libs.lifecycle.viewmodel.compose)
            // `LifecycleResumeEffect`: a faixa de e-mail (VUL-91) recarrega o usuário na
            // volta do plano de fundo.
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.okio)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.compose.ui.test)
            implementation(libs.koin.test)
        }
    }
}

compose.resources {
    // Público pelo mesmo motivo do `:features:access`: o print da faixa (VUL-91) roda no
    // `:android-app`, que é onde o Roborazzi vive, e lê as strings daqui em vez de
    // repetir o texto no teste.
    publicResClass = true
    packageOfResClass = "br.com.saqz.composeapp.resources"
    generateResClass = always
}
