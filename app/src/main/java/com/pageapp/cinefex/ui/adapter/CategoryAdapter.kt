package com.pageapp.cinefex.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.pageapp.cinefex.data.model.Movie
import com.pageapp.cinefex.data.repository.MovieCategory
import com.pageapp.cinefex.databinding.ItemCategoryRowBinding
import com.pageapp.cinefex.ui.viewmodel.CategorySection

class CategoryAdapter(
    private var sections: List<CategorySection>,
    private val onMovieClick: (Movie) -> Unit,
    private val onLoadNextPage: (MovieCategory) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder>() {

    fun updateSections(newSections: List<CategorySection>) {
        sections = newSections
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val binding = ItemCategoryRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CategoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        holder.bind(sections[position])
    }

    override fun getItemCount(): Int = sections.size

    inner class CategoryViewHolder(private val binding: ItemCategoryRowBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private val movieAdapter = MovieAdapter { movie -> onMovieClick(movie) }
        private val layoutManager = LinearLayoutManager(
            binding.root.context,
            LinearLayoutManager.HORIZONTAL,
            false
        )

        init {
            binding.rvHorizontalMovies.layoutManager = layoutManager
            binding.rvHorizontalMovies.adapter = movieAdapter

            binding.rvHorizontalMovies.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    if (dx > 0) { // Scrolling right
                        val visibleItemCount = layoutManager.childCount
                        val totalItemCount = layoutManager.itemCount
                        val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                        val currentPosition = bindingAdapterPosition
                        if (currentPosition != RecyclerView.NO_POSITION && currentPosition < sections.size) {
                            val section = sections[currentPosition]
                            if (!section.isLoadingMore && section.hasMorePages) {
                                // Trigger load when remaining items <= 5
                                if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount - 5
                                    && firstVisibleItemPosition >= 0
                                ) {
                                    onLoadNextPage(section.category)
                                }
                            }
                        }
                    }
                }
            })
        }

        fun bind(section: CategorySection) {
            binding.tvCategoryTitle.text = section.title
            movieAdapter.submitList(section.movies)
        }
    }
}
