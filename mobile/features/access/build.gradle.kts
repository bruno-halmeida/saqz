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
            api(project(":core:design-system"))
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

    // O `ui-contract.json` é a cópia versionada dos números do export e mora no
    // `commonTest` do design system — recurso de teste, que não é publicado para quem
    // depende do módulo. O `AccessMetricsTest` precisa dele para amarrar os literais do
    // fluxo 1 à chave `fluxo1`, então o source set de teste desta feature ganha a pasta
    // de lá como diretório extra: é o mesmo arquivo em disco, lido pelos dois testes.
    // Cópia geraria duas verdades, e mover o contrato para `commonMain` embarcaria um
    // artefato de desenvolvimento no app.
    customDirectory(
        sourceSetName = "commonTest",
        directoryProvider = provider {
            rootProject.layout.projectDirectory.dir("core/design-system/src/commonTest/composeResources")
        },
    )
}
