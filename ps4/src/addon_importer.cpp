#include "addon_importer.hpp"

#include "json.hpp"
#include "miniz.h"

#include <algorithm>
#include <cerrno>
#include <cctype>
#include <cstdio>
#include <cstring>
#include <fcntl.h>
#include <map>
#include <string>
#include <sys/stat.h>
#include <unistd.h>
#include <utility>
#include <vector>

namespace ps4app::addon_importer {
namespace {

constexpr std::size_t kMaxPathBytes = 4096;
constexpr std::size_t kMaxArchiveNameBytes = 1024;
constexpr std::size_t kMaxPackNameBytes = 256;

int openFlagsReadOnly() {
    int flags = O_RDONLY;
#ifdef O_CLOEXEC
    flags |= O_CLOEXEC;
#endif
#ifdef O_NOFOLLOW
    flags |= O_NOFOLLOW;
#endif
    return flags;
}

int openFlagsDirectory() {
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

int openFlagsNewFile() {
    int flags = O_WRONLY | O_CREAT | O_EXCL;
#ifdef O_CLOEXEC
    flags |= O_CLOEXEC;
#endif
#ifdef O_NOFOLLOW
    flags |= O_NOFOLLOW;
#endif
    return flags;
}

bool validExplicitAbsolutePath(const std::string& path, bool allowRoot) {
    if (path.empty() || path.size() > kMaxPathBytes || path.front() != '/') return false;
    if (path.find('\0') != std::string::npos || path.find('\\') != std::string::npos) return false;
    if (path == "/") return allowRoot;
    if (path.back() == '/') return false;

    std::size_t start = 1;
    while (start < path.size()) {
        const std::size_t slash = path.find('/', start);
        const std::size_t end = slash == std::string::npos ? path.size() : slash;
        if (end == start) return false;
        const std::string component = path.substr(start, end - start);
        if (component == "." || component == "..") return false;
        for (unsigned char ch : component) {
            if (ch < 0x20 || ch == 0x7f) return false;
        }
        if (slash == std::string::npos) break;
        start = slash + 1;
    }
    return true;
}

bool hasMcpackExtension(const std::string& path) {
    const std::size_t slash = path.find_last_of('/');
    const std::size_t dot = path.find_last_of('.');
    if (dot == std::string::npos || (slash != std::string::npos && dot < slash)) return false;
    const std::string extension = path.substr(dot);
    return extension.size() == 7 &&
           std::tolower(static_cast<unsigned char>(extension[1])) == 'm' &&
           std::tolower(static_cast<unsigned char>(extension[2])) == 'c' &&
           std::tolower(static_cast<unsigned char>(extension[3])) == 'p' &&
           std::tolower(static_cast<unsigned char>(extension[4])) == 'a' &&
           std::tolower(static_cast<unsigned char>(extension[5])) == 'c' &&
           std::tolower(static_cast<unsigned char>(extension[6])) == 'k';
}

struct ZipReader {
    mz_zip_archive zip{};
    FILE* file = nullptr;
    bool initialized = false;

    ~ZipReader() {
        if (initialized) mz_zip_reader_end(&zip);
        else if (zip.m_pState) mz_zip_reader_end(&zip);
        if (file) std::fclose(file);
    }

    ZipReader() { mz_zip_zero_struct(&zip); }
    ZipReader(const ZipReader&) = delete;
    ZipReader& operator=(const ZipReader&) = delete;
};

bool normalizeArchivePath(const std::string& raw, bool isDirectory, std::string& normalized) {
    if (raw.empty() || raw.size() > kMaxArchiveNameBytes ||
        raw.front() == '/' || raw.front() == '\\' ||
        raw.find('\0') != std::string::npos || raw.find('\\') != std::string::npos ||
        raw.find(':') != std::string::npos) {
        return false;
    }

    for (unsigned char ch : raw) {
        if (ch < 0x20 || ch == 0x7f) return false;
    }

    std::string path = raw;
    const bool hasTrailingSlash = !path.empty() && path.back() == '/';
    if (hasTrailingSlash != isDirectory) return false;
    if (hasTrailingSlash) path.pop_back();
    if (path.empty()) return false;

    std::size_t start = 0;
    while (start < path.size()) {
        const std::size_t slash = path.find('/', start);
        const std::size_t end = slash == std::string::npos ? path.size() : slash;
        if (end == start) return false;
        const std::string component = path.substr(start, end - start);
        if (component == "." || component == "..") return false;
        if (slash == std::string::npos) break;
        start = slash + 1;
    }

    normalized = std::move(path);
    return true;
}

bool getArchiveName(mz_zip_archive* zip, mz_uint index, std::string& name) {
    const mz_uint required = mz_zip_reader_get_filename(zip, index, nullptr, 0);
    if (required < 2 || required > kMaxArchiveNameBytes + 1) return false;

    std::vector<char> buffer(static_cast<std::size_t>(required), '\0');
    const mz_uint copied = mz_zip_reader_get_filename(zip, index, buffer.data(), required);
    if (copied != required || buffer.back() != '\0') return false;

    name.assign(buffer.data(), static_cast<std::size_t>(required - 1));
    return name.find('\0') == std::string::npos;
}

bool isUnsafeUnixEntry(const mz_zip_archive_file_stat& stat, bool isDirectory) {
    const unsigned hostOs = static_cast<unsigned>(stat.m_version_made_by >> 8);
    if (hostOs != 3) return false; // Unix mode bits are meaningful only for Unix-created ZIP entries.

    const unsigned mode = static_cast<unsigned>(stat.m_external_attr >> 16);
    const unsigned kind = mode & static_cast<unsigned>(S_IFMT);
    if (kind == 0) return false;
#ifdef S_IFLNK
    if (kind == static_cast<unsigned>(S_IFLNK)) return true;
#endif
    if (kind != static_cast<unsigned>(S_IFREG) && kind != static_cast<unsigned>(S_IFDIR)) return true;
    return isDirectory != (kind == static_cast<unsigned>(S_IFDIR));
}

bool validateEntryCollisions(const std::map<std::string, bool>& entryTypes) {
    for (const auto& item : entryTypes) {
        const std::string& name = item.first;
        const bool isDirectory = item.second;

        std::size_t slash = name.find('/');
        while (slash != std::string::npos) {
            const auto parent = entryTypes.find(name.substr(0, slash));
            if (parent != entryTypes.end() && !parent->second) return false;
            slash = name.find('/', slash + 1);
        }

        if (!isDirectory) {
            const std::string prefix = name + "/";
            auto child = entryTypes.lower_bound(prefix);
            if (child != entryTypes.end() && child->first.compare(0, prefix.size(), prefix) == 0) return false;
        }
    }
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

bool parseManifest(const std::string& text, Inspection& result) {
    addonstation::json::Value manifest;
    std::string parseError;
    if (!addonstation::json::parse(text, manifest, &parseError) ||
        manifest.type != addonstation::json::Type::Object) {
        result.error = Error::ManifestInvalid;
        result.message = "The root manifest.json is not a valid JSON object.";
        return false;
    }

    const addonstation::json::Value* format = manifest.get("format_version");
    const addonstation::json::Value* header = manifest.get("header");
    const addonstation::json::Value* modules = manifest.get("modules");
    if (!format || format->integerOr(0) <= 0 || !header ||
        header->type != addonstation::json::Type::Object || !modules ||
        modules->type != addonstation::json::Type::Array || modules->array.empty()) {
        result.error = Error::ManifestInvalid;
        result.message = "The manifest is missing a positive format_version, header, or modules array.";
        return false;
    }

    const addonstation::json::Value* name = header->get("name");
    const addonstation::json::Value* uuid = header->get("uuid");
    if (!name || name->type != addonstation::json::Type::String || name->text.empty() ||
        name->text.size() > kMaxPackNameBytes || !uuid || uuid->type != addonstation::json::Type::String ||
        !validUuid(uuid->text)) {
        result.error = Error::ManifestInvalid;
        result.message = "The manifest header must contain a bounded name and a canonical UUID.";
        return false;
    }

    bool hasResourceModule = false;
    bool hasBehaviorModule = false;
    for (const addonstation::json::Value& module : modules->array) {
        if (module.type != addonstation::json::Type::Object) {
            result.error = Error::ManifestInvalid;
            result.message = "A manifest module is not a JSON object.";
            return false;
        }
        const addonstation::json::Value* type = module.get("type");
        if (!type || type->type != addonstation::json::Type::String) {
            result.error = Error::ManifestInvalid;
            result.message = "A manifest module is missing its type.";
            return false;
        }
        if (type->text == "resources") hasResourceModule = true;
        else if (type->text == "data" || type->text == "client_data" || type->text == "script")
            hasBehaviorModule = true;
        else {
            result.error = Error::ManifestInvalid;
            result.message = "The manifest contains a module type not supported by this add-on importer.";
            return false;
        }
    }

    result.packName = name->text;
    result.packUuid = uuid->text;
    if (hasResourceModule && hasBehaviorModule) result.packKind = PackKind::Mixed;
    else if (hasResourceModule) result.packKind = PackKind::Resource;
    else if (hasBehaviorModule) result.packKind = PackKind::Behavior;
    else {
        result.error = Error::ManifestInvalid;
        result.message = "The manifest has no resource or behavior-pack module.";
        return false;
    }
    return true;
}

struct ArchiveEntry {
    mz_uint index = 0;
    std::string path;
    bool directory = false;
    std::uint64_t size = 0;
};

bool scanArchive(mz_zip_archive* zip,
                 const Limits& limits,
                 Inspection& result,
                 std::vector<ArchiveEntry>& entries,
                 mz_uint& manifestIndex) {
    const mz_uint total = mz_zip_reader_get_num_files(zip);
    if (total == 0) {
        result.error = Error::InvalidArchive;
        result.message = "The archive contains no entries.";
        return false;
    }
    if (total > limits.maxEntries) {
        result.error = Error::TooManyEntries;
        result.message = "The archive exceeds the configured entry-count limit.";
        return false;
    }

    std::map<std::string, bool> entryTypes;
    std::uint64_t expandedBytes = 0;
    std::uint32_t fileCount = 0;
    bool foundManifest = false;
    entries.clear();
    entries.reserve(total);

    for (mz_uint index = 0; index < total; ++index) {
        mz_zip_archive_file_stat stat {};
        if (!mz_zip_reader_file_stat(zip, index, &stat)) {
            result.error = Error::InvalidArchive;
            result.archiveError = static_cast<int>(mz_zip_get_last_error(zip));
            result.message = "Could not read ZIP central-directory metadata.";
            return false;
        }
        if (stat.m_is_encrypted || !stat.m_is_supported) {
            result.error = Error::UnsupportedArchiveEntry;
            result.archiveError = static_cast<int>(mz_zip_get_last_error(zip));
            result.message = "The archive contains an encrypted or unsupported ZIP entry.";
            return false;
        }

        std::string rawName;
        if (!getArchiveName(zip, index, rawName)) {
            result.error = Error::UnsafeArchivePath;
            result.message = "A ZIP entry name is empty, too long, or malformed.";
            return false;
        }

        const bool isDirectory = stat.m_is_directory != 0;
        if (isUnsafeUnixEntry(stat, isDirectory)) {
            result.error = Error::UnsupportedArchiveEntry;
            result.message = "The archive contains a symbolic link or a non-regular special file.";
            return false;
        }

        std::string normalized;
        if (!normalizeArchivePath(rawName, isDirectory, normalized)) {
            result.error = Error::UnsafeArchivePath;
            result.message = "A ZIP entry contains an absolute, traversal, or otherwise unsafe path.";
            return false;
        }
        if (entryTypes.find(normalized) != entryTypes.end()) {
            result.error = Error::DuplicateArchivePath;
            result.message = "The archive contains duplicate normalized entry paths.";
            return false;
        }
        entryTypes.emplace(normalized, isDirectory);

        ArchiveEntry entry;
        entry.index = index;
        entry.path = std::move(normalized);
        entry.directory = isDirectory;
        entry.size = isDirectory ? 0 : stat.m_uncomp_size;
        if (!isDirectory) {
            if (entry.size > limits.maxEntryBytes ||
                entry.size > limits.maxExpandedBytes - expandedBytes) {
                result.error = Error::ExpansionLimitExceeded;
                result.message = "The archive exceeds the configured per-file or total expansion limit.";
                return false;
            }
            expandedBytes += entry.size;
            ++fileCount;
            if (entry.path == "manifest.json") {
                if (foundManifest) {
                    result.error = Error::DuplicateArchivePath;
                    result.message = "The archive contains more than one root manifest.json.";
                    return false;
                }
                foundManifest = true;
                manifestIndex = index;
            }
        }
        entries.push_back(std::move(entry));
    }

    if (!validateEntryCollisions(entryTypes)) {
        result.error = Error::DuplicateArchivePath;
        result.message = "The archive has a file/directory path collision.";
        return false;
    }
    if (!foundManifest) {
        result.error = Error::ManifestMissing;
        result.message = "The .mcpack must contain manifest.json at the archive root.";
        return false;
    }

    mz_zip_archive_file_stat manifestStat {};
    if (!mz_zip_reader_file_stat(zip, manifestIndex, &manifestStat) ||
        manifestStat.m_uncomp_size == 0 || manifestStat.m_uncomp_size > limits.maxManifestBytes ||
        manifestStat.m_uncomp_size > static_cast<mz_uint64>(SIZE_MAX - 1)) {
        result.error = Error::ManifestInvalid;
        result.message = "The root manifest is empty or exceeds the manifest-size limit.";
        return false;
    }

    std::vector<char> manifestBytes(static_cast<std::size_t>(manifestStat.m_uncomp_size) + 1, '\0');
    if (!mz_zip_reader_extract_to_mem(zip, manifestIndex, manifestBytes.data(),
                                      static_cast<std::size_t>(manifestStat.m_uncomp_size), 0)) {
        result.error = Error::ManifestInvalid;
        result.archiveError = static_cast<int>(mz_zip_get_last_error(zip));
        result.message = "The root manifest could not be decompressed or failed ZIP CRC validation.";
        return false;
    }
    const std::string manifestText(manifestBytes.data(),
                                   static_cast<std::size_t>(manifestStat.m_uncomp_size));
    if (!parseManifest(manifestText, result)) return false;

    result.entryCount = total;
    result.fileCount = fileCount;
    result.expandedBytes = expandedBytes;
    result.error = Error::None;
    result.systemError = 0;
    result.archiveError = 0;
    result.message = "The .mcpack passed read-only archive and manifest validation.";
    return true;
}

bool openForInspection(const std::string& path,
                       const Limits& limits,
                       ZipReader& reader,
                       Inspection& result) {
    if (!validExplicitAbsolutePath(path, false)) {
        result.error = Error::InvalidRequest;
        result.message = "The source must be an explicit absolute POSIX path without dot segments.";
        return false;
    }
    if (!hasMcpackExtension(path)) {
        result.error = Error::UnsupportedContainer;
        result.message = "This importer currently accepts one .mcpack; .mcaddon is not implemented.";
        return false;
    }

    const int fd = ::open(path.c_str(), openFlagsReadOnly());
    if (fd < 0) {
        result.error = Error::SourceOpenFailed;
        result.systemError = errno;
        result.message = "Could not open the explicitly supplied source archive.";
        return false;
    }

    struct stat fileStatus {};
    if (::fstat(fd, &fileStatus) != 0) {
        const int savedError = errno;
        ::close(fd);
        result.error = Error::SourceOpenFailed;
        result.systemError = savedError;
        result.message = "Could not inspect the source archive.";
        return false;
    }
    if (!S_ISREG(fileStatus.st_mode) || fileStatus.st_size <= 0) {
        ::close(fd);
        result.error = Error::InvalidArchive;
        result.message = "The supplied source is not a non-empty regular file.";
        return false;
    }
    if (static_cast<std::uint64_t>(fileStatus.st_size) > limits.maxArchiveBytes) {
        ::close(fd);
        result.error = Error::SourceTooLarge;
        result.message = "The compressed archive exceeds the configured source-size limit.";
        return false;
    }

    reader.file = ::fdopen(fd, "rb");
    if (!reader.file) {
        const int savedError = errno;
        ::close(fd);
        result.error = Error::SourceOpenFailed;
        result.systemError = savedError;
        result.message = "Could not create a read-only stream for the source archive.";
        return false;
    }

    errno = 0;
    if (!mz_zip_reader_init_cfile(&reader.zip, reader.file,
                                  static_cast<mz_uint64>(fileStatus.st_size), 0)) {
        result.systemError = errno;
        result.archiveError = static_cast<int>(mz_zip_get_last_error(&reader.zip));
        result.error = Error::InvalidArchive;
        result.message = "The supplied .mcpack is not a valid supported ZIP archive.";
        return false;
    }
    reader.initialized = true;
    return true;
}

struct WriteContext {
    int fd = -1;
    std::uint64_t bytesWritten = 0;
    int systemError = 0;
};

size_t writeChunk(void* opaque, mz_uint64 fileOffset, const void* data, size_t size) {
    WriteContext* context = static_cast<WriteContext*>(opaque);
    if (!context || context->fd < 0 || fileOffset != context->bytesWritten) {
        if (context) context->systemError = EINVAL;
        return 0;
    }

    const unsigned char* cursor = static_cast<const unsigned char*>(data);
    size_t remaining = size;
    while (remaining > 0) {
        const ssize_t written = ::write(context->fd, cursor, remaining);
        if (written < 0) {
            if (errno == EINTR) continue;
            context->systemError = errno;
            return 0;
        }
        if (written == 0) {
            context->systemError = EIO;
            return 0;
        }
        cursor += written;
        remaining -= static_cast<size_t>(written);
        context->bytesWritten += static_cast<std::uint64_t>(written);
    }
    return size;
}

bool splitParent(const std::string& path, std::string& parent, std::string& leaf) {
    const std::size_t slash = path.find_last_of('/');
    if (slash == std::string::npos) {
        parent.clear();
        leaf = path;
        return !leaf.empty();
    }
    if (slash + 1 >= path.size()) return false;
    parent = slash == 0 ? std::string{} : path.substr(0, slash);
    leaf = path.substr(slash + 1);
    return !leaf.empty();
}

int openDirectoryPath(int rootFd,
                      const std::string& relativePath,
                      std::vector<std::string>& createdDirectories,
                      int& errorNumber) {
    if (relativePath.empty()) {
        const int duplicate = ::dup(rootFd);
        if (duplicate < 0) errorNumber = errno;
        return duplicate;
    }

    int currentFd = ::dup(rootFd);
    if (currentFd < 0) {
        errorNumber = errno;
        return -1;
    }

    std::string prefix;
    std::size_t start = 0;
    while (start < relativePath.size()) {
        const std::size_t slash = relativePath.find('/', start);
        const std::size_t end = slash == std::string::npos ? relativePath.size() : slash;
        const std::string component = relativePath.substr(start, end - start);
        if (component.empty() || component == "." || component == "..") {
            errorNumber = EINVAL;
            ::close(currentFd);
            return -1;
        }
        if (!prefix.empty()) prefix.push_back('/');
        prefix += component;

        if (::mkdirat(currentFd, component.c_str(), 0755) == 0) {
            createdDirectories.push_back(prefix);
        } else if (errno != EEXIST) {
            errorNumber = errno;
            ::close(currentFd);
            return -1;
        }

        struct stat status {};
        if (::fstatat(currentFd, component.c_str(), &status, AT_SYMLINK_NOFOLLOW) != 0) {
            errorNumber = errno;
            ::close(currentFd);
            return -1;
        }
        if (!S_ISDIR(status.st_mode)) {
            errorNumber = ENOTDIR;
            ::close(currentFd);
            return -1;
        }

        const int nextFd = ::openat(currentFd, component.c_str(), openFlagsDirectory());
        if (nextFd < 0) {
            errorNumber = errno;
            ::close(currentFd);
            return -1;
        }
        ::close(currentFd);
        currentFd = nextFd;

        if (slash == std::string::npos) break;
        start = slash + 1;
    }
    return currentFd;
}

int openDestinationRoot(const std::string& path, int& errorNumber) {
    if (::mkdir(path.c_str(), 0755) != 0) {
        errorNumber = errno;
        return -1;
    }
    const int fd = ::open(path.c_str(), openFlagsDirectory());
    if (fd < 0) {
        errorNumber = errno;
        ::rmdir(path.c_str());
    }
    return fd;
}

int rollbackImport(int rootFd,
                   const std::string& destination,
                   const std::vector<std::string>& files,
                   const std::vector<std::string>& directories) {
    int firstError = 0;
    if (rootFd >= 0) {
        for (auto it = files.rbegin(); it != files.rend(); ++it) {
            if (::unlinkat(rootFd, it->c_str(), 0) != 0 && errno != ENOENT && firstError == 0) {
                firstError = errno;
            }
        }
        for (auto it = directories.rbegin(); it != directories.rend(); ++it) {
            if (::unlinkat(rootFd, it->c_str(), AT_REMOVEDIR) != 0 && errno != ENOENT && firstError == 0) {
                firstError = errno;
            }
        }
    }
    if (::rmdir(destination.c_str()) != 0 && errno != ENOENT && firstError == 0) firstError = errno;
    return firstError;
}

void setResultError(ImportResult& result, Error error, int systemError, int archiveError,
                    const std::string& message) {
    result.error = error;
    result.systemError = systemError;
    result.archiveError = archiveError;
    result.message = message;
}

} // namespace

Inspection inspectMcpack(const std::string& archivePath, const Limits& limits) {
    Inspection result;
    if (limits.maxEntries == 0 || limits.maxArchiveBytes == 0 || limits.maxExpandedBytes == 0 ||
        limits.maxEntryBytes == 0 || limits.maxManifestBytes == 0) {
        result.error = Error::InvalidRequest;
        result.message = "All archive safety limits must be non-zero.";
        return result;
    }

    ZipReader reader;
    if (!openForInspection(archivePath, limits, reader, result)) return result;

    std::vector<ArchiveEntry> entries;
    mz_uint manifestIndex = 0;
    if (!scanArchive(&reader.zip, limits, result, entries, manifestIndex)) return result;
    return result;
}

ImportResult importMcpack(const ImportRequest& request) {
    ImportResult result;
    result.inspection = inspectMcpack(request.archivePath, request.limits);
    if (!result.inspection.ok()) {
        setResultError(result, result.inspection.error, result.inspection.systemError,
                       result.inspection.archiveError, result.inspection.message);
        return result;
    }

    if (!request.writeApproved) {
        setResultError(result, Error::ApprovalRequired, 0, 0,
                       "The archive passed read-only checks; explicit write approval is required to extract it.");
        return result;
    }
    if (!validExplicitAbsolutePath(request.destinationDirectory, false)) {
        setResultError(result, Error::InvalidRequest, EINVAL, 0,
                       "The destination must be a caller-supplied absolute POSIX path without dot segments.");
        return result;
    }

    ZipReader reader;
    Inspection extractionInspection;
    if (!openForInspection(request.archivePath, request.limits, reader, extractionInspection)) {
        result.inspection = extractionInspection;
        setResultError(result, extractionInspection.error, extractionInspection.systemError,
                       extractionInspection.archiveError, extractionInspection.message);
        return result;
    }

    std::vector<ArchiveEntry> entries;
    mz_uint manifestIndex = 0;
    if (!scanArchive(&reader.zip, request.limits, extractionInspection, entries, manifestIndex)) {
        result.inspection = extractionInspection;
        setResultError(result, extractionInspection.error, extractionInspection.systemError,
                       extractionInspection.archiveError, extractionInspection.message);
        return result;
    }
    result.inspection = extractionInspection;

    int rootError = 0;
    const int rootFd = openDestinationRoot(request.destinationDirectory, rootError);
    if (rootFd < 0) {
        const Error error = rootError == EEXIST ? Error::DestinationAlreadyExists : Error::DestinationCreateFailed;
        setResultError(result, error, rootError, 0,
                       rootError == EEXIST
                           ? "The destination already exists; existing data will not be overwritten."
                           : "Could not create the explicitly supplied new destination directory.");
        return result;
    }

    std::vector<std::string> createdFiles;
    std::vector<std::string> createdDirectories;
    bool failed = false;

    for (const ArchiveEntry& entry : entries) {
        if (entry.directory) {
            int dirError = 0;
            const int directoryFd = openDirectoryPath(rootFd, entry.path, createdDirectories, dirError);
            if (directoryFd < 0) {
                setResultError(result, Error::EntryCreateFailed, dirError, 0,
                               "Could not create or safely open an archive directory entry.");
                failed = true;
                break;
            }
            ::close(directoryFd);
            continue;
        }

        std::string parent;
        std::string leaf;
        if (!splitParent(entry.path, parent, leaf)) {
            setResultError(result, Error::UnsafeArchivePath, EINVAL, 0,
                           "Could not split a previously validated archive path.");
            failed = true;
            break;
        }

        int parentError = 0;
        const int parentFd = openDirectoryPath(rootFd, parent, createdDirectories, parentError);
        if (parentFd < 0) {
            setResultError(result, Error::EntryCreateFailed, parentError, 0,
                           "Could not create or safely open a file's parent directory.");
            failed = true;
            break;
        }

        const int fileFd = ::openat(parentFd, leaf.c_str(), openFlagsNewFile(), 0644);
        const int openError = fileFd < 0 ? errno : 0;
        ::close(parentFd);
        if (fileFd < 0) {
            setResultError(result, Error::EntryCreateFailed, openError, 0,
                           "Could not create a new archive file without overwriting existing data.");
            failed = true;
            break;
        }
        createdFiles.push_back(entry.path);

        WriteContext writeContext;
        writeContext.fd = fileFd;
        const mz_bool extracted = mz_zip_reader_extract_to_callback(
            &reader.zip, entry.index, writeChunk, &writeContext, 0);
        const int archiveError = extracted ? 0 : static_cast<int>(mz_zip_get_last_error(&reader.zip));
        const int closeResult = ::close(fileFd);
        const int closeError = closeResult == 0 ? 0 : errno;

        if (!extracted) {
            const Error error = writeContext.systemError != 0 ? Error::EntryWriteFailed : Error::ArchiveExtractFailed;
            setResultError(result, error, writeContext.systemError, archiveError,
                           writeContext.systemError != 0
                               ? "Writing an extracted archive entry failed."
                               : "ZIP extraction or CRC validation failed.");
            failed = true;
            break;
        }
        if (closeResult != 0) {
            setResultError(result, Error::EntryCloseFailed, closeError, 0,
                           "Closing an extracted destination file failed.");
            failed = true;
            break;
        }
        if (writeContext.bytesWritten != entry.size) {
            setResultError(result, Error::ArchiveExtractFailed, EIO, archiveError,
                           "The extracted entry size did not match its ZIP metadata.");
            failed = true;
            break;
        }

        ++result.filesWritten;
        result.bytesWritten += writeContext.bytesWritten;
    }

    if (failed) {
        result.rollbackError = rollbackImport(rootFd, request.destinationDirectory,
                                              createdFiles, createdDirectories);
        ::close(rootFd);
        if (result.rollbackError != 0) {
            result.error = Error::RollbackFailed;
            result.message += " Cleanup also failed; the destination may contain partial data.";
        }
        return result;
    }

    if (::close(rootFd) != 0) {
        const int closeError = errno;
        setResultError(result, Error::EntryCloseFailed, closeError, 0,
                       "Extraction finished, but closing the destination directory failed; inspect the explicit destination before retrying.");
        return result;
    }

    result.error = Error::None;
    result.message = "The validated .mcpack was extracted into the explicitly supplied new directory.";
    return result;
}

const char* errorName(Error error) {
    switch (error) {
        case Error::None: return "none";
        case Error::ApprovalRequired: return "approval-required";
        case Error::InvalidRequest: return "invalid-request";
        case Error::UnsupportedContainer: return "unsupported-container";
        case Error::SourceOpenFailed: return "source-open-failed";
        case Error::SourceTooLarge: return "source-too-large";
        case Error::InvalidArchive: return "invalid-archive";
        case Error::TooManyEntries: return "too-many-entries";
        case Error::ExpansionLimitExceeded: return "expansion-limit-exceeded";
        case Error::UnsupportedArchiveEntry: return "unsupported-archive-entry";
        case Error::UnsafeArchivePath: return "unsafe-archive-path";
        case Error::DuplicateArchivePath: return "duplicate-archive-path";
        case Error::ManifestMissing: return "manifest-missing";
        case Error::ManifestInvalid: return "manifest-invalid";
        case Error::DestinationAlreadyExists: return "destination-already-exists";
        case Error::DestinationCreateFailed: return "destination-create-failed";
        case Error::EntryCreateFailed: return "entry-create-failed";
        case Error::EntryWriteFailed: return "entry-write-failed";
        case Error::ArchiveExtractFailed: return "archive-extract-failed";
        case Error::EntryCloseFailed: return "entry-close-failed";
        case Error::RollbackFailed: return "rollback-failed";
    }
    return "unknown";
}

const char* packKindName(PackKind kind) {
    switch (kind) {
        case PackKind::Unknown: return "unknown";
        case PackKind::Resource: return "resource";
        case PackKind::Behavior: return "behavior";
        case PackKind::Mixed: return "mixed";
    }
    return "unknown";
}

} // namespace ps4app::addon_importer
