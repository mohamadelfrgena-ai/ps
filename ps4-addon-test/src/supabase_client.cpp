#include "supabase_client.hpp"

#include "content_config.hpp"
#include "http_download.hpp"

#include <cstdio>
#include <cstdlib>
#include <fstream>
#include <sstream>
#include <sys/stat.h>

namespace pkgtest {
namespace {

constexpr const char* kSupabaseCfgPath = "/data/AddonStation/supabase.cfg";
constexpr const char* kSessionPath = "/data/AddonStation/session.cfg";

std::string trim(const std::string& s) {
    std::size_t b = 0;
    while (b < s.size() && (s[b] == ' ' || s[b] == '\t' || s[b] == '\r' || s[b] == '\n')) ++b;
    std::size_t e = s.size();
    while (e > b && (s[e - 1] == ' ' || s[e - 1] == '\t' || s[e - 1] == '\r' || s[e - 1] == '\n')) --e;
    return s.substr(b, e - b);
}

void ensureParentDir(const std::string& path) {
    const std::size_t slash = path.find_last_of('/');
    if (slash == std::string::npos) return;
    const std::string dir = path.substr(0, slash);
    std::string acc;
    std::size_t pos = 1;
    while (pos <= dir.size()) {
        const std::size_t next = dir.find('/', pos);
        const std::size_t end = (next == std::string::npos) ? dir.size() : next;
        acc = dir.substr(0, end);
        if (!acc.empty()) ::mkdir(acc.c_str(), 0777);
        if (next == std::string::npos) break;
        pos = next + 1;
    }
}

std::string jsonEscape(const std::string& s) {
    std::string out;
    out.reserve(s.size() + 8);
    for (const char c : s) {
        if (c == '"' || c == '\\') { out += '\\'; out += c; }
        else if (c == '\n') { out += "\\n"; }
        else out += c;
    }
    return out;
}

} // namespace

SupabaseConfig loadSupabaseConfig() {
    SupabaseConfig cfg;
    cfg.url = content::kSupabaseUrl;
    cfg.anonKey = content::kSupabaseAnonKey;

    // Override from the PS4 HDD: /data/AddonStation/supabase.cfg
    // (سطر 1: الرابط — سطر 2: المفتاح)
    std::ifstream in(kSupabaseCfgPath);
    if (in) {
        std::string line1, line2;
        std::getline(in, line1);
        std::getline(in, line2);
        const std::string u = trim(line1);
        const std::string k = trim(line2);
        if (!u.empty()) cfg.url = u;
        if (!k.empty()) cfg.anonKey = k;
    }

    while (!cfg.url.empty() && cfg.url.back() == '/') cfg.url.pop_back();
    return cfg;
}

// v0.42: nested object extractor (string-aware brace matching)
std::string jsonGetObject(const std::string& json, const std::string& key) {
    const std::string pat = "\"" + key + "\"";
    std::size_t k = json.find(pat);
    while (k != std::string::npos) {
        std::size_t i = k + pat.size();
        while (i < json.size() && (json[i] == ' ' || json[i] == '\t' || json[i] == ':')) ++i;
        if (i < json.size() && json[i] == '{') {
            int depth = 0;
            bool inStr = false, esc = false;
            for (std::size_t j = i; j < json.size(); ++j) {
                const char c = json[j];
                if (esc) { esc = false; continue; }
                if (c == '\\') { esc = true; continue; }
                if (c == '"') { inStr = !inStr; continue; }
                if (inStr) continue;
                if (c == '{') ++depth;
                else if (c == '}') {
                    if (--depth == 0) return json.substr(i, j - i + 1);
                }
            }
            return json.substr(i);
        }
        k = json.find(pat, k + 1);
    }
    return std::string();
}

std::string jsonGetString(const std::string& json, const std::string& key) {
    const std::string pat = "\"" + key + "\"";
    std::size_t p = 0;
    while ((p = json.find(pat, p)) != std::string::npos) {
        std::size_t q = p + pat.size();
        while (q < json.size() && (json[q] == ' ' || json[q] == '\t' || json[q] == '\n' || json[q] == '\r')) ++q;
        if (q >= json.size() || json[q] != ':') { p += pat.size(); continue; }
        ++q;
        while (q < json.size() && (json[q] == ' ' || json[q] == '\t' || json[q] == '\n' || json[q] == '\r')) ++q;
        if (q >= json.size()) return "";
        if (json[q] == 'n') return "";   // null
        if (json[q] != '"') {
            // unquoted (number/bool) — read token
            std::size_t e = q;
            while (e < json.size() && json[e] != ',' && json[e] != '}' && json[e] != ' ') ++e;
            return json.substr(q, e - q);
        }
        ++q;
        std::string out;
        while (q < json.size() && json[q] != '"') {
            if (json[q] == '\\' && q + 1 < json.size()) {
                const char n = json[q + 1];
                if (n == 'n') out += '\n';
                else if (n == 't') out += '\t';
                else out += n;
                q += 2;
            } else {
                out += json[q++];
            }
        }
        return out;
    }
    return "";
}

long jsonGetInt(const std::string& json, const std::string& key, long def) {
    const std::string v = trim(jsonGetString(json, key));
    if (v.empty()) return def;
    return std::strtol(v.c_str(), nullptr, 10);
}

bool jsonGetBool(const std::string& json, const std::string& key, bool def) {
    const std::string v = trim(jsonGetString(json, key));
    if (v == "true") return true;
    if (v == "false") return false;
    return def;
}

std::string jsonGetRaw(const std::string& json, const std::string& key) {
    const std::string pat = "\"" + key + "\"";
    std::size_t p = 0;
    while ((p = json.find(pat, p)) != std::string::npos) {
        std::size_t q = p + pat.size();
        while (q < json.size() && (json[q] == ' ' || json[q] == '\t' || json[q] == '\n' || json[q] == '\r')) ++q;
        if (q >= json.size() || json[q] != ':') { p += pat.size(); continue; }
        ++q;
        while (q < json.size() && (json[q] == ' ' || json[q] == '\t' || json[q] == '\n' || json[q] == '\r')) ++q;
        if (q >= json.size()) return "";
        const char open = json[q];
        if (open != '{' && open != '[') {
            // قيمة بسيطة — أعِد نفس منطق jsonGetString
            return jsonGetString(json, key);
        }
        const char close = (open == '{') ? '}' : ']';
        int depth = 0;
        bool inStr = false;
        std::size_t i = q;
        for (; i < json.size(); ++i) {
            const char c = json[i];
            if (inStr) {
                if (c == '\\') ++i;
                else if (c == '"') inStr = false;
                continue;
            }
            if (c == '"') { inStr = true; continue; }
            if (c == open) ++depth;
            else if (c == close) {
                --depth;
                if (depth == 0) return json.substr(q, i - q + 1);
            }
        }
        return "";
    }
    return "";
}

std::vector<std::string> splitJsonObjects(const std::string& jsonArray) {
    std::vector<std::string> out;
    std::size_t i = 0;
    while (i < jsonArray.size() && jsonArray[i] != '[') ++i;
    if (i >= jsonArray.size()) return out;
    ++i;
    while (i < jsonArray.size()) {
        while (i < jsonArray.size() && jsonArray[i] != '{' && jsonArray[i] != ']') ++i;
        if (i >= jsonArray.size() || jsonArray[i] == ']') break;
        int depth = 0;
        bool inStr = false;
        std::size_t start = i;
        for (; i < jsonArray.size(); ++i) {
            const char c = jsonArray[i];
            if (inStr) {
                if (c == '\\') ++i;
                else if (c == '"') inStr = false;
                continue;
            }
            if (c == '"') { inStr = true; continue; }
            if (c == '{') ++depth;
            else if (c == '}') {
                --depth;
                if (depth == 0) {
                    out.push_back(jsonArray.substr(start, i - start + 1));
                    ++i;
                    break;
                }
            }
        }
        if (depth != 0) break;  // JSON غير مكتمل
    }
    return out;
}

std::string i18nPick(const std::string& i18nJson, const std::string& langCode) {
    std::string v = jsonGetString(i18nJson, langCode);
    if (v.empty()) v = jsonGetString(i18nJson, "en");
    return v;
}

RpcResult supabaseRpc(const SupabaseConfig& cfg,
                      const std::string& fn,
                      const std::string& jsonBody,
                      const std::string& userToken) {
    RpcResult result;
    if (!cfg.ok()) {
        result.message = "not_configured";
        return result;
    }

    const std::string url = cfg.url + "/rest/v1/rpc/" + fn;
    const std::string bearer = userToken.empty() ? cfg.anonKey : userToken;
    const std::vector<std::pair<std::string, std::string>> headers = {
        {"apikey", cfg.anonKey},
        {"Authorization", "Bearer " + bearer},
        {"Content-Type", "application/json"},
        {"Accept", "application/json"},
    };

    const HttpPostResult http = httpPostJson(url, headers, jsonBody);
    result.statusCode = http.statusCode;
    result.body = http.body;
    if (!http.ok) {
        result.message = http.message;
        return result;
    }

    result.ok = true;
    result.rpcOk = jsonGetBool(http.body, "ok", false);
    result.message = jsonGetString(http.body, "message");
    return result;
}

RpcResult callCreateLinkCode(const SupabaseConfig& cfg,
                             const std::string& platform,
                             const std::string& deviceName) {
    const std::string body =
        "{\"p_platform\":\"" + jsonEscape(platform) + "\","
        "\"p_device_name\":\"" + jsonEscape(deviceName) + "\"}";
    return supabaseRpc(cfg, "create_console_link_code", body);
}

RpcResult callPollLinkCode(const SupabaseConfig& cfg,
                           const std::string& code,
                           const std::string& pollToken) {
    const std::string body =
        "{\"p_code\":\"" + jsonEscape(code) + "\","
        "\"p_poll_token\":\"" + jsonEscape(pollToken) + "\"}";
    return supabaseRpc(cfg, "poll_console_link_code", body);
}

AuthLogin callRefreshSession(const SupabaseConfig& cfg, const std::string& refreshToken) {
    AuthLogin out;
    if (!cfg.ok() || refreshToken.empty()) {
        out.message = "not_configured";
        return out;
    }
    const std::string url = cfg.url + "/auth/v1/token?grant_type=refresh_token";
    const std::vector<std::pair<std::string, std::string>> headers = {
        {"apikey", cfg.anonKey},
        {"Content-Type", "application/json"},
        {"Accept", "application/json"},
    };
    const std::string body = "{\"refresh_token\":\"" + jsonEscape(refreshToken) + "\"}";
    const HttpPostResult http = httpPostJson(url, headers, body);
    if (!http.ok) {
        out.message = http.message;
        return out;
    }
    out.accessToken = jsonGetString(http.body, "access_token");
    out.refreshToken = jsonGetString(http.body, "refresh_token");
    if (out.accessToken.empty()) {
        out.message = jsonGetString(http.body, "error_description");
        if (out.message.empty()) out.message = jsonGetString(http.body, "msg");
        if (out.message.empty()) out.message = "refresh_failed";
        return out;
    }
    out.ok = true;
    out.userId = jsonGetString(http.body, "id");
    return out;
}

AuthLogin callEmailLogin(const SupabaseConfig& cfg,
                         const std::string& email,
                         const std::string& password) {
    AuthLogin out;
    if (!cfg.ok()) {
        out.message = "not_configured";
        return out;
    }
    const std::string url = cfg.url + "/auth/v1/token?grant_type=password";
    const std::vector<std::pair<std::string, std::string>> headers = {
        {"apikey", cfg.anonKey},
        {"Content-Type", "application/json"},
        {"Accept", "application/json"},
    };
    const std::string body =
        "{\"email\":\"" + jsonEscape(email) + "\","
        "\"password\":\"" + jsonEscape(password) + "\"}";
    const HttpPostResult http = httpPostJson(url, headers, body);
    if (!http.ok) {
        out.message = http.message;
        return out;
    }
    out.accessToken = jsonGetString(http.body, "access_token");
    out.refreshToken = jsonGetString(http.body, "refresh_token");
    if (out.accessToken.empty()) {
        out.message = jsonGetString(http.body, "error_description");
        if (out.message.empty()) out.message = jsonGetString(http.body, "msg");
        if (out.message.empty()) out.message = "login_failed";
        return out;
    }
    out.ok = true;
    out.userId = jsonGetString(http.body, "id");
    out.email = jsonGetString(http.body, "email");
    const std::string meta = jsonGetRaw(http.body, "user_metadata");
    out.displayName = jsonGetString(meta, "display_name");
    if (out.displayName.empty()) out.displayName = jsonGetString(meta, "name");
    if (out.displayName.empty()) out.displayName = jsonGetString(meta, "username");
    if (out.displayName.empty()) out.displayName = out.email;
    return out;
}

std::vector<BannerData> fetchStoreBanners(const SupabaseConfig& cfg) {
    std::vector<BannerData> out;
    if (!cfg.ok()) return out;
    const std::string url =
        cfg.url + "/rest/v1/store_banners?enabled=eq.true&order=sort_order.asc&select=*";
    const std::vector<std::pair<std::string, std::string>> headers = {
        {"apikey", cfg.anonKey},
        {"Authorization", "Bearer " + cfg.anonKey},
        {"Accept", "application/json"},
    };
    const HttpPostResult http = httpGetJson(url, headers);
    if (!http.ok || http.statusCode != 200) return out;
    for (const std::string& obj : splitJsonObjects(http.body)) {
        BannerData b;
        b.slug = jsonGetString(obj, "slug");
        b.theme = jsonGetString(obj, "theme");
        b.kickerI18n = jsonGetRaw(obj, "kicker_i18n");
        b.titleI18n = jsonGetRaw(obj, "title_i18n");
        b.subtitleI18n = jsonGetRaw(obj, "subtitle_i18n");
        b.primaryLabelI18n = jsonGetRaw(obj, "primary_label_i18n");
        b.secondaryLabelI18n = jsonGetRaw(obj, "secondary_label_i18n");
        b.badgeText = jsonGetString(obj, "badge_text");
        b.discountPercent = jsonGetInt(obj, "discount_percent", 0);
        b.targetType = jsonGetString(obj, "target_type");
        b.coverUrl = jsonGetString(obj, "cover_url");
        b.endsAt = jsonGetString(obj, "ends_at");
        out.push_back(b);
    }
    return out;
}

std::vector<ItemData> fetchContentItems(const SupabaseConfig& cfg, int limit) {
    std::vector<ItemData> out;
    if (!cfg.ok()) return out;
    const std::string url =
        cfg.url + "/rest/v1/content_items?is_published=eq.true"
        "&order=created_at.desc&limit=" + std::to_string(limit) + "&select=*";
    const std::vector<std::pair<std::string, std::string>> headers = {
        {"apikey", cfg.anonKey},
        {"Authorization", "Bearer " + cfg.anonKey},
        {"Accept", "application/json"},
    };
    const HttpPostResult http = httpGetJson(url, headers);
    if (!http.ok || http.statusCode != 200) return out;
    for (const std::string& obj : splitJsonObjects(http.body)) {
        ItemData it;
        it.id = jsonGetString(obj, "id");
        it.title = jsonGetString(obj, "title");
        it.slug = jsonGetString(obj, "slug");
        it.contentType = jsonGetString(obj, "content_type");
        it.plan = jsonGetString(obj, "plan");
        it.acPrice = jsonGetInt(obj, "ac_price", 0);
        const std::string sale = jsonGetString(obj, "sale_ac_price");
        it.saleAcPrice = (sale.empty() || sale == "null") ? -1 : std::strtol(sale.c_str(), nullptr, 10);
        it.isFeatured = jsonGetBool(obj, "is_featured", false);
        it.isExclusive = jsonGetBool(obj, "is_exclusive", false);
        it.description = jsonGetString(obj, "description");
        it.category = jsonGetString(obj, "category");
        it.coverUrl = jsonGetString(obj, "cover_url");
        it.version = jsonGetString(obj, "version");
        it.fileSize = (long long)jsonGetInt(obj, "file_size", 0);
        it.downloads = jsonGetInt(obj, "downloads_count", 0);
        it.views = jsonGetInt(obj, "views_count", 0);
        it.rating = (double)jsonGetInt(obj, "rating", 0);
        {
            const std::string r = jsonGetString(obj, "rating");
            if (!r.empty() && r != "null") it.rating = std::strtod(r.c_str(), nullptr);
        }
        it.ratingCount = jsonGetInt(obj, "rating_count", 0);
        it.exclusiveSlot = jsonGetInt(obj, "exclusive_slot", 0);
        it.createdAt = jsonGetString(obj, "created_at");
        it.updatedAt = jsonGetString(obj, "updated_at");
        if (it.updatedAt.empty()) it.updatedAt = it.createdAt;
        it.isPublished = jsonGetBool(obj, "is_published", true);
        it.ps4RawUrl = jsonGetString(obj, "ps4_raw_url");
        it.packTargetsRaw = jsonGetRaw(obj, "pack_targets");
        out.push_back(it);
    }
    return out;
}

DownloadGrant localGrantFrom(const std::string& title,
                             const std::string& contentType,
                             const std::string& ps4RawUrl,
                             const std::string& packTargetsRaw) {
    DownloadGrant g;
    g.ok = !ps4RawUrl.empty();
    g.title = title;
    g.contentType = contentType;
    g.ps4RawUrl = ps4RawUrl;
    for (const std::string& p : splitJsonObjects(packTargetsRaw)) {
        PackTarget t;
        t.target = jsonGetString(p, "target");
        t.destFolder = jsonGetString(p, "dest_folder");
        t.rawFile = jsonGetString(p, "raw_file");
        t.ps4RawUrl = jsonGetString(p, "ps4_raw_url");
        t.sha256 = jsonGetString(p, "sha256");
        g.packs.push_back(t);
    }
    return g;
}

DownloadGrant callRecordContentDownload(const SupabaseConfig& cfg,
                                        const std::string& itemId,
                                        const std::string& userAccessToken) {
    DownloadGrant out;
    if (!cfg.ok()) {
        out.message = "not_configured";
        return out;
    }
    const std::string body = "{\"p_item_id\":\"" + jsonEscape(itemId) + "\"}";
    const RpcResult r = supabaseRpc(cfg, "record_content_download", body, userAccessToken);
    out.message = r.message;
    if (!r.ok || !r.rpcOk) {
        if (out.message.empty()) out.message = "download_denied";
        return out;
    }
    out.ok = true;
    out.title = jsonGetString(r.body, "title");
    out.contentType = jsonGetString(r.body, "content_type");
    out.ps4RawUrl = jsonGetString(r.body, "ps4_raw_url");
    for (const std::string& p : splitJsonObjects(jsonGetRaw(r.body, "pack_targets"))) {
        PackTarget t;
        t.target = jsonGetString(p, "target");
        t.destFolder = jsonGetString(p, "dest_folder");
        t.rawFile = jsonGetString(p, "raw_file");
        t.ps4RawUrl = jsonGetString(p, "ps4_raw_url");
        t.sha256 = jsonGetString(p, "sha256");
        out.packs.push_back(t);
    }
    return out;
}

// v0.61 («التطبيق يسجل خروج بنفسه»): ATOMIC save — a crash mid-write used to truncate
// the session file and silently sign the user out on the next launch. Write .tmp first,
// rotate the old file to .bak, then rename into place.
void saveLinkSession(const LinkSession& s) {
    ensureParentDir(kSessionPath);
    const std::string tmp = std::string(kSessionPath) + ".tmp";
    std::ofstream out(tmp, std::ios::trunc);
    if (!out) return;
    out << "valid=" << (s.valid ? "1" : "0") << "\n"
        << "user_id=" << s.userId << "\n"
        << "username=" << s.username << "\n"
        << "display_name=" << s.displayName << "\n"
        << "avatar_url=" << s.avatarUrl << "\n"
        << "plan=" << s.plan << "\n"
        << "ac_balance=" << s.acBalance << "\n"
        << "access_token=" << s.accessToken << "\n"
        << "refresh_token=" << s.refreshToken << "\n";
    out.close();
    std::remove((std::string(kSessionPath) + ".bak").c_str());
    std::rename(kSessionPath, (std::string(kSessionPath) + ".bak").c_str());
    std::rename(tmp.c_str(), kSessionPath);
}

static LinkSession loadLinkSessionFrom(const char* path) {
    LinkSession s;
    std::ifstream in(path);
    if (!in) return s;
    std::string line;
    while (std::getline(in, line)) {
        const std::size_t eq = line.find('=');
        if (eq == std::string::npos) continue;
        const std::string k = line.substr(0, eq);
        const std::string v = line.substr(eq + 1);
        if (k == "valid") s.valid = (v == "1");
        else if (k == "user_id") s.userId = v;
        else if (k == "username") s.username = v;
        else if (k == "display_name") s.displayName = v;
        else if (k == "avatar_url") s.avatarUrl = v;
        else if (k == "plan") s.plan = v;
        else if (k == "ac_balance") s.acBalance = std::strtol(v.c_str(), nullptr, 10);
        else if (k == "access_token") s.accessToken = v;
        else if (k == "refresh_token") s.refreshToken = v;
    }
    return s;
}

LinkSession loadLinkSession() {
    LinkSession s = loadLinkSessionFrom(kSessionPath);
    if (!s.valid) {   // v0.61: heal from the .bak rotation if the live file was damaged
        LinkSession b = loadLinkSessionFrom((std::string(kSessionPath) + ".bak").c_str());
        if (b.valid) { s = b; saveLinkSession(b); }
    }
    return s;
}

void clearLinkSession() {
    std::remove(kSessionPath);
}

// ============================================================================
//  v7.3 — console-code sign-in RPCs (console_code_*), account / wallet / plans
// ============================================================================
RpcResult callConsoleCodeCreate(const SupabaseConfig& cfg,
                                const std::string& deviceName,
                                const std::string& appVersion,
                                const std::string& fingerprint) {
    const std::string body =
        "{\"p_device_name\":\"" + jsonEscape(deviceName) + "\",\"p_app_version\":\"" +
        jsonEscape(appVersion) + "\",\"p_platform\":\"ps4\",\"p_fingerprint\":\"" +
        jsonEscape(fingerprint) + "\"}";
    return supabaseRpc(cfg, "console_code_create", body);
}

RpcResult callConsoleCodeConsume(const SupabaseConfig& cfg,
                                 const std::string& code,
                                 const std::string& pollSecret) {
    const std::string body =
        "{\"p_code\":\"" + jsonEscape(code) + "\",\"p_poll_secret\":\"" +
        jsonEscape(pollSecret) + "\"}";
    return supabaseRpc(cfg, "console_code_consume", body);
}

RpcResult callConsoleCodeComplete(const SupabaseConfig& cfg,
                                  const std::string& exchangeId,
                                  const std::string& linkedConsoleId,
                                  const std::string& sessionRef) {
    const std::string body =
        "{\"p_exchange_id\":\"" + jsonEscape(exchangeId) + "\",\"p_linked_console_id\":\"" +
        jsonEscape(linkedConsoleId) + "\",\"p_session_ref\":\"" + jsonEscape(sessionRef) + "\"}";
    return supabaseRpc(cfg, "console_code_complete", body);
}

RpcResult callConsoleSessionTouch(const SupabaseConfig& cfg, const std::string& sessionToken) {
    return supabaseRpc(cfg, "console_session_touch",
                       "{\"p_session_ref\":\"" + jsonEscape(sessionToken) + "\"}", sessionToken);
}

RpcResult callConsoleUnlink(const SupabaseConfig& cfg, const std::string& sessionToken) {
    return supabaseRpc(cfg, "console_unlink",
                       "{\"p_session_ref\":\"" + jsonEscape(sessionToken) + "\"}", sessionToken);
}

RpcResult callAcUnlock(const SupabaseConfig& cfg, const std::string& itemId, const std::string& userToken) {
    return supabaseRpc(cfg, "ac_unlock_content",
                       "{\"p_item_id\":\"" + jsonEscape(itemId) + "\"}", userToken);
}

ProfileRow fetchProfile(const SupabaseConfig& cfg, const std::string& userId, const std::string& userToken) {
    ProfileRow p;
    if (!cfg.ok() || userId.empty()) return p;
    const std::string tok = userToken.empty() ? cfg.anonKey : userToken;
    const std::vector<std::pair<std::string, std::string>> headers = {
        {"apikey", cfg.anonKey},
        {"Authorization", "Bearer " + tok},
        {"Accept", "application/json"},
    };
    {
        const HttpPostResult h = httpGetJson(
            cfg.url + "/rest/v1/profiles?id=eq." + userId + "&select=display_name,username,avatar_url,plan,plan_expires_at&limit=1", headers);
        if (h.ok && h.statusCode == 200) {
            const std::vector<std::string> objs = splitJsonObjects(h.body);
            if (!objs.empty()) {
                p.ok = true;
                p.displayName = jsonGetString(objs[0], "display_name");
                p.username = jsonGetString(objs[0], "username");
                p.avatarUrl = jsonGetString(objs[0], "avatar_url");
                p.plan = jsonGetString(objs[0], "plan");
                p.planExpiresAt = jsonGetString(objs[0], "plan_expires_at");
            }
        }
    }
    {
        const HttpPostResult h = httpGetJson(
            cfg.url + "/rest/v1/ac_wallets?user_id=eq." + userId + "&select=balance&limit=1", headers);
        if (h.ok && h.statusCode == 200) {
            const std::vector<std::string> objs = splitJsonObjects(h.body);
            if (!objs.empty()) p.acBalance = jsonGetInt(objs[0], "balance", 0);
        }
    }
    {
        const HttpPostResult h = httpGetJson(
            cfg.url + "/rest/v1/content_downloads?user_id=eq." + userId + "&select=id", headers);
        if (h.ok && h.statusCode == 200) p.downloads = (long)splitJsonObjects(h.body).size();
    }
    return p;
}

std::vector<WalletRow> fetchWalletTx(const SupabaseConfig& cfg, const std::string& userId, const std::string& userToken, int limit) {
    std::vector<WalletRow> out;
    if (!cfg.ok() || userId.empty()) return out;
    const std::string tok = userToken.empty() ? cfg.anonKey : userToken;
    const std::vector<std::pair<std::string, std::string>> headers = {
        {"apikey", cfg.anonKey},
        {"Authorization", "Bearer " + tok},
        {"Accept", "application/json"},
    };
    const HttpPostResult h = httpGetJson(
        cfg.url + "/rest/v1/ac_transactions?user_id=eq." + userId +
        "&order=created_at.desc&limit=" + std::to_string(limit), headers);
    if (!h.ok || h.statusCode != 200) return out;
    for (const std::string& obj : splitJsonObjects(h.body)) {
        WalletRow w;
        w.amount = jsonGetInt(obj, "amount", 0);
        w.kind = jsonGetString(obj, "kind");
        w.label = jsonGetString(obj, "label");
        if (w.label.empty()) w.label = jsonGetString(obj, "reason");
        w.at = jsonGetString(obj, "created_at");
        w.balanceAfter = jsonGetInt(obj, "balance_after", 0);
        out.push_back(w);
    }
    return out;
}

std::vector<PlanRow> fetchSubscriptionPlans(const SupabaseConfig& cfg) {
    std::vector<PlanRow> out;
    if (!cfg.ok()) return out;
    const std::vector<std::pair<std::string, std::string>> headers = {
        {"apikey", cfg.anonKey},
        {"Authorization", "Bearer " + cfg.anonKey},
        {"Accept", "application/json"},
    };
    const HttpPostResult h = httpGetJson(
        cfg.url + "/rest/v1/subscription_plans?order=price_usd_cents.asc&select=*", headers);
    if (!h.ok || h.statusCode != 200) return out;
    for (const std::string& obj : splitJsonObjects(h.body)) {
        PlanRow p;
        p.plan = jsonGetString(obj, "plan");
        if (p.plan.empty()) p.plan = jsonGetString(obj, "slug");
        p.priceLabel = jsonGetString(obj, "price_label");
        if (p.priceLabel.empty()) {
            const std::string cents = jsonGetString(obj, "price_usd_cents");
            if (!cents.empty() && cents != "null") {
                char buf[32];
                std::snprintf(buf, sizeof(buf), "$%.2f", std::strtod(cents.c_str(), nullptr) / 100.0);
                p.priceLabel = buf;
            }
        }
        p.downloadLimit = jsonGetInt(obj, "download_limit", 0);
        p.limitPeriod = jsonGetString(obj, "limit_period");
        out.push_back(p);
    }
    return out;
}

// ============================================================================
//  v7.3 — live-publish sync (a phone publish appears on PS4/PS5 at once)
// ============================================================================
std::vector<ItemTouch> fetchContentItemsTouch(const SupabaseConfig& cfg) {
    std::vector<ItemTouch> out;
    if (!cfg.ok()) return out;
    const std::vector<std::pair<std::string, std::string>> headers = {
        {"apikey", cfg.anonKey},
        {"Authorization", "Bearer " + cfg.anonKey},
        {"Accept", "application/json"},
    };
    auto parse = [&](const std::string& body, bool withUpdated) {
        for (const std::string& obj : splitJsonObjects(body)) {
            ItemTouch t;
            t.id = jsonGetString(obj, "id");
            if (t.id.empty()) continue;
            t.touchAt = withUpdated ? jsonGetString(obj, "updated_at") : std::string();
            if (t.touchAt.empty()) t.touchAt = jsonGetString(obj, "created_at");
            t.published = jsonGetBool(obj, "is_published", true);
            out.push_back(t);
        }
    };
    HttpPostResult h = httpGetJson(
        cfg.url + "/rest/v1/content_items?select=id,updated_at,created_at,is_published"
                 "&order=updated_at.desc.nullslast&limit=200", headers);
    if (h.ok && h.statusCode == 200) {
        parse(h.body, true);
    } else {
        // fallback: table without updated_at
        out.clear();
        h = httpGetJson(
            cfg.url + "/rest/v1/content_items?select=id,created_at,is_published"
                     "&order=created_at.desc&limit=200", headers);
        if (h.ok && h.statusCode == 200) parse(h.body, false);
    }
    return out;
}

std::vector<ItemData> fetchContentItemsByIds(const SupabaseConfig& cfg, const std::vector<std::string>& ids) {
    std::vector<ItemData> out;
    if (!cfg.ok() || ids.empty()) return out;
    std::string list;
    for (std::size_t i = 0; i < ids.size(); ++i) {
        if (i) list += ",";
        list += ids[i];
    }
    const std::vector<std::pair<std::string, std::string>> headers = {
        {"apikey", cfg.anonKey},
        {"Authorization", "Bearer " + cfg.anonKey},
        {"Accept", "application/json"},
    };
    const HttpPostResult h = httpGetJson(
        cfg.url + "/rest/v1/content_items?id=in.(" + list + ")&select=*", headers);
    if (!h.ok || h.statusCode != 200) return out;
    for (const std::string& obj : splitJsonObjects(h.body)) {
        ItemData it;
        it.id = jsonGetString(obj, "id");
        it.title = jsonGetString(obj, "title");
        it.slug = jsonGetString(obj, "slug");
        it.contentType = jsonGetString(obj, "content_type");
        it.plan = jsonGetString(obj, "plan");
        it.acPrice = jsonGetInt(obj, "ac_price", 0);
        const std::string sale = jsonGetString(obj, "sale_ac_price");
        it.saleAcPrice = (sale.empty() || sale == "null") ? -1 : std::strtol(sale.c_str(), nullptr, 10);
        it.isFeatured = jsonGetBool(obj, "is_featured", false);
        it.isExclusive = jsonGetBool(obj, "is_exclusive", false);
        it.description = jsonGetString(obj, "description");
        it.category = jsonGetString(obj, "category");
        it.coverUrl = jsonGetString(obj, "cover_url");
        it.version = jsonGetString(obj, "version");
        it.fileSize = (long long)jsonGetInt(obj, "file_size", 0);
        it.downloads = jsonGetInt(obj, "downloads_count", 0);
        it.views = jsonGetInt(obj, "views_count", 0);
        {
            const std::string r = jsonGetString(obj, "rating");
            if (!r.empty() && r != "null") it.rating = std::strtod(r.c_str(), nullptr);
        }
        it.ratingCount = jsonGetInt(obj, "rating_count", 0);
        it.exclusiveSlot = jsonGetInt(obj, "exclusive_slot", 0);
        it.createdAt = jsonGetString(obj, "created_at");
        it.updatedAt = jsonGetString(obj, "updated_at");
        if (it.updatedAt.empty()) it.updatedAt = it.createdAt;
        it.isPublished = jsonGetBool(obj, "is_published", true);
        it.ps4RawUrl = jsonGetString(obj, "ps4_raw_url");
        it.packTargetsRaw = jsonGetRaw(obj, "pack_targets");
        out.push_back(it);
    }
    return out;
}

} // namespace pkgtest
