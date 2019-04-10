package com.example.exomusicplayerdemokt

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.os.Environment
import android.os.Environment.DIRECTORY_MUSIC
import android.os.ResultReceiver
import android.support.v4.media.*
import android.support.v4.media.MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
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
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
                        or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
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
                override fun onPlay() {
                    Log.d(LOG_TAG, "MediaSessionCompat.Callback onPlay")
                    isActive = true

                    Log.d(LOG_TAG, "MediaSessionCompat.Callback onPlay currentWindowIndex ${exoPlayer.currentWindowIndex}")
                    if (exoPlayer.isLoading) {
                        exoPlayer.seekTo(exoPlayer.currentWindowIndex,exoPlayer.currentPosition)
                    } else {
                        prepareSource();
                    }
                    exoPlayer.playWhenReady = true

                    //setMetadata()
                    val mediaMetadataCompat = mediaMetadata[exoPlayer.currentWindowIndex]
                    mediaMetadataCompat.bundle.putLong("total_time",exoPlayer.duration)
                    mediaMetadataCompat.bundle.putLong("current_time",exoPlayer.currentPosition)
                    setMetadata(mediaMetadataCompat)
                    //setPlaybackState
                    setPlaybackState(stateBuilder.setState(PlaybackStateCompat.STATE_PLAYING,0,1f).build())
                }

                override fun onPause() {
                    Log.d(LOG_TAG, "MediaSessionCompat.Callback onPause")
                    exoPlayer.playWhenReady = false
                    setPlaybackState(stateBuilder.setState(PlaybackStateCompat.STATE_PAUSED,0,1f).build())
                }

                override fun onStop() {
                    Log.d(LOG_TAG, "MediaSessionCompat.Callback onStop")
                    exoPlayer.stop()
                    isActive = false
                    setPlaybackState(stateBuilder.setState(PlaybackStateCompat.STATE_STOPPED,0,1f).build())
                }

                override fun onSkipToQueueItem(id: Long) {
                    //Log.d(LOG_TAG, "MediaSessionCompat.Callback onSkipToQueueItem")
                    exoPlayer.seekTo(id.toInt(),0)
                    setPlaybackState(stateBuilder.setState(PlaybackStateCompat.STATE_SKIPPING_TO_QUEUE_ITEM,0,1f).build())
                }


                override fun onSkipToNext() {
                    Log.d(LOG_TAG, "MediaSessionCompat.Callback onSkipToNext isLoading:${exoPlayer.isLoading}")
                    if (!exoPlayer.isLoading){
                        prepareSource()
                    }
                    if (exoPlayer.hasNext()) {
                        if (exoPlayer.isLoading){
                            exoPlayer.next()
                        }else{
                            exoPlayer.seekTo(1,0)
                        }
                        setPlaybackState(stateBuilder.setState(PlaybackStateCompat.STATE_SKIPPING_TO_NEXT,0,1f).build())
                    }
                    exoPlayer.playWhenReady = true

                }

                override fun onSkipToPrevious() {
                    Log.d(LOG_TAG, "MediaSessionCompat.Callback onSkipToPrevious")
                    if (exoPlayer.hasPrevious()) {
                        exoPlayer.previous()
                        setPlaybackState(stateBuilder.setState(PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS,0,1f).build())
                    }
                }

                override fun onSeekTo(pos: Long) {
                    Log.d(LOG_TAG, "MediaSessionCompat.Callback onSeekTo")
                    exoPlayer.seekTo(pos)
                    setPlaybackState(stateBuilder.setState(PlaybackStateCompat.STATE_PLAYING,0,1f).build())
                }

                override fun onMediaButtonEvent(mediaButtonEvent: Intent?): Boolean {
                    Log.d(LOG_TAG, "MediaSessionCompat.Callback onMediaButtonEvent")
                    return onMediaButtonEvent(mediaButtonEvent)
                }

                override fun onAddQueueItem(description: MediaDescriptionCompat?) {
                    Log.d(LOG_TAG, "MediaSessionCompat.Callback onAddQueueItem")
                }

                override fun onAddQueueItem(description: MediaDescriptionCompat?, index: Int) {
                    Log.d(LOG_TAG, "MediaSessionCompat.Callback onAddQueueItem")
                }

                override fun onCustomAction(action: String?, extras: Bundle?) {
                    Log.d(LOG_TAG, "MediaSessionCompat.Callback onCustomAction")
                }

                override fun onPrepare() {
                    Log.d(LOG_TAG, "MediaSessionCompat.Callback onPrepare")
                    super.onPrepare()
                }

                override fun onFastForward() {
                    Log.d(LOG_TAG, "MediaSessionCompat.Callback onFastForward")
                }

                override fun onRemoveQueueItem(description: MediaDescriptionCompat?) {
                    Log.d(LOG_TAG, "MediaSessionCompat.Callback onRemoveQueueItem")
                }

                override fun onPrepareFromMediaId(mediaId: String?, extras: Bundle?) {
                    Log.d(LOG_TAG, "MediaSessionCompat.Callback onPrepareFromMediaId")
                }

                override fun onSetRepeatMode(repeatMode: Int) {
                    Log.d(LOG_TAG, "MediaSessionCompat.Callback onSetRepeatMode")
                }

                override fun onCommand(command: String?, extras: Bundle?, cb: ResultReceiver?) {
                    Log.d(LOG_TAG, "MediaSessionCompat.Callback onCommand")
                }

                override fun onPrepareFromSearch(query: String?, extras: Bundle?) {
                    Log.d(LOG_TAG, "MediaSessionCompat.Callback onPrepareFromSearch")
                }

                override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
                    Log.d(LOG_TAG, "MediaSessionCompat.Callback onPlayFromMediaId")
                }

                override fun onSetShuffleMode(shuffleMode: Int) {
                    Log.d(LOG_TAG, "MediaSessionCompat.Callback onSetShuffleMode")
                }

                override fun onPrepareFromUri(uri: Uri?, extras: Bundle?) {
                    Log.d(LOG_TAG, "MediaSessionCompat.Callback onPrepareFromUri")
                }

                override fun onPlayFromSearch(query: String?, extras: Bundle?) {
                    Log.d(LOG_TAG, "MediaSessionCompat.Callback onPlayFromSearch")
                }

                override fun onPlayFromUri(uri: Uri?, extras: Bundle?) {
                    Log.d(LOG_TAG, "MediaSessionCompat.Callback onPlayFromUri")
                }

                override fun onSetRating(rating: RatingCompat?) {
                    Log.d(LOG_TAG, "MediaSessionCompat.Callback onSetRating")
                }

                override fun onSetRating(rating: RatingCompat?, extras: Bundle?) {
                    Log.d(LOG_TAG, "MediaSessionCompat.Callback onSetRating")
                }

                override fun onSetCaptioningEnabled(enabled: Boolean) {
                    Log.d(LOG_TAG, "MediaSessionCompat.Callback onSetCaptioningEnabled")
                }

                override fun onRewind() {
                    Log.d(LOG_TAG, "MediaSessionCompat.Callback onRewind")
                }
            })
            // Set the session's token so that client activities can communicate with it.
            setSessionToken(sessionToken)
        }
    }

    private fun prepareSource() {
        val dataSourceFactory = DefaultDataSourceFactory(applicationContext, Util.getUserAgent(applicationContext, "ExoMusicPlayerDemoKt"), null)
        val concatenatingMediaSource = ConcatenatingMediaSource()
        mediaMetadata.forEach {
            concatenatingMediaSource.addMediaSource(ExtractorMediaSource.Factory(dataSourceFactory).createMediaSource(it.description.mediaUri))
        }
        exoPlayer.addListener(object : Player.EventListener {
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
            override fun onTimelineChanged(timeline: Timeline?, manifest: Any?, reason: Int) {
                Log.d(LOG_TAG, "EventListener onTimelineChanged")
            }
            override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
                Log.d(LOG_TAG, "EventListener onPlayerStateChanged: $playWhenReady $playbackState ")
            }
        })
        exoPlayer.prepare(concatenatingMediaSource)
    }

    private fun sweepSongs() {
        ResolveTask(object : DataListener {
        override fun dataGet(result: ArrayList<MediaMetadataCompat>) {
            this@MediaBrowserServiceImpl.mediaMetadata = result
        }
    }).execute(baseContext)}

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
