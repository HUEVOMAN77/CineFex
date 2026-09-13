package com.pageapp.cinefex.ui

import android.app.ProgressDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.pageapp.cinefex.data.model.ServerOption
import com.pageapp.cinefex.databinding.ActivityMainBinding
import com.pageapp.cinefex.databinding.DialogServerSelectionBinding
import com.pageapp.cinefex.ui.adapter.MovieAdapter
import com.pageapp.cinefex.ui.adapter.ServerAdapter
import com.pageapp.cinefex.ui.viewmodel.MainViewModel
import com.pageapp.cinefex.ui.viewmodel.MoviesUiState
import com.pageapp.cinefex.ui.viewmodel.UiEvent
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var movieAdapter: MovieAdapter
    private var progressDialog: ProgressDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupListeners()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        movieAdapter = MovieAdapter { movie ->
            viewModel.onMovieClicked(movie)
        }
        binding.rvMovies.apply {
            layoutManager = GridLayoutManager(this@MainActivity, 3)
            adapter = movieAdapter
        }
    }

    private fun setupListeners() {
        binding.btnRetry.setOnClickListener {
            viewModel.loadPopularMovies()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        when (state) {
                            is MoviesUiState.Loading -> {
                                binding.progressBar.visibility = View.VISIBLE
                                binding.rvMovies.visibility = View.GONE
                                binding.errorContainer.visibility = View.GONE
                            }
                            is MoviesUiState.Success -> {
                                binding.progressBar.visibility = View.GONE
                                binding.rvMovies.visibility = View.VISIBLE
                                binding.errorContainer.visibility = View.GONE
                                movieAdapter.submitList(state.movies)
                            }
                            is MoviesUiState.Error -> {
                                binding.progressBar.visibility = View.GONE
                                binding.rvMovies.visibility = View.GONE
                                binding.errorContainer.visibility = View.VISIBLE
                                binding.tvErrorMessage.text = state.message
                            }
                        }
                    }
                }

                launch {
                    viewModel.eventFlow.collect { event ->
                        when (event) {
                            is UiEvent.ShowServerSelection -> {
                                showServerSelectionDialog(event.movieTitle, event.servers)
                            }
                            is UiEvent.OpenPlayer -> {
                                openPlayerActivity(event.embedUrl)
                            }
                            is UiEvent.ShowToast -> {
                                Toast.makeText(this@MainActivity, event.message, Toast.LENGTH_SHORT).show()
                            }
                            is UiEvent.ShowLoadingDialog -> {
                                showLoading()
                            }
                            is UiEvent.HideLoadingDialog -> {
                                hideLoading()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun showServerSelectionDialog(title: String, servers: List<ServerOption>) {
        val bottomSheetDialog = BottomSheetDialog(this)
        val dialogBinding = DialogServerSelectionBinding.inflate(layoutInflater)
        bottomSheetDialog.setContentView(dialogBinding.root)

        dialogBinding.tvDialogTitle.text = "Servidores para: $title"

        val sortedServers = servers.sortedBy { it.language }
        val adapter = ServerAdapter(sortedServers) { selectedServer ->
            bottomSheetDialog.dismiss()
            viewModel.onServerSelected(selectedServer)
        }

        dialogBinding.rvServers.apply {
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this@MainActivity)
            this.adapter = adapter
        }

        bottomSheetDialog.show()
    }

    private fun openPlayerActivity(embedUrl: String) {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_EMBED_URL, embedUrl)
        }
        startActivity(intent)
    }

    @Suppress("DEPRECATION")
    private fun showLoading() {
        if (progressDialog == null) {
            progressDialog = ProgressDialog(this).apply {
                setMessage("Cargando servidores...")
                setCancelable(false)
            }
        }
        progressDialog?.show()
    }

    private fun hideLoading() {
        progressDialog?.dismiss()
    }
}
