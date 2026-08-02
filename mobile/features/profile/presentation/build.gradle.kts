plugins {
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.roborazzi)
    id("saqz.kmp-compose-library")
    alias(libs.plugins.kotlin.serialization)
    id("saqz.kmp-library")
    id("saqz.detekt")
}

kotlin {
    android {
        namespace = "br.com.saqz.profile.presentation"
        withHostTest {
            isIncludeAndroidResources = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":features:profile:domain"))
            api(project(":core:design-system"))
            implementation(project(":core:domain"))
            implementation(project(":features:profile"))
            implementation(project(":core:common"))
            implementation(libs.bundles.compose)
            implementation(libs.coil.compose.core)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.lifecycle.viewmodel.savedstate)
            implementation(libs.koin.compose.viewmodel)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.compose.ui.test)
            implementation(libs.kotlinx.coroutines.test)
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

// A captura é host-only: as suítes de commonTest continuam rodando no simulador iOS.
// Sem este recorte, o host test herda commonTest e tenta executar testes Compose sem o
// runner do Robolectric.
afterEvaluate {
    tasks.named("compileAndroidHostTest", org.jetbrains.kotlin.gradle.tasks.KotlinCompileTool::class) {
        setSource(kotlin.sourceSets.getByName("androidHostTest").kotlin)
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "br.com.saqz.profile.resources"
    generateResClass = always
}
