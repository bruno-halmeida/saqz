import groovy.json.JsonSlurper

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("saqz.android-application")
    id("saqz.detekt")
}

data class FirebaseAndroidConfig(
    val projectId: String,
    val apiKey: String,
    val messagingSenderId: String,
    val applicationId: String,
    val googleServerClientId: String,
)

val localFirebaseAndroidConfig = FirebaseAndroidConfig(
    projectId = "saqz-local",
    apiKey = "fake-saqz-local-api-key",
    messagingSenderId = "123456789000",
    applicationId = "1:123456789000:android:saqzlocal",
    googleServerClientId = "fake-saqz-local-web-client-id.apps.googleusercontent.com",
)

val missingReleaseFirebaseAndroidConfig = FirebaseAndroidConfig(
    projectId = "missing-release-firebase-config",
    apiKey = "missing-release-firebase-config",
    messagingSenderId = "0",
    applicationId = "missing-release-firebase-config",
    googleServerClientId = "missing-release-google-server-client-id",
)

val requiresProdBranchConfig = gradle.startParameter.taskNames.any {
    it.contains("Prod", ignoreCase = true) || it.contains("Release", ignoreCase = true)
}

val branchTestKey = branchProperty(
    name = "saqz.branch.testKey",
    required = requiresProdBranchConfig,
    fallback = "key_test_saqz_local_fixture",
)
val branchLiveKey = branchProperty(
    name = "saqz.branch.liveKey",
    required = requiresProdBranchConfig,
    fallback = "missing-branch-live-key",
)
val branchTestDomain = branchProperty(
    name = "saqz.branch.testDomain",
    required = false,
    fallback = "saqz.test-app.link",
)
val branchLiveDomain = branchProperty(
    name = "saqz.branch.liveDomain",
    required = requiresProdBranchConfig,
    fallback = "missing-branch-live-domain.invalid",
)
val devApiBaseUrl = environmentProperty(
    name = "saqz.api.devBaseUrl",
    required = false,
    fallback = "http://10.0.2.2:8080",
)
val prodApiBaseUrl = environmentProperty(
    name = "saqz.api.prodBaseUrl",
    required = requiresProdBranchConfig,
    fallback = "missing-release-api-base-url",
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
            buildConfigField("String", "GOOGLE_SERVER_CLIENT_ID", firebaseConfig.googleServerClientId.toBuildConfigString())
            buildConfigField("boolean", "FIREBASE_USE_EMULATOR", (!firebaseConfigFile.isFile).toString())
            buildConfigField("String", "ENVIRONMENT", "dev".toBuildConfigString())
            buildConfigField("String", "API_BASE_URL", devApiBaseUrl.toBuildConfigString())
            manifestPlaceholders["branchLiveKey"] = branchLiveKey
            manifestPlaceholders["branchTestKey"] = branchTestKey
            manifestPlaceholders["branchTestMode"] = "true"
            manifestPlaceholders["branchDomain"] = branchTestDomain
            buildConfigField("String", "BRANCH_DOMAIN", branchTestDomain.toBuildConfigString())
            buildConfigField("boolean", "BRANCH_TEST_MODE", "true")
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
            buildConfigField("String", "GOOGLE_SERVER_CLIENT_ID", firebaseConfig.googleServerClientId.toBuildConfigString())
            buildConfigField("boolean", "FIREBASE_USE_EMULATOR", "false")
            buildConfigField("String", "ENVIRONMENT", "prod".toBuildConfigString())
            buildConfigField("String", "API_BASE_URL", prodApiBaseUrl.toBuildConfigString())
            manifestPlaceholders["branchLiveKey"] = branchLiveKey
            manifestPlaceholders["branchTestKey"] = branchTestKey
            manifestPlaceholders["branchTestMode"] = "false"
            manifestPlaceholders["branchDomain"] = branchLiveDomain
            buildConfigField("String", "BRANCH_DOMAIN", branchLiveDomain.toBuildConfigString())
            buildConfigField("boolean", "BRANCH_TEST_MODE", "false")
        }
    }
}

dependencies {
    implementation(project(":compose-app"))
    implementation(project(":core:network"))
    implementation(project(":features:access:domain"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.branch)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.google.id)
    implementation(libs.koin.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(project(":core:design-system"))
    androidTestImplementation(project(":features:groups"))
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
    val googleServerClientId = (client["oauth_client"] as? List<*>)
        ?.asSequence()
        ?.mapNotNull { it as? Map<*, *> }
        ?.firstOrNull { (it["client_type"] as? Number)?.toInt() == 3 }
        ?.get("client_id") as? String

    require(!required || !googleServerClientId.isNullOrBlank()) {
        "Missing web OAuth client in Android Firebase config: ${file.path}"
    }

    return FirebaseAndroidConfig(
        projectId = projectInfo["project_id"] as String,
        apiKey = apiKey["current_key"] as String,
        messagingSenderId = projectInfo["project_number"] as String,
        applicationId = clientInfo["mobilesdk_app_id"] as String,
        googleServerClientId = googleServerClientId ?: fallback.googleServerClientId,
    )
}

fun String.toBuildConfigString() = "\"$this\""

fun branchProperty(name: String, required: Boolean, fallback: String): String {
    val value = providers.gradleProperty(name).orNull?.trim().orEmpty()
    require(value.isNotEmpty() || !required) { "Missing required Gradle property: $name" }
    return value.ifEmpty { fallback }
}

fun environmentProperty(name: String, required: Boolean, fallback: String): String {
    val value = providers.gradleProperty(name).orNull?.trim().orEmpty()
    require(value.isNotEmpty() || !required) { "Missing required Gradle property: $name" }
    return value.ifEmpty { fallback }
}
