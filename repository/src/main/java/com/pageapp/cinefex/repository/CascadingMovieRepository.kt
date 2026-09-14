package com.pageapp.cinefex.repository

import android.content.Context
import com.pageapp.cinefex.bypass.VidsrcBypass
import com.pageapp.cinefex.cuevana.CuevanaScraper
import com.pageapp.cinefex.player.PlayerManager
import androidx.media3.exoplayer.ExoPlayer

class CascadingMovieRepository(private val context: Context) {

    suspend fun resolveSpanishStreamUrl(tmdbId: Int, movieTitle: String): String? {
        // Capa 1 - Extracción Directa: vidsrc-bypass
        val bypassUrl = VidsrcBypass.getStreamUrl(tmdbId, "movie")
        if (!bypassUrl.isNullOrEmpty()) {
            if (bypassUrl.contains("lang=lat") || bypassUrl.contains("es-lat") || bypassUrl.contains(".m3u8")) {
                return bypassUrl
            }
        }

        // Capa 2 - Fallback Latino: cuevana3-scraper
        val cuevanaLinks = CuevanaScraper.getLinks(movieTitle)
        if (cuevanaLinks.isNotEmpty()) {
            return cuevanaLinks.first()
        }

        return bypassUrl ?: "https://embed.su/embed/movie/$tmdbId?lang=lat"
    }
}
