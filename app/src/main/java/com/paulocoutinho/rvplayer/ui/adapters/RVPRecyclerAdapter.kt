package com.paulocoutinho.rvplayer.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.paulocoutinho.rvplayer.R
import com.paulocoutinho.rvplayer.models.MediaObject
import com.paulocoutinho.rvplayer.ui.viewholders.EmptyViewHolder
import com.paulocoutinho.rvplayer.ui.viewholders.ImageViewHolder
import com.paulocoutinho.rvplayer.ui.viewholders.VideoPlayerViewHolder
import com.paulocoutinho.rvplayer.util.Logger

class RVPRecyclerAdapter(private val mediaObjects: ArrayList<MediaObject>) : RecyclerView.Adapter<ViewHolder>() {

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        if (viewType == MediaObject.MediaType.IMAGE.ordinal) {
            val view = LayoutInflater.from(viewGroup.context).inflate(R.layout.list_item_image, viewGroup, false)

            return ImageViewHolder(view)
        } else if (viewType == MediaObject.MediaType.VIDEO.ordinal) {
            val view = LayoutInflater.from(viewGroup.context).inflate(R.layout.list_item_video_player, viewGroup, false)

            return VideoPlayerViewHolder(view)
        }

        val view = LayoutInflater.from(viewGroup.context).inflate(R.layout.list_item_empty, viewGroup, false)
        return EmptyViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, i: Int) {
        when (val itemViewType = getItemViewType(i)) {
            MediaObject.MediaType.IMAGE.ordinal -> {
                (viewHolder as ImageViewHolder).onBind(mediaObjects[i])
            }
            MediaObject.MediaType.VIDEO.ordinal -> {
                (viewHolder as VideoPlayerViewHolder).onBind(mediaObjects[i])
            }
            else -> {
                Logger.e("[RVPRecyclerAdapter : onBindViewHolder] Unknown type: $itemViewType")
            }
        }
    }

    override fun getItemViewType(i: Int): Int {
        val mediaObject = mediaObjects[i]
        return mediaObject.type?.ordinal ?: -1
    }

    override fun getItemCount(): Int {
        return mediaObjects.size
    }

}