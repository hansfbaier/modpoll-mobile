package io.github.hansfbaier.modpoll.engine

import io.github.hansfbaier.modpoll.data.BuiltInProfiles
import io.github.hansfbaier.modpoll.data.Framer
import io.github.hansfbaier.modpoll.data.ObjectType
import io.github.hansfbaier.modpoll.modbus.ModbusMaster
import io.github.hansfbaier.modpoll.modbus.SimulatedTransport
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScopeEngineTest {
    @Test
    fun samplesSelectedHoldingRegister() =
        runBlocking {
            val profile = BuiltInProfiles.demoPlc()
            val group = profile.groups.first { it.objectType == ObjectType.HOLDING_REGISTER }
            val signal = group.signals.first { it.name == "Speed Setpoint" }
            val transport = SimulatedTransport(Framer.RTU, responseDelayMs = 1)
            transport.open()
            val master =
                ModbusMaster(transport, Framer.RTU, profile.unitId, defaultTimeoutMs = 500, defaultRetries = 0, interFrameDelayMs = 1)
            val scope = ScopeEngine(master, profile, setOf(signal.id), intervalMs = 20, bufferLimit = 200)
            try {
                scope.start()
                delay(260)
                val running = scope.state.value
                assertTrue("expected several reads, got ${running.reads}", running.reads >= 3)
                assertTrue(running.running)
                val series = running.series.single()
                assertTrue(series.points.size >= 3)
                assertTrue(series.latest != null)
                assertTrue(running.actualIntervalMs > 0)

                scope.stop()
                val stopped = scope.state.value
                assertFalse(stopped.running)
                assertTrue(stopped.reads >= 3)
            } finally {
                scope.shutdown()
                transport.close()
            }
        }

    @Test
    fun emptySelectionReportsAnError() =
        runBlocking {
            val profile = BuiltInProfiles.demoPlc()
            val transport = SimulatedTransport(Framer.RTU, responseDelayMs = 1)
            transport.open()
            val master = ModbusMaster(transport, Framer.RTU, profile.unitId, defaultTimeoutMs = 500, defaultRetries = 0)
            val scope = ScopeEngine(master, profile, emptySet(), intervalMs = 20)
            try {
                scope.start()
                val state = scope.state.value
                assertFalse(state.running)
                assertTrue(state.lastError?.contains("Select") == true)
            } finally {
                scope.shutdown()
                transport.close()
            }
        }
}
