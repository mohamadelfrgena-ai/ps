#ifndef PKG_ADDON_TEST_SUPABASE_CLIENT_HPP
#define PKG_ADDON_TEST_SUPABASE_CLIENT_HPP

#include <string>

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

struct RpcResult {
    bool ok = false;        // نجاح النقل + الاستجابة قابلة للقراءة
    bool rpcOk = false;     // قيمة ok من الدالة نفسها
    int statusCode = 0;
    std::string body;       // الاستجابة الكاملة
    std::string message;    // message من الدالة أو رسالة خطأ النقل
};

// POST /rest/v1/rpc/<fn>  بجسم JSON
RpcResult supabaseRpc(const SupabaseConfig& cfg,
                      const std::string& fn,
                      const std::string& jsonBody);

// استدعاءات نظام الكود
RpcResult callCreateLinkCode(const SupabaseConfig& cfg,
                             const std::string& platform = "ps4",
                             const std::string& deviceName = "PlayStation 4");
RpcResult callPollLinkCode(const SupabaseConfig& cfg,
                           const std::string& code,
                           const std::string& pollToken);

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
