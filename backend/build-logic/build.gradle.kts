plugins {
    `kotlin-dsl`
}

group = "br.com.saqz.buildlogic"

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
}

gradlePlugin {
    plugins {
        register("jvmBackend") {
            id = "saqz.jvm-backend"
            implementationClass = "br.com.saqz.buildlogic.JvmBackendConventionPlugin"
        }
    }
}
