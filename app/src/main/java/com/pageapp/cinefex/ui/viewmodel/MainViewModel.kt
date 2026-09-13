package com.pageapp.cinefex.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pageapp.cinefex.data.model.Movie
import com.pageapp.cinefex.data.model.ServerOption
import com.pageapp.cinefex.data.repository.MovieCategory
import com.pageapp.cinefex.data.repository.MovieRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CategorySection(
    val category: MovieCategory,
    val title: String,
    val movies: List<Movie> = emptyList(),
    val currentPage: Int = 1,
    val isLoadingMore: Boolean = false,
    val hasMorePages: Boolean = true
)

data class MainUiState(
    val isLoading: Boolean = true,
    val sections: List<CategorySection> = emptyList(),
    val error: String? = null
)

sealed class MainUiEvent {
    data class ShowServerSelection(val movieTitle: String, val servers: List<ServerOption>) : MainUiEvent()
    data class OpenPlayer(val embedUrl: String) : MainUiEvent()
    data class ShowToast(val message: String) : MainUiEvent()
    object ShowLoadingDialog : MainUiEvent()
    object HideLoadingDialog : MainUiEvent()
}

class MainViewModel(
    private val repository: MovieRepository = MovieRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _eventFlow = MutableSharedFlow<MainUiEvent>()
    val eventFlow: SharedFlow<MainUiEvent> = _eventFlow.asSharedFlow()

    private val categoriesConfig = listOf(
        Pair(MovieCategory.NOW_PLAYING, "Estrenos"),
        Pair(MovieCategory.POPULAR, "Tendencias"),
        Pair(MovieCategory.TOP_RATED, "Mejor Valoradas"),
        Pair(MovieCategory.ACTION, "Acción")
    )

    init {
        loadAllCategories()
    }

    fun loadAllCategories() {
        viewModelScope.launch {
            _uiState.value = MainUiState(isLoading = true)
            val initialSections = mutableListOf<CategorySection>()
            var errorMessage: String? = null

            for ((category, title) in categoriesConfig) {
                val result = repository.getMoviesByCategory(category, page = 1)
                result.onSuccess { movies ->
                    initialSections.add(
                        CategorySection(
                            category = category,
                            title = title,
                            movies = movies,
                            currentPage = 1,
                            hasMorePages = movies.isNotEmpty()
                        )
                    )
                }.onFailure { throwable ->
                    if (errorMessage == null) {
                        errorMessage = throwable.localizedMessage ?: "Error al cargar categorías"
                    }
                }
            }

            if (initialSections.isNotEmpty()) {
                _uiState.value = MainUiState(isLoading = false, sections = initialSections)
            } else {
                _uiState.value = MainUiState(isLoading = false, error = errorMessage ?: "Error desconocido")
            }
        }
    }

    fun loadNextPage(category: MovieCategory) {
        val currentSections = _uiState.value.sections.toMutableList()
        val index = currentSections.indexOfFirst { it.category == category }
        if (index == -1) return

        val section = currentSections[index]
        if (section.isLoadingMore || !section.hasMorePages) return

        currentSections[index] = section.copy(isLoadingMore = true)
        _uiState.value = _uiState.value.copy(sections = currentSections)

        viewModelScope.launch {
            val nextPage = section.currentPage + 1
            repository.getMoviesByCategory(category, page = nextPage)
                .onSuccess { newMovies ->
                    val updatedSections = _uiState.value.sections.toMutableList()
                    val idx = updatedSections.indexOfFirst { it.category == category }
                    if (idx != -1) {
                        val currentSec = updatedSections[idx]
                        val combinedList = currentSec.movies + newMovies
                        updatedSections[idx] = currentSec.copy(
                            movies = combinedList,
                            currentPage = nextPage,
                            isLoadingMore = false,
                            hasMorePages = newMovies.isNotEmpty()
                        )
                        _uiState.value = _uiState.value.copy(sections = updatedSections)
                    }
                }
                .onFailure {
                    val updatedSections = _uiState.value.sections.toMutableList()
                    val idx = updatedSections.indexOfFirst { it.category == category }
                    if (idx != -1) {
                        updatedSections[idx] = updatedSections[idx].copy(isLoadingMore = false)
                        _uiState.value = _uiState.value.copy(sections = updatedSections)
                    }
                }
        }
    }

    fun onMovieClicked(movie: Movie) {
        viewModelScope.launch {
            _eventFlow.emit(MainUiEvent.ShowLoadingDialog)
            val linkData = repository.getMovieLinkData(movie.id)
            _eventFlow.emit(MainUiEvent.HideLoadingDialog)

            val servers = linkData.servers
            val singleEmbedUrl = linkData.embedUrl

            if (servers.isNotEmpty()) {
                _eventFlow.emit(MainUiEvent.ShowServerSelection(movie.title, servers))
            } else if (!singleEmbedUrl.isNullOrEmpty()) {
                _eventFlow.emit(MainUiEvent.OpenPlayer(singleEmbedUrl))
            } else {
                _eventFlow.emit(MainUiEvent.ShowToast("No hay servidores disponibles para esta película"))
            }
        }
    }

    fun onServerSelected(server: ServerOption) {
        viewModelScope.launch {
            if (server.embedUrl.isNotEmpty()) {
                _eventFlow.emit(MainUiEvent.OpenPlayer(server.embedUrl))
            } else {
                _eventFlow.emit(MainUiEvent.ShowToast("Enlace de servidor no válido"))
            }
        }
    }
}

class MainViewModelFactory(
    private val repository: MovieRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
