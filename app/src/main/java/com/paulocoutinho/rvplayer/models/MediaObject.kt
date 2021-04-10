package com.paulocoutinho.rvplayer.models

class MediaObject {

    enum class MediaType {
        IMAGE, VIDEO
    }

    var title: String? = null
    var mediaUrl: String? = null
    var thumbnail: String? = null
    var description: String? = null
    var type: MediaType? = null

    constructor(title: String?, media_url: String?, thumbnail: String?, description: String?, type: MediaType?) {
        this.title = title
        this.mediaUrl = media_url
        this.thumbnail = thumbnail
        this.description = description
        this.type = type
    }

}