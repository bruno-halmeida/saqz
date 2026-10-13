pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Ferramentas do alvo web (:web-app): o Kotlin baixa Node.js, Yarn e Binaryen como
        // dependências Ivy. Com FAIL_ON_PROJECT_REPOS elas precisam ser declaradas aqui, e o
        // build.gradle.kts da raiz desliga os repositórios que o plugin adicionaria por conta.
        exclusiveContent {
            forRepository {
                ivy("https://nodejs.org/dist") {
                    name = "Node.js"
                    patternLayout { artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]") }
                    metadataSources { artifact() }
                }
            }
            filter { includeModule("org.nodejs", "node") }
        }
        exclusiveContent {
            forRepository {
                ivy("https://github.com/yarnpkg/yarn/releases/download") {
                    name = "Yarn"
                    patternLayout { artifact("v[revision]/[artifact](-v[revision]).[ext]") }
                    metadataSources { artifact() }
                }
            }
            filter { includeModule("com.yarnpkg", "yarn") }
        }
        exclusiveContent {
            forRepository {
                ivy("https://github.com/WebAssembly/binaryen/releases/download") {
                    name = "Binaryen"
                    patternLayout { artifact("version_[revision]/[module]-version_[revision]-[classifier].[ext]") }
                    metadataSources { artifact() }
                }
            }
            filter { includeModule("com.github.webassembly", "binaryen") }
        }
    }
}

val settingsSource = file("settings.gradle.kts").readText()
require(!Regex("""includeBuild\(\s*[\"'][^\"']*\.\./""").containsMatchIn(settingsSource)) {
    "Mobile build must not include a sibling workspace or its build logic."
}

rootProject.name = "saqz-mobile"

includeBuild("build-logic")
include(":core:common")
include(":core:design-system")
include(":core:domain")
include(":core:network")
include(":features:access")
include(":features:access:domain")
include(":features:access:data")
include(":features:groups")
include(":features:groups:domain")
include(":features:groups:data")
include(":features:groups:presentation")
include(":features:profile")
include(":features:profile:domain")
include(":features:profile:data")
include(":features:profile:presentation")
include(":features:subscriptions:domain")
include(":features:subscriptions:data")
include(":features:subscriptions:presentation")
include(":compose-app")
include(":android-app")
include(":web-app")
include(":features:receivables:domain")
include(":features:receivables:data")
include(":features:receivables:presentation")
