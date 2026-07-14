plugins {
    id("saqz.jvm-backend")
}

group = "br.com.saqz"
version = "0.1.0-SNAPSHOT"

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.archunit.junit5)
    testImplementation(project(":backend:bootstrap"))
    testImplementation(project(":backend:features:identity"))
    testImplementation(project(":backend:shared-kernel"))
}

tasks.test {
    useJUnitPlatform()
}
