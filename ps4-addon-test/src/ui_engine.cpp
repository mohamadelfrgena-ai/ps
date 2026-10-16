#include "ui_engine.hpp"

#define STB_IMAGE_IMPLEMENTATION
#define STBI_ONLY_JPEG
#define STBI_ONLY_PNG
#include "stb_image.h"

#define STB_TRUETYPE_IMPLEMENTATION
#include "stb_truetype.h"

#include <algorithm>
#include <array>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <map>

namespace pkgtest {
namespace {

// ---------------------------------------------------------------------------
// Built-in 5x7 fallback font (proven rendering path from the working v0.5 app)
// ---------------------------------------------------------------------------
std::array<std::uint8_t, 7> fallbackGlyph(char value) {
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
        case '!': return {4, 4, 4, 4, 4, 0, 4};
        case ' ': return {0, 0, 0, 0, 0, 0, 0};
        default: return {31, 17, 1, 2, 4, 0, 4};
    }
}

// ---------------------------------------------------------------------------
// Arabic contextual shaping + Lam-Alef ligatures + visual RTL ordering
// (proven on host previews — Tajawal presentation forms)
// ---------------------------------------------------------------------------
struct ArabicLetterForms {
    std::uint32_t base;
    std::uint32_t iso;
    std::uint32_t fin;
    std::uint32_t ini;
    std::uint32_t med;
    bool connectsNext;
};

constexpr ArabicLetterForms kArabicTable[] = {
    {0x0621, 0x0621, 0x0621, 0x0621, 0x0621, false},
    {0x0622, 0x0622, 0xFE82, 0x0622, 0xFE82, false},
    {0x0623, 0x0623, 0xFE84, 0x0623, 0xFE84, false},
    {0x0624, 0x0624, 0xFE86, 0x0624, 0xFE86, false},
    {0x0625, 0x0625, 0xFE88, 0x0625, 0xFE88, false},
    {0x0626, 0x0626, 0xFE8A, 0xFE8B, 0xFE8C, true},
    {0x0627, 0x0627, 0xFE8E, 0x0627, 0xFE8E, false},
    {0x0628, 0x0628, 0xFE90, 0xFE91, 0xFE92, true},
    {0x0629, 0x0629, 0xFE94, 0x0629, 0xFE94, false},
    {0x062A, 0x062A, 0xFE96, 0xFE97, 0xFE98, true},
    {0x062B, 0x062B, 0xFE9A, 0xFE9B, 0xFE9C, true},
    {0x062C, 0x062C, 0xFE9E, 0xFE9F, 0xFEA0, true},
    {0x062D, 0x062D, 0xFEA2, 0xFEA3, 0xFEA4, true},
    {0x062E, 0x062E, 0xFEA6, 0xFEA7, 0xFEA8, true},
    {0x062F, 0x062F, 0xFEAA, 0x062F, 0xFEAA, false},
    {0x0630, 0x0630, 0xFEAC, 0x0630, 0xFEAC, false},
    {0x0631, 0x0631, 0xFEAE, 0x0631, 0xFEAE, false},
    {0x0632, 0x0632, 0xFEB0, 0x0632, 0xFEB0, false},
    {0x0633, 0x0633, 0xFEB2, 0xFEB3, 0xFEB4, true},
    {0x0634, 0x0634, 0xFEB6, 0xFEB7, 0xFEB8, true},
    {0x0635, 0x0635, 0xFEBA, 0xFEBB, 0xFEBC, true},
    {0x0636, 0x0636, 0xFEBE, 0xFEBF, 0xFEC0, true},
    {0x0637, 0x0637, 0xFEC2, 0xFEC3, 0xFEC4, true},
    {0x0638, 0x0638, 0xFEC6, 0xFEC7, 0xFEC8, true},
    {0x0639, 0x0639, 0xFECA, 0xFECB, 0xFECC, true},
    {0x063A, 0x063A, 0xFECE, 0xFECF, 0xFED0, true},
    {0x0640, 0x0640, 0x0640, 0x0640, 0x0640, true},
    {0x0641, 0x0641, 0xFED2, 0xFED3, 0xFED4, true},
    {0x0642, 0x0642, 0xFED6, 0xFED7, 0xFED8, true},
    {0x0643, 0x0643, 0xFEDA, 0xFEDB, 0xFEDC, true},
    {0x0644, 0x0644, 0xFEDE, 0xFEDF, 0xFEE0, true},
    {0x0645, 0x0645, 0xFEE2, 0xFEE3, 0xFEE4, true},
    {0x0646, 0x0646, 0xFEE6, 0xFEE7, 0xFEE8, true},
    {0x0647, 0x0647, 0xFEEA, 0xFEEB, 0xFEEC, true},
    {0x0648, 0x0648, 0xFEEE, 0x0648, 0xFEEE, false},
    {0x0649, 0x0649, 0xFEF0, 0x0649, 0xFEF0, false},
    {0x064A, 0x064A, 0xFEF2, 0xFEF3, 0xFEF4, true},
};

const ArabicLetterForms* findArabicForm(std::uint32_t cp) {
    for (const auto& entry : kArabicTable) {
        if (entry.base == cp) return &entry;
    }
    return nullptr;
}

bool isArabicDiacritic(std::uint32_t cp) {
    return (cp >= 0x064B && cp <= 0x065F) || cp == 0x0670;
}

bool isArabicCodepoint(std::uint32_t cp) {
    return (cp >= 0x0600 && cp <= 0x06FF) || (cp >= 0xFE70 && cp <= 0xFEFF);
}

std::vector<std::uint32_t> decodeUtf8(const std::string& text) {
    std::vector<std::uint32_t> out;
    out.reserve(text.size());
    std::size_t i = 0;
    while (i < text.size()) {
        const unsigned char c0 = static_cast<unsigned char>(text[i]);
        if (c0 < 0x80) {
            out.push_back(c0);
            ++i;
        } else if ((c0 & 0xE0) == 0xC0 && i + 1 < text.size()) {
            out.push_back(((c0 & 0x1FU) << 6) | (static_cast<unsigned char>(text[i + 1]) & 0x3FU));
            i += 2;
        } else if ((c0 & 0xF0) == 0xE0 && i + 2 < text.size()) {
            out.push_back(((c0 & 0x0FU) << 12) |
                          ((static_cast<unsigned char>(text[i + 1]) & 0x3FU) << 6) |
                          (static_cast<unsigned char>(text[i + 2]) & 0x3FU));
            i += 3;
        } else if ((c0 & 0xF8) == 0xF0 && i + 3 < text.size()) {
            out.push_back(((c0 & 0x07U) << 18) |
                          ((static_cast<unsigned char>(text[i + 1]) & 0x3FU) << 12) |
                          ((static_cast<unsigned char>(text[i + 2]) & 0x3FU) << 6) |
                          (static_cast<unsigned char>(text[i + 3]) & 0x3FU));
            i += 4;
        } else {
            ++i;
        }
    }
    return out;
}

bool containsArabicScript(const std::vector<std::uint32_t>& cps) {
    for (std::uint32_t cp : cps) {
        if (isArabicCodepoint(cp)) return true;
    }
    return false;
}

std::vector<std::uint32_t> shapeAndOrderArabic(const std::vector<std::uint32_t>& raw) {
    std::vector<std::uint32_t> clean;
    clean.reserve(raw.size());
    for (std::uint32_t cp : raw) {
        if (!isArabicDiacritic(cp)) {
            if (cp >= 0x0660 && cp <= 0x0669) {
                clean.push_back('0' + (cp - 0x0660));
            } else {
                clean.push_back(cp);
            }
        }
    }

    std::vector<std::uint32_t> shaped;
    shaped.reserve(clean.size());
    for (std::size_t i = 0; i < clean.size(); ++i) {
        const std::uint32_t curr = clean[i];
        const ArabicLetterForms* currForm = findArabicForm(curr);
        if (!currForm) {
            shaped.push_back(curr);
            continue;
        }

        const bool prevConnects = [&]() {
            if (i == 0) return false;
            const ArabicLetterForms* pf = findArabicForm(clean[i - 1]);
            return pf != nullptr && pf->connectsNext;
        }();

        if (curr == 0x0644 && i + 1 < clean.size()) {
            const std::uint32_t next = clean[i + 1];
            std::uint32_t ligIso = 0;
            std::uint32_t ligFin = 0;
            if (next == 0x0622) { ligIso = 0xFEF5; ligFin = 0xFEF6; }
            else if (next == 0x0623) { ligIso = 0xFEF7; ligFin = 0xFEF8; }
            else if (next == 0x0625) { ligIso = 0xFEF9; ligFin = 0xFEFA; }
            else if (next == 0x0627) { ligIso = 0xFEFB; ligFin = 0xFEFC; }
            if (ligIso != 0) {
                shaped.push_back(prevConnects ? ligFin : ligIso);
                ++i;
                continue;
            }
        }

        const bool nextConnects = [&]() {
            if (!currForm->connectsNext || i + 1 >= clean.size()) return false;
            return findArabicForm(clean[i + 1]) != nullptr;
        }();

        if (prevConnects && nextConnects) shaped.push_back(currForm->med);
        else if (prevConnects && !nextConnects) shaped.push_back(currForm->fin);
        else if (!prevConnects && nextConnects) shaped.push_back(currForm->ini);
        else shaped.push_back(currForm->iso);
    }

    auto isLtrRunChar = [](std::uint32_t cp) {
        return (cp >= '0' && cp <= '9') ||
               (cp >= 'A' && cp <= 'Z') ||
               (cp >= 'a' && cp <= 'z') ||
               cp == '.' || cp == '_' || cp == '-' || cp == '+' || cp == '%' || cp == '/' || cp == '@';
    };

    std::vector<std::uint32_t> visual;
    visual.reserve(shaped.size());
    std::size_t idx = shaped.size();
    while (idx > 0) {
        --idx;
        if (isLtrRunChar(shaped[idx])) {
            std::size_t endRun = idx + 1;
            while (idx > 0 && isLtrRunChar(shaped[idx - 1])) --idx;
            for (std::size_t k = idx; k < endRun; ++k) visual.push_back(shaped[k]);
        } else {
            std::uint32_t ch = shaped[idx];
            if (ch == '(') ch = ')';
            else if (ch == ')') ch = '(';
            else if (ch == '[') ch = ']';
            else if (ch == ']') ch = '[';
            else if (ch == '<') ch = '>';
            else if (ch == '>') ch = '<';
            visual.push_back(ch);
        }
    }
    return visual;
}

inline Uint8 clamp8(int v) {
    return static_cast<Uint8>(v < 0 ? 0 : (v > 255 ? 255 : v));
}

} // namespace

struct UiEngine::FontData {
    std::vector<unsigned char> f77Buf;
    std::vector<unsigned char> arBuf;
    std::vector<unsigned char> bodyBuf;
    std::vector<unsigned char> cjkBuf;
    stbtt_fontinfo f77Info{};
    stbtt_fontinfo arInfo{};
    stbtt_fontinfo bodyInfo{};
    stbtt_fontinfo cjkInfo{};
    bool f77Valid = false;
    bool arValid = false;
    bool bodyValid = false;
    bool cjkValid = false;
};

bool UiEngine::init(const std::string& pakPath) {
    fb_.assign(static_cast<std::size_t>(kWidth * kHeight), 0xFF0A0F1CU);
    fonts_ = new FontData();

    std::string chosenPath = pakPath;
    if (chosenPath.empty()) {
        std::FILE* f1 = std::fopen("/app0/assets/ui.pak", "rb");
        if (f1) {
            std::fclose(f1);
            chosenPath = "/app0/assets/ui.pak";
        } else {
            chosenPath = "assets/ui.pak";
        }
    }

    std::FILE* fp = std::fopen(chosenPath.c_str(), "rb");
    if (!fp) {
        fallbackReady_ = true;
        return false;
    }

    char magic[8] = {};
    if (std::fread(magic, 1, 8, fp) != 8 || std::memcmp(magic, "ASUI0001", 8) != 0) {
        std::fclose(fp);
        fallbackReady_ = true;
        return false;
    }

    std::uint32_t count = 0;
    if (std::fread(&count, sizeof(count), 1, fp) != 1) {
        std::fclose(fp);
        fallbackReady_ = true;
        return false;
    }

    for (std::uint32_t i = 0; i < count; ++i) {
        std::uint16_t nameLen = 0;
        std::uint32_t dataLen = 0;
        if (std::fread(&nameLen, sizeof(nameLen), 1, fp) != 1 ||
            std::fread(&dataLen, sizeof(dataLen), 1, fp) != 1) {
            break;
        }
        std::string name(nameLen, '\0');
        if (nameLen > 0 && std::fread(&name[0], 1, nameLen, fp) != nameLen) break;
        std::vector<unsigned char> data(dataLen);
        if (dataLen > 0 && std::fread(data.data(), 1, dataLen, fp) != dataLen) break;

        if (name == "font_f77") {
            fonts_->f77Buf = std::move(data);
            fonts_->f77Valid = stbtt_InitFont(&fonts_->f77Info, fonts_->f77Buf.data(), 0) != 0;
        } else if (name == "font_ar") {
            fonts_->arBuf = std::move(data);
            fonts_->arValid = stbtt_InitFont(&fonts_->arInfo, fonts_->arBuf.data(), 0) != 0;
        } else if (name == "font_body") {
            fonts_->bodyBuf = std::move(data);
            fonts_->bodyValid = stbtt_InitFont(&fonts_->bodyInfo, fonts_->bodyBuf.data(), 0) != 0;
        } else if (name == "font_cjk") {
            fonts_->cjkBuf = std::move(data);
            fonts_->cjkValid = stbtt_InitFont(&fonts_->cjkInfo, fonts_->cjkBuf.data(), 0) != 0;
        } else {
            int w = 0, h = 0, comp = 0;
            unsigned char* pixels = stbi_load_from_memory(
                data.data(), static_cast<int>(data.size()), &w, &h, &comp, 4);
            if (pixels) {
                ImageData img;
                img.w = w;
                img.h = h;
                img.rgba.assign(pixels, pixels + static_cast<std::size_t>(w) * h * 4);
                images_[name] = std::move(img);
                stbi_image_free(pixels);
            }
        }
    }
    std::fclose(fp);

    fontsLoaded_ = fonts_->f77Valid && fonts_->arValid && fonts_->bodyValid;
    fallbackReady_ = true;
    return fontsLoaded_;
}

void UiEngine::blendPx(int x, int y, Uint8 r, Uint8 g, Uint8 b, Uint8 a) {
    if (x < 0 || y < 0 || x >= kWidth || y >= kHeight) return;
    std::uint32_t& cell = fb_[static_cast<std::size_t>(y) * kWidth + x];
    if (a >= 255) {
        cell = (255u << 24) | (static_cast<std::uint32_t>(r) << 16) |
               (static_cast<std::uint32_t>(g) << 8) | b;
        return;
    }
    if (a == 0) return;
    const Uint8 dr = static_cast<Uint8>((cell >> 16) & 0xFF);
    const Uint8 dg = static_cast<Uint8>((cell >> 8) & 0xFF);
    const Uint8 db = static_cast<Uint8>(cell & 0xFF);
    const int ia = 255 - a;
    const Uint8 nr = static_cast<Uint8>((r * a + dr * ia) / 255);
    const Uint8 ng = static_cast<Uint8>((g * a + dg * ia) / 255);
    const Uint8 nb = static_cast<Uint8>((b * a + db * ia) / 255);
    cell = (255u << 24) | (static_cast<std::uint32_t>(nr) << 16) |
           (static_cast<std::uint32_t>(ng) << 8) | nb;
}

void UiEngine::blendRow(int x0, int x1, int y, Uint8 r, Uint8 g, Uint8 b, Uint8 a) {
    if (y < 0 || y >= kHeight) return;
    const int xs = std::max(0, x0);
    const int xe = std::min(kWidth, x1);
    for (int x = xs; x < xe; ++x) blendPx(x, y, r, g, b, a);
}

void UiEngine::clear(SDL_Color c) {
    const std::uint32_t v = (255u << 24) | (static_cast<std::uint32_t>(c.r) << 16) |
                            (static_cast<std::uint32_t>(c.g) << 8) | c.b;
    std::fill(fb_.begin(), fb_.end(), v);
}

void UiEngine::fillRect(int x, int y, int w, int h, SDL_Color c) {
    const int y0 = std::max(0, y);
    const int y1 = std::min(kHeight, y + h);
    const int x0 = std::max(0, x);
    const int x1 = std::min(kWidth, x + w);
    for (int yy = y0; yy < y1; ++yy) {
        for (int xx = x0; xx < x1; ++xx) blendPx(xx, yy, c.r, c.g, c.b, c.a);
    }
}

void UiEngine::fillGradH(int x, int y, int w, int h, SDL_Color left, SDL_Color right) {
    const int steps = std::min(w, 64);
    if (steps <= 0) return;
    for (int i = 0; i < steps; ++i) {
        const int x0 = x + (i * w) / steps;
        const int x1 = x + ((i + 1) * w) / steps;
        const float t = static_cast<float>(i) / static_cast<float>(std::max(1, steps - 1));
        SDL_Color c{
            clamp8(static_cast<int>(left.r + (right.r - left.r) * t)),
            clamp8(static_cast<int>(left.g + (right.g - left.g) * t)),
            clamp8(static_cast<int>(left.b + (right.b - left.b) * t)),
            clamp8(static_cast<int>(left.a + (right.a - left.a) * t))};
        fillRect(x0, y, std::max(1, x1 - x0), h, c);
    }
}

void UiEngine::fillGradV(int x, int y, int w, int h, SDL_Color top, SDL_Color bottom) {
    const int steps = std::min(h, 64);
    if (steps <= 0) return;
    for (int i = 0; i < steps; ++i) {
        const int y0 = y + (i * h) / steps;
        const int y1 = y + ((i + 1) * h) / steps;
        const float t = static_cast<float>(i) / static_cast<float>(std::max(1, steps - 1));
        SDL_Color c{
            clamp8(static_cast<int>(top.r + (bottom.r - top.r) * t)),
            clamp8(static_cast<int>(top.g + (bottom.g - top.g) * t)),
            clamp8(static_cast<int>(top.b + (bottom.b - top.b) * t)),
            clamp8(static_cast<int>(top.a + (bottom.a - top.a) * t))};
        fillRect(x, y0, w, std::max(1, y1 - y0), c);
    }
}

void UiEngine::drawRoundRect(int x, int y, int w, int h, int radius, SDL_Color c) {
    if (w <= 0 || h <= 0) return;
    const int r = std::max(0, std::min({radius, w / 2, h / 2}));
    if (r <= 1) {
        fillRect(x, y, w, h, c);
        return;
    }
    fillRect(x, y + r, w, h - 2 * r, c);
    for (int dy = 0; dy < r; ++dy) {
        const float yf = static_cast<float>(r - dy) - 0.5f;
        const int dx = static_cast<int>(std::sqrt(std::max(0.0f, static_cast<float>(r * r) - yf * yf)));
        blendRow(x + r - dx, x + w - (r - dx), y + dy, c.r, c.g, c.b, c.a);
        blendRow(x + r - dx, x + w - (r - dx), y + h - 1 - dy, c.r, c.g, c.b, c.a);
    }
}

void UiEngine::drawRoundGradH(int x, int y, int w, int h, int radius, SDL_Color left, SDL_Color right) {
    if (w <= 0 || h <= 0) return;
    const int r = std::max(0, std::min({radius, w / 2, h / 2}));
    for (int col = 0; col < w; ++col) {
        const float t = static_cast<float>(col) / static_cast<float>(std::max(1, w - 1));
        const Uint8 cr = clamp8(static_cast<int>(left.r + (right.r - left.r) * t));
        const Uint8 cg = clamp8(static_cast<int>(left.g + (right.g - left.g) * t));
        const Uint8 cb = clamp8(static_cast<int>(left.b + (right.b - left.b) * t));
        const Uint8 ca = clamp8(static_cast<int>(left.a + (right.a - left.a) * t));
        int dyInset = 0;
        if (col < r) {
            const float xf = static_cast<float>(r - col) - 0.5f;
            const int dy = static_cast<int>(std::sqrt(std::max(0.0f, static_cast<float>(r * r) - xf * xf)));
            dyInset = r - dy;
        } else if (col >= w - r) {
            const float xf = static_cast<float>(col - (w - r)) + 0.5f;
            const int dy = static_cast<int>(std::sqrt(std::max(0.0f, static_cast<float>(r * r) - xf * xf)));
            dyInset = r - dy;
        }
        for (int dy = dyInset; dy < h - dyInset; ++dy) {
            blendPx(x + col, y + dy, cr, cg, cb, ca);
        }
    }
}

void UiEngine::drawRoundBorder(int x, int y, int w, int h, int radius, int thickness, SDL_Color c) {
    if (w <= 0 || h <= 0 || thickness <= 0) return;
    const int rOut = std::max(0, std::min({radius, w / 2, h / 2}));
    const int rIn = std::max(0, rOut - thickness);

    for (int dy = 0; dy < h; ++dy) {
        int outLeft = x;
        int outRight = x + w;
        if (dy < rOut) {
            const float yf = static_cast<float>(rOut - dy) - 0.5f;
            const int dx = static_cast<int>(std::sqrt(std::max(0.0f, static_cast<float>(rOut * rOut) - yf * yf)));
            outLeft = x + rOut - dx;
            outRight = x + w - (rOut - dx);
        } else if (dy >= h - rOut) {
            const float yf = static_cast<float>(dy - (h - rOut)) + 0.5f;
            const int dx = static_cast<int>(std::sqrt(std::max(0.0f, static_cast<float>(rOut * rOut) - yf * yf)));
            outLeft = x + rOut - dx;
            outRight = x + w - (rOut - dx);
        }

        if (dy < thickness || dy >= h - thickness) {
            blendRow(outLeft, outRight, y + dy, c.r, c.g, c.b, c.a);
        } else {
            int inLeft = x + thickness;
            int inRight = x + w - thickness;
            const int inDy = dy - thickness;
            const int inH = h - 2 * thickness;
            if (rIn > 0 && inDy < rIn) {
                const float yf = static_cast<float>(rIn - inDy) - 0.5f;
                const int dx = static_cast<int>(std::sqrt(std::max(0.0f, static_cast<float>(rIn * rIn) - yf * yf)));
                inLeft = x + thickness + rIn - dx;
                inRight = x + w - thickness - (rIn - dx);
            } else if (rIn > 0 && inDy >= inH - rIn) {
                const float yf = static_cast<float>(inDy - (inH - rIn)) + 0.5f;
                const int dx = static_cast<int>(std::sqrt(std::max(0.0f, static_cast<float>(rIn * rIn) - yf * yf)));
                inLeft = x + thickness + rIn - dx;
                inRight = x + w - thickness - (rIn - dx);
            }
            blendRow(outLeft, inLeft, y + dy, c.r, c.g, c.b, c.a);
            blendRow(inRight, outRight, y + dy, c.r, c.g, c.b, c.a);
        }
    }
}

void UiEngine::drawFocusRing(int x, int y, int w, int h, int radius) {
    drawRoundBorder(x - 12, y - 12, w + 24, h + 24, radius + 12, 8, SDL_Color{46, 123, 239, 88});
    drawRoundBorder(x - 5, y - 5, w + 10, h + 10, radius + 5, 5, SDL_Color{156, 193, 255, 255});
}

void UiEngine::drawCircle(int cx, int cy, int radius, SDL_Color c) {
    drawRoundRect(cx - radius, cy - radius, radius * 2, radius * 2, radius, c);
}

void UiEngine::drawImage(const std::string& name, int x, int y, int w, int h, Uint8 alpha) {
    auto it = images_.find(name);
    if (it == images_.end() || w <= 0 || h <= 0) return;
    const ImageData& img = it->second;
    if (img.w <= 0 || img.h <= 0) return;

    // aspect-fill crop + bilinear scaling + alpha blend
    const float dstRatio = static_cast<float>(w) / static_cast<float>(h);
    const float srcRatio = static_cast<float>(img.w) / static_cast<float>(img.h);
    int sx0 = 0, sy0 = 0, sw = img.w, sh = img.h;
    if (srcRatio > dstRatio) {
        sw = static_cast<int>(img.h * dstRatio);
        sx0 = (img.w - sw) / 2;
    } else if (srcRatio < dstRatio) {
        sh = static_cast<int>(img.w / dstRatio);
        sy0 = (img.h - sh) / 2;
    }

    for (int dy = 0; dy < h; ++dy) {
        const float sy = sy0 + ((dy + 0.5f) / h) * sh - 0.5f;
        const int y0 = std::max(0, std::min(img.h - 1, static_cast<int>(std::floor(sy))));
        const int y1 = std::min(img.h - 1, y0 + 1);
        const float fy = std::max(0.0f, std::min(1.0f, sy - y0));
        for (int dx = 0; dx < w; ++dx) {
            const float sx = sx0 + ((dx + 0.5f) / w) * sw - 0.5f;
            const int x0 = std::max(0, std::min(img.w - 1, static_cast<int>(std::floor(sx))));
            const int x1 = std::min(img.w - 1, x0 + 1);
            const float fx = std::max(0.0f, std::min(1.0f, sx - x0));

            const std::uint8_t* p00 = &img.rgba[(static_cast<std::size_t>(y0) * img.w + x0) * 4];
            const std::uint8_t* p10 = &img.rgba[(static_cast<std::size_t>(y0) * img.w + x1) * 4];
            const std::uint8_t* p01 = &img.rgba[(static_cast<std::size_t>(y1) * img.w + x0) * 4];
            const std::uint8_t* p11 = &img.rgba[(static_cast<std::size_t>(y1) * img.w + x1) * 4];

            float sample[4];
            for (int c = 0; c < 4; ++c) {
                const float top = p00[c] * (1.0f - fx) + p10[c] * fx;
                const float bot = p01[c] * (1.0f - fx) + p11[c] * fx;
                sample[c] = top * (1.0f - fy) + bot * fy;
            }
            const int sa = static_cast<int>(sample[3] * alpha / 255.0f);
            if (sa <= 0) continue;
            blendPx(x + dx, y + dy,
                    clamp8(static_cast<int>(sample[0])),
                    clamp8(static_cast<int>(sample[1])),
                    clamp8(static_cast<int>(sample[2])),
                    clamp8(sa));
        }
    }
}

void UiEngine::drawImageZoomed(const std::string& name, int x, int y, int w, int h, float zoom, Uint8 alpha) {
    auto it = images_.find(name);
    if (it == images_.end() || w <= 0 || h <= 0) return;
    const ImageData& img = it->second;
    const float clampedZoom = std::max(1.0f, zoom);
    const int cropW = static_cast<int>(img.w / clampedZoom);
    const int cropH = static_cast<int>(img.h / clampedZoom);
    // reuse drawImage path via temporary crop ratios
    const float dstRatio = static_cast<float>(w) / static_cast<float>(h);
    const float srcRatio = static_cast<float>(cropW) / static_cast<float>(cropH);
    int sx0 = (img.w - cropW) / 2;
    int sy0 = (img.h - cropH) / 2;
    int sw = cropW;
    int sh = cropH;
    if (srcRatio > dstRatio) {
        sw = static_cast<int>(cropH * dstRatio);
        sx0 = (img.w - sw) / 2;
    } else if (srcRatio < dstRatio) {
        sh = static_cast<int>(cropW / dstRatio);
        sy0 = (img.h - sh) / 2;
    }

    for (int dy = 0; dy < h; ++dy) {
        const float sy = sy0 + ((dy + 0.5f) / h) * sh - 0.5f;
        const int y0 = std::max(0, std::min(img.h - 1, static_cast<int>(std::floor(sy))));
        const int y1 = std::min(img.h - 1, y0 + 1);
        const float fy = std::max(0.0f, std::min(1.0f, sy - y0));
        for (int dx = 0; dx < w; ++dx) {
            const float sx = sx0 + ((dx + 0.5f) / w) * sw - 0.5f;
            const int x0 = std::max(0, std::min(img.w - 1, static_cast<int>(std::floor(sx))));
            const int x1 = std::min(img.w - 1, x0 + 1);
            const float fx = std::max(0.0f, std::min(1.0f, sx - x0));
            const std::uint8_t* p00 = &img.rgba[(static_cast<std::size_t>(y0) * img.w + x0) * 4];
            const std::uint8_t* p10 = &img.rgba[(static_cast<std::size_t>(y0) * img.w + x1) * 4];
            const std::uint8_t* p01 = &img.rgba[(static_cast<std::size_t>(y1) * img.w + x0) * 4];
            const std::uint8_t* p11 = &img.rgba[(static_cast<std::size_t>(y1) * img.w + x1) * 4];
            float sample[4];
            for (int c = 0; c < 4; ++c) {
                const float top = p00[c] * (1.0f - fx) + p10[c] * fx;
                const float bot = p01[c] * (1.0f - fx) + p11[c] * fx;
                sample[c] = top * (1.0f - fy) + bot * fy;
            }
            const int sa = static_cast<int>(sample[3] * alpha / 255.0f);
            if (sa <= 0) continue;
            blendPx(x + dx, y + dy,
                    clamp8(static_cast<int>(sample[0])),
                    clamp8(static_cast<int>(sample[1])),
                    clamp8(static_cast<int>(sample[2])),
                    clamp8(sa));
        }
    }
}

const UiEngine::TextBitmap& UiEngine::buildFallbackText(const std::string& utf8, int pxSize) {
    static TextBitmap empty;
    // simple uppercase ASCII 5x7 fallback
    std::string upper;
    for (char ch : utf8) {
        char c = ch;
        if (c >= 'a' && c <= 'z') c = static_cast<char>(c - 'a' + 'A');
        if (static_cast<unsigned char>(c) >= 0x80) c = '?';
        if (c < 0x20) c = ' ';
        upper.push_back(c);
    }
    const int scale = std::max(1, pxSize / 7);
    const int w = std::max(1, static_cast<int>(upper.size()) * 6 * scale);
    const int h = 7 * scale + 2;
    TextBitmap bmp;
    bmp.w = w;
    bmp.h = h;
    bmp.px.assign(static_cast<std::size_t>(w * h), 0);
    int cursorX = 1;
    for (char ch : upper) {
        const auto rows = fallbackGlyph(ch);
        for (int row = 0; row < 7; ++row) {
            for (int col = 0; col < 5; ++col) {
                if ((rows[static_cast<std::size_t>(row)] & (1U << (4 - col))) == 0) continue;
                for (int sy = 0; sy < scale; ++sy) {
                    for (int sx = 0; sx < scale; ++sx) {
                        const int px = cursorX + col * scale + sx;
                        const int py = 1 + row * scale + sy;
                        if (px >= 0 && px < w && py >= 0 && py < h) {
                            bmp.px[static_cast<std::size_t>(py * w + px)] = 0xFFFFFFFFu;
                        }
                    }
                }
            }
        }
        cursorX += 6 * scale;
    }

    const std::string key = "fb:" + std::to_string(pxSize) + ":" + utf8;
    auto it = textCache_.find(key);
    if (it != textCache_.end()) return it->second;
    auto res = textCache_.emplace(key, std::move(bmp));
    return res.first->second;
}

const UiEngine::TextBitmap& UiEngine::getOrBuildText(const std::string& utf8,
                                                     int pxSize,
                                                     FontRole role,
                                                     bool isArabicLang) {
    static TextBitmap kEmpty{};
    if (utf8.empty() || pxSize <= 0) return kEmpty;

    const std::string key = std::to_string(static_cast<int>(role)) + ":" +
                            std::to_string(pxSize) + ":" + (isArabicLang ? "1:" : "0:") + utf8;
    auto it = textCache_.find(key);
    if (it != textCache_.end()) return it->second;

    if (textCache_.size() > 600) {
        textCache_.clear();
    }

    if (!fontsLoaded_) {
        return buildFallbackText(utf8, pxSize);
    }

    std::vector<std::uint32_t> cps = decodeUtf8(utf8);
    const bool hasArabic = containsArabicScript(cps);
    if (hasArabic) cps = shapeAndOrderArabic(cps);

    stbtt_fontinfo* primaryFont = &fonts_->bodyInfo;
    if (hasArabic || (role == FontRole::Pixel && isArabicLang) || role == FontRole::Arabic) {
        primaryFont = &fonts_->arInfo;
    } else if (role == FontRole::Pixel) {
        primaryFont = &fonts_->f77Info;
    }

    const float scalePrimary = stbtt_ScaleForPixelHeight(primaryFont, static_cast<float>(pxSize));
    int ascent = 0, descent = 0, lineGap = 0;
    stbtt_GetFontVMetrics(primaryFont, &ascent, &descent, &lineGap);
    const int baseline = static_cast<int>(std::ceil(ascent * scalePrimary)) + 2;
    const int surfH = std::max(pxSize + 8,
                               static_cast<int>(std::ceil((ascent - descent) * scalePrimary)) + 6);

    auto pickFontForCp = [&](std::uint32_t cp, float& outScale) -> stbtt_fontinfo* {
        if (stbtt_FindGlyphIndex(primaryFont, static_cast<int>(cp)) != 0) {
            outScale = scalePrimary;
            return primaryFont;
        }
        if (stbtt_FindGlyphIndex(&fonts_->bodyInfo, static_cast<int>(cp)) != 0) {
            outScale = stbtt_ScaleForPixelHeight(&fonts_->bodyInfo, static_cast<float>(pxSize));
            return &fonts_->bodyInfo;
        }
        if (stbtt_FindGlyphIndex(&fonts_->arInfo, static_cast<int>(cp)) != 0) {
            outScale = stbtt_ScaleForPixelHeight(&fonts_->arInfo, static_cast<float>(pxSize));
            return &fonts_->arInfo;
        }
        if (fonts_->cjkValid && stbtt_FindGlyphIndex(&fonts_->cjkInfo, static_cast<int>(cp)) != 0) {
            outScale = stbtt_ScaleForPixelHeight(&fonts_->cjkInfo, static_cast<float>(pxSize));
            return &fonts_->cjkInfo;
        }
        outScale = scalePrimary;
        return primaryFont;
    };

    float penX = 2.0f;
    for (std::size_t i = 0; i < cps.size(); ++i) {
        float sc = scalePrimary;
        stbtt_fontinfo* fn = pickFontForCp(cps[i], sc);
        int adv = 0, lsb = 0;
        stbtt_GetCodepointHMetrics(fn, static_cast<int>(cps[i]), &adv, &lsb);
        penX += adv * sc;
        if (i + 1 < cps.size() && fn == primaryFont) {
            penX += sc * stbtt_GetCodepointKernAdvance(
                             fn, static_cast<int>(cps[i]), static_cast<int>(cps[i + 1]));
        }
    }
    const int surfW = std::max(4, static_cast<int>(std::ceil(penX)) + 6);

    TextBitmap bmp;
    bmp.w = surfW;
    bmp.h = surfH;
    bmp.px.assign(static_cast<std::size_t>(surfW * surfH), 0);

    penX = 2.0f;
    for (std::size_t i = 0; i < cps.size(); ++i) {
        float sc = scalePrimary;
        stbtt_fontinfo* fn = pickFontForCp(cps[i], sc);
        int adv = 0, lsb = 0;
        stbtt_GetCodepointHMetrics(fn, static_cast<int>(cps[i]), &adv, &lsb);

        int gw = 0, gh = 0, xoff = 0, yoff = 0;
        unsigned char* glyphBmp = stbtt_GetCodepointBitmap(
            fn, sc, sc, static_cast<int>(cps[i]), &gw, &gh, &xoff, &yoff);
        if (glyphBmp) {
            const int dstX0 = static_cast<int>(std::floor(penX)) + xoff;
            const int dstY0 = baseline + yoff;
            for (int gy = 0; gy < gh; ++gy) {
                const int py = dstY0 + gy;
                if (py < 0 || py >= surfH) continue;
                for (int gx = 0; gx < gw; ++gx) {
                    const int px = dstX0 + gx;
                    if (px < 0 || px >= surfW) continue;
                    const unsigned char a = glyphBmp[gy * gw + gx];
                    if (a == 0) continue;
                    std::uint32_t& cell = bmp.px[static_cast<std::size_t>(py * surfW + px)];
                    const unsigned char prevA = static_cast<unsigned char>((cell >> 24) & 0xFFu);
                    const unsigned char newA = std::max(prevA, a);
                    cell = (static_cast<std::uint32_t>(newA) << 24) | 0x00FFFFFFu;
                }
            }
            stbtt_FreeBitmap(glyphBmp, nullptr);
        }

        penX += adv * sc;
        if (i + 1 < cps.size() && fn == primaryFont) {
            penX += sc * stbtt_GetCodepointKernAdvance(
                             fn, static_cast<int>(cps[i]), static_cast<int>(cps[i + 1]));
        }
    }

    auto res = textCache_.emplace(key, std::move(bmp));
    return res.first->second;
}

int UiEngine::measureText(const std::string& utf8, int pxSize, FontRole role, bool isArabicLang) {
    return getOrBuildText(utf8, pxSize, role, isArabicLang).w;
}

int UiEngine::drawText(const std::string& utf8,
                       int x,
                       int y,
                       int pxSize,
                       SDL_Color color,
                       FontRole role,
                       bool alignRight,
                       bool isArabicLang,
                       int maxWidth) {
    const TextBitmap& bmp = getOrBuildText(utf8, pxSize, role, isArabicLang);
    if (bmp.w <= 0 || bmp.h <= 0) return 0;

    int drawW = bmp.w;
    int srcX0 = 0;
    if (maxWidth > 0 && drawW > maxWidth) {
        drawW = maxWidth;
        if (alignRight) srcX0 = bmp.w - maxWidth;
    }
    const int drawX = alignRight ? (x - drawW) : x;

    for (int dy = 0; dy < bmp.h; ++dy) {
        for (int dx = 0; dx < drawW; ++dx) {
            const std::uint32_t cell = bmp.px[static_cast<std::size_t>(dy) * bmp.w + (srcX0 + dx)];
            const unsigned char a = static_cast<unsigned char>((cell >> 24) & 0xFF);
            if (a == 0) continue;
            const int fa = (a * color.a) / 255;
            if (fa <= 0) continue;
            blendPx(drawX + dx, y + dy, color.r, color.g, color.b, static_cast<Uint8>(fa));
        }
    }
    return drawW;
}

int UiEngine::drawTextCentered(const std::string& utf8,
                               int cx,
                               int cy,
                               int pxSize,
                               SDL_Color color,
                               FontRole role,
                               bool isArabicLang) {
    const TextBitmap& bmp = getOrBuildText(utf8, pxSize, role, isArabicLang);
    if (bmp.w <= 0 || bmp.h <= 0) return 0;
    return drawText(utf8, cx - bmp.w / 2, cy - bmp.h / 2, pxSize, color, role, false, isArabicLang);
}

void UiEngine::present(SDL_Surface* windowSurface) {
    if (!windowSurface || !windowSurface->pixels) return;
    SDL_PixelFormat* fmt = windowSurface->format;
    if (!fmt || fmt->BytesPerPixel != 4) return;

    Uint8* dstBase = static_cast<Uint8*>(windowSurface->pixels);
    const int pitch = windowSurface->pitch;

    // shift amounts from channel masks (handles RGBA/BGRA/ARGB/ABGR)
    auto maskShift = [](Uint32 mask) -> int {
        if (mask == 0) return -1;
        int shift = 0;
        while ((mask & 1u) == 0) {
            mask >>= 1;
            ++shift;
        }
        return shift;
    };
    const int rShift = maskShift(fmt->Rmask);
    const int gShift = maskShift(fmt->Gmask);
    const int bShift = maskShift(fmt->Bmask);
    const int aShift = maskShift(fmt->Amask);

    const int copyW = std::min(kWidth, windowSurface->w);
    const int copyH = std::min(kHeight, windowSurface->h);

    for (int y = 0; y < copyH; ++y) {
        const std::uint32_t* srcRow = &fb_[static_cast<std::size_t>(y) * kWidth];
        Uint8* dstRow = dstBase + static_cast<std::size_t>(y) * pitch;
        for (int x = 0; x < copyW; ++x) {
            const std::uint32_t px = srcRow[x];
            const Uint8 r = static_cast<Uint8>((px >> 16) & 0xFF);
            const Uint8 g = static_cast<Uint8>((px >> 8) & 0xFF);
            const Uint8 b = static_cast<Uint8>(px & 0xFF);
            const Uint8 a = static_cast<Uint8>((px >> 24) & 0xFF);
            std::uint32_t out = 0;
            if (rShift >= 0) out |= static_cast<std::uint32_t>(r) << rShift;
            if (gShift >= 0) out |= static_cast<std::uint32_t>(g) << gShift;
            if (bShift >= 0) out |= static_cast<std::uint32_t>(b) << bShift;
            if (aShift >= 0) out |= static_cast<std::uint32_t>(a) << aShift;
            std::memcpy(dstRow + static_cast<std::size_t>(x) * 4, &out, 4);
        }
    }
}

} // namespace pkgtest
