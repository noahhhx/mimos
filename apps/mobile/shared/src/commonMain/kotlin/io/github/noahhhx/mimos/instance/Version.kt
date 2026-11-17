package io.github.noahhhx.mimos.instance

/** A `MAJOR.MINOR.PATCH` version, as the server and the app report them. */
data class Version(val major: Int, val minor: Int, val patch: Int) : Comparable<Version> {
    override fun compareTo(other: Version): Int =
        compareValuesBy(this, other, Version::major, Version::minor, Version::patch)

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        private val pattern = Regex("""v?(\d+)\.(\d+)\.(\d+)([-+].*)?""")

        /** Reads `1.2.3`, also with a leading `v` or a suffix such as `-SNAPSHOT`. */
        fun parse(text: String): Version? {
            val (major, minor, patch) =
                pattern.matchEntire(text.trim())?.destructured ?: return null
            return Version(
                major.toIntOrNull() ?: return null,
                minor.toIntOrNull() ?: return null,
                patch.toIntOrNull() ?: return null,
            )
        }
    }
}

/** This app's version, from the build's `mimos.version`. */
val APP_VERSION: Version = requireNotNull(Version.parse(APP_VERSION_NAME)) { APP_VERSION_NAME }

/** The oldest server the app talks to: the first that answers `getInstance`. */
val MIN_SERVER_VERSION: Version = Version(0, 0, 0)
