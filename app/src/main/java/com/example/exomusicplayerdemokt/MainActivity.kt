package com.example.exomusicplayerdemokt

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.util.Log
import android.widget.CompoundButton
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {
    var TAG = "MainActivity"
    private lateinit var mediaBrowser: MediaBrowserCompat
    private lateinit var mediaController: MediaControllerCompat
    private lateinit var adapter: MusicAdapter
    private var position = 0;
    private var controllerCallback: MediaControllerCompat.Callback = object : MediaControllerCompat.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            Log.d(TAG, "onMetadataChanged")
            val playbackState = mediaController.playbackState
        }

        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            Log.d(TAG, "onPlaybackStateChanged-->${state?.state}")
            when (state?.state) {
                PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS,
                PlaybackStateCompat.STATE_SKIPPING_TO_NEXT,
                PlaybackStateCompat.STATE_SKIPPING_TO_QUEUE_ITEM,
                PlaybackStateCompat.STATE_PLAYING -> {
                    tvPlaying.text = "正在播放${adapter.getItem(position)?.description?.title}"
                    checkBox.isChecked = false
                }

                PlaybackStateCompat.STATE_STOPPED,
                PlaybackStateCompat.STATE_PAUSED -> {
                    tvPlaying.text = "暂停播放${adapter.getItem(position)?.description?.title}"
                    checkBox.isChecked = true
                }
            }
        }
    }
    private val connectionCallbacks = object : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {
            Log.d(TAG, "onConnected")
            // Get the token for the MediaSession
            mediaBrowser.sessionToken.also { token ->

                // Create a MediaControllerCompat
                val mediaController = MediaControllerCompat(
                    this@MainActivity, // Context
                    token
                )

                // Save the controller
                MediaControllerCompat.setMediaController(this@MainActivity, mediaController)
            }
            mediaBrowser.subscribe("media_root_id", object : MediaBrowserCompat.SubscriptionCallback() {
                override fun onChildrenLoaded(parentId: String, children: MutableList<MediaBrowserCompat.MediaItem>) {
                    super.onChildrenLoaded(parentId, children)
                    Log.d(TAG, "onChildrenLoaded")
                    adapter.setNewData(children)
                    tvPlaying.text = "暂停播放${adapter.getItem(position)?.description?.title}"
                }

                override fun onError(parentId: String) {
                    Log.d(TAG, "onError")
                    super.onError(parentId)

                }
            })
            // Finish building the UI
            buildTransportControls()
        }

        override fun onConnectionSuspended() {
            // The Service has crashed. Disable transport controls until it automatically reconnects
            Log.d(TAG, "onConnectionSuspended")
        }

        override fun onConnectionFailed() {
            // The Service has refused our connection
            Log.d(TAG, "onConnectionFailed")
        }
    }

    private fun buildTransportControls() {
        mediaController = MediaControllerCompat.getMediaController(this@MainActivity)
        // Grab the view for the play/pause button
        checkBox.setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                mediaController.transportControls.pause()
            } else {
                mediaController.transportControls.play()
            }
        })
        btn_previous.setOnClickListener {//1
            if (position>0){
                position--
                mediaController.transportControls.skipToPrevious()
            }else{
                Toast.makeText(this,"没有上一首了",Toast.LENGTH_SHORT).show()
            }
        }
        btn_next.setOnClickListener {//1 2
            if (position < adapter.itemCount - 1){
                position++
                mediaController.transportControls.skipToNext()
            }else{
                Toast.makeText(this,"没有下一首了",Toast.LENGTH_SHORT).show()
            }
        }
        // Display the initial state
        val metadata = mediaController.metadata
        val pbState = mediaController.playbackState
        // Register a Callback to stay in sync
        mediaController.registerCallback(controllerCallback)
        adapter = MusicAdapter(R.layout.music_item, null)
        adapter.setOnItemClickListener { adapter, view, position ->
            //checkBox.isChecked = !checkBox.isChecked
            this@MainActivity.position = position
            mediaController.transportControls.skipToQueueItem(position.toLong())
        }
        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }


    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            ) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 202)
            } else {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 202)
            }
        }
        mediaBrowser = MediaBrowserCompat(
            this,
            ComponentName(this, MediaBrowserServiceImpl::class.java),
            connectionCallbacks,
            null // optional Bundle
        )
    }

    public override fun onStart() {
        super.onStart()
        mediaBrowser.connect()
    }

    public override fun onResume() {
        super.onResume()
        volumeControlStream = AudioManager.STREAM_MUSIC
    }

    public override fun onStop() {
        super.onStop()
        // (see "stay in sync with the MediaSession")
        MediaControllerCompat.getMediaController(this)?.unregisterCallback(controllerCallback)
        mediaBrowser.unsubscribe("media_root_id")
        mediaBrowser.disconnect()
    }
}
