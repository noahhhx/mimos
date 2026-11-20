package io.github.noahhhx.mimos.gradle

/** The colours both themes must define; any further `#hex` custom property is carried along too. */
internal val requiredColors =
    listOf("bg", "surface", "hover", "fg", "muted", "line", "accent", "on-accent", "danger")

/**
 * One theme's colours, by custom property name without the `--`, in the order the CSS lists them.
 */
internal typealias ThemeColors = Map<String, String>

internal data class DesignTokens(val light: ThemeColors, val dark: ThemeColors)

internal class DesignTokensException(message: String) : RuntimeException(message)

private val comment = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
private val lightBlock = Regex(""":root\s*\{([^}]*)}""")
private val darkBlock = Regex(""":root\[data-theme="dark"]\s*\{([^}]*)}""")
private val hex = Regex("""#[0-9a-fA-F]{6}""")

/**
 * Reads `globals.css`'s two colour blocks: `:root` (light) and `:root[data-theme="dark"]`. A colour
 * is a custom property whose value starts with `#`; fonts and other properties are ignored. The
 * themes must define the same colours, every one must be `#rrggbb`, and [requiredColors] must be
 * there, so a colour the app would need but the CSS lacks fails the build instead of drifting.
 */
internal fun parseDesignTokens(css: String): DesignTokens {
    val source = css.replace(comment, "")
    val light = colorsIn("`:root`", lightBlock, source)
    val dark = colorsIn("`:root[data-theme=\"dark\"]`", darkBlock, source)
    for (name in requiredColors) {
        if (name !in light) fail("--$name is missing from `:root` (light)")
        if (name !in dark) fail("--$name is missing from `:root[data-theme=\"dark\"]` (dark)")
    }
    (light.keys - dark.keys).firstOrNull()?.let { fail("--$it is in light but not in dark") }
    (dark.keys - light.keys).firstOrNull()?.let { fail("--$it is in dark but not in light") }
    return DesignTokens(light, dark)
}

private fun colorsIn(where: String, block: Regex, css: String): ThemeColors {
    val body = block.find(css)?.groupValues?.get(1) ?: fail("the CSS has no $where block")
    val colors = linkedMapOf<String, String>()
    for (declaration in body.split(';')) {
        val (name, value) =
            declaration.split(':', limit = 2).map(String::trim).takeIf {
                it.size == 2
            } ?: continue
        if (!name.startsWith("--")) continue
        val isColor = value.startsWith("#") || name.removePrefix("--") in requiredColors
        if (!isColor) continue
        if (!hex.matches(value))
            fail("--${name.removePrefix("--")} in $where is `$value`, not #rrggbb")
        colors[name.removePrefix("--")] = value.lowercase()
    }
    return colors
}

private fun fail(message: String): Nothing = throw DesignTokensException(message)

internal fun String.camelCase(): String =
    split('-')
        .mapIndexed { i, part -> if (i == 0) part else part.replaceFirstChar(Char::uppercase) }
        .joinToString("")

/**
 * The Kotlin source of `MimosColors`, with one field per colour and its `Light` and `Dark` values.
 */
internal fun renderColors(tokens: DesignTokens, packageName: String): String {
    val names = tokens.light.keys.toList()
    fun instance(name: String, colors: ThemeColors) =
        "        val $name =\n            MimosColors(\n" +
            names.joinToString("") {
                "                ${it.camelCase()} = Color(0xFF${colors.getValue(it).drop(1).uppercase()}),\n"
            } +
            "            )\n"
    return buildString {
        appendLine(
            "// Generated from apps/web/src/app/globals.css by generateDesignTokens. Do not edit."
        )
        appendLine("package $packageName")
        appendLine()
        appendLine("import androidx.compose.ui.graphics.Color")
        appendLine()
        appendLine("data class MimosColors(")
        names.forEach { appendLine("    val ${it.camelCase()}: Color,") }
        appendLine(") {")
        appendLine("    companion object {")
        appendLine(instance("Light", tokens.light).trimEnd())
        appendLine()
        appendLine(instance("Dark", tokens.dark).trimEnd())
        appendLine("    }")
        appendLine("}")
    }
}
