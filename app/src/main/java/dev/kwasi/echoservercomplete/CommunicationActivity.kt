package dev.kwasi.echoservercomplete

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.kwasi.echoservercomplete.chatlist.ChatListAdapter
import dev.kwasi.echoservercomplete.connected.ConnectedDevicesAdapter
import dev.kwasi.echoservercomplete.connected.ConnectedDevicesAdapterInterface
import dev.kwasi.echoservercomplete.models.ContentModel
import dev.kwasi.echoservercomplete.network.Client
import dev.kwasi.echoservercomplete.network.NetworkMessageInterface
import dev.kwasi.echoservercomplete.network.Server
import dev.kwasi.echoservercomplete.peerlist.PeerListAdapter
import dev.kwasi.echoservercomplete.peerlist.PeerListAdapterInterface
import dev.kwasi.echoservercomplete.wifidirect.WifiDirectInterface
import dev.kwasi.echoservercomplete.wifidirect.WifiDirectManager


class CommunicationActivity : AppCompatActivity(), WifiDirectInterface, NetworkMessageInterface, ConnectedDevicesAdapterInterface {
    private var wfdManager: WifiDirectManager? = null
    private var connectedDevicesAdapter: ConnectedDevicesAdapter? = null
    private var peerListAdapter: PeerListAdapter? = null
    private var chatListAdapter: ChatListAdapter? = null

    private val intentFilter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    }

    private var wfdAdapterEnabled = false
    private var wfdHasConnection = false
    private var hasDevices = false
    private var server: Server? = null
    private var client: Client? = null
    private var deviceIp: String = ""

    //inits activity, sets up the UI, and prepares the WifiDirectManager and adapters for peer and chat lists
    //req client server
    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("CommunicationActivity", "onCreate: Starting activity")

        enableEdgeToEdge()
        setContentView(R.layout.activity_communication_lecturer)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        Log.d("CommunicationActivity", "onCreate: Initializing WifiDirectManager")
        val manager: WifiP2pManager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        val channel = manager.initialize(this, mainLooper, null)
        wfdManager = WifiDirectManager(manager, channel, this)

        //auto create the group
        Log.d("CommunicationActivity", "onCreate: Creating group")
        wfdManager?.createGroup()

        //display the list of connected devices
        connectedDevicesAdapter = ConnectedDevicesAdapter(this)
        val rvConnectedDevicesList: RecyclerView = findViewById(R.id.recyclerViewConnectedDevices)
        rvConnectedDevicesList.adapter = connectedDevicesAdapter
        rvConnectedDevicesList.layoutManager = LinearLayoutManager(this)
    }

    //registers WifiDirectManager receiver when activity resumes.
    //req client server
    override fun onResume() {
        super.onResume()
        wfdManager?.also {
            registerReceiver(it, intentFilter)
        }
    }

    //unregisters WifiDirectManager receiver when activity pauses.
    //req client server
    override fun onPause() {
        super.onPause()
        wfdManager?.also {
            unregisterReceiver(it)
        }
    }

    //inits creation of a WiFi direct group
    //req server
    fun createGroup(view: View) {
        Log.d("CommunicationActivity", "createGroup called")
        wfdManager?.createGroup()
    }

    //starts discovering nearby WiFi direct devices
    fun discoverNearbyPeers(view: View) {
        wfdManager?.discoverPeers()
    }

    //updates UI based on the state of the WiFi direct connection and devices
    //req client server
    private fun updateUI(){
        //The rules for updating the UI are as follows:
        // IF the WFD adapter is NOT enabled then
        //      Show UI that says turn on the wifi adapter
        // ELSE IF there is NO WFD connection then i need to show a view that allows the user to either
            // 1) create a group with them as the group owner OR
            // 2) discover nearby groups
        // ELSE IF there are nearby groups found, i need to show them in a list
        // ELSE IF i have a WFD connection i need to show a chat interface where i can send/receive messages
        // Find the no devices TextView
        val tvNoDevices: TextView = findViewById(R.id.tvNoDevices)

        // Check if there are connected devices
        if (connectedDevicesAdapter?.itemCount == 0) {
            // No devices connected, show the TextView
            tvNoDevices.visibility = View.VISIBLE
        } else {
            // Devices connected, hide the TextView
            tvNoDevices.visibility = View.GONE
        }

        // Notify the connected devices adapter of data change
        connectedDevicesAdapter?.notifyDataSetChanged()

    }

    //sends message to the connected client
    //req client server
    fun sendMessage(view: View) {
        val etMessage:EditText = findViewById(R.id.etMessage)
        val etString = etMessage.text.toString()
        val content = ContentModel(etString, deviceIp)
        etMessage.text.clear()
        if (server != null) {
            server?.sendMessage(content)
        } else {
            client?.sendMessage(content)
        }
        chatListAdapter?.addItemToEnd(content)
    }

    //handles changes in WiFi direct state and updates UI accordingly
    //req client server
    override fun onWiFiDirectStateChanged(isEnabled: Boolean) {
        wfdAdapterEnabled = isEnabled
        var text = "There was a state change in the WiFi Direct. Currently it is "
        text = if (isEnabled){
            "$text enabled!"
        } else {
            "$text disabled! Try turning on the WiFi adapter"
        }

        val toast = Toast.makeText(this, text, Toast.LENGTH_SHORT)
        toast.show()
        updateUI()
    }

    //updates the list of nearby devices and UI upon peer list change
    //req client server
    override fun onPeerListUpdated(deviceList: Collection<WifiP2pDevice>) {
        val toast = Toast.makeText(this, "Updated listing of nearby WiFi Direct devices", Toast.LENGTH_SHORT)
        toast.show()
        hasDevices = deviceList.isNotEmpty()
        peerListAdapter?.updateList(deviceList)
        updateUI()
    }

    //handles group formation status, starts server if group owner, or client if not.
    //req client server
    override fun onGroupStatusChanged(groupInfo: WifiP2pGroup?) {
        if (groupInfo != null) { // Group has been formed
            Toast.makeText(this, "Group was auto created", Toast.LENGTH_SHORT).show() // Add this line
        }

        val text = if (groupInfo == null){
            "Group is not formed"
        } else {
            "Group has been formed"
        }
        val toast = Toast.makeText(this, text , Toast.LENGTH_SHORT)
        toast.show()
        wfdHasConnection = groupInfo != null

        if (groupInfo == null){
            disconnectAndCleanup()
        } else if (groupInfo.isGroupOwner && server == null){
            server = Server(this)
            deviceIp = "192.168.49.1"
        } else if (!groupInfo.isGroupOwner && client == null) {
            client = Client(this)
            deviceIp = client!!.ip
        }

        updateUI()
    }

    //Notifies updates on device parameters
    //req client server
    override fun onDeviceStatusChanged(thisDevice: WifiP2pDevice) {
        val toast = Toast.makeText(this, "Device parameters have been updated" , Toast.LENGTH_SHORT)
        toast.show()
    }

    //on lecturer clicking student
    override fun onConnDeviceClick(peer: WifiP2pDevice) {
        // Handle the connection click for the connected device
        Toast.makeText(this, "Connecting to: ${peer.deviceName}", Toast.LENGTH_SHORT).show()
        wfdManager?.connectToPeer(peer)
    }

    //handles incoming content messages from server and updates the chat list.
    override fun onContent(content: ContentModel) {
        runOnUiThread{
            chatListAdapter?.addItemToEnd(content)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        disconnectAndCleanup()
    }

    private fun disconnectAndCleanup() {
        wfdManager?.disconnect()
        server?.close()
        client?.close()
        server = null
        client = null
        wfdHasConnection = false
        updateUI()
    }

    fun refreshConnection(view: View) {
        disconnectAndCleanup()
        wfdManager?.discoverPeers()
    }
}
