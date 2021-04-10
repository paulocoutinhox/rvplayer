package com.paulocoutinho.rvplayer.ui.recyclerviews

import android.annotation.SuppressLint
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

@Suppress("unused")
open class RVPRecyclerView : RecyclerView {

    enum class VolumeState {
        ON, OFF
    }

    open val videoPlayerComponentLayout: Int = R.layout.rvp_video_player
    open val videoPlayerDrawableVolumeOn: Int = R.drawable.ic_video_player_volume_on
    open val videoPlayerDrawableVolumeOff: Int = R.drawable.ic_video_player_volume_off

    private val className = javaClass.simpleName

    private var videoPlayerThumbnail: ImageView? = null
    private var videoPlayerVolumeControl: ImageView? = null
    private var videoPlayerProgressBar: ProgressBar? = null
    private var viewHolderParent: View? = null
    private var videoPlayerMediaContainer: FrameLayout? = null
    private var videoControlsBackground: FrameLayout? = null

    private var videoPlayerSurfaceView: PlayerView? = null
    private var videoPlayer: SimpleExoPlayer? = null

    private var listObjects: ArrayList<MediaObject> = ArrayList()

    private var playPosition = -1
    private var isVideoViewAdded = false
    private var firstScroll = true

    private var volumeState: VolumeState? = null

    private val viewHolderClickListener = OnClickListener {
        Logger.d("[$className : ViewHolderClickListener]")
        onViewHolderClick()
    }

    private val videoPlayerSurfaceViewClickListener = OnClickListener {
        Logger.d("[$className : VideoPlayerSurfaceViewClickListener]")
        onVideoPlayerSurfaceViewClick()
    }

    private val videoPlayerThumbnailClickListener = OnClickListener {
        Logger.d("[$className : VideoPlayerThumbnailClickListener]")
        onVideoPlayerThumbnailClick()
    }

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    private fun init() {
        Logger.d("[$className : init]")

        if (Application.instance.videoPlayer == null) {
            throw Exception("[$className : init] You need initialize VideoPlayer first")
        }

        videoPlayer = Application.instance.videoPlayer

        onVideoPlayerInitializeSurfaceView()
        onVideoPlayerChangeVolumeControl(VolumeState.ON)

        addOnScrollListener(object : OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)

                if (newState == SCROLL_STATE_IDLE) {
                    Logger.d("[$className : onScrollStateChanged] New state: $newState")
                    videoPlayerPlayFirstAvailable(false)
                }
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                Logger.d("[$className : onScrolled]")

                if (firstScroll) {
                    Logger.d("[$className : onScrolled] First scroll")

                    firstScroll = false
                    videoPlayerPlayFirstAvailable(false)
                } else {
                    Logger.d("[$className : onScrolled] Ignored")
                }
            }
        })

        videoPlayer?.addListener(object : Player.EventListener {
            override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
                Logger.d("[$className : onPlayerStateChanged] State: $playbackState, PlayWhenReady: $playWhenReady")

                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        Logger.d("[$className : onPlayerStateChanged] Buffering video")
                        onVideoPlayerStateIsBuffering()
                    }

                    Player.STATE_ENDED -> {
                        Logger.d("[$className : onPlayerStateChanged] Video ended")
                        onVideoPlayerStateIsEnded()
                    }

                    Player.STATE_IDLE -> {
                        onVideoPlayerStateIsIdle()
                    }

                    Player.STATE_READY -> {
                        Logger.d("[$className : onPlayerStateChanged] Ready to play")
                        onVideoPlayerStateIsReady()
                    }

                    else -> {
                        // ignore
                    }
                }
            }

            override fun onPlayerError(error: ExoPlaybackException) {
                super.onPlayerError(error)
                Logger.d("[$className : onPlayerError] Error: ${error.message}")
                onVideoPlayerStateIsError(error)
            }
        })
    }

    private fun removeVideoView(videoView: PlayerView?) {
        Logger.d("[$className : removeVideoView] Checking parent...")

        val parent = videoView?.parent as? ViewGroup ?: return

        Logger.d("[$className : removeVideoView] Parent is OK")

        val index = parent.indexOfChild(videoView)

        if (index >= 0) {
            parent.removeViewAt(index)

            videoPlayer?.stop()

            isVideoViewAdded = false
            viewHolderParent?.setOnClickListener(null)

            Logger.d("[$className : removeVideoView] Removed")
        } else {
            Logger.d("[$className : removeVideoView] Not removed")
        }
    }

    private fun addVideoView() {
        Logger.d("[$className : addVideoView]")

        if (videoPlayerSurfaceView == null) {
            Logger.d("[$className : addVideoView] Cannot add video view because VideoPlayerSurfaceView is null")
            return
        }

        videoPlayerMediaContainer?.addView(videoPlayerSurfaceView)

        isVideoViewAdded = true

        videoPlayerSurfaceView?.requestFocus()
        videoPlayerSurfaceView?.visibility = VISIBLE
        videoPlayerSurfaceView?.alpha = 1f

        videoPlayerThumbnail?.visibility = GONE
        videoPlayerProgressBar?.visibility = GONE
    }

    private fun resetVideoView(force: Boolean) {
        Logger.d("[$className : resetVideoView]")

        if (isVideoViewAdded || force) {
            removeVideoView(videoPlayerSurfaceView)

            playPosition = -1

            videoPlayerSurfaceView?.visibility = INVISIBLE
            videoPlayerThumbnail?.visibility = VISIBLE
            videoPlayerProgressBar?.visibility = GONE
        }
    }

    private fun toggleVolume() {
        Logger.d("[$className : toggleVolume]")

        if (videoPlayer != null) {
            if (volumeState == VolumeState.ON) {
                Logger.d("[$className : toggleVolume] Disabling volume")
                onVideoPlayerChangeVolumeControl(VolumeState.OFF)
            } else if (volumeState == VolumeState.OFF) {
                Logger.d("[$className : toggleVolume] Enabling volume")
                onVideoPlayerChangeVolumeControl(VolumeState.ON)
            }
        }
    }

    private fun getVisibleHeightPercentage(view: View): Double {
        Logger.d("[$className : getVisibleHeightPercentage]")

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
        Logger.d("[$className : playVideo] Position: $position")

        // video is already playing so return
        if (position == playPosition) {
            return
        }

        // set the position of the list item that is to be played
        playPosition = position

        if (videoPlayerSurfaceView == null) {
            return
        }

        // remove any old surface views from previously playing videos
        videoPlayerSurfaceView?.visibility = INVISIBLE
        removeVideoView(videoPlayerSurfaceView)

        val lm = (layoutManager as LinearLayoutManager)
        val currentPosition = playPosition - lm.findFirstVisibleItemPosition()
        val child = getChildAt(currentPosition) ?: return

        val holder = child.tag as? VideoPlayerViewHolder

        if (holder == null) {
            Logger.d("[$className : playVideo] Holder is not video player")
            playPosition = -1
            return
        }

        Logger.d("[$className : playVideo] Holder is video player")

        videoPlayerThumbnail = holder.videoPlayerThumbnail

        videoPlayerProgressBar = holder.videoPlayerProgressBar
        videoPlayerVolumeControl = holder.videoPlayerVolumeControl
        videoPlayerMediaContainer = holder.videoPlayerMediaContainer
        viewHolderParent = holder.itemView

        videoPlayerThumbnail?.setOnClickListener(videoPlayerThumbnailClickListener)
        viewHolderParent?.setOnClickListener(viewHolderClickListener)

        videoPlayerSurfaceView?.player = videoPlayer
        videoPlayerSurfaceView?.setOnClickListener(videoPlayerSurfaceViewClickListener)

        val mediaSource = onVideoPlayerBuildMediaSource(listObjects[playPosition])

        videoPlayer?.setMediaSource(mediaSource)
        videoPlayer?.prepare()
        videoPlayer?.repeatMode = Player.REPEAT_MODE_OFF
        videoPlayer?.playWhenReady = true
    }

    open fun videoPlayerInitializeSurfaceView() {
        Logger.d("[$className : videoPlayerInitializeSurfaceView]")
        onVideoPlayerInitializeSurfaceView()
    }

    fun setListObjects(objects: ArrayList<MediaObject>) {
        Logger.d("[$className : setListObjects]")
        listObjects = objects
    }

    fun videoPlayerRelease() {
        Logger.d("[$className : videoPlayerRelease]")
        onVideoPlayerRelease()
    }

    fun videoPlayerStop() {
        Logger.d("[$className : videoPlayerStop]")
        onVideoPlayerStop()
    }

    fun videoPlayerPause() {
        Logger.d("[$className : videoPlayerPause]")
        onVideoPlayerPause()
    }

    fun videoPlayerPlay() {
        Logger.d("[$className : videoPlayerPlay]")
        onVideoPlayerPlay()
    }

    fun videoPlayerRestart() {
        Logger.d("[$className : videoPlayerRestart]")
        onVideoPlayerRestart()
    }

    fun videoPlayerStopAndReset() {
        Logger.d("[$className : videoPlayerStopAndReset]")
        onVideoPlayerStopAndReset()
    }

    fun videoPlayerPlayFirstAvailable(force: Boolean) {
        Logger.d("[$className : videoPlayerPlayFirstAvailable]")

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

                Logger.d("[$className : playFirstVideo] Position: $pos, Percentage: $percentage")

                if (percentage > 60.0 || force) {
                    if (first100percent) {
                        Logger.d("[$className : playFirstVideo] Option: First 100%")
                        onVideoPlayerReset(vh)
                    } else {
                        Logger.d("[$className : playFirstVideo] Option: Not first 100%")
                        first100percent = true
                        onVideoPlayerPlayFirstAvailable(pos)
                    }
                } else {
                    Logger.d("[$className : playFirstVideo] Option: Nothing")
                    onVideoPlayerReset(vh)
                }
            }
        }
    }

    @SuppressLint("InflateParams")
    open fun onVideoPlayerInitializeSurfaceView() {
        Logger.d("[$className : onVideoPlayerInitializeSurfaceView]")

        if (videoPlayerSurfaceView == null) {
            videoPlayerSurfaceView = LayoutInflater.from(context).inflate(
                videoPlayerComponentLayout,
                null,
                false,
            ) as PlayerView
        }

        videoPlayer = Application.instance.videoPlayer
    }

    open fun onVideoPlayerStateIsIdle() {
        Logger.d("[$className : onVideoPlayerStateIsIdle]")
    }

    open fun onVideoPlayerStateIsReady() {
        Logger.d("[$className : onVideoPlayerStateIsReady]")

        if (!isVideoViewAdded) {
            addVideoView()
        }

        videoPlayerProgressBar?.visibility = GONE
        videoPlayerThumbnail?.visibility = GONE
    }

    open fun onVideoPlayerStateIsBuffering() {
        Logger.d("[$className : onVideoPlayerStateIsBuffering]")

        videoPlayerProgressBar?.visibility = VISIBLE
        videoPlayerThumbnail?.visibility = GONE
    }

    open fun onVideoPlayerStateIsEnded() {
        Logger.d("[$className : onVideoPlayerStateIsEnded]")
        resetVideoView(true)
    }

    open fun onVideoPlayerStateIsError(error: ExoPlaybackException) {
        Logger.d("[$className : onVideoPlayerStateIsError]")
        resetVideoView(true)
    }

    open fun onViewHolderClick() {
        Logger.d("[$className : onViewHolderClick]")
        toggleVolume()
    }

    open fun onVideoPlayerSurfaceViewClick() {
        Logger.d("[$className : onVideoPlayerSurfaceViewClick]")
        toggleVolume()
    }

    open fun onVideoPlayerThumbnailClick() {
        Logger.d("[$className : onVideoPlayerThumbnailClick]")
        toggleVolume()
    }

    open fun onVideoPlayerStopAndReset() {
        Logger.d("[$className : onVideoPlayerStopAndReset]")

        resetVideoView(false)

        videoPlayerSurfaceView?.player = null
        videoPlayer = null
    }

    open fun onVideoPlayerStop() {
        Logger.d("[$className : onVideoPlayerStop]")
        videoPlayer?.stop()
    }

    open fun onVideoPlayerPause() {
        Logger.d("[$className : onVideoPlayerPause]")
        videoPlayer?.playWhenReady = false
    }

    open fun onVideoPlayerPlay() {
        Logger.d("[$className : onVideoPlayerPlay]")
        videoPlayer?.playWhenReady = true
    }

    open fun onVideoPlayerRestart() {
        Logger.d("[$className : onVideoPlayerRestart]")
        videoPlayer?.seekToDefaultPosition()
    }

    open fun onVideoPlayerRelease() {
        Logger.d("[$className : onVideoPlayerRelease]")

        if (videoPlayer != null) {
            videoPlayer?.release()
            videoPlayer = null
        }

        viewHolderParent = null
    }

    open fun onVideoPlayerReset(vh: ViewHolder) {
        Logger.d("[$className : onVideoPlayerReset]")

        if (viewHolderParent != null && viewHolderParent == vh.itemView) {
            resetVideoView(false)
        }
    }

    open fun onVideoPlayerPlayFirstAvailable(pos: Int) {
        Logger.d("[$className : onVideoPlayerPlayFirstAvailable]")
        playVideo(pos)
    }

    open fun onVideoPlayerAnimateVolumeControl() {
        Logger.d("[$className : onVideoPlayerAnimateVolumeControl]")

        if (videoPlayerVolumeControl != null) {
            if (volumeState == VolumeState.ON) {
                videoPlayerVolumeControl?.load(videoPlayerDrawableVolumeOn)
            } else if (volumeState == VolumeState.OFF) {
                videoPlayerVolumeControl?.load(videoPlayerDrawableVolumeOff)
            }

            videoPlayerVolumeControl?.alpha = 1f

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

    open fun onVideoPlayerChangeVolumeControl(state: VolumeState) {
        Logger.d("[$className : onVideoPlayerChangeVolumeControl]")

        volumeState = state

        if (state == VolumeState.ON) {
            videoPlayer?.volume = 1f
            onVideoPlayerAnimateVolumeControl()
        } else if (state == VolumeState.OFF) {
            videoPlayer?.volume = 0f
            onVideoPlayerAnimateVolumeControl()
        }
    }

    open fun onVideoPlayerBuildMediaSource(item: MediaObject): MediaSource {
        Logger.d("[$className : onVideoPlayerBuildMediaSource]")

        val dataSourceFactory = DefaultDataSourceFactory(context)
        val mediaItem = MediaItem.fromUri(item.mediaUrl ?: "")

        return ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
    }
}
