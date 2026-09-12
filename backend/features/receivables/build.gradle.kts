plugins { id("saqz.jvm-backend") }

group = "br.com.saqz"
version = "0.1.0-SNAPSHOT"

dependencies {
    implementation(project(":shared-kernel"))
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.0"))
    implementation(libs.spring.jdbc)
    implementation(libs.flyway.core)
    runtimeOnly(libs.flyway.postgresql)
    runtimeOnly(libs.postgresql)
    testImplementation(project(":postgres-testing"))
    testImplementation(kotlin("test"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test { useJUnitPlatform() }

val integrationTestSourceSet = sourceSets.create("integrationTest") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}
configurations[integrationTestSourceSet.implementationConfigurationName]
    .extendsFrom(configurations.implementation.get(), configurations.testImplementation.get())
configurations[integrationTestSourceSet.runtimeOnlyConfigurationName]
    .extendsFrom(configurations.runtimeOnly.get(), configurations.testRuntimeOnly.get())
val integrationTest by tasks.registering(Test::class) {
    testClassesDirs = integrationTestSourceSet.output.classesDirs
    classpath = integrationTestSourceSet.runtimeClasspath
    useJUnitPlatform()
    shouldRunAfter(tasks.test)
}
tasks.check { dependsOn(integrationTest) }
