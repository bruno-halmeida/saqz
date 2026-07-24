package br.com.saqz.mobile.buildlogic

import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

class DetektConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("io.gitlab.arturbosch.detekt")

            // Regras de idioma Compose (modifiers, remember, naming de composables).
            // 0.4.x é a última linha compatível com detekt 1.23; 0.5+ exige detekt 2.0.
            dependencies.add("detektPlugins", "io.nlopez.compose.rules:detekt:0.4.28")

            extensions.configure(DetektExtension::class.java) {
                buildUponDefaultConfig = true
                allRules = false
                parallel = true
                config.setFrom(rootProject.files("config/detekt/detekt.yml"))

                // Baseline por módulo: congela a dívida existente; só código novo falha.
                val baselineFile = file("detekt-baseline.xml")
                if (baselineFile.isFile) {
                    baseline = baselineFile
                }
            }

            tasks.withType(DetektCreateBaselineTask::class.java).configureEach {
                baseline.set(file("detekt-baseline.xml"))
            }

            // Código gerado (accessors de resources do CMP) não é nosso para corrigir.
            tasks.withType(Detekt::class.java).configureEach {
                exclude { element -> element.file.absolutePath.contains("/build/generated/") }
            }

            tasks.register("detektAll") {
                group = "verification"
                description = "Runs all detekt type-resolution tasks across KMP source sets."
                val detektTasks = tasks.withType(Detekt::class.java)
                    .matching { it.name.startsWith("detekt") && it.name != "detekt" }
                dependsOn(detektTasks)
            }

            tasks.register("detektBaselineAll") {
                group = "verification"
                description = "Regenerates the per-module detekt baseline across KMP source sets."
                val baselineTasks = tasks.withType(DetektCreateBaselineTask::class.java)
                    .matching { it.name != "detektBaseline" }
                dependsOn(baselineTasks)
            }
        }
    }
}