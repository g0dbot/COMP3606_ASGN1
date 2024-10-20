/*IDS 816034693 816017853*/

package dev.kwasi.echoservercomplete.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class Database(context: Context, factory: SQLiteDatabase.CursorFactory?) : SQLiteOpenHelper(context, DB_NAME, factory, DB_VERSION) {
    companion object {
        private const val DB_NAME = "ChatDatabase.db" // Name of your database
        private const val DB_VERSION = 1 // Initial version of your database
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createChatMessageTableQuery = """
            CREATE TABLE ChatMessage (
                messageID INTEGER PRIMARY KEY AUTOINCREMENT,
                deviceName TEXT,
                text TEXT,
                timestamp INTEGER
            )
        """.trimIndent()

        db.execSQL(createChatMessageTableQuery)
    }

    // Handle database upgrades
    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        // Handle upgrade logic here
    }

    // Insert a new chat message
    fun createChatMessage(deviceName: String, text: String) {
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("deviceName", deviceName)
            put("text", text)
            put("timestamp", now)
        }

        val db = this.writableDatabase
        db.insert("ChatMessage", null, values)
        db.close()
    }

    // Retrieve all messages for a specific device name
    fun getMessagesForDevice(deviceName: String): List<ChatMessage> {
        val result: MutableList<ChatMessage> = mutableListOf()
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM ChatMessage WHERE deviceName = ?", arrayOf(deviceName))

        if (cursor.moveToFirst()) {
            do {
                val messageIdIdx = cursor.getColumnIndex("messageID")
                val messageTextIdx = cursor.getColumnIndex("text")
                val timestampIdx = cursor.getColumnIndex("timestamp")

                // Check if indices are valid before retrieving values
                if (messageIdIdx != -1 && messageTextIdx != -1 && timestampIdx != -1) {
                    val messageId = cursor.getInt(messageIdIdx)
                    val messageText = cursor.getString(messageTextIdx)
                    val timestamp = cursor.getLong(timestampIdx)
                    result.add(ChatMessage(messageId, deviceName, messageText, timestamp))
                }
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return result
    }

}

// Data class for chat messages
data class ChatMessage(val messageId: Int, val deviceName: String, val text: String, val timestamp: Long)
