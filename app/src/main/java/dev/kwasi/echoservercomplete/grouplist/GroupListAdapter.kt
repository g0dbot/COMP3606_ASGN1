package dev.kwasi.echoservercomplete.grouplist

import android.annotation.SuppressLint
import android.net.wifi.p2p.WifiP2pGroup
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import dev.kwasi.echoservercomplete.R

class GroupListAdapter(private val iFaceImpl: GroupListAdapterInterface) : RecyclerView.Adapter<GroupListAdapter.ViewHolder>() {
    private val groupsList: MutableList<WifiP2pGroup> = mutableListOf()

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleTextView: TextView = itemView.findViewById(R.id.titleTextView)
        val descriptionTextView: TextView = itemView.findViewById(R.id.descriptionTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.group_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val group = groupsList[position]

        holder.titleTextView.text = group.networkName
        holder.descriptionTextView.text = group.owner?.deviceAddress ?: "Unknown"

        holder.itemView.setOnClickListener {
            iFaceImpl.onGroupClicked(group)
        }
    }

    override fun getItemCount(): Int {
        return groupsList.size
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateList(newGroupsList: Collection<WifiP2pGroup>) {
        groupsList.clear()
        groupsList.addAll(newGroupsList)
        notifyDataSetChanged()
    }
}