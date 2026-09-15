pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // SDK do WhatsApp publicado pelo JitPack a partir do release da tag no GitHub.
        exclusiveContent {
            forRepository { maven("https://jitpack.io") }
            filter { includeModule("com.github.thoughtbruno", "uazapi-connector-sdk") }
        }
        mavenCentral()
    }
}

rootProject.name = "saqz-backend"

includeBuild("build-logic")

include(":shared-kernel")
include(":postgres-testing")
include(":features:access")
include(":features:groups")
include(":features:identity")
include(":features:subscriptions")
include(":features:receivables")
include(":bootstrap")
include(":architecture-tests")
