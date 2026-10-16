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

RpcResult supabaseRpc(const SupabaseConfig& cfg,
                      const std::string& fn,
                      const std::string& jsonBody) {
    RpcResult result;
    if (!cfg.ok()) {
        result.message = "not_configured";
        return result;
    }

    const std::string url = cfg.url + "/rest/v1/rpc/" + fn;
    const std::vector<std::pair<std::string, std::string>> headers = {
        {"apikey", cfg.anonKey},
        {"Authorization", "Bearer " + cfg.anonKey},
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

void saveLinkSession(const LinkSession& s) {
    ensureParentDir(kSessionPath);
    std::ofstream out(kSessionPath, std::ios::trunc);
    if (!out) return;
    out << "valid=" << (s.valid ? "1" : "0") << "\n"
        << "user_id=" << s.userId << "\n"
        << "username=" << s.username << "\n"
        << "display_name=" << s.displayName << "\n"
        << "plan=" << s.plan << "\n"
        << "ac_balance=" << s.acBalance << "\n"
        << "access_token=" << s.accessToken << "\n"
        << "refresh_token=" << s.refreshToken << "\n";
}

LinkSession loadLinkSession() {
    LinkSession s;
    std::ifstream in(kSessionPath);
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
        else if (k == "plan") s.plan = v;
        else if (k == "ac_balance") s.acBalance = std::strtol(v.c_str(), nullptr, 10);
        else if (k == "access_token") s.accessToken = v;
        else if (k == "refresh_token") s.refreshToken = v;
    }
    return s;
}

void clearLinkSession() {
    std::remove(kSessionPath);
}

} // namespace pkgtest
