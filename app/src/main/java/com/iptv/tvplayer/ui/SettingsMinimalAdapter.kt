package com.iptv.tvplayer.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.iptv.tvplayer.R

data class SettingItem(
    val name: String,
    val tag: String?,
    val isChecked: Boolean,
    val hasArrow: Boolean,
    val identifier: String // used for click handling (e.g. url or setting key)
)

class SettingsMinimalAdapter(
    private var items: List<SettingItem>,
    private val onItemSelected: (Int, SettingItem) -> Unit
) : RecyclerView.Adapter<SettingsMinimalAdapter.SettingsViewHolder>() {

    private var selectedPosition = 0

    inner class SettingsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tv_setting_name)
        val tvTag: TextView = itemView.findViewById(R.id.tv_setting_tag)
        val ivCheck: ImageView = itemView.findViewById(R.id.iv_setting_check)
        val ivArrow: TextView = itemView.findViewById(R.id.iv_setting_arrow)

        init {
            itemView.setOnClickListener {
                val pos = adapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    val oldPos = selectedPosition
                    selectedPosition = pos
                    notifyItemChanged(oldPos)
                    notifyItemChanged(selectedPosition)
                    onItemSelected(pos, items[pos])
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SettingsViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_setting_minimal, parent, false)
        return SettingsViewHolder(view)
    }

    override fun onBindViewHolder(holder: SettingsViewHolder, position: Int) {
        val item = items[position]
        holder.tvName.text = item.name
        
        if (item.tag != null) {
            holder.tvTag.text = item.tag
            holder.tvTag.visibility = View.VISIBLE
        } else {
            holder.tvTag.visibility = View.GONE
        }

        holder.ivCheck.visibility = if (item.isChecked) View.VISIBLE else View.GONE
        holder.ivArrow.visibility = if (item.hasArrow) View.VISIBLE else View.GONE

        // Focus boundaries
        if (position == 0) {
            holder.itemView.nextFocusUpId = R.id.btn_settings_back
        } else {
            holder.itemView.nextFocusUpId = View.NO_ID
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<SettingItem>, selectedPos: Int) {
        items = newItems
        selectedPosition = selectedPos
        notifyDataSetChanged()
    }
}
