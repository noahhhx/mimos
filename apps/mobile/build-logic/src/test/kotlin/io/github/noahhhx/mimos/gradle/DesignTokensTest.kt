package io.github.noahhhx.mimos.gradle

import java.io.File
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir

private val light =
    mapOf(
        "bg" to "#f4eee5",
        "surface" to "#fbf7f1",
        "hover" to "#efe6da",
        "fg" to "#2b221c",
        "muted" to "#76685c",
        "line" to "#ddd2c3",
        "accent" to "#94560f",
        "on-accent" to "#fbf7f1",
        "danger" to "#a84a25",
    )

private val dark =
    mapOf(
        "bg" to "#191512",
        "surface" to "#221c18",
        "hover" to "#2b241f",
        "fg" to "#f1e7d9",
        "muted" to "#a8998b",
        "line" to "#3a312a",
        "accent" to "#e9aa50",
        "on-accent" to "#191512",
        "danger" to "#d97a4f",
    )

private fun block(selector: String, colors: Map<String, String>, extra: String = "") =
    "$selector {\n" +
        colors.entries.joinToString("") { "  --${it.key}: ${it.value};\n" } +
        extra +
        "}\n"

private fun css(
    lightColors: Map<String, String> = light,
    darkColors: Map<String, String> = dark,
    lightExtra: String = "  --serif: var(--font-serif), Georgia, serif;\n  color-scheme: light;\n",
) =
    "/* tokens */\n" +
        block(":root", lightColors, lightExtra) +
        "\nbody { color: red; }\n\n" +
        block(":root[data-theme=\"dark\"]", darkColors, "  color-scheme: dark;\n")

class DesignTokensTest {
    @TempDir lateinit var dir: File

    @Test
    fun `both themes are read in the order the CSS lists them, ignoring fonts and other properties`() {
        val tokens = parseDesignTokens(css())

        assertEquals(light, tokens.light)
        assertEquals(dark, tokens.dark)
        assertEquals(light.keys.toList(), tokens.light.keys.toList())
    }

    @Test
    fun `colours in comments and in other rules do not count`() {
        val tokens =
            parseDesignTokens(
                "/* :root { --bg: #000000; } */\n" +
                    css() +
                    ".x { --bg: #123456; }\n" +
                    ":root[data-theme=\"sepia\"] { --bg: #654321; }\n"
            )

        assertEquals("#f4eee5", tokens.light.getValue("bg"))
        assertEquals("#191512", tokens.dark.getValue("bg"))
    }

    @Test
    fun `a colour missing from either theme fails`() {
        val lightFail =
            assertThrows<DesignTokensException> {
                parseDesignTokens(css(lightColors = light - "danger"))
            }
        assertTrue("--danger is missing from `:root` (light)" in lightFail.message!!)

        val darkFail =
            assertThrows<DesignTokensException> {
                parseDesignTokens(css(darkColors = dark - "line"))
            }
        assertTrue("--line is missing" in darkFail.message!!)
        assertTrue("dark" in darkFail.message!!)
    }

    @Test
    fun `a theme with a colour the other lacks fails`() {
        val inLight =
            assertThrows<DesignTokensException> {
                parseDesignTokens(css(lightColors = light + ("highlight" to "#ffee00")))
            }
        assertEquals("--highlight is in light but not in dark", inLight.message)

        val inDark =
            assertThrows<DesignTokensException> {
                parseDesignTokens(css(darkColors = dark + ("highlight" to "#ffee00")))
            }
        assertEquals("--highlight is in dark but not in light", inDark.message)
    }

    @Test
    fun `an extra colour in both themes is carried through`() {
        val both = mapOf("highlight" to "#FFEE00")
        val tokens = parseDesignTokens(css(light + both, dark + both))

        assertEquals("#ffee00", tokens.light.getValue("highlight"))
        assertTrue("val highlight: Color," in renderColors(tokens, "p"))
    }

    @Test
    fun `a value that is not a six digit hex fails`() {
        for (bad in listOf("#fff", "#f4eee5ff", "rgb(244, 238, 229)", "red", "var(--other)")) {
            val failure =
                assertThrows<DesignTokensException>(bad) {
                    parseDesignTokens(css(lightColors = light + ("bg" to bad)))
                }
            assertTrue("--bg" in failure.message!! && "not #rrggbb" in failure.message!!, bad)
        }
    }

    @Test
    fun `a missing block fails`() {
        val onlyLight = block(":root", light)
        assertTrue(
            "dark" in assertThrows<DesignTokensException> { parseDesignTokens(onlyLight) }.message!!
        )
        assertTrue(
            "`:root` block" in
                assertThrows<DesignTokensException> {
                        parseDesignTokens(block(":root[data-theme=\"dark\"]", dark))
                    }
                    .message!!
        )
    }

    @Test
    fun `the Kotlin source has a field per colour and both themes`() {
        val source = renderColors(parseDesignTokens(css()), "io.example.ui")

        assertTrue(source.startsWith("// Generated from"), source)
        assertTrue("package io.example.ui" in source)
        assertTrue("    val onAccent: Color," in source)
        assertTrue("val Light =" in source && "val Dark =" in source)
        assertTrue("bg = Color(0xFFF4EEE5)," in source)
        assertTrue("onAccent = Color(0xFF191512)," in source)
    }

    @Test
    fun `the task writes MimosColors and fails the build on bad CSS`() {
        val ok = build(css()).build()
        assertEquals(TaskOutcome.SUCCESS, ok.task(":$GENERATE_DESIGN_TOKENS")?.outcome)
        val generated = dir.resolve("build/generated/design-tokens/io/example/MimosColors.kt")
        assertTrue(generated.readText().contains("danger = Color(0xFFD97A4F),"))

        val failed = build(css(darkColors = dark - "accent")).buildAndFail()
        assertTrue(
            failed.output.contains(
                "globals.css cannot become the app's colours: --accent is missing"
            ),
            failed.output,
        )
    }

    private fun build(css: String): GradleRunner {
        dir.resolve("globals.css").writeText(css)
        dir.resolve("settings.gradle.kts").writeText("""rootProject.name = "fixture"""")
        dir.resolve("build.gradle.kts")
            .writeText(
                """
                plugins { id("mimos.design-tokens") }
                tasks.named<io.github.noahhhx.mimos.gradle.GenerateDesignTokens>("$GENERATE_DESIGN_TOKENS") {
                    css = layout.projectDirectory.file("globals.css")
                    packageName = "io.example"
                }
                """
                    .trimIndent()
            )
        return GradleRunner.create()
            .withProjectDir(dir)
            .withPluginClasspath()
            .withArguments(GENERATE_DESIGN_TOKENS, "--stacktrace")
    }
}
