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
        namespace = "br.com.saqz.access.feature"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":features:access:domain"))
            implementation(project(":core:domain"))
            implementation(project(":core:common"))
            implementation(libs.bundles.compose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.lifecycle.viewmodel.savedstate)
            implementation(libs.lifecycle.runtime.compose)
            api(libs.navigation3.runtime)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.compose.ui.test)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}

compose.resources {
    // Público porque as fontes e drawables de marca herdados do design system
    // apagado (VUL-36) precisam ser lidos pelos testes instrumentados do android-app.
    publicResClass = true
    packageOfResClass = "br.com.saqz.access.resources"
    generateResClass = always
}
