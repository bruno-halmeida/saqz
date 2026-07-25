// Poucos arquivos por desenho: existe para manter Ktor fora de :features:access (Compose) e de
// :features:access:domain, conforme AD-030. Justificativa exigida pelo critério do VUL-39.
plugins {
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    id("saqz.kmp-library")
    id("saqz.detekt")
}

kotlin {
    android {
        namespace = "br.com.saqz.features.access.data"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":features:access:domain"))
            implementation(project(":core:domain"))
            implementation(project(":core:network"))
            implementation(libs.ktor.client.core)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
    }
}
