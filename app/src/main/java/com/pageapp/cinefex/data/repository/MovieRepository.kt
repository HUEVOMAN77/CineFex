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
            ServerOption(name = "Voe", language = "SUB/LAT", embedUrl = "https://voe.sx/e/$movieId"),
            ServerOption(name = "FileMoon", language = "SUB/LAT", embedUrl = "https://filemoon.sx/e/$movieId"),
            ServerOption(name = "GoodStream", language = "SUB/LAT", embedUrl = "https://goodstream.one/e/$movieId"),
            ServerOption(name = "Vimeo", language = "SUB", embedUrl = "https://player.vimeo.com/video/$movieId"),
            ServerOption(name = "Cloudflare Stream", language = "SUB", embedUrl = "https://iframe.cloudflarestream.com/$movieId"),
            ServerOption(name = "Mux Player", language = "SUB", embedUrl = "https://stream.mux.com/$movieId.m3u8")
        )

        return MovieLinkData(
            embedUrl = null,
            servers = defaultServers
        )
    }
}
