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
        namespace = "br.com.saqz.subscriptions.presentation"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":features:subscriptions:domain"))
            api(project(":core:design-system"))
            implementation(project(":core:domain"))
            implementation(project(":core:common"))
            implementation(libs.bundles.compose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.lifecycle.viewmodel.savedstate)
            implementation(libs.koin.compose.viewmodel)
            api(libs.navigation3.runtime)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.compose.ui.test)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}

compose.resources {
    packageOfResClass = "br.com.saqz.subscriptions.resources"
    generateResClass = always
}
