package com.saggitt.omega.searchsuggestion

//import acr.browser.lightning.R
//import acr.browser.lightning.database.Bookmark
//import acr.browser.lightning.database.HistoryEntry
//import acr.browser.lightning.database.SearchSuggestion
//import acr.browser.lightning.database.WebPage
//import acr.browser.lightning.database.bookmark.BookmarkRepository
//import acr.browser.lightning.database.history.HistoryRepository
//import acr.browser.lightning.browser.di.DatabaseScheduler
//import acr.browser.lightning.browser.di.MainScheduler
//import acr.browser.lightning.browser.di.NetworkScheduler
//import acr.browser.lightning.browser.di.injector
//import acr.browser.lightning.extensions.drawable
//import acr.browser.lightning.preference.UserPreferences
//import acr.browser.lightning.rx.join
//import acr.browser.lightning.search.suggestions.NoOpSuggestionsRepository
//import acr.browser.lightning.search.suggestions.SuggestionsRepository
import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Filter
import android.widget.Filterable
import androidx.recyclerview.widget.RecyclerView
import com.android.launcher3.R
import io.reactivex.rxjava3.core.BackpressureStrategy
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.subjects.PublishSubject
import java.util.Locale

//import javax.inject.Inject

class SuggestionsAdapter(
    context: Context,
    var databaseScheduler: Scheduler,
    var networkScheduler: Scheduler,
    var mainScheduler: Scheduler,
    var searchEngineProvider: SearchEngineProvider,
) : BaseAdapter(), Filterable {

    private var filteredList: List<WebPage> = emptyList()

//    internal lateinit var bookmarkRepository: BookmarkRepository
//    @Inject internal lateinit var userPreferences: UserPreferences
//    lateinit var historyRepository: HistoryRepository


    private var allBookmarks: List<Bookmark.Entry> = emptyList()
    private val searchFilter = SearchFilter(this)

    private val searchIcon = context.drawable(R.drawable.ic_search)
    private val webPageIcon = context.drawable(R.drawable.ic_history)
    private val bookmarkIcon = context.drawable(R.drawable.ic_bookmark)
    private var suggestionsRepository: SuggestionsRepository
    private val disposables = CompositeDisposable() // Manage subscriptions

    //results
    fun getSearchResults(
        query: String,
        callBack: ((List<WebPage>) -> Unit),
    ): Disposable {//: Single<List<WebPage>>
        val disposable = suggestionsRepository.resultsForSearch(query)
            .subscribeOn(networkScheduler)
            .observeOn(mainScheduler)
            .subscribe({ results ->
                // Handle the search results here

                Log.d("Result", "Result $results")
                callBack.invoke(results)
            }, { error ->
                callBack.invoke(emptyList())
                // Handle the error here
                Log.d("Result", "Result error $error")
            })

        disposables.add(disposable) // Add to CompositeDisposable
        return disposable // You can return it for further handling if needed
    }

    fun cancelAllRequests() {
        disposables.clear() // Clear all ongoing requests
    }

    // Remember to dispose the CompositeDisposable when done
    fun onCleared() {
        cancelAllRequests() // Call this when you no longer need the adapter
    }

    /**
     * The listener that is fired when the insert button on a [SearchSuggestion] is clicked.
     */
    var onSuggestionInsertClick: ((WebPage) -> Unit)? = null

    private val onClick = View.OnClickListener {
        onSuggestionInsertClick?.invoke(it.tag as WebPage)
    }

    private val layoutInflater = LayoutInflater.from(context)

    init {
//        context.injector.inject(this)

        suggestionsRepository = searchEngineProvider.provideSearchSuggestions()
//        suggestionsRepository.resultsForSearch()

        searchFilter.input().results()
            .subscribeOn(databaseScheduler)
            .observeOn(mainScheduler)
            .subscribe(::publishResults)
    }

    fun refreshPreferences() {
        suggestionsRepository = searchEngineProvider.provideSearchSuggestions()
    }


    override fun getCount(): Int = filteredList.size

    override fun getItem(position: Int): Any? {
        if (position > filteredList.size || position < 0) {
            return null
        }
        return filteredList[position]
    }

    override fun getItemId(position: Int): Long = 0

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val holder: SuggestionViewHolder
        val finalView: View

        if (convertView == null) {
            finalView = layoutInflater.inflate(R.layout.two_line_autocomplete, parent, false)

            holder = SuggestionViewHolder(finalView)
            finalView.tag = holder
        } else {
            finalView = convertView
            holder = convertView.tag as SuggestionViewHolder
        }
        val webPage: WebPage = filteredList[position]

        holder.titleView.text = webPage.title
        holder.urlView.text = webPage.url

        val image = when (webPage) {
            is Bookmark -> bookmarkIcon
            is SearchSuggestion -> searchIcon
            is HistoryEntry -> webPageIcon
        }

        holder.imageView.setImageDrawable(image)

        holder.insertSuggestion.tag = webPage
        holder.insertSuggestion.setOnClickListener(onClick)

        return finalView
    }

    override fun getFilter(): Filter = searchFilter

    private fun publishResults(list: List<WebPage>?) {
        if (list == null) {
            notifyDataSetChanged()
            return
        }
        if (list != filteredList) {
            filteredList = list
            notifyDataSetChanged()
        }
    }

    private fun getBookmarksForQuery(query: String): Single<List<Bookmark.Entry>> =
        Single.fromCallable {
            (allBookmarks.filter {
                it.title.lowercase(Locale.getDefault()).startsWith(query)
            } + allBookmarks.filter {
                it.url.contains(query)
            }).distinct().take(MAX_SUGGESTIONS)
        }

    private fun Observable<CharSequence>.results(): Flowable<List<WebPage>> = this
        .toFlowable(BackpressureStrategy.LATEST)
        .map { it.toString().lowercase(Locale.getDefault()).trim() }
        .filter(String::isNotEmpty)
        .share()
        .compose { upstream ->
            val searchEntries = upstream
                .flatMapSingle(suggestionsRepository::resultsForSearch)
                .subscribeOn(networkScheduler)
                .startWithItem(emptyList())
                .share()

            searchEntries
        }
        .map { searches ->
            searches.take(MAX_SUGGESTIONS)
        }


    companion object {
        private const val MAX_SUGGESTIONS = 5
    }

    private class SearchFilter(
        private val suggestionsAdapter: SuggestionsAdapter,
    ) : Filter() {

        private val publishSubject = PublishSubject.create<CharSequence>()

        fun input(): Observable<CharSequence> = publishSubject.hide()

        override fun performFiltering(constraint: CharSequence?): FilterResults {
            if (constraint?.isBlank() != false) {
                return FilterResults()
            }
            publishSubject.onNext(constraint.trim())

            return FilterResults().apply { count = 1 }
        }

        override fun convertResultToString(resultValue: Any) = (resultValue as WebPage).url

        override fun publishResults(constraint: CharSequence?, results: FilterResults?) =
            suggestionsAdapter.publishResults(null)
    }

}


class RVSuggestionAdapter(
    var mList: ArrayList<WebPage>,
    var mContext: Context,
) : RecyclerView.Adapter<SuggestionViewHolder>() {

    private val searchIcon = mContext.drawable(R.drawable.ic_search)
    private val webPageIcon = mContext.drawable(R.drawable.ic_history)
    private val bookmarkIcon = mContext.drawable(R.drawable.ic_bookmark)

    /**
     * The listener that is fired when the insert button on a [SearchSuggestion] is clicked.
     */
    var onSuggestionInsertClick: ((WebPage) -> Unit)? = null

    private val onClick = View.OnClickListener {
        onSuggestionInsertClick?.invoke(it.tag as WebPage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SuggestionViewHolder {
        val holder =
            LayoutInflater.from(mContext).inflate(R.layout.two_line_autocomplete, parent, false)
        return SuggestionViewHolder(holder)
    }

    override fun getItemCount(): Int = mList.count()

    override fun onBindViewHolder(holder: SuggestionViewHolder, position: Int) {
        val webPage: WebPage = mList[position]

        holder.titleView.text = webPage.title
        holder.urlView.text = webPage.url

        val image = when (webPage) {
            is Bookmark -> bookmarkIcon
            is SearchSuggestion -> searchIcon
            is HistoryEntry -> webPageIcon
        }

        holder.imageView.setImageDrawable(image)

        holder.insertSuggestion.tag = webPage
        holder.insertSuggestion.setOnClickListener(onClick)
    }

}
