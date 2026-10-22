package com.renotube.tv.ambilight

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AmbilightRepo"

data class WledInfoResult(
    val name: String,
    val totalLeds: Int,
    val version: String,
    val fps: Int
)

@Singleton
class AmbilightRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val udpSender: WledUdpSender,
    private val processorProvider: javax.inject.Provider<AmbilightProcessor>
) {
    private val prefs = context.getSharedPreferences("renotube_ambilight_prefs", Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(loadConfig())
    val config: StateFlow<AmbilightConfig> = _config.asStateFlow()

    private val repoScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var watcherJob: kotlinx.coroutines.Job? = null
    private var firstDisconnectTimeMs: Long? = null

    init {
        startDisconnectWatcher()
    }

    private fun startDisconnectWatcher() {
        watcherJob?.cancel()
        watcherJob = repoScope.launch {
            while (isActive) {
                delay(15_000) // Heartbeat probe every 15 seconds
                val current = _config.value
                if (!current.enabled || !current.autoTurnOffOnDisconnect || current.wledIp.isBlank()) {
                    firstDisconnectTimeMs = null
                    continue
                }

                val reachable = checkWledReachable(current.wledIp)
                if (reachable) {
                    firstDisconnectTimeMs = null
                } else {
                    val now = System.currentTimeMillis()
                    if (firstDisconnectTimeMs == null) {
                        firstDisconnectTimeMs = now
                        Log.w(TAG, "WLED at ${current.wledIp} unreachable. Started ${current.disconnectTimeoutMinutes}-minute disconnection timer.")
                    } else {
                        val elapsedMs = now - firstDisconnectTimeMs!!
                        val thresholdMs = current.disconnectTimeoutMinutes * 60 * 1000L
                        Log.d(TAG, "WLED disconnected for ${elapsedMs / 1000}s / ${thresholdMs / 1000}s")
                        if (elapsedMs >= thresholdMs) {
                            Log.w(TAG, "WLED disconnected for >= ${current.disconnectTimeoutMinutes} mins! Automatically turning off WLED option.")
                            firstDisconnectTimeMs = null
                            updateConfig(current.copy(enabled = false))
                        }
                    }
                }
            }
        }
    }

    suspend fun checkWledReachable(ip: String): Boolean = withContext(Dispatchers.IO) {
        if (ip.isBlank()) return@withContext false
        try {
            val url = java.net.URL("http://$ip/json/state")
            val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 2000
                readTimeout = 2000
                requestMethod = "GET"
            }
            val code = conn.responseCode
            conn.disconnect()
            code in 200..299
        } catch (_: Exception) {
            try {
                java.net.InetAddress.getByName(ip).isReachable(1500)
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun loadConfig(): AmbilightConfig {
        val savedIp = prefs.getString("wled_ip", "192.168.1.6") ?: "192.168.1.6"
        val effectiveIp = if (savedIp == "192.168.1.100" || savedIp.isBlank()) "192.168.1.6" else savedIp
        val savedPort = prefs.getInt("wled_port", 4048)
        val effectivePort = if (savedPort == 21324 || savedPort == 0) {
            prefs.edit().putInt("wled_port", 4048).apply()
            4048
        } else {
            savedPort
        }

        val top = prefs.getInt("top_leds", 71)
        val right = prefs.getInt("right_leds", 41)
        val rawBottom = prefs.getInt("bottom_leds", 76)
        val left = prefs.getInt("left_leds", 40)

        // Exact 228-LED layout (Left: 40, Top: 71, Right: 41, Bottom: 76) matching HyperHDR
        val (effectiveTop, effectiveRight, effectiveBottom, effectiveLeft) =
            if (top != 71 || right != 41 || rawBottom != 76 || left != 40) {
                prefs.edit()
                    .putInt("top_leds", 71)
                    .putInt("right_leds", 41)
                    .putInt("bottom_leds", 76)
                    .putInt("left_leds", 40)
                    .apply()
                listOf(71, 41, 76, 40)
            } else {
                listOf(top, right, rawBottom, left)
            }

        val isEnabled = if (prefs.contains("enabled_v2")) {
            prefs.getBoolean("enabled_v2", true)
        } else {
            true
        }

        return AmbilightConfig(
            enabled = isEnabled,
            wledIp = effectiveIp,
            wledPort = effectivePort,
            wledDeviceName = prefs.getString("wled_device_name", "WLED Lightstrip") ?: "WLED Lightstrip",
            topLeds = effectiveTop,
            rightLeds = effectiveRight,
            bottomLeds = effectiveBottom,
            leftLeds = effectiveLeft,
            bottomGapLeds = prefs.getInt("bottom_gap_leds", 0),
            startCorner = prefs.getString("start_corner", "BOTTOM_LEFT") ?: "BOTTOM_LEFT",
            direction = prefs.getString("direction", "CLOCKWISE") ?: "CLOCKWISE",
            brightness = 1.0f,
            saturationGain = 1.0f,
            colorTemperature = "NEUTRAL",
            smoothFactor = prefs.getFloat("smooth_factor", 0.35f),
            keepAmbientGlow = prefs.getBoolean("keep_ambient_glow", true),
            letterboxProtection = prefs.getBoolean("letterbox_protection", true),
            autoTurnOffOnDisconnect = prefs.getBoolean("auto_turn_off_on_disconnect", true),
            disconnectTimeoutMinutes = prefs.getInt("disconnect_timeout_minutes", 3)
        )
    }

    fun updateConfig(newConfig: AmbilightConfig) {
        prefs.edit().apply {
            putBoolean("enabled", newConfig.enabled)
            putBoolean("enabled_v2", newConfig.enabled)
            putString("wled_ip", newConfig.wledIp)
            putInt("wled_port", newConfig.wledPort)
            putString("wled_device_name", newConfig.wledDeviceName)
            putInt("top_leds", newConfig.topLeds)
            putInt("right_leds", newConfig.rightLeds)
            putInt("bottom_leds", newConfig.bottomLeds)
            putInt("left_leds", newConfig.leftLeds)
            putInt("bottom_gap_leds", newConfig.bottomGapLeds)
            putString("start_corner", newConfig.startCorner)
            putString("direction", newConfig.direction)
            putFloat("brightness", newConfig.brightness)
            putFloat("saturation_gain", newConfig.saturationGain)
            putString("color_temperature", newConfig.colorTemperature)
            putFloat("smooth_factor", newConfig.smoothFactor)
            putBoolean("keep_ambient_glow", newConfig.keepAmbientGlow)
            putBoolean("letterbox_protection", newConfig.letterboxProtection)
            putBoolean("auto_turn_off_on_disconnect", newConfig.autoTurnOffOnDisconnect)
            putInt("disconnect_timeout_minutes", newConfig.disconnectTimeoutMinutes)
            apply()
        }
        _config.value = newConfig
        if (newConfig.enabled && newConfig.wledIp.isNotBlank()) {
            repoScope.launch {
                ensureWledOn()
            }
        }
    }

    suspend fun ensureWledOn() = withContext(Dispatchers.IO) {
        val current = _config.value
        if (!current.enabled || current.wledIp.isBlank()) return@withContext
        try {
            val url = java.net.URL("http://${current.wledIp}/json/state")
            val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 1200
                readTimeout = 1200
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
            val jsonPayload = """{"on":true,"bri":255}"""
            conn.outputStream.use { it.write(jsonPayload.toByteArray(Charsets.UTF_8)) }
            conn.responseCode
            conn.disconnect()
            Log.d(TAG, "WLED powered on via HTTP at ${current.wledIp}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed ensureWledOn: ${e.message}")
        }
    }

    suspend fun setWarmWhite() = withContext(Dispatchers.IO) {
        val current = _config.value
        if (!current.enabled || current.wledIp.isBlank()) return@withContext
        try {
            ensureWledOn()
            // 1. Send solid warm white with live:false and lor:0 so WLED terminates live stream and stays warm white
            val url = java.net.URL("http://${current.wledIp}/json/state")
            val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 1500
                readTimeout = 1500
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
            val bri = (current.brightness.coerceIn(0.1f, 1.0f) * 255).toInt()
            val jsonPayload = """{"on":true,"bri":$bri,"live":false,"lor":0,"seg":[{"col":[[255,197,143],[0,0,0],[0,0,0]],"fx":0}]}"""
            conn.outputStream.use { it.write(jsonPayload.toByteArray(Charsets.UTF_8)) }
            conn.responseCode
            conn.disconnect()
            Log.d(TAG, "Warm white applied via HTTP to WLED at ${current.wledIp}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set warm white HTTP on WLED: ${e.message}")
        }
    }

    suspend fun turnOffLeds() = withContext(Dispatchers.IO) {
        val current = _config.value
        if (!current.enabled || current.wledIp.isBlank()) return@withContext
        if (current.keepAmbientGlow) {
            setWarmWhite()
            return@withContext
        }
        try {
            val url = java.net.URL("http://${current.wledIp}/json/state")
            val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 1500
                readTimeout = 1500
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
            val jsonPayload = """{"on":false,"live":false,"lor":0}"""
            conn.outputStream.use { it.write(jsonPayload.toByteArray(Charsets.UTF_8)) }
            conn.responseCode
            conn.disconnect()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to turn off WLED: ${e.message}")
        }
    }

    suspend fun testWledConnection(): Boolean {
        val current = _config.value
        ensureWledOn()
        return udpSender.sendTestPattern(current.wledIp, current.wledPort, current.totalLeds)
    }

    suspend fun sendEdgeCalibrationPattern(): Boolean {
        val current = _config.value
        ensureWledOn()
        return udpSender.sendEdgeCalibrationPattern(
            ip = current.wledIp,
            port = current.wledPort,
            leftLeds = current.leftLeds,
            topLeds = current.topLeds,
            rightLeds = current.rightLeds,
            bottomLeds = current.bottomLeds
        )
    }

    suspend fun sendCornerCalibrationPattern(): Boolean {
        val current = _config.value
        ensureWledOn()
        return udpSender.sendCornerCalibrationPattern(
            ip = current.wledIp,
            port = current.wledPort,
            leftLeds = current.leftLeds,
            topLeds = current.topLeds,
            rightLeds = current.rightLeds,
            bottomLeds = current.bottomLeds
        )
    }

    suspend fun sendCalibrationPattern() {
        val current = _config.value
        ensureWledOn()
        processorProvider.get().sendCalibrationPattern(current)
    }

    fun getLatestLedsJson(): String {
        return processorProvider.get().getLatestLedsJson()
    }

    /**
     * Connects to WLED device via official HTTP JSON API (/json/info) to discover
     * device name, active LED count, and live frame rate.
     */
    suspend fun detectWledDevice(ip: String): Result<WledInfoResult> = withContext(Dispatchers.IO) {
        try {
            val url = java.net.URL("http://$ip/json/info")
            val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 3000
                readTimeout = 3000
                requestMethod = "GET"
            }
            val responseCode = conn.responseCode
            if (responseCode == 200) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val json = JSONObject(body)
                val name = json.optString("name", "WLED Ambilight")
                val ledsObj = json.optJSONObject("leds")
                val count = ledsObj?.optInt("count", 96) ?: 96
                val fps = ledsObj?.optInt("fps", 30) ?: 30
                val ver = json.optString("ver", "0.14.0")

                // Automatically calculate 16:9 proportional LED distribution for the user
                // If count is 228 or 230 (matching this physical TV installation), preserve exact HyperHDR layout
                val top: Int
                val bottom: Int
                val left: Int
                val right: Int
                if (count == 228 || count == 230) {
                    top = 71
                    bottom = 76
                    left = 40
                    right = 41
                } else {
                    val unit = (count / 50.0).coerceAtLeast(0.5)
                    top = (16 * unit).toInt()
                    bottom = top
                    left = ((count - top - bottom) / 2).coerceAtLeast(1)
                    right = count - top - bottom - left
                }

                val current = _config.value
                val updated = current.copy(
                    wledIp = ip,
                    wledDeviceName = name,
                    topLeds = top,
                    bottomLeds = bottom,
                    leftLeds = left,
                    rightLeds = right
                )
                updateConfig(updated)

                return@withContext Result.success(WledInfoResult(name, count, ver, fps))
            } else {
                conn.disconnect()
                return@withContext Result.failure(Exception("HTTP error $responseCode"))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed auto-detecting WLED at $ip: ${e.message}")
            return@withContext Result.failure(e)
        }
    }
}
