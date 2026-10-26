// audio_bank.hpp — v0.29: HUD sounds embedded in the eboot (generated at build time
// by tools/gen_audio_bank.py from assets/sfx/*.wav). Embedding removes every file-path
// and packaging dependency — the PS4 audio path is now self-contained.
#pragma once

namespace pkgtest {

struct AudioClipBankEntry {
    const char* name;            // clip name without extension ("focus", "app_start", ...)
    const unsigned char* wav;    // complete RIFF/WAVE file bytes (PCM16 mono 48 kHz)
    unsigned int wavLen;
};

extern const AudioClipBankEntry kAudioBank[];
extern const int kAudioBankCount;

} // namespace pkgtest
