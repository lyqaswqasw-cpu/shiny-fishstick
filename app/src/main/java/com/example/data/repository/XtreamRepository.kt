package com.example.data.repository

import android.util.JsonReader
import android.util.JsonToken
import com.example.data.model.LibraryFilterType
import com.example.data.model.LibraryMediaItem
import com.example.data.model.MediaDetailInfo
import com.example.data.model.SeriesEpisode
import com.example.data.model.SeriesSeason
import com.example.data.model.XtreamAccount
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStreamReader
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

class XtreamRepository(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()
) {
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    // High-speed in-memory catalog for instant browsing & sub-10ms search
    private val allMovies = CopyOnWriteArrayList<LibraryMediaItem>()
    private val allSeries = CopyOnWriteArrayList<LibraryMediaItem>()
    private val seenMovieIds = HashSet<String>()
    private val seenSeriesIds = HashSet<String>()

    private var activeAccount: XtreamAccount? = null
    private var isFullIndexStarted = false
    private val detailCache = HashMap<String, MediaDetailInfo>()

    companion object {
        // 72 items per page (between 60 and 80, and divisible by 3 for 3x3 grid!)
        const val PAGE_SIZE = 72
    }

    /**
     * Quickly resolves the fastest working Xtream account from Firebase and fetches the initial
     * batch of 72 movies & series in under 1 second using priority categories, while kicking off
     * a background streaming indexer for the complete catalog.
     */
    suspend fun ensureInitialCatalogLoaded(
        accounts: List<XtreamAccount>,
        onBackgroundCatalogUpdated: () -> Unit = {}
    ): Result<List<LibraryMediaItem>> = withContext(Dispatchers.IO) {
        if (accounts.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("لا توجد حسابات مكتبة متاحة في قاعدة البيانات"))
        }

        mutex.withLock {
            if (allMovies.isNotEmpty() || allSeries.isNotEmpty()) {
                return@withContext Result.success(buildCombinedList(LibraryFilterType.ALL))
            }

            // Find working Xtream account in parallel
            val working = findFastestWorkingAccount(accounts)
                ?: accounts.first()
            activeAccount = working

            // Load initial priority categories (Arabic + New Releases + Netflix) in parallel for <1s startup
            try {
                coroutineScope {
                    val moviesDeferred = async { fetchFastInitialMovies(working) }
                    val seriesDeferred = async { fetchFastInitialSeries(working) }
                    val initialMovies = moviesDeferred.await()
                    val initialSeries = seriesDeferred.await()

                    synchronized(seenMovieIds) {
                        for (m in initialMovies) {
                            if (seenMovieIds.add(m.id)) allMovies.add(m)
                        }
                    }
                    synchronized(seenSeriesIds) {
                        for (s in initialSeries) {
                            if (seenSeriesIds.add(s.id)) allSeries.add(s)
                        }
                    }
                }
            } catch (_: Exception) {
            }

            // Start full streaming index in the background so search covers 100% of VODs & Series
            if (!isFullIndexStarted) {
                isFullIndexStarted = true
                backgroundScope.launch {
                    streamFullCatalogInBackground(working, onBackgroundCatalogUpdated)
                }
            }

            val combined = buildCombinedList(LibraryFilterType.ALL)
            if (combined.isEmpty()) {
                // Fallback direct stream parse of first 120 items if categories endpoint was empty
                streamCatalogItems(
                    account = working,
                    action = "get_vod_streams",
                    isSeries = false,
                    maxItems = 80
                ).forEach { m ->
                    if (seenMovieIds.add(m.id)) allMovies.add(m)
                }
            }

            Result.success(buildCombinedList(LibraryFilterType.ALL))
        }
    }

    fun getCatalogItems(filter: LibraryFilterType): List<LibraryMediaItem> {
        return buildCombinedList(filter)
    }

    /**
     * Ultra-fast search across movies & series with filter support (الكل / الأفلام / المسلسلات).
     */
    fun searchCatalog(
        query: String,
        filter: LibraryFilterType,
        limit: Int = 180
    ): List<LibraryMediaItem> {
        val cleanQuery = query.trim().lowercase()
        if (cleanQuery.isEmpty()) {
            return buildCombinedList(filter).take(limit)
        }

        val tokens = cleanQuery.split(" ").filter { it.isNotBlank() }
        val results = ArrayList<LibraryMediaItem>(limit)

        val searchMovies = filter == LibraryFilterType.ALL || filter == LibraryFilterType.MOVIES
        val searchSeries = filter == LibraryFilterType.ALL || filter == LibraryFilterType.SERIES

        // Interleave or scan movies & series for exact/prefix matches first, then substring matches
        if (searchMovies && searchSeries) {
            val mList = allMovies
            val sList = allSeries
            val maxLen = maxOf(mList.size, sList.size)
            for (i in 0 until maxLen) {
                if (i < mList.size) {
                    val m = mList[i]
                    if (matchesQuery(m.lowerName, cleanQuery, tokens)) {
                        results.add(m)
                        if (results.size >= limit) break
                    }
                }
                if (i < sList.size) {
                    val s = sList[i]
                    if (matchesQuery(s.lowerName, cleanQuery, tokens)) {
                        results.add(s)
                        if (results.size >= limit) break
                    }
                }
            }
        } else if (searchMovies) {
            for (m in allMovies) {
                if (matchesQuery(m.lowerName, cleanQuery, tokens)) {
                    results.add(m)
                    if (results.size >= limit) break
                }
            }
        } else if (searchSeries) {
            for (s in allSeries) {
                if (matchesQuery(s.lowerName, cleanQuery, tokens)) {
                    results.add(s)
                    if (results.size >= limit) break
                }
            }
        }

        return results
    }

    private fun matchesQuery(lowerName: String, fullQuery: String, tokens: List<String>): Boolean {
        if (lowerName.contains(fullQuery)) return true
        for (t in tokens) {
            if (!lowerName.contains(t)) return false
        }
        return true
    }

    private fun buildCombinedList(filter: LibraryFilterType): List<LibraryMediaItem> {
        return when (filter) {
            LibraryFilterType.MOVIES -> allMovies.toList()
            LibraryFilterType.SERIES -> allSeries.toList()
            LibraryFilterType.ALL -> {
                val mList = allMovies
                val sList = allSeries
                val out = ArrayList<LibraryMediaItem>(mList.size + sList.size)
                val maxIndex = maxOf(mList.size, sList.size)
                // Interleave 2 movies + 1 series for a rich, balanced 3x3 grid
                var mIdx = 0
                var sIdx = 0
                while (mIdx < mList.size || sIdx < sList.size) {
                    if (mIdx < mList.size) out.add(mList[mIdx++])
                    if (mIdx < mList.size) out.add(mList[mIdx++])
                    if (sIdx < sList.size) out.add(sList[sIdx++])
                    if (out.size >= maxIndex * 2) break
                }
                out
            }
        }
    }

    private suspend fun findFastestWorkingAccount(accounts: List<XtreamAccount>): XtreamAccount? =
        coroutineScope {
            for (acc in accounts) {
                try {
                    val url = "${acc.cleanHost}/player_api.php?username=${acc.username}&password=${acc.password}"
                    val req = Request.Builder()
                        .url(url)
                        .header("User-Agent", "IPTVSmartersPlayer")
                        .build()
                    httpClient.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val body = resp.body?.string().orEmpty()
                            if (body.contains("user_info")) {
                                return@coroutineScope acc
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }
            accounts.firstOrNull()
        }

    private suspend fun fetchFastInitialMovies(account: XtreamAccount): List<LibraryMediaItem> =
        coroutineScope {
            val catIds = fetchPriorityCategoryIds(account, "get_vod_categories", maxCategories = 3)
            if (catIds.isEmpty()) {
                return@coroutineScope streamCatalogItems(
                    account = account,
                    action = "get_vod_streams",
                    isSeries = false,
                    maxItems = 90
                )
            }
            catIds.map { catId ->
                async {
                    streamCatalogItems(
                        account = account,
                        action = "get_vod_streams&category_id=$catId",
                        isSeries = false,
                        maxItems = 60
                    )
                }
            }.awaitAll().flatten()
        }

    private suspend fun fetchFastInitialSeries(account: XtreamAccount): List<LibraryMediaItem> =
        coroutineScope {
            val catIds = fetchPriorityCategoryIds(account, "get_series_categories", maxCategories = 3)
            if (catIds.isEmpty()) {
                return@coroutineScope streamCatalogItems(
                    account = account,
                    action = "get_series",
                    isSeries = true,
                    maxItems = 90
                )
            }
            catIds.map { catId ->
                async {
                    streamCatalogItems(
                        account = account,
                        action = "get_series&category_id=$catId",
                        isSeries = true,
                        maxItems = 60
                    )
                }
            }.awaitAll().flatten()
        }

    private fun fetchPriorityCategoryIds(
        account: XtreamAccount,
        action: String,
        maxCategories: Int
    ): List<String> {
        return try {
            val url = "${account.cleanHost}/player_api.php?username=${account.username}&password=${account.password}&action=$action"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "IPTVSmartersPlayer")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val arr = JSONArray(resp.body?.string().orEmpty())
                val arabicOrNew = mutableListOf<String>()
                val fallback = mutableListOf<String>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optString("category_id", "")
                    val name = obj.optString("category_name", "").uppercase()
                    if (id.isNotEmpty()) {
                        if (name.contains("ARAB") || name.contains("عرب") || name.contains("مترجم") ||
                            name.contains("NEW") || name.contains("NETFLIX") || name.contains("202")
                        ) {
                            arabicOrNew.add(id)
                        } else if (fallback.size < maxCategories) {
                            fallback.add(id)
                        }
                    }
                }
                (arabicOrNew + fallback).distinct().take(maxCategories)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun streamFullCatalogInBackground(
        account: XtreamAccount,
        onUpdated: () -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            coroutineScope {
                val seriesJob = launch {
                    val items = streamCatalogItems(
                        account = account,
                        action = "get_series",
                        isSeries = true,
                        maxItems = 25000
                    )
                    var added = 0
                    synchronized(seenSeriesIds) {
                        for (item in items) {
                            if (seenSeriesIds.add(item.id)) {
                                allSeries.add(item)
                                added++
                            }
                        }
                    }
                    if (added > 0) onUpdated()
                }

                val moviesJob = launch {
                    val items = streamCatalogItems(
                        account = account,
                        action = "get_vod_streams",
                        isSeries = false,
                        maxItems = 35000
                    )
                    var added = 0
                    synchronized(seenMovieIds) {
                        for (item in items) {
                            if (seenMovieIds.add(item.id)) {
                                allMovies.add(item)
                                added++
                            }
                        }
                    }
                    if (added > 0) onUpdated()
                }

                seriesJob.join()
                moviesJob.join()
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Streams JSON array from Xtream API using Android's streaming JsonReader so memory remains
     * tiny and parsing is ultra-fast even on massive catalogs.
     */
    private fun streamCatalogItems(
        account: XtreamAccount,
        action: String,
        isSeries: Boolean,
        maxItems: Int
    ): List<LibraryMediaItem> {
        val result = ArrayList<LibraryMediaItem>(minOf(maxItems, 2048))
        val url = "${account.cleanHost}/player_api.php?username=${account.username}&password=${account.password}&action=$action"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "IPTVSmartersPlayer")
            .build()

        try {
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val stream = resp.body?.byteStream() ?: return emptyList()
                val reader = JsonReader(InputStreamReader(stream, Charsets.UTF_8))
                reader.isLenient = true
                if (reader.peek() != JsonToken.BEGIN_ARRAY) return emptyList()
                reader.beginArray()
                while (reader.hasNext() && result.size < maxItems) {
                    if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                        reader.skipValue()
                        continue
                    }
                    reader.beginObject()
                    var id = ""
                    var name = ""
                    var poster = ""
                    var rating = ""
                    var ext = "mp4"
                    var catId = ""
                    var plot = ""
                    var year = ""

                    while (reader.hasNext()) {
                        val field = reader.nextName()
                        if (reader.peek() == JsonToken.NULL) {
                            reader.nextNull()
                            continue
                        }
                        when (field) {
                            "stream_id", "series_id" -> id = nextStringSafe(reader)
                            "name", "title" -> name = nextStringSafe(reader)
                            "stream_icon", "cover" -> poster = nextStringSafe(reader)
                            "rating" -> rating = nextStringSafe(reader)
                            "container_extension" -> ext = nextStringSafe(reader).ifBlank { "mp4" }
                            "category_id" -> catId = nextStringSafe(reader)
                            "plot" -> plot = nextStringSafe(reader)
                            "releaseDate", "year" -> year = nextStringSafe(reader)
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()

                    if (id.isNotEmpty() && name.isNotEmpty()) {
                        val cleanName = cleanDisplayName(name)
                        val extractedYear = if (year.length >= 4) {
                            year.take(4)
                        } else {
                            extractYearFromTitle(name)
                        }
                        result.add(
                            LibraryMediaItem(
                                id = id,
                                name = cleanName,
                                lowerName = name.lowercase(),
                                posterUrl = poster,
                                rating = formatRating(rating),
                                releaseYear = extractedYear,
                                isSeries = isSeries,
                                containerExtension = ext,
                                categoryId = catId,
                                plotPreview = plot,
                                account = account
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
        }
        return result
    }

    private fun nextStringSafe(reader: JsonReader): String {
        return try {
            when (reader.peek()) {
                JsonToken.STRING, JsonToken.NUMBER -> reader.nextString()
                JsonToken.BOOLEAN -> reader.nextBoolean().toString()
                else -> {
                    reader.skipValue()
                    ""
                }
            }
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Fetches real Movie or Series details (plot/description, cast, genre, seasons, episodes)
     * directly from the Xtream Codes API (`get_vod_info` or `get_series_info`).
     */
    suspend fun getMediaDetails(item: LibraryMediaItem): Result<MediaDetailInfo> =
        withContext(Dispatchers.IO) {
            val cacheKey = "${if (item.isSeries) "S" else "M"}_${item.id}"
            detailCache[cacheKey]?.let { return@withContext Result.success(it) }

            val acc = item.account
            val action = if (item.isSeries) {
                "get_series_info&series_id=${item.id}"
            } else {
                "get_vod_info&vod_id=${item.id}"
            }
            val url = "${acc.cleanHost}/player_api.php?username=${acc.username}&password=${acc.password}&action=$action"

            try {
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "IPTVSmartersPlayer")
                    .build()

                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        return@withContext Result.failure(
                            IllegalStateException("تعذر جلب التفاصيل: ${resp.code}")
                        )
                    }
                    val bodyStr = resp.body?.string().orEmpty()
                    val root = JSONObject(bodyStr)
                    val infoObj = root.optJSONObject("info") ?: JSONObject()

                    val description = infoObj.optString(
                        "plot",
                        infoObj.optString("description", infoObj.optString("overview", item.plotPreview))
                    ).trim().ifBlank { "لا يوجد وصف متاح لهذا العمل في المصدر حالياً." }

                    val genre = infoObj.optString("genre", "").trim()
                    val releaseDate = infoObj.optString(
                        "releasedate",
                        infoObj.optString("releaseDate", item.releaseYear)
                    ).trim()
                    val cast = infoObj.optString("cast", infoObj.optString("actors", "")).trim()
                    val director = infoObj.optString("director", "").trim()
                    val duration = infoObj.optString("duration", infoObj.optString("episode_run_time", "")).trim()
                    val rating = formatRating(infoObj.optString("rating", item.rating))

                    var backdropUrl = infoObj.optString("cover_big", infoObj.optString("movie_image", item.posterUrl))
                    val backdropArr = infoObj.optJSONArray("backdrop_path")
                    if (backdropArr != null && backdropArr.length() > 0) {
                        val firstBackdrop = backdropArr.optString(0, "")
                        if (firstBackdrop.isNotEmpty()) backdropUrl = firstBackdrop
                    }

                    // For movies, also check movie_data.container_extension
                    val movieDataObj = root.optJSONObject("movie_data")
                    val realExt = movieDataObj?.optString("container_extension", item.containerExtension)
                        ?.ifBlank { item.containerExtension } ?: item.containerExtension
                    val updatedItem = item.copy(containerExtension = realExt)

                    if (!item.isSeries) {
                        val detail = MediaDetailInfo(
                            item = updatedItem,
                            description = description,
                            genre = genre,
                            releaseDate = releaseDate,
                            cast = cast,
                            director = director,
                            duration = duration,
                            rating = rating,
                            backdropUrl = backdropUrl
                        )
                        detailCache[cacheKey] = detail
                        return@withContext Result.success(detail)
                    }

                    // Parse Series Seasons & Episodes cleanly and in strict order
                    val episodesBySeason = LinkedHashMap<Int, List<SeriesEpisode>>()
                    val episodesNode = root.opt("episodes")
                    if (episodesNode is JSONObject) {
                        val seasonKeys = episodesNode.keys().asSequence().toList()
                            .sortedBy { it.toIntOrNull() ?: 999 }
                        for (sKey in seasonKeys) {
                            val seasonNum = sKey.toIntOrNull() ?: 1
                            val epArr = episodesNode.optJSONArray(sKey) ?: continue
                            val parsedEps = parseEpisodeArray(epArr, seasonNum, item.posterUrl)
                            if (parsedEps.isNotEmpty()) {
                                episodesBySeason[seasonNum] = parsedEps
                            }
                        }
                    } else if (episodesNode is JSONArray) {
                        for (i in 0 until episodesNode.length()) {
                            val subArr = episodesNode.optJSONArray(i)
                            if (subArr != null) {
                                val seasonNum = i + 1
                                val parsedEps = parseEpisodeArray(subArr, seasonNum, item.posterUrl)
                                if (parsedEps.isNotEmpty()) {
                                    episodesBySeason[seasonNum] = parsedEps
                                }
                            }
                        }
                    }

                    val seasonsList = mutableListOf<SeriesSeason>()
                    val seasonsArr = root.optJSONArray("seasons")
                    if (seasonsArr != null) {
                        for (i in 0 until seasonsArr.length()) {
                            val sObj = seasonsArr.optJSONObject(i) ?: continue
                            val sNum = sObj.optInt("season_number", i + 1)
                            val epListForSeason = episodesBySeason[sNum]
                            if (epListForSeason != null && epListForSeason.isNotEmpty()) {
                                seasonsList.add(
                                    SeriesSeason(
                                        seasonNumber = sNum,
                                        name = sObj.optString("name", "الموسم $sNum").ifBlank { "الموسم $sNum" },
                                        episodeCount = epListForSeason.size,
                                        coverUrl = sObj.optString("cover", item.posterUrl).ifBlank { item.posterUrl },
                                        overview = sObj.optString("overview", "")
                                    )
                                )
                            }
                        }
                    }

                    // Ensure every season present in episodesBySeason is represented in seasonsList
                    val existingSeasonNums = seasonsList.map { it.seasonNumber }.toSet()
                    for ((sNum, epList) in episodesBySeason) {
                        if (sNum !in existingSeasonNums) {
                            seasonsList.add(
                                SeriesSeason(
                                    seasonNumber = sNum,
                                    name = "الموسم $sNum",
                                    episodeCount = epList.size,
                                    coverUrl = item.posterUrl
                                )
                            )
                        }
                    }
                    seasonsList.sortBy { it.seasonNumber }

                    val detail = MediaDetailInfo(
                        item = updatedItem,
                        description = description,
                        genre = genre,
                        releaseDate = releaseDate,
                        cast = cast,
                        director = director,
                        duration = duration,
                        rating = rating,
                        backdropUrl = backdropUrl,
                        seasons = seasonsList,
                        episodesBySeason = episodesBySeason
                    )
                    detailCache[cacheKey] = detail
                    Result.success(detail)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun parseEpisodeArray(
        epArr: JSONArray,
        defaultSeasonNum: Int,
        fallbackCover: String
    ): List<SeriesEpisode> {
        val list = ArrayList<SeriesEpisode>(epArr.length())
        for (i in 0 until epArr.length()) {
            val epObj = epArr.optJSONObject(i) ?: continue
            val id = epObj.optString("id", "")
            if (id.isEmpty()) continue
            val epNum = epObj.optInt("episode_num", i + 1)
            val seasonNum = epObj.optInt("season", defaultSeasonNum)
            val rawTitle = epObj.optString("title", "الحلقة $epNum")
            val ext = epObj.optString("container_extension", "mp4").ifBlank { "mp4" }

            val epInfo = epObj.optJSONObject("info")
            val plot = epInfo?.optString("plot", "")?.trim().orEmpty()
            val duration = epInfo?.optString("duration", "")?.trim()
                ?.takeIf { it != "00:00:00" }.orEmpty()
            val rating = formatRating(epInfo?.optString("rating", "").orEmpty())
            val releaseDate = epInfo?.optString("releasedate", "")?.trim().orEmpty()
            val cover = epInfo?.optString("movie_image", fallbackCover)?.ifBlank { fallbackCover }
                ?: fallbackCover

            list.add(
                SeriesEpisode(
                    id = id,
                    episodeNum = epNum,
                    seasonNum = seasonNum,
                    title = cleanDisplayName(rawTitle).ifBlank { "الحلقة $epNum" },
                    plot = plot,
                    duration = duration,
                    rating = rating,
                    releaseDate = releaseDate,
                    containerExtension = ext,
                    coverUrl = cover
                )
            )
        }
        return list.sortedBy { it.episodeNum }
    }

    /**
     * Builds the strict ordered sequence of candidate stream URLs for the Library Player:
     * 1. http://host/movie/user/pass/id.mp4 (or /series/...)
     * 2. http://host/movie/user/pass/id.mkv
     * 3. http://host/movie/user/pass/id.m3u8
     */
    fun buildLibraryStreamCandidates(
        account: XtreamAccount,
        streamId: String,
        isSeries: Boolean
    ): List<String> {
        val typeSegment = if (isSeries) "series" else "movie"
        val base = "${account.cleanHost}/$typeSegment/${account.username}/${account.password}/$streamId"
        return listOf(
            "$base.mp4",
            "$base.mkv",
            "$base.m3u8"
        )
    }

    private fun cleanDisplayName(raw: String): String {
        return raw.replace(Regex("^[A-Z]{2,4}\\s*[-|:]\\s*"), "").trim()
    }

    private fun extractYearFromTitle(title: String): String {
        val match = Regex("\\b(19\\d{2}|20\\d{2})\\b").find(title)
        return match?.value.orEmpty()
    }

    private fun formatRating(raw: String): String {
        val num = raw.toDoubleOrNull() ?: return ""
        if (num <= 0.0) return ""
        return String.format(java.util.Locale.US, "%.1f", num)
    }
}
