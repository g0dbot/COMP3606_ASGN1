/*IDS 816034693 816017853*/

package dev.kwasi.echoservercomplete

import android.content.Context
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.comp3606a1.encryption.Encryption
import dev.kwasi.echoservercomplete.chatlist.ChatListAdapter
import dev.kwasi.echoservercomplete.models.ContentModel
import dev.kwasi.echoservercomplete.network.Client
import dev.kwasi.echoservercomplete.network.NetworkMessageInterface
import dev.kwasi.echoservercomplete.network.Server
import dev.kwasi.echoservercomplete.peerlist.PeerListAdapter
import dev.kwasi.echoservercomplete.peerlist.PeerListAdapterInterface
import dev.kwasi.echoservercomplete.wifidirect.WifiDirectInterface
import dev.kwasi.echoservercomplete.wifidirect.WifiDirectManager
import android.widget.TextView

class LandingStudent : AppCompatActivity(), WifiDirectInterface, PeerListAdapterInterface, NetworkMessageInterface {
    private var wfdManager: WifiDirectManager? = null

    private val intentFilter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    }

    private var peerListAdapter:PeerListAdapter? = null
    private var chatListAdapter:ChatListAdapter? = null
    private var encryptionHelper:Encryption? = null

    private var wfdAdapterEnabled = false
    private var wfdHasConnection = false
    private var hasDevices = false
    private var server: Server? = null
    private var client: Client? = null
    private var deviceIp: String = ""

    //inits activity, sets up the UI, and prepares the WifiDirectManager and adapters for peer and chat lists
    //req client server
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_communication)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val manager: WifiP2pManager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        val channel = manager.initialize(this, mainLooper, null)
        wfdManager = WifiDirectManager(manager, channel, this)

        peerListAdapter = PeerListAdapter(this)
        val rvPeerList: RecyclerView= findViewById(R.id.rvPeerListing)
        rvPeerList.adapter = peerListAdapter
        rvPeerList.layoutManager = LinearLayoutManager(this)

        chatListAdapter = ChatListAdapter()
        val rvChatList: RecyclerView = findViewById(R.id.rvChat)
        rvChatList.adapter = chatListAdapter
        rvChatList.layoutManager = LinearLayoutManager(this)
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
        wfdManager?.createGroup()
    }

    //starts discovering nearby WiFi direct devices
    fun discoverNearbyPeers(view: View) {
        wfdManager?.discoverPeers()
    }

    //updates UI based on the state of the WiFi direct connection and devices
    //req client server
    private fun updateUI(){
        val wfdAdapterErrorView:ConstraintLayout = findViewById(R.id.clWfdAdapterDisabled)
        wfdAdapterErrorView.visibility = if (!wfdAdapterEnabled) View.VISIBLE else View.GONE

        val wfdNoConnectionView:ConstraintLayout = findViewById(R.id.clNoWifiDirectConnection)
        wfdNoConnectionView.visibility = if (wfdAdapterEnabled && !wfdHasConnection) View.VISIBLE else View.GONE

        val rvPeerList: RecyclerView= findViewById(R.id.rvPeerListing)
        rvPeerList.visibility = if (wfdAdapterEnabled && !wfdHasConnection && hasDevices) View.VISIBLE else View.GONE

        val wfdConnectedView:ConstraintLayout = findViewById(R.id.clHasConnection)
        wfdConnectedView.visibility = if(wfdHasConnection)View.VISIBLE else View.GONE
    }

    fun sendMessage(view: View) {

        val etMessage: EditText = findViewById(R.id.etMessage)
        val message = etMessage.text.toString()
        val studentId = message
        val content = ContentModel(message, deviceIp)
        etMessage.text.clear()

        try {
            Log.d("CommunicationActivity", "Attempting to send message. Server: $server, Client: $client")
            when {
                server != null -> {
                    Log.d("CommunicationActivity", "Sending message through server")
                    server?.sendMessage(content)
                }
                client != null -> {
                    Log.d("CommunicationActivity", "Sending message through client")
                    client?.sendMessage(content)
                }
                else -> {
                    Log.e("CommunicationActivity", "Both server and client are null")
                    Toast.makeText(this, "Error: Not connected", Toast.LENGTH_SHORT).show()
                    return
                }
            }
            chatListAdapter?.addItemToEnd(content)
            Log.d("CommunicationActivity", "Message sent successfully")
        } catch (e: Exception) {
            Log.e("CommunicationActivity", "Error sending message", e)
            Toast.makeText(this, "Error sending message: ${e.message}", Toast.LENGTH_SHORT).show()
        }
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
        val text = if (groupInfo == null){
            "Group is not formed"
        } else {
            "Group has been formed"
        }
        val toast = Toast.makeText(this, text , Toast.LENGTH_SHORT)
        val textView: TextView = findViewById(R.id.tvGroupName)

        toast.show()
        wfdHasConnection = groupInfo != null

        if (groupInfo == null){
            disconnectAndCleanup()
        } else if (groupInfo.isGroupOwner && server == null){
            textView.visibility = View.VISIBLE
            textView.text = "Currently attending: ${groupInfo.networkName}"
            server = Server(this, this)
            deviceIp = "192.168.49.1"
        } else if (!groupInfo.isGroupOwner && client == null) {
            textView.text = "Currently Attending: ${groupInfo.owner.deviceName}"
            textView.visibility = View.VISIBLE
            client = Client(this)
            deviceIp = client!!.ip
        }

        val etNumberInput: EditText = findViewById(R.id.etNumberInput)
        val studentId = etNumberInput.text.toString()

        if (studentId.isNotBlank()) {
            client?.sendId(studentId) // Set student ID in client
        } else {
            Toast.makeText(this, "Please enter a valid student ID", Toast.LENGTH_SHORT).show()
        }

        updateUI()
    }


    //Notifies updates on device parameters
    //req client server
    override fun onDeviceStatusChanged(thisDevice: WifiP2pDevice) {
        val toast = Toast.makeText(this, "Device parameters have been updated" , Toast.LENGTH_SHORT)
        toast.show()
    }

    //connects to the selected peer device
    //req client server
    override fun onPeerClicked(peer: WifiP2pDevice) {
        wfdManager?.connectToPeer(peer)
        val content = ContentModel("message", deviceIp)
        client?.sendMessage(content)
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