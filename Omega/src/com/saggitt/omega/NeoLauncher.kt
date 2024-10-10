/*
 * This file is part of Neo Launcher
 * Copyright (c) 2021   Neo Launcher Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.saggitt.omega

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentSender
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.getSystemService
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.android.launcher3.AppFilter
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherRootView
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.pageindicators.WorkspacePageIndicator
import com.android.launcher3.pm.UserCache
import com.android.launcher3.popup.SystemShortcut
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import com.android.launcher3.util.TouchController
import com.android.launcher3.views.OptionsPopupView
import com.android.systemui.plugins.shared.LauncherOverlayManager
import com.saggitt.omega.gestures.GestureController
import com.saggitt.omega.gestures.VerticalSwipeGestureController
import com.saggitt.omega.popup.OmegaShortcuts
import com.saggitt.omega.preferences.NeoPrefs
import com.saggitt.omega.preferences.PreferencesChangeCallback
import com.saggitt.omega.searchsuggestion.AndroidLogger
import com.saggitt.omega.searchsuggestion.RVSuggestionAdapter
import com.saggitt.omega.searchsuggestion.RequestFactory
import com.saggitt.omega.searchsuggestion.SearchEngineProvider
import com.saggitt.omega.searchsuggestion.SearchListener
import com.saggitt.omega.searchsuggestion.SearchView
import com.saggitt.omega.searchsuggestion.StyleRemovingTextWatcher
import com.saggitt.omega.searchsuggestion.SuggestionsAdapter
import com.saggitt.omega.searchsuggestion.WebPage
import com.saggitt.omega.theme.ThemeManager
import com.saggitt.omega.theme.ThemeOverride
import com.saggitt.omega.util.Config
import com.saggitt.omega.util.hasStoragePermission
import com.saggitt.omega.views.OmegaBackgroundView
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import okhttp3.CacheControl
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

import org.koin.dsl.module
import org.koin.java.KoinJavaComponent.inject
import java.util.stream.Stream

class NeoLauncher : Launcher(), LifecycleOwner, SavedStateRegistryOwner,
    ActivityResultRegistryOwner, ThemeManager.ThemeableActivity {

    override var currentTheme = 0
    override var currentAccent = 0
    private lateinit var themeOverride: ThemeOverride
    private val themeSet: ThemeOverride.ThemeSet get() = ThemeOverride.Settings()

    val prefs: NeoPrefs by lazy { Utilities.getNeoPrefs(this) }
    val gestureController by lazy { GestureController(this) }
    val background by lazy { findViewById<OmegaBackgroundView>(R.id.omega_background)!! }
    val dummyView by lazy { findViewById<View>(R.id.dummy_view)!! }
    private val rlYahooSearch by lazy { findViewById<RelativeLayout>(R.id.rlYahooSearch)!! }
    private val etYahooSearch by lazy { findViewById<EditText>(R.id.etYahooSearch)!! }
    private val launcherRoot by lazy { findViewById<LauncherRootView>(R.id.launcher)!! }
    private val btnCrossField by lazy { findViewById<ImageView>(R.id.btnCrossField)!! }
    private val btnSearch by lazy { findViewById<Button>(R.id.btnSearch)!! }
    private val rlSuggestion by lazy { findViewById<RecyclerView>(R.id.rlSuggestion)!! }
    private val search by lazy { findViewById<SearchView>(R.id.search)!! }
    private val llExtras by lazy { findViewById<LinearLayout>(R.id.llExtras)!! }
    private var mList: ArrayList<WebPage> = arrayListOf()
    private val txtSearch: String
        get() {
            return etYahooSearch.text?.toString()?.trim() ?: ""
        }
    val optionsView by lazy { findViewById<OptionsPopupView<Launcher>>(R.id.options_view)!! }
    private val prefCallback = PreferencesChangeCallback(this)

    //    showAllAppsFromIntent
    private val hiddenApps = ArrayList<AppInfo>()
    val allApps = ArrayList<AppInfo>()
    private var paused = false

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry
    private var mDisposable: Disposable? = null
    private var mRVSuggestionAdapter: RVSuggestionAdapter? = null
    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1
            && !this.hasStoragePermission
        ) Utilities.requestStoragePermission(this)

        savedStateRegistryController.performRestore(savedInstanceState)
        super.onCreate(savedInstanceState)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        prefs.registerCallback(prefCallback)

        MODEL_EXECUTOR.handler.postAtFrontOfQueue { loadHiddenApps(prefs.drawerHiddenAppSet.getValue()) }

        val coroutineScope = CoroutineScope(Dispatchers.IO)
        val config = Config(this)
        config.setAppLanguage(prefs.profileLanguage.getValue())
        mOverlayManager = defaultOverlay
        val camManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager?
        camManager?.registerTorchCallback(object : CameraManager.TorchCallback() {
            override fun onTorchModeUnavailable(cameraId: String) {
            }

            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                coroutineScope.launch {
                    if (cameraId == camManager.cameraIdList[0]) {
                        prefs.dashTorchState.setValue(enabled)
                    }
                }
            }
        }, null)

        themeOverride = ThemeOverride(themeSet, this)
        themeOverride.applyTheme(this)
        currentAccent = prefs.profileAccentColor.getColor()
        currentTheme = themeOverride.getTheme(this)
        theme.applyStyle(
            resources.getIdentifier(
                Integer.toHexString(currentAccent),
                "style",
                packageName
            ), true
        )
        etYahooSearch.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_SEARCH) {
                // Handle action when "Done" is pressed
                // Your code here
                if (txtSearch.isNotEmpty()) {
                    com.saggitt.omega.util.openURLInBrowser(
                        this,
                        "https://search.yahoo.com/search?q=$txtSearch",
                        rlYahooSearch.clipBounds,
                        null
                    )
                    etYahooSearch.clearFocus()
                    etYahooSearch.setText("")
                }
                true // Return true if you handled the action
            } else {
                false // Return false if you didn't handle the action
            }
        }

        btnCrossField.setOnClickListener {
            etYahooSearch.setText("")
            hideKeyboard()
        }
        btnSearch.setOnClickListener {
            hideKeyboard()
            com.saggitt.omega.util.openURLInBrowser(
                this,
                "https://search.yahoo.com/search?q=$txtSearch",
                rlYahooSearch.clipBounds,
                null
            )
            etYahooSearch.setText("")
            etYahooSearch.clearFocus()
            rlSuggestion.isVisible = false
        }

        // Assuming you're in an Activity or Fragment
        val context: Context = this // Use the current context
        val databaseScheduler = Schedulers.io() // Example for database operations
        val networkScheduler = Schedulers.io() // Example for network operations
        val mainScheduler = AndroidSchedulers.mainThread() // For main thread operations

// Create instances for the parameters of SearchEngineProvider
        val okHttpClient: Single<OkHttpClient> =
            Single.just(OkHttpClient()) // Replace with your OkHttpClient initialization
        val requestFactory = object : RequestFactory {
            override fun createSuggestionsRequest(httpUrl: HttpUrl, encoding: String): Request {
                return Request.Builder().url(httpUrl)
                    .addHeader("Accept-Charset", encoding)
                    .addHeader("Accept", "application/json")
                    .addHeader("Content-Type", "application/json")
                    .cacheControl(CacheControl.Builder().build())
                    .build()
            }
        }//RequestFactory() // Create an instance of RequestFactory
//        val application = context.applicationContext as Application // Get Application context
        val logger = AndroidLogger() // Create an instance of Logger
        val searchEngineProvider = SearchEngineProvider(
            okHttpClient,
            requestFactory,
            application,
            logger
        )
        val suggestionsAdapter = SuggestionsAdapter(
            context,
            databaseScheduler,
            networkScheduler,
            mainScheduler,
            searchEngineProvider
        )
        mRVSuggestionAdapter = RVSuggestionAdapter(mList, this)
        mRVSuggestionAdapter?.onSuggestionInsertClick = { itemWebPage ->
            Log.d(TAG,"$TAG ${itemWebPage.toString()}")
            etYahooSearch.setText(itemWebPage.title)
            etYahooSearch.selectAll()
            com.saggitt.omega.util.openURLInBrowser(
                this,
                "https://search.yahoo.com/search?q=${itemWebPage.url}",
                rlYahooSearch.clipBounds,
                null
            )
            etYahooSearch.clearFocus()
            hideKeyboard()
            rlSuggestion.isVisible = false
        }
        rlSuggestion.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        rlSuggestion.adapter = mRVSuggestionAdapter
        search.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            search.clearFocus()
            val item = suggestionsAdapter.getItem(position) as WebPage
            com.saggitt.omega.util.openURLInBrowser(
                this,
                item.url,
                rlYahooSearch.clipBounds,
                null
            )
            hideKeyboard()
        }
        val inputMethodManager = application.getSystemService<InputMethodManager>()!!
        //presenter.onSearch(search.text.toString())
        search.setAdapter(suggestionsAdapter)
        val searchListener = SearchListener(
            onConfirm = { },
            inputMethodManager = inputMethodManager
        )
        search.setOnEditorActionListener(searchListener)
        search.setOnKeyListener(searchListener)
        search.addTextChangedListener(StyleRemovingTextWatcher())
        search.setOnFocusChangeListener { _, hasFocus ->
//            presenter.onSearchFocusChanged(hasFocus)
            search.selectAll()
        }
        etYahooSearch.addTextChangedListener {
            llExtras.isVisible = txtSearch.isNotEmpty()
            rlSuggestion.isVisible = txtSearch.isNotEmpty()
            if (llExtras.isVisible) {
//                suggestionsAdapter.cancelAllRequests()
//                mDisposable?.dispose()
                mDisposable = suggestionsAdapter.getSearchResults(txtSearch) {
                    mList.clear()
                    mList.addAll(it.take(5))
                    mRVSuggestionAdapter?.notifyDataSetChanged()
                }
            } else {
//                suggestionsAdapter.cancelAllRequests()
                mDisposable?.dispose()
            }
        }

    }

    override fun onCreateView(
        parent: View?,
        name: String,
        context: Context,
        attrs: AttributeSet,
    ): View? {
        if (prefs.dockDotsPageIndicator.getValue() && WorkspacePageIndicator::class.java.name == name) {
            return LayoutInflater.from(context).inflate(
                R.layout.page_indicator_dots,
                parent as ViewGroup, false
            )
        }
        return super.onCreateView(parent, name, context, attrs)
    }

    override fun onThemeChanged(forceUpdate: Boolean) = recreate()

    override val activityResultRegistry: ActivityResultRegistry
        get() = object : ActivityResultRegistry() {
            override fun <I : Any?, O : Any?> onLaunch(
                requestCode: Int,
                contract: ActivityResultContract<I, O>,
                input: I,
                options: ActivityOptionsCompat?,
            ) {
                val activity = this@NeoLauncher

                // Immediate result path
                val synchronousResult = contract.getSynchronousResult(activity, input)
                if (synchronousResult != null) {
                    Handler(Looper.getMainLooper()).post {
                        dispatchResult(
                            requestCode,
                            synchronousResult.value
                        )
                    }
                    return
                }

                // Start activity path
                val intent = contract.createIntent(activity, input)
                var optionsBundle: Bundle? = null
                // If there are any extras, we should defensively set the classLoader
                if (intent.extras != null && intent.extras!!.classLoader == null) {
                    intent.setExtrasClassLoader(activity.classLoader)
                }
                if (intent.hasExtra(StartActivityForResult.EXTRA_ACTIVITY_OPTIONS_BUNDLE)) {
                    optionsBundle =
                        intent.getBundleExtra(StartActivityForResult.EXTRA_ACTIVITY_OPTIONS_BUNDLE)
                    intent.removeExtra(StartActivityForResult.EXTRA_ACTIVITY_OPTIONS_BUNDLE)
                } else if (options != null) {
                    optionsBundle = options.toBundle()
                }
                if (RequestMultiplePermissions.ACTION_REQUEST_PERMISSIONS == intent.action) {
                    // requestPermissions path
                    var permissions =
                        intent.getStringArrayExtra(RequestMultiplePermissions.EXTRA_PERMISSIONS)
                    if (permissions == null) {
                        permissions = arrayOfNulls(0)
                    }
                    ActivityCompat.requestPermissions(activity, permissions, requestCode)
                } else if (StartIntentSenderForResult.ACTION_INTENT_SENDER_REQUEST == intent.action) {
                    val request: IntentSenderRequest =
                        intent.getParcelableExtra(StartIntentSenderForResult.EXTRA_INTENT_SENDER_REQUEST)!!
                    try {
                        // startIntentSenderForResult path
                        ActivityCompat.startIntentSenderForResult(
                            activity, request.intentSender,
                            requestCode, request.fillInIntent, request.flagsMask,
                            request.flagsValues, 0, optionsBundle
                        )
                    } catch (e: IntentSender.SendIntentException) {
                        Handler(Looper.getMainLooper()).post {
                            dispatchResult(
                                requestCode, RESULT_CANCELED,
                                Intent()
                                    .setAction(StartIntentSenderForResult.ACTION_INTENT_SENDER_REQUEST)
                                    .putExtra(
                                        StartIntentSenderForResult.EXTRA_SEND_INTENT_EXCEPTION,
                                        e
                                    )
                            )
                        }
                    }
                } else {
                    // startActivityForResult path
                    ActivityCompat.startActivityForResult(
                        activity,
                        intent,
                        requestCode,
                        optionsBundle
                    )
                }
            }

        }

    private fun loadHiddenApps(hiddenAppsSet: Set<String>) {
        val mContext = this
        val appFilter = AppFilter(mContext)
        CoroutineScope(Dispatchers.IO).launch {
            for (user in UserCache.INSTANCE[mContext].userProfiles) {
                val duplicatePreventionCache: MutableList<ComponentName> = ArrayList()
                for (info in getSystemService(LauncherApps::class.java)
                    .getActivityList(null, user)) {
                    val key = ComponentKey(info.componentName, info.user)
                    if (hiddenAppsSet.contains(key.toString())) {
                        val appInfo = AppInfo(info, info.user, false)
                        appInfo.title = info.label
                        hiddenApps.add(appInfo)
                    }
                    if (prefs.searchHiddenApps.getValue()) {
                        if (!appFilter.shouldShowApp(info.componentName)) {
                            continue
                        }
                        if (!duplicatePreventionCache.contains(info.componentName)) {
                            duplicatePreventionCache.add(info.componentName)
                            val appInfo = AppInfo(mContext, info, user)
                            appInfo.title = info.label
                            allApps.add(appInfo)
                        }
                    }
                }
            }
        }
    }

    override fun getSupportedShortcuts(): Stream<SystemShortcut.Factory<*>> {
        return Stream.concat(
            super.getSupportedShortcuts(),
            Stream.of(
                OmegaShortcuts.CUSTOMIZE,
                OmegaShortcuts.APP_REMOVE,
                OmegaShortcuts.APP_UNINSTALL
            )
        )
    }

    override fun getDefaultOverlay(): LauncherOverlayManager {
        if (mOverlayManager == null) {
            mOverlayManager = OverlayCallbackImpl(this)
        }
        return mOverlayManager
    }

    override fun onUiChangedWhileSleeping() {
        if (Utilities.ATLEAST_S) {
            super.onUiChangedWhileSleeping()
        }
    }

//    private val resultLauncher =
//        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
//            if (result.resultCode == Activity.RESULT_OK) {
//                resetLauncherViaFakeActivity()
//            }
//        }

    override fun onStart() {
        super.onStart()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        showSertDefaultlauncher()
    }

    private fun showSertDefaultlauncher() {
        if (isDefaultLauncher() || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            resetLauncherViaFakeActivity()
        } else {
//            showLauncherSelector(resultLauncher)
            showLauncherSelector()
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        restartIfPending()
        llExtras.isVisible = txtSearch.isNotEmpty()
        rlSuggestion.isVisible = false
        dragLayer.viewTreeObserver.addOnDrawListener(object : ViewTreeObserver.OnDrawListener {
            private var handled = false

            override fun onDraw() {
                if (handled) {
                    return
                }
                handled = true

                dragLayer.post {
                    dragLayer.viewTreeObserver.removeOnDrawListener(this)
                }
            }
        })
        paused = false
    }

    override fun onPause() {
        super.onPause()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        paused = true
    }

    override fun onStop() {
        super.onStop()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        prefs.unregisterCallback()
    }

    override fun onSaveInstanceState(outState: Bundle, outPersistentState: PersistableBundle) {
        super.onSaveInstanceState(outState, outPersistentState)
        savedStateRegistryController.performSave(outState)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 2211221 && resultCode == Activity.RESULT_OK) {
            resetLauncherViaFakeActivity()
        } else {
            if (activityResultRegistry.dispatchResult(requestCode, resultCode, data)) {
                mPendingActivityRequestCode = -1
            } else {
                super.onActivityResult(requestCode, resultCode, data)
            }
        }
    }

    fun recreateIfNotScheduled() {
        if (sRestartFlags == 0) {
            recreate()
        }
    }

    fun scheduleRecreate() {
        if (paused) {
            sRestartFlags = FLAG_RECREATE
        }
    }

    private fun restartIfPending() {
        when {
            sRestartFlags and FLAG_RESTART != 0  -> neoApp.restart(false)
            sRestartFlags and FLAG_RECREATE != 0 -> {
                sRestartFlags = 0
                recreate()
            }
        }
    }

    fun scheduleRestart() {
        if (paused) {
            sRestartFlags = FLAG_RESTART
        } else {
            Utilities.restartLauncher(this)
        }
    }

    inline fun prepareDummyView(view: View, crossinline callback: (View) -> Unit) {
        val rect = Rect()
        dragLayer.getViewRectRelativeToSelf(view, rect)
        prepareDummyView(rect.left, rect.top, rect.right, rect.bottom, callback)
    }

    inline fun prepareDummyView(left: Int, top: Int, crossinline callback: (View) -> Unit) {
        val size = resources.getDimensionPixelSize(R.dimen.options_menu_thumb_size)
        val halfSize = size / 2
        prepareDummyView(left - halfSize, top - halfSize, left + halfSize, top + halfSize, callback)
    }

    inline fun prepareDummyView(
        left: Int, top: Int, right: Int, bottom: Int,
        crossinline callback: (View) -> Unit,
    ) {
        (dummyView.layoutParams as ViewGroup.MarginLayoutParams).let {
            it.width = right - left
            it.height = bottom - top
            it.leftMargin = left
            it.topMargin = top
        }
        dummyView.requestLayout()
        dummyView.post { callback(dummyView) }
    }

    override fun createTouchControllers(): Array<TouchController> {
        val list = ArrayList<TouchController>()
        list.add(dragController)
        list.add(VerticalSwipeGestureController(this))

        return list.toTypedArray() + super.createTouchControllers()
    }

    fun getViewBounds(v: View): Rect {
        val pos = IntArray(2)
        v.getLocationOnScreen(pos)
        return Rect(pos[0], pos[1], pos[0] + v.width, pos[1] + v.height)
    }

    companion object {

        @JvmStatic
        fun getLauncher(context: Context): NeoLauncher {
            return context as? NeoLauncher
                ?: (context as ContextWrapper).baseContext as? NeoLauncher
                ?: LauncherAppState.getInstance(context).launcher as NeoLauncher
        }

        private const val FLAG_RECREATE = 1 shl 0
        private const val FLAG_RESTART = 1 shl 1

        var sRestartFlags = 0
    }
}

val Context.nLauncher: NeoLauncher by inject(NeoLauncher::class.java)

val neoModule = module {
    single { NeoLauncher.getLauncher(get()) }
}