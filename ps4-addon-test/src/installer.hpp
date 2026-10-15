#pragma once

#include <cstdint>
#include <string>

namespace pkgtest {

struct CopyResult {
    bool ok = false;
    int errorCode = 0;
    std::uint32_t filesCopied = 0;
    std::uint64_t bytesCopied = 0;
    std::string message;
};

// Recursively copies a pre-extracted addon folder (e.g. "/app0/PrizmaVisuals 1.3.10222")
// directly into the target directory (e.g. "/app_tmp/CUSA00265/user497444003/games/com.mojang/resource_packs/PrizmaVisuals 1.3.10222").
CopyResult copyAddonFolder(const std::string& sourceFolder,
                           const std::string& destinationFolder);

} // namespace pkgtest
