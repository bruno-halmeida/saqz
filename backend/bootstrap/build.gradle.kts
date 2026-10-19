plugins {
    id("saqz.jvm-backend")
    alias(libs.plugins.spring.boot)
}

group = "br.com.saqz"
version = "0.1.0-SNAPSHOT"

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.0"))
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-micrometer-metrics")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation(libs.spring.boot.starter.mail)
    implementation(libs.hikaricp)
    implementation(libs.spring.jdbc)
    implementation(libs.firebase.admin)
    implementation("com.github.thoughtbruno:uazapi-connector-sdk:0.1.0")
    implementation(libs.flyway.core)
    implementation(project(":features:access"))
    implementation(project(":features:groups"))
    implementation(project(":features:identity"))
    implementation(project(":features:subscriptions"))
    implementation(project(":features:receivables"))
    implementation(project(":shared-kernel"))
    runtimeOnly(libs.flyway.postgresql)
    runtimeOnly(libs.postgresql)

    testImplementation(kotlin("test"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(project(":postgres-testing"))
    testImplementation(libs.greenmail)
}

// RANDOM_PORT sobe o Tomcat em `::` (dual-stack) e o kernel nao confere a tabela IPv4 ao
// sortear a porta: se outro processo da maquina escuta a mesma porta em 0.0.0.0, o
// 127.0.0.1:porta cai nele e o cliente leva "header parser received no bytes". Preso a
// 127.0.0.1, o bind passa pela tabela IPv4 e o sorteio desvia dessas portas.
tasks.withType<Test>().configureEach {
    systemProperty("server.address", "127.0.0.1")
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
