package com.addonstation.mobile.ui.chat

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Small in-memory snapshot of rooms the user has already opened.
 *
 * The rooms screen uses this for a real last-message preview, timestamp and (when
 * Realtime presence has reported it) online count. It deliberately never fetches
 * each room just to paint the list; rooms not opened in this session fall back to
 * their server-provided description and omit unknown counts.
 */
data class ChatRoomPeek(
    val createdAt: String? = null,
    val preview: String? = null,
    val messageType: String? = null,
    val onlineCount: Int? = null,
)

object ChatPeek {
    private val lock = Any()
    private val _rooms = MutableStateFlow<Map<String, ChatRoomPeek>>(emptyMap())
    val rooms: StateFlow<Map<String, ChatRoomPeek>> = _rooms.asStateFlow()

    /** Record the latest real message already loaded by the open room. */
    fun note(
        channelId: String,
        iso: String?,
        preview: String? = null,
        messageType: String? = null,
    ) {
        if (channelId.isBlank() || iso.isNullOrBlank()) return
        synchronized(lock) {
            val current = _rooms.value
            val old = current[channelId] ?: ChatRoomPeek()
            val next = old.copy(
                createdAt = iso,
                preview = preview?.trim()?.takeIf { it.isNotEmpty() },
                messageType = messageType?.takeIf { it.isNotBlank() },
            )
            if (next != old) _rooms.value = current + (channelId to next)
        }
    }

    /** A presence callback is room-scoped; null means this room has not reported yet. */
    fun noteOnline(channelId: String, count: Int) {
        if (channelId.isBlank()) return
        synchronized(lock) {
            val current = _rooms.value
            val old = current[channelId] ?: ChatRoomPeek()
            val next = old.copy(onlineCount = count.coerceAtLeast(0))
            if (next != old) _rooms.value = current + (channelId to next)
        }
    }

    fun room(channelId: String): ChatRoomPeek? = _rooms.value[channelId]

    /** Compatibility helper for the existing room-time call sites. */
    fun last(channelId: String): String? = _rooms.value[channelId]?.createdAt

    /** Clear the previous account's room snippets on sign-out. */
    fun clear() {
        synchronized(lock) { _rooms.value = emptyMap() }
    }
}
