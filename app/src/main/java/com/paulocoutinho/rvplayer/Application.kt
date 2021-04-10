package com.paulocoutinho.rvplayer

import android.os.StrictMode
import androidx.multidex.MultiDexApplication
import coil.Coil
import coil.ImageLoader
import com.google.android.exoplayer2.SimpleExoPlayer
import com.paulocoutinho.rvplayer.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class Application : MultiDexApplication(), CoroutineScope {

    override val coroutineContext = Dispatchers.Main

    companion object {

        const val LOG_GROUP = "RVPLAYER"

        lateinit var instance: Application
            private set
    }

    var videoPlayer: SimpleExoPlayer? = null

    init {
        instance = this
    }

    override fun onCreate() {
        super.onCreate()

        initializeStrictMode()
        initializeAppData()

        setupImageLoader()
    }

    private fun initializeStrictMode() {
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build()
            )

            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    .build()
            )
        }
    }

    private fun initializeAppData() {
        videoPlayer = SimpleExoPlayer.Builder(instance).build()
    }

    private fun setupImageLoader() {
        Logger.d("[Application : setupImageLoader]")

        val imageLoader = ImageLoader.Builder(this)
            .allowHardware(false)
            .build()

        Coil.setImageLoader(imageLoader)
    }
}
