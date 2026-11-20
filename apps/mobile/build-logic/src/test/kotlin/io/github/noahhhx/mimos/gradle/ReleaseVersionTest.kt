package io.github.noahhhx.mimos.gradle

import io.github.noahhhx.mimos.gradle.ReleaseVersion.Stage
import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ReleaseVersionTest {
    @Test
    fun `reads finals, candidates, and snapshots`() {
        assertEquals(ReleaseVersion(1, 2, 3, Stage.Final), ReleaseVersion.parse("1.2.3"))
        assertEquals(
            ReleaseVersion(1, 2, 3, Stage.Candidate(4)),
            ReleaseVersion.parse("1.2.3-rc.4"),
        )
        assertEquals(
            ReleaseVersion(0, 1, 0, Stage.Snapshot),
            ReleaseVersion.parse("0.1.0-SNAPSHOT"),
        )
    }

    @Test
    fun `refuses what a version code cannot hold`() {
        for (text in
            listOf(
                "",
                "v1.2.3",
                "1.2",
                "1.100.0",
                "1.0.100",
                "1.0.0-rc.0",
                "1.0.0-rc.99",
                "1.0.0-beta.1",
                "1.0.0-rc1",
                "2100.0.0",
                "1.0.0+build",
            )) {
            assertThrows<IllegalArgumentException>(text) { ReleaseVersion.parse(text) }
        }
    }

    @Test
    fun `the code is the published formula`() {
        assertEquals(1_020_399, ReleaseVersion.parse("1.2.3").versionCode)
        assertEquals(1_020_304, ReleaseVersion.parse("1.2.3-rc.4").versionCode)
        assertEquals(10_000, ReleaseVersion.parse("0.1.0-SNAPSHOT").versionCode)
        assertEquals(2_099_999_999, ReleaseVersion.parse("2099.99.99").versionCode)
    }

    @Test
    fun `codes grow in release order, so each install updates the one before`() {
        val order =
            listOf(
                "0.1.0-SNAPSHOT",
                "0.1.0-rc.1",
                "0.1.0-rc.2",
                "0.1.0-rc.10",
                "0.1.0-rc.98",
                "0.1.0",
                "0.1.1-SNAPSHOT",
                "0.1.1",
                "0.1.99",
                "0.2.0-rc.1",
                "0.99.99",
                "1.0.0-SNAPSHOT",
                "1.0.0",
                "10.0.0",
            )
        val codes = order.map { ReleaseVersion.parse(it).versionCode }
        assertEquals(codes.sorted(), codes)
        assertEquals(codes.size, codes.toSet().size)
    }

    @Test
    fun `the app takes the project's version from the root pom, not its parent's`() {
        val pom =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <parent>
                    <artifactId>spring-boot-starter-parent</artifactId>
                    <version>4.1.1</version>
                </parent>
                <artifactId>mimos-parent</artifactId>
                <version> 0.3.0-SNAPSHOT </version>
                <properties><version>9.9.9</version></properties>
            </project>
            """
                .trimIndent()
        assertEquals("0.3.0-SNAPSHOT", pomVersion(pom))
    }

    @Test
    fun `the repository's own pom names a version the app can release under`() {
        val pom =
            generateSequence(File("").absoluteFile) { it.parentFile }
                .map { File(it, "pom.xml") }
                .first { it.isFile }
        ReleaseVersion.parse(pomVersion(pom.readText()))
    }
}
