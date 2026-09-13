package com.pageapp.cinefex.data.model

import com.google.gson.annotations.SerializedName

data class MovieResponse(
    @SerializedName("page")
    val page: Int,
    @SerializedName("results")
    val results: List<Movie>,
    @SerializedName("total_pages")
    val totalPages: Int,
    @SerializedName("total_results")
    val totalResults: Int
)

data class Movie(
    @SerializedName("id")
    val id: Long,
    @SerializedName("title")
    val title: String,
    @SerializedName("overview")
    val overview: String?,
    @SerializedName("poster_path")
    val posterPath: String?
) {
    val fullPosterUrl: String?
        get() = posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }
}

data class ServerOption(
    val name: String = "",
    val language: String = "Español Latino",
    @SerializedName("embed_url")
    val embedUrl: String = "",
    val isLatino: Boolean = true
)

data class MovieLinkData(
    val embedUrl: String? = null,
    val servers: List<ServerOption> = emptyList()
)
