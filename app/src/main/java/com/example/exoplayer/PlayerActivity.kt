/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.exoplayer

import UnzipUtils
import android.annotation.SuppressLint
import android.app.Dialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.Window
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupMenu
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.ui.PlayerView.SHOW_BUFFERING_ALWAYS
import com.bumptech.glide.Glide
import com.example.exoplayer.databinding.ActivityPlayerBinding
import com.example.exoplayer.models.Audio
import com.example.exoplayer.models.Resolution
import com.example.exoplayer.utils.ExoUtils
import com.github.rubensousa.previewseekbar.PreviewBar
import com.github.rubensousa.previewseekbar.PreviewBar.OnScrubListener
import com.github.rubensousa.previewseekbar.media3.PreviewTimeBar
import java.io.File


private const val TAG = "PlayerActivity"

/**
 * A fullscreen activity to play audio or video streams.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerActivity : AppCompatActivity() {

    private val viewBinding by lazy(LazyThreadSafetyMode.NONE) {
        ActivityPlayerBinding.inflate(layoutInflater)
    }
    lateinit var subtitleSelectionPopup : PopupMenu
    lateinit var resolutionSelectorPopup : PopupMenu
    lateinit var audioSelectorPopup : PopupMenu
    private var lastSelectedAudioTrack : MenuItem? = null
    private var lastSelectedVideoTrack : MenuItem? = null
    private var lastSelectedSubtitleTrack : MenuItem? = null
    private var languageCode : String? = null
    private val playbackStateListener : Player.Listener = playbackStateListener()
    private var player : ExoPlayer? = null
    private var mediaItem :MediaItem? = null
    private var playWhenReady = true
    private var downloadPath : File? = null
    private var mediaItemIndex = 0
    private var playbackPosition = 0L
    private var previewTimeBar : PreviewTimeBar? = null
    private var mediaCodecSelector : MediaCodecSelector? = null
    private lateinit var mediaSource : MediaSource
    private var sublist : ArrayList<String>? = arrayListOf()
    private var reslist : ArrayList<Resolution>? = arrayListOf()
    private var audiolist : ArrayList<Audio>? = arrayListOf()
    private var resumeVideoOnPreviewStop : Boolean = false
    private lateinit var dataSourceFactory : DefaultDataSource.Factory
    private var thumbnailCache : File? = null
    private lateinit var downloadManager : DownloadManager
    private var reference : Long = 0L

    @RequiresApi(Build.VERSION_CODES.Q)
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    override fun  onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(viewBinding.root)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        downloadPath = File(externalCacheDir,"thumbnailcache.zip")
        if(downloadPath?.exists() == true){
            downloadPath?.delete()
        }

        val request = DownloadManager
            .Request(Uri
            .parse("https://video-transcoder.sfo3.digitaloceanspaces.com/ott/thumbnails.zip"))
            .setDestinationUri(downloadPath?.toUri())
        reference = downloadManager.enqueue(request)

        val onComplete: BroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctxt: Context, intent: Intent) {
                // your code
                File(filesDir, "").also {
                    it.mkdir()
                    downloadPath?.let { downloadPath ->
                        UnzipUtils.unzip(downloadPath,it.absolutePath)
                    }
                    thumbnailCache = File(it,"thumbnails")
                }
            }
        }

        if (Build.VERSION.SDK_INT>= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                RECEIVER_EXPORTED)
        }
        else{
            registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        }


        viewBinding.button.isEnabled = false
        viewBinding.etLink.setTextColor(resources.getColor(R.color.white))
        viewBinding.etLink.clearFocus()

        previewTimeBar = viewBinding.videoView.findViewById(androidx.media3.ui.R.id.exo_progress)
        previewTimeBar?.setPreviewLoader { currentPosition, max ->

            if (player?.isPlaying == true) {
                player?.playWhenReady = true;
            }
            val imageView:ImageView = viewBinding.videoView.findViewById(R.id.imageView)

            try {
                Glide.with(imageView)
                    .load(thumbnailBasedOnPositionFromFile(thumbnailCache,10000,currentPosition))
                    .into(imageView)
            }
            catch (e:Exception){
                Glide.with(imageView)
                    .load(R.drawable.ic_play)
                    .into(imageView)
            }

        }

        previewTimeBar?.addOnPreviewVisibilityListener { previewBar, isPreviewShowing ->
                Log.d("PreviewShowing", "$isPreviewShowing")
        }

        previewTimeBar?.addOnScrubListener(object : OnScrubListener {

            override fun onScrubStart(previewBar: PreviewBar?) {
                Log.d("Scrub", "START");
            }

            override fun onScrubMove(previewBar: PreviewBar?, progress: Int, fromUser: Boolean) {
                Log.d("Scrub", "MOVE to " + progress / 1000 + " FROM USER: " + fromUser);
            }

            override fun onScrubStop(previewBar: PreviewBar?) {
                Log.d("Scrub", "STOP");
            }

        })

        viewBinding.etLink.addTextChangedListener(object : TextWatcher {

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                //no op
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewBinding.button.isEnabled =
                    s?.contains(".mpd") == true || s?.contains(".m3u8") == true
            }

            override fun afterTextChanged(s: Editable?) {
                //no op
            }

        })

        // generate source on click
        viewBinding.button.setOnClickListener {
            viewBinding.etLink.text.let {text->
                ExoUtils.generateMediaSource(text.toString(),this)?.let {
                    mediaSource = it
                    releasePlayer()
                    initializePlayer()
                }
            }
        }

       // set the media source
       ExoUtils.generateMediaSource(getString(R.string.media_url_m3u8_multi_language),this)?.let {
            mediaSource = it
        }
    }

    public override fun onStart() {
        super.onStart()
            initializePlayer()
    }

    public override fun onResume() {
        super.onResume()
        hideSystemUi()
        if (player == null) {
            initializePlayer()
        }
    }

    public override fun onPause() {
        super.onPause()
            releasePlayer()
    }

    public override fun onStop() {
        super.onStop()
            releasePlayer()
    }

    /**
     * Function to initialize the player, set the source and
     */
    private fun initializePlayer() {

        // ExoPlayer implements the Player interface
        player = ExoPlayer.Builder(this)
            .build()
            .also { exoPlayer ->
                viewBinding.videoView.player = exoPlayer
                // Update the track selection parameters to only pick standard definition tracks

                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                        .buildUpon()
                    .setMaxVideoSize(1921,828)
                        .setPreferredTextLanguage("en")
                        .build()
                exoPlayer.setMediaSource(mediaSource)
                exoPlayer.playWhenReady = playWhenReady
                exoPlayer.addListener(playbackStateListener)
                this.resumeVideoOnPreviewStop = true
                exoPlayer.prepare()
            }
        player?.trackSelectionParameters = player?.trackSelectionParameters
            ?.buildUpon()?.setPreferredTextLanguage("de")
            ?.build()!!

        viewBinding.videoView.findViewById<ImageButton>(androidx.media3.ui.R.id.exo_pause)
            .setOnClickListener {
                player?.pause()
            }
        viewBinding.videoView.findViewById<ImageButton>(androidx.media3.ui.R.id.exo_play)
            .setOnClickListener {
                player?.play()
            }

        val wrapper: Context = ContextThemeWrapper(this,
            androidx.appcompat.R.style.ThemeOverlay_AppCompat_Dark_ActionBar)

        //audio selector
        audioSelectorPopup = PopupMenu(wrapper,viewBinding.videoView.findViewById(R.id.audio))
        audioSelectorPopup.menuInflater.inflate(R.menu.popup_menu,audioSelectorPopup.menu)
        audioSelectorPopup.menu.setGroupCheckable(0,true,true)
        audioSelectorPopup.setOnMenuItemClickListener { item ->
            if(lastSelectedAudioTrack != null){
                lastSelectedAudioTrack?.isChecked = false
            }
            lastSelectedAudioTrack = item
            item.isCheckable = true
            item.isChecked = true
            lateinit var selectedAudioTrack:Audio
            audiolist?.forEach { audio->
                if(audio.trackId == item.title){
                    selectedAudioTrack = audio
                }
            }
            player?.trackSelectionParameters = player?.trackSelectionParameters
                ?.buildUpon()?.setPreferredAudioLanguage(selectedAudioTrack.trackName)
                ?.build()!!
            true
        }
        audioSelectorPopup.setOnDismissListener {
            lastSelectedAudioTrack?.let { it1 -> it.menu.findItem(it1.itemId).isChecked = true }
        }
        //subtitle selector
        subtitleSelectionPopup = PopupMenu(wrapper,viewBinding.videoView.findViewById(R.id.sub))
        subtitleSelectionPopup.menuInflater.inflate(R.menu.popup_menu,subtitleSelectionPopup.menu)
        subtitleSelectionPopup.menu.setGroupCheckable(0,true,true)


        subtitleSelectionPopup.setOnMenuItemClickListener { item ->
            if(lastSelectedSubtitleTrack != null){
                lastSelectedSubtitleTrack?.isChecked = false
            }
            lastSelectedSubtitleTrack = item
            item.isCheckable = true
            item.isChecked = true
            player?.trackSelectionParameters = player?.trackSelectionParameters
                ?.buildUpon()?.setPreferredTextLanguage(item.title.toString())
                ?.build()!!
            true
        }

        //resolution selector
        resolutionSelectorPopup = PopupMenu(wrapper,viewBinding.videoView.findViewById(R.id.resolution))
        resolutionSelectorPopup.menuInflater.inflate(R.menu.popup_menu,subtitleSelectionPopup.menu)
        resolutionSelectorPopup.menu.setGroupCheckable(0,true,true)
        resolutionSelectorPopup.setOnMenuItemClickListener {item ->
            Log.d("amal", "initializePlayer:${item.itemId} ")
            if(lastSelectedVideoTrack != null){
                lastSelectedVideoTrack?.isChecked = false
            }
            lastSelectedVideoTrack = item
            item.isCheckable = true
            val selectedItemId = item.itemId
            lateinit var selectedResolution:Resolution
            reslist?.forEach {
                if(it.id?.toInt() == selectedItemId){
                    selectedResolution = it
                }
            }

            selectedResolution.let {
                player?.trackSelectionParameters = player?.trackSelectionParameters
                    ?.buildUpon()?.
                    setMaxVideoSize(selectedResolution.width,selectedResolution.height)
                    ?.build()!!
            }
            item.isChecked = true
            true
        }

        viewBinding.videoView.setShowSubtitleButton(true)
        viewBinding.videoView.showController()
        viewBinding.videoView.setShowBuffering(SHOW_BUFFERING_ALWAYS)
        viewBinding.videoView.controllerShowTimeoutMs = 2000
        viewBinding.videoView.controllerHideOnTouch = true
        Handler(Looper.getMainLooper()).postDelayed({
            viewBinding.videoView.hideController()
        },500)
        viewBinding.videoView.setOnClickListener {
        }
        viewBinding.videoView.findViewById<ImageButton>(R.id.sub)
            .setOnClickListener {
                    lastSelectedSubtitleTrack?.itemId?.let { it1 ->
                        subtitleSelectionPopup.menu.findItem(it1).setChecked(true)
                    }
                subtitleSelectionPopup.show()
            }
        viewBinding.videoView.findViewById<ImageButton>(R.id.resolution)
            .setOnClickListener {
                lastSelectedVideoTrack?.itemId?.let {it2->
                    resolutionSelectorPopup.menu.findItem(it2).setChecked(true)
                }
                resolutionSelectorPopup.show()
            }
        viewBinding.videoView.findViewById<ImageButton>(R.id.audio).setOnClickListener {
            lastSelectedAudioTrack?.itemId?.let {it2->
                audioSelectorPopup.menu.findItem(it2).setChecked(true)
            }
            audioSelectorPopup.show()
        }
    }

    /**
     * Releases the already initialized player.
     */
    private fun releasePlayer() {
        player?.let { player ->
            playbackPosition = player.currentPosition
            mediaItemIndex = player.currentMediaItemIndex
            playWhenReady = player.playWhenReady
            player.removeListener(playbackStateListener)
            player.release()
        }
        player = null
    }

    @SuppressLint("InlinedApi")
    /**
     * Hides the System Navbar
     */
    private fun hideSystemUi() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, viewBinding.videoView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    /**
     *@return object of Player.Listener
     */
    private fun playbackStateListener() = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {

            if (playbackState == ExoPlayer.STATE_READY){
                viewBinding.videoView.
                findViewById<PreviewTimeBar>(androidx.media3.ui.R.id.exo_progress).hidePreview()
            }
            val stateString: String = when (playbackState) {
                ExoPlayer.STATE_IDLE -> "ExoPlayer.STATE_IDLE      -"
                ExoPlayer.STATE_BUFFERING -> "ExoPlayer.STATE_BUFFERING -"
                ExoPlayer.STATE_READY -> "ExoPlayer.STATE_READY     -"
                ExoPlayer.STATE_ENDED -> "ExoPlayer.STATE_ENDED     -"
                else -> "UNKNOWN_STATE             -"
            }
            Log.d(TAG, "changed state to $stateString")
        }

        override fun onTracksChanged(tracks: Tracks) {
            if (sublist?.isNotEmpty() == true){
                sublist?.clear()
            }
            if (reslist?.isNotEmpty() == true){
                reslist?.clear()
            }
            if (audiolist?.isNotEmpty() == true){
                audiolist?.clear()
            }
            super.onTracksChanged(tracks)

            val trackGroupList = tracks.groups
            for (trackGroup in trackGroupList) {
                // Group level information.
                val trackType: @C.TrackType Int = trackGroup.type
                if (trackType == C.TRACK_TYPE_TEXT) {
                    val videoTrackGroup = trackGroup.mediaTrackGroup
                    for (i in 0 until trackGroup.mediaTrackGroup.length) {
                        val trackFormat: Format = videoTrackGroup.getFormat(i)
                        trackFormat.language?.let {
                            sublist?.add(it) }
                    }
                }
                if(trackType == C.TRACK_TYPE_AUDIO){
                    val audioTrackGroup = trackGroup.mediaTrackGroup
                    for (i in 0 until audioTrackGroup.length) {
                        val trackFormat: Format = audioTrackGroup.getFormat(i)
                        audiolist?.add(Audio(trackFormat.language,trackFormat.label,
                            trackFormat.id
                        ))
                    }
                }
                if(trackType == C.TRACK_TYPE_VIDEO){
                    val audioTrackGroup = trackGroup.mediaTrackGroup
                    for (i in 0 until audioTrackGroup.length) {
                        val trackFormat: Format = audioTrackGroup.getFormat(i)
                        reslist?.add(Resolution(trackFormat.width,trackFormat.height
                            ,trackFormat.id))
                    }
                }
            }

            audioSelectorPopup.menu.clear()
            audiolist?.forEach {
                it.trackId?.let {trackid->
                    audioSelectorPopup.menu.add(Menu.NONE,
                        audiolist!!.indexOf(it),Menu.NONE,trackid).apply {
                        this.isCheckable = true
                    }
                }
            }

            subtitleSelectionPopup.menu.clear()
            sublist?.forEach {
                subtitleSelectionPopup.menu.add(it).apply {
                    this.isCheckable = true
                }
            }

            resolutionSelectorPopup.menu.clear()
            reslist?.forEach {
                it.id?.toInt()?.let { it1 ->
                    resolutionSelectorPopup.menu.add(Menu.NONE,
                        it1,Menu.NONE,"${it.width}p").apply {
                        this.isCheckable = true
                    }
                }
            }
        }
    }


    /**
     * @param file  File representation of current thumbnail directory.
     * @param threshold  The value of the aggregate used to calculate the index of file to be
     * shown as thumbnail.
     * @param currentPosition The value of current position in millis.
     * @return File at the calculated index
     */
    private fun thumbnailBasedOnPositionFromFile (file:File?, threshold:Int, currentPosition:Long):File?{
       return file?.listFiles()?.get((currentPosition/threshold).toInt())
    }
}


