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
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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

@SuppressLint("PrivateResource")
@Suppress("unused")
open class RVPRecyclerView : RecyclerView {

    enum class VolumeState {
        ON, OFF, AUTO
    }

    enum class AutoPlayState {
        ON, OFF
    }

    enum class PlayingState {
        PLAY, PAUSE
    }

    enum class PlayingOptionsState {
        ON, OFF
    }

    open val videoPlayerComponentLayout: Int = R.layout.rvp_video_player
    open val videoPlayerDrawableVolumeOn: Int = R.drawable.ic_video_player_volume_on
    open val videoPlayerDrawableVolumeOff: Int = R.drawable.ic_video_player_volume_off
    open val videoPlayerDrawablePlay: Int = R.drawable.ic_video_player_play
    open val videoPlayerDrawablePause: Int = R.drawable.ic_video_player_pause

    open var initialVolumeState: VolumeState = VolumeState.AUTO
    open var autoPlayState: AutoPlayState = AutoPlayState.ON

    open var videoSearchRange = 60.0

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

    private var mediaEnded = false
    private var mediaLoaded = false

    private var playingState = PlayingState.PLAY
    private var playingOptionsState = PlayingOptionsState.OFF

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

    private val videoPlayerEventListener = object : Player.EventListener {
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
    }

    private val videoPlayerScrollListener = object : OnScrollListener() {
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
    }

    constructor(context: Context) : super(context) {
        // ignore
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        // ignore
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

        mediaEnded = false
        mediaLoaded = false
    }

    private fun videoPlayerToggleVolumeControl() {
        logDebug("[$className : videoPlayerToggleVolumeControl]")

        val volumeState = getVolumeStateByVolumeValue()

        if (videoPlayer != null) {
            if (volumeState == VolumeState.ON) {
                logDebug("[$className : toggleVolume] Disabling volume")
                onVideoPlayerChangeVolume(VolumeState.OFF)
            } else if (volumeState == VolumeState.OFF) {
                logDebug("[$className : toggleVolume] Enabling volume")
                onVideoPlayerChangeVolume(VolumeState.ON)
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

        // set state
        playingState = PlayingState.PLAY

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

        mediaEnded = false
        mediaLoaded = true

        onVideoPlayerChangeVolume(getVolumeStateByVolumeValue())
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

        videoPlayerVolumeControl.setOnClickListener(null)
        videoPlayerRestart.setOnClickListener(null)

        videoPlayerThumbnail.setOnClickListener {
            smoothScrollToPosition(position)
        }

        videoControlsBackground.setOnClickListener {
            smoothScrollToPosition(position)
        }

        videoPlayerPlay.setOnClickListener {
            smoothScrollToPosition(position)
        }

        videoPlayerThumbnail.visibility = VISIBLE
        videoPlayerMediaContainer.visibility = GONE
        videoPlayerVolumeControl.visibility = GONE
        videoPlayerProgressBar.visibility = GONE
        videoControlsBackground.visibility = VISIBLE
        videoPlayerPlay.visibility = VISIBLE
        videoPlayerRestart.visibility = GONE

        videoPlayerPlay.tag = 2
        videoPlayerPlay.setImageDrawable(ContextCompat.getDrawable(context, videoPlayerDrawablePlay))
    }

    private fun logDebug(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(className, message)
        }
    }

    private fun getVolumeStateByVolumeValue(): VolumeState {
        logDebug("[$className : getVolumeStateByVolumeValue]")

        val volume = (videoPlayer?.volume ?: 0f)

        return if (volume == 1f) {
            VolumeState.ON
        } else {
            VolumeState.OFF
        }
    }

    open fun videoPlayerInitializeSurfaceView() {
        logDebug("[$className : videoPlayerInitializeSurfaceView]")
        onVideoPlayerInitializeSurfaceView()
    }

    open fun setListObjects(objects: ArrayList<MediaObject>) {
        logDebug("[$className : setListObjects]")
        listObjects = objects
    }

    open fun videoPlayerRelease() {
        logDebug("[$className : videoPlayerRelease]")
        onVideoPlayerRelease()
    }

    open fun videoPlayerStop() {
        logDebug("[$className : videoPlayerStop]")
        onVideoPlayerStop()
    }

    open fun videoPlayerPause() {
        logDebug("[$className : videoPlayerPause]")
        onVideoPlayerPause()
    }

    open fun videoPlayerPlay() {
        logDebug("[$className : videoPlayerPlay]")
        onVideoPlayerPlay()
    }

    open fun videoPlayerRestart() {
        logDebug("[$className : videoPlayerRestart]")
        onVideoPlayerRestart()
    }

    open fun videoPlayerSetVolumeState(state: VolumeState) {
        logDebug("[$className : videoPlayerSetVolumeState]")

        when (state) {
            VolumeState.ON -> {
                onVideoPlayerChangeVolume(VolumeState.ON)
            }
            VolumeState.OFF -> {
                onVideoPlayerChangeVolume(VolumeState.OFF)
            }
            VolumeState.AUTO -> {
                onVideoPlayerChangeVolume(getVolumeStateByVolumeValue())
            }
        }
    }

    open fun videoPlayerCheckInitialVolumeState() {
        logDebug("[$className : videoPlayerCheckInitialVolumeState]")

        when (initialVolumeState) {
            VolumeState.ON -> {
                onVideoPlayerChangeVolume(VolumeState.ON)
            }
            VolumeState.OFF -> {
                onVideoPlayerChangeVolume(VolumeState.OFF)
            }
            VolumeState.AUTO -> {
                onVideoPlayerChangeVolume(getVolumeStateByVolumeValue())
            }
        }
    }

    open fun videoPlayerPlayFirstAvailable(force: Boolean) {
        logDebug("[$className : videoPlayerPlayFirstAvailable]")

        val lm = (layoutManager as LinearLayoutManager)

        val firstPosition = lm.findFirstVisibleItemPosition()
        val lastPosition = lm.findLastVisibleItemPosition()

        val globalVisibleRect = Rect()
        getGlobalVisibleRect(globalVisibleRect)

        var videoFound = false

        for (pos in firstPosition..lastPosition) {
            val viewHolder = findViewHolderForAdapterPosition(pos)

            (viewHolder as? VideoPlayerViewHolder)?.let { vh ->
                val percentage = getVisibleHeightPercentage(vh.itemView)

                if (percentage >= videoSearchRange || force) {
                    if (videoFound) {
                        logDebug("[$className : playFirstVideo] Video already found (position: $pos, percentage: $percentage)")
                        onVideoPlayerReset(vh)
                        onVideoPlayerAttachViewHolder(pos)
                    } else {
                        logDebug("[$className : playFirstVideo] First video found (position: $pos, percentage: $percentage)")
                        videoFound = true
                        onVideoPlayerPlayFirstAvailable(pos)
                    }
                } else {
                    logDebug("[$className : playFirstVideo] Out of range (position: $pos, percentage: $percentage)")
                    onVideoPlayerReset(vh)
                    onVideoPlayerAttachViewHolder(pos)
                }
            }
        }
    }

    open fun videoPlayerSystemStart() {
        logDebug("[$className : videoPlayerSystemStart]")
        onVideoPlayerSystemStart()
    }

    open fun videoPlayerSystemStop() {
        logDebug("[$className : videoPlayerSystemStop]")
        onVideoPlayerSystemStop()
    }

    open fun videoPlayerSystemRestart() {
        logDebug("[$className : videoPlayerSystemRestart]")
        onVideoPlayerSystemRestart()
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
        mediaEnded = true
        onVideoPlayerSetUiStateEnded()
    }

    open fun onVideoPlayerStateIsError(error: ExoPlaybackException) {
        logDebug("[$className : onVideoPlayerStateIsError]")
        resetVideoView(true)
        onVideoPlayerSetUiStateError()
    }

    open fun onViewHolderClick() {
        logDebug("[$className : onViewHolderClick]")
        onVideoPlayerSetUiStateCheckPlayingOptions()
    }

    open fun onVideoPlayerSurfaceViewClick() {
        logDebug("[$className : onVideoPlayerSurfaceViewClick]")
        onVideoPlayerSetUiStateCheckPlayingOptions()
    }

    open fun onVideoPlayerThumbnailClick() {
        logDebug("[$className : onVideoPlayerThumbnailClick]")
        // ignore because it has overlay
    }

    open fun onVideoPlayerVolumeControlClick() {
        logDebug("[$className : onVideoPlayerVolumeControlClick]")
        videoPlayerToggleVolumeControl()
    }

    open fun onVideoPlayerPlayClick() {
        logDebug("[$className : onVideoPlayerPlayClick]")

        if (playingState == PlayingState.PLAY) {
            onVideoPlayerPause()
        } else if (playingState == PlayingState.PAUSE) {
            onVideoPlayerPlay()
        }
    }

    open fun onVideoPlayerRestartClick() {
        logDebug("[$className : onVideoPlayerRestartClick]")
        videoPlayerRestart()
    }

    open fun onVideoPlayerStop() {
        logDebug("[$className : onVideoPlayerStop]")
        videoPlayer?.stop()
    }

    open fun onVideoPlayerPause() {
        logDebug("[$className : onVideoPlayerPause]")

        videoPlayer?.playWhenReady = false
        playingState = PlayingState.PAUSE

        onVideoPlayerSetUiStatePlayingOptions()
    }

    open fun onVideoPlayerPlay() {
        logDebug("[$className : onVideoPlayerPlay]")

        when {
            mediaEnded -> {
                onVideoPlayerSetUiStatePlaying()

                videoPlayer?.seekToDefaultPosition()
                videoPlayer?.play()
                videoPlayer?.playWhenReady = true

                playingState = PlayingState.PLAY
            }
            mediaLoaded -> {
                onVideoPlayerSetUiStatePlaying()

                videoPlayer?.play()
                videoPlayer?.playWhenReady = true

                playingState = PlayingState.PLAY
            }
            else -> {
                videoPlayerPlayFirstAvailable(true)
            }
        }
    }

    open fun onVideoPlayerRestart() {
        logDebug("[$className : onVideoPlayerRestart]")

        when {
            mediaEnded -> {
                onVideoPlayerSetUiStatePlaying()

                videoPlayer?.seekToDefaultPosition()
                videoPlayer?.play()
                videoPlayer?.playWhenReady = true

                playingState = PlayingState.PLAY
            }
            mediaLoaded -> {
                onVideoPlayerSetUiStatePlaying()

                videoPlayer?.seekToDefaultPosition()
                videoPlayer?.play()
                videoPlayer?.playWhenReady = true

                playingState = PlayingState.PLAY
            }
            else -> {
                videoPlayerPlayFirstAvailable(true)
            }
        }
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

        videoPlayerVolumeControl?.alpha = 1f

        /*
        TODO: UNCOMMENT IF FLICK PROBLEM WAS SOLVED AND REMOVE LINE UP
        videoPlayerVolumeControl?.animate()?.cancel()
        videoPlayerVolumeControl?.alpha = 1f

        videoPlayerVolumeControl
                ?.animate()
                ?.alpha(0f)
                ?.setDuration(600)
                ?.startDelay = 1000
         */
    }

    open fun onVideoPlayerBuildMediaSource(item: MediaObject): MediaSource {
        logDebug("[$className : onVideoPlayerBuildMediaSource]")

        val dataSourceFactory = DefaultDataSourceFactory(context)
        val mediaItem = MediaItem.fromUri(item.mediaUrl ?: "")

        return ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
    }

    open fun onVideoPlayerChangeVolumeControlImage() {
        logDebug("[$className : onVideoPlayerChangeVolumeControlImage]")

        val imageTag = videoPlayerVolumeControl?.tag ?: -1
        val volumeState = getVolumeStateByVolumeValue()

        if (volumeState == VolumeState.ON) {
            if (imageTag != 1) {
                videoPlayerVolumeControl?.tag = 1
                videoPlayerVolumeControl?.setImageDrawable(ContextCompat.getDrawable(context, videoPlayerDrawableVolumeOn))
            }
        } else if (volumeState == VolumeState.OFF) {
            if (imageTag != 2) {
                videoPlayerVolumeControl?.tag = 2
                videoPlayerVolumeControl?.setImageDrawable(ContextCompat.getDrawable(context, videoPlayerDrawableVolumeOff))
            }
        }
    }

    open fun onVideoPlayerChangePlayingImage() {
        logDebug("[$className : onVideoPlayerChangePlayingImage]")

        val imageTag = videoPlayerPlay?.tag ?: -1

        if (playingState == PlayingState.PLAY) {
            if (imageTag != 1) {
                videoPlayerPlay?.tag = 1
                videoPlayerPlay?.setImageDrawable(ContextCompat.getDrawable(context, videoPlayerDrawablePause))
            }
        } else if (playingState == PlayingState.PAUSE) {
            if (imageTag != 2) {
                videoPlayerPlay?.tag = 2
                videoPlayerPlay?.setImageDrawable(ContextCompat.getDrawable(context, videoPlayerDrawablePlay))
            }
        }
    }

    open fun onVideoPlayerChangeVolume(state: VolumeState) {
        logDebug("[$className : onVideoPlayerChangeVolume]")

        if (state == VolumeState.ON) {
            videoPlayer?.volume = 1f
            onVideoPlayerChangeVolumeControlImage()
            onVideoPlayerAnimateVolumeControl()
        } else if (state == VolumeState.OFF) {
            videoPlayer?.volume = 0f
            onVideoPlayerChangeVolumeControlImage()
            onVideoPlayerAnimateVolumeControl()
        }
    }

    open fun onVideoPlayerSetUiStateDefault() {
        logDebug("[$className : onVideoPlayerSetUiStateDefault]")

        onVideoPlayerChangePlayingImage()

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

        onVideoPlayerChangePlayingImage()

        playingOptionsState = PlayingOptionsState.OFF

        videoPlayerThumbnail?.visibility = GONE
        videoPlayerMediaContainer?.visibility = VISIBLE
        videoPlayerVolumeControl?.visibility = GONE
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

        onVideoPlayerChangePlayingImage()

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

        onVideoPlayerChangePlayingImage()

        videoPlayerThumbnail?.visibility = VISIBLE
        videoPlayerMediaContainer?.visibility = GONE
        videoPlayerVolumeControl?.visibility = GONE
        videoPlayerProgressBar?.visibility = GONE
        videoControlsBackground?.visibility = VISIBLE
        videoPlayerPlay?.visibility = VISIBLE
        videoPlayerRestart?.visibility = VISIBLE
    }

    open fun onVideoPlayerSetUiStateStopped() {
        logDebug("[$className : onVideoPlayerSetUiStateStopped]")

        onVideoPlayerChangePlayingImage()

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

    open fun onVideoPlayerSetUiStatePlayingOptions() {
        logDebug("[$className : onVideoPlayerSetUiStatePlayingOptions]")

        onVideoPlayerChangePlayingImage()

        playingOptionsState = PlayingOptionsState.ON

        videoPlayerProgressBar?.visibility = GONE
        videoPlayerThumbnail?.visibility = GONE
        videoPlayerMediaContainer?.visibility = VISIBLE
        videoPlayerVolumeControl?.visibility = VISIBLE
        videoPlayerPlay?.visibility = VISIBLE
        videoPlayerRestart?.visibility = VISIBLE
        videoControlsBackground?.visibility = VISIBLE
    }

    open fun onVideoPlayerSetUiStateCheckPlayingOptions() {
        logDebug("[$className : onVideoPlayerSetUiStateCheckPlayingOptions]")

        if (playingOptionsState == PlayingOptionsState.ON) {
            onVideoPlayerSetUiStatePlaying()
        } else if (playingOptionsState == PlayingOptionsState.OFF) {
            onVideoPlayerSetUiStatePlayingOptions()
        }
    }

    open fun onVideoPlayerSystemStart() {
        logDebug("[$className : onVideoPlayerSystemStart]")

        if (Application.instance.videoPlayer == null) {
            throw Exception("[$className : onVideoPlayerSystemStart] You need initialize VideoPlayer first")
        }

        videoPlayer = Application.instance.videoPlayer

        videoPlayerInitializeSurfaceView()
        videoPlayerCheckInitialVolumeState()

        removeOnScrollListener(videoPlayerScrollListener)
        addOnScrollListener(videoPlayerScrollListener)

        videoPlayer?.removeListener(videoPlayerEventListener)
        videoPlayer?.addListener(videoPlayerEventListener)
    }

    open fun onVideoPlayerSystemStop() {
        logDebug("[$className : onVideoPlayerSystemStop]")

        resetVideoView(false)
        onVideoPlayerSetUiStateStopped()

        videoPlayerSurfaceView?.player = null
        videoPlayer = null
    }

    open fun onVideoPlayerSystemRestart() {
        logDebug("[$className : onVideoPlayerSystemRestart]")

        // player surface
        onVideoPlayerInitializeSurfaceView()

        // volume
        videoPlayerCheckInitialVolumeState()
    }
}
