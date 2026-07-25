// Poucos arquivos por desenho: a fronteira presentation → domain ← data do AD-030 vale mesmo
// com o access reduzido ao login. É também a superfície pura exportada ao framework iOS.
// Justificativa exigida pelo critério do VUL-39.
plugins {
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.multiplatform)
    id("saqz.kmp-library")
    id("saqz.detekt")
}

kotlin {
    android {
        namespace = "br.com.saqz.features.access.domain"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:domain"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
