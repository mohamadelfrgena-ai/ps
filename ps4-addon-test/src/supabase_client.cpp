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

std::string sessionPath(const std::string& overridePath) {
    return overridePath.empty() ? std::string(kSessionPath) : overridePath;
}

bool hasJsonKey(const std::string& json, const std::string& key) {
    return json.find("\"" + key + "\"") != std::string::npos;
}

std::string encodeUrlPath(const std::string& value) {
    static constexpr char kHex[] = "0123456789ABCDEF";
    std::string out;
    out.reserve(value.size());
    for (const unsigned char c : value) {
        const bool safe = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') ||
                          (c >= '0' && c <= '9') || c == '/' || c == '-' ||
                          c == '_' || c == '.' || c == '~';
        if (safe) {
            out.push_back(static_cast<char>(c));
        } else {
            out.push_back('%');
            out.push_back(kHex[c >> 4]);
            out.push_back(kHex[c & 0x0F]);
        }
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
    if (http.statusCode < 200 || http.statusCode >= 300) {
        result.message = jsonGetString(http.body, "message");
        if (result.message.empty()) result.message = jsonGetString(http.body, "hint");
        if (result.message.empty()) result.message = "http_" + std::to_string(http.statusCode);
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
    return supabaseRpc(cfg, "poll_console_link", body);
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

bool linkSessionHasCredentials(const LinkSession& s) {
    return !s.userId.empty() && !s.accessToken.empty() && !s.refreshToken.empty();
}

LinkSession parseLinkSessionResponse(const std::string& body) {
    LinkSession s;
    const std::string profile = jsonGetRaw(body, "profile");
    auto field = [&](const char* key) {
        std::string value = jsonGetString(body, key);
        if (value.empty() && !profile.empty()) value = jsonGetString(profile, key);
        return value;
    };

    s.userId = field("user_id");
    if (s.userId.empty()) s.userId = field("claimed_by");
    if (s.userId.empty() && !profile.empty()) s.userId = jsonGetString(profile, "id");
    s.username = field("username");
    s.displayName = field("display_name");
    if (s.displayName.empty()) s.displayName = s.username;
    s.avatarUrl = field("avatar_url");
    s.plan = field("plan");
    s.planExpiresAt = field("plan_expires_at");
    s.accessToken = jsonGetString(body, "access_token");
    s.refreshToken = jsonGetString(body, "refresh_token");

    if (hasJsonKey(body, "ac_balance")) {
        s.acBalance = jsonGetInt(body, "ac_balance", 0);
        s.balanceKnown = true;
    } else if (!profile.empty() && hasJsonKey(profile, "ac_balance")) {
        s.acBalance = jsonGetInt(profile, "ac_balance", 0);
        s.balanceKnown = true;
    }
    s.profileKnown = !s.displayName.empty() || !s.username.empty();
    s.valid = linkSessionHasCredentials(s);
    return s;
}

std::string publicStorageUrl(const SupabaseConfig& cfg,
                             const std::string& bucket,
                             const std::string& path) {
    std::string clean = trim(path);
    if (clean.empty()) return {};
    if (clean.rfind("https://", 0) == 0 || clean.rfind("http://", 0) == 0) return clean;
    if (!clean.empty() && clean.front() == '/') return cfg.url + clean;
    const std::string prefix = bucket + "/";
    if (clean.rfind(prefix, 0) == 0) clean.erase(0, prefix.size());
    return cfg.url + "/storage/v1/object/public/" + encodeUrlPath(bucket) + "/" + encodeUrlPath(clean);
}

bool saveLinkSession(const LinkSession& s, const std::string& pathOverride) {
    // Never persist the half-session that caused the false "linked" screen: a
    // complete identity and a confirmed balance (including a real zero) are required.
    if (!s.valid || !linkSessionHasCredentials(s) || !s.profileKnown || !s.balanceKnown)
        return false;

    const std::string path = sessionPath(pathOverride);
    const std::string temporary = path + ".tmp";
    ensureParentDir(path);
    std::ofstream out(temporary, std::ios::trunc | std::ios::binary);
    if (!out) return false;
    out << "valid=1\n"
        << "user_id=" << s.userId << "\n"
        << "username=" << s.username << "\n"
        << "display_name=" << s.displayName << "\n"
        << "avatar_url=" << s.avatarUrl << "\n"
        << "plan=" << s.plan << "\n"
        << "plan_expires_at=" << s.planExpiresAt << "\n"
        << "ac_balance=" << s.acBalance << "\n"
        << "access_token=" << s.accessToken << "\n"
        << "refresh_token=" << s.refreshToken << "\n";
    out.flush();
    const bool wrote = out.good();
    out.close();
    if (!wrote || !out.good()) {
        std::remove(temporary.c_str());
        return false;
    }
    if (std::rename(temporary.c_str(), path.c_str()) != 0) {
        std::remove(temporary.c_str());
        return false;
    }
    return true;
}

LinkSession loadLinkSession(const std::string& pathOverride) {
    LinkSession s;
    bool storedValid = false;
    const std::string path = sessionPath(pathOverride);
    std::ifstream in(path, std::ios::binary);
    if (!in) return s;
    std::string line;
    while (std::getline(in, line)) {
        const std::size_t eq = line.find('=');
        if (eq == std::string::npos) continue;
        const std::string k = line.substr(0, eq);
        const std::string v = line.substr(eq + 1);
        if (k == "valid") storedValid = (v == "1");
        else if (k == "user_id") s.userId = v;
        else if (k == "username") s.username = v;
        else if (k == "display_name") s.displayName = v;
        else if (k == "avatar_url") s.avatarUrl = v;
        else if (k == "plan") s.plan = v;
        else if (k == "plan_expires_at") s.planExpiresAt = v;
        else if (k == "ac_balance") {
            s.acBalance = std::strtol(v.c_str(), nullptr, 10);
            s.balanceKnown = true;
        } else if (k == "access_token") s.accessToken = v;
        else if (k == "refresh_token") s.refreshToken = v;
    }
    s.profileKnown = !s.displayName.empty() || !s.username.empty();
    s.valid = storedValid && linkSessionHasCredentials(s) && s.profileKnown && s.balanceKnown;
    if (!s.valid) return LinkSession{};
    return s;
}

void clearLinkSession(const std::string& pathOverride) {
    const std::string path = sessionPath(pathOverride);
    std::remove(path.c_str());
    std::remove((path + ".tmp").c_str());
}

// ============================================================================
//  Account / wallet / plans
// ============================================================================
RpcResult callAcUnlock(const SupabaseConfig& cfg, const std::string& itemId, const std::string& userToken) {
    return supabaseRpc(cfg, "ac_unlock_content",
                       "{\"p_item_id\":\"" + jsonEscape(itemId) + "\"}", userToken);
}

ProfileRow fetchProfile(const SupabaseConfig& cfg, const std::string& userId, const std::string& userToken) {
    ProfileRow p;
    if (!cfg.ok() || userId.empty()) return p;
    const std::string tok = userToken.empty() ? cfg.anonKey : userToken;
    const std::vector<std::pair<std::string, std::string>> publicHeaders = {
        {"apikey", cfg.anonKey},
        {"Authorization", "Bearer " + cfg.anonKey},
        {"Accept", "application/json"},
    };
    const std::vector<std::pair<std::string, std::string>> userHeaders = {
        {"apikey", cfg.anonKey},
        {"Authorization", "Bearer " + tok},
        {"Accept", "application/json"},
    };
    {
        // Raw profiles SELECT is intentionally revoked in production. The
        // security-barrier visible_profiles view is the supported read surface.
        const std::string url = cfg.url + "/rest/v1/visible_profiles?id=eq." + userId +
            "&select=display_name,username,avatar_url,plan,plan_expires_at&limit=1";
        HttpPostResult h = httpGetJson(url, userHeaders);
        // A cached session may be temporarily unable to refresh; public profile
        // metadata can still restore the identity while the wallet stays cached.
        if (!h.ok || h.statusCode != 200) h = httpGetJson(url, publicHeaders);
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
            cfg.url + "/rest/v1/ac_wallets?user_id=eq." + userId + "&select=balance&limit=1", userHeaders);
        if (h.ok && h.statusCode == 200) {
            const std::vector<std::string> objs = splitJsonObjects(h.body);
            if (!objs.empty()) {
                p.acBalance = jsonGetInt(objs[0], "balance", 0);
                p.walletOk = true;
            }
        }
    }
    {
        const HttpPostResult h = httpGetJson(
            cfg.url + "/rest/v1/content_downloads?user_id=eq." + userId + "&select=id", userHeaders);
        if (h.ok && h.statusCode == 200) p.downloads = (long)splitJsonObjects(h.body).size();
    }
    {
        const HttpPostResult h = httpGetJson(
            cfg.url + "/rest/v1/content_favorites?user_id=eq." + userId + "&select=id", userHeaders);
        if (h.ok && h.statusCode == 200) p.favorites = (long)splitJsonObjects(h.body).size();
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
