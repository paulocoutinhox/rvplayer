package com.paulocoutinho.rvplayer.ui.viewholders

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import coil.load
import com.paulocoutinho.rvplayer.R
import com.paulocoutinho.rvplayer.models.MediaObject

class ImageViewHolder(var parent: View) : ViewHolder(parent) {

    var imageTitle: TextView = itemView.findViewById(R.id.imageTitle)
    var imageThumbnail: ImageView = itemView.findViewById(R.id.imageThumbnail)

    fun onBind(mediaObject: MediaObject) {
        imageTitle.text = mediaObject.title

        imageThumbnail.load(mediaObject.thumbnail) {
            placeholder(R.drawable.placeholder)
            error(R.drawable.placeholder)
        }
    }
}
