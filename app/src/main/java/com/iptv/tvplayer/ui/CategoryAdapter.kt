package com.iptv.tvplayer.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.iptv.tvplayer.R

class CategoryAdapter(
    private val categories: List<String>,
    private val onCategorySelected: (Int) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder>() {

    private var selectedPosition = 0

    inner class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tv_category_name)

        init {
            itemView.setOnClickListener {
                onCategorySelected(adapterPosition)
            }
            itemView.setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    val oldPos = selectedPosition
                    selectedPosition = adapterPosition
                    view.post {
                        notifyItemChanged(oldPos)
                        notifyItemChanged(selectedPosition)
                        onCategorySelected(adapterPosition)
                    }
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_category, parent, false)
        return CategoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        holder.tvName.text = categories[position]
        holder.itemView.isSelected = position == selectedPosition

        // Focus boundaries
        if (position == 0) {
            holder.itemView.nextFocusUpId = R.id.btn_settings_top
        } else {
            holder.itemView.nextFocusUpId = View.NO_ID
        }
        holder.itemView.nextFocusRightId = R.id.rv_channels
    }

    override fun getItemCount(): Int = categories.size

    fun setSelected(position: Int) {
        val oldPos = selectedPosition
        selectedPosition = position
        notifyItemChanged(oldPos)
        notifyItemChanged(selectedPosition)
    }
}
