package com.addonstation.mobile.data.repo

import com.addonstation.mobile.data.AppState
import com.addonstation.mobile.data.SupabaseClient
import com.addonstation.mobile.data.model.AcTask
import com.addonstation.mobile.data.model.AcTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive

/** اقتصاد AddonCoins: الرصيد، المهام، السجل، وفتح المحتوى بالـ AC. */
class AcRepository(
    private val supabase: SupabaseClient,
    private val state: AppState,
) {

    suspend fun refreshWallet() = withContext(Dispatchers.IO) {
        val uid = supabase.session()?.userId ?: return@withContext
        runCatching {
            val wallet = supabase.decodeOne<com.addonstation.mobile.data.model.AcWallet>(
                supabase.select("ac_wallets", "select=*&user_id=eq.$uid")
            )
            state.setWallet(wallet)
        }
    }

    suspend fun tasks(): List<AcTask> = runCatching {
        supabase.decodeList<AcTask>(
            supabase.select("ac_tasks", "select=*&active=eq.true&order=sort_order.asc")
        )
    }.getOrElse { emptyList() }

    suspend fun transactions(limit: Int = 50): List<AcTransaction> = withContext(Dispatchers.IO) {
        val uid = supabase.session()?.userId ?: return@withContext emptyList()
        runCatching {
            supabase.decodeList<AcTransaction>(
                supabase.select(
                    "ac_transactions",
                    "select=*&user_id=eq.$uid&order=created_at.desc&limit=$limit",
                )
            )
        }.getOrElse { emptyList() }
    }

    /** مطالبة بمكافأة مهمة (مهمة مرة واحدة / يومية / سلسلة). */
    suspend fun claimTask(taskId: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val outcome = supabase.rpc("ac_claim_task", mapOf("p_task_id" to JsonPrimitive(taskId)))
                .asOutcome().requireOk()
            val reward = outcome.data["reward"]?.asInt() ?: 0
            refreshWallet()
            reward
        }
    }

    suspend fun unlockedItemIds(): Set<String> = withContext(Dispatchers.IO) {
        val uid = supabase.session()?.userId ?: return@withContext emptySet()
        runCatching {
            supabase.decodeList<AcTransaction>(
                supabase.select(
                    "ac_transactions",
                    "select=item_id,kind&user_id=eq.$uid&kind=eq.unlock&limit=200",
                )
            ).mapNotNull { it.itemId }.toSet()
        }.getOrElse { emptySet() }
    }
}
