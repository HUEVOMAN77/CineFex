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
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.pageapp.cinefex.data.model.ServerOption
import com.pageapp.cinefex.databinding.ActivityMainBinding
import com.pageapp.cinefex.databinding.DialogServerSelectionBinding
import com.pageapp.cinefex.ui.adapter.CategoryAdapter
import com.pageapp.cinefex.ui.adapter.ServerAdapter
import com.pageapp.cinefex.ui.viewmodel.MainUiEvent
import com.pageapp.cinefex.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var categoryAdapter: CategoryAdapter
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
        categoryAdapter = CategoryAdapter(
            sections = emptyList(),
            onMovieClick = { movie ->
                viewModel.onMovieClicked(movie)
            },
            onLoadNextPage = { category ->
                viewModel.loadNextPage(category)
            }
        )

        binding.rvMovies.apply {
            layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.VERTICAL, false)
            adapter = categoryAdapter
        }
    }

    private fun setupListeners() {
        binding.btnRetry.setOnClickListener {
            viewModel.loadAllCategories()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        if (state.isLoading) {
                            binding.progressBar.visibility = View.VISIBLE
                            binding.rvMovies.visibility = View.GONE
                            binding.errorContainer.visibility = View.GONE
                        } else if (state.error != null) {
                            binding.progressBar.visibility = View.GONE
                            binding.rvMovies.visibility = View.GONE
                            binding.errorContainer.visibility = View.VISIBLE
                            binding.tvErrorMessage.text = state.error
                        } else {
                            binding.progressBar.visibility = View.GONE
                            binding.rvMovies.visibility = View.VISIBLE
                            binding.errorContainer.visibility = View.GONE
                            categoryAdapter.updateSections(state.sections)
                        }
                    }
                }

                launch {
                    viewModel.eventFlow.collect { event ->
                        when (event) {
                            is MainUiEvent.ShowServerSelection -> {
                                showServerSelectionDialog(event.movieTitle, event.servers)
                            }
                            is MainUiEvent.OpenPlayer -> {
                                openPlayerActivity(event.embedUrl)
                            }
                            is MainUiEvent.ShowToast -> {
                                Toast.makeText(this@MainActivity, event.message, Toast.LENGTH_SHORT).show()
                            }
                            is MainUiEvent.ShowLoadingDialog -> {
                                showLoading()
                            }
                            is MainUiEvent.HideLoadingDialog -> {
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
            layoutManager = LinearLayoutManager(this@MainActivity)
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
