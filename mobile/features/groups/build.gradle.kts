plugins {
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    id("saqz.kmp-compose-library")
}

kotlin {
    android {
        namespace = "br.com.saqz.groups.feature"
        compileSdk = libs.versions.compile.sdk.get().toInt()
        minSdk = libs.versions.min.sdk.get().toInt()
    }

    iosArm64()
    iosSimulatorArm64()
    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:common"))
            implementation(project(":core:design-system"))
            implementation(project(":core:network"))
            implementation(libs.ktor.client.core)
            implementation("org.jetbrains.compose.foundation:foundation:1.11.1")
            implementation("org.jetbrains.compose.material:material:1.11.1")
            implementation("org.jetbrains.compose.runtime:runtime:1.11.1")
            implementation("org.jetbrains.compose.ui:ui:1.11.1")
            implementation("org.jetbrains.compose.ui:ui-tooling-preview:1.11.1")
            implementation("org.jetbrains.compose.components:components-resources:1.11.1")
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
            implementation("org.jetbrains.compose.ui:ui-test:1.11.1")
        }
    }
}

compose.resources {
    packageOfResClass = "br.com.saqz.groups.resources"
    generateResClass = always
}
