#include "installer.hpp"

#include <cerrno>
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

bool ensureDirectory(const std::string& path, int& outErrno) {
    outErrno = 0;
    if (isDirectory(path)) return true;
    if (::mkdir(path.c_str(), 0755) == 0 || errno == EEXIST) {
        if (isDirectory(path)) return true;
    }

    std::size_t pos = 1;
    while (pos <= path.size()) {
        const std::size_t slash = path.find('/', pos);
        const std::size_t end = (slash == std::string::npos) ? path.size() : slash;
        const std::string sub = path.substr(0, end);
        if (!sub.empty() && !isDirectory(sub)) {
            if (::mkdir(sub.c_str(), 0755) != 0 && errno != EEXIST) {
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

bool copySingleFile(const std::string& srcPath,
                    const std::string& dstPath,
                    std::uint64_t& bytesCopied,
                    int& outErrno) {
    const int inFd = ::open(srcPath.c_str(), O_RDONLY);
    if (inFd < 0) {
        outErrno = errno;
        return false;
    }

    const int outFd = ::open(dstPath.c_str(), O_WRONLY | O_CREAT | O_TRUNC, 0644);
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

        const char* cursor = buffer.data();
        ssize_t remaining = readBytes;
        while (remaining > 0) {
            const ssize_t written = ::write(outFd, cursor, static_cast<std::size_t>(remaining));
            if (written < 0) {
                if (errno == EINTR) continue;
                outErrno = errno;
                ::close(inFd);
                ::close(outFd);
                return false;
            }
            cursor += written;
            remaining -= written;
            bytesCopied += static_cast<std::uint64_t>(written);
        }
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

    if (!copyTreeRecursive(sourceFolder, destinationFolder, result)) {
        return result;
    }

    result.ok = true;
    result.message = "DONE";
    return result;
}

} // namespace pkgtest
