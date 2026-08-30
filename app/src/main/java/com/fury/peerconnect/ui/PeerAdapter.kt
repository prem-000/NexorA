package com.fury.peerconnect.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.fury.peerconnect.R
import com.fury.peerconnect.data.PeerEntity

class PeerAdapter(private val onPeerClicked: (PeerEntity) -> Unit) :
    RecyclerView.Adapter<PeerAdapter.PeerViewHolder>() {

    private val peers = mutableListOf<PeerEntity>()

    fun updateList(newPeers: List<PeerEntity>) {
        peers.clear()
        peers.addAll(newPeers)
        notifyDataSetChanged()
    }

    fun updatePeerStatus(name: String, isOnline: Boolean) {
        val index = peers.indexOfFirst { it.name == name }
        if (index != -1) {
            peers[index] = peers[index].copy(isOnline = isOnline)
            notifyItemChanged(index)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PeerViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_peer, parent, false)
        return PeerViewHolder(view)
    }

    override fun onBindViewHolder(holder: PeerViewHolder, position: Int) {
        val peer = peers[position]
        holder.bind(peer)
        holder.itemView.setOnClickListener { onPeerClicked(peer) }
    }

    override fun getItemCount(): Int = peers.size

    class PeerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.peerName)
        private val statusText: TextView = itemView.findViewById(R.id.statusText)
        private val peerAvatar: TextView? = itemView.findViewById(R.id.peerAvatar)

        fun bind(peer: PeerEntity) {
            nameText.text = peer.name
            peerAvatar?.text = peer.name.take(1).uppercase()

            if (peer.isOnline) {
                statusText.text = "● Online"
                statusText.setTextColor(Color.parseColor("#4CAF50"))
                nameText.setTextColor(Color.parseColor("#212121"))
            } else {
                statusText.text = "Offline"
                statusText.setTextColor(Color.parseColor("#9E9E9E"))
                nameText.setTextColor(Color.parseColor("#757575"))
            }
        }
    }
}