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
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.annotation.DrawableRes
import androidx.annotation.LayoutRes
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

@SuppressLint("PrivateResource")
@Suppress("unused")
open class RVPRecyclerView<T> : RecyclerView {

    enum class VolumeState {
        ON, OFF, AUTO
    }

    enum class AutoPlayState {
        ON, OFF
    }

    enum class PlayingState {
        PLAYING, PAUSED, ENDED
    }

    enum class PlayingOptionsState {
        ON, OFF
    }

    @LayoutRes
    open val videoPlayerComponentLayout: Int = 0

    @DrawableRes
    open val videoPlayerDrawableVolumeOn: Int = 0

    @DrawableRes
    open val videoPlayerDrawableVolumeOff: Int = 0

    @DrawableRes
    open val videoPlayerDrawablePlay: Int = 0

    @DrawableRes
    open val videoPlayerDrawablePause: Int = 0

    open var initialVolumeState: VolumeState = VolumeState.AUTO
    open var autoPlayFirstState: AutoPlayState = AutoPlayState.ON

    open var videoSearchRange = 60.0

    private val className = javaClass.simpleName

    private var videoPlayerThumbnail: ImageView? = null
    private var videoPlayerPlayButton: ImageView? = null
    private var videoPlayerRestartButton: ImageView? = null
    private var videoPlayerVolumeButton: ImageView? = null
    private var videoPlayerProgressBar: ProgressBar? = null
    private var viewHolderParent: View? = null
    private var videoPlayerMediaContainer: ViewGroup? = null
    private var videoPlayerControlsBackground: View? = null

    private var videoPlayerSurfaceView: PlayerView? = null
    private var videoPlayer: SimpleExoPlayer? = null

    private var objectList: ArrayList<T> = ArrayList()

    private var playPosition = -1
    private var isVideoViewAdded = false
    private var firstScroll = true
    private var volumeStateChanged = false

    private var mediaEnded = false
    private var mediaLoaded = false

    private var playingState = PlayingState.PLAYING
    private var playingOptionsState = PlayingOptionsState.OFF

    private var positionOfAutoPlayFirst = -1

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

    private val videoPlayerControlsBackgroundClickListener = OnClickListener {
        logDebug("[$className : VideoPlayerControlsBackgroundClickListener]")
        onVideoPlayerControlsBackgroundClick()
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

                if (viewHolderParent != null) {
                    val lm = (layoutManager as LinearLayoutManager)
                    val currentPosition = playPosition - lm.findFirstVisibleItemPosition()
                    val child = getChildAt(currentPosition)

                    if (child == null) {
                        // playing cell is active but not visible
                        logDebug("[$className : onScrollStateChanged] Active item is not visible")
                        videoPlayerStop()
                    }
                }
            }
        }

        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            super.onScrolled(recyclerView, dx, dy)

            if (firstScroll) {
                logDebug("[$className : onScrolled] First scroll")

                firstScroll = false

                videoPlayerPlayFirstAvailable(true)
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
        playingState = PlayingState.PLAYING

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

        var holder: ViewHolder? = child.tag as? ViewHolder

        if (!isVideoPlayerViewHolder(holder)) {
            holder = null
        }

        if (holder == null) {
            logDebug("[$className : playVideo] View holder is not video player")
            playPosition = -1
            return
        }

        logDebug("[$className : playVideo] View holder is video player")

        videoPlayerThumbnail = getViewHolderThumbnail(holder)
        videoPlayerProgressBar = getViewHolderProgressBar(holder)
        videoPlayerPlayButton = getViewHolderVideoPlayerPlayButton(holder)
        videoPlayerRestartButton = getViewHolderVideoPlayerRestartButton(holder)
        videoPlayerVolumeButton = getViewHolderVideoPlayerVolumeButton(holder)
        videoPlayerMediaContainer = getViewHolderVideoPlayerMediaContainer(holder)
        videoPlayerControlsBackground = getViewHolderVideoPlayerControlsBackground(holder)

        viewHolderParent = holder.itemView

        videoPlayerThumbnail?.setOnClickListener(videoPlayerThumbnailClickListener)
        videoPlayerVolumeButton?.setOnClickListener(videoPlayerVolumeControlClickListener)
        videoPlayerControlsBackground?.setOnClickListener(videoPlayerControlsBackgroundClickListener)
        videoPlayerPlayButton?.setOnClickListener(videoPlayerPlayClickListener)
        videoPlayerRestartButton?.setOnClickListener(videoPlayerRestartClickListener)
        viewHolderParent?.setOnClickListener(viewHolderClickListener)

        if (videoPlayerSurfaceView?.player == null) {
            videoPlayerSurfaceView?.player = videoPlayer
        }

        videoPlayerSurfaceView?.setOnClickListener(videoPlayerSurfaceViewClickListener)

        val mediaSource = onVideoPlayerBuildMediaSource(objectList[playPosition])

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

        val lm = (layoutManager as LinearLayoutManager)
        val currentPosition = position - lm.findFirstVisibleItemPosition()
        val child = getChildAt(currentPosition) ?: return

        var holder: ViewHolder? = child.tag as? ViewHolder

        if (!isVideoPlayerViewHolder(holder)) {
            holder = null
        }

        if (holder == null) {
            logDebug("[$className : attachVideoHolder] View holder is not video player")
            return
        }

        logDebug("[$className : attachVideoHolder] View holder is video player")

        val videoPlayerThumbnail = getViewHolderThumbnail(holder)
        val videoPlayerProgressBar = getViewHolderProgressBar(holder)
        val videoPlayerPlayButton = getViewHolderVideoPlayerPlayButton(holder)
        val videoPlayerRestartButton = getViewHolderVideoPlayerRestartButton(holder)
        val videoPlayerVolumeButton = getViewHolderVideoPlayerVolumeButton(holder)
        val videoPlayerMediaContainer = getViewHolderVideoPlayerMediaContainer(holder)
        val videoPlayerControlsBackground = getViewHolderVideoPlayerControlsBackground(holder)

        videoPlayerVolumeButton?.setOnClickListener(null)
        videoPlayerRestartButton?.setOnClickListener(null)

        videoPlayerThumbnail?.setOnClickListener {
            logDebug("[$className : VideoPlayerThumbnailClickListener]")
            smoothScrollToPosition(position)
        }

        videoPlayerControlsBackground?.setOnClickListener {
            logDebug("[$className : VideoPlayerControlsBackgroundClickListener]")
            smoothScrollToPosition(position)
        }

        videoPlayerPlayButton?.setOnClickListener {
            logDebug("[$className : VideoPlayerPlayButtonClickListener]")

            positionOfAutoPlayFirst = -2

            attachVideoHolder(playPosition)
            smoothScrollToPosition(position)
            onVideoPlayerPlayFirstAvailable(position)
        }

        videoPlayerThumbnail?.visibility = VISIBLE
        videoPlayerMediaContainer?.visibility = GONE
        videoPlayerVolumeButton?.visibility = GONE
        videoPlayerProgressBar?.visibility = GONE
        videoPlayerControlsBackground?.visibility = VISIBLE
        videoPlayerPlayButton?.visibility = VISIBLE
        videoPlayerRestartButton?.visibility = GONE

        videoPlayerPlayButton?.tag = 2
        videoPlayerPlayButton?.setImageDrawable(
            ContextCompat.getDrawable(
                context,
                videoPlayerDrawablePlay
            )
        )
    }

    private fun logDebug(message: String) {
        if (isDebug()) {
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

    open fun setObjectList(objects: ArrayList<T>) {
        logDebug("[$className : setObjectList]")
        objectList = objects
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

            viewHolder?.let { vh ->
                if (isVideoPlayerViewHolder(vh)) {
                    val percentage = getVisibleHeightPercentage(vh.itemView)

                    if (percentage >= videoSearchRange || force) {
                        if (videoFound) {
                            logDebug("[$className : playFirstVideo] Video already found (position: $pos, percentage: $percentage)")
                            onVideoPlayerReset(vh)
                            onVideoPlayerAttachViewHolder(pos)
                        } else {
                            logDebug("[$className : playFirstVideo] First video found (position: $pos, percentage: $percentage)")

                            if (autoPlayFirstState == AutoPlayState.ON) {
                                videoFound = true
                                onVideoPlayerPlayFirstAvailable(pos)
                            } else {
                                if (positionOfAutoPlayFirst == -1 || positionOfAutoPlayFirst == pos) {
                                    positionOfAutoPlayFirst = pos

                                    videoFound = true

                                    logDebug("[$className : playFirstVideo] First video found but ignored (position: $pos, percentage: $percentage)")

                                    onVideoPlayerReset(vh)
                                    onVideoPlayerAttachViewHolder(pos)
                                } else {
                                    videoFound = true
                                    onVideoPlayerPlayFirstAvailable(pos)
                                }
                            }
                        }
                    } else {
                        logDebug("[$className : playFirstVideo] Out of range (position: $pos, percentage: $percentage)")

                        if (autoPlayFirstState == AutoPlayState.OFF) {
                            if (positionOfAutoPlayFirst == pos) {
                                positionOfAutoPlayFirst = -2
                            }
                        }

                        onVideoPlayerReset(vh)
                        onVideoPlayerAttachViewHolder(pos)
                    }
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

        videoPlayer = getVideoPlayer()
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
        playingState = PlayingState.ENDED

        onVideoPlayerSetUiStateEnded()
    }

    open fun onVideoPlayerStateIsError(error: ExoPlaybackException) {
        logDebug("[$className : onVideoPlayerStateIsError]")

        resetVideoView(true)

        playingState = PlayingState.ENDED

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

    open fun onVideoPlayerControlsBackgroundClick() {
        logDebug("[$className : onVideoPlayerControlsBackgroundClick]")
        onVideoPlayerSetUiStateCheckPlayingOptions()
    }

    open fun onVideoPlayerVolumeControlClick() {
        logDebug("[$className : onVideoPlayerVolumeControlClick]")
        videoPlayerToggleVolumeControl()
    }

    open fun onVideoPlayerPlayClick() {
        logDebug("[$className : onVideoPlayerPlayClick]")

        when (playingState) {
            PlayingState.PLAYING -> {
                onVideoPlayerPause()
            }
            PlayingState.PAUSED, PlayingState.ENDED -> {
                onVideoPlayerPlay()
            }
        }
    }

    open fun onVideoPlayerRestartClick() {
        logDebug("[$className : onVideoPlayerRestartClick]")
        videoPlayerRestart()
    }

    open fun onVideoPlayerStop() {
        logDebug("[$className : onVideoPlayerStop]")

        videoPlayer?.seekToDefaultPosition()
        videoPlayer?.stop()

        playPosition = -1
        playingState = PlayingState.ENDED

        onVideoPlayerSetUiStateStopped()
    }

    open fun onVideoPlayerPause() {
        logDebug("[$className : onVideoPlayerPause]")

        videoPlayer?.playWhenReady = false
        playingState = PlayingState.PAUSED

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

                playingState = PlayingState.PLAYING
            }
            mediaLoaded -> {
                onVideoPlayerSetUiStatePlaying()

                videoPlayer?.play()
                videoPlayer?.playWhenReady = true

                playingState = PlayingState.PLAYING
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

                playingState = PlayingState.PLAYING
            }
            mediaLoaded -> {
                onVideoPlayerSetUiStatePlaying()

                videoPlayer?.seekToDefaultPosition()
                videoPlayer?.play()
                videoPlayer?.playWhenReady = true

                playingState = PlayingState.PLAYING
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

        videoPlayerVolumeButton?.alpha = 1f

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

    open fun onVideoPlayerBuildMediaSource(item: T): MediaSource {
        logDebug("[$className : onVideoPlayerBuildMediaSource]")

        val dataSourceFactory = DefaultDataSourceFactory(context)
        val mediaItem = MediaItem.fromUri(getMediaURL(item))

        return ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
    }

    open fun onVideoPlayerChangeVolumeControlImage() {
        logDebug("[$className : onVideoPlayerChangeVolumeControlImage]")

        val imageTag = videoPlayerVolumeButton?.tag ?: -1

        when (getVolumeStateByVolumeValue()) {
            VolumeState.ON -> {
                if (imageTag != 1) {
                    videoPlayerVolumeButton?.tag = 1
                    videoPlayerVolumeButton?.setImageDrawable(
                        ContextCompat.getDrawable(
                            context,
                            videoPlayerDrawableVolumeOn
                        )
                    )
                }
            }
            VolumeState.OFF -> {
                if (imageTag != 2) {
                    videoPlayerVolumeButton?.tag = 2
                    videoPlayerVolumeButton?.setImageDrawable(
                        ContextCompat.getDrawable(
                            context,
                            videoPlayerDrawableVolumeOff
                        )
                    )
                }
            }
            else -> {
                // ignore
            }
        }
    }

    open fun onVideoPlayerChangePlayingImage(forcePlayingState: PlayingState? = null) {
        logDebug("[$className : onVideoPlayerChangePlayingImage]")

        val imageTag = videoPlayerPlayButton?.tag ?: -1

        if (forcePlayingState != null) {
            if (forcePlayingState == PlayingState.PLAYING) {
                videoPlayerPlayButton?.tag = 1
                videoPlayerPlayButton?.setImageDrawable(
                    ContextCompat.getDrawable(
                        context,
                        videoPlayerDrawablePause
                    )
                )
            } else if (forcePlayingState == PlayingState.PAUSED || forcePlayingState == PlayingState.ENDED) {
                videoPlayerPlayButton?.tag = 2
                videoPlayerPlayButton?.setImageDrawable(
                    ContextCompat.getDrawable(
                        context,
                        videoPlayerDrawablePlay
                    )
                )
            }
        } else {
            if (playingState == PlayingState.PLAYING) {
                if (imageTag != 1) {
                    videoPlayerPlayButton?.tag = 1
                    videoPlayerPlayButton?.setImageDrawable(
                        ContextCompat.getDrawable(
                            context,
                            videoPlayerDrawablePause
                        )
                    )
                }
            } else if (playingState == PlayingState.PAUSED || playingState == PlayingState.ENDED) {
                if (imageTag != 2) {
                    videoPlayerPlayButton?.tag = 2
                    videoPlayerPlayButton?.setImageDrawable(
                        ContextCompat.getDrawable(
                            context,
                            videoPlayerDrawablePlay
                        )
                    )
                }
            }
        }
    }

    open fun onVideoPlayerChangeVolume(state: VolumeState) {
        logDebug("[$className : onVideoPlayerChangeVolume]")

        when (state) {
            VolumeState.ON -> {
                videoPlayer?.volume = 1f
                onVideoPlayerChangeVolumeControlImage()
                onVideoPlayerAnimateVolumeControl()
            }
            VolumeState.OFF -> {
                videoPlayer?.volume = 0f
                onVideoPlayerChangeVolumeControlImage()
                onVideoPlayerAnimateVolumeControl()
            }
            else -> {
                // ignore
            }
        }
    }

    open fun onVideoPlayerSetUiStateDefault() {
        logDebug("[$className : onVideoPlayerSetUiStateDefault]")

        onVideoPlayerChangePlayingImage(PlayingState.ENDED)

        videoPlayerThumbnail?.visibility = VISIBLE
        videoPlayerMediaContainer?.visibility = GONE
        videoPlayerVolumeButton?.visibility = GONE
        videoPlayerProgressBar?.visibility = GONE
        videoPlayerControlsBackground?.visibility = VISIBLE
        videoPlayerPlayButton?.visibility = VISIBLE
        videoPlayerRestartButton?.visibility = GONE
    }

    open fun onVideoPlayerSetUiStatePlaying() {
        logDebug("[$className : onVideoPlayerSetUiStatePlaying]")

        onVideoPlayerChangePlayingImage()

        playingOptionsState = PlayingOptionsState.OFF

        videoPlayerThumbnail?.visibility = GONE
        videoPlayerMediaContainer?.visibility = VISIBLE
        videoPlayerVolumeButton?.visibility = GONE
        videoPlayerProgressBar?.visibility = GONE
        videoPlayerControlsBackground?.visibility = GONE
        videoPlayerPlayButton?.visibility = GONE
        videoPlayerRestartButton?.visibility = GONE
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
        videoPlayerPlayButton?.visibility = GONE
        videoPlayerRestartButton?.visibility = GONE
    }

    open fun onVideoPlayerSetUiStateError() {
        logDebug("[$className : onVideoPlayerSetUiStateError]")

        onVideoPlayerChangePlayingImage()

        videoPlayerThumbnail?.visibility = VISIBLE
        videoPlayerMediaContainer?.visibility = GONE
        videoPlayerVolumeButton?.visibility = GONE
        videoPlayerProgressBar?.visibility = GONE
        videoPlayerControlsBackground?.visibility = VISIBLE
        videoPlayerPlayButton?.visibility = VISIBLE
        videoPlayerRestartButton?.visibility = GONE
    }

    open fun onVideoPlayerSetUiStateEnded() {
        logDebug("[$className : onVideoPlayerSetUiStateEnded]")

        onVideoPlayerChangePlayingImage()

        videoPlayerThumbnail?.visibility = VISIBLE
        videoPlayerMediaContainer?.visibility = GONE
        videoPlayerVolumeButton?.visibility = GONE
        videoPlayerProgressBar?.visibility = GONE
        videoPlayerControlsBackground?.visibility = VISIBLE
        videoPlayerPlayButton?.visibility = VISIBLE
        videoPlayerRestartButton?.visibility = VISIBLE
    }

    open fun onVideoPlayerSetUiStateStopped() {
        logDebug("[$className : onVideoPlayerSetUiStateStopped]")

        onVideoPlayerChangePlayingImage(PlayingState.ENDED)

        videoPlayerThumbnail?.visibility = VISIBLE
        videoPlayerMediaContainer?.visibility = GONE
        videoPlayerVolumeButton?.visibility = GONE
        videoPlayerProgressBar?.visibility = GONE
        videoPlayerControlsBackground?.visibility = VISIBLE
        videoPlayerPlayButton?.visibility = VISIBLE
        videoPlayerRestartButton?.visibility = GONE
    }

    open fun onVideoPlayerSetUiStateAdded() {
        logDebug("[$className : onVideoPlayerSetUiStateAdded]")

        videoPlayerProgressBar?.visibility = GONE
        videoPlayerThumbnail?.visibility = GONE
        videoPlayerMediaContainer?.visibility = VISIBLE
        videoPlayerPlayButton?.visibility = GONE
        videoPlayerRestartButton?.visibility = GONE
    }

    open fun onVideoPlayerSetUiStatePlayingOptions() {
        logDebug("[$className : onVideoPlayerSetUiStatePlayingOptions]")

        onVideoPlayerChangePlayingImage()

        playingOptionsState = PlayingOptionsState.ON

        videoPlayerProgressBar?.visibility = GONE
        videoPlayerThumbnail?.visibility = GONE
        videoPlayerMediaContainer?.visibility = VISIBLE
        videoPlayerVolumeButton?.visibility = VISIBLE
        videoPlayerPlayButton?.visibility = VISIBLE
        videoPlayerRestartButton?.visibility = VISIBLE
        videoPlayerControlsBackground?.visibility = VISIBLE
    }

    open fun onVideoPlayerSetUiStateCheckPlayingOptions() {
        logDebug("[$className : onVideoPlayerSetUiStateCheckPlayingOptions]")

        if (playingState == PlayingState.PLAYING) {
            if (playingOptionsState == PlayingOptionsState.ON) {
                onVideoPlayerSetUiStatePlaying()
            } else if (playingOptionsState == PlayingOptionsState.OFF) {
                onVideoPlayerSetUiStatePlayingOptions()
            }
        } else {
            onVideoPlayerSetUiStatePlayingOptions()
        }
    }

    open fun onVideoPlayerSystemStart() {
        logDebug("[$className : onVideoPlayerSystemStart]")

        videoPlayer = getVideoPlayer()

        if (videoPlayer == null) {
            throw Exception("[$className : onVideoPlayerSystemStart] You need initialize your video player first")
        }

        videoPlayer?.removeListener(videoPlayerEventListener)
        videoPlayer?.addListener(videoPlayerEventListener)

        videoPlayerInitializeSurfaceView()
        videoPlayerCheckInitialVolumeState()

        removeOnScrollListener(videoPlayerScrollListener)
        addOnScrollListener(videoPlayerScrollListener)
    }

    open fun onVideoPlayerSystemStop() {
        logDebug("[$className : onVideoPlayerSystemStop]")

        resetVideoView(false)
        onVideoPlayerSetUiStateStopped()

        videoPlayerSurfaceView?.player = null
        videoPlayer = null
        playingState = PlayingState.ENDED
    }

    open fun onVideoPlayerSystemRestart() {
        logDebug("[$className : onVideoPlayerSystemRestart]")

        // player surface
        onVideoPlayerInitializeSurfaceView()

        // volume
        videoPlayerCheckInitialVolumeState()

        // check if have first video
        if (playingState != PlayingState.PLAYING) {
            videoPlayerPlayFirstAvailable(false)
        }
    }

    open fun getVideoPlayer(): SimpleExoPlayer? {
        return null
    }

    open fun isDebug(): Boolean {
        return false
    }

    open fun getMediaURL(item: T): String {
        return ""
    }

    open fun isVideoPlayerViewHolder(viewHolder: ViewHolder?): Boolean {
        return false
    }

    open fun getViewHolderThumbnail(viewHolder: ViewHolder?): ImageView? {
        return null
    }

    open fun getViewHolderProgressBar(viewHolder: ViewHolder?): ProgressBar? {
        return null
    }

    open fun getViewHolderVideoPlayerPlayButton(viewHolder: ViewHolder?): ImageView? {
        return null
    }

    open fun getViewHolderVideoPlayerRestartButton(viewHolder: ViewHolder?): ImageView? {
        return null
    }

    open fun getViewHolderVideoPlayerVolumeButton(viewHolder: ViewHolder?): ImageView? {
        return null
    }

    open fun getViewHolderVideoPlayerMediaContainer(viewHolder: ViewHolder?): ViewGroup? {
        return null
    }

    open fun getViewHolderVideoPlayerControlsBackground(viewHolder: ViewHolder?): View? {
        return null
    }
}
