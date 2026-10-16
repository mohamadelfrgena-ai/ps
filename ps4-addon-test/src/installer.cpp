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
    DIR* dir = ::opendir(parentDir.c_str());
    if (!dir) return;
    while (dirent* entry = ::readdir(dir)) {
        const std::string name(entry->d_name);
        if (name.rfind("PrizmaVisuals", 0) == 0) {
            removeTreeRecursive(parentDir + "/" + name);
        }
    }
    ::closedir(dir);
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

bool launchMinecraftOnPs4() {
    int32_t userId = 0;
    sceUserServiceInitialize(nullptr);
    if (sceUserServiceGetForegroundUser(&userId) != 0 || userId <= 0) {
        sceUserServiceGetInitialUser(&userId);
    }

    LncAppParam param{};
    param.size = sizeof(LncAppParam);
    param.user_id = static_cast<uint32_t>(userId);
    param.app_opt = 0;
    param.crash_report = 0;
    param.LaunchAppCheck_flag = LaunchApp_None;
    const char* argv[] = {nullptr};

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
            if (fn("CUSA00265", argv, &param) >= 0) return true;
            if (fn("CUSA00744", argv, &param) >= 0) return true;
        }
        if (sceKernelDlsym(sysMod, "sceSystemServiceLaunchApp", &sysLaunchSym) == 0 && sysLaunchSym) {
            auto fn = reinterpret_cast<LncLaunchFn>(sysLaunchSym);
            if (fn("CUSA00265", argv, &param) >= 0) return true;
            if (fn("CUSA00744", argv, &param) >= 0) return true;
        }
    }
    return false;
}
#else
void sendPs4Notification(const std::string&) {}
bool launchMinecraftOnPs4() { return true; }
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

// Finds an actively mounted Minecraft com.mojang directory WITHOUT creating it if Minecraft is closed.
std::string findMountedMojangDir(const std::string& preferredMojangDir) {
    if (!preferredMojangDir.empty() && isDirectory(preferredMojangDir)) {
        return preferredMojangDir;
    }

    const char* exactCandidates[] = {
        "/app_tmp/CUSA00265/user497444003/games/com.mojang",
        "/user/app_tmp/CUSA00265/user497444003/games/com.mojang",
        "/app_temp/CUSA00265/user497444003/games/com.mojang",
    };
    for (const char* cand : exactCandidates) {
        if (isDirectory(cand)) {
            return cand;
        }
    }

    const char* roots[] = {"/app_tmp", "/user/app_tmp", "/app_temp"};
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

    return "";
}

std::string resolveActualDestinationFolder(const std::string& requestedDest) {
    const std::string exactParent = "/app_tmp/CUSA00265/user497444003/games/com.mojang";
    const std::string userParent = "/user/app_tmp/CUSA00265/user497444003/games/com.mojang";
    if (!isDirectory("/app_tmp/CUSA00265") && isDirectory("/user/app_tmp/CUSA00265")) {
        if (requestedDest.rfind(exactParent, 0) == 0) {
            return userParent + requestedDest.substr(exactParent.size());
        }
    }
    return requestedDest;
}

bool copyFromOpenStream(std::FILE* inFp,
                        const std::string& dstRoot,
                        CopyResult& result) {
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
    }

    return true;
}

bool stageStreamToFile(std::FILE* inFp, const std::string& stagedFilePath, int& outErrno) {
    outErrno = 0;
    const int outFd = ::open(stagedFilePath.c_str(), O_WRONLY | O_CREAT | O_TRUNC, 0666);
    if (outFd < 0) {
        outErrno = errno;
        return false;
    }
    std::vector<char> buf(64 * 1024);
    while (true) {
        const std::size_t r = std::fread(buf.data(), 1, buf.size(), inFp);
        if (r > 0) {
            if (!writeExact(outFd, buf.data(), r, outErrno)) {
                ::close(outFd);
                return false;
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
        const bool ok = copyFromOpenStream(inFp, actualDest, result);
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
                               const std::string& preferredMojangDir) {
    CopyResult result;

    std::FILE* inFp = std::fopen(sourceRawPath.c_str(), "rb");
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

    int err = 0;
    if (!ensureDirectory(stageDir, err)) {
        std::fclose(inFp);
        result.errorCode = err;
        result.message = "STAGE MKDIR ERR (" + std::to_string(err) + ")";
        return result;
    }

    const std::string stagedRawPath = stageDir + "/" + addonFolderName + ".raw";
    const std::string pendingFlagPath = stageDir + "/" + addonFolderName + ".pending";

    if (!isRegularFile(stagedRawPath)) {
        if (!stageStreamToFile(inFp, stagedRawPath, err)) {
            std::fclose(inFp);
            result.errorCode = err;
            result.message = "STAGE WRITE ERR (" + std::to_string(err) + ")";
            return result;
        }
        std::rewind(inFp);
    }

    // Check if Minecraft's com.mojang folder is currently mounted
    const std::string mountedMojang = findMountedMojangDir(preferredMojangDir);
    if (!mountedMojang.empty()) {
        const std::string targetPackDir =
            mountedMojang + "/resource_packs/" + addonFolderName;
        const bool ok = copyFromOpenStream(inFp, targetPackDir, result);
        std::fclose(inFp);
        if (!ok) {
            return result;
        }
        ::unlink(pendingFlagPath.c_str());
        result.ok = true;
        result.queuedForMinecraft = false;
        result.message = "DONE (" + std::to_string(result.filesCopied) + ")";
        sendPs4Notification("AddonStation: Installed " + addonFolderName + "!");
        return result;
    }

    std::fclose(inFp);

    // Minecraft is CLOSED: mark pending in stageDir and launch Minecraft automatically!
    const int flagFd = ::open(pendingFlagPath.c_str(), O_WRONLY | O_CREAT | O_TRUNC, 0666);
    if (flagFd >= 0) {
        const char* nameBytes = addonFolderName.c_str();
        writeExact(flagFd, nameBytes, addonFolderName.size(), err);
        ::close(flagFd);
    }

    result.ok = true;
    result.queuedForMinecraft = true;
    result.launchedMinecraft = launchMinecraftOnPs4();
    result.message = "QUEUED - OPENING MC";
    sendPs4Notification("AddonStation: Saved! Opening Minecraft...");
    return result;
}

CopyResult syncPendingAddonsIfMounted(const std::string& stageDir,
                                      const std::string& preferredMojangDir) {
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

        std::FILE* inFp = std::fopen(rawPath.c_str(), "rb");
        if (!inFp) continue;

        std::vector<char> ioBuf(64 * 1024);
        std::setvbuf(inFp, ioBuf.data(), _IOFBF, ioBuf.size());

        const std::string targetPackDir = mountedMojang + "/resource_packs/" + addonName;
        CopyResult singleRes;
        const bool ok = copyFromOpenStream(inFp, targetPackDir, singleRes);
        std::fclose(inFp);
        if (ok) {
            ::unlink(pendingPath.c_str());
            result.ok = true;
            result.filesCopied += singleRes.filesCopied;
            result.bytesCopied += singleRes.bytesCopied;
            result.message = "DONE (" + std::to_string(result.filesCopied) + ")";
            sendPs4Notification("AddonStation: Auto-Installed " + addonName + "!");
        }
    }

    return result;
}

} // namespace pkgtest
