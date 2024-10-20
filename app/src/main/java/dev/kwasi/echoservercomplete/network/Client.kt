package dev.kwasi.echoservercomplete.network

import android.util.Log
import com.google.gson.Gson
import dev.kwasi.echoservercomplete.models.ContentModel
import java.io.BufferedReader
import java.io.BufferedWriter
import java.net.Socket
import kotlin.concurrent.thread

class Client (private val networkMessageInterface: NetworkMessageInterface){
    private lateinit var clientSocket: Socket
    private lateinit var reader: BufferedReader
    private lateinit var writer: BufferedWriter
    var ip:String = ""
    private var studentId: String = ""

    init {
        thread {
            try {
                clientSocket = Socket("192.168.49.1", Server.PORT)
                reader = clientSocket.inputStream.bufferedReader()
                writer = clientSocket.outputStream.bufferedWriter()
                ip = clientSocket.inetAddress.hostAddress!!

                while (true) {
                    val serverResponse = reader.readLine()
                    if (serverResponse != null) {
                        val serverContent = Gson().fromJson(serverResponse, ContentModel::class.java)
                        networkMessageInterface.onContent(serverContent)
                    }
                }
            } catch (e: Exception) {
                Log.e("CLIENT", "An error has occurred in the client: ${e.message}", e)
            }
        }
    }

    fun sendId(studentId: String) {
        thread {
            try {
                if (!::clientSocket.isInitialized || !clientSocket.isConnected) {
                    throw Exception("Client socket is not initialized or connected!")
                }

                val content = ContentModel(message = studentId, senderIp = ip)
                val contentAsStr: String = Gson().toJson(content)

                writer.write("$contentAsStr\n")
                writer.flush()
            } catch (e: Exception) {
                Log.e("CLIENT", "Error sending student ID: ${e.message}", e)
            }
        }
    }

    fun sendMessage(content: ContentModel){
        thread {
            if (!clientSocket.isConnected){
                throw Exception("We aren't currently connected to the server!")
            }
            val contentAsStr:String = Gson().toJson(content)
            writer.write("$contentAsStr\n")
            writer.flush()
        }

    }

    fun close() {
        try {
            writer.close()
            reader.close()
            clientSocket.close()
            Log.e("CLIENT", "Client connection closed properly")
        } catch (e: Exception) {
            Log.e("CLIENT", "An error occurred while closing the client")
            e.printStackTrace()
        }
    }
}