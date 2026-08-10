import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

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
        namespace = "br.com.saqz.composeapp"
    }

    // Os targets iOS vêm do saqz.kmp-library; aqui só o framework que eles publicam.
    targets.withType<KotlinNativeTarget>().configureEach {
        binaries.framework {
            baseName = "SaqzMobile"
            isStatic = true
            export(project(":core:design-system"))
            export(project(":features:access"))
            export(project(":features:access:domain"))
            export(project(":features:groups"))
            export(project(":features:groups:domain"))
            export(project(":features:profile:domain"))
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core:design-system"))
            api(project(":features:access"))
            api(project(":features:access:domain"))
            api(project(":features:groups"))
            api(project(":features:groups:domain"))
            api(project(":features:profile:domain"))
            api(project(":features:subscriptions:domain"))
            implementation(project(":features:groups:presentation"))
            implementation(project(":features:groups:data"))
            implementation(project(":features:profile:data"))
            implementation(project(":features:profile:presentation"))
            implementation(project(":features:access:data"))
            implementation(project(":features:subscriptions:data"))
            implementation(project(":features:subscriptions:presentation"))
            implementation(project(":core:common"))
            implementation(project(":core:domain"))
            implementation(project(":core:network"))
            implementation(libs.coil.core)
            implementation(libs.bundles.compose)
            implementation(libs.compose.ui.backhandler)
            implementation(libs.navigation3.runtime)
            implementation(libs.navigation3.ui)
            implementation(libs.lifecycle.viewmodel.compose)
            // VUL-204: o `ViewModelStoreNavEntryDecorator` — sem ele o
            // `LocalViewModelStoreOwner` de dentro de um `NavEntry` é a Activity, e toda
            // ViewModel de `koinViewModel()` vira singleton de processo.
            implementation(libs.lifecycle.viewmodel.navigation3)
            // `LifecycleResumeEffect`: a faixa de e-mail (VUL-91) recarrega o usuário na
            // volta do plano de fundo.
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.okio)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.compose.ui.test)
            implementation(libs.koin.test)
        }
    }
}

compose.resources {
    // Público pelo mesmo motivo do `:features:access`: o print da faixa (VUL-91) roda no
    // `:android-app`, que é onde o Roborazzi vive, e lê as strings daqui em vez de
    // repetir o texto no teste.
    publicResClass = true
    packageOfResClass = "br.com.saqz.composeapp.resources"
    generateResClass = always
}

// --- iOS API config ------------------------------------------------------------
// O iOS precisa das mesmas URLs que o Android lê de `gradle.properties`. O Xcode não
// enxerga o Gradle, então escrevemos um plist num caminho estável e uma shell phase do
// `project.pbxproj` (logo após "Copy Firebase Plist") sobrescreve `SaqzAPIBaseURL` no
// Info.plist built a partir dele. Assim `gradle.properties` é a fonte única para as duas
// plataformas — mudar `saqz.api.devBaseUrl` rebroadcasta no iOS sem tocar no pbxproj.

val devApiBaseUrl = providers.gradleProperty("saqz.api.devBaseUrl").orNull?.trim().orEmpty()
    .ifEmpty { "http://127.0.0.1:8080" }
val prodApiBaseUrl = providers.gradleProperty("saqz.api.prodBaseUrl").orNull?.trim().orEmpty()
    .ifEmpty { "https://api.saqz.app" }

val xcodeConfigDir = layout.buildDirectory.dir("xcode-frameworks")
val apiConfigPlist = xcodeConfigDir.map { it.file("saqz-api-config.plist") }

val generateIosApiConfig by tasks.registering {
    description = "Writes the iOS API config plist consumed by the Xcode build phase."
    group = "build"
    outputs.file(apiConfigPlist)
    val devUrl = devApiBaseUrl
    val prodUrl = prodApiBaseUrl
    val output = apiConfigPlist
    doLast {
        val file = output.get().asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0">
            <dict>
                <key>dev</key>
                <string>$devUrl</string>
                <key>prod</key>
                <string>$prodUrl</string>
            </dict>
            </plist>
            """.trimIndent() + "\n",
        )
    }
}

// A shell phase "Build SaqzMobile" dispara `embedAndSignAppleFrameworkForXcode`; anexar a
// geração do plist nela garante que o arquivo exista antes da phase que o lê.
tasks.matching { it.name == "embedAndSignAppleFrameworkForXcode" }.configureEach {
    dependsOn(generateIosApiConfig)
}
