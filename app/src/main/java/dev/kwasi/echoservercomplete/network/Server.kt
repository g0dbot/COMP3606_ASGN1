package dev.kwasi.echoservercomplete.network

import android.util.Log
import com.google.gson.Gson
import dev.kwasi.echoservercomplete.models.ContentModel
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.Exception
import kotlin.concurrent.thread

/// The [Server] class has all the functionality that is responsible for the 'server' connection.
/// This is implemented using TCP. This Server class is intended to be run on the GO.

class Server(private val iFaceImpl:NetworkMessageInterface) {
    companion object {
        const val PORT: Int = 9999

    }

    private val svrSocket: ServerSocket = ServerSocket(PORT, 0, InetAddress.getByName("192.168.49.1"))
    private val clientMap: HashMap<String, Socket> = HashMap()

    init {
        thread{
            while(true){
                try{
                    val clientConnectionSocket = svrSocket.accept()
                    Log.e("SERVER", "The server has accepted a connection: ")
                    handleSocket(clientConnectionSocket)

                }catch (e: Exception){
                    Log.e("SERVER", "An error has occurred in the server!")
                    e.printStackTrace()
                }
            }
        }
    }


    private fun handleSocket(socket: Socket){
        socket.inetAddress.hostAddress?.let {
            clientMap[it] = socket
            Log.e("SERVER", "A new connection has been detected!")
            thread {
                val clientReader = socket.inputStream.bufferedReader()
                val clientWriter = socket.outputStream.bufferedWriter()
                var receivedJson: String?

                while(socket.isConnected){
                    try{
                        receivedJson = clientReader.readLine()
                        if (receivedJson!= null){
                            Log.e("SERVER", "Received a message from client $it")
                            val clientContent = Gson().fromJson(receivedJson, ContentModel::class.java)

                            iFaceImpl.onContent(clientContent)
                        }
                    } catch (e: Exception){
                        Log.e("SERVER", "An error has occurred with the client $it")
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    fun sendMessage(content: ContentModel) {
        thread {
            clientMap.forEach { (_, socket) ->
                try {
                    val writer = socket.outputStream.bufferedWriter()
                    val contentStr = Gson().toJson(content)
                    writer.write("$contentStr\n")
                    writer.flush()
                    Log.e("SERVER", "Sent message to client: ${socket.inetAddress.hostAddress}")
                } catch (e: Exception) {
                    Log.e("SERVER", "Error sending message to client: ${socket.inetAddress.hostAddress}")
                    e.printStackTrace()
                }
            }
        }
    }

    fun close() {
        try {
            clientMap.forEach { (_, socket) ->
                socket.close()
            }
            clientMap.clear()

            if (!svrSocket.isClosed) {
                svrSocket.close()
            }

            Log.e("SERVER", "Server has been successfully closed.")
        } catch (e: Exception) {
            Log.e("SERVER", "An error occurred while closing the server.")
            e.printStackTrace()
        }
    }

}