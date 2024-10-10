package com.saggitt.omega.searchsuggestion

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.util.Patterns
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.webkit.URLUtil
import androidx.appcompat.R
import androidx.appcompat.widget.AppCompatAutoCompleteTextView
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class SearchView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.autoCompleteTextViewStyle
) : AppCompatAutoCompleteTextView(context, attrs, defStyleAttr) {

    interface PreFocusListener {
        fun onPreFocus()
    }

    @SuppressLint("SetTextI18n")
    override fun setText(text: CharSequence?, type: BufferType?) {
        if(text?.contains(WEB_HOME_CONTAIN) == true){
            super.setText("", type)
        }else {
            super.setText(text, type)
        }

    }

    private var onPreFocusListener: PreFocusListener? = null
    private var isBeingClicked: Boolean = false
    private var timePressedNs: Long = 0

    init {
        setSelectAllOnFocus(true)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                timePressedNs = System.nanoTime()
                isBeingClicked = true
            }

            MotionEvent.ACTION_CANCEL -> isBeingClicked = false
            MotionEvent.ACTION_UP -> if (isBeingClicked && !isLongPress(timePressedNs)) {
                onPreFocusListener?.onPreFocus()
            }
        }

        return super.onTouchEvent(event)
    }

    private fun isLongPress(actionDownTime: Long): Boolean =
        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - actionDownTime) >= ViewConfiguration.getLongPressTimeout()


}
const val WEB_HOME_CONTAIN = "browsesafe.kidsmode.co"
fun smartUrlFilter(url: String, canBeSearch: Boolean, searchUrl: String): String {
    var inUrl = url.trim()
    val hasSpace = inUrl.contains(' ')
    val matcher = ACCEPTED_URI_SCHEMA.matcher(inUrl)
    if (matcher.matches()) {
        // force scheme to lowercase
        val scheme = requireNotNull(matcher.group(1)) { "matches() implies this is non null" }
        val lcScheme = scheme.toLowerCase(Locale.getDefault())
        if (lcScheme != scheme) {
            inUrl = lcScheme + matcher.group(2)
        }
        if (hasSpace && Patterns.WEB_URL.matcher(inUrl).matches()) {
            inUrl = inUrl.replace(" ", URL_ENCODED_SPACE)
        }
        return inUrl
    }
    if (!hasSpace) {
        if (Patterns.WEB_URL.matcher(inUrl).matches()) {
            return URLUtil.guessUrl(inUrl)
        }
    }

    return if (canBeSearch) {
        URLUtil.composeSearchUrl(inUrl, searchUrl, QUERY_PLACE_HOLDER)
    } else {
        ""
    }
}

private val ACCEPTED_URI_SCHEMA =
    Pattern.compile("(?i)((?:http|https|file)://|(?:inline|data|about|javascript):|(?:.*:.*@))(.*)")
const val QUERY_PLACE_HOLDER = "%s"
private const val URL_ENCODED_SPACE = "%20"