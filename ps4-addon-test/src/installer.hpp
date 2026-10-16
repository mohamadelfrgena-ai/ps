#pragma once

#include <cstdint>
#include <string>

namespace pkgtest {

struct CopyResult {
    bool ok = false;
    bool queuedForMinecraft = false;
    bool launchedMinecraft = false;
    std::uint32_t filesCopied = 0;
    std::uint64_t bytesCopied = 0;
    int errorCode = 0;
    std::string message;
};

// Direct copy from source (.raw or directory) to destination folder.
CopyResult copyAddonFolder(const std::string& sourceFolder,
                           const std::string& destinationFolder);

// Smart dual-mode installer:
// - Always stages addon into stageDir (/data/AddonStation/installed).
// - If Minecraft's com.mojang path is mounted, installs immediately into resource_packs.
// - If Minecraft is closed, stages addon + marks pending + launches Minecraft (on PS4),
//   so the background watcher auto-syncs as soon as Minecraft mounts /app_tmp/CUSA00265.
CopyResult installOrQueueAddon(const std::string& sourceRawPath,
                               const std::string& addonFolderName,
                               const std::string& stageDir,
                               const std::string& preferredMojangDir);

// Called periodically by the background watcher thread:
// Checks if any .pending addon exists in stageDir AND Minecraft's com.mojang path is now mounted;
// if so, copies all staged addons into resource_packs and clears .pending.
CopyResult syncPendingAddonsIfMounted(const std::string& stageDir,
                                      const std::string& preferredMojangDir);

} // namespace pkgtest
