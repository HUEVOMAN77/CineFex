package com.pageapp.cinefex.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.pageapp.cinefex.data.api.PeliApiService
import com.pageapp.cinefex.data.api.TmdbApiService
import com.pageapp.cinefex.data.model.Movie
import com.pageapp.cinefex.data.model.MovieLinkData
import com.pageapp.cinefex.data.model.ServerOption
import kotlinx.coroutines.tasks.await
import java.lang.Exception

enum class MovieCategory {
    NOW_PLAYING,
    POPULAR,
    TOP_RATED,
    ACTION
}

class MovieRepository(
    private val apiService: TmdbApiService = TmdbApiService.create(),
    private val peliApiService: PeliApiService = PeliApiService.create(),
    private val firestoreProvider: () -> FirebaseFirestore = { FirebaseFirestore.getInstance() }
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
        // 1. First try centralized PeliAPI endpoint for clean Latino streams
        try {
            val peliApiResponse = peliApiService.getLatinoServers(movieId)
            if (peliApiResponse.servers.isNotEmpty()) {
                val latinoServers = peliApiResponse.servers.filter { it.embedUrl.isNotEmpty() && !it.language.contains("-ENG", ignoreCase = true) }
                if (latinoServers.isNotEmpty()) {
                    return MovieLinkData(servers = latinoServers)
                }
            }
        } catch (e: Exception) {
            // PeliAPI backend fallback
            e.printStackTrace()
        }

        // 2. Try Firestore custom links
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

        // 3. Fallback default Latino servers
        return getDefaultMovieLinkData(movieId)
    }

    fun getDefaultMovieLinkData(movieId: Long): MovieLinkData {
        val defaultLatinoServers = listOf(
            ServerOption(
                name = "Servidor Latino Principal (-LAT)",
                language = "Español Latino",
                embedUrl = "https://embed.su/embed/movie/$movieId?lang=lat",
                isLatino = true
            ),
            ServerOption(
                name = "Servidor Latino Secundario (-ESL)",
                language = "Español Latino",
                embedUrl = "https://autoembed.co/movie/tmdb/$movieId?lang=es-lat",
                isLatino = true
            ),
            ServerOption(
                name = "Servidor Latino Dual (-LAT)",
                language = "Español Latino / Dual",
                embedUrl = "https://multiembed.mov/directstream.php?video_id=$movieId&tmdb=1&lang=lat",
                isLatino = true
            ),
            ServerOption(
                name = "Servidor Latino Fast (-LAT)",
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
