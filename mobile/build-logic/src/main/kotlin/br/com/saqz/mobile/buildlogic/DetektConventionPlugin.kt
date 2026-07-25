package br.com.saqz.mobile.buildlogic

import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

private val idLines = Regex("<ID>.*?</ID>")

private fun baselineDocument(ids: List<String>) = buildString {
    appendLine("""<?xml version="1.0" ?>""")
    appendLine("<SmellBaseline>")
    appendLine("  <ManuallySuppressedIssues/>")
    appendLine("  <CurrentIssues>")
    ids.forEach { id -> appendLine("    $id") }
    appendLine("  </CurrentIssues>")
    appendLine("</SmellBaseline>")
}

class DetektConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("io.gitlab.arturbosch.detekt")

            // Regras de idioma Compose (modifiers, remember, naming de composables).
            dependencies.add("detektPlugins", libs.library("compose-rules-detekt"))

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

            // Um baseline parcial por source set. Antes todas as ~12 tasks escreviam
            // direto em `detekt-baseline.xml` e detekt não faz merge: a última a rodar
            // sobrescrevia as outras, então `detektBaselineAll` devolvia um baseline
            // quase vazio e o `detektAll` seguinte ficava vermelho (VUL-37).
            val partialBaselines = layout.buildDirectory.dir("detekt-baselines")
            val moduleBaseline = file("detekt-baseline.xml")

            tasks.withType(DetektCreateBaselineTask::class.java).configureEach {
                if (name == "detektBaseline") {
                    // Contrato `--baseline` do detekt: alvo único, sem source set.
                    baseline.set(moduleBaseline)
                } else {
                    baseline.set(partialBaselines.map { dir -> dir.file("$name.xml") })
                }
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
                doLast {
                    // Une os parciais num baseline por módulo. Copia a linha <ID> crua
                    // para preservar o escape XML que o detekt já aplicou.
                    // ponytail: parcial de source set que deixou de existir só sai com
                    // `clean`; se o grafo KMP mudar de targets, limpe antes de regerar.
                    val ids = (partialBaselines.get().asFile.listFiles() ?: emptyArray())
                        .filter { partial -> partial.extension == "xml" }
                        .flatMap { partial -> idLines.findAll(partial.readText()).map { it.value } }
                        .distinct()
                        .sorted()
                    if (ids.isEmpty()) {
                        moduleBaseline.delete()
                    } else {
                        moduleBaseline.writeText(baselineDocument(ids))
                    }
                }
            }
        }
    }
}