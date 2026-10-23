#ifndef PKG_ADDON_TEST_SUPABASE_CLIENT_HPP
#define PKG_ADDON_TEST_SUPABASE_CLIENT_HPP

#include <string>
#include <vector>

namespace pkgtest {

// ----------------------------------------------------------------------------
//  عميل Supabase المبسّط — نظام الدخول برمز (ربط جهاز السوني بالحساب)
//  الدوال موجودة في قاعدة البيانات (supabase/40_ps4_and_deals.sql):
//    create_console_link_code(p_platform, p_device_name)  ← من السوني
//    poll_console_link_code(p_code, p_poll_token)         ← من السوني كل 3 ثوانٍ
//    claim_console_link_code(p_code, ...)                 ← من الهاتف فقط
// ----------------------------------------------------------------------------

struct SupabaseConfig {
    std::string url;      // https://xxxx.supabase.co
    std::string anonKey;  // anon / publishable key
    bool ok() const { return !url.empty() && !anonKey.empty(); }
};

// content_config.hpp + override من /data/AddonStation/supabase.cfg إن وُجد
SupabaseConfig loadSupabaseConfig();

// مساعدات قراءة JSON بسيطة (استجابات الدوال معروفة الشكل)
std::string jsonGetString(const std::string& json, const std::string& key);
long jsonGetInt(const std::string& json, const std::string& key, long def = 0);
bool jsonGetBool(const std::string& json, const std::string& key, bool def = false);
// قيمة كائن/مصفوفة خام متوازنة {...} أو [...] بعد المفتاح
std::string jsonGetRaw(const std::string& json, const std::string& key);
// تفكيك مصفوفة JSON [...] إلى كائنات {...} منفصلة
std::vector<std::string> splitJsonObjects(const std::string& jsonArray);

struct RpcResult {
    bool ok = false;        // نجاح النقل + الاستجابة قابلة للقراءة
    bool rpcOk = false;     // قيمة ok من الدالة نفسها
    int statusCode = 0;
    std::string body;       // الاستجابة الكاملة
    std::string message;    // message من الدالة أو رسالة خطأ النقل
};

// POST /rest/v1/rpc/<fn>  بجسم JSON (userToken اختياري لاستدعاءات authenticated)
RpcResult supabaseRpc(const SupabaseConfig& cfg,
                      const std::string& fn,
                      const std::string& jsonBody,
                      const std::string& userToken = "");

// استدعاءات نظام الكود
RpcResult callCreateLinkCode(const SupabaseConfig& cfg,
                             const std::string& platform = "ps4",
                             const std::string& deviceName = "PlayStation 4");
RpcResult callPollLinkCode(const SupabaseConfig& cfg,
                           const std::string& code,
                           const std::string& pollToken);

// تسجيل الدخول بالبريد عبر Supabase Auth (شاشة Email في التطبيق)
struct AuthLogin {
    bool ok = false;
    std::string message;
    std::string userId;
    std::string email;
    std::string displayName;
    std::string accessToken;
    std::string refreshToken;
};
AuthLogin callEmailLogin(const SupabaseConfig& cfg,
                         const std::string& email,
                         const std::string& password);

// بيانات العرض المرتبطة من قاعدة البيانات
struct BannerData {
    std::string slug;
    std::string theme;        // winter | sunset | night
    std::string kickerI18n;   // كائن json خام {"en":..,"ar":..}
    std::string titleI18n;
    std::string subtitleI18n;
    std::string primaryLabelI18n;
    std::string secondaryLabelI18n;
    std::string badgeText;    // مثل -50%
    long discountPercent = 0;
    std::string targetType;   // deals | item | creators | store
    std::string coverUrl;     // optional artwork (v7.3)
    std::string endsAt;       // ISO timestamp (v7.3 countdown)
};

struct PackTarget {
    std::string target;       // resource_packs | behavior_packs | minecraftWorlds
    std::string destFolder;
    std::string rawFile;
    std::string ps4RawUrl;
    std::string sha256;
};

struct ItemData {
    std::string id;
    std::string title;
    std::string slug;
    std::string contentType;
    std::string plan;
    long acPrice = 0;
    long saleAcPrice = -1;    // -1 = لا يوجد سعر خصم
    bool isFeatured = false;
    bool isExclusive = false;
    // ---- v7.3 fields (all optional — empty/0 hides them in the UI) ----
    std::string description;
    std::string category;
    std::string coverUrl;
    std::string version;
    std::string creatorName;  // profiles.display_name of created_by
    long long fileSize = 0;
    long downloads = 0;
    double rating = 0;
    long ratingCount = 0;
    long exclusiveSlot = 0;
    std::string createdAt;    // ISO timestamp (UTC)
    std::string ps4RawUrl;
    std::string packTargetsRaw; // raw jsonb (pack_targets)
};

// قراءة البانرات والمحتوى المنشور (anon)
std::vector<BannerData> fetchStoreBanners(const SupabaseConfig& cfg);
std::vector<ItemData> fetchContentItems(const SupabaseConfig& cfg, int limit = 20);

// طلب التحميل المسجَّل (يتطلب جلسة مستخدم) → رابط .raw ووجهات الحزم
struct DownloadGrant {
    bool ok = false;
    std::string message;
    std::string title;
    std::string contentType;
    std::string ps4RawUrl;
    std::vector<PackTarget> packs;
};
DownloadGrant callRecordContentDownload(const SupabaseConfig& cfg,
                                        const std::string& itemId,
                                        const std::string& userAccessToken);

// ترجمة كائن i18n {"en":..,"ar":..} للغة المطلوبة (مع fallback للإنجليزية)
std::string i18nPick(const std::string& i18nJson, const std::string& langCode);

// ---- v7.3: console code sign-in (console_code_* RPCs) ---------------------------------
// console_code_create(device_name, app_version, 'ps4', fingerprint)
RpcResult callConsoleCodeCreate(const SupabaseConfig& cfg,
                                const std::string& deviceName,
                                const std::string& appVersion,
                                const std::string& fingerprint);
// console_code_consume(code, poll_secret) — 0 pending / 1 approved / 2 expired / 3 error
RpcResult callConsoleCodeConsume(const SupabaseConfig& cfg,
                                 const std::string& code,
                                 const std::string& pollSecret);
// console_code_complete(exchange_id, linked_console_id, session_ref)
RpcResult callConsoleCodeComplete(const SupabaseConfig& cfg,
                                  const std::string& exchangeId,
                                  const std::string& linkedConsoleId,
                                  const std::string& sessionRef);
RpcResult callConsoleSessionTouch(const SupabaseConfig& cfg, const std::string& sessionToken);
RpcResult callConsoleUnlink(const SupabaseConfig& cfg, const std::string& sessionToken);
// ac_unlock_content(p_item_id) — user-authenticated
RpcResult callAcUnlock(const SupabaseConfig& cfg, const std::string& itemId, const std::string& userToken);

// ---- v7.3: account / wallet / plans --------------------------------------------------
struct ProfileRow {
    bool ok = false;
    std::string displayName, username, avatarUrl, plan;
    std::string planExpiresAt;
    long acBalance = 0;
    long downloads = 0, favorites = 0;
};
ProfileRow fetchProfile(const SupabaseConfig& cfg, const std::string& userId, const std::string& userToken);

struct WalletRow {
    long amount = 0;
    std::string kind, label, at;
    long balanceAfter = 0;
};
std::vector<WalletRow> fetchWalletTx(const SupabaseConfig& cfg, const std::string& userId, const std::string& userToken, int limit = 8);

struct PlanRow {
    std::string plan;          // free | epic | legendary
    std::string priceLabel;    // already formatted ("" = hidden)
    long downloadLimit = 0;    // -1 unlimited
    std::string limitPeriod;   // day | week | month
};
std::vector<PlanRow> fetchSubscriptionPlans(const SupabaseConfig& cfg);

// جلسة المستخدم المرتبط (تُحفَظ على القرص لتعمل بعد إعادة التشغيل)
struct LinkSession {
    bool valid = false;
    std::string userId;
    std::string username;
    std::string displayName;
    std::string plan;
    long acBalance = 0;
    std::string accessToken;
    std::string refreshToken;
};

void saveLinkSession(const LinkSession& s);
LinkSession loadLinkSession();
void clearLinkSession();

} // namespace pkgtest

#endif
