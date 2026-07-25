plugins {
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.multiplatform)
    id("saqz.kmp-library")
    id("saqz.detekt")
}

kotlin {
    android {
        namespace = "br.com.saqz.features.groups.domain"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:domain"))
            // Herdado do antigo módulo raiz :features:groups (VUL-39): GroupSetupContracts/Ports
            // detectam o fuso do sistema.
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
