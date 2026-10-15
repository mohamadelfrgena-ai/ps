#include "installer.hpp"

#include <orbis/libkernel.h>

#include <SDL2/SDL.h>

#include <algorithm>
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
constexpr char kBundledMcpackPath[] = "/app0/assets/PrizmaVisuals-1.3.10.mcpack";
constexpr char kBundledCoverBmpPath[] = "/app0/assets/PrizmaVisuals-cover.bmp";

constexpr SDL_Color kBg{11, 15, 25, 255};
constexpr SDL_Color kCard{20, 28, 46, 255};
constexpr SDL_Color kCardBorder{46, 64, 98, 255};
constexpr SDL_Color kButtonReady{32, 178, 120, 255};
constexpr SDL_Color kButtonBusy{218, 148, 38, 255};
constexpr SDL_Color kButtonError{205, 68, 82, 255};
constexpr SDL_Color kButtonBorder{130, 255, 200, 255};
constexpr SDL_Color kWhite{242, 246, 255, 255};
constexpr SDL_Color kMuted{148, 166, 196, 255};
constexpr SDL_Color kTeal{72, 226, 204, 255};
constexpr SDL_Color kGreen{98, 230, 148, 255};
constexpr SDL_Color kAmber{255, 196, 92, 255};
constexpr SDL_Color kRed{255, 110, 124, 255};

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
        case ':': return {0, 4, 4, 0, 4, 4, 0};
        case '.': return {0, 0, 0, 0, 0, 12, 12};
        case ',': return {0, 0, 0, 0, 4, 4, 8};
        case '-': return {0, 0, 0, 31, 0, 0, 0};
        case '+': return {0, 4, 4, 31, 4, 4, 0};
        case '/': return {1, 2, 2, 4, 8, 8, 16};
        case '_': return {0, 0, 0, 0, 0, 0, 31};
        case '#': return {10, 31, 10, 10, 31, 10, 0};
        case '(': return {2, 4, 8, 8, 8, 4, 2};
        case ')': return {8, 4, 2, 2, 2, 4, 8};
        case '[': return {14, 8, 8, 8, 8, 8, 14};
        case ']': return {14, 2, 2, 2, 2, 2, 14};
        case '<': return {2, 4, 8, 16, 8, 4, 2};
        case '>': return {8, 4, 2, 1, 2, 4, 8};
        case '=': return {0, 0, 31, 0, 31, 0, 0};
        case '!': return {4, 4, 4, 4, 4, 0, 4};
        case '|': return {4, 4, 4, 4, 4, 4, 4};
        case ' ': return {0, 0, 0, 0, 0, 0, 0};
        default: return {14, 17, 1, 2, 4, 0, 4};
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

void drawText(SDL_Renderer* r, const std::string& raw, int x, int y, int scale, int maxW, SDL_Color color) {
    const std::string text = sanitizeAscii(raw);
    SDL_SetRenderDrawColor(r, color.r, color.g, color.b, color.a);
    const int advance = 6 * scale;
    int cx = x;
    for (char ch : text) {
        if (cx + 5 * scale > x + maxW) break;
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

void drawCenteredText(SDL_Renderer* r, const std::string& raw, int centerX, int y, int scale, SDL_Color color) {
    const std::string clean = sanitizeAscii(raw);
    const int w = textWidth(clean, scale);
    drawText(r, clean, centerX - w / 2, y, scale, w + 20, color);
}

struct AppState {
    std::mutex mutex;
    pkgtest::PackMetadata packMeta;
    pkgtest::TargetMode mode = pkgtest::TargetMode::AutoDetect;
    pkgtest::InstallReport lastReport;
    SDL_Texture* coverTexture = nullptr;
    bool hasRunInstall = false;
    std::atomic<int> installJobState{0}; // 0=idle, 1=queued, 2=running
    std::atomic<bool> stopping{false};
};

void refreshScanLogs(AppState& state) {
    const pkgtest::ScanReport scan = pkgtest::scanFilesystemForMinecraft();
    std::lock_guard<std::mutex> lock(state.mutex);
    state.lastReport.ok = false;
    state.lastReport.statusTitle = "READY — PRESS X OR SQUARE TO INSTALL PRIZMAVISUALS 1.3.10";
    state.lastReport.logs.clear();
    if (state.packMeta.valid) {
        state.lastReport.logs.push_back(
            "PACK READY: " + state.packMeta.name + " | UUID=" + state.packMeta.uuid +
            " | FILES=" + std::to_string(state.packMeta.fileCount) +
            " | SIZE=" + std::to_string(state.packMeta.totalBytes / 1024) + " KB");
    } else {
        state.lastReport.logs.push_back("PACK ERROR: " + state.packMeta.error);
    }
    for (const std::string& line : scan.diagnosticLines) {
        state.lastReport.logs.push_back(line);
    }
}

void renderScreen(SDL_Renderer* r, AppState& state) {
    fillRect(r, 0, 0, kScreenWidth, kScreenHeight, kBg);
    fillRect(r, 0, 0, kScreenWidth, 10, kTeal);

    pkgtest::TargetMode currentMode;
    pkgtest::InstallReport report;
    pkgtest::PackMetadata meta;
    bool hasRun = false;
    const int jobState = state.installJobState.load();
    {
        std::lock_guard<std::mutex> lock(state.mutex);
        currentMode = state.mode;
        report = state.lastReport;
        meta = state.packMeta;
        hasRun = state.hasRunInstall;
    }

    // Header
    drawCenteredText(r, "PKG ADDON TEST — PRIZMAVISUALS 1.3.10", kScreenWidth / 2, 34, 5, kWhite);
    drawCenteredText(r, "CATEGORY=GDE (NON-GAME MINI-APP) — SUSPENDS MINECRAFT IN BACKGROUND", kScreenWidth / 2, 84, 2, kTeal);

    // Top Card: Cover Image on the Left + Pack & Target Path Info on the Right
    fillRect(r, 140, 125, 1640, 230, kCard);
    drawBorder(r, 140, 125, 1640, 230, 2, kCardBorder);

    const int coverX = 160;
    const int coverY = 142;
    const int coverW = 414;
    const int coverH = 196;
    if (state.coverTexture) {
        SDL_Rect dst{coverX, coverY, coverW, coverH};
        SDL_RenderCopy(r, state.coverTexture, nullptr, &dst);
        drawBorder(r, coverX, coverY, coverW, coverH, 2, kTeal);
    } else {
        fillRect(r, coverX, coverY, coverW, coverH, kBg);
        drawBorder(r, coverX, coverY, coverW, coverH, 2, kCardBorder);
        drawCenteredText(r, "PRIZMAVISUALS 1.3.10", coverX + coverW / 2, coverY + 86, 3, kTeal);
    }

    const int infoX = 605;
    const int infoW = 1150;
    drawText(r, "ADDON: PRIZMAVISUALS 1.3.10 (VIBRANT VISUALS RESOURCE PACK)", infoX, 148, 3, infoW, kWhite);
    const std::string packLine = meta.valid
        ? ("UUID: " + meta.uuid + "  |  FILES: " + std::to_string(meta.fileCount) +
           "  |  SIZE: " + std::to_string(meta.totalBytes / (1024 * 1024)) + " MB")
        : ("ADDON FILE ERROR: " + meta.error);
    drawText(r, packLine, infoX, 188, 2, infoW, meta.valid ? kGreen : kRed);

    drawText(r, "TARGET PATH MODE (USE D-PAD LEFT / RIGHT TO CHANGE):", infoX, 230, 2, infoW, kMuted);
    drawText(r, std::string("<  ") + pkgtest::targetModeLabel(currentMode) + "  >", infoX, 262, 3, infoW, kAmber);
    drawText(r, std::string("PATH: ") + pkgtest::targetModePathPreview(currentMode), infoX, 304, 2, infoW, kWhite);

    // ONLY ONE BIG INSTALL BUTTON IN THE CENTER
    const int btnX = 360;
    const int btnY = 380;
    const int btnW = 1200;
    const int btnH = 165;

    SDL_Color btnColor = kButtonReady;
    std::string btnMainText = "INSTALL PRIZMAVISUALS 1.3.10";
    std::string btnSubText = "PRESS  [ X ]  OR  [ SQUARE ]  ON CONTROLLER";

    if (jobState != 0) {
        btnColor = kButtonBusy;
        btnMainText = "INSTALLING ADDON... PLEASE WAIT";
        btnSubText = "EXTRACTING 8806 FILES TO TARGET PATH...";
    } else if (hasRun && report.ok) {
        btnColor = kButtonReady;
        btnMainText = "INSTALLED OK! PRESS [X] TO RE-INSTALL";
        btnSubText = "ALL " + std::to_string(report.filesWritten) + " FILES WRITTEN TO TARGET PATH";
    } else if (hasRun && !report.ok) {
        btnColor = kButtonError;
        btnMainText = "INSTALL FAILED — PRESS [X] TO RETRY";
        btnSubText = "OR USE D-PAD LEFT/RIGHT TO SELECT ANOTHER TARGET PATH";
    }

    fillRect(r, btnX, btnY, btnW, btnH, btnColor);
    drawBorder(r, btnX, btnY, btnW, btnH, 4, kButtonBorder);
    drawCenteredText(r, btnMainText, kScreenWidth / 2, btnY + 40, 5, kWhite);
    drawCenteredText(r, btnSubText, kScreenWidth / 2, btnY + 106, 3, kWhite);

    // Live Diagnostic & Result Panel
    const int logX = 140;
    const int logY = 570;
    const int logW = 1640;
    const int logH = 430;
    fillRect(r, logX, logY, logW, logH, kCard);
    drawBorder(r, logX, logY, logW, logH, 2, kCardBorder);

    SDL_Color statusColor = kTeal;
    if (jobState != 0) statusColor = kAmber;
    else if (hasRun && report.ok) statusColor = kGreen;
    else if (hasRun && !report.ok) statusColor = kRed;

    drawText(r, "STATUS: " + report.statusTitle, logX + 30, logY + 22, 3, logW - 60, statusColor);

    if (!report.destinationDir.empty()) {
        drawText(r, "INSTALLED TO: " + report.destinationDir, logX + 30, logY + 62, 2, logW - 60, kGreen);
    } else {
        drawText(r, "FILESYSTEM DIAGNOSTICS & INSTALL LOG:", logX + 30, logY + 62, 2, logW - 60, kMuted);
    }

    int lineY = logY + 100;
    const std::size_t maxLines = 11;
    const std::size_t startIdx = report.logs.size() > maxLines ? (report.logs.size() - maxLines) : 0;
    for (std::size_t i = startIdx; i < report.logs.size(); ++i) {
        const std::string& line = report.logs[i];
        SDL_Color lineColor = kWhite;
        if (line.find("FOUND MOJANG") != std::string::npos || line.find("WRITTEN:") != std::string::npos ||
            line.find("DEST:") != std::string::npos) {
            lineColor = kGreen;
        } else if (line.find("ERROR") != std::string::npos || line.find("FAILED") != std::string::npos) {
            lineColor = kRed;
        } else if (line.find("CANDIDATE") != std::string::npos || line.find("FALLING BACK") != std::string::npos) {
            lineColor = kAmber;
        }
        drawText(r, "- " + line, logX + 30, lineY, 2, logW - 60, lineColor);
        lineY += 28;
    }

    // Footer bar
    drawCenteredText(
        r,
        "[X / SQUARE] INSTALL ADDON    [LEFT / RIGHT] CHANGE TARGET PATH    [TRIANGLE] RESCAN    [CIRCLE] EXIT",
        kScreenWidth / 2,
        1025,
        2,
        kMuted);
}

} // namespace

int main(int, char**) {
    setvbuf(stdout, nullptr, _IONBF, 0);

    if (SDL_Init(SDL_INIT_VIDEO | SDL_INIT_JOYSTICK) != 0) {
        std::printf("SDL_Init failed: %s\n", SDL_GetError());
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
        std::printf("SDL_CreateWindow failed: %s\n", SDL_GetError());
        SDL_Quit();
        return 1;
    }

    SDL_Surface* windowSurface = SDL_GetWindowSurface(window);
    SDL_Renderer* renderer = windowSurface ? SDL_CreateSoftwareRenderer(windowSurface) : nullptr;
    if (!renderer) {
        std::printf("SDL_CreateSoftwareRenderer failed: %s\n", SDL_GetError());
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
    if (SDL_Surface* coverSurface = SDL_LoadBMP(kBundledCoverBmpPath)) {
        state.coverTexture = SDL_CreateTextureFromSurface(renderer, coverSurface);
        SDL_FreeSurface(coverSurface);
    }
    state.packMeta = pkgtest::inspectMcpackFile(kBundledMcpackPath);
    refreshScanLogs(state);

    std::thread installerThread([&state]() {
        while (!state.stopping.load()) {
            int expected = 1;
            if (state.installJobState.compare_exchange_strong(expected, 2)) {
                pkgtest::TargetMode selectedMode;
                {
                    std::lock_guard<std::mutex> lock(state.mutex);
                    selectedMode = state.mode;
                    state.lastReport.statusTitle = "INSTALLING PRIZMAVISUALS 1.3.10...";
                }
                pkgtest::InstallReport result = pkgtest::installMcpack(kBundledMcpackPath, selectedMode);
                {
                    std::lock_guard<std::mutex> lock(state.mutex);
                    state.lastReport = std::move(result);
                    state.hasRunInstall = true;
                }
                state.installJobState.store(0);
            }
            sceKernelUsleep(40 * 1000);
        }
    });

    auto triggerInstall = [&state]() {
        int expected = 0;
        state.installJobState.compare_exchange_strong(expected, 1);
    };

    auto cycleMode = [&state](int delta) {
        if (state.installJobState.load() != 0) return;
        std::lock_guard<std::mutex> lock(state.mutex);
        state.mode = pkgtest::nextTargetMode(state.mode, delta);
    };

    bool running = true;
    while (running) {
        SDL_Event ev;
        while (SDL_PollEvent(&ev)) {
            if (ev.type == SDL_QUIT) {
                running = false;
            } else if (ev.type == SDL_KEYDOWN) {
                switch (ev.key.keysym.sym) {
                    case SDLK_ESCAPE: running = false; break;
                    case SDLK_RETURN:
                    case SDLK_SPACE:
                    case SDLK_x:
                    case SDLK_i:
                        triggerInstall();
                        break;
                    case SDLK_LEFT:
                        cycleMode(-1);
                        break;
                    case SDLK_RIGHT:
                        cycleMode(1);
                        break;
                    case SDLK_r:
                        if (state.installJobState.load() == 0) refreshScanLogs(state);
                        break;
                    default: break;
                }
            } else if (ev.type == SDL_JOYBUTTONDOWN) {
                switch (ev.jbutton.button) {
                    case 0: // Cross (X)
                    case 2: // Square
                        triggerInstall();
                        break;
                    case 1: // Circle
                        running = false;
                        break;
                    case 3: // Triangle
                        if (state.installJobState.load() == 0) refreshScanLogs(state);
                        break;
                    case 4: // L1
                    case 15: // D-pad left
                        cycleMode(-1);
                        break;
                    case 5: // R1
                    case 16: // D-pad right
                        cycleMode(1);
                        break;
                    default: break;
                }
            } else if (ev.type == SDL_JOYHATMOTION) {
                if (ev.jhat.value & SDL_HAT_LEFT) cycleMode(-1);
                else if (ev.jhat.value & SDL_HAT_RIGHT) cycleMode(1);
            }
        }

        renderScreen(renderer, state);
        SDL_RenderPresent(renderer);
        SDL_UpdateWindowSurface(window);
        SDL_Delay(16);
    }

    state.stopping.store(true);
    if (installerThread.joinable()) installerThread.join();

    if (state.coverTexture) SDL_DestroyTexture(state.coverTexture);
    for (SDL_Joystick* joy : joysticks) {
        if (joy) SDL_JoystickClose(joy);
    }
    SDL_DestroyRenderer(renderer);
    SDL_DestroyWindow(window);
    SDL_Quit();
    return 0;
}
