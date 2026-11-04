// Minimal SDL2 software-rendering stub for host preview builds only.
// Implements just the API surface used by ui_engine.cpp / screens.cpp / ps4_design_preview.cpp.
// PS4 builds use the real OpenOrbis SDL2 headers/libs instead.
#pragma once

#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <cmath>
#include <algorithm>

typedef unsigned char Uint8;
typedef unsigned int Uint32;
typedef unsigned short Uint16;

typedef struct SDL_Color { Uint8 r, g, b, a; } SDL_Color;
typedef struct SDL_Rect { int x, y, w, h; } SDL_Rect;

typedef struct SDL_PixelFormat {
    Uint32 format;
    Uint32 Rmask, Gmask, Bmask, Amask;
    Uint8 BytesPerPixel;
} SDL_PixelFormat;

typedef struct SDL_Surface {
    int w, h, pitch;
    void* pixels;
    int mustlock;
    SDL_PixelFormat* format;
} SDL_Surface;

typedef struct SDL_Texture {
    int w, h, pitch;
    Uint8* pixels;
    int blendmode;
    Uint8 rmod, gmod, bmod, amod;
} SDL_Texture;

typedef enum SDL_BlendMode { SDL_BLENDMODE_NONE = 0, SDL_BLENDMODE_BLEND = 1 } SDL_BlendMode;

typedef struct SDL_Renderer {
    SDL_Surface* target;
    Uint8 r, g, b, a;
    int blendmode;
} SDL_Renderer;

#define SDL_INIT_VIDEO 0x00000020u
#define SDL_PIXELFORMAT_RGBA32 0x16362004u
#define SDL_MUSTLOCK(S) (0)
#define SDL_WINDOWPOS_CENTERED 0

static inline const char* SDL_GetError(void) { return "host sdl stub"; }
// v0.29: the v7.4 UI animations are time-based (focus glow, icon motion)
#include <chrono>
static inline Uint32 SDL_GetTicks(void) {
    using namespace std::chrono;
    static const steady_clock::time_point t0 = steady_clock::now();
    return static_cast<Uint32>(duration_cast<milliseconds>(steady_clock::now() - t0).count());
}
static inline void SDL_Delay(Uint32 ms) { (void)ms; }
static inline int SDL_Init(Uint32) { return 0; }
static inline void SDL_Quit(void) {}

static inline SDL_PixelFormat* stubDefaultFormat() {
    static SDL_PixelFormat fmt{0x16362004u, 0x00FF0000u, 0x0000FF00u, 0x000000FFu, 0xFF000000u, 4};
    return &fmt;
}

static inline SDL_Surface* SDL_CreateRGBSurfaceWithFormat(Uint32, int w, int h, int, Uint32) {
    SDL_Surface* s = (SDL_Surface*)std::calloc(1, sizeof(SDL_Surface));
    s->w = w;
    s->h = h;
    s->pitch = w * 4;
    s->pixels = std::calloc((size_t)w * h, 4);
    s->format = stubDefaultFormat();
    return s;
}

static inline SDL_Surface* SDL_CreateRGBSurfaceWithFormatFrom(void* pixels, int w, int h, int, int pitch, Uint32) {
    SDL_Surface* s = (SDL_Surface*)std::calloc(1, sizeof(SDL_Surface));
    s->w = w;
    s->h = h;
    s->pitch = w * 4;
    s->format = stubDefaultFormat();
    s->pixels = std::malloc((size_t)w * h * 4);
    const Uint8* src = (const Uint8*)pixels;
    Uint8* dst = (Uint8*)s->pixels;
    for (int y = 0; y < h; ++y) {
        std::memcpy(dst + (size_t)y * w * 4, src + (size_t)y * pitch, (size_t)w * 4);
    }
    return s;
}

static inline void SDL_FreeSurface(SDL_Surface* s) {
    if (!s) return;
    std::free(s->pixels);
    std::free(s);
}

static inline int SDL_LockSurface(SDL_Surface*) { return 0; }
static inline void SDL_UnlockSurface(SDL_Surface*) {}

static inline SDL_Texture* SDL_CreateTextureFromSurface(SDL_Renderer*, SDL_Surface* surf) {
    if (!surf) return nullptr;
    SDL_Texture* t = (SDL_Texture*)std::calloc(1, sizeof(SDL_Texture));
    t->w = surf->w;
    t->h = surf->h;
    t->pitch = surf->w * 4;
    t->pixels = (Uint8*)std::malloc((size_t)surf->w * surf->h * 4);
    std::memcpy(t->pixels, surf->pixels, (size_t)surf->w * surf->h * 4);
    t->rmod = t->gmod = t->bmod = t->amod = 255;
    t->blendmode = SDL_BLENDMODE_BLEND;
    return t;
}

static inline void SDL_DestroyTexture(SDL_Texture* t) {
    if (!t) return;
    std::free(t->pixels);
    std::free(t);
}

static inline int SDL_SetTextureBlendMode(SDL_Texture* t, SDL_BlendMode m) {
    if (t) t->blendmode = (int)m;
    return 0;
}
static inline int SDL_SetTextureColorMod(SDL_Texture* t, Uint8 r, Uint8 g, Uint8 b) {
    if (t) { t->rmod = r; t->gmod = g; t->bmod = b; }
    return 0;
}
static inline int SDL_SetTextureAlphaMod(SDL_Texture* t, Uint8 a) {
    if (t) t->amod = a;
    return 0;
}
static inline int SDL_SetRenderDrawBlendMode(SDL_Renderer* ren, SDL_BlendMode m) {
    if (ren) ren->blendmode = (int)m;
    return 0;
}
static inline int SDL_SetRenderDrawColor(SDL_Renderer* ren, Uint8 r, Uint8 g, Uint8 b, Uint8 a) {
    if (ren) { ren->r = r; ren->g = g; ren->b = b; ren->a = a; }
    return 0;
}

static inline void stubBlendPx(SDL_Renderer* ren, int x, int y, Uint8 sr, Uint8 sg, Uint8 sb, Uint8 sa) {
    if (!ren || !ren->target) return;
    if (x < 0 || y < 0 || x >= ren->target->w || y >= ren->target->h) return;
    Uint8* px = (Uint8*)ren->target->pixels + ((size_t)y * ren->target->w + x) * 4;
    if (ren->blendmode == SDL_BLENDMODE_BLEND && sa < 255) {
        const int ia = 255 - sa;
        px[0] = (Uint8)((sr * sa + px[0] * ia) / 255);
        px[1] = (Uint8)((sg * sa + px[1] * ia) / 255);
        px[2] = (Uint8)((sb * sa + px[2] * ia) / 255);
        px[3] = (Uint8)std::min(255, (int)sa + px[3] * ia / 255);
    } else {
        px[0] = sr;
        px[1] = sg;
        px[2] = sb;
        px[3] = sa;
    }
}

static inline int SDL_RenderFillRect(SDL_Renderer* ren, const SDL_Rect* r) {
    if (!ren || !ren->target || !r) return -1;
    const int x0 = std::max(0, r->x);
    const int y0 = std::max(0, r->y);
    const int x1 = std::min(ren->target->w, r->x + r->w);
    const int y1 = std::min(ren->target->h, r->y + r->h);
    for (int y = y0; y < y1; ++y) {
        for (int x = x0; x < x1; ++x) {
            stubBlendPx(ren, x, y, ren->r, ren->g, ren->b, ren->a);
        }
    }
    return 0;
}

static inline int SDL_RenderDrawRect(SDL_Renderer* ren, const SDL_Rect* r) {
    if (!ren || !r) return -1;
    SDL_Rect top{r->x, r->y, r->w, 1};
    SDL_Rect bot{r->x, r->y + r->h - 1, r->w, 1};
    SDL_Rect left{r->x, r->y, 1, r->h};
    SDL_Rect right{r->x + r->w - 1, r->y, 1, r->h};
    SDL_RenderFillRect(ren, &top);
    SDL_RenderFillRect(ren, &bot);
    SDL_RenderFillRect(ren, &left);
    SDL_RenderFillRect(ren, &right);
    return 0;
}

static inline int SDL_RenderDrawPoint(SDL_Renderer* ren, int x, int y) {
    if (!ren) return -1;
    stubBlendPx(ren, x, y, ren->r, ren->g, ren->b, ren->a);
    return 0;
}

static inline int SDL_RenderCopy(SDL_Renderer* ren, SDL_Texture* tex, const SDL_Rect* srcR, const SDL_Rect* dstR) {
    if (!ren || !ren->target || !tex || !tex->pixels) return -1;
    SDL_Rect src{0, 0, tex->w, tex->h};
    if (srcR) src = *srcR;
    SDL_Rect dst{0, 0, ren->target->w, ren->target->h};
    if (dstR) dst = *dstR;
    if (src.w <= 0 || src.h <= 0 || dst.w <= 0 || dst.h <= 0) return 0;

    for (int dy = 0; dy < dst.h; ++dy) {
        const float sy = ((dy + 0.5f) / dst.h) * src.h - 0.5f;
        const int y0 = std::max(0, std::min(src.h - 1, (int)std::floor(sy)));
        const int y1 = std::min(src.h - 1, y0 + 1);
        const float fy = std::max(0.0f, std::min(1.0f, sy - y0));
        for (int dx = 0; dx < dst.w; ++dx) {
            const float sx = ((dx + 0.5f) / dst.w) * src.w - 0.5f;
            const int x0 = std::max(0, std::min(src.w - 1, (int)std::floor(sx)));
            const int x1 = std::min(src.w - 1, x0 + 1);
            const float fx = std::max(0.0f, std::min(1.0f, sx - x0));

            const Uint8* p00 = tex->pixels + ((size_t)(src.y + y0) * tex->w + (src.x + x0)) * 4;
            const Uint8* p10 = tex->pixels + ((size_t)(src.y + y0) * tex->w + (src.x + x1)) * 4;
            const Uint8* p01 = tex->pixels + ((size_t)(src.y + y1) * tex->w + (src.x + x0)) * 4;
            const Uint8* p11 = tex->pixels + ((size_t)(src.y + y1) * tex->w + (src.x + x1)) * 4;

            Uint8 sample[4];
            for (int c = 0; c < 4; ++c) {
                const float top = p00[c] * (1.0f - fx) + p10[c] * fx;
                const float bot = p01[c] * (1.0f - fx) + p11[c] * fx;
                sample[c] = (Uint8)std::min(255.0f, std::max(0.0f, top * (1.0f - fy) + bot * fy));
            }
            const Uint8 sr = (Uint8)((sample[0] * tex->rmod) / 255);
            const Uint8 sg = (Uint8)((sample[1] * tex->gmod) / 255);
            const Uint8 sb = (Uint8)((sample[2] * tex->bmod) / 255);
            const Uint8 sa = (Uint8)((sample[3] * tex->amod) / 255);
            if (sa == 0) continue;
            stubBlendPx(ren, dst.x + dx, dst.y + dy, sr, sg, sb, sa);
        }
    }
    return 0;
}

static inline SDL_Renderer* SDL_CreateSoftwareRenderer(SDL_Surface* s) {
    if (!s) return nullptr;
    SDL_Renderer* ren = (SDL_Renderer*)std::calloc(1, sizeof(SDL_Renderer));
    ren->target = s;
    ren->blendmode = SDL_BLENDMODE_BLEND;
    return ren;
}
static inline void SDL_DestroyRenderer(SDL_Renderer* ren) { std::free(ren); }
static inline void SDL_RenderPresent(SDL_Renderer*) {}
