package com.saggitt.omega.searchsuggestion

import android.util.Log


class AndroidLogger() : Logger {

    override fun log(tag: String, message: String) {
        Log.d(tag, message)
    }

    override fun log(tag: String, message: String, throwable: Throwable) {
        Log.e(tag, message, throwable)
    }

}
