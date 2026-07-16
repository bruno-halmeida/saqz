pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "saqz-backend"

includeBuild("build-logic")

include(":shared-kernel")
include(":features:access")
include(":features:identity")
include(":bootstrap")
include(":architecture-tests")
