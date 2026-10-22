package com.renotube.tv.data.repository

import android.content.Context
import com.liskovsoft.mediaserviceinterfaces.MediaItemService
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo
import com.renotube.tv.data.model.SponsorSegmentModel
import com.renotube.tv.data.model.VideoPlaybackMetadata
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaItemService: MediaItemService
) {
    private val formatCache = ConcurrentHashMap<String, Pair<Long, MediaItemFormatInfo>>()
    private val CACHE_TTL_MS = 15 * 60 * 1000L // 15 minutes fresh cache

    var ambilightLowResUrl: String? = null
        private set

    /**
     * Extracts the lowest-resolution video-only adaptive stream URL from formatInfo:
     * 1. 144p H.264 (itag=160) - CPU H.264 software decoding uses <1% CPU
     * 2. 240p H.264 (itag=133)
     * 3. 144p VP9 (itag=278)
     * 4. 240p VP9 (itag=242)
     * 5. Any lowest-resolution video format (<= 240p)
     */
    fun extractLowResVideoStreamUrl(formatInfo: MediaItemFormatInfo?): String? {
        if (formatInfo == null) return null
        val formats = formatInfo.adaptiveFormats ?: return null

        // 1. Prefer 144p H.264 (itag 160)
        val itag160 = formats.firstOrNull { it.iTag == "160" && !it.url.isNullOrBlank() }
        if (itag160 != null) return itag160.url

        // 2. Prefer 240p H.264 (itag 133)
        val itag133 = formats.firstOrNull { it.iTag == "133" && !it.url.isNullOrBlank() }
        if (itag133 != null) return itag133.url

        // 3. Fallback to 144p VP9 (itag 278)
        val itag278 = formats.firstOrNull { it.iTag == "278" && !it.url.isNullOrBlank() }
        if (itag278 != null) return itag278.url

        // 4. Fallback to 240p VP9 (itag 242)
        val itag242 = formats.firstOrNull { it.iTag == "242" && !it.url.isNullOrBlank() }
        if (itag242 != null) return itag242.url

        // 5. Fallback to any low-res video stream (height in 1..240 or lowest height)
        val lowRes = formats
            .filter { (it.mimeType?.contains("video") == true || it.height in 1..240) && !it.url.isNullOrBlank() }
            .minByOrNull { it.height }
        return lowRes?.url
    }

    init {
        resetClientToDefault()
    }

    fun resetClientToDefault() {
        formatCache.clear()
        ambilightLowResUrl = null
        try {
            com.liskovsoft.youtubeapi.service.YouTubeServiceManager.instance().invalidateCache()
        } catch (e: Exception) {
            android.util.Log.w("MediaRepository", "Failed resetting format client: ${e.message}")
        }
    }

    private val prefetchMutex = kotlinx.coroutines.sync.Mutex()

    suspend fun getFormatInfo(videoId: String, clickTrackingParams: String? = null): MediaItemFormatInfo? =
        withContext(Dispatchers.IO) {
            val cached = formatCache[videoId]
            val now = System.currentTimeMillis()
            if (cached != null && (now - cached.first) < CACHE_TTL_MS && !cached.second.isUnplayable) {
                ambilightLowResUrl = extractLowResVideoStreamUrl(cached.second)
                return@withContext cached.second
            }
            
            val start = System.currentTimeMillis()
            var info: MediaItemFormatInfo? = null
            try {
                info = withTimeoutOrNull(8000) {
                    mediaItemService.getFormatInfo(videoId, clickTrackingParams)
                }
                
                // If primary format timed out or returned unplayable, perform soft failover (pot cache reset)
                if (info == null || info.isUnplayable) {
                    android.util.Log.w("MediaRepository", "Primary format unplayable for $videoId. Trying soft failover...")
                    com.liskovsoft.youtubeapi.service.YouTubeServiceManager.instance().switchNextClient()
                    info = withTimeoutOrNull(6000) {
                        mediaItemService.getFormatInfo(videoId, clickTrackingParams)
                    }
                    // If soft failover still unplayable, restore default TV client so next video is never stuck
                    if (info == null || info.isUnplayable) {
                        resetClientToDefault()
                    }
                }
            } catch (t: Throwable) {
                android.util.Log.e("MediaRepository", "Format info resolution failed for $videoId: ${t.message}")
            }

            val success = (info != null && !info.isUnplayable)
            com.renotube.tv.perf.PerformanceTracker.recordApiLatency("Resolve Stream", start, isSuccess = success)

            if (success) {
                formatCache[videoId] = Pair(now, info!!)
                ambilightLowResUrl = extractLowResVideoStreamUrl(info)
            }
            info
        }

    suspend fun prefetchFormatInfo(videoId: String) = withContext(Dispatchers.IO) {
        val cached = formatCache[videoId]
        val now = System.currentTimeMillis()
        if (cached != null && (now - cached.first) < CACHE_TTL_MS && !cached.second.isUnplayable) {
            return@withContext
        }

        if (prefetchMutex.tryLock()) {
            try {
                if (!formatCache.containsKey(videoId)) {
                    val info = mediaItemService.getFormatInfo(videoId, null)
                    if (info != null && !info.isUnplayable) {
                        formatCache[videoId] = Pair(System.currentTimeMillis(), info)
                        // Pre-generate DASH MPD file in cache directory so player starts in 0ms!
                        try {
                            val mpdFile = File(context.cacheDir, "manifest_${videoId}.mpd")
                            if (!mpdFile.exists() || mpdFile.length() == 0L) {
                                if (info.containsDashFormats() || info.containsSabrFormats()) {
                                    info.createMpdStream()?.use { input ->
                                        mpdFile.outputStream().buffered().use { output ->
                                            input.copyTo(output)
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.w("MediaRepository", "MPD prewarm non-fatal warning: ${e.message}")
                        }
                    }
                }
            } catch (t: Throwable) {
                android.util.Log.w("MediaRepository", "prefetchFormatInfo error for $videoId: ${t.message}")
            } finally {
                prefetchMutex.unlock()
            }
        }
    }

    suspend fun getMetadata(videoId: String): VideoPlaybackMetadata? = withContext(Dispatchers.IO) {
        val meta = mediaItemService.getMetadata(videoId) ?: return@withContext null
        VideoPlaybackMetadata.fromMediaItemMetadata(meta)
    }

    suspend fun getSponsorSegments(videoId: String): List<SponsorSegmentModel> = withContext(Dispatchers.IO) {
        val segments = mediaItemService.getSponsorSegments(videoId) ?: emptyList()
        segments.map { SponsorSegmentModel.fromSponsorSegment(it) }
    }

    suspend fun updateHistory(videoId: String, positionSec: Float) = withContext(Dispatchers.IO) {
        mediaItemService.updateHistoryPosition(videoId, positionSec)
    }

    suspend fun subscribe(channelId: String) = withContext(Dispatchers.IO) {
        mediaItemService.subscribe(channelId)
    }

    suspend fun unsubscribe(channelId: String) = withContext(Dispatchers.IO) {
        mediaItemService.unsubscribe(channelId)
    }

    suspend fun switchNextFormat(force: Boolean = true) = withContext(Dispatchers.IO) {
        formatCache.clear()
        ambilightLowResUrl = null
        if (force) {
            com.liskovsoft.youtubeapi.service.YouTubeServiceManager.instance().switchNextClientNow()
        } else {
            com.liskovsoft.youtubeapi.service.YouTubeServiceManager.instance().switchNextClient()
        }
    }
}

