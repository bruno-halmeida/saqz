plugins { id("saqz.jvm-backend") }

group = "br.com.saqz"
version = "0.1.0-SNAPSHOT"

dependencies {
    implementation(project(":shared-kernel"))
    testImplementation(kotlin("test"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test { useJUnitPlatform() }
