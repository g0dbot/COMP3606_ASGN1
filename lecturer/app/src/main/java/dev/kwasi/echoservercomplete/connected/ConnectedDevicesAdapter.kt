/*IDS 816034693 816017853*/

package dev.kwasi.echoservercomplete.connected

import android.annotation.SuppressLint
import android.net.wifi.p2p.WifiP2pDevice
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import dev.kwasi.echoservercomplete.R

class ConnectedDevicesAdapter(private val iFaceImpl: ConnectedDevicesAdapterInterface) :
    RecyclerView.Adapter<ConnectedDevicesAdapter.ViewHolder>() {

    private val connectedDevicesList: MutableList<WifiP2pDevice> = mutableListOf()

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleTextView: TextView = itemView.findViewById(R.id.titleTextView)
        val descriptionTextView: TextView = itemView.findViewById(R.id.descriptionTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.peer_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val device = connectedDevicesList[position]

        holder.titleTextView.text = device.deviceName
        holder.descriptionTextView.text = device.deviceAddress

        holder.itemView.setOnClickListener {
            iFaceImpl.onConnDeviceClick(device)
        }
    }

    override fun getItemCount(): Int = connectedDevicesList.size

    @SuppressLint("NotifyDataSetChanged")
    fun updateConnectedDevices(newConnectedDevicesList: Collection<WifiP2pDevice>) {
        connectedDevicesList.clear()
        connectedDevicesList.addAll(newConnectedDevicesList)
        notifyDataSetChanged() // Notify the adapter of data changes
    }
}