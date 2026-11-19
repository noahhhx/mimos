package io.github.noahhhx.mimos.gradle

import javax.xml.parsers.DocumentBuilderFactory
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.w3c.dom.Element

/**
 * The version server and app share (ADR-0029): `MAJOR.MINOR.PATCH`, as a final release, a release
 * candidate (`-rc.N`), or a snapshot (`-SNAPSHOT`, every build that is not a tag's).
 */
data class ReleaseVersion(val major: Int, val minor: Int, val patch: Int, val stage: Stage) {
    sealed interface Stage {
        data object Snapshot : Stage

        data class Candidate(val number: Int) : Stage

        data object Final : Stage
    }

    /**
     * Android's version code, which only grows across the installs a phone accepts: `major *
     * 1_000_000 + minor * 10_000 + patch * 100 + pre`, where `pre` is 0 for a snapshot, the
     * candidate's number, or 99 for the release, so each sorts before the next.
     */
    val versionCode: Int
        get() {
            val pre =
                when (stage) {
                    Stage.Snapshot -> 0
                    is Stage.Candidate -> stage.number
                    Stage.Final -> 99
                }
            return major * 1_000_000 + minor * 10_000 + patch * 100 + pre
        }

    companion object {
        private val pattern =
            Regex("""(\d{1,4})\.(\d{1,2})\.(\d{1,2})(?:-rc\.(\d{1,2})|(-SNAPSHOT))?""")

        /** Google Play's ceiling, which installers share: 2,100,000,000. */
        private const val MAX_MAJOR = 2099

        /** Reads a version the build may release under, or says why it cannot. */
        fun parse(text: String): ReleaseVersion {
            val match =
                pattern.matchEntire(text)
                    ?: throw IllegalArgumentException(
                        "Mimos version \"$text\" is not MAJOR.MINOR.PATCH, -rc.N, or -SNAPSHOT, " +
                            "with MINOR and PATCH at most 99"
                    )
            val (major, minor, patch, candidate, snapshot) = match.destructured
            val stage =
                when {
                    snapshot.isNotEmpty() -> Stage.Snapshot
                    candidate.isNotEmpty() -> Stage.Candidate(candidate.toInt())
                    else -> Stage.Final
                }
            require(major.toInt() <= MAX_MAJOR) {
                "Mimos version \"$text\": MAJOR is at most $MAX_MAJOR"
            }
            require(stage !is Stage.Candidate || stage.number in 1..98) {
                "Mimos version \"$text\": a release candidate is numbered 1 to 98"
            }
            return ReleaseVersion(major.toInt(), minor.toInt(), patch.toInt(), stage)
        }
    }
}

/**
 * The version this build is: `-Pmimos.version`, which CI sets from a release tag, or else the
 * backend's, from the repository's root `pom.xml`, so app and server cannot drift.
 */
fun Project.mimosVersion(): Provider<String> =
    providers
        .gradleProperty("mimos.version")
        .orElse(
            providers
                .fileContents(rootProject.layout.projectDirectory.file("../../pom.xml"))
                .asText
                .map(::pomVersion)
        )

/** The project's own `<version>`, not its parent's. */
internal fun pomVersion(pom: String): String {
    val document =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pom.byteInputStream())
    val project = document.documentElement
    val version =
        (0 until project.childNodes.length)
            .map(project.childNodes::item)
            .filterIsInstance<Element>()
            .firstOrNull { it.tagName == "version" }
    return requireNotNull(version) { "the root pom.xml names no <version>" }.textContent.trim()
}
