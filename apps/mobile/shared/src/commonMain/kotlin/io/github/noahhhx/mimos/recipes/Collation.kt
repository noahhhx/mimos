package io.github.noahhhx.mimos.recipes

/**
 * Orders text as the web does, `Intl.Collator(undefined, { sensitivity: "base" })`: letters count
 * without their case or accents, and a space or a mark sorts before a digit, which sorts before a
 * letter. `java.text.Collator` is not used because it ignores spaces and marks at this strength
 * ("Apple tart" would follow "Applesauce"), and the unit tests run it on a desktop JVM, where it
 * differs from the phone's. The shared cases in `libraries/test-cases/recipe-order.json` pin the
 * rules below, and the web's tests run the same cases through `Intl`.
 *
 * It is exact for Latin text, which is what recipe titles and tags are. Other scripts compare by
 * their lower-case code points, which is alphabetical for the common ones.
 */
internal fun collationKey(text: String): IntArray {
    val key = ArrayList<Int>(text.length)
    for (char in text) {
        val folded = BASE_LETTERS[char.lowercaseChar()]
        if (folded != null) {
            folded.forEach { key += primary(it) }
        } else {
            key += primary(char.lowercaseChar())
        }
    }
    return key.toIntArray()
}

internal fun compareKeys(a: IntArray, b: IntArray): Int {
    for (i in 0 until minOf(a.size, b.size)) {
        if (a[i] != b[i]) return a[i].compareTo(b[i])
    }
    return a.size.compareTo(b.size)
}

private const val SPACE = 0
private const val MARK = 1 shl 24
private const val DIGIT = 2 shl 24
private const val LETTER = 3 shl 24

private fun primary(char: Char): Int =
    when {
        char.isWhitespace() -> SPACE or char.code
        char.isDigit() -> DIGIT or char.code
        char.isLetter() -> LETTER or char.code
        else ->
            MARK or (MARK_ORDER.indexOf(char).takeIf { it >= 0 } ?: (MARK_ORDER.length + char.code))
    }

/** The ASCII marks in the order the collation gives them, which `Intl.Collator` reports. */
private const val MARK_ORDER = "_-,;:!?.'\"()[]{}@*/\\&#%`^+<=>|~$"

/** A letter's base letters, for the accented letters of Latin-1 and Latin Extended-A. */
private val BASE_LETTERS: Map<Char, String> = buildMap {
    for ((base, accented) in
        listOf(
            "a" to "àáâãäåāăą",
            "c" to "çćĉċč",
            "d" to "ďđð",
            "e" to "èéêëēĕėęě",
            "g" to "ĝğġģ",
            "h" to "ĥħ",
            "i" to "ìíîïĩīĭį",
            "j" to "ĵ",
            "k" to "ķ",
            "l" to "ĺļľŀł",
            "n" to "ñńņň",
            "o" to "òóôõöøōŏő",
            "r" to "ŕŗř",
            "s" to "śŝşš",
            "t" to "ţť",
            "u" to "ùúûüũūŭůűų",
            "w" to "ŵ",
            "y" to "ýÿŷ",
            "z" to "źżž",
            "ae" to "æ",
            "oe" to "œ",
            "ss" to "ß",
        )) {
        for (char in accented) put(char, base)
    }
}
