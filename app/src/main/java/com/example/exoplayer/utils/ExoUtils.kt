package com.example.exoplayer.utils

import android.content.Context
import android.net.Uri
import android.text.Editable
import android.util.Log
import android.widget.EditText
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

    fun generateMediaSource(text: String, context: Context): MediaSource? {
        var mediaSource: MediaSource? = null
        if (text.isNotEmpty()) {
            if (text.contains(".mpd")) {
                val mediaItem = MediaItem.Builder()
                    .setUri(Uri.parse(text))
                    .setMimeType(MimeTypes.APPLICATION_MPD)
                    .build()
                val dataSourceFactory = DefaultDataSource.Factory(context)
                mediaSource =
                    DashMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
            } else if (text.contains(".m3u8")) {
                val mediaItem = MediaItem.Builder()
                    .setUri(Uri.parse(text))
                    .setMimeType(MimeTypes.APPLICATION_MPD)
                    .build()
                val dataSourceFactory = DefaultDataSource.Factory(context)
                mediaSource =
                    HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
            }
        }
        return mediaSource
    }
}