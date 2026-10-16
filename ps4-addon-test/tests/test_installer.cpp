#include "installer.hpp"

#include <cassert>
#include <cstdlib>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <string>
#include <vector>
#include <unistd.h>

namespace fs = std::filesystem;

namespace {

std::string makeTempDir() {
    std::string pattern = (fs::temp_directory_path() / "pkg-addon-copy-XXXXXX").string();
    std::vector<char> buf(pattern.begin(), pattern.end());
    buf.push_back('\0');
    char* res = ::mkdtemp(buf.data());
    assert(res != nullptr);
    return res;
}

} // namespace

int main() {
    const fs::path root(makeTempDir());
    const fs::path src = root / "app0" / "PrizmaVisuals 1.3.10222";
    const fs::path dst = root / "app_tmp" / "CUSA00265" / "user497444003" / "games" /
                         "com.mojang" / "resource_packs" / "PrizmaVisuals 1.3.10222";

    fs::create_directories(src / "textures" / "blocks");
    {
        std::ofstream f1(src / "manifest.json");
        f1 << "{\"format_version\":2}";
        std::ofstream f2(src / "textures" / "blocks" / "stone.png");
        f2 << "pngdata";
    }

    const pkgtest::CopyResult res = pkgtest::copyAddonFolder(src.string(), dst.string());
    assert(res.ok);
    assert(res.filesCopied == 2);
    assert(fs::exists(dst / "manifest.json"));
    assert(fs::exists(dst / "textures" / "blocks" / "stone.png"));

    if (fs::exists("assets/PrizmaVisuals-1.3.10222.zip")) {
        const int rc = std::system("python3 make_gp4.py assets/folder.raw");
        assert(rc == 0);

        const fs::path stageDir = root / "data" / "AddonStation" / "installed";
        const fs::path mojangDir =
            root / "sim_app_tmp" / "CUSA00265" / "user497444003" / "games" / "com.mojang";

        // 1. Test when Minecraft is CLOSED (mojangDir does not exist yet):
        const pkgtest::CopyResult queuedRes = pkgtest::installOrQueueAddon(
            "assets/folder.raw",
            "PrizmaVisuals 1.3.10222",
            stageDir.string(),
            mojangDir.string());
        assert(queuedRes.ok);
        assert(queuedRes.queuedForMinecraft);
        assert(queuedRes.launchedMinecraft);
        assert(fs::exists(stageDir / "PrizmaVisuals 1.3.10222.raw"));
        assert(fs::exists(stageDir / "PrizmaVisuals 1.3.10222.pending"));

        // 2. Simulate Minecraft launching and mounting com.mojang:
        fs::create_directories(mojangDir);
        const pkgtest::CopyResult syncRes =
            pkgtest::syncPendingAddonsIfMounted(stageDir.string(), mojangDir.string());
        assert(syncRes.ok);
        assert(syncRes.filesCopied == 8805);
        assert(!fs::exists(stageDir / "PrizmaVisuals 1.3.10222.pending"));
        assert(fs::exists(mojangDir / "resource_packs" / "PrizmaVisuals 1.3.10222" / "manifest.json"));

        // 3. Test when Minecraft is ALREADY OPEN (mojangDir already exists):
        const pkgtest::CopyResult directRes = pkgtest::installOrQueueAddon(
            "assets/folder.raw",
            "PrizmaVisuals 1.3.10222",
            stageDir.string(),
            mojangDir.string());
        assert(directRes.ok);
        assert(!directRes.queuedForMinecraft);
        assert(directRes.filesCopied == 8805);

        fs::remove("assets/folder.raw");
    }

    fs::remove_all(root);
    std::cout << "pkg addon test dual-mode (Minecraft closed + open) host tests passed.\n";
    return 0;
}
