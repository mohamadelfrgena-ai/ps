#include "orbis_http_transport.hpp"

#include <orbis/Http.h>
#include <orbis/Net.h>
#include <orbis/Ssl.h>
#include <orbis/Sysmodule.h>

#include <cstdint>
#include <iomanip>
#include <sstream>
#include <string>

namespace {
constexpr std::size_t kNetworkPoolBytes = 128 * 1024;
constexpr std::size_t kMaximumResponseBytes = 256 * 1024;
constexpr const char* kUserAgent = "AddonStationPS4/0.1";

std::string errorCode(const char* operation, int code) {
    std::ostringstream message;
    message << operation << " failed (0x" << std::hex
            << static_cast<unsigned int>(code) << ").";
    return message.str();
}

void ignoreResult(int result) {
    (void)result;
}

void terminateSslContext(int contextId) {
    // OpenOrbis' generated Ssl.h declares sceSslTerm() without parameters,
    // while its own net_http C sample calls sceSslTerm(contextId). Use the
    // sample's ABI without tripping the inaccurate C++ prototype.
    using SslTermWithContext = void (*)(std::int32_t);
    const auto terminate = reinterpret_cast<SslTermWithContext>(&sceSslTerm);
    terminate(static_cast<std::int32_t>(contextId));
}
} // namespace

OrbisHttpTransport::~OrbisHttpTransport() {
    shutdown();
}

bool OrbisHttpTransport::initialize(std::string& error) {
    if (httpContextId_ > 0) {
        error.clear();
        return true;
    }

    int result = static_cast<std::int32_t>(
        sceSysmoduleLoadModuleInternal(ORBIS_SYSMODULE_INTERNAL_NET));
    if (result < 0) {
        error = errorCode("Loading the PS4 network module", result);
        return false;
    }
    result = static_cast<std::int32_t>(
        sceSysmoduleLoadModuleInternal(ORBIS_SYSMODULE_INTERNAL_HTTP));
    if (result < 0) {
        error = errorCode("Loading the PS4 HTTP module", result);
        return false;
    }
    result = static_cast<std::int32_t>(
        sceSysmoduleLoadModuleInternal(ORBIS_SYSMODULE_INTERNAL_SSL));
    if (result < 0) {
        error = errorCode("Loading the PS4 TLS module", result);
        return false;
    }

    result = sceNetInit();
    if (result < 0) {
        error = errorCode("Initializing PS4 networking", result);
        return false;
    }

    netPoolId_ = sceNetPoolCreate("addonstation-ps4", static_cast<int>(kNetworkPoolBytes), 0);
    if (netPoolId_ < 0) {
        error = errorCode("Creating the PS4 network pool", netPoolId_);
        netPoolId_ = 0;
        return false;
    }

    sslContextId_ = sceSslInit(SSL_POOLSIZE);
    if (sslContextId_ < 0) {
        error = errorCode("Initializing PS4 TLS", sslContextId_);
        sslContextId_ = 0;
        shutdown();
        return false;
    }

    httpContextId_ = sceHttpInit(netPoolId_, sslContextId_, LIBHTTP_POOLSIZE);
    if (httpContextId_ < 0) {
        error = errorCode("Initializing PS4 HTTPS", httpContextId_);
        httpContextId_ = 0;
        shutdown();
        return false;
    }

    error.clear();
    return true;
}

void OrbisHttpTransport::shutdown() {
    if (httpContextId_ > 0) {
        ignoreResult(sceHttpTerm(httpContextId_));
        httpContextId_ = 0;
    }
    if (sslContextId_ > 0) {
        terminateSslContext(sslContextId_);
        sslContextId_ = 0;
    }
    if (netPoolId_ > 0) {
        sceNetPoolDestroy(netPoolId_);
        netPoolId_ = 0;
    }
}

addonstation::HttpResponse OrbisHttpTransport::get(
    const std::string& url,
    const std::string& publishableKey) {
    addonstation::HttpResponse response;
    if (httpContextId_ <= 0) {
        response.error = "PS4 HTTPS is not initialized.";
        return response;
    }

    int templateId = 0;
    int connectionId = 0;
    int requestId = 0;
    int result = 0;

    templateId = sceHttpCreateTemplate(
        httpContextId_, kUserAgent, ORBIS_HTTP_VERSION_1_1, 1);
    if (templateId < 0) {
        response.error = errorCode("Creating an HTTPS request", templateId);
        return response;
    }

    // Timeout values are in microseconds in the OpenOrbis HTTP API.
    ignoreResult(sceHttpSetConnectTimeOut(templateId, 15U * 1000U * 1000U));
    ignoreResult(sceHttpSetSendTimeOut(templateId, 15U * 1000U * 1000U));

    connectionId = sceHttpCreateConnectionWithURL(templateId, url.c_str(), 1);
    if (connectionId < 0) {
        response.error = errorCode("Opening the Supabase HTTPS connection", connectionId);
        goto cleanup;
    }

    requestId = sceHttpCreateRequestWithURL(
        connectionId, ORBIS_METHOD_GET, url.c_str(), 0);
    if (requestId < 0) {
        response.error = errorCode("Creating the Supabase GET request", requestId);
        goto cleanup;
    }

    // Header mode 0 is overwrite. The public key is sent only as `apikey`;
    // no Authorization bearer is added because this prototype is anonymous/read-only.
    result = sceHttpAddRequestHeader(requestId, "apikey", publishableKey.c_str(), 0);
    if (result < 0) {
        response.error = errorCode("Adding the Supabase public-key header", result);
        goto cleanup;
    }
    result = sceHttpAddRequestHeader(requestId, "Accept", "application/json", 0);
    if (result < 0) {
        response.error = errorCode("Adding the JSON Accept header", result);
        goto cleanup;
    }

    result = sceHttpSendRequest(requestId, nullptr, 0);
    if (result < 0) {
        response.error = errorCode("Sending the Supabase request", result);
        goto cleanup;
    }

    result = sceHttpGetStatusCode(requestId, &response.statusCode);
    if (result < 0) {
        response.error = errorCode("Reading the Supabase HTTP status", result);
        goto cleanup;
    }

    {
        char buffer[8192];
        for (;;) {
            const int bytesRead = sceHttpReadData(requestId, buffer, sizeof(buffer));
            if (bytesRead < 0) {
                response.error = errorCode("Reading the Supabase response", bytesRead);
                break;
            }
            if (bytesRead == 0) break;
            if (response.body.size() + static_cast<std::size_t>(bytesRead) > kMaximumResponseBytes) {
                response.body.clear();
                response.error = "Supabase response exceeded the 256 KB prototype limit.";
                break;
            }
            response.body.append(buffer, static_cast<std::size_t>(bytesRead));
        }
    }

cleanup:
    if (requestId > 0) ignoreResult(sceHttpDeleteRequest(requestId));
    if (connectionId > 0) ignoreResult(sceHttpDeleteConnection(connectionId));
    if (templateId > 0) ignoreResult(sceHttpDeleteTemplate(templateId));
    return response;
}
