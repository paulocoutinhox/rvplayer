package com.paulocoutinho.rvplayer.ui.recyclerviews

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import com.google.android.exoplayer2.SimpleExoPlayer
import com.paulocoutinho.rvplayer.Application
import com.paulocoutinho.rvplayer.BuildConfig
import com.paulocoutinho.rvplayer.R
import com.paulocoutinho.rvplayer.models.MediaObject
import com.paulocoutinho.rvplayer.ui.viewholders.VideoPlayerViewHolder

open class MyRecyclerView : RVPRecyclerView<MediaObject> {

    constructor(context: Context) : super(context) {
        // ignore
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        // ignore
    }

    override val videoPlayerComponentLayout: Int
        get() = R.layout.video_player

    override val videoPlayerDrawableVolumeOn: Int
        get() = R.drawable.ic_video_player_volume_on

    override val videoPlayerDrawableVolumeOff: Int
        get() = R.drawable.ic_video_player_volume_off

    override val videoPlayerDrawablePlay: Int
        get() = R.drawable.ic_video_player_play

    override val videoPlayerDrawablePause: Int
        get() = R.drawable.ic_video_player_pause

    override fun getVideoPlayer(): SimpleExoPlayer? {
        return Application.instance.videoPlayer
    }

    override fun isDebug(): Boolean {
        return BuildConfig.DEBUG
    }

    override fun getMediaURL(item: MediaObject): String {
        return item.mediaUrl ?: ""
    }

    override fun hasMediaAutoPlay(item: MediaObject): Boolean {
        return item.autoPlay ?: false
    }

    override fun isVideoPlayerViewHolder(viewHolder: ViewHolder?): Boolean {
        return (viewHolder is VideoPlayerViewHolder)
    }

    override fun getViewHolderThumbnail(viewHolder: ViewHolder?): ImageView? {
        return (viewHolder as? VideoPlayerViewHolder)?.videoPlayerThumbnail
    }

    override fun getViewHolderProgressBar(viewHolder: ViewHolder?): ProgressBar? {
        return (viewHolder as? VideoPlayerViewHolder)?.videoPlayerProgressBar
    }

    override fun getViewHolderVideoPlayerPlayButton(viewHolder: ViewHolder?): ImageView? {
        return (viewHolder as? VideoPlayerViewHolder)?.videoPlayerPlayButton
    }

    override fun getViewHolderVideoPlayerRestartButton(viewHolder: ViewHolder?): ImageView? {
        return (viewHolder as? VideoPlayerViewHolder)?.videoPlayerRestartButton
    }

    override fun getViewHolderVideoPlayerVolumeButton(viewHolder: ViewHolder?): ImageView? {
        return (viewHolder as? VideoPlayerViewHolder)?.videoPlayerVolumeButton
    }

    override fun getViewHolderVideoPlayerMediaContainer(viewHolder: ViewHolder?): ViewGroup? {
        return (viewHolder as? VideoPlayerViewHolder)?.videoPlayerMediaContainer
    }

    override fun getViewHolderVideoPlayerControlsBackground(viewHolder: ViewHolder?): View? {
        return (viewHolder as? VideoPlayerViewHolder)?.videoPlayerControlsBackground
    }
}
