plugins {
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.multiplatform)
    id("saqz.kmp-library")
    id("saqz.detekt")
}

kotlin {
    android {
        namespace = "br.com.saqz.profile.presentation"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":features:profile:domain"))
            implementation(project(":features:profile"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
