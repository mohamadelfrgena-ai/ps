#pragma once

#include <cstdint>
#include <string>

namespace ps4app::addon_importer {

enum class PackKind {
    Unknown,
    Resource,
    Behavior,
    Mixed,
};

enum class Error {
    None,
    ApprovalRequired,
    InvalidRequest,
    UnsupportedContainer,
    SourceOpenFailed,
    SourceTooLarge,
    InvalidArchive,
    TooManyEntries,
    ExpansionLimitExceeded,
    UnsupportedArchiveEntry,
    UnsafeArchivePath,
    DuplicateArchivePath,
    ManifestMissing,
    ManifestInvalid,
    DestinationAlreadyExists,
    DestinationCreateFailed,
    EntryCreateFailed,
    EntryWriteFailed,
    ArchiveExtractFailed,
    EntryCloseFailed,
    RollbackFailed,
};

struct Limits {
    std::uint32_t maxEntries = 16384;
    std::uint64_t maxArchiveBytes = 512ULL * 1024ULL * 1024ULL;
    std::uint64_t maxExpandedBytes = 512ULL * 1024ULL * 1024ULL;
    std::uint64_t maxEntryBytes = 128ULL * 1024ULL * 1024ULL;
    std::uint64_t maxManifestBytes = 1024ULL * 1024ULL;
};

struct Inspection {
    Error error = Error::None;
    int systemError = 0;
    int archiveError = 0;
    std::string message;
    std::string packName;
    std::string packUuid;
    PackKind packKind = PackKind::Unknown;
    std::uint32_t entryCount = 0;
    std::uint32_t fileCount = 0;
    std::uint64_t expandedBytes = 0;

    bool ok() const { return error == Error::None; }
};

struct ImportRequest {
    // Both paths must be explicit absolute POSIX paths supplied by a caller.
    // This module deliberately does not discover USB mounts or game directories.
    std::string archivePath;
    std::string destinationDirectory;
    bool writeApproved = false;
    Limits limits;
};

struct ImportResult {
    Inspection inspection;
    Error error = Error::None;
    int systemError = 0;
    int archiveError = 0;
    int rollbackError = 0;
    std::string message;
    std::uint32_t filesWritten = 0;
    std::uint64_t bytesWritten = 0;

    bool ok() const { return error == Error::None; }
};

// Read-only preflight. Accepts one .mcpack ZIP with a root manifest.json;
// does not create files or directories.
Inspection inspectMcpack(const std::string& archivePath, const Limits& limits = Limits{});

// Extracts a single validated .mcpack into a NEW destination directory.
// No path is guessed and no existing destination is overwritten. The caller
// must set writeApproved only after its own explicit confirmation.
ImportResult importMcpack(const ImportRequest& request);

const char* errorName(Error error);
const char* packKindName(PackKind kind);

} // namespace ps4app::addon_importer
