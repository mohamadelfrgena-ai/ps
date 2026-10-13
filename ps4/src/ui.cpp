#include "ui.hpp"

#include <algorithm>
#include <array>
#include <cctype>
#include <cstdint>
#include <string>
#include <vector>

namespace ps4app {
namespace {

constexpr int kScreenWidth = 1920;
constexpr SDL_Color kBackground{10, 13, 25, 255};
constexpr SDL_Color kPanel{18, 25, 43, 255};
constexpr SDL_Color kPanelSelected{24, 40, 64, 255};
constexpr SDL_Color kBorder{40, 53, 78, 255};
constexpr SDL_Color kText{239, 244, 255, 255};
constexpr SDL_Color kMuted{139, 155, 181, 255};
constexpr SDL_Color kTeal{69, 222, 201, 255};
constexpr SDL_Color kPurple{164, 130, 255, 255};
constexpr SDL_Color kGreen{98, 220, 146, 255};
constexpr SDL_Color kAmber{255, 191, 93, 255};
constexpr SDL_Color kRed{255, 113, 127, 255};

SDL_Rect rect(int x, int y, int width, int height) {
    return SDL_Rect{x, y, width, height};
}

void fill(SDL_Renderer* renderer, int x, int y, int width, int height, SDL_Color color) {
    SDL_SetRenderDrawColor(renderer, color.r, color.g, color.b, color.a);
    const SDL_Rect area = rect(x, y, width, height);
    SDL_RenderFillRect(renderer, &area);
}

void outline(SDL_Renderer* renderer, int x, int y, int width, int height, SDL_Color color) {
    SDL_SetRenderDrawColor(renderer, color.r, color.g, color.b, color.a);
    const SDL_Rect area = rect(x, y, width, height);
    SDL_RenderDrawRect(renderer, &area);
}

std::array<std::uint8_t, 7> glyph(char value) {
    switch (value) {
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
        case '%': return {17, 2, 4, 8, 16, 17, 0};
        case '?': return {14, 17, 1, 2, 4, 0, 4};
        case ' ': return {0, 0, 0, 0, 0, 0, 0};
        default: return {31, 17, 1, 2, 4, 0, 4};
    }
}

std::string asciiText(const std::string& input) {
    std::string output;
    output.reserve(input.size());
    for (std::size_t i = 0; i < input.size(); ++i) {
        unsigned char ch = static_cast<unsigned char>(input[i]);
        if ((ch & 0x80) != 0) {
            output.push_back('?');
            while (i + 1 < input.size() &&
                   (static_cast<unsigned char>(input[i + 1]) & 0xC0) == 0x80) {
                ++i;
            }
            continue;
        }
        if (ch >= 'a' && ch <= 'z') ch = static_cast<unsigned char>(ch - 'a' + 'A');
        if (ch < 0x20) ch = ' ';
        output.push_back(static_cast<char>(ch));
    }
    return output;
}

int drawText(SDL_Renderer* renderer, const std::string& raw, int x, int y, int scale,
             int maxWidth, SDL_Color color) {
    const std::string text = asciiText(raw);
    SDL_SetRenderDrawColor(renderer, color.r, color.g, color.b, color.a);
    const int advance = 6 * scale;
    int cursorX = x;
    for (char ch : text) {
        if (cursorX + 5 * scale > x + maxWidth) break;
        const auto rows = glyph(ch);
        for (int row = 0; row < 7; ++row) {
            for (int col = 0; col < 5; ++col) {
                if ((rows[static_cast<std::size_t>(row)] & (1U << (4 - col))) == 0) continue;
                if (scale == 1) {
                    SDL_RenderDrawPoint(renderer, cursorX + col, y + row);
                } else {
                    const SDL_Rect pixel = rect(cursorX + col * scale, y + row * scale, scale, scale);
                    SDL_RenderFillRect(renderer, &pixel);
                }
            }
        }
        cursorX += advance;
    }
    return cursorX - x;
}

void drawWrapped(SDL_Renderer* renderer, const std::string& raw, int x, int y,
                 int scale, int maxWidth, int maxLines, SDL_Color color) {
    const std::string text = asciiText(raw);
    const std::size_t maxCharacters = static_cast<std::size_t>(std::max(1, maxWidth / (6 * scale)));
    std::size_t position = 0;
    int line = 0;
    while (position < text.size() && line < maxLines) {
        while (position < text.size() && text[position] == ' ') ++position;
        if (position >= text.size()) break;
        std::size_t end = std::min(position + maxCharacters, text.size());
        if (end < text.size()) {
            const std::size_t space = text.rfind(' ', end);
            if (space != std::string::npos && space >= position) end = space;
        }
        if (end == position) end = std::min(position + maxCharacters, text.size());
        drawText(renderer, text.substr(position, end - position), x,
                 y + line * (9 * scale), scale, maxWidth, color);
        position = end;
        ++line;
    }
    if (position < text.size() && line > 0) {
        const int dotsX = x + (static_cast<int>(maxCharacters) - 4) * 6 * scale;
        drawText(renderer, "...", std::max(x, dotsX), y + (line - 1) * (9 * scale), scale,
                 std::min(maxWidth, 4 * 6 * scale), color);
    }
}

std::string planLabel(const std::string& value) {
    if (value.empty()) return "FREE";
    return value;
}

void drawHeader(SDL_Renderer* renderer, const UiState& state) {
    drawText(renderer, "ADDONSTATION", 82, 55, 6, 570, kText);
    drawText(renderer, "PS4 EXPERIMENTAL CLIENT", 86, 112, 2, 560, kMuted);
    drawText(renderer, "ONE DATABASE  /  MULTIPLE CLIENTS", 86, 150, 2, 650, kPurple);

    const SDL_Color badgeColor = state.loading ? kAmber :
                                 (state.connected ? kGreen :
                                  (state.demoMode ? kPurple : kRed));
    fill(renderer, 1450, 64, 385, 74, kPanel);
    outline(renderer, 1450, 64, 385, 74, badgeColor);
    const std::string badge = state.loading
        ? (state.demoMode ? "SYNCING - DEMO DATA SHOWN" : "SYNCING SHARED DATA")
        : (state.connected ? "CONNECTED TO SUPABASE"
                           : (state.demoMode ? "DEMO DATA ONLY" : "OFFLINE / NOT CONFIGURED"));
    drawText(renderer, badge, 1475, 88, 2, 340, badgeColor);
}

void drawTabs(SDL_Renderer* renderer, const UiState& state) {
    const bool contentSelected = state.tab == CatalogTab::Content;
    fill(renderer, 80, 222, 400, 74, contentSelected ? kPanelSelected : kPanel);
    outline(renderer, 80, 222, 400, 74, contentSelected ? kTeal : kBorder);
    drawText(renderer, "CONTENT CATALOG", 110, 248, 2, 340, contentSelected ? kTeal : kMuted);

    fill(renderer, 500, 222, 400, 74, !contentSelected ? kPanelSelected : kPanel);
    outline(renderer, 500, 222, 400, 74, !contentSelected ? kTeal : kBorder);
    drawText(renderer, "SUBSCRIPTION PLANS", 530, 248, 2, 340, !contentSelected ? kTeal : kMuted);

    drawText(renderer, "L1 / R1 OR LEFT / RIGHT TO SWITCH", 1030, 248, 2, 730, kMuted);
}

void drawContentList(SDL_Renderer* renderer, const UiState& state) {
    constexpr int x = 80;
    constexpr int y = 320;
    constexpr int width = 1040;
    constexpr int height = 638;
    fill(renderer, x, y, width, height, kPanel);
    outline(renderer, x, y, width, height, kBorder);
    drawText(renderer, "LATEST PUBLISHED CONTENT", x + 30, y + 25, 3, width - 60, kTeal);

    const auto& items = state.catalog.content;
    if (items.empty()) {
        drawText(renderer, state.loading ? "LOADING CONTENT..." : "NO PUBLISHED CONTENT YET.",
                 x + 36, y + 100, 3, width - 72, kText);
        drawWrapped(renderer, "Published Minecraft add-ons and worlds will appear here from the shared database.",
                    x + 36, y + 155, 2, width - 72, 3, kMuted);
        return;
    }

    constexpr int rowHeight = 78;
    constexpr int visibleRows = 6;
    const std::size_t selected = std::min(state.selectedContent, items.size() - 1);
    const std::size_t start = selected >= visibleRows ? selected - visibleRows + 1 : 0;
    const std::size_t end = std::min(items.size(), start + visibleRows);
    for (std::size_t index = start; index < end; ++index) {
        const int rowY = y + 82 + static_cast<int>(index - start) * rowHeight;
        const bool selectedRow = index == selected;
        fill(renderer, x + 22, rowY, width - 44, rowHeight - 8, selectedRow ? kPanelSelected : kPanel);
        outline(renderer, x + 22, rowY, width - 44, rowHeight - 8, selectedRow ? kTeal : kBorder);
        const addonstation::ContentItem& item = items[index];
        drawText(renderer, item.title.empty() ? "UNTITLED" : item.title,
                 x + 45, rowY + 13, 2, 650, selectedRow ? kText : kMuted);
        const std::string details = item.contentType + "  /  " + planLabel(item.plan) +
                                    "  /  " + std::to_string(item.downloads) + " DOWNLOADS";
        drawText(renderer, details, x + 45, rowY + 42, 1, width - 110, kMuted);
    }
}

void drawPlanList(SDL_Renderer* renderer, const UiState& state) {
    constexpr int x = 80;
    constexpr int y = 320;
    constexpr int width = 1040;
    constexpr int height = 638;
    fill(renderer, x, y, width, height, kPanel);
    outline(renderer, x, y, width, height, kBorder);
    drawText(renderer, "PLANS FROM THE SHARED DATABASE", x + 30, y + 25, 3, width - 60, kPurple);

    const auto& plans = state.catalog.plans;
    if (plans.empty()) {
        drawText(renderer, state.loading ? "LOADING PLANS..." : "NO ACTIVE PLANS FOUND.",
                 x + 36, y + 100, 3, width - 72, kText);
        drawWrapped(renderer, "Check that subscription_plans is seeded and readable by the public role.",
                    x + 36, y + 155, 2, width - 72, 3, kMuted);
        return;
    }

    constexpr int rowHeight = 136;
    constexpr int visibleRows = 4;
    const std::size_t selected = std::min(state.selectedPlan, plans.size() - 1);
    const std::size_t start = selected >= visibleRows ? selected - visibleRows + 1 : 0;
    const std::size_t end = std::min(plans.size(), start + visibleRows);
    for (std::size_t index = start; index < end; ++index) {
        const int rowY = y + 82 + static_cast<int>(index - start) * rowHeight;
        const bool selectedRow = index == selected;
        fill(renderer, x + 22, rowY, width - 44, rowHeight - 12, selectedRow ? kPanelSelected : kPanel);
        outline(renderer, x + 22, rowY, width - 44, rowHeight - 12, selectedRow ? kPurple : kBorder);
        const addonstation::SubscriptionPlan& plan = plans[index];
        drawText(renderer, plan.name.empty() ? plan.id : plan.name,
                 x + 45, rowY + 20, 3, 600, selectedRow ? kText : kMuted);
        drawText(renderer, "LYD " + plan.priceLyd + "  /  USD " + plan.priceUsd +
                 "  /  " + std::to_string(plan.durationDays) + " DAYS",
                 x + 45, rowY + 72, 2, width - 95, kTeal);
        const std::string limit = plan.downloadLimit.empty() || plan.downloadLimit == "unlimited"
            ? "UNLIMITED DOWNLOADS"
            : plan.downloadLimit + " DOWNLOADS PER " + plan.limitPeriod;
        drawText(renderer, limit, x + 45, rowY + 109, 1, width - 95, kMuted);
    }
}

void drawDetails(SDL_Renderer* renderer, const UiState& state) {
    constexpr int x = 1160;
    constexpr int y = 320;
    constexpr int width = 680;
    constexpr int height = 638;
    fill(renderer, x, y, width, height, kPanel);
    outline(renderer, x, y, width, height, kBorder);

    if (state.tab == CatalogTab::Content) {
        drawText(renderer, "SELECTED ITEM", x + 28, y + 28, 3, width - 56, kTeal);
        if (state.catalog.content.empty()) {
            drawWrapped(renderer, state.status, x + 30, y + 100, 2, width - 60, 8, kMuted);
            return;
        }
        const addonstation::ContentItem& item = state.catalog.content[
            std::min(state.selectedContent, state.catalog.content.size() - 1)];
        drawWrapped(renderer, item.title.empty() ? "UNTITLED" : item.title,
                    x + 30, y + 95, 3, width - 60, 3, kText);
        drawText(renderer, "TYPE: " + item.contentType, x + 30, y + 198, 2, width - 60, kMuted);
        drawText(renderer, "ACCESS: " + planLabel(item.plan), x + 30, y + 239, 2, width - 60, kPurple);
        drawText(renderer, "VERSION: " + (item.version.empty() ? "N/A" : item.version),
                 x + 30, y + 280, 2, width - 60, kMuted);
        drawText(renderer, "DOWNLOADS: " + std::to_string(item.downloads),
                 x + 30, y + 321, 2, width - 60, kMuted);
        drawText(renderer, "VIEWS: " + std::to_string(item.views),
                 x + 30, y + 362, 2, width - 60, kMuted);
        fill(renderer, x + 30, y + 405, width - 60, 2, kBorder);
        drawText(renderer, "DESCRIPTION", x + 30, y + 430, 2, width - 60, kTeal);
        drawWrapped(renderer, item.description.empty() ? "NO DESCRIPTION PROVIDED." : item.description,
                    x + 30, y + 468, 2, width - 60, 9, kText);
    } else {
        drawText(renderer, "PLAN DETAILS", x + 28, y + 28, 3, width - 56, kPurple);
        if (state.catalog.plans.empty()) {
            drawWrapped(renderer, state.status, x + 30, y + 100, 2, width - 60, 8, kMuted);
            return;
        }
        const addonstation::SubscriptionPlan& plan = state.catalog.plans[
            std::min(state.selectedPlan, state.catalog.plans.size() - 1)];
        drawWrapped(renderer, plan.name.empty() ? plan.id : plan.name,
                    x + 30, y + 95, 3, width - 60, 3, kText);
        drawWrapped(renderer, plan.tagline, x + 30, y + 195, 2, width - 60, 3, kMuted);
        drawText(renderer, "PRICE: " + plan.priceLyd + " LYD", x + 30, y + 300, 2, width - 60, kTeal);
        drawText(renderer, "PRICE: " + plan.priceUsd + " USD", x + 30, y + 341, 2, width - 60, kTeal);
        drawText(renderer, "DURATION: " + std::to_string(plan.durationDays) + " DAYS",
                 x + 30, y + 382, 2, width - 60, kMuted);
        const std::string limit = plan.downloadLimit.empty() || plan.downloadLimit == "unlimited"
            ? "UNLIMITED DOWNLOADS"
            : plan.downloadLimit + " DOWNLOADS PER " + plan.limitPeriod;
        drawWrapped(renderer, limit, x + 30, y + 430, 2, width - 60, 4, kMuted);
        drawWrapped(renderer, "PREVIEW ONLY - PURCHASES AND ACCOUNT ACTIONS ARE NOT ENABLED.",
                    x + 30, y + 535, 1, width - 60, 3, kAmber);
    }
}

void drawFooter(SDL_Renderer* renderer, const UiState& state) {
    fill(renderer, 80, 990, 1760, 2, kBorder);
    drawText(renderer, "UP / DOWN: SELECT", 82, 1018, 2, 360, kText);
    drawText(renderer, "L1 / R1: CHANGE TAB", 470, 1018, 2, 400, kText);
    drawText(renderer, "TRIANGLE: REFRESH", 900, 1018, 2, 380, kTeal);
    drawText(renderer, "CIRCLE: EXIT", 1330, 1018, 2, 330, kMuted);
    const SDL_Color statusColor = state.connected ? kGreen : (state.loading ? kAmber : kRed);
    drawText(renderer, state.status, 82, 1060, 1, 1700, statusColor);
}

void moveSelection(UiState& state, int direction) {
    if (state.tab == CatalogTab::Content) {
        if (state.catalog.content.empty()) return;
        const std::size_t count = state.catalog.content.size();
        state.selectedContent = direction < 0
            ? (state.selectedContent == 0 ? count - 1 : state.selectedContent - 1)
            : (state.selectedContent + 1) % count;
    } else {
        if (state.catalog.plans.empty()) return;
        const std::size_t count = state.catalog.plans.size();
        state.selectedPlan = direction < 0
            ? (state.selectedPlan == 0 ? count - 1 : state.selectedPlan - 1)
            : (state.selectedPlan + 1) % count;
    }
}

void toggleTab(UiState& state) {
    state.tab = state.tab == CatalogTab::Content ? CatalogTab::Plans : CatalogTab::Content;
}

} // namespace

void handleInput(const SDL_Event& event, UiState& state, UiActions& actions) {
    if (event.type == SDL_QUIT) {
        actions.quit = true;
        return;
    }
    if (event.type == SDL_KEYDOWN) {
        switch (event.key.keysym.sym) {
            case SDLK_ESCAPE: actions.quit = true; break;
            case SDLK_UP: moveSelection(state, -1); break;
            case SDLK_DOWN: moveSelection(state, 1); break;
            case SDLK_LEFT: toggleTab(state); break;
            case SDLK_RIGHT: toggleTab(state); break;
            case SDLK_r:
            case SDLK_RETURN:
                actions.requestRefresh = true;
                break;
            default: break;
        }
        return;
    }
    if (event.type == SDL_JOYBUTTONDOWN) {
        switch (event.jbutton.button) {
            case 1: actions.quit = true; break;       // Circle
            case 3: actions.requestRefresh = true; break; // Triangle
            case 4: toggleTab(state); break;           // L1
            case 5: toggleTab(state); break;           // R1
            case 9: actions.requestRefresh = true; break; // Options
            case 13: moveSelection(state, -1); break; // D-pad up
            case 14: moveSelection(state, 1); break;  // D-pad down
            case 15: toggleTab(state); break;          // D-pad left
            case 16: toggleTab(state); break;          // D-pad right
            default: break;
        }
    }
}

void renderUi(SDL_Renderer* renderer, const UiState& state) {
    SDL_SetRenderDrawColor(renderer, kBackground.r, kBackground.g, kBackground.b, kBackground.a);
    SDL_RenderClear(renderer);

    // Decorative top rule and subtle color marks, using only native SDL drawing.
    fill(renderer, 0, 0, kScreenWidth, 8, kTeal);
    fill(renderer, 0, 8, 620, 2, kPurple);
    drawHeader(renderer, state);
    drawTabs(renderer, state);
    if (state.tab == CatalogTab::Content) drawContentList(renderer, state);
    else drawPlanList(renderer, state);
    drawDetails(renderer, state);
    drawFooter(renderer, state);
}

} // namespace ps4app
