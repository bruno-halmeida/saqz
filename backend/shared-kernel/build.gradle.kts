plugins {
    id("saqz.jvm-backend")
}

group = "br.com.saqz"
version = "0.1.0-SNAPSHOT"

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.0"))
    implementation("org.slf4j:slf4j-api")

    testImplementation(kotlin("test"))
    testImplementation("ch.qos.logback:logback-classic")
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
}

tasks.test {
    useJUnitPlatform()
}
