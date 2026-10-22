package com.renotube.tv.perf

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class LatencyMetrics(
    val lastApiName: String = "Idle",
    val lastApiLatencyMs: Long = 0L,
    val isApiSuccess: Boolean = true,
    val streamStartLatencyMs: Long = 0L,
    val activeVideoId: String = "",
    val bufferHealthSec: Float = 0f,
    val heapUsedMb: Int = 0,
    val isHudVisible: Boolean = true
)

object PerformanceTracker {
    private val _metrics = MutableStateFlow(LatencyMetrics())
    val metrics: StateFlow<LatencyMetrics> = _metrics.asStateFlow()

    private var streamClickTimeMs = 0L
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        // Background ticker updating RAM and system stats every 2 seconds
        val memoryRunnable = object : Runnable {
            override fun run() {
                val runtime = Runtime.getRuntime()
                val usedMem = ((runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)).toInt()
                _metrics.update { it.copy(heapUsedMb = usedMem) }
                mainHandler.postDelayed(this, 2000)
            }
        }
        mainHandler.post(memoryRunnable)
    }

    fun recordApiStart(): Long = System.currentTimeMillis()

    fun recordApiLatency(name: String, startTimeMs: Long, isSuccess: Boolean = true) {
        val duration = System.currentTimeMillis() - startTimeMs
        _metrics.update {
            it.copy(
                lastApiName = name,
                lastApiLatencyMs = duration,
                isApiSuccess = isSuccess
            )
        }
    }

    fun onStreamClicked(videoId: String) {
        streamClickTimeMs = System.currentTimeMillis()
        _metrics.update {
            it.copy(
                activeVideoId = videoId,
                streamStartLatencyMs = 0L
            )
        }
    }

    fun onStreamReady() {
        if (streamClickTimeMs > 0L) {
            val latency = System.currentTimeMillis() - streamClickTimeMs
            streamClickTimeMs = 0L
            _metrics.update {
                it.copy(streamStartLatencyMs = latency)
            }
        }
    }

    fun updateBufferHealth(bufferMs: Long) {
        val bufferSec = (bufferMs / 1000f).coerceAtLeast(0f)
        _metrics.update { it.copy(bufferHealthSec = bufferSec) }
    }

    fun toggleHud() {
        _metrics.update { it.copy(isHudVisible = !it.isHudVisible) }
    }
}
