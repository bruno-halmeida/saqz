plugins {
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.multiplatform)
    id("saqz.detekt")
}

kotlin {
    jvmToolchain(21)

    android {
        namespace = "br.com.saqz.features.groups.domain"
        compileSdk = libs.versions.compile.sdk.get().toInt()
        minSdk = libs.versions.min.sdk.get().toInt()
    }

    iosArm64()
    iosSimulatorArm64()
    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:domain"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
