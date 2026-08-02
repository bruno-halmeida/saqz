plugins {
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.multiplatform)
    id("saqz.kmp-library")
    id("saqz.detekt")
}

kotlin {
    android {
        namespace = "br.com.saqz.profile.feature"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":features:profile:domain"))
            implementation(project(":core:domain"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

tasks.register("test") {
    group = "verification"
    description = "Runs all profile feature tests."
    dependsOn(
        ":features:profile:iosSimulatorArm64Test",
        ":features:profile:domain:iosSimulatorArm64Test",
        ":features:profile:data:iosSimulatorArm64Test",
        ":features:profile:presentation:iosSimulatorArm64Test",
    )
}
