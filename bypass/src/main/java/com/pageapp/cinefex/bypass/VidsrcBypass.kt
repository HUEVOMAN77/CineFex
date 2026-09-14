package com.pageapp.cinefex.bypass

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Base64

object VidsrcBypass {
    private val client = OkHttpClient.Builder().followRedirects(true).build()

    suspend fun getStreamUrl(tmdbId: Int, type: String = "movie"): String? = withContext(Dispatchers.IO) {
        try {
            val embedUrl = "https://vidsrc.to/embed/$type/$tmdbId"
            val request = Request.Builder()
                .url(embedUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null
            val html = response.body?.string() ?: return@withContext null

            // VRF token / rot13 / base64 extraction decoding
            val vrfMatch = Regex("""data-vrf=["']([^"']+)["']""").find(html)
            val rawVrf = vrfMatch?.groupValues?.get(1) ?: ""
            val decodedVrf = decodeRot13AndBase64(rawVrf)

            if (decodedVrf.isNotEmpty()) {
                return@withContext "https://vidsrc.stream/hls/$tmdbId/playlist.m3u8?vrf=$decodedVrf"
            }
            return@withContext "https://vidsrc.to/embed/$type/$tmdbId?lang=lat"
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    private fun decodeRot13AndBase64(input: String): String {
        if (input.isEmpty()) return ""
        val rot13 = input.map { char ->
            when (char) {
                in 'a'..'m' -> char + 13
                in 'n'..'z' -> char - 13
                in 'A'..'M' -> char + 13
                in 'N'..'Z' -> char - 13
                else -> char
            }
        }.joinToString("")

        return try {
            String(Base64.getDecoder().decode(rot13))
        } catch (e: Exception) {
            rot13
        }
    }
}
