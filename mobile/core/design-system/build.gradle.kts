plugins {
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.multiplatform)
    id("saqz.kmp-compose-library")
    id("saqz.detekt")
}

kotlin {
    android {
        namespace = "br.com.saqz.designsystem"
        compileSdk = libs.versions.compile.sdk.get().toInt()
        minSdk = libs.versions.min.sdk.get().toInt()
    }

    iosArm64()
    iosSimulatorArm64()
    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            // api: a superfície pública do design system é feita de tipos Compose
            // (Modifier, Color, TextStyle), então quem consome precisa vê-los.
            api("org.jetbrains.compose.foundation:foundation:1.11.1")
            api("org.jetbrains.compose.material:material:1.11.1")
            api("org.jetbrains.compose.runtime:runtime:1.11.1")
            api("org.jetbrains.compose.ui:ui:1.11.1")
            implementation("org.jetbrains.compose.ui:ui-tooling-preview:1.11.1")
            // implementation, não api: o back do sheet é assunto interno do componente,
            // ninguém precisa do BackHandler para consumir o design system.
            implementation(libs.compose.ui.backhandler)
            implementation("org.jetbrains.compose.components:components-resources:1.11.1")
            // implementation, não api: a Lucide entra só como fonte dos glifos.
            // O que sai daqui é `ImageVector`, e quem consome não importa `Lucide`.
            implementation(libs.icons.lucide)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.compose.ui:ui-test:1.11.1")
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}

compose.resources {
    // Público porque as fontes Inter são lidas por testes instrumentados do android-app.
    publicResClass = true
    packageOfResClass = "br.com.saqz.designsystem.resources"
    generateResClass = always
}
