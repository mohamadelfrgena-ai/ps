#include "screens.hpp"

#include <cstdio>

#include <algorithm>
#include <cmath>
#include <cstdio>
#include <map>

namespace pkgtest {
namespace {

const std::map<std::string, std::map<std::string, std::string>>& stringTable() {
    static const std::map<std::string, std::map<std::string, std::string>> kTable = {
        {"dream_it", {{"en", "DREAM IT."}, {"ar", "احلم بها."}, {"fr", "RÊVEZ-EN."},
                      {"tr", "HAYAL ET."}, {"es", "IMAGÍNALO."}, {"ru", "МЕЧТАЙ."},
                      {"pt", "SONHE COM ELA."}, {"zh", "敢想 ·"}}},
        {"build_it", {{"en", "BUILD IT."}, {"ar", "ابنِها."}, {"fr", "CONSTRUISEZ-LE."},
                      {"tr", "İNŞA ET."}, {"es", "CONSTRÚYELO."}, {"ru", "СТРОЙ."},
                      {"pt", "CONSTRUA."}, {"zh", "敢做 ·"}}},
        {"create_it", {{"en", "CREATE IT."}, {"ar", "أبدعها."}, {"fr", "CRÉEZ-LE."},
                       {"tr", "YARAT."}, {"es", "CRÉALO."}, {"ru", "ТВОРИ."},
                       {"pt", "CRIE."}, {"zh", "敢创 ·"}}},
        {"tagline", {{"en", "Minecraft addons & worlds store"}, {"ar", "متجر إضافات وعوالم ماينكرافت"},
                     {"fr", "Boutique d'addons et de mondes Minecraft"}, {"tr", "Minecraft eklenti ve dünyaları mağazası"},
                     {"es", "Tienda de addons y mundos de Minecraft"}, {"ru", "Магазин аддонов и миров Minecraft"},
                     {"pt", "Loja de addons e mundos de Minecraft"}, {"zh", "我的世界模组与世界商店"}}},
        {"skip", {{"en", "Skip"}, {"ar", "تخطي"}, {"fr", "Passer"}, {"tr", "Atla"},
                  {"es", "Saltar"}, {"ru", "Пропустить"}, {"pt", "Pular"}, {"zh", "跳过"}}},
        {"welcome", {{"en", "WELCOME"}, {"ar", "أهلاً بك"}, {"fr", "BIENVENUE"}, {"tr", "HOŞ GELDİN"},
                     {"es", "BIENVENIDO"}, {"ru", "ДОБРО ПОЖАЛОВАТЬ"}, {"pt", "BEM-VINDO"}, {"zh", "欢迎"}}},
        {"welcome_family", {{"en", "Welcome to the family"}, {"ar", "أهلاً بك في العائلة"},
                            {"fr", "Bienvenue dans la famille"}, {"tr", "Ailemize hoş geldin"},
                            {"es", "Bienvenido a la familia"}, {"ru", "Добро пожаловать в семью"},
                            {"pt", "Bem-vindo à família"}, {"zh", "欢迎加入大家庭"}}},
        {"welcome_sub", {{"en", "Download PS4/PS5 addons and worlds, join the community and enter the royal family"},
                         {"ar", "حمّل إضافات وعوالم PS4/PS5، شارك في المجتمع، وانضم إلى العائلة الملكية"},
                         {"fr", "Téléchargez des addons et mondes PS4/PS5, rejoignez la communauté"},
                         {"tr", "PS4/PS5 eklenti ve dünyalarını indir, topluluğa katıl"},
                         {"es", "Descarga addons y mundos PS4/PS5, únete a la comunidad"},
                         {"ru", "Скачивайте аддоны и миры PS4/PS5, присоединяйтесь к сообществу"},
                         {"pt", "Baixe addons e mundos PS4/PS5, junte-se à comunidade"},
                         {"zh", "下载 PS4/PS5 模组与世界，加入社区"}}},
        {"sign_in_code", {{"en", "Sign in with a code"}, {"ar", "الدخول برمز"}, {"fr", "Se connecter avec un code"},
                          {"tr", "Kod ile giriş yap"}, {"es", "Iniciar sesión con un código"},
                          {"ru", "Войти по коду"}, {"pt", "Entrar com um código"}, {"zh", "使用代码登录"}}},
        {"sign_in_code_sub", {{"en", "Type nothing: link with your phone or PC"},
                              {"ar", "بدون كتابة: اربط عبر جوالك أو الكمبيوتر"},
                              {"fr", "Sans écrire : liez via votre téléphone ou PC"},
                              {"tr", "Yazmadan: telefonun veya bilgisayarınla bağla"},
                              {"es", "Sin escribir: vincula con tu móvil o PC"},
                              {"ru", "Без ввода: свяжите с телефоном или ПК"},
                              {"pt", "Sem digitar: vincule pelo celular ou PC"},
                              {"zh", "无需输入：用手机或电脑关联"}}},
        {"sign_in", {{"en", "Sign in"}, {"ar", "تسجيل الدخول"}, {"fr", "Se connecter"}, {"tr", "Giriş yap"},
                     {"es", "Iniciar sesión"}, {"ru", "Вход"}, {"pt", "Entrar"}, {"zh", "登录"}}},
        {"sign_in_sub", {{"en", "Use your email and password"}, {"ar", "استخدم بريدك وكلمة المرور"},
                         {"fr", "Utilisez votre e-mail et mot de passe"}, {"tr", "E-posta ve şifreni kullan"},
                         {"es", "Usa tu correo y contraseña"}, {"ru", "Используйте почту и пароль"},
                         {"pt", "Use seu e-mail e senha"}, {"zh", "使用邮箱和密码"}}},
        {"guest", {{"en", "Browse as a guest"}, {"ar", "التصفح كزائر"}, {"fr", "Naviguer en invité"},
                   {"tr", "Misafir olarak gez"}, {"es", "Explorar como invitado"}, {"ru", "Гость"},
                   {"pt", "Navegar como visitante"}, {"zh", "游客浏览"}}},
        {"language", {{"en", "Language"}, {"ar", "اللغة"}, {"fr", "Langue"}, {"tr", "Dil"},
                      {"es", "Idioma"}, {"ru", "Язык"}, {"pt", "Idioma"}, {"zh", "语言"}}},
        {"select", {{"en", "Select"}, {"ar", "اختيار"}, {"fr", "Choisir"}, {"tr", "Seç"},
                    {"es", "Elegir"}, {"ru", "Выбрать"}, {"pt", "Selecionar"}, {"zh", "选择"}}},
        {"back", {{"en", "Back"}, {"ar", "رجوع"}, {"fr", "Retour"}, {"tr", "Geri"},
                  {"es", "Volver"}, {"ru", "Назад"}, {"pt", "Voltar"}, {"zh", "返回"}}},
        {"help", {{"en", "Help"}, {"ar", "مساعدة"}, {"fr", "Aide"}, {"tr", "Yardım"},
                  {"es", "Ayuda"}, {"ru", "Помощь"}, {"pt", "Ajuda"}, {"zh", "帮助"}}},
        {"terms", {{"en", "By creating an account you agree to the community rules and the terms set by AddonStation."},
                   {"ar", "من خلال إنشاء حساب، توافق على قواعد المجتمع والشروط التي تحددها AddonStation."},
                   {"fr", "En créant un compte, vous acceptez les règles de la communauté."},
                   {"tr", "Hesap oluşturarak topluluk kurallarını kabul edersin."},
                   {"es", "Al crear una cuenta aceptas las normas de la comunidad."},
                   {"ru", "Создавая аккаунт, вы принимаете правила сообщества."},
                   {"pt", "Ao criar uma conta você aceita as regras da comunidade."},
                   {"zh", "创建账户即表示您同意社区规则与条款。"}}},
        {"open", {{"en", "Open"}, {"ar", "فتح"}, {"fr", "Ouvrir"}, {"tr", "Aç"},
                  {"es", "Abrir"}, {"ru", "Открыть"}, {"pt", "Abrir"}, {"zh", "打开"}}},
        {"cancel", {{"en", "Cancel"}, {"ar", "إلغاء"}, {"fr", "Annuler"}, {"tr", "İptal"},
                    {"es", "Cancelar"}, {"ru", "Отмена"}, {"pt", "Cancelar"}, {"zh", "取消"}}},
        {"download", {{"en", "Download"}, {"ar", "تنزيل"}, {"fr", "Télécharger"}, {"tr", "İndir"},
                      {"es", "Descargar"}, {"ru", "Скачать"}, {"pt", "Baixar"}, {"zh", "下载"}}},
        {"downloading", {{"en", "Downloading..."}, {"ar", "جارٍ التنزيل..."}, {"fr", "Téléchargement..."},
                         {"tr", "İndiriliyor..."}, {"es", "Descargando..."}, {"ru", "Загрузка..."},
                         {"pt", "Baixando..."}, {"zh", "下载中..."}}},
        {"installing", {{"en", "Installing..."}, {"ar", "جارٍ التثبيت..."}, {"fr", "Installation..."},
                        {"tr", "Yükleniyor..."}, {"es", "Instalando..."}, {"ru", "Установка..."},
                        {"pt", "Instalando..."}, {"zh", "安装中..."}}},
        {"installed", {{"en", "Installed ✓"}, {"ar", "تم التثبيت ✓"}, {"fr", "Installé ✓"},
                       {"tr", "Yüklendi ✓"}, {"es", "Instalado ✓"}, {"ru", "Установлено ✓"},
                       {"pt", "Instalado ✓"}, {"zh", "已安装 ✓"}}},
        {"enjoy", {{"en", "Enjoy!"}, {"ar", "استمتع!"}, {"fr", "Profitez-en !"},
                   {"tr", "Keyfini çıkarın!"}, {"es", "¡Disfruta!"}, {"ru", "Приятной игры!"},
                   {"pt", "Aproveite!"}, {"zh", "尽情畅玩吧！"}}},
        {"install_demo_title", {{"en", "Install [Addon name]"}, {"ar", "تثبيت [اسم الإضافة]"},
                                {"fr", "Installer [nom de l'addon]"}, {"tr", "[Eklenti adını] kur"},
                                {"es", "Instalar [nombre del addon]"}, {"ru", "Установить [аддон]"},
                                {"pt", "Instalar [nome do addon]"}, {"zh", "安装 [模组名称]"}}},
        {"install_demo_sub", {{"en", "Dynamic PS4 addon & world installer — auto-detects CUSAxxxxx and userXXXXXXXXX"},
                              {"ar", "مثبّت إضافات وعوالم ذكي — يكتشف CUSAxxxxx وuserXXXXXXXXX تلقائياً"},
                              {"fr", "Installateur intelligent PS4 — détecte CUSAxxxxx et userXXXXXXXXX"},
                              {"tr", "Akıllı PS4 kurulumu — CUSAxxxxx ve userXXXXXXXXX algılar"},
                              {"es", "Instalador inteligente PS4 — detecta CUSAxxxxx y userXXXXXXXXX"},
                              {"ru", "Умный установщик PS4 — находит CUSAxxxxx и userXXXXXXXXX"},
                                {"pt", "Instalador inteligente PS4 — detecta CUSAxxxxx e userXXXXXXXXX"},
                                {"zh", "智能 PS4 安装器 — 自动识别 CUSAxxxxx 与 userXXXXXXXXX"}}},
        {"nav_home", {{"en", "Home"}, {"ar", "الرئيسية"}, {"fr", "Accueil"}, {"tr", "Ana sayfa"},
                      {"es", "Inicio"}, {"ru", "Главная"}, {"pt", "Início"}, {"zh", "主页"}}},
        {"nav_deals", {{"en", "Deals"}, {"ar", "العروض"}, {"fr", "Offres"}, {"tr", "Fırsatlar"},
                       {"es", "Ofertas"}, {"ru", "Скидки"}, {"pt", "Ofertas"}, {"zh", "特惠"}}},
        {"nav_store", {{"en", "Store"}, {"ar", "المتجر"}, {"fr", "Boutique"}, {"tr", "Mağaza"},
                       {"es", "Tienda"}, {"ru", "Магазин"}, {"pt", "Loja"}, {"zh", "商店"}}},
        {"nav_chat", {{"en", "Chat"}, {"ar", "الشات"}, {"fr", "Chat"}, {"tr", "Sohbet"},
                      {"es", "Chat"}, {"ru", "Чат"}, {"pt", "Chat"}, {"zh", "聊天"}}},
        {"nav_settings", {{"en", "Settings"}, {"ar", "الإعدادات"}, {"fr", "Réglages"}, {"tr", "Ayarlar"},
                          {"es", "Ajustes"}, {"ru", "Настройки"}, {"pt", "Ajustes"}, {"zh", "设置"}}},
        {"choose_language", {{"en", "Choose app language"}, {"ar", "اختر لغة التطبيق"},
                             {"fr", "Choisir la langue"}, {"tr", "Uygulama dilini seç"},
                             {"es", "Elegir idioma"}, {"ru", "Выберите язык"}, {"pt", "Escolher idioma"}, {"zh", "选择语言"}}},
    };
    return kTable;
}

[[maybe_unused]] std::string iso3ForLang(const std::string& langCode) {
    if (langCode == "ar") return "flag_ar";
    if (langCode == "fr") return "flag_fr";
    if (langCode == "tr") return "flag_tr";
    if (langCode == "es") return "flag_es";
    if (langCode == "ru") return "flag_ru";
    if (langCode == "pt") return "flag_pt";
    if (langCode == "zh") return "flag_zh";
    return "flag_en";
}

float easeOutBack(float t) {
    const float c1 = 1.70158f;
    const float c3 = c1 + 1.0f;
    const float x = t - 1.0f;
    return 1.0f + c3 * x * x * x + c1 * x * x;
}

float clamp01(float v) {
    return std::max(0.0f, std::min(1.0f, v));
}

[[maybe_unused]] std::string formatEnjoyNotificationStyle(const std::string& langCode) {
    static const std::map<std::string, std::string> kMap = {
        {"ar", "تم تثبيت [اسم الإضافة] — استمتع!"},
        {"en", "[Addon Name] installed — Enjoy!"},
        {"fr", "[Addon] installé — Profitez-en !"},
        {"tr", "[Eklenti] yüklendi — Keyfini çıkarın!"},
        {"es", "[Addon] instalado — ¡Disfruta!"},
        {"ru", "[Addon] установлен — Приятной игры!"},
        {"pt", "[Addon] instalado — Aproveite!"},
        {"zh", "[模组] 已安装 — 尽情畅玩吧！"},
    };
    auto it = kMap.find(langCode);
    return it != kMap.end() ? it->second : kMap.at("en");
}

} // namespace

std::string tr(const std::string& key, const std::string& langCode) {
    const auto& table = stringTable();
    auto it = table.find(key);
    if (it == table.end()) return key;
    auto lit = it->second.find(langCode);
    if (lit == it->second.end()) {
        auto en = it->second.find("en");
        return en != it->second.end() ? en->second : key;
    }
    return lit->second;
}


// ============================================================================
//  Rendering — metrics copied 1:1 from AddonStation_PS4_complete_design.html
//  (css: .spl / .wtop / .wcol / .wb / .wc / .nav / .itbody / .pb / .toast)
// ============================================================================

namespace {

// design palette (css :root dark)
constexpr SDL_Color kBG{10, 15, 28, 255};       // #0A0F1C
constexpr SDL_Color kSF{18, 26, 43, 255};       // #121A2B
constexpr SDL_Color kBD{34, 48, 73, 255};       // #223049
constexpr SDL_Color kTX{234, 240, 250, 255};    // #EAF0FA
constexpr SDL_Color kMU{157, 176, 207, 255};    // #9DB0CF
constexpr SDL_Color kGD{240, 194, 94, 255};     // #F0C25E
constexpr SDL_Color kBL{46, 123, 239, 255};     // #2E7BEF
constexpr SDL_Color kBL2{76, 154, 255, 255};    // #4C9AFF
constexpr SDL_Color kBLl{111, 168, 255, 255};   // #6FA8FF
constexpr SDL_Color kFOCUS{156, 193, 255, 255}; // #9CC1FF
constexpr SDL_Color kGRN{63, 191, 106, 255};    // #3FBF6A
constexpr SDL_Color kGRN2{127, 217, 154, 255};  // #7FD99A
constexpr SDL_Color kRED{255, 92, 122, 255};    // #FF5C7A
constexpr SDL_Color kPNK{229, 140, 198, 255};   // #E58CC6
constexpr SDL_Color kWHT{255, 255, 255, 255};

SDL_Color withA(SDL_Color c, Uint8 a) { c.a = a; return c; }

// letter-spaced pixel wordmark (design .word letter-spacing:4px)
void drawSpaced(UiEngine& e, const std::string& text, int x, int y, int pxSize,
                int spacing, SDL_Color color, bool center) {
    int total = 0;
    for (std::size_t i = 0; i < text.size(); ++i) {
        total += e.measureText(std::string(1, text[i]), pxSize, FontRole::Pixel, false);
        if (i + 1 < text.size()) total += spacing;
    }
    int cx = center ? x - total / 2 : x;
    for (std::size_t i = 0; i < text.size(); ++i) {
        std::string ch(1, text[i]);
        cx += e.drawText(ch, cx, y, pxSize, color, FontRole::Pixel, false, false);
        if (i + 1 < text.size()) cx += spacing;
    }
}

// .gl gamepad glyph circle
void drawGl(UiEngine& e, int cx, int cy, const std::string& label, SDL_Color color, int size = 44) {
    const int r = size / 2;
    e.drawRoundRect(cx - r, cy - r, size, size, r, SDL_Color{10, 15, 28, 90});
    e.drawRoundBorder(cx - r, cy - r, size, size, r, size >= 44 ? 3 : 2, color);
    e.drawTextCentered(label, cx, cy, size >= 44 ? 22 : 16, color, FontRole::Body, false);
}

// css .focus — outline 5px #9CC1FF offset 4 + blue glow
void focusRing(UiEngine& e, int x, int y, int w, int h, int r) {
    e.drawRoundBorder(x - 24, y - 24, w + 48, h + 48, r + 24, 14, SDL_Color{46, 123, 239, 60});
    e.drawRoundBorder(x - 15, y - 15, w + 30, h + 30, r + 15, 8, SDL_Color{46, 123, 239, 95});
    e.drawRoundBorder(x - 9, y - 9, w + 18, h + 18, r + 9, 5, kFOCUS);
}

void iconPhone(UiEngine& e, int x, int y, int s, SDL_Color c) {
    const int w = s * 46 / 100, h = s * 72 / 100;
    e.drawRoundBorder(x + (s - w) / 2, y + (s - h) / 2, w, h, 8, 3, c);
    e.fillRect(x + s / 2 - 5, y + (s + h) / 2 - 11, 10, 3, c);
}
void iconMail(UiEngine& e, int x, int y, int s, SDL_Color c) {
    const int w = s * 66 / 100, h = s * 48 / 100;
    const int x0 = x + (s - w) / 2, y0 = y + (s - h) / 2;
    e.drawRoundBorder(x0, y0, w, h, 6, 3, c);
    for (int i = 0; i < w / 2 - 2; ++i) {
        e.fillRect(x0 + 3 + i, y0 + 4 + i * (h / 2 - 7) / (w / 2), 3, 3, c);
        e.fillRect(x0 + w - 6 - i, y0 + 4 + i * (h / 2 - 7) / (w / 2), 3, 3, c);
    }
}
void iconGuest(UiEngine& e, int x, int y, int s, SDL_Color c) {
    e.drawCircle(x + s / 2, y + s * 32 / 100, s * 15 / 100, c);
    e.drawRoundRect(x + s * 22 / 100, y + s * 56 / 100, s * 56 / 100, s * 28 / 100, 12, c);
}
void iconDownload(UiEngine& e, int x, int y, int s, SDL_Color c) {
    e.fillRect(x + s / 2 - 3, y + s * 18 / 100, 6, s * 40 / 100, c);
    e.fillRect(x + s * 30 / 100, y + s * 48 / 100, s * 40 / 100, 6, c);
    e.fillRect(x + s * 38 / 100, y + s * 42 / 100, 6, 8, c);
    e.fillRect(x + s * 56 / 100, y + s * 42 / 100, 6, 8, c);
    e.drawRoundBorder(x + s * 22 / 100, y + s * 66 / 100, s * 56 / 100, s * 22 / 100, 4, 3, c);
}

} // namespace

void drawSplash(UiEngine& e, float t, const std::string& langCode) {
    const bool rtl = langCode == "ar";

    // .bgart.a1 (zoom 8s) + .vig + .snowfx
    const float zoom = 1.18f - 0.16f * clamp01(t / 8.0f);
    e.drawImageZoomed("bg_splash", 0, 0, 1920, 1080, zoom);
    e.fillRect(0, 0, 1920, 1080, SDL_Color{5, 10, 24, 92});
    e.fillGradV(0, 0, 1920, 380, SDL_Color{5, 10, 24, 170}, SDL_Color{5, 10, 24, 30});
    e.fillGradV(0, 700, 1920, 380, SDL_Color{5, 10, 24, 30}, SDL_Color{5, 10, 24, 215});
    e.fillGradH(0, 0, 560, 1080, SDL_Color{5, 10, 24, 175}, SDL_Color{5, 10, 24, 20});
    e.fillGradH(1360, 0, 560, 1080, SDL_Color{5, 10, 24, 20}, SDL_Color{5, 10, 24, 175});
    const int snow[8][2] = {{230, 190}, {690, 450}, {1340, 240}, {1650, 650},
                            {1000, 840}, {420, 700}, {1520, 430}, {790, 260}};
    for (const auto& s : snow) {
        e.drawCircle(s[0], s[1], 3, SDL_Color{255, 255, 255, 110});
    }

    // .sicon 150x150 r38 border 5 gold + glow (pop-in)
    const float popT = clamp01((t - 0.35f) / 0.65f);
    const float popScale = 0.86f + 0.14f * easeOutBack(popT);
    const int iconSize = static_cast<int>(150 * popScale);
    const int iconX = 960 - iconSize / 2;
    const int iconY = 175 + (150 - iconSize) / 2;
    if (popT > 0.0f) {
        e.dropShadow(iconX, iconY, iconSize, iconSize, 38, 20, 110);
        e.drawRoundRect(iconX, iconY, iconSize, iconSize, 38, kSF);
        e.drawImageRounded("icon_app", iconX + 6, iconY + 6, iconSize - 12, iconSize - 12, 32);
        e.drawRoundBorder(iconX, iconY, iconSize, iconSize, 38, 5, kGD);
        e.drawRoundBorder(iconX - 7, iconY - 7, iconSize + 14, iconSize + 14, 45, 2, withA(kGD, 110));
    }

    // .word — 64px F77 letter-spacing 4
    drawSpaced(e, "ADDONSTATION", 960, 348, 64, 4, kWHT, true);

    // .slog — 96px F77: l1 #EAF0FA, l2 #6FA8FF, l3 #F0C25E + glow
    auto slogan = [&](const std::string& key, int y, SDL_Color color, bool glow, float startT) {
        const float p = clamp01((t - startT) / 0.65f);
        if (p <= 0.0f) return;
        const float eased = easeOutBack(p);
        const int slide = static_cast<int>((1.0f - eased) * 240.0f);
        const std::string text = tr(key, langCode);
        const int w = e.measureText(text, 96, FontRole::Pixel, rtl);
        const int x = 960 - w / 2 + slide;
        if (glow) {
            e.drawTextGlow(text, x, y, 96, color, withA(kGD, 200), FontRole::Pixel, false, rtl);
        } else {
            e.drawText(text, x, y, 96, color, FontRole::Pixel, false, rtl);
        }
    };
    slogan("dream_it", 430, SDL_Color{234, 240, 250, 255}, false, 1.5f);
    slogan("build_it", 540, kBLl, false, 2.4f);
    slogan("create_it", 650, kGD, true, 3.3f);

    // .tagl — 30px bold #C4D1E8
    const float tagP = clamp01((t - 4.2f) / 0.6f);
    if (tagP > 0.0f) {
        e.drawTextCentered(tr("tagline", langCode), 960, 782, 30,
                           withA(SDL_Color{196, 209, 232, 255}, static_cast<Uint8>(255 * tagP)),
                           FontRole::Body, rtl);
    }

    // .loader — 10 segments 44x20 r4 gap 8
    const int segW = 44, segGap = 8, segN = 10;
    const int totalW = segN * segW + (segN - 1) * segGap;
    const int lx = 960 - totalW / 2;
    for (int i = 0; i < segN; ++i) {
        const float lit = clamp01((t - 5.0f - 0.22f * static_cast<float>(i)) / 0.18f);
        const Uint8 a = static_cast<Uint8>(46 + 209 * lit);
        e.drawRoundRect(lx + i * (segW + segGap), 836, segW, 20, 4, withA(kGD, a));
    }
}

void drawWelcome(UiEngine& e, int focusIndex, const std::string& langCode) {
    const bool rtl = langCode == "ar";

    // .bgart.a1.dim + .vigw (left-heavy dark gradient + bottom fade)
    e.drawImage("bg_splash", 0, 0, 1920, 1080);
    e.fillRect(0, 0, 1920, 1080, SDL_Color{5, 10, 24, 55});
    if (rtl) {
        e.fillGradH(0, 0, 1920, 1080, SDL_Color{5, 10, 24, 60}, SDL_Color{5, 10, 24, 248});
    } else {
        e.fillGradH(0, 0, 1920, 1080, SDL_Color{5, 10, 24, 248}, SDL_Color{5, 10, 24, 60});
    }
    e.fillGradV(0, 760, 1920, 320, SDL_Color{5, 10, 24, 0}, SDL_Color{5, 10, 24, 225});

    // .wtop — h140 padding 0 96 (RTL: brand right, chip left)
    {
        const std::string brandTxt = "ADDONSTATION";
        const int brandW = 68 + 18 + e.measureText(brandTxt, 30, FontRole::Pixel, false);
        const int bx = rtl ? 1920 - 96 - brandW : 96;
        const int by = 36;
        e.dropShadow(bx, by, 68, 68, 18, 12, 90);
        e.drawRoundRect(bx, by, 68, 68, 18, kSF);
        e.drawImageRounded("icon_app", bx + 3, by + 3, 62, 62, 15);
        e.drawRoundBorder(bx, by, 68, 68, 18, 3, kGD);
        e.drawText(brandTxt, bx + 86, by + 18, 30, kWHT, FontRole::Pixel, false, false);
    }
    {
        // .langchip — h68 r34, flag 44 + name 26 + □ gl
        const std::string langName = langCode == "ar" ? "\xD8\xA7\xD9\x84\xD8\xB9\xD8\xB1\xD8\xA8\xD9\x8A\xD8\xA9"
                                                     : "English";
        const std::string flag = langCode == "ar" ? "flag_ar" : "flag_en";
        const int chipW = 210;
        const int cx = rtl ? 96 : 1920 - 96 - chipW;
        const int cy = 36;
        e.drawRoundRect(cx, cy, chipW, 68, 34, SDL_Color{18, 26, 43, 190});
        e.drawRoundBorder(cx, cy, chipW, 68, 34, 2, SDL_Color{255, 255, 255, 42});
        e.drawImageRounded(flag, cx + 12, cy + 12, 44, 44, 22);
        e.drawText(langName, cx + 68, cy + 18, 26, kWHT, FontRole::Body, false, langCode == "ar", 110);
        drawGl(e, cx + chipW - 30, cy + 34, "\xE2\x96\xA1", kPNK, 36);
    }

    // .wcol — left 96 top 190 width 1040 (RTL: right 96)
    const int colX = rtl ? 1920 - 96 - 1040 : 96;
    const int colRight = colX + 1040;
    const int colY = 190;

    // .wk gold pill (h46 r23, 22px bold ls2)
    {
        const std::string wtxt = tr("welcome", langCode);
        const int tw = e.measureText(wtxt, 22, FontRole::Body, langCode == "ar");
        const int pw = tw + 76;
        const int px = rtl ? colRight - pw : colX;
        e.drawRoundRect(px, colY, pw, 46, 23, SDL_Color{240, 194, 94, 40});
        e.drawRoundBorder(px, colY, pw, 46, 23, 2, SDL_Color{240, 194, 94, 140});
        // star glyph
        if (rtl) {
            e.drawText("*", px + pw - 44, colY + 8, 26, kGD, FontRole::Body, false, false);
            e.drawText(wtxt, px + pw - 76 - tw, colY + 10, 22, kGD, FontRole::Body, false, true);
        } else {
            e.drawText("*", px + 22, colY + 8, 26, kGD, FontRole::Body, false, false);
            e.drawText(wtxt, px + 52, colY + 10, 22, kGD, FontRole::Body, false, false);
        }
    }

    // .wh — 62px F77 (ar 70 Tajawal), margin 22 0 14
    e.drawText(tr("welcome_family", langCode), rtl ? colRight : colX, colY + 68,
               langCode == "ar" ? 70 : 62,
               kWHT, FontRole::Pixel, rtl, langCode == "ar");

    // .wp — 30px line-height 1.5 #D5E0F2, wraps at 820px
    {
        const std::string sub = tr("welcome_sub", langCode);
        // greedy wrap at ~820px
        std::vector<std::string> lines;
        std::string cur, word;
        auto flushWord = [&]() {
            if (word.empty()) return;
            std::string cand = cur.empty() ? word : cur + " " + word;
            if (e.measureText(cand, 30, FontRole::Body, langCode == "ar") > 820 && !cur.empty()) {
                lines.push_back(cur);
                cur = word;
            } else {
                cur = cand;
            }
            word.clear();
        };
        for (char ch : sub) {
            if (ch == ' ') flushWord();
            else word.push_back(ch);
        }
        flushWord();
        if (!cur.empty()) lines.push_back(cur);
        for (std::size_t li = 0; li < lines.size() && li < 2; ++li) {
            e.drawText(lines[li], rtl ? colRight : colX, colY + 152 + static_cast<int>(li) * 45, 30,
                       SDL_Color{213, 224, 242, 255}, FontRole::Body, rtl, langCode == "ar");
        }
    }

    // .wbtns — width 700 gap 20 (RTL: at right of wcol)
    const int bw = 700;
    const int bx0 = rtl ? colRight - bw : colX;
    const int by0 = colY + 288;
    struct WB { int y; int h; const char* icon; bool pri; bool txt; };
    const WB wbs[3] = {
        {by0, 104, "phone", true, false},
        {by0 + 124, 104, "mail", false, false},
        {by0 + 248, 80, "guest", false, true},
    };
    for (int i = 0; i < 3; ++i) {
        const WB& b = wbs[i];
        const bool focused = (focusIndex == i);
        const bool asTxt = b.txt && !focused;
        const std::string title = tr(i == 0 ? "sign_in_code" : (i == 1 ? "sign_in" : "guest"), langCode);
        const std::string sub = tr(i == 0 ? "sign_in_code_sub" : (i == 1 ? "sign_in_sub" : "guest"), langCode);

        if (!asTxt) {
            // .wb — min-h 104, r28, border 3 rgba(255,255,255,.28), bg rgba(255,255,255,.10)
            if (b.pri) {
                e.dropShadow(bx0, b.y, bw, b.h, 28, 16, 120);
                e.drawRoundGradD(bx0, b.y, bw, b.h, 28, kBL, kBL2);
            } else {
                e.drawRoundRect(bx0, b.y, bw, b.h, 28, SDL_Color{255, 255, 255, 26});
                e.drawRoundBorder(bx0, b.y, bw, b.h, 28, 3, SDL_Color{255, 255, 255, 72});
            }
            // .bi 64x64 r20
            const int biX = bx0 + 34, biY = b.y + (b.h - 64) / 2;
            e.drawRoundRect(biX, biY, 64, 64, 20,
                            b.pri ? SDL_Color{255, 255, 255, 52} : SDL_Color{255, 255, 255, 36});
            const SDL_Color ink = kWHT;
            const int biDrawX = rtl ? (bx0 + bw - 34 - 64) : biX;
            if (std::string(b.icon) == "phone") iconPhone(e, biDrawX, biY, 64, ink);
            else if (std::string(b.icon) == "mail") iconMail(e, biDrawX, biY, 64, ink);
            else iconGuest(e, biDrawX, biY, 64, ink);
            // .wb b 34px + .bs 22px (RTL: icon chip on right, text right-aligned)
            const int tX = rtl ? (bx0 + bw - 34 - 64 - 24) : (biX + 88);
            e.drawText(title, tX, b.y + (b.h - 70) / 2, 34, kWHT,
                       FontRole::Body, rtl, langCode == "ar");
            e.drawText(sub, tX, b.y + (b.h - 70) / 2 + 46, 22,
                       b.pri ? SDL_Color{228, 238, 255, 255} : kMU,
                       FontRole::Body, rtl, langCode == "ar");
        } else {
            // .wb.txt — underlined link
            const int tw2 = e.measureText(title, 30, FontRole::Body, langCode == "ar");
            const int tx2 = rtl ? (bx0 + bw - 34 - tw2) : (bx0 + 34);
            e.drawText(title, tx2, b.y + 18, 30, kMU, FontRole::Body, false, langCode == "ar");
            e.fillRect(tx2, b.y + 58, tw2, 3, withA(kMU, 220));
        }
        if (focused) focusRing(e, bx0, b.y, bw, b.h, 28);
    }

    // .wcards — right 130 top 250, two tilted cards (330w, r28)
    struct WC { int x, y, rot; };
    const int cardsX = rtl ? 130 : 1920 - 130 - 640;
    const WC wcs[2] = {
        {cardsX + 10, 330, -5},
        {cardsX + 270, 500, 4},
    };
    for (int i = 0; i < 2; ++i) {
        const WC& c = wcs[i];
        const int cw = 330, ch = 320;
        const float ang = static_cast<float>(c.rot);
        const int cx = c.x + cw / 2, cy = c.y + ch / 2;
        e.dropShadow(c.x + 12, c.y + 16, cw, ch, 28, 22, 140);
        e.drawRoundRectRotated(cx, cy, cw - 12, ch - 12, 30, ang, SDL_Color{255, 255, 255, 46});
        e.drawRoundRectRotated(cx, cy, cw - 16, ch - 16, 28, ang, SDL_Color{14, 21, 38, 235});
        e.drawImageRotated(i == 0 ? "cover_0" : "cover_3", cx, cy - 42, cw - 44, 186, ang);
        // rotated content
        const std::string t = i == 0 ? "[Addon name]" : "[World name]";
        e.drawTextRotated(t, cx, cy + 74, 28, kWHT, ang, FontRole::Body, langCode == "ar");
        e.drawTextRotated(i == 0 ? "150 AC" : "250 AC", cx, cy + 118, 28, kGD, ang,
                          FontRole::Body, false);
    }

    // .terms — bottom 112 center 21px #8FA2C2
    e.drawTextCentered(tr("terms", langCode), 960, 928, 21, SDL_Color{143, 162, 194, 255},
                       FontRole::Body, langCode == "ar");

    // .legend — h96
    drawLegendBar(e, {{"\xE2\x9C\x95", "select"},
                      {"\xE2\x96\xA1", "language"},
                      {"OPT", "help"}},
                  langCode);
}

void drawLanguageDialog(UiEngine& e, int selectedLang, const std::string& langCode) {
    const bool rtl = langCode == "ar";
    // dimmed welcome behind
    drawWelcome(e, -1, langCode);
    e.fillRect(0, 0, 1920, 1080, SDL_Color{5, 10, 24, 140});

    // .dlg — width 760, r36, padding 44, bg #0E1526 border 2 #223049
    const int dlgW = 760;
    const int dlgH = 838;
    const int dlgX = 960 - dlgW / 2;
    const int dlgY = 540 - dlgH / 2;
    e.dropShadow(dlgX, dlgY, dlgW, dlgH, 36, 30, 180);
    e.drawRoundRect(dlgX, dlgY, dlgW, dlgH, 36, SDL_Color{14, 21, 38, 255});
    e.drawRoundBorder(dlgX, dlgY, dlgW, dlgH, 36, 2, kBD);

    // title 40px
    e.drawText(tr("choose_language", langCode), dlgX + 44, dlgY + 40, 40, kWHT,
               FontRole::Body, false, rtl, dlgW - 88);

    struct LRow { const char* flag; const char* native; const char* eng; };
    const LRow rows[8] = {
        {"flag_en", "English", "English"},
        {"flag_ar", "\xD8\xA7\xD9\x84\xD8\xB9\xD8\xB1\xD8\xA8\xD9\x8A\xD8\xA9", "Arabic"},
        {"flag_fr", "Fran\xC3\xA7\x61is", "French"},
        {"flag_tr", "T\xC3\xBCrk\xC3\xA7\x65", "Turkish"},
        {"flag_es", "Espa\xC3\xB1ol", "Spanish"},
        {"flag_ru", "\xD0\xA0\xD1\x83\xD1\x81\xD1\x81\xD0\xBA\xD0\xB8\xD0\xB9", "Russian"},
        {"flag_pt", "Portugu\xC3\xAAs", "Portuguese"},
        {"flag_zh", "\xE4\xB8\xAD\xE6\x96\x87", "Chinese"},
    };
    const int rowH = 88;
    for (int i = 0; i < 8; ++i) {
        const int y = dlgY + 112 + i * rowH;
        const bool sel = (i == selectedLang);
        if (sel) {
            e.drawRoundRect(dlgX + 44, y, dlgW - 88, rowH - 10, 22,
                            SDL_Color{46, 123, 239, 40});
            e.drawRoundBorder(dlgX + 44, y, dlgW - 88, rowH - 10, 22, 2, kBLl);
        } else {
            e.drawRoundRect(dlgX + 44, y, dlgW - 88, rowH - 10, 22, SDL_Color{14, 21, 38, 255});
        }
        const int fx = rtl ? dlgX + dlgW - 44 - 48 - 16 : dlgX + 44 + 16;
        e.drawImageRounded(rows[i].flag, fx, y + 15, 48, 48, 24);
        const int tx = rtl ? dlgX + 44 + 24 : dlgX + 44 + 80;
        e.drawText(rows[i].native, tx, y + 12, 26, kWHT, FontRole::Body, false,
                   i == 1 || i == 5 || i == 7, 400);
        e.drawText(rows[i].eng, tx, y + 46, 20, kMU, FontRole::Body, false, false, 400);
        // radio
        const int rx = rtl ? dlgX + 60 : dlgX + dlgW - 44 - 40;
        e.drawCircle(rx, y + 34, 14, SDL_Color{10, 15, 28, 255});
        e.drawRoundBorder(rx - 14, y + 20, 28, 28, 14, 2, sel ? kBLl : SDL_Color{255, 255, 255, 80});
        if (sel) e.drawCircle(rx, y + 34, 8, kBLl);
    }
}

void drawToast(UiEngine& e, const std::string& text, const std::string& langCode) {
    const bool rtl = langCode == "ar";
    // .toast — h84 r42 bg #1A2438 border 2 #3FBF6A, bottom 130
    const int tw = e.measureText(text, 28, FontRole::Body, rtl);
    const int w = tw + 140;
    const int x = 960 - w / 2;
    const int y = 1080 - 130 - 84;
    e.dropShadow(x, y, w, 84, 42, 18, 150);
    e.drawRoundRect(x, y, w, 84, 42, SDL_Color{26, 36, 56, 255});
    e.drawRoundBorder(x, y, w, 84, 42, 2, kGRN);
    const int checkX = rtl ? x + w - 56 : x + 32;
    e.drawCircle(checkX + 12, y + 42, 16, SDL_Color{63, 191, 106, 60});
    e.drawText("\xE2\x9C\x93", checkX, y + 22, 30, kGRN2, FontRole::Body, false, false);
    e.drawText(text, rtl ? x + 40 : x + 76, y + 22, 28, kGRN2, FontRole::Body, false, rtl, tw + 10);
}

void drawLegendBar(UiEngine& e,
                   const std::vector<std::pair<std::string, std::string>>& items,
                   const std::string& langCode) {
    // .legend — h96, gradient bottom fade, padding 0 96, gap 44
    e.fillGradV(0, 984, 1920, 96, SDL_Color{5, 8, 16, 0}, SDL_Color{5, 8, 16, 242});
    int x = 96;
    for (const auto& it : items) {
        const std::string& glyph = it.first;
        const std::string& labelKey = it.second;
        SDL_Color color = SDL_Color{196, 209, 232, 255};
        if (glyph.find("\xE2\x9C\x95") == 0 || glyph == "✕") color = kBLl;
        else if (glyph == "○") color = kRED;
        else if (glyph == "□") color = kPNK;
        else if (glyph == "△") color = kGRN;
        drawGl(e, x + 22, 1032, glyph, color, 44);
        const std::string label = tr(labelKey, langCode);
        e.drawText(label, x + 58, 1018, 24, SDL_Color{196, 209, 232, 255},
                   FontRole::Body, false, langCode == "ar");
        x += 58 + e.measureText(label, 24, FontRole::Body, false) + 44;
    }
}

void drawInstallDemo(UiEngine& e, DemoState state, int percent, const std::string& langCode) {
    const bool rtl = langCode == "ar";
    e.clear(kBG);
    drawTopNav(e, 2, langCode, 1250);

    // .itbody — left 96 top 140, grid 1000px + 1fr gap 56
    const int leftX = 96;
    const int rightX = 96 + 1000 + 56;
    const int topY = 150;

    // .itc — 560h r32 cover + ribbon
    e.dropShadow(leftX, topY, 1000, 560, 32, 24, 150);
    e.drawImageRounded("cover_1", leftX, topY, 1000, 560, 32);
    e.drawRoundBorder(leftX, topY, 1000, 560, 32, 2, kBD);
    // ribbon (dark pill top-left)
    {
        const std::string rib = "\xE2\x9C\xA6 " + std::string("Winter Sale");
        const int rw = e.measureText(rib, 22, FontRole::Body, false) + 44;
        const int rx = rtl ? leftX + 1000 - 24 - rw : leftX + 24;
        e.drawRoundRect(rx, topY + 24, rw, 54, 27, SDL_Color{10, 24, 52, 190});
        e.drawRoundBorder(rx, topY + 24, rw, 54, 27, 2, SDL_Color{156, 220, 255, 90});
        e.drawText(rib, rx + 22, topY + 36, 22, SDL_Color{189, 230, 255, 255},
                   FontRole::Body, false, false);
    }

    // right column
    e.drawText(tr("install_demo_title", langCode), rightX, topY + 4, 48, kWHT,
               FontRole::Pixel, false, rtl, 700);
    e.drawText("[Creator]", rightX, topY + 76, 28, kMU, FontRole::Body, false, false);
    // stats row
    e.drawText("\xE2\x86\x93 1,240    \xE2\x98\x85 4.8", rightX, topY + 120, 26,
               SDL_Color{196, 209, 232, 255}, FontRole::Body, false, false);
    // PS4 chip
    e.drawRoundRect(rightX, topY + 168, 92, 42, 21, SDL_Color{26, 42, 82, 255});
    e.drawRoundBorder(rightX, topY + 168, 92, 42, 21, 1, SDL_Color{46, 74, 138, 255});
    e.drawText("PS4", rightX + 18, topY + 176, 22, kFOCUS, FontRole::Body, false, false);
    e.drawText("\xE2\x9C\x93", rightX + 110, topY + 172, 28, kGRN, FontRole::Body, false, false);

    // .pb button stack — Download 88h r26 (xl full width of column)
    const int btnW = 620;
    const int btnY = topY + 250;
    const int btnH = 88;
    auto drawButton = [&](int y, const std::string& label, bool primary, bool gold, bool green) {
        if (primary || gold || green) {
            e.dropShadow(rightX, y, btnW, btnH, 26, 14, 120);
            if (primary) e.drawRoundGradD(rightX, y, btnW, btnH, 26, kBL, kBL2);
            else if (gold) e.drawRoundGradD(rightX, y, btnW, btnH, 26, kGD, SDL_Color{224, 169, 58, 255});
            else e.drawRoundGradD(rightX, y, btnW, btnH, 26, kGRN, SDL_Color{46, 158, 86, 255});
        } else {
            e.drawRoundRect(rightX, y, btnW, btnH, 26, SDL_Color{255, 255, 255, 30});
            e.drawRoundBorder(rightX, y, btnW, btnH, 26, 3, SDL_Color{255, 255, 255, 90});
        }
        const SDL_Color ink = (gold ? SDL_Color{27, 20, 5, 255} : kWHT);
        e.drawTextCentered(label, rightX + btnW / 2, y + btnH / 2, 32, ink,
                           FontRole::Body, langCode == "ar");
    };

    if (state == DemoState::Idle) {
        iconDownload(e, rightX + 120, btnY + 22, 44, kWHT);
        drawButton(btnY, tr("download", langCode), true, false, false);
        drawButton(btnY + 108, "Save", false, false, false);
    } else if (state == DemoState::Downloading) {
        drawButton(btnY, tr("downloading", langCode) + "  " + std::to_string(percent) + "%",
                   true, false, false);
        // progress bar under
        e.drawRoundRect(rightX, btnY + 104, btnW, 12, 6, kBD);
        e.drawRoundRect(rightX, btnY + 104, std::max(12, btnW * percent / 100), 12, 6, kBLl);
    } else if (state == DemoState::Installing) {
        drawButton(btnY, tr("installing", langCode), false, true, false);
    } else {
        drawButton(btnY, tr("installed", langCode), false, false, true);
    }

    // description card below cover
    {
        const int dy = topY + 560 + 36;
        e.drawText("DESCRIPTION", leftX, dy, 20, kGD, FontRole::Body, false, false);
        e.drawText(tr("install_demo_sub", langCode), leftX, dy + 36, 26, kMU,
                   FontRole::Body, false, rtl, 1000);
    }

    drawLegendBar(e, {{"\xE2\x9C\x95", "select"},
                      {"\xE2\x97\x8B", "back"},
                      {"\xE2\x96\xB3", "open"}},
                  langCode);
}

void drawTopNav(UiEngine& e, int activeTab, const std::string& langCode, int acBalance) {
    (void)langCode;
    // .nav — h120 padding 0 96 gap 36
    const int bx = 96;
    e.drawRoundRect(bx, 30, 60, 60, 16, kSF);
    e.drawImageRounded("icon_app", bx + 3, 33, 54, 54, 13);
    e.drawRoundBorder(bx, 30, 60, 60, 16, 3, kGD);
    e.drawText("ADDONSTATION", bx + 76, 46, 22, kWHT, FontRole::Pixel, false, false);

    // tabs
    struct Tab { const char* key; };
    const char* keys[5] = {"nav_home", "nav_deals", "nav_store", "nav_chat", "nav_settings"};
    int x = bx + 76 + e.measureText("ADDONSTATION", 22, FontRole::Pixel, false) + 48;
    drawGl(e, x + 22, 60, "L1", kMU, 44);
    x += 62;
    for (int i = 0; i < 5; ++i) {
        const std::string label = tr(keys[i], langCode);
        const int tw = e.measureText(label, 30, FontRole::Body, langCode == "ar");
        const bool on = (i == activeTab);
        e.drawText(label, x, 42, 30, on ? kWHT : SDL_Color{185, 198, 220, 255},
                   FontRole::Body, false, langCode == "ar");
        if (on) e.fillRect(x, 84, tw, 5, kBLl);
        x += tw + 34;
    }
    drawGl(e, x + 22, 60, "R1", kMU, 44);

    // right cluster: bell + AC pill + avatar
    int rx = 1920 - 96;
    // avatar 58 circle border 3 gold
    rx -= 58;
    e.drawImageRounded("icon_avatar", rx, 31, 58, 58, 29);
    e.drawRoundBorder(rx, 31, 58, 58, 29, 3, kGD);
    // .acp — h58 r29, coin 40 + balance 28 gold
    rx -= 190;
    e.drawRoundRect(rx, 31, 180, 58, 29, SDL_Color{26, 36, 56, 200});
    e.drawRoundBorder(rx, 31, 180, 58, 29, 1, SDL_Color{255, 255, 255, 32});
    e.drawImage("icon_coin", rx + 10, 40, 40, 40);
    char bal[24];
    std::snprintf(bal, sizeof(bal), "%d,%03d", acBalance / 1000, acBalance % 1000);
    e.drawText(bal, rx + 62, 44, 28, kGD, FontRole::Body, false, false);
    // bell 58 circle + red dot
    rx -= 78;
    e.drawRoundRect(rx, 31, 58, 58, 29, SDL_Color{26, 36, 56, 200});
    e.drawRoundBorder(rx, 31, 58, 58, 29, 1, SDL_Color{255, 255, 255, 32});
    e.drawText("!", rx + 24, 42, 30, SDL_Color{196, 209, 232, 255}, FontRole::Body, false, false);
    e.drawCircle(rx + 46, 42, 7, kRED);
}

} // namespace pkgtest
