// Host-only tool: renders AddonStation PS4 design screens to PNG previews
// (splash, welcome, language dialog, install states + 8-language notification).
// Build: c++ -std=c++17 -I../src tools/ps4_design_preview.cpp ../src/ui_engine.cpp ../src/screens.cpp
//        $(pkg-config --cflags --libs sdl2) -o build-host/ps4_design_preview

#include "../src/screens.hpp"

#define STB_IMAGE_WRITE_IMPLEMENTATION
#include "../src/stb_image_write.h"

#include <SDL2/SDL.h>
#include <cstdio>
#include <string>
#include <vector>

using namespace pkgtest;

namespace {

SDL_Surface* gSurface = nullptr;
SDL_Renderer* gRenderer = nullptr;

bool initOffscreen() {
    if (SDL_Init(SDL_INIT_VIDEO) != 0) {
        std::fprintf(stderr, "SDL_Init failed: %s\n", SDL_GetError());
        return false;
    }
    gSurface = SDL_CreateRGBSurfaceWithFormat(0, 1920, 1080, 32, SDL_PIXELFORMAT_RGBA32);
    if (!gSurface) {
        std::fprintf(stderr, "Surface creation failed: %s\n", SDL_GetError());
        return false;
    }
    gRenderer = SDL_CreateSoftwareRenderer(gSurface);
    if (!gRenderer) {
        std::fprintf(stderr, "Software renderer failed: %s\n", SDL_GetError());
        return false;
    }
    return true;
}

void savePng(const std::string& path) {
    SDL_RenderPresent(gRenderer);
    if (SDL_MUSTLOCK(gSurface)) SDL_LockSurface(gSurface);
    // SDL surface is RGBA32 (R,G,B,A bytes), stb expects same for comp=4
    const int ok = stbi_write_png(path.c_str(), gSurface->w, gSurface->h, 4,
                                  gSurface->pixels, gSurface->pitch);
    if (SDL_MUSTLOCK(gSurface)) SDL_UnlockSurface(gSurface);
    std::printf("%s -> %s\n", ok ? "saved" : "FAILED", path.c_str());
}

void clear(UiEngine& e, SDL_Color c) {
    e.fillRect(0, 0, 1920, 1080, c);
}

} // namespace

int main(int argc, char** argv) {
    const std::string outDir = argc > 1 ? argv[1] : "previews";
    if (!initOffscreen()) return 1;

    UiEngine engine;
    if (!engine.init(gRenderer, "assets/ui.pak")) {
        std::fprintf(stderr, "UiEngine init failed (assets/ui.pak missing?)\n");
        return 1;
    }

    // 1. Splash EN mid-animation
    clear(engine, theme::BG);
    drawSplash(engine, 3.35f, "en");
    savePng(outDir + "/ps4_splash_en.png");

    // 2. Splash AR with loader nearly full
    clear(engine, theme::BG);
    drawSplash(engine, 7.4f, "ar");
    savePng(outDir + "/ps4_splash_ar.png");

    // 3. Welcome EN (focus: sign in with a code)
    clear(engine, theme::BG);
    drawWelcome(engine, 0, "en");
    savePng(outDir + "/ps4_welcome_en.png");

    // 4. Welcome AR RTL (focus: sign in with a code)
    clear(engine, theme::BG);
    drawWelcome(engine, 0, "ar");
    savePng(outDir + "/ps4_welcome_ar_rtl.png");

    // 5. Language dialog (8 languages)
    clear(engine, theme::BG);
    drawLanguageDialog(engine, 1, "en");
    savePng(outDir + "/ps4_language_dialog.png");

    // 6. Install demo - Downloading 62%
    clear(engine, theme::BG);
    drawInstallDemo(engine, DemoState::Downloading, 62, "en");
    savePng(outDir + "/ps4_install_downloading_en.png");

    // 7. Install demo - Installed + AR notification (the user's requested toast)
    clear(engine, theme::BG);
    drawInstallDemo(engine, DemoState::Installed, 100, "ar");
    drawToast(engine, "تم تثبيت PrizmaVisuals — استمتع!", "ar");
    savePng(outDir + "/ps4_install_done_toast_ar.png");

    // 8. Install demo - Installed + EN notification
    clear(engine, theme::BG);
    drawInstallDemo(engine, DemoState::Installed, 100, "en");
    drawToast(engine, "PrizmaVisuals installed — Enjoy!", "en");
    savePng(outDir + "/ps4_install_done_toast_en.png");

    // 9. Install demo - Installing FR (progress)
    clear(engine, theme::BG);
    drawInstallDemo(engine, DemoState::Installing, 34, "fr");
    savePng(outDir + "/ps4_install_installing_fr.png");

    engine.shutdown();
    SDL_DestroyRenderer(gRenderer);
    SDL_FreeSurface(gSurface);
    SDL_Quit();
    return 0;
}
