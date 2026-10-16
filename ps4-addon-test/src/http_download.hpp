#ifndef PKG_ADDON_TEST_HTTP_DOWNLOAD_HPP
#define PKG_ADDON_TEST_HTTP_DOWNLOAD_HPP

#include <cstdint>
#include <functional>
#include <string>
#include <utility>
#include <vector>

namespace pkgtest {

// Progress: percent 0..100 while downloading
using DownloadProgress = std::function<void(int percent)>;

struct DownloadResult {
    bool ok = false;
    int statusCode = 0;
    std::uint64_t bytesWritten = 0;
    std::string message;
};

// Downloads a URL to outFilePath over HTTPS (follows redirects — GitHub Releases).
// Returns ok=false with a message on any failure; partial files are deleted.
DownloadResult downloadToFile(const std::string& url,
                              const std::string& outFilePath,
                              DownloadProgress onProgress = nullptr);

struct HttpPostResult {
    bool ok = false;          // transport-level success (response body received)
    int statusCode = 0;
    std::string body;         // raw response body (JSON)
    std::string message;
};

// POST JSON with custom headers over HTTPS (Supabase RPC). Returns the raw body.
HttpPostResult httpPostJson(const std::string& url,
                            const std::vector<std::pair<std::string, std::string>>& headers,
                            const std::string& jsonBody);

} // namespace pkgtest

#endif
