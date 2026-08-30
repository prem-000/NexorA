package com.fury.peerconnect.ui

import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.fury.peerconnect.R
import com.fury.peerconnect.data.ChatMessage

class RecentActivityAdapter(
    private val onItemClick: (String) -> Unit
) : RecyclerView.Adapter<RecentActivityAdapter.RecentViewHolder>() {

    private val items = mutableListOf<ChatMessage>()

    fun setItems(newItems: List<ChatMessage>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecentViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_recent_activity, parent, false)
        return RecentViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecentViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
        holder.itemView.setOnClickListener { onItemClick(item.senderName) }
    }

    override fun getItemCount(): Int = items.size

    class RecentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textAvatar: TextView = itemView.findViewById(R.id.textAvatar)
        private val textPeerName: TextView = itemView.findViewById(R.id.textPeerName)
        private val textTime: TextView = itemView.findViewById(R.id.textTime)
        private val textPreview: TextView = itemView.findViewById(R.id.textPreview)

        fun bind(item: ChatMessage) {
            textPeerName.text = item.senderName
            textAvatar.text = item.senderName.take(1).uppercase()
            textPreview.text = item.messageBody

            val formattedTime = DateFormat.format("hh:mm a", item.time).toString()
            textTime.text = formattedTime
        }
    }
}
