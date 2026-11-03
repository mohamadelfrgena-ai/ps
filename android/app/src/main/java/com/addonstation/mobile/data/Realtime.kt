package com.addonstation.mobile.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * عميل Realtime على بروتوكول Phoenix (نفس ما يستخدمه Supabase):
 * يفتح WebSocket على /realtime/v1/websocket ويرسل phx_join لجدول معين ثم يسلّم السجلات الجديدة.
 * الاستخدام الحقيقي في التطبيق: غرفة الشات (chat_messages) — والاستقصاء الدوري (Polling) يعمل كخطة بديلة.
 */
class SupabaseRealtime(private val supabase: SupabaseClient) {

    private val socketClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()
    }

    fun subscribeChatMessages(
        channelId: String,
        onRecord: (JsonObject) -> Unit,
        onStatus: (String) -> Unit = {},
        /**
         * عدد الأعضاء المتصلين بالقناة الآن (Realtime presence).
         * الوثيقة تطلب «[N] online» في شريط الشات — والقناة مقيّدة بهذه الغرفة
         * (`channel_id=eq.X`) فالحاضرون فيها هم **من في الغرفة** تحديداً.
         * لا طلب شبكة إضافي: الرقم يأتي مع نفس المقبس المفتوح.
         */
        onPresence: (Int) -> Unit = {},
    ): RealtimeSub? = subscribe(
        table = "chat_messages",
        filter = "channel_id=eq.$channelId",
        onRecord = onRecord,
        onStatus = onStatus,
        onPresence = onPresence,
    )

    fun subscribeTable(
        table: String,
        filter: String? = null,
        onRecord: (JsonObject) -> Unit,
        onStatus: (String) -> Unit = {},
    ): RealtimeSub? = subscribe(table, filter, onRecord, onStatus, {})

    private fun subscribe(
        table: String,
        filter: String?,
        onRecord: (JsonObject) -> Unit,
        onStatus: (String) -> Unit,
        onPresence: (Int) -> Unit,
    ): RealtimeSub? {
        val config = supabase.configOrNull() ?: return null
        val session = supabase.session() ?: return null
        val url = buildString {
            append(config.url.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://"))
            append("/realtime/v1/websocket?apikey=")
            append(config.anonKey)
            append("&vsn=1.0.0")
        }

        val topic = "realtime:public:$table" + (filter?.let { ":$it" } ?: "")
        val ref = AtomicLong(1)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        var heartbeat: Job? = null

        val request = Request.Builder()
            .url(url)
            .header("apikey", config.anonKey)
            .header("Authorization", "Bearer ${session.accessToken}")
            .build()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val join = buildJsonObject {
                    put("topic", topic)
                    put("event", "phx_join")
                    put("ref", ref.getAndIncrement().toString())
                    put("payload", buildJsonObject {
                        put("config", buildJsonObject {
                            put("broadcast", buildJsonObject { put("self", false) })
                            put("presence", buildJsonObject { put("key", session.userId) })
                            put("postgres_changes", buildJsonArray {
                                add(buildJsonObject {
                                    put("event", "*")
                                    put("schema", "public")
                                    put("table", table)
                                    if (filter != null) put("filter", filter)
                                })
                            })
                        })
                        put("access_token", session.accessToken)
                    })
                }
                webSocket.send(join.toString())
                onStatus("joined")
                heartbeat = scope.launch {
                    while (true) {
                        delay(25_000)
                        val ping = buildJsonObject {
                            put("topic", "phoenix")
                            put("event", "heartbeat")
                            put("payload", buildJsonObject { })
                            put("ref", ref.getAndIncrement().toString())
                        }
                        // إن أُغلق المقبس (أو فُقدت الشبكة لحظةً) لا يجوز أن
                        // يموت خيط النبض مع الخطأ: نبتلعه ونستمر، فإعادة
                        // الاتصال تتولّاها دورة الإعادة في الأعلى.
                        runCatching { webSocket.send(ping.toString()) }
                    }
                }
            }

            // الحاضرون الآن في هذه القناة (presence): يُبنى من state الأول ثم diffs
            val present = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching {
                    val obj = supabase.json.parseToJsonElement(text).jsonObject
                    val event = obj["event"]?.jsonPrimitive?.contentOrNull ?: return
                    // ── الحضور: `presence_state` أولاً ثم `presence_diff` بعده
                    if (event == "presence_state" || event == "presence_diff") {
                        val payload = obj["payload"]?.jsonObject ?: return
                        if (event == "presence_state") {
                            present.clear()
                            payload.keys.forEach { present.add(it) }
                        } else {
                            payload["joins"]?.jsonObject?.keys?.forEach { present.add(it) }
                            payload["leaves"]?.jsonObject?.keys?.forEach { present.remove(it) }
                        }
                        onPresence(present.size)
                        return
                    }
                    if (event != "postgres_changes") return
                    val payload = obj["payload"]?.jsonObject ?: return
                    val data = payload["data"]?.jsonObject ?: return
                    val record = data["record"]?.jsonObject ?: return
                    onRecord(record)
                }.onFailure { onStatus("error") }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                onStatus("failed")
                heartbeat?.cancel()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                heartbeat?.cancel()
            }
        }

        val socket = socketClient.newWebSocket(request, listener)
        return RealtimeSub {
            heartbeat?.cancel()
            scope.cancel()
            runCatching { socket.close(1000, null) }
        }
    }
}

/** مقبض اشتراك واحد: استدعِ close() عند مغادرة الشاشة أو تبديل الغرفة. */
class RealtimeSub(private val onClose: () -> Unit) {
    @Volatile
    private var closed = false

    fun close() {
        if (closed) return
        closed = true
        onClose()
    }
}
