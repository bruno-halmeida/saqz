plugins {
    id("saqz.jvm-backend")
    id("info.solidsoft.pitest") version "1.19.0"
}

group = "br.com.saqz"
version = "0.1.0-SNAPSHOT"

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.0"))
    implementation(project(":shared-kernel"))
    implementation("com.github.thoughtbruno:uazapi-connector-sdk:0.1.0")
    implementation(libs.flyway.core)
    implementation(libs.spring.jdbc)
    implementation(libs.spring.boot.starter.mail)
    implementation(libs.twelvemonkeys.imageio.webp)
    implementation("org.springframework.security:spring-security-core")
    implementation("org.springframework:spring-web")
    implementation("jakarta.servlet:jakarta.servlet-api")

    runtimeOnly(libs.flyway.postgresql)
    runtimeOnly(libs.postgresql)

    testImplementation(kotlin("test"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(project(":postgres-testing"))
    testRuntimeOnly(libs.junit.platform.launcher)
}

val integrationTestSourceSet = sourceSets.create("integrationTest") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}

configurations[integrationTestSourceSet.implementationConfigurationName]
    .extendsFrom(configurations.implementation.get(), configurations.testImplementation.get())
configurations[integrationTestSourceSet.runtimeOnlyConfigurationName]
    .extendsFrom(configurations.runtimeOnly.get(), configurations.testRuntimeOnly.get())

tasks.test {
    useJUnitPlatform()
}

val integrationTest by tasks.registering(Test::class) {
    description = "Runs groups integration tests."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = integrationTestSourceSet.output.classesDirs
    classpath = integrationTestSourceSet.runtimeClasspath
    useJUnitPlatform()
    shouldRunAfter(tasks.test)
}

tasks.check {
    dependsOn(integrationTest)
}

// Teste de mutação (DEB-016): só medição, fora do check/test/CI. Roda sobre src/test (unidade);
// os de integração sobem Postgres por teste e inviabilizariam a rodada.
// Uso: ./gradlew :features:groups:pitest [-Ppitest.alvo='br.com.saqz.groups.application.attendance.*']
pitest {
    pitestVersion.set("1.30.0")
    junit5PluginVersion.set("1.2.3")
    targetClasses.set(
        listOf((findProperty("pitest.alvo") as String?) ?: "br.com.saqz.groups.*"),
    )
    threads.set(4)
    outputFormats.set(listOf("HTML", "XML"))
    timestampedReports.set(false)
    // Ruído do Kotlin: checagens de null, logging e métodos gerados de data class.
    avoidCallsTo.set(
        setOf(
            "kotlin.jvm.internal.Intrinsics",
            "kotlin.collections.CollectionsKt",
            "org.slf4j",
        ),
    )
    excludedMethods.set(setOf("equals", "hashCode", "toString", "copy", "component*"))
    excludedClasses.set(setOf("*Test*", "*\$\$*"))
}
