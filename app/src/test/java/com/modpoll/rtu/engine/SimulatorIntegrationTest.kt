package com.modpoll.rtu.engine

import com.modpoll.rtu.data.BuiltInProfiles
import com.modpoll.rtu.data.Framer
import com.modpoll.rtu.modbus.ModbusDeviceException
import com.modpoll.rtu.modbus.ModbusMaster
import com.modpoll.rtu.modbus.ModbusTimeoutException
import com.modpoll.rtu.modbus.SimulatedTransport
import com.modpoll.rtu.modbus.ValueCodec
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class SimulatorIntegrationTest {
    @Test
    fun readsAndWritesOverRtu() =
        runBlocking {
            val transport = SimulatedTransport(Framer.RTU, responseDelayMs = 1)
            transport.open()
            val master = ModbusMaster(transport, Framer.RTU, 1, defaultTimeoutMs = 500, defaultRetries = 0)
            try {
                val read = master.readInputRegisters(0, 2)
                val words = read.registers!!
                val voltage =
                    ValueCodec.decodeWords(
                        BuiltInProfiles
                            .demoPlc()
                            .groups
                            .first()
                            .signals
                            .first(),
                        words,
                    )
                assertTrue((voltage.numeric ?: 0.0) > 200.0)

                master.writeSingleRegister(1, 1600)
                val speed = master.readHoldingRegisters(1, 1).registers!!.single()
                assertEquals(1600, speed)

                master.writeSingleCoil(0, true)
                val coils = master.readCoils(0, 1).bits!!
                assertTrue(coils[0])
            } finally {
                transport.close()
            }
        }

    @Test
    fun readsOverAsciiFraming() =
        runBlocking {
            val transport = SimulatedTransport(Framer.ASCII, responseDelayMs = 1)
            transport.open()
            val master = ModbusMaster(transport, Framer.ASCII, 1, defaultTimeoutMs = 500, defaultRetries = 0)
            try {
                val read = master.readHoldingRegisters(0, 2)
                assertEquals(2, read.registers!!.size)
            } finally {
                transport.close()
            }
        }

    @Test
    fun illegalAddressReturnsDeviceException() =
        runBlocking {
            val transport = SimulatedTransport(Framer.RTU, responseDelayMs = 1)
            transport.open()
            val master = ModbusMaster(transport, Framer.RTU, 1, defaultTimeoutMs = 500, defaultRetries = 0)
            try {
                master.rawRequest(byteArrayOf(3, 0xFF.toByte(), 0xFF.toByte(), 0, 2))
                throw AssertionError("Expected exception response")
            } catch (expected: ModbusDeviceException) {
                assertEquals(2, expected.exceptionCode)
            } finally {
                transport.close()
            }
        }

    @Test
    fun wrongUnitTimesOut() =
        runBlocking {
            val transport = SimulatedTransport(Framer.RTU, responseDelayMs = 1)
            transport.open()
            val master = ModbusMaster(transport, Framer.RTU, 1, defaultTimeoutMs = 120, defaultRetries = 0)
            try {
                master.readHoldingRegisters(0, 1, unitId = 9)
                throw AssertionError("Expected timeout")
            } catch (expected: ModbusTimeoutException) {
                assertTrue(expected.message!!.contains("respond", ignoreCase = true))
            } finally {
                transport.close()
            }
        }

    @Test
    fun pollEngineReadsDemoProfileAndWritesSetpoint() =
        runBlocking {
            val profile = BuiltInProfiles.demoPlc()
            val transport = SimulatedTransport(Framer.RTU, responseDelayMs = 1)
            transport.open()
            val master = ModbusMaster(transport, Framer.RTU, profile.unitId, defaultTimeoutMs = 500, defaultRetries = 0)
            val engine = PollEngine(master, profile, autoDisableAfterFailures = false)
            try {
                engine.pollOnce()
                val state = engine.state.value
                assertTrue(state.values.isNotEmpty())
                val voltage = state.values.values.first { it.signal.name == "Voltage" }
                assertNotNull(voltage.measurement?.numeric)

                val group = profile.groups.first { it.objectType.label.contains("Holding") }
                val setpoint = group.signals.first { it.name == "Speed Setpoint" }
                val result = engine.writeSignal(group.id, setpoint.id, "1700")
                assertTrue(result.isSuccess)
                val updated = engine.state.value.values[setpoint.id]
                assertEquals(1700.0, updated?.measurement?.numeric)
            } finally {
                engine.shutdown()
                transport.close()
            }
        }

    @Test
    fun pollingUsesProfileUnitId() =
        runBlocking {
            val profile = BuiltInProfiles.demoPlc().copy(unitId = 2)
            val transport = SimulatedTransport(Framer.RTU, responseDelayMs = 1)
            transport.open()
            val master = ModbusMaster(transport, Framer.RTU, 1, defaultTimeoutMs = 500, defaultRetries = 0)
            val engine = PollEngine(master, profile, autoDisableAfterFailures = false)
            try {
                engine.pollOnce()
                val voltage = engine.state.value.values.values.first { it.signal.name == "Voltage" }
                assertNotNull(voltage.measurement?.numeric)
                assertTrue(engine.state.value.values.values.all { it.error == null })
            } finally {
                engine.shutdown()
                transport.close()
            }
        }

    @Test
    fun throughputIsFastEnoughForPolling() =
        runBlocking {
            val transport = SimulatedTransport(Framer.RTU, responseDelayMs = 1)
            transport.open()
            val master = ModbusMaster(transport, Framer.RTU, 1, defaultTimeoutMs = 500, defaultRetries = 0, interFrameDelayMs = 1)
            try {
                val started = System.nanoTime()
                repeat(50) { master.readHoldingRegisters(0, 10) }
                val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
                assertTrue("50 reads took $elapsedMs ms", elapsedMs < 5_000)
            } finally {
                transport.close()
            }
        }
}
