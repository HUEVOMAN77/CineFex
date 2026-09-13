package com.pageapp.cinefex.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pageapp.cinefex.data.model.Movie
import com.pageapp.cinefex.data.model.MovieLinkData
import com.pageapp.cinefex.data.model.ServerOption
import com.pageapp.cinefex.data.repository.MovieRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class MoviesUiState {
    object Loading : MoviesUiState()
    data class Success(val movies: List<Movie>) : MoviesUiState()
    data class Error(val message: String) : MoviesUiState()
}

sealed class UiEvent {
    data class ShowServerSelection(val movieTitle: String, val servers: List<ServerOption>) : UiEvent()
    data class OpenPlayer(val embedUrl: String) : UiEvent()
    data class ShowToast(val message: String) : UiEvent()
    object ShowLoadingDialog : UiEvent()
    object HideLoadingDialog : UiEvent()
}

class MainViewModel(
    private val repository: MovieRepository = MovieRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow<MoviesUiState>(MoviesUiState.Loading)
    val uiState: StateFlow<MoviesUiState> = _uiState.asStateFlow()

    private val _eventFlow = MutableSharedFlow<UiEvent>()
    val eventFlow: SharedFlow<UiEvent> = _eventFlow.asSharedFlow()

    init {
        loadPopularMovies()
    }

    fun loadPopularMovies() {
        viewModelScope.launch {
            _uiState.value = MoviesUiState.Loading
            repository.getPopularMovies()
                .onSuccess { movies ->
                    _uiState.value = MoviesUiState.Success(movies)
                }
                .onFailure { throwable ->
                    _uiState.value = MoviesUiState.Error(throwable.localizedMessage ?: "Error al cargar las películas")
                }
        }
    }

    fun onMovieClicked(movie: Movie) {
        viewModelScope.launch {
            _eventFlow.emit(UiEvent.ShowLoadingDialog)
            val linkData = repository.getMovieLinkData(movie.id)
            _eventFlow.emit(UiEvent.HideLoadingDialog)

            val servers = linkData.servers
            val singleEmbedUrl = linkData.embedUrl

            if (servers.isNotEmpty()) {
                _eventFlow.emit(UiEvent.ShowServerSelection(movie.title, servers))
            } else if (!singleEmbedUrl.isNullOrEmpty()) {
                _eventFlow.emit(UiEvent.OpenPlayer(singleEmbedUrl))
            } else {
                _eventFlow.emit(UiEvent.ShowToast("No hay servidores disponibles para esta película"))
            }
        }
    }

    fun onServerSelected(server: ServerOption) {
        viewModelScope.launch {
            if (server.embedUrl.isNotEmpty()) {
                _eventFlow.emit(UiEvent.OpenPlayer(server.embedUrl))
            } else {
                _eventFlow.emit(UiEvent.ShowToast("Enlace de servidor no válido"))
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
