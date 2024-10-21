package dev.kwasi.echoservercomplete

import android.content.Context
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.kwasi.echoservercomplete.chatlist.ChatListAdapter
import dev.kwasi.echoservercomplete.models.ContentModel
import dev.kwasi.echoservercomplete.network.Client
import dev.kwasi.echoservercomplete.network.NetworkMessageInterface
import dev.kwasi.echoservercomplete.network.Server
import dev.kwasi.echoservercomplete.peerlist.PeerListAdapter
import dev.kwasi.echoservercomplete.peerlist.PeerListAdapterInterface
import dev.kwasi.echoservercomplete.wifidirect.WifiDirectInterface
import dev.kwasi.echoservercomplete.wifidirect.WifiDirectManager
import com.example.comp3606a1.encryption.Encryption
import dev.kwasi.echoservercomplete.grouplist.GroupListAdapter
import dev.kwasi.echoservercomplete.grouplist.GroupListAdapterInterface
import kotlin.random.Random

class CommunicationActivity : AppCompatActivity(), WifiDirectInterface, PeerListAdapterInterface,
    GroupListAdapterInterface, NetworkMessageInterface {
    private var wfdManager: WifiDirectManager? = null

    private val intentFilter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    }

    private var peerListAdapter: PeerListAdapter? = null
    private var chatListAdapter: ChatListAdapter? = null
    private var groupListAdapter: GroupListAdapter? = null

    private var wfdAdapterEnabled = false
    private var wfdHasConnection = false
    private var hasDevices = false
    private var server: Server? = null
    private var client: Client? = null
    private var deviceIp: String = ""

    private lateinit var etStudentId: EditText
    private lateinit var btnSearchClasses: Button
    private lateinit var tvClassName: TextView

    private lateinit var encryption: Encryption
    private var isAuthenticated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_communication_student)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val manager: WifiP2pManager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        val channel = manager.initialize(this, mainLooper, null)
        wfdManager = WifiDirectManager(manager, channel, this)

        peerListAdapter = PeerListAdapter(this)
        val rvPeerList: RecyclerView = findViewById(R.id.rvPeerListing)
        rvPeerList.adapter = peerListAdapter
        rvPeerList.layoutManager = LinearLayoutManager(this)

        chatListAdapter = ChatListAdapter()
        val rvChatList: RecyclerView = findViewById(R.id.rvChat)
        rvChatList.adapter = chatListAdapter
        rvChatList.layoutManager = LinearLayoutManager(this)

        groupListAdapter = GroupListAdapter(this)
        val rvGroupList: RecyclerView = findViewById(R.id.rvGroupListing)
        rvGroupList.adapter = groupListAdapter
        rvGroupList.layoutManager = LinearLayoutManager(this)

        etStudentId = findViewById(R.id.etStudentId)
        btnSearchClasses = findViewById(R.id.btnSearchClasses)
        tvClassName = findViewById(R.id.tvClassName)

        encryption = Encryption()

        etStudentId.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                btnSearchClasses.isEnabled = isValidStudentId(s.toString())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        btnSearchClasses.setOnClickListener {
            if (isValidStudentId(etStudentId.text.toString())) {
                authenticateAndSearch()
            } else {
                Toast.makeText(this, "Please enter a valid student ID", Toast.LENGTH_SHORT).show()
            }
        }

        updateUI()
    }

    override fun onResume() {
        super.onResume()
        wfdManager?.also {
            registerReceiver(it, intentFilter)
        }
    }

    override fun onPause() {
        super.onPause()
        wfdManager?.also {
            unregisterReceiver(it)
        }
    }

    fun createGroup(view: View) {
        wfdManager?.createGroup()
    }

    fun discoverNearbyPeers(view: View) {
        wfdManager?.discoverPeers()
    }

    private fun updateUI() {
        val wfdAdapterErrorView: ConstraintLayout = findViewById(R.id.clWfdAdapterDisabled)
        val wfdNoConnectionView: ConstraintLayout = findViewById(R.id.clNoWifiDirectConnection)
        val wfdConnectedView: ConstraintLayout = findViewById(R.id.clHasConnection)
        val rvPeerList: RecyclerView = findViewById(R.id.rvPeerListing)

        wfdAdapterErrorView.visibility = if (!wfdAdapterEnabled) View.VISIBLE else View.GONE
        wfdNoConnectionView.visibility = if (wfdAdapterEnabled && !wfdHasConnection) View.VISIBLE else View.GONE
        rvPeerList.visibility = if (wfdAdapterEnabled && !wfdHasConnection && hasDevices) View.VISIBLE else View.GONE
        wfdConnectedView.visibility = if (wfdHasConnection) View.VISIBLE else View.GONE

        if (wfdHasConnection) {
            tvClassName.text = "Class: ${wfdManager?.groupInfo?.networkName ?: "Unknown"}"
        }

        btnSearchClasses.isEnabled = isValidStudentId(etStudentId.text.toString())
    }

    fun sendMessage(view: View) {
        if (!isAuthenticated) {
            Toast.makeText(this, "Please authenticate first", Toast.LENGTH_SHORT).show()
            return
        }
        
        val etMessage: EditText = findViewById(R.id.etMessage)
        val message = etMessage.text.toString()
        val studentId = etStudentId.text.toString()
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

    override fun onWiFiDirectStateChanged(isEnabled: Boolean) {
        wfdAdapterEnabled = isEnabled
        var text = if (isEnabled) {
            "WiFi Direct is enabled."
        } else {
            "WiFi Direct is disabled. Please turn on WiFi in your device settings."
        }

        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
        updateUI()
    }

    override fun onPeerListUpdated(deviceList: Collection<WifiP2pDevice>) {
        val toast = Toast.makeText(this, "Updated listing of nearby WiFi Direct devices", Toast.LENGTH_SHORT)
        toast.show()
        hasDevices = deviceList.isNotEmpty()
        peerListAdapter?.updateList(deviceList)
        updateUI()
    }

    override fun onGroupStatusChanged(groupInfo: WifiP2pGroup?) {
        val text = if (groupInfo == null) {
            "Group is not formed"
        } else {
            "Group has been formed"
        }
        val toast = Toast.makeText(this, text, Toast.LENGTH_SHORT)
        toast.show()
        wfdHasConnection = groupInfo != null

        if (groupInfo == null) {
            disconnectAndCleanup()
        } else if (groupInfo.isGroupOwner && server == null) {
            server = Server(this)
            deviceIp = "192.168.49.1"
        } else if (!groupInfo.isGroupOwner && client == null) {
            client = Client(this)
            deviceIp = client!!.ip
        }

        val sendButton: Button = findViewById(R.id.btnSendMessage)
        sendButton.isEnabled = wfdHasConnection

        updateUI()
    }

    override fun onDeviceStatusChanged(thisDevice: WifiP2pDevice) {
        val toast = Toast.makeText(this, "Device parameters have been updated", Toast.LENGTH_SHORT)
        toast.show()
    }

    override fun onPeerClicked(peer: WifiP2pDevice) {
        wfdManager?.connectToPeer(peer)
    }

    override fun onContent(content: ContentModel) {
        runOnUiThread {
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

    private fun searchForClasses() {
        if (isAuthenticated) {
            wfdManager?.discoverPeers()
        } else {
            Toast.makeText(this, "Please authenticate first", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isValidStudentId(studentId: String): Boolean {
        return studentId.isNotEmpty() && studentId.length == 8
    }

    private fun authenticateAndSearch() {
        val studentId = etStudentId.text.toString()
        if (isValidStudentId(studentId)) {
            isAuthenticated = true
            Toast.makeText(this, "Authentication successful", Toast.LENGTH_SHORT).show()
            wfdManager?.discoverPeers()
        } else {
            Toast.makeText(this, "Invalid student ID", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onGroupInfoUpdated(group: WifiP2pGroup?) {
        group?.let {
            groupListAdapter?.updateList(listOf(it))
        }
        updateUI()
    }

    override fun onGroupClicked(group: WifiP2pGroup) {
        wfdManager?.joinGroup(group)
    }

    fun discoverGroups(view: View) {
        wfdManager?.discoverGroups()
    }

    override fun joinGroup(group: WifiP2pGroup) {
        wfdManager?.joinGroup(group)
    }
}
