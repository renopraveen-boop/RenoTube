package com.renotube.tv.data.repository

import android.content.Context
import android.util.Log
import com.renotube.tv.data.model.VideoCardModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "UserHistoryRepo"
private const val HISTORY_FILE_NAME = "user_watch_history.json"
private const val BLOCKED_FILE_NAME = "user_blocked_content.json"
private const val PLAYLISTS_FILE_NAME = "user_playlists.json"
private const val MAX_HISTORY_RECORDS = 100

data class BlockedContent(
    val blockedChannels: MutableSet<String> = mutableSetOf(),
    val hiddenVideoIds: MutableSet<String> = mutableSetOf()
)

data class HistoryEntry(
    val videoId: String,
    val title: String,
    val author: String,
    var thumbnailUrl: String,
    val durationText: String = "",
    var viewCount: Int = 1,
    var timestamp: Long = System.currentTimeMillis()
) {
    fun toVideoCardModel(): VideoCardModel {
        val cleanThumb = when {
            thumbnailUrl.isNotBlank() &&
                !thumbnailUrl.contains("yt3.ggpht.com") &&
                !thumbnailUrl.contains("yt4.ggpht.com") &&
                !thumbnailUrl.contains("/channel/") &&
                !thumbnailUrl.contains("/channels/") -> thumbnailUrl
            videoId.isNotBlank() -> "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
            else -> thumbnailUrl
        }
        return VideoCardModel(
            videoId = videoId,
            title = title,
            author = author,
            thumbnailUrl = cleanThumb,
            backdropUrl = if (videoId.isNotBlank()) "https://i.ytimg.com/vi/$videoId/hqdefault.jpg" else cleanThumb,
            durationText = durationText,
            secondTitle = if (viewCount > 1) "Played $viewCount times" else "Recently watched"
        )
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", videoId)
        put("title", title)
        put("author", author)
        put("thumb", thumbnailUrl)
        put("duration", durationText)
        put("views", viewCount)
        put("time", timestamp)
    }

    companion object {
        fun fromJson(obj: JSONObject): HistoryEntry? {
            val id = obj.optString("id")
            if (id.isNullOrEmpty()) return null
            var thumb = obj.optString("thumb", "")
            if (thumb.isBlank() || thumb.contains("yt3.ggpht.com") || thumb.contains("yt4.ggpht.com") || thumb.contains("/channel/")) {
                thumb = "https://i.ytimg.com/vi/$id/hqdefault.jpg"
            }
            return HistoryEntry(
                videoId = id,
                title = obj.optString("title", ""),
                author = obj.optString("author", ""),
                thumbnailUrl = thumb,
                durationText = obj.optString("duration", ""),
                viewCount = obj.optInt("views", 1),
                timestamp = obj.optLong("time", System.currentTimeMillis())
            )
        }
    }
}

@Singleton
class UserHistoryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val signInService: com.liskovsoft.mediaserviceinterfaces.SignInService
) {
    private val historyFile: File by lazy { File(context.filesDir, HISTORY_FILE_NAME) }
    private val historyGzFile: File by lazy { File(context.filesDir, "$HISTORY_FILE_NAME.gz") }
    private val blockedFile: File by lazy { File(context.filesDir, BLOCKED_FILE_NAME) }
    private val playlistFile: File by lazy { File(context.filesDir, PLAYLISTS_FILE_NAME) }
    private val playlistGzFile: File by lazy { File(context.filesDir, "$PLAYLISTS_FILE_NAME.gz") }
    private val inMemoryEntries = mutableListOf<HistoryEntry>()
    private val inMemoryBlocked = BlockedContent()
    private val inMemoryPlaylists = mutableMapOf<String, MutableList<HistoryEntry>>()
    private var isLoaded = false
    private var isBlockedLoaded = false
    private var isPlaylistLoaded = false

    init {
        try {
            signInService.addOnAccountChange { account ->
                if (account != null) {
                    compressLocalHistoryIfNeeded()
                } else {
                    decompressLocalHistoryIfNeeded()
                }
            }
        } catch (_: Exception) {}
        if (signInService.isSigned) {
            compressLocalHistoryIfNeeded()
        } else if (historyGzFile.exists() && !historyFile.exists()) {
            decompressLocalHistoryIfNeeded()
        }
    }

    fun compressLocalHistoryIfNeeded() {
        if (!signInService.isSigned) return
        synchronized(inMemoryEntries) {
            try {
                if (historyFile.exists() && historyFile.length() > 0) {
                    val bytes = historyFile.readBytes()
                    java.util.zip.GZIPOutputStream(historyGzFile.outputStream()).use { gz ->
                        gz.write(bytes)
                    }
                    historyFile.delete()
                    Log.i(TAG, "Compressed local watch history (${bytes.size} bytes -> ${historyGzFile.length()} bytes gz)")
                }
                if (playlistFile.exists() && playlistFile.length() > 0) {
                    val bytes = playlistFile.readBytes()
                    java.util.zip.GZIPOutputStream(playlistGzFile.outputStream()).use { gz ->
                        gz.write(bytes)
                    }
                    playlistFile.delete()
                    Log.i(TAG, "Compressed local playlists (${bytes.size} bytes -> ${playlistGzFile.length()} bytes gz)")
                }
                inMemoryEntries.clear()
                inMemoryPlaylists.clear()
                isLoaded = false
                isPlaylistLoaded = false
            } catch (e: Exception) {
                Log.w(TAG, "Failed compressing local data: ${e.message}")
            }
        }
    }

    fun decompressLocalHistoryIfNeeded() {
        synchronized(inMemoryEntries) {
            try {
                if (historyGzFile.exists()) {
                    java.util.zip.GZIPInputStream(historyGzFile.inputStream()).use { gz ->
                        historyFile.outputStream().use { out ->
                            gz.copyTo(out)
                        }
                    }
                    historyGzFile.delete()
                    Log.i(TAG, "Decompressed local watch history after Google Account logout")
                }
                if (playlistGzFile.exists()) {
                    java.util.zip.GZIPInputStream(playlistGzFile.inputStream()).use { gz ->
                        playlistFile.outputStream().use { out ->
                            gz.copyTo(out)
                        }
                    }
                    playlistGzFile.delete()
                    Log.i(TAG, "Decompressed local playlists after Google Account logout")
                }
                isLoaded = false
                isPlaylistLoaded = false
                loadFromDiskIfNeeded()
                loadPlaylistsFromDiskIfNeeded()
            } catch (e: Exception) {
                Log.w(TAG, "Failed decompressing local data: ${e.message}")
            }
        }
    }

    private fun loadBlockedFromDiskIfNeeded() {
        if (isBlockedLoaded) return
        synchronized(inMemoryBlocked) {
            if (isBlockedLoaded) return
            inMemoryBlocked.blockedChannels.clear()
            inMemoryBlocked.hiddenVideoIds.clear()
            if (blockedFile.exists()) {
                try {
                    val root = JSONObject(blockedFile.readText())
                    val channels = root.optJSONArray("channels")
                    if (channels != null) {
                        for (i in 0 until channels.length()) {
                            val ch = channels.optString(i)
                            if (ch.isNotBlank()) inMemoryBlocked.blockedChannels.add(ch)
                        }
                    }
                    val videos = root.optJSONArray("videos")
                    if (videos != null) {
                        for (i in 0 until videos.length()) {
                            val v = videos.optString(i)
                            if (v.isNotBlank()) inMemoryBlocked.hiddenVideoIds.add(v)
                        }
                    }
                    Log.d(TAG, "Loaded ${inMemoryBlocked.blockedChannels.size} blocked channels and ${inMemoryBlocked.hiddenVideoIds.size} hidden videos (<10KB footprint)")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed reading blocked content: ${e.message}")
                }
            }
            isBlockedLoaded = true
        }
    }

    private fun saveBlockedToDisk() {
        synchronized(inMemoryBlocked) {
            try {
                val root = JSONObject()
                val channels = JSONArray()
                inMemoryBlocked.blockedChannels.forEach { channels.put(it) }
                val videos = JSONArray()
                inMemoryBlocked.hiddenVideoIds.forEach { videos.put(it) }
                root.put("channels", channels)
                root.put("videos", videos)
                blockedFile.writeText(root.toString())
            } catch (e: Exception) {
                Log.w(TAG, "Failed saving blocked content: ${e.message}")
            }
        }
    }

    suspend fun blockChannel(author: String) = withContext(Dispatchers.IO) {
        val trimmed = author.trim()
        if (trimmed.isEmpty()) return@withContext
        loadBlockedFromDiskIfNeeded()
        synchronized(inMemoryBlocked) {
            inMemoryBlocked.blockedChannels.add(trimmed)
            saveBlockedToDisk()
        }
        Log.i(TAG, "Channel blocked: '$trimmed'")
    }

    suspend fun unblockChannel(author: String) = withContext(Dispatchers.IO) {
        val trimmed = author.trim()
        loadBlockedFromDiskIfNeeded()
        synchronized(inMemoryBlocked) {
            inMemoryBlocked.blockedChannels.remove(trimmed)
            saveBlockedToDisk()
        }
    }

    suspend fun hideVideo(videoId: String) = withContext(Dispatchers.IO) {
        val trimmed = videoId.trim()
        if (trimmed.isEmpty()) return@withContext
        loadBlockedFromDiskIfNeeded()
        synchronized(inMemoryBlocked) {
            inMemoryBlocked.hiddenVideoIds.add(trimmed)
            saveBlockedToDisk()
        }
        Log.i(TAG, "Video hidden: '$trimmed'")
    }

    suspend fun getBlockedChannels(): Set<String> = withContext(Dispatchers.IO) {
        loadBlockedFromDiskIfNeeded()
        synchronized(inMemoryBlocked) {
            inMemoryBlocked.blockedChannels.toSet()
        }
    }

    suspend fun getHiddenVideoIds(): Set<String> = withContext(Dispatchers.IO) {
        loadBlockedFromDiskIfNeeded()
        synchronized(inMemoryBlocked) {
            inMemoryBlocked.hiddenVideoIds.toSet()
        }
    }

    suspend fun clearBlockedContent() = withContext(Dispatchers.IO) {
        synchronized(inMemoryBlocked) {
            inMemoryBlocked.blockedChannels.clear()
            inMemoryBlocked.hiddenVideoIds.clear()
            if (blockedFile.exists()) blockedFile.delete()
        }
    }

    fun filterBlocked(videos: List<VideoCardModel>): List<VideoCardModel> {
        loadBlockedFromDiskIfNeeded()
        synchronized(inMemoryBlocked) {
            if (inMemoryBlocked.blockedChannels.isEmpty() && inMemoryBlocked.hiddenVideoIds.isEmpty()) {
                return videos
            }
            return videos.filter { video ->
                !inMemoryBlocked.hiddenVideoIds.contains(video.videoId) &&
                !inMemoryBlocked.blockedChannels.contains(video.author)
            }
        }
    }

    private fun loadFromDiskIfNeeded() {
        if (isLoaded) return
        synchronized(inMemoryEntries) {
            if (isLoaded) return
            inMemoryEntries.clear()
            if (historyFile.exists()) {
                try {
                    val content = historyFile.readText()
                    val array = JSONArray(content)
                    for (i in 0 until array.length()) {
                        val obj = array.optJSONObject(i)
                        if (obj != null) {
                            HistoryEntry.fromJson(obj)?.let { inMemoryEntries.add(it) }
                        }
                    }
                    Log.d(TAG, "Loaded ${inMemoryEntries.size} history items from disk (<40KB footprint)")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed reading watch history: ${e.message}")
                }
            }
            isLoaded = true
        }
    }

    private fun saveToDisk() {
        synchronized(inMemoryEntries) {
            try {
                val array = JSONArray()
                // Keep only top MAX_HISTORY_RECORDS sorted by timestamp descending
                inMemoryEntries.sortByDescending { it.timestamp }
                while (inMemoryEntries.size > MAX_HISTORY_RECORDS) {
                    inMemoryEntries.removeAt(inMemoryEntries.size - 1)
                }
                for (entry in inMemoryEntries) {
                    array.put(entry.toJson())
                }
                historyFile.writeText(array.toString())
            } catch (e: Exception) {
                Log.w(TAG, "Failed saving watch history: ${e.message}")
            }
        }
    }

    suspend fun recordView(
        videoId: String,
        title: String,
        author: String,
        thumbnailUrl: String,
        durationText: String = ""
    ) = withContext(Dispatchers.IO) {
        if (videoId.isBlank() || signInService.isSigned) return@withContext
        val cleanThumb = if (thumbnailUrl.isNotBlank() &&
            !thumbnailUrl.contains("yt3.ggpht.com") &&
            !thumbnailUrl.contains("yt4.ggpht.com") &&
            !thumbnailUrl.contains("/channel/") &&
            !thumbnailUrl.contains("/channels/")
        ) {
            thumbnailUrl
        } else {
            "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
        }
        loadFromDiskIfNeeded()
        synchronized(inMemoryEntries) {
            val existing = inMemoryEntries.firstOrNull { it.videoId == videoId }
            if (existing != null) {
                existing.viewCount += 1
                existing.timestamp = System.currentTimeMillis()
                if (title.isNotEmpty()) existing.title.ifEmpty { title }
                existing.thumbnailUrl = cleanThumb
            } else {
                inMemoryEntries.add(
                    0,
                    HistoryEntry(
                        videoId = videoId,
                        title = title,
                        author = author,
                        thumbnailUrl = cleanThumb,
                        durationText = durationText,
                        viewCount = 1,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
            saveToDisk()
        }
    }

    suspend fun getRecentlyWatched(limit: Int = 20): List<VideoCardModel> = withContext(Dispatchers.IO) {
        if (signInService.isSigned) return@withContext emptyList()
        loadFromDiskIfNeeded()
        synchronized(inMemoryEntries) {
            val filtered = inMemoryEntries.sortedByDescending { it.timestamp }
                .take(limit)
                .map { it.toVideoCardModel() }
            filterBlocked(filtered)
        }
    }

    suspend fun getMostPlayed(limit: Int = 20): List<VideoCardModel> = withContext(Dispatchers.IO) {
        loadFromDiskIfNeeded()
        synchronized(inMemoryEntries) {
            val filtered = inMemoryEntries.filter { it.viewCount >= 2 }
                .sortedByDescending { it.viewCount }
                .take(limit)
                .map { it.toVideoCardModel() }
            filterBlocked(filtered)
        }
    }

    suspend fun getTopAuthors(limit: Int = 4): List<String> = withContext(Dispatchers.IO) {
        loadFromDiskIfNeeded()
        loadBlockedFromDiskIfNeeded()
        synchronized(inMemoryEntries) {
            val authorCounts = mutableMapOf<String, Int>()
            for (entry in inMemoryEntries) {
                if (entry.author.isNotBlank() && entry.author.length > 2) {
                    synchronized(inMemoryBlocked) {
                        if (!inMemoryBlocked.blockedChannels.contains(entry.author)) {
                            authorCounts[entry.author] = authorCounts.getOrDefault(entry.author, 0) + entry.viewCount
                        }
                    }
                }
            }
            authorCounts.entries.sortedByDescending { it.value }
                .take(limit)
                .map { it.key }
        }
    }

    private fun loadPlaylistsFromDiskIfNeeded() {
        if (isPlaylistLoaded) return
        synchronized(inMemoryPlaylists) {
            if (isPlaylistLoaded) return
            inMemoryPlaylists.clear()
            if (playlistFile.exists()) {
                try {
                    val root = JSONObject(playlistFile.readText())
                    val keys = root.keys()
                    while (keys.hasNext()) {
                        val name = keys.next()
                        val arr = root.optJSONArray(name)
                        val list = mutableListOf<HistoryEntry>()
                        if (arr != null) {
                            for (i in 0 until arr.length()) {
                                val obj = arr.optJSONObject(i)
                                if (obj != null) {
                                    HistoryEntry.fromJson(obj)?.let { list.add(it) }
                                }
                            }
                        }
                        inMemoryPlaylists[name] = list
                    }
                    Log.d(TAG, "Loaded playlists (${inMemoryPlaylists.keys.joinToString()}) from disk")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed reading playlists: ${e.message}")
                }
            }
            isPlaylistLoaded = true
        }
    }

    private fun savePlaylistsToDisk() {
        synchronized(inMemoryPlaylists) {
            try {
                val root = JSONObject()
                for ((name, items) in inMemoryPlaylists) {
                    val arr = JSONArray()
                    for (item in items) {
                        arr.put(item.toJson())
                    }
                    root.put(name, arr)
                }
                playlistFile.writeText(root.toString())
            } catch (e: Exception) {
                Log.w(TAG, "Failed saving playlists: ${e.message}")
            }
        }
    }

    suspend fun addToPlaylist(video: VideoCardModel, playlistName: String = "Watch Later"): Boolean = withContext(Dispatchers.IO) {
        if (video.videoId.isBlank()) return@withContext false
        loadPlaylistsFromDiskIfNeeded()
        synchronized(inMemoryPlaylists) {
            val list = inMemoryPlaylists.getOrPut(playlistName) { mutableListOf() }
            val existingIndex = list.indexOfFirst { it.videoId == video.videoId }
            if (existingIndex >= 0) {
                // Already in playlist
                return@withContext false
            }
            list.add(
                0,
                HistoryEntry(
                    videoId = video.videoId,
                    title = video.title,
                    author = video.author,
                    thumbnailUrl = video.thumbnailUrl,
                    durationText = video.durationText,
                    viewCount = 1,
                    timestamp = System.currentTimeMillis()
                )
            )
            savePlaylistsToDisk()
            Log.i(TAG, "Added '${video.title}' to playlist '$playlistName'")
            true
        }
    }

    suspend fun removeFromPlaylist(videoId: String, playlistName: String = "Watch Later") = withContext(Dispatchers.IO) {
        loadPlaylistsFromDiskIfNeeded()
        synchronized(inMemoryPlaylists) {
            val list = inMemoryPlaylists[playlistName] ?: return@withContext
            list.removeAll { it.videoId == videoId }
            savePlaylistsToDisk()
        }
    }

    suspend fun getPlaylist(playlistName: String = "Watch Later"): List<VideoCardModel> = withContext(Dispatchers.IO) {
        loadPlaylistsFromDiskIfNeeded()
        synchronized(inMemoryPlaylists) {
            val list = inMemoryPlaylists[playlistName] ?: return@withContext emptyList()
            val models = list.map { it.toVideoCardModel() }
            filterBlocked(models)
        }
    }

    suspend fun isInPlaylist(videoId: String, playlistName: String = "Watch Later"): Boolean = withContext(Dispatchers.IO) {
        loadPlaylistsFromDiskIfNeeded()
        synchronized(inMemoryPlaylists) {
            val list = inMemoryPlaylists[playlistName] ?: return@withContext false
            list.any { it.videoId == videoId }
        }
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        synchronized(inMemoryEntries) {
            inMemoryEntries.clear()
            if (historyFile.exists()) {
                historyFile.delete()
            }
        }
    }
}
