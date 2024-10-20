package dev.kwasi.echoservercomplete.network

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import dev.kwasi.echoservercomplete.database.Database
import dev.kwasi.echoservercomplete.models.ContentModel
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.Exception
import kotlin.concurrent.thread

class Server(private val iFaceImpl: NetworkMessageInterface, context: Context) {
    companion object {
        const val PORT: Int = 9999
    }

    private val svrSocket: ServerSocket = ServerSocket(PORT, 0, InetAddress.getByName("192.168.49.1"))
    private val clientMap: HashMap<String, Socket> = HashMap()
    private val dbHelper = Database(context, null) // Pass null for CursorFactory

    init {
        thread {
            while (true) {
                try {
                    val clientConnectionSocket = svrSocket.accept()
                    Log.e("SERVER", "The server has accepted a connection: ")
                    handleSocket(clientConnectionSocket)
                } catch (e: Exception) {
                    Log.e("SERVER", "An error has occurred in the server!")
                    e.printStackTrace()
                }
            }
        }
    }

    private fun handleSocket(socket: Socket) {
        socket.inetAddress.hostAddress?.let { ipAddress ->
            clientMap[ipAddress] = socket
            Log.e("SERVER", "A new connection has been detected!")
            thread {
                val clientReader = socket.inputStream.bufferedReader()
                var receivedJson: String?

                while (socket.isConnected) {
                    try {
                        receivedJson = clientReader.readLine()
                        if (receivedJson != null) {
                            Log.e("SERVER", "Received a message from client $ipAddress")
                            val clientContent = Gson().fromJson(receivedJson, ContentModel::class.java)

                            //store the msg in the database
                            dbHelper.createChatMessage(ipAddress, clientContent.message)

                            iFaceImpl.onContent(clientContent)
                        }
                    } catch (e: Exception) {
                        Log.e("SERVER", "An error has occurred with the client $ipAddress")
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

                    val clientIp = socket.inetAddress.hostAddress ?: "Unknown IP"

                    dbHelper.createChatMessage(clientIp, content.message)

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
            clientMap.forEach { (_, socket) -> socket.close() }
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