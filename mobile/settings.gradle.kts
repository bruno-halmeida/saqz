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
    }
}

val settingsSource = file("settings.gradle.kts").readText()
require(!Regex("""includeBuild\(\s*[\"'][^\"']*\.\./""").containsMatchIn(settingsSource)) {
    "Mobile build must not include a sibling workspace or its build logic."
}

rootProject.name = "saqz-mobile"

includeBuild("build-logic")
include(":core:common")
include(":core:domain")
include(":core:design-system")
include(":core:network")
include(":features:access")
include(":features:access:domain")
include(":features:access:data")
include(":features:groups")
include(":features:groups:domain")
include(":features:groups:data")
include(":navigation")
include(":compose-app")
include(":android-app")
