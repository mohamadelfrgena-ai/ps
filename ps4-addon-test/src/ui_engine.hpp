#ifndef PKG_ADDON_TEST_UI_ENGINE_HPP
#define PKG_ADDON_TEST_UI_ENGINE_HPP

#include <SDL2/SDL.h>
#include <cstdint>
#include <string>
#include <unordered_map>
#include <vector>

namespace pkgtest {

enum class FontRole {
    Pixel = 0,   // F77-Minecraft.ttf (switched to Tajawal automatically in Arabic)
    Body = 1,    // Nunito / DejaVuSans-Bold (switched to Tajawal automatically for Arabic script)
    Arabic = 2   // Tajawal-ExtraBold.ttf
};

struct TextureEntry {
    SDL_Texture* tex = nullptr;
    int w = 0;
    int h = 0;
};

class UiEngine {
public:
    UiEngine() = default;
    ~UiEngine();

    bool init(SDL_Renderer* renderer, const std::string& pakPath = "");
    void shutdown();

    SDL_Texture* getTexture(const std::string& name, int* outW = nullptr, int* outH = nullptr);

    void drawImage(const std::string& name, int x, int y, int w, int h, Uint8 alpha = 255);
    void drawImageZoomed(const std::string& name, int x, int y, int w, int h, float zoom, Uint8 alpha = 255);

    void fillRect(int x, int y, int w, int h, SDL_Color c);
    void fillGradH(int x, int y, int w, int h, SDL_Color left, SDL_Color right);
    void fillGradV(int x, int y, int w, int h, SDL_Color top, SDL_Color bottom);

    void drawRoundRect(int x, int y, int w, int h, int radius, SDL_Color c);
    void drawRoundGradH(int x, int y, int w, int h, int radius, SDL_Color left, SDL_Color right);
    void drawRoundBorder(int x, int y, int w, int h, int radius, int thickness, SDL_Color c);
    void drawFocusRing(int x, int y, int w, int h, int radius);
    void drawCircle(int cx, int cy, int radius, SDL_Color c);

    int measureText(const std::string& utf8, int pxSize, FontRole role, bool isArabicLang = false);
    int drawText(const std::string& utf8,
                 int x,
                 int y,
                 int pxSize,
                 SDL_Color color,
                 FontRole role = FontRole::Body,
                 bool alignRight = false,
                 bool isArabicLang = false,
                 int maxWidth = 0);
    int drawTextCentered(const std::string& utf8,
                         int cx,
                         int cy,
                         int pxSize,
                         SDL_Color color,
                         FontRole role = FontRole::Body,
                         bool isArabicLang = false);

private:
    struct FontData;
    struct CachedText {
        SDL_Texture* tex = nullptr;
        int w = 0;
        int h = 0;
        std::uint64_t lastUsed = 0;
    };

    const CachedText& getOrBuildText(const std::string& utf8,
                                     int pxSize,
                                     FontRole role,
                                     bool isArabicLang);

    SDL_Renderer* renderer_ = nullptr;
    FontData* fonts_ = nullptr;
    std::unordered_map<std::string, TextureEntry> images_;
    std::unordered_map<std::string, CachedText> textCache_;
    std::uint64_t tickCounter_ = 0;
};

} // namespace pkgtest

#endif
