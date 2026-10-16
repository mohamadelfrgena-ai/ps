#ifndef PKG_ADDON_TEST_SCREENS_HPP
#define PKG_ADDON_TEST_SCREENS_HPP

#include "ui_engine.hpp"

#include <string>

namespace pkgtest {

// Palettes from SPEC_PS4_COMPLETE.md (dark TV theme 1920x1080)
namespace theme {
constexpr SDL_Color BG{10, 15, 28, 255};        // #0A0F1C
constexpr SDL_Color SF{18, 26, 43, 255};        // #121A2B
constexpr SDL_Color SF2{26, 36, 56, 255};       // #1A2438
constexpr SDL_Color BAR{14, 21, 38, 255};       // #0E1526
constexpr SDL_Color BD{34, 48, 73, 255};        // #223049
constexpr SDL_Color TX{234, 240, 250, 255};     // #EAF0FA
constexpr SDL_Color MU{157, 176, 207, 255};     // #9DB0CF
constexpr SDL_Color CHIP{196, 209, 232, 255};   // #C4D1E8
constexpr SDL_Color BL{46, 123, 239, 255};      // #2E7BEF
constexpr SDL_Color BL2{111, 168, 255, 255};    // #6FA8FF
constexpr SDL_Color GD{240, 194, 94, 255};      // #F0C25E
constexpr SDL_Color GDB{138, 106, 34, 255};     // #8A6A22
constexpr SDL_Color EP{155, 107, 255, 255};     // #9B6BFF
constexpr SDL_Color EPT{183, 155, 255, 255};    // #B79BFF
constexpr SDL_Color OK{63, 191, 106, 255};      // #3FBF6A
constexpr SDL_Color OKT{127, 217, 154, 255};    // #7FD99A
constexpr SDL_Color WR{240, 160, 75, 255};      // #F0A04B
constexpr SDL_Color ER{255, 107, 120, 255};     // #FF6B78
constexpr SDL_Color ERT{255, 135, 145, 255};    // #FF8791
constexpr SDL_Color FOCUS{156, 193, 255, 255};  // #9CC1FF
} // namespace theme

// t = seconds since splash start. Shows art zoom, icon pop, slogans slide-in, 10 gold loader segments.
void drawSplash(UiEngine& e, float t, const std::string& langCode);

// focusIndex: 0 = code button, 1 = email button, 2 = guest button
void drawWelcome(UiEngine& e, int focusIndex, const std::string& langCode);

// Language dialog with 8 languages (grid), selectedLang 0..7
void drawLanguageDialog(UiEngine& e, int selectedLang, const std::string& langCode);

// Top pill toast (green check) e.g. "تم تثبيت [اسم الإضافة] — استمتع!"
void drawToast(UiEngine& e, const std::string& text, const std::string& langCode);

// Bottom controller legend bar: pairs of (button glyph, label)
void drawLegendBar(UiEngine& e,
                   const std::vector<std::pair<std::string, std::string>>& items,
                   const std::string& langCode);

// Install screen: big title, cover art, state button (Downloading/Installing/Installed), progress bar
enum class DemoState { Idle = 0, Downloading = 1, Installing = 2, Installed = 3 };
void drawInstallDemo(UiEngine& e, DemoState state, int percent, const std::string& langCode);

// Top navigation bar with L1/R1 tabs (Home Deals Store Chat Settings) + bell + AC pill + avatar
void drawTopNav(UiEngine& e, int activeTab, const std::string& langCode, int acBalance);

// --- interactive screens (every button navigates / acts) ---
void drawCodeLink(UiEngine& e, const std::string& code, int secondsLeft,
                  bool linked, const std::string& langCode);
void drawEmailSignin(UiEngine& e, int focus, const std::string& email,
                     const std::string& pass, bool showError,
                     const std::string& langCode);
// filterIndex: active Home category chip 0..6 (All Addons Worlds Free Epic Legendary New)
void drawHome(UiEngine& e, int focus, int heroIndex, int filterIndex, const std::string& langCode);
// Deals campaign landing: chips 0..4 then 5-card grid 5..9
void drawDeals(UiEngine& e, int focus, int filterIndex, const std::string& langCode);
// filterIndex: active Store filter chip 0..2 (Addons Worlds Newest)
void drawStore(UiEngine& e, int focus, int filterIndex, const std::string& langCode);
void drawChat(UiEngine& e, int focus, bool roomOpen, const std::string& langCode);
void drawSettings(UiEngine& e, int focus, bool notifOn, const std::string& langCode);
void drawConfirmDialog(UiEngine& e, const std::string& title, const std::string& sub,
                       const std::string& yesLabel, const std::string& noLabel,
                       int focus, const std::string& langCode);

std::string tr(const std::string& key, const std::string& langCode);

} // namespace pkgtest

#endif
