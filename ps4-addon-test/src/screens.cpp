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
        {"shop_sale", {{"en", "Shop the sale"}, {"ar", "تسوق التخفيضات"}, {"fr", "Acheter en solde"},
                       {"tr", "İndirimden satın al"}, {"es", "Comprar en oferta"},
                       {"ru", "Купить со скидкой"}, {"pt", "Comprar na promoção"}, {"zh", "抢购特惠"}}},
        {"save", {{"en", "Save"}, {"ar", "حفظ"}, {"fr", "Enregistrer"}, {"tr", "Kaydet"},
                  {"es", "Guardar"}, {"ru", "Сохранить"}, {"pt", "Salvar"}, {"zh", "收藏"}}},
        {"share", {{"en", "Share"}, {"ar", "مشاركة"}, {"fr", "Partager"}, {"tr", "Paylaş"},
                   {"es", "Compartir"}, {"ru", "Поделиться"}, {"pt", "Compartilhar"}, {"zh", "分享"}}},
        {"prev_offer", {{"en", "Prev offer"}, {"ar", "العرض السابق"}, {"fr", "Offre précédente"},
                        {"tr", "Önceki teklif"}, {"es", "Oferta anterior"}, {"ru", "Пред. предложение"},
                        {"pt", "Oferta anterior"}, {"zh", "上一个优惠"}}},
        {"next_offer", {{"en", "Next offer"}, {"ar", "العرض التالي"}, {"fr", "Offre suivante"},
                        {"tr", "Sonraki teklif"}, {"es", "Oferta siguiente"}, {"ru", "След. предложение"},
                        {"pt", "Próxima oferta"}, {"zh", "下一个优惠"}}},
        {"filter", {{"en", "Filter"}, {"ar", "تصفية"}, {"fr", "Filtrer"}, {"tr", "Filtre"},
                    {"es", "Filtrar"}, {"ru", "Фильтр"}, {"pt", "Filtrar"}, {"zh", "筛选"}}},
        {"sort", {{"en", "Sort"}, {"ar", "ترتيب"}, {"fr", "Trier"}, {"tr", "Sırala"},
                  {"es", "Ordenar"}, {"ru", "Сортировка"}, {"pt", "Ordenar"}, {"zh", "排序"}}},
        {"up_to_50", {{"en", "Up to 50% off the best addons and worlds"},
                      {"ar", "خصم حتى ٥٠٪ على أفضل الإضافات والعوالم"},
                      {"fr", "Jusqu'à -50 % sur les meilleurs addons et mondes"},
                      {"tr", "En iyi eklenti ve dünyalarda %50'ye varan indirim"},
                      {"es", "Hasta un 50% de descuento en los mejores addons y mundos"},
                      {"ru", "Скидки до 50% на лучшие аддоны и миры"},
                      {"pt", "Até 50% de desconto nos melhores addons e mundos"},
                      {"zh", "最佳附加包与世界最高五折"}}},
        {"nav_store", {{"en", "Store"}, {"ar", "المتجر"}, {"fr", "Boutique"}, {"tr", "Mağaza"},
                       {"es", "Tienda"}, {"ru", "Магазин"}, {"pt", "Loja"}, {"zh", "商店"}}},
        {"nav_chat", {{"en", "Chat"}, {"ar", "الشات"}, {"fr", "Chat"}, {"tr", "Sohbet"},
                      {"es", "Chat"}, {"ru", "Чат"}, {"pt", "Chat"}, {"zh", "聊天"}}},
        {"nav_settings", {{"en", "Settings"}, {"ar", "الإعدادات"}, {"fr", "Réglages"}, {"tr", "Ayarlar"},
                          {"es", "Ajustes"}, {"ru", "Настройки"}, {"pt", "Ajustes"}, {"zh", "设置"}}},
        {"choose_language", {{"en", "Choose app language"}, {"ar", "اختر لغة التطبيق"},
                             {"fr", "Choisir la langue"}, {"tr", "Uygulama dilini seç"},
                             {"es", "Elegir idioma"}, {"ru", "Выберите язык"}, {"pt", "Escolher idioma"}, {"zh", "选择语言"}}},
        {"autolaunch", {{"en", "Auto-launch Minecraft after install"},
                        {"ar", "تشغيل Minecraft تلقائياً بعد التثبيت"},
                        {"fr", "Lancer Minecraft après l'installation"},
                        {"tr", "Kurulumdan sonra Minecraft'ı otomatik başlat"},
                        {"es", "Iniciar Minecraft automáticamente al instalar"},
                        {"ru", "Автозапуск Minecraft после установки"},
                        {"pt", "Abrir Minecraft automaticamente após instalar"},
                        {"zh", "安装后自动启动 Minecraft"}}},
        {"autolaunch_on", {{"en", "Auto-launch ON"}, {"ar", "التشغيل التلقائي مُفعَّل"},
                           {"fr", "Lancement auto activé"}, {"tr", "Otomatik başlatma açık"},
                           {"es", "Inicio automático activado"}, {"ru", "Автозапуск включён"},
                           {"pt", "Abertura automática ativada"}, {"zh", "自动启动已开启"}}},
        {"autolaunch_off", {{"en", "Auto-launch OFF"}, {"ar", "التشغيل التلقائي مُعطَّل"},
                            {"fr", "Lancement auto désactivé"}, {"tr", "Otomatik başlatma kapalı"},
                            {"es", "Inicio automático desactivado"}, {"ru", "Автозапуск выключен"},
                            {"pt", "Abertura automática desativada"}, {"zh", "自动启动已关闭"}}},
        {"settings_language", {{"en", "App language"}, {"ar", "لغة التطبيق"},
                               {"fr", "Langue de l'application"}, {"tr", "Uygulama dili"},
                               {"es", "Idioma de la aplicación"}, {"ru", "Язык приложения"},
                               {"pt", "Idioma do aplicativo"}, {"zh", "应用语言"}}},
        {"settings_account", {{"en", "Account"}, {"ar", "الحساب"}, {"fr", "Compte"}, {"tr", "Hesap"},
                              {"es", "Cuenta"}, {"ru", "Аккаунт"}, {"pt", "Conta"}, {"zh", "账户"}}},
        {"settings_link", {{"en", "Link a console"}, {"ar", "ربط جهاز"}, {"fr", "Lier une console"},
                           {"tr", "Konsol bağla"}, {"es", "Vincular una consola"},
                           {"ru", "Привязать консоль"}, {"pt", "Vincular um console"}, {"zh", "绑定主机"}}},
        {"settings_about", {{"en", "About the app"}, {"ar", "عن التطبيق"}, {"fr", "À propos"},
                            {"tr", "Uygulama hakkında"}, {"es", "Acerca de la aplicación"},
                            {"ru", "О приложении"}, {"pt", "Sobre o aplicativo"}, {"zh", "关于应用"}}},
        {"settings_signout", {{"en", "Sign out"}, {"ar", "تسجيل الخروج"}, {"fr", "Se déconnecter"},
                              {"tr", "Çıkış yap"}, {"es", "Cerrar sesión"}, {"ru", "Выйти"},
                              {"pt", "Sair"}, {"zh", "退出登录"}}},
        {"link_linked", {{"en", "Console linked!"}, {"ar", "تم ربط الجهاز!"},
                         {"fr", "Console liée !"}, {"tr", "Konsol bağlandı!"},
                         {"es", "¡Consola vinculada!"}, {"ru", "Консоль привязана!"},
                         {"pt", "Console vinculado!"}, {"zh", "主机已绑定！"}}},
        {"link_expired", {{"en", "Code expired — request a new one"},
                          {"ar", "انتهت صلاحية الكود — اطلب كوداً جديداً"},
                          {"fr", "Code expiré — demandez-en un nouveau"},
                          {"tr", "Kodun süresi doldu — yenisini isteyin"},
                          {"es", "Código caducado — pide uno nuevo"},
                          {"ru", "Код истёк — запросите новый"},
                          {"pt", "Código expirado — peça um novo"},
                          {"zh", "代码已过期 — 请获取新代码"}}},
        {"link_failed", {{"en", "Connection failed — try again"},
                         {"ar", "تعذّر الاتصال — حاول مجدداً"},
                         {"fr", "Échec de connexion — réessayez"},
                         {"tr", "Bağlantı başarısız — tekrar deneyin"},
                         {"es", "Error de conexión — inténtalo de nuevo"},
                         {"ru", "Ошибка соединения — попробуйте снова"},
                         {"pt", "Falha na conexão — tente novamente"},
                         {"zh", "连接失败 — 请重试"}}},
        {"link_new", {{"en", "New code created"}, {"ar", "تم إنشاء كود جديد"},
                      {"fr", "Nouveau code créé"}, {"tr", "Yeni kod oluşturuldu"},
                      {"es", "Nuevo código creado"}, {"ru", "Новый код создан"},
                      {"pt", "Novo código criado"}, {"zh", "已生成新代码"}}},
        {"login_failed", {{"en", "Login failed — check email and password"},
                          {"ar", "فشل تسجيل الدخول — تحقق من البريد وكلمة المرور"},
                          {"fr", "Échec de connexion — vérifiez e-mail et mot de passe"},
                          {"tr", "Giriş başarısız — e-posta ve şifreyi kontrol edin"},
                          {"es", "Error de inicio — revisa correo y contraseña"},
                          {"ru", "Ошибка входа — проверьте почту и пароль"},
                          {"pt", "Falha no login — verifique e-mail e senha"},
                          {"zh", "登录失败 — 请检查邮箱和密码"}}},
        {"login_ok", {{"en", "Signed in!"}, {"ar", "تم تسجيل الدخول!"},
                      {"fr", "Connecté !"}, {"tr", "Giriş yapıldı!"},
                      {"es", "¡Sesión iniciada!"}, {"ru", "Вход выполнен!"},
                      {"pt", "Sessão iniciada!"}, {"zh", "登录成功！"}}},
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

// PS legacy button icons (cropped from 17220652-pslegacy_xl.png into ui.pak).
// The old drawn circle+glyph shape is gone — these icons ARE the buttons.
std::string btnIconName(const std::string& key) {
    if (key.find("\xE2\x9C\x95") == 0 || key == "X") return "btn_cross";
    if (key.find("\xE2\x97\x8B") == 0) return "btn_circle";
    if (key.find("\xE2\x96\xA1") == 0) return "btn_square";
    if (key.find("\xE2\x96\xB3") == 0) return "btn_triangle";
    if (key == "L1") return "btn_l1";
    if (key == "R1") return "btn_r1";
    if (key == "L2") return "btn_l2";
    if (key == "R2") return "btn_r2";
    if (key == "OPT" || key == "OPTIONS") return "btn_options";
    return "";
}

// Draws the button icon centered at (cx,cy) with pixel height `h`
// (aspect preserved). Returns the drawn width.
int drawBtn(UiEngine& e, const std::string& key, int cx, int cy, int h) {
    const std::string name = btnIconName(key);
    if (name.empty()) return h;
    int iw = h, ih = h;
    if (e.imageSize(name, iw, ih) && ih > 0) {
        iw = std::max(8, h * iw / ih);
        ih = h;
    }
    e.drawImage(name, cx - iw / 2, cy - ih / 2, iw, ih);
    return iw;
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

// ---- بيانات Supabase المرتبطة بالواجهة (اختيارية) -------------------------
namespace {
HeroInfo gHeroes[3];
CardInfo gExCards[3];
CardInfo gGridCards[5];
std::string gDetailTitle;

const HeroInfo& heroAt(int i) {
    static const HeroInfo kEmpty;
    return (i >= 0 && i < 3) ? gHeroes[i] : kEmpty;
}
const CardInfo& exAt(int i) {
    static const CardInfo kEmpty;
    return (i >= 0 && i < 3) ? gExCards[i] : kEmpty;
}
const CardInfo& gridAt(int i) {
    static const CardInfo kEmpty;
    return (i >= 0 && i < 5) ? gGridCards[i] : kEmpty;
}
} // namespace

void setServerContent(const HeroInfo* heroes, const CardInfo* exCards, const CardInfo* gridCards) {
    for (int i = 0; i < 3; ++i) {
        gHeroes[i] = heroes ? heroes[i] : HeroInfo{};
        gExCards[i] = exCards ? exCards[i] : CardInfo{};
    }
    for (int i = 0; i < 5; ++i) gGridCards[i] = gridCards ? gridCards[i] : CardInfo{};
}

void setDetailTitle(const std::string& title) { gDetailTitle = title; }

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
        drawBtn(e, "\xE2\x96\xA1", cx + chipW - 30, cy + 31, 32);
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
        const int iw = drawBtn(e, it.first, x + 28, 1032, 38);
        const std::string label = tr(it.second, langCode);
        const int lx = x + 30 + iw / 2 + 16;
        e.drawText(label, lx, 1018, 24, SDL_Color{196, 209, 232, 255},
                   FontRole::Body, false, langCode == "ar");
        x = lx + e.measureText(label, 24, FontRole::Body, false) + 44;
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
    e.drawText(gDetailTitle.empty() ? tr("install_demo_title", langCode) : gDetailTitle,
               rightX, topY + 4, 48, kWHT,
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
                      {"\xE2\x96\xA1", "save"},
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
    x += drawBtn(e, "L1", x + 26, 60, 36) / 2 + 36;
    for (int i = 0; i < 5; ++i) {
        const std::string label = tr(keys[i], langCode);
        const int tw = e.measureText(label, 30, FontRole::Body, langCode == "ar");
        const bool on = (i == activeTab);
        e.drawText(label, x, 42, 30, on ? kWHT : SDL_Color{185, 198, 220, 255},
                   FontRole::Body, false, langCode == "ar");
        if (on) e.fillRect(x, 84, tw, 5, kBLl);
        x += tw + 34;
    }
    drawBtn(e, "R1", x + 26, 60, 36);

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



// ============================================================================
//  Interactive screens (every button navigates / acts — nothing decorative)
//  Layout metrics from the design css (.h2wrap .exg .chipsrow .below3 .stbody
//  .rm2 .wl2 .sr .dlg .emp).
// ============================================================================

void drawCodeLink(UiEngine& e, const std::string& code, int secondsLeft,
                  bool linked, const std::string& langCode) {
    const bool rtl = langCode == "ar";
    e.clear(kBG);
    drawTopNav(e, 4, langCode, 1250);

    // headline + steps (left)
    e.drawText(tr("sign_in_code", langCode), 96, 170, 54, kWHT, FontRole::Pixel, false, rtl, 800);
    struct Step { const char* icon; const char* text; };
    const Step steps[3] = {
        {"phone", "Open AddonStation on your phone or PC"},
        {"gear", "Go to Settings, then \"Link a console\""},
        {"key", "Enter this code"},
    };
    for (int i = 0; i < 3; ++i) {
        const int y = 280 + i * 110;
        e.drawRoundRect(96, y, 760, 88, 22, kSF);
        e.drawRoundBorder(96, y, 760, 88, 22, 2, kBD);
        e.drawCircle(96 + 48, y + 44, 22, kBL);
        e.drawTextCentered(std::to_string(i + 1), 96 + 48, y + 44, 24, kWHT, FontRole::Body, false);
        e.drawText(steps[i].text, 96 + 96, y + 28, 26, SDL_Color{196, 209, 232, 255},
                   FontRole::Body, false, false, 640);
    }

    // code panel (right)
    const int px = 1050, py = 250, pw = 700, ph = 380;
    e.dropShadow(px, py, pw, ph, 36, 18, 130);
    e.drawRoundRect(px, py, pw, ph, 36, SDL_Color{14, 21, 38, 255});
    e.drawRoundBorder(px, py, pw, ph, 36, 2, kBD);
    e.drawTextCentered(tr("choose_language", langCode) == "choose_language" ? "YOUR CODE" : "YOUR CODE",
                       px + pw / 2, py + 26, 22, kMU, FontRole::Body, false);
    // big F77 code
    e.drawTextCentered(code, px + pw / 2, py + 90, 88, kWHT, FontRole::Pixel, false);
    if (linked) {
        e.drawCircle(px + pw / 2, py + 250, 40, SDL_Color{63, 191, 106, 70});
        e.drawTextCentered("\xE2\x9C\x93", px + pw / 2, py + 232, 48, kGRN2, FontRole::Body, false);
        e.drawTextCentered("LINKED!", px + pw / 2, py + 305, 30, kGRN2, FontRole::Body, false);
    } else {
        char buf[64];
        std::snprintf(buf, sizeof(buf), "Expires in %d:%02d", secondsLeft / 60, secondsLeft % 60);
        e.drawTextCentered(buf, px + pw / 2, py + 240, 26, kMU, FontRole::Body, false);
        // waiting pill
        e.drawRoundRect(px + 180, py + 290, 340, 56, 28, kSF);
        e.drawRoundBorder(px + 180, py + 290, 340, 56, 28, 2, kBD);
        e.drawTextCentered("Waiting for you...", px + pw / 2, py + 304, 24,
                           SDL_Color{156, 193, 255, 255}, FontRole::Body, false);
    }

    // New code button
    const int bx = 1050, by = 670, bw = 700, bh = 88;
    e.dropShadow(bx, by, bw, bh, 26, 12, 110);
    e.drawRoundGradD(bx, by, bw, bh, 26, kBL, kBL2);
    e.drawTextCentered(tr("cancel", langCode) == "cancel" ? "New code" : "New code",
                       bx + bw / 2, by + bh / 2, 32, kWHT, FontRole::Body, false);
    focusRing(e, bx, by, bw, bh, 26);

    drawLegendBar(e, {{"\xE2\x9C\x95", "select"}, {"\xE2\x97\x8B", "back"}}, langCode);
}

void drawEmailSignin(UiEngine& e, int focus, const std::string& email,
                     const std::string& pass, bool showError,
                     const std::string& langCode) {
    const bool rtl = langCode == "ar";
    e.clear(kBG);
    e.drawImage("bg_splash", 0, 0, 1920, 1080);
    e.fillRect(0, 0, 1920, 1080, SDL_Color{5, 10, 24, 165});

    // .emp card — width 880 top 170 r40
    const int cx = 960 - 440, cy = 150, cw = 880;
    e.dropShadow(cx, cy, cw, showError ? 720 : 660, 40, 22, 160);
    e.drawRoundRect(cx, cy, cw, showError ? 720 : 660, 40, SDL_Color{14, 21, 38, 232});
    e.drawRoundBorder(cx, cy, cw, showError ? 720 : 660, 40, 2, SDL_Color{255, 255, 255, 42});
    e.drawText(tr("sign_in", langCode), cx + 50, cy + 36, 54, kWHT, FontRole::Pixel, false, rtl);

    int y = cy + 130;
    if (showError) {
        // .errb
        e.drawRoundRect(cx + 50, y, cw - 100, 64, 20, SDL_Color{255, 107, 120, 36});
        e.drawRoundBorder(cx + 50, y, cw - 100, 64, 20, 2, SDL_Color{255, 107, 120, 128});
        e.drawText("!  Wrong email or password. Try again.", cx + 74, y + 16, 26,
                   SDL_Color{255, 135, 145, 255}, FontRole::Body, false, false, cw - 160);
        y += 92;
    }

    auto field = [&](int idx, const char* label, const std::string& value, int fy) {
        e.drawText(label, cx + 50, fy, 24, kMU, FontRole::Body, false, false);
        const int iy = fy + 38;
        e.drawRoundRect(cx + 50, iy, cw - 100, 96, 24, kSF);
        e.drawRoundBorder(cx + 50, iy, cw - 100, 96, 24, 2,
                          showError ? SDL_Color{255, 107, 120, 255} : kBD);
        e.drawText(value.empty() ? (idx == 0 ? "name@example.com" : "..........") : value,
                   cx + 130, iy + 28, 32, value.empty() ? SDL_Color{124, 140, 168, 255} : kWHT,
                   FontRole::Body, false, false, cw - 320);
        e.drawText("Press \xE2\x9C\x95 to type", cx + cw - 290, iy + 36, 20,
                   SDL_Color{124, 140, 168, 255}, FontRole::Body, false, false);
        if (focus == idx) focusRing(e, cx + 50, iy, cw - 100, 96, 24);
    };
    field(0, "Email", email, y);
    field(1, "Password", pass, y + 160);

    // sign-in button
    const int by = y + 340;
    e.dropShadow(cx + 50, by, cw - 100, 88, 26, 12, 110);
    e.drawRoundGradD(cx + 50, by, cw - 100, 88, 26, kBL, kBL2);
    e.drawTextCentered(tr("sign_in", langCode), 960, by + 44, 32, kWHT, FontRole::Body, rtl);
    if (focus == 2) focusRing(e, cx + 50, by, cw - 100, 88, 26);
    e.drawTextCentered("Forgot your password? Reset it on your phone.", 960, by + 118, 22,
                       SDL_Color{143, 162, 194, 255}, FontRole::Body, false);

    drawLegendBar(e, {{"\xE2\x9C\x95", "select"}, {"\xE2\x97\x8B", "back"}}, langCode);
}

void drawHome(UiEngine& e, int focus, int heroIndex, int filterIndex, const std::string& langCode) {
    const bool rtl = langCode == "ar";
    e.clear(kBG);
    drawTopNav(e, 0, langCode, 1250);

    // .h2wrap — left 96 right 96 top 126 h520, grid 1040px + 1fr gap 28
    const int hx = 96, hy = 126, hw = 1040, hh = 520;
    const char* heroArt = heroIndex == 0 ? "bg_winter" : (heroIndex == 1 ? "bg_sunset" : "bg_creator");
    e.dropShadow(hx, hy, hw, hh, 34, 18, 130);
    e.drawImageRounded(heroArt, hx, hy, hw, hh, 34);
    e.drawRoundBorder(hx, hy, hw, hh, 34, 2, SDL_Color{255, 255, 255, 32});
    // left vignette .hv1
    e.fillGradH(hx, hy, hw, hh, SDL_Color{5, 10, 24, 235}, SDL_Color{5, 10, 24, 20});
    // kicker pill
    e.drawRoundRect(hx + 56, hy + 44, 320, 46, 23, SDL_Color{156, 220, 255, 40});
    e.drawRoundBorder(hx + 56, hy + 44, 320, 46, 23, 2, SDL_Color{156, 220, 255, 128});
    e.drawText(heroAt(heroIndex).kicker.empty() ? "*  LIMITED TIME" : heroAt(heroIndex).kicker,
               hx + 78, hy + 52, 22, SDL_Color{189, 230, 255, 255},
               FontRole::Body, false, false);
    // headline (winter gradient)
    std::string heroTitle = heroAt(heroIndex).title;
    if (heroTitle.empty()) {
        heroTitle = heroIndex == 0 ? "WINTER SALE" : (heroIndex == 1 ? "SUNSET VILLAGE" : "CREATOR SPOTLIGHT");
    }
    e.drawTextGradV(heroTitle, hx + 56, hy + 110, 72, kWHT, SDL_Color{111, 178, 255, 255},
                    FontRole::Pixel, false, false);
    e.drawText(heroAt(heroIndex).subtitle.empty()
                   ? "Up to 50% off the best addons and worlds" : heroAt(heroIndex).subtitle,
               hx + 56, hy + 210, 30,
               SDL_Color{230, 238, 251, 255}, FontRole::Body, false, false);
    // -50% badge + countdown
    e.drawRoundGradD(hx + 56, hy + 270, 170, 78, 20, SDL_Color{255, 92, 122, 255},
                     SDL_Color{255, 61, 107, 255});
    e.drawTextCentered(heroAt(heroIndex).badge.empty() ? "-50%" : heroAt(heroIndex).badge,
                       hx + 141, hy + 286, 44, kWHT, FontRole::Body, false);
    const char* cdVals[3] = {"03", "12", "45"};
    const char* cdLbls[3] = {"DAYS", "HRS", "MIN"};
    for (int i = 0; i < 3; ++i) {
        const int bx2 = hx + 250 + i * 102;
        e.drawRoundRect(bx2, hy + 270, 88, 78, 16, SDL_Color{10, 24, 52, 140});
        e.drawRoundBorder(bx2, hy + 270, 88, 78, 16, 2, SDL_Color{156, 220, 255, 100});
        e.drawTextCentered(cdVals[i], bx2 + 44, hy + 280, 34, kWHT, FontRole::Pixel, false);
        e.drawTextCentered(cdLbls[i], bx2 + 44, hy + 324, 13, SDL_Color{156, 220, 255, 255},
                           FontRole::Body, false);
    }
    // hero buttons
    const int hbY = hy + 390;
    e.dropShadow(hx + 56, hbY, 280, 78, 22, 10, 100);
    e.drawRoundGradD(hx + 56, hbY, 280, 78, 22, kBL, kBL2);
    e.drawTextCentered(tr("nav_deals", langCode), hx + 196, hbY + 20, 30, kWHT, FontRole::Body, rtl);
    e.drawRoundRect(hx + 356, hbY, 200, 78, 22, SDL_Color{255, 255, 255, 30});
    e.drawRoundBorder(hx + 356, hbY, 200, 78, 22, 3, SDL_Color{255, 255, 255, 80});
    e.drawTextCentered(tr("back", langCode), hx + 456, hbY + 22, 30, kWHT, FontRole::Body, rtl);
    if (focus == 0) focusRing(e, hx + 56, hbY, 280, 78, 22);
    if (focus == 1) focusRing(e, hx + 356, hbY, 200, 78, 22);

    // exclusive column (right)
    const int exX = 96 + 1040 + 28, exW = 1920 - 96 - exX;
    struct EX { int y, h; const char* art; const char* title; const char* price; };
    const EX exs[3] = {
        {126, 300, "bg_sunset", "[Exclusive addon]", "250"},
        {438, 192, "bg_creator", "[Exclusive world]", "180"},
        {438, 192, "bg_winter", "[Exclusive pack]", "120"},
    };
    auto drawEx = [&](int i, int x, int y, int w, int h) {
        const EX& ex = exs[i];
        e.drawImageRounded(ex.art, x, y, w, h, 28);
        e.drawRoundBorder(x, y, w, h, 28, 2, SDL_Color{255, 255, 255, 36});
        e.fillGradV(x, y + h / 2, w, h / 2, SDL_Color{5, 10, 24, 0}, SDL_Color{5, 10, 24, 210});
        // EXCLUSIVE chip + price
        e.drawRoundRect(x + 16, y + 16, 150, 40, 10, kGD);
        e.drawText("EXCLUSIVE", x + 28, y + 24, 19, SDL_Color{27, 20, 5, 255}, FontRole::Body, false, false);
        e.drawImage("icon_coin", x + w - 110, y + 18, 26, 26);
        e.drawText(exAt(i).price.empty() ? ex.price : exAt(i).price,
                   x + w - 76, y + 22, 24, kWHT, FontRole::Body, false, false);
        e.drawText(exAt(i).title.empty() ? ex.title : exAt(i).title,
                   x + 20, y + h - 64, 26, kWHT, FontRole::Body, false, false, w - 40);
    };
    drawEx(0, exX, 126, exW, 300);
    drawEx(1, exX, 438, (exW - 28) / 2, 192);
    drawEx(2, exX + (exW - 28) / 2 + 28, 438, (exW - 28) / 2, 192);
    if (focus == 2) focusRing(e, exX, 126, exW, 300, 28);
    if (focus == 3) focusRing(e, exX, 438, (exW - 28) / 2, 192, 28);
    if (focus == 4) focusRing(e, exX + (exW - 28) / 2 + 28, 438, (exW - 28) / 2, 192, 28);

    // offer pager dots under the banner (L2/R2 change the slide)
    {
        const int dotsW = 3 * 30;
        int dx = hx + (hw - dotsW) / 2;
        for (int i = 0; i < 3; ++i) {
            const bool on = (i == (heroIndex % 3));
            e.drawCircle(dx + 15, 670, on ? 9 : 6,
                         on ? kBLl : SDL_Color{255, 255, 255, 70});
            dx += 30;
        }
    }

    // chips row (top 700) — one active filter at a time
    const char* chipKeys[7] = {"open", "nav_store", "nav_home", "installed", "nav_deals", "nav_settings", "nav_chat"};
    const char* chipLbls[7] = {"All", "Addons", "Worlds", "Free", "Epic", "Legendary", "New"};
    int cxp = 96;
    for (int i = 0; i < 6; ++i) {
        const int cw = e.measureText(chipLbls[i], 24, FontRole::Body, false) + 56;
        const bool active = (i == filterIndex);
        if (active) {
            e.drawRoundRect(cxp, 700, cw, 62, 31, SDL_Color{46, 123, 239, 255});
            e.drawRoundBorder(cxp, 700, cw, 62, 31, 2, SDL_Color{156, 193, 255, 255});
            e.drawTextCentered(chipLbls[i], cxp + cw / 2, 714, 24, kWHT, FontRole::Body, false);
        } else {
            e.drawRoundRect(cxp, 700, cw, 62, 31, kSF);
            e.drawRoundBorder(cxp, 700, cw, 62, 31, 2, kBD);
            e.drawTextCentered(chipLbls[i], cxp + cw / 2, 714, 24, SDL_Color{196, 209, 232, 255},
                               FontRole::Body, false);
        }
        if (focus == 5 + i) focusRing(e, cxp, 700, cw, 62, 31);
        cxp += cw + 16;
    }

    // recommended row (top 790)
    e.drawText(tr("nav_home", langCode) == "nav_home" ? "Recommended for you" : "Recommended for you",
               96, 790, 34, kWHT, FontRole::Body, false, false);
    for (int i = 0; i < 5; ++i) {
        const int x = 96 + i * 348;
        e.drawRoundRect(x, 840, 318, 178, 22, kSF);
        e.drawRoundBorder(x, 840, 318, 178, 22, 2, kBD);
        e.drawImageRounded(i == 0 ? "cover_0" : (i == 1 ? "cover_1" : (i == 2 ? "cover_2" : (i == 3 ? "cover_3" : "cover_4"))),
                           x + 6, 846, 306, 120, 16);
        e.drawText(gridAt(i).title.empty() ? "[Addon name]" : gridAt(i).title,
                   x + 14, 976, 24, kWHT, FontRole::Body, false, false);
        if (focus == 12 + i) focusRing(e, x, 840, 318, 178, 22);
    }

    drawLegendBar(e, {{"\xE2\x9C\x95", "select"}, {"\xE2\x97\x8B", "back"},
                      {"L2", "prev_offer"}, {"R2", "next_offer"}}, langCode);
}

void drawStore(UiEngine& e, int focus, int filterIndex, const std::string& langCode) {
    const bool rtl = langCode == "ar";
    e.clear(kBG);
    drawTopNav(e, 2, langCode, 1250);

    // promo strip
    e.drawImageRounded("bg_winter", 96, 130, 1728, 210, 30);
    e.fillGradH(96, 130, 1728, 210, SDL_Color{5, 10, 24, 230}, SDL_Color{5, 10, 24, 40});
    e.drawTextGradV(heroAt(0).title.empty() ? "WINTER SALE" : heroAt(0).title,
                    148, 170, 60, kWHT, SDL_Color{111, 178, 255, 255},
                    FontRole::Pixel, false, false);
    e.drawText(heroAt(0).subtitle.empty() ? "Up to 50% off the best addons and worlds" : heroAt(0).subtitle,
               148, 252, 26,
               SDL_Color{230, 238, 251, 255}, FontRole::Body, false, false);
    e.drawRoundGradD(1480, 190, 300, 88, 26, kBL, kBL2);
    e.drawTextCentered(tr("shop_sale", langCode), 1630, 214, 28, kWHT, FontRole::Body, rtl);
    if (focus == 8) focusRing(e, 1480, 190, 300, 88, 26);

    // filter chips
    const char* filters[3] = {"Addons", "Worlds", "Newest"};
    for (int i = 0; i < 3; ++i) {
        const int cw = e.measureText(filters[i], 24, FontRole::Body, false) + 56;
        const int x = 96 + [&]{ int s = 0; for (int j = 0; j < i; ++j) s += e.measureText(filters[j], 24, FontRole::Body, false) + 56 + 14; return s; }();
        const bool active = (i == filterIndex);
        if (active) {
            e.drawRoundRect(x, 370, cw, 62, 31, SDL_Color{46, 123, 239, 255});
            e.drawRoundBorder(x, 370, cw, 62, 31, 2, SDL_Color{156, 193, 255, 255});
            e.drawTextCentered(filters[i], x + cw / 2, 384, 24, kWHT, FontRole::Body, false);
        } else {
            e.drawRoundRect(x, 370, cw, 62, 31, kSF);
            e.drawRoundBorder(x, 370, cw, 62, 31, 2, kBD);
            e.drawTextCentered(filters[i], x + cw / 2, 384, 24, SDL_Color{196, 209, 232, 255},
                               FontRole::Body, false);
        }
        if (focus == 9 + i) focusRing(e, x, 370, cw, 62, 31);
    }

    // 5-card grid
    const char* badges[5] = {"NEW", "-40%", "EPIC", "", "LEGENDARY"};
    const SDL_Color badgeCol[5] = {{63,191,106,255}, {255,92,122,255}, {155,107,255,255},
                                   {240,194,94,255}, {240,194,94,255}};
    for (int i = 0; i < 5; ++i) {
        const int x = 96 + i * 348;
        e.drawRoundRect(x, 470, 318, 430, 22, kSF);
        e.drawRoundBorder(x, 470, 318, 430, 22, 2, kBD);
        e.drawImageRounded(i == 0 ? "cover_0" : (i == 1 ? "cover_1" : (i == 2 ? "cover_2" : (i == 3 ? "cover_3" : "cover_4"))),
                           x + 6, 476, 306, 200, 16);
        if (badges[i][0]) {
            e.drawRoundRect(x + 16, 486, 120, 38, 10, badgeCol[i]);
            e.drawTextCentered(badges[i], x + 76, 492, 20,
                               i == 4 ? SDL_Color{27, 20, 5, 255} : kWHT, FontRole::Body, false);
        }
        e.drawText(gridAt(i).title.empty() ? "[Addon name]" : gridAt(i).title,
                   x + 16, 692, 26, kWHT, FontRole::Body, false, false);
        e.drawText(gridAt(i).creator.empty() ? "By [Creator]" : gridAt(i).creator,
                   x + 16, 730, 20, kMU, FontRole::Body, false, false);
        e.drawImage("icon_coin", x + 16, 770, 26, 26);
        e.drawText(gridAt(i).price.empty() ? (i == 3 ? "0" : "200") : gridAt(i).price,
                   x + 52, 772, 26, kGD, FontRole::Body, false, false);
        if (focus == i) focusRing(e, x, 470, 318, 430, 22);
    }

    drawLegendBar(e, {{"\xE2\x9C\x95", "open"}, {"\xE2\x97\x8B", "back"},
                      {"\xE2\x96\xA1", "filter"}, {"\xE2\x96\xB3", "sort"}}, langCode);
}

// Deals campaign landing (design section 6): header band 430 + chips + 5-col grid
void drawDeals(UiEngine& e, int focus, int filterIndex, const std::string& langCode) {
    const bool rtl = langCode == "ar";
    e.clear(kBG);
    drawTopNav(e, 1, langCode, 1250);

    // header band y130 h430 radius 30 — campaign art + label + F77 title + countdown
    e.dropShadow(96, 130, 1728, 430, 30, 20, 140);
    e.drawImageRounded("bg_winter", 96, 130, 1728, 430, 30);
    e.drawRoundBorder(96, 130, 1728, 430, 30, 2, SDL_Color{255, 255, 255, 32});
    e.fillGradH(96, 130, 1728, 430, SDL_Color{5, 10, 24, 235}, SDL_Color{5, 10, 24, 25});

    e.drawRoundRect(152, 174, 320, 46, 23, SDL_Color{156, 220, 255, 40});
    e.drawRoundBorder(152, 174, 320, 46, 23, 2, SDL_Color{156, 220, 255, 128});
    e.drawText(heroAt(0).kicker.empty() ? "*  LIMITED TIME" : heroAt(0).kicker,
               174, 182, 22, SDL_Color{189, 230, 255, 255},
               FontRole::Body, false, false);
    e.drawTextGradV(heroAt(0).title.empty() ? "WINTER SALE" : heroAt(0).title,
                    152, 236, 80, kWHT, SDL_Color{111, 178, 255, 255},
                    FontRole::Pixel, false, false);
    e.drawText(heroAt(0).subtitle.empty() ? tr("up_to_50", langCode) : heroAt(0).subtitle,
               152, 348, 30, SDL_Color{230, 238, 251, 255},
               FontRole::Body, false, rtl, 900);
    e.drawRoundGradD(152, 404, 170, 78, 20, SDL_Color{255, 92, 122, 255},
                     SDL_Color{255, 61, 107, 255});
    e.drawTextCentered(heroAt(0).badge.empty() ? "-50%" : heroAt(0).badge,
                       237, 420, 44, kWHT, FontRole::Body, false);
    const char* cdVals[3] = {"03", "12", "45"};
    const char* cdLbls[3] = {"DAYS", "HRS", "MIN"};
    for (int i = 0; i < 3; ++i) {
        const int bx2 = 346 + i * 102;
        e.drawRoundRect(bx2, 404, 88, 78, 16, SDL_Color{10, 24, 52, 140});
        e.drawRoundBorder(bx2, 404, 88, 78, 16, 2, SDL_Color{156, 220, 255, 100});
        e.drawTextCentered(cdVals[i], bx2 + 44, 414, 34, kWHT, FontRole::Pixel, false);
        e.drawTextCentered(cdLbls[i], bx2 + 44, 458, 13, SDL_Color{156, 220, 255, 255},
                           FontRole::Body, false);
    }

    // filter chips y590 — one active at a time
    const char* chips[5] = {"All", "Addons", "Worlds", "Under 100 AC", "Biggest discount"};
    int cxp = 96;
    for (int i = 0; i < 5; ++i) {
        const int cw = e.measureText(chips[i], 24, FontRole::Body, false) + 56;
        const bool active = (i == filterIndex);
        if (active) {
            e.drawRoundRect(cxp, 590, cw, 62, 31, SDL_Color{46, 123, 239, 255});
            e.drawRoundBorder(cxp, 590, cw, 62, 31, 2, SDL_Color{156, 193, 255, 255});
            e.drawTextCentered(chips[i], cxp + cw / 2, 604, 24, kWHT, FontRole::Body, false);
        } else {
            e.drawRoundRect(cxp, 590, cw, 62, 31, kSF);
            e.drawRoundBorder(cxp, 590, cw, 62, 31, 2, kBD);
            e.drawTextCentered(chips[i], cxp + cw / 2, 604, 24, SDL_Color{196, 209, 232, 255},
                               FontRole::Body, false);
        }
        if (focus == i) focusRing(e, cxp, 590, cw, 62, 31);
        cxp += cw + 16;
    }

    // 5-card grid y680 with discount tags + price rows
    const char* badges[5] = {"-50%", "-40%", "-30%", "-25%", "-20%"};
    const char* prices[5] = {"75", "120", "140", "0", "200"};
    const char* olds[5] = {"150", "200", "200", "", "250"};
    for (int i = 0; i < 5; ++i) {
        const int x = 96 + i * 348;
        e.drawRoundRect(x, 680, 318, 286, 22, kSF);
        e.drawRoundBorder(x, 680, 318, 286, 22, 2, kBD);
        e.drawImageRounded(i == 0 ? "cover_0" : (i == 1 ? "cover_1" : (i == 2 ? "cover_2" : (i == 3 ? "cover_3" : "cover_4"))),
                           x + 6, 686, 306, 150, 16);
        e.drawRoundRect(x + 16, 696, 110, 38, 10, SDL_Color{255, 92, 122, 255});
        e.drawTextCentered(gridAt(i).badge.empty() ? badges[i] : gridAt(i).badge,
                           x + 71, 702, 20, kWHT, FontRole::Body, false);
        e.drawText(gridAt(i).title.empty() ? "[Addon name]" : gridAt(i).title,
                   x + 16, 852, 26, kWHT, FontRole::Body, false, false);
        e.drawImage("icon_coin", x + 16, 896, 26, 26);
        e.drawText(gridAt(i).price.empty() ? prices[i] : gridAt(i).price,
                   x + 52, 898, 26, kGD, FontRole::Body, false, false);
        const char* oldP = gridAt(i).oldPrice.empty() ? olds[i] : gridAt(i).oldPrice.c_str();
        if (oldP[0]) {
            const int ow = e.measureText(oldP, 20, FontRole::Body, false);
            e.drawText(oldP, x + 130, 902, 20, kMU, FontRole::Body, false, false);
            e.fillRect(x + 128, 912, ow + 4, 2, withA(kMU, 220));
        }
        if (focus == 5 + i) focusRing(e, x, 680, 318, 286, 22);
    }

    drawLegendBar(e, {{"\xE2\x9C\x95", "open"}, {"\xE2\x97\x8B", "back"},
                      {"\xE2\x96\xA1", "filter"}, {"\xE2\x96\xB3", "sort"}}, langCode);
}

void drawChat(UiEngine& e, int focus, bool roomOpen, const std::string& langCode) {
    const bool rtl = langCode == "ar";
    e.clear(kBG);
    drawTopNav(e, 3, langCode, 1250);

    e.drawText("Community", 96, 150, 44, kWHT, FontRole::Pixel, false, false);
    e.drawText("Community rules - Breaking them can lead to a mute or a ban", 96, 210, 22,
               SDL_Color{240, 194, 94, 220}, FontRole::Body, false, false);

    struct Room { const char* name; const char* art; int online; };
    const Room rooms[5] = {
        {"General", "cover_0", 12}, {"Minecraft", "cover_1", 8},
        {"Creator", "cover_2", 12}, {"Support", "cover_3", 3},
        {"Social Media Makers", "cover_4", 6},
    };
    const int listX = 96, listW = 760;
    for (int i = 0; i < 5; ++i) {
        const int y = 260 + i * 118;
        e.drawRoundRect(listX, y, listW, 100, 22, kSF);
        e.drawRoundBorder(listX, y, listW, 100, 22, 2, kBD);
        e.drawImageRounded(rooms[i].art, listX + 14, y + 14, 72, 72, 36);
        e.drawText(rooms[i].name, listX + 110, y + 18, 28, kWHT, FontRole::Body, false, false);
        e.drawText("[Name]: [Last message]", listX + 110, y + 58, 20, kMU,
                   FontRole::Body, false, false);
        e.drawText("12:41", listX + listW - 90, y + 20, 20, kMU, FontRole::Body, false, false);
        if (focus == i && !roomOpen) focusRing(e, listX, y, listW, 100, 22);
    }

    // room panel (right)
    const int rx = 900, rw = 1920 - 96 - rx;
    e.drawRoundRect(rx, 260, rw, 590, 36, kSF);
    e.drawRoundBorder(rx, 260, rw, 590, 36, 2, kBD);
    e.drawImageRounded("cover_0", rx + 24, 284, 78, 78, 39);
    e.drawText(roomOpen ? "General" : "General", rx + 126, 292, 34, kWHT, FontRole::Pixel, false, false);
    e.drawText("12 online", rx + 126, 336, 22, kGRN2, FontRole::Body, false, false);
    // pinned
    e.drawRoundRect(rx + 24, 392, rw - 48, 58, 20, SDL_Color{240, 194, 94, 26});
    e.drawRoundBorder(rx + 24, 392, rw - 48, 58, 20, 2, SDL_Color{240, 194, 94, 100});
    e.drawText("[Pinned message text]", rx + 50, 404, 24, SDL_Color{234, 217, 168, 255},
               FontRole::Body, false, false);
    // messages
    e.drawText("[Name]", rx + 60, 500, 22, kGRN2, FontRole::Body, false, false);
    e.drawRoundRect(rx + 40, 532, 520, 76, 28, SDL_Color{14, 21, 38, 255});
    e.drawRoundBorder(rx + 40, 532, 520, 76, 28, 2, kBD);
    e.drawText("[Message text]   12:41", rx + 66, 554, 24, SDL_Color{213, 224, 242, 255},
               FontRole::Body, false, false);
    e.drawRoundGradD(rx + rw - 420, 640, 380, 76, 28, kBL, kBL2);
    e.drawText("[Message text]   12:41", rx + rw - 394, 662, 24, kWHT, FontRole::Body, false, false);
    // composer
    e.drawRoundRect(rx + 24, 760, rw - 48, 66, 33, SDL_Color{14, 21, 38, 255});
    e.drawRoundBorder(rx + 24, 760, rw - 48, 66, 33, 2, kBD);
    e.drawText("Write a message to the room", rx + 54, 776, 24, kMU, FontRole::Body, false, false);
    if (roomOpen) focusRing(e, rx + 24, 760, rw - 48, 66, 33);

    drawLegendBar(e, {{"\xE2\x9C\x95", roomOpen ? "select" : "open"}, {"\xE2\x97\x8B", "back"}}, langCode);
}

void drawSettings(UiEngine& e, int focus, bool autoLaunchOn,
                  const std::string& accountValue, const std::string& langCode) {
    const bool rtl = langCode == "ar";
    e.clear(kBG);
    drawTopNav(e, 4, langCode, 1250);

    e.drawText("Settings", 96, 150, 44, kWHT, FontRole::Pixel, false, false);

    struct Row { std::string label; std::string value; bool danger; int toggle; };
    const Row rows[6] = {
        {tr("settings_language", langCode), "English >", false, 0},
        {tr("settings_account", langCode), accountValue, false, 0},
        {tr("autolaunch", langCode), "", false, 1},
        {tr("settings_link", langCode), "1 device >", false, 0},
        {tr("settings_about", langCode), ">", false, 0},
        {tr("settings_signout", langCode), ">", true, 0},
    };
    const int listX = 96, listW = 900;
    for (int i = 0; i < 6; ++i) {
        const int y = 230 + i * 100;
        e.drawRoundRect(listX, y, listW, 92, 26, kSF);
        e.drawRoundBorder(listX, y, listW, 92, 26, 2, kBD);
        e.drawText(rows[i].label, listX + 36, y + 28, 28,
                   rows[i].danger ? SDL_Color{255, 135, 145, 255} : kWHT,
                   FontRole::Body, false, rtl, 560);
        if (rows[i].toggle) {
            const bool on = autoLaunchOn;
            const int tx = listX + listW - 120;
            e.drawRoundRect(tx, y + 24, 84, 44, 22, on ? kBL : kBD);
            e.drawCircle(tx + (on ? 62 : 22), y + 46, 18, kWHT);
        } else {
            e.drawText(rows[i].value, listX + listW - 300, y + 32, 22, kMU,
                       FontRole::Body, true, false, 260);
        }
        if (focus == i) focusRing(e, listX, y, listW, 92, 26);
    }

    // right panel: language grid
    const int rx = 1050, rw = 1920 - 96 - rx;
    e.drawRoundRect(rx, 230, rw, 692, 32, SDL_Color{14, 21, 38, 255});
    e.drawRoundBorder(rx, 230, rw, 692, 32, 2, kBD);
    e.drawText("App language", rx + 36, 260, 36, kWHT, FontRole::Pixel, false, false);
    e.drawText("The app changes language right away. Arabic switches the whole layout to right-to-left.",
               rx + 36, 310, 22, kMU, FontRole::Body, false, false, rw - 72);
    const char* langs[8] = {"English", "\xD8\xA7\xD9\x84\xD8\xB9\xD8\xB1\xD8\xA8\xD9\x8A\xD8\xA9",
                            "Fran\xC3\xA7\x61is", "T\xC3\xBCrk\xC3\xA7\x65", "Espa\xC3\xB1ol",
                            "\xD0\xA0\xD1\x83\xD1\x81\xD1\x81\xD0\xBA\xD0\xB8\xD0\xB9",
                            "Portugu\xC3\xAAs", "\xE4\xB8\xAD\xE6\x96\x87"};
    const char* flags[8] = {"flag_en", "flag_ar", "flag_fr", "flag_tr", "flag_es", "flag_ru", "flag_pt", "flag_zh"};
    for (int i = 0; i < 8; ++i) {
        const int x = rx + 36 + (i % 2) * ((rw - 100) / 2 + 24);
        const int y = 380 + (i / 2) * 110;
        const int cw = (rw - 100) / 2;
        e.drawRoundRect(x, y, cw, 88, 22, kSF);
        e.drawRoundBorder(x, y, cw, 88, 22, 2, kBD);
        e.drawImageRounded(flags[i], x + 16, y + 20, 48, 48, 24);
        e.drawText(langs[i], x + 82, y + 28, 26, kWHT, FontRole::Body, false, i == 1, cw - 100);
    }

    drawLegendBar(e, {{"\xE2\x9C\x95", "select"}, {"\xE2\x97\x8B", "back"}}, langCode);
}

void drawConfirmDialog(UiEngine& e, const std::string& title, const std::string& sub,
                       const std::string& yesLabel, const std::string& noLabel,
                       int focus, const std::string& langCode) {
    const bool rtl = langCode == "ar";
    // backdrop = settings behind (already drawn by caller)
    e.fillRect(0, 0, 1920, 1080, SDL_Color{5, 10, 24, 150});
    const int w = 760, h = 420, x = 960 - w / 2, y = 540 - h / 2;
    e.dropShadow(x, y, w, h, 36, 26, 170);
    e.drawRoundRect(x, y, w, h, 36, SDL_Color{14, 21, 38, 255});
    e.drawRoundBorder(x, y, w, h, 36, 2, kBD);
    e.drawCircle(960, y + 70, 44, SDL_Color{255, 107, 120, 40});
    e.drawTextCentered("!", 960, y + 44, 44, SDL_Color{255, 135, 145, 255}, FontRole::Body, false);
    e.drawTextCentered(title, 960, y + 130, 40, kWHT, FontRole::Body, rtl);
    e.drawTextCentered(sub, 960, y + 186, 24, kMU, FontRole::Body, rtl);
    // buttons
    const int bw = 640;
    e.drawRoundRect(960 - bw / 2, y + 240, bw, 78, 26, SDL_Color{255, 255, 255, 26});
    e.drawRoundBorder(960 - bw / 2, y + 240, bw, 78, 26, 3, SDL_Color{255, 255, 255, 72});
    e.drawTextCentered(noLabel, 960, y + 260, 30, kWHT, FontRole::Body, rtl);
    if (focus == 0) focusRing(e, 960 - bw / 2, y + 240, bw, 78, 26);
    e.drawRoundRect(960 - bw / 2, y + 332, bw, 78, 26, SDL_Color{255, 107, 120, 45});
    e.drawRoundBorder(960 - bw / 2, y + 332, bw, 78, 26, 3, SDL_Color{255, 107, 120, 255});
    e.drawTextCentered(yesLabel, 960, y + 352, 30, SDL_Color{255, 135, 145, 255},
                       FontRole::Body, rtl);
    if (focus == 1) focusRing(e, 960 - bw / 2, y + 332, bw, 78, 26);
}

} // namespace pkgtest
