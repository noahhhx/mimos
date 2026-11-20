package io.github.noahhhx.mimos.ui

/**
 * The two looks of Evening Kitchen. Light is the default; dark applies only when chosen (ADR-0021).
 */
enum class ThemeChoice(
    /** What Keycloak's pages read as `mimos_theme`. */
    val key: String,
    val colors: MimosColors,
) {
    Light("light", MimosColors.Light),
    Dark("dark", MimosColors.Dark);

    fun toggled(): ThemeChoice =
        when (this) {
            Light -> Dark
            Dark -> Light
        }

    companion object {
        fun fromKey(key: String?): ThemeChoice = entries.firstOrNull { it.key == key } ?: Light
    }
}
