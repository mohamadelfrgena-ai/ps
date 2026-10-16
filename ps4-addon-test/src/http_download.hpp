#ifndef PKG_ADDON_TEST_HTTP_DOWNLOAD_HPP
#define PKG_ADDON_TEST_HTTP_DOWNLOAD_HPP

#include <cstdint>
#include <functional>
#include <string>

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

} // namespace pkgtest

#endif
