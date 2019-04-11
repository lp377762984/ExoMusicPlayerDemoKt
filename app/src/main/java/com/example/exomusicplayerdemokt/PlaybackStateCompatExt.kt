package com.example.exomusicplayerdemokt

import android.support.v4.media.session.PlaybackStateCompat

inline val PlaybackStateCompat.isPrepared
    get() = (state == PlaybackStateCompat.STATE_BUFFERING) || (state == PlaybackStateCompat.STATE_PLAYING)
            || (state == PlaybackStateCompat.STATE_SKIPPING_TO_NEXT) || (state == PlaybackStateCompat.STATE_PAUSED)
            || (state == PlaybackStateCompat.STATE_STOPPED) || (state == PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS)
            || (state == PlaybackStateCompat.STATE_SKIPPING_TO_QUEUE_ITEM)
