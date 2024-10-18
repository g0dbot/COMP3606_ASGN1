package dev.kwasi.echoservercomplete
import android.content.Intent
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pGroup
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import dev.kwasi.echoservercomplete.models.ContentModel
import dev.kwasi.echoservercomplete.network.NetworkMessageInterface
import dev.kwasi.echoservercomplete.peerlist.PeerListAdapterInterface
import dev.kwasi.echoservercomplete.wifidirect.WifiDirectInterface

class LandingLecturer : AppCompatActivity(){
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Render the activity_class.xml layout
        setContentView(R.layout.lecturer_landing)

        // You can add any further initialization or logic here if needed
    }

    //redirects to class activity
    fun redirectClassActivity(view: View){
        val intent = Intent(this, CommunicationActivity::class.java)
        startActivity(intent)
    }
}