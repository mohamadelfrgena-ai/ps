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

SDL_Texture* gBackroomsCover = nullptr;

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

const char* tabLabel(CatalogTab tab) {
    switch (tab) {
        case CatalogTab::Home: return "HOME";
        case CatalogTab::Deals: return "DEALS";
        case CatalogTab::Store: return "STORE";
        case CatalogTab::Chat: return "CHAT";
        case CatalogTab::Settings: return "SETTINGS";
    }
    return "HOME";
}

std::vector<std::size_t> filteredContentIndices(const UiState& state) {
    std::vector<std::size_t> indices;
    const std::string query = state.contentFilter == 1 ? "addon" :
                              (state.contentFilter == 2 ? "world" : "");
    for (std::size_t index = 0; index < state.catalog.content.size(); ++index) {
        if (query.empty()) {
            indices.push_back(index);
            continue;
        }
        std::string contentType = state.catalog.content[index].contentType;
        std::transform(contentType.begin(), contentType.end(), contentType.begin(),
                       [](unsigned char value) {
                           return static_cast<char>(std::tolower(value));
                       });
        if (state.contentFilter == 1 &&
            (contentType.find("addon") != std::string::npos ||
             contentType.find("add-on") != std::string::npos ||
             contentType.find("resource") != std::string::npos)) {
            indices.push_back(index);
        } else if (state.contentFilter == 2 && contentType.find(query) != std::string::npos) {
            indices.push_back(index);
        }
    }
    return indices;
}

void toggleFilter(UiState& state, int direction) {
    if (state.tab != CatalogTab::Home && state.tab != CatalogTab::Store) return;
    constexpr int filterCount = 3;
    state.contentFilter = (state.contentFilter + direction + filterCount) % filterCount;
    state.selectedContent = 0;
}

void drawChip(SDL_Renderer* renderer, int x, int y, int width, const std::string& label,
              bool active = false, SDL_Color accent = kTeal) {
    const SDL_Color background = active ? kPanelSelected : kPanel;
    fill(renderer, x, y, width, 34, background);
    outline(renderer, x, y, width, 34, active ? accent : kBorder);
    drawText(renderer, label, x + 12, y + 12, 1, width - 24, active ? accent : kMuted);
}

void drawButton(SDL_Renderer* renderer, int x, int y, int width, int height,
                const std::string& label, SDL_Color accent = kTeal) {
    fill(renderer, x, y, width, height, kPanelSelected);
    outline(renderer, x, y, width, height, accent);
    const std::string text = asciiText(label);
    const int textWidth = static_cast<int>(text.size()) * 12;
    drawText(renderer, text, x + std::max(12, (width - textWidth) / 2),
             y + (height - 14) / 2, 2, width - 24, accent);
}

void drawCoverCrop(SDL_Renderer* renderer, const SDL_Rect& destination) {
    if (!gBackroomsCover) {
        fill(renderer, destination.x, destination.y, destination.w, destination.h, kPanelSelected);
        outline(renderer, destination.x, destination.y, destination.w, destination.h, kBorder);
        return;
    }

    int textureWidth = 0;
    int textureHeight = 0;
    if (SDL_QueryTexture(gBackroomsCover, nullptr, nullptr, &textureWidth, &textureHeight) != 0 ||
        textureWidth <= 0 || textureHeight <= 0) return;

    SDL_Rect source{0, 0, textureWidth, textureHeight};
    const double destinationRatio = static_cast<double>(destination.w) / destination.h;
    const double textureRatio = static_cast<double>(textureWidth) / textureHeight;
    if (textureRatio > destinationRatio) {
        source.w = static_cast<int>(textureHeight * destinationRatio);
        source.x = (textureWidth - source.w) / 2;
    } else if (textureRatio < destinationRatio) {
        source.h = static_cast<int>(textureWidth / destinationRatio);
        source.y = (textureHeight - source.h) / 2;
    }
    SDL_RenderCopy(renderer, gBackroomsCover, &source, &destination);
}

void drawHeader(SDL_Renderer* renderer, const UiState& state) {
    fill(renderer, 80, 42, 54, 54, kTeal);
    drawText(renderer, "AS", 91, 55, 4, 40, kBackground);
    drawText(renderer, "ADDONSTATION", 150, 47, 4, 380, kText);
    drawText(renderer, "PS4 CONSOLE EDITION", 153, 91, 1, 370, kMuted);

    const std::array<CatalogTab, 5> tabs{{
        CatalogTab::Home, CatalogTab::Deals, CatalogTab::Store,
        CatalogTab::Chat, CatalogTab::Settings,
    }};
    constexpr int tabX = 555;
    constexpr int tabWidth = 132;
    constexpr int tabGap = 10;
    for (std::size_t index = 0; index < tabs.size(); ++index) {
        const bool selected = state.tab == tabs[index];
        const int x = tabX + static_cast<int>(index) * (tabWidth + tabGap);
        fill(renderer, x, 47, tabWidth, 52, selected ? kPanelSelected : kPanel);
        outline(renderer, x, 47, tabWidth, 52, selected ? kTeal : kBorder);
        const std::string label = tabLabel(tabs[index]);
        const int labelWidth = static_cast<int>(label.size()) * 12;
        drawText(renderer, label, x + (tabWidth - labelWidth) / 2, 67, 2,
                 tabWidth - 12, selected ? kTeal : kMuted);
    }

    const SDL_Color badgeColor = state.loading ? kAmber :
                                 (state.connected ? kGreen :
                                  (state.demoMode ? kPurple : kRed));
    fill(renderer, 1365, 43, 475, 60, kPanel);
    outline(renderer, 1365, 43, 475, 60, badgeColor);
    const std::string badge = state.loading
        ? (state.demoMode ? "SYNCING / DEMO DATA SHOWN" : "SYNCING SHARED DATA")
        : (state.connected ? "CONNECTED TO SHARED CATALOG"
                           : (state.demoMode ? "DEMO DATA ONLY" : "OFFLINE / NOT CONFIGURED"));
    drawText(renderer, badge, 1385, 66, 2, 435, badgeColor);
}

void drawCatalogCard(SDL_Renderer* renderer, const addonstation::ContentItem& item,
                     int x, int y, int width, int height, bool selected) {
    const SDL_Color accent = selected ? kTeal : kPurple;
    fill(renderer, x, y, width, height, selected ? kPanelSelected : kPanel);
    fill(renderer, x, y, width, 8, accent);
    fill(renderer, x + 18, y + 26, 52, 52, selected ? kTeal : kBorder);
    drawText(renderer, "ADD", x + 25, y + 47, 1, 40, selected ? kBackground : kText);
    drawText(renderer, item.contentType.empty() ? "CONTENT" : item.contentType,
             x + 84, y + 45, 1, width - 102, kMuted);
    drawWrapped(renderer, item.title.empty() ? "UNTITLED" : item.title,
                x + 18, y + 100, 2, width - 36, 3, kText);
    drawText(renderer, planLabel(item.plan) + "  /  " + std::to_string(item.downloads) + " DOWNLOADS",
             x + 18, y + height - 42, 1, width - 36, selected ? kTeal : kMuted);
    outline(renderer, x, y, width, height, selected ? kTeal : kBorder);
}

void drawHome(SDL_Renderer* renderer, const UiState& state) {
    constexpr int heroY = 142;
    constexpr int heroHeight = 430;
    fill(renderer, 80, heroY, 1090, heroHeight, kPanel);
    fill(renderer, 80, heroY, 8, heroHeight, kTeal);
    outline(renderer, 80, heroY, 1090, heroHeight, kBorder);

    // The bundled cover is the featured art. The copy stays separate from the
    // API catalog so the local installer never relies on unverified backend fields.
    drawChip(renderer, 112, 170, 205, "FEATURED / BUNDLED", true, kAmber);
    drawText(renderer, "BACKROOMS", 112, 228, 6, 480, kText);
    drawText(renderer, "1.1 B / RESOURCE PACK", 114, 290, 3, 480, kTeal);
    drawWrapped(renderer,
                "A Minecraft resource pack included with AddonStation. Press Square to import it to the active profile.",
                114, 348, 2, 468, 4, kMuted);
    drawChip(renderer, 114, 447, 158, "MINECRAFT", false, kPurple);
    drawChip(renderer, 282, 447, 190, "RESOURCE PACK", false, kPurple);
    drawButton(renderer, 114, 500, 256, 48, "SQUARE / INSTALL", kTeal);

    const SDL_Rect heroCover = rect(635, 178, 510, 287);
    drawCoverCrop(renderer, heroCover);
    outline(renderer, heroCover.x, heroCover.y, heroCover.w, heroCover.h, kTeal);
    drawText(renderer, "BUNDLED COVER ART", 650, 493, 1, 480, kMuted);

    constexpr int featureX = 1192;
    constexpr int featureWidth = 648;
    fill(renderer, featureX, heroY, featureWidth, heroHeight, kPanel);
    outline(renderer, featureX, heroY, featureWidth, heroHeight, kBorder);
    drawText(renderer, "FEATURED IN THIS BUILD", featureX + 28, heroY + 28, 2,
             featureWidth - 56, kAmber);
    const SDL_Rect featureCover = rect(featureX + 28, heroY + 95, 230, 130);
    drawCoverCrop(renderer, featureCover);
    outline(renderer, featureCover.x, featureCover.y, featureCover.w, featureCover.h, kBorder);
    const int copyX = featureX + 282;
    drawText(renderer, "BACKROOMS 1.1 B", copyX, heroY + 105, 2, 330, kText);
    drawText(renderer, "RESOURCE PACK", copyX, heroY + 142, 1, 330, kTeal);
    drawWrapped(renderer, "Bundled locally. No offer price or discount is assumed.",
                copyX, heroY + 176, 1, 326, 3, kMuted);
    drawChip(renderer, featureX + 28, heroY + 250, 188, "LOCAL PACKAGE", false, kPurple);
    drawChip(renderer, featureX + 226, heroY + 250, 193, "UUID FOLDER", false, kPurple);
    drawButton(renderer, featureX + 28, heroY + 312, featureWidth - 56, 52,
               "PRESS SQUARE TO IMPORT", kTeal);

    drawText(renderer, "DISCOVER", 80, 606, 2, 300, kText);
    drawText(renderer, "LATEST FROM THE SHARED CATALOG", 244, 606, 1, 520, kMuted);
    drawChip(renderer, 80, 638, 150, "ALL ITEMS", state.contentFilter == 0, kTeal);
    drawChip(renderer, 240, 638, 150, "ADD-ONS", state.contentFilter == 1, kPurple);
    drawChip(renderer, 400, 638, 150, "WORLDS", state.contentFilter == 2, kPurple);

    const auto indices = filteredContentIndices(state);
    if (indices.empty()) {
        fill(renderer, 80, 694, 1760, 256, kPanel);
        outline(renderer, 80, 694, 1760, 256, kBorder);
        const std::string emptyTitle = state.loading
            ? "LOADING THE SHARED CATALOG..."
            : (state.catalog.content.empty() ? "NO CATALOG ITEMS TO DISPLAY."
                                             : "NO ITEMS MATCH THIS FILTER.");
        drawText(renderer, emptyTitle, 116, 744, 3, 1600, kText);
        const std::string emptyMessage = state.catalog.content.empty()
            ? (state.demoMode
                ? "The local Backrooms pack above is available in every build. Shared catalog data will appear here when a live catalog is configured."
                : "Published catalog items will appear here as soon as they are available.")
            : "Press L2 or R2 to choose a different content filter.";
        drawWrapped(renderer, emptyMessage, 116, 805, 2, 1550, 4, kMuted);
        return;
    }

    constexpr int cardWidth = 332;
    constexpr int cardHeight = 256;
    constexpr int cardGap = 20;
    constexpr int cardX = 80;
    const std::size_t visible = std::min<std::size_t>(indices.size(), 5);
    const std::size_t selected = std::min(state.selectedContent, indices.size() - 1);
    for (std::size_t index = 0; index < visible; ++index) {
        const int x = cardX + static_cast<int>(index) * (cardWidth + cardGap);
        drawCatalogCard(renderer, state.catalog.content[indices[index]], x, 694,
                        cardWidth, cardHeight, index == selected);
    }
}

void drawContentList(SDL_Renderer* renderer, const UiState& state) {
    constexpr int x = 80;
    constexpr int y = 320;
    constexpr int width = 1040;
    constexpr int height = 638;
    fill(renderer, x, y, width, height, kPanel);
    outline(renderer, x, y, width, height, kBorder);
    drawText(renderer, "LATEST PUBLISHED CONTENT", x + 30, y + 25, 3, width - 60, kTeal);

    const auto indices = filteredContentIndices(state);
    if (indices.empty()) {
        const bool noCatalogItems = state.catalog.content.empty();
        const std::string title = state.loading ? "LOADING CONTENT..." :
                                  (noCatalogItems ? "NO PUBLISHED CONTENT YET."
                                                  : "NO ITEMS MATCH THIS FILTER.");
        drawText(renderer, title, x + 36, y + 100, 3, width - 72, kText);
        drawWrapped(renderer,
                    noCatalogItems
                        ? "Published Minecraft add-ons and worlds will appear here from the shared database."
                        : "Press L2 or R2 to choose a different content filter.",
                    x + 36, y + 155, 2, width - 72, 3, kMuted);
        return;
    }

    constexpr int rowHeight = 78;
    constexpr int visibleRows = 6;
    const std::size_t selected = std::min(state.selectedContent, indices.size() - 1);
    const std::size_t start = selected >= visibleRows ? selected - visibleRows + 1 : 0;
    const std::size_t end = std::min(indices.size(), start + visibleRows);
    for (std::size_t index = start; index < end; ++index) {
        const int rowY = y + 82 + static_cast<int>(index - start) * rowHeight;
        const bool selectedRow = index == selected;
        fill(renderer, x + 22, rowY, width - 44, rowHeight - 8, selectedRow ? kPanelSelected : kPanel);
        outline(renderer, x + 22, rowY, width - 44, rowHeight - 8, selectedRow ? kTeal : kBorder);
        const addonstation::ContentItem& item = state.catalog.content[indices[index]];
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
    drawText(renderer, "SUBSCRIPTION PLANS", x + 30, y + 25, 3, width - 60, kPurple);

    const auto& plans = state.catalog.plans;
    if (plans.empty()) {
        drawText(renderer, state.loading ? "LOADING PLANS..." : "NO ACTIVE PLANS FOUND.",
                 x + 36, y + 100, 3, width - 72, kText);
        drawWrapped(renderer, "Plans are displayed for reference only. Purchases are not enabled in this PS4 build.",
                    x + 36, y + 155, 2, width - 72, 4, kMuted);
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

    if (state.tab == CatalogTab::Store) {
        drawText(renderer, "SELECTED ITEM", x + 28, y + 28, 3, width - 56, kTeal);
        const auto indices = filteredContentIndices(state);
        if (indices.empty()) {
            drawWrapped(renderer,
                        state.catalog.content.empty() ? state.status : "No items match the selected filter.",
                        x + 30, y + 100, 2, width - 60, 8, kMuted);
            return;
        }
        const addonstation::ContentItem& item = state.catalog.content[
            indices[std::min(state.selectedContent, indices.size() - 1)]];
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

void drawStore(SDL_Renderer* renderer, const UiState& state) {
    fill(renderer, 80, 142, 1760, 132, kPanel);
    outline(renderer, 80, 142, 1760, 132, kBorder);
    const SDL_Rect cover = rect(100, 155, 194, 106);
    drawCoverCrop(renderer, cover);
    outline(renderer, cover.x, cover.y, cover.w, cover.h, kTeal);
    drawText(renderer, "BUNDLED RESOURCE PACK", 322, 165, 2, 850, kAmber);
    drawText(renderer, "BACKROOMS 1.1 B", 322, 203, 3, 760, kText);
    drawText(renderer, "Press Square to import the local pack; existing UUID folders are never overwritten.",
             322, 245, 1, 1050, kMuted);
    drawButton(renderer, 1435, 178, 360, 54, "SQUARE / INSTALL PACK", kTeal);

    drawChip(renderer, 80, 284, 150, "ALL ITEMS", state.contentFilter == 0, kTeal);
    drawChip(renderer, 240, 284, 150, "ADD-ONS", state.contentFilter == 1, kPurple);
    drawChip(renderer, 400, 284, 150, "WORLDS", state.contentFilter == 2, kPurple);
    drawContentList(renderer, state);
    drawDetails(renderer, state);
}

void drawDeals(SDL_Renderer* renderer, const UiState& state) {
    fill(renderer, 80, 142, 1760, 810, kPanel);
    outline(renderer, 80, 142, 1760, 810, kBorder);
    fill(renderer, 80, 142, 8, 810, kAmber);
    drawChip(renderer, 120, 180, 210, "PROMOTIONS", true, kAmber);
    drawText(renderer, "OFFERS & DEALS", 120, 242, 5, 870, kText);
    drawText(renderer, "NO PROMOTION FEED IS CONFIGURED", 120, 326, 2, 980, kAmber);
    drawWrapped(renderer,
                "The current shared catalog provides content and subscription-plan data, but no promotion banners or per-item sale and discount fields. This screen will not invent a deal or display a misleading crossed-out price.",
                120, 378, 2, 920, 7, kMuted);
    drawButton(renderer, 120, 560, 260, 58, "OPEN STORE", kTeal);
    drawWrapped(renderer, state.status, 120, 667, 1, 1000, 5, kMuted);

    const SDL_Rect cover = rect(1220, 255, 520, 293);
    drawCoverCrop(renderer, cover);
    outline(renderer, cover.x, cover.y, cover.w, cover.h, kAmber);
    drawText(renderer, "FEATURED BUNDLED PACK", 1220, 586, 2, 540, kTeal);
    drawText(renderer, "BACKROOMS 1.1 B", 1220, 626, 3, 540, kText);
    drawText(renderer, "SQUARE TO IMPORT / NO SALE CLAIM", 1220, 678, 1, 560, kMuted);
}

void drawChat(SDL_Renderer* renderer) {
    fill(renderer, 80, 142, 1760, 810, kPanel);
    outline(renderer, 80, 142, 1760, 810, kBorder);
    drawChip(renderer, 120, 180, 150, "COMMUNITY", true, kPurple);
    drawText(renderer, "CHAT", 120, 248, 6, 700, kText);
    drawText(renderer, "CHAT IS NOT AVAILABLE IN THIS BUILD", 120, 352, 2, 1150, kAmber);
    drawWrapped(renderer,
                "The verified PS4 API client currently reads the shared content catalog and subscription plans only. No chat endpoint or message schema was supplied, so this tab remains a safe placeholder.",
                120, 406, 2, 1120, 7, kMuted);
    fill(renderer, 1300, 244, 420, 300, kBackground);
    outline(renderer, 1300, 244, 420, 300, kBorder);
    drawText(renderer, "NO MESSAGES", 1360, 366, 2, 320, kMuted);
}

void drawSettings(SDL_Renderer* renderer, const UiState& state) {
    fill(renderer, 80, 142, 1760, 132, kPanel);
    outline(renderer, 80, 142, 1760, 132, kBorder);
    drawText(renderer, "SETTINGS & MEMBERSHIP", 112, 166, 3, 700, kText);
    const SDL_Color connectionColor = state.loading ? kAmber :
                                      (state.connected ? kGreen :
                                       (state.demoMode ? kPurple : kRed));
    drawText(renderer, state.connected ? "CATALOG: CONNECTED" :
             (state.demoMode ? "CATALOG: DEMO DATA" : "CATALOG: OFFLINE"),
             112, 220, 2, 640, connectionColor);
    drawText(renderer, "PURCHASES AND ACCOUNT CHANGES ARE DISABLED", 900, 202, 2, 820, kAmber);
    drawPlanList(renderer, state);
    drawDetails(renderer, state);
}

void drawFooter(SDL_Renderer* renderer, const UiState& state) {
    fill(renderer, 80, 990, 1760, 2, kBorder);
    drawText(renderer, "UP / DOWN: SELECT", 82, 1018, 2, 250, kText);
    drawText(renderer, "L1 / R1: TABS", 350, 1018, 2, 220, kText);
    drawText(renderer, "L2 / R2: FILTER", 585, 1018, 2, 230, kPurple);
    drawText(renderer, "TRIANGLE: REFRESH", 830, 1018, 2, 270, kTeal);
    drawText(renderer, "SQUARE: INSTALL", 1110, 1018, 2, 250, kAmber);
    drawText(renderer, "CIRCLE: EXIT", 1465, 1018, 2, 245, kMuted);
    const SDL_Color statusColor = state.connected ? kGreen :
                                  (state.loading ? kAmber :
                                   (state.demoMode ? kPurple : kRed));
    drawText(renderer, state.status, 82, 1060, 1, 1700, statusColor);
}

void moveSelection(UiState& state, int direction) {
    if (state.tab == CatalogTab::Home || state.tab == CatalogTab::Store) {
        const std::vector<std::size_t> indices = filteredContentIndices(state);
        if (indices.empty()) return;
        const std::size_t count = indices.size();
        state.selectedContent = direction < 0
            ? (state.selectedContent == 0 ? count - 1 : state.selectedContent - 1)
            : (state.selectedContent + 1) % count;
    } else if (state.tab == CatalogTab::Settings) {
        if (state.catalog.plans.empty()) return;
        const std::size_t count = state.catalog.plans.size();
        state.selectedPlan = direction < 0
            ? (state.selectedPlan == 0 ? count - 1 : state.selectedPlan - 1)
            : (state.selectedPlan + 1) % count;
    }
}

void toggleTab(UiState& state) {
    switch (state.tab) {
        case CatalogTab::Home: state.tab = CatalogTab::Deals; break;
        case CatalogTab::Deals: state.tab = CatalogTab::Store; break;
        case CatalogTab::Store: state.tab = CatalogTab::Chat; break;
        case CatalogTab::Chat: state.tab = CatalogTab::Settings; break;
        case CatalogTab::Settings: state.tab = CatalogTab::Home; break;
    }
}

} // namespace

void initializeUiAssets(SDL_Renderer* renderer) {
    if (!renderer || gBackroomsCover) return;
    SDL_Surface* surface = SDL_LoadBMP("/app0/assets/Backrooms-1.1-B-cover.bmp");
    if (!surface) {
        SDL_Log("Could not load the bundled Backrooms cover: %s", SDL_GetError());
        return;
    }
    gBackroomsCover = SDL_CreateTextureFromSurface(renderer, surface);
    SDL_FreeSurface(surface);
    if (!gBackroomsCover) SDL_Log("Could not create the Backrooms cover texture: %s", SDL_GetError());
}

void shutdownUiAssets() {
    if (!gBackroomsCover) return;
    SDL_DestroyTexture(gBackroomsCover);
    gBackroomsCover = nullptr;
}

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
            case SDLK_a: toggleFilter(state, -1); break;
            case SDLK_d: toggleFilter(state, 1); break;
            case SDLK_r:
            case SDLK_RETURN:
                actions.requestRefresh = true;
                break;
            case SDLK_i:
                actions.requestPackImport = true;
                break;
            default: break;
        }
        return;
    }
    if (event.type == SDL_JOYBUTTONDOWN) {
        switch (event.jbutton.button) {
            case 1: actions.quit = true; break;       // Circle
            case 2: actions.requestPackImport = true; break; // Square
            case 3: actions.requestRefresh = true; break; // Triangle
            case 4: toggleTab(state); break;           // L1
            case 5: toggleTab(state); break;           // R1
            case 6: toggleFilter(state, -1); break;    // L2
            case 7: toggleFilter(state, 1); break;     // R2
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
    SDL_SetRenderDrawBlendMode(renderer, SDL_BLENDMODE_BLEND);
    SDL_SetRenderDrawColor(renderer, kBackground.r, kBackground.g, kBackground.b, kBackground.a);
    SDL_RenderClear(renderer);

    fill(renderer, 0, 0, kScreenWidth, 8, kTeal);
    fill(renderer, 0, 8, 620, 2, kPurple);
    drawHeader(renderer, state);
    switch (state.tab) {
        case CatalogTab::Home:
            drawHome(renderer, state);
            break;
        case CatalogTab::Deals:
            drawDeals(renderer, state);
            break;
        case CatalogTab::Store:
            drawStore(renderer, state);
            break;
        case CatalogTab::Chat:
            drawChat(renderer);
            break;
        case CatalogTab::Settings:
            drawSettings(renderer, state);
            break;
    }
    drawFooter(renderer, state);
}

} // namespace ps4app
