// Poucos arquivos por desenho: vocabulário compartilhado (SaqzResult, DataError, GroupId) que
// :features:*:domain podem ver sem enxergar nada de rede/UI. Fundi-lo em qualquer feature
// criaria dependência entre features. Justificativa exigida pelo critério do VUL-39.
plugins {
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.multiplatform)
    id("saqz.kmp-library")
    id("saqz.detekt")
}

kotlin {
    android {
        namespace = "br.com.saqz.core.domain"
    }

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
