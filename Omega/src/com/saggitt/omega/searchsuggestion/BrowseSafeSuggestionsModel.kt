package com.saggitt.omega.searchsuggestion


import android.app.Application
import com.android.launcher3.R
import io.reactivex.rxjava3.core.Single
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import org.json.JSONArray

class BrowseSafeSuggestionsModel(
    okHttpClient: Single<OkHttpClient>,
    requestFactory: RequestFactory,
    application: Application,
    logger: Logger
) : BaseSuggestionsModel(okHttpClient, requestFactory, UTF8, application.preferredLocale, logger) {

    private val searchSubtitle = application.getString(R.string.suggestion)

//    https://sug.kidsmode.site/v1/sug/?q=<query>
//    https://sug.kidsmode.site/v1/sug/?q=s
    override fun createQueryUrl(query: String, language: String): HttpUrl = HttpUrl.Builder()
        .scheme("https")
        .host("sug.kidsmode.site")
        .encodedPath("/v1/sug")
        .addEncodedQueryParameter("q", query)
        .build()

    @Throws(Exception::class)
    override fun parseResults(responseBody: ResponseBody): List<SearchSuggestion> {
        val array = JSONArray(responseBody.string())
        val jSon = array.get(1) as JSONArray
        val suggestions = mutableListOf<SearchSuggestion>()
        for (i in 0 until jSon.length()) {
            val item = jSon.get(i) as String
            suggestions.add(SearchSuggestion("$searchSubtitle \"$item\"", item))
        }
        return suggestions
    }

}