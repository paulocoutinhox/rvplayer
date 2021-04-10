package com.paulocoutinho.rvplayer.ui.recyclerviews

import android.content.Context
import android.graphics.Point
import android.util.AttributeSet
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.paulocoutinho.rvplayer.Application
import com.paulocoutinho.rvplayer.R
import com.paulocoutinho.rvplayer.models.MediaObject
import com.paulocoutinho.rvplayer.ui.viewholders.VideoPlayerViewHolder
import com.paulocoutinho.rvplayer.util.Logger


class MyRecyclerView : RecyclerView {

    private enum class VolumeState {
        ON, OFF
    }

    private var thumbnail: ImageView? = null
    private var volumeControl: ImageView? = null
    private var progressBar: ProgressBar? = null
    private var viewHolderParent: View? = null
    private var frameLayout: FrameLayout? = null
    private var videoSurfaceView: PlayerView? = null
    private var videoPlayer: SimpleExoPlayer? = null

    // vars
    private var listObjects: ArrayList<MediaObject> = ArrayList()

    private var videoSurfaceDefaultHeight = 0
    private var screenDefaultHeight = 0
    private var playPosition = -1
    private var isVideoViewAdded = false

    private var volumeState: VolumeState? = null

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    private fun init() {
        Logger.d("[MyRecyclerView : init]")

        videoPlayer = Application.instance.videoPlayer

        val displayMetrics = resources.displayMetrics
        val height = displayMetrics.heightPixels
        val width = displayMetrics.widthPixels
        val point = Point(width, height)

        videoSurfaceDefaultHeight = point.x
        screenDefaultHeight = point.y

        videoSurfaceView = PlayerView(this.context)
        videoSurfaceView?.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        videoSurfaceView?.useController = false
        videoSurfaceView?.player = videoPlayer

        setVolumeControl(VolumeState.ON)

        addOnScrollListener(object : OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)

                if (newState == SCROLL_STATE_IDLE) {
                    Logger.d("[MyRecyclerView : onScrollStateChanged] New state: $newState")

                    thumbnail?.visibility = VISIBLE

                    checkToPlayVideo()
                }
            }
        })

        addOnChildAttachStateChangeListener(object : OnChildAttachStateChangeListener {
            override fun onChildViewAttachedToWindow(view: View) {
                Logger.d("[MyRecyclerView : onChildViewAttachedToWindow]")
                // ignore
            }

            override fun onChildViewDetachedFromWindow(view: View) {
                Logger.d("[MyRecyclerView : onChildViewDetachedFromWindow]")

                if (viewHolderParent != null && viewHolderParent == view) {
                    resetVideoView()
                }
            }
        })

        videoPlayer?.addListener(object : Player.EventListener {
            override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
                Logger.d("[MyRecyclerView : onPlayerStateChanged] State: $playbackState, PlayWhenReady: $playWhenReady")

                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        Logger.d("[MyRecyclerView : onPlayerStateChanged] Buffering video")

                        progressBar?.visibility = VISIBLE
                    }

                    Player.STATE_ENDED -> {
                        Logger.d("[MyRecyclerView : onPlayerStateChanged] Video ended")
                    }

                    Player.STATE_IDLE -> {
                        // ignore
                    }

                    Player.STATE_READY -> {
                        Logger.d("[MyRecyclerView : onPlayerStateChanged] Ready to play")

                        progressBar?.visibility = GONE

                        if (!isVideoViewAdded) {
                            addVideoView()
                        }
                    }

                    else -> {
                        // ignore
                    }
                }
            }
        })
    }

    private val videoViewClickListener = OnClickListener {
        Logger.d("[MyRecyclerView : videoViewClickListener]")
        toggleVolume()
    }

    /**
     * Returns the visible region of the video surface on the screen.
     * if some is cut off, it will return less than the @videoSurfaceDefaultHeight
     *
     * @param playPosition
     * @return
     */
    private fun getVisibleVideoSurfaceHeight(playPosition: Int): Int {
        Logger.d("[MyRecyclerView : getVisibleVideoSurfaceHeight]")

        val lm = (layoutManager as LinearLayoutManager)
        val at = playPosition - lm.findFirstVisibleItemPosition()

        Logger.d("[MyRecyclerView : getVisibleVideoSurfaceHeight] At: $at")

        val child = getChildAt(at) ?: return 0
        val location = IntArray(2)
        child.getLocationInWindow(location)

        return if (location[1] < 0) {
            location[1] + videoSurfaceDefaultHeight
        } else {
            screenDefaultHeight - location[1]
        }
    }

    private fun removeVideoView(videoView: PlayerView?) {
        Logger.d("[MyRecyclerView : removeVideoView]")

        val parent = videoView?.parent as? ViewGroup ?: return
        val index = parent.indexOfChild(videoView)

        if (index >= 0) {
            parent.removeViewAt(index)

            videoPlayer?.stop()

            isVideoViewAdded = false
            viewHolderParent?.setOnClickListener(null)
        }
    }

    private fun addVideoView() {
        Logger.d("[MyRecyclerView : addVideoView]")

        frameLayout?.addView(videoSurfaceView)

        isVideoViewAdded = true

        videoSurfaceView?.requestFocus()
        videoSurfaceView?.visibility = VISIBLE
        videoSurfaceView?.alpha = 1f

        thumbnail?.visibility = GONE
        progressBar?.visibility = GONE
    }

    private fun resetVideoView() {
        Logger.d("[MyRecyclerView : resetVideoView]")

        if (isVideoViewAdded) {
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
                setVolumeControl(VolumeState.ON)
            } else if (volumeState == VolumeState.ON) {
                Logger.d("[MyRecyclerView : toggleVolume] Disabling volume")
                setVolumeControl(VolumeState.OFF)
            }
        }
    }

    private fun setVolumeControl(state: VolumeState) {
        Logger.d("[MyRecyclerView : setVolumeControl]")

        volumeState = state

        if (state == VolumeState.OFF) {
            videoPlayer?.volume = 0f
            animateVolumeControl()
        } else if (state == VolumeState.ON) {
            videoPlayer?.volume = 1f
            animateVolumeControl()
        }
    }

    private fun buildMediaSource(item: MediaObject): MediaSource {
        val dataSourceFactory = DefaultDataSourceFactory(context)
        val mediaItem = MediaItem.fromUri(item.mediaUrl ?: "")

        return ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
    }

    private fun animateVolumeControl() {
        Logger.d("[MyRecyclerView : animateVolumeControl]")

        if (volumeControl != null) {
            volumeControl?.bringToFront()

            if (volumeState == VolumeState.OFF) {
                volumeControl?.load(R.drawable.ic_volume_off_grey_24dp)
            } else if (volumeState == VolumeState.ON) {
                volumeControl?.load(R.drawable.ic_volume_up_grey_24dp)
            }

            volumeControl?.animate()?.cancel()
            volumeControl?.alpha = 1f

            volumeControl
                    ?.animate()
                    ?.alpha(0f)
                    ?.setDuration(600)
                    ?.startDelay = 1000
        }
    }

    fun playVideo(isEndOfList: Boolean) {
        Logger.d("[MyRecyclerView : playVideo]")

        val targetPosition: Int

        if (!isEndOfList) {
            val lm = (layoutManager as LinearLayoutManager)
            val startPosition = lm.findFirstVisibleItemPosition()
            var endPosition = lm.findLastVisibleItemPosition()

            // if there is more than 2 list-items on the screen, set the difference to be 1
            if (endPosition - startPosition > 1) {
                endPosition = startPosition + 1
            }

            // something is wrong. return.
            if (startPosition < 0 || endPosition < 0) {
                return
            }

            // if there is more than 1 list-item on the screen
            targetPosition = if (startPosition != endPosition) {
                val startPositionVideoHeight = getVisibleVideoSurfaceHeight(startPosition)
                val endPositionVideoHeight = getVisibleVideoSurfaceHeight(endPosition)
                if (startPositionVideoHeight > endPositionVideoHeight) startPosition else endPosition
            } else {
                startPosition
            }
        } else {
            targetPosition = listObjects.size - 1
        }

        Logger.d("[MyRecyclerView : playVideo] Target position: $targetPosition")

        // video is already playing so return
        if (targetPosition == playPosition) {
            return
        }

        // set the position of the list-item that is to be played
        playPosition = targetPosition

        if (videoSurfaceView == null) {
            return
        }

        // remove any old surface views from previously playing videos
        videoSurfaceView?.visibility = INVISIBLE
        removeVideoView(videoSurfaceView)

        val lm = (layoutManager as LinearLayoutManager)
        val currentPosition = targetPosition - lm.findFirstVisibleItemPosition()
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
        viewHolderParent = holder.itemView
        frameLayout = holder.mediaContainer

        videoSurfaceView?.player = videoPlayer
        viewHolderParent?.setOnClickListener(videoViewClickListener)

        val mediaSource = buildMediaSource(listObjects[targetPosition])

        videoPlayer?.setMediaSource(mediaSource)
        videoPlayer?.prepare()
        videoPlayer?.repeatMode = Player.REPEAT_MODE_OFF
        videoPlayer?.playWhenReady = true
    }

    fun releasePlayer() {
        Logger.d("[MyRecyclerView : releasePlayer]")

        if (videoPlayer != null) {
            videoPlayer?.release()
            videoPlayer = null
        }

        viewHolderParent = null
    }

    fun checkToPlayVideo() {
        Logger.d("[MyRecyclerView : checkToPlayVideo]")

        if (canScrollVertically(1)) {
            playVideo(false)
        } else {
            playVideo(true)
        }
    }

    fun setListObjects(objects: ArrayList<MediaObject>) {
        Logger.d("[MyRecyclerView : setListObjects]")
        listObjects = objects
    }
}
