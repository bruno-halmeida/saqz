plugins {
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
    id("saqz.kmp-compose-library")
    id("saqz.detekt")
}

kotlin {
    android {
        namespace = "br.com.saqz.groups.presentation"
        withHostTest {
            isIncludeAndroidResources = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":features:groups:domain"))
            api(project(":core:design-system"))
            implementation(project(":features:groups"))
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
        getByName("androidHostTest").dependencies {
            implementation(libs.junit)
            implementation(libs.robolectric)
            implementation(libs.roborazzi)
            implementation(libs.roborazzi.compose)
            implementation(libs.compose.ui.test.junit4)
            implementation(libs.androidx.compose.ui.test.manifest)
        }
    }
}

compose.resources {
    packageOfResClass = "br.com.saqz.groups.resources"
    generateResClass = always
}
