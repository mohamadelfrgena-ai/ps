package com.addonstation.mobile.data.repo

import com.addonstation.mobile.data.SupabaseClient
import com.addonstation.mobile.data.model.RpcOutcome
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer

/** أدوات فك الترميز: Supabase يعيد مصفوفات JSON، وهذه الدوال تحوّلها إلى كائنات Kotlin. */
inline fun <reified T> SupabaseClient.decodeList(element: JsonElement): List<T> =
    runCatching { json.decodeFromJsonElement(ListSerializer(serializer<T>()), element) }.getOrElse { emptyList() }

inline fun <reified T> SupabaseClient.decodeOne(element: JsonElement): T? =
    (element as? JsonArray)?.firstOrNull()?.let {
        runCatching { json.decodeFromJsonElement(serializer<T>(), it) }.getOrNull()
    }

fun JsonElement?.asObject(): JsonObject? = this as? JsonObject

// ---------------------------------------------------------------- قراءة قيم JSON
// (دوال مساعدة تستخدمها المستودعات لقراءة حقول نتائج RPC: asInt()/asString()/…)
fun JsonElement?.asString(): String? = (this as? JsonPrimitive)?.contentOrNull
fun JsonElement?.asInt(): Int? = (this as? JsonPrimitive)?.intOrNull
fun JsonElement?.asLong(): Long? = (this as? JsonPrimitive)?.longOrNull
fun JsonElement?.asDouble(): Double? = (this as? JsonPrimitive)?.doubleOrNull
fun JsonElement?.asBool(): Boolean? = (this as? JsonPrimitive)?.booleanOrNull
fun JsonElement?.asArray(): JsonArray? = this as? JsonArray

fun JsonObject?.str(key: String): String? = this?.get(key)?.jsonPrimitive?.contentOrNull
fun JsonObject?.num(key: String): Int? = this?.get(key)?.jsonPrimitive?.intOrNull
fun JsonObject?.dbl(key: String): Double? = this?.get(key)?.jsonPrimitive?.doubleOrNull
fun JsonObject?.flag(key: String): Boolean? = this?.get(key)?.jsonPrimitive?.booleanOrNull
fun JsonObject?.obj(key: String): JsonObject? = this?.get(key) as? JsonObject

/** يحوّل نتيجة دالة RPC إلى نتيجة موحّدة (ok / message / بقية الحقول). */
fun JsonElement?.toOutcome(): RpcOutcome {
    val obj = this as? JsonObject ?: return RpcOutcome(ok = true, message = null, data = JsonObject(emptyMap()))
    val okValue = obj["ok"]?.jsonPrimitive?.booleanOrNull
        ?: obj["success"]?.jsonPrimitive?.booleanOrNull
        ?: true
    val message = obj["message"]?.jsonPrimitive?.contentOrNull
        ?: obj["error"]?.jsonPrimitive?.contentOrNull
    return RpcOutcome(ok = okValue, message = message, data = obj)
}

/** اسم بديل مختصر لـtoOutcome() تستخدمه المستودعات في كل مكان. */
fun JsonElement?.asOutcome(): RpcOutcome = this.toOutcome()

/** يرمي خطأً عربياً مفهوماً إذا كانت الدالة أعادت ok=false. */
fun RpcOutcome.orThrow(fallback: String? = null): RpcOutcome {
    if (!ok) throw com.addonstation.mobile.data.ApiError.message(message ?: fallback)
    return this
}

/** مثل orThrow بلا وسيط — توقف التنفيذ إن كانت النتيجة غير ناجحة. */
fun RpcOutcome.requireOk(): RpcOutcome = orThrow()

/** يبني وسائط RPC ويتجاهل القيم الفارغة (null) حتى لا تُرسل للحقول الاختيارية. */
fun params(vararg pairs: Pair<String, JsonElement?>): Map<String, JsonElement?> =
    pairs.filter { it.second != null }.toMap()

fun p(value: String?): JsonElement? = value?.let { JsonPrimitive(it) }
fun p(value: Int?): JsonElement? = value?.let { JsonPrimitive(it) }
fun p(value: Double?): JsonElement? = value?.let { JsonPrimitive(it) }
fun p(value: Boolean?): JsonElement? = value?.let { JsonPrimitive(it) }
fun p(value: Long?): JsonElement? = value?.let { JsonPrimitive(it) }
