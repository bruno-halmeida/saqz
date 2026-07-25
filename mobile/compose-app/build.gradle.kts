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
            export(project(":features:access"))
            export(project(":features:access:domain"))
            export(project(":features:groups:domain"))
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":features:access"))
            api(project(":features:access:domain"))
            api(project(":features:groups:domain"))
            implementation(project(":features:access:data"))
            implementation(project(":core:common"))
            implementation(project(":core:domain"))
            implementation(project(":core:network"))
            implementation(libs.bundles.compose)
            implementation(libs.compose.ui.backhandler)
            implementation(libs.navigation3.runtime)
            implementation(libs.navigation3.ui)
            implementation(libs.lifecycle.viewmodel.compose)
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
    packageOfResClass = "br.com.saqz.composeapp.resources"
    generateResClass = always
}
