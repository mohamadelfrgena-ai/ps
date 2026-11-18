package com.addonstation.mobile.data.repo

import com.addonstation.mobile.data.SupabaseClient
import com.addonstation.mobile.data.model.PlanAcPricing
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Parse the shared Android / PS4 / PS5 plan-pricing RPC contract. */
internal fun parsePlanAcPricingSnapshot(element: JsonElement?): List<PlanAcPricing> {
    val outcome = element.asOutcome().requireOk()
    val serverNow = outcome.data["server_now_epoch_ms"].asLong()
        ?: throw IllegalStateException("plan_ac_pricing_server_time_missing")
    val revision = outcome.data["revision"].asString()

    return outcome.data["plans"].asArray().orEmpty().mapNotNull { row ->
        val obj = row as? JsonObject ?: return@mapNotNull null
        val plan = obj["plan"].asString()?.lowercase()?.takeIf { it == "epic" || it == "legendary" }
            ?: return@mapNotNull null
        val basePrice = obj["base_ac_price"].asInt()?.coerceAtLeast(0) ?: 0
        val salePrice = obj["sale_ac_price"].asInt()?.takeIf { it > 0 }
        PlanAcPricing(
            plan = plan,
            baseAcPrice = basePrice,
            saleAcPrice = salePrice,
            effectiveAcPriceAtFetch = obj["effective_ac_price"].asInt()?.coerceAtLeast(0) ?: basePrice,
            discountActiveAtFetch = obj["discount_active"].asBool() == true,
            discountPercent = obj["discount_percent"].asInt()?.coerceIn(0, 100) ?: 0,
            saleStartsAt = obj["sale_starts_at"].asString(),
            saleEndsAt = obj["sale_ends_at"].asString(),
            saleEndsAtEpochMs = obj["sale_ends_at_epoch_ms"].asLong(),
            serverNowEpochMs = serverNow,
            updatedAt = obj["updated_at"].asString(),
            revision = revision,
        )
    }
}

internal suspend fun loadPlanAcPricingSnapshot(supabase: SupabaseClient): List<PlanAcPricing> =
    parsePlanAcPricingSnapshot(supabase.rpc("plan_ac_pricing_snapshot"))
