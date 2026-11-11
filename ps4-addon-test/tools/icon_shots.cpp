// icon_shots.cpp — export the app's REAL engine-drawn icons + no-cover card as RGBA PNGs.
// Includes screens.cpp in this TU so internal statics (lineIcon, glyph, itemCover, vcard) are callable.
#include "../src/screens.cpp"
#define STB_IMAGE_WRITE_IMPLEMENTATION
#include "stb_image_write.h"
#include <cstring>

using namespace pkgtest;

extern "C" unsigned int SDL_GetTicks(void) { return 123456; }

static UiEngine* gE = nullptr;

// Draw twice (black bg, white bg) and solve for straight alpha -> true RGBA PNG.
template <class F>
static void exportIcon(const char* path, int x, int y, int w, int h, F draw) {
    gE->fillRect(0, 0, 1920, 1080, SDL_Color{0, 0, 0, 255});
    draw();
    std::vector<std::uint32_t> black = gE->framebuffer();
    gE->fillRect(0, 0, 1920, 1080, SDL_Color{255, 255, 255, 255});
    draw();
    const std::vector<std::uint32_t>& white = gE->framebuffer();
    std::vector<unsigned char> rgba((size_t)w * h * 4, 0);
    for (int py = 0; py < h; ++py) {
        for (int px = 0; px < w; ++px) {
            const std::uint32_t pb = black[(size_t)(y + py) * 1920 + (x + px)];
            const std::uint32_t pw = white[(size_t)(y + py) * 1920 + (x + px)];
            const int rb = (pb >> 16) & 255, gb = (pb >> 8) & 255, bb2 = pb & 255;
            const int rw = (pw >> 16) & 255, gw = (pw >> 8) & 255, bw2 = pw & 255;
            // c_w - c_b = (1-a)*255  ->  a = 1 - (c_w-c_b)/255  (avg channels, clamp)
            float a = 3.f - ((rw + gw + bw2) - (rb + gb + bb2)) / 255.f;
            a /= 3.f;
            if (a < 0.004f) a = 0.f;
            if (a > 1.f) a = 1.f;
            const float inv = a > 0.f ? 1.f / a : 0.f;
            const int r = std::min(255, (int)(rb * inv));
            const int g = std::min(255, (int)(gb * inv));
            const int b = std::min(255, (int)(bb2 * inv));
            unsigned char* o = &rgba[((size_t)py * w + px) * 4];
            o[0] = (unsigned char)r; o[1] = (unsigned char)g; o[2] = (unsigned char)b; o[3] = (unsigned char)(a * 255.f + 0.5f);
        }
    }
    stbi_write_png(path, w, h, 4, rgba.data(), w * 4);
    std::fprintf(stderr, "png %s\n", path);
}

// Opaque region crop -> PNG (for the full card composite).
static void exportCrop(const char* path, int x, int y, int w, int h) {
    const std::vector<std::uint32_t>& fb = gE->framebuffer();
    std::vector<unsigned char> rgba((size_t)w * h * 4, 0);
    for (int py = 0; py < h; ++py)
        for (int px = 0; px < w; ++px) {
            const std::uint32_t p = fb[(size_t)(y + py) * 1920 + (x + px)];
            unsigned char* o = &rgba[((size_t)py * w + px) * 4];
            o[0] = (p >> 16) & 255; o[1] = (p >> 8) & 255; o[2] = p & 255; o[3] = 255;
        }
    stbi_write_png(path, w, h, 4, rgba.data(), w * 4);
    std::fprintf(stderr, "png %s\n", path);
}

int main(int argc, char** argv) {
    const std::string outDir = argc > 1 ? argv[1] : "/tmp/wanted";
    UiEngine e;
    gE = &e;
    if (!e.init("assets/ui.pak")) { std::fprintf(stderr, "pak init failed\n"); return 1; }
    setThemeLight(false);

    const SDL_Color kAccD[6] = {{88, 156, 255, 255}, {176, 132, 255, 255}, {250, 196, 64, 255},
                                {76, 214, 124, 255}, {160, 170, 188, 255}, {255, 96, 112, 255}};
    const char* kNames[6] = {"language_globe", "appearance_contrast", "account_user",
                             "autolaunch_gamepad", "about_info", "signout_logout"};

    // 1) settings tile icons — accent (as in app) + white (reusable)
    for (int k = 0; k < 6; ++k) {
        exportIcon((outDir + "/settings-icons/" + kNames[k] + "_accent.png").c_str(), 800, 400, 256, 256,
                   [&]{ lineIcon(*gE, k, 800, 400, 256, kAccD[k]); });
        exportIcon((outDir + "/settings-icons/" + kNames[k] + "_white.png").c_str(), 800, 400, 256, 256,
                   [&]{ lineIcon(*gE, k, 800, 400, 256, SDL_Color{255, 255, 255, 255}); });
    }

    // 2) glyphs (the "emoji" drawn by the engine) — known: 0 world, 12 addon/puzzle; dump 0..24
    for (int k = 0; k <= 24; ++k) {
        char nm[64];
        std::snprintf(nm, sizeof nm, "%s/glyphs-emoji/glyph_%02d.png", outDir.c_str(), k);
        exportIcon(nm, 800, 400, 192, 192,
                   [&]{ glyph(*gE, k, 800, 400, 192, SDL_Color{255, 255, 255, 255}, SDL_Color{255, 255, 255, 255}, false); });
    }

    // 3) no-cover card — full vcard (real shell) + bare placeholder cover slot
    AddonItem it;
    it.id = "gravestones"; it.title = "Gravestones and Item Elevators by sliver fox";
    it.creator = "sliver fox"; it.tier = Tier::Free; it.cover = ""; it.rating = 4.7f; it.ratingCount = 128;
    it.downloads = 1200; it.views = 2400; it.fileSize = 543 * 1024; it.featured = true;
    Lay L; L.rtl = true;
    gE->fillRect(0, 0, 1920, 1080, SDL_Color{11, 17, 28, 255});
    vcard(*gE, L, 100, 200, 420, 236, it, true, "ar", false, false, false);
    exportCrop((outDir + "/card-no-cover/vcard_nocover_addon.png").c_str(), L.X(100, 420) - 30, 170, 480, 420);

    AddonItem iw = it; iw.id = "aether-moa"; iw.title = "Aether Moa by sliver fox"; iw.isWorld = true;
    gE->fillRect(0, 0, 1920, 1080, SDL_Color{11, 17, 28, 255});
    vcard(*gE, L, 100, 200, 420, 236, iw, false, "ar", false, false, true);
    exportCrop((outDir + "/card-no-cover/vcard_nocover_world.png").c_str(), L.X(100, 420) - 30, 170, 480, 420);

    // bare placeholder cover slots (the glow+glyph block alone)
    gE->fillRect(0, 0, 1920, 1080, SDL_Color{11, 17, 28, 255});
    itemCover(*gE, it, 100, 100, 640, 360, 18, true);
    exportCrop((outDir + "/card-no-cover/cover_slot_nocover.png").c_str(), 100, 100, 640, 360);

    // ---- AS-IN-APP: settings tiles via the app's own lineIconTile (accent tile + white icon + halo)
    for (int k = 0; k < 6; ++k) {
        exportIcon((outDir + "/settings-tiles/tile_" + kNames[k] + ".png").c_str(), 800, 400, 300, 300,
                   [&]{ lineIconTile(*gE, k, 822, 422, 256, kAccD[k], false); });
        exportIcon((outDir + "/settings-tiles/tile_" + kNames[k] + "_focused.png").c_str(), 800, 400, 300, 300,
                   [&]{ lineIconTile(*gE, k, 822, 422, 256, kAccD[k], true); });
    }

    // ---- AS-IN-APP: full store screen where EVERY card has no cover (real context)
    std::vector<AddonItem> items;
    for (int i = 0; i < 6; ++i) {
        AddonItem n;
        n.id = std::string("n") + char('0' + i);
        n.title = "Gravestones and Item Elevators by sliver fox";
        n.creator = "sliver fox"; n.tier = (i % 3 == 2) ? Tier::Legendary : (i % 3 == 1 ? Tier::Epic : Tier::Free);
        n.cover = ""; n.rating = 4.7f; n.ratingCount = 128; n.downloads = 1200; n.views = 2400;
        n.isWorld = (i == 3);
        items.push_back(n);
    }
    setCatalog(items);
    for (int theme = 0; theme < 2; ++theme) {
        setThemeLight(theme == 1);
        gE->fillRect(0, 0, 1920, 1080, SDL_Color{11, 17, 28, 255});
        drawStore(*gE, 0, 0, "ar", 1.0f);
        exportCrop((outDir + "/card-no-cover/store_nocover_" + (theme ? "light" : "dark") + ".png").c_str(), 0, 0, 1920, 1080);
    }
    setThemeLight(false);

    return 0;
}
