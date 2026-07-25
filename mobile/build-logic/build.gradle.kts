plugins {
    `kotlin-dsl`
}

group = "br.com.saqz.mobile.buildlogic"

dependencies {
    implementation(libs.android.gradle.plugin)
    implementation(libs.compose.gradle.plugin)
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.detekt.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("kmpLibrary") {
            id = "saqz.kmp-library"
            implementationClass = "br.com.saqz.mobile.buildlogic.KmpLibraryConventionPlugin"
        }
        register("kmpComposeLibrary") {
            id = "saqz.kmp-compose-library"
            implementationClass = "br.com.saqz.mobile.buildlogic.KmpComposeLibraryConventionPlugin"
        }
        register("androidApplication") {
            id = "saqz.android-application"
            implementationClass = "br.com.saqz.mobile.buildlogic.AndroidApplicationConventionPlugin"
        }
        register("detekt") {
            id = "saqz.detekt"
            implementationClass = "br.com.saqz.mobile.buildlogic.DetektConventionPlugin"
        }
    }
}
