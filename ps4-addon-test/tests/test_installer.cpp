#include "installer.hpp"

#include <cassert>
#include <filesystem>
#include <iostream>
#include <string>
#include <vector>
#include <unistd.h>

namespace fs = std::filesystem;

namespace {

std::string makeTempDir() {
    std::string pattern = (fs::temp_directory_path() / "pkg-addon-test-XXXXXX").string();
    std::vector<char> buf(pattern.begin(), pattern.end());
    buf.push_back('\0');
    char* res = ::mkdtemp(buf.data());
    assert(res != nullptr);
    return res;
}

} // namespace

int main() {
    const std::string archive = "assets/PrizmaVisuals-1.3.10.mcpack";
    const pkgtest::PackMetadata meta = pkgtest::inspectMcpackFile(archive);
    assert(meta.valid);
    assert(meta.name == "PrizmaVisuals 1.3.10");
    assert(meta.uuid == "3bcccbf1-ff4d-43a5-8452-12b9534dd70d");
    assert(meta.kind == pkgtest::PackKind::Resource);
    assert(meta.fileCount == 8806);

    const fs::path tempRoot(makeTempDir());
    const fs::path appTmp = tempRoot / "app_tmp";
    const fs::path dataMojang = tempRoot / "data" / "com.mojang";

    // Case 1: Active CUSA00744 + hex user folder WITHOUT pre-existing games/com.mojang
    fs::create_directories(appTmp / "CUSA00744" / "1000000a");
    const pkgtest::InstallReport firstInstall = pkgtest::installMcpack(
        archive,
        pkgtest::TargetMode::AutoDetect,
        {appTmp.string()},
        dataMojang.string());
    assert(firstInstall.ok);
    assert(firstInstall.filesWritten == 8806);
    const fs::path expectedDest =
        appTmp / "CUSA00744" / "1000000a" / "games" / "com.mojang" / "resource_packs" / "PrizmaVisuals 1.3.10";
    assert(firstInstall.destinationDir == expectedDest.string());
    assert(fs::exists(expectedDest / "manifest.json"));
    assert(fs::exists(expectedDest / "pack_icon.png"));

    // Case 2: Pressing Install a second time overwrites cleanly without failing
    const pkgtest::InstallReport secondInstall = pkgtest::installMcpack(
        archive,
        pkgtest::TargetMode::AutoDetect,
        {appTmp.string()},
        dataMojang.string());
    assert(secondInstall.ok);
    assert(secondInstall.filesWritten == 8806);
    assert(fs::exists(expectedDest / "manifest.json"));

    // Case 3: Existing "resource packs" (with space) under com.mojang
    const fs::path appTmpSpace = tempRoot / "app_tmp_space";
    fs::create_directories(appTmpSpace / "CUSA00265" / "user123456" / "games" / "com.mojang" / "resource packs");
    const pkgtest::InstallReport spaceInstall = pkgtest::installMcpack(
        archive,
        pkgtest::TargetMode::AutoDetect,
        {appTmpSpace.string()},
        dataMojang.string());
    assert(spaceInstall.ok);
    assert(spaceInstall.filesWritten == 8806);
    assert(fs::exists(appTmpSpace / "CUSA00265" / "user123456" / "games" / "com.mojang" / "resource packs" / "PrizmaVisuals 1.3.10" / "manifest.json"));

    // Case 4: Direct /data/com.mojang mode
    const pkgtest::InstallReport dataInstall = pkgtest::installMcpack(
        archive,
        pkgtest::TargetMode::DataComMojang,
        {appTmp.string()},
        dataMojang.string());
    assert(dataInstall.ok);
    assert(dataInstall.filesWritten == 8806);
    assert(fs::exists(dataMojang / "resource_packs" / "PrizmaVisuals 1.3.10" / "manifest.json"));

    fs::remove_all(tempRoot);
    std::cout << "pkg addon test (PrizmaVisuals 1.3.10) host tests passed (8806 files verified).\n";
    return 0;
}
