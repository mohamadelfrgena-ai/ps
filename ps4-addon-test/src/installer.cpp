#include "installer.hpp"

#include "json.hpp"
#include "miniz.h"

#include <algorithm>
#include <cctype>
#include <cerrno>
#include <cstdio>
#include <cstring>
#include <dirent.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <unistd.h>
#include <utility>
#include <vector>

namespace pkgtest {
namespace {

bool startsWithIgnoreCase(const std::string& text, const char* prefix) {
    std::size_t i = 0;
    while (prefix[i] != '\0') {
        if (i >= text.size()) return false;
        const unsigned char a = static_cast<unsigned char>(text[i]);
        const unsigned char b = static_cast<unsigned char>(prefix[i]);
        if (std::tolower(a) != std::tolower(b)) return false;
        ++i;
    }
    return true;
}

bool isDirectory(const std::string& path) {
    struct stat st {};
    return ::stat(path.c_str(), &st) == 0 && S_ISDIR(st.st_mode);
}

std::vector<std::string> listChildDirectories(const std::string& dirPath) {
    std::vector<std::string> result;
    DIR* dir = ::opendir(dirPath.c_str());
    if (!dir) return result;
    const std::string prefix = (dirPath == "/") ? "/" : (dirPath + "/");
    while (dirent* entry = ::readdir(dir)) {
        const std::string name(entry->d_name);
        if (name.empty() || name == "." || name == "..") continue;
        bool isDir = false;
#ifdef DT_DIR
        if (entry->d_type == DT_DIR) {
            isDir = true;
        } else if (entry->d_type == DT_UNKNOWN)
#endif
        {
            const std::string full = prefix + name;
            struct stat st {};
            if (::stat(full.c_str(), &st) == 0 && S_ISDIR(st.st_mode)) {
                isDir = true;
            }
        }
        if (isDir) {
            result.push_back(name);
        }
    }
    ::closedir(dir);
    std::sort(result.begin(), result.end());
    return result;
}

bool mkdirRecursive(const std::string& path, int& outErrno) {
    outErrno = 0;
    if (path.empty() || path[0] != '/') {
        outErrno = EINVAL;
        return false;
    }
    if (isDirectory(path)) {
        return true;
    }
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

bool removeTreeRecursive(const std::string& path, int& outErrno) {
    struct stat st {};
    if (::stat(path.c_str(), &st) != 0) {
        if (errno == ENOENT) return true;
        outErrno = errno;
        return false;
    }
    if (!S_ISDIR(st.st_mode)) {
        if (::unlink(path.c_str()) != 0 && errno != ENOENT) {
            outErrno = errno;
            return false;
        }
        return true;
    }

    DIR* dir = ::opendir(path.c_str());
    if (!dir) {
        outErrno = errno;
        return false;
    }
    while (dirent* entry = ::readdir(dir)) {
        const std::string name(entry->d_name);
        if (name == "." || name == "..") continue;
        if (!removeTreeRecursive(path + "/" + name, outErrno)) {
            ::closedir(dir);
            return false;
        }
    }
    ::closedir(dir);
    if (::rmdir(path.c_str()) != 0 && errno != ENOENT) {
        outErrno = errno;
        return false;
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

bool normalizeZipEntryPath(const std::string& raw, bool isDir, std::string& out) {
    if (raw.empty() || raw.size() > 1024 || raw.front() == '/' || raw.front() == '\\' ||
        raw.find('\0') != std::string::npos || raw.find('\\') != std::string::npos ||
        raw.find(':') != std::string::npos) {
        return false;
    }
    std::string p = raw;
    if (isDir) {
        if (!p.empty() && p.back() == '/') p.pop_back();
    } else if (!p.empty() && p.back() == '/') {
        return false;
    }
    if (p.empty()) return false;

    std::size_t start = 0;
    while (start < p.size()) {
        const std::size_t slash = p.find('/', start);
        const std::size_t end = slash == std::string::npos ? p.size() : slash;
        if (end == start) return false;
        const std::string part = p.substr(start, end - start);
        if (part == "." || part == "..") return false;
        if (slash == std::string::npos) break;
        start = slash + 1;
    }
    out = std::move(p);
    return true;
}

struct ZipHandle {
    mz_zip_archive zip{};
    bool active = false;
    ZipHandle() { mz_zip_zero_struct(&zip); }
    ~ZipHandle() {
        if (active) mz_zip_reader_end(&zip);
    }
    ZipHandle(const ZipHandle&) = delete;
    ZipHandle& operator=(const ZipHandle&) = delete;
};

struct WriteState {
    int fd = -1;
    std::uint64_t written = 0;
    int err = 0;
};

size_t writeCallback(void* opaque, mz_uint64 offset, const void* buf, size_t len) {
    WriteState* state = static_cast<WriteState*>(opaque);
    if (!state || state->fd < 0 || offset != state->written) {
        if (state) state->err = EINVAL;
        return 0;
    }
    const unsigned char* ptr = static_cast<const unsigned char*>(buf);
    size_t left = len;
    while (left > 0) {
        const ssize_t w = ::write(state->fd, ptr, left);
        if (w < 0) {
            if (errno == EINTR) continue;
            state->err = errno;
            return 0;
        }
        if (w == 0) {
            state->err = EIO;
            return 0;
        }
        ptr += w;
        left -= static_cast<size_t>(w);
        state->written += static_cast<std::uint64_t>(w);
    }
    return len;
}

std::vector<std::string> defaultPs4Roots() {
    return {
        "/app_tmp",
        "/app_temp",
        "/user/app_tmp",
        "/user/app_temp",
        "/mnt/sandbox/pfsmnt",
    };
}

std::string pickResourcePacksSubfolder(const std::string& comMojangDir, PackKind kind) {
    if (kind == PackKind::Behavior) {
        if (isDirectory(comMojangDir + "/behavior_packs")) return "behavior_packs";
        if (isDirectory(comMojangDir + "/behavior packs")) return "behavior packs";
        return "behavior_packs";
    }
    if (isDirectory(comMojangDir + "/resource_packs")) return "resource_packs";
    if (isDirectory(comMojangDir + "/resource packs")) return "resource packs";
    return "resource_packs";
}

std::string resolveSpecificCusaMojang(const std::string& root, const std::string& cusaId) {
    const std::string cusaPath = root + "/" + cusaId;
    if (isDirectory(cusaPath)) {
        const std::vector<std::string> children = listChildDirectories(cusaPath);
        for (const std::string& child : children) {
            if (child == "games") continue;
            const std::string existingMojang = cusaPath + "/" + child + "/games/com.mojang";
            if (isDirectory(existingMojang)) return existingMojang;
        }
        for (const std::string& child : children) {
            if (child == "games") continue;
            return cusaPath + "/" + child + "/games/com.mojang";
        }
    }
    return cusaPath + "/user/games/com.mojang";
}

} // namespace

const char* targetModeLabel(TargetMode mode) {
    switch (mode) {
        case TargetMode::ExactUserPath:
            return "MODE 1: EXACT PATH /app_tmp/CUSA00265/user497444003/games/com.mojang";
        case TargetMode::AutoDetect:
            return "MODE 2: AUTO DETECT (MINECRAFT /app_tmp OR /app_temp)";
        case TargetMode::AppTmpUsa:
            return "MODE 3: FORCE /app_tmp/CUSA00744 (MINECRAFT USA)";
        case TargetMode::AppTmpEur:
            return "MODE 4: FORCE /app_tmp/CUSA00265 (MINECRAFT EUR)";
        case TargetMode::AppTempUsa:
            return "MODE 5: FORCE /app_temp/CUSA00744 (MINECRAFT USA)";
        case TargetMode::AppTempEur:
            return "MODE 6: FORCE /app_temp/CUSA00265 (MINECRAFT EUR)";
        case TargetMode::DataComMojang:
            return "MODE 7: FORCE /data/com.mojang (PS4 DATA TEST PATH)";
    }
    return "EXACT PATH";
}

const char* targetModePathPreview(TargetMode mode) {
    switch (mode) {
        case TargetMode::ExactUserPath:
            return "/app_tmp/CUSA00265/user497444003/games/com.mojang/resource_packs/<uuid>";
        case TargetMode::AutoDetect:
            return "/app_tmp/<CUSA>/<user>/games/com.mojang/resource_packs/<uuid>";
        case TargetMode::AppTmpUsa:
            return "/app_tmp/CUSA00744/<user>/games/com.mojang/resource_packs/<uuid>";
        case TargetMode::AppTmpEur:
            return "/app_tmp/CUSA00265/<user>/games/com.mojang/resource_packs/<uuid>";
        case TargetMode::AppTempUsa:
            return "/app_temp/CUSA00744/<user>/games/com.mojang/resource_packs/<uuid>";
        case TargetMode::AppTempEur:
            return "/app_temp/CUSA00265/<user>/games/com.mojang/resource_packs/<uuid>";
        case TargetMode::DataComMojang:
            return "/data/com.mojang/resource_packs/<uuid>";
    }
    return "";
}

TargetMode nextTargetMode(TargetMode mode, int delta) {
    constexpr int kCount = 7;
    int current = static_cast<int>(mode);
    current = (current + delta) % kCount;
    if (current < 0) current += kCount;
    return static_cast<TargetMode>(current);
}

PackMetadata inspectMcpackFile(const std::string& archivePath) {
    PackMetadata meta;
    ZipHandle handle;
    if (!mz_zip_reader_init_file(&handle.zip, archivePath.c_str(), 0)) {
        meta.error = "Could not open .mcpack archive: " + archivePath;
        return meta;
    }
    handle.active = true;

    const mz_uint total = mz_zip_reader_get_num_files(&handle.zip);
    if (total == 0) {
        meta.error = "Archive is empty.";
        return meta;
    }

    int manifestIndex = -1;
    for (mz_uint i = 0; i < total; ++i) {
        mz_zip_archive_file_stat st {};
        if (!mz_zip_reader_file_stat(&handle.zip, i, &st)) {
            meta.error = "Failed reading ZIP entry metadata.";
            return meta;
        }
        const bool isDir = st.m_is_directory != 0;
        std::string normalized;
        if (!normalizeZipEntryPath(st.m_filename, isDir, normalized)) {
            meta.error = std::string("Unsafe ZIP entry path: ") + st.m_filename;
            return meta;
        }
        if (!isDir) {
            ++meta.fileCount;
            meta.totalBytes += st.m_uncomp_size;
            if (normalized == "manifest.json") {
                manifestIndex = static_cast<int>(i);
            }
        }
    }

    if (manifestIndex < 0) {
        meta.error = "manifest.json not found at archive root.";
        return meta;
    }

    size_t manifestSize = 0;
    void* rawManifest = mz_zip_reader_extract_to_heap(&handle.zip, static_cast<mz_uint>(manifestIndex), &manifestSize, 0);
    if (!rawManifest || manifestSize == 0) {
        if (rawManifest) mz_free(rawManifest);
        meta.error = "Could not extract manifest.json.";
        return meta;
    }
    const std::string manifestText(static_cast<const char*>(rawManifest), manifestSize);
    mz_free(rawManifest);

    addonstation::json::Value root;
    std::string parseError;
    if (!addonstation::json::parse(manifestText, root, &parseError)) {
        meta.error = "Invalid manifest.json: " + parseError;
        return meta;
    }

    const addonstation::json::Value* header = root.get("header");
    const addonstation::json::Value* modules = root.get("modules");
    if (!header || !modules || modules->type != addonstation::json::Type::Array || modules->array.empty()) {
        meta.error = "manifest.json missing header or modules.";
        return meta;
    }

    const addonstation::json::Value* nameVal = header->get("name");
    const addonstation::json::Value* uuidVal = header->get("uuid");
    if (!nameVal || !uuidVal || !validUuid(uuidVal->text)) {
        meta.error = "manifest.json missing valid header name or UUID.";
        return meta;
    }

    bool hasRes = false;
    bool hasBeh = false;
    for (const auto& mod : modules->array) {
        const addonstation::json::Value* typeVal = mod.get("type");
        if (!typeVal) continue;
        if (typeVal->text == "resources") hasRes = true;
        else if (typeVal->text == "data" || typeVal->text == "client_data" || typeVal->text == "script") hasBeh = true;
    }

    meta.name = nameVal->text;
    meta.uuid = uuidVal->text;
    if (hasRes && hasBeh) meta.kind = PackKind::Mixed;
    else if (hasRes) meta.kind = PackKind::Resource;
    else if (hasBeh) meta.kind = PackKind::Behavior;
    else {
        meta.error = "No recognized resource/behavior module in manifest.json.";
        return meta;
    }

    meta.valid = true;
    return meta;
}

ScanReport scanFilesystemForMinecraft(const std::vector<std::string>& customRoots) {
    ScanReport report;
    if (customRoots.empty()) {
        const std::vector<std::string> topDirs = listChildDirectories("/");
        std::string topList;
        for (std::size_t i = 0; i < topDirs.size() && i < 12; ++i) {
            if (i > 0) topList += ", ";
            topList += "/" + topDirs[i];
        }
        if (!topList.empty()) {
            report.diagnosticLines.push_back("VISIBLE IN /: " + topList);
        }

        const char* exactMojangCandidates[] = {
            "/app_tmp/CUSA00265/user497444003/games/com.mojang",
            "/user/app_tmp/CUSA00265/user497444003/games/com.mojang",
            "/app_temp/CUSA00265/user497444003/games/com.mojang",
            "/user/app_temp/CUSA00265/user497444003/games/com.mojang",
        };
        for (const char* exactMojang : exactMojangCandidates) {
            const std::string rp = std::string(exactMojang) + "/resource_packs";
            struct stat st {};
            if (::stat(rp.c_str(), &st) == 0 && S_ISDIR(st.st_mode)) {
                report.detectedMojangDirs.push_back(exactMojang);
                report.diagnosticLines.push_back(std::string("FOUND EXACT PATH: ") + rp);
            } else if (::stat(exactMojang, &st) == 0 && S_ISDIR(st.st_mode)) {
                report.detectedMojangDirs.push_back(exactMojang);
                report.diagnosticLines.push_back(std::string("FOUND EXACT MOJANG: ") + exactMojang);
            } else {
                const int err = errno;
                if (std::string(exactMojang) == "/app_tmp/CUSA00265/user497444003/games/com.mojang") {
                    report.diagnosticLines.push_back(
                        "STAT /app_tmp/CUSA00265/user497444003/... -> ERRNO " + std::to_string(err) +
                        " (" + std::strerror(err) + ")");
                }
            }
        }
    }
    const std::vector<std::string> roots = customRoots.empty() ? defaultPs4Roots() : customRoots;

    for (const std::string& root : roots) {
        if (!isDirectory(root)) {
            report.diagnosticLines.push_back("ROOT " + root + " -> NOT PRESENT");
            continue;
        }
        const std::vector<std::string> titles = listChildDirectories(root);
        if (titles.empty()) {
            report.diagnosticLines.push_back("ROOT " + root + " -> EXISTS (EMPTY)");
            continue;
        }

        std::string joined;
        for (std::size_t i = 0; i < titles.size() && i < 5; ++i) {
            if (i > 0) joined += ", ";
            joined += titles[i];
        }
        report.diagnosticLines.push_back("ROOT " + root + " -> DIRS: " + joined);

        for (const std::string& title : titles) {
            if (!startsWithIgnoreCase(title, "cusa")) continue;
            const std::string titlePath = root + "/" + title;

            const std::string directMojang = titlePath + "/games/com.mojang";
            if (isDirectory(directMojang)) {
                report.detectedMojangDirs.push_back(directMojang);
                report.diagnosticLines.push_back("FOUND MOJANG: " + directMojang);
            }

            const std::vector<std::string> users = listChildDirectories(titlePath);
            bool hasUserSubdir = false;
            for (const std::string& user : users) {
                if (user == "games" || user == "sce_sys" || user == "sce_module") continue;
                hasUserSubdir = true;
                const std::string userMojang = titlePath + "/" + user + "/games/com.mojang";
                if (isDirectory(userMojang)) {
                    report.detectedMojangDirs.push_back(userMojang);
                    report.diagnosticLines.push_back("FOUND MOJANG: " + userMojang);
                } else {
                    report.candidateUserDirs.push_back(userMojang);
                    report.diagnosticLines.push_back("CANDIDATE USER DIR: " + titlePath + "/" + user);
                }
            }

            if (!hasUserSubdir && !isDirectory(directMojang)) {
                report.candidateUserDirs.push_back(titlePath + "/user/games/com.mojang");
                report.diagnosticLines.push_back("CANDIDATE TITLE DIR: " + titlePath);
            }
        }
    }

    if (customRoots.empty()) {
        if (isDirectory("/data")) {
            report.diagnosticLines.push_back("ROOT /data -> WRITABLE PS4 DATA PARTITION READY");
        }
    }

    return report;
}

InstallReport installMcpack(const std::string& archivePath,
                            TargetMode mode,
                            const std::vector<std::string>& customRoots,
                            const std::string& customDataRoot) {
    InstallReport report;
    const PackMetadata meta = inspectMcpackFile(archivePath);
    if (!meta.valid) {
        report.statusTitle = "PREFLIGHT FAILED";
        report.logs.push_back("ERROR: " + meta.error);
        return report;
    }

    report.logs.push_back("PACK VERIFIED: UUID=" + meta.uuid + " FILES=" + std::to_string(meta.fileCount));

    const ScanReport scan = scanFilesystemForMinecraft(customRoots);
    for (const std::string& line : scan.diagnosticLines) {
        report.logs.push_back(line);
    }

    std::string targetMojang;
    switch (mode) {
        case TargetMode::ExactUserPath:
            if (isDirectory("/user/app_tmp/CUSA00265/user497444003/games/com.mojang") &&
                !isDirectory("/app_tmp/CUSA00265/user497444003/games/com.mojang")) {
                targetMojang = "/user/app_tmp/CUSA00265/user497444003/games/com.mojang";
            } else if (isDirectory("/app_temp/CUSA00265/user497444003/games/com.mojang") &&
                       !isDirectory("/app_tmp/CUSA00265/user497444003/games/com.mojang")) {
                targetMojang = "/app_temp/CUSA00265/user497444003/games/com.mojang";
            } else {
                targetMojang = "/app_tmp/CUSA00265/user497444003/games/com.mojang";
            }
            report.logs.push_back("USING EXACT TARGET: " + targetMojang);
            break;
        case TargetMode::AutoDetect:
            if (!scan.detectedMojangDirs.empty()) {
                targetMojang = scan.detectedMojangDirs.front();
                report.logs.push_back("AUTO SELECTED EXISTING MOJANG: " + targetMojang);
            } else if (!scan.candidateUserDirs.empty()) {
                targetMojang = scan.candidateUserDirs.front();
                report.logs.push_back("AUTO SELECTED ACTIVE CUSA USER PATH: " + targetMojang);
            } else {
                report.statusTitle = "NOT FOUND: /app_tmp/<CUSA>/<user>/games/com.mojang";
                report.logs.push_back("ERROR: No /app_tmp/<CUSA>/<user>/games/com.mojang path was found.");
                return report;
            }
            break;
        case TargetMode::AppTmpUsa:
            targetMojang = resolveSpecificCusaMojang("/app_tmp", "CUSA00744");
            break;
        case TargetMode::AppTmpEur:
            targetMojang = resolveSpecificCusaMojang("/app_tmp", "CUSA00265");
            break;
        case TargetMode::AppTempUsa:
            targetMojang = resolveSpecificCusaMojang("/app_temp", "CUSA00744");
            break;
        case TargetMode::AppTempEur:
            targetMojang = resolveSpecificCusaMojang("/app_temp", "CUSA00265");
            break;
        case TargetMode::DataComMojang:
            targetMojang = customDataRoot;
            break;
    }

    const std::string packSubfolder = pickResourcePacksSubfolder(targetMojang, meta.kind);
    const std::string packsParentDir = targetMojang + "/" + packSubfolder;
    int mkdirErr = 0;
    if (!mkdirRecursive(packsParentDir, mkdirErr)) {
        report.statusTitle = "FAILED TO CREATE " + packSubfolder;
        report.logs.push_back("MKDIR FAILED AT " + packsParentDir + " (ERRNO " + std::to_string(mkdirErr) +
                              ": " + std::strerror(mkdirErr) + ")");
        return report;
    }

    report.resolvedMojangDir = targetMojang;
    report.destinationDir = packsParentDir + "/" + meta.uuid;

    if (isDirectory(report.destinationDir)) {
        int rmErr = 0;
        if (!removeTreeRecursive(report.destinationDir, rmErr)) {
            report.statusTitle = "FAILED TO CLEAN PREVIOUS PACK COPY";
            report.logs.push_back("RMDIR FAILED AT " + report.destinationDir + " (ERRNO " + std::to_string(rmErr) + ")");
            return report;
        }
        report.logs.push_back("CLEANED PREVIOUS INSTALL AT: " + report.destinationDir);
    }

    if (!mkdirRecursive(report.destinationDir, mkdirErr)) {
        report.statusTitle = "FAILED TO CREATE PACK FOLDER";
        report.logs.push_back("MKDIR FAILED AT " + report.destinationDir + " (ERRNO " + std::to_string(mkdirErr) + ")");
        return report;
    }

    ZipHandle handle;
    if (!mz_zip_reader_init_file(&handle.zip, archivePath.c_str(), 0)) {
        report.statusTitle = "FAILED TO REOPEN ARCHIVE";
        report.logs.push_back("Could not reopen archive for extraction.");
        return report;
    }
    handle.active = true;

    const mz_uint total = mz_zip_reader_get_num_files(&handle.zip);
    for (mz_uint i = 0; i < total; ++i) {
        mz_zip_archive_file_stat st {};
        if (!mz_zip_reader_file_stat(&handle.zip, i, &st)) {
            report.statusTitle = "ZIP STAT FAILED";
            return report;
        }
        const bool isDir = st.m_is_directory != 0;
        std::string relPath;
        if (!normalizeZipEntryPath(st.m_filename, isDir, relPath)) {
            report.statusTitle = "UNSAFE ZIP ENTRY";
            return report;
        }

        const std::string fullPath = report.destinationDir + "/" + relPath;
        if (isDir) {
            if (!mkdirRecursive(fullPath, mkdirErr)) {
                report.statusTitle = "FAILED CREATING SUBFOLDER";
                report.logs.push_back("MKDIR FAILED: " + fullPath);
                return report;
            }
            continue;
        }

        const std::size_t slash = fullPath.find_last_of('/');
        if (slash != std::string::npos) {
            const std::string parentDir = fullPath.substr(0, slash);
            if (!mkdirRecursive(parentDir, mkdirErr)) {
                report.statusTitle = "FAILED CREATING PARENT DIR";
                report.logs.push_back("MKDIR FAILED: " + parentDir);
                return report;
            }
        }

        const int fd = ::open(fullPath.c_str(), O_WRONLY | O_CREAT | O_TRUNC, 0644);
        if (fd < 0) {
            report.statusTitle = "FILE OPEN FAILED";
            report.logs.push_back("OPEN FAILED: " + fullPath + " (ERRNO " + std::to_string(errno) + ")");
            return report;
        }

        WriteState ws;
        ws.fd = fd;
        const mz_bool ok = mz_zip_reader_extract_to_callback(&handle.zip, i, writeCallback, &ws, 0);
        ::close(fd);
        if (!ok || ws.err != 0 || ws.written != st.m_uncomp_size) {
            report.statusTitle = "FILE WRITE FAILED";
            report.logs.push_back("EXTRACT FAILED: " + relPath);
            return report;
        }

        ++report.filesWritten;
        report.bytesWritten += ws.written;
    }

    report.ok = true;
    report.statusTitle = "INSTALLED SUCCESSFULLY!";
    report.logs.push_back("DEST: " + report.destinationDir);
    report.logs.push_back("WRITTEN: " + std::to_string(report.filesWritten) + " FILES (" +
                          std::to_string(report.bytesWritten / 1024) + " KB)");
    return report;
}

} // namespace pkgtest
