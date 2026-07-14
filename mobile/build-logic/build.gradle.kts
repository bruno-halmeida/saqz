plugins {
    `kotlin-dsl`
}

group = "br.com.saqz.mobile.buildlogic"

dependencies {
    implementation("com.android.tools.build:gradle:9.1.0")
    implementation("org.jetbrains.compose:compose-gradle-plugin:1.11.1")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
}

gradlePlugin {
    plugins {
        register("kmpComposeLibrary") {
            id = "saqz.kmp-compose-library"
            implementationClass = "br.com.saqz.mobile.buildlogic.KmpComposeLibraryConventionPlugin"
        }
    }
}
