package com.pageapp.cinefex.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.pageapp.cinefex.data.api.TmdbApiService
import com.pageapp.cinefex.data.model.Movie
import com.pageapp.cinefex.data.model.MovieLinkData
import com.pageapp.cinefex.data.model.ServerOption
import kotlinx.coroutines.tasks.await
import java.lang.Exception

class MovieRepository(
    private val apiService: TmdbApiService = TmdbApiService.create(),
    private val firestoreProvider: () -> FirebaseFirestore = { FirebaseFirestore.getInstance() }
) {
    suspend fun getPopularMovies(page: Int = 1): Result<List<Movie>> {
        return try {
            val response = apiService.getPopularMovies(page)
            Result.success(response.results)
        } catch (e: Exception) {
            Result.failure(e)
        }
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

            // Fallback default servers if document doesn't exist or servers are empty
            getDefaultMovieLinkData(movieId)
        } catch (e: Exception) {
            e.printStackTrace()
            getDefaultMovieLinkData(movieId)
        }
    }

    fun getDefaultMovieLinkData(movieId: Long): MovieLinkData {
        val defaultServers = listOf(
            ServerOption(name = "Ultra", language = "SUB", embedUrl = "https://vidsrc.to/embed/movie/$movieId"),
            ServerOption(name = "Zeus", language = "LAT/SUB", embedUrl = "https://autoembed.to/movie/tmdb/$movieId"),
            ServerOption(name = "Fast", language = "SUB", embedUrl = "https://multiembed.mov/directstream.php?video_id=$movieId&tmdb=1")
        ).sortedBy { it.language }

        return MovieLinkData(
            embedUrl = null,
            servers = defaultServers
        )
    }
}
