// AddonStation "pkg addon test" PS4 Mini-App — design UI (splash + welcome + installer)
// Dual-mode installer: copies PrizmaVisuals into com.mojang immediately if Minecraft is
// open; otherwise stages to /data/AddonStation/installed/, launches Minecraft, auto-syncs
// and deletes the staged .raw afterwards. CUSAxxxxx and userYYYY are always auto-detected.

#include "installer.hpp"
#include "screens.hpp"

#include <SDL2/SDL.h>

#include <atomic>
#include <chrono>
#include <cstdio>
#include <string>
#include <thread>

namespace {

constexpr const char* kStageDir = "/data/AddonStation/installed";
constexpr const char* kAddonFolderName = "PrizmaVisuals 1.3.10222";
constexpr const char* kSourceRaw = "/app0/assets/folder.raw";

const char* kLangCodes[8] = {"en", "ar", "fr", "tr", "es", "ru", "pt", "zh"};

enum class Screen { Splash, Welcome, Language, Installing };

struct AppState {
    Screen screen = Screen::Splash;
    int focus = 0;              // welcome: 0 code, 1 email, 2 guest; language: 0..7
    int langIndex = 0;
    pkgtest::DemoState demoState = pkgtest::DemoState::Idle;
    int progress = 0;
    std::string toastText;
    float toastUntil = 0.0f;
    std::atomic<bool> installing{false};
};

float nowSeconds() {
    return std::chrono::duration<float>(std::chrono::steady_clock::now().time_since_epoch()).count();
}

} // namespace

int main(int, char**) {
    if (SDL_Init(SDL_INIT_VIDEO | SDL_INIT_JOYSTICK) != 0) {
        std::fprintf(stderr, "SDL_Init failed: %s\n", SDL_GetError());
        return 1;
    }

    SDL_Joystick* pad = nullptr;
    if (SDL_NumJoysticks() > 0) {
        pad = SDL_JoystickOpen(0);
    }

    SDL_Window* window = SDL_CreateWindow(
        "AddonStation", SDL_WINDOWPOS_CENTERED, SDL_WINDOWPOS_CENTERED, 1920, 1080, 0);
    SDL_Surface* windowSurface = window ? SDL_GetWindowSurface(window) : nullptr;
    SDL_Renderer* renderer = windowSurface ? SDL_CreateSoftwareRenderer(windowSurface) : nullptr;
    if (!renderer) {
        std::fprintf(stderr, "No renderer available\n");
        return 1;
    }

    pkgtest::UiEngine engine;
    if (!engine.init(renderer, "/app0/assets/ui.pak")) {
        std::fprintf(stderr, "ui.pak load failed\n");
        return 1;
    }

    AppState state;
    const float bootTime = nowSeconds();
    float splashStart = bootTime;
    bool firstLaunch = true;

    // Background watcher: keeps auto-syncing staged addons whenever Minecraft mounts com.mojang
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

    auto triggerInstall = [&state, &currentLang]() {
        if (state.installing.load()) return;
        if (state.demoState == pkgtest::DemoState::Installed) {
            // Re-install allowed
        }
        state.installing.store(true);
        state.demoState = pkgtest::DemoState::Downloading;
        state.progress = 0;

        std::thread([&, lang = currentLang()]() {
            const pkgtest::CopyResult res = pkgtest::installOrQueueAddon(
                kSourceRaw,
                kAddonFolderName,
                kStageDir,
                "",
                "resource_packs",
                lang,
                [&](pkgtest::InstallPhase phase, int percent) {
                    if (phase == pkgtest::InstallPhase::Downloading) {
                        state.demoState = pkgtest::DemoState::Downloading;
                        state.progress = percent;
                    } else if (phase == pkgtest::InstallPhase::Installing) {
                        state.demoState = pkgtest::DemoState::Installing;
                        state.progress = percent;
                    } else if (phase == pkgtest::InstallPhase::Installed) {
                        state.demoState = pkgtest::DemoState::Installed;
                        state.progress = 100;
                    }
                });

            if (res.ok && res.queuedForMinecraft) {
                // Minecraft closed: staged + launched — keep "Installing..." until sync watcher finishes
                state.demoState = pkgtest::DemoState::Installing;
                state.progress = 75;
            } else if (res.ok) {
                state.demoState = pkgtest::DemoState::Installed;
                state.progress = 100;
                state.toastText = pkgtest::formatEnjoyNotification(kAddonFolderName, lang);
                state.toastUntil = nowSeconds() + 6.0f;
            } else {
                state.demoState = pkgtest::DemoState::Idle;
                state.progress = 0;
            }
            state.installing.store(false);
        }).detach();
    };

    bool running = true;
    SDL_Event ev;
    while (running) {
        const float t = nowSeconds();
        const std::string lang = currentLang();
        const bool rtl = lang == "ar";

        while (SDL_PollEvent(&ev)) {
            if (ev.type == SDL_QUIT) {
                running = false;
            } else if (ev.type == SDL_JOYHATMOTION) {
                const Uint8 hat = ev.jhat.value;
                if (state.screen == Screen::Welcome) {
                    if (hat & SDL_HAT_UP) state.focus = (state.focus + 2) % 3;
                    if (hat & SDL_HAT_DOWN) state.focus = (state.focus + 1) % 3;
                } else if (state.screen == Screen::Language) {
                    if (hat & SDL_HAT_UP) state.focus = (state.focus + 6) % 8;
                    if (hat & SDL_HAT_DOWN) state.focus = (state.focus + 2) % 8;
                    if (hat & SDL_HAT_LEFT) state.focus = (state.focus + 7) % 8;
                    if (hat & SDL_HAT_RIGHT) state.focus = (state.focus + 1) % 8;
                }
            } else if (ev.type == SDL_JOYBUTTONDOWN) {
                // 0=Cross(select/install) 1=Circle(back) 2=Square(language) 3=Triangle
                const Uint8 b = ev.jbutton.button;
                if (b == 1) {
                    if (state.screen == Screen::Language) {
                        state.screen = Screen::Welcome;
                    } else if (state.screen == Screen::Installing) {
                        state.screen = Screen::Welcome;
                    } else if (state.screen == Screen::Welcome && state.focus == 2) {
                        // guest: go straight to installer demo
                        state.screen = Screen::Installing;
                    } else {
                        running = false;
                    }
                } else if (b == 0 || b == 2) {
                    if (state.screen == Screen::Splash) {
                        state.screen = Screen::Welcome;
                    } else if (state.screen == Screen::Welcome) {
                        if (b == 2) {
                            state.screen = Screen::Language;
                            state.focus = state.langIndex;
                        } else if (state.focus == 2) {
                            state.screen = Screen::Installing;
                        } else {
                            // code / email sign-in not wired yet -> straight to installer demo
                            state.screen = Screen::Installing;
                        }
                    } else if (state.screen == Screen::Language) {
                        state.langIndex = state.focus;
                        state.screen = Screen::Welcome;
                    } else if (state.screen == Screen::Installing) {
                        triggerInstall();
                    }
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
                    if (k == SDLK_BACKSPACE || k == SDLK_ESCAPE) state.screen = Screen::Welcome;
                } else if (state.screen == Screen::Installing) {
                    if (k == SDLK_RETURN || k == SDLK_SPACE) triggerInstall();
                    if (k == SDLK_BACKSPACE) state.screen = Screen::Welcome;
                }
            }
        }

        // Auto-advance splash: 8s first launch, 1.5s afterwards
        if (state.screen == Screen::Splash) {
            const float splashDur = firstLaunch ? 8.0f : 1.5f;
            if (t - splashStart > splashDur) {
                state.screen = Screen::Welcome;
                firstLaunch = false;
            }
        }

        // Render
        engine.fillRect(0, 0, 1920, 1080, pkgtest::theme::BG);
        switch (state.screen) {
            case Screen::Splash:
                pkgtest::drawSplash(engine, t - splashStart, lang);
                break;
            case Screen::Welcome:
                pkgtest::drawWelcome(engine, state.focus, lang);
                break;
            case Screen::Language:
                pkgtest::drawLanguageDialog(engine, state.focus, lang);
                break;
            case Screen::Installing:
                pkgtest::drawInstallDemo(engine, state.demoState, state.progress, lang);
                if (state.demoState == pkgtest::DemoState::Installed && !state.toastText.empty() &&
                    t < state.toastUntil) {
                    pkgtest::drawToast(engine, state.toastText, lang);
                }
                break;
        }
        SDL_RenderPresent(renderer);
        SDL_Delay(16);
    }

    engine.shutdown();
    if (renderer) SDL_DestroyRenderer(renderer);
    if (window) SDL_DestroyWindow(window);
    if (pad) SDL_JoystickClose(pad);
    SDL_Quit();
    return 0;
}
