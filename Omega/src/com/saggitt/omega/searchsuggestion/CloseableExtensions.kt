package com.saggitt.omega.searchsuggestion

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import java.io.Closeable
import java.util.Locale

/**
 * Close a [Closeable] and absorb any exceptions within [block], logging them when they occur.
 */
inline fun <T : Closeable, R> T.safeUse(block: (T) -> R): R? {
    return try {
        this.use(block)
    } catch (throwable: Throwable) {
        Log.e("Closeable", "Unable to parse results", throwable)
        null
    }
}

const val UTF8 = "UTF-8"
/**
 * The preferred locale of the user.
 */
val Context.preferredLocale: Locale
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        resources.configuration.locales[0]
    } else {
        @Suppress("DEPRECATION")
        resources.configuration.locale
    }


/**
 * Gets a drawable from the context.
 */
inline fun Context.drawable(@DrawableRes drawableRes: Int): Drawable =
    ContextCompat.getDrawable(this, drawableRes)!!