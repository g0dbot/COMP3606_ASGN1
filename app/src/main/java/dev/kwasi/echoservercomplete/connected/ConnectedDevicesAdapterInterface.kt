package dev.kwasi.echoservercomplete.connected

import android.net.wifi.p2p.WifiP2pDevice

interface ConnectedDevicesAdapterInterface {
    fun onConnDeviceClick(peer:WifiP2pDevice)
}