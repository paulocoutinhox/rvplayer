package com.paulocoutinho.rvplayer.ui.viewholders

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import coil.load
import com.paulocoutinho.rvplayer.Application
import com.paulocoutinho.rvplayer.R
import com.paulocoutinho.rvplayer.models.MediaObject

@Suppress("unused")
class VideoPlayerViewHolder(var parent: View) : ViewHolder(parent) {

    var videoPlayerContainer: FrameLayout = itemView.findViewById(R.id.videoPlayerContainer)
    var listItemVideoPlayerCardBody: ConstraintLayout = itemView.findViewById(R.id.listItemVideoPlayerCardBody)
    var videoPlayerMediaContainer: FrameLayout = itemView.findViewById(R.id.videoPlayerMediaContainer)
    var videoControlsBackground: FrameLayout = itemView.findViewById(R.id.videoControlsBackground)
    var videoPlayerTitle: TextView = itemView.findViewById(R.id.videoPlayerTitle)
    var videoPlayerThumbnail: ImageView = itemView.findViewById(R.id.videoPlayerThumbnail)
    var videoPlayerVolumeControl: ImageView = itemView.findViewById(R.id.videoPlayerVolumeControl)
    var videoPlayerProgressBar: ProgressBar = itemView.findViewById(R.id.videoPlayerProgressBar)

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
