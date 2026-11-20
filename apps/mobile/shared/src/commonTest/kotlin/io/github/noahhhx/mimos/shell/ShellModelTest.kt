package io.github.noahhhx.mimos.shell

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

class ShellModelTest {
    @Test
    fun `the app opens on the kitchen with the menu closed`() {
        val shell = ShellModel()

        assertEquals(Destination.Kitchen, shell.destination.value)
        assertFalse(shell.profileOpen.value)
    }

    @Test
    fun `choosing a tab shows it`() {
        val shell = ShellModel()

        shell.go(Destination.Shopping)

        assertEquals(Destination.Shopping, shell.destination.value)
    }

    @Test
    fun `choosing from the profile menu shows it and closes the menu`() {
        val shell = ShellModel()
        shell.openProfile()
        assertTrue(shell.profileOpen.value)

        shell.go(Destination.Fuel)

        assertEquals(Destination.Fuel, shell.destination.value)
        assertFalse(shell.profileOpen.value)
    }

    @Test
    fun `closing the menu keeps the destination`() {
        val shell = ShellModel()
        shell.go(Destination.Plan)
        shell.openProfile()

        shell.closeProfile()

        assertEquals(Destination.Plan, shell.destination.value)
        assertFalse(shell.profileOpen.value)
    }

    @Test
    fun `resetting returns to the kitchen so the next person starts there`() {
        val shell = ShellModel()
        shell.go(Destination.YourData)
        shell.openProfile()

        shell.reset()

        assertEquals(Destination.Kitchen, shell.destination.value)
        assertFalse(shell.profileOpen.value)
    }

    @Test
    fun `the tabs and the profile menu are the web's, in its order`() {
        assertEquals(
            listOf("Kitchen", "Recipes", "Plan", "Shopping", "Log"),
            Destination.tabs.map { it.label },
        )
        assertEquals(
            listOf("Household", "Plugins", "Fuel", "Your data", "Settings"),
            Destination.profileMenu.map { it.label },
        )
    }

    @Test
    fun `member since reads as a date in the given time zone`() {
        val since = Instant.parse("2026-11-18T23:30:00Z")

        assertEquals("Member since 18 November 2026", memberSinceText(since, TimeZone.UTC))
        assertEquals(
            "Member since 19 November 2026",
            memberSinceText(since, TimeZone.of("Pacific/Auckland")),
        )
    }
}
