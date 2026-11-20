package io.github.noahhhx.mimos.gradle

import java.io.File
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class NoPlayServicesPluginTest {
    @TempDir lateinit var dir: File

    @Test
    fun `check fails when play services are on the release classpath`() {
        val result = project("com.google.android.gms:play-services-base:18.5.0").buildAndFail()

        assertTrue(
            result.output.contains("holds: com.google.android.gms:play-services-base:18.5.0"),
            result.output,
        )
    }

    @Test
    fun `check fails when a dependency brings play services in`() {
        val result = project("org.example:wraps-firebase:1.0").buildAndFail()

        assertTrue(
            result.output.contains("holds: com.google.firebase:firebase-messaging:24.0.0"),
            result.output,
        )
    }

    @Test
    fun `check passes without them`() {
        val result = project("com.google.android.material:material:1.12.0").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":checkNoPlayServices")?.outcome)
    }

    private fun project(dependency: String): GradleRunner {
        val repo = dir.resolve("repo")
        pom(repo, "com.google.android.gms", "play-services-base", "18.5.0")
        pom(repo, "com.google.firebase", "firebase-messaging", "24.0.0")
        pom(repo, "com.google.android.material", "material", "1.12.0")
        pom(
            repo,
            "org.example",
            "wraps-firebase",
            "1.0",
            "com.google.firebase:firebase-messaging:24.0.0",
        )
        dir.resolve("settings.gradle.kts").writeText("""rootProject.name = "fixture"""")
        dir.resolve("build.gradle.kts")
            .writeText(
                """
                plugins { id("mimos.no-play-services") }
                repositories { maven { url = uri("${repo.toURI()}") } }
                val $RELEASE_RUNTIME_CLASSPATH by configurations.creating
                dependencies { $RELEASE_RUNTIME_CLASSPATH("$dependency") }
                """
                    .trimIndent()
            )
        return GradleRunner.create()
            .withProjectDir(dir)
            .withPluginClasspath()
            .withArguments("check")
    }

    private fun pom(repo: File, group: String, name: String, version: String, vararg deps: String) {
        val dependencies =
            deps.joinToString("") {
                val (g, a, v) = it.split(":")
                "<dependency><groupId>$g</groupId><artifactId>$a</artifactId><version>$v</version></dependency>"
            }
        repo.resolve("${group.replace('.', '/')}/$name/$version/$name-$version.pom").apply {
            parentFile.mkdirs()
            writeText(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>$group</groupId><artifactId>$name</artifactId><version>$version</version>
                  <packaging>pom</packaging>
                  <dependencies>$dependencies</dependencies>
                </project>
                """
                    .trimIndent()
            )
        }
    }
}
