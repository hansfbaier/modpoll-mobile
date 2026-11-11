package com.modpoll.rtu.engine

import com.modpoll.rtu.data.DataType
import com.modpoll.rtu.data.DeviceProfile
import com.modpoll.rtu.data.ObjectType
import com.modpoll.rtu.data.Signal
import com.modpoll.rtu.data.SignalGroup
import com.modpoll.rtu.modbus.EncodeResult
import com.modpoll.rtu.modbus.Measurement
import com.modpoll.rtu.modbus.ModbusDeviceException
import com.modpoll.rtu.modbus.ModbusException
import com.modpoll.rtu.modbus.ModbusMaster
import com.modpoll.rtu.modbus.ValueCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class SamplePoint(
    val timeMs: Long,
    val value: Double,
)

data class SignalReading(
    val signal: Signal,
    val groupId: String,
    val measurement: Measurement? = null,
    val updatedAt: Long = 0,
    val changedAt: Long = 0,
    val error: String? = null,
    val history: List<SamplePoint> = emptyList(),
)

data class GroupState(
    val group: SignalGroup,
    val lastPollAt: Long = 0,
    val lastDurationMs: Long = 0,
    val requests: Long = 0,
    val failures: Long = 0,
    val consecutiveFailures: Int = 0,
    val error: String? = null,
    val autoDisabled: Boolean = false,
)

data class PollSnapshot(
    val running: Boolean = false,
    val values: Map<String, SignalReading> = emptyMap(),
    val groups: Map<String, GroupState> = emptyMap(),
    val lastCycleAt: Long = 0,
    val cycleMs: Long = 0,
    val totalRequests: Long = 0,
    val totalFailures: Long = 0,
    val lastError: String? = null,
) {
    val successRate: Double
        get() {
            val total = totalRequests
            return if (total <= 0) 0.0 else (1.0 - totalFailures.toDouble() / total).coerceIn(0.0, 1.0)
        }
}

/**
 * Polls a profile's groups over one Modbus master.
 *
 * Each group is read with its own function code and interval. Signals are decoded from the
 * group's contiguous read block, so a screen of 20 values typically costs one request.
 */
class PollEngine(
    private val master: ModbusMaster,
    initialProfile: DeviceProfile,
    private val autoDisableAfterFailures: Boolean,
    private val onTransportFailure: (Throwable) -> Unit = {},
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loopJob: Job? = null
    private val writeMutex = Mutex()
    private val pollMutex = Mutex()
    private val stateLock = Any()
    private val _state = MutableStateFlow(PollSnapshot())

    @Volatile
    private var profile: DeviceProfile = initialProfile

    @Volatile
    private var paused = false

    private var readings = LinkedHashMap<String, SignalReading>()
    private var groupStates = LinkedHashMap<String, GroupState>()
    private var totalRequests = 0L
    private var totalFailures = 0L
    private var lastError: String? = null
    private var lastCycleAt = 0L
    private var cycleMs = 0L

    val state: StateFlow<PollSnapshot> = _state.asStateFlow()

    init {
        rebuild(initialProfile)
        publish()
    }

    fun start() {
        if (loopJob?.isActive == true) return
        paused = false
        loopJob = scope.launch { loop() }
        publish()
    }

    fun pause() {
        paused = true
        publish()
    }

    fun resume() {
        if (loopJob?.isActive != true) {
            start()
        } else {
            paused = false
            publish()
        }
    }

    fun stop() {
        loopJob?.cancel()
        loopJob = null
        publish()
    }

    fun shutdown() {
        stop()
        scope.cancel()
    }

    fun updateProfile(next: DeviceProfile) {
        profile = next
        rebuild(next)
        publish()
    }

    /** Single cycle regardless of intervals. Used by "Read once". */
    suspend fun pollOnce() {
        pollCycle(force = true)
    }

    fun resetAutoDisabled(groupId: String) {
        val current = groupStates[groupId] ?: return
        groupStates[groupId] = current.copy(autoDisabled = false, consecutiveFailures = 0, error = null)
        publish()
    }

    suspend fun writeSignal(
        groupId: String,
        signalId: String,
        input: String,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            writeMutex.withLock {
                val group =
                    profile.groups.firstOrNull { it.id == groupId }
                        ?: return@withLock Result.failure(ModbusException("Group not found"))
                val signal =
                    group.signals.firstOrNull { it.id == signalId }
                        ?: return@withLock Result.failure(ModbusException("Signal not found"))
                if (!signal.writable) return@withLock Result.failure(ModbusException("Signal is read-only"))
                try {
                    when {
                        group.objectType.isBitTable -> {
                            if (group.objectType != ObjectType.COIL) {
                                return@withLock Result.failure(ModbusException("Discrete inputs are read-only"))
                            }
                            val on =
                                ValueCodec.parseBoolean(input)
                                    ?: return@withLock Result.failure(ModbusException("Expected 0/1 or ON/OFF"))
                            if (signal.dataType == DataType.BOOL) {
                                master.writeSingleCoil(signal.address, on)
                            } else {
                                return@withLock Result.failure(
                                    ModbusException("Use the bit switches to write a coil group"),
                                )
                            }
                        }

                        signal.dataType == DataType.BOOL -> {
                            if (group.objectType != ObjectType.HOLDING_REGISTER) {
                                return@withLock Result.failure(ModbusException("Only holding registers are writable"))
                            }
                            val on =
                                ValueCodec.parseBoolean(input)
                                    ?: return@withLock Result.failure(ModbusException("Expected 0/1 or ON/OFF"))
                            val current = master.readHoldingRegisters(signal.address, 1).registers?.firstOrNull() ?: 0
                            val bit = (signal.bit ?: 0).coerceIn(0, 15)
                            val updated = if (on) current or (1 shl bit) else current and (1 shl bit).inv()
                            master.writeSingleRegister(signal.address, updated and 0xFFFF)
                        }

                        else -> {
                            val encoded = ValueCodec.encodeRegister(signal, input)
                            when (encoded) {
                                is EncodeResult.Error -> {
                                    return@withLock Result.failure(ModbusException(encoded.message))
                                }

                                is EncodeResult.Ok -> {
                                    if (encoded.words.size == 1) {
                                        master.writeSingleRegister(signal.address, encoded.words[0])
                                    } else {
                                        master.writeMultipleRegisters(signal.address, encoded.words)
                                    }
                                }
                            }
                        }
                    }
                    pollGroup(group, force = true)
                    Result.success(Unit)
                } catch (failure: Exception) {
                    lastError = failure.message
                    publish()
                    Result.failure(failure)
                }
            }
        }

    /** Toggle one bit inside a BOOL8 / BOOL16 coil signal. */
    suspend fun writeCoilBit(
        groupId: String,
        signalId: String,
        bitIndex: Int,
        on: Boolean,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            writeMutex.withLock {
                val group =
                    profile.groups.firstOrNull { it.id == groupId }
                        ?: return@withLock Result.failure(ModbusException("Group not found"))
                if (group.objectType != ObjectType.COIL) {
                    return@withLock Result.failure(ModbusException("Only coils are writable"))
                }
                val signal =
                    group.signals.firstOrNull { it.id == signalId }
                        ?: return@withLock Result.failure(ModbusException("Signal not found"))
                if (!signal.writable) return@withLock Result.failure(ModbusException("Signal is read-only"))
                if (bitIndex !in 0 until signal.bitWidth) {
                    return@withLock Result.failure(ModbusException("Bit index out of range"))
                }
                try {
                    master.writeSingleCoil(signal.address + bitIndex, on)
                    pollGroup(group, force = true)
                    Result.success(Unit)
                } catch (failure: Exception) {
                    Result.failure(failure)
                }
            }
        }

    private suspend fun loop() {
        while (currentCoroutineContext().isActive) {
            if (!paused) {
                pollCycle(force = false)
            }
            delay(LOOP_TICK_MS)
        }
    }

    private suspend fun pollCycle(force: Boolean) {
        val cycleStart = System.currentTimeMillis()
        val groups = profile.groups
        var didWork = false
        for (group in groups) {
            if (!group.enabled) continue
            val state = groupStates[group.id] ?: continue
            if (state.autoDisabled) continue
            val now = System.currentTimeMillis()
            if (!force && now - state.lastPollAt < group.intervalMs) continue
            pollGroup(group, force)
            didWork = true
        }
        if (didWork || force) {
            lastCycleAt = System.currentTimeMillis()
            cycleMs = lastCycleAt - cycleStart
            publish()
        }
    }

    private suspend fun pollGroup(
        group: SignalGroup,
        force: Boolean,
    ) = pollMutex.withLock {
        pollGroupLocked(group, force)
    }

    private suspend fun pollGroupLocked(
        group: SignalGroup,
        force: Boolean,
    ) {
        val started = System.currentTimeMillis()
        val blocks = splitBlocks(group)
        var requestCount = 0
        var failed = false
        var failureMessage: String? = null

        for (range in blocks) {
            val quantity = range.last - range.first + 1
            try {
                requestCount++
                when (group.objectType) {
                    ObjectType.COIL -> {
                        val result = master.readCoils(range.first, quantity)
                        applyBits(group, result.bits ?: BooleanArray(0), range.first, started)
                    }

                    ObjectType.DISCRETE_INPUT -> {
                        val result = master.readDiscreteInputs(range.first, quantity)
                        applyBits(group, result.bits ?: BooleanArray(0), range.first, started)
                    }

                    ObjectType.INPUT_REGISTER -> {
                        val result = master.readInputRegisters(range.first, quantity)
                        applyRegisters(group, result.registers ?: IntArray(0), range.first, started)
                    }

                    ObjectType.HOLDING_REGISTER -> {
                        val result = master.readHoldingRegisters(range.first, quantity)
                        applyRegisters(group, result.registers ?: IntArray(0), range.first, started)
                    }
                }
            } catch (failure: Exception) {
                failed = true
                failureMessage = failure.message ?: failure.javaClass.simpleName
                markGroupError(group, failureMessage, started, requestCount)
                if (failure is ModbusException && failure.message?.startsWith("Serial") == true) {
                    onTransportFailure(failure)
                }
                break
            }
        }

        val now = System.currentTimeMillis()
        synchronized(stateLock) {
            val previous = groupStates[group.id]
            val consecutive = if (failed) (previous?.consecutiveFailures ?: 0) + 1 else 0
            val disabled = autoDisableAfterFailures && consecutive >= AUTO_DISABLE_THRESHOLD
            totalRequests += requestCount
            if (failed) totalFailures++
            lastError = failureMessage ?: lastError
            groupStates[group.id] =
                (previous ?: GroupState(group)).copy(
                    lastPollAt = now,
                    lastDurationMs = now - started,
                    requests = (previous?.requests ?: 0) + requestCount,
                    failures = (previous?.failures ?: 0) + if (failed) 1 else 0,
                    consecutiveFailures = consecutive,
                    error = failureMessage,
                    autoDisabled = disabled,
                )
        }
        publish()
    }

    private fun applyRegisters(
        group: SignalGroup,
        registers: IntArray,
        blockStart: Int,
        now: Long,
    ) {
        group.signals.forEach { signal ->
            if (signal.address < blockStart) return@forEach
            if (signal.address + signal.registerSpan - 1 >= blockStart + registers.size) return@forEach
            val measurement = ValueCodec.decodeRegister(signal, registers, blockStart)
            updateReading(group, signal, measurement, now)
        }
    }

    private fun applyBits(
        group: SignalGroup,
        bits: BooleanArray,
        blockStart: Int,
        now: Long,
    ) {
        group.signals.forEach { signal ->
            if (signal.address < blockStart) return@forEach
            if (signal.address + signal.bitWidth - 1 >= blockStart + bits.size) return@forEach
            val measurement = ValueCodec.decodeCoil(signal, bits, blockStart)
            updateReading(group, signal, measurement, now)
        }
    }

    private fun updateReading(
        group: SignalGroup,
        signal: Signal,
        measurement: Measurement,
        now: Long,
    ) = synchronized(stateLock) {
        val previous = readings[signal.id]
        val changed =
            previous?.measurement?.formatted != measurement.formatted ||
                previous?.measurement?.numeric != measurement.numeric
        val numeric = measurement.numeric
        val history =
            if (numeric != null) {
                (previous?.history.orEmpty() + SamplePoint(now, numeric)).takeLast(HISTORY_LIMIT)
            } else {
                previous?.history.orEmpty()
            }
        readings[signal.id] =
            SignalReading(
                signal = signal,
                groupId = group.id,
                measurement = measurement,
                updatedAt = now,
                changedAt = if (changed) now else previous?.changedAt ?: now,
                error = null,
                history = history,
            )
    }

    private fun markGroupError(
        group: SignalGroup,
        message: String?,
        now: Long,
        requests: Int,
    ) = synchronized(stateLock) {
        group.signals.forEach { signal ->
            val previous = readings[signal.id]
            readings[signal.id] =
                SignalReading(
                    signal = signal,
                    groupId = group.id,
                    measurement = previous?.measurement,
                    updatedAt = previous?.updatedAt ?: 0,
                    changedAt = previous?.changedAt ?: 0,
                    error = message ?: "Read failed",
                    history = previous?.history.orEmpty(),
                )
        }
    }

    private fun rebuild(next: DeviceProfile) = synchronized(stateLock) {
        val newReadings = LinkedHashMap<String, SignalReading>()
        next.groups.forEach { group ->
            group.signals.forEach { signal ->
                newReadings[signal.id] =
                    readings[signal.id]?.copy(signal = signal, groupId = group.id)
                        ?: SignalReading(signal = signal, groupId = group.id)
            }
        }
        readings = newReadings
        val newGroups = LinkedHashMap<String, GroupState>()
        next.groups.forEach { group ->
            newGroups[group.id] = groupStates[group.id]?.copy(group = group) ?: GroupState(group)
        }
        groupStates = newGroups
    }

    private fun publish() {
        val snapshot = synchronized(stateLock) {
            PollSnapshot(
                running = loopJob?.isActive == true && !paused,
                values = readings.toMap(),
                groups = groupStates.toMap(),
                lastCycleAt = lastCycleAt,
                cycleMs = cycleMs,
                totalRequests = totalRequests,
                totalFailures = totalFailures,
                lastError = lastError,
            )
        }
        _state.value = snapshot
    }

    private fun splitBlocks(group: SignalGroup): List<IntRange> {
        val limit = group.objectType.maximumReadQuantity
        val out = mutableListOf<IntRange>()
        var offset = 0
        while (offset < group.count) {
            val size = minOf(limit, group.count - offset)
            out += (group.start + offset) until (group.start + offset + size)
            offset += size
        }
        return out
    }

    companion object {
        private const val LOOP_TICK_MS = 50L
        private const val HISTORY_LIMIT = 720
        private const val AUTO_DISABLE_THRESHOLD = 3
    }
}
