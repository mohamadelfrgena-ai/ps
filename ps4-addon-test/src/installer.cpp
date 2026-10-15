#include "installer.hpp"

#include <algorithm>
#include <cerrno>
#include <cstdint>
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

bool readExact(int fd, void* dst, std::size_t len, int& outErrno) {
    char* ptr = static_cast<char*>(dst);
    std::size_t remaining = len;
    while (remaining > 0) {
        const ssize_t r = ::read(fd, ptr, remaining);
        if (r < 0) {
            if (errno == EINTR) continue;
            outErrno = errno;
            return false;
        }
        if (r == 0) {
            outErrno = EIO;
            return false;
        }
        ptr += r;
        remaining -= static_cast<std::size_t>(r);
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
        ptr += w;
        remaining -= static_cast<std::size_t>(w);
    }
    return true;
}

bool copyFromPreExtractedStream(const std::string& rawStreamPath,
                                const std::string& dstRoot,
                                CopyResult& result) {
    int err = 0;
    if (!ensureDirectory(dstRoot, err)) {
        result.errorCode = err;
        result.message = "MKDIR FAILED (" + std::to_string(err) + ")";
        return false;
    }

    const int inFd = ::open(rawStreamPath.c_str(), O_RDONLY);
    if (inFd < 0) {
        result.errorCode = errno;
        result.message = "OPEN FAILED (" + std::to_string(result.errorCode) + ")";
        return false;
    }

    char magic[8] = {};
    if (!readExact(inFd, magic, 8, err) || std::memcmp(magic, "PVRAW001", 8) != 0) {
        ::close(inFd);
        result.errorCode = err != 0 ? err : EINVAL;
        result.message = "HEADER FAILED";
        return false;
    }

    std::uint32_t dirCount = 0;
    if (!readExact(inFd, &dirCount, sizeof(dirCount), err)) {
        ::close(inFd);
        result.errorCode = err;
        result.message = "READ DIRCOUNT FAILED";
        return false;
    }

    for (std::uint32_t i = 0; i < dirCount; ++i) {
        std::uint16_t pathLen = 0;
        if (!readExact(inFd, &pathLen, sizeof(pathLen), err)) {
            ::close(inFd);
            result.errorCode = err;
            result.message = "READ DIRLEN FAILED";
            return false;
        }
        std::string relDir(pathLen, '\0');
        if (pathLen > 0 && !readExact(inFd, &relDir[0], pathLen, err)) {
            ::close(inFd);
            result.errorCode = err;
            result.message = "READ DIRPATH FAILED";
            return false;
        }
        const std::string fullDir = dstRoot + "/" + relDir;
        if (::mkdir(fullDir.c_str(), 0777) != 0 && errno != EEXIST) {
            if (!ensureDirectory(fullDir, err)) {
                ::close(inFd);
                result.errorCode = err;
                result.message = "MKDIR FAILED (" + std::to_string(err) + ")";
                return false;
            }
        }
    }

    std::uint32_t fileCount = 0;
    if (!readExact(inFd, &fileCount, sizeof(fileCount), err)) {
        ::close(inFd);
        result.errorCode = err;
        result.message = "READ FILECOUNT FAILED";
        return false;
    }

    std::vector<char> buffer(256 * 1024);
    for (std::uint32_t i = 0; i < fileCount; ++i) {
        std::uint16_t pathLen = 0;
        std::uint64_t fileSize = 0;
        if (!readExact(inFd, &pathLen, sizeof(pathLen), err) ||
            !readExact(inFd, &fileSize, sizeof(fileSize), err)) {
            ::close(inFd);
            result.errorCode = err;
            result.message = "READ FILEMETA FAILED";
            return false;
        }
        std::string relFile(pathLen, '\0');
        if (pathLen > 0 && !readExact(inFd, &relFile[0], pathLen, err)) {
            ::close(inFd);
            result.errorCode = err;
            result.message = "READ FILEPATH FAILED";
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
                ::close(inFd);
                result.errorCode = err;
                result.message = "WRITE OPEN FAILED (" + std::to_string(err) + ")";
                return false;
            }
        }

        std::uint64_t remaining = fileSize;
        while (remaining > 0) {
            const std::size_t chunk = static_cast<std::size_t>(
                std::min<std::uint64_t>(remaining, buffer.size()));
            if (!readExact(inFd, buffer.data(), chunk, err) ||
                !writeExact(outFd, buffer.data(), chunk, err)) {
                ::close(outFd);
                ::close(inFd);
                result.errorCode = err;
                result.message = "COPY FAILED (" + std::to_string(err) + ")";
                return false;
            }
            remaining -= chunk;
            result.bytesCopied += chunk;
        }

        if (::close(outFd) != 0) {
            err = errno;
            ::close(inFd);
            result.errorCode = err;
            result.message = "CLOSE FAILED (" + std::to_string(err) + ")";
            return false;
        }
        ++result.filesCopied;
    }

    ::close(inFd);
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

    std::vector<char> buffer(64 * 1024);
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
    if (::close(outFd) != 0) {
        outErrno = errno;
        return false;
    }
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
    if (!isDirectory(sourceFolder)) {
        result.errorCode = errno;
        result.message = "SOURCE MISSING (" + std::to_string(result.errorCode) + ")";
        return result;
    }

    const std::string rawStreamPath = sourceFolder + "/folder.raw";
    if (isRegularFile(rawStreamPath)) {
        if (!copyFromPreExtractedStream(rawStreamPath, destinationFolder, result)) {
            return result;
        }
        result.ok = true;
        result.message = "DONE";
        return result;
    }

    if (!copyTreeRecursive(sourceFolder, destinationFolder, result)) {
        return result;
    }

    result.ok = true;
    result.message = "DONE";
    return result;
}

} // namespace pkgtest
