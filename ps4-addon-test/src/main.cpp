// AddonStation "pkg addon test" PS4 Mini-App — design UI (splash + welcome + installer)
//
// Rendering: the UiEngine rasterizes EVERYTHING into its own RGBA framebuffer
// (proven stb_truetype/stb_image path — no SDL textures, no SDL_RenderCopy),
// then the frame is blitted into the SDL window surface and presented exactly
// like the working v0.5 app:
//     SDL_RenderPresent(renderer); SDL_UpdateWindowSurface(window);
//
// Dual-mode installer: copies PrizmaVisuals into com.mojang immediately if
// Minecraft is open; otherwise stages to /data/AddonStation/installed/, launches
// Minecraft, auto-syncs and deletes the staged .raw afterwards. CUSAxxxxx and
// userYYYY are always auto-detected.

#include "installer.hpp"
#include "screens.hpp"

#include <SDL2/SDL.h>

#include <atomic>
#include <chrono>
#include <cstdio>
#include <string>
#include <thread>
#include <vector>

namespace {

constexpr const char* kStageDir = "/data/AddonStation/installed";
constexpr const char* kAddonFolderName = "PrizmaVisuals 1.3.10222";
constexpr const char* kSourceRaw = "/app0/assets/folder.raw";

const char* kLangCodes[8] = {"en", "ar", "fr", "tr", "es", "ru", "pt", "zh"};

enum class Screen { Splash, Welcome, Language, Installing };

struct AppState {
    Screen screen = Screen::Splash;
    int focus = 0;
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
    const bool fontsOk = engine.init("/app0/assets/ui.pak");
    if (!fontsOk) {
        std::fprintf(stderr, "ui.pak missing or invalid - fallback font active\n");
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
                    if (state.screen == Screen::Language || state.screen == Screen::Installing) {
                        state.screen = Screen::Welcome;
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
                        } else {
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
                    if (k == SDLK_BACKSPACE) state.screen = Screen::Welcome;
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

        // Render into the engine framebuffer
        engine.clear(pkgtest::theme::BG);
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

        // Blit + present exactly like the working v0.5 app
        engine.present(windowSurface);
        SDL_RenderPresent(renderer);
        SDL_UpdateWindowSurface(window);
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
