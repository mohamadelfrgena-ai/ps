#include "installer.hpp"

#ifdef __ORBIS__
#include "jbc.h"
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

} // namespace pkgtest
