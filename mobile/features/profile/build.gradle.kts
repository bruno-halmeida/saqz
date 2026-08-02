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
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
