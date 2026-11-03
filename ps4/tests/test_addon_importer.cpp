#include "addon_importer.hpp"
#include "miniz.h"

#include <cassert>
#include <cerrno>
#include <cstdio>
#include <cstdlib>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <iterator>
#include <string>
#include <sys/stat.h>
#include <unistd.h>
#include <utility>
#include <vector>

namespace {

using ArchiveEntry = std::pair<std::string, std::string>;

std::string makeTempDirectory() {
    char pattern[] = "/tmp/addonstation-import-test-XXXXXX";
    char* path = ::mkdtemp(pattern);
    assert(path != nullptr);
    return path;
}

void writeArchive(const std::string& path, const std::vector<ArchiveEntry>& entries) {
    mz_zip_archive zip;
    mz_zip_zero_struct(&zip);
    assert(mz_zip_writer_init_file(&zip, path.c_str(), 0));
    for (const ArchiveEntry& entry : entries) {
        assert(mz_zip_writer_add_mem(&zip, entry.first.c_str(), entry.second.data(),
                                     entry.second.size(), MZ_BEST_SPEED));
    }
    assert(mz_zip_writer_finalize_archive(&zip));
    assert(mz_zip_writer_end(&zip));
}

std::string validManifest() {
    return "{"
           "\"format_version\":2,"
           "\"header\":{"
             "\"name\":\"Host Test Pack\","
             "\"uuid\":\"123e4567-e89b-12d3-a456-426614174000\","
             "\"version\":[1,0,0]"
           "},"
           "\"modules\":[{"
             "\"type\":\"resources\","
             "\"uuid\":\"123e4567-e89b-12d3-a456-426614174001\","
             "\"version\":[1,0,0]"
           "}]"
           "}";
}

bool pathExists(const std::string& path) {
    struct stat status {};
    return ::lstat(path.c_str(), &status) == 0;
}

std::string readTextFile(const std::string& path) {
    std::ifstream input(path.c_str(), std::ios::binary);
    assert(input.good());
    return std::string(std::istreambuf_iterator<char>(input), std::istreambuf_iterator<char>());
}

void testReadOnlyInspectionAndApprovalGate(const std::string& root) {
    const std::string archive = root + "/resource.mcpack";
    const std::string destination = root + "/not-created-without-approval";
    writeArchive(archive, {
        {"manifest.json", validManifest()},
        {"textures/items/sample.txt", "read-only inspection fixture"},
    });

    const ps4app::addon_importer::Inspection inspection =
        ps4app::addon_importer::inspectMcpack(archive);
    assert(inspection.ok());
    assert(inspection.packName == "Host Test Pack");
    assert(inspection.packUuid == "123e4567-e89b-12d3-a456-426614174000");
    assert(inspection.packKind == ps4app::addon_importer::PackKind::Resource);
    assert(inspection.fileCount == 2);
    assert(inspection.expandedBytes > validManifest().size());
    assert(!pathExists(destination));

    ps4app::addon_importer::ImportRequest request;
    request.archivePath = archive;
    request.destinationDirectory = destination;
    request.writeApproved = false;
    const ps4app::addon_importer::ImportResult result =
        ps4app::addon_importer::importMcpack(request);
    assert(!result.ok());
    assert(result.error == ps4app::addon_importer::Error::ApprovalRequired);
    assert(!pathExists(destination));
}

void testLegacyClientDataModuleAccepted(const std::string& root) {
    std::string manifest = validManifest();
    const std::string resourceType = "\"type\":\"resources\"";
    const std::string clientDataType = "\"type\":\"client_data\"";
    const std::size_t typePosition = manifest.find(resourceType);
    assert(typePosition != std::string::npos);
    manifest.replace(typePosition, resourceType.size(), clientDataType);

    const std::string archive = root + "/legacy-client-data.mcpack";
    writeArchive(archive, {{"manifest.json", manifest}});
    const ps4app::addon_importer::Inspection inspection =
        ps4app::addon_importer::inspectMcpack(archive);
    assert(inspection.ok());
    assert(inspection.packKind == ps4app::addon_importer::PackKind::Behavior);
}

void testSuccessfulExplicitImport(const std::string& root) {
    const std::string archive = root + "/valid.mcpack";
    const std::string destination = root + "/new-pack-directory";
    const std::string payload = "bedrock pack payload\n";
    writeArchive(archive, {
        {"manifest.json", validManifest()},
        {"textures/items/sample.txt", payload},
    });

    ps4app::addon_importer::ImportRequest request;
    request.archivePath = archive;
    request.destinationDirectory = destination;
    request.writeApproved = true;
    const ps4app::addon_importer::ImportResult result =
        ps4app::addon_importer::importMcpack(request);
    if (!result.ok()) {
        std::cerr << "import error=" << ps4app::addon_importer::errorName(result.error)
                  << " errno=" << result.systemError
                  << " zip=" << result.archiveError
                  << " rollback=" << result.rollbackError
                  << " message=" << result.message << '\n';
    }
    assert(result.ok());
    assert(result.filesWritten == 2);
    assert(result.bytesWritten == validManifest().size() + payload.size());
    assert(readTextFile(destination + "/manifest.json") == validManifest());
    assert(readTextFile(destination + "/textures/items/sample.txt") == payload);

    request.destinationDirectory = destination;
    const ps4app::addon_importer::ImportResult second =
        ps4app::addon_importer::importMcpack(request);
    assert(!second.ok());
    assert(second.error == ps4app::addon_importer::Error::DestinationAlreadyExists);
    assert(readTextFile(destination + "/textures/items/sample.txt") == payload);
}

void testTraversalRejectedBeforeWrites(const std::string& root) {
    const std::string archive = root + "/unsafe.mcpack";
    const std::string destination = root + "/unsafe-destination";
    const std::string outside = root + "/escape.txt";
    writeArchive(archive, {
        {"manifest.json", validManifest()},
        {"../escape.txt", "must never be written"},
    });

    ps4app::addon_importer::ImportRequest request;
    request.archivePath = archive;
    request.destinationDirectory = destination;
    request.writeApproved = true;
    const ps4app::addon_importer::ImportResult result =
        ps4app::addon_importer::importMcpack(request);
    assert(!result.ok());
    assert(result.error == ps4app::addon_importer::Error::UnsafeArchivePath);
    assert(!pathExists(destination));
    assert(!pathExists(outside));
}

void testExistingDestinationAndUnsupportedContainer(const std::string& root) {
    const std::string archive = root + "/existing.mcpack";
    const std::string destination = root + "/already-exists";
    writeArchive(archive, {{"manifest.json", validManifest()}});
    assert(::mkdir(destination.c_str(), 0755) == 0);
    {
        std::ofstream marker((destination + "/marker.txt").c_str(), std::ios::binary);
        marker << "preserve";
    }

    ps4app::addon_importer::ImportRequest request;
    request.archivePath = archive;
    request.destinationDirectory = destination;
    request.writeApproved = true;
    const ps4app::addon_importer::ImportResult result =
        ps4app::addon_importer::importMcpack(request);
    assert(!result.ok());
    assert(result.error == ps4app::addon_importer::Error::DestinationAlreadyExists);
    assert(readTextFile(destination + "/marker.txt") == "preserve");

    const ps4app::addon_importer::Inspection unsupported =
        ps4app::addon_importer::inspectMcpack(root + "/bundle.mcaddon");
    assert(!unsupported.ok());
    assert(unsupported.error == ps4app::addon_importer::Error::UnsupportedContainer);
}

void testInvalidArchiveReportsSystemOrZipError(const std::string& root) {
    const std::string archive = root + "/broken.mcpack";
    {
        std::ofstream output(archive.c_str(), std::ios::binary);
        output << "not a zip archive";
    }
    const ps4app::addon_importer::Inspection result =
        ps4app::addon_importer::inspectMcpack(archive);
    assert(!result.ok());
    assert(result.error == ps4app::addon_importer::Error::InvalidArchive);
    assert(result.systemError != 0 || result.archiveError != 0);
}

} // namespace

int main() {
    const std::string root = makeTempDirectory();
    testReadOnlyInspectionAndApprovalGate(root);
    testLegacyClientDataModuleAccepted(root);
    testSuccessfulExplicitImport(root);
    testTraversalRejectedBeforeWrites(root);
    testExistingDestinationAndUnsupportedContainer(root);
    testInvalidArchiveReportsSystemOrZipError(root);
    std::filesystem::remove_all(root);
    std::cout << "PS4 add-on importer host tests passed (temporary host paths only).\n";
    return 0;
}
