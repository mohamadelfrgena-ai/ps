// ui_audio.cpp — AddonStation HUD sound effects (v7.2+)
// Design goals: CHEAP. Load once at boot → play from RAM; background thread for
// PS4 (SceAudioOut) or SDL2 on host; mixing = 4 mono voices of PCM16 @ 48kHz.
#include "ui_audio.hpp"
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <vector>
#include <thread>
#include <mutex>
#include <atomic>

#if defined(__ORBIS__) || defined(__PROSPERO__)
#  define PKGTEST_AUDIO_HDR 0
#  if defined(__has_include)
#    if __has_include(<orbis/AudioOut.h>)
#      include <orbis/AudioOut.h>
#      undef PKGTEST_AUDIO_HDR
#      define PKGTEST_AUDIO_HDR 1
#    elif __has_include(<orbis/audioout.h>)
#      include <orbis/audioout.h>
#      undef PKGTEST_AUDIO_HDR
#      define PKGTEST_AUDIO_HDR 1
#    elif __has_include(<audioout.h>)
#      include <audioout.h>
#      undef PKGTEST_AUDIO_HDR
#      define PKGTEST_AUDIO_HDR 1
#    elif __has_include(<audio_out.h>)
#      include <audio_out.h>
#      undef PKGTEST_AUDIO_HDR
#      define PKGTEST_AUDIO_HDR 1
#    endif
#    if __has_include(<orbis/UserService.h>)
#      include <orbis/UserService.h>
#    endif
#  endif
// Fallbacks if the toolchain headers are missing these (audio then fails soft: silent UI)
#  ifndef SCE_AUDIO_OUT_PORT_TYPE_MAIN
#    define SCE_AUDIO_OUT_PORT_TYPE_MAIN 0
#  endif
#  ifndef SCE_AUDIO_OUT_PARAM_FORMAT_S16_STEREO
#    define SCE_AUDIO_OUT_PARAM_FORMAT_S16_STEREO 0
#  endif
#  ifndef SCE_USER_SERVICE_USER_ID_INTERACTIVE
#    define SCE_USER_SERVICE_USER_ID_INTERACTIVE 1
#  endif
#  if !PKGTEST_AUDIO_HDR
extern "C" {
int32_t sceAudioOutOpen(int32_t userId, int32_t type, int32_t index, int32_t len, int32_t freq, int32_t format);
int32_t sceAudioOutOutput(int32_t handle, const void* data);
int32_t sceAudioOutClose(int32_t handle);
}
#  endif
#endif

namespace {
constexpr int kRate = 48000;
constexpr int kVoices = 4;             // concurrent SFX slots
constexpr int kBlockMs = 10;           // audio block = 480 samples

struct Clip { std::vector<int16_t> pcm; };

std::vector<Clip> gClips;
bool gOk = false;
std::atomic<bool> gRun{false};
std::thread gThread;
std::atomic<int> gVol{80};

struct Voice { const Clip* clip = nullptr; std::size_t pos = 0; };
Voice gVoices[kVoices];
std::mutex gMix;

// ---------------------------------------------------------------- WAV loader
// Minimal RIFF/WAVE PCM reader: mono, 16-bit, 48kHz (the exported SFX format).
bool loadWav(const char* path, Clip& out) {
    FILE* f = std::fopen(path, "rb");
    if (!f) return false;
    unsigned char hdr[12];
    if (std::fread(hdr, 1, 12, f) != 12 || std::memcmp(hdr, "RIFF", 4) || std::memcmp(hdr + 8, "WAVE", 4)) {
        std::fclose(f); return false;
    }
    int ch = 0, rate = 0, bits = 0;
    bool gotData = false;
    while (!std::feof(f)) {
        char id[4]; unsigned int sz = 0;
        if (std::fread(id, 1, 4, f) != 4) break;
        if (std::fread(&sz, 1, 4, f) != 4) break;
        if (!std::memcmp(id, "fmt ", 4)) {
            unsigned char fmt[40]; unsigned int n = sz < sizeof(fmt) ? sz : sizeof(fmt);
            if (std::fread(fmt, 1, n, f) != n) { std::fclose(f); return false; }
            const unsigned short audioFmt = fmt[0] | (fmt[1] << 8);
            ch = fmt[2] | (fmt[3] << 8);
            rate = fmt[4] | (fmt[5] << 8) | (fmt[6] << 16) | (fmt[7] << 24);
            bits = fmt[14] | (fmt[15] << 8);
            if (audioFmt != 1) { std::fclose(f); return false; }   // PCM only
            if (sz > n) std::fseek(f, (long)(sz - n), SEEK_CUR);
        } else if (!std::memcmp(id, "data", 4)) {
            if (ch != 1 || bits != 16 || rate != kRate) { std::fclose(f); return false; }
            out.pcm.resize(sz / 2);
            if (std::fread(out.pcm.data(), 2, sz / 2, f) != sz / 2) { std::fclose(f); return false; }
            gotData = true;
            break;
        } else {
            std::fseek(f, (long)sz, SEEK_CUR);
        }
        if (sz & 1u) std::fseek(f, 1, SEEK_CUR);
    }
    std::fclose(f);
    return gotData;
}

const char* clipFile(Sfx s) {
    switch (s) {
        case Sfx::Focus:        return "focus.wav";
        case Sfx::Tab:          return "tab.wav";
        case Sfx::Press:        return "press.wav";
        case Sfx::Close:        return "close.wav";
        case Sfx::Toggle:       return "toggle.wav";
        case Sfx::Error:        return "error.wav";
        case Sfx::AppStart:     return "app_start.wav";
        case Sfx::Success:      return "success.wav";
        case Sfx::Purchase:     return "purchase.wav";
        case Sfx::DownloadDone: return "download_done.wav";
        case Sfx::Notify:       return "notify.wav";
        case Sfx::_count:       return "";
    }
    return "";
}

// ------------------------------------------------------------ mixing
void mixBlock(int16_t* dst, int frames) {
    // mono voices -> interleaved L/R output
    int acc[512];
    for (int i = 0; i < frames; ++i) acc[i] = 0;
    {
        std::lock_guard<std::mutex> lk(gMix);
        const int vol = gVol.load();
        for (int v = 0; v < kVoices; ++v) {
            Voice& vo = gVoices[v];
            if (!vo.clip) continue;
            const int16_t* d = vo.clip->pcm.data();
            const std::size_t n = vo.clip->pcm.size();
            for (int i = 0; i < frames && vo.pos < n; ++i, ++vo.pos)
                acc[i] += (int(d[vo.pos]) * vol) / 100;
            if (vo.pos >= n) vo.clip = nullptr;   // finished
        }
    }
    for (int i = 0; i < frames; ++i) {
        int s = acc[i];
        if (s > 32767) s = 32767; else if (s < -32768) s = -32768;
        dst[2 * i] = dst[2 * i + 1] = static_cast<int16_t>(s);
    }
}
#if defined(__ORBIS__) || defined(__PROSPERO__)
void audioThreadOrbis() {
    const int frames = kRate * kBlockMs / 1000;      // 480
    const int grain = 256;
    int32_t hnd = sceAudioOutOpen(SCE_USER_SERVICE_USER_ID_INTERACTIVE,
                                  SCE_AUDIO_OUT_PORT_TYPE_MAIN, 0,
                                  frames, kRate, SCE_AUDIO_OUT_PARAM_FORMAT_S16_STEREO);
    if (hnd < 0) return;
    std::vector<int16_t> buf(frames * 2);
    while (gRun) {
        for (int off = 0; off < frames; off += grain)
            mixBlock(&buf[off * 2], grain);
        if (sceAudioOutOutput(hnd, buf.data()) < 0) break;
    }
    sceAudioOutClose(hnd);
}
#endif

#if !(defined(__ORBIS__) || defined(__PROSPERO__))
// Host fallback (dev machine preview): SDL2 audio.
#include <SDL2/SDL.h>
static Uint32 gDev = 0;
void sdlAudioCb(void*, Uint8* stream, int len) {
    mixBlock(reinterpret_cast<int16_t*>(stream), len / 4);
}
#endif
} // namespace

bool ui_audio_init(const char* dir) {
    static const int kN = static_cast<int>(Sfx::_count);
    gClips.resize(kN);
    char path[512];
    for (int i = 0; i < kN; ++i) {
        std::snprintf(path, sizeof(path), "%s/%s", dir, clipFile(static_cast<Sfx>(i)));
        if (!loadWav(path, gClips[i])) { gOk = false; return false; }   // missing file = silent mode
    }
    gRun = true;
#if defined(__ORBIS__) || defined(__PROSPERO__)
    gThread = std::thread(audioThreadOrbis);
#else
    if (SDL_WasInit(SDL_INIT_AUDIO) || SDL_InitSubSystem(SDL_INIT_AUDIO) == 0) {
        SDL_AudioSpec want{};
        want.freq = kRate; want.format = AUDIO_S16SYS; want.channels = 2;
        want.samples = 1024; want.callback = sdlAudioCb;
        gDev = SDL_OpenAudioDevice(nullptr, 0, &want, nullptr, 0);
        if (gDev) SDL_PauseAudioDevice(gDev, 0);
    }
#endif
    gOk = true;
    return true;
}

void ui_audio_shutdown() {
    if (!gOk) return;
#if !(defined(__ORBIS__) || defined(__PROSPERO__))
    if (gDev) { SDL_CloseAudioDevice(gDev); gDev = 0; }
#endif
    gRun = false;
    if (gThread.joinable()) gThread.join();
    gOk = false;
}

void ui_sfx(Sfx id) {
    if (!gOk) return;
    const Clip& c = gClips[static_cast<int>(id)];
    if (c.pcm.empty()) return;
    std::lock_guard<std::mutex> lk(gMix);
    for (int v = 0; v < kVoices; ++v)          // steal the first free voice …
        if (!gVoices[v].clip) { gVoices[v].clip = &c; gVoices[v].pos = 0; return; }
    gVoices[0].clip = &c; gVoices[0].pos = 0;  // … or replace the oldest.
}

void ui_sfx_volume(int v01) {
    if (v01 < 0) v01 = 0; if (v01 > 100) v01 = 100;
    gVol = v01;
}
