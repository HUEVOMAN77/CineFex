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
                        it.embedUrl.isNotEmpty() && isExplicitlyLatino(it.language.orEmpty())
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
                val rawLanguage = document.getString("audio_language")
                    ?: document.getString("language")
                    ?: ""
                val rawIsLatino = document.getBoolean("is_latino")
                    ?: document.getBoolean("isLatino")
                    ?: isExplicitlyLatino(rawLanguage)
                val serversListRaw = document.get("servers") as? List<Map<String, Any>>

                val servers = serversListRaw?.mapNotNull { item ->
                    val name = item["name"] as? String ?: ""
                    val language = item["audio_language"] as? String
                        ?: item["language"] as? String
                        ?: ""
                    val embedUrl = item["embed_url"] as? String ?: (item["embedUrl"] as? String ?: "")
                    val itemIsLatino = (item["is_latino"] as? Boolean)
                        ?: (item["isLatino"] as? Boolean)
                        ?: isExplicitlyLatino(language)

                    if (embedUrl.isNotEmpty() && itemIsLatino && isExplicitlyLatino(language)) {
                        ServerOption(
                            name = name,
                            language = language,
                            embedUrl = embedUrl,
                            isLatino = true
                        )
                    } else null
                }?.sortedBy { it.name } ?: emptyList()

                val verifiedEmbedUrl = rawEmbedUrl.takeIf {
                    !it.isNullOrEmpty() && rawIsLatino && isExplicitlyLatino(rawLanguage)
                }

                if (servers.isNotEmpty() || verifiedEmbedUrl != null) {
                    return MovieLinkData(
                        embedUrl = verifiedEmbedUrl,
                        servers = servers
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // No se inventan servidores Latino. Un parámetro como lang=lat no
        // garantiza que el proveedor tenga audio o subtítulos en español.
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
                    if (embedUrl.isNotEmpty() && isExplicitlyLatino(lang)) {
                        extractedServers.add(
                            ServerOption(
                                name = "PeliApi PelisPlus (Latino)",
                                language = lang,
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
                    // El enlace devuelto también debe declarar el idioma; que la
                    // solicitud lleve lang=lat no demuestra que el proveedor lo
                    // haya respetado.
                    if (embedUrl.isNotEmpty() && isExplicitlyLatino(embedUrl)) {
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
        return MovieLinkData(
            embedUrl = null,
            servers = emptyList()
        )
    }

    private fun isExplicitlyLatino(language: String): Boolean {
        val normalized = language.trim().lowercase()
        if (normalized.isEmpty()) return false

        return normalized.contains("es-419") ||
                normalized.contains("es-lat") ||
                normalized.contains("es-mx") ||
                normalized.contains("español latino") ||
                normalized.contains("espanol latino") ||
                normalized.contains("latino") ||
                normalized.contains("latam") ||
                normalized == "lat" ||
                normalized.contains("lat/sub")
    }
}
