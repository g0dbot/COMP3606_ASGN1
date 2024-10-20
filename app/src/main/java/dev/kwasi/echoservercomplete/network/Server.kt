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
    private val validStudentIds = listOf("816017853", "816123456") // Add more IDs as needed

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
        thread {
            val clientIp = socket.inetAddress.hostAddress
            val reader = socket.inputStream.bufferedReader()
            val writer = socket.outputStream.bufferedWriter()

            try {
                // Wait for "I am here" message
                val initialMessage = reader.readLine()
                if (initialMessage == "I am here") {
                    // Generate and send random number
                    val randomNumber = Encryption().genRandomNum().toString()
                    writer.write("$randomNumber\n")
                    writer.flush()

                    // Receive encrypted response
                    val encryptedResponse = reader.readLine()

                    // Verify response
                    val studentId = verifyStudentResponse(encryptedResponse, randomNumber)
                    if (studentId != null) {
                        clientMap[clientIp!!] = socket
                        Log.e("SERVER", "Student authenticated: $studentId")
                        writer.write("AUTH_SUCCESS\n")
                        writer.flush()
                        
                        // Continue with normal message handling
                        handleAuthenticatedClient(clientIp, reader)
                    } else {
                        Log.e("SERVER", "Authentication failed for client: $clientIp")
                        writer.write("AUTH_FAILED\n")
                        writer.flush()
                        socket.close()
                    }
                } else {
                    Log.e("SERVER", "Invalid initial message from client: $clientIp")
                    socket.close()
                }
            } catch (e: Exception) {
                Log.e("SERVER", "Error handling client $clientIp", e)
                socket.close()
            }
        }
    }

    private fun verifyStudentResponse(encryptedResponse: String, randomNumber: String): String? {
        val encryption = Encryption()
        for (studentId in encryption.students) {
            if (encryption.verifyResponse(encryptedResponse, randomNumber, studentId)) {
                return studentId
            }
        }
        return null
    }

    private fun handleAuthenticatedClient(clientIp: String, reader: BufferedReader) {
        while (true) {
            try {
                val receivedJson = reader.readLine()
                if (receivedJson != null) {
                    Log.e("SERVER", "Received a message from client $clientIp")
                    val clientContent = Gson().fromJson(receivedJson, ContentModel::class.java)

                    //store the msg in the database
                    dbHelper.createChatMessage(clientIp, clientContent.message)

                    iFaceImpl.onContent(clientContent)
                }
            } catch (e: Exception) {
                Log.e("SERVER", "An error has occurred with the client $clientIp")
                e.printStackTrace()
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
