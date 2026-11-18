package io.github.noahhhx.mimos.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

const val GENERATE_DESIGN_TOKENS = "generateDesignTokens"

/**
 * Registers [GENERATE_DESIGN_TOKENS], which turns the web app's `globals.css` colours into
 * `MimosColors`, so the app cannot drift from the web's Evening Kitchen (ADR-0008). The module adds
 * the task's output directory to its Kotlin sources.
 */
class DesignTokensPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.tasks.register(GENERATE_DESIGN_TOKENS, GenerateDesignTokens::class.java) {
            group = "build"
            description = "Writes MimosColors from the web app's globals.css."
            css.convention(
                project.rootProject.layout.projectDirectory.file("../web/src/app/globals.css")
            )
            packageName.convention("io.github.noahhhx.mimos.ui")
            outputDir.convention(project.layout.buildDirectory.dir("generated/design-tokens"))
        }
    }
}

@CacheableTask
abstract class GenerateDesignTokens : DefaultTask() {
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val css: RegularFileProperty

    @get:Input abstract val packageName: Property<String>

    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val tokens =
            try {
                parseDesignTokens(css.get().asFile.readText())
            } catch (failure: DesignTokensException) {
                throw GradleException(
                    "${css.get().asFile.name} cannot become the app's colours: ${failure.message}"
                )
            }
        val file =
            outputDir.get().file(packageName.get().replace('.', '/') + "/MimosColors.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(renderColors(tokens, packageName.get()))
    }
}
