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
include(":compose-app")
