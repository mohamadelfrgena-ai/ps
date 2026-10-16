#include "installer.hpp"

#include <cassert>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fstream>
#include <string>
#include <sys/stat.h>
#include <unistd.h>

namespace {

bool fileExists(const std::string& path) {
    struct stat st {};
    return ::stat(path.c_str(), &st) == 0 && S_ISREG(st.st_mode);
}

void writeTextFile(const std::string& path, const std::string& content) {
    std::ofstream out(path, std::ios::binary);
    assert(out.good());
    out.write(content.data(), static_cast<std::streamsize>(content.size()));
    assert(out.good());
}

} // namespace

int main() {
    // 1. Verify 8-language notification & phase strings
    assert(pkgtest::formatEnjoyNotification("PrizmaVisuals", "ar") ==
           "تم تثبيت PrizmaVisuals — استمتع!");
    assert(pkgtest::formatEnjoyNotification("PrizmaVisuals", "en") ==
           "PrizmaVisuals installed — Enjoy!");
    assert(pkgtest::formatEnjoyNotification("PrizmaVisuals", "fr") ==
           "PrizmaVisuals installé — Profitez-en !");
    assert(pkgtest::formatEnjoyNotification("PrizmaVisuals", "tr") ==
           "PrizmaVisuals yüklendi — Keyfini çıkarın!");
    assert(pkgtest::formatEnjoyNotification("PrizmaVisuals", "es") ==
           "PrizmaVisuals instalado — ¡Disfruta!");
    assert(pkgtest::formatEnjoyNotification("PrizmaVisuals", "ru") ==
           "PrizmaVisuals установлен — Приятной игры!");
    assert(pkgtest::formatEnjoyNotification("PrizmaVisuals", "pt") ==
           "PrizmaVisuals instalado — Aproveite!");
    assert(pkgtest::formatEnjoyNotification("PrizmaVisuals", "zh") ==
           "PrizmaVisuals 已安装 — 尽情畅玩吧！");

    assert(pkgtest::formatInstallPhaseLabel(pkgtest::InstallPhase::Downloading, "en") == "Downloading...");
    assert(pkgtest::formatInstallPhaseLabel(pkgtest::InstallPhase::Installing, "ar") == "جارٍ التثبيت...");
    assert(pkgtest::formatInstallPhaseLabel(pkgtest::InstallPhase::Installed, "en") == "Installed ✓");

    char tempTemplate[] = "/tmp/pkg-addon-test-XXXXXX";
    char* rootPtr = ::mkdtemp(tempTemplate);
    assert(rootPtr != nullptr);
    const std::string root(rootPtr);

    const std::string srcAddon = root + "/src/PrizmaVisuals 1.3.10222";
    assert(::mkdir((root + "/src").c_str(), 0777) == 0);
    assert(::mkdir(srcAddon.c_str(), 0777) == 0);
    assert(::mkdir((srcAddon + "/textures").c_str(), 0777) == 0);

    writeTextFile(srcAddon + "/manifest.json", "{\"format_version\":2,\"header\":{\"name\":\"PrizmaVisuals\"}}");
    writeTextFile(srcAddon + "/textures/terrain_texture.json", "{\"resource_pack_name\":\"prizma\"}");

    const std::string dstPack =
        root + "/app_tmp/CUSA00744/user987654321/games/com.mojang/resource_packs/PrizmaVisuals 1.3.10222";

    const pkgtest::CopyResult res = pkgtest::copyAddonFolder(srcAddon, dstPack);
    assert(res.ok);
    assert(res.filesCopied == 2);
    assert(fileExists(dstPack + "/manifest.json"));
    assert(fileExists(dstPack + "/textures/terrain_texture.json"));

    struct stat zipSt {};
    if (::stat("assets/PrizmaVisuals-1.3.10222.zip", &zipSt) == 0) {
        const int rc = std::system("python3 make_gp4.py assets/folder.raw");
        assert(rc == 0);

        const std::string stageDir = root + "/data_stage";
        const std::string closedMojangDir =
            root + "/closed_mc/CUSA00744/user123456789/games/com.mojang";

        // Mode 1: Minecraft is CLOSED (closedMojangDir does NOT exist yet)
        const pkgtest::CopyResult queuedRes = pkgtest::installOrQueueAddon(
            "assets/folder.raw",
            "PrizmaVisuals 1.3.10222",
            stageDir,
            closedMojangDir,
            "resource_packs",
            "ar");
        assert(queuedRes.ok);
        assert(queuedRes.queuedForMinecraft);
        assert(fileExists(stageDir + "/PrizmaVisuals 1.3.10222.raw"));
        assert(fileExists(stageDir + "/PrizmaVisuals 1.3.10222.pending"));

        // Simulate Minecraft opening and mounting closedMojangDir
        const std::string mkCmd = "mkdir -p \"" + closedMojangDir + "\"";
        assert(std::system(mkCmd.c_str()) == 0);

        const pkgtest::CopyResult syncRes = pkgtest::syncPendingAddonsIfMounted(
            stageDir,
            closedMojangDir,
            "ar");
        assert(syncRes.ok);
        assert(syncRes.filesCopied == 8805);
        assert(fileExists(closedMojangDir + "/resource_packs/PrizmaVisuals 1.3.10222/manifest.json"));
        // Verify automatic cleanup of both .pending AND the large .raw file!
        assert(!fileExists(stageDir + "/PrizmaVisuals 1.3.10222.pending"));
        assert(!fileExists(stageDir + "/PrizmaVisuals 1.3.10222.raw"));
        assert(pkgtest::isAddonMarkedInstalled("PrizmaVisuals 1.3.10222", stageDir));

        // Mode 2: Minecraft is ALREADY OPEN
        const std::string openMojangDir =
            root + "/open_mc/CUSA00283/user555666777/games/com.mojang";
        const std::string mkOpenCmd = "mkdir -p \"" + openMojangDir + "\"";
        assert(std::system(mkOpenCmd.c_str()) == 0);

        const pkgtest::CopyResult immediateRes = pkgtest::installOrQueueAddon(
            "assets/folder.raw",
            "PrizmaVisuals 1.3.10222",
            stageDir,
            openMojangDir,
            "resource_packs",
            "en");
        assert(immediateRes.ok);
        assert(!immediateRes.queuedForMinecraft);
        assert(immediateRes.filesCopied == 8805);
        assert(fileExists(openMojangDir + "/resource_packs/PrizmaVisuals 1.3.10222/manifest.json"));
        assert(!fileExists(stageDir + "/PrizmaVisuals 1.3.10222.raw"));
    }

    std::printf("pkg addon test dual-mode + dynamic CUSA/user + cleanup + 8-lang host tests passed.\n");
    return 0;
}
