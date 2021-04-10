package com.paulocoutinho.rvplayer.ui.viewholders

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import coil.load
import com.paulocoutinho.rvplayer.R
import com.paulocoutinho.rvplayer.models.MediaObject

class ImageViewHolder(var parent: View) : ViewHolder(parent) {

    var title: TextView = itemView.findViewById(R.id.title)
    var thumbnail: ImageView = itemView.findViewById(R.id.thumbnail)

    fun onBind(mediaObject: MediaObject) {
        title.text = mediaObject.title

        thumbnail.load(mediaObject.thumbnail) {
            placeholder(R.drawable.placeholder)
            error(R.drawable.placeholder)
        }
    }

}