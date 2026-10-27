package com.example.data.model

enum class BottomNavTab {
    HOME,
    LIBRARY,
    MATCHES,
    SETTINGS
}

enum class LibraryFilterType(val labelAr: String) {
    ALL("الكل"),
    MOVIES("الأفلام"),
    SERIES("المسلسلات")
}

// --- Firebase Realtime Database Models ---

data class ChannelCategory(
    val id: String,
    val name: String,
    val order: Int
)

data class ChannelServer(
    val id: String,
    val name: String,
    val streamType: String,
    val streamUrl: String
)

data class LiveChannel(
    val id: String,
    val name: String,
    val category: String,
    val imageUrl: String,
    val servers: List<ChannelServer>,
    val views: Int = 0
)

data class EventBanner(
    val id: String,
    val title: String,
    val imageUrl: String,
    val channelId: String,
    val channelName: String,
    val channelIds: List<String>,
    val channelNames: List<String>,
    val showWatchNow: Boolean,
    val watchNowUrl: String
)

data class LiveEventMatch(
    val id: String,
    val name: String,
    val homeTeam: String,
    val awayTeam: String,
    val homeLogo: String,
    val imageUrl: String,
    val broadcaster: String,
    val leagueName: String,
    val isLive: Boolean,
    val targetChannelId: String
)

data class XtreamAccount(
    val id: String,
    val name: String,
    val host: String,
    val username: String,
    val password: String,
    val streamExtension: String = "mp4"
) {
    val cleanHost: String
        get() = host.trim().removeSuffix("/")
}

data class FirebaseHomePayload(
    val categories: List<ChannelCategory>,
    val channels: List<LiveChannel>,
    val banners: List<EventBanner>,
    val liveMatches: List<LiveEventMatch>,
    val xtreamAccounts: List<XtreamAccount>,
    val telegramUrl: String
)

// --- Library (Xtream Codes) Models ---

data class LibraryMediaItem(
    val id: String,
    val name: String,
    val lowerName: String,
    val posterUrl: String,
    val rating: String,
    val releaseYear: String,
    val isSeries: Boolean,
    val containerExtension: String,
    val categoryId: String,
    val plotPreview: String = "",
    val account: XtreamAccount
)

data class SeriesSeason(
    val seasonNumber: Int,
    val name: String,
    val episodeCount: Int,
    val coverUrl: String,
    val overview: String = ""
)

data class SeriesEpisode(
    val id: String,
    val episodeNum: Int,
    val seasonNum: Int,
    val title: String,
    val plot: String,
    val duration: String,
    val rating: String,
    val releaseDate: String,
    val containerExtension: String,
    val coverUrl: String
)

data class MediaDetailInfo(
    val item: LibraryMediaItem,
    val description: String,
    val genre: String,
    val releaseDate: String,
    val cast: String,
    val director: String,
    val duration: String,
    val rating: String,
    val backdropUrl: String,
    val seasons: List<SeriesSeason> = emptyList(),
    val episodesBySeason: Map<Int, List<SeriesEpisode>> = emptyMap()
)

data class PlaybackRequest(
    val title: String,
    val subtitle: String = "",
    val isLiveChannel: Boolean,
    // For library items: ordered list of candidate URLs (mp4 -> mkv -> m3u8)
    val candidateUrls: List<String>,
    // For live channels: server options
    val servers: List<ChannelServer> = emptyList()
)

// --- 365Scores Matches & Details Models ---

data class MatchCompetitor(
    val id: Int,
    val name: String,
    val symbolicName: String,
    val score: Int,
    val hasScore: Boolean,
    val imageVersion: Int,
    val color: String,
    val awayColor: String,
    val redCards: Int = 0
) {
    val logoUrl: String
        get() = "https://imagecache.365scores.com/image/upload/f_png,w_120,h_120,c_limit,q_auto:eco,dpr_2,d_Competitors:default1.png/v${imageVersion.coerceAtLeast(1)}/Competitors/$id"
}

data class MatchItem(
    val id: Int,
    val competitionId: Int,
    val competitionName: String,
    val competitionImageVersion: Int,
    val startTimeIso: String,
    val displayTime: String,
    val statusGroup: Int, // 2 = Scheduled, 3 = Live, 4 = Ended
    val statusText: String,
    val gameTimeDisplay: String,
    val roundName: String,
    val homeTeam: MatchCompetitor,
    val awayTeam: MatchCompetitor
) {
    val isLive: Boolean get() = statusGroup == 3
    val isFinished: Boolean get() = statusGroup == 4
    val competitionLogoUrl: String
        get() = "https://imagecache.365scores.com/image/upload/f_png,w_80,h_80,c_limit,q_auto:eco,dpr_2,d_Competitions:default.png/v${competitionImageVersion.coerceAtLeast(1)}/Competitions/$competitionId"
}

data class LeagueMatchesGroup(
    val competitionId: Int,
    val competitionName: String,
    val countryId: Int,
    val imageVersion: Int,
    val color: String,
    val priorityRank: Int,
    val matches: List<MatchItem>
) {
    val logoUrl: String
        get() = "https://imagecache.365scores.com/image/upload/f_png,w_80,h_80,c_limit,q_auto:eco,dpr_2,d_Competitions:default.png/v${imageVersion.coerceAtLeast(1)}/Competitions/$competitionId"
}

data class MatchEventItem(
    val id: String,
    val order: Int,
    val gameTime: Int,
    val minuteDisplay: String,
    val competitorId: Int,
    val isHome: Boolean,
    val typeId: Int,
    val typeName: String,
    val subTypeName: String,
    val playerName: String,
    val secondaryPlayerName: String,
    val playerPhotoUrl: String
)

data class LineupPlayer(
    val id: Int,
    val athleteId: Int,
    val name: String,
    val shortName: String,
    val jerseyNumber: Int,
    val positionName: String,
    val fieldLine: Int, // 0 = GK, 1..4 = outfield lines
    val fieldSide: Int, // 0..100 horizontal coordinate
    val yardLine: Int,
    val ranking: Double,
    val isStarting: Boolean,
    val imageVersion: Int
) {
    val photoUrl: String
        get() = if (athleteId > 0) {
            "https://imagecache.365scores.com/image/upload/f_png,w_120,h_120,c_limit,q_auto:eco,dpr_2,d_Athletes:default.png/v${imageVersion.coerceAtLeast(1)}/Athletes/$athleteId"
        } else {
            "https://imagecache.365scores.com/image/upload/f_png,w_120,h_120,c_limit,q_auto:eco,dpr_2,d_Athletes:default.png/Athletes/$id"
        }
}

data class TeamLineup(
    val competitorId: Int,
    val teamName: String,
    val teamColor: String,
    val formation: String,
    val statusText: String,
    val startingPlayers: List<LineupPlayer>,
    val substitutes: List<LineupPlayer>
)

data class MatchStatComparison(
    val statId: Int,
    val name: String,
    val homeValue: String,
    val awayValue: String,
    val homeRatio: Float,
    val awayRatio: Float,
    val isPossession: Boolean,
    val order: Int
)

data class StandingRowItem(
    val position: Int,
    val competitorId: Int,
    val teamName: String,
    val imageVersion: Int,
    val teamColor: String,
    val played: Int,
    val won: Int,
    val drawn: Int,
    val lost: Int,
    val goalsFor: Int,
    val goalsAgainst: Int,
    val goalDiff: Int,
    val points: Int,
    val isHomeMatchTeam: Boolean,
    val isAwayMatchTeam: Boolean
) {
    val logoUrl: String
        get() = "https://imagecache.365scores.com/image/upload/f_png,w_80,h_80,c_limit,q_auto:eco,dpr_2,d_Competitors:default1.png/v${imageVersion.coerceAtLeast(1)}/Competitors/$competitorId"
}

data class NewsArticleItem(
    val id: Long,
    val title: String,
    val publishDate: String,
    val formattedDate: String,
    val imageUrl: String,
    val url: String
)

data class MatchDetailData(
    val match: MatchItem,
    val events: List<MatchEventItem>,
    val homeLineup: TeamLineup?,
    val awayLineup: TeamLineup?,
    val stats: List<MatchStatComparison>,
    val standings: List<StandingRowItem>,
    val standingsLeagueName: String,
    val news: List<NewsArticleItem>,
    val venueName: String,
    val refereeName: String
)
