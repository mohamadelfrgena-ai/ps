// AddonStation "pkg addon test" PS4 Mini-App — full interactive design UI
//
// Performance: the UiEngine rasterizes into its own RGBA framebuffer (no SDL
// textures) and each screen is redrawn ONLY when something changes — the frame
// is then blitted/presented like the working v0.5 app:
//     SDL_RenderPresent(renderer); SDL_UpdateWindowSurface(window);
//
// Navigation (v0.12): EVERY button has its real action — Welcome (code /
// email / guest) leads to its own screen; ONLY content cards open the addon
// install page; category/filter chips apply a filter (visible active chip);
// banner Back returns; "Shop the sale" opens Deals; L1/R1 switch tabs only
// inside the app (Home · Deals · Store · Chat · Settings) and L2/R2 change
// the Home offer; Settings rows act (language / notifications toggle /
// auto-launch Minecraft after install / link console / sign out with confirm
// dialog), text fields open the PS4 system
// keyboard (sceImeDialog), Download installs content from the GitHub Release.

#include "content_config.hpp"
#include "http_download.hpp"
#include "ime_dialog.hpp"
#include "installer.hpp"
#include "screens.hpp"

#include <SDL2/SDL.h>

#include <algorithm>
#include <atomic>
#include <chrono>
#include <cstdio>
#include <cstdlib>
#include <string>
#include <thread>
#include <vector>

namespace {

constexpr const char* kStageDir = "/data/AddonStation/installed";

const char* kLangCodes[8] = {"en", "ar", "fr", "tr", "es", "ru", "pt", "zh"};

enum class Screen {
    Splash, Welcome, CodeLink, Email, Language,
    Home, Deals, Store, Chat, Settings, Detail, Confirm
};

struct AppState {
    Screen screen = Screen::Splash;
    Screen prevScreen = Screen::Welcome;
    Screen tabScreen = Screen::Home;   // active L1/R1 tab
    int focus = 0;
    int langIndex = 0;
    pkgtest::DemoState demoState = pkgtest::DemoState::Idle;
    int progress = 0;
    std::string toastText;
    double toastUntil = 0.0;

    // code link
    std::string code = "K7P 4QX";
    double codeUntil = 0.0;
    bool linked = false;

    // email
    std::string email;
    std::string pass;
    bool emailError = false;

    int heroIndex = 0;
    int filterIndex = 0;   // Home category chip 0..6
    int storeFilter = 0;   // Store filter chip 0..2
    int dealsFilter = 0;   // Deals chip 0..4
    bool notifOn = true;
    bool autoLaunch = false;   // «تشغيل Minecraft تلقائياً بعد التثبيت» (يُقرأ من الإعدادات)
    bool roomOpen = false;
    bool saved = false;

    std::atomic<bool> installing{false};
};

double nowSeconds() {
    return std::chrono::duration<double>(std::chrono::steady_clock::now().time_since_epoch()).count();
}

const char* kHomeChips[7] = {"All", "Addons", "Worlds", "Free", "Epic", "Legendary", "New"};
const char* kStoreFilters[3] = {"Addons", "Worlds", "Newest"};
const char* kDealsChips[5] = {"All", "Addons", "Worlds", "Under 100 AC", "Biggest discount"};

std::string makeCode() {
    static const char* alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    std::string c;
    for (int i = 0; i < 6; ++i) {
        if (i == 3) c += ' ';
        c += alphabet[std::rand() % 32];
    }
    return c;
}

int frameSignature(const AppState& s, double t) {
    const int codeSecs = static_cast<int>(std::max(0.0, s.codeUntil - t));
    int sig = static_cast<int>(s.screen) * 100000000 +
              s.focus * 1000000 +
              s.langIndex * 100000 +
              static_cast<int>(s.demoState) * 10000 +
              (s.progress >= 100 ? 99 : s.progress) * 100 +
              (s.linked ? 11 : (codeSecs % 60));
    sig += s.heroIndex * 31337 + s.filterIndex * 2711 +
           s.storeFilter * 397 + s.dealsFilter * 53 +
           (s.autoLaunch ? 31 : 0) + (s.notifOn ? 17 : 0);
    return sig;
}

} // namespace

int main(int, char**) {
    setvbuf(stdout, nullptr, _IONBF, 0);
    std::srand(static_cast<unsigned>(nowSeconds()));

    if (SDL_Init(SDL_INIT_VIDEO | SDL_INIT_JOYSTICK) != 0) {
        return 1;
    }

    SDL_Window* window = SDL_CreateWindow(
        "pkg addon test",
        SDL_WINDOWPOS_UNDEFINED,
        SDL_WINDOWPOS_UNDEFINED,
        1920,
        1080,
        0);
    if (!window) {
        SDL_Quit();
        return 1;
    }

    SDL_Surface* windowSurface = SDL_GetWindowSurface(window);
    SDL_Renderer* renderer = windowSurface ? SDL_CreateSoftwareRenderer(windowSurface) : nullptr;
    if (!renderer) {
        SDL_DestroyWindow(window);
        SDL_Quit();
        return 1;
    }

    std::vector<SDL_Joystick*> joysticks;
    const int numJoy = SDL_NumJoysticks();
    for (int i = 0; i < numJoy; ++i) {
        if (SDL_Joystick* joy = SDL_JoystickOpen(i)) {
            joysticks.push_back(joy);
        }
    }
    SDL_JoystickEventState(SDL_ENABLE);

    pkgtest::UiEngine engine;
    engine.init("/app0/assets/ui.pak");

    AppState state;
    const double bootTime = nowSeconds();
    double splashStart = bootTime;
    bool firstLaunch = true;
    state.codeUntil = bootTime + 582.0;   // 9:42 like the design
    state.autoLaunch = pkgtest::loadAutoLaunchSetting();

    auto currentLang = [&state]() { return std::string(kLangCodes[state.langIndex]); };
    auto toast = [&state](const std::string& text) {
        state.toastText = text;
        state.toastUntil = nowSeconds() + 4.5;
    };
    auto go = [&state](Screen s) {
        if (state.screen != s) state.prevScreen = state.screen;
        state.screen = s;
        state.focus = 0;
    };

    // Background auto-sync: delivers staged installs the moment com.mojang is
    // mounted (game open/suspended) — and flips the button to "Installed ✓"
    // with the completion toast when the deferred install finishes.
    std::thread syncWatcher([&state, &currentLang, &toast]() {
        while (true) {
            if (!state.installing.load()) {
                const pkgtest::CopyResult res =
                    pkgtest::syncPendingAddonsIfMounted(kStageDir, "", currentLang());
                if (res.ok && state.demoState == pkgtest::DemoState::Installing) {
                    state.demoState = pkgtest::DemoState::Installed;
                    state.progress = 100;
                    toast(pkgtest::formatEnjoyNotification(
                        res.lastAddonName.empty() ? "the addon" : res.lastAddonName,
                        currentLang()));
                }
            }
            std::this_thread::sleep_for(std::chrono::seconds(2));
        }
    });
    syncWatcher.detach();

    // Download the .raw from the GitHub Release, then install it (dual-mode)
    auto triggerInstall = [&state, &currentLang, &toast]() {
        if (state.installing.load()) return;
        if (std::string(pkgtest::content::kAddonFolderName).empty() ||
            std::string(pkgtest::content::kDownloadUrl).empty()) {
            state.demoState = pkgtest::DemoState::Idle;
            state.progress = 0;
            toast("No content configured yet — add it in content_config.hpp");
            return;
        }
        state.installing.store(true);
        state.demoState = pkgtest::DemoState::Downloading;
        state.progress = 0;

        std::thread([&, lang = currentLang()]() {
            const std::string rawPath =
                std::string(kStageDir) + "/" + pkgtest::content::kAddonFolderName + ".raw";

            const pkgtest::DownloadResult dl = pkgtest::downloadToFile(
                pkgtest::content::kDownloadUrl,
                rawPath,
                [&](int percent) {
                    state.demoState = pkgtest::DemoState::Downloading;
                    state.progress = percent;
                });

            if (!dl.ok) {
                state.demoState = pkgtest::DemoState::Idle;
                state.progress = 0;
                state.installing.store(false);
                return;
            }

            state.demoState = pkgtest::DemoState::Installing;
            state.progress = 10;

            const pkgtest::CopyResult res = pkgtest::installOrQueueAddon(
                rawPath,
                pkgtest::content::kAddonFolderName,
                kStageDir,
                "",
                "resource_packs",
                lang,
                [&](pkgtest::InstallPhase phase, int percent) {
                    if (phase == pkgtest::InstallPhase::Installing) {
                        state.demoState = pkgtest::DemoState::Installing;
                        state.progress = std::min(100, 10 + percent * 9 / 10);
                    } else if (phase == pkgtest::InstallPhase::Installed) {
                        state.demoState = pkgtest::DemoState::Installed;
                        state.progress = 100;
                    }
                },
                state.autoLaunch);

            if (res.ok && res.queuedForMinecraft) {
                state.demoState = pkgtest::DemoState::Installing;
                state.progress = 75;
            } else if (res.ok) {
                state.demoState = pkgtest::DemoState::Installed;
                state.progress = 100;
                state.toastText = pkgtest::formatEnjoyNotification(pkgtest::content::kAddonFolderName, lang);
                state.toastUntil = nowSeconds() + 6.0;
            } else {
                state.demoState = pkgtest::DemoState::Idle;
                state.progress = 0;
            }
            state.installing.store(false);
        }).detach();
    };

    // ---- input helpers -------------------------------------------------
    // Joystick map (as used by the working v0.5 app): 0=✕ 1=○ 2=□ 3=△ 4=L1 5=R1 6=L2 7=R2
    auto back = [&]() {
        switch (state.screen) {
            case Screen::Splash: break;
            case Screen::Welcome: break;  // stay (guest entry screen)
            case Screen::CodeLink: go(Screen::Welcome); break;
            case Screen::Email: go(Screen::Welcome); break;
            case Screen::Language: go(state.prevScreen == Screen::Language ? Screen::Welcome : state.prevScreen); break;
            case Screen::Home:
            case Screen::Deals:
            case Screen::Store:
            case Screen::Chat:
            case Screen::Settings:
                go(Screen::Welcome);
                break;
            case Screen::Detail: go(state.tabScreen); break;
            case Screen::Confirm: go(Screen::Settings); break;
        }
    };

    auto activate = [&](int cross) {
        (void)cross;
        switch (state.screen) {
            case Screen::Splash:
                go(Screen::Welcome);
                break;
            case Screen::Welcome:
                if (state.focus == 0) { state.linked = false; state.code = makeCode(); state.codeUntil = nowSeconds() + 582.0; go(Screen::CodeLink); }
                else if (state.focus == 1) go(Screen::Email);
                else { state.tabScreen = Screen::Home; go(Screen::Home); }
                break;
            case Screen::CodeLink:
                state.code = makeCode();
                state.codeUntil = nowSeconds() + 582.0;
                state.linked = false;
                toast("New code generated");
                break;
            case Screen::Email:
                if (state.focus == 0) {
                    const std::string t = pkgtest::openImeDialog("Email", state.email, 64);
                    if (!t.empty()) { state.email = t; state.emailError = false; }
                } else if (state.focus == 1) {
                    const std::string t = pkgtest::openImeDialog("Password", state.pass, 32);
                    if (!t.empty()) state.pass = t;
                } else {
                    if (state.email.empty()) { state.emailError = true; }
                    else { state.emailError = false; state.tabScreen = Screen::Home; go(Screen::Home); }
                }
                break;
            case Screen::Language:
                state.langIndex = state.focus;
                go(state.prevScreen == Screen::Language ? Screen::Welcome : state.prevScreen);
                break;
            case Screen::Home:
                // banner primary -> store, banner back -> return,
                // exclusive/recommended cards -> the addon page,
                // category chips -> apply a filter (never the install page)
                if (state.focus == 0) { state.tabScreen = Screen::Store; go(Screen::Store); }
                else if (state.focus == 1) { back(); }
                else if (state.focus <= 4) { go(Screen::Detail); }
                else if (state.focus <= 11) {
                    state.filterIndex = state.focus - 5;
                    toast(std::string("Filter: ") + kHomeChips[state.filterIndex]);
                } else { go(Screen::Detail); }
                break;
            case Screen::Deals:
                if (state.focus <= 4) {
                    state.dealsFilter = state.focus;
                    toast(std::string("Filter: ") + kDealsChips[state.dealsFilter]);
                } else { go(Screen::Detail); }
                break;
            case Screen::Store:
                // cards -> the addon page, promo button -> Deals, chips -> filter
                if (state.focus <= 4) { go(Screen::Detail); }
                else if (state.focus == 8) { state.tabScreen = Screen::Deals; go(Screen::Deals); }
                else {
                    state.storeFilter = std::min(2, std::max(0, state.focus - 9));
                    toast(std::string("Filter: ") + kStoreFilters[state.storeFilter]);
                }
                break;
            case Screen::Chat:
                if (!state.roomOpen) { state.roomOpen = true; state.focus = 0; }
                else {
                    const std::string t = pkgtest::openImeDialog("Message", "", 120);
                    if (!t.empty()) toast("Message sent: " + t);
                }
                break;
            case Screen::Settings:
                if (state.focus == 0) { state.focus = state.langIndex; state.prevScreen = state.screen; state.screen = Screen::Language; }
                else if (state.focus == 1) toast("Account: name@example.com");
                else if (state.focus == 2) { state.notifOn = !state.notifOn; toast(state.notifOn ? "Notifications ON" : "Notifications OFF"); }
                else if (state.focus == 3) {
                    state.autoLaunch = !state.autoLaunch;
                    pkgtest::saveAutoLaunchSetting(state.autoLaunch);
                    toast(pkgtest::tr(state.autoLaunch ? "autolaunch_on" : "autolaunch_off", currentLang()));
                }
                else if (state.focus == 4) { state.linked = false; state.code = makeCode(); state.codeUntil = nowSeconds() + 582.0; go(Screen::CodeLink); }
                else if (state.focus == 5) toast("AddonStation PS4 v0.12 — official app");
                else go(Screen::Confirm);
                break;
            case Screen::Detail:
                triggerInstall();
                break;
            case Screen::Confirm:
                if (state.focus == 0) go(Screen::Settings);
                else { toast("Signed out"); state.email.clear(); state.pass.clear(); go(Screen::Welcome); }
                break;
        }
    };

    auto moveFocus = [&](int dx, int dy) {
        switch (state.screen) {
            case Screen::Welcome: {
                if (dy != 0) state.focus = (state.focus + (dy > 0 ? 1 : 2)) % 3;
                break;
            }
            case Screen::Email: {
                if (dy != 0) state.focus = std::max(0, std::min(2, state.focus + dy));
                break;
            }
            case Screen::Language: {
                if (dy != 0) state.focus = (state.focus + (dy > 0 ? 1 : 7)) % 8;
                if (dx != 0) state.focus = (state.focus + (dx > 0 ? 1 : 7)) % 8;
                break;
            }
            case Screen::Home: {
                // rows: [0-1] banner | [2-4] exclusives | [5-11] chips | [12-16] recommended
                static const int rowStart[4] = {0, 2, 5, 12};
                static const int rowLen[4] = {2, 3, 7, 5};
                int row = 0, col = state.focus;
                for (int r = 3; r >= 0; --r) {
                    if (state.focus >= rowStart[r]) { row = r; col = state.focus - rowStart[r]; break; }
                }
                if (dy != 0) {
                    row = std::max(0, std::min(3, row + dy));
                    col = std::min(col, rowLen[row] - 1);
                } else if (dx != 0) {
                    col = (col + dx + rowLen[row]) % rowLen[row];
                }
                state.focus = rowStart[row] + col;
                break;
            }
            case Screen::Store: {
                // rows top->bottom: promo button [8] | filters [9-11] | cards [0-4]
                int row = (state.focus == 8) ? 0 : (state.focus >= 9 ? 1 : 2);
                int col = (row == 1) ? state.focus - 9 : (row == 2 ? state.focus : 0);
                if (dy != 0) {
                    row = (row + (dy > 0 ? 1 : 2)) % 3;
                    col = std::min(col, row == 1 ? 2 : (row == 2 ? 4 : 0));
                } else if (dx != 0 && row != 0) {
                    const int len = (row == 1 ? 3 : 5);
                    col = (col + dx + len) % len;
                }
                state.focus = (row == 0) ? 8 : (row == 1 ? 9 + col : col);
                break;
            }
            case Screen::Deals: {
                // rows: filter chips [0-4] | card grid [5-9]
                int row = state.focus < 5 ? 0 : 1;
                int col = state.focus - (row ? 5 : 0);
                if (dy != 0) {
                    row = 1 - row;
                    col = std::min(col, 4);
                } else if (dx != 0) {
                    col = (col + dx + 5) % 5;
                }
                state.focus = row * 5 + col;
                break;
            }
            case Screen::Chat: {
                if (state.roomOpen) break;
                if (dy != 0) state.focus = std::max(0, std::min(4, state.focus + dy));
                break;
            }
            case Screen::Settings: {
                if (dy != 0) state.focus = std::max(0, std::min(6, state.focus + dy));
                break;
            }
            case Screen::Confirm: {
                if (dy != 0) state.focus = 1 - state.focus;
                break;
            }
            case Screen::Detail:
            case Screen::CodeLink:
            case Screen::Splash:
                break;
        }
    };

    auto switchTab = [&](int dir) {
        // L1/R1 tabs are ONLY for the app area (Home Deals Store Chat Settings)
        switch (state.screen) {
            case Screen::Home:
            case Screen::Deals:
            case Screen::Store:
            case Screen::Chat:
            case Screen::Settings:
            case Screen::Detail:
                break;
            default:
                return;
        }
        static const Screen tabs[5] = {Screen::Home, Screen::Deals, Screen::Store, Screen::Chat, Screen::Settings};
        int idx = 0;
        if (state.screen == Screen::Deals) idx = 1;
        else if (state.screen == Screen::Store || state.screen == Screen::Detail) idx = 2;
        else if (state.screen == Screen::Chat) idx = 3;
        else if (state.screen == Screen::Settings) idx = 4;
        idx = (idx + dir + 5) % 5;
        state.tabScreen = tabs[idx];
        state.roomOpen = false;
        go(tabs[idx]);
    };

    bool running = true;
    SDL_Event ev;
    int lastSig = -1;
    bool lastToastVisible = false;
    long long frameCount = 0;

    while (running) {
        const double t = nowSeconds();
        const std::string lang = currentLang();
        bool stateChanged = false;

        while (SDL_PollEvent(&ev)) {
            if (ev.type == SDL_QUIT) {
                running = false;
            } else if (ev.type == SDL_JOYHATMOTION) {
                const Uint8 hat = ev.jhat.value;
                if (hat & SDL_HAT_UP) moveFocus(0, -1);
                if (hat & SDL_HAT_DOWN) moveFocus(0, 1);
                if (hat & SDL_HAT_LEFT) moveFocus(-1, 0);
                if (hat & SDL_HAT_RIGHT) moveFocus(1, 0);
                stateChanged = true;
            } else if (ev.type == SDL_JOYBUTTONDOWN) {
                const Uint8 b = ev.jbutton.button;
                if (b == 0) { activate(1); stateChanged = true; }
                else if (b == 1) { back(); stateChanged = true; }
                else if (b == 2) { // square - filter / language / save
                    if (state.screen == Screen::Detail) {
                        state.saved = !state.saved;
                        toast(state.saved ? "Saved to your favorites" : "Removed from favorites");
                    } else if (state.screen == Screen::Store) {
                        state.focus = 9 + state.storeFilter;      // jump to filter chips
                        stateChanged = true;
                    } else if (state.screen == Screen::Deals) {
                        state.focus = state.dealsFilter;          // jump to filter chips
                        stateChanged = true;
                    } else if (state.screen == Screen::Home || state.screen == Screen::Welcome ||
                               state.screen == Screen::Chat) {
                        state.prevScreen = state.screen;
                        state.focus = state.langIndex;
                        if (state.screen != Screen::Language) state.screen = Screen::Language;
                        stateChanged = true;
                    }
                } else if (b == 3) { // triangle - open Minecraft / sort
                    if (state.screen == Screen::Detail) {
                        toast("Opening Minecraft\xE2\x80\xA6");
                        pkgtest::launchMinecraftOnPs4();
                    } else if (state.screen == Screen::Deals) toast("Sorted by biggest discount");
                    else if (state.screen == Screen::Store) toast("Sorted by newest");
                    stateChanged = true;
                } else if (b == 4) { switchTab(-1); stateChanged = true; }             // L1
                else if (b == 5) { switchTab(1); stateChanged = true; }                // R1
                else if (b == 6) {                                                   // L2 - prev offer on Home
                    if (state.screen == Screen::Home) state.heroIndex = (state.heroIndex + 2) % 3;
                    else switchTab(-1);
                    stateChanged = true;
                } else if (b == 7) {                                                 // R2 - next offer on Home
                    if (state.screen == Screen::Home) state.heroIndex = (state.heroIndex + 1) % 3;
                    else switchTab(1);
                    stateChanged = true;
                }
            } else if (ev.type == SDL_KEYDOWN) {
                const SDL_Keycode k = ev.key.keysym.sym;
                if (k == SDLK_ESCAPE) running = false;
                if (k == SDLK_UP) moveFocus(0, -1);
                else if (k == SDLK_DOWN) moveFocus(0, 1);
                else if (k == SDLK_LEFT) moveFocus(-1, 0);
                else if (k == SDLK_RIGHT) moveFocus(1, 0);
                else if (k == SDLK_RETURN || k == SDLK_SPACE) activate(1);
                else if (k == SDLK_BACKSPACE) back();
                else if (k == SDLK_l) {
                    state.prevScreen = state.screen;
                    state.focus = state.langIndex;
                    if (state.screen != Screen::Language) state.screen = Screen::Language;
                } else if (k == SDLK_LEFTBRACKET) {
                    if (state.screen == Screen::Home) state.heroIndex = (state.heroIndex + 2) % 3;
                    else switchTab(-1);
                } else if (k == SDLK_RIGHTBRACKET) {
                    if (state.screen == Screen::Home) state.heroIndex = (state.heroIndex + 1) % 3;
                    else switchTab(1);
                }
                stateChanged = true;
            }
        }

        // Auto-advance splash: 8s first launch, 1.5s afterwards
        if (state.screen == Screen::Splash) {
            const double splashDur = firstLaunch ? 8.0 : 1.5;
            if (t - splashStart > splashDur) {
                state.screen = Screen::Welcome;
                firstLaunch = false;
                stateChanged = true;
            }
        }

        // code countdown expiry
        if (state.screen == Screen::CodeLink && !state.linked && t > state.codeUntil) {
            state.codeUntil = t;  // hold at 0
        }

        // Redraw ONLY when something changed (perf):
        const int sig = frameSignature(state, t);
        const bool toastVisible = !state.toastText.empty() && t < state.toastUntil;
        const bool splashAnimating =
            state.screen == Screen::Splash && (t - splashStart) < 8.2;
        const bool splashFrame = splashAnimating && (frameCount % 2 == 0);
        const bool codeAnim = state.screen == Screen::CodeLink && (frameCount % 20 == 0);

        if (stateChanged || sig != lastSig || toastVisible != lastToastVisible ||
            splashFrame || codeAnim) {
            engine.clear(pkgtest::theme::BG);
            switch (state.screen) {
                case Screen::Splash:
                    pkgtest::drawSplash(engine, static_cast<float>(t - splashStart), lang);
                    break;
                case Screen::Welcome:
                    pkgtest::drawWelcome(engine, state.focus, lang);
                    break;
                case Screen::CodeLink:
                    pkgtest::drawCodeLink(engine, state.code,
                                          static_cast<int>(std::max(0.0, state.codeUntil - t)),
                                          state.linked, lang);
                    break;
                case Screen::Email:
                    pkgtest::drawEmailSignin(engine, state.focus, state.email, state.pass,
                                             state.emailError, lang);
                    break;
                case Screen::Language:
                    pkgtest::drawLanguageDialog(engine, state.focus, lang);
                    break;
                    pkgtest::drawHome(engine, state.focus, state.heroIndex, state.filterIndex, lang);
                    break;
                case Screen::Deals:
                    pkgtest::drawDeals(engine, state.focus, state.dealsFilter, lang);
                    break;
                case Screen::Chat:
                    pkgtest::drawChat(engine, state.focus, state.roomOpen, lang);
                    break;
                case Screen::Settings:
                    pkgtest::drawSettings(engine, state.focus, state.notifOn, state.autoLaunch, lang);
                    break;
                case Screen::Detail:
                    pkgtest::drawInstallDemo(engine, state.demoState, state.progress, lang);
                    break;
                case Screen::Confirm:
                    pkgtest::drawSettings(engine, state.focus, state.notifOn, state.autoLaunch, lang);
                    pkgtest::drawConfirmDialog(engine, "Sign out?",
                                               "You can sign in again any time with a code.",
                                               "Sign out", "Cancel",
                                               state.focus, lang);
                    break;
            }
            if (toastVisible) {
                pkgtest::drawToast(engine, state.toastText, lang);
            }
            lastSig = sig;
            lastToastVisible = toastVisible;
        }

        engine.present(windowSurface);
        SDL_RenderPresent(renderer);
        SDL_UpdateWindowSurface(window);
        ++frameCount;
        SDL_Delay(16);
    }

    state.installing.store(true);
    for (SDL_Joystick* joy : joysticks) {
        SDL_JoystickClose(joy);
    }
    SDL_DestroyRenderer(renderer);
    SDL_DestroyWindow(window);
    SDL_Quit();
    return 0;
}
