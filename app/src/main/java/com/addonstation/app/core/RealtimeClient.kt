package com.addonstation.app.core

import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.url
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** A postgres_changes event received over the realtime socket. */
data class PgChange(
    val type: String, // INSERT | UPDATE | DELETE
    val table: String,
    val record: JsonObject?,
    val oldRecord: JsonObject?
)

/**
 * Minimal Supabase Realtime v1 client (phoenix protocol) for:
 *  - chat_messages filtered by channel_id
 *  - chat_notifications filtered by recipient_id
 */
class RealtimeClient(private val backend: SupabaseBackend) {

    private val json get() = backend.json

    fun chatMessages(channelId: Long): Flow<PgChange> = pgChanges(
        channel = "mchat-$channelId",
        table = "chat_messages",
        filter = "channel_id=eq.$channelId",
        event = "*"
    )

    fun notifications(userId: String): Flow<PgChange> = pgChanges(
        channel = "notif-$userId",
        table = "chat_notifications",
        filter = "recipient_id=eq.$userId",
        event = "INSERT"
    )

    private fun pgChanges(channel: String, table: String, filter: String, event: String): Flow<PgChange> =
        callbackFlow {
            val host = backend.config.url.removePrefix("https://").removePrefix("http://").trimEnd('/')
            val url =
                "wss://$host/realtime/v1/websocket?apikey=${backend.config.anonKey}&vsn=1.0.0"
            var ref = 0
            fun nextRef(): String = (++ref).toString()

            val session = try {
                backend.http.webSocketSession { url(url) }
            } catch (e: Exception) {
                close(e)
                return@callbackFlow
            }

            val jwt = backend.tokenProvider() ?: backend.config.anonKey
            val topic = "realtime:$channel"
            val joinRef = nextRef()
            val join = buildJsonObject {
                put("topic", topic)
                put("event", "phx_join")
                put("payload", buildJsonObject {
                    put("config", buildJsonObject {
                        put("broadcast", buildJsonObject {
                            put("ack", false); put("self", false)
                        })
                        put("postgres_changes", kotlinx.serialization.json.JsonArray(
                            listOf(
                                buildJsonObject {
                                    put("event", event)
                                    put("schema", "public")
                                    put("table", table)
                                    put("filter", filter)
                                }
                            )
                        ))
                    })
                    put("access_token", jwt)
                })
                put("ref", joinRef)
                put("join_ref", joinRef)
            }
            session.send(Frame.Text(join.toString()))

            val heartbeat = launch {
                while (isActive) {
                    delay(25_000)
                    try {
                        session.send(
                            Frame.Text(
                                buildJsonObject {
                                    put("topic", "phoenix")
                                    put("event", "heartbeat")
                                    put("payload", buildJsonObject {})
                                    put("ref", nextRef())
                                }.toString()
                            )
                        )
                    } catch (_: Exception) { }
                }
            }

            try {
                for (frame in session.incoming) {
                    if (frame !is Frame.Text) continue
                    val el = try {
                        json.parseToJsonElement(frame.readText()).jsonObject
                    } catch (_: Exception) { continue }
                    if (el["event"]?.jsonPrimitive?.content != "postgres_changes") continue
                    val payload = el["payload"]?.jsonObject ?: continue
                    val data = payload["data"]?.jsonObject ?: continue
                    val change = PgChange(
                        type = data["type"]?.jsonPrimitive?.content ?: continue,
                        table = data["table"]?.jsonPrimitive?.content ?: table,
                        record = data["record"] as? JsonObject,
                        oldRecord = data["old_record"] as? JsonObject
                    )
                    trySend(change)
                }
            } catch (e: Exception) {
                close(e)
            } finally {
                heartbeat.cancel()
            }
            awaitClose {
                heartbeat.cancel()
                launch {
                    try {
                        session.close()
                    } catch (_: Exception) { }
                }
            }
        }.retryWhen { _, attempt ->
            if (attempt >= 6) return@retryWhen false
            delay(2_000L * (attempt + 1))
            true
        }.catch { /* terminal failure: collectors fall back to polling */ }
}
