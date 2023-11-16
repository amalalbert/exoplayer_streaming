package com.example.exoplayer.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.media3.common.C.TRACK_TYPE_TEXT
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Tracks
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import com.example.exoplayer.models.Language
import com.franmontiel.persistentcookiejar.PersistentCookieJar
import com.franmontiel.persistentcookiejar.cache.SetCookieCache
import com.franmontiel.persistentcookiejar.persistence.SharedPrefsCookiePersistor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.io.BufferedInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Date
import java.util.Locale

object ExoUtils {
    /**
     * @param track  Track to parse.
     * @return List<Language>
     */
    fun getLanguagesFromTrackInfo(track: Tracks): List<Language> {
        return track.groups.asSequence().mapNotNull {
            if (it.type != TRACK_TYPE_TEXT) {
                return@mapNotNull null
            }
            val group = it
            val formats = (0 until group.length).map { index -> group.getTrackFormat(index) }
            formats.mapNotNull { format ->
                format.language
            }
        }
            .flatten()
            .toSet()
            .map {
                val locale = Locale(it)
                val languageName = locale.getDisplayLanguage(locale)
                Language(code = it, name = languageName)
            }.toList()
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
     /**
     *@param url url to generate the MediaSource.
     *@param context A context.
     *@param cookies A List of Cookies.
     *@param httpUrl the url to be used for Building the Okhttp Client.
     *@return MediaSource
     */
    fun generateMediaSource(
        url: String,
        context: Context,
        cookies: ArrayList<Cookie?>,
        httpUrl: HttpUrl
    ):
            MediaSource? {
        var mediaSource: MediaSource? = null
        if (url.isNotEmpty()) {
            if (url.contains(".mpd")) {
                val mediaItem = MediaItem.Builder()
                    .setUri(Uri.parse(url))
                    .setMimeType(MimeTypes.APPLICATION_MPD)
                    .build()
                val dataSourceFactory = DefaultDataSource.Factory(context)
                mediaSource =
                    DashMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
            } else if (url.contains(".m3u8")) {
                val mediaItem = MediaItem.Builder()
                    .setUri(Uri.parse(url))
                    .setMimeType(MimeTypes.APPLICATION_M3U8)
                    .build()

                val interceptor = LoggingInterceptor()
                val cookieJar: CookieJar =
                    PersistentCookieJar(SetCookieCache(), SharedPrefsCookiePersistor(context))

                cookieJar.saveFromResponse(
                    httpUrl,
                    cookies.toList() as List<Cookie>
                )
                val client = OkHttpClient.Builder()
                    .addInterceptor(interceptor)
                    .cookieJar(cookieJar)
                    .cache(Cache(context.cacheDir, 4096))
                    .build()

                val httpDataSourceFactory = OkHttpDataSource.Factory(client)

                val dataSourceFactory = DefaultDataSource.Factory(
                    context,
                    httpDataSourceFactory
                )

                return HlsMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(mediaItem)
            }
        }
        return mediaSource
    }
    fun cookieBuilder (name:String, value:String, expiry: Date, httpUrl: HttpUrl): Cookie {
        val builder = Cookie.Builder()
            .name(name)
            .value(value)
            .domain(httpUrl.host)
            .path(httpUrl.pathSegments[0])
            .expiresAt(expiry.time)
        return builder.build().also {
            Log.d("amal", "cookieBuilder:${it.path} ")
        }
    }
    suspend fun downloadAndSaveFile(context: Context, fileUrl: String, fileName: String) : LiveData<Boolean> {
        Log.e("DownloadStatus", "Download in progress")
        val downloadComplete = MutableLiveData<Boolean>()
        withContext(Dispatchers.IO) {
            try {
                // Create a URL object from the file URL
                val url = URL(fileUrl)
                // Open a connection to the URL
                val urlConnection: HttpURLConnection = url.openConnection() as HttpURLConnection

                // Set up input and output streams
                urlConnection.connect()
                val input: InputStream = BufferedInputStream(urlConnection.inputStream)
                val output: OutputStream = FileOutputStream("$fileName/download.zip")

                // Read data from the input stream and write it to the output stream
                val buffer = ByteArray(1024)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                }

                // Close the streams
                output.flush()
                output.close()
                input.close()
                // Notify the user on the main thread
                withContext(Dispatchers.Main){
                    downloadComplete.value = true
                }
            } catch (e: IOException) {
                Log.e("DownloadError",e.toString())
                // Handle exceptions appropriately
                e.printStackTrace()

                // In case of an error, set the LiveData value to false
                withContext(Dispatchers.Main) {
                    downloadComplete.value = false
                }
            }
        }
        return downloadComplete
    }

}