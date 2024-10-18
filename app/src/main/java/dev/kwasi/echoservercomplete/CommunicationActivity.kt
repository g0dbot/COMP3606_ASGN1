package dev.kwasi.echoservercomplete

import android.content.Context
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pManager
import android.os.Bundle
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
import kotlin.random.Random

class CommunicationActivity : AppCompatActivity(), WifiDirectInterface, PeerListAdapterInterface, NetworkMessageInterface {
    private var wfdManager: WifiDirectManager? = null

    private val intentFilter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    }

    private var peerListAdapter: PeerListAdapter? = null
    private var chatListAdapter: ChatListAdapter? = null

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
        val rvPeerList: RecyclerView = findViewById(R.id.rvPeerListing)
        rvPeerList.adapter = peerListAdapter
        rvPeerList.layoutManager = LinearLayoutManager(this)

        chatListAdapter = ChatListAdapter()
        val rvChatList: RecyclerView = findViewById(R.id.rvChat)
        rvChatList.adapter = chatListAdapter
        rvChatList.layoutManager = LinearLayoutManager(this)

        etStudentId = findViewById(R.id.etStudentId)
        btnSearchClasses = findViewById(R.id.btnSearchClasses)
        tvClassName = findViewById(R.id.tvClassName)

        encryption = Encryption()

        btnSearchClasses.setOnClickListener {
            if (isValidStudentId(etStudentId.text.toString())) {
                authenticateAndSearch()
            } else {
                Toast.makeText(this, "Please enter a valid student ID", Toast.LENGTH_SHORT).show()
            }
        }
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
    }

    //sends message to the connected client
    //req client server
    fun sendMessage(view: View) {
        if (!isAuthenticated) {
            Toast.makeText(this, "Please authenticate first", Toast.LENGTH_SHORT).show()
            return
        }
        
        val etMessage: EditText = findViewById(R.id.etMessage)
        val etString = etMessage.text.toString()
        val encryptedMessage = encryption.encryptMessage(etString, etStudentId.text.toString())
        val content = ContentModel(encryptedMessage, deviceIp)
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
        text = if (isEnabled) {
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
        updateUI()
    }

    //Notifies updates on device parameters
    //req client server
    override fun onDeviceStatusChanged(thisDevice: WifiP2pDevice) {
        val toast = Toast.makeText(this, "Device parameters have been updated", Toast.LENGTH_SHORT)
        toast.show()
    }

    //connects to the selected peer device
    //req client server
    override fun onPeerClicked(peer: WifiP2pDevice) {
        wfdManager?.connectToPeer(peer)
    }

    //handles incoming content messages from server and updates the chat list.
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
        val studentId = etStudentId.text.toString()
        if (isValidStudentId(studentId)) {
            wfdManager?.discoverPeers()
        } else {
            Toast.makeText(this, "Please enter a valid student ID", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isValidStudentId(studentId: String): Boolean {
        // Implement your student ID validation logic here
        return studentId.isNotEmpty() && studentId.length == 8 // Example: 8-digit ID
    }

    private fun authenticateAndSearch() {
        val studentId = etStudentId.text.toString()
        val randomNumber = Random.nextInt().toString()
        val encryptedResponse = encryption.studentResponse(randomNumber, studentId)
        isAuthenticated = encryption.verifyResponse(encryptedResponse, randomNumber, studentId)
        if (isAuthenticated) {
            Toast.makeText(this, "Authentication successful", Toast.LENGTH_SHORT).show()
            searchForClasses()
        } else {
            Toast.makeText(this, "Authentication failed", Toast.LENGTH_SHORT).show()
        }
    }
}
