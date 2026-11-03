#include "addonstation_api.hpp"
#include "demo_data.hpp"
#include "json.hpp"

#include <cassert>
#include <deque>
#include <iostream>
#include <string>
#include <utility>
#include <vector>

namespace {

class FakeTransport final : public addonstation::HttpTransport {
public:
    std::deque<addonstation::HttpResponse> replies;
    std::vector<std::string> urls;
    std::vector<std::string> keys;

    addonstation::HttpResponse get(const std::string& url, const std::string& key) override {
        urls.push_back(url);
        keys.push_back(key);
        if (replies.empty()) return {500, {}, "No fake response queued"};
        addonstation::HttpResponse reply = std::move(replies.front());
        replies.pop_front();
        return reply;
    }
};

void testJsonParser() {
    addonstation::json::Value value;
    std::string error;
    const std::string document =
        "{\"message\":\"quote: \\\"; emoji: \\uD83C\\uDFE0\","
        "\"count\":42,\"price\":5.50,\"enabled\":true,\"nothing\":null}";
    assert(addonstation::json::parse(document, value, &error));
    assert(value.type == addonstation::json::Type::Object);
    assert(value.get("message") != nullptr);
    assert(value.get("message")->stringOr().find("quote: \"") != std::string::npos);
    assert(value.get("message")->stringOr().find("\xF0\x9F\x8F\xA0") != std::string::npos);
    assert(value.get("count")->integerOr() == 42);
    assert(value.get("price")->numberOr() > 5.49 && value.get("price")->numberOr() < 5.51);
    assert(value.get("enabled")->boolOr());
    assert(value.get("nothing")->type == addonstation::json::Type::Null);

    assert(!addonstation::json::parse("{\"bad\":,}", value, &error));
    assert(!error.empty());
    assert(!addonstation::json::parse("[1,]", value, &error));
}

void testConfigurationValidation() {
    std::string error;
    assert(addonstation::AddonStationApi::validateProjectUrl(
        "https://demo-project.supabase.co", &error));
    assert(addonstation::AddonStationApi::validateProjectUrl(
        "https://demo-project.supabase.co/", &error));
    assert(!addonstation::AddonStationApi::validateProjectUrl(
        "http://demo-project.supabase.co", &error));
    assert(!addonstation::AddonStationApi::validateProjectUrl(
        "https://demo-project.supabase.co/rest/v1", &error));

    assert(addonstation::AddonStationApi::validatePublicKey(
        "sb_publishable_abcdefghijklmnopqrstuvwxyz0123456789", &error));
    assert(!addonstation::AddonStationApi::validatePublicKey(
        "sb_publishable_REPLACE_ME", &error));
    assert(!addonstation::AddonStationApi::validatePublicKey(
        "sb_secret_sensitive_value", &error));
    assert(!addonstation::AddonStationApi::validatePublicKey(
        "eyJhbGci.eyJyb2xlIjoic2VydmljZV9yb2xlIn0.signature", &error));
    assert(addonstation::AddonStationApi::validatePublicKey(
        "eyJhbGci.eyJyb2xlIjoiYW5vbiJ9.signature", &error));
}

void testSharedCatalogRead() {
    FakeTransport transport;
    transport.replies.push_back({200,
        "[{\"id\":\"c-1\",\"title\":\"Demo Add-on\",\"description\":\"A\\nsmall demo.\","
        "\"content_type\":\"addon\",\"plan\":\"free\",\"version\":\"1.2\","
        "\"downloads_count\":42,\"views_count\":120}]", {}});
    transport.replies.push_back({200,
        "[{\"id\":\"free\",\"name\":\"Free\",\"tagline\":\"Start here\","
        "\"price_lyd\":0,\"price_usd\":0,\"duration_days\":30,"
        "\"download_limit\":null,\"limit_period\":\"none\"}]", {}});

    addonstation::AddonStationApi api(
        transport,
        "https://demo-project.supabase.co/",
        "sb_publishable_abcdefghijklmnopqrstuvwxyz0123456789");
    assert(api.isConfigured());
    const addonstation::FetchResult result = api.fetchCatalog();
    assert(result.ok);
    assert(result.snapshot.content.size() == 1);
    assert(result.snapshot.plans.size() == 1);
    assert(result.snapshot.content[0].title == "Demo Add-on");
    assert(result.snapshot.content[0].downloads == 42);
    assert(result.snapshot.content[0].description == "A\nsmall demo.");
    assert(result.snapshot.plans[0].priceLyd == "0");
    assert(result.snapshot.plans[0].downloadLimit == "unlimited");
    assert(transport.urls.size() == 2);
    assert(transport.urls[0].find("/rest/v1/content_items?") != std::string::npos);
    assert(transport.urls[0].find("is_published=eq.true") != std::string::npos);
    assert(transport.urls[1].find("/rest/v1/subscription_plans?") != std::string::npos);
    assert(transport.urls[1].find("active=eq.true") != std::string::npos);
    assert(transport.urls[0].find("demo-project.supabase.co//") == std::string::npos);
    assert(transport.keys[0] == transport.keys[1]);
}

void testDemoCatalog() {
    const addonstation::CatalogSnapshot demo = ps4app::makeDemoCatalog();
    assert(demo.content.size() == 6);
    assert(demo.plans.size() == 3);
    assert(demo.content.front().id.find("demo-") == 0);
    assert(demo.content.front().title.find("DEMO") != std::string::npos);
    assert(demo.plans[0].name.find("DEMO") != std::string::npos);
}

void testDeniedRead() {
    FakeTransport transport;
    transport.replies.push_back({403, "{}", {}});
    addonstation::AddonStationApi api(
        transport,
        "https://demo-project.supabase.co",
        "sb_publishable_abcdefghijklmnopqrstuvwxyz0123456789");
    const addonstation::FetchResult result = api.fetchCatalog();
    assert(!result.ok);
    assert(result.message.find("RLS") != std::string::npos);
    assert(transport.urls.size() == 1);
}

} // namespace

int main() {
    testJsonParser();
    testConfigurationValidation();
    testSharedCatalogRead();
    testDemoCatalog();
    testDeniedRead();
    std::cout << "PS4 shared-catalog host tests passed.\n";
    return 0;
}
