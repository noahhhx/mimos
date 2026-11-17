package io.github.noahhhx.mimos.instance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InstanceStorageTest {
    private val instance =
        Instance(
            InstanceConfig(
                webUrl = WebAddress("https://mimos.example.com"),
                apiUrl = "https://api.mimos.example.com",
                oidcAuthority = "https://id.mimos.example.com/realms/mimos",
                oidcClientId = "mimos-web",
                signup = SignupPolicy.OPEN,
            ),
            serverVersion = Version(1, 2, 3),
        )

    @Test
    fun `a stored instance reads back as itself`() {
        assertEquals(instance, decodeInstance(instance.encode()))
    }

    @Test
    fun `anything else stored reads as no instance`() {
        val bad = instance.encode().replace("https://mimos.example.com", "http://mimos.example.com")
        for (text in listOf("", "{}", "not json", bad)) assertNull(decodeInstance(text), text)
    }
}
