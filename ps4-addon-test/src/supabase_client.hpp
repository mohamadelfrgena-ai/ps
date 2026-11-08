#ifndef PKG_ADDON_TEST_SUPABASE_CLIENT_HPP
#define PKG_ADDON_TEST_SUPABASE_CLIENT_HPP

#include <string>
#include <vector>

namespace pkgtest {

// ----------------------------------------------------------------------------
//  عميل Supabase المبسّط — نظام الدخول برمز (ربط جهاز السوني بالحساب)
//  عقد الإنتاج المشترك الوحيد:
//    create_console_link_code(p_platform, p_device_name)  ← من السوني
//    poll_console_link(p_code, p_poll_token)              ← من السوني كل 3 ثوانٍ
//    claim_console_link(p_code, ...)                      ← من الهاتف فقط
//  لا يستدعي عميل PS4 أي console_code_* RPC.
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
// v0.31: تجديد جلسة الهاتف (تنتهي بعد ساعة) عبر refresh_token — يبقي الربط حيّاً
AuthLogin callRefreshSession(const SupabaseConfig& cfg, const std::string& refreshToken);

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
    long views = 0;
    double rating = 0;
    long ratingCount = 0;
    long exclusiveSlot = 0;
    std::string createdAt;    // ISO timestamp (UTC)
    std::string updatedAt;    // ISO timestamp (UTC) — live-publish sync
    bool isPublished = true;
    std::string ps4RawUrl;
    std::string packTargetsRaw; // raw jsonb (pack_targets)
};

// v7.3 live-publish sync: light touch list (id + timestamp + published flag)
struct ItemTouch {
    std::string id;
    std::string touchAt;   // updated_at (or created_at fallback)
    bool published = true;
};
std::vector<ItemTouch> fetchContentItemsTouch(const SupabaseConfig& cfg);
std::vector<ItemData> fetchContentItemsByIds(const SupabaseConfig& cfg, const std::vector<std::string>& ids);

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

// v0.36 2121: anonymous download grant built from catalog fields (no account)
DownloadGrant localGrantFrom(const std::string& title,
                             const std::string& contentType,
                             const std::string& ps4RawUrl,
                             const std::string& packTargetsRaw);

// ترجمة كائن i18n {"en":..,"ar":..} للغة المطلوبة (مع fallback للإنجليزية)
std::string i18nPick(const std::string& i18nJson, const std::string& langCode);

// ac_unlock_content(p_item_id) — user-authenticated
RpcResult callAcUnlock(const SupabaseConfig& cfg, const std::string& itemId, const std::string& userToken);

// ---- v7.3: account / wallet / plans --------------------------------------------------
struct ProfileRow {
    bool ok = false;          // visible_profiles returned this user
    bool walletOk = false;    // ac_wallets returned a real balance row
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
    bool profileKnown = false;  // poll snapshot or visible_profiles supplied a real identity
    bool balanceKnown = false;  // poll payload or ac_wallets supplied the balance (zero is valid)
    std::string userId;
    std::string username;
    std::string displayName;
    std::string avatarUrl;
    std::string plan;
    std::string planExpiresAt;
    long acBalance = 0;
    std::string accessToken;
    std::string refreshToken;
};

// Parses both supported poll payloads:
//  * legacy flat fields: user_id/display_name/... at the response root
//  * current shared contract: credentials at the root and identity in profile {...}
// `valid` is true only when user id + both session tokens are present.
LinkSession parseLinkSessionResponse(const std::string& body);
bool linkSessionHasCredentials(const LinkSession& s);

// Turn a stored site-assets path into its public Storage URL. Absolute URLs pass through.
std::string publicStorageUrl(const SupabaseConfig& cfg,
                             const std::string& bucket,
                             const std::string& path);

// Atomic persistence. An optional path is exposed for host tests; an empty path uses
// /data/AddonStation/session.cfg on the console.
bool saveLinkSession(const LinkSession& s, const std::string& path = "");
LinkSession loadLinkSession(const std::string& path = "");
void clearLinkSession(const std::string& path = "");

} // namespace pkgtest

#endif
