package com.renotube.tv.ambilight

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "WledUdpSender"

@Singleton
class WledUdpSender @Inject constructor() {
    private var socket: DatagramSocket? = null
    private var targetAddress: InetAddress? = null
    private var currentIp: String = ""
    private var ddpSequence: Byte = 1
    private var buffer = ByteArray(1500)
    private var cachedPacket: DatagramPacket? = null
    private var snifferAddress: InetAddress? = null
    private var cachedMirrorPacket: DatagramPacket? = null

    private fun getSocket(): DatagramSocket {
        var s = socket
        if (s == null || s.isClosed) {
            s = DatagramSocket()
            socket = s
        }
        return s
    }

    suspend fun sendFrame(ip: String, port: Int, rgbData: IntArray) = withContext(Dispatchers.IO) {
        sendFrameDirect(ip, port, rgbData)
    }

    fun sendFrameDirect(ip: String, port: Int, rgbData: IntArray) {
        if (ip.isEmpty() || rgbData.isEmpty()) return
        try {
            if (ip != currentIp || targetAddress == null) {
                targetAddress = InetAddress.getByName(ip)
                currentIp = ip
            }

            val ledCount = rgbData.size
            val dataLen = ledCount * 3

            if (port == 4048) {
                // ── DDP (Distributed Display Protocol - HyperHDR Standard) ──
                val packetSize = 10 + dataLen
                if (buffer.size < packetSize) {
                    buffer = ByteArray(packetSize + 128)
                    cachedPacket = null
                }

                buffer[0] = 0x41.toByte() // Push immediately flag
                buffer[1] = ddpSequence++
                if (ddpSequence == 0.toByte()) ddpSequence = 1
                buffer[2] = 0x01.toByte() // RGB 8-bit
                buffer[3] = 0x01.toByte() // Default device ID
                buffer[4] = 0
                buffer[5] = 0
                buffer[6] = 0
                buffer[7] = 0
                buffer[8] = ((dataLen shr 8) and 0xFF).toByte()
                buffer[9] = (dataLen and 0xFF).toByte()

                var offset = 10
                for (i in 0 until ledCount) {
                    val color = rgbData[i]
                    buffer[offset++] = ((color shr 16) and 0xFF).toByte()
                    buffer[offset++] = ((color shr 8) and 0xFF).toByte()
                    buffer[offset++] = (color and 0xFF).toByte()
                }

                val p = cachedPacket ?: DatagramPacket(buffer, packetSize, targetAddress, port).also { cachedPacket = it }
                p.address = targetAddress
                p.port = port
                p.length = packetSize
                getSocket().send(p)
            } else {
                // ── Standard WLED DRGB (Port 21324) ──
                val packetSize = 2 + dataLen
                if (buffer.size < packetSize) {
                    buffer = ByteArray(packetSize + 128)
                    cachedPacket = null
                }

                buffer[0] = 2 // DRGB protocol
                buffer[1] = 255.toByte() // 255s timeout (HyperHDR DriverNetWled standard)

                var offset = 2
                for (i in 0 until ledCount) {
                    val color = rgbData[i]
                    buffer[offset++] = ((color shr 16) and 0xFF).toByte()
                    buffer[offset++] = ((color shr 8) and 0xFF).toByte()
                    buffer[offset++] = (color and 0xFF).toByte()
                }

                val p = cachedPacket ?: DatagramPacket(buffer, packetSize, targetAddress, port).also { cachedPacket = it }
                p.address = targetAddress
                p.port = port
                p.length = packetSize
                getSocket().send(p)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send WLED packet to $ip:$port - ${e.message}")
        }
    }

    suspend fun sendTestPattern(ip: String, port: Int, totalLeds: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val addr = InetAddress.getByName(ip)
            val testLeds = totalLeds.coerceAtLeast(60)
            val dataLen = testLeds * 3

            val packet = if (port == 4048) {
                val size = 10 + dataLen
                val buf = ByteArray(size)
                buf[0] = 0x41.toByte()
                buf[1] = 1
                buf[2] = 0x01.toByte()
                buf[3] = 0x01.toByte()
                buf[8] = ((dataLen shr 8) and 0xFF).toByte()
                buf[9] = (dataLen and 0xFF).toByte()
                var offset = 10
                for (i in 0 until testLeds) {
                    if (i % 2 == 0) {
                        buf[offset++] = 255.toByte()
                        buf[offset++] = 140.toByte()
                        buf[offset++] = 20.toByte()
                    } else {
                        buf[offset++] = 0.toByte()
                        buf[offset++] = 200.toByte()
                        buf[offset++] = 255.toByte()
                    }
                }
                DatagramPacket(buf, size, addr, port)
            } else {
                val size = 2 + dataLen
                val buf = ByteArray(size)
                buf[0] = 2
                buf[1] = 4
                var offset = 2
                for (i in 0 until testLeds) {
                    if (i % 2 == 0) {
                        buf[offset++] = 255.toByte()
                        buf[offset++] = 140.toByte()
                        buf[offset++] = 20.toByte()
                    } else {
                        buf[offset++] = 0.toByte()
                        buf[offset++] = 200.toByte()
                        buf[offset++] = 255.toByte()
                    }
                }
                DatagramPacket(buf, size, addr, port)
            }

            getSocket().send(packet)
            Log.i(TAG, "Test pattern successfully sent to WLED ($port) at $ip")
            true
        } catch (e: Exception) {
            Log.e(TAG, "WLED test pattern failed: ${e.message}", e)
            false
        }
    }

    /**
     * 4-Edge Calibration Test Pattern:
     * - Left Edge: PURE RED (0xFF0000)
     * - Top Edge: PURE GREEN (0x00FF00)
     * - Right Edge: PURE BLUE (0x0064FF)
     * - Bottom Edge: PURE YELLOW (0xFFE600)
     * 
     * Allows user to visually verify exact corner boundaries and LED counts.
     */
    suspend fun sendEdgeCalibrationPattern(
        ip: String,
        port: Int,
        leftLeds: Int,
        topLeds: Int,
        rightLeds: Int,
        bottomLeds: Int
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val totalLeds = leftLeds + topLeds + rightLeds + bottomLeds
            val colors = IntArray(totalLeds)

            for (i in 0 until leftLeds) {
                colors[i] = 0xFF0000
            }

            val topEnd = leftLeds + topLeds
            for (i in leftLeds until topEnd) {
                colors[i] = 0x00FF00
            }

            val rightEnd = topEnd + rightLeds
            for (i in topEnd until rightEnd) {
                colors[i] = 0x0064FF
            }

            for (i in rightEnd until totalLeds) {
                colors[i] = 0xFFE600
            }

            sendFrame(ip, port, colors)
            Log.i(TAG, "4-Edge Color Calibration pattern sent to $ip:$port ($totalLeds LEDs)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed sending 4-edge calibration pattern: ${e.message}", e)
            false
        }
    }

    /**
     * 4-Corner Calibration Test Pattern:
     * Lights exactly 2 LEDs on each side of every corner bend (4 LEDs per corner, 16 LEDs total):
     * - Bottom-Left: RED (0xFF0000)
     * - Top-Left: GREEN (0x00FF00)
     * - Top-Right: BLUE (0x0078FF)
     * - Bottom-Right: YELLOW (0xFFE600)
     * All other LEDs are completely off (0x000000).
     */
    suspend fun sendCornerCalibrationPattern(
        ip: String,
        port: Int,
        leftLeds: Int,
        topLeds: Int,
        rightLeds: Int,
        bottomLeds: Int
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val totalLeds = leftLeds + topLeds + rightLeds + bottomLeds
            val colors = IntArray(totalLeds)

            // 1. Top-Left: between (leftLeds - 1) and leftLeds
            if (leftLeds >= 2) {
                colors[leftLeds - 2] = 0x00FF00
                colors[leftLeds - 1] = 0x00FF00
            }
            if (leftLeds < totalLeds) colors[leftLeds] = 0x00FF00
            if (leftLeds + 1 < totalLeds) colors[leftLeds + 1] = 0x00FF00

            // 2. Top-Right: between (leftLeds + topLeds - 1) and (leftLeds + topLeds)
            val trCorner = leftLeds + topLeds
            if (trCorner - 2 >= 0) colors[trCorner - 2] = 0x0078FF
            if (trCorner - 1 >= 0) colors[trCorner - 1] = 0x0078FF
            if (trCorner < totalLeds) colors[trCorner] = 0x0078FF
            if (trCorner + 1 < totalLeds) colors[trCorner + 1] = 0x0078FF

            // 3. Bottom-Right: between (leftLeds + topLeds + rightLeds - 1) and (leftLeds + topLeds + rightLeds)
            val brCorner = leftLeds + topLeds + rightLeds
            if (brCorner - 2 >= 0) colors[brCorner - 2] = 0xFFE600
            if (brCorner - 1 >= 0) colors[brCorner - 1] = 0xFFE600
            if (brCorner < totalLeds) colors[brCorner] = 0xFFE600
            if (brCorner + 1 < totalLeds) colors[brCorner + 1] = 0xFFE600

            // 4. Bottom-Left: between (totalLeds - 1) and 0
            if (totalLeds >= 3) {
                colors[totalLeds - 3] = 0xFF0000
            }
            if (totalLeds >= 2) {
                colors[totalLeds - 2] = 0xFF0000
                colors[totalLeds - 1] = 0xFF0000
            }
            if (totalLeds > 0) colors[0] = 0xFF0000
            if (totalLeds > 1) colors[1] = 0xFF0000

            sendFrame(ip, port, colors)
            Log.i(TAG, "4-Corner Calibration pattern sent to $ip:$port ($totalLeds LEDs)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed sending 4-corner calibration pattern: ${e.message}", e)
            false
        }
    }

    fun close() {
        try {
            socket?.close()
            socket = null
        } catch (_: Exception) {}
    }
}
