package com.paulocoutinho.rvplayer.util

import android.util.Log
import com.paulocoutinho.rvplayer.Application

object Logger {

    @Suppress("unused")
    @JvmStatic
    fun d(message: String) {
        Log.d(Application.LOG_GROUP, message)
    }

    @Suppress("unused")
    @JvmStatic
    fun i(message: String) {
        Log.i(Application.LOG_GROUP, message)
    }

    @Suppress("unused")
    @JvmStatic
    fun e(message: String) {
        Log.e(Application.LOG_GROUP, message)
    }

    @Suppress("unused")
    @JvmStatic
    fun w(message: String) {
        Log.w(Application.LOG_GROUP, message)
    }

    @Suppress("unused")
    @JvmStatic
    fun v(message: String) {
        Log.v(Application.LOG_GROUP, message)
    }

}