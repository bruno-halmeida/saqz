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
        compileSdk = libs.versions.compile.sdk.get().toInt()
        minSdk = libs.versions.min.sdk.get().toInt()
    }

    iosArm64()
    iosSimulatorArm64()
    applyDefaultHierarchyTemplate()

    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework {
            baseName = "SaqzMobile"
            isStatic = true
            export(project(":features:access"))
            export(project(":features:groups"))
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":features:access"))
            api(project(":features:groups"))
            implementation(project(":features:access:data"))
            implementation(project(":core:common"))
            implementation(project(":core:domain"))
            implementation(project(":core:design-system"))
            implementation(project(":core:network"))
            implementation(libs.coil.compose.core)
            implementation("org.jetbrains.compose.foundation:foundation:1.11.1")
            implementation("org.jetbrains.compose.material:material:1.11.1")
            implementation("org.jetbrains.compose.runtime:runtime:1.11.1")
            implementation("org.jetbrains.compose.ui:ui:1.11.1")
            implementation("org.jetbrains.compose.ui:ui-backhandler:1.11.1")
            implementation("org.jetbrains.compose.ui:ui-tooling-preview:1.11.1")
            implementation("org.jetbrains.compose.components:components-resources:1.11.1")
            implementation(libs.navigation.compose)
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
            implementation(libs.kotlinx.coroutines.test)
            implementation("org.jetbrains.compose.ui:ui-test:1.11.1")
            implementation(libs.koin.test)
        }
    }
}

compose.resources {
    packageOfResClass = "br.com.saqz.composeapp.resources"
    generateResClass = always
}
