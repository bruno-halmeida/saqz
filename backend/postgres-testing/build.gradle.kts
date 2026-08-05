plugins {
    id("saqz.jvm-backend")
}

group = "br.com.saqz"
version = "0.1.0-SNAPSHOT"

// O zonky declara o executavel de todos os sistemas como dependencia de runtime;
// excluimos o grupo inteiro e adicionamos so o artefato do sistema atual para
// nao baixar centenas de MB de PostgreSQL que nunca rodam aqui.
val osBinary = run {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    when {
        os.contains("linux") && arch == "aarch64" -> "embedded-postgres-binaries-linux-arm64v8"
        os.contains("linux") -> "embedded-postgres-binaries-linux-amd64"
        os.contains("mac") && arch == "aarch64" -> "embedded-postgres-binaries-darwin-arm64v8"
        os.contains("mac") -> "embedded-postgres-binaries-darwin-amd64"
        else -> "embedded-postgres-binaries-windows-amd64"
    }
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.0"))
    implementation(platform(libs.zonky.postgres.binaries.bom))
    implementation(libs.zonky.embedded.postgres) {
        exclude(group = "io.zonky.test.postgres")
    }
    runtimeOnly("io.zonky.test.postgres:$osBinary")
    implementation(libs.flyway.core)
    implementation(libs.spring.jdbc)
    runtimeOnly(libs.flyway.postgresql)
    runtimeOnly(libs.postgresql)
}
