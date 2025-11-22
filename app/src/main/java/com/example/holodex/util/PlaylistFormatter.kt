// File: java/com/example/holodex/util/PlaylistFormatter.kt

package com.example.holodex.util

import android.content.Context
import com.example.holodex.R
import com.example.holodex.data.model.discovery.PlaylistStub
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import timber.log.Timber

// Data classes to safely parse the JSON that is often in the description field of SGPs
private data class DescriptionContext(val channel: ChannelInfo?, val org: String?, val id: String?, val title: String?)
private data class ChannelInfo(val name: String?, val english_name: String?)

// Base interface for all our specific formatters
private interface SgpFormatter {
    fun getTitle(
        playlist: PlaylistStub,
        params: Map<String, String>,
        descriptionJson: String?,
        context: Context,
        namePicker: (en: String?, jp: String?) -> String?
    ): String

    fun getDescription(
        playlist: PlaylistStub,
        params: Map<String, String>,
        descriptionJson: String?,
        context: Context,
        namePicker: (en: String?, jp: String?) -> String?
    ): String?
}

/**
 * A utility object to format playlist titles and descriptions, replicating
 * the logic from the Musicdex web frontend for consistency.
 */
object PlaylistFormatter {

    private val GSON = Gson()

    // The central map, mirroring the web app's `formatters` object
    private val formatters = mapOf<String, SgpFormatter>(
        ":artist" to ArtistFormatter,
        ":dailyrandom" to DailyRandomFormatter,
        ":video" to VideoFormatter,
        ":latest" to LatestFormatter,
        ":mv" to MvFormatter,
        ":weekly" to WeeklyFormatter,
        ":userweekly" to UserWeeklyFormatter,
        ":history" to HistoryFormatter,
        ":hot" to HotFormatter
    )

    /**
     * Gets the user-facing display title for any playlist.
     *
     * @param playlist The playlist object.
     * @param context Android context for string resources.
     * @param namePicker A helper lambda to choose between English and Japanese names.
     * @return The formatted title string.
     */
    fun getDisplayTitle(
        playlist: PlaylistStub,
        context: Context,
        namePicker: (en: String?, jp: String?) -> String?
    ): String {
        if (!playlist.id.startsWith(":")) {
            return playlist.title
        }

        val (type, params) = parsePlaylistID(playlist.id)
        val formatter = formatters[type] ?: DefaultFormatter

        return formatter.getTitle(playlist, params, playlist.description, context, namePicker)
    }
    /**
     * Gets the user-facing display description for any playlist.
     *
     * @param playlist The playlist object.
     * @param context Android context for string resources.
     * @param namePicker A helper lambda to choose between English and Japanese names.
     * @return The formatted description string, or null if there is none.
     */
    fun getDisplayDescription(
        playlist: PlaylistStub,
        context: Context,
        namePicker: (en: String?, jp: String?) -> String?
    ): String? {
        if (!playlist.id.startsWith(":")) {
            return playlist.description
        }

        val (type, params) = parsePlaylistID(playlist.id)
        val formatter = formatters[type] ?: DefaultFormatter

        return formatter.getDescription(playlist, params, playlist.description, context, namePicker)
    }
    private fun parsePlaylistID(id: String): Pair<String, Map<String, String>> {
        if (!id.startsWith(":")) return Pair(id, emptyMap())
        val typeEndIndex = id.indexOf('[')
        if (typeEndIndex == -1) return Pair(id, emptyMap())

        val type = id.substring(0, typeEndIndex)
        val paramsString = id.substring(typeEndIndex + 1, id.lastIndexOf(']'))

        val params = paramsString.split(',')
            .mapNotNull {
                val parts = it.split('=', limit = 2)
                if (parts.size == 2) parts[0] to parts[1] else null
            }
            .toMap()

        return Pair(type, params)
    }

    private fun parseDescription(json: String?): DescriptionContext? {
        if (json.isNullOrBlank()) return null
        return try {
            GSON.fromJson(json, DescriptionContext::class.java)
        } catch (e: JsonSyntaxException) {
            Timber.e(e, "Failed to parse SGP description JSON: $json")
            null
        }
    }

    // --- Formatter Implementations ---

    private object DefaultFormatter : SgpFormatter {
        override fun getTitle(p: PlaylistStub, pa: Map<String, String>, d: String?, c: Context, n: (String?, String?) -> String?) = p.title
        override fun getDescription(p: PlaylistStub, pa: Map<String, String>, d: String?, c: Context, n: (String?, String?) -> String?) = p.description
    }


    private object ArtistFormatter : SgpFormatter {
        override fun getTitle(p: PlaylistStub, pa: Map<String, String>, d: String?, c: Context, n: (String?, String?) -> String?): String {
            val channelInfo = parseDescription(d)?.channel
            val name = n(channelInfo?.english_name, channelInfo?.name) ?: "Artist"
            return c.getString(R.string.sgp_artist_radio_title, name)
        }
        override fun getDescription(p: PlaylistStub, pa: Map<String, String>, d: String?, c: Context, n: (String?, String?) -> String?): String {
            val channelInfo = parseDescription(d)?.channel
            val name = n(channelInfo?.english_name, channelInfo?.name) ?: "Artist"
            return c.getString(R.string.sgp_artist_radio_desc, name)
        }
    }

    private object DailyRandomFormatter : SgpFormatter {
        override fun getTitle(p: PlaylistStub, pa: Map<String, String>, d: String?, c: Context, n: (String?, String?) -> String?): String {
            val channelInfo = parseDescription(d)?.channel
            val name = n(channelInfo?.english_name, channelInfo?.name) ?: "Artist"
            return c.getString(R.string.sgp_daily_mix_title, name)
        }
        override fun getDescription(p: PlaylistStub, pa: Map<String, String>, d: String?, c: Context, n: (String?, String?) -> String?): String {
            val channelInfo = parseDescription(d)?.channel
            val name = n(channelInfo?.english_name, channelInfo?.name) ?: "Artist"
            return c.getString(R.string.sgp_daily_mix_desc, name)
        }
    }

    private object MvFormatter : SgpFormatter {
        override fun getTitle(p: PlaylistStub, pa: Map<String, String>, d: String?, c: Context, n: (String?, String?) -> String?): String {
            val org = pa["org"] ?: "Community"
            return when (pa["sort"]) {
                "random" -> c.getString(R.string.sgp_mv_random_title, org)
                "latest" -> c.getString(R.string.sgp_mv_latest_title, org)
                else -> p.title
            }
        }
        override fun getDescription(p: PlaylistStub, pa: Map<String, String>, d: String?, c: Context, n: (String?, String?) -> String?): String? {
            val org = pa["org"] ?: "Community"
            return when (pa["sort"]) {
                "random" -> c.getString(R.string.sgp_mv_random_desc, org)
                "latest" -> c.getString(R.string.sgp_mv_latest_desc, org)
                else -> p.description
            }
        }
    }

    private object LatestFormatter : SgpFormatter {
        override fun getTitle(p: PlaylistStub, pa: Map<String, String>, d: String?, c: Context, n: (String?, String?) -> String?): String {
            val org = pa["org"] ?: "Community"
            return c.getString(R.string.sgp_latest_title, org)
        }
        override fun getDescription(p: PlaylistStub, pa: Map<String, String>, d: String?, c: Context, n: (String?, String?) -> String?): String {
            val org = pa["org"] ?: "Community"
            return c.getString(R.string.sgp_latest_desc, org)
        }
    }

    private object WeeklyFormatter : SgpFormatter {
        override fun getTitle(p: PlaylistStub, pa: Map<String, String>, d: String?, c: Context, n: (String?, String?) -> String?): String {
            val org = pa["org"] ?: "Community"
            return c.getString(R.string.sgp_weekly_mix_title, org)
        }
        override fun getDescription(p: PlaylistStub, pa: Map<String, String>, d: String?, c: Context, n: (String?, String?) -> String?): String {
            val org = pa["org"] ?: "Community"
            return c.getString(R.string.sgp_weekly_mix_desc, org)
        }
    }

    private object UserWeeklyFormatter : SgpFormatter {
        override fun getTitle(p: PlaylistStub, pa: Map<String, String>, d: String?, c: Context, n: (String?, String?) -> String?) = c.getString(R.string.sgp_my_weekly_mix_title)
        override fun getDescription(p: PlaylistStub, pa: Map<String, String>, d: String?, c: Context, n: (String?, String?) -> String?) = c.getString(R.string.sgp_my_weekly_mix_desc)
    }

    private object HistoryFormatter : SgpFormatter {
        override fun getTitle(p: PlaylistStub, pa: Map<String, String>, d: String?, c: Context, n: (String?, String?) -> String?) = c.getString(R.string.sgp_history_title)
        override fun getDescription(p: PlaylistStub, pa: Map<String, String>, d: String?, c: Context, n: (String?, String?) -> String?) = c.getString(R.string.sgp_history_desc)
    }

    private object HotFormatter : SgpFormatter {
        override fun getTitle(p: PlaylistStub, pa: Map<String, String>, d: String?, c: Context, n: (String?, String?) -> String?) = c.getString(R.string.sgp_hot_title)
        override fun getDescription(p: PlaylistStub, pa: Map<String, String>, d: String?, c: Context, n: (String?, String?) -> String?) = c.getString(R.string.sgp_hot_desc)
    }

    private object VideoFormatter: SgpFormatter {
        override fun getTitle(p: PlaylistStub, pa: Map<String, String>, d: String?, c: Context, n: (String?, String?) -> String?): String {
            return parseDescription(d)?.title ?: p.title
        }
        override fun getDescription(p: PlaylistStub, pa: Map<String, String>, d: String?, c: Context, n: (String?, String?) -> String?): String? {
            val desc = parseDescription(d)
            val name = n(desc?.channel?.english_name, desc?.channel?.name) ?: "Artist"
            return c.getString(R.string.sgp_video_desc, name)
        }
    }
}