#include "ui_engine.hpp"

#define STB_IMAGE_IMPLEMENTATION
#define STBI_ONLY_JPEG
#define STBI_ONLY_PNG
#include "stb_image.h"

#define STB_TRUETYPE_IMPLEMENTATION
#include "stb_truetype.h"

#include <algorithm>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <vector>

namespace pkgtest {
namespace {

struct ArabicLetterForms {
    std::uint32_t base;
    std::uint32_t iso;
    std::uint32_t fin;
    std::uint32_t ini;
    std::uint32_t med;
    bool connectsNext;
};

constexpr ArabicLetterForms kArabicTable[] = {
    {0x0621, 0x0621, 0x0621, 0x0621, 0x0621, false}, // Hamza
    {0x0622, 0x0622, 0xFE82, 0x0622, 0xFE82, false}, // Alef Madda
    {0x0623, 0x0623, 0xFE84, 0x0623, 0xFE84, false}, // Alef Hamza Above
    {0x0624, 0x0624, 0xFE86, 0x0624, 0xFE86, false}, // Waw Hamza
    {0x0625, 0x0625, 0xFE88, 0x0625, 0xFE88, false}, // Alef Hamza Below
    {0x0626, 0x0626, 0xFE8A, 0xFE8B, 0xFE8C, true},  // Yeh Hamza
    {0x0627, 0x0627, 0xFE8E, 0x0627, 0xFE8E, false}, // Alef
    {0x0628, 0x0628, 0xFE90, 0xFE91, 0xFE92, true},  // Beh
    {0x0629, 0x0629, 0xFE94, 0x0629, 0xFE94, false}, // Teh Marbuta
    {0x062A, 0x062A, 0xFE96, 0xFE97, 0xFE98, true},  // Teh
    {0x062B, 0x062B, 0xFE9A, 0xFE9B, 0xFE9C, true},  // Theh
    {0x062C, 0x062C, 0xFE9E, 0xFE9F, 0xFEA0, true},  // Jeem
    {0x062D, 0x062D, 0xFEA2, 0xFEA3, 0xFEA4, true},  // Hah
    {0x062E, 0x062E, 0xFEA6, 0xFEA7, 0xFEA8, true},  // Khah
    {0x062F, 0x062F, 0xFEAA, 0x062F, 0xFEAA, false}, // Dal
    {0x0630, 0x0630, 0xFEAC, 0x0630, 0xFEAC, false}, // Thal
    {0x0631, 0x0631, 0xFEAE, 0x0631, 0xFEAE, false}, // Reh
    {0x0632, 0x0632, 0xFEB0, 0x0632, 0xFEB0, false}, // Zain
    {0x0633, 0x0633, 0xFEB2, 0xFEB3, 0xFEB4, true},  // Seen
    {0x0634, 0x0634, 0xFEB6, 0xFEB7, 0xFEB8, true},  // Sheen
    {0x0635, 0x0635, 0xFEBA, 0xFEBB, 0xFEBC, true},  // Sad
    {0x0636, 0x0636, 0xFEBE, 0xFEBF, 0xFEC0, true},  // Dad
    {0x0637, 0x0637, 0xFEC2, 0xFEC3, 0xFEC4, true},  // Tah
    {0x0638, 0x0638, 0xFEC6, 0xFEC7, 0xFEC8, true},  // Zah
    {0x0639, 0x0639, 0xFECA, 0xFECB, 0xFECC, true},  // Ain
    {0x063A, 0x063A, 0xFECE, 0xFECF, 0xFED0, true},  // Ghain
    {0x0640, 0x0640, 0x0640, 0x0640, 0x0640, true},  // Tatweel
    {0x0641, 0x0641, 0xFED2, 0xFED3, 0xFED4, true},  // Feh
    {0x0642, 0x0642, 0xFED6, 0xFED7, 0xFED8, true},  // Qaf
    {0x0643, 0x0643, 0xFEDA, 0xFEDB, 0xFEDC, true},  // Kaf
    {0x0644, 0x0644, 0xFEDE, 0xFEDF, 0xFEE0, true},  // Lam
    {0x0645, 0x0645, 0xFEE2, 0xFEE3, 0xFEE4, true},  // Meem
    {0x0646, 0x0646, 0xFEE6, 0xFEE7, 0xFEE8, true},  // Noon
    {0x0647, 0x0647, 0xFEEA, 0xFEEB, 0xFEEC, true},  // Heh
    {0x0648, 0x0648, 0xFEEE, 0x0648, 0xFEEE, false}, // Waw
    {0x0649, 0x0649, 0xFEF0, 0x0649, 0xFEF0, false}, // Alef Maksura
    {0x064A, 0x064A, 0xFEF2, 0xFEF3, 0xFEF4, true},  // Yeh
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
            const std::uint32_t cp = ((c0 & 0x1FU) << 6) |
                                     (static_cast<unsigned char>(text[i + 1]) & 0x3FU);
            out.push_back(cp);
            i += 2;
        } else if ((c0 & 0xF0) == 0xE0 && i + 2 < text.size()) {
            const std::uint32_t cp = ((c0 & 0x0FU) << 12) |
                                     ((static_cast<unsigned char>(text[i + 1]) & 0x3FU) << 6) |
                                     (static_cast<unsigned char>(text[i + 2]) & 0x3FU);
            out.push_back(cp);
            i += 3;
        } else if ((c0 & 0xF8) == 0xF0 && i + 3 < text.size()) {
            const std::uint32_t cp = ((c0 & 0x07U) << 18) |
                                     ((static_cast<unsigned char>(text[i + 1]) & 0x3FU) << 12) |
                                     ((static_cast<unsigned char>(text[i + 2]) & 0x3FU) << 6) |
                                     (static_cast<unsigned char>(text[i + 3]) & 0x3FU);
            out.push_back(cp);
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

// Performs Arabic contextual shaping + Lam-Alef ligatures + visual RTL ordering
std::vector<std::uint32_t> shapeAndOrderArabic(const std::vector<std::uint32_t>& raw) {
    std::vector<std::uint32_t> clean;
    clean.reserve(raw.size());
    for (std::uint32_t cp : raw) {
        if (!isArabicDiacritic(cp)) {
            // Convert Arabic-Indic digits (٠..٩) to standard digits so numbers render cleanly
            if (cp >= 0x0660 && cp <= 0x0669) {
                clean.push_back('0' + (cp - 0x0660));
            } else {
                clean.push_back(cp);
            }
        }
    }

    // Step 1: Contextual shaping + Lam-Alef ligatures in logical order
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

        // Check Lam-Alef ligatures (0x0644 followed by 0x0622, 0x0623, 0x0625, 0x0627)
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

        if (prevConnects && nextConnects) {
            shaped.push_back(currForm->med);
        } else if (prevConnects && !nextConnects) {
            shaped.push_back(currForm->fin);
        } else if (!prevConnects && nextConnects) {
            shaped.push_back(currForm->ini);
        } else {
            shaped.push_back(currForm->iso);
        }
    }

    // Step 2: Convert logical RTL sequence into visual LTR sequence while keeping LTR runs (Latin/numbers) intact
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
            while (idx > 0 && isLtrRunChar(shaped[idx - 1])) {
                --idx;
            }
            for (std::size_t k = idx; k < endRun; ++k) {
                visual.push_back(shaped[k]);
            }
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

UiEngine::~UiEngine() {
    shutdown();
}

bool UiEngine::init(SDL_Renderer* renderer, const std::string& pakPath) {
    renderer_ = renderer;
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
        return false;
    }

    char magic[8] = {};
    if (std::fread(magic, 1, 8, fp) != 8 || std::memcmp(magic, "ASUI0001", 8) != 0) {
        std::fclose(fp);
        return false;
    }

    std::uint32_t count = 0;
    if (std::fread(&count, sizeof(count), 1, fp) != 1) {
        std::fclose(fp);
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
        if (nameLen > 0 && std::fread(&name[0], 1, nameLen, fp) != nameLen) {
            break;
        }
        std::vector<unsigned char> data(dataLen);
        if (dataLen > 0 && std::fread(data.data(), 1, dataLen, fp) != dataLen) {
            break;
        }

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
        } else if (renderer_) {
            int w = 0, h = 0, comp = 0;
            unsigned char* pixels = stbi_load_from_memory(
                data.data(), static_cast<int>(data.size()), &w, &h, &comp, 4);
            if (pixels) {
                SDL_Surface* surf = SDL_CreateRGBSurfaceWithFormatFrom(
                    pixels, w, h, 32, w * 4, SDL_PIXELFORMAT_RGBA32);
                if (surf) {
                    SDL_Texture* tex = SDL_CreateTextureFromSurface(renderer_, surf);
                    if (tex) {
                        SDL_SetTextureBlendMode(tex, SDL_BLENDMODE_BLEND);
                        images_[name] = TextureEntry{tex, w, h};
                    }
                    SDL_FreeSurface(surf);
                }
                stbi_image_free(pixels);
            }
        }
    }

    std::fclose(fp);
    return fonts_->f77Valid && fonts_->arValid && fonts_->bodyValid;
}

void UiEngine::shutdown() {
    for (auto& kv : textCache_) {
        if (kv.second.tex) SDL_DestroyTexture(kv.second.tex);
    }
    textCache_.clear();

    for (auto& kv : images_) {
        if (kv.second.tex) SDL_DestroyTexture(kv.second.tex);
    }
    images_.clear();

    delete fonts_;
    fonts_ = nullptr;
    renderer_ = nullptr;
}

SDL_Texture* UiEngine::getTexture(const std::string& name, int* outW, int* outH) {
    auto it = images_.find(name);
    if (it == images_.end()) return nullptr;
    if (outW) *outW = it->second.w;
    if (outH) *outH = it->second.h;
    return it->second.tex;
}

void UiEngine::drawImage(const std::string& name, int x, int y, int w, int h, Uint8 alpha) {
    int srcW = 0, srcH = 0;
    SDL_Texture* tex = getTexture(name, &srcW, &srcH);
    if (!tex || srcW <= 0 || srcH <= 0) return;
    SDL_SetTextureAlphaMod(tex, alpha);

    // Aspect-fill crop so covers and banners never stretch
    const float dstRatio = static_cast<float>(w) / static_cast<float>(std::max(1, h));
    const float srcRatio = static_cast<float>(srcW) / static_cast<float>(std::max(1, srcH));
    SDL_Rect srcRect{0, 0, srcW, srcH};
    if (srcRatio > dstRatio) {
        const int cropW = static_cast<int>(srcH * dstRatio);
        srcRect.x = (srcW - cropW) / 2;
        srcRect.w = cropW;
    } else if (srcRatio < dstRatio) {
        const int cropH = static_cast<int>(srcW / dstRatio);
        srcRect.y = (srcH - cropH) / 2;
        srcRect.h = cropH;
    }
    SDL_Rect dstRect{x, y, w, h};
    SDL_RenderCopy(renderer_, tex, &srcRect, &dstRect);
}

void UiEngine::drawImageZoomed(const std::string& name, int x, int y, int w, int h, float zoom, Uint8 alpha) {
    int srcW = 0, srcH = 0;
    SDL_Texture* tex = getTexture(name, &srcW, &srcH);
    if (!tex || srcW <= 0 || srcH <= 0) return;
    SDL_SetTextureAlphaMod(tex, alpha);
    const float clampedZoom = std::max(1.0f, zoom);
    const int cropW = static_cast<int>(srcW / clampedZoom);
    const int cropH = static_cast<int>(srcH / clampedZoom);
    SDL_Rect srcRect{(srcW - cropW) / 2, (srcH - cropH) / 2, cropW, cropH};
    SDL_Rect dstRect{x, y, w, h};
    SDL_RenderCopy(renderer_, tex, &srcRect, &dstRect);
}

void UiEngine::fillRect(int x, int y, int w, int h, SDL_Color c) {
    SDL_SetRenderDrawBlendMode(renderer_, SDL_BLENDMODE_BLEND);
    SDL_SetRenderDrawColor(renderer_, c.r, c.g, c.b, c.a);
    SDL_Rect r{x, y, w, h};
    SDL_RenderFillRect(renderer_, &r);
}

void UiEngine::fillGradH(int x, int y, int w, int h, SDL_Color left, SDL_Color right) {
    SDL_SetRenderDrawBlendMode(renderer_, SDL_BLENDMODE_BLEND);
    const int steps = std::min(w, 64);
    if (steps <= 0) return;
    for (int i = 0; i < steps; ++i) {
        const int x0 = x + (i * w) / steps;
        const int x1 = x + ((i + 1) * w) / steps;
        const float t = static_cast<float>(i) / static_cast<float>(std::max(1, steps - 1));
        const Uint8 r = static_cast<Uint8>(left.r + (right.r - left.r) * t);
        const Uint8 g = static_cast<Uint8>(left.g + (right.g - left.g) * t);
        const Uint8 b = static_cast<Uint8>(left.b + (right.b - left.b) * t);
        const Uint8 a = static_cast<Uint8>(left.a + (right.a - left.a) * t);
        SDL_SetRenderDrawColor(renderer_, r, g, b, a);
        SDL_Rect strip{x0, y, std::max(1, x1 - x0), h};
        SDL_RenderFillRect(renderer_, &strip);
    }
}

void UiEngine::fillGradV(int x, int y, int w, int h, SDL_Color top, SDL_Color bottom) {
    SDL_SetRenderDrawBlendMode(renderer_, SDL_BLENDMODE_BLEND);
    const int steps = std::min(h, 64);
    if (steps <= 0) return;
    for (int i = 0; i < steps; ++i) {
        const int y0 = y + (i * h) / steps;
        const int y1 = y + ((i + 1) * h) / steps;
        const float t = static_cast<float>(i) / static_cast<float>(std::max(1, steps - 1));
        const Uint8 r = static_cast<Uint8>(top.r + (bottom.r - top.r) * t);
        const Uint8 g = static_cast<Uint8>(top.g + (bottom.g - top.g) * t);
        const Uint8 b = static_cast<Uint8>(top.b + (bottom.b - top.b) * t);
        const Uint8 a = static_cast<Uint8>(top.a + (bottom.a - top.a) * t);
        SDL_SetRenderDrawColor(renderer_, r, g, b, a);
        SDL_Rect strip{x, y0, w, std::max(1, y1 - y0)};
        SDL_RenderFillRect(renderer_, &strip);
    }
}

void UiEngine::drawRoundRect(int x, int y, int w, int h, int radius, SDL_Color c) {
    if (w <= 0 || h <= 0) return;
    const int r = std::max(0, std::min({radius, w / 2, h / 2}));
    if (r <= 1) {
        fillRect(x, y, w, h, c);
        return;
    }
    SDL_SetRenderDrawBlendMode(renderer_, SDL_BLENDMODE_BLEND);
    SDL_SetRenderDrawColor(renderer_, c.r, c.g, c.b, c.a);

    SDL_Rect center{x, y + r, w, h - 2 * r};
    SDL_RenderFillRect(renderer_, &center);

    for (int dy = 0; dy < r; ++dy) {
        const float yf = static_cast<float>(r - dy) - 0.5f;
        const int dx = static_cast<int>(std::sqrt(std::max(0.0f, static_cast<float>(r * r) - yf * yf)));
        const int lineX = x + r - dx;
        const int lineW = w - 2 * (r - dx);
        SDL_Rect topRow{lineX, y + dy, lineW, 1};
        SDL_Rect botRow{lineX, y + h - 1 - dy, lineW, 1};
        SDL_RenderFillRect(renderer_, &topRow);
        SDL_RenderFillRect(renderer_, &botRow);
    }
}

void UiEngine::drawRoundGradH(int x, int y, int w, int h, int radius, SDL_Color left, SDL_Color right) {
    if (w <= 0 || h <= 0) return;
    const int r = std::max(0, std::min({radius, w / 2, h / 2}));
    SDL_SetRenderDrawBlendMode(renderer_, SDL_BLENDMODE_BLEND);
    for (int col = 0; col < w; ++col) {
        const float t = static_cast<float>(col) / static_cast<float>(std::max(1, w - 1));
        const Uint8 cr = static_cast<Uint8>(left.r + (right.r - left.r) * t);
        const Uint8 cg = static_cast<Uint8>(left.g + (right.g - left.g) * t);
        const Uint8 cb = static_cast<Uint8>(left.b + (right.b - left.b) * t);
        const Uint8 ca = static_cast<Uint8>(left.a + (right.a - left.a) * t);
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
        SDL_SetRenderDrawColor(renderer_, cr, cg, cb, ca);
        SDL_Rect vline{x + col, y + dyInset, 1, std::max(1, h - 2 * dyInset)};
        SDL_RenderFillRect(renderer_, &vline);
    }
}

void UiEngine::drawRoundBorder(int x, int y, int w, int h, int radius, int thickness, SDL_Color c) {
    if (w <= 0 || h <= 0 || thickness <= 0) return;
    const int rOut = std::max(0, std::min({radius, w / 2, h / 2}));
    const int rIn = std::max(0, rOut - thickness);
    SDL_SetRenderDrawBlendMode(renderer_, SDL_BLENDMODE_BLEND);
    SDL_SetRenderDrawColor(renderer_, c.r, c.g, c.b, c.a);

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
            SDL_Rect row{outLeft, y + dy, std::max(0, outRight - outLeft), 1};
            SDL_RenderFillRect(renderer_, &row);
        } else {
            int inLeft = x + thickness;
            int inRight = x + w - thickness;
            const int inDy = dy - thickness;
            const int inH = h - 2 * thickness;
            if (inDy < rIn) {
                const float yf = static_cast<float>(rIn - inDy) - 0.5f;
                const int dx = static_cast<int>(std::sqrt(std::max(0.0f, static_cast<float>(rIn * rIn) - yf * yf)));
                inLeft = x + thickness + rIn - dx;
                inRight = x + w - thickness - (rIn - dx);
            } else if (inDy >= inH - rIn) {
                const float yf = static_cast<float>(inDy - (inH - rIn)) + 0.5f;
                const int dx = static_cast<int>(std::sqrt(std::max(0.0f, static_cast<float>(rIn * rIn) - yf * yf)));
                inLeft = x + thickness + rIn - dx;
                inRight = x + w - thickness - (rIn - dx);
            }
            SDL_Rect leftSeg{outLeft, y + dy, std::max(0, inLeft - outLeft), 1};
            SDL_Rect rightSeg{inRight, y + dy, std::max(0, outRight - inRight), 1};
            SDL_RenderFillRect(renderer_, &leftSeg);
            SDL_RenderFillRect(renderer_, &rightSeg);
        }
    }
}

void UiEngine::drawFocusRing(int x, int y, int w, int h, int radius) {
    // 14px outer glow #2E7BEF@35% + 5px focus ring #9CC1FF (offset 4px)
    drawRoundBorder(x - 12, y - 12, w + 24, h + 24, radius + 12, 8, SDL_Color{46, 123, 239, 88});
    drawRoundBorder(x - 5, y - 5, w + 10, h + 10, radius + 5, 5, SDL_Color{156, 193, 255, 255});
}

void UiEngine::drawCircle(int cx, int cy, int radius, SDL_Color c) {
    drawRoundRect(cx - radius, cy - radius, radius * 2, radius * 2, radius, c);
}

const UiEngine::CachedText& UiEngine::getOrBuildText(const std::string& utf8,
                                                     int pxSize,
                                                     FontRole role,
                                                     bool isArabicLang) {
    static CachedText kEmpty{};
    if (!fonts_ || utf8.empty() || pxSize <= 0) return kEmpty;

    ++tickCounter_;
    const std::string key = std::to_string(static_cast<int>(role)) + ":" +
                            std::to_string(pxSize) + ":" +
                            (isArabicLang ? "1:" : "0:") + utf8;
    auto it = textCache_.find(key);
    if (it != textCache_.end()) {
        it->second.lastUsed = tickCounter_;
        return it->second;
    }

    if (textCache_.size() > 512) {
        auto oldest = textCache_.begin();
        for (auto cur = textCache_.begin(); cur != textCache_.end(); ++cur) {
            if (cur->second.lastUsed < oldest->second.lastUsed) oldest = cur;
        }
        if (oldest->second.tex) SDL_DestroyTexture(oldest->second.tex);
        textCache_.erase(oldest);
    }

    std::vector<std::uint32_t> cps = decodeUtf8(utf8);
    const bool hasArabic = containsArabicScript(cps);
    if (hasArabic) {
        cps = shapeAndOrderArabic(cps);
    }

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
        int g = stbtt_FindGlyphIndex(primaryFont, static_cast<int>(cp));
        if (g != 0) {
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

    // Measure total width
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

    std::vector<std::uint32_t> rgba(static_cast<std::size_t>(surfW * surfH), 0x00FFFFFFU);
    penX = 2.0f;

    for (std::size_t i = 0; i < cps.size(); ++i) {
        float sc = scalePrimary;
        stbtt_fontinfo* fn = pickFontForCp(cps[i], sc);
        int adv = 0, lsb = 0;
        stbtt_GetCodepointHMetrics(fn, static_cast<int>(cps[i]), &adv, &lsb);

        int gw = 0, gh = 0, xoff = 0, yoff = 0;
        unsigned char* bmp = stbtt_GetCodepointBitmap(
            fn, sc, sc, static_cast<int>(cps[i]), &gw, &gh, &xoff, &yoff);
        if (bmp) {
            const int dstX0 = static_cast<int>(std::floor(penX)) + xoff;
            const int dstY0 = baseline + yoff;
            for (int gy = 0; gy < gh; ++gy) {
                const int py = dstY0 + gy;
                if (py < 0 || py >= surfH) continue;
                for (int gx = 0; gx < gw; ++gx) {
                    const int px = dstX0 + gx;
                    if (px < 0 || px >= surfW) continue;
                    const unsigned char a = bmp[gy * gw + gx];
                    if (a == 0) continue;
                    std::uint32_t& cell = rgba[static_cast<std::size_t>(py * surfW + px)];
                    const unsigned char prevA = static_cast<unsigned char>((cell >> 24) & 0xFFU);
                    const unsigned char newA = std::max(prevA, a);
                    cell = (static_cast<std::uint32_t>(newA) << 24) | 0x00FFFFFFU;
                }
            }
            stbtt_FreeBitmap(bmp, nullptr);
        }

        penX += adv * sc;
        if (i + 1 < cps.size() && fn == primaryFont) {
            penX += sc * stbtt_GetCodepointKernAdvance(
                             fn, static_cast<int>(cps[i]), static_cast<int>(cps[i + 1]));
        }
    }

    SDL_Texture* tex = nullptr;
    if (renderer_) {
        SDL_Surface* surf = SDL_CreateRGBSurfaceWithFormatFrom(
            rgba.data(), surfW, surfH, 32, surfW * 4, SDL_PIXELFORMAT_RGBA32);
        if (surf) {
            tex = SDL_CreateTextureFromSurface(renderer_, surf);
            if (tex) {
                SDL_SetTextureBlendMode(tex, SDL_BLENDMODE_BLEND);
            }
            SDL_FreeSurface(surf);
        }
    }

    CachedText entry{tex, surfW, surfH, tickCounter_};
    auto res = textCache_.emplace(key, entry);
    return res.first->second;
}

int UiEngine::measureText(const std::string& utf8, int pxSize, FontRole role, bool isArabicLang) {
    const CachedText& ct = getOrBuildText(utf8, pxSize, role, isArabicLang);
    return ct.w;
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
    const CachedText& ct = getOrBuildText(utf8, pxSize, role, isArabicLang);
    if (!ct.tex || ct.w <= 0 || ct.h <= 0) return 0;

    SDL_SetTextureColorMod(ct.tex, color.r, color.g, color.b);
    SDL_SetTextureAlphaMod(ct.tex, color.a);

    int drawW = ct.w;
    SDL_Rect src{0, 0, ct.w, ct.h};
    if (maxWidth > 0 && drawW > maxWidth) {
        drawW = maxWidth;
        if (alignRight) {
            src.x = ct.w - maxWidth;
        }
        src.w = maxWidth;
    }

    const int drawX = alignRight ? (x - drawW) : x;
    SDL_Rect dst{drawX, y, drawW, ct.h};
    SDL_RenderCopy(renderer_, ct.tex, &src, &dst);
    return drawW;
}

int UiEngine::drawTextCentered(const std::string& utf8,
                               int cx,
                               int cy,
                               int pxSize,
                               SDL_Color color,
                               FontRole role,
                               bool isArabicLang) {
    const CachedText& ct = getOrBuildText(utf8, pxSize, role, isArabicLang);
    if (!ct.tex || ct.w <= 0 || ct.h <= 0) return 0;

    SDL_SetTextureColorMod(ct.tex, color.r, color.g, color.b);
    SDL_SetTextureAlphaMod(ct.tex, color.a);

    SDL_Rect dst{cx - ct.w / 2, cy - ct.h / 2, ct.w, ct.h};
    SDL_RenderCopy(renderer_, ct.tex, nullptr, &dst);
    return ct.w;
}

} // namespace pkgtest
