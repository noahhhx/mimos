package io.github.noahhhx.mimos.instance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VersionTest {
    @Test
    fun `versions read leniently`() {
        assertEquals(Version(1, 2, 3), Version.parse("1.2.3"))
        assertEquals(Version(0, 1, 0), Version.parse("0.1.0-SNAPSHOT"))
        assertEquals(Version(2, 0, 10), Version.parse(" v2.0.10+build.7 "))
        for (text in listOf("", "1.2", "1.2.x", "one.two.three", "1.2.3.4", "99999999999.0.0")) {
            assertNull(Version.parse(text), text)
        }
    }

    @Test
    fun `versions compare by number, not text`() {
        assertTrue(Version(0, 10, 0) > Version(0, 9, 9))
        assertTrue(Version(1, 0, 0) > Version(0, 99, 99))
        assertTrue(Version(1, 2, 3) < Version(1, 2, 10))
        assertEquals("1.2.3", Version(1, 2, 3).toString())
    }

    @Test
    fun `the app knows its own version`() {
        assertTrue(APP_VERSION >= Version(0, 0, 0))
    }
}
