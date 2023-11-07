package com.example.exoplayer.utils

import android.content.Context
import android.net.Uri
import androidx.media3.common.C.TRACK_TYPE_TEXT
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Tracks
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import com.example.exoplayer.models.Language
import java.util.*

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
     *@return MediaSource
     */
    fun generateMediaSource(url: String, context: Context): MediaSource? {
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
                val dataSourceFactory = DefaultDataSource.Factory(context)
                mediaSource =
                    HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
            }
        }
        return mediaSource
    }
}