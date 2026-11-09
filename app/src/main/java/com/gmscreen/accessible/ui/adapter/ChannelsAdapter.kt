package com.gmscreen.accessible.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.gmscreen.accessible.R
import com.gmscreen.accessible.data.model.Channel

class ChannelsAdapter(
    private var channels: List<Channel>,
    private val keepGlobalNumber: Boolean,
    private val onChannelClick: (Channel) -> Unit
) : RecyclerView.Adapter<ChannelsAdapter.ChannelViewHolder>() {

    fun updateChannels(newChannels: List<Channel>) {
        channels = newChannels
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_channel, parent, false)
        return ChannelViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) {
        val ch = channels[position]
        val displayNum = if (keepGlobalNumber && ch.channelNumber > 0) {
            ch.channelNumber
        } else {
            position + 1
        }
        holder.bind(ch, displayNum, onChannelClick)
    }

    override fun getItemCount(): Int = channels.size

    class ChannelViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val txtNum: TextView = itemView.findViewById(R.id.txtChannelNumber)
        private val txtName: TextView = itemView.findViewById(R.id.txtChannelName)
        private val txtDetails: TextView = itemView.findViewById(R.id.txtChannelDetails)
        private val badgeHd: TextView = itemView.findViewById(R.id.badgeHd)
        private val badgeLock: TextView = itemView.findViewById(R.id.badgeLock)

        fun bind(channel: Channel, displayNumber: Int, onClick: (Channel) -> Unit) {
            txtNum.text = displayNumber.toString()
            txtName.text = channel.serviceName

            val satText = if (channel.satelliteName.isNotBlank()) channel.satelliteName else "ماهواره"
            val freqText = if (channel.frequency > 0) "${channel.frequency} MHz" else ""
            txtDetails.text = if (freqText.isNotBlank()) "$satText • $freqText" else satText

            if (channel.isHd) {
                badgeHd.visibility = View.VISIBLE
            } else {
                badgeHd.visibility = View.GONE
            }

            if (channel.isScrambled) {
                badgeLock.visibility = View.VISIBLE
                badgeLock.text = "$"
            } else {
                badgeLock.visibility = View.GONE
            }

            // High priority: Accessible TalkBack content description
            val accessibilityDescription = buildString {
                append("کانال ")
                append(displayNumber)
                append("، ")
                append(channel.serviceName)
                if (channel.isHd) append("، اچ دی")
                if (channel.isScrambled) append("، کارتی") else append("، رایگان")
                if (channel.satelliteName.isNotBlank()) {
                    append("، ماهواره ")
                    append(channel.satelliteName)
                }
            }
            itemView.contentDescription = accessibilityDescription

            itemView.setOnClickListener {
                onClick(channel)
            }
        }
    }
}
