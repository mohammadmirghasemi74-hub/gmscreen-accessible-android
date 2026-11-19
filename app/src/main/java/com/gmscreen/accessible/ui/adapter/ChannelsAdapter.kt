package com.gmscreen.accessible.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.gmscreen.accessible.R
import com.gmscreen.accessible.data.model.Channel

class ChannelsAdapter(
    private var channels: List<Channel>,
    private val keepGlobalNumber: Boolean,
    private val onChannelClick: (Channel) -> Unit,
    private val onPlayClick: (Channel) -> Unit,
    private val onChannelLongClick: (Channel) -> Unit
) : RecyclerView.Adapter<ChannelsAdapter.ChannelViewHolder>() {

    private var currentPlayingServiceId: String? = null

    fun getChannels(): List<Channel> = channels

    fun updateChannels(newChannels: List<Channel>) {
        channels = newChannels
        notifyDataSetChanged()
    }

    fun setCurrentPlayingChannel(serviceId: String?) {
        if (currentPlayingServiceId != serviceId) {
            val oldIdx = channels.indexOfFirst { it.serviceId == currentPlayingServiceId }
            val newIdx = channels.indexOfFirst { it.serviceId == serviceId }
            currentPlayingServiceId = serviceId
            try {
                if (oldIdx in channels.indices) notifyItemChanged(oldIdx)
                if (newIdx in channels.indices) notifyItemChanged(newIdx)
            } catch (_: Exception) {}
        }
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
        val isPlaying = (ch.serviceId == currentPlayingServiceId)
        holder.bind(ch, displayNum, isPlaying, onChannelClick, onPlayClick, onChannelLongClick)
    }

    override fun getItemCount(): Int = channels.size

    class ChannelViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val txtNum: TextView = itemView.findViewById(R.id.txtChannelNumber)
        private val txtName: TextView = itemView.findViewById(R.id.txtChannelName)
        private val txtDetails: TextView = itemView.findViewById(R.id.txtChannelDetails)
        private val badgePlaying: TextView = itemView.findViewById(R.id.badgePlaying)
        private val badgeHd: TextView = itemView.findViewById(R.id.badgeHd)
        private val badgeLock: TextView = itemView.findViewById(R.id.badgeLock)
        private val btnLivePlay: MaterialButton = itemView.findViewById(R.id.btnLivePlay)

        fun bind(
            channel: Channel,
            displayNumber: Int,
            isCurrentlyPlaying: Boolean,
            onClick: (Channel) -> Unit,
            onPlayClick: (Channel) -> Unit,
            onLongClick: (Channel) -> Unit
        ) {
            txtNum.text = displayNumber.toString()
            txtName.text = channel.serviceName

            val satText = if (channel.satelliteName.isNotBlank()) channel.satelliteName else "ماهواره"
            val freqText = if (channel.frequency > 0) "${channel.frequency} MHz" else ""
            txtDetails.text = if (freqText.isNotBlank()) "$satText • $freqText" else satText

            badgePlaying.visibility = if (isCurrentlyPlaying) View.VISIBLE else View.GONE
            badgeHd.visibility = if (channel.isHd) View.VISIBLE else View.GONE
            badgeLock.visibility = if (channel.isScrambled) View.VISIBLE else View.GONE

            if (isCurrentlyPlaying) {
                itemView.setBackgroundColor(ContextCompat.getColor(itemView.context, R.color.card_bg))
            } else {
                itemView.background = null
            }

            // Concise TalkBack announcement: no repetitive instructions
            val typePrefix = if (channel.isRadio) "رادیو" else "کانال"
            val accessibilityDescription = buildString {
                append("$typePrefix $displayNumber، ${channel.serviceName}")
                if (isCurrentlyPlaying) {
                    append("، در حال پخش")
                }
                if (channel.isHd) append("، HD")
                if (channel.isScrambled) append("، کارتی")
            }
            itemView.contentDescription = accessibilityDescription

            // Tapping the channel tunes TV
            itemView.setOnClickListener {
                onClick(channel)
            }

            // Tapping the play button streams to phone
            btnLivePlay.contentDescription = "پخش زنده ${channel.serviceName} در گوشی"
            btnLivePlay.setOnClickListener {
                onPlayClick(channel)
            }

            itemView.setOnLongClickListener {
                onLongClick(channel)
                true
            }
        }
    }
}
