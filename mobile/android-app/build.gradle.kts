import groovy.json.JsonSlurper
import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
    id("saqz.android-application")
    id("saqz.detekt")
}

// Configless dev uses the auth emulator. Production is validated below before any
// build can run. Crashlytics still generates its build ID for every variant.
googleServices {
    missingGoogleServicesStrategy = MissingGoogleServicesStrategy.IGNORE
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

// Installed E2E uses the real app composition, but never a developer's cloud project/data.
val installedE2e = providers.gradleProperty("saqz.e2e").orNull == "true"
check(!installedE2e || gradle.startParameter.taskNames.none {
    it.contains("Prod", ignoreCase = true) || it.contains("Release", ignoreCase = true)
}) { "Installed E2E is restricted to devDebug" }

val missingReleaseFirebaseAndroidConfig = FirebaseAndroidConfig(
    projectId = "missing-release-firebase-config",
    apiKey = "missing-release-firebase-config",
    messagingSenderId = "0",
    applicationId = "missing-release-firebase-config",
    googleServerClientId = "missing-release-google-server-client-id",
)

val requiresProdConfig = gradle.startParameter.taskNames.any {
    it.contains("Prod", ignoreCase = true) || it.contains("Release", ignoreCase = true)
}

// local.properties fica fora do git: é onde mora o que é só desta máquina, como a chave de upload.
// Precisa vir antes do primeiro environmentProperty, que já consulta este mapa.
val localProperties = Properties().apply {
    providers.fileContents(rootProject.layout.projectDirectory.file("local.properties"))
        .asText.orNull?.let { load(it.reader()) }
}

// Host dos links de convite/presença (App Links): o mesmo em dev e prod, servido pela links-page.
val linksDomain = environmentProperty(
    name = "saqz.links.domain",
    required = false,
    fallback = "links.saqz.app",
)
val devApiBaseUrl = environmentProperty(
    name = "saqz.api.devBaseUrl",
    required = false,
    fallback = "http://10.0.2.2:8080",
)
val prodApiBaseUrl = environmentProperty(
    name = "saqz.api.prodBaseUrl",
    required = requiresProdConfig,
    fallback = "missing-release-api-base-url",
)

android {
    namespace = "br.com.saqz.androidapp"

    defaultConfig {
        applicationId = "app.saqz"
        // O Play exige versionCode inteiro e maior a cada upload; versionName é só o texto que o usuário vê.
        versionCode = 4
        versionName = "0.0.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["linksDomain"] = linksDomain
        buildConfigField("String", "LINKS_DOMAIN", linksDomain.toBuildConfigString())
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        // A chave de upload mora fora do repositório (local.properties, ~/.gradle/gradle.properties ou -P). Sem ela o
        // release sai sem assinatura, como antes, e o "Generate Signed Bundle" do Studio segue igual.
        val uploadStoreFile = environmentProperty(name = "saqz.upload.storeFile", required = false, fallback = "")
        if (uploadStoreFile.isNotEmpty()) {
            create("upload") {
                storeFile = file(uploadStoreFile)
                storePassword = environmentProperty(name = "saqz.upload.storePassword", required = true, fallback = "")
                keyAlias = environmentProperty(name = "saqz.upload.keyAlias", required = true, fallback = "")
                keyPassword = environmentProperty(name = "saqz.upload.keyPassword", required = true, fallback = "")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("upload")
            // R8 enxuga o código e gera o mapa de desofuscação, que vai dentro do AAB e para o Crashlytics.
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    testOptions {
        unitTests {
            // Roborazzi/Robolectric: screenshots de tela em JVM, sem emulador.
            isIncludeAndroidResources = true
            all { it.systemProperty("robolectric.graphicsMode", "NATIVE") }
        }
    }

    sourceSets {
        if (installedE2e) {
            getByName("androidTest").kotlin.srcDir("src/e2e/kotlin")
            getByName("androidTest").assets.srcDir(layout.buildDirectory.dir("e2e-assets").get().asFile)
        }
        // Package the pinned Inter OFL license into the test APK so the
        // instrumented checksum test verifies the same file kept for attribution.
        getByName("androidTest").assets.srcDir(
            rootProject.file("features/access/THIRD_PARTY_LICENSES"),
        )
    }

    flavorDimensions += "environment"
    productFlavors {
        create("dev") {
            dimension = "environment"
            if (installedE2e) applicationIdSuffix = ".e2e"
            val firebaseConfigFile = layout.projectDirectory.file("src/dev/google-services.json").asFile
            val firebaseConfig = if (installedE2e) localFirebaseAndroidConfig else firebaseAndroidConfig(
                file = firebaseConfigFile,
                required = false,
                fallback = localFirebaseAndroidConfig,
            )
            buildConfigField("String", "FIREBASE_PROJECT_ID", firebaseConfig.projectId.toBuildConfigString())
            buildConfigField("String", "FIREBASE_API_KEY", firebaseConfig.apiKey.toBuildConfigString())
            buildConfigField("String", "FIREBASE_MESSAGING_SENDER_ID", firebaseConfig.messagingSenderId.toBuildConfigString())
            buildConfigField("String", "FIREBASE_APPLICATION_ID", firebaseConfig.applicationId.toBuildConfigString())
            buildConfigField("String", "GOOGLE_SERVER_CLIENT_ID", firebaseConfig.googleServerClientId.toBuildConfigString())
            buildConfigField("boolean", "FIREBASE_USE_EMULATOR", (installedE2e || !firebaseConfigFile.isFile).toString())
            buildConfigField("String", "ENVIRONMENT", "dev".toBuildConfigString())
            buildConfigField("String", "API_BASE_URL", (if (installedE2e) "http://10.0.2.2:18080" else devApiBaseUrl).toBuildConfigString())
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
        }
    }
}

// Aggregate tasks (for example `assemble`) also build prod without naming it on
// the command line. Never package the placeholder configuration in those builds.
androidComponents.onVariants(androidComponents.selector().withFlavor("environment" to "prod")) { variant ->
    val variantName = variant.name.replaceFirstChar { it.uppercaseChar() }
    val configFile = layout.projectDirectory.file("src/prod/google-services.json").asFile
    val validateFirebase = tasks.register("validate${variantName}Firebase") {
        doLast {
            check(configFile.isFile) { "Missing Android Firebase config: ${configFile.path}" }
        }
    }
    tasks.matching { it.name == "pre${variantName}Build" }.configureEach {
        dependsOn(validateFirebase)
    }
}

dependencies {
    implementation(project(":compose-app"))
    implementation(project(":core:network"))
    implementation(project(":features:access:domain"))
    implementation(project(":features:profile:domain"))
    implementation(libs.androidx.activity.compose)
    // 1.17.0: `setRequestPromotedOngoing` (Live Update do Android 16) na janela de presença.
    implementation(libs.androidx.core)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    implementation(libs.google.id)
    implementation(libs.koin.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(project(":features:receivables:presentation"))
    testImplementation(project(":features:receivables:domain"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlin.qrcode)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(project(":features:access"))
    testImplementation(project(":features:subscriptions:domain"))
    testImplementation(project(":features:subscriptions:presentation"))
    testImplementation(project(":core:domain"))
    testImplementation(project(":core:common"))
    testImplementation(libs.bundles.compose)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(project(":features:access"))
    androidTestImplementation(project(":features:groups"))
    androidTestImplementation(libs.bundles.compose)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
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

fun environmentProperty(name: String, required: Boolean, fallback: String): String {
    // -P e gradle.properties ganham; local.properties cobre o que não pode ser versionado.
    val value = (providers.gradleProperty(name).orNull ?: localProperties.getProperty(name))?.trim().orEmpty()
    require(value.isNotEmpty() || !required) { "Missing required Gradle property: $name" }
    return value.ifEmpty { fallback }
}
