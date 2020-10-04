package com.github.k1rakishou.chan.features.search

import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.core.base.BasePresenter
import com.github.k1rakishou.chan.core.base.SuspendDebouncer
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.sites.search.SiteGlobalSearchType
import com.github.k1rakishou.chan.features.search.data.GlobalSearchControllerState
import com.github.k1rakishou.chan.features.search.data.GlobalSearchControllerStateData
import com.github.k1rakishou.chan.features.search.data.SelectedSite
import com.github.k1rakishou.chan.features.search.data.SitesWithSearch
import com.github.k1rakishou.chan.utils.Logger
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.processors.BehaviorProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

internal class GlobalSearchPresenter : BasePresenter<GlobalSearchView>() {

  @Inject
  lateinit var siteManager: SiteManager

  private val globalSearchControllerStateSubject =
    BehaviorProcessor.createDefault<GlobalSearchControllerState>(GlobalSearchControllerState.Loading)
  private val searchResultsStateStorage = SearchResultsStateStorage

  private val queryEnterDebouncer = SuspendDebouncer(scope)

  override fun onCreate(view: GlobalSearchView) {
    super.onCreate(view)

    Chan.inject(this)

    scope.launch {
      if (searchResultsStateStorage.searchInputState != null) {
        if (tryRestorePrevState()) {
          return@launch
        }

        // fallthrough
      }

      siteManager.awaitUntilInitialized()
      loadDefaultSearchState()
    }
  }

  private fun tryRestorePrevState(): Boolean {
    val searchInputState = searchResultsStateStorage.searchInputState!!

    val isQueryOk = if (searchInputState is GlobalSearchControllerStateData.SearchQueryEntered) {
      searchInputState.query.length >= MIN_SEARCH_QUERY_LENGTH
    } else {
      true
    }

    if (isQueryOk) {
      setState(GlobalSearchControllerState.Data(searchInputState))

      withViewNormal {
        if (searchInputState is GlobalSearchControllerStateData.SearchQueryEntered) {
          restoreSearchResultsController(
            searchInputState.sitesWithSearch.selectedSite.siteDescriptor,
            searchInputState.query
          )
        }
      }

      return true
    }

    return false
  }

  fun listenForStateChanges(): Flowable<GlobalSearchControllerState> {
    return globalSearchControllerStateSubject
      .onBackpressureLatest()
      .observeOn(AndroidSchedulers.mainThread())
      .doOnError { error ->
        Logger.e(TAG, "Unknown error subscribed to globalSearchControllerStateSubject.listenForStateChanges()", error)
      }
      .onErrorReturn { error -> GlobalSearchControllerState.Error(error.errorMessageOrClassName()) }
      .hide()
  }

  fun resetSavedState() {
    searchResultsStateStorage.resetSearchInputState()
  }
  fun resetSearchResultsSavedState() {
    searchResultsStateStorage.resetSearchResultState()
  }

  // TODO(KurobaEx):
//  fun reloadWithSelection(siteDescriptor: SiteDescriptor, sitesWithSearch: SitesWithSearch) {
//    val selectedSiteDescriptor = sitesWithSearch.selectedSite.siteDescriptor
//    if (selectedSiteDescriptor == siteDescriptor) {
//      return
//    }
//
//    val site = siteManager.bySiteDescriptor(selectedSiteDescriptor)
//      ?: return
//
//    val siteIcon = site.icon().url.toString()
//    val searchType = site.siteGlobalSearchType()
//
//    val dataState = GlobalSearchControllerStateData.SitesSupportingSearchLoaded(
//      sitesWithSearch.copy(selectedSite = SelectedSite(selectedSiteDescriptor, siteIcon, searchType))
//    )
//
//    setState(GlobalSearchControllerState.Data(dataState))
//  }

  fun reloadWithSearchQuery(query: String, sitesWithSearch: SitesWithSearch) {
    val prevDataState = (globalSearchControllerStateSubject.value as? GlobalSearchControllerState.Data)?.data
    if (prevDataState is GlobalSearchControllerStateData.SearchQueryEntered) {
      if (prevDataState.query == query) {
        return
      }
    }

    queryEnterDebouncer.post(DEBOUNCE_TIMEOUT_MS) {
      val dataState = GlobalSearchControllerStateData.SearchQueryEntered(
        sitesWithSearch,
        query
      )

      setState(GlobalSearchControllerState.Data(dataState))
      searchResultsStateStorage.updateSearchInputState(dataState)

      withView { withContext(Dispatchers.Main) { setNeedSetInitialQueryFlag() } }
    }
  }

  private fun loadDefaultSearchState() {
    val sitesSupportingSearch = mutableListOf<SiteDescriptor>()

    siteManager.viewActiveSitesOrdered { chanSiteData, site ->
      if (site.siteGlobalSearchType() != SiteGlobalSearchType.SearchNotSupported) {
        sitesSupportingSearch += chanSiteData.siteDescriptor
      }

      return@viewActiveSitesOrdered true
    }

    if (sitesSupportingSearch.isEmpty()) {
      setState(GlobalSearchControllerState.Empty)
      return
    }

    val selectedSiteDescriptor = sitesSupportingSearch.first()

    val site = siteManager.bySiteDescriptor(selectedSiteDescriptor)!!
    val siteIcon = site.icon().url.toString()
    val searchType = site.siteGlobalSearchType()

    val dataState = GlobalSearchControllerStateData.SitesSupportingSearchLoaded(
      SitesWithSearch(
        sitesSupportingSearch,
        SelectedSite(selectedSiteDescriptor, siteIcon, searchType)
      )
    )

    setState(GlobalSearchControllerState.Data(dataState))
  }

  private fun setState(state: GlobalSearchControllerState) {
    globalSearchControllerStateSubject.onNext(state)
  }

  fun onSearchButtonClicked(selectedSite: SelectedSite, query: String) {
    withViewNormal { openSearchResultsController(selectedSite.siteDescriptor, query) }
  }

  companion object {
    private const val TAG = "GlobalSearchPresenter"

    const val MIN_SEARCH_QUERY_LENGTH = 2
    private const val DEBOUNCE_TIMEOUT_MS = 150L
  }
}