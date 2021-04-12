package com.paulocoutinho.rvplayer.ui.recyclerviews

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.util.Log
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
import com.paulocoutinho.rvplayer.BuildConfig
import com.paulocoutinho.rvplayer.R
import com.paulocoutinho.rvplayer.models.MediaObject
import com.paulocoutinho.rvplayer.ui.viewholders.VideoPlayerViewHolder

@Suppress("unused")
open class RVPRecyclerView : RecyclerView {

    enum class VolumeState {
        ON, OFF
    }

    enum class AutoPlayState {
        ON, OFF
    }

    open val videoPlayerComponentLayout: Int = R.layout.rvp_video_player
    open val videoPlayerDrawableVolumeOn: Int = R.drawable.ic_video_player_volume_on
    open val videoPlayerDrawableVolumeOff: Int = R.drawable.ic_video_player_volume_off

    open var volumeState: VolumeState = VolumeState.OFF
        set(value) {
            field = value
            onVideoPlayerChangeVolume(field)
        }

    open var autoPlayState: AutoPlayState = AutoPlayState.ON

    private val className = javaClass.simpleName

    private var videoPlayerThumbnail: ImageView? = null
    private var videoPlayerPlay: ImageView? = null
    private var videoPlayerRestart: ImageView? = null
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
    private var volumeStateChanged = false

    private val viewHolderClickListener = OnClickListener {
        logDebug("[$className : ViewHolderClickListener]")
        onViewHolderClick()
    }

    private val videoPlayerSurfaceViewClickListener = OnClickListener {
        logDebug("[$className : VideoPlayerSurfaceViewClickListener]")
        onVideoPlayerSurfaceViewClick()
    }

    private val videoPlayerThumbnailClickListener = OnClickListener {
        logDebug("[$className : VideoPlayerThumbnailClickListener]")
        onVideoPlayerThumbnailClick()
    }

    private val videoPlayerVolumeControlClickListener = OnClickListener {
        logDebug("[$className : videoPlayerVolumeControlClickListener]")
        onVideoPlayerVolumeControlClick()
    }

    private val videoPlayerPlayClickListener = OnClickListener {
        logDebug("[$className : videoPlayerPlayClickListener]")
        onVideoPlayerPlayClick()
    }

    private val videoPlayerRestartClickListener = OnClickListener {
        logDebug("[$className : videoPlayerRestartClickListener]")
        onVideoPlayerRestartClick()
    }

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    private fun init() {
        logDebug("[$className : init]")

        if (Application.instance.videoPlayer == null) {
            throw Exception("[$className : init] You need initialize VideoPlayer first")
        }

        videoPlayer = Application.instance.videoPlayer

        onVideoPlayerInitializeSurfaceView()
        onVideoPlayerChangeVolumeControl(volumeState)

        addOnScrollListener(object : OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)

                if (newState == SCROLL_STATE_IDLE) {
                    logDebug("[$className : onScrollStateChanged] New state: $newState")
                    videoPlayerPlayFirstAvailable(false)
                }
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                logDebug("[$className : onScrolled]")

                if (firstScroll) {
                    logDebug("[$className : onScrolled] First scroll")

                    firstScroll = false

                    // auto play
                    if (autoPlayState == AutoPlayState.ON) {
                        videoPlayerPlayFirstAvailable(false)
                    }
                } else {
                    logDebug("[$className : onScrolled] Ignored")
                }
            }
        })

        addOnChildAttachStateChangeListener(object : OnChildAttachStateChangeListener {
            override fun onChildViewAttachedToWindow(view: View) {
                logDebug("[$className : onChildViewAttachedToWindow]")
                // ignore
            }

            override fun onChildViewDetachedFromWindow(view: View) {
                logDebug("[$className : onChildViewDetachedFromWindow]")

                if (viewHolderParent != null && viewHolderParent == view) {
                    // resetVideoView(false)
                }
            }
        })

        videoPlayer?.addListener(object : Player.EventListener {
            override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
                logDebug("[$className : onPlayerStateChanged] State: $playbackState, PlayWhenReady: $playWhenReady")

                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        logDebug("[$className : onPlayerStateChanged] Buffering video")
                        onVideoPlayerStateIsBuffering()
                    }

                    Player.STATE_ENDED -> {
                        logDebug("[$className : onPlayerStateChanged] Video ended")
                        onVideoPlayerStateIsEnded()
                    }

                    Player.STATE_IDLE -> {
                        onVideoPlayerStateIsIdle()
                    }

                    Player.STATE_READY -> {
                        logDebug("[$className : onPlayerStateChanged] Ready to play")
                        onVideoPlayerStateIsReady()
                    }

                    else -> {
                        // ignore
                    }
                }
            }

            override fun onPlayerError(error: ExoPlaybackException) {
                super.onPlayerError(error)
                logDebug("[$className : onPlayerError] Error: ${error.message}")
                onVideoPlayerStateIsError(error)
            }
        })
    }

    private fun removeVideoView(videoView: PlayerView?) {
        logDebug("[$className : removeVideoView] Checking parent...")

        val parent = videoView?.parent as? ViewGroup ?: return

        logDebug("[$className : removeVideoView] Parent is OK")

        val index = parent.indexOfChild(videoView)

        if (index >= 0) {
            parent.removeViewAt(index)

            videoPlayer?.stop()

            isVideoViewAdded = false
            viewHolderParent?.setOnClickListener(null)

            logDebug("[$className : removeVideoView] Removed")
        } else {
            logDebug("[$className : removeVideoView] Not removed")
        }
    }

    private fun addVideoView() {
        logDebug("[$className : addVideoView]")

        if (videoPlayerSurfaceView == null) {
            logDebug("[$className : addVideoView] Cannot add video view because VideoPlayerSurfaceView is null")
            return
        }

        videoPlayerMediaContainer?.addView(videoPlayerSurfaceView)

        isVideoViewAdded = true

        videoPlayerSurfaceView?.requestFocus()
        videoPlayerSurfaceView?.visibility = VISIBLE
        videoPlayerSurfaceView?.alpha = 1f

        onVideoPlayerSetUiStateAdded()
    }

    private fun resetVideoView(force: Boolean) {
        logDebug("[$className : resetVideoView]")

        // only if video was added before
        if (isVideoViewAdded || force) {
            removeVideoView(videoPlayerSurfaceView)
            playPosition = -1
        }
    }

    private fun videoPlayerToggleVolumeControl() {
        logDebug("[$className : videoPlayerToggleVolumeControl]")

        if (videoPlayer != null) {
            if (volumeState == VolumeState.ON) {
                logDebug("[$className : toggleVolume] Disabling volume")
                onVideoPlayerChangeVolumeControl(VolumeState.OFF)
            } else if (volumeState == VolumeState.OFF) {
                logDebug("[$className : toggleVolume] Enabling volume")
                onVideoPlayerChangeVolumeControl(VolumeState.ON)
            }
        }
    }

    private fun getVisibleHeightPercentage(view: View): Double {
        logDebug("[$className : getVisibleHeightPercentage]")

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
        logDebug("[$className : playVideo] Position: $position")

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
            logDebug("[$className : playVideo] Holder is not video player")
            playPosition = -1
            return
        }

        logDebug("[$className : playVideo] Holder is video player")

        videoPlayerThumbnail = holder.videoPlayerThumbnail
        videoPlayerProgressBar = holder.videoPlayerProgressBar
        videoPlayerPlay = holder.videoPlayerPlay
        videoPlayerRestart = holder.videoPlayerRestart
        videoPlayerVolumeControl = holder.videoPlayerVolumeControl
        videoPlayerMediaContainer = holder.videoPlayerMediaContainer
        videoControlsBackground = holder.videoControlsBackground

        viewHolderParent = holder.itemView

        videoPlayerThumbnail?.setOnClickListener(videoPlayerThumbnailClickListener)
        videoPlayerVolumeControl?.setOnClickListener(videoPlayerVolumeControlClickListener)
        videoPlayerPlay?.setOnClickListener(videoPlayerPlayClickListener)
        videoPlayerRestart?.setOnClickListener(videoPlayerRestartClickListener)
        viewHolderParent?.setOnClickListener(viewHolderClickListener)

        if (videoPlayerSurfaceView?.player == null) {
            videoPlayerSurfaceView?.player = videoPlayer
        }

        videoPlayerSurfaceView?.setOnClickListener(videoPlayerSurfaceViewClickListener)

        val mediaSource = onVideoPlayerBuildMediaSource(listObjects[playPosition])

        videoPlayer?.setMediaSource(mediaSource)
        videoPlayer?.prepare()
        videoPlayer?.repeatMode = Player.REPEAT_MODE_OFF
        videoPlayer?.playWhenReady = true

        onVideoPlayerSetUiStatePlaying()
    }

    private fun attachVideoHolder(position: Int) {
        logDebug("[$className : attachVideoHolder] Position: $position")

        // video is already playing so return
        if (position == playPosition) {
            return
        }

        val lm = (layoutManager as LinearLayoutManager)
        val currentPosition = position - lm.findFirstVisibleItemPosition()
        val child = getChildAt(currentPosition) ?: return

        val holder = child.tag as? VideoPlayerViewHolder

        if (holder == null) {
            logDebug("[$className : attachVideoHolder] Holder is not video player")
            return
        }

        logDebug("[$className : attachVideoHolder] Holder is video player")

        val videoPlayerThumbnail = holder.videoPlayerThumbnail
        val videoPlayerProgressBar = holder.videoPlayerProgressBar
        val videoPlayerPlay = holder.videoPlayerPlay
        val videoPlayerRestart = holder.videoPlayerRestart
        val videoPlayerVolumeControl = holder.videoPlayerVolumeControl
        val videoPlayerMediaContainer = holder.videoPlayerMediaContainer
        val videoControlsBackground = holder.videoControlsBackground

        videoPlayerThumbnail.setOnClickListener(videoPlayerThumbnailClickListener)
        videoPlayerVolumeControl.setOnClickListener(videoPlayerVolumeControlClickListener)
        videoPlayerPlay.setOnClickListener(videoPlayerPlayClickListener)
        videoPlayerRestart.setOnClickListener(videoPlayerRestartClickListener)

        videoPlayerThumbnail.visibility = GONE
        videoPlayerMediaContainer.visibility = VISIBLE
        videoPlayerVolumeControl.visibility = VISIBLE
        videoPlayerProgressBar.visibility = GONE
        videoControlsBackground.visibility = GONE
        videoPlayerPlay.visibility = GONE
        videoPlayerRestart.visibility = GONE
    }

    private fun logDebug(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(className, message)
        }
    }

    open fun videoPlayerInitializeSurfaceView() {
        logDebug("[$className : videoPlayerInitializeSurfaceView]")
        onVideoPlayerInitializeSurfaceView()
    }

    fun setListObjects(objects: ArrayList<MediaObject>) {
        logDebug("[$className : setListObjects]")
        listObjects = objects
    }

    fun videoPlayerRelease() {
        logDebug("[$className : videoPlayerRelease]")
        onVideoPlayerRelease()
    }

    fun videoPlayerStop() {
        logDebug("[$className : videoPlayerStop]")
        onVideoPlayerStop()
    }

    fun videoPlayerPause() {
        logDebug("[$className : videoPlayerPause]")
        onVideoPlayerPause()
    }

    fun videoPlayerPlay() {
        logDebug("[$className : videoPlayerPlay]")
        onVideoPlayerPlay()
    }

    fun videoPlayerRestart() {
        logDebug("[$className : videoPlayerRestart]")
        onVideoPlayerRestart()
    }

    fun videoPlayerStopAndReset() {
        logDebug("[$className : videoPlayerStopAndReset]")
        onVideoPlayerStopAndReset()
    }

    fun videoPlayerSetVolumeState(state: VolumeState) {
        logDebug("[$className : videoPlayerSetVolumeState]")
        volumeState = state
        onVideoPlayerChangeVolumeControlImage()
    }

    fun videoPlayerPlayFirstAvailable(force: Boolean) {
        logDebug("[$className : videoPlayerPlayFirstAvailable]")

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

                logDebug("[$className : playFirstVideo] Position: $pos, Percentage: $percentage")

                if (percentage > 60.0 || force) {
                    if (first100percent) {
                        logDebug("[$className : playFirstVideo] Option: First 100%")
                        onVideoPlayerReset(vh)
                        onVideoPlayerAttachViewHolder(pos)
                    } else {
                        logDebug("[$className : playFirstVideo] Option: Not first 100%")
                        first100percent = true
                        onVideoPlayerPlayFirstAvailable(pos)
                    }
                } else {
                    logDebug("[$className : playFirstVideo] Option: Nothing")
                    onVideoPlayerReset(vh)
                    onVideoPlayerAttachViewHolder(pos)
                }
            }
        }
    }

    @SuppressLint("InflateParams")
    open fun onVideoPlayerInitializeSurfaceView() {
        logDebug("[$className : onVideoPlayerInitializeSurfaceView]")

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
        logDebug("[$className : onVideoPlayerStateIsIdle]")
    }

    open fun onVideoPlayerStateIsReady() {
        logDebug("[$className : onVideoPlayerStateIsReady]")

        if (!isVideoViewAdded) {
            addVideoView()
        }

        onVideoPlayerSetUiStateIsReady()
    }

    open fun onVideoPlayerStateIsBuffering() {
        logDebug("[$className : onVideoPlayerStateIsBuffering]")
        onVideoPlayerSetUiStateBuffering()
    }

    open fun onVideoPlayerStateIsEnded() {
        logDebug("[$className : onVideoPlayerStateIsEnded]")
        resetVideoView(true)
        onVideoPlayerSetUiStateEnded()
    }

    open fun onVideoPlayerStateIsError(error: ExoPlaybackException) {
        logDebug("[$className : onVideoPlayerStateIsError]")
        resetVideoView(true)
        onVideoPlayerSetUiStateError()
    }

    open fun onViewHolderClick() {
        logDebug("[$className : onViewHolderClick]")
        videoPlayerToggleVolumeControl()
    }

    open fun onVideoPlayerSurfaceViewClick() {
        logDebug("[$className : onVideoPlayerSurfaceViewClick]")
        videoPlayerToggleVolumeControl()
    }

    open fun onVideoPlayerThumbnailClick() {
        logDebug("[$className : onVideoPlayerThumbnailClick]")
        videoPlayerToggleVolumeControl()
    }

    open fun onVideoPlayerVolumeControlClick() {
        logDebug("[$className : onVideoPlayerVolumeControlClick]")
        videoPlayerToggleVolumeControl()
    }

    open fun onVideoPlayerPlayClick() {
        logDebug("[$className : onVideoPlayerPlayClick]")
        videoPlayerPlayFirstAvailable(false)
    }

    open fun onVideoPlayerRestartClick() {
        logDebug("[$className : onVideoPlayerRestartClick]")
        videoPlayerRestart()
    }

    open fun onVideoPlayerStopAndReset() {
        logDebug("[$className : onVideoPlayerStopAndReset]")

        resetVideoView(false)
        onVideoPlayerSetUiStateStopped()

        videoPlayerSurfaceView?.player = null
        videoPlayer = null
    }

    open fun onVideoPlayerStop() {
        logDebug("[$className : onVideoPlayerStop]")
        videoPlayer?.stop()
    }

    open fun onVideoPlayerPause() {
        logDebug("[$className : onVideoPlayerPause]")
        videoPlayer?.playWhenReady = false
    }

    open fun onVideoPlayerPlay() {
        logDebug("[$className : onVideoPlayerPlay]")
        videoPlayer?.playWhenReady = true
    }

    open fun onVideoPlayerRestart() {
        logDebug("[$className : onVideoPlayerRestart]")
        videoPlayer?.seekToDefaultPosition()
    }

    open fun onVideoPlayerRelease() {
        logDebug("[$className : onVideoPlayerRelease]")

        if (videoPlayer != null) {
            videoPlayer?.release()
            videoPlayer = null
        }

        viewHolderParent = null
    }

    open fun onVideoPlayerReset(vh: ViewHolder) {
        logDebug("[$className : onVideoPlayerReset]")

        if (viewHolderParent != null && viewHolderParent == vh.itemView) {
            resetVideoView(false)
        }
    }

    open fun onVideoPlayerPlayFirstAvailable(pos: Int) {
        logDebug("[$className : onVideoPlayerPlayFirstAvailable]")
        playVideo(pos)
    }

    open fun onVideoPlayerAttachViewHolder(pos: Int) {
        logDebug("[$className : onVideoPlayerAttachViewHolder]")
        attachVideoHolder(pos)
    }

    open fun onVideoPlayerAnimateVolumeControl() {
        logDebug("[$className : onVideoPlayerAnimateVolumeControl]")

        if (videoPlayerVolumeControl != null) {
            onVideoPlayerChangeVolumeControlImage()
        }
    }

    open fun onVideoPlayerChangeVolumeControl(state: VolumeState) {
        logDebug("[$className : onVideoPlayerChangeVolumeControl]")

        volumeState = state

        onVideoPlayerChangeVolume(volumeState)
    }

    open fun onVideoPlayerBuildMediaSource(item: MediaObject): MediaSource {
        logDebug("[$className : onVideoPlayerBuildMediaSource]")

        val dataSourceFactory = DefaultDataSourceFactory(context)
        val mediaItem = MediaItem.fromUri(item.mediaUrl ?: "")

        return ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
    }

    open fun onVideoPlayerChangeVolumeControlImage() {
        logDebug("[$className : onVideoPlayerChangeVolumeControlImage]")

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

    open fun onVideoPlayerChangeVolume(state: VolumeState) {
        logDebug("[$className : onVideoPlayerChangeVolume]")

        if (state == VolumeState.ON) {
            videoPlayer?.volume = 1f
            onVideoPlayerAnimateVolumeControl()
        } else if (state == VolumeState.OFF) {
            videoPlayer?.volume = 0f
            onVideoPlayerAnimateVolumeControl()
        }
    }

    open fun onVideoPlayerSetUiStateDefault() {
        logDebug("[$className : onVideoPlayerSetUiStateDefault]")

        videoPlayerThumbnail?.visibility = VISIBLE
        videoPlayerMediaContainer?.visibility = GONE
        videoPlayerVolumeControl?.visibility = GONE
        videoPlayerProgressBar?.visibility = GONE
        videoControlsBackground?.visibility = VISIBLE
        videoPlayerPlay?.visibility = VISIBLE
        videoPlayerRestart?.visibility = GONE
    }

    open fun onVideoPlayerSetUiStatePlaying() {
        logDebug("[$className : onVideoPlayerSetUiStatePlaying]")

        onVideoPlayerChangeVolumeControlImage()

        videoPlayerThumbnail?.visibility = GONE
        videoPlayerMediaContainer?.visibility = VISIBLE
        videoPlayerVolumeControl?.visibility = VISIBLE
        videoPlayerProgressBar?.visibility = GONE
        videoControlsBackground?.visibility = GONE
        videoPlayerPlay?.visibility = GONE
        videoPlayerRestart?.visibility = GONE
    }

    open fun onVideoPlayerSetUiStateBuffering() {
        logDebug("[$className : onVideoPlayerSetUiStateBuffering]")

        videoPlayerProgressBar?.visibility = VISIBLE
    }

    open fun onVideoPlayerSetUiStateIsReady() {
        logDebug("[$className : onVideoPlayerSetUiStateIsReady]")

        videoPlayerProgressBar?.visibility = GONE
        videoPlayerThumbnail?.visibility = GONE
        videoPlayerMediaContainer?.visibility = VISIBLE
        videoPlayerPlay?.visibility = GONE
        videoPlayerRestart?.visibility = GONE
    }

    open fun onVideoPlayerSetUiStateError() {
        logDebug("[$className : onVideoPlayerSetUiStateError]")

        videoPlayerThumbnail?.visibility = VISIBLE
        videoPlayerMediaContainer?.visibility = GONE
        videoPlayerVolumeControl?.visibility = GONE
        videoPlayerProgressBar?.visibility = GONE
        videoControlsBackground?.visibility = VISIBLE
        videoPlayerPlay?.visibility = VISIBLE
        videoPlayerRestart?.visibility = GONE
    }

    open fun onVideoPlayerSetUiStateEnded() {
        logDebug("[$className : onVideoPlayerSetUiStateEnded]")

        videoPlayerThumbnail?.visibility = VISIBLE
        videoPlayerMediaContainer?.visibility = GONE
        videoPlayerVolumeControl?.visibility = GONE
        videoPlayerProgressBar?.visibility = GONE
        videoControlsBackground?.visibility = VISIBLE
        videoPlayerPlay?.visibility = VISIBLE
        videoPlayerRestart?.visibility = GONE
    }

    open fun onVideoPlayerSetUiStateStopped() {
        logDebug("[$className : onVideoPlayerSetUiStateStopped]")

        videoPlayerThumbnail?.visibility = VISIBLE
        videoPlayerMediaContainer?.visibility = GONE
        videoPlayerVolumeControl?.visibility = GONE
        videoPlayerProgressBar?.visibility = GONE
        videoControlsBackground?.visibility = VISIBLE
        videoPlayerPlay?.visibility = VISIBLE
        videoPlayerRestart?.visibility = GONE
    }

    open fun onVideoPlayerSetUiStateAdded() {
        logDebug("[$className : onVideoPlayerSetUiStateAdded]")

        videoPlayerProgressBar?.visibility = GONE
        videoPlayerThumbnail?.visibility = GONE
        videoPlayerMediaContainer?.visibility = VISIBLE
        videoPlayerPlay?.visibility = GONE
        videoPlayerRestart?.visibility = GONE
    }
}
