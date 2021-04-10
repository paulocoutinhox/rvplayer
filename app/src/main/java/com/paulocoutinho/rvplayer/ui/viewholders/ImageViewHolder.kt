package com.paulocoutinho.rvplayer.ui.viewholders

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import coil.load
import com.paulocoutinho.rvplayer.Application
import com.paulocoutinho.rvplayer.R
import com.paulocoutinho.rvplayer.models.MediaObject

@SuppressLint("PrivateResource")
@Suppress("unused")
class ImageViewHolder(var parent: View) : ViewHolder(parent) {

    var imageTitle: TextView = itemView.findViewById(R.id.imageTitle)
    var imageThumbnail: ImageView = itemView.findViewById(R.id.imageThumbnail)
    var imageProgressBar: ProgressBar = itemView.findViewById(R.id.imageProgressBar)

    fun onBind(mediaObject: MediaObject) {
        // layout
        val layoutParams = imageThumbnail.layoutParams
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

        // text
        imageTitle.text = mediaObject.title

        // image
        imageThumbnail.load(mediaObject.thumbnail) {
            placeholder(R.drawable.placeholder)
            error(R.drawable.placeholder)
            listener(
                onStart = {
                    imageProgressBar.visibility = View.VISIBLE
                },
                onSuccess = { _, _ ->
                    imageThumbnail.visibility = View.VISIBLE
                    imageProgressBar.visibility = View.GONE
                },
                onError = { _, _ ->
                    imageThumbnail.visibility = View.VISIBLE
                    imageProgressBar.visibility = View.GONE
                }

            )
        }
    }
}
