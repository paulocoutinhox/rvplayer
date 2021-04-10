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


open class MyRecyclerView : RecyclerView {

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
        Logger.d("[MyRecyclerView : ViewHolderClickListener]")
        onViewHolderClick()
    }

    private val videoSurfaceClickListener = OnClickListener {
        Logger.d("[MyRecyclerView : VideoSurfaceClickListener]")
        onVideoSurfaceClick()
    }

    private val thumbnailClickListener = OnClickListener {
        Logger.d("[MyRecyclerView : ThumbnailClickListener]")
        onThumbnailClick()
    }

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    private fun init() {
        Logger.d("[MyRecyclerView : init]")

        if (Application.instance.videoPlayer == null) {
            throw Exception("You need initialize VideoPlayer first")
        }

        videoPlayer = Application.instance.videoPlayer

        initializeVideoSurfaceView()

        onChangeVolumeControl(VolumeState.ON)

        addOnScrollListener(object : OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)

                if (newState == SCROLL_STATE_IDLE) {
                    Logger.d("[MyRecyclerView : onScrollStateChanged] New state: $newState")
                    playFirstVideo(false)
                }
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                Logger.d("[MyRecyclerView : onScrolled]")

                if (firstScroll) {
                    Logger.d("[MyRecyclerView : onScrolled] First scroll")

                    firstScroll = false
                    playFirstVideo(false)
                } else {
                    Logger.d("[MyRecyclerView : onScrolled] Ignored")
                }
            }
        })

        videoPlayer?.addListener(object : Player.EventListener {
            override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
                Logger.d("[MyRecyclerView : onPlayerStateChanged] State: $playbackState, PlayWhenReady: $playWhenReady")

                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        Logger.d("[MyRecyclerView : onPlayerStateChanged] Buffering video")
                        onPlayerStateIsBuffering()
                    }

                    Player.STATE_ENDED -> {
                        Logger.d("[MyRecyclerView : onPlayerStateChanged] Video ended")
                        onPlayerStateIsEnded()
                    }

                    Player.STATE_IDLE -> {
                        onPlayerStateIsIdle()
                    }

                    Player.STATE_READY -> {
                        Logger.d("[MyRecyclerView : onPlayerStateChanged] Ready to play")
                        onPlayerStateIsReady()
                    }

                    else -> {
                        // ignore
                    }
                }
            }

            override fun onPlayerError(error: ExoPlaybackException) {
                super.onPlayerError(error)
                Logger.d("[MyRecyclerView : onPlayerError] Error: ${error.message}")
                onPlayerStateIsError(error)
            }
        })
    }

    private fun removeVideoView(videoView: PlayerView?) {
        Logger.d("[MyRecyclerView : removeVideoView] Checking parent...")

        val parent = videoView?.parent as? ViewGroup ?: return

        Logger.d("[MyRecyclerView : removeVideoView] Parent is OK")

        val index = parent.indexOfChild(videoView)

        if (index >= 0) {
            parent.removeViewAt(index)

            videoPlayer?.stop()

            isVideoViewAdded = false
            viewHolderParent?.setOnClickListener(null)

            Logger.d("[MyRecyclerView : removeVideoView] Removed")
        } else {
            Logger.d("[MyRecyclerView : removeVideoView] Not removed")
        }
    }

    private fun addVideoView() {
        Logger.d("[MyRecyclerView : addVideoView]")

        if (videoSurfaceView == null) {
            Logger.d("[MyRecyclerView : addVideoView] Cannot add video view because VideoSurfaceView is null")
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
        Logger.d("[MyRecyclerView : resetVideoView]")

        if (isVideoViewAdded || force) {
            removeVideoView(videoSurfaceView)

            playPosition = -1

            videoSurfaceView?.visibility = INVISIBLE
            thumbnail?.visibility = VISIBLE
            progressBar?.visibility = GONE
        }
    }

    private fun toggleVolume() {
        Logger.d("[MyRecyclerView : toggleVolume]")

        if (videoPlayer != null) {
            if (volumeState == VolumeState.OFF) {
                Logger.d("[MyRecyclerView : toggleVolume] Enabling volume")
                onChangeVolumeControl(VolumeState.ON)
            } else if (volumeState == VolumeState.ON) {
                Logger.d("[MyRecyclerView : toggleVolume] Disabling volume")
                onChangeVolumeControl(VolumeState.OFF)
            }
        }
    }

    private fun buildMediaSource(item: MediaObject): MediaSource {
        val dataSourceFactory = DefaultDataSourceFactory(context)
        val mediaItem = MediaItem.fromUri(item.mediaUrl ?: "")

        return ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
    }

    private fun getVisibleHeightPercentage(view: View): Double {
        Logger.d("[MyRecyclerView : getVisibleHeightPercentage]")

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

    fun playVideo(position: Int) {
        Logger.d("[MyRecyclerView : playVideo] Position: $position")

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
            Logger.d("[MyRecyclerView : playVideo] Holder is not video player")
            playPosition = -1
            return
        }

        Logger.d("[MyRecyclerView : playVideo] Holder is video player")

        thumbnail = holder.thumbnail

        progressBar = holder.progressBar
        volumeControl = holder.volumeControl
        frameLayout = holder.mediaContainer
        viewHolderParent = holder.itemView

        thumbnail?.setOnClickListener(thumbnailClickListener)
        viewHolderParent?.setOnClickListener(viewHolderClickListener)

        videoSurfaceView?.player = videoPlayer
        videoSurfaceView?.setOnClickListener(videoSurfaceClickListener)

        val mediaSource = buildMediaSource(listObjects[playPosition])

        videoPlayer?.setMediaSource(mediaSource)
        videoPlayer?.prepare()
        videoPlayer?.repeatMode = Player.REPEAT_MODE_OFF
        videoPlayer?.playWhenReady = true
    }

    fun releasePlayer() {
        Logger.d("[MyRecyclerView : releasePlayer]")
        onPlayerRelease()
    }

    fun stopAndResetPlayer() {
        Logger.d("[MyRecyclerView : stopAndResetPlayer]")
        onPlayerStopAndReset()
    }

    fun setListObjects(objects: ArrayList<MediaObject>) {
        Logger.d("[MyRecyclerView : setListObjects]")
        listObjects = objects
    }

    fun playFirstVideo(force: Boolean) {
        Logger.d("[MyRecyclerView : playFirstVideo]")

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

                Logger.d("[MyRecyclerView : playFirstVideo] Position: $pos, Percentage: $percentage")

                if (percentage > 60.0 || force) {
                    if (first100percent) {
                        Logger.d("[MyRecyclerView : playFirstVideo] Option: First 100%")
                        onPlayerReset(vh)
                    } else {
                        Logger.d("[MyRecyclerView : playFirstVideo] Option: Not first 100%")
                        first100percent = true
                        onPlayerPlayVideo(pos)
                    }
                } else {
                    Logger.d("[MyRecyclerView : playFirstVideo] Option: Nothing")
                    onPlayerReset(vh)
                }
            }
        }
    }

    fun initializeVideoSurfaceView() {
        Logger.d("[MyRecyclerView : initializeVideoSurfaceView]")

        if (videoSurfaceView == null) {
            videoSurfaceView = LayoutInflater.from(context).inflate(R.layout.my_video_player, null, false) as PlayerView
        }

        videoPlayer = Application.instance.videoPlayer
    }

    open fun onPlayerStateIsIdle() {
        Logger.d("[MyRecyclerView : onPlayerStateIsIdle]")
    }

    open fun onPlayerStateIsReady() {
        Logger.d("[MyRecyclerView : onPlayerStateIsReady]")

        if (!isVideoViewAdded) {
            addVideoView()
        }

        progressBar?.visibility = GONE
        thumbnail?.visibility = GONE
    }

    open fun onPlayerStateIsBuffering() {
        Logger.d("[MyRecyclerView : onPlayerStateIsBuffering]")

        progressBar?.visibility = VISIBLE
        thumbnail?.visibility = GONE
    }

    open fun onPlayerStateIsEnded() {
        Logger.d("[MyRecyclerView : onPlayerStateIsEnded]")
        resetVideoView(true)
    }

    open fun onPlayerStateIsError(error: ExoPlaybackException) {
        Logger.d("[MyRecyclerView : onPlayerStateIsError]")
        resetVideoView(true)
    }

    open fun onViewHolderClick() {
        Logger.d("[MyRecyclerView : onViewHolderClick]")
        toggleVolume()
    }

    open fun onVideoSurfaceClick() {
        Logger.d("[MyRecyclerView : onVideoSurfaceViewClick]")
        toggleVolume()
    }

    open fun onThumbnailClick() {
        Logger.d("[MyRecyclerView : onThumbnailClick]")
        toggleVolume()
    }

    open fun onPlayerStopAndReset() {
        Logger.d("[MyRecyclerView : onPlayerStopAndReset]")

        resetVideoView(false)
        videoSurfaceView?.player = null
        videoPlayer = null
    }

    open fun onPlayerRelease() {
        Logger.d("[MyRecyclerView : onPlayerRelease]")

        if (videoPlayer != null) {
            videoPlayer?.release()
            videoPlayer = null
        }

        viewHolderParent = null
    }

    open fun onPlayerReset(vh: ViewHolder) {
        Logger.d("[MyRecyclerView : onPlayerReset]")

        if (viewHolderParent != null && viewHolderParent == vh.itemView) {
            resetVideoView(false)
        }
    }

    open fun onPlayerPlayVideo(pos: Int) {
        Logger.d("[MyRecyclerView : onPlayerPlayVideo]")
        playVideo(pos)
    }

    open fun onAnimateVolumeControl() {
        Logger.d("[MyRecyclerView : onAnimateVolumeControl]")

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
        Logger.d("[MyRecyclerView : onChangeVolumeControl]")

        volumeState = state

        if (state == VolumeState.ON) {
            videoPlayer?.volume = 1f
            onAnimateVolumeControl()
        } else if (state == VolumeState.OFF) {
            videoPlayer?.volume = 0f
            onAnimateVolumeControl()
        }
    }

}
