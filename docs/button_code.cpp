// =============================================================================
// أكواد الأزرار في التطبيق — button() styles & color codes  (v0.69)
// المصدر الأصلي: ps4-addon-test/src/screens.cpp (دالة button, سطور 1685-1730)
//
// ⭐ زر "View addon" = النمط 0 (الأساسي — أزرق سوني)
//
// النمط 0 — الأساسي (أزرق سوني):  تدرج #4AA8FF ← #0856BE ، نص أبيض #FFFFFF
//          استعمال: View addon / Download / Get code / تسجيل الدخول
// النمط 1 (else) — الثانوي (نيوترال): غامق #2B3238←#151B21 ، فاتح #FFFFFF←#EEF2F7 ، نص #F3F5F7
//          استعمال: Not now / «لا أملك التطبيق»
// النمط 2 — الذهبي (خاص):           تدرج #FFE282 ← #EE961E ، نص غامق #2C1A02
//          استعمال: Get tier للـ Legendary / «جار التثبيت»
// النمط 3 — أزرق شفاف (حالة):      تدرج rgba(74,168,255,.22←.08) + حد #4AA8FF@90% ، نص #96CDFF
//          استعمال: Installed / Waiting for Minecraft
// النمط 4 — أحمر شفاف (خطر):       تدرج rgba(251,76,110,.21←.07) + حد #FB4C6E@90% ، نص #FF8096
//          استعمال: إجراءات الحذف/الإنهاء
//
// اللمسات المشتركة على كل الأزرار:
//   - لمعة بيضاء (glass) في النصف العلوي
//   - حافة بيضاء 1px (rim) + خط غامق 2px تحت الزر
//   - توهج ملوّن تحت الزر (under) من طبقتين شفافتين بنفس لون الزر
//   - ظل ناعم (dropShadow)
//
// ألوان مساعدة: إطار التركيز #FFD65A ، الذهبي #FFC940 ، الخطأ #E05252 ، نص ثانوي #86A9B5
// =============================================================================

// الألوان المستخدمة داخل الدالة (معرّفة في التطبيق):
//   kTX   = {243, 245, 247, 255}   // النص الأساسي
//   kGRN2 = {150, 205, 255, 255}   // نص النمط 3 (أزرق فاتح)
//   kERT  = {255, 128, 150, 255}   // نص النمط 4 (أحمر فاتح)
//   gLight = هل الثيم فاتح (يضبط شفافيات اللمعة/الحافة/الظل)
//   ink(a) = أبيض بشفافية a (فوق الثيم الغامق) أو أسود بشفافية a (فوق الفاتح)

void button(UiEngine& e, const Lay& L, int xl, int y, int w, int h, const std::string& label,
            int style, int px = 30, int radius = 20) {
    const int rx = L.X(xl, w);
    const int r = std::min(radius, 20);
    // glass highlight (top half) + light rim, shared by the filled styles
    auto glass = [&](Uint8 top) {
        if (w > 2 * r + 8 && h > 16)
            e.drawRoundGradV(rx + 2, y + 2, w - 4, h / 2 - 1, std::max(2, r - 2), SDL_Color{255, 255, 255, top}, SDL_Color{255, 255, 255, 0});
    };
    auto rim = [&](Uint8 a) {
        e.drawRoundBorder(rx, y, w, h, r, 1, SDL_Color{255, 255, 255, a});
        if (w > 2 * r + 8) e.fillRect(rx + r, y + h - 3, w - 2 * r, 2, SDL_Color{0, 0, 0, static_cast<Uint8>(gLight ? 22 : 46)});
    };
    // coloured under-glow (two soft expanded layers, drawn before the button)
    auto under = [&](SDL_Color c, Uint8 a1, Uint8 a2) {
        e.drawRoundRect(rx - 3, y + 3, w + 6, h + 6, r + 4, SDL_Color{c.r, c.g, c.b, a2});
        e.drawRoundRect(rx - 7, y + 6, w + 14, h + 12, r + 8, SDL_Color{c.r, c.g, c.b, a1});
    };
    if (style == 0) {              // ⭐ الأساسي — أزرق سوني (مثل زر View addon)
        e.dropShadow(rx, y + 4, w, h, r, 14, gLight ? 70 : 120);
        under(SDL_Color{74, 168, 255, 255}, gLight ? 16 : 14, gLight ? 26 : 24);   // توهج #4AA8FF
        e.drawRoundGradD(rx, y, w, h, r, SDL_Color{74, 168, 255, 255}, SDL_Color{8, 86, 190, 255});   // #4AA8FF ← #0856BE
        glass(gLight ? 90 : 78); rim(gLight ? 120 : 90);
    } else if (style == 2) {       // الذهبي — خاص / Legendary
        e.dropShadow(rx, y + 4, w, h, r, 14, gLight ? 70 : 110);
        under(SDL_Color{255, 190, 60, 255}, gLight ? 16 : 14, gLight ? 26 : 24);  // توهج #FFBE3C
        e.drawRoundGradD(rx, y, w, h, r, SDL_Color{255, 226, 130, 255}, SDL_Color{238, 150, 30, 255}); // #FFE282 ← #EE961E
        glass(gLight ? 90 : 80); rim(gLight ? 120 : 90);
    } else if (style == 3) {       // أزرق شفاف — حالة (Installed / Waiting)
        e.drawRoundGradV(rx, y, w, h, r, SDL_Color{74, 168, 255, 56}, SDL_Color{74, 168, 255, 20});
        e.drawRoundBorder(rx, y, w, h, r, 2, SDL_Color{74, 168, 255, 230});
        glass(gLight ? 40 : 22);
    } else if (style == 4) {       // أحمر شفاف — خطر (حذف/إنهاء)
        e.drawRoundGradV(rx, y, w, h, r, SDL_Color{251, 76, 110, 54}, SDL_Color{251, 76, 110, 18});
        e.drawRoundBorder(rx, y, w, h, r, 2, SDL_Color{251, 76, 110, 230});
        glass(gLight ? 40 : 22);
    } else {                       // النمط 1 — الثانوي (نيوترال)
        e.dropShadow(rx, y + 3, w, h, r, 10, gLight ? 36 : 70);
        if (gLight) e.drawRoundGradV(rx, y, w, h, r, SDL_Color{255, 255, 255, 255}, SDL_Color{238, 242, 247, 255});
        else        e.drawRoundGradV(rx, y, w, h, r, ink(40), ink(14));
        e.drawRoundBorder(rx, y, w, h, r, 2, ink(gLight ? 44 : 72));
        glass(gLight ? 60 : 30);
    }
    // لون النص حسب النمط: 0 أبيض | 2 غامق #2C1A02 | 3 #96CDFF | 4 #FF8096 | 1 #F3F5F7
    const SDL_Color ink = style == 0 ? SDL_Color{255, 255, 255, 255} : style == 2 ? SDL_Color{44, 26, 2, 255} : (style == 3 ? kGRN2 : (style == 4 ? kERT : kTX));
    e.drawTextCentered(label, rx + w / 2, y + h / 2, px, ink, FontRole::Body, L.rtl);
}

// =============================================================================
// أمثلة استعمال من التطبيق:
//   زر View addon في واجهة المتجر (النمط 0):
//       const std::string vt = tr("view_addon", lang);
//       const int vw = textW(e, L, vt, 22) + 70;
//       button(e, L, x, y, vw, 60, vt, 0, 22, 18);
//
//   زر التحميل الرئيسي في صفحة الإضافة (النمط 0):
//       button(e, L, bx, yy, bw, bh, tr("download", lang), 0, 28, 20);
//
//   زر «جار التثبيت» (النمط 2 — الذهبي):
//       button(e, L, bx, yy, bw, bh, tr("installing", lang), 2, 28, 20);
//
//   حالة Installed (النمط 3 — أزرق شفاف):
//       button(e, L, bx, yy, bw, bh, tr("installed", lang), 3, 28, 20);
// =============================================================================
