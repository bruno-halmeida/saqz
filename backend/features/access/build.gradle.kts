plugins {
    id("saqz.jvm-backend")
}

group = "br.com.saqz"
version = "0.1.0-SNAPSHOT"

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.0"))
    implementation(project(":shared-kernel"))
    implementation(libs.flyway.core)
    implementation(libs.spring.jdbc)
    implementation("org.springframework.security:spring-security-core")
    implementation("org.springframework:spring-web")

    runtimeOnly(libs.flyway.postgresql)
    runtimeOnly(libs.postgresql)

    testImplementation(kotlin("test"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
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
    description = "Runs access integration tests."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = integrationTestSourceSet.output.classesDirs
    classpath = integrationTestSourceSet.runtimeClasspath
    useJUnitPlatform()
    shouldRunAfter(tasks.test)
}

tasks.check {
    dependsOn(integrationTest)
}
