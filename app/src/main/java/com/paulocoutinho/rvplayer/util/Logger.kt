package com.paulocoutinho.rvplayer.util

import android.util.Log
import com.paulocoutinho.rvplayer.Application
import com.paulocoutinho.rvplayer.BuildConfig

object Logger {

    @Suppress("unused")
    @JvmStatic
    fun d(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(Application.LOG_GROUP, message)
        }
    }

    @Suppress("unused")
    @JvmStatic
    fun i(message: String) {
        if (BuildConfig.DEBUG) {
            Log.i(Application.LOG_GROUP, message)
        }
    }

    @Suppress("unused")
    @JvmStatic
    fun w(message: String) {
        if (BuildConfig.DEBUG) {
            Log.w(Application.LOG_GROUP, message)
        }
    }

    @Suppress("unused")
    @JvmStatic
    fun v(message: String) {
        if (BuildConfig.DEBUG) {
            Log.v(Application.LOG_GROUP, message)
        }
    }

    @Suppress("unused")
    @JvmStatic
    fun e(message: String) {
        Log.e(Application.LOG_GROUP, message)
    }
}
