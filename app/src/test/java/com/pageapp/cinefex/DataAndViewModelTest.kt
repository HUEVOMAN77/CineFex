package com.pageapp.cinefex.data

import com.pageapp.cinefex.data.api.TmdbApiService
import com.pageapp.cinefex.data.model.Movie
import com.pageapp.cinefex.data.model.MovieResponse
import com.pageapp.cinefex.data.model.ServerOption
import com.pageapp.cinefex.data.repository.MovieCategory
import com.pageapp.cinefex.data.repository.MovieRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DataAndViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testMovieModelPosterUrl() {
        val movie = Movie(
            id = 12345,
            title = "Test Movie",
            overview = "Test Overview",
            posterPath = "/test_poster.jpg"
        )
        assertEquals("https://image.tmdb.org/t/p/w500/test_poster.jpg", movie.fullPosterUrl)
    }

    @Test
    fun testServerOptionSorting() {
        val servers = listOf(
            ServerOption(name = "Zeus", language = "Latino", embedUrl = "https://server1.com"),
            ServerOption(name = "Fast", language = "Castellano", embedUrl = "https://server2.com"),
            ServerOption(name = "Ultra", language = "Subtitulado", embedUrl = "https://server3.com")
        )

        val sorted = servers.sortedBy { it.language }

        assertEquals("Castellano", sorted[0].language)
        assertEquals("Latino", sorted[1].language)
        assertEquals("Subtitulado", sorted[2].language)
    }

    @Test
    fun testCentralizedLatinoServers() {
        val movieId = 550L
        val repository = MovieRepository()
        val defaultData = repository.getDefaultMovieLinkData(movieId)

        assertTrue(defaultData.servers.size >= 4)

        val principalLatino = defaultData.servers.find { it.name.contains("Servidor 1") || it.name.contains("Principal") }
        assertNotNull(principalLatino)
        assertTrue(principalLatino!!.embedUrl.contains("lang=lat"))
        assertTrue(principalLatino.isLatino)

        val secundarioLatino = defaultData.servers.find { it.name.contains("Servidor 2") || it.name.contains("Secundario") }
        assertNotNull(secundarioLatino)
        assertTrue(secundarioLatino!!.embedUrl.contains("lang=es-lat"))
        assertTrue(secundarioLatino.isLatino)
    }

    @Test
    fun testMovieCategoriesEnum() {
        assertEquals(4, MovieCategory.values().size)
    }

    @Test
    fun testMovieResponseData() {
        val response = MovieResponse(
            page = 1,
            results = listOf(
                Movie(id = 1, title = "Movie 1", overview = "Overview 1", posterPath = "/path1.jpg")
            ),
            totalPages = 10,
            totalResults = 100
        )
        assertEquals(1, response.page)
        assertEquals(1, response.results.size)
        assertEquals("Movie 1", response.results[0].title)
    }

    @Test
    fun testTmdbApiKeyConstant() {
        assertEquals("f369892a9607bf16bad402a98b662989", TmdbApiService.API_KEY)
    }
}
