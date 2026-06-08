package com.iptv.tvplayer.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.iptv.tvplayer.R
import com.iptv.tvplayer.data.Channel

class ChannelAdapter(
    private var channels: List<Channel>,
    private val onChannelSelected: (Channel, Int) -> Unit
) : RecyclerView.Adapter<ChannelAdapter.ChannelViewHolder>() {

    private var selectedPosition = -1

    inner class ChannelViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvNumber: TextView = itemView.findViewById(R.id.tv_channel_number)
        val tvName: TextView = itemView.findViewById(R.id.tv_channel_name)
        val tvEpg: TextView = itemView.findViewById(R.id.tv_channel_epg)

        init {
            itemView.setOnClickListener {
                onChannelSelected(channels[adapterPosition], adapterPosition)
            }
            itemView.setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    val oldPos = selectedPosition
                    selectedPosition = adapterPosition
                    view.post {
                        if (oldPos != -1) notifyItemChanged(oldPos)
                        notifyItemChanged(selectedPosition)
                    }
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_channel, parent, false)
        return ChannelViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) {
        val channel = channels[position]
        holder.tvNumber.text = String.format("%03d", position + 1)
        holder.tvName.text = channel.name
        holder.itemView.isSelected = position == selectedPosition
        
        val epgInfo = com.iptv.tvplayer.data.EpgManager.getCurrentAndNextProgram(channel.name)
        if (epgInfo.first != null) {
            holder.tvEpg.text = "${epgInfo.first!!.startTimeStr} ${epgInfo.first!!.title}"
        } else {
            holder.tvEpg.text = "精彩节目"
        }

        // Focus boundaries
        holder.itemView.nextFocusLeftId = R.id.rv_categories
    }

    override fun getItemCount(): Int = channels.size

    fun updateData(newChannels: List<Channel>) {
        channels = newChannels
        selectedPosition = -1
        notifyDataSetChanged()
    }

    fun setSelected(position: Int) {
        val oldPos = selectedPosition
        selectedPosition = position
        notifyItemChanged(oldPos)
        notifyItemChanged(selectedPosition)
    }
}
