#pragma once

#include "addonstation_api.hpp"

#include <string>

class OrbisHttpTransport final : public addonstation::HttpTransport {
public:
    OrbisHttpTransport() = default;
    ~OrbisHttpTransport() override;

    bool initialize(std::string& error);
    void shutdown();
    addonstation::HttpResponse get(const std::string& url, const std::string& publishableKey) override;

private:
    int netPoolId_ = 0;
    int sslContextId_ = 0;
    int httpContextId_ = 0;
};
