package com.paulocoutinho.rvplayer.models

class MediaObject {

    enum class MediaType {
        IMAGE, VIDEO
    }

    var title: String? = null
    var mediaUrl: String? = null
    var thumbnail: String? = null
    var description: String? = null
    var autoPlay: Boolean? = false
    var type: MediaType? = null
    var width: Int = 0
    var height: Int = 0

    constructor(title: String?, media_url: String?, thumbnail: String?, description: String?, autoPlay: Boolean, width: Int, height: Int, type: MediaType?) {
        this.title = title
        this.mediaUrl = media_url
        this.thumbnail = thumbnail
        this.description = description
        this.autoPlay = autoPlay
        this.type = type
        this.width = width
        this.height = height
    }
}
