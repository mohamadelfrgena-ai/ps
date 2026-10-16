// AddonStation "pkg addon test" PS4 Mini-App — full interactive design UI
//
// Performance: the UiEngine rasterizes into its own RGBA framebuffer (no SDL
// textures) and each screen is redrawn ONLY when something changes — the frame
// is then blitted/presented like the working v0.5 app:
//     SDL_RenderPresent(renderer); SDL_UpdateWindowSurface(window);
//
// Navigation: EVERY button works — Welcome (code / email / guest), L1/R1 tabs
// (Home · Deals · Store · Chat · Settings), cards open the install screen,
// Settings rows act (language / notifications toggle / link console / sign
// out with confirm dialog), text fields open the PS4 system keyboard
// (sceImeDialog), Download installs content from the GitHub Release.

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
    Home, Store, Chat, Settings, Detail, Confirm
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
    bool notifOn = true;
    bool roomOpen = false;
    bool saved = false;

    std::atomic<bool> installing{false};
};

double nowSeconds() {
    return std::chrono::duration<double>(std::chrono::steady_clock::now().time_since_epoch()).count();
}

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
    return static_cast<int>(s.screen) * 100000000 +
           s.focus * 1000000 +
           s.langIndex * 100000 +
           static_cast<int>(s.demoState) * 10000 +
           (s.progress >= 100 ? 99 : s.progress) * 100 +
           (s.linked ? 11 : (codeSecs % 60));
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

    std::thread syncWatcher([&state]() {
        while (true) {
            if (!state.installing.load()) {
                pkgtest::syncPendingAddonsIfMounted(kStageDir);
            }
            std::this_thread::sleep_for(std::chrono::seconds(2));
        }
    });
    syncWatcher.detach();

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
                });

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
                if (state.focus == 0) { state.tabScreen = Screen::Store; go(Screen::Store); }
                else if (state.focus == 1) { /* details */ go(Screen::Detail); }
                else go(Screen::Detail);
                break;
            case Screen::Store:
                if (state.focus >= 3) go(Screen::Detail);
                else toast("Filter applied");
                break;
            case Screen::Chat:
                if (!state.roomOpen) { state.roomOpen = true; state.focus = 0; }
                else {
                    const std::string t = pkgtest::openImeDialog("Message", "", 120);
                    if (!t.empty()) toast("Message sent: " + t);
                }
                break;
            case Screen::Settings:
                if (state.focus == 0) { state.focus = state.langIndex; state.screen = Screen::Language; }
                else if (state.focus == 1) toast("Account: name@example.com");
                else if (state.focus == 2) { state.notifOn = !state.notifOn; toast(state.notifOn ? "Notifications ON" : "Notifications OFF"); }
                else if (state.focus == 3) { state.linked = false; state.code = makeCode(); state.codeUntil = nowSeconds() + 582.0; go(Screen::CodeLink); }
                else if (state.focus == 4) toast("AddonStation PS4 v0.11 — pkg addon test");
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

    auto back = [&]() {
        switch (state.screen) {
            case Screen::Splash: break;
            case Screen::Welcome: break;  // stay (guest entry screen)
            case Screen::CodeLink: go(Screen::Welcome); break;
            case Screen::Email: go(Screen::Welcome); break;
            case Screen::Language: go(state.prevScreen == Screen::Language ? Screen::Welcome : state.prevScreen); break;
            case Screen::Home:
            case Screen::Store:
            case Screen::Chat:
            case Screen::Settings:
                go(Screen::Welcome);
                break;
            case Screen::Detail: go(state.tabScreen); break;
            case Screen::Confirm: go(Screen::Settings); break;
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
                // rows: [0-1] hero | [2-4] exclusives | [5-11] chips | [12-16] recommended
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
                // [0-4] cards | [5-7] filters | [8] shop button
                if (dy != 0) {
                    if (state.focus < 5) state.focus = (dy > 0) ? 5 + std::min(state.focus, 2) : 8;
                    else if (state.focus < 8) state.focus = (dy > 0) ? 8 : std::min(state.focus - 5, 4);
                    else state.focus = (dy > 0) ? std::min(state.focus, 4) : 5;
                } else if (dx != 0) {
                    if (state.focus < 5) state.focus = (state.focus + dx + 5) % 5;
                    else if (state.focus < 8) state.focus = 5 + (state.focus - 5 + dx + 3) % 3;
                }
                break;
            }
            case Screen::Chat: {
                if (state.roomOpen) break;
                if (dy != 0) state.focus = std::max(0, std::min(4, state.focus + dy));
                break;
            }
            case Screen::Settings: {
                if (dy != 0) state.focus = std::max(0, std::min(5, state.focus + dy));
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
        // tabs: Home Deals Store Chat Settings
        static const Screen tabs[5] = {Screen::Home, Screen::Home, Screen::Store, Screen::Chat, Screen::Settings};
        int idx = 0;
        for (int i = 0; i < 5; ++i) if (tabs[i] == state.tabScreen && (i != 1 || state.tabScreen == Screen::Home)) idx = i;
        if (state.screen == Screen::Home) idx = 0;
        else if (state.screen == Screen::Store) idx = 2;
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
                else if (b == 2) { // □ — language / save
                    if (state.screen == Screen::Detail) {
                        state.saved = !state.saved;
                        toast(state.saved ? "Saved to your favorites" : "Removed from favorites");
                    } else if (state.screen == Screen::Home || state.screen == Screen::Welcome ||
                               state.screen == Screen::Store || state.screen == Screen::Chat) {
                        state.focus = state.langIndex;
                        if (state.screen != Screen::Language) state.screen = Screen::Language;
                    }
                    stateChanged = true;
                } else if (b == 3) { // △ — share / menu
                    if (state.screen == Screen::Detail) toast("Share link copied");
                    stateChanged = true;
                } else if (b == 4 || b == 6) { switchTab(-1); stateChanged = true; }   // L1/L2
                else if (b == 5 || b == 7) { switchTab(1); stateChanged = true; }      // R1/R2
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
                    state.focus = state.langIndex;
                    if (state.screen != Screen::Language) state.screen = Screen::Language;
                } else if (k == SDLK_LEFTBRACKET) switchTab(-1);
                else if (k == SDLK_RIGHTBRACKET) switchTab(1);
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
                case Screen::Home:
                    pkgtest::drawHome(engine, state.focus, state.heroIndex, lang);
                    break;
                case Screen::Store:
                    pkgtest::drawStore(engine, state.focus, lang);
                    break;
                case Screen::Chat:
                    pkgtest::drawChat(engine, state.focus, state.roomOpen, lang);
                    break;
                case Screen::Settings:
                    pkgtest::drawSettings(engine, state.focus, state.notifOn, lang);
                    break;
                case Screen::Detail:
                    pkgtest::drawInstallDemo(engine, state.demoState, state.progress, lang);
                    break;
                case Screen::Confirm:
                    pkgtest::drawSettings(engine, state.focus, state.notifOn, lang);
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
