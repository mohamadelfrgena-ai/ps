#include "installer.hpp"
#include "supabase_client.hpp"

#include <cassert>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fstream>
#include <map>
#include <string>
#include <sys/stat.h>
#include <unistd.h>
#include <vector>

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

// Builds a tiny PVRAW001 stream (same format the store pipeline produces)
void writeTestRaw(const std::string& rawPath,
                  const std::vector<std::string>& dirs,
                  const std::vector<std::pair<std::string, std::string>>& files) {
    std::ofstream out(rawPath, std::ios::binary);
    assert(out.good());
    out.write("PVRAW001", 8);
    const std::uint32_t dirCount = static_cast<std::uint32_t>(dirs.size());
    out.write(reinterpret_cast<const char*>(&dirCount), sizeof(dirCount));
    for (const std::string& d : dirs) {
        const std::uint16_t len = static_cast<std::uint16_t>(d.size());
        out.write(reinterpret_cast<const char*>(&len), sizeof(len));
        out.write(d.data(), len);
    }
    const std::uint32_t fileCount = static_cast<std::uint32_t>(files.size());
    out.write(reinterpret_cast<const char*>(&fileCount), sizeof(fileCount));
    for (const auto& f : files) {
        const std::uint16_t len = static_cast<std::uint16_t>(f.first.size());
        const std::uint64_t size = f.second.size();
        out.write(reinterpret_cast<const char*>(&len), sizeof(len));
        out.write(reinterpret_cast<const char*>(&size), sizeof(size));
        out.write(f.first.data(), len);
        out.write(f.second.data(), static_cast<std::streamsize>(size));
    }
}

} // namespace

int main() {
    // 1. 8-language notification & phase strings
    assert(pkgtest::formatEnjoyNotification("My Pack", "ar") == "تم تثبيت My Pack — استمتع!");
    assert(pkgtest::formatEnjoyNotification("My Pack", "en") == "My Pack installed — Enjoy!");
    assert(pkgtest::formatEnjoyNotification("My Pack", "fr") == "My Pack installé — Profitez-en !");
    assert(pkgtest::formatEnjoyNotification("My Pack", "tr") == "My Pack yüklendi — Keyfini çıkarın!");
    assert(pkgtest::formatEnjoyNotification("My Pack", "es") == "My Pack instalado — ¡Disfruta!");
    assert(pkgtest::formatEnjoyNotification("My Pack", "ru") == "My Pack установлен — Приятной игры!");
    assert(pkgtest::formatEnjoyNotification("My Pack", "pt") == "My Pack instalado — Aproveite!");
    assert(pkgtest::formatEnjoyNotification("My Pack", "zh") == "My Pack 已安装 — 尽情畅玩吧！");

    assert(pkgtest::formatInstallPhaseLabel(pkgtest::InstallPhase::Downloading, "en") == "Downloading...");
    assert(pkgtest::formatInstallPhaseLabel(pkgtest::InstallPhase::Installing, "ar") == "جارٍ التثبيت...");
    assert(pkgtest::formatInstallPhaseLabel(pkgtest::InstallPhase::Installed, "en") == "Installed ✓");

    char tempTemplate[] = "/tmp/pkg-addon-test-XXXXXX";
    char* rootPtr = ::mkdtemp(tempTemplate);
    assert(rootPtr != nullptr);
    const std::string root(rootPtr);

    // 2. copyAddonFolder from an extracted folder
    const std::string srcAddon = root + "/src/My Pack 1.0";
    assert(::mkdir((root + "/src").c_str(), 0777) == 0);
    assert(::mkdir(srcAddon.c_str(), 0777) == 0);
    assert(::mkdir((srcAddon + "/textures").c_str(), 0777) == 0);
    writeTextFile(srcAddon + "/manifest.json", "{\"format_version\":2,\"header\":{\"name\":\"My Pack\"}}");
    writeTextFile(srcAddon + "/textures/terrain_texture.json", "{\"resource_pack_name\":\"my-pack\"}");

    const std::string dstPack =
        root + "/app_tmp/CUSA00744/user987654321/games/com.mojang/resource_packs/My Pack 1.0";

    const pkgtest::CopyResult res = pkgtest::copyAddonFolder(srcAddon, dstPack);
    assert(res.ok);
    assert(res.filesCopied == 2);
    assert(fileExists(dstPack + "/manifest.json"));
    assert(fileExists(dstPack + "/textures/terrain_texture.json"));

    // 3. Build a synthetic .raw (store pipeline format) — no external assets needed
    const std::string testRaw = root + "/my-pack-1.0.raw";
    writeTestRaw(testRaw,
                 {"textures"},
                 {
                     {"manifest.json", "{\"header\":{\"name\":\"My Pack\"}}"},
                     {"textures/terrain_texture.json", "{\"resource_pack_name\":\"my-pack\"}"},
                 });

    const std::string stageDir = root + "/data_stage";

    // 4. Mode 1: Minecraft is CLOSED -> v0.67 BOTH-STATES («النظام عندما تكون اللعبة
    //    مغلق او مفتوح» + «ونظام التخزين ... ينقلها لملف اللعبة»): the pack is STAGED in
    //    the hidden /data staging and syncPendingAddonsIfMounted() transfers it into the
    //    game folder when the game opens.
    {
        const std::string closedMojangDir =
            root + "/closed_mc/CUSA00744/user123456789/games/com.mojang";

        const pkgtest::CopyResult queuedRes = pkgtest::installOrQueueAddon(
            testRaw, "My Pack 1.0", stageDir, closedMojangDir, "resource_packs", "ar");
        assert(queuedRes.ok);                   // saved...
        assert(queuedRes.queuedForMinecraft);   // ...and queued for the game
        assert(queuedRes.errorCode == 0);
        assert(fileExists(stageDir + "/My Pack 1.0.raw"));       // staged raw (hidden)
        assert(fileExists(stageDir + "/My Pack 1.0.pending"));   // pending flag

        // legacy back-compat: a pack staged by an OLD app version still completes
        // once the game is mounted (syncPendingAddonsIfMounted keeps working).
        writeTestRaw(stageDir + "/My Pack 1.0.raw",
                     {"textures"},
                     {
                         {"manifest.json", "{\"header\":{\"name\":\"My Pack\"}}"},
                         {"textures/terrain_texture.json", "{\"resource_pack_name\":\"my-pack\"}"},
                     });
        writeTextFile(stageDir + "/My Pack 1.0.pending", "resource_packs");

        const std::string mkCmd = "mkdir -p \"" + closedMojangDir + "\"";
        assert(std::system(mkCmd.c_str()) == 0);

        const pkgtest::CopyResult syncRes =
            pkgtest::syncPendingAddonsIfMounted(stageDir, closedMojangDir, "ar");
        assert(syncRes.ok);
        assert(syncRes.filesCopied == 2);
        assert(fileExists(closedMojangDir + "/resource_packs/My Pack 1.0/manifest.json"));
        assert(!fileExists(stageDir + "/My Pack 1.0.pending"));
        assert(!fileExists(stageDir + "/My Pack 1.0.raw"));
        assert(pkgtest::isAddonMarkedInstalled("My Pack 1.0", stageDir));
    }

    // 5. Mode 2: Minecraft is OPEN (downloaded source lives in stageDir -> deleted after)
    {
        const std::string openMojangDir =
            root + "/open_mc/CUSA00283/user555666777/games/com.mojang";
        const std::string mkOpenCmd = "mkdir -p \"" + openMojangDir + "\"";
        assert(std::system(mkOpenCmd.c_str()) == 0);

        const std::string downloadedRaw = stageDir + "/My Pack 1.0.raw";
        writeTestRaw(downloadedRaw,
                     {"textures"},
                     {
                         {"manifest.json", "{\"header\":{\"name\":\"My Pack\"}}"},
                         {"textures/terrain_texture.json", "{\"resource_pack_name\":\"my-pack\"}"},
                     });

        const pkgtest::CopyResult immediateRes = pkgtest::installOrQueueAddon(
            downloadedRaw, "My Pack 1.0", stageDir, openMojangDir, "resource_packs", "en");
        assert(immediateRes.ok);
        assert(!immediateRes.queuedForMinecraft);
        assert(immediateRes.filesCopied == 2);
        assert(fileExists(openMojangDir + "/resource_packs/My Pack 1.0/manifest.json"));
        assert(!fileExists(downloadedRaw));
    }

    // 6. Auto-launch setting round-trip («والخيار الذي في الاعداات ارجعه»): the
    //    Settings toggle is back. With the game closed both cases QUEUE (v0.67
    //    both-states); autoLaunch=true additionally launches Minecraft so the staged
    //    pack transfers at once.
    {
        const std::string cfg = root + "/autolaunch.cfg";
        assert(!pkgtest::loadAutoLaunchSetting(cfg));                 // default OFF
        assert(pkgtest::saveAutoLaunchSetting(true, cfg));
        assert(pkgtest::loadAutoLaunchSetting(cfg));
        assert(pkgtest::saveAutoLaunchSetting(false, cfg));
        assert(!pkgtest::loadAutoLaunchSetting(cfg));

        const std::string autoMojangDir =
            root + "/auto_mc/CUSA00265/user497444003/games/com.mojang";
        const pkgtest::CopyResult autoRes = pkgtest::installOrQueueAddon(
            testRaw, "My Pack 1.0", stageDir, autoMojangDir, "resource_packs", "en", nullptr, true);
        assert(autoRes.ok);                   // staged + queued
        assert(autoRes.queuedForMinecraft);
        assert(autoRes.launchedMinecraft);    // autoLaunch=true -> Minecraft opens (host stub = true)

        const pkgtest::CopyResult noAutoRes = pkgtest::installOrQueueAddon(
            testRaw, "My Pack 1.0", stageDir, autoMojangDir, "resource_packs", "en");
        assert(noAutoRes.ok);
        assert(noAutoRes.queuedForMinecraft);
        assert(!noAutoRes.launchedMinecraft); // default OFF -> the user opens the game
    }

    // 7. Supabase JSON helpers + array splitting (content pipeline parsing)
    {
        const std::string created =
            "{\"ok\":true,\"code\":\"K7P4QX\",\"formatted_code\":\"K7P 4QX\","
            "\"poll_token\":\"3f1c9a20-1234-4abc-9def-000000000001\","
            "\"expires_at\":\"2026-10-16T20:00:00Z\",\"expires_in_seconds\":600}";
        assert(pkgtest::jsonGetBool(created, "ok"));
        assert(pkgtest::jsonGetString(created, "code") == "K7P4QX");
        assert(pkgtest::jsonGetString(created, "formatted_code") == "K7P 4QX");
        assert(pkgtest::jsonGetInt(created, "expires_in_seconds", 0) == 600);

        const std::string linked =
            "{\"ok\":true,\"status\":\"linked\",\"username\":\"gamer\","
            "\"display_name\":\"Gamer One\",\"plan\":\"epic\",\"ac_balance\":150,"
            "\"access_token\":\"tok-a\",\"refresh_token\":\"ref-b\"}";
        assert(pkgtest::jsonGetString(linked, "status") == "linked");
        assert(pkgtest::jsonGetString(linked, "display_name") == "Gamer One");
        assert(pkgtest::jsonGetInt(linked, "ac_balance", 0) == 150);
        assert(pkgtest::jsonGetString(linked, "access_token") == "tok-a");

        const std::string i18n = "{\"en\":\"Winter Sale\",\"ar\":\"\\xD8\\xAA\\xD8\\xAE\\xD9\\x81\\xD9\\x8A\\xD8\\xB6\\xD8\\xA7\\xD8\\xAA\"}";
        assert(pkgtest::i18nPick(i18n, "en") == "Winter Sale");
        assert(!pkgtest::i18nPick(i18n, "ar").empty());
        assert(pkgtest::i18nPick(i18n, "fr") == "Winter Sale");  // fallback

        const std::string arr =
            "[{\"id\":\"a1\",\"title\":\"Pack A\",\"sale_ac_price\":null,\"ac_price\":200,"
            "\"is_exclusive\":true},{\"id\":\"b2\",\"title\":\"Pack B\",\"sale_ac_price\":75,"
            "\"ac_price\":150,\"is_exclusive\":false}]";
        const std::vector<std::string> objs = pkgtest::splitJsonObjects(arr);
        assert(objs.size() == 2);
        assert(pkgtest::jsonGetString(objs[0], "title") == "Pack A");
        assert(pkgtest::jsonGetString(objs[1], "id") == "b2");
        assert(pkgtest::jsonGetRaw(arr, "missing").empty());

        const std::string grant =
            "{\"ok\":true,\"ps4_raw_url\":\"https://x/y.raw\","
            "\"pack_targets\":[{\"target\":\"resource_packs\",\"dest_folder\":\"pack_ab12\","
            "\"raw_file\":\"pack0.raw\",\"ps4_raw_url\":\"https://x/p0.raw\"}]}";
        const std::string packs = pkgtest::jsonGetRaw(grant, "pack_targets");
        const std::vector<std::string> packObjs = pkgtest::splitJsonObjects(packs);
        assert(packObjs.size() == 1);
        assert(pkgtest::jsonGetString(packObjs[0], "target") == "resource_packs");
        assert(pkgtest::jsonGetString(packObjs[0], "dest_folder") == "pack_ab12");
    }

    std::printf("installer host tests passed (dynamic paths, dual-mode, cleanup, 8-lang, autolaunch, json).\n");
    return 0;
}
