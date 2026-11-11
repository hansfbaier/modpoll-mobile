package io.github.hansfbaier.modpoll.engine

import io.github.hansfbaier.modpoll.data.DataType
import io.github.hansfbaier.modpoll.data.DeviceProfile
import io.github.hansfbaier.modpoll.data.ObjectType
import io.github.hansfbaier.modpoll.data.Signal
import io.github.hansfbaier.modpoll.data.SignalGroup
import io.github.hansfbaier.modpoll.modbus.ModbusMaster
import io.github.hansfbaier.modpoll.modbus.ValueCodec
import kotlinx.coroutines.CancellationException
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
import java.util.ArrayDeque

data class ScopePoint(
    val timeMs: Long,
    val value: Double,
)

data class ScopeSeries(
    val signalId: String,
    val name: String,
    val unit: String,
    val address: Int,
    val groupId: String,
    val groupName: String,
    val points: List<ScopePoint>,
    val latest: Double?,
    val latestFormatted: String,
)

data class ScopeSnapshot(
    val running: Boolean = false,
    val intervalMs: Long = 50,
    val actualIntervalMs: Long = 0,
    val reads: Long = 0,
    val errors: Long = 0,
    val lastError: String? = null,
    val series: List<ScopeSeries> = emptyList(),
    val lastSampleAt: Long = 0,
    val bufferLimit: Int = 3000,
) {
    val hertz: Double
        get() = if (actualIntervalMs <= 0) 0.0 else 1000.0 / actualIntervalMs
}

/**
 * High-rate sampler for a handful of holding-register signals.
 *
 * Unlike [PollEngine] this does not keep a long trend history. It reads only the selected
 * signals' register span, publishes at a capped UI rate, and keeps a short ring buffer
 * sized for the scope window.
 */
class ScopeEngine(
    private val master: ModbusMaster,
    private val profile: DeviceProfile,
    private val signalIds: Set<String>,
    private val intervalMs: Long,
    private val bufferLimit: Int = 3000,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val _state = MutableStateFlow(ScopeSnapshot(intervalMs = intervalMs, bufferLimit = bufferLimit))
    val state: StateFlow<ScopeSnapshot> = _state.asStateFlow()

    private val selected: List<Pair<SignalGroup, Signal>> =
        profile.groups
            .filter { it.objectType == ObjectType.HOLDING_REGISTER }
            .flatMap { group ->
                group.signals
                    .filter { signal -> signal.id in signalIds && signal.isNumericScopeSignal() }
                    .map { signal -> group to signal }
            }.sortedWith(compareBy({ it.first.start }, { it.second.address }))

    private val buffers = LinkedHashMap<String, ArrayDeque<ScopePoint>>()
    private val lock = Any()
    private var reads = 0L
    private var errors = 0L
    private var lastError: String? = null
    private var actualIntervalMs = 0L
    private var lastSampleAt = 0L
    private var lastPollAt = 0L
    private var lastPublishNanos = 0L

    init {
        selected.forEach { (_, signal) -> buffers[signal.id] = ArrayDeque() }
    }

    fun start() {
        if (job?.isActive == true) return
        if (selected.isEmpty()) {
            _state.value =
                ScopeSnapshot(
                    running = false,
                    intervalMs = intervalMs,
                    lastError = "Select at least one numeric holding-register signal",
                    bufferLimit = bufferLimit,
                )
            return
        }
        reset()
        job =
            scope.launch {
                loop()
            }
        publish(force = true)
    }

    fun stop() {
        job?.cancel()
        job = null
        publish(force = true, running = false)
    }

    fun shutdown() {
        stop()
        scope.cancel()
    }

    private suspend fun loop() {
        while (currentCoroutineContext().isActive) {
            val cycleStartedNanos = System.nanoTime()
            val now = System.currentTimeMillis()
            try {
                val samples = readSelected()
                samples.forEach { (signal, value) ->
                    append(signal, now, value)
                }
                reads++
                lastError = null
                lastSampleAt = now
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                errors++
                lastError = failure.message ?: failure.javaClass.simpleName
            }
            if (lastPollAt > 0) {
                actualIntervalMs = now - lastPollAt
            }
            lastPollAt = now
            publish(force = false)
            val elapsedMs = (System.nanoTime() - cycleStartedNanos) / 1_000_000L
            delay((intervalMs - elapsedMs).coerceAtLeast(0))
        }
    }

    private suspend fun readSelected(): List<Pair<Signal, Double>> {
        val out = mutableListOf<Pair<Signal, Double>>()
        selected
            .groupBy { it.first.id }
            .forEach { (_, entries) ->
                val signals = entries.map { it.second }
                val start = signals.minOf { it.address }
                val end = signals.maxOf { it.address + it.registerSpan - 1 }
                val result =
                    master.readHoldingRegisters(
                        address = start,
                        quantity = (end - start + 1).coerceAtMost(125),
                        unitId = profile.unitId,
                        timeoutMs = scopeTimeoutMs(),
                        retries = 0,
                    )
                val registers = result.registers ?: IntArray(0)
                signals.forEach { signal ->
                    val measurement = ValueCodec.decodeRegister(signal, registers, start)
                    measurement.numeric?.let { value -> out += signal to value }
                }
            }
        return out
    }

    private fun append(
        signal: Signal,
        timeMs: Long,
        value: Double,
    ) = synchronized(lock) {
        val buffer = buffers[signal.id] ?: return@synchronized
        buffer.addLast(ScopePoint(timeMs, value))
        while (buffer.size > bufferLimit) buffer.removeFirst()
    }

    private fun scopeTimeoutMs(): Int = (intervalMs * 4 + 100).coerceIn(100, 2_000).toInt()

    private fun publish(
        force: Boolean,
        running: Boolean = job?.isActive == true,
    ) {
        val nowNanos = System.nanoTime()
        if (!force && nowNanos - lastPublishNanos < PUBLISH_INTERVAL_NANOS) return
        lastPublishNanos = nowNanos
        val series =
            synchronized(lock) {
                selected.map { (group, signal) ->
                    val points = buffers[signal.id]?.toList().orEmpty()
                    val latest = points.lastOrNull()?.value
                    ScopeSeries(
                        signalId = signal.id,
                        name = signal.name,
                        unit = signal.unit,
                        address = signal.address,
                        groupId = group.id,
                        groupName = group.name,
                        points = points,
                        latest = latest,
                        latestFormatted =
                            if (latest != null) {
                                ValueCodec.formatNumber(latest, ValueCodec.decimalsFor(signal), signal.unit)
                            } else {
                                "—"
                            },
                    )
                }
            }
        _state.value =
            ScopeSnapshot(
                running = running,
                intervalMs = intervalMs,
                actualIntervalMs = actualIntervalMs,
                reads = reads,
                errors = errors,
                lastError = lastError,
                series = series,
                lastSampleAt = lastSampleAt,
                bufferLimit = bufferLimit,
            )
    }

    private fun reset() {
        synchronized(lock) {
            selected.forEach { (_, signal) -> buffers[signal.id] = ArrayDeque() }
        }
        reads = 0
        errors = 0
        lastError = null
        actualIntervalMs = 0
        lastSampleAt = 0
        lastPollAt = 0
        lastPublishNanos = 0
    }

    private fun Signal.isNumericScopeSignal(): Boolean = dataType.isInteger || dataType.isFloat

    companion object {
        private const val PUBLISH_INTERVAL_NANOS = 33_000_000L
    }
}
