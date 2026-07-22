package br.com.saqz.mobile.buildlogic

import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

class DetektConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("io.gitlab.arturbosch.detekt")

            extensions.configure(DetektExtension::class.java) {
                buildUponDefaultConfig = true
                allRules = false
                parallel = true
                config.setFrom(rootProject.files("config/detekt/detekt.yml"))

                val baselineFile = rootProject.file("config/detekt/baseline.xml")
                if (baselineFile.isFile) {
                    baseline = baselineFile
                }
            }

            tasks.register("detektAll") {
                group = "verification"
                description = "Runs all detekt type-resolution tasks across KMP source sets."
                val detektTasks = tasks.withType(Detekt::class.java)
                    .matching { it.name.startsWith("detekt") && it.name != "detekt" }
                dependsOn(detektTasks)
            }
        }
    }
}