import groovy.json.JsonSlurper

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("saqz.android-application")
}

data class FirebaseAndroidConfig(
    val projectId: String,
    val apiKey: String,
    val messagingSenderId: String,
    val applicationId: String,
)

val localFirebaseAndroidConfig = FirebaseAndroidConfig(
    projectId = "saqz-local",
    apiKey = "fake-saqz-local-api-key",
    messagingSenderId = "123456789000",
    applicationId = "1:123456789000:android:saqzlocal",
)

val missingReleaseFirebaseAndroidConfig = FirebaseAndroidConfig(
    projectId = "missing-release-firebase-config",
    apiKey = "missing-release-firebase-config",
    messagingSenderId = "0",
    applicationId = "missing-release-firebase-config",
)

android {
    namespace = "br.com.saqz.androidapp"
    compileSdk = libs.versions.compile.sdk.get().toInt()

    defaultConfig {
        applicationId = "app.saqz"
        minSdk = libs.versions.min.sdk.get().toInt()
        targetSdk = libs.versions.compile.sdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
    }

    sourceSets {
        // Package the pinned Inter OFL license into the test APK so the
        // instrumented checksum test verifies the same file kept for attribution.
        getByName("androidTest").assets.srcDir(
            rootProject.file("core/design-system/THIRD_PARTY_LICENSES"),
        )
    }

    flavorDimensions += "environment"
    productFlavors {
        create("dev") {
            dimension = "environment"
            val firebaseConfigFile = layout.projectDirectory.file("src/dev/google-services.json").asFile
            val firebaseConfig = firebaseAndroidConfig(
                file = firebaseConfigFile,
                required = false,
                fallback = localFirebaseAndroidConfig,
            )
            buildConfigField("String", "FIREBASE_PROJECT_ID", firebaseConfig.projectId.toBuildConfigString())
            buildConfigField("String", "FIREBASE_API_KEY", firebaseConfig.apiKey.toBuildConfigString())
            buildConfigField("String", "FIREBASE_MESSAGING_SENDER_ID", firebaseConfig.messagingSenderId.toBuildConfigString())
            buildConfigField("String", "FIREBASE_APPLICATION_ID", firebaseConfig.applicationId.toBuildConfigString())
            buildConfigField("boolean", "FIREBASE_USE_EMULATOR", (!firebaseConfigFile.isFile).toString())
        }
        create("prod") {
            dimension = "environment"
            val firebaseConfig = firebaseAndroidConfig(
                file = layout.projectDirectory.file("src/prod/google-services.json").asFile,
                required = gradle.startParameter.taskNames.any {
                    it.contains("Prod", ignoreCase = true) || it.contains("Release", ignoreCase = true)
                },
                fallback = missingReleaseFirebaseAndroidConfig,
            )
            buildConfigField("String", "FIREBASE_PROJECT_ID", firebaseConfig.projectId.toBuildConfigString())
            buildConfigField("String", "FIREBASE_API_KEY", firebaseConfig.apiKey.toBuildConfigString())
            buildConfigField("String", "FIREBASE_MESSAGING_SENDER_ID", firebaseConfig.messagingSenderId.toBuildConfigString())
            buildConfigField("String", "FIREBASE_APPLICATION_ID", firebaseConfig.applicationId.toBuildConfigString())
            buildConfigField("boolean", "FIREBASE_USE_EMULATOR", "false")
        }
    }
}

dependencies {
    implementation(project(":compose-app"))
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(project(":core:design-system"))
    androidTestImplementation("org.jetbrains.compose.components:components-resources:1.11.1")
    androidTestImplementation("org.jetbrains.compose.material:material:1.11.1")
    androidTestImplementation("org.jetbrains.compose.foundation:foundation:1.11.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.11.2")
}

fun firebaseAndroidConfig(
    file: File,
    required: Boolean,
    fallback: FirebaseAndroidConfig,
): FirebaseAndroidConfig {
    if (!file.isFile) {
        require(!required) { "Missing Android Firebase config: ${file.path}" }
        return fallback
    }

    val root = JsonSlurper().parse(file) as Map<*, *>
    val projectInfo = root["project_info"] as Map<*, *>
    val client = (root["client"] as List<*>).first() as Map<*, *>
    val clientInfo = client["client_info"] as Map<*, *>
    val apiKey = (client["api_key"] as List<*>).first() as Map<*, *>

    return FirebaseAndroidConfig(
        projectId = projectInfo["project_id"] as String,
        apiKey = apiKey["current_key"] as String,
        messagingSenderId = projectInfo["project_number"] as String,
        applicationId = clientInfo["mobilesdk_app_id"] as String,
    )
}

fun String.toBuildConfigString() = "\"$this\""
