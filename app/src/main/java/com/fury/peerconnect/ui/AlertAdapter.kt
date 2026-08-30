package com.fury.peerconnect.ui

import android.graphics.Color
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.fury.peerconnect.R
import com.fury.peerconnect.data.AlertEntity

class AlertAdapter(
    private val onAlertClick: ((AlertEntity) -> Unit)? = null
) : RecyclerView.Adapter<AlertAdapter.AlertViewHolder>() {

    private val alerts = mutableListOf<AlertEntity>()

    fun setAlerts(newAlerts: List<AlertEntity>) {
        alerts.clear()
        alerts.addAll(newAlerts)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlertViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_alert, parent, false)
        return AlertViewHolder(view)
    }

    override fun onBindViewHolder(holder: AlertViewHolder, position: Int) {
        holder.bind(alerts[position], onAlertClick)
    }

    override fun getItemCount(): Int = alerts.size

    class AlertViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val badgeType: TextView = itemView.findViewById(R.id.badgeType)
        private val textAlertTime: TextView = itemView.findViewById(R.id.textAlertTime)
        private val textAlertTitle: TextView = itemView.findViewById(R.id.textAlertTitle)
        private val textAlertDescription: TextView = itemView.findViewById(R.id.textAlertDescription)

        fun bind(alert: AlertEntity, onAlertClick: ((AlertEntity) -> Unit)?) {
            badgeType.text = alert.type
            textAlertTitle.text = alert.title
            textAlertDescription.text = alert.description

            val timeFormatted = DateFormat.format("hh:mm a", alert.timestamp).toString()
            textAlertTime.text = timeFormatted

            when (alert.type) {
                "SOS" -> badgeType.setBackgroundColor(Color.parseColor("#EF4444"))
                "CONNECTION" -> badgeType.setBackgroundColor(Color.parseColor("#4CAF50"))
                "DISCONNECTION" -> badgeType.setBackgroundColor(Color.parseColor("#F44336"))
                "TRANSFER" -> badgeType.setBackgroundColor(Color.parseColor("#2196F3"))
                "QUEUED" -> badgeType.setBackgroundColor(Color.parseColor("#FF9800"))
                "DISCOVERY" -> badgeType.setBackgroundColor(Color.parseColor("#9C27B0"))
                else -> badgeType.setBackgroundColor(Color.parseColor("#6200EE"))
            }

            itemView.setOnClickListener {
                onAlertClick?.invoke(alert)
            }
        }
    }
}
