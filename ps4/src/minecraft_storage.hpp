#pragma once

#include "addon_importer.hpp"

#include <string>
#include <vector>

namespace ps4app::minecraft_storage {

struct ComMojangLocation {
    std::string appTemporaryRoot;
    std::string titleDirectory;
    std::string userDirectory;
    std::string comMojangDirectory;
};

enum class LocateError {
    None,
    InvalidRoot,
    NotFound,
    Ambiguous,
};

struct LocateResult {
    LocateError error = LocateError::None;
    std::string message;
    std::vector<ComMojangLocation> locations;

    bool ok() const { return error == LocateError::None && locations.size() == 1; }
};

// These are the two PS4 root names supplied for this project. The locator
// checks only these explicit roots; it never recursively scans the filesystem.
std::vector<std::string> ps4AppTemporaryRootCandidates();

// Finds only paths of the form:
//   <app-root>/<CUSA + digits>/<user + digits>/games/com.mojang
// A candidate is accepted only when both resource_packs and behavior_packs
// are existing real directories. Symlinks are not followed. Multiple matches
// are returned as Ambiguous so the caller cannot silently pick another user.
LocateResult locateComMojangDirectories(const std::vector<std::string>& appRoots);

// Creates a pack-specific destination under the explicitly resolved com.mojang
// directory. The caller must still obtain explicit approval and pass the result
// to addon_importer::importMcpack(). Mixed/unknown pack types are rejected.
bool packDestinationDirectory(const std::string& comMojangDirectory,
                              addon_importer::PackKind kind,
                              const std::string& packUuid,
                              std::string& destination,
                              std::string& error);

} // namespace ps4app::minecraft_storage
