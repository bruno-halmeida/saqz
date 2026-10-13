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
        }
    }
}
