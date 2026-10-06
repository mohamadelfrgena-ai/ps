package com.addonstation.mobile.data.repo

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

    /** النسخة المحفوظة إن كانت ما تزال في مدتها، وإلا null. */
    fun fresh(): T? = value?.takeIf { System.currentTimeMillis() - at < ttlMs }

    /** آخر نسخة معروفة ولو انقضت مدتها — تُبذر بها الشاشة ريثما يُجلب الجديد. */
    fun peek(): T? = value

    fun set(v: T) {
        value = v
        at = System.currentTimeMillis()
    }

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
    val list = runCatching { fetch() }.getOrElse { emptyList() }
    if (list.isNotEmpty()) cache.set(list)
    return list.ifEmpty { cache.peek().orEmpty() }
}
