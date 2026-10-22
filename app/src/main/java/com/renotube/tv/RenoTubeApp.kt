package com.renotube.tv

import android.app.Application
import android.graphics.Bitmap
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.liskovsoft.sharedutils.prefs.GlobalPreferences
import com.renotube.tv.crash.CrashHandler
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient

@HiltAndroidApp
class RenoTubeApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        CrashHandler.install(this)
        super.onCreate()
        // CRITICAL: Must initialize GlobalPreferences before any SmartTube MediaServiceCore call
        GlobalPreferences.instance(this)
        try {
            com.liskovsoft.youtubeapi.service.YouTubeServiceManager.instance().invalidateCache()
        } catch (e: Exception) {
            android.util.Log.w("RenoTubeApp", "Initial format reset: ${e.message}")
        }
        coil.Coil.setImageLoader(this)
    }

    override fun newImageLoader(): ImageLoader {
        android.util.Log.d("RenoTubeApp", "newImageLoader() configuring OkHttp3UriFetcher")
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .header("Referer", "https://www.youtube.com/")
                    .build()
                chain.proceed(request)
            }
            .build()

        return ImageLoader.Builder(this)
            .components {
                add(com.renotube.tv.image.OkHttp3UriFetcher.Factory(okHttpClient))
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.15)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(64L * 1024 * 1024)
                    .build()
            }
            .allowHardware(true)
            .allowRgb565(true)
            .bitmapConfig(Bitmap.Config.RGB_565)
            .crossfade(100)
            .build()
    }
}
