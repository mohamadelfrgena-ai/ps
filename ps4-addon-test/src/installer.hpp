#ifndef PKG_ADDON_TEST_INSTALLER_HPP
#define PKG_ADDON_TEST_INSTALLER_HPP

#include <cstdint>
#include <functional>
#include <string>
#include <vector>

namespace pkgtest {

enum class InstallPhase {
    Downloading = 0,
    Installing = 1,
    Installed = 2,
    Failed = 3
};

using ProgressCallback = std::function<void(InstallPhase phase, int percent)>;

struct CopyResult {
    bool ok = false;
    bool queuedForMinecraft = false;
    bool launchedMinecraft = false;
    std::string lastAddonName;   // آخر إضافة اكتملت في syncPendingAddonsIfMounted
    std::size_t filesCopied = 0;
    std::uint64_t bytesCopied = 0;
    int errorCode = 0;
    std::string targetCategory = "resource_packs";
    std::string detectedMojangDir;
    std::string message;
};

// Returns localized "<Addon> installed — Enjoy!" notification string for any of the 8 languages.
std::string formatEnjoyNotification(const std::string& addonName, const std::string& langCode);

// Returns localized status label ("Downloading...", "Installing...", "Installed") for any of the 8 languages.
std::string formatInstallPhaseLabel(InstallPhase phase, const std::string& langCode);

// v0.41: honest "ready — waiting for Minecraft" message (8 languages) shown when
// the addon is staged but NOT yet inside the game (queuedForMinecraft).
std::string formatQueuedMessage(const std::string& langCode);

// Checks if an addon is recorded as installed in /data/AddonStation/installed/installed.json
bool isAddonMarkedInstalled(const std::string& addonName,
                            const std::string& stageDir = "/data/AddonStation/installed");

// v0.63: real-presence + mark reconciliation («يقول مثبتة وهي غير مثبتة»)
bool addonFolderPresent(const std::string& mojangDir, const std::string& folderName);
void rewriteInstalledMarks(const std::string& stageDir, const std::vector<std::string>& names);

// Copies a pre-extracted addon folder or .raw stream to destinationFolder.
CopyResult copyAddonFolder(const std::string& sourceFolder,
                           const std::string& destinationFolder);

// Dual-mode installer:
// 1. Dynamically discovers ANY mounted Minecraft CUSAxxxxx and userYYYY folder on PS4.
// 2. Routes to resource_packs, behavior_packs, or minecraftWorlds automatically.
// 3. If Minecraft is open: installs directly into com.mojang, cleans up any temporary .raw in stageDir,
//    records in installed.json, and sends the 8-language "Enjoy!" PS4 notification.
// 4. If Minecraft is closed: stages .raw in stageDir WITHOUT launching the game (mod #4 default).
//    Pass autoLaunchMinecraft=true (the Settings toggle) to also launch the detected Minecraft
//    CUSAxxxxx right away so the deferred install completes immediately. Either way, once
//    syncPendingAddonsIfMounted() finishes copying to com.mojang, the staged .raw is deleted.
CopyResult installOrQueueAddon(
    const std::string& sourceRawPath,
    const std::string& addonFolderName,
    const std::string& stageDir = "/data/AddonStation/installed",
    const std::string& preferredMojangDir = "",
    const std::string& targetCategory = "resource_packs",
    const std::string& langCode = "en",
    ProgressCallback onProgress = nullptr,
    bool autoLaunchMinecraft = false);

// إعداد «تشغيل Minecraft تلقائياً بعد التثبيت» (الافتراضي: إيقاف). يُحفَظ في ملف
// صغير (/data/AddonStation/autolaunch.cfg) ليبقى بعد إعادة تشغيل التطبيق.
bool loadAutoLaunchSetting(const std::string& configPath = "/data/AddonStation/autolaunch.cfg");
bool saveAutoLaunchSetting(bool enabled, const std::string& configPath = "/data/AddonStation/autolaunch.cfg");

// Background watcher: checks if ANY Minecraft com.mojang is mounted, installs pending addons from stageDir,
// deletes the staged .raw files after copying to free PS4 disk space, and triggers the 8-language notification.
// يُطلق Minecraft المثبَّت (يكتشف CUSAxxxxx تلقائياً). لا يُستدعى من المثبّت
// نفسه منذ v0.13 — بل فقط من فعل المستخدم «فتح Minecraft» (△ في صفحة الإضافة).
bool launchMinecraftOnPs4();

// v0.62: exported for the download pre-check (is any Minecraft com.mojang mounted?)
std::string mountedMojangDir();
CopyResult syncPendingAddonsIfMounted(
    const std::string& stageDir = "/data/AddonStation/installed",
    const std::string& preferredMojangDir = "",
    const std::string& langCode = "en");

} // namespace pkgtest

#endif
