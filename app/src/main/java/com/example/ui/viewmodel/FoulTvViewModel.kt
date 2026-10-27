package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.BottomNavTab
import com.example.data.model.ChannelCategory
import com.example.data.model.EventBanner
import com.example.data.model.LeagueMatchesGroup
import com.example.data.model.LibraryFilterType
import com.example.data.model.LibraryMediaItem
import com.example.data.model.LiveChannel
import com.example.data.model.LiveEventMatch
import com.example.data.model.MatchDetailData
import com.example.data.model.MatchItem
import com.example.data.model.MediaDetailInfo
import com.example.data.model.PlaybackRequest
import com.example.data.model.SeriesEpisode
import com.example.data.model.XtreamAccount
import com.example.data.repository.FirebaseRepository
import com.example.data.repository.Scores365Repository
import com.example.data.repository.XtreamRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

data class DateOption(
    val labelAr: String,
    val apiDateString: String // DD/MM/YYYY
)

data class FoulTvUiState(
    val currentTab: BottomNavTab = BottomNavTab.HOME,

    // Home (Firebase) State
    val isLoadingHome: Boolean = true,
    val homeError: String? = null,
    val categories: List<ChannelCategory> = emptyList(),
    val selectedCategoryName: String = "الكل",
    val channels: List<LiveChannel> = emptyList(),
    val banners: List<EventBanner> = emptyList(),
    val liveEvents: List<LiveEventMatch> = emptyList(),
    val xtreamAccounts: List<XtreamAccount> = emptyList(),
    val telegramUrl: String = FirebaseRepository.DEFAULT_TELEGRAM_URL,

    // Library (Xtream) State
    val isLoadingLibrary: Boolean = false,
    val libraryError: String? = null,
    val libraryMainFilter: LibraryFilterType = LibraryFilterType.ALL,
    val libraryDisplayedItems: List<LibraryMediaItem> = emptyList(),
    val libraryVisibleCount: Int = XtreamRepository.PAGE_SIZE, // 72 items initially (60-80 range)
    val libraryHasMore: Boolean = false,

    // Library Search Page State
    val isLibrarySearchOpen: Boolean = false,
    val searchQuery: String = "",
    val searchFilter: LibraryFilterType = LibraryFilterType.ALL,
    val searchResults: List<LibraryMediaItem> = emptyList(),
    val isSearching: Boolean = false,

    // Library Media Detail (Movie / Series) State
    val selectedMediaItem: LibraryMediaItem? = null,
    val isLoadingMediaDetail: Boolean = false,
    val mediaDetailError: String? = null,
    val mediaDetail: MediaDetailInfo? = null,
    val selectedSeasonNumber: Int = 1,

    // Active Video Playback State (Library Dedicated Player or Live Channel Player)
    val activePlayback: PlaybackRequest? = null,

    // Matches (365Scores) State
    val dateOptions: List<DateOption> = emptyList(),
    val selectedDateString: String = "",
    val isLoadingMatches: Boolean = false,
    val matchesError: String? = null,
    val leagueGroups: List<LeagueMatchesGroup> = emptyList(),

    // Match Detail Page State
    val selectedMatch: MatchItem? = null,
    val isLoadingMatchDetail: Boolean = false,
    val matchDetailError: String? = null,
    val matchDetail: MatchDetailData? = null
) {
    val filteredHomeChannels: List<LiveChannel>
        get() = if (selectedCategoryName == "الكل" || selectedCategoryName.isBlank()) {
            channels
        } else {
            channels.filter { it.category.equals(selectedCategoryName, ignoreCase = true) }
        }
}

class FoulTvViewModel(
    private val firebaseRepository: FirebaseRepository = FirebaseRepository(),
    private val xtreamRepository: XtreamRepository = XtreamRepository(),
    private val scoresRepository: Scores365Repository = Scores365Repository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(FoulTvUiState())
    val uiState: StateFlow<FoulTvUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        val dates = buildDateOptions()
        val defaultDate = dates.firstOrNull { it.labelAr == "اليوم" }?.apiDateString
            ?: dates.first().apiDateString
        _uiState.update {
            it.copy(
                dateOptions = dates,
                selectedDateString = defaultDate
            )
        }
        loadHomeData(forceRefresh = false)
        loadMatchesForDate(defaultDate)
    }

    private fun buildDateOptions(): List<DateOption> {
        val baghdadTz = TimeZone.getTimeZone("Asia/Baghdad")
        val apiFmt = SimpleDateFormat("dd/MM/yyyy", Locale.US).apply { timeZone = baghdadTz }
        val dayLabelFmt = SimpleDateFormat("EEEE dd/MM", Locale("ar")).apply { timeZone = baghdadTz }

        val list = mutableListOf<DateOption>()
        for (offset in -2..2) {
            val cal = Calendar.getInstance(baghdadTz)
            cal.add(Calendar.DAY_OF_YEAR, offset)
            val dateStr = apiFmt.format(cal.time)
            val label = when (offset) {
                -1 -> "أمس"
                0 -> "اليوم"
                1 -> "غداً"
                else -> dayLabelFmt.format(cal.time)
            }
            list.add(DateOption(labelAr = label, apiDateString = dateStr))
        }
        // Also ensure 21/10/2026 (from the prompt's URL) is available as a quick chip if not already in the 5-day window
        if (list.none { it.apiDateString == "21/10/2026" }) {
            list.add(0, DateOption(labelAr = "الأربعاء 21/10", apiDateString = "21/10/2026"))
        }
        return list
    }

    fun selectTab(tab: BottomNavTab) {
        _uiState.update { it.copy(currentTab = tab) }
        if (tab == BottomNavTab.LIBRARY && _uiState.value.libraryDisplayedItems.isEmpty() && !_uiState.value.isLoadingLibrary) {
            loadLibraryInitial()
        }
    }

    // --- Home & Firebase Channels ---

    fun loadHomeData(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingHome = true, homeError = null) }
            val result = firebaseRepository.getFirebaseData(forceRefresh = forceRefresh)
            result.fold(
                onSuccess = { payload ->
                    _uiState.update { state ->
                        state.copy(
                            isLoadingHome = false,
                            homeError = null,
                            categories = payload.categories,
                            channels = payload.channels,
                            banners = payload.banners,
                            liveEvents = payload.liveMatches,
                            xtreamAccounts = payload.xtreamAccounts,
                            telegramUrl = payload.telegramUrl
                        )
                    }
                    // Pre-warm Library initial 72 items in background so Library opens instantaneously
                    if (payload.xtreamAccounts.isNotEmpty() && _uiState.value.libraryDisplayedItems.isEmpty()) {
                        loadLibraryInitial()
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoadingHome = false,
                            homeError = err.message ?: "تعذر الاتصال بقاعدة البيانات"
                        )
                    }
                }
            )
        }
    }

    fun selectHomeCategory(categoryName: String) {
        _uiState.update { it.copy(selectedCategoryName = categoryName) }
    }

    fun playLiveChannel(channel: LiveChannel) {
        if (channel.servers.isEmpty()) return
        _uiState.update {
            it.copy(
                activePlayback = PlaybackRequest(
                    title = channel.name,
                    subtitle = channel.category,
                    isLiveChannel = true,
                    candidateUrls = channel.servers.map { s -> s.streamUrl },
                    servers = channel.servers
                )
            )
        }
    }

    fun onBannerClick(banner: EventBanner) {
        val state = _uiState.value
        val matchedChannel = state.channels.firstOrNull { ch ->
            ch.id == banner.channelId ||
                banner.channelIds.contains(ch.id) ||
                ch.name.equals(banner.channelName, ignoreCase = true)
        }
        if (matchedChannel != null) {
            playLiveChannel(matchedChannel)
        }
    }

    // --- Library (Xtream VOD & Series) ---

    fun loadLibraryInitial() {
        val accounts = _uiState.value.xtreamAccounts
        if (accounts.isEmpty()) {
            viewModelScope.launch {
                val fbRes = firebaseRepository.getFirebaseData(forceRefresh = false)
                val loadedAccounts = fbRes.getOrNull()?.xtreamAccounts.orEmpty()
                if (loadedAccounts.isNotEmpty()) {
                    _uiState.update { it.copy(xtreamAccounts = loadedAccounts) }
                    fetchLibraryFromAccounts(loadedAccounts)
                } else {
                    _uiState.update {
                        it.copy(
                            isLoadingLibrary = false,
                            libraryError = "لا توجد حسابات مكتبة متاحة حالياً في قاعدة البيانات"
                        )
                    }
                }
            }
        } else {
            fetchLibraryFromAccounts(accounts)
        }
    }

    private fun fetchLibraryFromAccounts(accounts: List<XtreamAccount>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingLibrary = true, libraryError = null) }
            val res = xtreamRepository.ensureInitialCatalogLoaded(
                accounts = accounts,
                onBackgroundCatalogUpdated = {
                    refreshLibraryPaginationState()
                    if (_uiState.value.isLibrarySearchOpen) {
                        executeLibrarySearch(_uiState.value.searchQuery, _uiState.value.searchFilter)
                    }
                }
            )
            res.fold(
                onSuccess = {
                    _uiState.update { state -> state.copy(isLoadingLibrary = false, libraryError = null) }
                    refreshLibraryPaginationState()
                },
                onFailure = { err ->
                    _uiState.update { state ->
                        state.copy(
                            isLoadingLibrary = false,
                            libraryError = err.message ?: "تعذر جلب الأفلام والمسلسلات"
                        )
                    }
                }
            )
        }
    }

    fun selectLibraryMainFilter(filter: LibraryFilterType) {
        _uiState.update {
            it.copy(
                libraryMainFilter = filter,
                libraryVisibleCount = XtreamRepository.PAGE_SIZE
            )
        }
        refreshLibraryPaginationState()
    }

    fun loadMoreLibraryItems() {
        _uiState.update { state ->
            state.copy(libraryVisibleCount = state.libraryVisibleCount + XtreamRepository.PAGE_SIZE)
        }
        refreshLibraryPaginationState()
    }

    private fun refreshLibraryPaginationState() {
        val state = _uiState.value
        val fullList = xtreamRepository.getCatalogItems(state.libraryMainFilter)
        val visible = fullList.take(state.libraryVisibleCount)
        _uiState.update {
            it.copy(
                libraryDisplayedItems = visible,
                libraryHasMore = fullList.size > visible.size
            )
        }
    }

    // --- Library Dedicated Search Page ---

    fun openLibrarySearch() {
        val initialResults = xtreamRepository.searchCatalog("", LibraryFilterType.ALL, limit = 72)
        _uiState.update {
            it.copy(
                isLibrarySearchOpen = true,
                searchQuery = "",
                searchFilter = LibraryFilterType.ALL,
                searchResults = initialResults,
                isSearching = false
            )
        }
    }

    fun closeLibrarySearch() {
        _uiState.update {
            it.copy(
                isLibrarySearchOpen = false,
                searchQuery = "",
                isSearching = false
            )
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        executeLibrarySearch(query, _uiState.value.searchFilter)
    }

    fun updateSearchFilter(filter: LibraryFilterType) {
        _uiState.update { it.copy(searchFilter = filter) }
        executeLibrarySearch(_uiState.value.searchQuery, filter)
    }

    private fun executeLibrarySearch(query: String, filter: LibraryFilterType) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }
            if (query.isNotBlank()) {
                delay(60) // Ultra-fast debounce for smooth typing
            }
            val results = xtreamRepository.searchCatalog(query, filter, limit = 180)
            _uiState.update {
                it.copy(
                    searchResults = results,
                    isSearching = false
                )
            }
        }
    }

    // --- Library Media Detail (Movie / Series Seasons & Episodes) ---

    fun openMediaDetail(item: LibraryMediaItem) {
        _uiState.update {
            it.copy(
                selectedMediaItem = item,
                isLoadingMediaDetail = true,
                mediaDetailError = null,
                mediaDetail = null,
                selectedSeasonNumber = 1
            )
        }
        viewModelScope.launch {
            val res = xtreamRepository.getMediaDetails(item)
            res.fold(
                onSuccess = { detail ->
                    val firstSeason = detail.seasons.firstOrNull()?.seasonNumber
                        ?: detail.episodesBySeason.keys.firstOrNull()
                        ?: 1
                    _uiState.update {
                        it.copy(
                            isLoadingMediaDetail = false,
                            mediaDetail = detail,
                            selectedSeasonNumber = firstSeason
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoadingMediaDetail = false,
                            mediaDetailError = err.message ?: "تعذر جلب تفاصيل العمل"
                        )
                    }
                }
            )
        }
    }

    fun selectSeason(seasonNum: Int) {
        _uiState.update { it.copy(selectedSeasonNumber = seasonNum) }
    }

    fun closeMediaDetail() {
        _uiState.update {
            it.copy(
                selectedMediaItem = null,
                mediaDetail = null,
                mediaDetailError = null
            )
        }
    }

    /**
     * Launches the Dedicated Library Player for a Movie, automatically testing
     * http://.../id.mp4 -> .mkv -> .m3u8 in Landscape mode!
     */
    fun playLibraryMovie(item: LibraryMediaItem) {
        val candidates = xtreamRepository.buildLibraryStreamCandidates(
            account = item.account,
            streamId = item.id,
            isSeries = false
        )
        _uiState.update {
            it.copy(
                activePlayback = PlaybackRequest(
                    title = item.name,
                    subtitle = if (item.releaseYear.isNotEmpty()) "فيلم • ${item.releaseYear}" else "فيلم",
                    isLiveChannel = false,
                    candidateUrls = candidates
                )
            )
        }
    }

    /**
     * Launches the Dedicated Library Player for a Series Episode, automatically testing
     * http://.../episodeId.mp4 -> .mkv -> .m3u8 in Landscape mode!
     */
    fun playLibraryEpisode(seriesItem: LibraryMediaItem, episode: SeriesEpisode) {
        val candidates = xtreamRepository.buildLibraryStreamCandidates(
            account = seriesItem.account,
            streamId = episode.id,
            isSeries = true
        )
        _uiState.update {
            it.copy(
                activePlayback = PlaybackRequest(
                    title = seriesItem.name,
                    subtitle = "الموسم ${episode.seasonNum} • الحلقة ${episode.episodeNum} - ${episode.title}",
                    isLiveChannel = false,
                    candidateUrls = candidates
                )
            )
        }
    }

    fun closePlayer() {
        _uiState.update { it.copy(activePlayback = null) }
    }

    // --- Matches Schedule & Details (365Scores) ---

    fun loadMatchesForDate(dateString: String) {
        _uiState.update {
            it.copy(
                selectedDateString = dateString,
                isLoadingMatches = true,
                matchesError = null
            )
        }
        viewModelScope.launch {
            val res = scoresRepository.getMatchesByDate(dateString)
            res.fold(
                onSuccess = { groups ->
                    _uiState.update {
                        it.copy(
                            isLoadingMatches = false,
                            leagueGroups = groups,
                            matchesError = null
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoadingMatches = false,
                            matchesError = err.message ?: "تعذر جلب جدول المباريات"
                        )
                    }
                }
            )
        }
    }

    fun openMatchDetail(match: MatchItem) {
        _uiState.update {
            it.copy(
                selectedMatch = match,
                isLoadingMatchDetail = true,
                matchDetailError = null,
                matchDetail = null
            )
        }
        viewModelScope.launch {
            val res = scoresRepository.getMatchFullDetails(match)
            res.fold(
                onSuccess = { detail ->
                    _uiState.update {
                        it.copy(
                            isLoadingMatchDetail = false,
                            matchDetail = detail,
                            matchDetailError = null
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoadingMatchDetail = false,
                            matchDetailError = err.message ?: "تعذر جلب تفاصيل المباراة"
                        )
                    }
                }
            )
        }
    }

    fun closeMatchDetail() {
        _uiState.update {
            it.copy(
                selectedMatch = null,
                matchDetail = null,
                matchDetailError = null
            )
        }
    }
}
