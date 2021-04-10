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

    var mediaContainer: FrameLayout = itemView.findViewById(R.id.mediaContainer)
    var title: TextView = itemView.findViewById(R.id.title)
    var thumbnail: ImageView = itemView.findViewById(R.id.thumbnail)
    var volumeControl: ImageView = itemView.findViewById(R.id.volumeControl)
    var progressBar: ProgressBar = itemView.findViewById(R.id.progressBar)

    fun onBind(mediaObject: MediaObject) {
        parent.tag = this

        title.text = mediaObject.title
        thumbnail.load(mediaObject.thumbnail)
    }

}