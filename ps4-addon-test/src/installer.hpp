#pragma once

#include <cstdint>
#include <string>
#include <vector>

namespace pkgtest {

enum class PackKind {
    Unknown,
    Resource,
    Behavior,
    Mixed,
};

enum class TargetMode {
    ExactUserPath = 0,
    AutoDetect = 1,
    AppTmpUsa = 2,
    AppTmpEur = 3,
    AppTempUsa = 4,
    AppTempEur = 5,
    DataComMojang = 6,
};

struct PackMetadata {
    bool valid = false;
    std::string error;
    std::string name;
    std::string uuid;
    PackKind kind = PackKind::Unknown;
    std::uint32_t fileCount = 0;
    std::uint64_t totalBytes = 0;
};

struct ScanReport {
    std::vector<std::string> detectedMojangDirs;
    std::vector<std::string> candidateUserDirs;
    std::vector<std::string> diagnosticLines;
};

struct InstallReport {
    bool ok = false;
    std::string statusTitle;
    std::string resolvedMojangDir;
    std::string destinationDir;
    std::uint32_t filesWritten = 0;
    std::uint64_t bytesWritten = 0;
    std::vector<std::string> logs;
};

const char* targetModeLabel(TargetMode mode);
const char* targetModePathPreview(TargetMode mode);
TargetMode nextTargetMode(TargetMode mode, int delta);

PackMetadata inspectMcpackFile(const std::string& archivePath);

ScanReport scanFilesystemForMinecraft(const std::vector<std::string>& customRoots = {});

InstallReport installMcpack(const std::string& archivePath,
                            TargetMode mode,
                            const std::vector<std::string>& customRoots = {},
                            const std::string& customDataRoot = "/data/com.mojang");

} // namespace pkgtest
