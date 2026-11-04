package com.addonstation.mobile.data.repo

import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * ذاكرة مؤقّتة قصيرة العمر (Time-To-Live) لقوائم الشبكة.
 *
 * **لماذا؟** شاشات التطبيق تُعاد تركيبها مع كل رجوع وكل تبديل تبويب، فكانت
 * كل شاشة تُفرغ محتواها وتنتظر رحلات شبكة قبل أن تظهر ⇒ مؤقّت تحميل ووميض
 * ولاق. بالذاكرة: تُبذر الشاشة بالنسخة المحفوظة **فوراً**، ويجري تحديثها في
 * الخلفية بعد انقضاء المدة فقط.
 *
 * الاستعمال في المستودعات:
 * ```
 * private val cache = TtlCache<List<Thing>>(60_000L)
 * fun cached(): List<Thing> = cache.peek().orEmpty()        // بذر الشاشة
 * suspend fun load(force: Boolean = false) = through(cache, force) { … }
 * ```
 *
 * @param ttlMs مدة صلاحية النسخة المحفوظة بالميلي ثانية.
 */
class TtlCache<T>(private val ttlMs: Long) {

    @Volatile
    private var value: T? = null

    @Volatile
    private var at = 0L

    /** يمنع شاشتين من إطلاق الطلب نفسه في اللحظة نفسها بعد انتهاء TTL. */
    private val refreshMutex = Mutex()

    /** النسخة المحفوظة إن كانت ما تزال في مدتها، وإلا null (ساعة رتيبة لا تتأثر بتغيير الوقت). */
    fun fresh(): T? = value?.takeIf { SystemClock.elapsedRealtime() - at < ttlMs }

    /** آخر نسخة معروفة ولو انقضت مدتها — تُبذر بها الشاشة ريثما يُجلب الجديد. */
    fun peek(): T? = value

    fun set(v: T) {
        value = v
        at = SystemClock.elapsedRealtime()
    }

    /** اعتبر الجلب ناجحاً من دون استبدال نسخة مفيدة بنتيجة فارغة عابرة. */
    fun touch() {
        if (value != null) at = SystemClock.elapsedRealtime()
    }

    suspend fun <R> singleFlight(block: suspend () -> R): R = refreshMutex.withLock { block() }

    fun clear() {
        value = null
        at = 0L
    }
}

/**
 * يمرّ عبر الذاكرة: نسخة حديثة ⇒ تُعاد بلا شبكة · وإلا تُجلب وتُحفظ.
 * فإن فشل الجلب (أو أتى فارغاً) تُعاد النسخة القديمة — فلا تختفي الشاشة.
 */
suspend fun <T> through(
    cache: TtlCache<List<T>>,
    force: Boolean = false,
    fetch: suspend () -> List<T>,
): List<T> {
    if (!force) cache.fresh()?.let { return it }
    return cache.singleFlight {
        // قد تكون شاشة أخرى سبقتنا بينما انتظرنا القفل.
        if (!force) cache.fresh()?.let { return@singleFlight it }

        val list = try {
            fetch()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            return@singleFlight cache.peek().orEmpty()
        }
        if (list.isNotEmpty() || cache.peek() == null) {
            cache.set(list)
            return@singleFlight list
        }

        // نتيجة فارغة عابرة لا تمحو شاشة مفيدة، ولا تعيد الطلب مع كل تركيب.
        cache.touch()
        cache.peek().orEmpty()
    }
}
