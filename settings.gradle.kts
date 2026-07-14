pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "saqz"

includeBuild("build-logic")

include(":backend:shared-kernel")
include(":backend:features:identity")
include(":backend:bootstrap")
include(":backend:architecture-tests")
