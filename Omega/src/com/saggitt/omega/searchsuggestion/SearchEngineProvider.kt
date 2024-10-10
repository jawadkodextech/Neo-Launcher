package com.saggitt.omega.searchsuggestion

import android.app.Application
import io.reactivex.rxjava3.core.Single
import okhttp3.OkHttpClient

/**
 * The model that provides the search engine based
 * on the user's preference.
 */
//@Reusable//@SuggestionsClient//@Inject
class SearchEngineProvider constructor(
    private val okHttpClient: Single<OkHttpClient>,
    private val requestFactory: RequestFactory,
    private val application: Application,
    private val logger: Logger,
) {

    /**
     * Provide the [SuggestionsRepository] that maps to the user's current preference.
     */
    fun provideSearchSuggestions(): SuggestionsRepository =
        BrowseSafeSuggestionsModel(okHttpClient, requestFactory, application, logger)

    /**
     * Provide the [BaseSearchEngine] that maps to the user's current preference.
     */
    fun provideSearchEngine(): BaseSearchEngine = BrowseSafeSearch()

    /**
     * Return the serializable index of of the provided [BaseSearchEngine].
     */
    fun mapSearchEngineToPreferenceIndex(searchEngine: BaseSearchEngine): Int = 0

    /**
     * Provide a list of all supported search engines.
     */
    fun provideAllSearchEngines(): List<BaseSearchEngine> = listOf(
        BrowseSafeSearch()
    )

}
