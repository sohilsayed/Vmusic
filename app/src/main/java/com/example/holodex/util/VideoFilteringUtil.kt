// File: java/com/example/holodex/util/VideoFilteringUtil.kt
package com.example.holodex.util

import com.example.holodex.data.model.HolodexVideoItem
import timber.log.Timber
import java.text.Normalizer

object VideoFilteringUtil {

    private val STRICT_CORE_MUSIC_TOPICS = setOf("singing", "Music_Cover", "Original_Song")

    private val musicKeywords = setOf(
        "cover", "歌ってみた", "song", "singing", "karaoke", "mv", "original song", "original", "music",
        "op/ed", "theme", "曲", "歌枠", "オリジナル曲", "アコースティック", "acoustic", "live", "concert",
        "弾き語り", "音楽", "medley", "arrange", "remix", "instrumental", "bgm", "soundtrack", "ost",
        "vocaloid", "ボカロ", "album", "single", "ギター", "guitar", "piano", "ピアノ", "うた",
        "歌", "ソング", "ミュージック", "official audio", "music video"
    )

    private val channelMusicKeywords = setOf(
        "music", "song", "cover", "vsinger", "singer", "utaite", "archive", "records",
        "official channel", "ミュージック", "音楽"
    )

    /**
     * Normalizes a title by converting all Unicode variants to their ASCII equivalents.
     * This includes:
     * - Full-width characters (Japanese/Chinese input style)
     * - Mathematical Alphanumeric Symbols (bold, italic, script, etc.)
     * - Enclosed Alphanumerics
     * - Accented characters
     * - Various stylized Unicode text
     *
     * Works for ANY word, not just hardcoded ones.
     */
    private fun normalizeTitle(title: String): String {
        var normalized = title

        // Step 1: Replace common full-width punctuation
        normalized = normalized
            .replace("（", "(").replace("）", ")")
            .replace("［", "[").replace("］", "]")
            .replace("｛", "{").replace("｝", "}")
            .replace("／", "/").replace("｜", "|")
            .replace("＠", "@").replace("＃", "#")
            .replace("＄", "$").replace("％", "%")
            .replace("＆", "&").replace("＊", "*")
            .replace("＋", "+").replace("－", "-")
            .replace("＝", "=").replace("：", ":")
            .replace("；", ";").replace("！", "!")
            .replace("？", "?").replace("～", "~")
            .replace("＜", "<").replace("＞", ">")
            .replace("．", ".").replace("，", ",")
            .replace("'", "'").replace("'", "'")
            .replace(""", "\"").replace(""", "\"")
            .replace("　", " ") // Full-width space to regular space

        // Step 2: Use Unicode normalization to decompose accented characters
        // NFD = Canonical Decomposition (é becomes e + ´)
        normalized = Normalizer.normalize(normalized, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}"), "") // Remove all diacritical marks

        // Step 3: Convert all stylized Unicode characters to ASCII
        val result = StringBuilder()
        var i = 0
        while (i < normalized.length) {
            val codePoint = normalized.codePointAt(i)
            val converted = convertUnicodeToAscii(codePoint)
            result.append(converted)
            i += Character.charCount(codePoint)
        }

        return result.toString()
    }

    /**
     * Converts a single Unicode code point to its ASCII equivalent string.
     * Handles multiple Unicode ranges for stylized text.
     */
    private fun convertUnicodeToAscii(codePoint: Int): String {
        return when {
            // Full-width alphanumeric (０-９, Ａ-Ｚ, ａ-ｚ)
            codePoint in 0xFF01..0xFF5E -> {
                (codePoint - 0xFEE0).toChar().toString()
            }

            // Mathematical Bold (𝐀-𝐙, 𝐚-𝐳) U+1D400-U+1D433
            codePoint in 0x1D400..0x1D419 -> ('A'.code + (codePoint - 0x1D400)).toChar().toString()
            codePoint in 0x1D41A..0x1D433 -> ('a'.code + (codePoint - 0x1D41A)).toChar().toString()

            // Mathematical Italic (𝐴-𝑍, 𝑎-𝑧) U+1D434-U+1D467
            codePoint in 0x1D434..0x1D44D -> ('A'.code + (codePoint - 0x1D434)).toChar().toString()
            codePoint in 0x1D44E..0x1D467 -> ('a'.code + (codePoint - 0x1D44E)).toChar().toString()

            // Mathematical Bold Italic (𝑨-𝒁, 𝒂-𝒛) U+1D468-U+1D49B
            codePoint in 0x1D468..0x1D481 -> ('A'.code + (codePoint - 0x1D468)).toChar().toString()
            codePoint in 0x1D482..0x1D49B -> ('a'.code + (codePoint - 0x1D482)).toChar().toString()

            // Mathematical Script (𝒜-𝒵, 𝒶-𝓏) U+1D49C-U+1D4CF
            codePoint in 0x1D49C..0x1D4B5 -> ('A'.code + (codePoint - 0x1D49C)).toChar().toString()
            codePoint in 0x1D4B6..0x1D4CF -> ('a'.code + (codePoint - 0x1D4B6)).toChar().toString()

            // Mathematical Bold Script (𝓐-𝓩, 𝓪-𝔃) U+1D4D0-U+1D503
            codePoint in 0x1D4D0..0x1D4E9 -> ('A'.code + (codePoint - 0x1D4D0)).toChar().toString()
            codePoint in 0x1D4EA..0x1D503 -> ('a'.code + (codePoint - 0x1D4EA)).toChar().toString()

            // Mathematical Fraktur (𝔄-𝔜, 𝔞-𝔷) U+1D504-U+1D537
            codePoint in 0x1D504..0x1D51C -> ('A'.code + (codePoint - 0x1D504)).toChar().toString()
            codePoint in 0x1D51E..0x1D537 -> ('a'.code + (codePoint - 0x1D51E)).toChar().toString()

            // Mathematical Double-Struck (𝔸-ℤ, 𝕒-𝕫) U+1D538-U+1D56B
            codePoint in 0x1D538..0x1D550 -> ('A'.code + (codePoint - 0x1D538)).toChar().toString()
            codePoint in 0x1D552..0x1D56B -> ('a'.code + (codePoint - 0x1D552)).toChar().toString()

            // Mathematical Bold Fraktur (𝕬-𝖅, 𝖆-𝖟) U+1D56C-U+1D59F
            codePoint in 0x1D56C..0x1D585 -> ('A'.code + (codePoint - 0x1D56C)).toChar().toString()
            codePoint in 0x1D586..0x1D59F -> ('a'.code + (codePoint - 0x1D586)).toChar().toString()

            // Mathematical Sans-Serif (𝖠-𝖹, 𝖺-𝗓) U+1D5A0-U+1D5D3
            codePoint in 0x1D5A0..0x1D5B9 -> ('A'.code + (codePoint - 0x1D5A0)).toChar().toString()
            codePoint in 0x1D5BA..0x1D5D3 -> ('a'.code + (codePoint - 0x1D5BA)).toChar().toString()

            // Mathematical Sans-Serif Bold (𝗔-𝗭, 𝗮-𝘇) U+1D5D4-U+1D607
            codePoint in 0x1D5D4..0x1D5ED -> ('A'.code + (codePoint - 0x1D5D4)).toChar().toString()
            codePoint in 0x1D5EE..0x1D607 -> ('a'.code + (codePoint - 0x1D5EE)).toChar().toString()

            // Mathematical Sans-Serif Italic (𝘈-𝘡, 𝘢-𝘻) U+1D608-U+1D63B
            codePoint in 0x1D608..0x1D621 -> ('A'.code + (codePoint - 0x1D608)).toChar().toString()
            codePoint in 0x1D622..0x1D63B -> ('a'.code + (codePoint - 0x1D622)).toChar().toString()

            // Mathematical Sans-Serif Bold Italic (𝘼-𝙕, 𝙖-𝙯) U+1D63C-U+1D66F
            codePoint in 0x1D63C..0x1D655 -> ('A'.code + (codePoint - 0x1D63C)).toChar().toString()
            codePoint in 0x1D656..0x1D66F -> ('a'.code + (codePoint - 0x1D656)).toChar().toString()

            // Mathematical Monospace (𝙰-𝚉, 𝚊-𝚣) U+1D670-U+1D6A3
            codePoint in 0x1D670..0x1D689 -> ('A'.code + (codePoint - 0x1D670)).toChar().toString()
            codePoint in 0x1D68A..0x1D6A3 -> ('a'.code + (codePoint - 0x1D68A)).toChar().toString()

            // Enclosed Alphanumerics (Ⓐ-Ⓩ, ⓐ-ⓩ) U+24B6-U+24E9
            codePoint in 0x24B6..0x24CF -> ('A'.code + (codePoint - 0x24B6)).toChar().toString()
            codePoint in 0x24D0..0x24E9 -> ('a'.code + (codePoint - 0x24D0)).toChar().toString()

            // Parenthesized Latin (⒜-⒵) U+249C-U+24B5
            codePoint in 0x249C..0x24B5 -> ('a'.code + (codePoint - 0x249C)).toChar().toString()

            // Squared Latin (🄰-🅉, 🅰-🆉) U+1F130-U+1F149, U+1F170-U+1F189
            codePoint in 0x1F130..0x1F149 -> ('A'.code + (codePoint - 0x1F130)).toChar().toString()
            codePoint in 0x1F170..0x1F189 -> ('A'.code + (codePoint - 0x1F170)).toChar().toString()

            // Regional Indicator Symbols (🇦-🇿) U+1F1E6-U+1F1FF
            codePoint in 0x1F1E6..0x1F1FF -> ('A'.code + (codePoint - 0x1F1E6)).toChar().toString()

            else -> Character.toChars(codePoint).concatToString()
        }
    }

    fun isMusicContent(video: HolodexVideoItem): Boolean {
        val videoLogId = "${video.id} ('${video.title.take(30)}...')"
        Timber.d("isMusicContent Checking: ID=${videoLogId}, Type=${video.type}, Topic=${video.topicId}, Songcount=${video.songcount ?: 0}, Chan='${video.channel.name.take(20)}'")

        // 1. Strongest Indicator: Positive Song Count
        if ((video.songcount ?: 0) > 0 || !video.songs.isNullOrEmpty()) {
            Timber.d("isMusicContent [PASS] ID=${videoLogId} via songcount > 0 or non-empty songs list.")
            return true
        }

        // 2. Strict Core Music Topics
        if (STRICT_CORE_MUSIC_TOPICS.contains(video.topicId)) {
            Timber.d("isMusicContent [PASS] ID=${videoLogId} via STRICT_CORE_MUSIC_TOPICS: ${video.topicId}")
            return true
        }

        // 3. Normalize and check title keywords
        val normalizedTitle = normalizeTitle(video.title).lowercase()

        if (musicKeywords.any { keyword -> normalizedTitle.contains(keyword) }) {
            Timber.d("isMusicContent [PASS] ID=${videoLogId} via musicKeyword in title (topic was '${video.topicId}').")
            return true
        }

        // 4. Specific Check for Music Shorts
        if (video.topicId == "shorts" || (video.type == "clip" && video.duration > 0 && video.duration <= 90 && video.topicId.isNullOrEmpty())) {
            if (musicKeywords.any { keyword -> normalizedTitle.contains(keyword) }) {
                Timber.d("isMusicContent [PASS] ID=${videoLogId} via music short (type/duration & title keyword).")
                return true
            }
        }

        // 5. Fallback for Generic Topics
        val potentiallyMusicRelatedTopics = setOf("3D_Stream", "FreeChat", "雑談", "misc", "unknown", null, "")
        if (potentiallyMusicRelatedTopics.contains(video.topicId)) {
            val channelNameLower = video.channel.name.lowercase()
            if (channelMusicKeywords.any { keyword -> channelNameLower.contains(keyword) }) {
                if (musicKeywords.any { keyword -> normalizedTitle.contains(keyword) }) {
                    Timber.d("isMusicContent [PASS] ID=${videoLogId} via generic topic, channel keyword, AND title keyword.")
                    return true
                }
            }
        }

        Timber.d("isMusicContent [FAIL] ID=${videoLogId}. No conditions met.")
        return false
    }
}