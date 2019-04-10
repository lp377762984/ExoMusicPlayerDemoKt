package com.example.exomusicplayerdemokt

import android.support.v4.media.MediaBrowserCompat
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.BaseViewHolder

class MusicAdapter(layoutResId: Int, data: MutableList<MediaBrowserCompat.MediaItem>?) :
    BaseQuickAdapter<MediaBrowserCompat.MediaItem, BaseViewHolder>(layoutResId, data) {
    override fun convert(helper: BaseViewHolder?, item: MediaBrowserCompat.MediaItem?) {
        helper?.setText(R.id.textView, item?.description?.title)
    }
}