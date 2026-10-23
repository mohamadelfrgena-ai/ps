// Host-only tool: renders AddonStation UI v7.3 screens to PNG previews
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
    const int w = UiEngine::kWidth;
    const int h = UiEngine::kHeight;
    std::vector<unsigned char> rgba(static_cast<std::size_t>(w) * h * 4);
    const std::vector<std::uint32_t>& px = e.framebuffer();
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

AddonItem demoItem(const char* title, Tier tier, bool featured, bool excl, int ac) {
    AddonItem it;
    it.id = std::string("demo-") + title;
    it.title = title;
    it.creator = "sliver fox";
    it.description = "Sulfur Cars adds new vehicles to Minecraft.";
    it.category = "addons";
    it.tier = tier;
    it.acPrice = ac;
    it.version = "1.0";
    it.fileSize = 7340032;
    it.downloads = 24300;
    it.rating = 4.9f;
    it.ratingCount = 312;
    it.featured = featured;
    it.exclusive = excl;
    it.exclusiveSlot = excl ? 1 : 0;
    it.createdAt = 1760000000;
    return it;
}

} // namespace

int main(int argc, char** argv) {
    const std::string outDir = argc > 1 ? argv[1] : "previews";

    UiEngine engine;
    if (!engine.init("assets/ui.pak")) {
        std::fprintf(stderr, "WARNING: ui.pak failed to load — using fallback font\n");
    }

    // 1. legal splash (static frame)
    engine.clear(theme::BG);
    drawLegalSplash(engine, "en");
    savePng(engine, outDir + "/v73_01_splash_en.png");

    // 2. sign-in code card EN/AR
    engine.clear(theme::BG);
    drawSignIn(engine, 0, "AB12CD", 482, 0, "en", false, false);
    savePng(engine, outDir + "/v73_02_signin_code_en.png");
    engine.clear(theme::BG);
    drawSignIn(engine, 1, "AB12CD", 482, 0, "ar", true, false);
    savePng(engine, outDir + "/v73_03_signin_code_ar.png");

    // 3. sign-in QR card
    engine.clear(theme::BG);
    drawSignIn(engine, 0, "AB12CD", 482, 0, "en", false, true);
    savePng(engine, outDir + "/v73_04_signin_qr_en.png");

    // 4. Home empty (guest) EN/AR
    setCatalog({});
    setAccount(Account{});
    engine.clear(theme::BG);
    drawHome(engine, 0, 0, 0, "en");
    savePng(engine, outDir + "/v73_05_home_empty_en.png");
    engine.clear(theme::BG);
    drawHome(engine, 0, 0, 0, "ar");
    savePng(engine, outDir + "/v73_06_home_empty_ar.png");

    // 5. Home with demo catalog + live sale
    {
        std::vector<AddonItem> items;
        items.push_back(demoItem("Sulfur Cars by sliver fox", Tier::Free, true, true, 0));
        items.push_back(demoItem("Desert World by sliver fox", Tier::Epic, false, false, 120));
        items.push_back(demoItem("Castle Pack by sliver fox", Tier::Legendary, true, false, 0));
        items.push_back(demoItem("Redstone Tools by sliver fox", Tier::Free, false, false, 0));
        setCatalog(items);
        SaleBanner s;
        s.active = true;
        s.theme = "autumn";
        s.kicker = {{"en", "Limited time"}, {"ar", "لفترة محدودة"}};
        s.title = {{"en", "AUTUMN SALE"}, {"ar", "عرض الخريف"}};
        s.subtitle = {{"en", "Up to 50% off"}, {"ar", "خصم حتى 50%"}};
        s.discountPercent = 50;
        s.endsAt = 1770000000;
        setSaleBanner(s);
        engine.clear(theme::BG);
        drawHome(engine, 5, 0, 0, "en");
        savePng(engine, outDir + "/v73_07_home_sale_en.png");
        engine.clear(theme::BG);
        drawHome(engine, 12, 0, 0, "ar");
        savePng(engine, outDir + "/v73_08_home_sale_ar.png");

        engine.clear(theme::BG);
        drawDeals(engine, 2, 0, "en");
        savePng(engine, outDir + "/v73_09_deals_en.png");

        engine.clear(theme::BG);
        drawStore(engine, 16, 0, "ar");
        savePng(engine, outDir + "/v73_10_store_ar.png");
        engine.clear(theme::BG);
        drawStore(engine, 0, 0, "en");
        savePng(engine, outDir + "/v73_11_store_en.png");

        engine.clear(theme::BG);
        drawAddonDetail(engine, 0, 0, DemoState::Idle, 0, "en");
        savePng(engine, outDir + "/v73_12_detail_en.png");
        engine.clear(theme::BG);
        drawAddonDetail(engine, 0, 1, DemoState::Downloading, 42, "ar");
        savePng(engine, outDir + "/v73_13_detail_downloading_ar.png");
        engine.clear(theme::BG);
        drawAddonDetail(engine, 0, 0, DemoState::Installed, 100, "en");
        savePng(engine, outDir + "/v73_14_detail_installed_en.png");

        engine.clear(theme::BG);
        drawSearch(engine, 4, "car", 0, "en");
        savePng(engine, outDir + "/v73_15_search_en.png");
    }

    // 6. Profile guest + signed-in
    engine.clear(theme::BG);
    drawProfile(engine, 0, "en");
    savePng(engine, outDir + "/v73_16_profile_guest_en.png");
    {
        Account a;
        a.signedIn = true;
        a.displayName = "sliver fox";
        a.username = "sliverfox";
        a.plan = Tier::Legendary;
        a.acBalance = 240;
        a.downloads = 24;
        a.favorites = 8;
        a.installed = 5;
        a.downloadLimit = -1;
        setAccount(a);
    }
    engine.clear(theme::BG);
    drawProfile(engine, 1, "ar");
    savePng(engine, outDir + "/v73_17_profile_ar.png");

    // 7. Plans (bg_plans v7.3) + Settings dark/light + confirm + toast
    engine.clear(theme::BG);
    drawPlans(engine, -1, "en");
    savePng(engine, outDir + "/v73_18_plans_en.png");

    engine.clear(theme::BG);
    drawSettings(engine, 0, -1, true, "ar");
    savePng(engine, outDir + "/v73_19_settings_dark_ar.png");
    setThemeLight(true);
    engine.clear(theme::BG);
    drawSettings(engine, 1, -1, true, "en");
    savePng(engine, outDir + "/v73_20_settings_light_en.png");
    setThemeLight(false);

    engine.clear(theme::BG);
    drawConfirmDialog(engine, "Sign out?", "You can sign in again with a code.", "Sign out", "Cancel", 0, "en");
    savePng(engine, outDir + "/v73_21_confirm_en.png");

    engine.clear(theme::BG);
    drawHome(engine, 0, 0, 0, "ar");
    drawToast(engine, "تم تثبيت Sulfur Cars by sliver fox — استمتع!", "ar");
    savePng(engine, outDir + "/v73_22_toast_ar.png");

    std::printf("done: %s\n", outDir.c_str());
    return 0;
}
