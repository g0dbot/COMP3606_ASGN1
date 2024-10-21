package dev.kwasi.echoservercomplete.grouplist

import android.net.wifi.p2p.WifiP2pGroup

interface GroupListAdapterInterface {
    fun onGroupClicked(group: WifiP2pGroup)
}
