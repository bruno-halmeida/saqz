plugins {
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    id("saqz.kmp-library")
    id("saqz.detekt")
}

kotlin {
    android {
        namespace = "br.com.saqz.features.groups.data"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":features:groups:domain"))
            implementation(project(":core:domain"))
            implementation(project(":core:network"))
            implementation(libs.ktor.client.core)
            // DefaultGroupSystemTimeZonePort + validação IANA (VUL-39): capacidade de plataforma
            // mora aqui, nunca no domínio.
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
    }
}
