package io.github.noahhhx.mimos.connect

import io.github.noahhhx.mimos.instance.Discovery
import io.github.noahhhx.mimos.instance.InsecureAddress
import io.github.noahhhx.mimos.instance.Instance
import io.github.noahhhx.mimos.instance.InstanceConfig
import io.github.noahhhx.mimos.instance.InstanceStore
import io.github.noahhhx.mimos.instance.InvalidAddress
import io.github.noahhhx.mimos.instance.SignupPolicy
import io.github.noahhhx.mimos.instance.Version
import io.github.noahhhx.mimos.instance.WebAddress
import io.github.noahhhx.mimos.instance.parseWebAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

private class MemoryInstanceStore(var instance: Instance? = null) : InstanceStore {
    override fun load() = instance

    override fun save(instance: Instance) {
        this.instance = instance
    }

    override fun clear() {
        instance = null
    }
}

private val instance =
    Instance(
        InstanceConfig(
            webUrl = parseWebAddress("mimos.example.com") as WebAddress,
            apiUrl = "https://api.mimos.example.com",
            oidcAuthority = "https://id.mimos.example.com/realms/mimos",
            oidcClientId = "mimos-web",
            signup = SignupPolicy.OPEN,
        ),
        serverVersion = Version(1, 0, 0),
    )

class ConnectModelTest {
    @Test
    fun `connecting shows progress, then the instance, and keeps it`() = runTest {
        val store = MemoryInstanceStore()
        val answer = CompletableDeferred<Discovery>()
        val model = ConnectModel(store) { answer.await() }
        model.edit("mimos.example.com")
        launch { model.connect() }
        runCurrent()
        assertEquals(ConnectState.Connecting("mimos.example.com"), model.state.value)
        answer.complete(Discovery.Connected(instance))
        runCurrent()
        assertEquals(ConnectState.Connected(instance), model.state.value)
        assertEquals(instance, store.instance)
    }

    @Test
    fun `a failure keeps the address to fix and connect again`() = runTest {
        val outcomes = ArrayDeque(listOf(InsecureAddress, Discovery.Connected(instance)))
        val tried = mutableListOf<String>()
        val model =
            ConnectModel(MemoryInstanceStore()) {
                tried += it
                outcomes.removeFirst()
            }
        model.edit("http://mimos.example.com")
        model.connect()
        assertEquals(
            ConnectState.Failed("http://mimos.example.com", InsecureAddress),
            model.state.value,
        )
        model.edit("https://mimos.example.com")
        model.connect()
        assertEquals(ConnectState.Connected(instance), model.state.value)
        assertEquals(listOf("http://mimos.example.com", "https://mimos.example.com"), tried)
    }

    @Test
    fun `a kept instance opens connected, and disconnecting forgets it`() = runTest {
        val store = MemoryInstanceStore(instance)
        val model = ConnectModel(store) { error("not called") }
        assertEquals(ConnectState.Connected(instance), model.state.value)
        model.disconnect()
        assertNull(store.instance)
        assertEquals(ConnectState.Editing("https://mimos.example.com"), model.state.value)
    }

    @Test
    fun `each failure has its own message`() {
        val failures =
            listOf(
                InvalidAddress,
                InsecureAddress,
                Discovery.Unreachable,
                Discovery.NotMimos,
                Discovery.ServerTooOld(Version(0, 1, 0)),
                Discovery.AppTooOld(Version(2, 0, 0)),
            )
        val messages = failures.map { it.message() }
        assertEquals(messages.size, messages.toSet().size)
        assertEquals(listOf(false), messages.map { '\u2014' in it }.distinct())
    }
}
