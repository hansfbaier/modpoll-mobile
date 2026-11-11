package io.github.hansfbaier.modpoll.modbus

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

/**
 * Modbus TCP transport over a plain TCP socket.
 *
 * The MBAP header and transaction id are handled by [ModbusMaster]; this class only moves bytes.
 */
class TcpTransport(
    private val host: String,
    private val port: Int,
    private val connectTimeoutMs: Int = 5_000,
) : SerialTransport {
    private var socket: Socket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null

    override val label: String = "$host:$port"

    override var isOpen: Boolean = false
        private set

    override fun open() {
        if (isOpen) return
        val newSocket = Socket()
        try {
            newSocket.tcpNoDelay = true
            newSocket.keepAlive = true
            newSocket.connect(InetSocketAddress(host, port), connectTimeoutMs)
            input = newSocket.getInputStream()
            output = newSocket.getOutputStream()
            socket = newSocket
            isOpen = true
        } catch (io: IOException) {
            runCatching { newSocket.close() }
            throw ModbusException("Cannot connect to $host:$port: ${io.message}", io)
        }
    }

    override fun close() {
        isOpen = false
        input = null
        output = null
        runCatching { socket?.close() }
        socket = null
    }

    override fun write(data: ByteArray) {
        val stream = output ?: throw ModbusException("TCP link is closed")
        try {
            stream.write(data)
            stream.flush()
        } catch (io: IOException) {
            throw ModbusException("TCP write failed: ${io.message}", io)
        }
    }

    override fun readExact(
        count: Int,
        timeoutMs: Long,
    ): ByteArray {
        require(count >= 0)
        if (count == 0) return ByteArray(0)
        val activeSocket = socket ?: throw ModbusException("TCP link is closed")
        val stream = input ?: throw ModbusException("TCP link is closed")
        val deadline = System.nanoTime() + timeoutMs * 1_000_000L
        val out = ByteArray(count)
        var offset = 0
        while (offset < count) {
            val remainingMs = ((deadline - System.nanoTime()) / 1_000_000L).toInt()
            if (remainingMs <= 0) {
                throw ModbusTimeoutException("TCP timeout after $offset/$count bytes")
            }
            try {
                activeSocket.soTimeout = remainingMs.coerceAtLeast(1)
                val read = stream.read(out, offset, count - offset)
                if (read < 0) throw ModbusException("TCP connection closed by remote host")
                if (read == 0) throw ModbusTimeoutException("TCP timeout after $offset/$count bytes")
                offset += read
            } catch (timeout: SocketTimeoutException) {
                throw ModbusTimeoutException("TCP timeout after $offset/$count bytes")
            } catch (io: IOException) {
                throw ModbusException("TCP read failed: ${io.message}", io)
            }
        }
        return out
    }

    override fun readUntil(
        terminator: Int,
        maxBytes: Int,
        timeoutMs: Long,
    ): ByteArray {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000L
        val out = ByteArrayOutputStream()
        while (out.size() < maxBytes) {
            val remainingMs = ((deadline - System.nanoTime()) / 1_000_000L).coerceAtLeast(1)
            val byte = readExact(1, remainingMs)
            out.write(byte[0].toInt())
            if ((byte[0].toInt() and 0xFF) == terminator) return out.toByteArray()
        }
        throw ModbusProtocolException("TCP frame exceeds $maxBytes bytes")
    }

    override fun purge() {
        val stream = input ?: return
        runCatching {
            while (stream.available() > 0) stream.read()
        }
    }
}
