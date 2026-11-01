package com.addonstation.mobile.data.repo

import com.addonstation.mobile.data.SupabaseClient
import com.addonstation.mobile.data.model.ChatNotification
import com.addonstation.mobile.data.model.FriendEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

/** الإشعارات (طلب صداقة، منشن، رد، نشاط موضوع) وقائمة الأصدقاء. */
class NotificationsRepository(private val supabase: SupabaseClient) {

    suspend fun list(limit: Int = 60): List<ChatNotification> = withContext(Dispatchers.IO) {
        val uid = supabase.session()?.userId ?: return@withContext emptyList()
        supabase.decodeList<ChatNotification>(
            supabase.select(
                "chat_notifications",
                "select=*&recipient_id=eq.$uid&order=created_at.desc&limit=$limit",
            )
        )
    }

    suspend fun unreadCount(): Int = withContext(Dispatchers.IO) {
        val uid = supabase.session()?.userId ?: return@withContext 0
        runCatching {
            val rows = supabase.select(
                "chat_notifications",
                "select=id&recipient_id=eq.$uid&is_read=eq.false&limit=200",
            )
            (rows as? JsonArray)?.size ?: 0
        }.getOrElse { 0 }
    }

    suspend fun markRead(ids: List<Long>? = null): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val array = ids?.let { list -> JsonArray(list.map { JsonPrimitive(it) }) }
            supabase.rpc(
                "mark_notifications_read",
                mapOf("p_ids" to array),
            ).asOutcome().requireOk()
            Unit
        }
    }

    /** Delete one owned notification; RLS rejects every other recipient. */
    suspend fun delete(id: Long): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            supabase.delete("chat_notifications", "id=eq.$id")
            Unit
        }
    }

    /** طلبات الصداقة الواردة (لعرضها مع زر القبول). */
    suspend fun incomingFriendRequests(): List<FriendEntry> = withContext(Dispatchers.IO) {
        val uid = supabase.session()?.userId ?: return@withContext emptyList()
        runCatching {
            supabase.decodeList<FriendEntry>(
                supabase.select(
                    "user_friends",
                    "select=*&receiver_id=eq.$uid&status=eq.pending&order=created_at.desc&limit=50",
                )
            )
        }.getOrElse { emptyList() }
    }

    suspend fun outgoingFriendRequests(): List<FriendEntry> = withContext(Dispatchers.IO) {
        val uid = supabase.session()?.userId ?: return@withContext emptyList()
        runCatching {
            supabase.decodeList<FriendEntry>(
                supabase.select(
                    "user_friends",
                    "select=*&requester_id=eq.$uid&status=eq.pending&order=created_at.desc&limit=50",
                )
            )
        }.getOrElse { emptyList() }
    }

    suspend fun acceptedFriends(): List<FriendEntry> = withContext(Dispatchers.IO) {
        val uid = supabase.session()?.userId ?: return@withContext emptyList()
        runCatching {
            supabase.decodeList<FriendEntry>(
                supabase.select(
                    "user_friends",
                    "select=*&status=eq.accepted&or=(requester_id.eq.$uid,receiver_id.eq.$uid)&limit=100",
                )
            )
        }.getOrElse { emptyList() }
    }

    suspend fun respond(requestId: Long, accept: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            supabase.rpc(
                "respond_friend_request",
                mapOf(
                    "p_request_id" to JsonPrimitive(requestId),
                    "p_accept" to JsonPrimitive(accept),
                ),
            ).asOutcome().requireOk()
            Unit
        }
    }
}
