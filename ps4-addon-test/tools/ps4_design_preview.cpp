// Host-only tool: renders AddonStation PS4 design screens to PNG previews
// using the SAME framebuffer rasterizer that ships on the PS4 (no SDL needed).
//
// Build:
//   c++ -std=c++17 -O2 -I../src tools/ps4_design_preview.cpp +
//       ../src/ui_engine.cpp ../src/screens.cpp -o build-host/ps4_design_preview

#include "../src/screens.hpp"

#define STB_IMAGE_WRITE_IMPLEMENTATION
#include "../src/stb_image_write.h"

#include <cstdio>
#include <string>
#include <vector>

using namespace pkgtest;

namespace {

void savePng(UiEngine& e, const std::string& path) {
    const int w = e.width();
    const int h = e.height();
    std::vector<unsigned char> rgba(static_cast<std::size_t>(w) * h * 4);
    const std::uint32_t* px = e.pixels();
    for (std::size_t i = 0; i < static_cast<std::size_t>(w) * h; ++i) {
        const std::uint32_t v = px[i];
        rgba[i * 4 + 0] = static_cast<unsigned char>((v >> 16) & 0xFF);
        rgba[i * 4 + 1] = static_cast<unsigned char>((v >> 8) & 0xFF);
        rgba[i * 4 + 2] = static_cast<unsigned char>(v & 0xFF);
        rgba[i * 4 + 3] = static_cast<unsigned char>((v >> 24) & 0xFF);
    }
    const int ok = stbi_write_png(path.c_str(), w, h, 4, rgba.data(), w * 4);
    std::printf("%s -> %s\n", ok ? "saved" : "FAILED", path.c_str());
}

} // namespace

int main(int argc, char** argv) {
    const std::string outDir = argc > 1 ? argv[1] : "previews";

    UiEngine engine;
    if (!engine.init("assets/ui.pak")) {
        std::fprintf(stderr, "WARNING: ui.pak fonts failed to load — using fallback font\n");
    }

    // 1. Splash EN mid-animation
    engine.clear(theme::BG);
    drawSplash(engine, 3.35f, "en");
    savePng(engine, outDir + "/ps4_splash_en.png");

    // 2. Splash AR with loader nearly full
    engine.clear(theme::BG);
    drawSplash(engine, 7.4f, "ar");
    savePng(engine, outDir + "/ps4_splash_ar.png");

    // 3. Welcome EN (focus: sign in with a code)
    engine.clear(theme::BG);
    drawWelcome(engine, 0, "en");
    savePng(engine, outDir + "/ps4_welcome_en.png");

    // 4. Welcome AR RTL (focus: sign in with a code)
    engine.clear(theme::BG);
    drawWelcome(engine, 0, "ar");
    savePng(engine, outDir + "/ps4_welcome_ar_rtl.png");

    // 5. Language dialog (8 languages)
    engine.clear(theme::BG);
    drawLanguageDialog(engine, 1, "en");
    savePng(engine, outDir + "/ps4_language_dialog.png");

    // 6. Install demo - Downloading 62%
    engine.clear(theme::BG);
    drawInstallDemo(engine, DemoState::Downloading, 62, "en");
    savePng(engine, outDir + "/ps4_install_downloading_en.png");

    // 7. Install demo - Installed + AR notification (the user's requested toast)
    engine.clear(theme::BG);
    drawInstallDemo(engine, DemoState::Installed, 100, "ar");
    drawToast(engine, "تم تثبيت [اسم الإضافة] — استمتع!", "ar");
    savePng(engine, outDir + "/ps4_install_done_toast_ar.png");

    // 8. Install demo - Installed + EN notification
    engine.clear(theme::BG);
    drawInstallDemo(engine, DemoState::Installed, 100, "en");
    drawToast(engine, "[Addon name] installed — Enjoy!", "en");
    savePng(engine, outDir + "/ps4_install_done_toast_en.png");

    // 9. Install demo - Installing FR (progress)
    engine.clear(theme::BG);
    drawInstallDemo(engine, DemoState::Installing, 34, "fr");
    savePng(engine, outDir + "/ps4_install_installing_fr.png");

    return 0;
}
