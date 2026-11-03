#include "minecraft_storage.hpp"

#include <algorithm>
#include <cctype>
#include <dirent.h>
#include <fcntl.h>
#include <set>
#include <sys/stat.h>
#include <unistd.h>
#include <utility>

namespace ps4app::minecraft_storage {
namespace {

constexpr std::size_t kMaxPathBytes = 4096;

class ScopedFd {
public:
    explicit ScopedFd(int fd = -1) : fd_(fd) {}
    ~ScopedFd() { if (fd_ >= 0) ::close(fd_); }
    ScopedFd(const ScopedFd&) = delete;
    ScopedFd& operator=(const ScopedFd&) = delete;
    ScopedFd(ScopedFd&& other) noexcept : fd_(other.release()) {}
    ScopedFd& operator=(ScopedFd&& other) noexcept {
        if (this != &other) {
            if (fd_ >= 0) ::close(fd_);
            fd_ = other.release();
        }
        return *this;
    }
    int get() const { return fd_; }
    int release() {
        const int result = fd_;
        fd_ = -1;
        return result;
    }

private:
    int fd_;
};

int directoryOpenFlags() {
    int flags = O_RDONLY;
#ifdef O_DIRECTORY
    flags |= O_DIRECTORY;
#endif
#ifdef O_CLOEXEC
    flags |= O_CLOEXEC;
#endif
#ifdef O_NOFOLLOW
    flags |= O_NOFOLLOW;
#endif
    return flags;
}

bool isSafeAbsolutePath(const std::string& path) {
    if (path.empty() || path.size() > kMaxPathBytes || path.front() != '/' || path == "/" ||
        path.back() == '/' || path.find('\\') != std::string::npos ||
        path.find('\0') != std::string::npos) {
        return false;
    }

    std::size_t start = 1;
    while (start < path.size()) {
        const std::size_t slash = path.find('/', start);
        const std::size_t end = slash == std::string::npos ? path.size() : slash;
        if (end == start) return false;
        const std::string component = path.substr(start, end - start);
        if (component == "." || component == "..") return false;
        if (slash == std::string::npos) break;
        start = slash + 1;
    }
    return true;
}

bool isAllowedAppRoot(const std::string& path) {
    if (!isSafeAbsolutePath(path)) return false;
    const std::size_t slash = path.find_last_of('/');
    const std::string name = path.substr(slash + 1);
    return name == "app_tmp" || name == "app_temp";
}

bool hasPrefixAndDigits(const std::string& name, const char* prefix) {
    std::size_t prefixLength = 0;
    while (prefix[prefixLength] != '\0') ++prefixLength;
    if (name.size() <= prefixLength) return false;
    for (std::size_t i = 0; i < prefixLength; ++i) {
        const unsigned char actual = static_cast<unsigned char>(name[i]);
        const unsigned char wanted = static_cast<unsigned char>(prefix[i]);
        if (std::tolower(actual) != std::tolower(wanted)) return false;
    }
    for (std::size_t i = prefixLength; i < name.size(); ++i) {
        const unsigned char ch = static_cast<unsigned char>(name[i]);
        if (!std::isdigit(ch)) return false;
    }
    return true;
}

std::vector<std::string> childDirectories(int parentFd) {
    std::vector<std::string> result;
    const int scanFd = ::openat(parentFd, ".", directoryOpenFlags());
    if (scanFd < 0) return result;
    DIR* directory = ::fdopendir(scanFd);
    if (!directory) {
        ::close(scanFd);
        return result;
    }

    while (dirent* entry = ::readdir(directory)) {
        const std::string name(entry->d_name);
        if (name == "." || name == "..") continue;
        struct stat status {};
        if (::fstatat(parentFd, name.c_str(), &status, AT_SYMLINK_NOFOLLOW) == 0 &&
            S_ISDIR(status.st_mode)) {
            result.push_back(name);
        }
    }
    ::closedir(directory);
    std::sort(result.begin(), result.end());
    return result;
}

ScopedFd openChildDirectory(int parentFd, const std::string& name) {
    return ScopedFd(::openat(parentFd, name.c_str(), directoryOpenFlags()));
}

bool hasChildDirectory(int parentFd, const char* name) {
    const int childFd = ::openat(parentFd, name, directoryOpenFlags());
    if (childFd < 0) return false;
    ::close(childFd);
    return true;
}

bool validUuid(const std::string& uuid) {
    if (uuid.size() != 36) return false;
    for (std::size_t i = 0; i < uuid.size(); ++i) {
        if (i == 8 || i == 13 || i == 18 || i == 23) {
            if (uuid[i] != '-') return false;
        } else if (!std::isxdigit(static_cast<unsigned char>(uuid[i]))) {
            return false;
        }
    }
    return true;
}

bool validComMojangDirectory(const std::string& path) {
    if (!isSafeAbsolutePath(path)) return false;
    const std::size_t slash = path.find_last_of('/');
    if (path.substr(slash + 1) != "com.mojang") return false;

    ScopedFd root(::open(path.c_str(), directoryOpenFlags()));
    if (root.get() < 0) return false;
    return hasChildDirectory(root.get(), "resource_packs") &&
           hasChildDirectory(root.get(), "behavior_packs");
}

} // namespace

std::vector<std::string> ps4AppTemporaryRootCandidates() {
    return {"/app_tmp", "/app_temp"};
}

LocateResult locateComMojangDirectories(const std::vector<std::string>& appRoots) {
    LocateResult result;
    if (appRoots.empty()) {
        result.error = LocateError::InvalidRoot;
        result.message = "Supply the explicitly allowed PS4 app_tmp/app_temp root candidates.";
        return result;
    }

    for (const std::string& root : appRoots) {
        if (!isAllowedAppRoot(root)) {
            result.error = LocateError::InvalidRoot;
            result.message = "Only absolute app_tmp or app_temp roots are accepted; broad filesystem scans are refused.";
            result.locations.clear();
            return result;
        }
    }

    std::set<std::string> seenRoots;
    std::set<std::string> seenLocations;
    for (const std::string& rootPath : appRoots) {
        if (!seenRoots.insert(rootPath).second) continue;
        ScopedFd root(::open(rootPath.c_str(), directoryOpenFlags()));
        if (root.get() < 0) continue;

        const std::vector<std::string> titleNames = childDirectories(root.get());
        for (const std::string& titleName : titleNames) {
            if (!hasPrefixAndDigits(titleName, "cusa")) continue;
            ScopedFd title = openChildDirectory(root.get(), titleName);
            if (title.get() < 0) continue;

            const std::vector<std::string> userNames = childDirectories(title.get());
            for (const std::string& userName : userNames) {
                if (!hasPrefixAndDigits(userName, "user")) continue;
                ScopedFd user = openChildDirectory(title.get(), userName);
                if (user.get() < 0) continue;

                ScopedFd games = openChildDirectory(user.get(), "games");
                if (games.get() < 0) continue;
                ScopedFd mojang = openChildDirectory(games.get(), "com.mojang");
                if (mojang.get() < 0 ||
                    !hasChildDirectory(mojang.get(), "resource_packs") ||
                    !hasChildDirectory(mojang.get(), "behavior_packs")) {
                    continue;
                }

                const std::string path = rootPath + "/" + titleName + "/" + userName +
                                         "/games/com.mojang";
                if (!seenLocations.insert(path).second) continue;
                result.locations.push_back({rootPath, titleName, userName, path});
            }
        }
    }

    std::sort(result.locations.begin(), result.locations.end(),
              [](const ComMojangLocation& lhs, const ComMojangLocation& rhs) {
                  return lhs.comMojangDirectory < rhs.comMojangDirectory;
              });
    if (result.locations.empty()) {
        result.error = LocateError::NotFound;
        result.message = "No matching Minecraft com.mojang directory was found under the supplied PS4 roots.";
    } else if (result.locations.size() > 1) {
        result.error = LocateError::Ambiguous;
        result.message = "Multiple Minecraft data directories were found; the caller must select one explicitly.";
    } else {
        result.error = LocateError::None;
        result.message = "One matching Minecraft com.mojang directory was found.";
    }
    return result;
}

bool packDestinationDirectory(const std::string& comMojangDirectory,
                              addon_importer::PackKind kind,
                              const std::string& packUuid,
                              std::string& destination,
                              std::string& error) {
    destination.clear();
    error.clear();
    if (!validUuid(packUuid)) {
        error = "The pack UUID is invalid; a canonical UUID is required for the destination name.";
        return false;
    }
    if (!validComMojangDirectory(comMojangDirectory)) {
        error = "The explicitly supplied directory is not a verified com.mojang root.";
        return false;
    }

    const char* packFolder = nullptr;
    switch (kind) {
        case addon_importer::PackKind::Resource: packFolder = "resource_packs"; break;
        case addon_importer::PackKind::Behavior: packFolder = "behavior_packs"; break;
        case addon_importer::PackKind::Mixed:
            error = "A single .mcpack with mixed modules cannot be routed to one pack directory.";
            return false;
        case addon_importer::PackKind::Unknown:
            error = "The pack type is unknown; no destination will be selected.";
            return false;
    }

    destination = comMojangDirectory + "/" + packFolder + "/" + packUuid;
    if (destination.size() > kMaxPathBytes) {
        destination.clear();
        error = "The resolved Minecraft pack destination exceeds the safe path-length limit.";
        return false;
    }
    return true;
}

} // namespace ps4app::minecraft_storage
