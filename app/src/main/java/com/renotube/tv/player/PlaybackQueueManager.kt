package com.renotube.tv.player

import com.renotube.tv.data.model.VideoCardModel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackQueueManager @Inject constructor() {
    private val queue = mutableListOf<VideoCardModel>()

    /**
     * Prepend or insert video as the next video to be played.
     */
    fun playNext(video: VideoCardModel) {
        synchronized(queue) {
            queue.removeAll { it.videoId == video.videoId }
            queue.add(0, video)
        }
    }

    /**
     * Enqueue a video at the end of the playback queue.
     */
    fun enqueue(video: VideoCardModel) {
        synchronized(queue) {
            queue.removeAll { it.videoId == video.videoId }
            queue.add(video)
        }
    }

    /**
     * Retrieves and removes the next queued video.
     */
    fun pollNext(): VideoCardModel? {
        synchronized(queue) {
            return if (queue.isNotEmpty()) queue.removeAt(0) else null
        }
    }

    /**
     * Peeks at the next video without removing it.
     */
    fun peekNext(): VideoCardModel? {
        synchronized(queue) {
            return queue.firstOrNull()
        }
    }

    /**
     * Returns a snapshot of currently queued videos.
     */
    fun getQueue(): List<VideoCardModel> {
        synchronized(queue) {
            return queue.toList()
        }
    }

    fun remove(videoId: String) {
        synchronized(queue) {
            queue.removeAll { it.videoId == videoId }
        }
    }

    fun clearQueue() {
        synchronized(queue) {
            queue.clear()
        }
    }
}
