package io.github.hansfbaier.modpoll.modbus

import io.github.hansfbaier.modpoll.data.Framer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.InputStream
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

class TcpTransportTest {
    @Test
    fun mbapRequestAndResponseAreFramed() {
        val pdu = byteArrayOf(3, 0, 0, 0, 2)
        val request = FramerCodec.encodeTcp(1, pdu, 0x1234)
        assertEquals(0x12, request[0].toInt() and 0xFF)
        assertEquals(0x34, request[1].toInt() and 0xFF)
        assertEquals(0, request[2].toInt())
        assertEquals(0, request[3].toInt())
        assertEquals(6, ((request[4].toInt() and 0xFF) shl 8) or (request[5].toInt() and 0xFF))
        assertEquals(1, request[6].toInt())
        assertArrayEquals(pdu, request.copyOfRange(7, request.size))

        val responsePdu = byteArrayOf(3, 4, 0, 1, 0x05, 0xDC.toByte())
        val response =
            byteArrayOf(
                0x12,
                0x34,
                0,
                0,
                0,
                (responsePdu.size + 1).toByte(),
                1,
            ) + responsePdu
        val decoded = FramerCodec.decodeTcpResponse(response, 0x1234, 1, 3)
        assertArrayEquals(responsePdu.copyOfRange(1, responsePdu.size), decoded.payload)
    }

    @Test(expected = ModbusProtocolException::class)
    fun mismatchedTransactionIdIsRejected() {
        val responsePdu = byteArrayOf(3, 4, 0, 1, 0, 2)
        val response = byteArrayOf(0x22, 0x22, 0, 0, 0, (responsePdu.size + 1).toByte(), 1) + responsePdu
        FramerCodec.decodeTcpResponse(response, 0x1234, 1, 3)
    }

    @Test
    fun masterReadsOverTcp() =
        runBlocking {
            val server = ServerSocket(0)
            val serverError = AtomicReference<Throwable?>()
            val serverThread =
                thread(name = "modbus-tcp-test-server") {
                    try {
                        server.accept().use { client ->
                            client.soTimeout = 3_000
                            val input = client.getInputStream()
                            val header = ByteArray(7)
                            readFully(input, header)
                            val length = ((header[4].toInt() and 0xFF) shl 8) or (header[5].toInt() and 0xFF)
                            val pdu = ByteArray(length - 1)
                            readFully(input, pdu)
                            assertEquals(3, pdu[0].toInt())

                            val responsePdu = byteArrayOf(3, 4, 0, 1, 0x05, 0xDC.toByte())
                            val response = ByteArray(7 + responsePdu.size)
                            response[0] = header[0]
                            response[1] = header[1]
                            response[2] = 0
                            response[3] = 0
                            val responseLength = responsePdu.size + 1
                            response[4] = (responseLength ushr 8).toByte()
                            response[5] = responseLength.toByte()
                            response[6] = header[6]
                            responsePdu.copyInto(response, 7)
                            client.getOutputStream().write(response)
                            client.getOutputStream().flush()
                        }
                    } catch (failure: Throwable) {
                        serverError.set(failure)
                    } finally {
                        runCatching { server.close() }
                    }
                }

            val transport = TcpTransport("127.0.0.1", server.localPort)
            try {
                transport.open()
                val master =
                    ModbusMaster(
                        transport = transport,
                        framer = Framer.TCP,
                        defaultUnitId = 1,
                        defaultTimeoutMs = 1_000,
                        defaultRetries = 0,
                        interFrameDelayMs = 1,
                    )
                val result = master.readHoldingRegisters(0, 2)
                assertArrayEquals(intArrayOf(1, 1500), result.registers)
            } finally {
                transport.close()
                serverThread.join(2_000)
                serverError.get()?.let { throw it }
            }
        }

    private fun readFully(
        input: InputStream,
        target: ByteArray,
    ) {
        var offset = 0
        while (offset < target.size) {
            val read = input.read(target, offset, target.size - offset)
            if (read < 0) throw IllegalStateException("Unexpected end of stream")
            offset += read
        }
    }
}
