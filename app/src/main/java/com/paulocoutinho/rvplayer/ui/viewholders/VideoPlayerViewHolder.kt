package com.paulocoutinho.rvplayer.ui.viewholders

import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import coil.load
import com.paulocoutinho.rvplayer.R
import com.paulocoutinho.rvplayer.models.MediaObject

class VideoPlayerViewHolder(var parent: View) : ViewHolder(parent) {

    var videoPlayerMediaContainer: FrameLayout = itemView.findViewById(R.id.videoPlayerMediaContainer)
    var videoControlsBackground: FrameLayout = itemView.findViewById(R.id.videoControlsBackground)
    var videoPlayerTitle: TextView = itemView.findViewById(R.id.videoPlayerTitle)
    var videoPlayerThumbnail: ImageView = itemView.findViewById(R.id.videoPlayerThumbnail)
    var videoPlayerVolumeControl: ImageView = itemView.findViewById(R.id.videoPlayerVolumeControl)
    var videoPlayerProgressBar: ProgressBar = itemView.findViewById(R.id.videoPlayerProgressBar)

    fun onBind(mediaObject: MediaObject) {
        parent.tag = this

        videoPlayerTitle.text = mediaObject.title

        videoPlayerThumbnail.load(mediaObject.thumbnail) {
            placeholder(R.drawable.placeholder)
            error(R.drawable.placeholder)
        }
    }
}
