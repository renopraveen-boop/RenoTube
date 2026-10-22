# SmartTube → YouTube for Android TV: Complete Analysis & Project Blueprint

> [!IMPORTANT]
> Full reverse-engineering and deep analysis of `MediaServiceCore` (220 Java/Kotlin files, ~17,000+ LoC). This is the **master blueprint** for building a new Android TV app that reuses all SmartTube backend algorithms and adds a pixel-perfect YouTube for TV UI.

---

## Table of Contents
1. [Repository Structure](#1-repository-structure)
2. [Core Architecture](#2-core-architecture)
3. [Subsystem Deep Dives](#3-subsystem-deep-dives)
4. [Data Models Reference](#4-data-models-reference)
5. [YouTube TV UI Layouts](#5-youtube-tv-ui-layouts)
6. [New App Architecture](#6-new-app-architecture)
7. [YouTube TV UI Components (Compose)](#7-youtube-tv-ui-components-compose)
8. [Full Feature Checklist](#8-full-feature-checklist)
9. [Development Roadmap](#9-development-roadmap)
10. [Dependencies](#10-dependencies)
11. [Critical Gotchas](#11-critical-gotchas)

---

## 1. Repository Structure

```
MediaServiceCore/
├── mediaserviceinterfaces/        ← Public interfaces & data contracts
│   └── .../mediaserviceinterfaces/
│       ├── ContentService.java       (browse: home, search, channels)
│       ├── MediaItemService.java     (video info, likes, playlists)
│       ├── SignInService.java        (auth)
│       ├── RemoteControlService.java (Lounge/Cast)
│       ├── LiveChatService.java      (live stream chat)
│       ├── CommentsService.java      (video comments)
│       ├── NotificationsService.java (bell notifications)
│       ├── ChannelGroupService.java  (channel groups)
│       ├── ServiceManager.java       (root service locator)
│       └── data/                     (all data model interfaces)
│
└── youtubeapi/                    ← YouTube InnerTube implementation
    └── .../youtubeapi/
        ├── app/                      (JS sig/nsig extraction, PoToken)
        ├── auth/V1/, V2/             (OAuth 2.0 Device Code flow)
        ├── block/                    (SponsorBlock API)
        ├── browse/v1/, v2/           (Home, Subs, Channels, History)
        ├── common/                   (InnerTube context builders)
        ├── dearrow/                  (DeArrow community thumbnails)
        ├── feedback/                 (Not interested, feedback tokens)
        ├── formatbuilders/           (DASH MPD + HLS manifest builders)
        ├── innertube/                (InnerTube core, ytcfg, session)
        ├── lounge/                   (YouTube Cast/Remote Control)
        ├── next/v1/, v2/             (Watch Next suggestions + metadata)
        ├── notifications/            (Push notifications)
        ├── panel/                    (Comment panels, quality panels)
        ├── playlist/                 (Playlist CRUD)
        ├── playlistgroups/           (Playlist groups)
        ├── rss/                      (RSS subscription feed)
        ├── search/v1/, v2/           (Search + autocomplete)
        ├── service/                  ← HIGH-LEVEL SERVICE LAYER
        │   ├── YouTubeServiceManager.java
        │   ├── YouTubeContentService.java
        │   ├── YouTubeMediaItemService.java
        │   ├── YouTubeSignInService.java
        │   ├── YouTubeRemoteControlService.java
        │   ├── YouTubeLiveChatService.java
        │   └── YouTubeCommentsService.java
        ├── track/                    (Watch-time heartbeat reporting)
        ├── utils/                    (Canonical channel IDs, helpers)
        └── videoinfo/V1/, V2/        (Stream URL resolution, formats)
```

---

## 2. Core Architecture

### 2.1 Single Entry Point

```java
// The ONLY entry point to the entire SmartTube backend:
ServiceManager sm = YouTubeServiceManager.instance();

ContentService   content  = sm.getContentService();   // Browse/search
MediaItemService media    = sm.getMediaItemService();  // Video streams
SignInService    signin   = sm.getSignInService();     // OAuth
RemoteControlService cast = sm.getRemoteControlService(); // Cast
LiveChatService  chat     = sm.getLiveChatService();   // Live chat
CommentsService  comments = sm.getCommentsService();   // Comments
NotificationsService notifs = sm.getNotificationsService(); // Notifications
ChannelGroupService groups  = sm.getChannelGroupService();  // Channel groups

// Essential management:
sm.invalidateCache();              // Force re-fetch (on account switch)
sm.refreshCacheIfNeeded();         // Refresh stale player JS
sm.switchNextClient();             // Try next video client if playback fails
sm.reloadPlayerResponse(videoId, token); // SABR reload token
```

### 2.2 InnerTube API Endpoints

| Endpoint | Path | Purpose |
|---|---|---|
| Browse | `POST /youtubei/v1/browse` | Home, Trending, Channel, History, Subs |
| Player | `POST /youtubei/v1/player` | Video stream URLs + metadata |
| Next | `POST /youtubei/v1/next` | Watch next + suggestions + metadata |
| Search | `POST /youtubei/v1/search` | Search results |
| Guide | `POST /youtubei/v1/guide` | Navigation sidebar |
| Account | `POST /youtubei/v1/account/...` | Account info |
| Playlist | `POST /youtubei/v1/browse/edit_playlist` | Playlist CRUD |

**Base URL:** `https://www.youtube.com/youtubei/v1/`

### 2.3 InnerTube Request Context

Every request includes this JSON context:
```json
{
  "context": {
    "client": {
      "clientName": "TVHTML5",
      "clientVersion": "7.20230405.08.01",
      "hl": "en",
      "gl": "US",
      "utcOffsetMinutes": 0,
      "visitorData": "CgtZbV...",
      "poToken": "MnTXM..."
    },
    "user": { "lockedSafetyMode": false }
  }
}
```

### 2.4 Client Profiles (AppClient)

| Client | InnerTube Name | Used For |
|---|---|---|
| `TV` | `TVHTML5` | Default — auth-supported, all features |
| `VISIONOS` | `WEB_CREATOR` | Fallback — no URL formats |
| `TV_DOWNGRADED` | `TVHTML5` (older) | Old Cobalt / Xbox compatibility |
| `WEB` | `WEB` | Geo-blocked video fix |
| `WEB_EMBED` | `WEB_EMBEDDED_PLAYER` | Age-restricted videos |
| `WEB_SAFARI` | `WEB` (Safari UA) | Special bypass |
| `IOS` | `IOS` | HLS + extended formats |
| `GEO` | `WEB` | Geographic restriction bypass |
| `MWEB` | `MWEB` | Single audio language |
| `ANDROID_VR` | `ANDROID_VR` | No PoToken needed |

**Fallback order:** TV → VisionOS → TV_Downgraded → Web → Web_Embed → Web_Safari → iOS → GEO → mWeb → Android_VR

---

## 3. Subsystem Deep Dives

### 3.1 AppService — Bootstrap, Signature Extraction & PoToken

**Purpose:** Downloads YouTube player JS, extracts deobfuscation functions, manages PoToken.

```java
// Key methods:
AppService app = AppService.instance();

app.extractSig(String sParam)           // Decipher stream URL signature
app.extractNSig(String nParam)          // Transform anti-throttle 'n' param
app.bulkSigExtract(nParams, sParams)    // Batch transform (performance)
app.getClientPlaybackNonce()            // Unique per-video nonce (anti-replay)
app.getVisitorData()                    // Anonymous visitor cookie
app.getSignatureTimestamp()             // Player JS timestamp
app.getClientId()                       // OAuth client ID
app.getClientSecret()                   // OAuth client secret
app.getTvConfig()                       // TV-specific configuration
app.invalidateCache()                   // Force fresh player JS download
```

**n-parameter deobfuscation pipeline:**
1. Download `https://www.youtube.com/s/player/{hash}/base.js`
2. Regex-extract the function name that transforms `n` (e.g. `var b = a.split(""); ...`)
3. Run extracted function body in V8/QuickJS/WebView engine
4. Replace `&n={original}` with `&n={transformed}` in stream URL

**PoToken (Bot Protection) architecture:**
- `PoTokenProvider` interface → implemented by `WebViewPoTokenProvider` or `CloudPoTokenProvider`
- `PoTokenResult`: fields `poToken`, `visitorData`, `timestamp`
- Required for: TV client, Web client
- NOT required for: Android VR, iOS (these bypass bot protection)

### 3.2 Authentication (OAuth 2.0 Device Flow)

**Credentials:**
- Client ID: `861556708454-d6dlm3lh05dd8kjsmcvn0oh0uh4ngup7.apps.googleusercontent.com`
- Client Secret: `SwsuzWSa2VgahHGv9EYV-Isf`
- Scope: `http://gdata.youtube.com https://www.googleapis.com/auth/youtube-paid-content`

**Flow:**
```
1. GET /o/oauth2/device/code
   → returns: user_code (e.g. "ABCD-EFGH"), verification_url, device_code, expires_in, interval

2. User visits https://www.youtube.com/activate and enters code

3. Poll POST /o/oauth2/token every {interval} seconds:
   → success: returns access_token + refresh_token + expires_in

4. Store access_token + refresh_token in encrypted SharedPreferences

5. All InnerTube requests include:
   Authorization: Bearer {access_token}
```

**Multi-account:**
- `YouTubeAccountManager` stores multiple accounts
- `getAccounts()` → `List<Account>` (name, avatar URL, channel ID)
- `selectAccount(Account)` → switches active account

### 3.3 Content Browsing (BrowseService v2)

**Key methods (`ContentService` interface):**

| Method | Returns | Description |
|---|---|---|
| `getHome()` | `List<MediaGroup>` | Full home feed |
| `getSubscriptions()` | `MediaGroup` | Latest from subscribed channels |
| `getHistory()` | `MediaGroup` | Watch history |
| `getRecommended()` | `MediaGroup` | First home row |
| `getSearch(text)` | `List<MediaGroup>` | Search results |
| `getSearchTags(text)` | `List<String>` | Autocomplete suggestions |
| `getSubscribedChannels()` | `MediaGroup` | Channel management |
| `getGroup(item)` | `MediaGroup` | Navigate to channel/playlist |
| `continueGroup(group)` | `MediaGroup` | Load more (pagination) |

**Observable versions (same API but returns `Observable<T>`):**
- `getHomeObserve()` — emits rows progressively as loaded
- `getTrendingObserve()` — emits trending rows
- `getShortsObserve()` — emits Shorts feed
- `getMusicObserve()` / `getGamingObserve()` / `getNewsObserve()`
- `getSportsObserve()` / `getLiveObserve()` / `getKidsHomeObserve()`
- `getChannelObserve(channelId)` — emits channel tab rows
- `continueGroupObserve(group)` — pagination observable

**BrowseID constants:**
```
"FEwhat_to_watch"   → Home
"FEsubscriptions"   → Subscriptions  
"FEhistory"         → History
"FEtrending"        → Trending
"FEmusic"           → Music
"FEnews"            → News
"FEgaming"          → Gaming
"FElive"            → Live
"FEkids_home"       → YouTube Kids
```

### 3.4 Video Info & Stream Resolution

**Client fallback chain** (V2 VideoInfoService):
```
TV → VisionOS → TV_Downgraded → Web → Web_Embed → Web_Safari → iOS → GEO → mWeb → Android_VR
```
Each client is tried until a playable, non-unplayable result is found.

**Player request body:**
```json
{
  "context": { ... },
  "videoId": "dQw4w9WgXcQ",
  "cpn": "randomNonce16chars",
  "contentCheckOk": true,
  "racyCheckOk": true,
  "playbackContext": {
    "contentPlaybackContext": {
      "html5Preference": "HTML5_PREF_WANTS",
      "signatureTimestamp": 19800
    }
  }
}
```

**`MediaItemFormatInfo` interface (what you use in the player):**
```java
// Detect stream type
formatInfo.containsDashFormats()   // True for adaptive (separate video+audio)
formatInfo.containsHlsUrl()        // True for live streams
formatInfo.containsUrlFormats()    // True for legacy combined streams
formatInfo.containsSabrFormats()   // True for SABR (server-side ABR)

// Get streams
formatInfo.createMpdStream()       // → InputStream of DASH MPD XML → ExoPlayer
formatInfo.getHlsManifestUrl()     // → String URL → ExoPlayer HLS
formatInfo.createUrlList()         // → List<String> of direct URLs → ProgressiveMediaSource
formatInfo.getAdaptiveFormats()    // → List<MediaFormat> (video+audio separate)
formatInfo.getUrlFormats()         // → List<MediaFormat> (combined)
formatInfo.getSubtitles()          // → List<MediaSubtitle>

// Metadata
formatInfo.getTitle(), .getAuthor(), .getVideoId(), .getChannelId()
formatInfo.getLengthSeconds(), .isLive(), .isLiveContent()

// Live stream specifics
formatInfo.getStartTimestamp()     // UTC ISO string e.g. "2021-10-06T13:36:25+00:00"
formatInfo.getStartTimeMs()        // UNIX timestamp in ms
formatInfo.getStartSegmentNum()    // First segment number
formatInfo.getSegmentDurationUs()  // Segment duration in microseconds

// Bot protection tokens
formatInfo.getPoToken()            // Proof-of-origin token
formatInfo.getVisitorCookie()      // Visitor data cookie
formatInfo.getServerAbrStreamingUrl() // SABR URL (newer clients)

// Error handling
formatInfo.isUnplayable()         // True if video unavailable
formatInfo.getPlayabilityReason() // Error message
formatInfo.isStreamSeekable()     // True if seeking is supported
formatInfo.getVolumeLevel()       // Normalized volume
```

### 3.5 DASH MPD Generation (formatbuilders/)

SmartTube dynamically builds MPEG-DASH manifests:

```xml
<MPD xmlns="urn:mpeg:dash:schema:mpd:2011" minBufferTime="PT1.5S" 
     type="static" mediaPresentationDuration="PT{duration}S">
  <Period>
    <AdaptationSet mimeType="video/mp4" contentType="video">
      <Representation id="{itag}" bandwidth="{bitrate}" 
                      width="{w}" height="{h}" codecs="{codecs}" frameRate="{fps}">
        <BaseURL>{streamUrl}</BaseURL>
        <SegmentBase indexRange="{idxStart}-{idxEnd}">
          <Initialization range="{initStart}-{initEnd}"/>
        </SegmentBase>
      </Representation>
      <!-- More resolutions... -->
    </AdaptationSet>
    <AdaptationSet mimeType="audio/mp4" contentType="audio">
      <!-- Audio tracks... -->
    </AdaptationSet>
  </Period>
</MPD>
```

**ITag → Resolution mapping (key ones):**
| ITag | Codec | Resolution | FPS |
|---|---|---|---|
| 137 | H.264 | 1080p | 30 |
| 248 | VP9 | 1080p | 30 |
| 399 | AV1 | 1080p | 30 |
| 313 | VP9 | 2160p | 30 |
| 401 | AV1 | 2160p | 30 |
| 272 | VP9 | 2160p | 60 |
| 337 | VP9.2 (HDR) | 2160p | 60 |
| 140 | AAC | Audio 128kbps | — |
| 251 | Opus | Audio 160kbps | — |

### 3.6 Watch Next Metadata (next/v2/)

Called after a video starts playing:

```java
// Fetches: title, description, like/dislike counts, author info, suggestions
MediaItemMetadata meta = mediaItemService.getMetadata(videoId);

meta.getTitle()             // Full video title
meta.getDescription()       // Full description text
meta.getAuthor()            // Channel name
meta.getAuthorImageUrl()    // Channel avatar URL
meta.getViewCount()         // "1,234,567 views"
meta.getLikeCount()         // "12K" (may be null)
meta.getDislikeCount()      // via Return YouTube Dislike
meta.getSubscriberCount()   // "2.1M subscribers"
meta.getPublishedDate()     // "3 days ago"
meta.isSubscribed()         // true/false
meta.isLive()               // true for live streams
meta.getLiveChatKey()       // Token to start fetching live chat
meta.getCommentsKey()       // Token to fetch comments
meta.getNextVideo()         // MediaItem for auto-play next
meta.getShuffleVideo()      // MediaItem for playlist shuffle
meta.getSuggestions()       // List<MediaGroup> — sidebar rows
meta.getPlaylistInfo()      // Playlist progress + total count
meta.getChapters()          // List<ChapterItem> with timestamps
meta.getNotificationStates() // Bell subscription states
meta.getLikeStatus()        // LIKE_STATUS_LIKE/DISLIKE/INDIFFERENT
meta.getPercentWatched()    // Resume point percentage
meta.getChannelId()         // Channel ID
meta.getParams()            // Playlist params
meta.getDurationMs()        // Duration in milliseconds
meta.getBadgeText()         // "4K" / "CC" badges
```

### 3.7 SponsorBlock

```
API: GET https://sponsor.ajay.app/api/skipSegments
     ?videoID={videoId}
     &categories=["sponsor","intro","outro","interaction","selfpromo","music_offtopic","preview"]
```

```java
List<SponsorSegment> segments = mediaItemService.getSponsorSegments(videoId, categories);

segment.getStartMs()   // Start time in milliseconds
segment.getEndMs()     // End time in milliseconds
segment.getCategory()  // "sponsor", "intro", "outro", etc.
```

**Integration in player:** Listen to ExoPlayer position updates; when `currentPosition` enters a segment range, seek to `segment.endMs`.

### 3.8 YouTube Cast / Lounge Protocol

**Protocol stages:**
```
1. TV generates screenId (UUID) + loungeToken
2. TV displays 12-digit pairing code
3. Phone visits youtube.com/pair + enters code
4. Phone sends commands via AJAX

5. TV sends long-polling GET to /api/lounge/bc/bind
   (holds HTTP connection open - YouTube streams commands down)
   
6. Commands received by TV:
   - setPlaylist: { videoId, playlistId, currentIndex, params }
   - play / pause
   - seekTo: { newTime (seconds) }
   - setVolume: { volume (0-100) }
   - updatePlaylist: { videoIds[] }
   
7. TV posts state back:
   - postStartPlayingObserve(videoId, positionMs, durationMs, STATE_PLAYING)
   - postStateChangeObserve(positionMs, durationMs, state)
   - postVolumeChangeObserve(volume)
```

### 3.9 Search

```java
// Search with autocomplete
List<String> suggestions = contentService.getSearchTags("machine learn");

// Full search
List<MediaGroup> results = contentService.getSearch("machine learning");

// Filtered search (SearchOptions flags)
List<MediaGroup> results = contentService.getSearch("gaming", options);

// Pagination
MediaGroup nextPage = contentService.continueGroup(results.get(0));
```

**Search filter options (protobuf-encoded):**
- Sort: Relevance, Upload Date, View Count, Rating
- Type: Video, Channel, Playlist, Movie
- Duration: < 4min, 4–20min, > 20min
- Features: 4K, HDR, Subtitles, Live, 360, Creative Commons

### 3.10 Watch-Time Tracking

```
Report every ~30 seconds during playback to:
videostatsPlaybackUrl:  &cmt={currentTimeSec}&state=playing&cpn={nonce}&...
videostatsWatchtimeUrl: &cmt={currentTimeSec}&rtn={reportTimeSec}&...
```

Both URLs are embedded in the `/player` response as `VideoInfo.playbackTracking`.

### 3.11 DeArrow & Return YouTube Dislike

```java
// Community-sourced titles/thumbnails
DeArrowData dearrow = mediaItemService.getDeArrowDataObserve(videoId).blockingFirst();
dearrow.getTitle()       // Community-preferred title
dearrow.getThumbnail()   // Community-preferred thumbnail URL
dearrow.isOriginal()     // true = original YouTube title/thumb preferred

// Real dislike counts from returnyoutubedislikeapi.com
DislikeData dislike = mediaItemService.getDislikeDataObserve(videoId).blockingFirst();
dislike.getDislikes()    // int
dislike.getLikes()       // int
dislike.getRating()      // float (0–5)
```

---

## 4. Data Models Reference

### 4.1 MediaGroup (a "row" or "section")

```java
interface MediaGroup {
    // Type constants
    int TYPE_HOME = 0, TYPE_SEARCH = 1, TYPE_RECOMMENDED = 2;
    int TYPE_HISTORY = 3, TYPE_SUBSCRIPTIONS = 4;
    int TYPE_MUSIC = 5, TYPE_NEWS = 6, TYPE_GAMING = 7;
    int TYPE_USER_PLAYLISTS = 8, TYPE_SUGGESTIONS = 9;
    int TYPE_CHANNEL = 10, TYPE_SETTINGS = 11;
    int TYPE_CHANNEL_UPLOADS = 12, TYPE_KIDS_HOME = 13;
    int TYPE_TRENDING = 14, TYPE_SHORTS = 15;
    int TYPE_NOTIFICATIONS = 16, TYPE_SPORTS = 17;
    int TYPE_MOVIES = 18, TYPE_LIVE = 19;
    int TYPE_MY_VIDEOS = 20, TYPE_PLAYBACK_QUEUE = 21;
    int TYPE_BLOCKED_CHANNELS = 22;

    int getType();
    String getTitle();          // Row title e.g. "Recommended for you"
    List<MediaItem> getMediaItems();
    String getNextPageKey();    // Pagination continuation token
    String getChannelId();
    String getParams();
    String getReloadPageKey();
    String getChannelUrl();
    boolean isEmpty();          // True for chip-filtered sections
}
```

### 4.2 MediaItem (a "card/tile")

```java
interface MediaItem {
    int TYPE_VIDEO = 0, TYPE_MUSIC = 1;
    int TYPE_CHANNEL = 2, TYPE_PLAYLIST = 3;

    // Core
    int getType();
    String getVideoId();
    String getTitle();
    CharSequence getSecondTitle();   // "Channel · 2M views · 3 days ago"
    String getChannelId();
    String getCardImageUrl();        // Thumbnail URL
    String getBackgroundImageUrl();  // For hero/spotlight display
    long getDurationMs();
    
    // Status flags
    boolean isLive();
    boolean isUpcoming();
    boolean isShorts();
    boolean isMovie();
    boolean hasNewContent();
    boolean hasUploads();
    
    // Engagement
    int getPercentWatched();         // Progress bar 0–100
    int getStartTimeSeconds();       // Resume position
    String getFeedbackToken();       // For "not interested"
    String getFeedbackToken2();
    FeedbackEndpoint getFeedbackEndpoint();
    
    // Playlist context
    String getPlaylistId();
    int getPlaylistIndex();
    String getParams();
    String getReloadPageKey();
    
    // Metadata
    String getAuthor();
    String getBadgeText();           // "4K", "CC", "LIVE"
    String getProductionDate();
    long getPublishedDate();
    String getVideoPreviewUrl();     // Hover animation URL
    String getAudioChannelConfig();  // "2.0", "5.1"
    String getPurchasePrice();       // For paid content
    String getRentalPrice();
    int getRatingStyle();
    double getRatingScore();
    String getContentType();         // MIME type
    int getWidth(), getHeight();
    
    // Tracking
    String getClickTrackingParams();
    String getSearchQuery();
}
```

### 4.3 MediaItemMetadata (Watch Next response)

```java
interface MediaItemMetadata {
    int LIKE_STATUS_INDIFFERENT = 0;
    int LIKE_STATUS_LIKE = 1;
    int LIKE_STATUS_DISLIKE = 2;

    String getTitle(), getDescription(), getAuthor();
    String getAuthorImageUrl(), getViewCount(), getPublishedDate();
    String getLikeCount(), getDislikeCount(), getSubscriberCount();
    String getVideoId(), getChannelId(), getParams();
    
    boolean isSubscribed(), isLive(), isUpcoming();
    String getLiveChatKey();         // Live chat session key
    String getCommentsKey();         // Comments continuation key
    
    MediaItem getNextVideo();        // Auto-play next
    MediaItem getShuffleVideo();     // Playlist shuffle
    List<MediaGroup> getSuggestions(); // Sidebar suggestions rows
    PlaylistInfo getPlaylistInfo();  // Playlist context
    List<ChapterItem> getChapters(); // Video chapters
    List<NotificationState> getNotificationStates();
    
    int getPercentWatched();
    int getLikeStatus();
    long getDurationMs();
    String getBadgeText();
    CharSequence getSecondTitle();
}
```

### 4.4 MediaItemFormatInfo (Stream info)

```java
interface MediaItemFormatInfo extends FormatInfoProvision {
    // Stream types
    boolean containsDashFormats();
    boolean containsHlsUrl();
    boolean containsUrlFormats();
    boolean containsSabrFormats();
    boolean hasExtendedHlsFormats();
    
    // Stream access
    List<MediaFormat> getAdaptiveFormats(); // DASH video+audio
    List<MediaFormat> getUrlFormats();       // Legacy combined
    List<MediaSubtitle> getSubtitles();
    String getHlsManifestUrl();
    String getDashManifestUrl();
    InputStream createMpdStream();           // → ExoPlayer
    Observable<InputStream> createMpdStreamObservable();
    List<String> createUrlList();
    MediaItemStoryboard createStoryboard();
    
    // Metadata
    String getTitle(), getAuthor(), getVideoId(), getChannelId();
    String getLengthSeconds(), getViewCount(), getDescription();
    boolean isLive(), isLiveContent();
    
    // Live-specific
    String getStartTimestamp();     // UTC ISO string
    long getStartTimeMs();          // UNIX timestamp ms
    int getStartSegmentNum();
    int getSegmentDurationUs();
    
    // Protection tokens
    String getPoToken(), getVisitorCookie();
    String getServerAbrStreamingUrl();
    String getVideoPlaybackUstreamerConfig();
    
    // Error states
    boolean isUnplayable(), isUnknownError();
    boolean isStreamSeekable();
    String getPlayabilityReason();
    String getPaidContentText();
    float getVolumeLevel();
    ClientInfo getClientInfo();
}
```

### 4.5 MediaFormat (individual stream)

```java
interface MediaFormat {
    String getUrl();            // Playable stream URL
    String getMimeType();       // "video/mp4", "audio/webm"
    String getCodecs();         // "avc1.64002A", "vp9", "av01.0.08M"
    int getItag();              // YouTube format ITag (137, 248, 140, etc.)
    long getBitrate();          // bits/sec
    int getWidth(), getHeight(); // Video dimensions
    int getFps();               // Frame rate
    int getAudioChannels();
    int getAudioSampleRate();   // Hz (44100, 48000)
    String getInitRange();      // "0-739"
    String getIndexRange();     // "740-1235"
    long getContentLength();    // bytes
    long getApproxDurationMs();
}
```

### 4.6 MediaSubtitle (subtitle track)

```java
interface MediaSubtitle {
    String getUrl();          // VTT/SRT subtitle URL
    String getLanguage();     // "en", "fr", "es"
    String getName();         // "English (auto-generated)"
    boolean isDefault();
}
```

### 4.7 SponsorSegment

```java
interface SponsorSegment {
    long getStartMs();        // Start time in milliseconds
    long getEndMs();          // End time in milliseconds
    String getCategory();     // "sponsor", "intro", "outro", "interaction"
                              // "selfpromo", "music_offtopic", "preview"
}
```

### 4.8 ChapterItem

```java
interface ChapterItem {
    String getTitle();
    long getStartTimeMs();
    String getThumbnailUrl();
}
```

### 4.9 ChatItem (Live Chat)

```java
interface ChatItem {
    String getAuthorName();
    String getAuthorImageUrl();
    CharSequence getMessage();  // May contain emoji/image spans
    String getSuperChatAmount(); // "$5.00" for Super Chats
    String getAuthorBadgeUrl(); // Channel membership badge
    boolean isModerator();
    boolean isOwner();
}
```

---

## 5. YouTube TV UI Layouts

### 5.1 Navigation Sidebar (Left Rail)

The sidebar collapses to icons-only unless focused:

```
┌─────────────────────────────────────────────────────────────────────┐
│ [ICON] Label      ← Collapsed: only icons; Expanded: icons + labels │
├─────────────────────────────────────────────────────────────────────┤
│ [🏠]   Home                                                         │
│ [🔍]   Search                                                       │
│ [🔔]   Notifications                      (bell + unread count)     │
│ [📡]   Subscriptions                                                │
│ ─────────────────────────────────────────────────────────────────── │
│ [📁]   Library                                                      │
│   [▶]  Continue watching                                            │
│   [🕐]  History                                                      │
│   [👍]  Liked videos                                                │
│   [🎬]  Your videos                                                 │
│   [💾]  Saved playlists...                                          │
│ ─────────────────────────────────────────────────────────────────── │
│ [🔥]   Trending                                                     │
│ [🎵]   Music                                                        │
│ [🎮]   Gaming                                                       │
│ [📰]   News                                                         │
│ [🏅]   Sports                                                       │
│ [📺]   Live                                                         │
│ [📱]   Shorts                                                       │
│ ─────────────────────────────────────────────────────────────────── │
│ [⚙️]   Settings                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 5.2 Home Screen

```
┌──────┬────────────────────────────────────────────────────────────┐
│      │ ┌──────────────────────────────────────────────────────┐   │
│      │ │          SPOTLIGHT / HERO CARD (large, animated)     │   │
│ NAV  │ │  [Thumbnail background]  Title   Subtitle  [▶ Play]  │   │
│ RAIL │ └──────────────────────────────────────────────────────┘   │
│      │ Row Title: "Recommended for you"                            │
│      │ ┌────┐ ┌────┐ ┌────┐ ┌────┐ ┌────┐ ┌────┐ →              │
│      │ │    │ │    │ │    │ │    │ │    │ │    │                  │
│      │ │CARD│ │CARD│ │CARD│ │CARD│ │CARD│ │CARD│                  │
│      │ └────┘ └────┘ └────┘ └────┘ └────┘ └────┘                  │
│      │ Row Title: "Continue watching"                              │
│      │ ┌────┐ ┌────┐ ┌────┐ ┌────┐ ←──────────────               │
│      │ │░░░░│ │    │ │    │ │    │   Progress bars               │
└──────┴────────────────────────────────────────────────────────────┘
```

### 5.3 Video Card (Tile)

```
┌──────────────────────┐
│                      │
│    [THUMBNAIL 16:9]  │  ← cardImageUrl
│  ████████░░░ 78%    │  ← percentWatched bar (YouTube red)
│  [LIVE] [4K] badges │
│                      │
├──────────────────────┤
│ Video Title Here...  │  ← title (max 2 lines)
│ Channel Name         │  ← author
│ 2M views · 3 days   │  ← secondTitle
│                12:34 │  ← durationMs formatted
└──────────────────────┘
```
*On focus: scale 1.05x + white border highlight*

### 5.4 Player Screen

```
┌──────────────────────────────────────────────────────────────────┐
│                                                                  │
│                   VIDEO PLAYER (ExoPlayer)                       │
│                       16:9 full width                            │
│                                                                  │
├──────────────────────────────────────────────────────────────────┤
│  ▶ ────────────────────────────── 12:34 / 1:05:22               │
│     Chapter marks on seekbar: ▲  ▲   ▲     ▲                   │
│  Title of Video                           [CC] [⚙] [☰] [⋮]    │
├─────────────────────────────────┬────────────────────────────────┤
│ ABOUT THIS VIDEO                │ SUGGESTED VIDEOS               │
│ ┌──┐ Channel Name    [🔔][SUB]  │ ┌────┐ ┌────┐ ┌────┐ ┌────┐  │
│ └──┘ 2.1M subscribers           │ │UP  │ │    │ │    │ │    │  │
│ 👍 12K   👎 800   Share  Save   │ │NEXT│ │    │ │    │ │    │  │
│                                 │ └────┘ └────┘ └────┘ └────┘  │
│ 3 days ago  ·  1,234,567 views  │ [Chapter 1] [Chapter 2] ...   │
│ Description text here...        │                                │
│ [Show more]                     │                                │
├─────────────────────────────────┴────────────────────────────────┤
│ COMMENTS (2,345)                                                 │
│ [Avatar] Top comment text here...                                │
│ [Avatar] Another comment...                                      │
└──────────────────────────────────────────────────────────────────┘
```

### 5.5 Search Screen

```
┌──────┬──────────────────────────────────────────────────────────┐
│ NAV  │ 🔍 [___Search query here_______________________________] │
│      │ ──────────────────────────────────────────────────────── │
│      │ Suggestions: "machine learning" "ML basics" "ML course"  │
│      │ ──────────────────────────────────────────────────────── │
│      │ Filters: [↕ Relevance▼] [📅 Any time▼] [🎬 All▼] [⏱▼] │
│      │ ──────────────────────────────────────────────────────── │
│      │ Videos                                                    │
│      │ ┌────┐ ┌────┐ ┌────┐ ┌────┐ ┌────┐                      │
│      │ │    │ │    │ │    │ │    │ │    │                      │
│      │ └────┘ └────┘ └────┘ └────┘ └────┘                      │
│      │ Channels                                                  │
│      │ ┌──────────┐ ┌──────────┐ ┌──────────┐                  │
│      │ │[Avatar]  │ │[Avatar]  │ │[Avatar]  │                  │
│      │ │Name 2.1M │ │Name 890K │ │Name 450K │                  │
│      │ └──────────┘ └──────────┘ └──────────┘                  │
└──────┴──────────────────────────────────────────────────────────┘
```

### 5.6 Channel Screen

```
┌──────────────────────────────────────────────────────────────────┐
│                   [CHANNEL BANNER IMAGE]                         │
│ [Avatar] Channel Name              [🔔 Subscribe] [2.1M subs]   │
├──────────────────────────────────────────────────────────────────┤
│ [HOME]  [VIDEOS]  [PLAYLISTS]  [COMMUNITY]  [ABOUT]  [SEARCH]  │
├──────────────────────────────────────────────────────────────────┤
│ Popular uploads                                                  │
│ ┌────┐ ┌────┐ ┌────┐ ┌────┐ ┌────┐                             │
│ │    │ │    │ │    │ │    │ │    │                             │
│ └────┘ └────┘ └────┘ └────┘ └────┘                             │
│ Playlists                                                        │
│ ┌──────────┐ ┌──────────┐ ┌──────────┐                         │
│ │[Playlist]│ │[Playlist]│ │[Playlist]│                         │
└─────────────────────────────────────────────────────────────────┘
```

---

## 6. New App Architecture

### 6.1 Tech Stack

| Layer | Technology |
|---|---|
| Language | **Kotlin** (Java interop for SmartTube backend) |
| UI | **Jetpack Compose for TV** (`androidx.tv:tv-compose`) |
| Architecture | **MVVM + Clean Architecture** |
| DI | **Hilt** |
| Async | **RxJava 2** (matches SmartTube) + Kotlin Coroutines adapter |
| Player | **ExoPlayer (Media3)** with DASH + HLS + Progressive |
| Images | **Coil** (Compose-native, Glide alternative) |
| Navigation | **Jetpack Navigation Compose** |
| Backend | **SmartTube's `youtubeapi` module** (imported as-is) |

### 6.2 Project Module Structure

```
YoutubeTVApp/
├── app/                              ← Android TV Application
│   ├── di/
│   │   ├── ServiceModule.kt          ← SmartTube service bindings
│   │   └── PlayerModule.kt           ← ExoPlayer DI
│   ├── MainActivity.kt
│   └── Application.kt               ← GlobalPreferences.init(this)
│
├── data/                             ← Data layer (wraps SmartTube)
│   ├── repository/
│   │   ├── ContentRepository.kt
│   │   ├── MediaRepository.kt
│   │   ├── AuthRepository.kt
│   │   ├── SearchRepository.kt
│   │   └── PlayerRepository.kt
│   └── mapper/
│       ├── MediaItemMapper.kt
│       └── MediaGroupMapper.kt
│
├── domain/                           ← Business logic
│   ├── model/                        ← App-internal data classes
│   └── usecase/
│       ├── GetHomeUseCase.kt
│       ├── PlayVideoUseCase.kt
│       └── SearchUseCase.kt
│
├── ui/
│   ├── theme/
│   │   ├── YouTubeTheme.kt           ← Colors, typography
│   │   └── Dimensions.kt
│   ├── components/
│   │   ├── VideoCard.kt              ← Thumbnail card (D-pad focusable)
│   │   ├── ChannelCard.kt
│   │   ├── SectionRow.kt             ← Horizontal scrollable row
│   │   ├── NavigationSidebar.kt
│   │   ├── PlayerControls.kt
│   │   ├── ChapterBar.kt
│   │   ├── LiveChatOverlay.kt
│   │   └── SponsorBlockIndicator.kt
│   ├── screen/
│   │   ├── home/
│   │   │   ├── HomeScreen.kt
│   │   │   └── HomeViewModel.kt
│   │   ├── search/
│   │   │   ├── SearchScreen.kt
│   │   │   └── SearchViewModel.kt
│   │   ├── player/
│   │   │   ├── PlayerScreen.kt
│   │   │   └── PlayerViewModel.kt
│   │   ├── channel/
│   │   │   ├── ChannelScreen.kt
│   │   │   └── ChannelViewModel.kt
│   │   ├── subscriptions/
│   │   │   ├── SubsScreen.kt
│   │   │   └── SubsViewModel.kt
│   │   ├── library/
│   │   │   ├── LibraryScreen.kt
│   │   │   └── LibraryViewModel.kt
│   │   └── signin/
│   │       ├── SignInScreen.kt
│   │       └── SignInViewModel.kt
│   └── navigation/
│       └── AppNavGraph.kt
│
└── smarttube-core/                   ← SmartTube MediaServiceCore (submodule)
    ├── youtubeapi/
    └── mediaserviceinterfaces/
```

### 6.3 Gradle Integration

```gradle
// settings.gradle
include ':smarttube-core'
project(':smarttube-core').projectDir = new File('../smarttube/MediaServiceCore-7f36935...')

// app/build.gradle
android {
    defaultConfig {
        minSdk 21           // Android TV minimum
        targetSdk 34
    }
}

dependencies {
    implementation project(':smarttube-core')
}
```

### 6.4 Application Initialization

```kotlin
@HiltAndroidApp
class YouTubeTVApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // CRITICAL: Must be called before any SmartTube service is accessed
        GlobalPreferences.init(this, "ytv_prefs")
    }
}
```

### 6.5 Hilt DI Module

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {
    
    @Provides @Singleton
    fun provideServiceManager(): ServiceManager = YouTubeServiceManager.instance()

    @Provides @Singleton
    fun provideContentService(sm: ServiceManager): ContentService = sm.getContentService()

    @Provides @Singleton
    fun provideMediaItemService(sm: ServiceManager): MediaItemService = sm.getMediaItemService()

    @Provides @Singleton
    fun provideSignInService(sm: ServiceManager): SignInService = sm.getSignInService()

    @Provides @Singleton
    fun provideRemoteControlService(sm: ServiceManager): RemoteControlService =
        sm.getRemoteControlService()

    @Provides @Singleton
    fun provideLiveChatService(sm: ServiceManager): LiveChatService = sm.getLiveChatService()
}
```

### 6.6 Repository Pattern

```kotlin
@Singleton
class ContentRepository @Inject constructor(
    private val contentService: ContentService
) {
    // Flow-based (progressive emission as rows load)
    fun getHomeFlow(): Flow<List<MediaGroup>> =
        contentService.getHomeObserve().toFlow()

    fun getTrendingFlow(): Flow<List<MediaGroup>> =
        contentService.getTrendingObserve().toFlow()

    fun getChannelFlow(channelId: String): Flow<List<MediaGroup>> =
        contentService.getChannelObserve(channelId).toFlow()

    // Suspend functions for single-shot requests
    suspend fun search(query: String) = withContext(Dispatchers.IO) {
        contentService.getSearch(query)
    }

    suspend fun getSuggestions(query: String) = withContext(Dispatchers.IO) {
        contentService.getSearchTags(query)
    }

    suspend fun continueGroup(group: MediaGroup) = withContext(Dispatchers.IO) {
        contentService.continueGroup(group)
    }
}

@Singleton
class PlayerRepository @Inject constructor(
    private val mediaItemService: MediaItemService
) {
    suspend fun getFormatInfo(videoId: String) = withContext(Dispatchers.IO) {
        mediaItemService.getFormatInfo(videoId)
    }

    suspend fun getMetadata(videoId: String) = withContext(Dispatchers.IO) {
        mediaItemService.getMetadata(videoId)
    }

    suspend fun getSponsorSegments(videoId: String) = withContext(Dispatchers.IO) {
        mediaItemService.getSponsorSegments(videoId)
    }

    suspend fun setLike(item: MediaItem) = withContext(Dispatchers.IO) {
        mediaItemService.setLike(item)
    }

    suspend fun subscribe(channelId: String) = withContext(Dispatchers.IO) {
        mediaItemService.subscribe(channelId)
    }

    suspend fun updateHistory(videoId: String, positionSec: Float) = withContext(Dispatchers.IO) {
        mediaItemService.updateHistoryPosition(videoId, positionSec)
    }
}
```

### 6.7 ViewModel Pattern

```kotlin
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val contentRepo: ContentRepository
) : ViewModel() {
    
    private val _sections = MutableStateFlow<List<MediaGroup>>(emptyList())
    val sections: StateFlow<List<MediaGroup>> = _sections.asStateFlow()
    
    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    init { loadHome() }

    fun loadHome() {
        viewModelScope.launch {
            _loading.value = true
            contentRepo.getHomeFlow()
                .catch { /* handle errors */ }
                .collect { newGroups ->
                    _sections.update { current -> current + newGroups }
                }
            _loading.value = false
        }
    }
}
```

### 6.8 Player ViewModel

```kotlin
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playerRepo: PlayerRepository,
    private val exoPlayer: ExoPlayer
) : ViewModel() {
    
    private val _metadata = MutableStateFlow<MediaItemMetadata?>(null)
    val metadata: StateFlow<MediaItemMetadata?> = _metadata.asStateFlow()
    
    private val _sponsorSegments = MutableStateFlow<List<SponsorSegment>>(emptyList())
    val sponsorSegments: StateFlow<List<SponsorSegment>> = _sponsorSegments.asStateFlow()

    fun playVideo(videoId: String, clickTrackingParams: String? = null) {
        viewModelScope.launch {
            // Load format info + metadata concurrently
            val formatInfoDeferred = async(Dispatchers.IO) {
                playerRepo.getFormatInfo(videoId)
            }
            val metadataDeferred = async(Dispatchers.IO) {
                playerRepo.getMetadata(videoId)
            }
            val sponsorDeferred = async(Dispatchers.IO) {
                playerRepo.getSponsorSegments(videoId)
            }

            val formatInfo = formatInfoDeferred.await() ?: return@launch
            _metadata.value = metadataDeferred.await()
            _sponsorSegments.value = sponsorDeferred.await() ?: emptyList()

            val mediaItem = when {
                formatInfo.containsHlsUrl() ->
                    MediaItem.fromUri(formatInfo.hlsManifestUrl)
                formatInfo.containsDashFormats() ->
                    buildDashMediaItem(formatInfo) // Uses createMpdStream()
                else ->
                    MediaItem.fromUri(formatInfo.createUrlList().first())
            }

            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.play()
        }
    }

    private suspend fun buildDashMediaItem(formatInfo: MediaItemFormatInfo): MediaItem {
        // createMpdStream() returns an InputStream of the DASH MPD XML
        return MediaItem.Builder()
            .setUri(Uri.EMPTY) // Will be overridden by custom data source
            .setMimeType("application/dash+xml")
            .build()
    }

    // SponsorBlock: call from ExoPlayer position listener
    fun checkSponsorSegments(currentPositionMs: Long) {
        val segments = _sponsorSegments.value
        for (segment in segments) {
            if (currentPositionMs in segment.startMs..segment.endMs) {
                exoPlayer.seekTo(segment.endMs)
                break
            }
        }
    }
}
```

---

## 7. YouTube TV UI Components (Compose)

### 7.1 YouTube Dark Theme

```kotlin
val YouTubeDarkColors = darkColorScheme(
    background   = Color(0xFF0F0F0F),   // Dark background
    surface      = Color(0xFF212121),   // Card surface
    surfaceVariant = Color(0xFF282828), // Hover state
    primary      = Color(0xFFFF0000),   // YouTube red
    onPrimary    = Color.White,
    onBackground = Color.White,
    onSurface    = Color(0xFFAAAAAA),   // Secondary text
    outline      = Color(0xFF3D3D3D),   // Borders/dividers
    tertiary     = Color(0xFF0096FF),   // Blue accent (live badge)
)

@Composable
fun YouTubeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = YouTubeDarkColors,
        typography = Typography(
            titleLarge = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp, color = Color.White
            ),
            // ...
        ),
        content = content
    )
}
```

### 7.2 Focusable Video Card (D-Pad Ready)

```kotlin
@Composable
fun VideoCard(
    item: MediaItem,
    modifier: Modifier = Modifier,
    cardWidth: Dp = 240.dp,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.06f else 1f)

    Card(
        onClick = onClick,
        modifier = modifier
            .width(cardWidth)
            .scale(scale)
            .onFocusChanged { isFocused = it.isFocused },
        colors = CardDefaults.colors(
            containerColor = Color(0xFF212121),
            focusedContainerColor = Color(0xFF282828)
        ),
        border = if (isFocused)
            CardDefaults.border(
                focusedBorder = Border(BorderStroke(2.dp, Color.White))
            )
        else CardDefaults.border()
    ) {
        Box {
            // Thumbnail
            AsyncImage(
                model = item.cardImageUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
            )
            // Progress bar overlay
            if (item.percentWatched > 0) {
                LinearProgressIndicator(
                    progress = item.percentWatched / 100f,
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
                    color = Color(0xFFFF0000), trackColor = Color(0x66FFFFFF)
                )
            }
            // LIVE badge
            if (item.isLive) {
                Surface(
                    color = Color(0xFFFF0000),
                    shape = RoundedCornerShape(2.dp),
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp)
                ) {
                    Text("LIVE", color = Color.White, fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                }
            }
            // Duration badge (bottom-right)
            if (!item.isLive && item.durationMs > 0) {
                Surface(
                    color = Color.Black.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(2.dp),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
                ) {
                    Text(formatDuration(item.durationMs), color = Color.White,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                }
            }
            // Badge text (4K, HDR, etc.)
            item.badgeText?.let { badge ->
                Surface(
                    color = Color(0xFF222222).copy(alpha = 0.9f),
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                ) {
                    Text(badge, color = Color.White, fontSize = 9.sp,
                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp))
                }
            }
        }
        // Text info
        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text(item.title ?: "", maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium, color = Color.White)
            Spacer(Modifier.height(2.dp))
            Text(item.secondTitle?.toString() ?: "",
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall, color = Color(0xFFAAAAAA))
        }
    }
}

fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
```

### 7.3 Horizontal Section Row

```kotlin
@Composable
fun SectionRow(
    group: MediaGroup,
    onItemClick: (MediaItem) -> Unit,
    onLoadMore: () -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        // Row title
        group.title?.let { title ->
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier.padding(start = 48.dp, bottom = 12.dp, top = 24.dp)
            )
        }
        // Horizontal scroll
        TvLazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(
                items = group.mediaItems ?: emptyList(),
                key = { it.videoId ?: it.hashCode().toString() }
            ) { item ->
                VideoCard(item = item, onClick = { onItemClick(item) })
            }
            // "Load more" trigger at end of list
            item {
                LaunchedEffect(group.nextPageKey) {
                    if (group.nextPageKey != null) onLoadMore()
                }
            }
        }
    }
}
```

### 7.4 Navigation Sidebar

```kotlin
@Composable
fun NavigationSidebar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    isExpanded: Boolean = false
) {
    val navItems = listOf(
        NavItem("home", Icons.Default.Home, "Home"),
        NavItem("search", Icons.Default.Search, "Search"),
        NavItem("notifications", Icons.Default.Notifications, "Notifications"),
        NavItem("subscriptions", Icons.Default.Subscriptions, "Subscriptions"),
        null, // Divider
        NavItem("library", Icons.Default.VideoLibrary, "Library"),
        NavItem("history", Icons.Default.History, "History"),
        NavItem("liked", Icons.Default.ThumbUp, "Liked videos"),
        null, // Divider
        NavItem("trending", Icons.Default.Whatshot, "Trending"),
        NavItem("music", Icons.Default.MusicNote, "Music"),
        NavItem("gaming", Icons.Default.SportsEsports, "Gaming"),
        NavItem("news", Icons.Default.Article, "News"),
        NavItem("sports", Icons.Default.SportsSoccer, "Sports"),
        NavItem("live", Icons.Default.LiveTv, "Live"),
    )

    NavigationDrawer(
        drawerContent = {
            Spacer(Modifier.height(16.dp))
            navItems.forEach { item ->
                if (item == null) {
                    Divider(color = Color(0xFF3D3D3D), modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp))
                } else {
                    NavigationDrawerItem(
                        selected = currentRoute == item.route,
                        onClick = { onNavigate(item.route) },
                        leadingContent = { Icon(item.icon, null, tint = Color.White) },
                        content = { if (isExpanded) Text(item.label, color = Color.White) }
                    )
                }
            }
        }
    ) { /* main content slot */ }
}

data class NavItem(val route: String, val icon: ImageVector, val label: String)
```

---

## 8. Full Feature Checklist

### Backend Features (all inherited from SmartTube)

- [x] Home feed rows (recommended, trending, categories)
- [x] Subscriptions feed (latest from channels)
- [x] Watch history
- [x] Trending tab
- [x] Music, Gaming, News, Sports, Live tabs
- [x] YouTube Kids home
- [x] Shorts feed
- [x] My Videos (uploaded videos)
- [x] Search with autocomplete suggestions
- [x] Channel page (all tabs: Home, Videos, Playlists, Community, About)
- [x] Channel search
- [x] Channel subscription management
- [x] User playlists (CRUD: create, rename, delete, add/remove videos)
- [x] Playlist ordering (by date, popularity, published)
- [x] Video playback via DASH adaptive streaming
- [x] HLS live stream playback
- [x] Subtitle/CC tracks
- [x] Multiple audio tracks
- [x] Storyboard seeking (hover thumbnails)
- [x] Video chapters (seekbar markers + list)
- [x] Watch Next suggestions sidebar
- [x] Auto-play next video
- [x] Shuffle for playlists
- [x] Like/Dislike actions
- [x] Subscribe/Unsubscribe actions
- [x] "Not interested" / feedback
- [x] Watch history position tracking (resume playback)
- [x] Watch history pause/resume
- [x] Clear watch history / search history
- [x] SponsorBlock auto-skip segments
- [x] DeArrow community thumbnails/titles
- [x] Return YouTube Dislike (real dislike counts)
- [x] YouTube Cast/Lounge remote control pairing
- [x] Live chat during live streams
- [x] Video comments
- [x] Bell notifications with states
- [x] Multi-account sign-in
- [x] RSS feed for subscriptions (alternative to InnerTube)
- [x] Channel groups
- [x] Client auto-fallback (handles geo-restriction, 403, throttle)
- [x] n-parameter deobfuscation (anti-throttle)
- [x] PoToken bot protection
- [x] SABR (server-side ABR) streaming
- [x] Signature cipher descrambling
- [x] Playback nonce (anti-replay)

### UI Features (new)
- [ ] YouTube dark theme (exact color scheme)
- [ ] Left navigation rail (collapsible)
- [ ] Horizontal scrollable rows (D-pad navigable)
- [ ] Spotlight/Hero card on Home
- [ ] Focusable cards with scale + border on D-pad focus
- [ ] Video player with standard YouTube controls
- [ ] Chapter markers on seekbar
- [ ] Suggestions panel (right side of player)
- [ ] About panel (below player)
- [ ] Sign-in screen (QR code / device code display)
- [ ] Search screen with keyboard + suggestions
- [ ] Channel screen with tabs
- [ ] Live chat overlay
- [ ] Settings screen
- [ ] 4K / HDR badge display on thumbnails
- [ ] Progress bar on thumbnails (watch history)
- [ ] LIVE badge on live streams

---

## 9. Development Roadmap

### Phase 1 — Foundation (Weeks 1–2)
- [ ] Create Android TV project (Kotlin + Compose TV + Hilt)
- [ ] Import SmartTube MediaServiceCore as Gradle submodule
- [ ] Set up DI, repository layer, and RxJava→Flow adapters
- [ ] Sign-in screen (device code + QR code display)
- [ ] Navigation sidebar skeleton

### Phase 2 — Home & Browse (Weeks 3–4)
- [ ] Home screen with hero card + section rows
- [ ] Video card component (all states: focused, unfocused, live, shorts)
- [ ] Pagination / infinite scroll (`continueGroup`)
- [ ] Trending, Subscriptions screens
- [ ] Category tabs (Music, Gaming, News, Sports, Live, Shorts)

### Phase 3 — Playback Engine (Weeks 5–6)
- [ ] ExoPlayer integration (DASH MPD via `createMpdStream()`)
- [ ] HLS support (live streams)
- [ ] Legacy URL formats support
- [ ] Player UI (controls, seekbar, chapters, subtitles)
- [ ] SponsorBlock segment auto-skip
- [ ] Watch-time heartbeat reporting

### Phase 4 — Discovery & Social (Weeks 7–8)
- [ ] Search screen (keyboard + autocomplete + filter chips)
- [ ] Channel screen (all tabs + subscription button)
- [ ] Watch Next panel (suggestions sidebar)
- [ ] Video metadata panel (description, likes, subscriber count)
- [ ] Comments section
- [ ] Live chat overlay
- [ ] Like/Dislike/Subscribe buttons + feedback

### Phase 5 — Library & Account (Weeks 9–10)
- [ ] Watch history screen
- [ ] Playlists screen + management (CRUD)
- [ ] My Videos screen
- [ ] Notifications screen
- [ ] Multi-account switching
- [ ] Settings screen (SponsorBlock categories, quality prefs)

### Phase 6 — Polish & Advanced (Weeks 11–12)
- [ ] YouTube Cast/Lounge remote control
- [ ] DeArrow community thumbnails
- [ ] Return YouTube Dislike real counts
- [ ] Storyboard seeking (hover thumbnail previews)
- [ ] RSS feed mode for subscriptions
- [ ] Keyboard + D-pad navigation polish
- [ ] Focus management & accessibility
- [ ] Animations and transitions

---

## 10. Dependencies

```gradle
// app/build.gradle
dependencies {
    // SmartTube backend
    implementation project(':smarttube-core')

    // Jetpack Compose TV
    implementation "androidx.tv:tv-compose:1.0.0"
    implementation "androidx.tv:tv-foundation:1.0.0"
    implementation "androidx.compose.ui:ui:1.6.0"
    implementation "androidx.compose.material3:material3:1.2.0"
    implementation "androidx.compose.material:material-icons-extended:1.6.0"
    implementation "androidx.compose.animation:animation:1.6.0"
    
    // ExoPlayer / Media3
    implementation "androidx.media3:media3-exoplayer:1.3.0"
    implementation "androidx.media3:media3-exoplayer-dash:1.3.0"
    implementation "androidx.media3:media3-exoplayer-hls:1.3.0"
    implementation "androidx.media3:media3-exoplayer-smoothstreaming:1.3.0"
    implementation "androidx.media3:media3-datasource-okhttp:1.3.0"
    implementation "androidx.media3:media3-ui:1.3.0"
    
    // Hilt DI
    implementation "com.google.dagger:hilt-android:2.51"
    kapt "com.google.dagger:hilt-compiler:2.51"
    implementation "androidx.hilt:hilt-navigation-compose:1.2.0"
    
    // RxJava (required by SmartTube)
    implementation "io.reactivex.rxjava2:rxjava:2.2.21"
    implementation "io.reactivex.rxjava2:rxandroid:2.1.1"
    
    // Retrofit + OkHttp (required by SmartTube)
    implementation "com.squareup.retrofit2:retrofit:2.9.0"
    implementation "com.squareup.retrofit2:converter-gson:2.9.0"
    implementation "com.squareup.okhttp3:okhttp:4.12.0"
    implementation "com.squareup.okhttp3:logging-interceptor:4.12.0"
    
    // Image loading
    implementation "io.coil-kt:coil-compose:2.6.0"
    
    // Navigation
    implementation "androidx.navigation:navigation-compose:2.7.7"
    
    // Lifecycle
    implementation "androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0"
    implementation "androidx.lifecycle:lifecycle-runtime-compose:2.7.0"
}
```

---

## 11. Critical Gotchas

> [!CAUTION]
> **`GlobalPreferences.init(context, "prefs_name")` MUST be called in `Application.onCreate()` BEFORE any SmartTube service is accessed.** The entire auth system and preferences persistence depends on it.

> [!WARNING]
> **Never replace SmartTube backend code.** The `youtubeapi` module handles n-param deobfuscation, PoToken, client fallback, and signature cipher — all of which require constant maintenance as YouTube changes. Only add new UI code on top.

> [!IMPORTANT]
> **All blocking service calls (`getHome()`, `getFormatInfo()`, etc.) MUST run on background threads.** Use `withContext(Dispatchers.IO)` or RxJava `subscribeOn(Schedulers.io())`. Calling on main thread will cause ANR.

> [!TIP]
> **Client switching:** If a video fails to play, call `serviceManager.switchNextClient()` — SmartTube cycles through TV, iOS, Web, ANDROID_VR, etc. until one works. Add a "retry" / "try different server" button in the player UI.

> [!NOTE]
> **PoToken and WebView:** The `potokennp2` module may require a live `WebView` for bot challenge solving. Ensure the `WebView` is initialized during app startup before the first player request.

> [!NOTE]
> **Cache invalidation:** Call `serviceManager.invalidateCache()` when switching accounts, changing locale, or after extended idle sessions. This refreshes visitor data, player JS cache, and auth tokens.

> [!TIP]
> **RxJava to Flow adapter:** SmartTube uses RxJava 2 `Observable`. Use `observable.toFlow()` extension (from `kotlinx-coroutines-rx2`) to bridge to Kotlin Flow in ViewModels.

> [!NOTE]
> **DASH MPD via InputStream:** `formatInfo.createMpdStream()` returns an `InputStream` of the MPD XML. Pass it to ExoPlayer using a custom `DataSource` that reads from the stream, or write it to a temporary file/byte array first.
