package com.example.data.repository

import com.example.data.model.ChannelCategory
import com.example.data.model.ChannelServer
import com.example.data.model.EventBanner
import com.example.data.model.FirebaseHomePayload
import com.example.data.model.LiveChannel
import com.example.data.model.LiveEventMatch
import com.example.data.model.XtreamAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class FirebaseRepository(
    private val httpClient: OkHttpClient = defaultHttpClient()
) {
    private val mutex = Mutex()
    private var cachedPayload: FirebaseHomePayload? = null
    private var lastFetchTimeMs: Long = 0L

    // 10-minute cache TTL to avoid heavy pressure or redundant reads on Firebase RTDB
    private val cacheTtlMs = 10 * 60 * 1000L

    suspend fun getFirebaseData(forceRefresh: Boolean = false): Result<FirebaseHomePayload> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val now = System.currentTimeMillis()
                val current = cachedPayload
                if (!forceRefresh && current != null && (now - lastFetchTimeMs) < cacheTtlMs) {
                    return@withContext Result.success(current)
                }

                try {
                    val request = Request.Builder()
                        .url("$FIREBASE_DB_URL/.json")
                        .header("Accept", "application/json")
                        .header("User-Agent", "FoulTV-Android/1.0")
                        .build()

                    httpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            if (current != null) return@withContext Result.success(current)
                            return@withContext Result.failure(
                                IllegalStateException("خطأ في الاتصال بقاعدة البيانات: ${response.code}")
                            )
                        }
                        val bodyStr = response.body?.string().orEmpty()
                        val root = if (bodyStr.isNotBlank() && bodyStr != "null") {
                            JSONObject(bodyStr)
                        } else {
                            JSONObject()
                        }

                        val payload = parseFirebaseRoot(root)
                        cachedPayload = payload
                        lastFetchTimeMs = now
                        Result.success(payload)
                    }
                } catch (e: Exception) {
                    if (current != null) {
                        Result.success(current)
                    } else {
                        Result.failure(e)
                    }
                }
            }
        }

    private fun parseFirebaseRoot(root: JSONObject): FirebaseHomePayload {
        // 1. Parse Categories (v4_channel_categories)
        val categories = mutableListOf<ChannelCategory>()
        val catObj = root.optJSONObject("v4_channel_categories")
        if (catObj != null) {
            val keys = catObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val item = catObj.optJSONObject(key) ?: continue
                val id = item.optString("id", key)
                val name = item.optString("name", "").trim()
                val order = item.optInt("order", 999)
                if (name.isNotEmpty()) {
                    categories.add(ChannelCategory(id = id, name = name, order = order))
                }
            }
        }
        categories.sortBy { it.order }

        // 2. Parse Channels (v4_channels)
        val channels = mutableListOf<LiveChannel>()
        val chanObj = root.optJSONObject("v4_channels")
        if (chanObj != null) {
            val keys = chanObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val item = chanObj.optJSONObject(key) ?: continue
                val id = item.optString("id", key)
                val name = item.optString("name", "").trim()
                val category = item.optString("category", "").trim()
                val imageUrl = item.optString("imageUrl", "").trim()
                val views = item.optInt("views", 0)

                val servers = mutableListOf<ChannelServer>()
                val serversArr = item.optJSONArray("servers")
                if (serversArr != null) {
                    for (i in 0 until serversArr.length()) {
                        val sObj = serversArr.optJSONObject(i) ?: continue
                        val sUrl = sObj.optString("streamUrl", "").trim()
                        if (sUrl.isNotEmpty()) {
                            servers.add(
                                ChannelServer(
                                    id = sObj.optString("id", "$i"),
                                    name = sObj.optString("name", "Server ${i + 1}").ifBlank { "HD" },
                                    streamType = sObj.optString("streamType", "m3u8"),
                                    streamUrl = sUrl
                                )
                            )
                        }
                    }
                }
                // Also support single streamUrl if present
                if (servers.isEmpty()) {
                    val directUrl = item.optString("streamUrl", item.optString("url", "")).trim()
                    if (directUrl.isNotEmpty()) {
                        servers.add(
                            ChannelServer(
                                id = "default",
                                name = "بث مباشر",
                                streamType = "m3u8",
                                streamUrl = directUrl
                            )
                        )
                    }
                }

                if (name.isNotEmpty()) {
                    channels.add(
                        LiveChannel(
                            id = id,
                            name = name,
                            category = category,
                            imageUrl = imageUrl,
                            servers = servers,
                            views = views
                        )
                    )
                }
            }
        }

        // Ensure any category referenced by a channel is also available in categories list
        val existingCatNames = categories.map { it.name.lowercase() }.toMutableSet()
        for (ch in channels) {
            if (ch.category.isNotEmpty() && !existingCatNames.contains(ch.category.lowercase())) {
                existingCatNames.add(ch.category.lowercase())
                categories.add(
                    ChannelCategory(
                        id = ch.category,
                        name = ch.category,
                        order = categories.size + 10
                    )
                )
            }
        }

        // 3. Parse Event Banners (v4_event_banners)
        val banners = mutableListOf<EventBanner>()
        val bannersObj = root.optJSONObject("v4_event_banners")
        if (bannersObj != null) {
            val keys = bannersObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val item = bannersObj.optJSONObject(key) ?: continue
                val id = item.optString("id", key)
                val title = item.optString("title", "").trim()
                val imageUrl = item.optString("imageUrl", "").trim()
                val channelId = item.optString("channelId", "")
                val channelName = item.optString("channelName", "")
                val showWatchNow = item.optBoolean("showWatchNow", true)
                val watchNowUrl = item.optString("watchNowUrl", "")

                val channelIds = mutableListOf<String>()
                val cIdsArr = item.optJSONArray("channelIds")
                if (cIdsArr != null) {
                    for (i in 0 until cIdsArr.length()) {
                        val cid = cIdsArr.optString(i, "")
                        if (cid.isNotEmpty()) channelIds.add(cid)
                    }
                }
                val channelNames = mutableListOf<String>()
                val cNamesArr = item.optJSONArray("channelNames")
                if (cNamesArr != null) {
                    for (i in 0 until cNamesArr.length()) {
                        val cname = cNamesArr.optString(i, "")
                        if (cname.isNotEmpty()) channelNames.add(cname)
                    }
                }

                if (title.isNotEmpty() || imageUrl.isNotEmpty()) {
                    banners.add(
                        EventBanner(
                            id = id,
                            title = title,
                            imageUrl = imageUrl,
                            channelId = channelId,
                            channelName = channelName,
                            channelIds = channelIds,
                            channelNames = channelNames,
                            showWatchNow = showWatchNow,
                            watchNowUrl = watchNowUrl
                        )
                    )
                }
            }
        }

        // 4. Parse Live Matches / Events (v4_live_matches & v4_events)
        val liveMatches = mutableListOf<LiveEventMatch>()
        val seenMatchIds = mutableSetOf<String>()
        for (nodeName in listOf("v4_live_matches", "v4_events")) {
            val matchesObj = root.optJSONObject(nodeName) ?: continue
            val keys = matchesObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val item = matchesObj.optJSONObject(key) ?: continue
                val id = item.optString("id", key)
                if (!seenMatchIds.add(id)) continue
                liveMatches.add(
                    LiveEventMatch(
                        id = id,
                        name = item.optString("name", ""),
                        homeTeam = item.optString("homeTeam", ""),
                        awayTeam = item.optString("awayTeam", ""),
                        homeLogo = item.optString("homeLogo", ""),
                        imageUrl = item.optString("imageUrl", ""),
                        broadcaster = item.optString("broadcaster", ""),
                        leagueName = item.optString("leagueName", item.optString("tournament", "")),
                        isLive = item.optBoolean("isLive", false),
                        targetChannelId = item.optString("targetChannelId", "")
                    )
                )
            }
        }

        // 5. Parse Xtream Accounts (v4_xtreme_accounts & xtream_accounts)
        val xtreamAccounts = mutableListOf<XtreamAccount>()
        val v4XtreamObj = root.optJSONObject("v4_xtreme_accounts")
        if (v4XtreamObj != null) {
            val keys = v4XtreamObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val item = v4XtreamObj.optJSONObject(key) ?: continue
                val host = item.optString("host", item.optString("url", "")).trim()
                val username = item.optString("username", "").trim()
                val password = item.optString("password", "").trim()
                if (host.isNotEmpty() && username.isNotEmpty() && password.isNotEmpty()) {
                    xtreamAccounts.add(
                        XtreamAccount(
                            id = item.optString("id", key),
                            name = item.optString("name", "Xtream"),
                            host = host,
                            username = username,
                            password = password,
                            streamExtension = item.optString("streamExtension", "mp4")
                        )
                    )
                }
            }
        }
        val legacyXtreamObj = root.optJSONObject("xtream_accounts")
        if (legacyXtreamObj != null) {
            val keys = legacyXtreamObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val item = legacyXtreamObj.optJSONObject(key) ?: continue
                val host = item.optString("url", item.optString("host", "")).trim()
                val username = item.optString("username", "").trim()
                val password = item.optString("password", "").trim()
                if (host.isNotEmpty() && username.isNotEmpty() && password.isNotEmpty()) {
                    xtreamAccounts.add(
                        XtreamAccount(
                            id = key,
                            name = item.optString("name", "Xtream"),
                            host = host,
                            username = username,
                            password = password,
                            streamExtension = "mp4"
                        )
                    )
                }
            }
        }

        // 6. Parse App Config (telegram_url)
        val configObj = root.optJSONObject("v4_app_config")
        val telegramUrl = configObj?.optString("telegram_url", DEFAULT_TELEGRAM_URL)
            ?.ifBlank { DEFAULT_TELEGRAM_URL } ?: DEFAULT_TELEGRAM_URL

        return FirebaseHomePayload(
            categories = categories,
            channels = channels,
            banners = banners,
            liveMatches = liveMatches,
            xtreamAccounts = xtreamAccounts,
            telegramUrl = telegramUrl
        )
    }

    companion object {
        const val FIREBASE_DB_URL = "https://foul-ddbb8-default-rtdb.firebaseio.com"
        const val DEFAULT_TELEGRAM_URL = "https://t.me/jdj_q"

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
