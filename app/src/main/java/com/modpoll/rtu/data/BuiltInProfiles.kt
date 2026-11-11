package com.modpoll.rtu.data

import java.util.UUID

/**
 * Profiles shipped with the app. The demo profile matches the built-in simulator,
 * so it works out of the box without hardware.
 */
object BuiltInProfiles {
    const val DEMO_ID = "builtin.demo-plc"
    const val MODSIM_ID = "builtin.modsim"
    const val SDM120_ID = "builtin.sdm120"

    fun all(): List<DeviceProfile> = listOf(demoPlc(), modsimReference(), sdm120())

    fun demoPlc(): DeviceProfile =
        DeviceProfile(
            id = DEMO_ID,
            name = "Demo PLC (simulator)",
            unitId = 1,
            description = "Live simulated PLC: power measurements, setpoints, relays and inputs. No hardware required.",
            builtIn = true,
            groups =
                listOf(
                    SignalGroup(
                        id = "builtin.demo-plc.ir",
                        name = "Electrical measurements",
                        objectType = ObjectType.INPUT_REGISTER,
                        start = 0,
                        count = 26,
                        intervalMs = 1000,
                        signals =
                            listOf(
                                sig("Voltage", 0, DataType.FLOAT32, unit = "V", decimals = 1),
                                sig("Current", 2, DataType.FLOAT32, unit = "A"),
                                sig("Active Power", 4, DataType.FLOAT32, unit = "W", decimals = 1),
                                sig("Line Frequency", 6, DataType.FLOAT32, unit = "Hz"),
                                sig("Energy", 8, DataType.FLOAT32, unit = "kWh", decimals = 3),
                                sig("Status Word", 10, DataType.UINT16, description = "Bit 0 run, bit 1 ready"),
                                sig("Temperature", 12, DataType.FLOAT32, unit = "°C", decimals = 1),
                                sig(
                                    "Digital Faults",
                                    14,
                                    DataType.BOOL16,
                                    description = "Fault bitmap, bit 0 = overcurrent",
                                ),
                                sig("Total Starts", 16, DataType.UINT32),
                                sig("Total Runtime", 18, DataType.UINT32, unit = "min"),
                                sig("Device Name", 20, DataType.STRING, stringBytes = 12),
                            ),
                    ),
                    SignalGroup(
                        id = "builtin.demo-plc.hr",
                        name = "Setpoints & configuration",
                        objectType = ObjectType.HOLDING_REGISTER,
                        start = 0,
                        count = 24,
                        intervalMs = 2000,
                        signals =
                            listOf(
                                sig("Control Mode", 0, DataType.UINT16, writable = true, decimals = 0),
                                sig("Speed Setpoint", 1, DataType.UINT16, unit = "rpm", writable = true, decimals = 0),
                                sig("Temperature Setpoint", 2, DataType.FLOAT32, unit = "°C", writable = true),
                                sig("Batch Counter", 4, DataType.UINT32, writable = true, decimals = 0),
                                sig("Trim Offset", 6, DataType.FLOAT32, unit = "%", writable = true),
                                sig("Pump ID", 10, DataType.STRING, stringBytes = 7, writable = true),
                                sig("Recipe 1", 20, DataType.UINT16, writable = true, decimals = 0),
                                sig("Recipe 2", 21, DataType.UINT16, writable = true, decimals = 0),
                                sig("Recipe 3", 22, DataType.UINT16, writable = true, decimals = 0),
                                sig("Recipe 4", 23, DataType.UINT16, writable = true, decimals = 0),
                            ),
                    ),
                    SignalGroup(
                        id = "builtin.demo-plc.coils",
                        name = "Relay outputs",
                        objectType = ObjectType.COIL,
                        start = 0,
                        count = 16,
                        intervalMs = 1000,
                        signals =
                            listOf(
                                sig("Pump Relays", 0, DataType.BOOL8, writable = true),
                                sig("Valve Relays", 8, DataType.BOOL8, writable = true),
                            ),
                    ),
                    SignalGroup(
                        id = "builtin.demo-plc.di",
                        name = "Field inputs",
                        objectType = ObjectType.DISCRETE_INPUT,
                        start = 0,
                        count = 16,
                        intervalMs = 1000,
                        signals =
                            listOf(
                                sig("Limit Switches", 0, DataType.BOOL8),
                                sig("Safety Chain", 8, DataType.BOOL8),
                            ),
                    ),
                ),
        )

    fun modsimReference(): DeviceProfile =
        DeviceProfile(
            id = MODSIM_ID,
            name = "modsim addresses (simulator)",
            unitId = 1,
            description = "Mirrors the modpoll modsim.csv layout at 0 / 10000 / 30000 / 40000. Useful for comparing CSV import.",
            builtIn = true,
            groups =
                listOf(
                    SignalGroup(
                        id = "builtin.modsim.coils",
                        name = "Coils",
                        objectType = ObjectType.COIL,
                        start = 0,
                        count = 16,
                        signals =
                            listOf(
                                sig("coil01-08", 0, DataType.BOOL8, writable = true),
                                sig("coil09-16", 8, DataType.BOOL8, writable = true),
                            ),
                    ),
                    SignalGroup(
                        id = "builtin.modsim.di",
                        name = "Discrete inputs",
                        objectType = ObjectType.DISCRETE_INPUT,
                        start = 10000,
                        count = 16,
                        signals =
                            listOf(
                                sig("di01-08", 10000, DataType.BOOL8),
                                sig("di09-16", 10008, DataType.BOOL8),
                            ),
                    ),
                    SignalGroup(
                        id = "builtin.modsim.ir",
                        name = "Input registers 3xxxx",
                        objectType = ObjectType.INPUT_REGISTER,
                        start = 30000,
                        count = 20,
                        signals =
                            listOf(
                                sig("input_reg01", 30000, DataType.UINT16),
                                sig("input_reg02", 30001, DataType.UINT16),
                                sig("input_reg03", 30002, DataType.UINT16),
                                sig("input_reg04", 30003, DataType.UINT16),
                                sig("input_reg05", 30004, DataType.INT16),
                                sig("input_reg06", 30005, DataType.INT16),
                                sig("input_reg07", 30006, DataType.INT16),
                                sig("input_reg08", 30007, DataType.INT16),
                                sig("input_reg09", 30008, DataType.UINT32),
                                sig("input_reg10", 30010, DataType.UINT32, scale = 0.001),
                                sig("input_reg11", 30012, DataType.INT32),
                                sig("input_reg12", 30014, DataType.INT32, scale = 1000.0),
                                sig("input_reg13", 30016, DataType.FLOAT32),
                                sig("input_reg14", 30018, DataType.FLOAT32),
                            ),
                    ),
                    SignalGroup(
                        id = "builtin.modsim.hr",
                        name = "Holding registers 4xxxx",
                        objectType = ObjectType.HOLDING_REGISTER,
                        start = 40000,
                        count = 44,
                        intervalMs = 1000,
                        signals =
                            listOf(
                                sig("holding_reg01", 40000, DataType.UINT16, writable = true),
                                sig("holding_reg02", 40001, DataType.UINT16, writable = true),
                                sig("holding_reg03", 40002, DataType.UINT16, writable = true),
                                sig("holding_reg04", 40003, DataType.UINT16, writable = true),
                                sig("holding_reg05", 40004, DataType.INT16, writable = true),
                                sig("holding_reg06", 40005, DataType.INT16, writable = true),
                                sig("holding_reg07", 40006, DataType.INT16, writable = true),
                                sig("holding_reg08", 40007, DataType.INT16, writable = true),
                                sig("holding_reg09", 40008, DataType.UINT32, writable = true),
                                sig("holding_reg10", 40010, DataType.UINT32, writable = true, scale = 0.001),
                                sig("holding_reg11", 40012, DataType.INT32, writable = true),
                                sig("holding_reg12", 40014, DataType.INT32, writable = true, scale = 1000.0),
                                sig("holding_reg13", 40016, DataType.FLOAT32, writable = true),
                                sig("holding_reg14", 40018, DataType.FLOAT32, writable = true),
                                sig("holding_reg15", 40020, DataType.UINT64, writable = true),
                                sig("holding_reg16", 40024, DataType.INT64, writable = true),
                                sig("holding_reg17", 40028, DataType.FLOAT64, writable = true),
                                sig("holding_reg18", 40032, DataType.FLOAT64, writable = true),
                                sig("holding_reg19", 40036, DataType.STRING, stringBytes = 16, writable = true),
                            ),
                    ),
                ),
        )

    fun sdm120(): DeviceProfile =
        DeviceProfile(
            id = SDM120_ID,
            name = "Eastron SDM120M",
            unitId = 1,
            description = "Single-phase energy meter. Registers per Eastron SDM120 Modbus protocol (float32, BE).",
            builtIn = true,
            groups =
                listOf(
                    SignalGroup(
                        id = "builtin.sdm120.ir.a",
                        name = "Instantaneous",
                        objectType = ObjectType.INPUT_REGISTER,
                        start = 0,
                        count = 38,
                        signals =
                            listOf(
                                sig("Voltage", 0, DataType.FLOAT32, unit = "V", decimals = 2),
                                sig("Current", 6, DataType.FLOAT32, unit = "A", decimals = 3),
                                sig("Active Power", 12, DataType.FLOAT32, unit = "W", decimals = 1),
                                sig("Apparent Power", 18, DataType.FLOAT32, unit = "VA", decimals = 1),
                                sig("Reactive Power", 24, DataType.FLOAT32, unit = "VAr", decimals = 1),
                                sig("Power Factor", 30, DataType.FLOAT32, decimals = 3),
                                sig("Phase Angle", 36, DataType.FLOAT32, unit = "°", decimals = 1),
                            ),
                    ),
                    SignalGroup(
                        id = "builtin.sdm120.ir.b",
                        name = "Energy",
                        objectType = ObjectType.INPUT_REGISTER,
                        start = 70,
                        count = 10,
                        intervalMs = 5000,
                        signals =
                            listOf(
                                sig("Frequency", 70, DataType.FLOAT32, unit = "Hz", decimals = 2),
                                sig("Import Energy", 72, DataType.FLOAT32, unit = "kWh", decimals = 3),
                                sig("Export Energy", 74, DataType.FLOAT32, unit = "kWh", decimals = 3),
                                sig("Import Reactive", 76, DataType.FLOAT32, unit = "kvarh", decimals = 3),
                                sig("Export Reactive", 78, DataType.FLOAT32, unit = "kvarh", decimals = 3),
                            ),
                    ),
                ),
        )

    private fun sig(
        name: String,
        address: Int,
        type: DataType,
        unit: String = "",
        writable: Boolean = false,
        scale: Double = 1.0,
        decimals: Int? = null,
        stringBytes: Int = 0,
        description: String = "",
    ): Signal =
        Signal(
            id = UUID.randomUUID().toString(),
            name = name,
            address = address,
            dataType = type,
            unit = unit,
            writable = writable,
            scale = scale,
            decimals = decimals,
            stringBytes = stringBytes,
            description = description,
        )
}
