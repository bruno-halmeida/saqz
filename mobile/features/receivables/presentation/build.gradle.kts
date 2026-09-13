plugins {
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.multiplatform)
    id("saqz.kmp-library")
    id("saqz.detekt")
}

kotlin {
    android {
        namespace = "br.com.saqz.features.receivables.presentation"
        withHostTest { }
    }
    sourceSets {
        commonMain.dependencies {
            implementation(project(":features:receivables:domain"))
            implementation(project(":core:domain"))
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
