#pragma once

#include <string>
#include <vector>

namespace addonstation {

struct HttpResponse {
    int statusCode = 0;
    std::string body;
    std::string error;
};

class HttpTransport {
public:
    virtual ~HttpTransport() = default;
    virtual HttpResponse get(const std::string& url, const std::string& publishableKey) = 0;
};

struct ContentItem {
    std::string id;
    std::string title;
    std::string description;
    std::string contentType;
    std::string plan;
    std::string version;
    long long downloads = 0;
    long long views = 0;
};

struct SubscriptionPlan {
    std::string id;
    std::string name;
    std::string tagline;
    std::string priceLyd = "0";
    std::string priceUsd = "0";
    std::string limitPeriod;
    std::string downloadLimit;
    long long durationDays = 0;
};

struct CatalogSnapshot {
    std::vector<ContentItem> content;
    std::vector<SubscriptionPlan> plans;
};

struct FetchResult {
    bool ok = false;
    std::string message;
    CatalogSnapshot snapshot;
};

class AddonStationApi {
public:
    AddonStationApi(HttpTransport& transport, std::string projectUrl, std::string publishableKey);

    bool isConfigured() const;
    const std::string& configurationError() const;
    FetchResult fetchCatalog() const;

    // These checks are deliberately strict: only HTTPS project roots and a
    // Supabase publishable key (or a legacy JWT whose role is exactly "anon").
    static bool validateProjectUrl(const std::string& url, std::string* error = nullptr);
    static bool validatePublicKey(const std::string& key, std::string* error = nullptr);

private:
    HttpTransport& transport_;
    std::string projectUrl_;
    std::string publishableKey_;
    std::string configurationError_;

    bool getArray(const std::string& path, std::string& body, std::string& error) const;
};

} // namespace addonstation
