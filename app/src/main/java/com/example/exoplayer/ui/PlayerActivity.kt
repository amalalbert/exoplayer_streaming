package com.example.exoplayer.ui

import UnzipUtils
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
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
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupMenu
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.ui.PlayerView
import com.bumptech.glide.Glide
import com.example.exoplayer.R
import com.example.exoplayer.TAG
import com.example.exoplayer.databinding.ActivityPlayerBinding
import com.example.exoplayer.models.Audio
import com.example.exoplayer.models.Resolution
import com.example.exoplayer.utils.ExoUtils
import com.example.exoplayer.utils.LoggingInterceptor
import com.franmontiel.persistentcookiejar.PersistentCookieJar
import com.franmontiel.persistentcookiejar.cache.SetCookieCache
import com.franmontiel.persistentcookiejar.persistence.SharedPrefsCookiePersistor
import com.github.rubensousa.previewseekbar.PreviewBar
import com.github.rubensousa.previewseekbar.media3.PreviewTimeBar
import kotlinx.coroutines.launch
import okhttp3.Cache
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.io.File
import java.time.Instant
import java.util.Date

/**
 * A fullscreen activity to play audio or video streams.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerActivity : AppCompatActivity() {

    private val viewBinding by lazy(LazyThreadSafetyMode.NONE) {
        ActivityPlayerBinding.inflate(layoutInflater)
    }
    lateinit var downloadCompleteLiveData : LiveData<Boolean>
    lateinit var subtitleSelectionPopup : PopupMenu
    lateinit var resolutionSelectorPopup : PopupMenu
    lateinit var audioSelectorPopup : PopupMenu
    private var lastSelectedAudioTrack : MenuItem? = null
    private var lastSelectedVideoTrack : MenuItem? = null
    private var lastSelectedSubtitleTrack : MenuItem? = null
    private val playbackStateListener : Player.Listener = playbackStateListener()
    private var player : ExoPlayer? = null
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
    private var thumbnailCache : File? = null
    private lateinit var downloadManager : DownloadManager
    private var reference : Long = 0L

    companion object {
        val cookies: ArrayList<Cookie?> = arrayListOf()
        private val httpurl = HttpUrl.Builder()
            .scheme("https")
            .host("d2ikp5103loz36.cloudfront.net")
            .addPathSegment("/trailer")
            .build()
        private val expiryDate: Date = Date.from(Instant.parse("2024-11-06T08:37:10Z"))

        init {
            cookies.add(
                ExoUtils.cookieBuilder(
                    "CloudFront-Policy",
                    "eyJTdGF0ZW1lbnQiOlt7IlJlc291cmNlIjoiaHR0cHM6Ly9kMmlrcDUxMDNsb3ozNi5jbG91ZGZyb250Lm5ldC8qIiwiQ29uZGl0aW9uIjp7IklwQWRkcmVzcyI6eyJBV1M6U291cmNlSXAiOiIxMDMuMTM1Ljk1LjE1NCJ9LCJEYXRlTGVzc1RoYW4iOnsiQVdTOkVwb2NoVGltZSI6MTczMjU4ODgwMH19fV19",
                    expiryDate,
                    httpurl
                )
            )
            cookies.add(
                ExoUtils.cookieBuilder(
                    "CloudFront-Signature",
                    "EyTVruMRs2cSNkuAmK11pIoN2cL5UFF5OYWSiGwuSEoEBpFccb7Zdz43MeZPVhwd8~A7~xkCtW0uKG0LsKxL38CMT0rB~QQkukDuY3sDtMc5mu4vF9sFk24Ieygw5ggDq1FG7-B-vSzJd6kxnoU3r8MK-lxzT~zMfGYrtgLqPAZ4gGi74CPvHXf-yJ5gYGeb~eXTWnMcjUjVE6SoiLZXrmq1P5DNrjHqcX083sR5x4R4f0lt6c0fos6a6pJavltwzGo6IUVvNmT5FN6qI7CyYkCF6Ppj4tlyVgI8eKLz6AOqczbBx-Z4KzXCdDngnM0w9ifNuuyEDnABn0CW41zT0A__",
                    expiryDate,
                    httpurl
                )
            )
            cookies.add(
                ExoUtils.cookieBuilder(
                    "CloudFront-Key-Pair-Id", "K24JEKU60VKL77",
                    expiryDate,
                    httpurl
                )
            )
        }
    }



    @RequiresApi(Build.VERSION_CODES.Q)
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    override fun  onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(viewBinding.root)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        val zipPath = File(filesDir, "download.zip")
        val fileUrl = "htps://drive.google.com/u/0/uc?id=1kJBRwxxcLHCkkqZbAGJ5LGttR5fCDTNw&export=download"
        downloadPath = File(filesDir, "thumbnails")
        if(downloadPath?.exists() == true){
            downloadPath?.delete()
        }

        lifecycleScope.launch {
            downloadCompleteLiveData = ExoUtils.downloadAndSaveFile(
                this@PlayerActivity,
                fileUrl,
                filesDir!!.absolutePath
            )

            downloadCompleteLiveData.observe(this@PlayerActivity, Observer { downloadComplete ->
                if (downloadComplete) {
                    // Download is complete
                    Log.d("DownloadStatus", "Download complete")
                    try {
                        File(filesDir, "").also {
                            it.mkdir()
                            thumbnailCache = File(it, "thumbnails")
                            if (!thumbnailCache!!.exists()){
                                thumbnailCache!!.mkdir()
                            }
                            thumbnailCache?.listFiles()?.forEach { prevThumbs->
                                prevThumbs.delete()
                            }
                            downloadPath?.let { downloadPath ->
                                downloadPath.mkdir()
                                UnzipUtils.unzip(zipPath, it.absolutePath)
                            }
                        }
                    }
                    catch (e:Exception){
                        e.printStackTrace()
                    }
                    // Update the UI accordingly
                } else {
                    // Download failed
                    Log.e("DownloadStatus", "Download failed")
                     lifecycleScope.launch {
                         downloadCompleteLiveData = ExoUtils.downloadAndSaveFile(
                             this@PlayerActivity, fileUrl, filesDir!!.absolutePath
                         )
                     }
                    // Handle download failure
                }
            })
        }

        viewBinding.button.isEnabled = false
        viewBinding.etLink.setTextColor(resources.getColor(R.color.white))
        viewBinding.etLink.clearFocus()

        previewTimeBar = viewBinding.videoView.findViewById(androidx.media3.ui.R.id.exo_progress)

        previewTimeBar?.setPreviewLoader { currentPosition, max ->

            if (player?.isPlaying == true) {
                player?.playWhenReady = true;
            }
            val imageView: ImageView = viewBinding.videoView.findViewById(R.id.imageView)

            try {
                Glide.with(imageView)
                    .load(thumbnailBasedOnPositionFromFile(thumbnailCache,10000,currentPosition))
                    .into(imageView)
            }
            catch (e:Exception){
                Glide.with(imageView)
                    .load(R.color.black)
                    .into(imageView)
                lifecycleScope.launch {
                    downloadCompleteLiveData = ExoUtils.downloadAndSaveFile(
                        this@PlayerActivity,
                        fileUrl,
                        filesDir!!.absolutePath
                    )

                    downloadCompleteLiveData.observe(this@PlayerActivity, Observer { downloadComplete ->
                        if (downloadComplete) {
                            // Download is complete
                            Log.d("DownloadStatus", "Download complete")
                            try {
                                File(filesDir, "").also {
                                    it.mkdir()
                                    thumbnailCache = File(it, "thumbnails")
                                    if (thumbnailCache?.exists() == false){
                                        thumbnailCache?.mkdir()
                                    }
                                    thumbnailCache?.listFiles()?.forEach { prevThumbs->
                                        prevThumbs.delete()
                                    }
                                    downloadPath?.let { downloadPath ->
                                        downloadPath.mkdir()
                                        UnzipUtils.unzip(zipPath, it.absolutePath)
                                    }
                                }
                            }
                            catch (e:Exception){
                                e.printStackTrace()
                            }
                            // Update the UI accordingly
                        } else {
                            // Download failed
                            Log.e("DownloadStatus", "Download failed")
                            lifecycleScope.launch {
                                downloadCompleteLiveData = ExoUtils.downloadAndSaveFile(
                                    this@PlayerActivity, fileUrl, filesDir!!.absolutePath
                                )
                            }
                            // Handle download failure
                        }
                    })
                }
            }

        }

        previewTimeBar?.addOnPreviewVisibilityListener { previewBar, isPreviewShowing ->
            Log.d("PreviewShowing", "$isPreviewShowing")
        }

        previewTimeBar?.addOnScrubListener(object : PreviewBar.OnScrubListener {

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
                ExoUtils.generateMediaSource(
                    text.toString(), this, cookies,
                    httpurl
                )?.let {
                    mediaSource = it
                    releasePlayer()
                    initializePlayer()
                }
            }
        }

       // set the media source
       ExoUtils.generateMediaSource(
           getString(R.string.media_url_m3u8_multi_language), this,
           cookies, httpurl
       )?.let {
            mediaSource = it
        }
    }

    override fun onStart() {
        super.onStart()
        if (player == null) {
            initializePlayer()
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemUi()
        if (player == null) {
            initializePlayer()
        }
    }

    override fun onPause() {
        super.onPause()
            player?.pause()
    }

    override fun onDestroy() {
        releasePlayer()
        super.onDestroy()
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
            ?.buildUpon()?.setPreferredTextLanguage("en")
            ?.build()!!

        viewBinding.videoView.findViewById<ImageButton>(androidx.media3.ui.R.id.exo_pause)
            .setOnClickListener {
                player?.pause()
            }
        viewBinding.videoView.findViewById<ImageButton>(androidx.media3.ui.R.id.exo_play)
            .setOnClickListener {
                player?.play()
            }

        val wrapper: Context = ContextThemeWrapper(
            this,
            androidx.appcompat.R.style.ThemeOverlay_AppCompat_Dark_ActionBar
        )

        //audio selector
        audioSelectorPopup = PopupMenu(wrapper, viewBinding.videoView.findViewById(R.id.audio))
        audioSelectorPopup.menuInflater.inflate(R.menu.popup_menu,audioSelectorPopup.menu)
        audioSelectorPopup.menu.setGroupCheckable(0,true,true)
        audioSelectorPopup.setOnMenuItemClickListener { item ->
            if(lastSelectedAudioTrack != null){
                lastSelectedAudioTrack?.isChecked = false
            }
            lastSelectedAudioTrack = item
            item.isCheckable = true
            item.isChecked = true
            lateinit var selectedAudioTrack: Audio
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
        subtitleSelectionPopup = PopupMenu(wrapper, viewBinding.videoView.findViewById(R.id.sub))
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
        resolutionSelectorPopup =
            PopupMenu(wrapper, viewBinding.videoView.findViewById(R.id.resolution))
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
            lateinit var selectedResolution: Resolution
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
        viewBinding.videoView.setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
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
                        audiolist?.add(
                            Audio(
                                trackFormat.language, trackFormat.label,
                                trackFormat.id
                            )
                        )
                    }
                }
                if(trackType == C.TRACK_TYPE_VIDEO){
                    val audioTrackGroup = trackGroup.mediaTrackGroup
                    for (i in 0 until audioTrackGroup.length) {
                        val trackFormat: Format = audioTrackGroup.getFormat(i)
                        reslist?.add(
                            Resolution(
                                trackFormat.width, trackFormat.height, trackFormat.id
                            )
                        )
                    }
                }
            }

            audioSelectorPopup.menu.clear()
            audiolist?.forEach {
                it.trackId?.let {trackid->
                    audioSelectorPopup.menu.add(
                        Menu.NONE,
                        audiolist!!.indexOf(it), Menu.NONE,trackid).apply {
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
                    resolutionSelectorPopup.menu.add(
                        Menu.NONE,
                        it1, Menu.NONE,"${it.width}p").apply {
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
    private fun thumbnailBasedOnPositionFromFile (file: File?, threshold:Int, currentPosition:Long): File?{
       return file?.listFiles()?.get((currentPosition/threshold).toInt())
    }

    private fun cloudFrontHttpsSource(url: String, context: Context): HlsMediaSource {
        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(url))
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .build()

        cookies.add(
            ExoUtils.cookieBuilder(
                "CloudFront-Policy",
                "eyJTdGF0ZW1lbnQiOlt7IlJlc291cmNlIjoiaHR0cHM6Ly9kMmlrcDUxMDNsb3ozNi5jbG91ZGZyb250Lm5ldC8qIiwiQ29uZGl0aW9uIjp7IklwQWRkcmVzcyI6eyJBV1M6U291cmNlSXAiOiIxMDMuMTM1Ljk1LjE1NCJ9LCJEYXRlTGVzc1RoYW4iOnsiQVdTOkVwb2NoVGltZSI6MTczMjU4ODgwMH19fV19",
                expiryDate,
                httpurl
            )
        )
        cookies.add(
            ExoUtils.cookieBuilder(
                "CloudFront-Signature",
                "EyTVruMRs2cSNkuAmK11pIoN2cL5UFF5OYWSiGwuSEoEBpFccb7Zdz43MeZPVhwd8~A7~xkCtW0uKG0LsKxL38CMT0rB~QQkukDuY3sDtMc5mu4vF9sFk24Ieygw5ggDq1FG7-B-vSzJd6kxnoU3r8MK-lxzT~zMfGYrtgLqPAZ4gGi74CPvHXf-yJ5gYGeb~eXTWnMcjUjVE6SoiLZXrmq1P5DNrjHqcX083sR5x4R4f0lt6c0fos6a6pJavltwzGo6IUVvNmT5FN6qI7CyYkCF6Ppj4tlyVgI8eKLz6AOqczbBx-Z4KzXCdDngnM0w9ifNuuyEDnABn0CW41zT0A__",
                expiryDate,
                httpurl
            )
        )
        cookies.add(
            ExoUtils.cookieBuilder(
                "CloudFront-Key-Pair-Id",
                "K24JEKU60VKL77",
                expiryDate,
                httpurl
            )
        )

        val interceptor = LoggingInterceptor()

        val cookieJar: CookieJar =
            PersistentCookieJar(SetCookieCache(), SharedPrefsCookiePersistor(context))

        cookieJar.saveFromResponse(
            httpurl,
            cookies.toList() as List<Cookie>
        )

        val client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .cookieJar(cookieJar)
            .cache(Cache(cacheDir, 4096))
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