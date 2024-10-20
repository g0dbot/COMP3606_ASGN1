package dev.kwasi.echoservercomplete.peerlist

import android.annotation.SuppressLint
import android.net.wifi.p2p.WifiP2pDevice
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import dev.kwasi.echoservercomplete.R

class PeerListAdapter(private val iFaceImpl: PeerListAdapterInterface): RecyclerView.Adapter<PeerListAdapter.ViewHolder>() {
    private val deviceList: MutableList<WifiP2pDevice> = mutableListOf()

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleTextView: TextView = itemView.findViewById(R.id.titleTextView)
        val descriptionTextView: TextView = itemView.findViewById(R.id.descriptionTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.peer_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val device = deviceList[position]

        holder.titleTextView.text = device.deviceName
        holder.descriptionTextView.text = "Wi-Fi Direct Device"

        holder.itemView.setOnClickListener {
            iFaceImpl.onPeerClicked(device)
        }
    }

    override fun getItemCount(): Int {
        return deviceList.size
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateList(newDeviceList: Collection<WifiP2pDevice>) {
        deviceList.clear()
        deviceList.addAll(newDeviceList)
        notifyDataSetChanged()
    }
}
