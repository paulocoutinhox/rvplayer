package com.paulocoutinho.rvplayer.ui.recyclerviews

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.exoplayer2.ExoPlaybackException
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.paulocoutinho.rvplayer.Application
import com.paulocoutinho.rvplayer.R
import com.paulocoutinho.rvplayer.models.MediaObject
import com.paulocoutinho.rvplayer.ui.viewholders.VideoPlayerViewHolder
import com.paulocoutinho.rvplayer.util.Logger


open class RVPRecyclerView : RecyclerView {

    enum class VolumeState {
        ON, OFF
    }

    private var thumbnail: ImageView? = null
    private var volumeControl: ImageView? = null
    private var progressBar: ProgressBar? = null
    private var viewHolderParent: View? = null
    private var frameLayout: FrameLayout? = null
    private var videoSurfaceView: PlayerView? = null
    private var videoPlayer: SimpleExoPlayer? = null

    private var listObjects: ArrayList<MediaObject> = ArrayList()

    private var playPosition = -1
    private var isVideoViewAdded = false

    private var volumeState: VolumeState? = null

    private var firstScroll = true

    private val viewHolderClickListener = OnClickListener {
        Logger.d("[RVPRecyclerView : ViewHolderClickListener]")
        onViewHolderClick()
    }

    private val videoSurfaceClickListener = OnClickListener {
        Logger.d("[RVPRecyclerView : VideoSurfaceClickListener]")
        onVideoSurfaceClick()
    }

    private val thumbnailClickListener = OnClickListener {
        Logger.d("[RVPRecyclerView : ThumbnailClickListener]")
        onThumbnailClick()
    }

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    private fun init() {
        Logger.d("[RVPRecyclerView : init]")

        if (Application.instance.videoPlayer == null) {
            throw Exception("You need initialize VideoPlayer first")
        }

        videoPlayer = Application.instance.videoPlayer

        onInitializeVideoSurfaceView()
        onChangeVolumeControl(VolumeState.ON)

        addOnScrollListener(object : OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)

                if (newState == SCROLL_STATE_IDLE) {
                    Logger.d("[RVPRecyclerView : onScrollStateChanged] New state: $newState")
                    playFirstAvailable(false)
                }
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                Logger.d("[RVPRecyclerView : onScrolled]")

                if (firstScroll) {
                    Logger.d("[RVPRecyclerView : onScrolled] First scroll")

                    firstScroll = false
                    playFirstAvailable(false)
                } else {
                    Logger.d("[RVPRecyclerView : onScrolled] Ignored")
                }
            }
        })

        videoPlayer?.addListener(object : Player.EventListener {
            override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
                Logger.d("[RVPRecyclerView : onPlayerStateChanged] State: $playbackState, PlayWhenReady: $playWhenReady")

                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        Logger.d("[RVPRecyclerView : onPlayerStateChanged] Buffering video")
                        onPlayerStateIsBuffering()
                    }

                    Player.STATE_ENDED -> {
                        Logger.d("[RVPRecyclerView : onPlayerStateChanged] Video ended")
                        onPlayerStateIsEnded()
                    }

                    Player.STATE_IDLE -> {
                        onPlayerStateIsIdle()
                    }

                    Player.STATE_READY -> {
                        Logger.d("[RVPRecyclerView : onPlayerStateChanged] Ready to play")
                        onPlayerStateIsReady()
                    }

                    else -> {
                        // ignore
                    }
                }
            }

            override fun onPlayerError(error: ExoPlaybackException) {
                super.onPlayerError(error)
                Logger.d("[RVPRecyclerView : onPlayerError] Error: ${error.message}")
                onPlayerStateIsError(error)
            }
        })
    }

    private fun removeVideoView(videoView: PlayerView?) {
        Logger.d("[RVPRecyclerView : removeVideoView] Checking parent...")

        val parent = videoView?.parent as? ViewGroup ?: return

        Logger.d("[RVPRecyclerView : removeVideoView] Parent is OK")

        val index = parent.indexOfChild(videoView)

        if (index >= 0) {
            parent.removeViewAt(index)

            videoPlayer?.stop()

            isVideoViewAdded = false
            viewHolderParent?.setOnClickListener(null)

            Logger.d("[RVPRecyclerView : removeVideoView] Removed")
        } else {
            Logger.d("[RVPRecyclerView : removeVideoView] Not removed")
        }
    }

    private fun addVideoView() {
        Logger.d("[RVPRecyclerView : addVideoView]")

        if (videoSurfaceView == null) {
            Logger.d("[RVPRecyclerView : addVideoView] Cannot add video view because VideoSurfaceView is null")
            return
        }

        frameLayout?.addView(videoSurfaceView)

        isVideoViewAdded = true

        videoSurfaceView?.requestFocus()
        videoSurfaceView?.visibility = VISIBLE
        videoSurfaceView?.alpha = 1f

        thumbnail?.visibility = GONE
        progressBar?.visibility = GONE
    }

    private fun resetVideoView(force: Boolean) {
        Logger.d("[RVPRecyclerView : resetVideoView]")

        if (isVideoViewAdded || force) {
            removeVideoView(videoSurfaceView)

            playPosition = -1

            videoSurfaceView?.visibility = INVISIBLE
            thumbnail?.visibility = VISIBLE
            progressBar?.visibility = GONE
        }
    }

    private fun toggleVolume() {
        Logger.d("[RVPRecyclerView : toggleVolume]")

        if (videoPlayer != null) {
            if (volumeState == VolumeState.OFF) {
                Logger.d("[RVPRecyclerView : toggleVolume] Enabling volume")
                onChangeVolumeControl(VolumeState.ON)
            } else if (volumeState == VolumeState.ON) {
                Logger.d("[RVPRecyclerView : toggleVolume] Disabling volume")
                onChangeVolumeControl(VolumeState.OFF)
            }
        }
    }

    private fun getVisibleHeightPercentage(view: View): Double {
        Logger.d("[RVPRecyclerView : getVisibleHeightPercentage]")

        val itemRect = Rect()
        val isParentViewEmpty = view.getLocalVisibleRect(itemRect)

        val visibleHeight = itemRect.height().toDouble()
        val height = view.measuredHeight

        val viewVisibleHeightPercentage = (visibleHeight / height) * 100

        return if (isParentViewEmpty) {
            viewVisibleHeightPercentage
        } else {
            0.0
        }
    }

    private fun playVideo(position: Int) {
        Logger.d("[RVPRecyclerView : playVideo] Position: $position")

        // video is already playing so return
        if (position == playPosition) {
            return
        }

        // set the position of the list-item that is to be played
        playPosition = position

        if (videoSurfaceView == null) {
            return
        }

        // remove any old surface views from previously playing videos
        videoSurfaceView?.visibility = INVISIBLE
        removeVideoView(videoSurfaceView)

        val lm = (layoutManager as LinearLayoutManager)
        val currentPosition = playPosition - lm.findFirstVisibleItemPosition()
        val child = getChildAt(currentPosition) ?: return

        val holder = child.tag as? VideoPlayerViewHolder

        if (holder == null) {
            Logger.d("[RVPRecyclerView : playVideo] Holder is not video player")
            playPosition = -1
            return
        }

        Logger.d("[RVPRecyclerView : playVideo] Holder is video player")

        thumbnail = holder.thumbnail

        progressBar = holder.progressBar
        volumeControl = holder.volumeControl
        frameLayout = holder.mediaContainer
        viewHolderParent = holder.itemView

        thumbnail?.setOnClickListener(thumbnailClickListener)
        viewHolderParent?.setOnClickListener(viewHolderClickListener)

        videoSurfaceView?.player = videoPlayer
        videoSurfaceView?.setOnClickListener(videoSurfaceClickListener)

        val mediaSource = onBuildMediaSource(listObjects[playPosition])

        videoPlayer?.setMediaSource(mediaSource)
        videoPlayer?.prepare()
        videoPlayer?.repeatMode = Player.REPEAT_MODE_OFF
        videoPlayer?.playWhenReady = true
    }

    fun releasePlayer() {
        Logger.d("[RVPRecyclerView : releasePlayer]")
        onPlayerRelease()
    }

    fun stopAndResetPlayer() {
        Logger.d("[RVPRecyclerView : stopAndResetPlayer]")
        onPlayerStopAndReset()
    }

    fun setListObjects(objects: ArrayList<MediaObject>) {
        Logger.d("[RVPRecyclerView : setListObjects]")
        listObjects = objects
    }

    fun playFirstAvailable(force: Boolean) {
        Logger.d("[RVPRecyclerView : playFirstAvailable]")

        val lm = (layoutManager as LinearLayoutManager)

        val firstPosition = lm.findFirstVisibleItemPosition()
        val lastPosition = lm.findLastVisibleItemPosition()

        val globalVisibleRect = Rect()
        getGlobalVisibleRect(globalVisibleRect)

        var first100percent = false

        for (pos in firstPosition..lastPosition) {
            val viewHolder = findViewHolderForAdapterPosition(pos)

            (viewHolder as? VideoPlayerViewHolder)?.let { vh ->
                val percentage = getVisibleHeightPercentage(vh.itemView)

                Logger.d("[RVPRecyclerView : playFirstVideo] Position: $pos, Percentage: $percentage")

                if (percentage > 60.0 || force) {
                    if (first100percent) {
                        Logger.d("[RVPRecyclerView : playFirstVideo] Option: First 100%")
                        onPlayerReset(vh)
                    } else {
                        Logger.d("[RVPRecyclerView : playFirstVideo] Option: Not first 100%")
                        first100percent = true
                        onPlayFirstAvailable(pos)
                    }
                } else {
                    Logger.d("[RVPRecyclerView : playFirstVideo] Option: Nothing")
                    onPlayerReset(vh)
                }
            }
        }
    }

    open fun onInitializeVideoSurfaceView() {
        Logger.d("[RVPRecyclerView : onInitializeVideoSurfaceView]")

        if (videoSurfaceView == null) {
            videoSurfaceView = LayoutInflater.from(context).inflate(R.layout.rvp_video_player, null, false) as PlayerView
        }

        videoPlayer = Application.instance.videoPlayer
    }

    open fun onPlayerStateIsIdle() {
        Logger.d("[RVPRecyclerView : onPlayerStateIsIdle]")
    }

    open fun onPlayerStateIsReady() {
        Logger.d("[RVPRecyclerView : onPlayerStateIsReady]")

        if (!isVideoViewAdded) {
            addVideoView()
        }

        progressBar?.visibility = GONE
        thumbnail?.visibility = GONE
    }

    open fun onPlayerStateIsBuffering() {
        Logger.d("[RVPRecyclerView : onPlayerStateIsBuffering]")

        progressBar?.visibility = VISIBLE
        thumbnail?.visibility = GONE
    }

    open fun onPlayerStateIsEnded() {
        Logger.d("[RVPRecyclerView : onPlayerStateIsEnded]")
        resetVideoView(true)
    }

    open fun onPlayerStateIsError(error: ExoPlaybackException) {
        Logger.d("[RVPRecyclerView : onPlayerStateIsError]")
        resetVideoView(true)
    }

    open fun onViewHolderClick() {
        Logger.d("[RVPRecyclerView : onViewHolderClick]")
        toggleVolume()
    }

    open fun onVideoSurfaceClick() {
        Logger.d("[RVPRecyclerView : onVideoSurfaceViewClick]")
        toggleVolume()
    }

    open fun onThumbnailClick() {
        Logger.d("[RVPRecyclerView : onThumbnailClick]")
        toggleVolume()
    }

    open fun onPlayerStopAndReset() {
        Logger.d("[RVPRecyclerView : onPlayerStopAndReset]")

        resetVideoView(false)
        videoSurfaceView?.player = null
        videoPlayer = null
    }

    open fun onPlayerRelease() {
        Logger.d("[RVPRecyclerView : onPlayerRelease]")

        if (videoPlayer != null) {
            videoPlayer?.release()
            videoPlayer = null
        }

        viewHolderParent = null
    }

    open fun onPlayerReset(vh: ViewHolder) {
        Logger.d("[RVPRecyclerView : onPlayerReset]")

        if (viewHolderParent != null && viewHolderParent == vh.itemView) {
            resetVideoView(false)
        }
    }

    open fun onPlayFirstAvailable(pos: Int) {
        Logger.d("[RVPRecyclerView : onPlayFirstAvailable]")
        playVideo(pos)
    }

    open fun onAnimateVolumeControl() {
        Logger.d("[RVPRecyclerView : onAnimateVolumeControl]")

        if (volumeControl != null) {
            if (volumeState == VolumeState.OFF) {
                volumeControl?.load(R.drawable.ic_volume_off)
            } else if (volumeState == VolumeState.ON) {
                volumeControl?.load(R.drawable.ic_volume_on)
            }

            volumeControl?.alpha = 1f

            /*
            // TODO: Animation in breaking the surface view
            volumeControl?.animate()?.cancel()
            volumeControl?.alpha = 1f

            volumeControl
                    ?.animate()
                    ?.alpha(0f)
                    ?.setDuration(600)
                    ?.startDelay = 1000
             */
        }
    }

    open fun onChangeVolumeControl(state: VolumeState) {
        Logger.d("[RVPRecyclerView : onChangeVolumeControl]")

        volumeState = state

        if (state == VolumeState.ON) {
            videoPlayer?.volume = 1f
            onAnimateVolumeControl()
        } else if (state == VolumeState.OFF) {
            videoPlayer?.volume = 0f
            onAnimateVolumeControl()
        }
    }

    open fun onBuildMediaSource(item: MediaObject): MediaSource {
        Logger.d("[RVPRecyclerView : onBuildMediaSource]")

        val dataSourceFactory = DefaultDataSourceFactory(context)
        val mediaItem = MediaItem.fromUri(item.mediaUrl ?: "")

        return ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
    }

}
