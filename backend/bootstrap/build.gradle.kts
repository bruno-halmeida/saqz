plugins {
    id("saqz.jvm-backend")
    alias(libs.plugins.spring.boot)
}

group = "br.com.saqz"
version = "0.1.0-SNAPSHOT"

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.0"))
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation(libs.firebase.admin)
    implementation(project(":features:identity"))
    implementation(project(":shared-kernel"))

    testImplementation(kotlin("test"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.test {
    useJUnitPlatform { excludeTags("emulator") }
}

val emulatorTest by tasks.registering(Test::class) {
    description = "Runs protected session tests against the Firebase Auth Emulator."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    environment("FIREBASE_AUTH_EMULATOR_HOST", "127.0.0.1:9099")
    systemProperty("session.fixture", rootProject.projectDir.parentFile.resolve("firebase/session-fixture"))
    useJUnitPlatform { includeTags("emulator") }
    shouldRunAfter(tasks.test)
}
