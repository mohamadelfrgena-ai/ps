#include "installer.hpp"

#include <orbis/libkernel.h>

#include <SDL2/SDL.h>

#include <array>
#include <atomic>
#include <cstdint>
#include <cstdio>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

namespace {

constexpr int kScreenWidth = 1920;
constexpr int kScreenHeight = 1080;
constexpr char kSourceAddonFolder[] = "/app0/assets/folder.raw";
constexpr char kTargetAddonFolder[] =
    "/app_tmp/CUSA00265/user497444003/games/com.mojang/resource_packs/PrizmaVisuals 1.3.10222";

constexpr SDL_Color kBg{11, 15, 25, 255};
constexpr SDL_Color kButtonReady{32, 178, 120, 255};
constexpr SDL_Color kButtonBusy{218, 148, 38, 255};
constexpr SDL_Color kButtonError{205, 68, 82, 255};
constexpr SDL_Color kButtonBorder{130, 255, 200, 255};
constexpr SDL_Color kWhite{242, 246, 255, 255};

void fillRect(SDL_Renderer* r, int x, int y, int w, int h, SDL_Color c) {
    SDL_SetRenderDrawColor(r, c.r, c.g, c.b, c.a);
    SDL_Rect rc{x, y, w, h};
    SDL_RenderFillRect(r, &rc);
}

void drawBorder(SDL_Renderer* r, int x, int y, int w, int h, int thickness, SDL_Color c) {
    for (int i = 0; i < thickness; ++i) {
        SDL_SetRenderDrawColor(r, c.r, c.g, c.b, c.a);
        SDL_Rect rc{x + i, y + i, w - 2 * i, h - 2 * i};
        SDL_RenderDrawRect(r, &rc);
    }
}

std::array<std::uint8_t, 7> glyph(char ch) {
    switch (ch) {
        case 'A': return {14, 17, 17, 31, 17, 17, 17};
        case 'B': return {30, 17, 17, 30, 17, 17, 30};
        case 'C': return {14, 17, 16, 16, 16, 17, 14};
        case 'D': return {30, 17, 17, 17, 17, 17, 30};
        case 'E': return {31, 16, 16, 30, 16, 16, 31};
        case 'F': return {31, 16, 16, 30, 16, 16, 16};
        case 'G': return {14, 17, 16, 23, 17, 17, 15};
        case 'H': return {17, 17, 17, 31, 17, 17, 17};
        case 'I': return {14, 4, 4, 4, 4, 4, 14};
        case 'J': return {7, 2, 2, 2, 18, 18, 12};
        case 'K': return {17, 18, 20, 24, 20, 18, 17};
        case 'L': return {16, 16, 16, 16, 16, 16, 31};
        case 'M': return {17, 27, 21, 21, 17, 17, 17};
        case 'N': return {17, 25, 25, 21, 19, 19, 17};
        case 'O': return {14, 17, 17, 17, 17, 17, 14};
        case 'P': return {30, 17, 17, 30, 16, 16, 16};
        case 'Q': return {14, 17, 17, 17, 21, 18, 13};
        case 'R': return {30, 17, 17, 30, 20, 18, 17};
        case 'S': return {15, 16, 16, 14, 1, 1, 30};
        case 'T': return {31, 4, 4, 4, 4, 4, 4};
        case 'U': return {17, 17, 17, 17, 17, 17, 14};
        case 'V': return {17, 17, 17, 17, 17, 10, 4};
        case 'W': return {17, 17, 17, 21, 21, 21, 10};
        case 'X': return {17, 17, 10, 4, 10, 17, 17};
        case 'Y': return {17, 17, 10, 4, 4, 4, 4};
        case 'Z': return {31, 1, 2, 4, 8, 16, 31};
        case '0': return {14, 17, 19, 21, 25, 17, 14};
        case '1': return {4, 12, 4, 4, 4, 4, 14};
        case '2': return {14, 17, 1, 2, 4, 8, 31};
        case '3': return {30, 1, 1, 14, 1, 1, 30};
        case '4': return {2, 6, 10, 18, 31, 2, 2};
        case '5': return {31, 16, 16, 30, 1, 1, 30};
        case '6': return {14, 16, 16, 30, 17, 17, 14};
        case '7': return {31, 1, 2, 4, 8, 8, 8};
        case '8': return {14, 17, 17, 14, 17, 17, 14};
        case '9': return {14, 17, 17, 15, 1, 1, 14};
        case '(': return {2, 4, 8, 8, 8, 4, 2};
        case ')': return {8, 4, 2, 2, 2, 4, 8};
        case '[': return {14, 8, 8, 8, 8, 8, 14};
        case ']': return {14, 2, 2, 2, 2, 2, 14};
        case '-': return {0, 0, 0, 31, 0, 0, 0};
        case ' ': return {0, 0, 0, 0, 0, 0, 0};
        default: return {0, 0, 0, 0, 0, 0, 0};
    }
}

std::string sanitizeAscii(const std::string& raw) {
    std::string out;
    out.reserve(raw.size());
    for (unsigned char ch : raw) {
        if (ch >= 'a' && ch <= 'z') ch = static_cast<unsigned char>(ch - 'a' + 'A');
        if (ch < 0x20 || ch >= 0x7f) ch = ' ';
        out.push_back(static_cast<char>(ch));
    }
    return out;
}

int textWidth(const std::string& raw, int scale) {
    if (raw.empty()) return 0;
    return static_cast<int>(raw.size()) * 6 * scale - scale;
}

void drawCenteredText(SDL_Renderer* r, const std::string& raw, int centerX, int y, int scale, SDL_Color color) {
    const std::string text = sanitizeAscii(raw);
    const int w = textWidth(text, scale);
    int cx = centerX - w / 2;
    SDL_SetRenderDrawColor(r, color.r, color.g, color.b, color.a);
    const int advance = 6 * scale;
    for (char ch : text) {
        const auto rows = glyph(ch);
        for (int row = 0; row < 7; ++row) {
            for (int col = 0; col < 5; ++col) {
                if ((rows[static_cast<std::size_t>(row)] & (1U << (4 - col))) == 0) continue;
                SDL_Rect px{cx + col * scale, y + row * scale, scale, scale};
                SDL_RenderFillRect(r, &px);
            }
        }
        cx += advance;
    }
}

struct AppState {
    std::mutex mutex;
    pkgtest::CopyResult lastResult;
    bool hasRun = false;
    std::atomic<int> jobState{0}; // 0=idle, 1=queued, 2=running
    std::atomic<bool> stopping{false};
};

void renderScreen(SDL_Renderer* r, AppState& state) {
    fillRect(r, 0, 0, kScreenWidth, kScreenHeight, kBg);

    pkgtest::CopyResult res;
    bool hasRun = false;
    const int job = state.jobState.load();
    {
        std::lock_guard<std::mutex> lock(state.mutex);
        res = state.lastResult;
        hasRun = state.hasRun;
    }

    const int btnW = 900;
    const int btnH = 220;
    const int btnX = (kScreenWidth - btnW) / 2;
    const int btnY = (kScreenHeight - btnH) / 2;

    SDL_Color btnColor = kButtonReady;
    std::string label = "INSTALL [X]";
    if (job != 0) {
        btnColor = kButtonBusy;
        label = "COPYING...";
    } else if (hasRun && res.ok) {
        btnColor = kButtonReady;
        label = "DONE (" + std::to_string(res.filesCopied) + ")";
    } else if (hasRun && !res.ok) {
        btnColor = kButtonError;
        label = res.message;
    }

    fillRect(r, btnX, btnY, btnW, btnH, btnColor);
    drawBorder(r, btnX, btnY, btnW, btnH, 5, kButtonBorder);
    drawCenteredText(r, label, kScreenWidth / 2, btnY + (btnH - 42) / 2, 6, kWhite);
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
        kScreenWidth,
        kScreenHeight,
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

    AppState state;
    std::thread worker([&state]() {
        while (!state.stopping.load()) {
            int expected = 1;
            if (state.jobState.compare_exchange_strong(expected, 2)) {
                pkgtest::CopyResult result =
                    pkgtest::copyAddonFolder(kSourceAddonFolder, kTargetAddonFolder);
                {
                    std::lock_guard<std::mutex> lock(state.mutex);
                    state.lastResult = std::move(result);
                    state.hasRun = true;
                }
                state.jobState.store(0);
            }
            sceKernelUsleep(40 * 1000);
        }
    });

    auto triggerInstall = [&state]() {
        int expected = 0;
        state.jobState.compare_exchange_strong(expected, 1);
    };

    bool running = true;
    while (running) {
        SDL_Event ev;
        while (SDL_PollEvent(&ev)) {
            if (ev.type == SDL_QUIT) {
                running = false;
            } else if (ev.type == SDL_KEYDOWN) {
                if (ev.key.keysym.sym == SDLK_ESCAPE) running = false;
                else triggerInstall();
            } else if (ev.type == SDL_JOYBUTTONDOWN) {
                if (ev.jbutton.button == 1) running = false; // Circle exits
                else if (ev.jbutton.button == 0 || ev.jbutton.button == 2) triggerInstall(); // X or Square installs
            }
        }

        renderScreen(renderer, state);
        SDL_RenderPresent(renderer);
        SDL_UpdateWindowSurface(window);
        SDL_Delay(16);
    }

    state.stopping.store(true);
    if (worker.joinable()) worker.join();

    for (SDL_Joystick* joy : joysticks) {
        if (joy) SDL_JoystickClose(joy);
    }
    SDL_DestroyRenderer(renderer);
    SDL_DestroyWindow(window);
    SDL_Quit();
    return 0;
}
