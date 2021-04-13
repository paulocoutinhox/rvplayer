package com.paulocoutinho.rvplayer.ui.viewholders

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.paulocoutinho.rvplayer.Application
import com.paulocoutinho.rvplayer.R
import com.paulocoutinho.rvplayer.models.MediaObject

@Suppress("unused")
class VideoPlayerViewHolder(var parent: View) : RecyclerView.ViewHolder(parent) {

    var videoPlayerContainer: FrameLayout = itemView.findViewById(R.id.videoPlayerContainer)
    var videoPlayerMediaContainer: FrameLayout = itemView.findViewById(R.id.videoPlayerMediaContainer)
    var videoPlayerControlsBackground: FrameLayout = itemView.findViewById(R.id.videoPlayerControlsBackground)
    var videoPlayerTitle: TextView = itemView.findViewById(R.id.videoPlayerTitle)
    var videoPlayerThumbnail: ImageView = itemView.findViewById(R.id.videoPlayerThumbnail)
    var videoPlayerVolumeButton: ImageView = itemView.findViewById(R.id.videoPlayerVolumeButton)
    var videoPlayerProgressBar: ProgressBar = itemView.findViewById(R.id.videoPlayerProgressBar)
    var videoPlayerPlayButton: ImageView = itemView.findViewById(R.id.videoPlayerPlayButton)
    var videoPlayerRestartButton: ImageView = itemView.findViewById(R.id.videoPlayerRestartButton)

    init {
        videoPlayerThumbnail.visibility = RecyclerView.VISIBLE
        videoPlayerMediaContainer.visibility = RecyclerView.GONE
        videoPlayerVolumeButton.visibility = RecyclerView.GONE
        videoPlayerProgressBar.visibility = RecyclerView.GONE
        videoPlayerControlsBackground.visibility = RecyclerView.VISIBLE
        videoPlayerPlayButton.visibility = RecyclerView.VISIBLE
        videoPlayerRestartButton.visibility = RecyclerView.GONE
    }

    fun onBind(mediaObject: MediaObject) {
        // layout
        val layoutParams = videoPlayerContainer.layoutParams
        layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT

        // size
        val displayMetrics = Application.instance.resources.displayMetrics
        val density = displayMetrics.density
        val offset = 32 * density
        val srcWidth = mediaObject.width
        val srcHeight = mediaObject.height
        val screenWidth = displayMetrics.widthPixels - offset
        val scale = screenWidth / 1.0 / srcWidth

        layoutParams.height = (scale * srcHeight).toInt()

        // tag
        parent.tag = this

        // text
        videoPlayerTitle.text = mediaObject.title

        // thumb
        videoPlayerThumbnail.load(mediaObject.thumbnail) {
            placeholder(R.drawable.placeholder)
            error(R.drawable.placeholder)
        }
    }
}
