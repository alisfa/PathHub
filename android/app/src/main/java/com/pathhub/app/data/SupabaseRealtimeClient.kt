package com.pathhub.app.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

enum class RealtimeEventType { INSERT, UPDATE, DELETE, UNKNOWN }

data class RealtimeChangeEvent(
    val table: String,
    val eventType: RealtimeEventType,
    val recordJson: String,
    val oldRecordJson: String
)

interface RealtimeListener {
    fun onTableChanged(event: RealtimeChangeEvent)
}

class SupabaseRealtimeClient(private val context: Context) {
    companion object {
        private const val TAG = "SupabaseRealtime"
        @Volatile
        private var instance: SupabaseRealtimeClient? = null

        fun getInstance(context: Context): SupabaseRealtimeClient {
            return instance ?: synchronized(this) {
                instance ?: SupabaseRealtimeClient(context.applicationContext).also { instance = it }
            }
        }
    }

    private val httpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }
    private val listeners = mutableListOf<RealtimeListener>()
    private var webSocket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private val refCounter = AtomicInteger(1)
    private var isConnected = false

    fun addListener(listener: RealtimeListener) {
        synchronized(listeners) {
            if (!listeners.contains(listener)) listeners.add(listener)
        }
        if (webSocket == null) {
            connect()
        }
    }

    fun removeListener(listener: RealtimeListener) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }

    fun connect() {
        val wsUrl = SupabaseConfig.getRealtimeWsUrl(context)
        Log.d(TAG, "Connecting to Supabase Realtime: $wsUrl")

        val request = Request.Builder().url(wsUrl).build()
        webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "Realtime WebSocket connected!")
                isConnected = true
                startHeartbeats()
                subscribeToTable("sites")
                subscribeToTable("playlist_items")
                subscribeToTable("history")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "Realtime WebSocket failure: ${t.message}. Reconnecting in 5s...")
                isConnected = false
                heartbeatJob?.cancel()
                scope.launch {
                    delay(5000)
                    connect()
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Realtime WebSocket closed: $code $reason")
                isConnected = false
                heartbeatJob?.cancel()
            }
        })
    }

    private fun subscribeToTable(table: String) {
        val ref = refCounter.incrementAndGet().toString()
        val joinMsg = """
            {
                "topic": "realtime:public:$table",
                "event": "phx_join",
                "payload": {
                    "config": {
                        "postgres_changes": [
                            {"event": "*", "schema": "public", "table": "$table"}
                        ]
                    }
                },
                "ref": "$ref"
            }
        """.trimIndent()
        webSocket?.send(joinMsg)
        Log.d(TAG, "Subscribed to realtime table: $table")
    }

    private fun startHeartbeats() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive && isConnected) {
                delay(25000)
                val ref = refCounter.incrementAndGet().toString()
                val hbMsg = """{"topic":"phoenix","event":"heartbeat","payload":{},"ref":"$ref"}"""
                webSocket?.send(hbMsg)
            }
        }
    }

    private fun handleMessage(text: String) {
        try {
            val root = json.parseToJsonElement(text).jsonObject
            val event = root["event"]?.jsonPrimitive?.content ?: ""
            val topic = root["topic"]?.jsonPrimitive?.content ?: ""

            if (event == "postgres_changes") {
                val payload = root["payload"]?.jsonObject ?: return
                val data = payload["data"]?.jsonObject ?: return
                val typeStr = data["type"]?.jsonPrimitive?.content ?: ""
                val record = data["record"]?.toString() ?: "{}"
                val oldRecord = data["old_record"]?.toString() ?: "{}"

                val eventType = when (typeStr.uppercase()) {
                    "INSERT" -> RealtimeEventType.INSERT
                    "UPDATE" -> RealtimeEventType.UPDATE
                    "DELETE" -> RealtimeEventType.DELETE
                    else -> RealtimeEventType.UNKNOWN
                }

                val table = topic.removePrefix("realtime:public:")
                val changeEvent = RealtimeChangeEvent(
                    table = table,
                    eventType = eventType,
                    recordJson = record,
                    oldRecordJson = oldRecord
                )

                synchronized(listeners) {
                    listeners.forEach { it.onTableChanged(changeEvent) }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing realtime message: $text", e)
        }
    }

    fun disconnect() {
        heartbeatJob?.cancel()
        webSocket?.close(1000, "Normal closure")
        webSocket = null
        isConnected = false
    }
}
