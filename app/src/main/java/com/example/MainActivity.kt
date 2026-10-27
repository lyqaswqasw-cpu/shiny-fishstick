package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.BottomNavTab
import com.example.ui.components.OrderedColorfulDivider
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.LibraryVideoPlayerScreen
import com.example.ui.screens.MatchesScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.FoulBlackBg
import com.example.ui.theme.FoulBottomBarBlack
import com.example.ui.theme.FoulPurpleDeep
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.FoulTvViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                FoulTvApp()
            }
        }
    }
}

private data class NavItemSpec(
    val tab: BottomNavTab,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val NAV_ITEMS = listOf(
    NavItemSpec(
        tab = BottomNavTab.HOME,
        label = "الرئيسية",
        selectedIcon = Icons.Filled.LiveTv,
        unselectedIcon = Icons.Outlined.LiveTv,
        testTag = "nav_tab_home"
    ),
    NavItemSpec(
        tab = BottomNavTab.LIBRARY,
        label = "المكتبة",
        selectedIcon = Icons.Filled.VideoLibrary,
        unselectedIcon = Icons.Outlined.VideoLibrary,
        testTag = "nav_tab_library"
    ),
    NavItemSpec(
        tab = BottomNavTab.MATCHES,
        label = "جدول المباريات",
        selectedIcon = Icons.Filled.SportsSoccer,
        unselectedIcon = Icons.Outlined.SportsSoccer,
        testTag = "nav_tab_matches"
    ),
    NavItemSpec(
        tab = BottomNavTab.SETTINGS,
        label = "الإعدادات",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        testTag = "nav_tab_settings"
    )
)

@Composable
fun FoulTvApp(
    viewModel: FoulTvViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Fullscreen Landscape Player overrides Scaffold when active
    val activePlayback = uiState.activePlayback
    if (activePlayback != null) {
        LibraryVideoPlayerScreen(
            playbackRequest = activePlayback,
            onBack = { viewModel.closePlayer() }
        )
        return
    }

    // Back handler when on secondary tabs returns to Home tab
    BackHandler(
        enabled = uiState.currentTab != BottomNavTab.HOME &&
            uiState.selectedMediaItem == null &&
            !uiState.isLibrarySearchOpen &&
            uiState.selectedMatch == null
    ) {
        viewModel.selectTab(BottomNavTab.HOME)
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars),
        containerColor = FoulBlackBg,
        bottomBar = {
            FoulCompactBottomBar(
                currentTab = uiState.currentTab,
                onSelectTab = viewModel::selectTab
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (uiState.currentTab) {
                BottomNavTab.HOME -> {
                    HomeScreen(
                        isLoading = uiState.isLoadingHome,
                        error = uiState.homeError,
                        categories = uiState.categories,
                        selectedCategory = uiState.selectedCategoryName,
                        channels = uiState.filteredHomeChannels,
                        banners = uiState.banners,
                        onSelectCategory = viewModel::selectHomeCategory,
                        onChannelClick = viewModel::playLiveChannel,
                        onBannerClick = viewModel::onBannerClick,
                        onRefresh = { viewModel.loadHomeData(forceRefresh = true) }
                    )
                }

                BottomNavTab.LIBRARY -> {
                    LibraryScreen(
                        isLoading = uiState.isLoadingLibrary,
                        error = uiState.libraryError,
                        mainFilter = uiState.libraryMainFilter,
                        items = uiState.libraryDisplayedItems,
                        hasMore = uiState.libraryHasMore,
                        isSearchOpen = uiState.isLibrarySearchOpen,
                        searchQuery = uiState.searchQuery,
                        searchFilter = uiState.searchFilter,
                        searchResults = uiState.searchResults,
                        isSearching = uiState.isSearching,
                        selectedMediaItem = uiState.selectedMediaItem,
                        isLoadingDetail = uiState.isLoadingMediaDetail,
                        detailError = uiState.mediaDetailError,
                        mediaDetail = uiState.mediaDetail,
                        selectedSeasonNumber = uiState.selectedSeasonNumber,
                        onSelectMainFilter = viewModel::selectLibraryMainFilter,
                        onLoadMore = viewModel::loadMoreLibraryItems,
                        onOpenSearch = viewModel::openLibrarySearch,
                        onCloseSearch = viewModel::closeLibrarySearch,
                        onSearchQueryChange = viewModel::updateSearchQuery,
                        onSearchFilterChange = viewModel::updateSearchFilter,
                        onItemClick = viewModel::openMediaDetail,
                        onCloseDetail = viewModel::closeMediaDetail,
                        onSelectSeason = viewModel::selectSeason,
                        onPlayMovie = viewModel::playLibraryMovie,
                        onPlayEpisode = viewModel::playLibraryEpisode,
                        onRetry = viewModel::loadLibraryInitial
                    )
                }

                BottomNavTab.MATCHES -> {
                    MatchesScreen(
                        dateOptions = uiState.dateOptions,
                        selectedDate = uiState.selectedDateString,
                        isLoading = uiState.isLoadingMatches,
                        error = uiState.matchesError,
                        leagueGroups = uiState.leagueGroups,
                        selectedMatch = uiState.selectedMatch,
                        isLoadingMatchDetail = uiState.isLoadingMatchDetail,
                        matchDetailError = uiState.matchDetailError,
                        matchDetail = uiState.matchDetail,
                        onSelectDate = viewModel::loadMatchesForDate,
                        onMatchClick = viewModel::openMatchDetail,
                        onCloseMatchDetail = viewModel::closeMatchDetail,
                        onRefresh = { viewModel.loadMatchesForDate(uiState.selectedDateString) }
                    )
                }

                BottomNavTab.SETTINGS -> {
                    SettingsScreen(
                        telegramUrl = uiState.telegramUrl
                    )
                }
            }
        }
    }
}

/**
 * Sleek, compact, pitch-black bottom bar with ordered colorful top divider line and crisp white icons & labels.
 */
@Composable
private fun FoulCompactBottomBar(
    currentTab: BottomNavTab,
    onSelectTab: (BottomNavTab) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(FoulBottomBarBlack)
            .navigationBarsPadding()
    ) {
        // Ordered colorful top divider line
        OrderedColorfulDivider(thickness = 1.5.dp)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            NAV_ITEMS.forEach { item ->
                val selected = currentTab == item.tab
                val interactionSource = remember { MutableInteractionSource() }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (selected) FoulPurpleDeep else Color.Transparent
                        )
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = { onSelectTab(item.tab) }
                        )
                        .testTag(item.testTag),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                            contentDescription = item.label,
                            tint = if (selected) Color.White else Color.White.copy(alpha = 0.55f),
                            modifier = Modifier.size(19.dp)
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.5.sp),
                            color = if (selected) Color.White else Color.White.copy(alpha = 0.55f),
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1
                        )
                    }

                    // Subtle top active indicator pill
                    if (selected) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .width(22.dp)
                                .height(2.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.White)
                        )
                    }
                }
            }
        }
    }
}
