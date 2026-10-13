#include "addonstation_api.hpp"

#include "json.hpp"

#include <cctype>
#include <cstdint>
#include <utility>

namespace addonstation {
namespace {

bool startsWith(const std::string& value, const char* prefix) {
    const std::string wanted(prefix);
    return value.size() >= wanted.size() && value.compare(0, wanted.size(), wanted) == 0;
}

std::string trimTrailingSlashes(std::string value) {
    while (!value.empty() && value.back() == '/') value.pop_back();
    return value;
}

bool decodeBase64Url(const std::string& input, std::string& output) {
    output.clear();
    std::uint32_t accumulator = 0;
    int bits = -8;
    for (char ch : input) {
        if (ch == '=') break;
        int value = -1;
        if (ch >= 'A' && ch <= 'Z') value = ch - 'A';
        else if (ch >= 'a' && ch <= 'z') value = ch - 'a' + 26;
        else if (ch >= '0' && ch <= '9') value = ch - '0' + 52;
        else if (ch == '-') value = 62;
        else if (ch == '_') value = 63;
        if (value < 0) return false;
        accumulator = (accumulator << 6) | value;
        bits += 6;
        if (bits >= 0) {
            output.push_back(static_cast<char>((accumulator >> bits) & 0xFF));
            bits -= 8;
        }
    }
    return true;
}

std::string stringField(const json::Value& row, const char* key, const std::string& fallback = {}) {
    const json::Value* value = row.get(key);
    if (!value) return fallback;
    return value->textOr(fallback);
}

long long integerField(const json::Value& row, const char* key, long long fallback = 0) {
    const json::Value* value = row.get(key);
    return value ? value->integerOr(fallback) : fallback;
}

bool parseRows(const std::string& body, json::Value& rows, std::string& error) {
    if (body.size() > 256 * 1024) {
        error = "Supabase response is larger than the 256 KB prototype limit.";
        return false;
    }
    if (!json::parse(body, rows, &error)) {
        error = "Invalid JSON from Supabase: " + error;
        return false;
    }
    if (rows.type != json::Type::Array) {
        error = "Supabase returned an unexpected response; expected a JSON array.";
        return false;
    }
    return true;
}

} // namespace

AddonStationApi::AddonStationApi(HttpTransport& transport, std::string projectUrl, std::string publishableKey)
    : transport_(transport), projectUrl_(trimTrailingSlashes(std::move(projectUrl))),
      publishableKey_(std::move(publishableKey)) {
    if (!validateProjectUrl(projectUrl_, &configurationError_)) return;
    if (!validatePublicKey(publishableKey_, &configurationError_)) return;
}

bool AddonStationApi::isConfigured() const {
    return configurationError_.empty();
}

const std::string& AddonStationApi::configurationError() const {
    return configurationError_;
}

bool AddonStationApi::validateProjectUrl(const std::string& url, std::string* error) {
    auto reject = [error](const char* message) {
        if (error) *error = message;
        return false;
    };
    const std::string normalized = trimTrailingSlashes(url);
    if (normalized.empty()) return reject("Set the shared Supabase project URL in ps4/config.local.hpp.");
    if (!startsWith(normalized, "https://")) return reject("The Supabase project URL must start with https://.");
    const std::string host = normalized.substr(8);
    if (host.empty() || host.find('.') == std::string::npos)
        return reject("The Supabase URL must contain a valid project host.");
    for (unsigned char ch : host) {
        if (std::isspace(ch) || ch < 0x21 || ch == '/' || ch == '?' || ch == '#' || ch == '@' || ch == '\\')
            return reject("Use only the HTTPS project root, for example https://your-project.supabase.co.");
    }
    if (error) error->clear();
    return true;
}

bool AddonStationApi::validatePublicKey(const std::string& key, std::string* error) {
    auto reject = [error](const char* message) {
        if (error) *error = message;
        return false;
    };
    if (key.empty()) return reject("Set a Supabase publishable key (sb_publishable_...) in ps4/config.local.hpp.");
    if (key.find("sb_secret_") != std::string::npos || key.find("service_role") != std::string::npos)
        return reject("Secret/service_role keys are blocked. Use sb_publishable_... (never a secret key).");
    for (unsigned char ch : key) {
        if (std::isspace(ch) || ch < 0x21 || ch == '\\' || ch == '"' || ch == '\'')
            return reject("The Supabase key contains an invalid character.");
    }
    if (key.find("REPLACE") != std::string::npos || key.find("YOUR_") != std::string::npos)
        return reject("Replace the sample key with your Supabase publishable key.");

    if (startsWith(key, "sb_publishable_")) {
        if (key.size() <= std::string("sb_publishable_").size() + 8)
            return reject("The Supabase publishable key looks incomplete.");
        if (error) error->clear();
        return true;
    }

    // Older Supabase projects use JWT-shaped anon keys. Decode and inspect the
    // role claim so a legacy service_role JWT cannot be accepted by mistake.
    if (startsWith(key, "eyJ")) {
        const std::size_t firstDot = key.find('.');
        const std::size_t secondDot = firstDot == std::string::npos ? firstDot : key.find('.', firstDot + 1);
        if (firstDot == std::string::npos || secondDot == std::string::npos)
            return reject("Legacy key is not a complete JWT; use the publishable key from Supabase.");
        std::string payload;
        if (!decodeBase64Url(key.substr(firstDot + 1, secondDot - firstDot - 1), payload))
            return reject("Could not inspect the legacy key. Use sb_publishable_... instead.");
        json::Value claims;
        std::string parseError;
        if (!json::parse(payload, claims, &parseError) || claims.type != json::Type::Object)
            return reject("Could not inspect the legacy key. Use sb_publishable_... instead.");
        const json::Value* role = claims.get("role");
        if (!role || role->stringOr() != "anon")
            return reject("Only a legacy JWT with role=anon is allowed; service_role is blocked.");
        if (error) error->clear();
        return true;
    }

    return reject("Unsupported key format. Use sb_publishable_... or a legacy anon JWT.");
}

bool AddonStationApi::getArray(const std::string& path, std::string& body, std::string& error) const {
    const HttpResponse response = transport_.get(projectUrl_ + path, publishableKey_);
    if (!response.error.empty()) {
        error = response.error;
        return false;
    }
    if (response.statusCode < 200 || response.statusCode >= 300) {
        if (response.statusCode == 401 || response.statusCode == 403) {
            error = "Supabase denied the public read (HTTP " + std::to_string(response.statusCode) +
                    "). Check the publishable key, table grants, and RLS policies.";
        } else {
            error = "Supabase returned HTTP " + std::to_string(response.statusCode) + ".";
        }
        return false;
    }
    body = response.body;
    return true;
}

FetchResult AddonStationApi::fetchCatalog() const {
    FetchResult result;
    if (!isConfigured()) {
        result.message = configurationError_;
        return result;
    }

    // Deliberately read-only: the PS4 prototype reads only the same public
    // catalog and plans used by the other AddonStation clients.
    std::string contentBody;
    if (!getArray(
            "/rest/v1/content_items?select=id,title,description,content_type,plan,version,downloads_count,views_count"
            "&is_published=eq.true&order=created_at.desc&limit=12",
            contentBody, result.message)) {
        return result;
    }

    json::Value contentRows;
    if (!parseRows(contentBody, contentRows, result.message)) return result;
    for (const json::Value& row : contentRows.array) {
        if (row.type != json::Type::Object) continue;
        ContentItem item;
        item.id = stringField(row, "id");
        item.title = stringField(row, "title", "Untitled");
        item.description = stringField(row, "description");
        item.contentType = stringField(row, "content_type", "addon");
        item.plan = stringField(row, "plan", "free");
        item.version = stringField(row, "version");
        item.downloads = integerField(row, "downloads_count");
        item.views = integerField(row, "views_count");
        result.snapshot.content.push_back(std::move(item));
    }

    std::string plansBody;
    if (!getArray(
            "/rest/v1/subscription_plans?select=id,name,tagline,price_lyd,price_usd,duration_days,download_limit,limit_period"
            "&active=eq.true&order=sort_order.asc",
            plansBody, result.message)) {
        result.snapshot = CatalogSnapshot{};
        return result;
    }

    json::Value planRows;
    if (!parseRows(plansBody, planRows, result.message)) {
        result.snapshot = CatalogSnapshot{};
        return result;
    }
    for (const json::Value& row : planRows.array) {
        if (row.type != json::Type::Object) continue;
        SubscriptionPlan plan;
        plan.id = stringField(row, "id");
        plan.name = stringField(row, "name", plan.id);
        plan.tagline = stringField(row, "tagline");
        plan.priceLyd = stringField(row, "price_lyd", "0");
        plan.priceUsd = stringField(row, "price_usd", "0");
        plan.limitPeriod = stringField(row, "limit_period", "none");
        plan.downloadLimit = stringField(row, "download_limit", "unlimited");
        plan.durationDays = integerField(row, "duration_days");
        result.snapshot.plans.push_back(std::move(plan));
    }

    result.ok = true;
    result.message = "Catalog and plans loaded from the shared Supabase project.";
    return result;
}

} // namespace addonstation
