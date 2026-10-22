package com.renotube.tv.image

import android.net.Uri
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.source
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest

class OkHttp3UriFetcher(
    private val uri: Uri,
    private val options: Options,
    private val client: OkHttpClient
) : Fetcher {

    override suspend fun fetch(): FetchResult = withContext(Dispatchers.IO) {
        val urlString = uri.toString()
        val cacheDir = File(options.context.cacheDir, "thumb_cache").apply { if (!exists()) mkdirs() }
        val hash = md5(urlString)
        val cacheFile = File(cacheDir, "$hash.img")

        // 1. Check local persistent disk cache (0-3ms load time)
        if (cacheFile.exists() && cacheFile.length() > 0) {
            try {
                return@withContext SourceResult(
                    source = ImageSource(source = cacheFile.source().buffer(), context = options.context),
                    mimeType = "image/jpeg",
                    dataSource = DataSource.DISK
                )
            } catch (e: Exception) {
                cacheFile.delete()
            }
        }

        // 2. Network fetch with optimized YouTube headers
        val request = Request.Builder()
            .url(urlString)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .header("Referer", "https://www.youtube.com/")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            response.close()
            throw IOException("HTTP ${response.code()}: ${response.message()}")
        }

        val body = response.body() ?: throw IOException("Empty response body for $uri")
        val mimeType = response.header("Content-Type") ?: "image/jpeg"

        // 3. Atomically persist to disk cache
        val tempFile = File(cacheDir, "$hash.tmp_${System.currentTimeMillis()}")
        try {
            body.byteStream().use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (tempFile.length() > 0) {
                tempFile.renameTo(cacheFile)
            }
        } catch (e: Exception) {
            tempFile.delete()
        } finally {
            response.close()
        }

        val fileToRead = if (cacheFile.exists() && cacheFile.length() > 0) cacheFile else tempFile
        if (fileToRead.exists() && fileToRead.length() > 0) {
            SourceResult(
                source = ImageSource(source = fileToRead.source().buffer(), context = options.context),
                mimeType = mimeType,
                dataSource = DataSource.NETWORK
            )
        } else {
            throw IOException("Failed to save or stream image $uri")
        }
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        return md.digest(input.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    class Factory(private val client: OkHttpClient) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            val scheme = data.scheme
            if (scheme == "http" || scheme == "https") {
                return OkHttp3UriFetcher(data, options, client)
            }
            return null
        }
    }
}

