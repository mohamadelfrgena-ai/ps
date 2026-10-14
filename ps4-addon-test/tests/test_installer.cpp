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
    const std::string archive = "../ps4/assets/Backrooms-1.1-B.mcpack";
    const pkgtest::PackMetadata meta = pkgtest::inspectMcpackFile(archive);
    assert(meta.valid);
    assert(meta.uuid == "29661a5a-ef24-0c27-ad26-e3f2260f0d55");
    assert(meta.kind == pkgtest::PackKind::Resource);
    assert(meta.fileCount == 2705);

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
    assert(firstInstall.filesWritten == 2705);
    const fs::path expectedDest =
        appTmp / "CUSA00744" / "1000000a" / "games" / "com.mojang" / "resource_packs" / meta.uuid;
    assert(firstInstall.destinationDir == expectedDest.string());
    assert(fs::exists(expectedDest / "manifest.json"));

    // Case 2: Pressing Install a second time overwrites cleanly without failing
    const pkgtest::InstallReport secondInstall = pkgtest::installMcpack(
        archive,
        pkgtest::TargetMode::AutoDetect,
        {appTmp.string()},
        dataMojang.string());
    assert(secondInstall.ok);
    assert(secondInstall.filesWritten == 2705);
    assert(fs::exists(expectedDest / "manifest.json"));

    // Case 3: Direct /data/com.mojang mode
    const pkgtest::InstallReport dataInstall = pkgtest::installMcpack(
        archive,
        pkgtest::TargetMode::DataComMojang,
        {appTmp.string()},
        dataMojang.string());
    assert(dataInstall.ok);
    assert(dataInstall.filesWritten == 2705);
    assert(fs::exists(dataMojang / "resource_packs" / meta.uuid / "manifest.json"));

    fs::remove_all(tempRoot);
    std::cout << "pkg addon test host tests passed (2705 files verified).\n";
    return 0;
}
