package com.pageapp.cinefex.data.repository

import com.google.firebase.firestore.FirebaseFirestore
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
        return try {
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
                    val language = item["language"] as? String ?: ""
                    val embedUrl = item["embed_url"] as? String ?: (item["embedUrl"] as? String ?: "")
                    if (embedUrl.isNotEmpty()) {
                        ServerOption(name = name, language = language, embedUrl = embedUrl)
                    } else null
                }?.sortedBy { it.language } ?: emptyList()

                if (servers.isNotEmpty() || !rawEmbedUrl.isNullOrEmpty()) {
                    return MovieLinkData(
                        embedUrl = rawEmbedUrl,
                        servers = servers
                    )
                }
            }

            getDefaultMovieLinkData(movieId)
        } catch (e: Exception) {
            e.printStackTrace()
            getDefaultMovieLinkData(movieId)
        }
    }

    fun getDefaultMovieLinkData(movieId: Long): MovieLinkData {
        val defaultServers = listOf(
            ServerOption(name = "EmbedSU", language = "LAT/SUB", embedUrl = "https://embed.su/embed/movie/$movieId"),
            ServerOption(name = "VidSrc IN", language = "SUB", embedUrl = "https://vidsrc.in/embed/movie/$movieId"),
            ServerOption(name = "AutoEmbed CC", language = "LAT/SUB", embedUrl = "https://autoembed.cc/movie/tmdb/$movieId"),
            ServerOption(name = "VidLink", language = "SUB", embedUrl = "https://vidlink.pro/movie/$movieId"),
            ServerOption(name = "2Embed", language = "SUB", embedUrl = "https://www.2embed.cc/embed/$movieId"),
            ServerOption(name = "Multi", language = "LAT/SUB", embedUrl = "https://multiembed.mov/directstream.php?video_id=$movieId&tmdb=1")
        ).sortedBy { it.language }

        return MovieLinkData(
            embedUrl = null,
            servers = defaultServers
        )
    }
}
