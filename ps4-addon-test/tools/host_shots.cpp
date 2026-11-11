// host_shots.cpp — renders REAL AddonStation screens offscreen (same engine + same
// screens.cpp as the PS4 build) and dumps PPM frames. Usage (from ps4-addon-test/):
//   g++ -std=c++17 -O1 -I/usr/local/include/SDL2 -Isrc tools/host_shots.cpp \
//       src/screens.cpp src/ui_engine.cpp src/supabase_client.cpp src/http_download.cpp \
//       src/installer.cpp -o /tmp/shots && /tmp/shots out_dir [scene.jpg]
#include "../src/screens.hpp"
#include "../src/ui_engine.hpp"
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <ctime>
#include <string>
#include <vector>

using namespace pkgtest;

// ---- SDL stub: the engine only reads SDL_GetTicks (timers/anim) ----
extern "C" unsigned int SDL_GetTicks(void) { return (unsigned int)(std::clock() * 1000 / CLOCKS_PER_SEC + 123456); }

static void dumpPPM(const UiEngine& e, const std::string& path) {
    const std::vector<std::uint32_t>& fb = e.framebuffer();
    FILE* f = std::fopen(path.c_str(), "wb");
    if (!f) { std::fprintf(stderr, "open %s failed\n", path.c_str()); return; }
    std::fprintf(f, "P6\n%d %d\n255\n", UiEngine::kWidth, UiEngine::kHeight);
    std::vector<unsigned char> row((size_t)UiEngine::kWidth * 3);
    for (int y = 0; y < UiEngine::kHeight; ++y) {
        for (int x = 0; x < UiEngine::kWidth; ++x) {
            const std::uint32_t p = fb[(size_t)y * UiEngine::kWidth + x];
            row[(size_t)x * 3 + 0] = (unsigned char)((p >> 16) & 0xFF);   // R
            row[(size_t)x * 3 + 1] = (unsigned char)((p >> 8) & 0xFF);    // G
            row[(size_t)x * 3 + 2] = (unsigned char)(p & 0xFF);           // B
        }
        std::fwrite(row.data(), 1, row.size(), f);
    }
    std::fclose(f);
}

int main(int argc, char** argv) {
    const std::string outDir = argc > 1 ? argv[1] : "/tmp/shots";
    const std::string scenePath = argc > 2 ? argv[2] : "";

    UiEngine e;
    if (!e.init("assets/ui.pak")) { std::fprintf(stderr, "pak load failed\n"); return 1; }

    // ---- content: the free pack, covers = crops of the scene ----
    const char* titles[10] = {"Gravestones by sliver fox","Owls by sliver fox","Spark Pets by sliver fox",
        "Weapons Armors by sliver fox","Tiny Takeover Mini Mania by sliver fox","Backpacks and Jetpacks by sliver fox",
        "Aether Moa by sliver fox","Block Furniture 1.0 by sliver fox","Combine Items Add-on by sliver fox",
        "A Minecraft Movie by sliver fox"};
    const char* cats[10] = {"addon","addon","addon","pvp","addon","addon","adventure","addon","addon","other"};
    const char* slugs[10] = {"gravestones","owls","spark-pets","weapons-armors","tiny-takeover","backpacks","aether-moa","block-furniture","combine-items","a-minecraft-movie"};
    std::vector<AddonItem> items;
    for (int i = 0; i < 10; ++i) {
        AddonItem it;
        it.id = std::string("id-") + slugs[i];
        it.title = titles[i];
        it.creator = "sliver fox";
        it.description = "Gear up for battle with new weapons and armor sets - sharp blades, heavy hammers and strong shields. Everything you need to win the toughest fights.";
        it.category = cats[i];
        it.ftype = 0;
        it.tier = Tier::Free;
        it.cover = std::string("cv_") + slugs[i];
        it.coverUrl = "x";
        it.version = "1.0.0";
        it.fileSize = 556442;
        it.downloads = 2 + i * 137;
        it.views = 41 + i * 260;
        it.rating = 4.1f + (i % 3) * 0.2f;
        it.ratingCount = 12 + i * 7;
        it.featured = (i == 3);
        it.createdAt = 1760000000 + i * 86400;
        items.push_back(it);
    }
    setCatalog(items);
    Account acc;
    acc.signedIn = true;
    acc.displayName = "sliver fox";
    acc.username = "sliverfoxstudio";
    acc.plan = Tier::Free;
    acc.acBalance = 270;
    acc.downloads = 8; acc.favorites = 3; acc.installed = 2;
    setAccount(acc);
    setAppVersion("0.72");
    setServerTime(0);

    // covers = pre-cropped scene jpgs (coversDir/<slug>.jpg) so cards show art like the live app
    if (!scenePath.empty()) {
        for (int i = 0; i < 10; ++i) {
            std::string name = std::string("cv_") + slugs[i];
            e.loadImageFile(name, scenePath + "/" + slugs[i] + ".jpg", 640, 360);
        }
    }

    struct Shot { const char* name; int a; int b; };
    const Shot shots[] = {
        {"home", 0, 0}, {"store", 0, 0}, {"deals", 5, 0}, {"detail", 0, 3},
        {"downloads", 0, 0}, {"settings", 0, 0}, {"profile", 0, 0}, {"plans", 0, 0},
    };
    for (int theme = 0; theme < 2; ++theme) {
        setThemeLight(theme == 1);
        for (const Shot& sh : shots) {
            const std::string lang = "ar";
            if (std::strcmp(sh.name, "home") == 0) drawHome(e, sh.a, 0, sh.b, lang);
            else if (std::strcmp(sh.name, "store") == 0) drawStore(e, sh.a, sh.b, lang, 0.0f);
            else if (std::strcmp(sh.name, "deals") == 0) drawDeals(e, sh.a, sh.b, lang);
            else if (std::strcmp(sh.name, "detail") == 0) drawAddonDetail(e, sh.a, sh.b, DemoState::Idle, 0, lang, std::string());
            else if (std::strcmp(sh.name, "downloads") == 0) drawMine(e, sh.a, lang);
            else if (std::strcmp(sh.name, "settings") == 0) drawSettings(e, sh.a, sh.b, false, lang);
            else if (std::strcmp(sh.name, "profile") == 0) drawProfile(e, sh.a, lang);
            else if (std::strcmp(sh.name, "plans") == 0) drawPlans(e, sh.a, lang);
            const std::string path = outDir + "/" + sh.name + (theme ? "_light" : "_dark") + ".ppm";
            dumpPPM(e, path);
            std::fprintf(stderr, "shot %s\n", path.c_str());
        }
    }
    return 0;
}
