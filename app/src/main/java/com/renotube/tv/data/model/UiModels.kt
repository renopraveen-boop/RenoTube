package com.renotube.tv.data.model

import androidx.compose.runtime.Immutable
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata
import com.liskovsoft.mediaserviceinterfaces.data.SponsorSegment

@Immutable
data class VideoCardModel(
    val videoId: String,
    val title: String,
    val author: String,
    val secondTitle: String = "",
    val thumbnailUrl: String,
    val backdropUrl: String? = null,
    val durationText: String = "",
    val durationMs: Long = 0,
    val percentWatched: Int = 0,
    val isLive: Boolean = false,
    val isUpcoming: Boolean = false,
    val channelId: String? = null,
    val badgeText: String? = null,
    val clickTrackingParams: String? = null
) {
    companion object {
        fun fromMediaItem(item: MediaItem): VideoCardModel {
            val totalSec = item.durationMs / 1000
            val h = totalSec / 3600
            val m = (totalSec % 3600) / 60
            val s = totalSec % 60
            val durationFormatted = when {
                item.isLive -> "LIVE"
                h > 0 -> "%d:%02d:%02d".format(h, m, s)
                totalSec > 0 -> "%d:%02d".format(m, s)
                else -> ""
            }

            val sanitizedThumb = sanitizeUrl(item.cardImageUrl, item.videoId)
                .ifEmpty { sanitizeUrl(item.backgroundImageUrl, item.videoId) }

            val (cleanedAuthor, cleanedSecondTitle) = cleanAuthorAndDetails(item.author, item.secondTitle?.toString())

            return VideoCardModel(
                videoId = item.videoId ?: "",
                title = item.title ?: "Untitled",
                author = cleanedAuthor,
                secondTitle = cleanedSecondTitle,
                thumbnailUrl = sanitizedThumb,
                backdropUrl = sanitizeUrl(item.backgroundImageUrl, item.videoId).ifEmpty { sanitizedThumb },
                durationText = durationFormatted,
                durationMs = item.durationMs,
                percentWatched = item.percentWatched,
                isLive = item.isLive,
                isUpcoming = item.isUpcoming,
                channelId = item.channelId,
                badgeText = item.badgeText,
                clickTrackingParams = item.clickTrackingParams
            )
        }

        fun cleanAuthorAndDetails(rawAuthor: String?, rawSecondTitle: String?): Pair<String, String> {
            val authorParts = (rawAuthor ?: "").split(Regex("[•·|]")).map { it.trim() }.filter { it.isNotEmpty() }
            val cleanAuthor = authorParts.firstOrNull { !it.startsWith("@") }
                ?: authorParts.firstOrNull()
                ?: ""

            val secondParts = (rawSecondTitle ?: "").split(Regex("[•·|]")).map { it.trim() }.filter { it.isNotEmpty() }
            val cleanSecondParts = mutableListOf<String>()

            val authorNorm = cleanAuthor.replace(" ", "").lowercase()
            for (part in secondParts) {
                if (part.equals(cleanAuthor, ignoreCase = true)) continue
                val partNorm = part.removePrefix("@").replace(" ", "").replace("-", "").replace("_", "").lowercase()
                if (part.startsWith("@") && (partNorm == authorNorm || authorNorm.contains(partNorm) || partNorm.contains(authorNorm))) continue
                if (!cleanSecondParts.contains(part)) {
                    cleanSecondParts.add(part)
                }
            }

            val cleanSecond = cleanSecondParts.joinToString(" • ")
            return Pair(cleanAuthor, cleanSecond)
        }

        private fun sanitizeUrl(rawUrl: String?, videoId: String?): String {
            if (rawUrl.isNullOrBlank()) {
                return if (!videoId.isNullOrBlank()) "https://i.ytimg.com/vi/$videoId/hqdefault.jpg" else ""
            }
            val trimmed = rawUrl.trim()
            if (!videoId.isNullOrBlank() && (
                trimmed.contains("yt3.ggpht.com") ||
                trimmed.contains("yt4.ggpht.com") ||
                trimmed.contains("/channel/") ||
                trimmed.contains("/channels/")
            )) {
                return "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
            }
            return when {
                trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
                trimmed.startsWith("//") -> "https:$trimmed"
                !videoId.isNullOrBlank() -> "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                else -> trimmed
            }
        }
    }
}

@Immutable
data class SectionRowModel(
    val title: String,
    val type: Int = 0,
    val nextPageKey: String? = null,
    val items: List<VideoCardModel>
) {
    companion object {
        fun fromMediaGroup(group: MediaGroup): SectionRowModel {
            val cards = group.mediaItems
                ?.filter { it != null && !it.videoId.isNullOrBlank() }
                ?.map { VideoCardModel.fromMediaItem(it) }
                ?: emptyList()

            val fallbackTitle = when (group.type) {
                MediaGroup.TYPE_TRENDING -> "Trending"
                MediaGroup.TYPE_RECOMMENDED -> "Recommended"
                MediaGroup.TYPE_MUSIC -> "Music"
                MediaGroup.TYPE_GAMING -> "Gaming"
                MediaGroup.TYPE_NEWS -> "News"
                MediaGroup.TYPE_SPORTS -> "Sports"
                MediaGroup.TYPE_LIVE -> "Live Now"
                MediaGroup.TYPE_SUBSCRIPTIONS -> "Subscriptions"
                MediaGroup.TYPE_HISTORY -> "Watch History"
                else -> "Recommended for You"
            }

            return SectionRowModel(
                title = group.title?.takeIf { it.isNotBlank() } ?: fallbackTitle,
                type = group.type,
                nextPageKey = group.nextPageKey,
                items = cards
            )
        }
    }
}

@Immutable
data class VideoPlaybackMetadata(
    val videoId: String,
    val title: String,
    val description: String,
    val author: String,
    val authorAvatarUrl: String?,
    val subscriberCount: String?,
    val viewCount: String?,
    val likeCount: String?,
    val dislikeCount: String?,
    val publishedDate: String?,
    val isSubscribed: Boolean,
    val isLive: Boolean,
    val chapters: List<ChapterModel>,
    val suggestions: List<VideoCardModel>,
    val suggestionSections: List<SectionRowModel> = emptyList(),
    val nextVideo: VideoCardModel?
) {
    companion object {
        fun fromMediaItemMetadata(meta: MediaItemMetadata): VideoPlaybackMetadata {
            val chaptersList = meta.chapters?.map {
                ChapterModel(it.title ?: "", it.startTimeMs)
            } ?: emptyList()

            val nextCard = meta.nextVideo?.let { VideoCardModel.fromMediaItem(it) }
            val rawSuggestions = meta.suggestions?.flatMap { group ->
                group.mediaItems?.map { VideoCardModel.fromMediaItem(it) } ?: emptyList()
            } ?: emptyList()

            val suggestionCards = mutableListOf<VideoCardModel>()
            if (nextCard != null && nextCard.videoId.isNotBlank()) {
                suggestionCards.add(nextCard)
            }
            rawSuggestions.forEach { card ->
                if (card.videoId.isNotBlank() && suggestionCards.none { it.videoId == card.videoId }) {
                    suggestionCards.add(card)
                }
            }

            // Populate multiple suggestion shelves matching YouTube TV layout & algorithm
            val sections = mutableListOf<SectionRowModel>()
            meta.suggestions?.forEachIndexed { index, group ->
                if (group != null) {
                    val groupCards = group.mediaItems
                        ?.filterNotNull()
                        ?.filter { !it.videoId.isNullOrBlank() }
                        ?.map { VideoCardModel.fromMediaItem(it) }
                        ?: emptyList()
                    if (groupCards.isNotEmpty()) {
                        val rowTitle = group.title?.takeIf { it.isNotBlank() }
                            ?: if (index == 0) "Up Next" else "Related Videos"
                        sections.add(
                            SectionRowModel(
                                title = rowTitle,
                                type = group.type,
                                nextPageKey = group.nextPageKey,
                                items = groupCards
                            )
                        )
                    }
                }
            }

            // Partition into exactly 3 shelves of 10 cards each (30 suggestions total) for Leanback TV 3-row layout:
            val allSuggestions = suggestionCards.filter { it.videoId.isNotBlank() }
            val row1Items = allSuggestions.take(10)
            val row2Items = allSuggestions.drop(10).take(10)
            val row3Items = allSuggestions.drop(20).take(10)

            val threeRowSections = mutableListOf<SectionRowModel>()
            if (row1Items.isNotEmpty()) {
                threeRowSections.add(SectionRowModel(title = "Up Next", items = row1Items))
            }
            if (row2Items.isNotEmpty()) {
                val row2Title = if (!meta.author.isNullOrBlank()) "More from ${meta.author}" else "Related Videos"
                threeRowSections.add(SectionRowModel(title = row2Title, items = row2Items))
            }
            if (row3Items.isNotEmpty()) {
                threeRowSections.add(SectionRowModel(title = "Recommended for You", items = row3Items))
            }
            if (threeRowSections.isNotEmpty()) {
                sections.clear()
                sections.addAll(threeRowSections)
            }

            return VideoPlaybackMetadata(
                videoId = meta.videoId ?: "",
                title = meta.title ?: "",
                description = meta.description ?: "",
                author = meta.author ?: "",
                authorAvatarUrl = meta.authorImageUrl,
                subscriberCount = meta.subscriberCount,
                viewCount = meta.viewCount,
                likeCount = meta.likeCount,
                dislikeCount = meta.dislikeCount,
                publishedDate = meta.publishedDate,
                isSubscribed = meta.isSubscribed,
                isLive = meta.isLive,
                chapters = chaptersList,
                suggestions = suggestionCards,
                suggestionSections = sections,
                nextVideo = nextCard
            )
        }
    }
}

@Immutable
data class ChapterModel(
    val title: String,
    val startTimeMs: Long
)

@Immutable
data class SponsorSegmentModel(
    val startMs: Long,
    val endMs: Long,
    val category: String
) {
    companion object {
        fun fromSponsorSegment(segment: SponsorSegment): SponsorSegmentModel {
            return SponsorSegmentModel(
                startMs = segment.startMs,
                endMs = segment.endMs,
                category = segment.category ?: "sponsor"
            )
        }
    }
}
