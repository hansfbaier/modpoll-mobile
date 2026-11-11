package com.modpoll.rtu.modbus

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.modpoll.rtu.data.Parity
import com.modpoll.rtu.data.SerialSettings
import java.io.IOException
import java.util.ArrayDeque

/**
 * Modbus serial transport on top of usb-serial-for-android.
 * One request is in flight at a time; the Modbus master serializes access.
 */
class UsbSerialTransport(
    private val usbManager: UsbManager,
    private val device: UsbDevice,
    private val settings: SerialSettings,
    override val label: String,
) : SerialTransport {
    private var connection: UsbDeviceConnection? = null
    private var port: UsbSerialPort? = null
    private val pending = ArrayDeque<Byte>()
    private val chunk = ByteArray(4096)

    override var isOpen: Boolean = false
        private set

    override fun open() {
        if (isOpen) return
        val driver =
            UsbSerialProber.getDefaultProber().probeDevice(device)
                ?: throw ModbusException("No USB serial driver for ${device.deviceName}")
        val conn =
            usbManager.openDevice(device)
                ?: throw ModbusException("USB permission denied for ${device.deviceName}")
        val serialPort =
            driver.ports.firstOrNull()
                ?: throw ModbusException("Device has no serial port")
        try {
            serialPort.open(conn)
            serialPort.setParameters(
                settings.baudRate,
                settings.dataBits,
                when (settings.stopBits) {
                    1 -> UsbSerialPort.STOPBITS_1
                    2 -> UsbSerialPort.STOPBITS_2
                    else -> UsbSerialPort.STOPBITS_1
                },
                when (settings.parity) {
                    Parity.NONE -> UsbSerialPort.PARITY_NONE
                    Parity.EVEN -> UsbSerialPort.PARITY_EVEN
                    Parity.ODD -> UsbSerialPort.PARITY_ODD
                },
            )
        } catch (io: IOException) {
            runCatching { serialPort.close() }
            runCatching { conn.close() }
            throw ModbusException("Cannot open serial port: ${io.message}", io)
        }
        connection = conn
        port = serialPort
        pending.clear()
        isOpen = true
    }

    override fun close() {
        isOpen = false
        pending.clear()
        val p = port
        port = null
        runCatching { p?.close() }
        runCatching { connection?.close() }
        connection = null
    }

    override fun write(data: ByteArray) {
        val p = port ?: throw ModbusException("Serial port is closed")
        try {
            p.write(data, WRITE_TIMEOUT_MS)
        } catch (io: IOException) {
            throw ModbusException("Serial write failed: ${io.message}", io)
        }
    }

    override fun readExact(
        count: Int,
        timeoutMs: Long,
    ): ByteArray {
        require(count >= 0)
        val deadline = System.nanoTime() + timeoutMs * 1_000_000L
        while (pending.size < count) {
            if (!fill(deadline)) {
                throw ModbusTimeoutException(
                    "Timeout waiting for ${count - pending.size} more byte(s) " +
                        "(got ${pending.size}/$count)",
                )
            }
        }
        val out = ByteArray(count)
        for (index in 0 until count) out[index] = pending.removeFirst()
        return out
    }

    override fun readUntil(
        terminator: Int,
        maxBytes: Int,
        timeoutMs: Long,
    ): ByteArray {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000L
        while (true) {
            val index = pending.indexOfFirst { it.toInt() and 0xFF == terminator }
            if (index >= 0) {
                val out = ByteArray(index + 1)
                for (i in out.indices) out[i] = pending.removeFirst()
                return out
            }
            if (pending.size >= maxBytes) {
                throw ModbusProtocolException("Frame exceeds $maxBytes bytes")
            }
            if (!fill(deadline)) {
                throw ModbusTimeoutException("Timeout waiting for frame terminator (${pending.size} bytes)")
            }
        }
    }

    override fun purge() {
        pending.clear()
        val p = port ?: return
        runCatching { p.purgeHwBuffers(true, true) }
    }

    /** Returns true when at least one byte was read. */
    private fun fill(deadline: Long): Boolean {
        val p = port ?: throw ModbusException("Serial port is closed")
        val remainingMs = (deadline - System.nanoTime()) / 1_000_000L
        if (remainingMs <= 0) return false
        val waitMs = remainingMs.coerceAtMost(MAX_READ_SLICE_MS)
        val read =
            try {
                p.read(chunk, chunk.size, waitMs.toInt().coerceAtLeast(1))
            } catch (io: IOException) {
                throw ModbusException("Serial read failed: ${io.message}", io)
            }
        if (read <= 0) return false
        for (index in 0 until read) pending.addLast(chunk[index])
        return true
    }

    companion object {
        private const val WRITE_TIMEOUT_MS = 2_000
        private const val MAX_READ_SLICE_MS = 200L

        fun deviceKey(device: UsbDevice): String = "%04X:%04X:%s".format(device.vendorId, device.productId, device.deviceName)

        fun create(
            context: Context,
            device: UsbDevice,
            settings: SerialSettings,
            label: String,
        ): UsbSerialTransport {
            val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
            return UsbSerialTransport(manager, device, settings, label)
        }
    }
}
