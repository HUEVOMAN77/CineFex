package com.pageapp.cinefex.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.pageapp.cinefex.data.api.PeliApiService
import com.pageapp.cinefex.data.api.TmdbApiService
import com.pageapp.cinefex.data.model.Movie
import com.pageapp.cinefex.data.model.MovieLinkData
import com.pageapp.cinefex.data.model.ServerOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.lang.Exception
import java.net.URLEncoder

enum class MovieCategory {
    NOW_PLAYING,
    POPULAR,
    TOP_RATED,
    ACTION
}

class MovieRepository(
    private val apiService: TmdbApiService = TmdbApiService.create(),
    private val peliApiService: PeliApiService = PeliApiService.create(),
    private val firestoreProvider: () -> FirebaseFirestore = { FirebaseFirestore.getInstance() },
    private val httpClient: OkHttpClient = OkHttpClient.Builder().followRedirects(true).build()
) {
    suspend fun getMoviesByCategory(category: MovieCategory, page: Int = 1): Result<List<Movie>> {
        return try {
            val response = when (category) {
                MovieCategory.NOW_PLAYING -> apiService.getNowPlayingMovies(page)
                MovieCategory.POPULAR -> apiService.getPopularMovies(page)
                MovieCategory.TOP_RATED -> apiService.getTopRatedMovies(page)
                MovieCategory.ACTION -> apiService.getDiscoverMovies(withGenres = "28", page = page)
            }
            Result.success(response.results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPopularMovies(page: Int = 1): Result<List<Movie>> {
        return getMoviesByCategory(MovieCategory.POPULAR, page)
    }

    suspend fun getMovieLinkData(movieId: Long): MovieLinkData {
        // 1. PeliApi Native Latino Extraction Strategy (PelisPlus & RePelisHD scraper mapping)
        val peliApiLatinoServers = extractPeliApiLatinoServers(movieId)
        if (peliApiLatinoServers.isNotEmpty()) {
            return MovieLinkData(servers = peliApiLatinoServers)
        }

        // 2. Try Centralized PeliAPI Endpoint
        try {
            val peliApiResponse = peliApiService.getLatinoServers(movieId)
            if (peliApiResponse.servers.isNotEmpty()) {
                val latinoServers = peliApiResponse.servers.filter {
                    it.embedUrl.isNotEmpty() && !it.language.contains("-ENG", ignoreCase = true)
                }
                if (latinoServers.isNotEmpty()) {
                    return MovieLinkData(servers = latinoServers)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. Try Firestore Custom Links
        try {
            val firestore = firestoreProvider()
            val document = firestore.collection("movies_links")
                .document(movieId.toString())
                .get()
                .await()

            if (document.exists()) {
                val rawEmbedUrl = document.getString("embed_url")
                val serversListRaw = document.get("servers") as? List<Map<String, Any>>

                val servers = serversListRaw?.mapNotNull { item ->
                    val name = item["name"] as? String ?: ""
                    val language = item["language"] as? String ?: "Español Latino"
                    val embedUrl = item["embed_url"] as? String ?: (item["embedUrl"] as? String ?: "")

                    val isEnglishOnly = language.contains("SUB-ONLY", ignoreCase = true) ||
                            language.contains("-ENG", ignoreCase = true) ||
                            language.equals("English", ignoreCase = true)

                    if (embedUrl.isNotEmpty() && !isEnglishOnly) {
                        ServerOption(
                            name = name,
                            language = if (language.contains("Latino", ignoreCase = true)) language else "$language (Latino)",
                            embedUrl = embedUrl,
                            isLatino = true
                        )
                    } else null
                }?.sortedBy { it.name } ?: emptyList()

                if (servers.isNotEmpty() || !rawEmbedUrl.isNullOrEmpty()) {
                    return MovieLinkData(
                        embedUrl = rawEmbedUrl,
                        servers = servers
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 4. Default Latino Server Providers Fallback
        return getDefaultMovieLinkData(movieId)
    }

    private suspend fun extractPeliApiLatinoServers(movieId: Long): List<ServerOption> = withContext(Dispatchers.IO) {
        val extractedServers = mutableListOf<ServerOption>()
        try {
            // PeliApi Provider 1: PelisPlus Latino Resolver
            val pelisPlusUrl = "https://pelisplus.gd/pelicula/$movieId"
            val request = Request.Builder()
                .url(pelisPlusUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val html = response.body?.string() ?: ""
                val regex = Regex("""data-url=["']([^"']+)["'][^>]*data-name=["']([^"']+)["']""")
                val matches = regex.findAll(html)
                for (match in matches) {
                    val embedUrl = match.groupValues[1]
                    val lang = match.groupValues[2]
                    if (embedUrl.isNotEmpty() && !lang.contains("Subtitulado", ignoreCase = true) && !lang.contains("Ingles", ignoreCase = true)) {
                        extractedServers.add(
                            ServerOption(
                                name = "PeliApi PelisPlus (Latino)",
                                language = "Español Latino",
                                embedUrl = embedUrl,
                                isLatino = true
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            // PeliApi Provider 2: RePelisHD VerHDLink Player Resolver
            val verHdLinkUrl = "https://verhdlink.cam/embed.php?id=$movieId&lang=lat"
            val request = Request.Builder()
                .url(verHdLinkUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val html = response.body?.string() ?: ""
                val linkRegex = Regex("""data-link=["']([^"']+)["']""")
                val matches = linkRegex.findAll(html)
                for (match in matches) {
                    var embedUrl = match.groupValues[1]
                    if (embedUrl.startsWith("//")) embedUrl = "https:$embedUrl"
                    if (embedUrl.isNotEmpty()) {
                        extractedServers.add(
                            ServerOption(
                                name = "PeliApi RePelisHD (Latino)",
                                language = "Español Latino",
                                embedUrl = embedUrl,
                                isLatino = true
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return@withContext extractedServers
    }

    fun getDefaultMovieLinkData(movieId: Long): MovieLinkData {
        val defaultLatinoServers = listOf(
            ServerOption(
                name = "PeliApi Latino Principal (-LAT)",
                language = "Español Latino",
                embedUrl = "https://embed.su/embed/movie/$movieId?lang=lat",
                isLatino = true
            ),
            ServerOption(
                name = "PeliApi Latino Secundario (-ESL)",
                language = "Español Latino",
                embedUrl = "https://autoembed.co/movie/tmdb/$movieId?lang=es-lat",
                isLatino = true
            ),
            ServerOption(
                name = "PeliApi Latino Dual (-LAT)",
                language = "Español Latino / Dual",
                embedUrl = "https://multiembed.mov/directstream.php?video_id=$movieId&tmdb=1&lang=lat",
                isLatino = true
            ),
            ServerOption(
                name = "PeliApi Latino Fast (-LAT)",
                language = "Español Latino",
                embedUrl = "https://vidsrc.to/embed/movie/$movieId?lang=lat",
                isLatino = true
            )
        )

        return MovieLinkData(
            embedUrl = null,
            servers = defaultLatinoServers
        )
    }
}
