#include "http_download.hpp"
#include <mutex>

#ifdef __ORBIS__

#include <orbis/Http.h>
#include <orbis/Net.h>
#include <orbis/Ssl.h>
#include <orbis/Sysmodule.h>

#include <algorithm>
#include <cerrno>
#include <cstdio>
#include <cstring>
#include <vector>

namespace pkgtest {
namespace {

constexpr std::size_t kNetworkPoolBytes = 128 * 1024;
constexpr const char* kUserAgent = "AddonStationPS4/0.8";

struct HttpStack {
    int netPoolId = 0;
    int sslContextId = 0;
    int httpContextId = 0;
    bool ready = false;

    bool init() {
        // MANY threads request HTTP (catalog, covers, RPCs) — serialize the one-time stack init
        static std::mutex initMx;
        std::lock_guard<std::mutex> lk(initMx);
        if (ready) return true;
        if (sceSysmoduleLoadModuleInternal(ORBIS_SYSMODULE_INTERNAL_NET) < 0) return false;
        if (sceSysmoduleLoadModuleInternal(ORBIS_SYSMODULE_INTERNAL_HTTP) < 0) return false;
        if (sceSysmoduleLoadModuleInternal(ORBIS_SYSMODULE_INTERNAL_SSL) < 0) return false;
        if (sceNetInit() < 0) return false;

        netPoolId = sceNetPoolCreate("addonstation-dl", static_cast<int>(kNetworkPoolBytes), 0);
        if (netPoolId < 0) return false;

        sslContextId = sceSslInit(SSL_POOLSIZE);
        if (sslContextId < 0) return false;

        httpContextId = sceHttpInit(netPoolId, sslContextId, LIBHTTP_POOLSIZE);
        if (httpContextId < 0) return false;

        ready = true;
        return true;
    }
};

HttpStack gHttp;

} // namespace

DownloadResult downloadToFile(const std::string& url,
                              const std::string& outFilePath,
                              DownloadProgress onProgress) {
    DownloadResult result;

    if (!gHttp.init()) {
        result.message = "HTTP INIT FAILED";
        return result;
    }

    int templateId = 0;
    int connectionId = 0;
    int requestId = 0;

    templateId = sceHttpCreateTemplate(gHttp.httpContextId, kUserAgent, ORBIS_HTTP_VERSION_1_1, 1);
    if (templateId < 0) {
        result.message = "TEMPLATE FAILED (" + std::to_string(templateId) + ")";
        return result;
    }

    // Microseconds timeouts (same as ps4/src/orbis_http_transport.cpp)
    sceHttpSetConnectTimeOut(templateId, 20U * 1000U * 1000U);
    sceHttpSetSendTimeOut(templateId, 20U * 1000U * 1000U);
    {
        // OpenOrbis declares sceHttpSetRecvTimeOut() without args (stub); real ABI
        // is (int32_t id, uint32_t usec) — same trick as sceSslTerm in the codebase.
        using SetRecvTimeoutFn = std::int32_t (*)(std::int32_t, std::uint32_t);
        const auto setRecvTimeout = reinterpret_cast<SetRecvTimeoutFn>(&sceHttpSetRecvTimeOut);
        setRecvTimeout(templateId, 120U * 1000U * 1000U);
    }

    // GitHub Releases redirect to objects.githubusercontent.com — auto-follow.
    // OpenOrbis declares sceHttpSetAutoRedirect() without args (stub); use the
    // real ABI via a function pointer (same trick as sceSslTerm in the codebase).
    {
        using SetAutoRedirectFn = std::int32_t (*)(std::int32_t, std::int32_t);
        const auto setAutoRedirect = reinterpret_cast<SetAutoRedirectFn>(&sceHttpSetAutoRedirect);
        setAutoRedirect(templateId, 1);
    }

    connectionId = sceHttpCreateConnectionWithURL(templateId, url.c_str(), 1);
    if (connectionId < 0) {
        result.message = "CONNECT FAILED (" + std::to_string(connectionId) + ")";
        sceHttpDeleteTemplate(templateId);
        return result;
    }

    requestId = sceHttpCreateRequestWithURL(connectionId, ORBIS_METHOD_GET, url.c_str(), 0);
    if (requestId < 0) {
        result.message = "REQUEST FAILED (" + std::to_string(requestId) + ")";
        sceHttpDeleteConnection(connectionId);
        sceHttpDeleteTemplate(templateId);
        return result;
    }

    const int sendResult = sceHttpSendRequest(requestId, nullptr, 0);
    if (sendResult < 0) {
        result.message = "SEND FAILED (" + std::to_string(sendResult) + ")";
        goto cleanup;
    }

    {
        int statusCode = 0;
        if (sceHttpGetStatusCode(requestId, &statusCode) < 0) {
            result.message = "STATUS FAILED";
            goto cleanup;
        }
        result.statusCode = statusCode;
        if (statusCode != 200) {
            result.message = "HTTP STATUS " + std::to_string(statusCode);
            goto cleanup;
        }

        std::size_t contentLength = 0;
        {
            int lenResult = 0;
            sceHttpGetResponseContentLength(requestId, &lenResult, &contentLength);
        }

        std::FILE* outFp = std::fopen(outFilePath.c_str(), "wb");
        if (!outFp) {
            result.message = "OPEN FAILED (" + std::to_string(errno) + ")";
            goto cleanup;
        }

        {
            std::vector<char> buffer(64 * 1024);
            std::uint64_t written = 0;
            int lastPct = -1;
            for (;;) {
                const int bytesRead = sceHttpReadData(requestId, buffer.data(),
                                                      static_cast<std::uint32_t>(buffer.size()));
                if (bytesRead < 0) {
                    std::fclose(outFp);
                    std::remove(outFilePath.c_str());
                    result.message = "READ FAILED (" + std::to_string(bytesRead) + ")";
                    goto cleanup;
                }
                if (bytesRead == 0) break;
                if (std::fwrite(buffer.data(), 1, static_cast<std::size_t>(bytesRead), outFp) !=
                    static_cast<std::size_t>(bytesRead)) {
                    std::fclose(outFp);
                    std::remove(outFilePath.c_str());
                    result.message = "WRITE FAILED (" + std::to_string(errno) + ")";
                    goto cleanup;
                }
                written += static_cast<std::uint64_t>(bytesRead);
                if (onProgress && contentLength > 0) {
                    const int pct = static_cast<int>(
                        std::min<std::uint64_t>(100ULL, (written * 100ULL) / contentLength));
                    if (pct != lastPct) {
                        lastPct = pct;
                        onProgress(pct);
                    }
                }
            }
            std::fclose(outFp);
            result.ok = true;
            result.bytesWritten = written;
            result.message = "DONE";
            if (onProgress) onProgress(100);
        }
    }

cleanup:
    if (requestId > 0) sceHttpDeleteRequest(requestId);
    if (connectionId > 0) sceHttpDeleteConnection(connectionId);
    if (templateId > 0) sceHttpDeleteTemplate(templateId);
    return result;
}

HttpPostResult httpPostJson(const std::string& url,
                            const std::vector<std::pair<std::string, std::string>>& headers,
                            const std::string& jsonBody) {
    HttpPostResult result;

    if (!gHttp.init()) {
        result.message = "HTTP INIT FAILED";
        return result;
    }

    const int templateId = sceHttpCreateTemplate(
        gHttp.httpContextId, kUserAgent, ORBIS_HTTP_VERSION_1_1, 1);
    if (templateId < 0) {
        result.message = "TEMPLATE FAILED (" + std::to_string(templateId) + ")";
        return result;
    }

    sceHttpSetConnectTimeOut(templateId, 20U * 1000U * 1000U);
    sceHttpSetSendTimeOut(templateId, 20U * 1000U * 1000U);
    {
        using SetRecvTimeoutFn = std::int32_t (*)(std::int32_t, std::uint32_t);
        const auto setRecvTimeout = reinterpret_cast<SetRecvTimeoutFn>(&sceHttpSetRecvTimeOut);
        setRecvTimeout(templateId, 20U * 1000U * 1000U);
    }
    {
        using SetAutoRedirectFn = std::int32_t (*)(std::int32_t, std::int32_t);
        const auto setAutoRedirect = reinterpret_cast<SetAutoRedirectFn>(&sceHttpSetAutoRedirect);
        setAutoRedirect(templateId, 1);
    }

    const int connectionId = sceHttpCreateConnectionWithURL(templateId, url.c_str(), 1);
    if (connectionId < 0) {
        sceHttpDeleteTemplate(templateId);
        result.message = "CONNECT FAILED (" + std::to_string(connectionId) + ")";
        return result;
    }

    const int requestId = sceHttpCreateRequestWithURL(
        connectionId, ORBIS_METHOD_POST, url.c_str(), jsonBody.size());
    if (requestId < 0) {
        sceHttpDeleteConnection(connectionId);
        sceHttpDeleteTemplate(templateId);
        result.message = "REQUEST FAILED (" + std::to_string(requestId) + ")";
        return result;
    }

    for (const auto& h : headers) {
        sceHttpAddRequestHeader(requestId, h.first.c_str(), h.second.c_str(), 1);
    }

    const int sendResult = sceHttpSendRequest(
        requestId, jsonBody.data(), static_cast<std::uint32_t>(jsonBody.size()));
    if (sendResult < 0) {
        result.message = "SEND FAILED (" + std::to_string(sendResult) + ")";
    } else {
        int statusCode = 0;
        if (sceHttpGetStatusCode(requestId, &statusCode) < 0) {
            result.message = "STATUS FAILED";
        } else {
            result.statusCode = statusCode;
            std::vector<char> buffer(64 * 1024);
            for (;;) {
                const int bytesRead = sceHttpReadData(
                    requestId, buffer.data(), static_cast<std::uint32_t>(buffer.size()));
                if (bytesRead < 0) {
                    result.message = "READ FAILED (" + std::to_string(bytesRead) + ")";
                    break;
                }
                if (bytesRead == 0) break;
                result.body.append(buffer.data(), static_cast<std::size_t>(bytesRead));
            }
            if (result.message.empty()) {
                result.ok = true;
                result.message = "DONE";
            }
        }
    }

    sceHttpDeleteRequest(requestId);
    sceHttpDeleteConnection(connectionId);
    sceHttpDeleteTemplate(templateId);
    return result;
}

HttpPostResult httpGetJson(const std::string& url,
                           const std::vector<std::pair<std::string, std::string>>& headers) {
    HttpPostResult result;

    if (!gHttp.init()) {
        result.message = "HTTP INIT FAILED";
        return result;
    }

    const int templateId = sceHttpCreateTemplate(
        gHttp.httpContextId, kUserAgent, ORBIS_HTTP_VERSION_1_1, 1);
    if (templateId < 0) {
        result.message = "TEMPLATE FAILED (" + std::to_string(templateId) + ")";
        return result;
    }

    sceHttpSetConnectTimeOut(templateId, 20U * 1000U * 1000U);
    sceHttpSetSendTimeOut(templateId, 20U * 1000U * 1000U);
    {
        using SetRecvTimeoutFn = std::int32_t (*)(std::int32_t, std::uint32_t);
        const auto setRecvTimeout = reinterpret_cast<SetRecvTimeoutFn>(&sceHttpSetRecvTimeOut);
        setRecvTimeout(templateId, 20U * 1000U * 1000U);
    }
    {
        using SetAutoRedirectFn = std::int32_t (*)(std::int32_t, std::int32_t);
        const auto setAutoRedirect = reinterpret_cast<SetAutoRedirectFn>(&sceHttpSetAutoRedirect);
        setAutoRedirect(templateId, 1);
    }

    const int connectionId = sceHttpCreateConnectionWithURL(templateId, url.c_str(), 1);
    if (connectionId < 0) {
        sceHttpDeleteTemplate(templateId);
        result.message = "CONNECT FAILED (" + std::to_string(connectionId) + ")";
        return result;
    }

    const int requestId = sceHttpCreateRequestWithURL(
        connectionId, ORBIS_METHOD_GET, url.c_str(), 0);
    if (requestId < 0) {
        sceHttpDeleteConnection(connectionId);
        sceHttpDeleteTemplate(templateId);
        result.message = "REQUEST FAILED (" + std::to_string(requestId) + ")";
        return result;
    }

    for (const auto& h : headers) {
        sceHttpAddRequestHeader(requestId, h.first.c_str(), h.second.c_str(), 1);
    }

    const int sendResult = sceHttpSendRequest(requestId, nullptr, 0);
    if (sendResult < 0) {
        result.message = "SEND FAILED (" + std::to_string(sendResult) + ")";
    } else {
        int statusCode = 0;
        if (sceHttpGetStatusCode(requestId, &statusCode) < 0) {
            result.message = "STATUS FAILED";
        } else {
            result.statusCode = statusCode;
            std::vector<char> buffer(64 * 1024);
            for (;;) {
                const int bytesRead = sceHttpReadData(
                    requestId, buffer.data(), static_cast<std::uint32_t>(buffer.size()));
                if (bytesRead < 0) {
                    result.message = "READ FAILED (" + std::to_string(bytesRead) + ")";
                    break;
                }
                if (bytesRead == 0) break;
                result.body.append(buffer.data(), static_cast<std::size_t>(bytesRead));
            }
            if (result.message.empty()) {
                result.ok = true;
                result.message = "DONE";
            }
        }
    }

    sceHttpDeleteRequest(requestId);
    sceHttpDeleteConnection(connectionId);
    sceHttpDeleteTemplate(templateId);
    return result;
}

} // namespace pkgtest

#else // ---- host build (tests) ----

namespace pkgtest {

DownloadResult downloadToFile(const std::string&, const std::string&, DownloadProgress) {
    DownloadResult result;
    result.message = "DOWNLOAD NOT AVAILABLE ON HOST";
    return result;
}

HttpPostResult httpPostJson(const std::string&,
                            const std::vector<std::pair<std::string, std::string>>&,
                            const std::string&) {
    HttpPostResult result;
    result.message = "NETWORK NOT AVAILABLE ON HOST";
    return result;
}

HttpPostResult httpGetJson(const std::string&,
                           const std::vector<std::pair<std::string, std::string>>&) {
    HttpPostResult result;
    result.message = "NETWORK NOT AVAILABLE ON HOST";
    return result;
}

} // namespace pkgtest

#endif
