#include "screens.hpp"

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
        {"install_demo_title", {{"en", "Install PrizmaVisuals 1.3.10222"}, {"ar", "تثبيت PrizmaVisuals 1.3.10222"},
                                {"fr", "Installer PrizmaVisuals 1.3.10222"}, {"tr", "PrizmaVisuals 1.3.10222 kur"},
                                {"es", "Instalar PrizmaVisuals 1.3.10222"}, {"ru", "Установить PrizmaVisuals 1.3.10222"},
                                {"pt", "Instalar PrizmaVisuals 1.3.10222"}, {"zh", "安装 PrizmaVisuals 1.3.10222"}}},
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

std::string iso3ForLang(const std::string& langCode) {
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

std::string formatEnjoyNotificationStyle(const std::string& langCode) {
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

void drawSplash(UiEngine& e, float t, const std::string& langCode) {
    const bool rtl = langCode == "ar";

    // Zoom 1.18 -> 1.02 over 8s + vignette overlay
    const float zoom = 1.18f - 0.16f * clamp01(t / 8.0f);
    e.drawImageZoomed("bg_splash", 0, 0, 1920, 1080, zoom);

    // Vignette: dark edges
    e.fillGradV(0, 0, 1920, 260, SDL_Color{10, 15, 28, 150}, SDL_Color{10, 15, 28, 0});
    e.fillGradV(0, 820, 1920, 260, SDL_Color{10, 15, 28, 0}, SDL_Color{10, 15, 28, 200});
    e.fillGradH(0, 0, 320, 1080, SDL_Color{10, 15, 28, 170}, SDL_Color{10, 15, 28, 0});
    e.fillGradH(1600, 0, 320, 1080, SDL_Color{10, 15, 28, 0}, SDL_Color{10, 15, 28, 170});
    e.fillRect(0, 0, 1920, 1080, SDL_Color{10, 15, 28, 60});

    // App icon with gold frame glow (pop-in)
    const float popT = clamp01((t - 0.35f) / 0.65f);
    const float popScale = 0.82f + 0.18f * easeOutBack(popT);
    const int iconSize = static_cast<int>(110 * popScale);
    const int iconX = 960 - iconSize / 2;
    const int iconY = 300 - iconSize / 2;
    if (popT > 0.0f) {
        e.drawRoundBorder(iconX - 14, iconY - 14, iconSize + 28, iconSize + 28, 30, 5,
                          SDL_Color{240, 194, 94, static_cast<Uint8>(200 * popT)});
        e.drawRoundBorder(iconX - 8, iconY - 8, iconSize + 16, iconSize + 16, 26, 3,
                          SDL_Color{240, 194, 94, static_cast<Uint8>(255 * popT)});
        e.drawRoundRect(iconX, iconY, iconSize, iconSize, 20, theme::SF);
        e.drawImage("icon_app", iconX + 6, iconY + 6, iconSize - 12, iconSize - 12);
    }

    // Wordmark ADDONSTATION (F77)
    e.drawTextCentered("ADDONSTATION", 960, 425, 64, theme::TX, FontRole::Pixel);

    // Slogans slide in from alternating sides + scale 0.88 -> 1.00
    auto drawSlogan = [&](const std::string& key, int y, SDL_Color color, bool fromLeft, float startT) {
        const float p = clamp01((t - startT) / 0.65f);
        if (p <= 0.0f) return;
        const float eased = easeOutBack(p);
        const int slide = static_cast<int>((1.0f - eased) * (fromLeft ? -420.0f : 420.0f));
        const std::string text = tr(key, langCode);
        const int w = e.measureText(text, 54, FontRole::Pixel, rtl);
        const int x = 960 - w / 2 + slide;
        e.drawText(text, x, y, 54, color, FontRole::Pixel, false, rtl);
    };
    drawSlogan("dream_it", 520, theme::TX, rtl ? false : true, 1.6f);
    drawSlogan("build_it", 595, SDL_Color{46, 123, 239, 255}, rtl ? true : false, 2.5f);

    const float goldP = clamp01((t - 3.4f) / 0.7f);
    if (goldP > 0.0f) {
        const float eased = easeOutBack(goldP);
        const int slide = static_cast<int>((1.0f - eased) * (rtl ? 420.0f : -420.0f));
        const std::string text = tr("create_it", langCode);
        const int w = e.measureText(text, 54, FontRole::Pixel, rtl);
        const int x = 960 - w / 2 + slide;
        // Gold glow text
        e.drawText(text, x + 2, 672, 54, SDL_Color{138, 106, 34, 160}, FontRole::Pixel, false, rtl);
        e.drawText(text, x, 670, 54, theme::GD, FontRole::Pixel, false, rtl);
    }

    // Tagline
    e.drawTextCentered(tr("tagline", langCode), 960, 775, 26, theme::MU, FontRole::Body, rtl);

    // Loader: 10 gold segments fill at 0.5s intervals starting at t=5.0s
    const int segW = 92;
    const int segGap = 14;
    const int totalW = 10 * segW + 9 * segGap;
    const int startX = 960 - totalW / 2;
    const int loaderY = 880;
    for (int i = 0; i < 10; ++i) {
        const float fillStart = 5.0f + 0.2f * i;
        const float fill = clamp01((t - fillStart) / 0.2f);
        e.drawRoundRect(startX + i * (segW + segGap), loaderY, segW, 14, 7,
                        SDL_Color{240, 194, 94, 40});
        if (fill > 0.0f) {
            const int fw = std::max(2, static_cast<int>(segW * fill));
            e.drawRoundRect(startX + i * (segW + segGap), loaderY, fw, 14, 7, theme::GD);
        }
    }

    // Skip hint bottom-right (✕ Skip)
    e.drawRoundRect(1740, 985, 120, 52, 26, SDL_Color{18, 26, 43, 170});
    e.drawRoundBorder(1740, 985, 120, 52, 26, 2, SDL_Color{34, 48, 73, 220});
    e.drawText(tr("skip", langCode), 1800, 1002, 22, theme::MU, FontRole::Body, false, rtl, 110);
}

void drawWelcome(UiEngine& e, int focusIndex, const std::string& langCode) {
    const bool rtl = langCode == "ar";
    e.drawImageZoomed("bg_splash", 0, 0, 1920, 1080, 1.02f);
    e.fillRect(0, 0, 1920, 1080, SDL_Color{10, 15, 28, 150});
    e.fillGradH(0, 0, 1500, 1080, SDL_Color{10, 15, 28, 210}, SDL_Color{10, 15, 28, 30});

    // Top bar: brand + language chip
    e.drawImage("icon_app", 64, 42, 84, 84);
    e.drawText("ADDONSTATION", 168, 68, 40, theme::TX, FontRole::Pixel);

    e.drawRoundRect(1560, 48, 296, 72, 36, SDL_Color{18, 26, 43, 190});
    e.drawRoundBorder(1560, 48, 296, 72, 36, 2, SDL_Color{229, 140, 198, 220});
    e.drawImage(iso3ForLang(langCode), 1584, 66, 44, 36);
    e.drawText(tr("language", langCode), 1780, 72, 26, theme::TX, FontRole::Body, true, rtl, 150);
    e.drawText("□", 1832, 70, 28, SDL_Color{229, 140, 198, 255}, FontRole::Body);

    // WELCOME kicker with star
    e.drawText("★", 64 + (rtl ? 420 : 0), 260, 30, theme::GD, FontRole::Body);
    e.drawText(tr("welcome", langCode), 108 + (rtl ? 420 : 0), 262, 28, theme::GD, FontRole::Body);

    // Heading (F77)
    e.drawText(tr("welcome_family", langCode), 64, 316, 56, theme::TX, FontRole::Pixel, false, rtl, 900);
    // Subtitle
    e.drawText(tr("welcome_sub", langCode), 64, 400, 26, theme::MU, FontRole::Body, false, rtl, 1180);

    // 3 buttons
    const int btnX = 64;
    const int btnW = 1080;
    const int btnH = 118;
    const int btnY[3] = {480, 618, 736};

    auto drawWelcomeBtn = [&](int idx, const std::string& title, const std::string& sub, bool primary, bool textOnly) {
        const int y = btnY[idx];
        const bool focused = focusIndex == idx;
        if (textOnly) {
            if (focused) {
                e.drawRoundRect(btnX, y, btnW, 84, 18, SDL_Color{26, 36, 56, 160});
            }
        } else if (primary) {
            e.drawRoundGradH(btnX, y, btnW, btnH, 18,
                             SDL_Color{46, 123, 239, 255}, SDL_Color{76, 154, 255, 255});
        } else {
            e.drawRoundRect(btnX, y, btnW, btnH, 18, SDL_Color{18, 26, 43, 235});
            e.drawRoundBorder(btnX, y, btnW, btnH, 18, 2, theme::BD);
        }
        if (focused) {
            e.drawFocusRing(btnX, y, btnW, textOnly ? 84 : btnH, 18);
        }
        if (!textOnly) {
            // Icon tile
            e.drawRoundRect(btnX + 24, y + 26, 66, 66, 14,
                            primary ? SDL_Color{255, 255, 255, 45} : SDL_Color{26, 36, 56, 255});
            if (idx == 0) {
                // phone glyph
                e.drawRoundBorder(btnX + 42, y + 38, 30, 44, 7, 3, SDL_Color{255, 255, 255, 235});
                e.fillRect(btnX + 52, y + 74, 10, 3, SDL_Color{255, 255, 255, 235});
            } else {
                // envelope glyph
                e.drawRoundBorder(btnX + 36, y + 42, 42, 32, 6, 3, SDL_Color{111, 168, 255, 235});
                e.fillRect(btnX + 38, y + 44, 38, 3, SDL_Color{111, 168, 255, 235});
            }
            e.drawText(title, btnX + 116, y + 24, 32,
                       primary ? SDL_Color{255, 255, 255, 255} : theme::TX, FontRole::Body, false, rtl, 820);
            e.drawText(sub, btnX + 116, y + 68, 21,
                       primary ? SDL_Color{220, 232, 255, 235} : theme::MU, FontRole::Body, false, rtl, 820);
        } else {
            e.drawText(title, btnX + 116, y + 26, 26, theme::BL2, FontRole::Body, false, rtl, 700);
        }
    };

    drawWelcomeBtn(0, tr("sign_in_code", langCode), tr("sign_in_code_sub", langCode), true, false);
    drawWelcomeBtn(1, tr("sign_in", langCode), tr("sign_in_sub", langCode), false, false);
    drawWelcomeBtn(2, tr("guest", langCode), "", false, true);

    // Terms
    e.drawText(tr("terms", langCode), 64, 852, 18, SDL_Color{120, 138, 168, 255}, FontRole::Body, false, rtl, 1180);

    // Right tilted cards (drawn as clean rounded cards with covers + prices)
    const int cardW = 360;
    const int cardH = 470;
    const int card1X = 1240;
    const int card2X = 1450;
    const int cardY = 320;

    auto drawCard = [&](int x, int y, const std::string& cover, const std::string& name, const std::string& price, int tiltShift) {
        e.drawRoundRect(x + tiltShift, y + 18, cardW, cardH, 26, SDL_Color{8, 12, 24, 160});
        e.drawRoundRect(x + tiltShift, y, cardW, cardH, 26, theme::SF);
        e.drawRoundBorder(x + tiltShift, y, cardW, cardH, 26, 2, theme::BD);
        e.drawImage(cover, x + tiltShift + 14, y + 14, cardW - 28, cardH - 190);
        e.drawText(name, x + tiltShift + 24, y + cardH - 152, 30, theme::TX, FontRole::Body, false, rtl, cardW - 60);
        e.drawImage("icon_coin", x + tiltShift + 24, y + cardH - 92, 36, 36);
        e.drawText(price, x + tiltShift + 72, y + cardH - 88, 26, theme::GD, FontRole::Body);
    };
    drawCard(card1X, cardY + 40, "cover_0", "[Addon name]", "150 AC", 0);
    drawCard(card2X, cardY, "cover_3", "[World name]", "250 AC", 40);

    // Legend bar
    drawLegendBar(e,
                  {{"✕", tr("select", langCode)},
                   {"□", tr("language", langCode)},
                   {"OPT", tr("help", langCode)}},
                  langCode);
}

void drawLanguageDialog(UiEngine& e, int selectedLang, const std::string& langCode) {
    const bool rtl = langCode == "ar";
    drawWelcome(e, 0, langCode);
    e.fillRect(0, 0, 1920, 1080, SDL_Color{5, 8, 16, 170});

    const int dlgX = 460;
    const int dlgY = 170;
    const int dlgW = 1000;
    const int dlgH = 740;
    e.drawRoundRect(dlgX, dlgY, dlgW, dlgH, 28, theme::SF);
    e.drawRoundBorder(dlgX, dlgY, dlgW, dlgH, 28, 2, theme::BD);

    e.drawText(tr("choose_language", langCode), dlgX + dlgW / 2, dlgY + 46, 34, theme::TX, FontRole::Body, false, rtl);

    const char* flags[8] = {"flag_en", "flag_ar", "flag_fr", "flag_tr", "flag_es", "flag_ru", "flag_pt", "flag_zh"};
    const char* names[8] = {"English", "العربية", "Français", "Türkçe", "Español", "Русский", "Português", "中文"};

    const int cellW = 420;
    const int cellH = 120;
    const int gapX = 40;
    const int gapY = 28;
    const int gridX = dlgX + (dlgW - 2 * cellW - gapX) / 2;
    const int gridY = dlgY + 120;

    for (int i = 0; i < 8; ++i) {
        const int col = i % 2;
        const int row = i / 2;
        const int x = gridX + col * (cellW + gapX);
        const int y = gridY + row * (cellH + gapY);
        const bool selected = i == selectedLang;
        e.drawRoundRect(x, y, cellW, cellH, 18, selected ? SDL_Color{46, 123, 239, 46} : theme::SF2);
        e.drawRoundBorder(x, y, cellW, cellH, 18, selected ? 3 : 1,
                          selected ? theme::BL : theme::BD);
        if (selected) {
            e.drawFocusRing(x, y, cellW, cellH, 18);
        }
        e.drawImage(flags[i], x + 26, y + 34, 64, 52);
        e.drawText(names[i], x + 118, y + 42, 32, theme::TX, FontRole::Body, false, i == 1, cellW - 140);
        if (selected) {
            e.drawText("✓", x + cellW - 48, y + 42, 32, theme::OK, FontRole::Body);
        }
    }

    drawLegendBar(e,
                  {{"✕", tr("select", langCode)},
                   {"○", tr("back", langCode)}},
                  langCode);
}

void drawToast(UiEngine& e, const std::string& text, const std::string& langCode) {
    const bool rtl = langCode == "ar";
    const int padX = 46;
    const int textW = e.measureText(text, 34, FontRole::Body, rtl);
    const int boxW = textW + padX * 2 + 76;
    const int boxX = 960 - boxW / 2;
    const int boxY = 92;

    e.drawRoundRect(boxX + 6, boxY + 12, boxW, 100, 22, SDL_Color{8, 12, 24, 170});
    e.drawRoundGradH(boxX, boxY, boxW, 100, 22, SDL_Color{30, 62, 42, 255}, SDL_Color{24, 52, 38, 255});
    e.drawRoundBorder(boxX, boxY, boxW, 100, 22, 2, SDL_Color{63, 191, 106, 130});

    e.drawCircle(boxX + padX + 22, boxY + 50, 24, SDL_Color{63, 191, 106, 55});
    e.drawText("✓", boxX + padX + 22, boxY + 32, 38, theme::OKT, FontRole::Body);

    e.drawText(text, boxX + padX + 62, boxY + 32, 34, SDL_Color{226, 244, 232, 255}, FontRole::Body, false, rtl, textW + 20);
}

void drawLegendBar(UiEngine& e,
                   const std::vector<std::pair<std::string, std::string>>& items,
                   const std::string& langCode) {
    const bool rtl = langCode == "ar";
    e.fillRect(0, 1006, 1920, 74, SDL_Color{14, 21, 38, 225});
    e.fillRect(0, 1006, 1920, 2, SDL_Color{34, 48, 73, 255});

    int x = rtl ? 1856 : 64;
    for (const auto& item : items) {
        const int labelW = e.measureText(item.second, 26, FontRole::Body, rtl);
        if (rtl) {
            e.drawText(item.second, x, 1028, 26, theme::MU, FontRole::Body, true, rtl, labelW + 8);
            x -= labelW + 16;
            e.drawRoundRect(x - 58, 1022, 58, 42, 12, SDL_Color{26, 36, 56, 255});
            e.drawRoundBorder(x - 58, 1022, 58, 42, 12, 2, SDL_Color{60, 80, 116, 255});
            e.drawTextCentered(item.first, x - 29, 1043, 22, theme::CHIP, FontRole::Body);
            x -= 88;
        } else {
            e.drawRoundRect(x, 1022, 58, 42, 12, SDL_Color{26, 36, 56, 255});
            e.drawRoundBorder(x, 1022, 58, 42, 12, 2, SDL_Color{60, 80, 116, 255});
            e.drawTextCentered(item.first, x + 29, 1043, 22, theme::CHIP, FontRole::Body);
            x += 70;
            e.drawText(item.second, x, 1028, 26, theme::MU, FontRole::Body, false, rtl, labelW + 8);
            x += labelW + 32;
        }
    }
}

void drawInstallDemo(UiEngine& e, DemoState state, int percent, const std::string& langCode) {
    const bool rtl = langCode == "ar";
    e.fillRect(0, 0, 1920, 1080, theme::BG);

    drawTopNav(e, 2, langCode, 1250);

    // Section header
    e.drawText(tr("install_demo_title", langCode), 96, 170, 46, theme::TX, FontRole::Pixel, false, rtl, 1300);
    e.drawText(tr("install_demo_sub", langCode), 96, 236, 24, theme::MU, FontRole::Body, false, rtl, 1500);

    // Cover card
    const int cardX = 96;
    const int cardY = 320;
    const int cardW = 860;
    const int cardH = 520;
    e.drawRoundRect(cardX, cardY, cardW, cardH, 24, theme::SF);
    e.drawRoundBorder(cardX, cardY, cardW, cardH, 24, 2, theme::BD);
    e.drawImage("cover_0", cardX + 12, cardY + 12, cardW - 24, cardH - 120);
    e.drawText("PrizmaVisuals 1.3.10222", cardX + 32, cardY + cardH - 86, 32, theme::TX, FontRole::Body, false, rtl, cardW - 320);
    e.drawRoundRect(cardX + cardW - 250, cardY + cardH - 92, 140, 44, 10, SDL_Color{155, 107, 255, 45});
    e.drawText("Epic", cardX + cardW - 220, cardY + cardH - 82, 24, theme::EPT, FontRole::Body);

    // State button
    const int btnX = 1040;
    const int btnY = 380;
    const int btnW = 760;
    const int btnH = 118;

    std::string label;
    SDL_Color left, right;
    switch (state) {
        case DemoState::Idle:
            label = tr("download", langCode);
            left = SDL_Color{46, 123, 239, 255};
            right = SDL_Color{76, 154, 255, 255};
            break;
        case DemoState::Downloading:
            label = tr("downloading", langCode);
            left = SDL_Color{46, 123, 239, 255};
            right = SDL_Color{76, 154, 255, 255};
            break;
        case DemoState::Installing:
            label = tr("installing", langCode);
            left = SDL_Color{46, 123, 239, 255};
            right = SDL_Color{76, 154, 255, 255};
            break;
        case DemoState::Installed:
            label = tr("installed", langCode);
            left = SDL_Color{46, 148, 84, 255};
            right = SDL_Color{63, 191, 106, 255};
            break;
    }

    e.drawRoundGradH(btnX, btnY, btnW, btnH, 20, left, right);
    e.drawText(label, btnX + btnW / 2, btnY + 36, 40, SDL_Color{255, 255, 255, 255}, FontRole::Body, false, rtl);
    if (state == DemoState::Downloading || state == DemoState::Installing) {
        // Progress bar under button
        e.drawRoundRect(btnX, btnY + 150, btnW, 18, 9, SDL_Color{26, 36, 56, 255});
        const int fillW = std::max(8, (btnW * std::min(100, percent)) / 100);
        e.drawRoundGradH(btnX, btnY + 150, fillW, 18, 9, theme::GD, SDL_Color{255, 224, 140, 255});
        char pctText[16];
        std::snprintf(pctText, sizeof(pctText), "%d%%", std::min(100, percent));
        e.drawText(pctText, btnX + btnW, btnY + 182, 26, theme::MU, FontRole::Body, true, rtl, 120);
    }

    // Info rows
    const int infoY = 610;
    auto infoRow = [&](int idx, const std::string& labelKey, const std::string& value, SDL_Color valueColor) {
        const int y = infoY + idx * 76;
        e.drawRoundRect(btnX, y, btnW, 62, 14, theme::SF2);
        e.drawText(labelKey, btnX + 26, y + 16, 24, theme::MU, FontRole::Body, false, rtl, 300);
        e.drawText(value, btnX + btnW - 26, y + 16, 24, valueColor, FontRole::Body, true, rtl, 380);
    };
    infoRow(0, "Target", "/app_tmp/<CUSAxxxxx>/<userYYYY>/games/com.mojang", theme::BL2);
    infoRow(1, "Mode", "Auto-detect open or closed Minecraft", theme::CHIP);
    infoRow(2, "Cleanup", "Staged .raw deleted after install (HDD free)", theme::OKT);
    infoRow(3, "Notification", formatEnjoyNotificationStyle(langCode), theme::GD);

    drawLegendBar(e,
                  {{"✕", tr("select", langCode)},
                   {"○", tr("back", langCode)}},
                  langCode);
}

void drawTopNav(UiEngine& e, int activeTab, const std::string& langCode, int acBalance) {
    const bool rtl = langCode == "ar";
    e.fillRect(0, 0, 1920, 110, theme::BAR);
    e.fillRect(0, 110, 1920, 2, theme::BD);

    e.drawImage("icon_app", 40, 22, 66, 66);
    e.drawText("ADDONSTATION", 122, 40, 30, theme::TX, FontRole::Pixel);

    const char* keys[5] = {"nav_home", "nav_deals", "nav_store", "nav_chat", "nav_settings"};
    int x = 560;
    e.drawText("L1", x - 44, 44, 22, theme::MU, FontRole::Body);
    for (int i = 0; i < 5; ++i) {
        const std::string label = tr(keys[i], langCode);
        const int w = e.measureText(label, 26, FontRole::Body, rtl) + 56;
        const bool active = i == activeTab;
        if (active) {
            e.drawRoundRect(x, 26, w, 58, 29, SDL_Color{46, 123, 239, 48});
            e.drawRoundBorder(x, 26, w, 58, 29, 2, SDL_Color{46, 123, 239, 160});
        }
        e.drawText(label, x + w / 2, 42, 26,
                   active ? theme::TX : theme::MU, FontRole::Body, false, rtl);
        x += w + 12;
    }
    e.drawText("R1", x + 8, 44, 22, theme::MU, FontRole::Body);

    // Bell + dot
    e.drawRoundRect(1560, 30, 58, 52, 14, theme::SF2);
    e.drawText("!", 1589, 40, 30, theme::CHIP, FontRole::Body);
    e.drawCircle(1610, 38, 8, theme::ER);

    // AC pill
    e.drawRoundRect(1638, 28, 168, 56, 28, SDL_Color{26, 36, 56, 255});
    e.drawRoundBorder(1638, 28, 168, 56, 28, 2, SDL_Color{240, 194, 94, 120});
    e.drawImage("icon_coin", 1652, 38, 36, 36);
    char acText[24];
    std::snprintf(acText, sizeof(acText), "%d", acBalance);
    e.drawText(acText, 1700, 42, 26, theme::GD, FontRole::Body);

    // Avatar
    e.drawCircle(1858, 56, 30, theme::SF2);
    e.drawImage("icon_avatar", 1828, 26, 60, 60);
}

} // namespace pkgtest
