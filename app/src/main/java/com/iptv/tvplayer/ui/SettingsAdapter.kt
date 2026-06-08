package com.iptv.tvplayer.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.iptv.tvplayer.R

class SettingsAdapter(
    private var items: List<String>,
    private val triggerOnFocus: Boolean = false,
    private val onItemSelected: (Int) -> Unit
) : RecyclerView.Adapter<SettingsAdapter.SettingsViewHolder>() {

    private var selectedPosition = 0

    inner class SettingsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tv_setting_name)

        init {
            itemView.setOnClickListener {
                val pos = adapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onItemSelected(pos)
                }
            }
            itemView.setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    val pos = adapterPosition
                    if (pos != RecyclerView.NO_POSITION) {
                        val oldPos = selectedPosition
                        selectedPosition = pos
                        view.post {
                            if (oldPos != -1) notifyItemChanged(oldPos)
                            notifyItemChanged(selectedPosition)
                            if (triggerOnFocus) {
                                onItemSelected(pos)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SettingsViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_setting, parent, false)
        return SettingsViewHolder(view)
    }

    override fun onBindViewHolder(holder: SettingsViewHolder, position: Int) {
        holder.tvName.text = items[position]
        holder.itemView.isSelected = position == selectedPosition
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<String>) {
        items = newItems
        selectedPosition = 0
        notifyDataSetChanged()
    }
}
