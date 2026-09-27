package com.addonstation.app.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.addonstation.app.data.ApiException
import com.addonstation.app.data.Profile
import com.addonstation.app.data.Repository
import com.addonstation.app.data.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppModel(app: Application) : AndroidViewModel(app) {
    val repo = Repository(app)
    var session by mutableStateOf<Session?>(repo.session())
    var profile by mutableStateOf<Profile?>(null)
    var language by mutableStateOf(repo.language())
    var themeName by mutableStateOf(repo.theme())
    var balance by mutableStateOf<Int?>(null)
    var toast by mutableStateOf<String?>(null)
    var showSplash by mutableStateOf(true)

    val text: Strings get() = Strings(language)
    val rtl: Boolean get() = language == "ar"

    init {
        refreshIdentity()
    }

    fun launch(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (error: Exception) {
                toast = friendly(error)
            }
        }
    }

    fun <T> io(block: () -> T, onOk: (T) -> Unit = {}) = launch {
        val value = withContext(Dispatchers.IO) { block() }
        onOk(value)
    }

    fun refreshIdentity() = io({
        val current = repo.session()
        val loaded = current?.let { runCatching { repo.profile(it.userId) }.getOrNull() }
        val coins = current?.let { runCatching { repo.balance() }.getOrNull() }
        Triple(current, loaded, coins)
    }) { (current, loaded, coins) ->
        session = current
        profile = loaded
        balance = coins
    }

    fun setLanguage(code: String) {
        language = code
        repo.api.putPref("language", code)
    }

    fun setTheme(name: String) {
        themeName = name
        repo.api.putPref("theme", name)
    }

    fun signedIn(): Boolean = session != null

    fun friendly(error: Throwable): String {
        val message = error.message.orEmpty()
        return when {
            message.contains("Invalid login", true) -> text.badLogin
            message.contains("not confirmed", true) -> text.confirmEmail
            message.contains("already registered", true) -> text.alreadyRegistered
            message.contains("Not enough AC", true) -> text.notEnoughAc
            message.contains("Login required", true) -> text.signInFirst
            error is ApiException && error.status == 401 -> text.signInFirst
            message.contains("Unable to resolve", true) || message.contains("Failed to connect", true) || message.contains("timeout", true) -> text.offline
            else -> message.ifBlank { text.genericError }
        }
    }
}

class Strings(private val lang: String) {
    private fun t(ar: String, en: String) = if (lang == "ar") ar else en

    val home = t("الرئيسية", "Home")
    val store = t("المتجر", "Store")
    val community = t("المجتمع", "Community")
    val profile = t("حسابي", "Profile")
    val settings = t("الإعدادات", "Settings")
    val worlds = t("العوالم", "Worlds")
    val addons = t("الإضافات", "Addons")
    val search = t("ابحث عن إضافات وعوالم...", "Search addons and worlds...")
    val guestTitle = t("متجر إضافات وعوالم Minecraft", "Minecraft addons and worlds")
    val guestBody = t("حمّل إضافات وعوالم، شارك في المجتمع، وانضم إلى العائلة.", "Download addons and worlds, join the community, and keep your progress.")
    val signIn = t("تسجيل الدخول", "Sign in")
    val register = t("حساب جديد", "Create account")
    val signOut = t("تسجيل الخروج", "Sign out")
    val email = t("البريد الإلكتروني", "Email")
    val password = t("كلمة المرور", "Password")
    val username = t("اسم المستخدم", "Username")
    val forgot = t("نسيت كلمة المرور؟", "Forgot password?")
    val noAccount = t("ليس لديك حساب؟", "No account?")
    val hasAccount = t("لديك حساب؟", "Already have an account?")
    val featured = t("مميز", "Featured")
    val latest = t("أحدث الإضافات", "Latest")
    val recommended = t("موصى به لك", "Recommended")
    val creators = t("الصنّاع", "Creators")
    val viewAll = t("عرض الكل", "View all")
    val plans = t("خطط الاشتراك", "Subscription plans")
    val exclusives = t("الحصريات", "Exclusives")
    val becomeCreator = t("كن صانع محتوى", "Become a Creator")
    val creatorStudio = t("استوديو الصانع", "Creator Studio")
    val wallet = t("محفظة AC", "AC Wallet")
    val favorites = t("مفضلتي", "Favorites")
    val myDownloads = t("تحميلاتي", "Downloads")
    val upgrade = t("الترقية", "Upgrade")
    val support = t("الدعم", "Support")
    val download = t("تحميل", "Download")
    val save = t("حفظ", "Save")
    val saved = t("محفوظ", "Saved")
    val description = t("الوصف", "Description")
    val similar = t("قد يعجبك أيضًا", "You may also like")
    val signInFirst = t("سجل الدخول أولًا", "Sign in first")
    val fileMissing = t("ملف التحميل غير مرفوع بعد", "The download file is not uploaded yet")
    val notEnoughAc = t("رصيد AC غير كافٍ", "Not enough AC")
    val badLogin = t("بيانات الدخول غير صحيحة", "Invalid login credentials")
    val confirmEmail = t("أكّد البريد ثم سجل الدخول", "Confirm your email, then sign in")
    val alreadyRegistered = t("هذا الحساب موجود", "This account already exists")
    val offline = t("تعذر الاتصال بخادم AddonStation", "Could not reach AddonStation")
    val genericError = t("حدث خطأ غير متوقع", "Something went wrong")
    val rulesTitle = t("قوانين المجتمع", "Community rules")
    val rulesAccept = t("أوافق وأدخل الشات", "I agree and enter chat")
    val send = t("إرسال", "Send")
    val stickers = t("ملصقات", "Stickers")
    val notifications = t("التنبيهات", "Notifications")
    val language = t("لغة التطبيق", "App language")
    val appearance = t("مظهر التطبيق", "Appearance")
    val dark = t("داكن", "Dark")
    val light = t("فاتح", "Light")
    val terms = t("الشروط والأحكام", "Terms")
    val privacy = t("الخصوصية", "Privacy")
    val deleteAccount = t("حذف الحساب", "Delete account")
    val version = t("الإصدار 1.2", "Version 1.2")
    val empty = t("لا يوجد محتوى بعد", "Nothing here yet")
    val follow = t("متابعة", "Follow")
    val following = t("تتابعه", "Following")
    val tip = t("دعم الصانع", "Support creator")
    val apply = t("تقديم طلب", "Submit application")
    val claim = t("استلام", "Claim")
    val balance = t("الرصيد الحالي", "Current balance")
    val newest = t("الأحدث", "Newest")
    val mostDownloads = t("الأكثر تحميلًا", "Downloads")
    val topRated = t("الأعلى تقييمًا", "Rating")
    val welcomeGuest = t("مرحبًا بك في", "Welcome to")
    val quick = t("إجراءات سريعة", "Quick actions")
    val device = t("المساعدة", "Help")
}
