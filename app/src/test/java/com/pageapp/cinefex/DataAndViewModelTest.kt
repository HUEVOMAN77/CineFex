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
    fun testTenFallbackServers() {
        val movieId = 550L
        val repository = MovieRepository()
        val defaultData = repository.getDefaultMovieLinkData(movieId)

        assertEquals(10, defaultData.servers.size)

        val vidsrcTo = defaultData.servers.find { it.name == "VidSrc TO" }
        assertNotNull(vidsrcTo)
        assertEquals("https://vidsrc.to/embed/movie/550", vidsrcTo?.embedUrl)

        val autoEmbed = defaultData.servers.find { it.name == "AutoEmbed" }
        assertNotNull(autoEmbed)
        assertEquals("https://autoembed.co/movie/tmdb/550", autoEmbed?.embedUrl)

        val moviesApi = defaultData.servers.find { it.name == "MoviesAPI" }
        assertNotNull(moviesApi)
        assertEquals("https://moviesapi.club/movie/550", moviesApi?.embedUrl)
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
