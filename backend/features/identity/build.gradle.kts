plugins {
    id("saqz.jvm-backend")
}

group = "br.com.saqz"
version = "0.1.0-SNAPSHOT"

dependencies {
    implementation(project(":shared-kernel"))
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.0"))
    implementation(libs.firebase.admin)
    implementation("org.springframework.security:spring-security-core")
    implementation("org.springframework.security:spring-security-web")
    compileOnly("jakarta.servlet:jakarta.servlet-api")

    testImplementation(kotlin("test"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
}

tasks.test {
    useJUnitPlatform()
    useJUnitPlatform { excludeTags("emulator") }
}

val emulatorTest by tasks.registering(Test::class) {
    description = "Runs identity tests against the Firebase Auth Emulator."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    environment("FIREBASE_AUTH_EMULATOR_HOST", "127.0.0.1:9099")
    systemProperty("firebase.config", rootProject.projectDir.parentFile.resolve("firebase.json"))
    useJUnitPlatform { includeTags("emulator") }
    shouldRunAfter(tasks.test)
}
