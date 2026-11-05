#include "installer.hpp"

#ifdef __ORBIS__
#include "jbc.h"
#include <orbis/SystemService.h>
#include <orbis/UserService.h>
#include <orbis/libkernel.h>
#endif

#include <algorithm>
#include <cerrno>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <dirent.h>
#include <fcntl.h>
#include <fstream>
#include <sys/stat.h>
#include <unistd.h>
#include <vector>

namespace pkgtest {
namespace {

bool isDirectory(const std::string& path) {
    struct stat st {};
    return ::stat(path.c_str(), &st) == 0 && S_ISDIR(st.st_mode);
}

bool isRegularFile(const std::string& path) {
    struct stat st {};
    return ::stat(path.c_str(), &st) == 0 && S_ISREG(st.st_mode);
}

bool ensureDirectory(const std::string& path, int& outErrno) {
    outErrno = 0;
    if (isDirectory(path)) return true;
    if (::mkdir(path.c_str(), 0777) == 0 || errno == EEXIST) {
        if (isDirectory(path)) return true;
    }

    std::size_t pos = 1;
    while (pos <= path.size()) {
        const std::size_t slash = path.find('/', pos);
        const std::size_t end = (slash == std::string::npos) ? path.size() : slash;
        const std::string sub = path.substr(0, end);
        if (!sub.empty() && !isDirectory(sub)) {
            if (::mkdir(sub.c_str(), 0777) != 0 && errno != EEXIST) {
                outErrno = errno;
            }
        }
        if (slash == std::string::npos) break;
        pos = slash + 1;
    }
    if (isDirectory(path)) {
        outErrno = 0;
        return true;
    }
    if (outErrno == 0) outErrno = errno;
    return false;
}

// v0.48: installation diagnostics — so a failed install can be explained.
static bool gRootActive = false;
static void diagLine(const std::string& line) {
    const int fd = ::open("/data/AddonStation/install_diag.txt",
                          O_WRONLY | O_CREAT | O_APPEND, 0666);
    if (fd >= 0) {
        const std::string t = line + "\n";
        ssize_t r = ::write(fd, t.data(), t.size());
        (void)r;
        ::close(fd);
    }
}

// bounded recursive search for a directory by name (used to locate com.mojang)
static std::string findDirNamed(const std::string& root, const std::string& name, int depth) {
    if (depth < 0) return std::string();
    DIR* dir = ::opendir(root.c_str());
    if (!dir) return std::string();
    std::string found;
    while (dirent* ent = ::readdir(dir)) {
        const std::string n(ent->d_name);
        if (n.empty() || n == "." || n == "..") continue;
        const std::string full = root + "/" + n;
        if (n == name && isDirectory(full)) { found = full; break; }
        struct stat st {};
        if (depth > 0 && ::stat(full.c_str(), &st) == 0 && S_ISDIR(st.st_mode)) {
            found = findDirNamed(full, name, depth - 1);
            if (!found.empty()) break;
        }
    }
    ::closedir(dir);
    return found;
}

void removeTreeRecursive(const std::string& path) {
    struct stat st {};
    if (::stat(path.c_str(), &st) != 0) return;
    if (!S_ISDIR(st.st_mode)) {
        ::unlink(path.c_str());
        return;
    }

    DIR* dir = ::opendir(path.c_str());
    if (dir) {
        while (dirent* entry = ::readdir(dir)) {
            const std::string name(entry->d_name);
            if (name.empty() || name == "." || name == "..") continue;
            removeTreeRecursive(path + "/" + name);
        }
        ::closedir(dir);
    }
    ::rmdir(path.c_str());
}

void cleanPreviousPackFolders(const std::string& destinationFolder) {
    removeTreeRecursive(destinationFolder);
    const std::size_t slash = destinationFolder.rfind('/');
    if (slash == std::string::npos) return;
    const std::string parentDir = destinationFolder.substr(0, slash);
    const std::string baseName = destinationFolder.substr(slash + 1);

    // Remove older versions of the SAME pack: siblings whose name equals the
    // product prefix (first whitespace token) or starts with "<prefix> -_.".
    const std::size_t space = baseName.find(' ');
    const std::string prefix = (space == std::string::npos) ? baseName : baseName.substr(0, space);
    if (prefix.empty()) return;

    DIR* dir = ::opendir(parentDir.c_str());
    if (!dir) return;
    while (dirent* entry = ::readdir(dir)) {
        const std::string name(entry->d_name);
        if (name == baseName || name == prefix) {
            removeTreeRecursive(parentDir + "/" + name);
            continue;
        }
        if (name.size() > prefix.size() && name.rfind(prefix, 0) == 0) {
            const char sep = name[prefix.size()];
            if (sep == ' ' || sep == '-' || sep == '_' || sep == '.') {
                removeTreeRecursive(parentDir + "/" + name);
            }
        }
    }
    ::closedir(dir);
}

std::string sanitizeTargetCategory(const std::string& category) {
    if (category == "behavior_packs" || category == "behavior_pack" || category == "data") {
        return "behavior_packs";
    }
    if (category == "minecraftWorlds" || category == "world" || category == "worlds" || category == "map") {
        return "minecraftWorlds";
    }
    return "resource_packs";
}

// Auto-detects whether an unpacked folder is a world, behavior pack, or resource pack
std::string detectCategoryFromExtractedDir(const std::string& dirPath, const std::string& fallback) {
    if (isRegularFile(dirPath + "/level.dat") || isRegularFile(dirPath + "/levelname.txt")) {
        return "minecraftWorlds";
    }
    const std::string manifestPath = dirPath + "/manifest.json";
    if (isRegularFile(manifestPath)) {
        std::ifstream f(manifestPath);
        std::string content((std::istreambuf_iterator<char>(f)), std::istreambuf_iterator<char>());
        if (content.find("\"data\"") != std::string::npos &&
            content.find("\"resources\"") == std::string::npos) {
            return "behavior_packs";
        }
        if (content.find("\"world_template\"") != std::string::npos) {
            return "minecraftWorlds";
        }
        return "resource_packs";
    }
    return sanitizeTargetCategory(fallback);
}

#ifdef __ORBIS__
struct ScopedRootJailbreak {
    jbc_cred origCred{};
    bool active = false;

    ScopedRootJailbreak() {
        if (jbc_get_cred(&origCred) == 0) {
            jbc_cred rootCred = origCred;
            if (jbc_jailbreak_cred(&rootCred) == 0 && jbc_set_cred(&rootCred) == 0) {
                active = true;
            }
        }
        gRootActive = active;
    }

    ~ScopedRootJailbreak() {
        if (active) {
            jbc_set_cred(&origCred);
        }
    }
};

void sendPs4Notification(const std::string& text) {
    OrbisNotificationRequest req{};
    req.type = static_cast<OrbisNotificationRequestType>(0);
    req.targetId = -1;
    req.useIconImageUri = 1;
    std::snprintf(req.iconUri, sizeof(req.iconUri), "cxml://psnotification/tex_icon_system");
    std::snprintf(req.message, sizeof(req.message), "%s", text.c_str());
    sceKernelSendNotificationRequest(0, &req, sizeof(req), 0);
}

int32_t getActivePs4UserId() {
    int32_t userId = 0;
    sceUserServiceInitialize(nullptr);
    if (sceUserServiceGetForegroundUser(&userId) != 0 || userId <= 0) {
        sceUserServiceGetInitialUser(&userId);
    }
    return userId;
}

bool fileContainsCaseInsensitive(const std::string& path, const char* needle) {
    std::ifstream f(path, std::ios::binary);
    if (!f) return false;
    std::string buf((std::istreambuf_iterator<char>(f)), std::istreambuf_iterator<char>());
    std::string lowerBuf = buf;
    std::transform(lowerBuf.begin(), lowerBuf.end(), lowerBuf.begin(),
                   [](unsigned char c) { return static_cast<char>(std::tolower(c)); });
    std::string lowerNeedle(needle);
    std::transform(lowerNeedle.begin(), lowerNeedle.end(), lowerNeedle.begin(),
                   [](unsigned char c) { return static_cast<char>(std::tolower(c)); });
    return lowerBuf.find(lowerNeedle) != std::string::npos;
}

// Dynamically discovers installed Minecraft Title IDs (CUSAxxxxx) on the PS4 HDD or external USB.
std::vector<std::string> detectInstalledMinecraftTitleIds() {
    std::vector<std::string> found;
    auto addUnique = [&](const std::string& tid) {
        if (tid.size() == 9 && tid.rfind("CUSA", 0) == 0) {
            if (std::find(found.begin(), found.end(), tid) == found.end()) {
                found.push_back(tid);
            }
        }
    };

    // 1. Check known Minecraft PS4 regional editions if installed on internal or external storage
    const char* knownMinecraftIds[] = {
        "CUSA00265", // Europe
        "CUSA00744", // USA
        "CUSA00283", // Japan
        "CUSA00572", // Asia
        "CUSA04919", // Retail / Bedrock Edition
        "CUSA05624", // Bedrock Edition
    };
    const char* appRoots[] = {
        "/user/app",
        "/mnt/ext0/user/app",
        "/mnt/ext1/user/app",
        "/system_data/priv/appmeta",
        "/user/appmeta",
    };
    for (const char* tid : knownMinecraftIds) {
        for (const char* root : appRoots) {
            if (isDirectory(std::string(root) + "/" + tid)) {
                addUnique(tid);
                break;
            }
        }
    }

    // 2. Scan appmeta param.sfo files for any other CUSAxxxxx whose title contains "Minecraft"
    const char* metaRoots[] = {"/system_data/priv/appmeta", "/user/appmeta"};
    for (const char* mroot : metaRoots) {
        DIR* dir = ::opendir(mroot);
        if (!dir) continue;
        while (dirent* ent = ::readdir(dir)) {
            const std::string tid(ent->d_name);
            if (tid.rfind("CUSA", 0) != 0) continue;
            const std::string sfoPath = std::string(mroot) + "/" + tid + "/param.sfo";
            if (isRegularFile(sfoPath) && fileContainsCaseInsensitive(sfoPath, "minecraft")) {
                addUnique(tid);
            }
        }
        ::closedir(dir);
    }

    // 3. Fallback list so launch still attempts standard regional IDs if appmeta wasn't readable
    for (const char* tid : knownMinecraftIds) {
        addUnique(tid);
    }
    return found;
}

bool launchMinecraftImpl() {
    const int32_t userId = getActivePs4UserId();

    LncAppParam param{};
    param.size = sizeof(LncAppParam);
    param.user_id = static_cast<uint32_t>(userId);
    param.app_opt = 0;
    param.crash_report = 0;
    param.LaunchAppCheck_flag = LaunchApp_None;
    const char* argv[] = {nullptr};

    const std::vector<std::string> candidates = detectInstalledMinecraftTitleIds();

    using LncInitFn = int (*)(void);
    using LncLaunchFn = int32_t (*)(const char*, const char**, LncAppParam*);
    const int32_t sysMod = static_cast<int32_t>(
        sceKernelLoadStartModule("/system/common/lib/libSceSystemService.sprx", 0, nullptr, 0, nullptr, nullptr));
    if (sysMod >= 0) {
        void* initSym = nullptr;
        void* launchSym = nullptr;
        void* sysLaunchSym = nullptr;
        if (sceKernelDlsym(sysMod, "sceLncUtilInitialize", &initSym) == 0 && initSym) {
            reinterpret_cast<LncInitFn>(initSym)();
        }
        if (sceKernelDlsym(sysMod, "sceLncUtilLaunchApp", &launchSym) == 0 && launchSym) {
            auto fn = reinterpret_cast<LncLaunchFn>(launchSym);
            for (const std::string& tid : candidates) {
                if (fn(tid.c_str(), argv, &param) >= 0) return true;
            }
        }
        if (sceKernelDlsym(sysMod, "sceSystemServiceLaunchApp", &sysLaunchSym) == 0 && sysLaunchSym) {
            auto fn = reinterpret_cast<LncLaunchFn>(sysLaunchSym);
            for (const std::string& tid : candidates) {
                if (fn(tid.c_str(), argv, &param) >= 0) return true;
            }
        }
    }
    return false;
}
#else
void sendPs4Notification(const std::string&) {}
int32_t getActivePs4UserId() { return 0; }
bool launchMinecraftImpl() { return true; }
#endif

bool freadExact(std::FILE* fp, void* dst, std::size_t len, int& outErrno) {
    char* ptr = static_cast<char*>(dst);
    std::size_t remaining = len;
    while (remaining > 0) {
        const std::size_t r = std::fread(ptr, 1, remaining, fp);
        if (r == 0) {
            outErrno = errno != 0 ? errno : EIO;
            return false;
        }
        ptr += r;
        remaining -= r;
    }
    return true;
}

bool writeExact(int fd, const void* src, std::size_t len, int& outErrno) {
    const char* ptr = static_cast<const char*>(src);
    std::size_t remaining = len;
    while (remaining > 0) {
        const ssize_t w = ::write(fd, ptr, remaining);
        if (w < 0) {
            if (errno == EINTR) continue;
            outErrno = errno;
            return false;
        }
        if (w == 0) {
            outErrno = EIO;
            return false;
        }
        ptr += w;
        remaining -= static_cast<std::size_t>(w);
    }
    return true;
}

// Dynamically discovers ANY mounted Minecraft com.mojang directory across any CUSAxxxxx and any userYYYY
// WITHOUT creating com.mojang if Minecraft is closed.
std::string findMountedMojangDir(const std::string& preferredMojangDir) {
    if (!preferredMojangDir.empty() && isDirectory(preferredMojangDir)) {
        return preferredMojangDir;
    }

    const int32_t fgUserId = getActivePs4UserId();
    const std::string preferredUserFolder =
        (fgUserId > 0) ? ("user" + std::to_string(fgUserId)) : "";

    const char* roots[] = {"/app_tmp", "/user/app_tmp", "/app_temp"};

    // Pass 1: if we know the foreground PS4 user ID, check /app_tmp/CUSA*/user<fgUserId>/games/com.mojang first
    if (!preferredUserFolder.empty()) {
        for (const char* root : roots) {
            DIR* rdir = ::opendir(root);
            if (!rdir) continue;
            while (dirent* rent = ::readdir(rdir)) {
                const std::string cusa(rent->d_name);
                if (cusa.rfind("CUSA", 0) != 0) continue;
                const std::string candidate =
                    std::string(root) + "/" + cusa + "/" + preferredUserFolder + "/games/com.mojang";
                if (isDirectory(candidate)) {
                    ::closedir(rdir);
                    return candidate;
                }
            }
            ::closedir(rdir);
        }
    }

    // Pass 2: wildcard scan across ALL /app_tmp/CUSA*/<any_user_dir>/games/com.mojang
    for (const char* root : roots) {
        DIR* rdir = ::opendir(root);
        if (!rdir) continue;
        while (dirent* rent = ::readdir(rdir)) {
            const std::string cusa(rent->d_name);
            if (cusa.rfind("CUSA", 0) != 0) continue;
            const std::string cusaPath = std::string(root) + "/" + cusa;
            DIR* cdir = ::opendir(cusaPath.c_str());
            if (!cdir) continue;
            while (dirent* uent = ::readdir(cdir)) {
                const std::string userDir(uent->d_name);
                if (userDir.empty() || userDir == "." || userDir == "..") continue;
                const std::string mojang = cusaPath + "/" + userDir + "/games/com.mojang";
                if (isDirectory(mojang)) {
                    ::closedir(cdir);
                    ::closedir(rdir);
                    return mojang;
                }
            }
            ::closedir(cdir);
        }
        ::closedir(rdir);
    }

    // Pass 3 (v0.41): native sandbox mounts — a suspended game keeps its sandbox
    // at /mnt/sandbox/<id>_000; check the layout the FTP guides use.
    {
        DIR* sdir = ::opendir("/mnt/sandbox");
        if (sdir) {
            while (dirent* sent = ::readdir(sdir)) {
                const std::string sb(sent->d_name);
                if (sb.empty() || sb == "." || sb == "..") continue;
                const std::string cands[3] = {
                    "/mnt/sandbox/" + sb + "/user/games/com.mojang",
                    "/mnt/sandbox/" + sb + "/savedata0/games/com.mojang",
                    "/mnt/sandbox/" + sb + "/app0/user/games/com.mojang",
                };
                for (const std::string& c : cands) {
                    if (isDirectory(c)) {
                        ::closedir(sdir);
                        return c;
                    }
                }
            }
            ::closedir(sdir);
        }
    }

    // Pass 4 (v0.41): with jailbreak root the persistent savedata store is
    // directly readable — /user/home/<uid>/savedata/<entry>/games/com.mojang
    // (the same data the game mounts while it runs). This lets the install
    // complete even while Minecraft is CLOSED.
    {
        DIR* hdir = ::opendir("/user/home");
        if (hdir) {
            while (dirent* hent = ::readdir(hdir)) {
                const std::string hn(hent->d_name);
                if (hn.empty() || hn == "." || hn == "..") continue;
                const std::string saveRoot = "/user/home/" + hn + "/savedata";
                DIR* edir = ::opendir(saveRoot.c_str());
                if (!edir) continue;
                while (dirent* eent = ::readdir(edir)) {
                    const std::string en(eent->d_name);
                    if (en.empty() || en == "." || en == "..") continue;
                    const std::string entry = saveRoot + "/" + en;
                    // v0.48: direct candidates + "entry has minecraftWorlds -> it IS the
                    // packs home" rule + bounded deep search for a com.mojang dir.
                    const std::string cands[3] = {
                        entry + "/games/com.mojang",
                        entry + "/com.mojang",
                        (isDirectory(entry + "/minecraftWorlds") ? entry : std::string()),
                    };
                    for (const std::string& c : cands) {
                        if (!c.empty() && isDirectory(c)) {
                            ::closedir(edir);
                            ::closedir(hdir);
                            diagLine("mojang found: " + c);
                            return c;
                        }
                    }
                    const std::string deep = findDirNamed(entry, "com.mojang", 3);
                    if (!deep.empty()) {
                        ::closedir(edir);
                        ::closedir(hdir);
                        diagLine("mojang found (deep): " + deep);
                        return deep;
                    }
                }
                ::closedir(edir);
            }
            ::closedir(hdir);
        }
    }

    return "";
}

std::string resolveActualDestinationFolder(const std::string& requestedDest) {
    // If requestedDest already points inside an existing com.mojang, keep it
    const std::string marker = "/games/com.mojang/";
    const std::size_t pos = requestedDest.find(marker);
    if (pos != std::string::npos) {
        const std::string reqMojang = requestedDest.substr(0, pos + marker.size() - 1);
        if (isDirectory(reqMojang)) {
            return requestedDest;
        }
        const std::string suffix = requestedDest.substr(pos + marker.size());
        const std::string mounted = findMountedMojangDir("");
        if (!mounted.empty()) {
            return mounted + "/" + suffix;
        }
    }
    return requestedDest;
}

void recordInstalledAddon(const std::string& stageDir, const std::string& addonName) {
    int err = 0;
    ensureDirectory(stageDir, err);
    const std::string jsonPath = stageDir + "/installed.json";
    std::string existing;
    if (isRegularFile(jsonPath)) {
        std::ifstream in(jsonPath);
        existing.assign((std::istreambuf_iterator<char>(in)), std::istreambuf_iterator<char>());
    }
    const std::string token = "\"" + addonName + "\"";
    if (existing.find(token) != std::string::npos) {
        return;
    }
    if (existing.empty() || existing.find('[') == std::string::npos) {
        existing = "[\n  " + token + "\n]\n";
    } else {
        const std::size_t closeBracket = existing.rfind(']');
        if (closeBracket != std::string::npos) {
            existing = existing.substr(0, closeBracket) + ",\n  " + token + "\n]\n";
        }
    }
    const int fd = ::open(jsonPath.c_str(), O_WRONLY | O_CREAT | O_TRUNC, 0666);
    if (fd >= 0) {
        writeExact(fd, existing.data(), existing.size(), err);
        ::close(fd);
    }
}

bool copyFromOpenStream(std::FILE* inFp,
                        const std::string& dstRoot,
                        CopyResult& result,
                        const ProgressCallback& onProgress) {
    int err = 0;
    cleanPreviousPackFolders(dstRoot);

    if (!ensureDirectory(dstRoot, err)) {
        result.errorCode = err;
        result.message = "MKDIR FAILED (" + std::to_string(err) + ")";
        return false;
    }

    char magic[8] = {};
    if (!freadExact(inFp, magic, 8, err) || std::memcmp(magic, "PVRAW001", 8) != 0) {
        result.errorCode = err != 0 ? err : EINVAL;
        result.message = "HEADER FAILED";
        return false;
    }

    std::uint32_t dirCount = 0;
    if (!freadExact(inFp, &dirCount, sizeof(dirCount), err)) {
        result.errorCode = err;
        result.message = "READ DIRCOUNT FAILED";
        return false;
    }

    for (std::uint32_t i = 0; i < dirCount; ++i) {
        std::uint16_t pathLen = 0;
        if (!freadExact(inFp, &pathLen, sizeof(pathLen), err)) {
            result.errorCode = err;
            result.message = "READ DIRLEN FAILED";
            return false;
        }
        std::string relDir(pathLen, '\0');
        if (pathLen > 0 && !freadExact(inFp, &relDir[0], pathLen, err)) {
            result.errorCode = err;
            result.message = "READ DIRPATH FAILED";
            return false;
        }
        const std::string fullDir = dstRoot + "/" + relDir;
        if (::mkdir(fullDir.c_str(), 0777) != 0 && errno != EEXIST) {
            if (!ensureDirectory(fullDir, err)) {
                result.errorCode = err;
                result.message = "MKDIR FAILED (" + std::to_string(err) + ")";
                return false;
            }
        }
    }

    std::uint32_t fileCount = 0;
    if (!freadExact(inFp, &fileCount, sizeof(fileCount), err)) {
        result.errorCode = err;
        result.message = "READ FILECOUNT FAILED";
        return false;
    }

    int lastReportedPct = -1;
    std::vector<char> buffer(32 * 1024);
    for (std::uint32_t i = 0; i < fileCount; ++i) {
        std::uint16_t pathLen = 0;
        std::uint64_t fileSize = 0;
        if (!freadExact(inFp, &pathLen, sizeof(pathLen), err) ||
            !freadExact(inFp, &fileSize, sizeof(fileSize), err)) {
            result.errorCode = err;
            result.message = "READ META ERR (" + std::to_string(err) + ")";
            return false;
        }
        std::string relFile(pathLen, '\0');
        if (pathLen > 0 && !freadExact(inFp, &relFile[0], pathLen, err)) {
            result.errorCode = err;
            result.message = "READ PATH ERR (" + std::to_string(err) + ")";
            return false;
        }

        const std::string fullFile = dstRoot + "/" + relFile;
        int outFd = ::open(fullFile.c_str(), O_WRONLY | O_CREAT | O_TRUNC, 0666);
        if (outFd < 0) {
            const std::size_t slash = fullFile.rfind('/');
            if (slash != std::string::npos) {
                ensureDirectory(fullFile.substr(0, slash), err);
            }
            outFd = ::open(fullFile.c_str(), O_WRONLY | O_CREAT | O_TRUNC, 0666);
            if (outFd < 0) {
                err = errno;
                result.errorCode = err;
                result.message = "OPEN ERR (" + std::to_string(err) + ") AT " +
                                 std::to_string(result.filesCopied);
                return false;
            }
        }

        std::uint64_t remaining = fileSize;
        while (remaining > 0) {
            const std::size_t chunk = static_cast<std::size_t>(
                std::min<std::uint64_t>(remaining, buffer.size()));
            if (!freadExact(inFp, buffer.data(), chunk, err)) {
                ::close(outFd);
                result.errorCode = err;
                result.message = "READ ERR (" + std::to_string(err) + ") AT " +
                                 std::to_string(result.filesCopied);
                return false;
            }
            if (!writeExact(outFd, buffer.data(), chunk, err)) {
                ::close(outFd);
                result.errorCode = err;
                result.message = "WRITE ERR (" + std::to_string(err) + ") AT " +
                                 std::to_string(result.filesCopied);
                return false;
            }
            remaining -= chunk;
            result.bytesCopied += chunk;
        }

        ::close(outFd);
        ++result.filesCopied;

        if (onProgress && fileCount > 0) {
            const int pct = static_cast<int>((static_cast<std::uint64_t>(i + 1) * 100ULL) / fileCount);
            if (pct != lastReportedPct && (pct % 4 == 0 || i + 1 == fileCount)) {
                lastReportedPct = pct;
                onProgress(InstallPhase::Installing, pct);
            }
        }
    }

    return true;
}

bool stageStreamToFile(std::FILE* inFp,
                       const std::string& stagedFilePath,
                       int& outErrno,
                       const ProgressCallback& onProgress) {
    outErrno = 0;
    struct stat st {};
    std::uint64_t totalBytes = 0;
    if (::fstat(fileno(inFp), &st) == 0 && st.st_size > 0) {
        totalBytes = static_cast<std::uint64_t>(st.st_size);
    }

    const int outFd = ::open(stagedFilePath.c_str(), O_WRONLY | O_CREAT | O_TRUNC, 0666);
    if (outFd < 0) {
        outErrno = errno;
        return false;
    }
    std::vector<char> buf(64 * 1024);
    std::uint64_t written = 0;
    int lastPct = -1;
    while (true) {
        const std::size_t r = std::fread(buf.data(), 1, buf.size(), inFp);
        if (r > 0) {
            if (!writeExact(outFd, buf.data(), r, outErrno)) {
                ::close(outFd);
                return false;
            }
            written += r;
            if (onProgress && totalBytes > 0) {
                const int pct = static_cast<int>(std::min<std::uint64_t>(100ULL, (written * 100ULL) / totalBytes));
                if (pct != lastPct && (pct % 5 == 0 || pct == 100)) {
                    lastPct = pct;
                    onProgress(InstallPhase::Downloading, pct);
                }
            }
        }
        if (r < buf.size()) {
            if (std::ferror(inFp)) {
                outErrno = errno != 0 ? errno : EIO;
                ::close(outFd);
                return false;
            }
            break;
        }
    }
    ::close(outFd);
    return true;
}

bool copySingleFile(const std::string& srcPath,
                    const std::string& dstPath,
                    std::uint64_t& bytesCopied,
                    int& outErrno) {
    const int inFd = ::open(srcPath.c_str(), O_RDONLY);
    if (inFd < 0) {
        outErrno = errno;
        return false;
    }

    const int outFd = ::open(dstPath.c_str(), O_WRONLY | O_CREAT | O_TRUNC, 0666);
    if (outFd < 0) {
        outErrno = errno;
        ::close(inFd);
        return false;
    }

    std::vector<char> buffer(32 * 1024);
    while (true) {
        const ssize_t readBytes = ::read(inFd, buffer.data(), buffer.size());
        if (readBytes < 0) {
            if (errno == EINTR) continue;
            outErrno = errno;
            ::close(inFd);
            ::close(outFd);
            return false;
        }
        if (readBytes == 0) break;

        if (!writeExact(outFd, buffer.data(), static_cast<std::size_t>(readBytes), outErrno)) {
            ::close(inFd);
            ::close(outFd);
            return false;
        }
        bytesCopied += static_cast<std::uint64_t>(readBytes);
    }

    ::close(inFd);
    ::close(outFd);
    return true;
}

bool copyTreeRecursive(const std::string& srcDir,
                       const std::string& dstDir,
                       CopyResult& result) {
    int err = 0;
    if (!ensureDirectory(dstDir, err)) {
        result.errorCode = err;
        result.message = "MKDIR FAILED (" + std::to_string(err) + ")";
        return false;
    }

    DIR* dir = ::opendir(srcDir.c_str());
    if (!dir) {
        result.errorCode = errno;
        result.message = "OPENDIR FAILED (" + std::to_string(result.errorCode) + ")";
        return false;
    }

    while (dirent* entry = ::readdir(dir)) {
        const std::string name(entry->d_name);
        if (name.empty() || name == "." || name == "..") continue;

        const std::string srcChild = srcDir + "/" + name;
        const std::string dstChild = dstDir + "/" + name;

        struct stat st {};
        if (::stat(srcChild.c_str(), &st) != 0) {
            result.errorCode = errno;
            result.message = "STAT FAILED (" + std::to_string(result.errorCode) + ")";
            ::closedir(dir);
            return false;
        }

        if (S_ISDIR(st.st_mode)) {
            if (!copyTreeRecursive(srcChild, dstChild, result)) {
                ::closedir(dir);
                return false;
            }
        } else if (S_ISREG(st.st_mode)) {
            if (!copySingleFile(srcChild, dstChild, result.bytesCopied, err)) {
                result.errorCode = err;
                result.message = "COPY FAILED (" + std::to_string(err) + ")";
                ::closedir(dir);
                return false;
            }
            ++result.filesCopied;
        }
    }

    ::closedir(dir);
    return true;
}

} // namespace

// عام (external linkage) — يُستدعى من main.cpp لزر «فتح Minecraft» (△).
bool launchMinecraftOnPs4() { return launchMinecraftImpl(); }

bool loadAutoLaunchSetting(const std::string& configPath) {
    std::FILE* fp = std::fopen(configPath.c_str(), "rb");
    if (!fp) return false;   // الافتراضي: إيقاف (اللعبة لا تُطلق تلقائياً)
    char v = '0';
    if (std::fread(&v, 1, 1, fp) != 1) v = '0';
    std::fclose(fp);
    return v == '1';
}

bool saveAutoLaunchSetting(bool enabled, const std::string& configPath) {
    const std::string parent = configPath.substr(0, configPath.find_last_of('/'));
    if (!parent.empty()) {
        int err = 0;
        ensureDirectory(parent, err);
    }
    std::FILE* fp = std::fopen(configPath.c_str(), "wb");
    if (!fp) return false;
    const char v = enabled ? '1' : '0';
    const bool ok = std::fwrite(&v, 1, 1, fp) == 1;
    std::fclose(fp);
    return ok;
}

std::string formatEnjoyNotification(const std::string& addonName, const std::string& langCode) {
    if (langCode == "ar") {
        return "تم تثبيت " + addonName + " — استمتع!";
    }
    if (langCode == "fr") {
        return addonName + " installé — Profitez-en !";
    }
    if (langCode == "tr") {
        return addonName + " yüklendi — Keyfini çıkarın!";
    }
    if (langCode == "es") {
        return addonName + " instalado — ¡Disfruta!";
    }
    if (langCode == "ru") {
        return addonName + " установлен — Приятной игры!";
    }
    if (langCode == "pt") {
        return addonName + " instalado — Aproveite!";
    }
    if (langCode == "zh") {
        return addonName + " 已安装 — 尽情畅玩吧！";
    }
    return addonName + " installed — Enjoy!";
}

std::string formatQueuedMessage(const std::string& langCode) {
    if (langCode == "ar") return "جاهز — شغّل Minecraft ثم اضغط زر PS وعُد للتطبيق ليُثبَّت تلقائياً";
    if (langCode == "fr") return "Prêt — lancez Minecraft, appuyez sur PS et revenez ici pour installer";
    if (langCode == "tr") return "Hazır — Minecraft'ı başlat, PS'ye bas ve kurulum için buraya dön";
    if (langCode == "es") return "Listo — inicia Minecraft, pulsa PS y vuelve aquí para instalar";
    if (langCode == "ru") return "Готово — запустите Minecraft, нажмите PS и вернитесь сюда для установки";
    if (langCode == "pt") return "Pronto — inicie Minecraft, aperte PS e volte aqui para instalar";
    if (langCode == "zh") return "就绪 — 启动 Minecraft，按 PS 键回到这里完成安装";
    return "Ready — launch Minecraft, press PS and return here to finish installing";
}

std::string formatInstallPhaseLabel(InstallPhase phase, const std::string& langCode) {
    switch (phase) {
        case InstallPhase::Downloading:
            if (langCode == "ar") return "جارٍ التنزيل...";
            if (langCode == "fr") return "Téléchargement...";
            if (langCode == "tr") return "İndiriliyor...";
            if (langCode == "es") return "Descargando...";
            if (langCode == "ru") return "Загрузка...";
            if (langCode == "pt") return "Baixando...";
            if (langCode == "zh") return "下载中...";
            return "Downloading...";
        case InstallPhase::Installing:
            if (langCode == "ar") return "جارٍ التثبيت...";
            if (langCode == "fr") return "Installation...";
            if (langCode == "tr") return "Yükleniyor...";
            if (langCode == "es") return "Instalando...";
            if (langCode == "ru") return "Установка...";
            if (langCode == "pt") return "Instalando...";
            if (langCode == "zh") return "安装中...";
            return "Installing...";
        case InstallPhase::Installed:
            if (langCode == "ar") return "تم التثبيت ✓";
            if (langCode == "fr") return "Installé ✓";
            if (langCode == "tr") return "Yüklendi ✓";
            if (langCode == "es") return "Instalado ✓";
            if (langCode == "ru") return "Установлено ✓";
            if (langCode == "pt") return "Instalado ✓";
            if (langCode == "zh") return "已安装 ✓";
            return "Installed ✓";
        case InstallPhase::Failed:
            if (langCode == "ar") return "إعادة المحاولة";
            if (langCode == "fr") return "Réessayer";
            if (langCode == "tr") return "Tekrar dene";
            if (langCode == "es") return "Reintentar";
            if (langCode == "ru") return "Повторить";
            if (langCode == "pt") return "Tentar novamente";
            if (langCode == "zh") return "重试";
            return "Retry";
    }
    return "Download";
}

bool isAddonMarkedInstalled(const std::string& addonName, const std::string& stageDir) {
#ifdef __ORBIS__
    ScopedRootJailbreak jailbreakGuard;
#endif
    const std::string jsonPath = stageDir + "/installed.json";
    if (!isRegularFile(jsonPath)) return false;
    std::ifstream in(jsonPath);
    std::string content((std::istreambuf_iterator<char>(in)), std::istreambuf_iterator<char>());
    return content.find("\"" + addonName + "\"") != std::string::npos;
}

CopyResult copyAddonFolder(const std::string& sourceFolder,
                           const std::string& destinationFolder) {
    CopyResult result;

    std::string rawCandidate = sourceFolder;
    if (!isRegularFile(rawCandidate)) {
        if (isRegularFile(sourceFolder + "/folder.raw")) {
            rawCandidate = sourceFolder + "/folder.raw";
        } else if (isRegularFile("/app0/assets/folder.raw")) {
            rawCandidate = "/app0/assets/folder.raw";
        }
    }

    if (isRegularFile(rawCandidate)) {
        std::FILE* inFp = std::fopen(rawCandidate.c_str(), "rb");
        if (!inFp) {
            result.errorCode = errno;
            result.message = "OPEN FAILED (" + std::to_string(result.errorCode) + ")";
            return result;
        }

        std::vector<char> ioBuf(64 * 1024);
        std::setvbuf(inFp, ioBuf.data(), _IOFBF, ioBuf.size());

#ifdef __ORBIS__
        ScopedRootJailbreak jailbreakGuard;
#endif
        const std::string actualDest = resolveActualDestinationFolder(destinationFolder);
        const bool ok = copyFromOpenStream(inFp, actualDest, result, nullptr);
        std::fclose(inFp);
        if (!ok) {
            return result;
        }
        result.ok = true;
        result.message = "DONE";
        return result;
    }

    if (!isDirectory(sourceFolder)) {
        result.errorCode = errno;
        result.message = "SOURCE MISSING (" + std::to_string(result.errorCode) + ")";
        return result;
    }

    cleanPreviousPackFolders(destinationFolder);
    if (!copyTreeRecursive(sourceFolder, destinationFolder, result)) {
        return result;
    }

    result.ok = true;
    result.message = "DONE";
    return result;
}

CopyResult installOrQueueAddon(const std::string& sourceRawPath,
                               const std::string& addonFolderName,
                               const std::string& stageDir,
                               const std::string& preferredMojangDir,
                               const std::string& targetCategory,
                               const std::string& langCode,
                               ProgressCallback onProgress,
                               bool autoLaunchMinecraft) {
    CopyResult result;
    result.targetCategory = sanitizeTargetCategory(targetCategory);

    if (onProgress) {
        onProgress(InstallPhase::Downloading, 10);
    }

    std::FILE* inFp = std::fopen(sourceRawPath.c_str(), "rb");
    if (!inFp) {
        result.errorCode = errno;
        result.message = formatInstallPhaseLabel(InstallPhase::Failed, langCode);
        return result;
    }

    std::vector<char> ioBuf(64 * 1024);
    std::setvbuf(inFp, ioBuf.data(), _IOFBF, ioBuf.size());

#ifdef __ORBIS__
    ScopedRootJailbreak jailbreakGuard;
#endif

    int err = 0;
    if (!ensureDirectory(stageDir, err)) {
        std::fclose(inFp);
        result.errorCode = err;
        result.message = formatInstallPhaseLabel(InstallPhase::Failed, langCode);
        return result;
    }

    const std::string stagedRawPath = stageDir + "/" + addonFolderName + ".raw";
    const std::string pendingFlagPath = stageDir + "/" + addonFolderName + ".pending";

    // Dynamically check if ANY Minecraft CUSAxxxxx/userYYYY/games/com.mojang is mounted right now
    diagLine("--- install attempt: " + addonFolderName +
             " root=" + (gRootActive ? "ok" : "FAILED"));
    const std::string mountedMojang = findMountedMojangDir(preferredMojangDir);
    diagLine(std::string("scan result: ") + (mountedMojang.empty() ? "NONE (addon queued)" : mountedMojang));
    if (!mountedMojang.empty()) {
        result.detectedMojangDir = mountedMojang;
        if (onProgress) {
            onProgress(InstallPhase::Installing, 0);
        }
        const std::string targetPackDir =
            mountedMojang + "/" + result.targetCategory + "/" + addonFolderName;
        const bool ok = copyFromOpenStream(inFp, targetPackDir, result, onProgress);
        std::fclose(inFp);
        if (!ok) {
            return result;
        }

        // Verify if manifest.json / level.dat inside the unpacked pack belongs in another subfolder
        const std::string detectedCat = detectCategoryFromExtractedDir(targetPackDir, result.targetCategory);
        if (detectedCat != result.targetCategory) {
            const std::string correctedDir = mountedMojang + "/" + detectedCat + "/" + addonFolderName;
            ensureDirectory(mountedMojang + "/" + detectedCat, err);
            removeTreeRecursive(correctedDir);
            ::rename(targetPackDir.c_str(), correctedDir.c_str());
            result.targetCategory = detectedCat;
        }

        // Clean up any staged .raw and .pending files so PS4 HDD space is freed immediately!
        ::unlink(pendingFlagPath.c_str());
        ::unlink(stagedRawPath.c_str());
        // Also delete the downloaded source .raw itself when it lives in stageDir
        if (sourceRawPath.rfind(stageDir + "/", 0) == 0 && sourceRawPath != stagedRawPath) {
            ::unlink(sourceRawPath.c_str());
        }
        recordInstalledAddon(stageDir, addonFolderName);

        result.ok = true;
        result.queuedForMinecraft = false;
        result.message = formatInstallPhaseLabel(InstallPhase::Installed, langCode);
        if (onProgress) {
            onProgress(InstallPhase::Installed, 100);
        }
        sendPs4Notification(formatEnjoyNotification(addonFolderName, langCode));
        return result;
    }

    // Minecraft is CLOSED: stage the .raw file into /data/AddonStation/installed/
    if (!stageStreamToFile(inFp, stagedRawPath, err, onProgress)) {
        std::fclose(inFp);
        result.errorCode = err;
        result.message = formatInstallPhaseLabel(InstallPhase::Failed, langCode);
        return result;
    }
    std::fclose(inFp);

    if (onProgress) {
        onProgress(InstallPhase::Installing, 50);
    }

    // Store targetCategory + langCode inside the .pending metadata file
    const std::string metaLine = result.targetCategory + "|" + langCode;
    const int flagFd = ::open(pendingFlagPath.c_str(), O_WRONLY | O_CREAT | O_TRUNC, 0666);
    if (flagFd >= 0) {
        writeExact(flagFd, metaLine.data(), metaLine.size(), err);
        ::close(flagFd);
    }

    result.ok = true;
    result.queuedForMinecraft = true;
    result.message = formatQueuedMessage(langCode);
    // v0.13: إطلاق Minecraft تلقائياً اختياري — يُفعَّل من الإعدادات
    // «تشغيل Minecraft تلقائياً بعد التثبيت» (الافتراضي: إيقاف). حتى مع
    // الإيقاف يبقى الملف مؤجَّلاً في stageDir وتُكمله المزامنة التلقائية
    // (syncPendingAddonsIfMounted) في أول ظهور لمجلد com.mojang، سواء
    // شغّلت Minecraft بنفسك من △ في صفحة الإضافة أو يدوياً من القائمة.
    result.launchedMinecraft = autoLaunchMinecraft && launchMinecraftOnPs4();
    return result;
}

CopyResult syncPendingAddonsIfMounted(const std::string& stageDir,
                                      const std::string& preferredMojangDir,
                                      const std::string& langCode) {
    CopyResult result;
#ifdef __ORBIS__
    ScopedRootJailbreak jailbreakGuard;
#endif

    if (!isDirectory(stageDir)) {
        return result;
    }

    const std::string mountedMojang = findMountedMojangDir(preferredMojangDir);
    if (mountedMojang.empty()) {
        return result;
    }
    result.detectedMojangDir = mountedMojang;

    DIR* dir = ::opendir(stageDir.c_str());
    if (!dir) {
        return result;
    }

    std::vector<std::string> pendingAddons;
    while (dirent* ent = ::readdir(dir)) {
        const std::string fname(ent->d_name);
        if (fname.size() > 8 && fname.substr(fname.size() - 8) == ".pending") {
            pendingAddons.push_back(fname.substr(0, fname.size() - 8));
        }
    }
    ::closedir(dir);

    if (pendingAddons.empty()) {
        return result;
    }

    for (const std::string& addonName : pendingAddons) {
        const std::string rawPath = stageDir + "/" + addonName + ".raw";
        const std::string pendingPath = stageDir + "/" + addonName + ".pending";
        if (!isRegularFile(rawPath)) {
            ::unlink(pendingPath.c_str());
            continue;
        }

        std::string itemCategory = "resource_packs";
        std::string itemLang = langCode;
        {
            std::ifstream metaIn(pendingPath);
            std::string metaStr((std::istreambuf_iterator<char>(metaIn)), std::istreambuf_iterator<char>());
            const std::size_t bar = metaStr.find('|');
            if (bar != std::string::npos) {
                itemCategory = sanitizeTargetCategory(metaStr.substr(0, bar));
                const std::string savedLang = metaStr.substr(bar + 1);
                if (!savedLang.empty()) itemLang = savedLang;
            }
        }

        std::FILE* inFp = std::fopen(rawPath.c_str(), "rb");
        if (!inFp) continue;

        std::vector<char> ioBuf(64 * 1024);
        std::setvbuf(inFp, ioBuf.data(), _IOFBF, ioBuf.size());

        const std::string targetPackDir = mountedMojang + "/" + itemCategory + "/" + addonName;
        CopyResult singleRes;
        const bool ok = copyFromOpenStream(inFp, targetPackDir, singleRes, nullptr);
        std::fclose(inFp);
        if (ok) {
            const std::string detectedCat = detectCategoryFromExtractedDir(targetPackDir, itemCategory);
            if (detectedCat != itemCategory) {
                int err = 0;
                const std::string correctedDir = mountedMojang + "/" + detectedCat + "/" + addonName;
                ensureDirectory(mountedMojang + "/" + detectedCat, err);
                removeTreeRecursive(correctedDir);
                ::rename(targetPackDir.c_str(), correctedDir.c_str());
            }
            result.lastAddonName = addonName;

            // Delete both .pending and the large .raw file to free PS4 HDD space!
            ::unlink(pendingPath.c_str());
            ::unlink(rawPath.c_str());
            recordInstalledAddon(stageDir, addonName);

            result.ok = true;
            result.filesCopied += singleRes.filesCopied;
            result.bytesCopied += singleRes.bytesCopied;
            result.message = formatInstallPhaseLabel(InstallPhase::Installed, itemLang);
            sendPs4Notification(formatEnjoyNotification(addonName, itemLang));
        }
    }

    return result;
}

} // namespace pkgtest
