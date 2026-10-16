// AddonStation "pkg addon test" PS4 Mini-App — design UI (splash + welcome + installer)
//
// Performance: the UiEngine rasterizes into its own RGBA framebuffer (no SDL
// textures) and each screen is redrawn ONLY when something changes — the frame
// is then blitted/presented like the working v0.5 app:
//     SDL_RenderPresent(renderer); SDL_UpdateWindowSurface(window);
//
// Content: NOTHING is bundled anymore. Pressing Download fetches the .raw from
// the GitHub Release (Supabase <-> GitHub Releases pipeline) into
// /data/AddonStation/installed/ and the dual-mode installer takes over:
// Minecraft open  -> copy into com.mojang immediately + cleanup + notification
// Minecraft closed-> stage, launch Minecraft, auto-sync, delete the .raw
// CUSAxxxxx and userYYYY are always auto-detected.

#include "http_download.hpp"
#include "installer.hpp"
#include "screens.hpp"

#include <SDL2/SDL.h>

#include <algorithm>
#include <atomic>
#include <chrono>
#include <cstdio>
#include <string>
#include <thread>
#include <vector>

namespace {

constexpr const char* kStageDir = "/data/AddonStation/installed";
constexpr const char* kAddonFolderName = "PrizmaVisuals 1.3.10222";
constexpr const char* kDownloadUrl =
    "https://github.com/mohamadelfrgena-ai/ps/releases/download/"
    "content-prizmavisuals-1.3.10222/prizmavisuals-1-3-10_3bcccbf1.raw";

const char* kLangCodes[8] = {"en", "ar", "fr", "tr", "es", "ru", "pt", "zh"};

enum class Screen { Splash, Welcome, Language, Installing };

struct AppState {
    Screen screen = Screen::Splash;
    int focus = 0;
    int langIndex = 0;
    pkgtest::DemoState demoState = pkgtest::DemoState::Idle;
    int progress = 0;
    std::string toastText;
    double toastUntil = 0.0;
    std::atomic<bool> installing{false};
};

double nowSeconds() {
    return std::chrono::duration<double>(std::chrono::steady_clock::now().time_since_epoch()).count();
}

int frameSignature(const AppState& s) {
    return static_cast<int>(s.screen) * 10000000 +
           s.focus * 100000 +
           s.langIndex * 10000 +
           static_cast<int>(s.demoState) * 100 +
           (s.progress >= 100 ? 99 : s.progress);
}

} // namespace

int main(int, char**) {
    setvbuf(stdout, nullptr, _IONBF, 0);

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

    // Download the .raw from the GitHub Release, then install it (dual-mode)
    auto triggerInstall = [&state, &currentLang]() {
        if (state.installing.load()) return;
        state.installing.store(true);
        state.demoState = pkgtest::DemoState::Downloading;
        state.progress = 0;

        std::thread([&, lang = currentLang()]() {
            const std::string rawPath =
                std::string(kStageDir) + "/" + kAddonFolderName + ".raw";

            const pkgtest::DownloadResult dl = pkgtest::downloadToFile(
                kDownloadUrl,
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
                kAddonFolderName,
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
                state.toastText = pkgtest::formatEnjoyNotification(kAddonFolderName, lang);
                state.toastUntil = nowSeconds() + 6.0;
            } else {
                state.demoState = pkgtest::DemoState::Idle;
                state.progress = 0;
            }
            state.installing.store(false);
        }).detach();
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
                if (state.screen == Screen::Welcome) {
                    if (hat & SDL_HAT_UP) state.focus = (state.focus + 2) % 3;
                    if (hat & SDL_HAT_DOWN) state.focus = (state.focus + 1) % 3;
                    stateChanged = true;
                } else if (state.screen == Screen::Language) {
                    if (hat & SDL_HAT_UP) state.focus = (state.focus + 6) % 8;
                    if (hat & SDL_HAT_DOWN) state.focus = (state.focus + 2) % 8;
                    if (hat & SDL_HAT_LEFT) state.focus = (state.focus + 7) % 8;
                    if (hat & SDL_HAT_RIGHT) state.focus = (state.focus + 1) % 8;
                    stateChanged = true;
                }
            } else if (ev.type == SDL_JOYBUTTONDOWN) {
                const Uint8 b = ev.jbutton.button;
                if (b == 1) {
                    if (state.screen == Screen::Language || state.screen == Screen::Installing) {
                        state.screen = Screen::Welcome;
                    } else {
                        running = false;
                    }
                    stateChanged = true;
                } else if (b == 0 || b == 2) {
                    if (state.screen == Screen::Splash) {
                        state.screen = Screen::Welcome;
                    } else if (state.screen == Screen::Welcome) {
                        if (b == 2) {
                            state.screen = Screen::Language;
                            state.focus = state.langIndex;
                        } else {
                            state.screen = Screen::Installing;
                        }
                    } else if (state.screen == Screen::Language) {
                        state.langIndex = state.focus;
                        state.screen = Screen::Welcome;
                    } else if (state.screen == Screen::Installing) {
                        triggerInstall();
                    }
                    stateChanged = true;
                }
            } else if (ev.type == SDL_KEYDOWN) {
                const SDL_Keycode k = ev.key.keysym.sym;
                if (k == SDLK_ESCAPE) running = false;
                if (state.screen == Screen::Splash) {
                    if (k == SDLK_RETURN || k == SDLK_SPACE) state.screen = Screen::Welcome;
                } else if (state.screen == Screen::Welcome) {
                    if (k == SDLK_UP) state.focus = (state.focus + 2) % 3;
                    if (k == SDLK_DOWN) state.focus = (state.focus + 1) % 3;
                    if (k == SDLK_RETURN) state.screen = Screen::Installing;
                    if (k == SDLK_l) state.screen = Screen::Language;
                } else if (state.screen == Screen::Language) {
                    if (k == SDLK_UP) state.focus = (state.focus + 6) % 8;
                    if (k == SDLK_DOWN) state.focus = (state.focus + 2) % 8;
                    if (k == SDLK_LEFT) state.focus = (state.focus + 7) % 8;
                    if (k == SDLK_RIGHT) state.focus = (state.focus + 1) % 8;
                    if (k == SDLK_RETURN) {
                        state.langIndex = state.focus;
                        state.screen = Screen::Welcome;
                    }
                    if (k == SDLK_BACKSPACE) state.screen = Screen::Welcome;
                } else if (state.screen == Screen::Installing) {
                    if (k == SDLK_RETURN || k == SDLK_SPACE) triggerInstall();
                    if (k == SDLK_BACKSPACE) state.screen = Screen::Welcome;
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

        // Redraw ONLY when something changed (perf fix):
        // - static screens (welcome/language/done) render once and reuse the frame
        // - the animated splash redraws at 30fps while animating
        const int sig = frameSignature(state);
        const bool toastVisible = !state.toastText.empty() && t < state.toastUntil;
        const bool splashAnimating =
            state.screen == Screen::Splash && (t - splashStart) < 8.2;
        const bool splashFrame = splashAnimating && (frameCount % 2 == 0);

        if (stateChanged || sig != lastSig || toastVisible != lastToastVisible || splashFrame) {
            engine.clear(pkgtest::theme::BG);
            switch (state.screen) {
                case Screen::Splash:
                    pkgtest::drawSplash(engine, static_cast<float>(t - splashStart), lang);
                    break;
                case Screen::Welcome:
                    pkgtest::drawWelcome(engine, state.focus, lang);
                    break;
                case Screen::Language:
                    pkgtest::drawLanguageDialog(engine, state.focus, lang);
                    break;
                case Screen::Installing:
                    pkgtest::drawInstallDemo(engine, state.demoState, state.progress, lang);
                    if (toastVisible) {
                        pkgtest::drawToast(engine, state.toastText, lang);
                    }
                    break;
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
        if (joy) SDL_JoystickClose(joy);
    }
    SDL_DestroyRenderer(renderer);
    SDL_DestroyWindow(window);
    SDL_Quit();
    return 0;
}
