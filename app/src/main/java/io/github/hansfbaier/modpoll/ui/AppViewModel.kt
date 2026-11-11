package io.github.hansfbaier.modpoll.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.hansfbaier.modpoll.AppContainer
import io.github.hansfbaier.modpoll.ModPollApp
import io.github.hansfbaier.modpoll.data.BuiltInProfiles
import io.github.hansfbaier.modpoll.data.CsvImportResult
import io.github.hansfbaier.modpoll.data.DataType
import io.github.hansfbaier.modpoll.data.DeviceProfile
import io.github.hansfbaier.modpoll.data.Framer
import io.github.hansfbaier.modpoll.data.LinkSettings
import io.github.hansfbaier.modpoll.data.ModpollCsv
import io.github.hansfbaier.modpoll.data.ObjectType
import io.github.hansfbaier.modpoll.data.ProfilesFile
import io.github.hansfbaier.modpoll.data.SerialSettings
import io.github.hansfbaier.modpoll.data.Signal
import io.github.hansfbaier.modpoll.data.SignalGroup
import io.github.hansfbaier.modpoll.engine.PollEngine
import io.github.hansfbaier.modpoll.engine.PollSnapshot
import io.github.hansfbaier.modpoll.engine.ScopeEngine
import io.github.hansfbaier.modpoll.engine.ScopeSnapshot
import io.github.hansfbaier.modpoll.engine.SignalReading
import io.github.hansfbaier.modpoll.modbus.ExchangeKind
import io.github.hansfbaier.modpoll.modbus.FrameExchange
import io.github.hansfbaier.modpoll.modbus.ModbusException
import io.github.hansfbaier.modpoll.modbus.ModbusMaster
import io.github.hansfbaier.modpoll.modbus.RawResponseDecoder
import io.github.hansfbaier.modpoll.modbus.SerialTransport
import io.github.hansfbaier.modpoll.modbus.SimulatedTransport
import io.github.hansfbaier.modpoll.modbus.TcpTransport
import io.github.hansfbaier.modpoll.modbus.toHex
import io.github.hansfbaier.modpoll.usb.UsbSerialDeviceInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

enum class TransportKind {
    NONE,
    USB,
    SIMULATOR,
    TCP,
}

data class LinkUiState(
    val kind: TransportKind = TransportKind.NONE,
    val connected: Boolean = false,
    val connecting: Boolean = false,
    val polling: Boolean = false,
    val label: String = "",
    val lastError: String? = null,
    val selectedDeviceKey: String = "",
)

data class UnitProbe(
    val unitId: Int,
    val latencyMs: Long,
    val sample: String,
)

data class ScanUiState(
    val running: Boolean = false,
    val progress: Float = 0f,
    val label: String = "",
    val hits: List<UnitProbe> = emptyList(),
    val error: String? = null,
)

data class RegisterCell(
    val address: Int,
    val value: Int?,
    val error: String? = null,
)

data class RegisterScanState(
    val running: Boolean = false,
    val progress: Float = 0f,
    val objectType: ObjectType = ObjectType.HOLDING_REGISTER,
    val start: Int = 0,
    val count: Int = 0,
    val cells: List<RegisterCell> = emptyList(),
    val error: String? = null,
)

data class RawState(
    val sending: Boolean = false,
    val requestHex: String = "",
    val responseHex: String = "",
    val durationMs: Long = 0,
    val attempts: Int = 0,
    val error: String? = null,
    val decoded: String = "",
)

data class ImportReport(
    val title: String,
    val warnings: List<String> = emptyList(),
)

class AppViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val container: AppContainer = (application as ModPollApp).container
    private val store = container.profileStore
    private val usb = container.usb
    private val trafficRecorder = container.traffic
    private val json =
        Json {
            prettyPrint = true
            encodeDefaults = true
        }

    private val initial = store.load()

    private val _profiles = MutableStateFlow(initial.profiles)
    val profiles: StateFlow<List<DeviceProfile>> = _profiles.asStateFlow()

    private val _activeProfileId = MutableStateFlow(initial.activeProfileId)
    val activeProfileId: StateFlow<String> = _activeProfileId.asStateFlow()

    private val _activeProfile = MutableStateFlow(initial.profiles.firstOrNull { it.id == initial.activeProfileId })
    val activeProfile: StateFlow<DeviceProfile?> = _activeProfile.asStateFlow()

    private val _linkSettings = MutableStateFlow(initial.link)
    val linkSettings: StateFlow<LinkSettings> = _linkSettings.asStateFlow()

    private val _link = MutableStateFlow(LinkUiState())
    val link: StateFlow<LinkUiState> = _link.asStateFlow()

    private val _poll = MutableStateFlow(PollSnapshot())
    val poll: StateFlow<PollSnapshot> = _poll.asStateFlow()

    private val _scope = MutableStateFlow(ScopeSnapshot())
    val scope: StateFlow<ScopeSnapshot> = _scope.asStateFlow()

    private val _scan = MutableStateFlow(ScanUiState())
    val scan: StateFlow<ScanUiState> = _scan.asStateFlow()

    private val _registerScan = MutableStateFlow(RegisterScanState())
    val registerScan: StateFlow<RegisterScanState> = _registerScan.asStateFlow()

    private val _raw = MutableStateFlow(RawState())
    val raw: StateFlow<RawState> = _raw.asStateFlow()

    private val _report = MutableStateFlow<ImportReport?>(null)
    val report: StateFlow<ImportReport?> = _report.asStateFlow()

    val usbDevices = usb.devices
    val traffic: StateFlow<List<FrameExchange>> = trafficRecorder.entries

    private var transport: SerialTransport? = null
    private var master: ModbusMaster? = null
    private var engine: PollEngine? = null
    private var pollCollector: Job? = null
    private var scanJob: Job? = null
    private var registerScanJob: Job? = null
    private var lastTarget: ConnectionTarget? = null
    private var scopeEngine: ScopeEngine? = null
    private var scopeCollector: Job? = null
    private var scopeResumePolling = false

    init {
        _link.value =
            _link.value.copy(
                selectedDeviceKey = initial.link.lastDeviceKey,
                polling = false,
            )
    }

    // ---------------------------------------------------------------- profiles

    fun selectProfile(profileId: String) {
        val profile = _profiles.value.firstOrNull { it.id == profileId } ?: return
        stopScopeInternal(resume = false)
        _activeProfileId.value = profileId
        _activeProfile.value = profile
        persist()
        if (_link.value.connected) {
            startEngine(profile)
        } else {
            _poll.value = PollSnapshot()
            _link.value = _link.value.copy(polling = false)
        }
    }

    /** Clear the active profile. Nothing is polled until a profile is activated again. */
    fun deactivateProfile() {
        stopScopeInternal(resume = false)
        _activeProfileId.value = ""
        _activeProfile.value = null
        persist()
        pollCollector?.cancel()
        pollCollector = null
        engine?.shutdown()
        engine = null
        _poll.value = PollSnapshot()
        _link.value = _link.value.copy(polling = false)
    }

    fun createProfile(name: String = "New device"): DeviceProfile {
        val profile =
            DeviceProfile(
                id = UUID.randomUUID().toString(),
                name = name.ifBlank { "New device" },
                unitId = 1,
            ).ensureIds()
        _profiles.value = _profiles.value + profile
        _activeProfileId.value = profile.id
        _activeProfile.value = profile
        persist()
        if (_link.value.connected) startEngine(profile)
        return profile
    }

    fun duplicateProfile(profileId: String) {
        val source = _profiles.value.firstOrNull { it.id == profileId } ?: return
        val copy =
            source.copy(
                id = UUID.randomUUID().toString(),
                name = "${source.name} copy",
                builtIn = false,
                groups =
                    source.groups.map { group ->
                        group.copy(
                            id = UUID.randomUUID().toString(),
                            signals = group.signals.map { it.copy(id = UUID.randomUUID().toString()) },
                        )
                    },
            )
        _profiles.value = _profiles.value + copy
        _activeProfileId.value = copy.id
        _activeProfile.value = copy
        persist()
        if (_link.value.connected) startEngine(copy)
    }

    fun deleteProfile(profileId: String) {
        val target = _profiles.value.firstOrNull { it.id == profileId } ?: return
        if (target.builtIn) {
            _report.value = ImportReport("Built-in profiles cannot be deleted. Duplicate it instead.")
            return
        }
        val remaining = _profiles.value.filterNot { it.id == profileId }
        if (remaining.isEmpty()) return
        _profiles.value = remaining
        if (_activeProfileId.value == profileId) {
            deactivateProfile()
        } else {
            persist()
        }
    }

    fun upsertProfile(profile: DeviceProfile) {
        val normalized = profile.normalize()
        _profiles.value = _profiles.value.map { if (it.id == normalized.id) normalized else it }
        if (_activeProfileId.value == normalized.id) {
            _activeProfile.value = normalized
            engine?.updateProfile(normalized)
        }
        persist()
    }

    fun upsertGroup(
        profileId: String,
        group: SignalGroup,
    ) {
        val profile = _profiles.value.firstOrNull { it.id == profileId } ?: return
        val normalizedGroup = group.ensureIds()
        val groups =
            if (profile.groups.any { it.id == normalizedGroup.id }) {
                profile.groups.map { if (it.id == normalizedGroup.id) normalizedGroup else it }
            } else {
                profile.groups + normalizedGroup
            }
        upsertProfile(profile.copy(groups = groups))
    }

    fun deleteGroup(
        profileId: String,
        groupId: String,
    ) {
        val profile = _profiles.value.firstOrNull { it.id == profileId } ?: return
        upsertProfile(profile.copy(groups = profile.groups.filterNot { it.id == groupId }))
    }

    fun upsertSignal(
        profileId: String,
        groupId: String,
        signal: Signal,
    ) {
        val profile = _profiles.value.firstOrNull { it.id == profileId } ?: return
        val group = profile.groups.firstOrNull { it.id == groupId } ?: return
        val withId = signal.ensureId()
        val signals =
            if (group.signals.any { it.id == withId.id }) {
                group.signals.map { if (it.id == withId.id) withId else it }
            } else {
                group.signals + withId
            }
        upsertProfile(
            profile.copy(
                groups = profile.groups.map { if (it.id == groupId) group.copy(signals = signals) else it },
            ),
        )
    }

    fun deleteSignal(
        profileId: String,
        groupId: String,
        signalId: String,
    ) {
        val profile = _profiles.value.firstOrNull { it.id == profileId } ?: return
        upsertProfile(
            profile.copy(
                groups =
                    profile.groups.map { group ->
                        if (group.id == groupId) group.copy(signals = group.signals.filterNot { it.id == signalId }) else group
                    },
            ),
        )
    }

    fun toggleFavorite(
        profileId: String,
        groupId: String,
        signalId: String,
    ) {
        val profile = _profiles.value.firstOrNull { it.id == profileId } ?: return
        val group = profile.groups.firstOrNull { it.id == groupId } ?: return
        val signal = group.signals.firstOrNull { it.id == signalId } ?: return
        upsertSignal(profileId, groupId, signal.copy(favorite = !signal.favorite))
    }

    fun setProfileUnitId(unitId: Int) {
        val profile = _activeProfile.value ?: return
        upsertProfile(profile.copy(unitId = unitId.coerceIn(1, 247)))
    }

    /**
     * Add a scanned address to the active profile, creating or extending a group as needed.
     * Returns true when the signal was saved.
     */
    fun addSignalToActiveProfile(
        objectType: ObjectType,
        address: Int,
        dataType: DataType,
        name: String,
        writable: Boolean,
    ): Boolean {
        val profile =
            _activeProfile.value ?: run {
                _report.value =
                    ImportReport(
                        "No active profile",
                        listOf("Activate a profile first (Profiles → Activate), then add the signal again."),
                    )
                return false
            }
        val span =
            if (objectType.isBitTable) {
                dataType.bitWidth.coerceAtLeast(1)
            } else {
                (if (dataType == DataType.STRING) 1 else dataType.registerSpan).coerceAtLeast(1)
            }
        if (address < 0 || address + span > 65536) {
            _report.value = ImportReport("Address out of range", listOf("Address $address + $span exceeds 65535."))
            return false
        }

        val existing = profile.groups.firstOrNull { it.objectType == objectType && address in it.start..it.endAddress }
        val canExtend =
            existing != null &&
                address + span - existing.start <= objectType.maximumReadQuantity
        val group =
            if (existing != null && canExtend) {
                if (address + span - 1 > existing.endAddress) {
                    existing.copy(count = address + span - existing.start)
                } else {
                    existing
                }
            } else {
                val start = if (objectType.isBitTable) address else (address / 16) * 16
                val desired = maxOf(16, address + span - start)
                SignalGroup(
                    id = UUID.randomUUID().toString(),
                    name = "${objectType.label} $start",
                    objectType = objectType,
                    start = start,
                    count = desired.coerceAtMost(objectType.maximumReadQuantity).coerceAtMost(65536 - start),
                    intervalMs = 1000,
                )
            }

        val baseName = name.trim().ifBlank { "${objectType.shortLabel} $address" }
        var uniqueName = baseName
        var suffix = 2
        while (group.signals.any { it.name == uniqueName }) {
            uniqueName = "$baseName $suffix"
            suffix++
        }
        val signal =
            Signal(
                id = UUID.randomUUID().toString(),
                name = uniqueName,
                address = address,
                dataType = dataType,
                writable = writable && (objectType == ObjectType.COIL || objectType == ObjectType.HOLDING_REGISTER),
            )
        val updatedGroup = group.copy(signals = group.signals + signal)
        val groups =
            if (profile.groups.any { it.id == group.id }) {
                profile.groups.map { if (it.id == group.id) updatedGroup else it }
            } else {
                profile.groups + updatedGroup
            }
        upsertProfile(profile.copy(groups = groups))
        _report.value =
            ImportReport(
                "Added '$uniqueName' to ${profile.name}",
                listOf(
                    "Group: ${updatedGroup.name} (${updatedGroup.objectType.shortLabel} ${updatedGroup.start}..${updatedGroup.endAddress})",
                    "Type: ${dataType.label} at address $address",
                    "The signal is saved in the profile and will appear as a card on Live.",
                ),
            )
        return true
    }

    // ------------------------------------------------------------------ link

    fun updateSerialSettings(settings: SerialSettings) {
        updateLinkSettings(_linkSettings.value.copy(serial = settings))
    }

    fun updateModbusSettings(
        timeoutMs: Int? = null,
        retries: Int? = null,
        interFrameDelayMs: Int? = null,
        framer: Framer? = null,
        autoReconnect: Boolean? = null,
        autoDisableAfterFailures: Boolean? = null,
    ) {
        val current = _linkSettings.value
        updateLinkSettings(
            current.copy(
                timeoutMs = timeoutMs?.coerceIn(100, 30_000) ?: current.timeoutMs,
                retries = retries?.coerceIn(0, 5) ?: current.retries,
                interFrameDelayMs = interFrameDelayMs?.coerceIn(0, 500) ?: current.interFrameDelayMs,
                framer = framer ?: current.framer,
                autoReconnect = autoReconnect ?: current.autoReconnect,
                autoDisableAfterFailures = autoDisableAfterFailures ?: current.autoDisableAfterFailures,
            ),
        )
    }

    private fun updateLinkSettings(settings: LinkSettings) {
        _linkSettings.value = settings
        persist()
    }

    fun refreshDevices() = usb.refresh()

    fun connectUsb(info: UsbSerialDeviceInfo) {
        if (_link.value.connected || _link.value.connecting) disconnect()
        viewModelScope.launch {
            _link.value =
                _link.value.copy(
                    connecting = true,
                    connected = false,
                    lastError = null,
                    selectedDeviceKey = info.key,
                )
            if (!info.supported) {
                _link.value = _link.value.copy(connecting = false, lastError = "No USB serial driver for this device")
                return@launch
            }
            val granted = withTimeoutOrNull(20_000) { usb.ensurePermission(info.device) } == true
            if (!granted) {
                _link.value = _link.value.copy(connecting = false, lastError = "USB permission denied")
                return@launch
            }
            val newTransport =
                try {
                    withContext(Dispatchers.IO) {
                        usb.openTransport(info, _linkSettings.value.serial).also { it.open() }
                    }
                } catch (failure: Exception) {
                    _link.value =
                        _link.value.copy(connecting = false, lastError = failure.message ?: "Cannot open USB serial port")
                    return@launch
                }
            lastTarget = ConnectionTarget.Usb(info)
            attachTransport(newTransport, TransportKind.USB, info.displayName)
        }
    }

    fun connectTcp(
        host: String,
        port: Int,
    ) {
        val address = host.trim()
        if (address.isEmpty() || port !in 1..65535) {
            _link.value = _link.value.copy(lastError = "Enter a TCP host and port (1-65535)")
            return
        }
        if (_activeProfile.value == null) {
            _link.value = _link.value.copy(lastError = "Activate a profile first (Profiles → Activate)")
            return
        }
        if (_link.value.connected || _link.value.connecting) disconnect()
        updateLinkSettings(_linkSettings.value.copy(tcpHost = address, tcpPort = port))
        viewModelScope.launch {
            _link.value = _link.value.copy(connecting = true, connected = false, lastError = null)
            val newTransport =
                try {
                    withContext(Dispatchers.IO) {
                        TcpTransport(address, port).also { it.open() }
                    }
                } catch (failure: Exception) {
                    _link.value =
                        _link.value.copy(
                            connecting = false,
                            lastError = failure.message ?: "Cannot connect to $address:$port",
                        )
                    return@launch
                }
            lastTarget = ConnectionTarget.Tcp(address, port)
            attachTransport(
                newTransport,
                TransportKind.TCP,
                "$address:$port",
                Framer.TCP,
            )
        }
    }

    fun connectDemo() {
        if (_link.value.connected || _link.value.connecting) disconnect()
        if (_activeProfile.value == null || _activeProfile.value?.signalCount == 0) {
            val demo = _profiles.value.firstOrNull { it.id == BuiltInProfiles.DEMO_ID }
            if (demo != null) {
                _activeProfileId.value = demo.id
                _activeProfile.value = demo
                persist()
            }
        }
        val newTransport =
            SimulatedTransport(
                framer = if (_linkSettings.value.framer == Framer.TCP) Framer.RTU else _linkSettings.value.framer,
                unitIds = setOf(1, 2, 3),
            )
        lastTarget = ConnectionTarget.Simulator
        attachTransport(newTransport, TransportKind.SIMULATOR, "Built-in simulator")
    }

    private fun attachTransport(
        newTransport: SerialTransport,
        kind: TransportKind,
        label: String,
        framing: Framer = _linkSettings.value.framer,
    ) {
        val profile = _activeProfile.value
        if (profile == null) {
            _link.value = _link.value.copy(connecting = false, lastError = "Activate a profile first (Profiles → Activate)")
            runCatching { newTransport.close() }
            return
        }
        try {
            if (!newTransport.isOpen) newTransport.open()
        } catch (failure: Exception) {
            _link.value = _link.value.copy(connecting = false, lastError = failure.message ?: "Cannot open link")
            return
        }
        transport = newTransport
        val master =
            ModbusMaster(
                transport = newTransport,
                framer = framing,
                defaultUnitId = profile.unitId,
                defaultTimeoutMs = _linkSettings.value.timeoutMs,
                defaultRetries = _linkSettings.value.retries,
                interFrameDelayMs = _linkSettings.value.interFrameDelayMs.toLong(),
                recorder = trafficRecorder,
            )
        this.master = master
        _link.value =
            _link.value.copy(
                kind = kind,
                connected = true,
                connecting = false,
                polling = true,
                label = label,
                lastError = null,
            )
        startEngine(profile)
        if (kind == TransportKind.USB) {
            updateLinkSettings(_linkSettings.value.copy(lastDeviceKey = _link.value.selectedDeviceKey))
        }
    }

    fun disconnect() {
        stopScopeInternal(resume = false)
        pollCollector?.cancel()
        pollCollector = null
        engine?.shutdown()
        engine = null
        runCatching { transport?.close() }
        transport = null
        master = null
        lastTarget = null
        _poll.value = _poll.value.copy(running = false)
        _link.value =
            LinkUiState(
                kind = TransportKind.NONE,
                selectedDeviceKey = _link.value.selectedDeviceKey,
            )
    }

    private suspend fun onTransportLost(failure: Throwable) {
        if (!_link.value.connected) return
        stopScopeInternal(resume = false)
        val target = lastTarget
        _link.value =
            _link.value.copy(
                connected = false,
                polling = false,
                lastError = "Link lost: ${failure.message}",
            )
        pollCollector?.cancel()
        pollCollector = null
        engine?.shutdown()
        engine = null
        runCatching { transport?.close() }
        transport = null
        master = null
        if (_linkSettings.value.autoReconnect && target != null) {
            delay(1500)
            when (target) {
                is ConnectionTarget.Usb -> connectUsb(target.info)
                is ConnectionTarget.Tcp -> connectTcp(target.host, target.port)
                ConnectionTarget.Simulator -> connectDemo()
            }
        }
    }

    private fun startEngine(profile: DeviceProfile) {
        val activeMaster = master ?: return
        stopScopeInternal(resume = false)
        pollCollector?.cancel()
        engine?.shutdown()
        val newEngine =
            PollEngine(
                master = activeMaster,
                initialProfile = profile,
                autoDisableAfterFailures = _linkSettings.value.autoDisableAfterFailures,
                onTransportFailure = { failure -> viewModelScope.launch { onTransportLost(failure) } },
            )
        engine = newEngine
        pollCollector =
            viewModelScope.launch {
                newEngine.state.collect { snapshot -> _poll.value = snapshot }
            }
        newEngine.start()
        _link.value = _link.value.copy(polling = true)
    }

    fun togglePolling() {
        val engine =
            engine ?: run {
                val profile = _activeProfile.value
                if (master != null && profile != null) {
                    startEngine(profile)
                    return
                }
                _link.value = _link.value.copy(lastError = "No active profile")
                return
            }
        if (_link.value.polling) {
            engine.pause()
            _link.value = _link.value.copy(polling = false)
        } else {
            engine.resume()
            _link.value = _link.value.copy(polling = true)
        }
    }

    fun readOnce() {
        val engine =
            engine ?: run {
                _link.value = _link.value.copy(lastError = "Not connected")
                return
            }
        viewModelScope.launch {
            try {
                engine.pollOnce()
            } catch (failure: Exception) {
                _link.value = _link.value.copy(lastError = failure.message)
            }
        }
    }

    /**
     * Start a high-rate scope on holding-register signals. Normal polling is paused until
     * [stopScope] is called.
     */
    fun startScope(
        signalIds: Set<String>,
        intervalMs: Long,
    ): Boolean {
        val activeMaster = master
        val profile = _activeProfile.value
        val error =
            when {
                activeMaster == null -> "Connect a link first"
                profile == null -> "Activate a profile first"
                signalIds.isEmpty() -> "Select at least one holding-register signal"
                else -> null
            }
        if (error != null) {
            _scope.value = ScopeSnapshot(running = false, intervalMs = intervalMs, lastError = error)
            return false
        }
        val wasScopeRunning = scopeEngine != null
        val resumeAfter = if (wasScopeRunning) scopeResumePolling else _link.value.polling
        stopScopeInternal(resume = false)
        scopeResumePolling = resumeAfter
        engine?.pause()
        _link.value = _link.value.copy(polling = false)
        val sampler =
            ScopeEngine(
                master = activeMaster!!,
                profile = profile!!,
                signalIds = signalIds,
                intervalMs = intervalMs.coerceIn(10, 2_000),
            )
        scopeEngine = sampler
        scopeCollector =
            viewModelScope.launch {
                sampler.state.collect { snapshot -> _scope.value = snapshot }
            }
        sampler.start()
        return true
    }

    fun stopScope() {
        stopScopeInternal(resume = true)
    }

    private fun stopScopeInternal(resume: Boolean) {
        val shouldResume = resume && scopeResumePolling
        scopeEngine?.shutdown()
        scopeEngine = null
        scopeCollector?.cancel()
        scopeCollector = null
        scopeResumePolling = false
        if (_scope.value.running || _scope.value.series.isNotEmpty()) {
            _scope.value = _scope.value.copy(running = false)
        }
        if (shouldResume) {
            engine?.resume()
            _link.value = _link.value.copy(polling = engine != null)
        }
    }

    fun resetAutoDisabled(groupId: String) {
        engine?.resetAutoDisabled(groupId)
    }

    suspend fun writeSignal(
        groupId: String,
        signalId: String,
        value: String,
    ): Result<Unit> = engine?.writeSignal(groupId, signalId, value) ?: Result.failure(ModbusException("Not connected"))

    suspend fun writeCoilBit(
        groupId: String,
        signalId: String,
        bitIndex: Int,
        on: Boolean,
    ): Result<Unit> = engine?.writeCoilBit(groupId, signalId, bitIndex, on) ?: Result.failure(ModbusException("Not connected"))

    // --------------------------------------------------------------- scanners

    fun cancelScan() {
        scanJob?.cancel()
        _scan.value = _scan.value.copy(running = false)
    }

    fun cancelRegisterScan() {
        registerScanJob?.cancel()
        _registerScan.value = _registerScan.value.copy(running = false)
    }

    fun scanUnits(
        startUnit: Int,
        endUnit: Int,
        function: Int,
        address: Int,
        quantity: Int,
        timeoutMs: Int,
    ) {
        val master =
            master ?: run {
                _scan.value = ScanUiState(error = "Connect to a device first")
                return
            }
        val from = startUnit.coerceIn(1, 247)
        val to = endUnit.coerceIn(from, 247)
        scanJob?.cancel()
        scanJob =
            viewModelScope.launch(Dispatchers.IO) {
                _scan.value = ScanUiState(running = true, label = "Probing units $from..$to")
                val hits = mutableListOf<UnitProbe>()
                val total = (to - from + 1).coerceAtLeast(1)
                for (unit in from..to) {
                    try {
                        val started = System.nanoTime()
                        val result =
                            master.read(
                                function = function,
                                address = address,
                                quantity = quantity.coerceIn(1, 125),
                                unitId = unit,
                                timeoutMs = timeoutMs.coerceIn(50, 10_000),
                                retries = 0,
                                kind = ExchangeKind.SCAN,
                            )
                        val latency = (System.nanoTime() - started) / 1_000_000
                        val sample =
                            result.registers?.take(2)?.joinToString("/")
                                ?: result.bits?.take(16)?.joinToString("") { if (it) "1" else "0" }
                                ?: ""
                        hits += UnitProbe(unit, latency, sample)
                        _scan.value =
                            ScanUiState(
                                running = true,
                                progress = (unit - from + 1f) / total,
                                label = "Probing unit $unit",
                                hits = hits.toList(),
                            )
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        _scan.value =
                            ScanUiState(
                                running = true,
                                progress = (unit - from + 1f) / total,
                                label = "Probing unit $unit",
                                hits = hits.toList(),
                            )
                    }
                }
                _scan.value =
                    ScanUiState(
                        running = false,
                        progress = 1f,
                        label = "Found ${hits.size} device(s)",
                        hits = hits.toList(),
                    )
            }
    }

    fun scanRegisters(
        objectType: ObjectType,
        start: Int,
        count: Int,
    ) {
        val master =
            master ?: run {
                _registerScan.value = RegisterScanState(error = "Connect to a device first")
                return
            }
        val safeStart = start.coerceIn(0, 65535)
        val safeCount = count.coerceIn(1, minOf(4096, 65536 - safeStart))
        registerScanJob?.cancel()
        registerScanJob =
            viewModelScope.launch(Dispatchers.IO) {
                _registerScan.value =
                    RegisterScanState(
                        running = true,
                        objectType = objectType,
                        start = safeStart,
                        count = safeCount,
                    )
                val cells = mutableListOf<RegisterCell>()
                var offset = 0
                while (offset < safeCount) {
                    val blockSize = minOf(32, safeCount - offset)
                    val address = safeStart + offset
                    try {
                        if (objectType.isBitTable) {
                            val result =
                                master.read(
                                    if (objectType == ObjectType.COIL) 1 else 2,
                                    address,
                                    blockSize,
                                    retries = 0,
                                    kind = ExchangeKind.SCAN,
                                )
                            val bits = result.bits ?: BooleanArray(0)
                            for (index in 0 until blockSize) {
                                cells += RegisterCell(address + index, if (bits.getOrNull(index) == true) 1 else 0)
                            }
                        } else {
                            val result =
                                master.read(
                                    if (objectType == ObjectType.INPUT_REGISTER) 4 else 3,
                                    address,
                                    blockSize,
                                    retries = 0,
                                    kind = ExchangeKind.SCAN,
                                )
                            val registers = result.registers ?: IntArray(0)
                            for (index in 0 until blockSize) {
                                cells += RegisterCell(address + index, registers.getOrNull(index) ?: 0)
                            }
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        for (index in 0 until blockSize) {
                            cells += RegisterCell(address + index, null, failure.message ?: "Read failed")
                        }
                    }
                    offset += blockSize
                    _registerScan.value =
                        RegisterScanState(
                            running = true,
                            progress = offset.toFloat() / safeCount,
                            objectType = objectType,
                            start = safeStart,
                            count = safeCount,
                            cells = cells.toList(),
                        )
                }
                _registerScan.value =
                    RegisterScanState(
                        running = false,
                        progress = 1f,
                        objectType = objectType,
                        start = safeStart,
                        count = safeCount,
                        cells = cells.toList(),
                    )
            }
    }

    // -------------------------------------------------------------------- raw

    fun sendRaw(
        unitId: Int,
        pduHex: String,
        timeoutMs: Int,
    ) {
        val master =
            master ?: run {
                _raw.value = RawState(error = "Connect to a device first")
                return
            }
        val pdu = parseHex(pduHex)
        if (pdu == null || pdu.isEmpty()) {
            _raw.value = RawState(error = "Invalid PDU hex")
            return
        }
        viewModelScope.launch {
            _raw.value = RawState(sending = true)
            try {
                val result = master.rawRequest(pdu, unitId.coerceIn(0, 247), timeoutMs.coerceIn(50, 30_000), retries = 0)
                _raw.value =
                    RawState(
                        sending = false,
                        requestHex = result.request.toHex(),
                        responseHex = result.responseRaw.toHex(),
                        durationMs = result.durationMs,
                        attempts = result.attempts,
                        decoded = RawResponseDecoder.decode(result.response, pdu),
                    )
            } catch (failure: Exception) {
                _raw.value =
                    RawState(
                        sending = false,
                        requestHex = "",
                        responseHex = "",
                        error = failure.message ?: "Request failed",
                    )
            }
        }
    }

    // ---------------------------------------------------------- import/export

    fun importCsvText(text: String) {
        val result: CsvImportResult = ModpollCsv.import(text)
        if (result.profiles.isEmpty()) {
            _report.value = ImportReport("CSV import failed", result.warnings)
            return
        }
        appendImported(result.profiles)
        _report.value =
            ImportReport(
                "Imported ${result.profiles.size} device profile(s)",
                result.warnings,
            )
    }

    fun importJsonText(text: String) {
        try {
            val imported = store.importJson(text)
            if (imported.isEmpty()) {
                _report.value = ImportReport("JSON import failed", listOf("No profiles found"))
                return
            }
            appendImported(imported)
            _report.value = ImportReport("Imported ${imported.size} profile(s)")
        } catch (failure: Exception) {
            _report.value = ImportReport("JSON import failed", listOf(failure.message ?: "Invalid JSON"))
        }
    }

    fun importCsvFromUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val text = readUri(uri)
                withContext(Dispatchers.Main) { importCsvText(text) }
            } catch (failure: Exception) {
                _report.value = ImportReport("CSV import failed", listOf(failure.message ?: "Cannot read file"))
            }
        }
    }

    fun importJsonFromUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val text = readUri(uri)
                withContext(Dispatchers.Main) { importJsonText(text) }
            } catch (failure: Exception) {
                _report.value = ImportReport("JSON import failed", listOf(failure.message ?: "Cannot read file"))
            }
        }
    }

    private fun appendImported(profiles: List<DeviceProfile>) {
        val existingNames = _profiles.value.map { it.name }.toMutableSet()
        val additions =
            profiles.map { incoming ->
                var name = incoming.name.ifBlank { "Imported device" }
                while (!existingNames.add(name)) name = "$name (2)"
                incoming
                    .copy(
                        id = UUID.randomUUID().toString(),
                        name = name,
                        builtIn = false,
                    ).ensureIds()
            }
        _profiles.value = _profiles.value + additions
        if (_activeProfile.value == null) {
            _activeProfileId.value = additions.first().id
            _activeProfile.value = additions.first()
        }
        persist()
    }

    fun exportProfilesJson(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val text = store.exportJson(_profiles.value)
                writeUri(uri, text)
                withContext(Dispatchers.Main) { _report.value = ImportReport("Exported profiles") }
            } catch (failure: Exception) {
                withContext(Dispatchers.Main) {
                    _report.value = ImportReport("Export failed", listOf(failure.message ?: "Cannot write file"))
                }
            }
        }
    }

    fun exportProfileCsv(
        uri: Uri,
        profileId: String,
    ) {
        val profile = _profiles.value.firstOrNull { it.id == profileId } ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val (csv, warnings) = ModpollCsv.export(profile)
                writeUri(uri, csv)
                withContext(Dispatchers.Main) {
                    _report.value = ImportReport("Exported ${profile.name} as modpoll CSV", warnings)
                }
            } catch (failure: Exception) {
                withContext(Dispatchers.Main) {
                    _report.value = ImportReport("Export failed", listOf(failure.message ?: "Cannot write file"))
                }
            }
        }
    }

    fun exportSnapshot(uri: Uri) {
        val profile = _activeProfile.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val values = _poll.value.values
                val payload =
                    buildJsonObject {
                        put(
                            profile.name,
                            buildJsonObject {
                                profile.groups.forEach { group ->
                                    group.signals.forEach { signal ->
                                        val reading: SignalReading? = values[signal.id]
                                        val value = reading?.measurement?.numeric
                                        if (value != null) {
                                            put(signal.name, value)
                                        } else {
                                            put(signal.name, reading?.measurement?.formatted ?: "—")
                                        }
                                    }
                                }
                                put("timestamp", System.currentTimeMillis() / 1000)
                            },
                        )
                    }
                writeUri(
                    uri,
                    json.encodeToString(
                        kotlinx.serialization.json.JsonObject
                            .serializer(),
                        payload,
                    ),
                )
                withContext(Dispatchers.Main) { _report.value = ImportReport("Snapshot exported") }
            } catch (failure: Exception) {
                withContext(Dispatchers.Main) {
                    _report.value = ImportReport("Export failed", listOf(failure.message ?: "Cannot write file"))
                }
            }
        }
    }

    fun exportTrafficCsv(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val builder = StringBuilder()
                builder.appendLine("time,kind,framer,unit,function,attempt,duration_ms,ok,request,response,error")
                trafficRecorder.snapshot().forEach { entry ->
                    builder.appendLine(
                        listOf(
                            entry.timeMs.toString(),
                            entry.kind.label,
                            entry.framer.label,
                            entry.unitId.toString(),
                            entry.function.toString(),
                            entry.attempt.toString(),
                            entry.durationMs.toString(),
                            entry.ok.toString(),
                            entry.request.toHex(" "),
                            entry.response?.toHex(" ").orEmpty(),
                            (entry.error ?: "").replace(',', ';'),
                        ).joinToString(","),
                    )
                }
                writeUri(uri, builder.toString())
                withContext(Dispatchers.Main) { _report.value = ImportReport("Traffic log exported") }
            } catch (failure: Exception) {
                withContext(Dispatchers.Main) {
                    _report.value = ImportReport("Export failed", listOf(failure.message ?: "Cannot write file"))
                }
            }
        }
    }

    fun clearTraffic() = trafficRecorder.clear()

    fun clearReport() {
        _report.value = null
    }

    private fun readUri(uri: Uri): String =
        getApplication<Application>().contentResolver.openInputStream(uri)?.use {
            it.readBytes().toString(Charsets.UTF_8)
        } ?: throw IllegalStateException("Cannot open file")

    private fun writeUri(
        uri: Uri,
        text: String,
    ) {
        getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use {
            it.write(text.toByteArray(Charsets.UTF_8))
        } ?: throw IllegalStateException("Cannot open file")
    }

    private fun parseHex(input: String): ByteArray? {
        val clean = input.replace(Regex("[^0-9A-Fa-f]"), "")
        if (clean.length % 2 != 0) return null
        return try {
            ByteArray(clean.length / 2) { index ->
                clean.substring(index * 2, index * 2 + 2).toInt(16).toByte()
            }
        } catch (_: NumberFormatException) {
            null
        }
    }

    private fun persist() {
        val state =
            ProfilesFile(
                activeProfileId = _activeProfileId.value,
                profiles = _profiles.value,
                link = _linkSettings.value,
            )
        runCatching { store.save(state) }
            .onFailure { _link.value = _link.value.copy(lastError = "Cannot save profiles: ${it.message}") }
    }

    override fun onCleared() {
        stopScopeInternal(resume = false)
        pollCollector?.cancel()
        engine?.shutdown()
        runCatching { transport?.close() }
        super.onCleared()
    }

    private sealed interface ConnectionTarget {
        data class Usb(
            val info: UsbSerialDeviceInfo,
        ) : ConnectionTarget

        data class Tcp(
            val host: String,
            val port: Int,
        ) : ConnectionTarget

        data object Simulator : ConnectionTarget
    }
}
