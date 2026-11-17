package io.github.noahhhx.mimos.instance

import kotlin.test.Test
import kotlin.test.assertEquals

class WebAddressTest {
    @Test
    fun `an address reads as its https origin`() {
        val cases =
            mapOf(
                "mimos.example.com" to "https://mimos.example.com",
                "  https://Mimos.Example.com/app/plan?week=1#x " to "https://mimos.example.com",
                "https://mimos.example.com/" to "https://mimos.example.com",
                "https://mimos.example.com:443" to "https://mimos.example.com",
                "mimos.example.com:8443/app" to "https://mimos.example.com:8443",
                "HTTPS://192.168.1.20" to "https://192.168.1.20",
                "localhost:3000" to "https://localhost:3000",
            )
        for ((input, origin) in cases) assertEquals(
            WebAddress(origin),
            parseWebAddress(input),
            input,
        )
    }

    @Test
    fun `plain http is only for this device's own loopback`() {
        assertEquals(WebAddress("http://localhost:3000"), parseWebAddress("http://localhost:3000/"))
        assertEquals(WebAddress("http://127.0.0.1:3000"), parseWebAddress("http://127.0.0.1:3000"))
        for (input in
            listOf(
                "http://mimos.example.com",
                "http://192.168.1.20:3000",
                "http://localhost.example.com",
            )) {
            assertEquals(InsecureAddress, parseWebAddress(input), input)
        }
    }

    @Test
    fun `anything else is not a web address`() {
        val garbage =
            listOf(
                "",
                "   ",
                "not a web address",
                "ftp://mimos.example.com",
                "https://",
                "https://mimos_example.com",
                "https://mimos..example.com",
                "https://mimos.example.com:99999",
                "https://me@mimos.example.com",
                "mailto:me@example.com",
            )
        for (input in garbage) assertEquals(InvalidAddress, parseWebAddress(input), input)
    }
}
