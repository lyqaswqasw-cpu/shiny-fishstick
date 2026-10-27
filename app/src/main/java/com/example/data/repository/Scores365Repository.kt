package com.example.data.repository

import com.example.data.model.LeagueMatchesGroup
import com.example.data.model.LineupPlayer
import com.example.data.model.MatchCompetitor
import com.example.data.model.MatchDetailData
import com.example.data.model.MatchEventItem
import com.example.data.model.MatchItem
import com.example.data.model.MatchStatComparison
import com.example.data.model.NewsArticleItem
import com.example.data.model.StandingRowItem
import com.example.data.model.TeamLineup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class Scores365Repository(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    /**
     * Fetches real matches for the given date (formatted DD/MM/YYYY) from 365Scores API
     * and orders leagues strictly with:
     * 1. دوري أبطال أوروبا (UEFA Champions League)
     * 2. الدوري الإسباني (La Liga)
     * 3. الدوري الإيطالي (Serie A)
     * 4. الدوري الإنجليزي الممتاز (Premier League)
     * followed by other competitions.
     */
    suspend fun getMatchesByDate(dateFormatted: String): Result<List<LeagueMatchesGroup>> =
        withContext(Dispatchers.IO) {
            val url = "https://webws.365scores.com/web/games/allscores/" +
                "?appTypeId=5&langId=27&timezoneName=Asia/Baghdad&userCountryId=-1&sports=1" +
                "&startDate=$dateFormatted&endDate=$dateFormatted"

            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
                    .header("Accept", "application/json")
                    .build()

                httpClient.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        return@withContext Result.failure(
                            IllegalStateException("فشل جلب جدول المباريات: ${resp.code}")
                        )
                    }
                    val body = resp.body?.string().orEmpty()
                    val root = JSONObject(body)
                    Result.success(parseAllScoresResponse(root))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun parseAllScoresResponse(root: JSONObject): List<LeagueMatchesGroup> {
        // Map competition metadata
        data class CompMeta(
            val id: Int,
            val name: String,
            val countryId: Int,
            val imageVersion: Int,
            val color: String,
            val popularityRank: Long
        )

        val compMap = HashMap<Int, CompMeta>()
        val compsArr = root.optJSONArray("competitions")
        if (compsArr != null) {
            for (i in 0 until compsArr.length()) {
                val cObj = compsArr.optJSONObject(i) ?: continue
                val id = cObj.optInt("id", -1)
                if (id != -1) {
                    compMap[id] = CompMeta(
                        id = id,
                        name = cObj.optString("name", ""),
                        countryId = cObj.optInt("countryId", -1),
                        imageVersion = cObj.optInt("imageVersion", 1),
                        color = cObj.optString("color", "#7E22CE"),
                        popularityRank = cObj.optLong("popularityRank", 0L)
                    )
                }
            }
        }

        val gamesByComp = LinkedHashMap<Int, MutableList<MatchItem>>()
        val gamesArr = root.optJSONArray("games")
        if (gamesArr != null) {
            for (i in 0 until gamesArr.length()) {
                val gObj = gamesArr.optJSONObject(i) ?: continue
                val gameId = gObj.optInt("id", -1)
                val compId = gObj.optInt("competitionId", -1)
                if (gameId == -1 || compId == -1) continue

                val compMeta = compMap[compId]
                val compName = gObj.optString("competitionDisplayName", compMeta?.name.orEmpty())
                val startTimeIso = gObj.optString("startTime", "")
                val statusGroup = gObj.optInt("statusGroup", 2)
                val statusText = gObj.optString("statusText", gObj.optString("shortStatusText", ""))
                val gameTimeVal = gObj.optDouble("gameTime", -1.0)
                val gameTimeDisplay = gObj.optString("gameTimeDisplay", "").ifBlank {
                    if (statusGroup == 3 && gameTimeVal > 0) "${gameTimeVal.toInt()}'" else ""
                }
                val roundName = gObj.optString("roundName", "")

                val homeObj = gObj.optJSONObject("homeCompetitor") ?: continue
                val awayObj = gObj.optJSONObject("awayCompetitor") ?: continue

                val homeScore = homeObj.optDouble("score", -1.0)
                val awayScore = awayObj.optDouble("score", -1.0)
                val hasScore = (statusGroup == 3 || statusGroup == 4) && homeScore >= 0 && awayScore >= 0

                val homeTeam = MatchCompetitor(
                    id = homeObj.optInt("id", 0),
                    name = homeObj.optString("name", ""),
                    symbolicName = homeObj.optString("symbolicName", ""),
                    score = if (hasScore) homeScore.toInt() else 0,
                    hasScore = hasScore,
                    imageVersion = homeObj.optInt("imageVersion", 1),
                    color = homeObj.optString("color", "#7E22CE"),
                    awayColor = homeObj.optString("awayColor", "#FFFFFF"),
                    redCards = homeObj.optInt("redCards", 0)
                )

                val awayTeam = MatchCompetitor(
                    id = awayObj.optInt("id", 0),
                    name = awayObj.optString("name", ""),
                    symbolicName = awayObj.optString("symbolicName", ""),
                    score = if (hasScore) awayScore.toInt() else 0,
                    hasScore = hasScore,
                    imageVersion = awayObj.optInt("imageVersion", 1),
                    color = awayObj.optString("color", "#3B82F6"),
                    awayColor = awayObj.optString("awayColor", "#FFFFFF"),
                    redCards = awayObj.optInt("redCards", 0)
                )

                val matchItem = MatchItem(
                    id = gameId,
                    competitionId = compId,
                    competitionName = compName,
                    competitionImageVersion = compMeta?.imageVersion ?: 1,
                    startTimeIso = startTimeIso,
                    displayTime = formatBaghdadTime(startTimeIso),
                    statusGroup = statusGroup,
                    statusText = statusText,
                    gameTimeDisplay = gameTimeDisplay,
                    roundName = roundName,
                    homeTeam = homeTeam,
                    awayTeam = awayTeam
                )

                gamesByComp.getOrPut(compId) { mutableListOf() }.add(matchItem)
            }
        }

        val groups = gamesByComp.map { (compId, matches) ->
            val meta = compMap[compId]
            val compName = meta?.name?.ifBlank { matches.first().competitionName }
                ?: matches.first().competitionName
            val priority = computeLeaguePriority(
                compId = compId,
                compName = compName,
                countryId = meta?.countryId ?: -1
            )
            LeagueMatchesGroup(
                competitionId = compId,
                competitionName = compName,
                countryId = meta?.countryId ?: -1,
                imageVersion = meta?.imageVersion ?: 1,
                color = meta?.color ?: "#7E22CE",
                priorityRank = priority,
                matches = matches
            )
        }

        return groups.sortedWith(
            compareBy<LeagueMatchesGroup> { it.priorityRank }
                .thenByDescending { compMap[it.competitionId]?.popularityRank ?: 0L }
        )
    }

    /**
     * Enforces strict league ordering requested by user:
     * 1. دوري أبطال أوروبا (UEFA Champions League)
     * 2. الدوري الإسباني (La Liga)
     * 3. الدوري الإيطالي (Serie A)
     * 4. الدوري الإنجليزي الممتاز (Premier League)
     */
    private fun computeLeaguePriority(compId: Int, compName: String, countryId: Int): Int {
        val clean = compName.trim()
        // 1. UEFA Champions League (ID 572)
        if (compId == 572 || (clean == "دوري أبطال أوروبا" && !clean.contains("شباب") && !clean.contains("سيدات"))) {
            return 1
        }
        // 2. La Liga (ID 11)
        if (compId == 11 || clean == "الدوري الإسباني" || clean == "الدوري الاسباني") {
            return 2
        }
        // 3. Serie A (ID 17)
        if (compId == 17 || clean == "الدوري الإيطالي" || clean == "الدوري الايطالي") {
            return 3
        }
        // 4. Premier League (ID 7)
        if (compId == 7 || clean == "الدوري الإنجليزي الممتاز" || clean == "الدوري الانجليزي الممتاز" ||
            (clean == "الدوري الإنجليزي" && countryId == 1)
        ) {
            return 4
        }
        // Secondary top competitions
        if (clean.contains("دوري أبطال أوروبا")) return 5
        if (clean.contains("الدوري الأوروبي") || clean.contains("أبطال آسيا")) return 6
        if (clean.contains("الدوري الألماني") || clean.contains("الدوري الفرنسي")) return 7
        if (clean.contains("الدوري السعودي") || clean.contains("الدوري العراقي") || clean.contains("الدوري المصري")) return 8
        return 50
    }

    /**
     * Fetches full Match Details (Events, Lineups on pitch with player photos, Stats with Possession first,
     * Standings with both teams highlighted, and News) in parallel.
     */
    suspend fun getMatchFullDetails(match: MatchItem): Result<MatchDetailData> =
        withContext(Dispatchers.IO) {
            try {
                coroutineScope {
                    val gameDeferred = async { fetchGameDetailJson(match.id) }
                    val statsDeferred = async { fetchGameStatsJson(match.id) }
                    val standingsDeferred = async { fetchStandingsJson(match.competitionId) }
                    val newsDeferred = async { fetchCompetitionNewsJson(match.competitionId) }

                    val gameRoot = gameDeferred.await()
                    val statsRoot = statsDeferred.await()
                    val standingsRoot = standingsDeferred.await()
                    val newsRoot = newsDeferred.await()

                    val detailData = buildMatchDetailData(
                        initialMatch = match,
                        gameRoot = gameRoot,
                        statsRoot = statsRoot,
                        standingsRoot = standingsRoot,
                        newsRoot = newsRoot
                    )
                    Result.success(detailData)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun fetchGameDetailJson(gameId: Int): JSONObject? {
        val url = "https://webws.365scores.com/web/game/?appTypeId=5&langId=27&timezoneName=Asia/Baghdad&userCountryId=-1&gameId=$gameId"
        return executeGetJson(url)
    }

    private fun fetchGameStatsJson(gameId: Int): JSONObject? {
        val url = "https://webws.365scores.com/web/game/stats/?appTypeId=5&langId=27&timezoneName=Asia/Baghdad&userCountryId=-1&games=$gameId"
        return executeGetJson(url)
    }

    private fun fetchStandingsJson(competitionId: Int): JSONObject? {
        val url = "https://webws.365scores.com/web/standings/?appTypeId=5&langId=27&timezoneName=Asia/Baghdad&userCountryId=-1&competitions=$competitionId"
        return executeGetJson(url)
    }

    private fun fetchCompetitionNewsJson(competitionId: Int): JSONObject? {
        val specificUrl = "https://webws.365scores.com/web/news/?appTypeId=5&langId=27&timezoneName=Asia/Baghdad&userCountryId=-1&competitions=$competitionId"
        val res = executeGetJson(specificUrl)
        val arr = res?.optJSONArray("news")
        if (arr != null && arr.length() > 0) return res
        // Fallback to general Arabic football news from 365Scores if competition-specific news is empty
        val generalUrl = "https://webws.365scores.com/web/news/?appTypeId=5&langId=27&timezoneName=Asia/Baghdad&userCountryId=-1"
        return executeGetJson(generalUrl)
    }

    private fun executeGetJson(url: String): JSONObject? {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
                .header("Accept", "application/json")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string().orEmpty()
                if (body.isNotBlank()) JSONObject(body) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun buildMatchDetailData(
        initialMatch: MatchItem,
        gameRoot: JSONObject?,
        statsRoot: JSONObject?,
        standingsRoot: JSONObject?,
        newsRoot: JSONObject?
    ): MatchDetailData {
        val gameObj = gameRoot?.optJSONObject("game")

        // Map game.members by player ID for fast lookup of player names, athleteIds, and jersey numbers
        data class MemberMeta(
            val id: Int,
            val athleteId: Int,
            val competitorId: Int,
            val name: String,
            val shortName: String,
            val jerseyNumber: Int,
            val imageVersion: Int
        )

        val membersMap = HashMap<Int, MemberMeta>()
        val membersArr = gameObj?.optJSONArray("members")
        if (membersArr != null) {
            for (i in 0 until membersArr.length()) {
                val mObj = membersArr.optJSONObject(i) ?: continue
                val pid = mObj.optInt("id", -1)
                if (pid != -1) {
                    val name = mObj.optString("name", "")
                    membersMap[pid] = MemberMeta(
                        id = pid,
                        athleteId = mObj.optInt("athleteId", 0),
                        competitorId = mObj.optInt("competitorId", 0),
                        name = name,
                        shortName = mObj.optString("shortName", name),
                        jerseyNumber = mObj.optInt("jerseyNumber", 0),
                        imageVersion = mObj.optInt("imageVersion", 1)
                    )
                }
            }
        }

        val homeId = initialMatch.homeTeam.id
        val awayId = initialMatch.awayTeam.id

        // 1. Parse Events (separated by Home / Away team)
        val events = mutableListOf<MatchEventItem>()
        val eventsArr = gameObj?.optJSONArray("events")
        if (eventsArr != null) {
            for (i in 0 until eventsArr.length()) {
                val eObj = eventsArr.optJSONObject(i) ?: continue
                val compId = eObj.optInt("competitorId", 0)
                val playerId = eObj.optInt("playerId", 0)
                val member = membersMap[playerId]

                val extraArr = eObj.optJSONArray("extraPlayers")
                val extraPlayerId = if (extraArr != null && extraArr.length() > 0) extraArr.optInt(0, 0) else 0
                val extraMember = membersMap[extraPlayerId]

                val eventTypeObj = eObj.optJSONObject("eventType")
                val typeId = eventTypeObj?.optInt("id", 0) ?: 0
                val typeName = eventTypeObj?.optString("name", "").orEmpty()
                val subTypeName = eventTypeObj?.optString("subTypeName", "").orEmpty()

                val gameTime = eObj.optDouble("gameTime", 0.0).toInt()
                val addedTime = eObj.optInt("addedTime", 0)
                val minuteDisplay = eObj.optString("gameTimeDisplay", "").ifBlank {
                    if (addedTime > 0) "${gameTime}+${addedTime}'" else "${gameTime}'"
                }

                val photoUrl = if (member != null && member.athleteId > 0) {
                    "https://imagecache.365scores.com/image/upload/f_png,w_100,h_100,c_limit,q_auto:eco,dpr_2,d_Athletes:default.png/v${member.imageVersion.coerceAtLeast(1)}/Athletes/${member.athleteId}"
                } else ""

                events.add(
                    MatchEventItem(
                        id = "${eObj.optInt("order", i)}_$playerId",
                        order = eObj.optInt("order", i),
                        gameTime = gameTime,
                        minuteDisplay = minuteDisplay,
                        competitorId = compId,
                        isHome = compId == homeId,
                        typeId = typeId,
                        typeName = typeName,
                        subTypeName = subTypeName,
                        playerName = member?.name ?: "",
                        secondaryPlayerName = extraMember?.name ?: "",
                        playerPhotoUrl = photoUrl
                    )
                )
            }
        }
        events.sortBy { it.order }

        // 2. Parse Lineups for Home & Away teams
        fun parseTeamLineup(compJson: JSONObject?, teamCompetitor: MatchCompetitor): TeamLineup? {
            val lineupsObj = compJson?.optJSONObject("lineups") ?: return null
            val formation = lineupsObj.optString("formation", "4-3-3")
            val statusText = lineupsObj.optString("status", "")
            val lMembersArr = lineupsObj.optJSONArray("members") ?: return null

            val starters = mutableListOf<LineupPlayer>()
            val subs = mutableListOf<LineupPlayer>()

            for (i in 0 until lMembersArr.length()) {
                val lmObj = lMembersArr.optJSONObject(i) ?: continue
                val pid = lmObj.optInt("id", -1)
                val status = lmObj.optInt("status", 0) // 1 = Starting, 2 = Substitute
                val meta = membersMap[pid]

                val posObj = lmObj.optJSONObject("position") ?: lmObj.optJSONObject("formation")
                val posName = posObj?.optString("name", "") ?: ""

                val yardObj = lmObj.optJSONObject("yardFormation")
                val fieldLine = yardObj?.optInt("fieldLine", if (starters.isEmpty()) 0 else 2) ?: 2
                val fieldSide = yardObj?.optInt("fieldSide", 50) ?: 50
                val yardLine = yardObj?.optInt("line", fieldLine + 1) ?: (fieldLine + 1)
                val ranking = lmObj.optDouble("ranking", 0.0)

                val player = LineupPlayer(
                    id = pid,
                    athleteId = meta?.athleteId ?: 0,
                    name = meta?.name?.ifBlank { "لاعب" } ?: "لاعب",
                    shortName = meta?.shortName?.ifBlank { meta.name } ?: "لاعب",
                    jerseyNumber = meta?.jerseyNumber ?: 0,
                    positionName = posName,
                    fieldLine = fieldLine,
                    fieldSide = fieldSide,
                    yardLine = yardLine,
                    ranking = ranking,
                    isStarting = status == 1,
                    imageVersion = meta?.imageVersion ?: 1
                )

                if (status == 1) {
                    starters.add(player)
                } else if (status == 2) {
                    subs.add(player)
                }
            }

            if (starters.isEmpty() && subs.isEmpty()) return null
            return TeamLineup(
                competitorId = teamCompetitor.id,
                teamName = teamCompetitor.name,
                teamColor = teamCompetitor.color,
                formation = formation,
                statusText = statusText,
                startingPlayers = starters,
                substitutes = subs
            )
        }

        val homeLineup = parseTeamLineup(gameObj?.optJSONObject("homeCompetitor"), initialMatch.homeTeam)
        val awayLineup = parseTeamLineup(gameObj?.optJSONObject("awayCompetitor"), initialMatch.awayTeam)

        // 3. Parse Statistics (with Ball Possession / الاستحواذ FIRST!)
        val statsList = mutableListOf<MatchStatComparison>()
        val rawStatsArr = statsRoot?.optJSONArray("statistics") ?: gameObj?.optJSONArray("statistics")
        if (rawStatsArr != null) {
            data class PartialStat(
                val statId: Int,
                val name: String,
                var homeVal: String = "0",
                var awayVal: String = "0",
                var homePct: Float = 0.5f,
                var awayPct: Float = 0.5f,
                val order: Int
            )

            val grouped = LinkedHashMap<Int, PartialStat>()
            for (i in 0 until rawStatsArr.length()) {
                val sObj = rawStatsArr.optJSONObject(i) ?: continue
                val statId = sObj.optInt("id", i)
                val name = sObj.optString("name", "")
                if (name.isEmpty()) continue
                val compId = sObj.optInt("competitorId", 0)
                val valStr = sObj.optString("value", "0")
                val pct = sObj.optDouble("valuePercentage", 0.5).toFloat().coerceIn(0f, 1f)
                val order = sObj.optInt("order", i + 10)

                val entry = grouped.getOrPut(statId) {
                    PartialStat(statId = statId, name = name, order = order)
                }
                if (compId == homeId) {
                    entry.homeVal = valStr
                    entry.homePct = pct
                } else if (compId == awayId) {
                    entry.awayVal = valStr
                    entry.awayPct = pct
                }
            }

            for ((_, p) in grouped) {
                val isPossession = p.statId == 10 || p.name.contains("الاستحواذ") || p.name.contains("استحواذ")
                statsList.add(
                    MatchStatComparison(
                        statId = p.statId,
                        name = p.name,
                        homeValue = p.homeVal,
                        awayValue = p.awayVal,
                        homeRatio = p.homePct,
                        awayRatio = p.awayPct,
                        isPossession = isPossession,
                        order = if (isPossession) -100 else p.order
                    )
                )
            }
            statsList.sortBy { it.order }
        }

        // 4. Parse Standings Table (highlighting the 2 match teams!)
        val standingsRows = mutableListOf<StandingRowItem>()
        var standingsLeagueName = initialMatch.competitionName
        val standingsArr = standingsRoot?.optJSONArray("standings")
        if (standingsArr != null && standingsArr.length() > 0) {
            val firstTable = standingsArr.optJSONObject(0)
            standingsLeagueName = firstTable?.optString("displayName", initialMatch.competitionName)
                ?.ifBlank { initialMatch.competitionName } ?: initialMatch.competitionName
            val rowsArr = firstTable?.optJSONArray("rows")
            if (rowsArr != null) {
                for (i in 0 until rowsArr.length()) {
                    val rObj = rowsArr.optJSONObject(i) ?: continue
                    val compObj = rObj.optJSONObject("competitor") ?: continue
                    val cid = compObj.optInt("id", 0)
                    val tName = compObj.optString("name", "")
                    val imgVer = compObj.optInt("imageVersion", 1)
                    val color = compObj.optString("color", "#7E22CE")

                    standingsRows.add(
                        StandingRowItem(
                            position = rObj.optInt("position", i + 1),
                            competitorId = cid,
                            teamName = tName,
                            imageVersion = imgVer,
                            teamColor = color,
                            played = rObj.optInt("gamePlayed", 0),
                            won = rObj.optInt("gamesWon", 0),
                            drawn = rObj.optInt("gamesEven", 0),
                            lost = rObj.optInt("gamesLost", 0),
                            goalsFor = rObj.optInt("for", 0),
                            goalsAgainst = rObj.optInt("against", 0),
                            goalDiff = rObj.optDouble("ratio", 0.0).toInt(),
                            points = rObj.optDouble("points", 0.0).toInt(),
                            isHomeMatchTeam = cid == homeId,
                            isAwayMatchTeam = cid == awayId
                        )
                    )
                }
            }
        }

        // 5. Parse News
        val newsList = mutableListOf<NewsArticleItem>()
        val newsArr = newsRoot?.optJSONArray("news")
        if (newsArr != null) {
            for (i in 0 until minOf(newsArr.length(), 25)) {
                val nObj = newsArr.optJSONObject(i) ?: continue
                val title = nObj.optString("title", "").trim()
                if (title.isEmpty()) continue
                val pubDate = nObj.optString("publishDate", "")
                newsList.add(
                    NewsArticleItem(
                        id = nObj.optLong("id", i.toLong()),
                        title = title,
                        publishDate = pubDate,
                        formattedDate = formatNewsDate(pubDate),
                        imageUrl = nObj.optString("image", ""),
                        url = nObj.optString("url", "")
                    )
                )
            }
        }

        val venueName = gameObj?.optJSONObject("venue")?.optString("name", "").orEmpty()
        val officialsArr = gameObj?.optJSONArray("officials")
        val refereeName = if (officialsArr != null && officialsArr.length() > 0) {
            officialsArr.optJSONObject(0)?.optString("name", "").orEmpty()
        } else ""

        return MatchDetailData(
            match = initialMatch,
            events = events,
            homeLineup = homeLineup,
            awayLineup = awayLineup,
            stats = statsList,
            standings = standingsRows,
            standingsLeagueName = standingsLeagueName,
            news = newsList,
            venueName = venueName,
            refereeName = refereeName
        )
    }

    private fun formatBaghdadTime(isoString: String): String {
        if (isoString.isBlank()) return ""
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
            val date: Date = parser.parse(isoString) ?: return isoString.takeLast(8).take(5)
            val outFmt = SimpleDateFormat("hh:mm a", Locale("ar"))
            outFmt.timeZone = TimeZone.getTimeZone("Asia/Baghdad")
            outFmt.format(date)
        } catch (_: Exception) {
            if (isoString.length >= 16) isoString.substring(11, 16) else isoString
        }
    }

    private fun formatNewsDate(isoString: String): String {
        if (isoString.isBlank()) return ""
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
            val date: Date = parser.parse(isoString) ?: return isoString.take(10)
            val outFmt = SimpleDateFormat("yyyy/MM/dd - hh:mm a", Locale("ar"))
            outFmt.timeZone = TimeZone.getTimeZone("Asia/Baghdad")
            outFmt.format(date)
        } catch (_: Exception) {
            isoString.take(10)
        }
    }
}
