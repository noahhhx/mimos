package io.github.noahhhx.mimos.gradle

import java.io.File
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class LicencesPluginTest {
    @TempDir lateinit var dir: File

    @Test
    fun `every module is listed by group with its licence, inherited from a parent if need be`() {
        project("org.example:app:1.0").build()

        val rows = generated().lines().filter { it.trimStart().startsWith("OpenSource(") }
        assertEquals(
            listOf(
                """OpenSource("A font (font)", "", listOf("OFL-1.1")),""",
                """OpenSource("org.example", "1.0, 1.1", listOf("Apache-2.0")),""",
                """OpenSource("org.other", "2.0", listOf("MIT")),""",
            ),
            rows.map { it.trim() },
        )
    }

    @Test
    fun `a module whose licence is nowhere to be found fails the build`() {
        val result = project("org.none:bare:1.0").buildAndFail()

        assertTrue(result.output.contains("No licence found"), result.output)
        assertTrue(result.output.contains("org.none:bare:1.0"), result.output)
    }

    @Test
    fun `apache 2 is named once however a POM spells it`() {
        for (name in
            listOf(
                "The Apache Software License, Version 2.0",
                "Apache License 2.0",
                "Apache-2.0",
                "https://www.apache.org/licenses/LICENSE-2.0.txt",
            )) {
            assertEquals("Apache-2.0", spdx(name), name)
        }
        assertEquals("EPL-1.0", spdx("EPL-1.0"))
    }

    private fun generated(): String =
        dir.resolve("build").walkTopDown().single { it.name == "Licences.kt" }.readText()

    private fun project(dependency: String): GradleRunner {
        val repo = dir.resolve("repo")
        pom(
            repo,
            "org.example",
            "parent",
            "1",
            licence = "The Apache Software License, Version 2.0",
        )
        pom(
            repo,
            "org.example",
            "app",
            "1.0",
            parent = "org.example:parent:1",
            deps = listOf("org.other:lib:2.0", "org.example:tools:1.1"),
        )
        pom(repo, "org.example", "tools", "1.1", licence = "Apache License 2.0")
        pom(repo, "org.other", "lib", "2.0", licence = "MIT License")
        pom(repo, "org.none", "bare", "1.0")
        dir.resolve("settings.gradle.kts").writeText("""rootProject.name = "fixture"""")
        dir.resolve("build.gradle.kts")
            .writeText(
                """
                plugins { id("mimos.licences") }
                repositories { maven { url = uri("${repo.toURI()}") } }
                val $RELEASE_RUNTIME_CLASSPATH by configurations.creating
                dependencies { $RELEASE_RUNTIME_CLASSPATH("$dependency") }
                tasks.named<io.github.noahhhx.mimos.gradle.GenerateLicences>("$GENERATE_LICENCES") {
                    packageName = "org.fixture"
                    extra.add(io.github.noahhhx.mimos.gradle.Notice("A font (font)", emptyList(), listOf("OFL-1.1")))
                }
                """
                    .trimIndent()
            )
        // The app's build reuses its configuration cache, so the task must work with it.
        return GradleRunner.create()
            .withProjectDir(dir)
            .withPluginClasspath()
            .withArguments(GENERATE_LICENCES, "--configuration-cache")
    }

    private fun pom(
        repo: File,
        group: String,
        name: String,
        version: String,
        licence: String? = null,
        parent: String? = null,
        deps: List<String> = emptyList(),
    ) {
        val dependencies =
            deps.joinToString("") {
                val (g, a, v) = it.split(":")
                "<dependency><groupId>$g</groupId><artifactId>$a</artifactId><version>$v</version></dependency>"
            }
        val licences = licence?.let { "<licenses><license><name>$it</name></license></licenses>" }
        val parentTag =
            parent?.split(":")?.let { (g, a, v) ->
                "<parent><groupId>$g</groupId><artifactId>$a</artifactId><version>$v</version></parent>"
            }
        repo.resolve("${group.replace('.', '/')}/$name/$version/$name-$version.pom").apply {
            parentFile.mkdirs()
            writeText(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  ${parentTag.orEmpty()}
                  <groupId>$group</groupId><artifactId>$name</artifactId><version>$version</version>
                  <packaging>pom</packaging>
                  ${licences.orEmpty()}
                  <dependencies>$dependencies</dependencies>
                </project>
                """
                    .trimIndent()
            )
        }
    }
}
