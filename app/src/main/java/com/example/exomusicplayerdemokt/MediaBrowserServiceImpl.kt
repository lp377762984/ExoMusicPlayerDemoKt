package com.example.exomusicplayerdemokt

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.*
import android.os.Environment.DIRECTORY_MUSIC
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
import android.support.v4.media.MediaBrowserServiceCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DURATION
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.source.ConcatenatingMediaSource
import com.google.android.exoplayer2.source.ExtractorMediaSource
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.trackselection.TrackSelectionArray
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import java.util.*

const val MY_MEDIA_ROOT_ID = "media_root_id"
const val LOG_TAG = "MediaBrowserService"

class MediaBrowserServiceImpl : MediaBrowserServiceCompat() {
    private var position = 0;
    private var mediaSession: MediaSessionCompat? = null
    private lateinit var stateBuilder: PlaybackStateCompat.Builder
    private lateinit var mediaMetadata: ArrayList<MediaMetadataCompat>
    private val uAmpAudioAttributes = com.google.android.exoplayer2.audio.AudioAttributes.Builder()
        .setContentType(C.CONTENT_TYPE_MUSIC)
        .setUsage(C.USAGE_MEDIA)
        .build()
    private val exoPlayer: ExoPlayer by lazy {
        ExoPlayerFactory.newSimpleInstance(this).apply {
            setAudioAttributes(uAmpAudioAttributes, true)
        }
    }

    override fun onCreate() {
        super.onCreate()
        sweepSongs()
        // Create a MediaSessionCompat
        mediaSession = MediaSessionCompat(baseContext, LOG_TAG).apply {
            // Enable callbacks from MediaButtons and TransportControls
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
                        or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            stateBuilder = PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY
                            or PlaybackStateCompat.ACTION_PLAY_PAUSE
                            or PlaybackStateCompat.ACTION_STOP
                            or PlaybackStateCompat.ACTION_SEEK_TO
                            or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                            or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                )
            setPlaybackState(stateBuilder.build())
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPrepareFromMediaId(mediaId: String?, extras: Bundle?) {
                    Log.d(LOG_TAG, "MediaSessionCompat.Callback onPrepareFromMediaId")
                    prepareSource()
                    setPlaybackState(stateBuilder.setState(PlaybackStateCompat.STATE_STOPPED, 0, 1f).build())
                }

                override fun onPlay() {
                    Log.d(
                        LOG_TAG,
                        "MediaSessionCompat.Callback onPlay currentWindowIndex ${exoPlayer.currentWindowIndex}"
                    )
                    isActive = true
                    exoPlayer.playWhenReady = true
                    exoPlayer.seekTo(exoPlayer.currentWindowIndex, exoPlayer.currentPosition)
                    //setPlaybackState
                    setPlaybackState(stateBuilder.setState(PlaybackStateCompat.STATE_PLAYING, 0, 1f).build())
                    //setMetaData
                    val metadataCompat = mediaMetadata[exoPlayer.currentWindowIndex]
                    metadataCompat.bundle.putLong(METADATA_KEY_DURATION, exoPlayer.duration)
                    setMetadata(metadataCompat)
                    //Log.d(LOG_TAG, "MediaSessionCompat.Callback onPlay total_time ${exoPlayer.duration}")
                }

                override fun onPause() {
                    Log.d(LOG_TAG, "MediaSessionCompat.Callback onPause currentWindowIndex ${exoPlayer.currentWindowIndex}")
                    exoPlayer.playWhenReady = false
                    setPlaybackState(stateBuilder.setState(PlaybackStateCompat.STATE_PAUSED, 0, 1f).build())
                }

                override fun onStop() {
                    Log.d(LOG_TAG, "MediaSessionCompat.Callback onStop")
                    exoPlayer.stop(true)
                    isActive = false
                    setPlaybackState(stateBuilder.setState(PlaybackStateCompat.STATE_STOPPED, 0, 1f).build())
                }

                override fun onSkipToNext() {//见TimelineQueueNavigator.java的写法
                    if (exoPlayer.hasNext()) {
                        exoPlayer.seekTo(exoPlayer.nextWindowIndex, 0)
                        exoPlayer.playWhenReady = true
                        setPlaybackState(stateBuilder.setState(PlaybackStateCompat.STATE_SKIPPING_TO_NEXT, 0, 1f).build())
                    }
                    Log.d(LOG_TAG, "MediaSessionCompat.Callback onSkipToNext currentWindowIndex ${exoPlayer.currentWindowIndex}")

                }

                override fun onSkipToPrevious() {//见TimelineQueueNavigator.java的写法
                    if (exoPlayer.hasPrevious()) {
                        exoPlayer.seekTo(exoPlayer.previousWindowIndex, 0)
                        exoPlayer.playWhenReady = true
                        setPlaybackState(stateBuilder.setState(
                            PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS, 0, 1f).build()
                        )
                    }
                    Log.d(LOG_TAG, "MediaSessionCompat.Callback onSkipToPrevious currentWindowIndex ${exoPlayer.currentWindowIndex}")

                }

                override fun onSkipToQueueItem(id: Long) {
                    Log.d(LOG_TAG, "MediaSessionCompat.Callback onSkipToQueueItem")
                    exoPlayer.seekTo(id.toInt(), 0)
                    exoPlayer.playWhenReady = true
                    setPlaybackState(
                        stateBuilder.setState(
                            PlaybackStateCompat.STATE_SKIPPING_TO_QUEUE_ITEM,
                            0,
                            1f
                        ).build()
                    )
                }

                override fun onSeekTo(pos: Long) {
                    Log.d(LOG_TAG, "MediaSessionCompat.Callback onSeekTo")
                    exoPlayer.seekTo(pos)
                    setPlaybackState(stateBuilder.setState(PlaybackStateCompat.STATE_PLAYING, 0, 1f).build())
                }
            })
            // Set the session's token so that client activities can communicate with it.
            setSessionToken(sessionToken)
        }
    }
    private var handler : Handler = @SuppressLint("HandlerLeak")
    object :Handler(){
        override fun handleMessage(msg: Message?) {

        }
    }

    private fun prepareSource() {
        val dataSourceFactory = DefaultDataSourceFactory(
            applicationContext,
            Util.getUserAgent(applicationContext, "ExoMusicPlayerDemoKt"),
            null
        )
        val concatenatingMediaSource = ConcatenatingMediaSource()
        mediaMetadata.forEach {
            concatenatingMediaSource.addMediaSource(ExtractorMediaSource.Factory(dataSourceFactory).createMediaSource(it.description.mediaUri))
        }
        exoPlayer.addListener(object : Player.EventListener {
            override fun onTimelineChanged(timeline: Timeline?, manifest: Any?, reason: Int) {
                Log.d(LOG_TAG, "EventListener onTimelineChanged")
            }

            override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
                Log.d(LOG_TAG, "EventListener onPlayerStateChanged: $playWhenReady $playbackState ")
                if (playWhenReady && playbackState == PlaybackStateCompat.STATE_PLAYING){

                }
            }
            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters?) {
                Log.d(LOG_TAG, "EventListener onPlaybackParametersChanged")
            }

            override fun onSeekProcessed() {
                Log.d(LOG_TAG, "EventListener onSeekProcessed")
            }

            override fun onTracksChanged(trackGroups: TrackGroupArray?, trackSelections: TrackSelectionArray?) {
                Log.d(LOG_TAG, "EventListener onTracksChanged")
            }

            override fun onPlayerError(error: ExoPlaybackException?) {
                Log.d(LOG_TAG, "EventListener onPlayerError")
            }

            override fun onLoadingChanged(isLoading: Boolean) {
                Log.d(LOG_TAG, "EventListener onLoadingChanged")
            }

            override fun onPositionDiscontinuity(reason: Int) {
                Log.d(LOG_TAG, "EventListener onPositionDiscontinuity")
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                Log.d(LOG_TAG, "EventListener onRepeatModeChanged")
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                Log.d(LOG_TAG, "EventListener onShuffleModeEnabledChanged")
            }
        })
        exoPlayer.stop()
        exoPlayer.playWhenReady = false
        exoPlayer.prepare(concatenatingMediaSource)
    }

    private fun sweepSongs() {
        ResolveTask(object : DataListener {
            override fun dataGet(result: ArrayList<MediaMetadataCompat>) {
                this@MediaBrowserServiceImpl.mediaMetadata = result
            }
        }).execute(baseContext)
    }

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        if (mediaMetadata.isNullOrEmpty()) {
            result.detach()
            return
        }
        val mediaItems = mutableListOf<MediaBrowserCompat.MediaItem>()
        mediaMetadata.forEach {
            mediaItems += MediaBrowserCompat.MediaItem(it.description, FLAG_PLAYABLE)
        }
        result.sendResult(mediaItems)
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot? {
        return MediaBrowserServiceCompat.BrowserRoot(MY_MEDIA_ROOT_ID, null)
    }
}

class ResolveTask(val listener: DataListener) : AsyncTask<Context, Void, ArrayList<MediaMetadataCompat>>() {
    override fun doInBackground(vararg params: Context?): ArrayList<MediaMetadataCompat> {
        val datas = ArrayList<MediaMetadataCompat>()
        val musicDir = Environment.getExternalStoragePublicDirectory(DIRECTORY_MUSIC)
        Log.d("ResolveTask", "${musicDir.absoluteFile}")
        params.forEach {
            if (musicDir.isDirectory && musicDir.canRead()) {
                val listFiles = musicDir.listFiles().filter {
                    it.isFile && it.absolutePath.endsWith(".mp3")
                }.forEachIndexed { index, file ->
                    Log.d("ResolveTask_after", file.absolutePath)
                    datas +=
                        MediaMetadataCompat.Builder()
                            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, index.toString())
                            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, file.name)
                            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, file.name)
                            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI, Uri.fromFile(file).path)
                            .build()
                }
            }
        }
        return datas
    }

    override fun onPostExecute(result: ArrayList<MediaMetadataCompat>?) {
        Log.d("ResolveTask", "${result?.size}")
        if (result != null) {
            listener.dataGet(result)
        }
    }

}

interface DataListener {
    fun dataGet(result: ArrayList<MediaMetadataCompat>)
}

class SendTimerTask(totalTime:Long,currentTime:Long) : TimerTask() {
    override fun run() {

    }
}
