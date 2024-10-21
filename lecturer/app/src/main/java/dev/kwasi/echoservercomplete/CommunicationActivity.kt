/*IDS 816034693 816017853*/

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
import dev.kwasi.echoservercomplete.database.ChatMessage
import dev.kwasi.echoservercomplete.models.ContentModel
import dev.kwasi.echoservercomplete.network.Client
import dev.kwasi.echoservercomplete.network.NetworkMessageInterface
import dev.kwasi.echoservercomplete.network.Server
import dev.kwasi.echoservercomplete.peerlist.PeerListAdapter
import dev.kwasi.echoservercomplete.wifidirect.WifiDirectInterface
import dev.kwasi.echoservercomplete.wifidirect.WifiDirectManager
import dev.kwasi.echoservercomplete.database.Database

class CommunicationActivity : AppCompatActivity(), WifiDirectInterface, NetworkMessageInterface, ConnectedDevicesAdapterInterface {

    private var wfdManager: WifiDirectManager? = null
    private var connectedDevicesAdapter: ConnectedDevicesAdapter? = null
    private var chatListAdapter: ChatListAdapter? = null
    private var dbHelper: Database? = null
    private lateinit var tvTitle: TextView
    private lateinit var tvChat: TextView

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
    private var groupInfo: WifiP2pGroup? = null

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_communication_lecturer)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val manager: WifiP2pManager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        val channel = manager.initialize(this, mainLooper, null)
        dbHelper = Database(this, null)
        wfdManager = WifiDirectManager(manager, channel, this)
        wfdManager?.createGroup()

        tvTitle = findViewById(R.id.tvTitle)
        tvChat = findViewById(R.id.tvChatLabel)

        connectedDevicesAdapter = ConnectedDevicesAdapter(this)
        findViewById<RecyclerView>(R.id.recyclerViewConnectedDevices).apply {
            adapter = connectedDevicesAdapter
            layoutManager = LinearLayoutManager(this@CommunicationActivity)
        }

        chatListAdapter = ChatListAdapter()
        findViewById<RecyclerView>(R.id.rvChat).apply {
            adapter = chatListAdapter
            layoutManager = LinearLayoutManager(this@CommunicationActivity)
        }
    }

    override fun onResume() {
        super.onResume()
        wfdManager?.also { registerReceiver(it, intentFilter) }
    }

    override fun onPause() {
        super.onPause()
        wfdManager?.also { unregisterReceiver(it) }
    }

    fun createGroup(view: View) {
        wfdManager?.createGroup()
    }

    fun discoverNearbyPeers(view: View) {
        wfdManager?.discoverPeers()
    }

    private fun updateUI() {
        val tvNoDevices: TextView = findViewById(R.id.tvNoDevices)
        val rvConnectedDevicesList: RecyclerView = findViewById(R.id.recyclerViewConnectedDevices)

        if (!wfdAdapterEnabled) {
            tvNoDevices.text = "WiFi Direct is disabled. Please enable it."
            tvNoDevices.visibility = View.VISIBLE
            rvConnectedDevicesList.visibility = View.GONE
            return
        }

        val connectedDevices = getConnectedDevices()

        if (connectedDevices.isEmpty()) {
            tvNoDevices.text = "No devices connected."
            tvNoDevices.visibility = View.VISIBLE
            rvConnectedDevicesList.visibility = View.GONE
        } else {
            tvNoDevices.visibility = View.GONE
            rvConnectedDevicesList.visibility = View.VISIBLE
            connectedDevicesAdapter?.updateConnectedDevices(connectedDevices)
        }
    }

    private fun getConnectedDevices(): Collection<WifiP2pDevice> {
        return groupInfo?.clientList ?: emptyList()
    }

    fun sendMessage(view: View) {
        val etMessage: EditText = findViewById(R.id.etMessage)
        val messageContent = etMessage.text.toString()
        val content = ContentModel(messageContent, deviceIp)
        etMessage.text.clear()

        server?.sendMessage(content) ?: client?.sendMessage(content)
        chatListAdapter?.addItemToEnd(content)
    }

    override fun onWiFiDirectStateChanged(isEnabled: Boolean) {
        wfdAdapterEnabled = isEnabled
        Toast.makeText(this, if (isEnabled) "WiFi Direct enabled!" else "WiFi Direct disabled! Turn on the WiFi adapter.", Toast.LENGTH_SHORT).show()
        updateUI()
    }

    override fun onPeerListUpdated(deviceList: Collection<WifiP2pDevice>) {
        Toast.makeText(this, "Updated listing of nearby WiFi Direct devices", Toast.LENGTH_SHORT).show()
        hasDevices = deviceList.isNotEmpty()
        updateUI()
    }

    override fun onGroupStatusChanged(group: WifiP2pGroup?) {
        groupInfo = group
        Toast.makeText(this, if (group == null) "Group is not formed" else "Group has been formed", Toast.LENGTH_SHORT).show()

        wfdHasConnection = group != null

        if (group == null) {
            disconnectAndCleanup(findViewById(R.id.btnEndClass))
        } else {
            if (group.isGroupOwner && server == null) {
                server = Server(this, this)
                deviceIp = "192.168.49.1"
                tvTitle.text = "Class Network: ${group.networkName}"
            } else if (!group.isGroupOwner && client == null) {
                client = Client(this)
                deviceIp = client!!.ip
            }
            connectedDevicesAdapter?.updateConnectedDevices(group.clientList)
        }
        updateUI()
    }

    override fun onDeviceStatusChanged(thisDevice: WifiP2pDevice) {
        Toast.makeText(this, "Device parameters updated", Toast.LENGTH_SHORT).show()
    }

    override fun onConnDeviceClick(peer: WifiP2pDevice) {
        Toast.makeText(this, "Connecting to: ${peer.deviceName}", Toast.LENGTH_SHORT).show()
        wfdManager?.connectToPeer(peer)

        tvChat.text = "Student Chat - ${peer.deviceName}"
        val chatMessages: MutableList<ChatMessage> = dbHelper?.getMessagesForDevice(peer.deviceName)?.toMutableList() ?: mutableListOf()
        val contentModels: List<ContentModel> = chatMessages.map { ContentModel(it.deviceName, it.text) }
        chatListAdapter?.loadMessagesFromDb(contentModels)
    }

    override fun onContent(content: ContentModel) {
        runOnUiThread {
            chatListAdapter?.addItemToEnd(content)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        disconnectAndCleanup(findViewById(R.id.btnEndClass))
    }

    fun disconnectAndCleanup(view: View) {
        wfdManager?.disconnect()
        server?.close()
        client?.close()
        server = null
        client = null
        wfdHasConnection = false
        updateUI()
        startActivity(Intent(this, LandingLecturer::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
        })
    }

    fun refreshConnection(view: View) {
        disconnectAndCleanup(findViewById(R.id.btnEndClass))
        wfdManager?.discoverPeers()
    }
}
