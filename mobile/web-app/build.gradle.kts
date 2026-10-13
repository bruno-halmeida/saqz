import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

// Hospedeiro web do Saqz (Kotlin/Wasm): o mesmo `:compose-app` dos apps Android e iOS, com as
// portas nativas implementadas sobre as APIs do navegador (src/wasmJsMain/kotlin/.../webapp).
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    id("saqz.detekt")
}

// O alvo nodejs() aplica o plugin do Node por projeto, com o repositório Ivy padrão; como o da raiz,
// ele precisa usar o que o settings.gradle.kts já declara (FAIL_ON_PROJECT_REPOS).
plugins.withType<org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsPlugin> {
    the<org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsEnvSpec>().downloadBaseUrl.set(null as String?)
}

// O executável de teste leva o app inteiro e importa o skiko.mjs (Compose), que tenta carregar o skiko.wasm e
// aborta no Node. O hook (src/wasmJsTest/node) troca só esse módulo por um vazio; as portas testadas não desenham.
tasks.withType<org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest>().configureEach {
    environment("NODE_OPTIONS", "--import=${layout.projectDirectory.file("src/wasmJsTest/node/stub-skiko.mjs").asFile.toURI()}")
}

kotlin {
    wasmJs {
        outputModuleName.set("saqz-web")
        browser {
            commonWebpackConfig {
                outputFileName = "saqz-web.js"
                // Dev: a API de staging fica atrás do mesmo origin (sem CORS), como em produção,
                // onde o nginx de app.saqz.app faz o proxy de /api para o backend.
                devServer = (devServer ?: KotlinWebpackConfig.DevServer()).copy(
                    port = 8787,
                    proxy = mutableListOf(
                        KotlinWebpackConfig.DevServer.Proxy(
                            context = mutableListOf("/api", "/public"),
                            target = "https://saqz-api.brunoalmeida.dev",
                            changeOrigin = true,
                        ),
                    ),
                )
            }
        }
        // Os testes das portas (parser de links, rascunhos) rodam no Node, sem navegador: ./gradlew :web-app:wasmJsNodeTest.
        nodejs()
        binaries.executable()
    }

    sourceSets {
        wasmJsMain.dependencies {
            implementation(project(":compose-app"))
            implementation(project(":core:domain"))
            implementation(libs.bundles.compose)
            implementation(libs.kotlinx.browser)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.koin.core)
            implementation(libs.coil.core)
            implementation(libs.navigationevent.compose)
        }
        wasmJsTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
