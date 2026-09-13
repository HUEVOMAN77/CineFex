package com.pageapp.cinefex.data.api

import com.pageapp.cinefex.data.model.ServerOption
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

data class PeliApiServerResponse(
    val status: String? = null,
    val movieId: Long? = null,
    val servers: List<ServerOption> = emptyList()
)

interface PeliApiService {

    @GET("movie/{id}/servers")
    suspend fun getLatinoServers(
        @Path("id") movieId: Long,
        @Query("lang") lang: String = "lat"
    ): PeliApiServerResponse

    companion object {
        private const val BASE_URL = "https://peliapi.com/api/"

        fun create(): PeliApiService {
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(PeliApiService::class.java)
        }
    }
}
