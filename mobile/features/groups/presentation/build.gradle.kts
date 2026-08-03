plugins {
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
    id("saqz.kmp-compose-library")
    id("saqz.detekt")
}

kotlin {
    android {
        namespace = "br.com.saqz.groups.presentation"
        withHostTest {
            isIncludeAndroidResources = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":features:groups:domain"))
            implementation(project(":core:domain"))
            api(project(":core:design-system"))
            implementation(project(":features:groups"))
            implementation(project(":core:common"))
            implementation(libs.bundles.compose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlin.qrcode)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.lifecycle.viewmodel.savedstate)
            implementation(libs.koin.compose.viewmodel)
            api(libs.navigation3.runtime)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.compose.ui.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.kotlinx.serialization.json)
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

// VUL-100: `withHostTest { }` existe neste módulo por um motivo só — a suíte de captura
// Roborazzi do VUL-66. Mas a hierarquia padrão do KMP faz `androidHostTest` depender de
// `commonTest`, então o opt-in arrastava os `commonTest` para uma execução JVM que eles não
// pedem: lá todo `runComposeUiTest` estoura `NullPointerException` em `Build.FINGERPRINT`,
// porque exige o runner do Robolectric. Declarar esse runner para o `commonTest` é impossível
// — é `@RunWith` de JUnit4, anotação JVM-only, e `commonTest` também compila para iOS.
// O gate de `commonTest` é `iosSimulatorArm64Test`, onde as mesmas suítes passam.
// Logo, a compilação de host test carrega só a suíte de captura. `isIncludeAndroidResources`
// continua ligado: é ele que faz o Roborazzi rodar.
// O `afterEvaluate` é necessário: fora dele o KGP volta a somar os dirs de `commonTest` depois
// deste bloco e o `setSource` não surte efeito.
afterEvaluate {
    tasks.named("compileAndroidHostTest", org.jetbrains.kotlin.gradle.tasks.KotlinCompileTool::class) {
        setSource(kotlin.sourceSets.getByName("androidHostTest").kotlin)
    }
}

compose.resources {
    packageOfResClass = "br.com.saqz.groups.resources"
    generateResClass = always
}
