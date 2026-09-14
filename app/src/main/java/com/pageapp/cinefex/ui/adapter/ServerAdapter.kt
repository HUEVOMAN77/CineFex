package com.pageapp.cinefex.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.pageapp.cinefex.databinding.ItemServerBinding
import com.pageapp.cinefex.data.model.ServerOption

class ServerAdapter(
    private val servers: List<ServerOption>,
    private val onServerClick: (ServerOption) -> Unit
) : RecyclerView.Adapter<ServerAdapter.ServerViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ServerViewHolder {
        val binding = ItemServerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ServerViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ServerViewHolder, position: Int) {
        holder.bind(servers[position])
    }

    override fun getItemCount(): Int = servers.size

    inner class ServerViewHolder(private val binding: ItemServerBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(server: ServerOption) {
            binding.textViewServerName.text = server.name.ifEmpty { "Servidor Latino ${bindingAdapterPosition + 1}" }
            binding.textViewServerLanguage.text = if (server.language.contains("Latino", ignoreCase = true)) {
                server.language
            } else {
                "${server.language} [LATINO]"
            }
            binding.root.setOnClickListener {
                onServerClick(server)
            }
        }
    }
}
