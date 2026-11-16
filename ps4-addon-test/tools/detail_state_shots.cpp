// detail_state_shots.cpp — renders the ADDON / WORLD / SKIN / TEXTURE PACK detail page
// (the page with the download) in ALL its states, offscreen with the REAL engine and the
// REAL screens.cpp (identical to the PS4 build). Used to produce the project-file previews
// (AddonStation-PS4-DetailPage.zip).
//
// Usage (from ps4-addon-test/):
//   g++ -std=c++17 -O1 -I/usr/local/include/SDL2 -Isrc tools/detail_state_shots.cpp \
//       src/screens.cpp src/ui_engine.cpp src/supabase_client.cpp src/http_download.cpp \
//       src/installer.cpp -o /tmp/dshots
//   /tmp/dshots out_dir [covers_dir]
//
// Writes <out_dir>/<state>__<lang>-<theme>.ppm  (state matrix x {ar-dark, ar-light, en-dark}).
#include "../src/screens.hpp"
#include "../src/ui_engine.hpp"
#include <algorithm>
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
            row[(size_t)x * 3 + 0] = (unsigned char)((p >> 16) & 0xFF);
            row[(size_t)x * 3 + 1] = (unsigned char)((p >> 8) & 0xFF);
            row[(size_t)x * 3 + 2] = (unsigned char)(p & 0xFF);
        }
        std::fwrite(row.data(), 1, row.size(), f);
    }
    std::fclose(f);
}

int main(int argc, char** argv) {
    const std::string outDir = argc > 1 ? argv[1] : "/tmp/dshots";
    const std::string coversDir = argc > 2 ? argv[2] : "";

    UiEngine e;
    if (!e.init("assets/ui.pak")) { std::fprintf(stderr, "pak load failed\n"); return 1; }

    const std::time_t now = std::time(nullptr);

    // ---------- catalog: every type + tier + data shape the page can meet ----------
    std::vector<AddonItem> items;
    auto mk = [&](const char* id, const char* title, const char* creator, int ftype, Tier tier,
                  const char* desc, long long size, int dl, float rt, int rc,
                  std::time_t created, int acPrice, int acOld) {
        AddonItem it;
        it.id = id; it.title = title; it.creator = creator; it.description = desc;
        it.category = "addon";
        it.ftype = ftype; it.isWorld = (ftype == 2);
        it.tier = tier;
        it.acPrice = acPrice; it.acPriceOld = acOld;
        it.cover = std::string("cv_") + id;
        it.coverUrl = "x";
        it.version = size > 0 ? "1.0.0" : "";
        it.fileSize = size;
        it.downloads = dl; it.views = dl * 3 + 41;
        it.rating = rt; it.ratingCount = rc;
        it.createdAt = created;
        items.push_back(it);
        return (int)items.size() - 1;
    };
    const char* DESC = "Gear up for battle with new weapons and armor sets - sharp blades, heavy hammers and strong shields. Everything you need to win the toughest fights.";
    const char* DESC_LONG = "A complete medieval warfare overhaul: twelve sharp blades, eight heavy hammers and six tower shields, plus a full steel armor set with unique stats. Forge your gear at the new anvil, discover hidden recipes in ruined keeps, and challenge the arena master. Everything you need to win the toughest fights - and to look good doing it.";
    // rail family: same creator "sliver fox"
    mk("id-free",      "Aether Moa by sliver fox",      "sliver fox", 0, Tier::Free,       DESC, 556442, 824,   4.1f, 12, now - 60 * 86400, 0,    0);
    mk("id-epic",      "Combine Items Add-on",          "sliver fox", 0, Tier::Epic,       DESC, 2100333, 5100,  4.5f, 40, now - 90 * 86400, 600,  1000);
    mk("id-legendary", "Weapons Armors Legendary",      "sliver fox", 0, Tier::Legendary, DESC, 12884901, 120000, 4.8f, 90, now - 120 * 86400, 1200, 2000);
    mk("id-world",     "Sky Kingdom Adventure",         "sliver fox", 2, Tier::Free,       DESC, 44040192, 2100,  4.4f, 33, now - 30 * 86400, 0, 0);
    mk("id-skin",      "Hero Skins Pack",               "sliver fox", 1, Tier::Free,       DESC, 88080, 910,  4.2f, 15, now - 20 * 86400, 0, 0);
    mk("id-texture",   "Faithful 32x Texture Pack",     "sliver fox", 3, Tier::Free,       DESC, 6442450, 3300,  4.6f, 51, now - 45 * 86400, 0, 0);
    mk("id-new",       "Tiny Takeover Mini Mania",      "sliver fox", 0, Tier::Free,       DESC, 0, 0,      0.f, 0,  now - 2 * 86400, 0, 0);            // NEW + empty facts cells hidden
    mk("id-big",       "Backpacks and Jetpacks",        "sliver fox", 0, Tier::Free,       DESC_LONG, 15032385536LL, 1200000, 4.9f, 220, now - 200 * 86400, 0, 0); // long desc + huge stats
    mk("id-epic2",     "Block Furniture 1.0",           "sliver fox", 0, Tier::Epic,       DESC, 1101003, 961,   4.3f, 22, now - 70 * 86400, 0, 0);   // pure "Get Epic" (no AC line)
    mk("id-owned",     "Spark Pets",                    "sliver fox", 0, Tier::Epic,       DESC, 734003, 276,   4.5f, 18, now - 35 * 86400, 0, 0);   // unlocked via AC earlier
    items.back().unlocked = true;
    // other creators (must NOT appear in the rail)
    mk("id-pf1",       "Gravestones",                   "PixelForge", 0, Tier::Free,       DESC, 402653, 550,  4.3f, 11, now - 15 * 86400, 0, 0);
    mk("id-pf2",       "Owls",                          "PixelForge", 0, Tier::Free,       DESC, 1908408, 139, 4.3f, 9,  now - 25 * 86400, 0, 0);
    mk("id-nocreator", "Mystery Pack",                  "",           0, Tier::Free,       DESC, 335544, 77,   4.0f, 5,  now - 10 * 86400, 0, 0);        // no creator -> no rail

    std::stable_sort(items.begin(), items.end(),
                     [](const AddonItem& a, const AddonItem& b) { return a.createdAt > b.createdAt; });
    setCatalog(items);
    auto idxOf = [&](const char* id) {
        for (int i = 0; i < (int)items.size(); ++i) if (items[(size_t)i].id == id) return i;
        return -1;
    };
    const int I_FREE = idxOf("id-free"), I_EPIC = idxOf("id-epic"), I_LEG = idxOf("id-legendary"),
              I_WORLD = idxOf("id-world"), I_SKIN = idxOf("id-skin"), I_TEX = idxOf("id-texture"),
              I_NEW = idxOf("id-new"), I_BIG = idxOf("id-big"), I_NONE = idxOf("id-nocreator"),
              I_EPIC2 = idxOf("id-epic2"), I_OWNED = idxOf("id-owned");

    // account variants
    Account guest;   // signedIn = false
    Account freeU;   freeU.signedIn = true; freeU.displayName = "sliver fox"; freeU.username = "sliverfoxstudio";
    freeU.plan = Tier::Free; freeU.acBalance = 5000; freeU.downloads = 8; freeU.favorites = 3; freeU.installed = 2;
    Account freeLow = freeU; freeLow.acBalance = 270;
    Account epicU = freeU;  epicU.plan = Tier::Epic;

    // covers (optional second arg): <coversDir>/<id>.jpg
    if (!coversDir.empty()) {
        for (const AddonItem& it : items)
            e.loadImageFile(it.cover, coversDir + "/" + it.id + ".jpg", 640, 360);
    }
    setAppVersion("0.80");
    setServerTime(0);

    // ---------- the state matrix ----------
    struct State {
        const char* name;
        int idx;
        int focus;
        DemoState st;
        int pct;
        const char* notice;      // plain text (already localized by the caller)
        const char* noticeKey;   // tr() key (used per language when non-null)
        const Account* acc;
        int railOff;
        int dialog;            // 0 none, 1 = AC confirm (Epic purple), 2 = AC confirm (Legendary gold)
    };
    static const char* NOTICE_AR = "\xD8\xB9\xD8\xB0\xD8\xB1\xD9\x8B\xD8\xA7\xD8\x8C \xD9\x81\xD8\xB4\xD9\x84 \xD8\xA7\xD9\x84\xD8\xAA\xD8\xAD\xD9\x85\xD9\x8A\xD9\x84";   // عذرًا، فشل التحميل
    static const char* NOTICE_EN = "Download failed, please try again";
    const State states[] = {
        {"01-addon-free-download",        I_FREE,  0, DemoState::Idle,        0,   nullptr,     nullptr,          &freeU,   0},
        {"02-addon-included-plan",        I_EPIC,  0, DemoState::Idle,        0,   nullptr,     nullptr,          &epicU,   0},
        {"03-need-signin",                I_EPIC2, 0, DemoState::Idle,        0,   nullptr,     nullptr,          &guest,   0},
        {"04-need-plan-get-epic",         I_EPIC2, 0, DemoState::Idle,        0,   nullptr,     nullptr,          &freeU,   0},
        {"05-ac-offer-epic-focus",        I_EPIC,  1, DemoState::Idle,        0,   nullptr,     nullptr,          &freeU,   0},
        {"06-ac-offer-legendary-focus",   I_LEG,   1, DemoState::Idle,        0,   nullptr,     nullptr,          &freeU,   0},
        {"07-ac-low-balance",             I_EPIC,  1, DemoState::Idle,        0,   nullptr,     nullptr,          &freeLow, 0},
        {"08-downloading-47",             I_FREE,  0, DemoState::Downloading, 47,  nullptr,     nullptr,          &freeU,   0},
        {"09-installing",                 I_FREE,  0, DemoState::Installing,  0,   nullptr,     nullptr,          &freeU,   0},
        {"10-queued-minecraft",           I_FREE,  0, DemoState::Queued,      0,   nullptr,     nullptr,          &freeU,   0},
        {"11-installed",                  I_FREE,  0, DemoState::Installed,   0,   nullptr,     nullptr,          &freeU,   0},
        {"12-notice-error",               I_FREE,  0, DemoState::Idle,        0,   "NOTICE",    nullptr,          &freeU,   0},
        {"13-rail-card-focus",            I_FREE,  2, DemoState::Idle,        0,   nullptr,     nullptr,          &freeU,   0},
        {"14-rail-scrolled",              I_FREE,  5, DemoState::Idle,        0,   nullptr,     nullptr,          &freeU,   3},
        {"15-world-type",                 I_WORLD, 0, DemoState::Idle,        0,   nullptr,     nullptr,          &freeU,   0},
        {"16-skin-type",                  I_SKIN,  0, DemoState::Idle,        0,   nullptr,     nullptr,          &freeU,   0},
        {"17-texture-type",               I_TEX,   0, DemoState::Idle,        0,   nullptr,     nullptr,          &freeU,   0},
        {"18-no-creator-no-rail",         I_NONE,  0, DemoState::Idle,        0,   nullptr,     nullptr,          &freeU,   0},
        {"19-new-badge-minimal-facts",    I_NEW,   0, DemoState::Idle,        0,   nullptr,     nullptr,          &freeU,   0},
        {"20-long-description-stats",     I_BIG,   0, DemoState::Idle,        0,   nullptr,     nullptr,          &freeU,   0},
        {"21-removed-item-empty",         -1,      0, DemoState::Idle,        0,   nullptr,     nullptr,          &freeU,   0},
        {"22-unlocked-owned",             I_OWNED, 0, DemoState::Idle,        0,   nullptr,     nullptr,          &freeU,   0},
        {"23-confirm-dialog-epic",        I_EPIC,  0, DemoState::Idle,        0,   nullptr,     nullptr,          &freeU,   0, 1},
        {"24-confirm-dialog-legendary",   I_LEG,   0, DemoState::Idle,        0,   nullptr,     nullptr,          &freeU,   0, 2},
    };

    struct Combo { const char* lang; bool light; const char* tag; };
    const Combo combos[] = {
        {"ar", false, "ar-dark"},
        {"ar", true,  "ar-light"},
        {"en", false, "en-dark"},
    };

    for (const Combo& c : combos) {
        setThemeLight(c.light);
        for (const State& s : states) {
            setAccount(*s.acc);
            setDetailRailOff(s.railOff);
            std::string notice;
            if (s.notice && std::strcmp(s.notice, "NOTICE") == 0)
                notice = (std::strcmp(c.lang, "ar") == 0) ? NOTICE_AR : NOTICE_EN;
            drawAddonDetail(e, s.focus, s.idx, s.st, s.pct, c.lang, notice);
            if (s.dialog && s.idx >= 0 && s.idx < (int)items.size())
                drawAcConfirm(e, 0, items[(std::size_t)s.idx], c.lang);
            const std::string path = outDir + "/" + s.name + "__" + c.tag + ".ppm";
            dumpPPM(e, path);
            std::fprintf(stderr, "shot %s\n", path.c_str());
        }
    }
    std::fprintf(stderr, "done: %d states x %d combos\n", (int)(sizeof(states) / sizeof(states[0])), (int)(sizeof(combos) / sizeof(combos[0])));
    return 0;
}
