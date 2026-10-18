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
        {"search", {{"en", "Search"}, {"ar", "بحث"}}},
        {"lang_note", {{"en", "The app changes language right away. Arabic switches the whole layout to right-to-left."}, {"ar", "يتغير لغة التطبيق فوراً. العربية تقلب التصميم كله من اليمين لليسار."}}},
        {"guest_short", {{"en", "Guest"}, {"ar", "زائر"}}},
        {"signed_in", {{"en", "Signed in"}, {"ar", "مسجّل الدخول"}}},
        {"account_hint", {{"en", "Your account is linked to this console. Manage it in the AddonStation app on your phone or PC."}, {"ar", "حسابك مرتبط بهذا الجهاز. تدير حسابك من تطبيق AddonStation على جوالك أو الكمبيوتر."}}},
        {"one_device", {{"en", "1 device"}, {"ar", "جهاز واحد"}}},
        {"autolaunch_desc", {{"en", "When this is on, Minecraft opens right after an addon finishes installing. It is off by default."}, {"ar", "عند التفعيل يفتح Minecraft مباشرة بعد اكتمال تثبيت الإضافة. الخيار مُعطّل افتراضياً."}}},
        {"open_app_phone", {{"en", "Open AddonStation on your phone or PC"}, {"ar", "افتح AddonStation على جوالك أو الكمبيوتر"}}},
        {"go_settings_link", {{"en", "Go to Settings, then “Link a console”"}, {"ar", "اذهب إلى الإعدادات ثم ربط جهاز"}}},
        {"enter_code", {{"en", "Enter this code"}, {"ar", "أدخل هذا الرمز"}}},
        {"press_x_code", {{"en", "Press ✕ to get a code"}, {"ar", "اضغط ✕ للحصول على رمز"}}},
        {"version", {{"en", "Version"}, {"ar", "الإصدار"}}},
        {"credits", {{"en", "CREDITS"}, {"ar", "الشكر والتقدير"}}},
        {"signout_q", {{"en", "Sign out?"}, {"ar", "تسجيل الخروج؟"}}},
        {"signout_sub", {{"en", "You can sign in again any time with a code."}, {"ar", "تقدر تدخل مرة أخرى في أي وقت برمز."}}},
        {"profile", {{"en", "Profile"}, {"ar", "الملف الشخصي"}}},
        {"wallet", {{"en", "AC Wallet"}, {"ar", "محفظة AC"}}},
        {"plans", {{"en", "Plans"}, {"ar", "الخطط"}}},
        {"menu", {{"en", "Menu"}, {"ar", "القائمة"}}},
        {"balance", {{"en", "Your balance"}, {"ar", "رصيدك"}}},
        {"history", {{"en", "History"}, {"ar", "السجل"}}},
        {"tx_purchase", {{"en", "Purchase"}, {"ar", "شراء"}}},
        {"tx_topup", {{"en", "Top-up"}, {"ar", "شحن"}}},
        {"tx_gift", {{"en", "Gift"}, {"ar", "هدية"}}},
        {"topup_hint", {{"en", "Top up your balance from the AddonStation app on your phone or PC."}, {"ar", "اشحن رصيدك من تطبيق AddonStation على جوالك أو الكمبيوتر."}}},
        {"plan_free", {{"en", "Free"}, {"ar", "مجاني"}}},
        {"plan_epic", {{"en", "Epic"}, {"ar", "Epic"}}},
        {"plan_legendary", {{"en", "Legendary"}, {"ar", "Legendary"}}},
        {"plan_current", {{"en", "Current plan"}, {"ar", "خطتك الحالية"}}},
        {"plan_free_price", {{"en", "FREE"}, {"ar", "مجاناً"}}},
        {"pf_free_1", {{"en", "Free addons and worlds"}, {"ar", "إضافات وعوالم مجانية"}}},
        {"pf_free_2", {{"en", "Install straight to your PS4"}, {"ar", "تثبيت مباشر على جهاز PS4"}}},
        {"pf_epic_1", {{"en", "Everything in Free"}, {"ar", "كل مميزات المجاني"}}},
        {"pf_epic_2", {{"en", "Epic addons and worlds"}, {"ar", "إضافات وعوالم Epic"}}},
        {"pf_epic_3", {{"en", "Epic badge on your profile"}, {"ar", "شارة Epic على ملفك"}}},
        {"pf_leg_1", {{"en", "Everything in Epic"}, {"ar", "كل مميزات Epic"}}},
        {"pf_leg_2", {{"en", "Legendary addons and worlds"}, {"ar", "إضافات وعوالم Legendary"}}},
        {"pf_leg_3", {{"en", "Exclusive releases"}, {"ar", "إصدارات حصرية"}}},
        {"subscribe_elsewhere", {{"en", "Subscribe from your phone or PC"}, {"ar", "الاشتراك من الجوال أو الكمبيوتر"}}},
        {"scan_to_get_app", {{"en", "Scan the code to download the app"}, {"ar", "امسح الرمز لتحميل التطبيق"}}},
        {"get_app_cta", {{"en", "Get the app on your phone for the best addons"}, {"ar", "حمّل التطبيق على هاتفك للحصول على أفضل الإضافات"}}},
        {"search_hint", {{"en", "Type to search addons and worlds"}, {"ar", "اكتب للبحث عن الإضافات والعوالم"}}},
        {"popular", {{"en", "Popular searches"}, {"ar", "عمليات بحث شائعة"}}},
        {"results", {{"en", "results"}, {"ar", "نتيجة"}}},
        {"no_results", {{"en", "No results"}, {"ar", "لا نتائج"}}},
        {"no_results_sub", {{"en", "Try a different search word or browse the categories."}, {"ar", "جرّب كلمة بحث ثانية أو تصفّح الأقسام."}}},
        {"stat_downloads", {{"en", "Downloads"}, {"ar", "التنزيلات"}}},
        {"stat_saved", {{"en", "Saved"}, {"ar", "المحفوظات"}}},
        {"stat_installed", {{"en", "Installed"}, {"ar", "المثبّتة"}}},
        {"recommended", {{"en", "Recommended for you"}, {"ar", "مقترحة لك"}}},
        {"exclusive", {{"en", "EXCLUSIVE"}, {"ar", "حصري"}}},
        {"limited_time", {{"en", "LIMITED TIME"}, {"ar", "لفترة محدودة"}}},
        {"days", {{"en", "DAYS"}, {"ar", "أيام"}}},
        {"hrs", {{"en", "HRS"}, {"ar", "ساعات"}}},
        {"min", {{"en", "MIN"}, {"ar", "دقائق"}}},
        {"chip_all", {{"en", "All"}, {"ar", "الكل"}}},
        {"chip_addons", {{"en", "Addons"}, {"ar", "الإضافات"}}},
        {"chip_worlds", {{"en", "Worlds"}, {"ar", "العوالم"}}},
        {"chip_free", {{"en", "Free"}, {"ar", "مجاني"}}},
        {"chip_epic", {{"en", "Epic"}, {"ar", "Epic"}}},
        {"chip_legendary", {{"en", "Legendary"}, {"ar", "Legendary"}}},
        {"chip_new", {{"en", "New"}, {"ar", "جديد"}}},
        {"chip_under100", {{"en", "Under 100 AC"}, {"ar", "أقل من 100 AC"}}},
        {"chip_biggest", {{"en", "Biggest discount"}, {"ar", "أكبر خصم"}}},
        {"chip_newest", {{"en", "Newest"}, {"ar", "الأحدث"}}},
        {"winter_sale", {{"en", "WINTER SALE"}, {"ar", "عروض الشتاء"}}},
        {"description", {{"en", "DESCRIPTION"}, {"ar", "الوصف"}}},
        {"by", {{"en", "By"}, {"ar", "بواسطة"}}},
        {"your_code", {{"en", "YOUR CODE"}, {"ar", "رمزك"}}},
        {"expires_in", {{"en", "Expires in"}, {"ar", "ينتهي خلال"}}},
        {"waiting", {{"en", "Waiting for you..."}, {"ar", "بانتظارك..."}}},
        {"new_code", {{"en", "New code"}, {"ar", "رمز جديد"}}},
        {"linked_ok", {{"en", "LINKED!"}, {"ar", "تم الربط!"}}},
        {"email", {{"en", "Email"}, {"ar", "البريد الإلكتروني"}}},
        {"password", {{"en", "Password"}, {"ar", "كلمة المرور"}}},
        {"press_x_type", {{"en", "Press ✕ to type"}, {"ar", "اضغط ✕ للكتابة"}}},
        {"wrong_login", {{"en", "Wrong email or password. Try again."}, {"ar", "البريد أو كلمة المرور غير صحيحة. حاول مرة ثانية."}}},
        {"forgot_pw", {{"en", "Forgot your password? Reset it on your phone."}, {"ar", "نسيت كلمة المرور؟ أعد تعيينها من جوالك."}}},
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

[[maybe_unused]] float easeOutBack(float t) {
    const float c1 = 1.70158f;
    const float c3 = c1 + 1.0f;
    const float x = t - 1.0f;
    return 1.0f + c3 * x * x * x + c1 * x * x;
}

[[maybe_unused]] float clamp01(float v) {
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

[[maybe_unused]] SDL_Color withA(SDL_Color c, Uint8 a) { c.a = a; return c; }

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

// ONE solid focus ring (colour #9CC1FF, 4 px, 5 px gap) — no glow, no layers.
void focusRing(UiEngine& e, int x, int y, int w, int h, int r) {
    e.drawRoundBorder(x - 9, y - 9, w + 18, h + 18, r + 9, 4, kFOCUS);
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
[[maybe_unused]] void iconGuest(UiEngine& e, int x, int y, int s, SDL_Color c) {
    e.drawCircle(x + s / 2, y + s * 32 / 100, s * 15 / 100, c);
    e.drawRoundRect(x + s * 22 / 100, y + s * 56 / 100, s * 56 / 100, s * 28 / 100, 12, c);
}
[[maybe_unused]] void iconDownload(UiEngine& e, int x, int y, int s, SDL_Color c) {
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


// ============================================================================
//  AddonStation PS4 — UI v2 (same identity, cleaner & more modern)
//   * one solid focus ring colour (no glow)            * Home Deals Store Settings (no chat)
//   * no notification bell                              * every screen mirrors for Arabic (RTL)
//   * new screens: Search, Profile, AC Wallet, Plans, Menu (△)
// ============================================================================

namespace {

constexpr SDL_Color kBAR{14, 21, 38, 255};       // #0E1526
constexpr SDL_Color kSF2{26, 36, 56, 255};       // #1A2438
constexpr SDL_Color kCHIPTX{196, 209, 232, 255}; // #C4D1E8
constexpr SDL_Color kERT{255, 135, 145, 255};    // #FF8791
constexpr SDL_Color kEPIC{155, 107, 255, 255};   // #9B6BFF
constexpr SDL_Color kEPICT{183, 155, 255, 255};  // #B79BFF
constexpr SDL_Color kWARN{240, 160, 75, 255};    // #F0A04B
constexpr SDL_Color kSCRIM{3, 6, 14, 200};

// ---- RTL layout helper -----------------------------------------------------
struct Lay {
    bool rtl = false;
    int X(int x, int w) const { return rtl ? 1920 - x - w : x; }   // LTR left edge -> real left edge
};

int boxText(UiEngine& e, const Lay& L, const std::string& s, int x, int w, int y, int px,
            SDL_Color c, FontRole role = FontRole::Body) {
    if (!L.rtl) return e.drawText(s, x, y, px, c, role, false, false, w);
    return e.drawText(s, L.X(x, w) + w, y, px, c, role, true, true, w);
}
int endText(UiEngine& e, const Lay& L, const std::string& s, int x, int w, int y, int px,
            SDL_Color c, FontRole role = FontRole::Body) {
    const int tw = std::min(w, e.measureText(s, px, role, L.rtl));
    const int rx = L.X(x, w);
    if (!L.rtl) return e.drawText(s, rx + w - tw, y, px, c, role, false, false, w);
    return e.drawText(s, rx, y, px, c, role, false, true, w);
}
int textW(UiEngine& e, const Lay& L, const std::string& s, int px, FontRole role = FontRole::Body) {
    return e.measureText(s, px, role, L.rtl);
}
std::string num(int v) { return std::to_string(v); }

// Greedy word wrap (UTF-8 aware; per-character for CJK).
std::vector<std::string> wrapLines(UiEngine& e, const std::string& text, int px, int maxW,
                                   bool rtl, FontRole role = FontRole::Body) {
    std::vector<std::string> out, words;
    {
        std::string cur;
        auto flush = [&]() { if (!cur.empty()) { words.push_back(cur); cur.clear(); } };
        for (std::size_t i = 0; i < text.size();) {
            const unsigned char ch = static_cast<unsigned char>(text[i]);
            const std::size_t n = ch < 0x80 ? 1 : (ch >> 5) == 6 ? 2 : (ch >> 4) == 14 ? 3 : 4;
            const std::string u = text.substr(i, n);
            i += n;
            if (u == " ") { flush(); continue; }
            if (n == 3 && ch >= 0xE3 && ch <= 0xE9) { flush(); words.push_back(u); continue; }
            cur += u;
        }
        flush();
    }
    std::string line;
    for (const auto& w : words) {
        const bool cjk = w.size() == 3 && static_cast<unsigned char>(w[0]) >= 0xE3;
        const std::string cand = line.empty() ? w : (cjk ? line + w : line + " " + w);
        if (!line.empty() && e.measureText(cand, px, role, rtl) > maxW) { out.push_back(line); line = w; }
        else line = cand;
    }
    if (!line.empty()) out.push_back(line);
    return out;
}
// Draw a wrapped paragraph in box [x,x+w); returns y after the last line.
int paragraphAt(UiEngine& e, const Lay& L, const std::string& text, int x, int w, int y, int px,
                SDL_Color col, int maxLines = 99, int lineGap = 12) {
    const auto lines = wrapLines(e, text, px, w, L.rtl);
    for (std::size_t i = 0; i < lines.size() && static_cast<int>(i) < maxLines; ++i) {
        boxText(e, L, lines[i], x, w, y, px, col);
        y += px + lineGap;
    }
    return y;
}

// ---- vector icons ------------------------------------------------------------
void fillTriUp(UiEngine& e, int x, int y, int w, int h, SDL_Color c) {
    for (int i = 0; i < h; ++i) { const int rw = std::max(1, w * (i + 1) / h); e.fillRect(x + (w - rw) / 2, y + i, rw, 1, c); }
}
void fillTriDown(UiEngine& e, int x, int y, int w, int h, SDL_Color c) {
    for (int i = 0; i < h; ++i) { const int rw = std::max(1, w * (h - i) / h); e.fillRect(x + (w - rw) / 2, y + i, rw, 1, c); }
}
void fillTriRight(UiEngine& e, int x, int y, int w, int h, SDL_Color c) {
    for (int i = 0; i < w; ++i) { const int rh = std::max(1, h * (w - i) / w); e.fillRect(x + i, y + (h - rh) / 2, 1, rh, c); }
}
void chevron(UiEngine& e, int x, int y, int s, bool right, int t, SDL_Color c) {
    for (int i = 0; i <= s; ++i) {
        const int xx = right ? x + i : x + s - i;
        e.fillRect(xx, y + i, t, t, c);
        e.fillRect(xx, y + 2 * s - i, t, t, c);
    }
}
void icGlobe(UiEngine& e, int x, int y, int s, SDL_Color c) {
    e.drawRoundBorder(x, y, s, s, s / 2, 3, c);
    e.drawRoundBorder(x + s / 4, y, s / 2, s, s / 4, 2, c);
    e.fillRect(x + 2, y + s / 2 - 1, s - 4, 3, c);
}
void icPerson(UiEngine& e, int x, int y, int s, SDL_Color c) {
    e.drawCircle(x + s / 2, y + s * 32 / 100, s * 17 / 100, c);
    e.drawRoundRect(x + s * 18 / 100, y + s * 56 / 100, s * 64 / 100, s * 32 / 100, s * 15 / 100, c);
}
void icRocket(UiEngine& e, int x, int y, int s, SDL_Color c) {
    e.drawRoundBorder(x + s * 6 / 100, y + s * 6 / 100, s * 88 / 100, s * 88 / 100, s * 44 / 100, 3, c);
    fillTriRight(e, x + s * 38 / 100, y + s * 30 / 100, s * 30 / 100, s * 40 / 100, c);
}
void icLink(UiEngine& e, int x, int y, int s, SDL_Color c) {
    e.drawRoundBorder(x + s * 2 / 100, y + s * 30 / 100, s * 54 / 100, s * 40 / 100, s * 20 / 100, 4, c);
    e.drawRoundBorder(x + s * 44 / 100, y + s * 30 / 100, s * 54 / 100, s * 40 / 100, s * 20 / 100, 4, c);
}
void icInfo(UiEngine& e, int x, int y, int s, SDL_Color c) {
    e.drawRoundBorder(x, y, s, s, s / 2, 3, c);
    e.drawCircle(x + s / 2, y + s * 30 / 100, std::max(2, s * 6 / 100), c);
    e.drawRoundRect(x + s / 2 - std::max(2, s * 5 / 100), y + s * 44 / 100, std::max(4, s * 10 / 100), s * 32 / 100, 2, c);
}
void icExit(UiEngine& e, int x, int y, int s, SDL_Color c) {
    e.drawRoundBorder(x + s * 10 / 100, y + s * 12 / 100, s * 42 / 100, s * 76 / 100, 6, 3, c);
    e.fillRect(x + s * 40 / 100, y + s * 47 / 100, s * 42 / 100, std::max(3, s / 14), c);
    fillTriRight(e, x + s * 72 / 100, y + s * 34 / 100, s * 22 / 100, s * 30 / 100, c);
}
[[maybe_unused]] void icLock(UiEngine& e, int x, int y, int s, SDL_Color c) {
    e.drawRoundRect(x + s * 20 / 100, y + s * 44 / 100, s * 60 / 100, s * 44 / 100, s * 10 / 100, c);
    e.drawRoundBorder(x + s * 30 / 100, y + s * 12 / 100, s * 40 / 100, s * 50 / 100, s * 20 / 100, std::max(3, s / 10), c);
}
void icCheck(UiEngine& e, int x, int y, int s, SDL_Color c) {
    for (int i = 0; i < s * 36 / 100; ++i) e.fillRect(x + s * 8 / 100 + i, y + s * 50 / 100 + i, 4, 4, c);
    for (int i = 0; i < s * 58 / 100; ++i) e.fillRect(x + s * 40 / 100 + i, y + s * 86 / 100 - i, 4, 4, c);
}
void icSearch(UiEngine& e, int x, int y, int s, SDL_Color c) {
    e.drawRoundBorder(x + s * 6 / 100, y + s * 6 / 100, s * 62 / 100, s * 62 / 100, s * 31 / 100, std::max(3, s / 9), c);
    e.drawRoundRectRotated(x + s * 79 / 100, y + s * 79 / 100, s * 36 / 100, std::max(4, s / 8), 3, 45.0f, c);
}
void icWallet(UiEngine& e, int x, int y, int s, SDL_Color c) {
    e.drawRoundBorder(x + s * 8 / 100, y + s * 22 / 100, s * 84 / 100, s * 62 / 100, s * 14 / 100, 3, c);
    e.fillRect(x + s * 8 / 100 + 4, y + s * 36 / 100, s * 84 / 100 - 8, 3, c);
    e.drawCircle(x + s * 72 / 100, y + s * 60 / 100, std::max(3, s * 6 / 100), c);
}
void icCrown(UiEngine& e, int x, int y, int s, SDL_Color c) {
    e.fillRect(x + s * 14 / 100, y + s * 66 / 100, s * 72 / 100, s * 12 / 100, c);
    fillTriUp(e, x + s * 12 / 100, y + s * 26 / 100, s * 26 / 100, s * 42 / 100, c);
    fillTriUp(e, x + s * 37 / 100, y + s * 14 / 100, s * 26 / 100, s * 54 / 100, c);
    fillTriUp(e, x + s * 62 / 100, y + s * 26 / 100, s * 26 / 100, s * 42 / 100, c);
}
void icStar(UiEngine& e, int x, int y, int s, SDL_Color c) {
    fillTriUp(e, x + s * 18 / 100, y + s * 6 / 100, s * 64 / 100, s * 52 / 100, c);
    fillTriDown(e, x + s * 18 / 100, y + s * 30 / 100, s * 64 / 100, s * 52 / 100, c);
}
[[maybe_unused]] void icHeart(UiEngine& e, int x, int y, int s, SDL_Color c) {
    e.drawCircle(x + s * 30 / 100, y + s * 34 / 100, s * 20 / 100, c);
    e.drawCircle(x + s * 70 / 100, y + s * 34 / 100, s * 20 / 100, c);
    fillTriDown(e, x + s * 10 / 100, y + s * 40 / 100, s * 80 / 100, s * 48 / 100, c);
}
void icDownloadArrow(UiEngine& e, int x, int y, int s, SDL_Color c) {
    e.fillRect(x + s / 2 - 3, y + s * 10 / 100, 6, s * 50 / 100, c);
    fillTriDown(e, x + s * 26 / 100, y + s * 46 / 100, s * 48 / 100, s * 26 / 100, c);
    e.fillRect(x + s * 16 / 100, y + s * 82 / 100, s * 68 / 100, 5, c);
}
void icMenu(UiEngine& e, int x, int y, int s, SDL_Color c) {
    for (int i = 0; i < 3; ++i) e.drawRoundRect(x + s * 14 / 100, y + s * (24 + i * 24) / 100, s * 72 / 100, std::max(4, s / 9), 3, c);
}

// ---- shared widgets ----------------------------------------------------------
void backdrop(UiEngine& e) {
    e.clear(kBG);
    e.fillGradV(0, 0, 1920, 560, SDL_Color{30, 56, 112, 62}, SDL_Color{10, 15, 28, 0});
}

int chipBox(UiEngine& e, int x, int y, int w, int h, SDL_Color bg, SDL_Color border, int bw = 2) {
    e.drawRoundRect(x, y, w, h, h / 2, bg);
    if (bw > 0) e.drawRoundBorder(x, y, w, h, h / 2, bw, border);
    return w;
}

[[maybe_unused]] void panel(UiEngine& e, int x, int y, int w, int h, int r, SDL_Color bg) {
    e.drawRoundRect(x, y, w, h, r, bg);
    e.drawRoundBorder(x, y, w, h, r, 2, kBD);
}

void avatarCircle(UiEngine& e, int x, int y, int s, bool ring) {
    if (ring) e.drawCircle(x + s / 2, y + s / 2, s / 2 + 4, kGD);
    e.drawCircle(x + s / 2, y + s / 2, s / 2, SDL_Color{60, 78, 108, 255});
    e.drawImageRounded("icon_avatar", x, y, s, s, s / 2);
}

// Section heading: blue accent bar + F77 title.
void sectionTitle(UiEngine& e, const Lay& L, const std::string& s, int x, int w, int y, int px = 34) {
    const int bx = L.X(x, 8);
    e.drawRoundRect(bx, y + 4, 8, px, 4, kBL);
    boxText(e, L, s, x + 22, w - 22, y, px, kWHT, FontRole::Pixel);
}

// style: 0 primary blue, 1 ghost, 2 gold, 3 green, 4 danger
void button(UiEngine& e, const Lay& L, int xl, int y, int w, int h, const std::string& label,
            int style, int px = 30, int radius = 26) {
    const int rx = L.X(xl, w);
    if (style == 0) { e.dropShadow(rx, y, w, h, radius, 14, 110); e.drawRoundGradD(rx, y, w, h, radius, kBL, kBL2); }
    else if (style == 2) { e.dropShadow(rx, y, w, h, radius, 14, 110); e.drawRoundGradD(rx, y, w, h, radius, kGD, SDL_Color{224, 169, 58, 255}); }
    else if (style == 3) { e.dropShadow(rx, y, w, h, radius, 14, 110); e.drawRoundGradD(rx, y, w, h, radius, kGRN, SDL_Color{46, 158, 86, 255}); }
    else if (style == 4) { e.drawRoundRect(rx, y, w, h, radius, SDL_Color{255, 107, 120, 40}); e.drawRoundBorder(rx, y, w, h, radius, 3, SDL_Color{255, 107, 120, 255}); }
    else { e.drawRoundRect(rx, y, w, h, radius, SDL_Color{255, 255, 255, 26}); e.drawRoundBorder(rx, y, w, h, radius, 3, SDL_Color{255, 255, 255, 72}); }
    const SDL_Color ink = style == 2 ? SDL_Color{27, 20, 5, 255} : (style == 4 ? kERT : kWHT);
    e.drawTextCentered(label, rx + w / 2, y + h / 2, px, ink, FontRole::Body, L.rtl);
}

// Filter chips row. focusBase = focus index of chip 0 (or -1).
void chipRow(UiEngine& e, const Lay& L, int xl, int y, const std::vector<std::string>& labels, int active,
             int focusBase, int focus, int h = 58) {
    int cx = xl;
    for (std::size_t i = 0; i < labels.size(); ++i) {
        const int cw = textW(e, L, labels[i], 24) + 56;
        const int rx = L.X(cx, cw);
        const bool on = static_cast<int>(i) == active;
        if (on) {
            e.drawRoundGradD(rx, y, cw, h, h / 2, kBL, kBL2);
        } else {
            e.drawRoundRect(rx, y, cw, h, h / 2, kSF);
            e.drawRoundBorder(rx, y, cw, h, h / 2, 2, kBD);
        }
        e.drawTextCentered(labels[i], rx + cw / 2, y + h / 2, 24, on ? kWHT : kCHIPTX, FontRole::Body, L.rtl);
        if (focusBase >= 0 && focus == focusBase + static_cast<int>(i)) focusRing(e, rx, y, cw, h, h / 2);
        cx += cw + 14;
    }
}

struct CardArgs {
    const char* art = "cover_0";
    std::string title, creator, price, oldPrice, badge;
    SDL_Color badgeCol{63, 191, 106, 255};
    bool badgeDark = false;
};

// Store / deals / recommended card.
void card(UiEngine& e, const Lay& L, int xl, int y, int w, int h, int artH, const CardArgs& a, bool focused,
          const std::string& lang) {
    const int rx = L.X(xl, w);
    e.dropShadow(rx, y, w, h, 24, 12, 90);
    e.drawRoundRect(rx, y, w, h, 24, kSF);
    e.drawRoundBorder(rx, y, w, h, 24, 2, focused ? SDL_Color{60, 86, 130, 255} : kBD);
    e.drawImageRounded(a.art, rx + 6, y + 6, w - 12, artH, 18);
    e.fillGradV(rx + 6, y + 6 + artH * 55 / 100, w - 12, artH - artH * 55 / 100, SDL_Color{5, 10, 24, 0}, SDL_Color{5, 10, 24, 150});
    if (!a.badge.empty()) {
        const int bw = std::max(84, textW(e, L, a.badge, 20) + 32);
        const int bx = L.X(xl + 18, bw);
        e.drawRoundRect(bx, y + 18, bw, 38, 12, a.badgeCol);
        e.drawTextCentered(a.badge, bx + bw / 2, y + 37, 20, a.badgeDark ? SDL_Color{27, 20, 5, 255} : kWHT, FontRole::Body, L.rtl);
    }
    boxText(e, L, a.title, xl + 18, w - 36, y + artH + 18, 26, kWHT);
    if (!a.creator.empty()) boxText(e, L, a.creator, xl + 18, w - 36, y + artH + 54, 22, kMU);
    if (h >= 400) {   // tall cards: rating + downloads row
        const int ry = y + artH + 96;
        icStar(e, L.X(xl + 18, 24), ry, 24, kGD);
        boxText(e, L, "4.8", xl + 50, 80, ry - 1, 22, kCHIPTX);
        icDownloadArrow(e, L.X(xl + 130, 24), ry, 24, kBLl);
        boxText(e, L, "1,240", xl + 162, 120, ry - 1, 22, kCHIPTX);
    }
    // price row (bottom)
    const int py = y + h - 54;
    const bool free = (a.price == "0");
    if (free) {
        boxText(e, L, tr("chip_free", lang), xl + 18, w - 36, py + 2, 26, kGRN2);
    } else {
        e.drawImage("icon_coin", L.X(xl + 18, 30), py, 30, 30);
        boxText(e, L, a.price, xl + 58, 120, py + 1, 26, kGD);
        if (!a.oldPrice.empty()) {
            const int pw = textW(e, L, a.price, 26);
            const int ox = xl + 58 + pw + 14;
            const int ow = textW(e, L, a.oldPrice, 22);
            boxText(e, L, a.oldPrice, ox, ow + 4, py + 5, 22, kMU);
            e.fillRect(L.X(ox, ow), py + 16, ow, 2, SDL_Color{157, 176, 207, 220});
        }
    }
    if (focused) focusRing(e, rx, y, w, h, 24);
}

// QR tile (white). Uses image "qr_app" from ui.pak; shows a clear placeholder when missing.
void qrTile(UiEngine& e, int x, int y, int s) {
    e.drawRoundRect(x, y, s, s, 16, kWHT);
    const int pad = s * 8 / 100;
    int iw = 0, ih = 0;
    if (e.imageSize("qr_app", iw, ih)) {
        e.drawImage("qr_app", x + pad, y + pad, s - 2 * pad, s - 2 * pad);
        return;
    }
    // placeholder: three finder patterns + dotted modules
    const SDL_Color ink{20, 26, 40, 255};
    const int u = (s - 2 * pad) / 21;
    const int ox = x + (s - u * 21) / 2, oy = y + (s - u * 21) / 2;
    auto finder = [&](int fx, int fy) {
        e.fillRect(ox + fx * u, oy + fy * u, 7 * u, 7 * u, ink);
        e.fillRect(ox + (fx + 1) * u, oy + (fy + 1) * u, 5 * u, 5 * u, kWHT);
        e.fillRect(ox + (fx + 2) * u, oy + (fy + 2) * u, 3 * u, 3 * u, ink);
    };
    finder(0, 0); finder(14, 0); finder(0, 14);
    unsigned h = 2166136261u;
    for (int r = 0; r < 21; ++r) for (int c = 0; c < 21; ++c) {
        const bool inFinder = (r < 8 && c < 8) || (r < 8 && c > 12) || (r > 12 && c < 8);
        if (inFinder) continue;
        h = h * 16777619u ^ static_cast<unsigned>(r * 31 + c);
        if ((h >> 7) & 1u) e.fillRect(ox + c * u, oy + r * u, u, u, ink);
    }
}

// "Get the app" card: QR on top, call to action below. xl/w are LTR coordinates.
void qrCard(UiEngine& e, const Lay& L, int xl, int y, int w, int h, const std::string& text, bool focusedRing = false) {
    const int rx = L.X(xl, w);
    e.drawRoundGradD(rx, y, w, h, 28, SDL_Color{22, 40, 78, 255}, SDL_Color{16, 26, 48, 255});
    e.drawRoundBorder(rx, y, w, h, 28, 2, SDL_Color{60, 90, 150, 255});
    const int qs = std::min(w - 120, h - 112);
    qrTile(e, rx + (w - qs) / 2, y + 16, qs);
    const auto lines = wrapLines(e, text, 22, w - 36, L.rtl);
    int ty = y + 16 + qs + 14;
    for (std::size_t i = 0; i < lines.size() && i < 3; ++i) {
        e.drawTextCentered(lines[i], rx + w / 2, ty + 11, 22, kWHT, FontRole::Body, L.rtl);
        ty += 28;
    }
    if (focusedRing) focusRing(e, rx, y, w, h, 28);
}

std::string fmtAC(int v) {
    char b[24];
    if (v >= 1000) std::snprintf(b, sizeof(b), "%d,%03d", v / 1000, v % 1000);
    else std::snprintf(b, sizeof(b), "%d", v);
    return b;
}

} // namespace

// ============================================================================
//  Toast / legend / top navigation
// ============================================================================
void drawToast(UiEngine& e, const std::string& text, const std::string& langCode) {
    Lay L; L.rtl = (langCode == "ar");
    const int tw = e.measureText(text, 28, FontRole::Body, L.rtl);
    const int w = tw + 150;
    const int x = 960 - w / 2, y = 1080 - 130 - 84;
    e.dropShadow(x, y, w, 84, 42, 18, 150);
    e.drawRoundRect(x, y, w, 84, 42, kSF2);
    e.drawRoundBorder(x, y, w, 84, 42, 2, kGRN);
    e.drawCircle(L.rtl ? x + w - 54 : x + 54, y + 42, 22, SDL_Color{63, 191, 106, 60});
    icCheck(e, L.rtl ? x + w - 54 - 14 : x + 54 - 14, y + 28, 28, kGRN2);
    e.drawText(text, L.rtl ? x + w - 96 : x + 96, y + 22, 28, kGRN2, FontRole::Body, L.rtl, L.rtl, tw + 10);
}

void drawLegendBar(UiEngine& e, const std::vector<std::pair<std::string, std::string>>& items,
                   const std::string& langCode) {
    Lay L; L.rtl = (langCode == "ar");
    e.fillGradV(0, 984, 1920, 96, SDL_Color{5, 8, 16, 0}, SDL_Color{5, 8, 16, 242});
    // each item: [icon 38][16][label]  gap 44 — laid out LTR, mirrored for RTL
    std::vector<int> widths;
    int total = 0;
    for (const auto& it : items) {
        const std::string label = tr(it.second, langCode);
        const int w = 38 + 14 + e.measureText(label, 24, FontRole::Body, L.rtl);
        widths.push_back(w);
        total += w + 44;
    }
    total -= 44;
    int x = L.rtl ? 1920 - 96 - total : 96;
    for (std::size_t i = 0; i < items.size(); ++i) {
        const std::string label = tr(items[i].second, langCode);
        if (!L.rtl) {
            drawBtn(e, items[i].first, x + 19, 1032, 38);
            e.drawText(label, x + 52, 1018, 24, kCHIPTX, FontRole::Body, false, false);
        } else {
            const int lw = e.measureText(label, 24, FontRole::Body, true);
            e.drawText(label, x, 1018, 24, kCHIPTX, FontRole::Body, false, true);
            drawBtn(e, items[i].first, x + lw + 14 + 19, 1032, 38);
        }
        x += widths[i] + 44;
    }
}

// Tabs: 0 Home, 1 Deals, 2 Store, 3 Settings. Any other value = no tab highlighted.
void drawTopNav(UiEngine& e, int activeTab, const std::string& langCode, int acBalance) {
    Lay L; L.rtl = (langCode == "ar");
    const int bx = 96;
    // brand
    const std::string brand = "ADDONSTATION";
    const int brandW = e.measureText(brand, 22, FontRole::Pixel, false);
    e.drawRoundRect(L.X(bx, 60), 30, 60, 60, 16, kSF);
    e.drawImageRounded("icon_app", L.X(bx, 60) + 3, 33, 54, 54, 13);
    e.drawRoundBorder(L.X(bx, 60), 30, 60, 60, 16, 3, kGD);
    e.drawText(brand, L.X(bx + 76, brandW), 46, 22, kWHT, FontRole::Pixel, false, false);

    // tab group
    const char* keys[4] = {"nav_home", "nav_deals", "nav_store", "nav_settings"};
    int tw[4], total = 0;
    for (int i = 0; i < 4; ++i) { tw[i] = textW(e, L, tr(keys[i], langCode), 28) + 52; total += tw[i]; }
    total += 3 * 6;
    int x = bx + 76 + brandW + 52;
    drawBtn(e, "L1", L.X(x, 40) + 20, 60, 36);
    x += 54;
    const int groupX = x - 8, groupW = total + 16;
    e.drawRoundRect(L.X(groupX, groupW), 28, groupW, 64, 32, SDL_Color{18, 26, 43, 225});
    e.drawRoundBorder(L.X(groupX, groupW), 28, groupW, 64, 32, 2, SDL_Color{255, 255, 255, 30});
    for (int i = 0; i < 4; ++i) {
        const int rx = L.X(x, tw[i]);
        const bool on = (i == activeTab);
        if (on) { e.drawRoundGradD(rx, 34, tw[i], 52, 26, kBL, kBL2); }
        e.drawTextCentered(tr(keys[i], langCode), rx + tw[i] / 2, 60, 28,
                           on ? kWHT : SDL_Color{185, 198, 220, 255}, FontRole::Body, L.rtl);
        x += tw[i] + 6;
    }
    x += 2;
    drawBtn(e, "R1", L.X(x, 40) + 20, 60, 36);

    // right cluster: AC pill + avatar   (LTR coords from the right edge)
    const int av = 58;
    const int avX = 1920 - 96 - av;
    avatarCircle(e, L.X(avX, av), 31, av, true);
    const std::string bal = fmtAC(acBalance);
    const int pillW = 62 + e.measureText(bal, 28, FontRole::Body, false) + 26;
    const int pillX = avX - 16 - pillW;
    const int prx = L.X(pillX, pillW);
    e.drawRoundRect(prx, 31, pillW, 58, 29, SDL_Color{26, 36, 56, 215});
    e.drawRoundBorder(prx, 31, pillW, 58, 29, 2, SDL_Color{255, 255, 255, 32});
    e.drawImage("icon_coin", L.rtl ? prx + pillW - 50 : prx + 10, 40, 40, 40);
    e.drawText(bal, L.rtl ? prx + 22 : prx + 58, 44, 28, kGD, FontRole::Body, false, false);
}

// ============================================================================
//  Welcome / language / code link / email
// ============================================================================
void drawWelcome(UiEngine& e, int focusIndex, const std::string& langCode) {
    Lay L; L.rtl = (langCode == "ar");
    const bool ar = L.rtl;
    e.drawImage("bg_welcome", 0, 0, 1920, 1080);   // same direction in every language (never mirrored)
    e.fillRect(0, 0, 1920, 1080, SDL_Color{5, 10, 24, 38});
    // The art itself is never mirrored; the text column and the dark side-gradient follow the language.
    if (L.rtl) e.fillGradH(0, 0, 1920, 1080, SDL_Color{5, 10, 24, 10}, SDL_Color{5, 10, 24, 228});
    else e.fillGradH(0, 0, 1920, 1080, SDL_Color{5, 10, 24, 228}, SDL_Color{5, 10, 24, 10});
    e.fillGradV(0, 740, 1920, 340, SDL_Color{5, 10, 24, 0}, SDL_Color{5, 10, 24, 235});

    // brand + language chip
    {
        const std::string brand = "ADDONSTATION";
        const int bw = e.measureText(brand, 30, FontRole::Pixel, false);
        const int rx = L.X(96, 68);
        e.dropShadow(rx, 36, 68, 68, 18, 12, 90);
        e.drawRoundRect(rx, 36, 68, 68, 18, kSF);
        e.drawImageRounded("icon_app", rx + 3, 39, 62, 62, 15);
        e.drawRoundBorder(rx, 36, 68, 68, 18, 3, kGD);
        e.drawText(brand, L.X(96 + 86, bw), 54, 30, kWHT, FontRole::Pixel, false, false);

        const std::string name = ar ? "\xD8\xA7\xD9\x84\xD8\xB9\xD8\xB1\xD8\xA8\xD9\x8A\xD8\xA9" : "English";
        const int cw = 230;
        const int cx = L.X(1920 - 96 - cw, cw);
        e.drawRoundRect(cx, 36, cw, 68, 34, SDL_Color{18, 26, 43, 200});
        e.drawRoundBorder(cx, 36, cw, 68, 34, 2, SDL_Color{255, 255, 255, 46});
        e.drawImageRounded(ar ? "flag_ar" : "flag_en", L.rtl ? cx + cw - 56 : cx + 12, 48, 44, 44, 22);
        e.drawText(name, L.rtl ? cx + cw - 70 : cx + 68, 54, 26, kWHT, FontRole::Body, L.rtl, ar, 100);
        drawBtn(e, "\xE2\x96\xA1", L.rtl ? cx + 30 : cx + cw - 30, 70, 32);
    }

    const int colW = 800, colY = 190;
    const int colX = 96;   // LTR coordinate: left in English, mirrored to the right in Arabic
    {   // kicker pill
        const std::string wtxt = tr("welcome", langCode);
        const int tw = textW(e, L, wtxt, 22);
        const int pw = tw + 84;
        const int rx = L.X(colX, pw);
        chipBox(e, rx, colY, pw, 46, SDL_Color{240, 194, 94, 40}, SDL_Color{240, 194, 94, 150});
        icStar(e, L.rtl ? rx + pw - 40 : rx + 16, colY + 10, 26, kGD);
        e.drawText(wtxt, L.rtl ? rx + pw - 52 : rx + 54, colY + 10, 22, kGD, FontRole::Body, L.rtl, ar);
    }
    boxText(e, L, tr("welcome_family", langCode), colX, colW, colY + 68, ar ? 70 : 62, kWHT, FontRole::Pixel);
    paragraphAt(e, L, tr("welcome_sub", langCode), colX, 760, colY + 156, 30, SDL_Color{213, 224, 242, 255}, 2, 15);

    const int bw = 700, by0 = colY + 290;
    struct WB { int y, h; };
    const WB wbs[3] = {{by0, 104}, {by0 + 124, 104}, {by0 + 248, 76}};
    for (int i = 0; i < 3; ++i) {
        const bool foc = focusIndex == i;
        const int y = wbs[i].y, h = wbs[i].h;
        const std::string title = tr(i == 0 ? "sign_in_code" : (i == 1 ? "sign_in" : "guest"), langCode);
        const std::string sub = tr(i == 0 ? "sign_in_code_sub" : (i == 1 ? "sign_in_sub" : "guest"), langCode);
        const int rx = L.X(colX, bw);
        if (i == 2) {
            const int tw = textW(e, L, title, 30);
            boxText(e, L, title, colX + 34, bw - 68, y + 16, 30, foc ? kWHT : kCHIPTX);
            const int ux = L.rtl ? rx + bw - 34 - tw : rx + 34;
            e.fillRect(ux, y + 56, tw, 3, foc ? kWHT : SDL_Color{196, 209, 232, 200});
            if (foc) focusRing(e, rx - 6, y - 4, bw + 12, h + 8, 28);
            continue;
        }
        if (i == 0) { e.dropShadow(rx, y, bw, h, 28, 16, 120); e.drawRoundGradD(rx, y, bw, h, 28, kBL, kBL2); }
        else { e.drawRoundRect(rx, y, bw, h, 28, SDL_Color{14, 21, 38, 205}); e.drawRoundBorder(rx, y, bw, h, 28, 3, SDL_Color{255, 255, 255, 72}); }
        const int tileX = L.X(colX + 30, 64);
        e.drawRoundRect(tileX, y + (h - 64) / 2, 64, 64, 20, SDL_Color{255, 255, 255, static_cast<Uint8>(i == 0 ? 52 : 34)});
        if (i == 0) iconPhone(e, tileX, y + (h - 64) / 2, 64, kWHT);
        else iconMail(e, tileX, y + (h - 64) / 2, 64, kWHT);
        const int tx = colX + 30 + 64 + 24;
        boxText(e, L, title, tx, bw - (tx - colX) - 30, y + 14, 34, kWHT);
        boxText(e, L, sub, tx, bw - (tx - colX) - 30, y + 60, 22, i == 0 ? SDL_Color{228, 238, 255, 255} : kMU);
        if (foc) focusRing(e, rx, y, bw, h, 28);
    }

    paragraphAt(e, L, tr("terms", langCode), colX, 700, 904, 21, SDL_Color{200, 212, 232, 255}, 2, 8);
    drawLegendBar(e, {{"\xE2\x9C\x95", "select"}, {"\xE2\x96\xA1", "language"}, {"OPT", "help"}}, langCode);
}

void drawLanguageDialog(UiEngine& e, int selectedLang, const std::string& langCode) {
    Lay L; L.rtl = (langCode == "ar");
    drawWelcome(e, -1, langCode);
    e.fillRect(0, 0, 1920, 1080, kSCRIM);
    const int w = 760, h = 860, x = 960 - w / 2, y = 540 - h / 2;
    e.dropShadow(x, y, w, h, 36, 30, 190);
    e.drawRoundRect(x, y, w, h, 36, kBAR);
    e.drawRoundBorder(x, y, w, h, 36, 2, kBD);
    icGlobe(e, L.X(x + 44, 44), y + 40, 44, kBLl);
    boxText(e, L, tr("choose_language", langCode), x + 44 + 62, w - 88 - 62, y + 44, 36, kWHT);

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
    const int rowH = 88, top = y + 118, rw = w - 88;
    for (int i = 0; i < 8; ++i) {
        const int ry = top + i * rowH;
        const bool sel = (i == selectedLang);
        const int rx = L.X(x + 44, rw);
        e.drawRoundRect(rx, ry, rw, rowH - 10, 24, sel ? SDL_Color{28, 60, 118, 255} : kSF);
        e.drawRoundBorder(rx, ry, rw, rowH - 10, 24, 2, sel ? kBLl : kBD);
        e.drawCircle(L.X(x + 44 + 18, 52) + 26, ry + 34, 27, SDL_Color{255, 255, 255, 30});
        e.drawImageRounded(rows[i].flag, L.X(x + 44 + 18, 52), ry + 8, 52, 52, 26);
        boxText(e, L, rows[i].native, x + 44 + 90, rw - 90 - 70, ry + 8, 28, kWHT);
        boxText(e, L, rows[i].eng, x + 44 + 90, rw - 90 - 70, ry + 42, 22, kMU);
        const int cx = L.X(x + 44 + rw - 52, 28) + 14;
        e.drawRoundBorder(cx - 14, ry + 20, 28, 28, 14, 3, sel ? kBLl : SDL_Color{90, 108, 140, 255});
        if (sel) e.drawCircle(cx, ry + 34, 8, kBLl);
        if (sel) focusRing(e, rx, ry, rw, rowH - 10, 24);
    }
}

void drawCodeLink(UiEngine& e, const std::string& code, int secondsLeft, bool linked,
                  const std::string& langCode) {
    Lay L; L.rtl = (langCode == "ar");
    e.drawImage("bg_cave", 0, 0, 1920, 1080);
    e.fillRect(0, 0, 1920, 1080, SDL_Color{5, 10, 24, 165});
    e.fillGradV(0, 0, 1920, 300, SDL_Color{5, 10, 24, 150}, SDL_Color{5, 10, 24, 0});
    drawTopNav(e, -1, langCode, 1250);
    boxText(e, L, tr("sign_in_code", langCode), 96, 860, 150, 52, kWHT, FontRole::Pixel);

    const char* steps[3] = {"open_app_phone", "go_settings_link", "enter_code"};
    for (int i = 0; i < 3; ++i) {
        const int y = 262 + i * 112;
        const int rx = L.X(96, 800);
        e.drawRoundRect(rx, y, 800, 92, 26, kSF);
        e.drawRoundBorder(rx, y, 800, 92, 26, 2, kBD);
        const int nx = L.X(96 + 22, 52);
        e.drawRoundGradD(nx, y + 20, 52, 52, 26, kBL, kBL2);
        e.drawTextCentered(num(i + 1), nx + 26, y + 47, 28, kWHT, FontRole::Pixel, false);
        boxText(e, L, tr(steps[i], langCode), 96 + 22 + 52 + 22, 800 - 22 - 52 - 22 - 24, y + 28, 26, kCHIPTX);
    }

    const int px = 1000, py = 200, pw = 824, ph = 520;
    const int rpx = L.X(px, pw);
    e.dropShadow(rpx, py, pw, ph, 36, 20, 140);
    e.drawRoundRect(rpx, py, pw, ph, 36, kBAR);
    e.drawRoundBorder(rpx, py, pw, ph, 36, 2, kBD);
    e.drawTextCentered(tr("your_code", langCode), rpx + pw / 2, py + 44, 22, kMU, FontRole::Body, L.rtl);
    // code as tiles
    std::string clean;
    for (char ch : code) if (ch != ' ') clean.push_back(ch);
    const int n = static_cast<int>(clean.size());
    const int tileW = 84, tileH = 116, gap = 12, groupGap = 30;
    const int groups = n > 3 ? 2 : 1;
    const int totalW = n * tileW + (n - 1) * gap + (groups - 1) * (groupGap - gap);
    int cx = rpx + (pw - totalW) / 2;
    for (int i = 0; i < n; ++i) {
        e.drawRoundRect(cx, py + 96, tileW, tileH, 22, kSF);
        e.drawRoundBorder(cx, py + 96, tileW, tileH, 22, 2, linked ? kGRN : SDL_Color{60, 90, 150, 255});
        e.drawTextCentered(std::string(1, clean[i]), cx + tileW / 2, py + 96 + tileH / 2, 66, kWHT, FontRole::Pixel, false);
        cx += tileW + (i == 2 && groups == 2 ? groupGap : gap);
    }
    if (linked) {
        e.drawCircle(rpx + pw / 2, py + 330, 44, SDL_Color{63, 191, 106, 70});
        icCheck(e, rpx + pw / 2 - 26, py + 304, 52, kGRN2);
        e.drawTextCentered(tr("linked_ok", langCode), rpx + pw / 2, py + 424, 32, kGRN2, FontRole::Pixel, L.rtl);
    } else {
        const int total = 600;
        const int frac = std::max(0, std::min(total, secondsLeft));
        const int barW = pw - 160;
        e.drawRoundRect(rpx + 80, py + 270, barW, 12, 6, kBD);
        e.drawRoundRect(rpx + 80, py + 270, std::max(12, barW * frac / total), 12, 6, kBLl);
        char buf[96];
        std::snprintf(buf, sizeof(buf), "%s %d:%02d", tr("expires_in", langCode).c_str(), secondsLeft / 60, secondsLeft % 60);
        e.drawTextCentered(buf, rpx + pw / 2, py + 322, 26, kMU, FontRole::Body, L.rtl);
        const int wp = textW(e, L, tr("waiting", langCode), 24) + 70;
        chipBox(e, rpx + (pw - wp) / 2, py + 380, wp, 60, kSF, kBD);
        e.drawCircle(L.rtl ? rpx + (pw + wp) / 2 - 30 : rpx + (pw - wp) / 2 + 30, py + 410, 8, kBLl);
        e.drawTextCentered(tr("waiting", langCode), rpx + (pw - wp) / 2 + wp / 2 + (L.rtl ? -12 : 12), py + 411, 24, kFOCUS, FontRole::Body, L.rtl);
    }
    button(e, L, px, py + ph + 40, pw, 92, tr("new_code", langCode), 0, 32);
    focusRing(e, L.X(px, pw), py + ph + 40, pw, 92, 26);
    drawLegendBar(e, {{"\xE2\x9C\x95", "select"}, {"\xE2\x97\x8B", "back"}}, langCode);
}

void drawEmailSignin(UiEngine& e, int focus, const std::string& email, const std::string& pass,
                     bool showError, const std::string& langCode) {
    Lay L; L.rtl = (langCode == "ar");
    e.drawImage("bg_splash", 0, 0, 1920, 1080);
    e.fillRect(0, 0, 1920, 1080, SDL_Color{5, 10, 24, 175});
    const int cw = 880, cx = 960 - cw / 2, cy = 120, ch = showError ? 800 : 720;
    e.dropShadow(cx, cy, cw, ch, 40, 24, 170);
    e.drawRoundRect(cx, cy, cw, ch, 40, SDL_Color{14, 21, 38, 238});
    e.drawRoundBorder(cx, cy, cw, ch, 40, 2, SDL_Color{255, 255, 255, 44});
    e.drawRoundRect(L.X(cx + 50, 64), cy + 40, 64, 64, 20, SDL_Color{46, 123, 239, 50});
    iconMail(e, L.X(cx + 50, 64), cy + 40, 64, kBLl);
    boxText(e, L, tr("sign_in", langCode), cx + 50 + 84, cw - 100 - 84, cy + 46, 50, kWHT, FontRole::Pixel);

    int y = cy + 140;
    if (showError) {
        const int rx = L.X(cx + 50, cw - 100);
        e.drawRoundRect(rx, y, cw - 100, 70, 22, SDL_Color{255, 107, 120, 36});
        e.drawRoundBorder(rx, y, cw - 100, 70, 22, 2, SDL_Color{255, 107, 120, 130});
        boxText(e, L, tr("wrong_login", langCode), cx + 50 + 28, cw - 100 - 56, y + 20, 26, kERT);
        y += 94;
    }
    auto field = [&](int idx, const std::string& label, const std::string& value, bool secret, int fy) {
        boxText(e, L, label, cx + 50, cw - 100, fy, 24, kMU);
        const int iy = fy + 40, fw = cw - 100;
        const int rx = L.X(cx + 50, fw);
        e.drawRoundRect(rx, iy, fw, 96, 26, kSF);
        e.drawRoundBorder(rx, iy, fw, 96, 26, 2, showError ? SDL_Color{255, 107, 120, 255} : kBD);
        std::string shown = value.empty() ? (idx == 0 ? "name@example.com" : "\xE2\x80\xA2\xE2\x80\xA2\xE2\x80\xA2\xE2\x80\xA2\xE2\x80\xA2\xE2\x80\xA2\xE2\x80\xA2\xE2\x80\xA2")
                                           : (secret ? std::string(value.size(), '*') : value);
        boxText(e, L, shown, cx + 50 + 32, fw - 64 - 250, iy + 28, 32, value.empty() ? SDL_Color{124, 140, 168, 255} : kWHT);
        endText(e, L, tr("press_x_type", langCode), cx + 50, fw - 28, iy + 36, 22, SDL_Color{124, 140, 168, 255});
        if (focus == idx) focusRing(e, rx, iy, fw, 96, 26);
    };
    field(0, tr("email", langCode), email, false, y);
    field(1, tr("password", langCode), pass, true, y + 168);
    const int by = y + 360;
    button(e, L, cx + 50, by, cw - 100, 92, tr("sign_in", langCode), 0, 32);
    if (focus == 2) focusRing(e, L.X(cx + 50, cw - 100), by, cw - 100, 92, 26);
    e.drawTextCentered(tr("forgot_pw", langCode), 960, by + 130, 22, SDL_Color{170, 186, 214, 255}, FontRole::Body, L.rtl);
    drawLegendBar(e, {{"\xE2\x9C\x95", "select"}, {"\xE2\x97\x8B", "back"}}, langCode);
}

// ============================================================================
//  Home / Deals / Store / Addon page
// ============================================================================
namespace {

const char* kCoverNames[5] = {"cover_0", "cover_1", "cover_2", "cover_3", "cover_4"};

CardArgs gridCard(int i, const std::string& lang, bool deal) {
    static const char* badgesStore[5] = {"NEW", "-40%", "EPIC", "", "LEGENDARY"};
    static const SDL_Color badgeColStore[5] = {{63, 191, 106, 255}, {255, 92, 122, 255}, {155, 107, 255, 255}, {240, 194, 94, 255}, {240, 194, 94, 255}};
    static const char* badgesDeal[5] = {"-50%", "-40%", "-30%", "-25%", "-20%"};
    static const char* pricesStore[5] = {"200", "200", "200", "0", "200"};
    static const char* pricesDeal[5] = {"75", "120", "140", "0", "200"};
    static const char* oldsDeal[5] = {"150", "200", "200", "", "250"};
    CardArgs a;
    a.art = kCoverNames[i % 5];
    const CardInfo& g = gridAt(i);
    a.title = g.title.empty() ? "[Addon name]" : g.title;
    a.creator = g.creator.empty() ? tr("by", lang) + " [Creator]" : g.creator;
    if (deal) {
        a.badge = g.badge.empty() ? badgesDeal[i] : g.badge;
        a.badgeCol = SDL_Color{255, 92, 122, 255};
        a.price = g.price.empty() ? pricesDeal[i] : g.price;
        a.oldPrice = g.oldPrice.empty() ? oldsDeal[i] : g.oldPrice;
    } else {
        a.badge = g.badge.empty() ? badgesStore[i] : g.badge;
        a.badgeCol = badgeColStore[i];
        a.badgeDark = (i == 3 || i == 4);
        a.price = g.price.empty() ? pricesStore[i] : g.price;
        a.oldPrice = g.oldPrice;
    }
    return a;
}

void heroTitleText(UiEngine& e, const Lay& L, const std::string& t, int xl, int w, int y, int px) {
    if (L.rtl) e.drawTextGradV(t, L.X(xl, w) + w, y, px, kWHT, SDL_Color{111, 178, 255, 255}, FontRole::Pixel, true, true);
    else e.drawTextGradV(t, xl, y, px, kWHT, SDL_Color{111, 178, 255, 255}, FontRole::Pixel, false, false);
}

// kicker pill + badge + countdown block used by the Home hero and the Deals header.
void campaignBlock(UiEngine& e, const Lay& L, const std::string& lang, int xl, int y, const std::string& kicker,
                   const std::string& badge, int boxW, int boxH) {
    // kicker
    const int kw = textW(e, L, kicker, 22) + 70;
    const int krx = L.X(xl, kw);
    chipBox(e, krx, y, kw, 44, SDL_Color{156, 220, 255, 36}, SDL_Color{156, 220, 255, 140});
    icStar(e, L.rtl ? krx + kw - 38 : krx + 14, y + 9, 24, SDL_Color{189, 230, 255, 255});
    e.drawText(kicker, L.rtl ? krx + kw - 48 : krx + 48, y + 10, 22, SDL_Color{189, 230, 255, 255}, FontRole::Body, L.rtl, L.rtl);
    (void)lang; (void)badge; (void)boxW; (void)boxH;
}

void badgeAndCountdown(UiEngine& e, const Lay& L, const std::string& lang, int xl, int y, const std::string& badge,
                       int boxW, int boxH) {
    const int bw = 160;
    const int brx = L.X(xl, bw);
    e.drawRoundGradD(brx, y, bw, boxH, 20, SDL_Color{255, 92, 122, 255}, SDL_Color{255, 61, 107, 255});
    e.drawTextCentered(badge, brx + bw / 2, y + boxH / 2, 42, kWHT, FontRole::Body, false);
    const char* vals[3] = {"03", "12", "45"};
    const char* keys[3] = {"days", "hrs", "min"};
    for (int i = 0; i < 3; ++i) {
        const int bx = xl + bw + 18 + i * (boxW + 12);
        const int rx = L.X(bx, boxW);
        e.drawRoundRect(rx, y, boxW, boxH, 16, SDL_Color{10, 24, 52, 170});
        e.drawRoundBorder(rx, y, boxW, boxH, 16, 2, SDL_Color{156, 220, 255, 100});
        e.drawTextCentered(vals[i], rx + boxW / 2, y + boxH * 38 / 100, 32, kWHT, FontRole::Pixel, false);
        e.drawTextCentered(tr(keys[i], lang), rx + boxW / 2, y + boxH * 80 / 100, 16, SDL_Color{156, 220, 255, 255}, FontRole::Body, L.rtl);
    }
}

} // namespace

void drawHome(UiEngine& e, int focus, int heroIndex, int filterIndex, const std::string& langCode) {
    Lay L; L.rtl = (langCode == "ar");
    backdrop(e);
    drawTopNav(e, 0, langCode, 1250);

    // ---- hero banner ----------------------------------------------------------------
    const int hx = 96, hy = 128, hw = 1080, hh = 400;
    const int hrx = L.X(hx, hw);
    const char* heroArt = heroIndex == 0 ? "bg_winter" : (heroIndex == 1 ? "bg_sunset" : "bg_creator");
    e.dropShadow(hrx, hy, hw, hh, 34, 18, 140);
    e.drawImageRounded(heroArt, hrx, hy, hw, hh, 34);
    if (L.rtl) e.fillGradH(hrx, hy, hw, hh, SDL_Color{5, 10, 24, 30}, SDL_Color{5, 10, 24, 235});
    else e.fillGradH(hrx, hy, hw, hh, SDL_Color{5, 10, 24, 235}, SDL_Color{5, 10, 24, 30});
    e.drawRoundBorder(hrx, hy, hw, hh, 34, 2, SDL_Color{255, 255, 255, 40});
    const HeroInfo& hi = heroAt(heroIndex);
    campaignBlock(e, L, langCode, hx + 48, hy + 32, hi.kicker.empty() ? tr("limited_time", langCode) : hi.kicker, "", 0, 0);
    std::string heroTitle = hi.title;
    if (heroTitle.empty()) heroTitle = heroIndex == 0 ? tr("winter_sale", langCode) : (heroIndex == 1 ? "SUNSET VILLAGE" : "CREATOR SPOTLIGHT");
    heroTitleText(e, L, heroTitle, hx + 48, 700, hy + 92, L.rtl ? 66 : 62);
    boxText(e, L, hi.subtitle.empty() ? tr("up_to_50", langCode) : hi.subtitle, hx + 48, 640, hy + 182, 28,
            SDL_Color{230, 238, 251, 255});
    badgeAndCountdown(e, L, langCode, hx + 48, hy + 232, hi.badge.empty() ? "-50%" : hi.badge, 86, 70);
    button(e, L, hx + 48, hy + 316, 270, 64, tr("nav_deals", langCode), 0, 28, 22);
    button(e, L, hx + 48 + 270 + 18, hy + 316, 190, 64, tr("back", langCode), 1, 28, 22);
    if (focus == 0) focusRing(e, L.X(hx + 48, 270), hy + 316, 270, 64, 22);
    if (focus == 1) focusRing(e, L.X(hx + 48 + 288, 190), hy + 316, 190, 64, 22);

    // ---- exclusives (right column) ------------------------------------------------------
    const int exX = hx + hw + 28, exW = 1920 - 96 - exX;   // LTR coords
    struct EX { int xo, y, w, h; const char* art; const char* title; const char* price; };
    const int sw = (exW - 20) / 2;
    const EX exs[3] = {
        {0, 128, exW, 240, "bg_sunset", "[Exclusive addon]", "250"},
        {0, 388, sw, 140, "bg_creator", "[Exclusive world]", "180"},
        {sw + 20, 388, sw, 140, "bg_winter", "[Exclusive pack]", "120"},
    };
    for (int i = 0; i < 3; ++i) {
        const EX& ex = exs[i];
        const int xl = exX + ex.xo, rx = L.X(xl, ex.w);
        e.dropShadow(rx, ex.y, ex.w, ex.h, 28, 12, 100);
        e.drawImageRounded(ex.art, rx, ex.y, ex.w, ex.h, 28);
        e.fillGradV(rx, ex.y + ex.h * 40 / 100, ex.w, ex.h - ex.h * 40 / 100, SDL_Color{5, 10, 24, 0}, SDL_Color{5, 10, 24, 225});
        e.drawRoundBorder(rx, ex.y, ex.w, ex.h, 28, 2, SDL_Color{255, 255, 255, 40});
        const std::string ext = tr("exclusive", langCode);
        const int ew = textW(e, L, ext, 19) + 30;
        const int erx = L.X(xl + 16, ew);
        e.drawRoundRect(erx, ex.y + 14, ew, 36, 11, kGD);
        e.drawTextCentered(ext, erx + ew / 2, ex.y + 32, 19, SDL_Color{27, 20, 5, 255}, FontRole::Body, L.rtl);
        const std::string price = exAt(i).price.empty() ? ex.price : exAt(i).price;
        const int pw = textW(e, L, price, 24) + 62;
        const int prx = L.X(xl + ex.w - 16 - pw, pw);
        chipBox(e, prx, ex.y + 12, pw, 40, SDL_Color{10, 20, 44, 200}, SDL_Color{255, 255, 255, 40});
        e.drawImage("icon_coin", L.rtl ? prx + pw - 36 : prx + 8, ex.y + 17, 30, 30);
        e.drawText(price, L.rtl ? prx + 14 : prx + 44, ex.y + 17, 24, kWHT, FontRole::Body, false, false);
        boxText(e, L, exAt(i).title.empty() ? ex.title : exAt(i).title, xl + 20, ex.w - 40, ex.y + ex.h - 52, 26, kWHT);
        if (focus == 2 + i) focusRing(e, rx, ex.y, ex.w, ex.h, 28);
    }

    // pager dots
    {
        int dx = L.X(hx + (hw - 90) / 2, 90);
        for (int i = 0; i < 3; ++i) {
            const bool on = (i == (heroIndex % 3));
            if (on) e.drawRoundRect(dx + i * 30, 541, 24, 10, 5, kBLl);
            else e.drawCircle(dx + i * 30 + 6, 546, 5, SDL_Color{255, 255, 255, 80});
        }
    }

    // ---- category chips -----------------------------------------------------------------
    chipRow(e, L, 96, 566, {tr("chip_all", langCode), tr("chip_addons", langCode), tr("chip_worlds", langCode),
                            tr("chip_free", langCode), tr("chip_epic", langCode), tr("chip_legendary", langCode),
                            tr("chip_new", langCode)}, filterIndex, 5, focus);

    // ---- recommended row + app QR ----------------------------------------------------------
    sectionTitle(e, L, tr("recommended", langCode), 96, 800, 642, 32);
    const int cw = 256, gap = 18, cy = 696, ch = 256;
    for (int i = 0; i < 5; ++i) {
        CardArgs a = gridCard(i, langCode, false);
        a.badge.clear();
        card(e, L, 96 + i * (cw + gap), cy, cw, ch, 112, a, focus == 12 + i, langCode);
    }
    const int qx = 96 + 5 * (cw + gap) + 6;
    qrCard(e, L, qx, cy, 1824 - qx, ch, tr("get_app_cta", langCode));

    drawLegendBar(e, {{"\xE2\x9C\x95", "select"}, {"\xE2\x97\x8B", "back"}, {"\xE2\x96\xA1", "search"}, {"\xE2\x96\xB3", "menu"},
                      {"L2", "prev_offer"}, {"R2", "next_offer"}}, langCode);
}

void drawDeals(UiEngine& e, int focus, int filterIndex, const std::string& langCode) {
    Lay L; L.rtl = (langCode == "ar");
    backdrop(e);
    drawTopNav(e, 1, langCode, 1250);
    const int hx = 96, hy = 128, hw = 1728, hh = 372;
    const int hrx = L.X(hx, hw);
    e.dropShadow(hrx, hy, hw, hh, 32, 18, 140);
    e.drawImageRounded("bg_winter", hrx, hy, hw, hh, 32);
    if (L.rtl) e.fillGradH(hrx, hy, hw, hh, SDL_Color{5, 10, 24, 25}, SDL_Color{5, 10, 24, 238});
    else e.fillGradH(hrx, hy, hw, hh, SDL_Color{5, 10, 24, 238}, SDL_Color{5, 10, 24, 25});
    e.drawRoundBorder(hrx, hy, hw, hh, 32, 2, SDL_Color{255, 255, 255, 40});
    const HeroInfo& hi = heroAt(0);
    campaignBlock(e, L, langCode, hx + 56, hy + 36, hi.kicker.empty() ? tr("limited_time", langCode) : hi.kicker, "", 0, 0);
    heroTitleText(e, L, hi.title.empty() ? tr("winter_sale", langCode) : hi.title, hx + 56, 900, hy + 96, L.rtl ? 78 : 74);
    boxText(e, L, hi.subtitle.empty() ? tr("up_to_50", langCode) : hi.subtitle, hx + 56, 900, hy + 200, 30,
            SDL_Color{230, 238, 251, 255});
    badgeAndCountdown(e, L, langCode, hx + 56, hy + 262, hi.badge.empty() ? "-50%" : hi.badge, 92, 76);

    chipRow(e, L, 96, 528, {tr("chip_all", langCode), tr("chip_addons", langCode), tr("chip_worlds", langCode),
                            tr("chip_under100", langCode), tr("chip_biggest", langCode)}, filterIndex, 0, focus);

    const int cw = 324, gap = 27, cy = 612, ch = 340;
    for (int i = 0; i < 5; ++i) card(e, L, 96 + i * (cw + gap), cy, cw, ch, 190, gridCard(i, langCode, true), focus == 5 + i, langCode);
    drawLegendBar(e, {{"\xE2\x9C\x95", "open"}, {"\xE2\x97\x8B", "back"}, {"\xE2\x96\xA1", "filter"}, {"\xE2\x96\xB3", "sort"}}, langCode);
}

void drawStore(UiEngine& e, int focus, int filterIndex, const std::string& langCode) {
    Lay L; L.rtl = (langCode == "ar");
    backdrop(e);
    drawTopNav(e, 2, langCode, 1250);
    // promo strip (button = focus 8)
    const int hx = 96, hy = 128, hw = 1728, hh = 212;
    const int hrx = L.X(hx, hw);
    e.dropShadow(hrx, hy, hw, hh, 30, 16, 130);
    e.drawImageRounded("bg_winter", hrx, hy, hw, hh, 30);
    if (L.rtl) e.fillGradH(hrx, hy, hw, hh, SDL_Color{5, 10, 24, 30}, SDL_Color{5, 10, 24, 232});
    else e.fillGradH(hrx, hy, hw, hh, SDL_Color{5, 10, 24, 232}, SDL_Color{5, 10, 24, 30});
    e.drawRoundBorder(hrx, hy, hw, hh, 30, 2, SDL_Color{255, 255, 255, 40});
    heroTitleText(e, L, heroAt(0).title.empty() ? tr("winter_sale", langCode) : heroAt(0).title, hx + 52, 800, hy + 40, L.rtl ? 60 : 58);
    boxText(e, L, heroAt(0).subtitle.empty() ? tr("up_to_50", langCode) : heroAt(0).subtitle, hx + 52, 900, hy + 124, 28,
            SDL_Color{230, 238, 251, 255});
    button(e, L, hx + hw - 52 - 320, hy + 62, 320, 88, tr("shop_sale", langCode), 0, 28);
    if (focus == 8) focusRing(e, L.X(hx + hw - 52 - 320, 320), hy + 62, 320, 88, 26);

    chipRow(e, L, 96, 368, {tr("chip_addons", langCode), tr("chip_worlds", langCode), tr("chip_newest", langCode)},
            filterIndex, 9, focus);

    const int cw = 324, gap = 27, cy = 458, ch = 470;
    for (int i = 0; i < 5; ++i) card(e, L, 96 + i * (cw + gap), cy, cw, ch, 290, gridCard(i, langCode, false), focus == i, langCode);
    drawLegendBar(e, {{"\xE2\x9C\x95", "open"}, {"\xE2\x97\x8B", "back"}, {"\xE2\x96\xA1", "filter"}, {"\xE2\x96\xB3", "sort"}}, langCode);
}

void drawInstallDemo(UiEngine& e, DemoState state, int percent, const std::string& langCode) {
    Lay L; L.rtl = (langCode == "ar");
    backdrop(e);
    drawTopNav(e, 2, langCode, 1250);
    const int lx = 96, ly = 140, lw = 1000, lh = 560;
    const int lrx = L.X(lx, lw);
    e.dropShadow(lrx, ly, lw, lh, 32, 24, 160);
    e.drawImageRounded("cover_1", lrx, ly, lw, lh, 32);
    e.drawRoundBorder(lrx, ly, lw, lh, 32, 2, SDL_Color{255, 255, 255, 40});
    {   // ribbon
        const std::string rib = tr("winter_sale", langCode);
        const int rw = textW(e, L, rib, 22) + 78;
        const int rrx = L.X(lx + 24, rw);
        chipBox(e, rrx, ly + 24, rw, 54, SDL_Color{10, 24, 52, 205}, SDL_Color{156, 220, 255, 100});
        icStar(e, L.rtl ? rrx + rw - 46 : rrx + 18, ly + 38, 26, SDL_Color{189, 230, 255, 255});
        e.drawText(rib, L.rtl ? rrx + rw - 56 : rrx + 54, ly + 36, 22, SDL_Color{189, 230, 255, 255}, FontRole::Body, L.rtl, L.rtl);
    }

    const int rx0 = lx + lw + 56, rw = 1824 - rx0;
    boxText(e, L, gDetailTitle.empty() ? tr("install_demo_title", langCode) : gDetailTitle, rx0, rw, ly + 4, 46, kWHT, FontRole::Pixel);
    boxText(e, L, tr("by", langCode) + " [Creator]", rx0, rw, ly + 76, 26, kMU);
    // stats chips
    {
        int cx = rx0;
        auto stat = [&](void (*ic)(UiEngine&, int, int, int, SDL_Color), SDL_Color icc, const std::string& t) {
            const int w = textW(e, L, t, 24) + 76;
            const int rx = L.X(cx, w);
            chipBox(e, rx, ly + 124, w, 52, kSF, kBD);
            ic(e, L.rtl ? rx + w - 46 : rx + 16, ly + 137, 26, icc);
            e.drawText(t, L.rtl ? rx + 22 : rx + 52, ly + 136, 24, kCHIPTX, FontRole::Body, false, false);
            cx += w + 12;
        };
        stat(icDownloadArrow, kBLl, "1,240");
        stat(icStar, kGD, "4.8");
        stat(icCheck, kGRN2, "PS4");
    }

    const int btnW = rw, btnY = ly + 224, btnH = 92;
    auto stateBtn = [&](int y, const std::string& label, int style) { button(e, L, rx0, y, btnW, btnH, label, style, 32); };
    if (state == DemoState::Idle) {
        stateBtn(btnY, tr("download", langCode), 0);
        focusRing(e, L.X(rx0, btnW), btnY, btnW, btnH, 26);
        button(e, L, rx0, btnY + 112, btnW, btnH, tr("save", langCode), 1, 30);
        button(e, L, rx0, btnY + 224, btnW, btnH, tr("share", langCode), 1, 30);
    } else if (state == DemoState::Downloading) {
        stateBtn(btnY, tr("downloading", langCode) + "  " + num(percent) + "%", 0);
        e.drawRoundRect(L.X(rx0, btnW), btnY + 112, btnW, 14, 7, kBD);
        const int pw = std::max(14, btnW * percent / 100);
        e.drawRoundRect(L.X(rx0, btnW) + (L.rtl ? btnW - pw : 0), btnY + 112, pw, 14, 7, kBLl);
    } else if (state == DemoState::Installing) {
        stateBtn(btnY, tr("installing", langCode), 2);
    } else {
        stateBtn(btnY, tr("installed", langCode), 3);
    }

    const int dy = ly + lh + 36;
    boxText(e, L, tr("description", langCode), lx, lw, dy, 22, kGD);
    paragraphAt(e, L, tr("install_demo_sub", langCode), lx, lw, dy + 36, 26, kMU, 3, 12);
    drawLegendBar(e, {{"\xE2\x9C\x95", "select"}, {"\xE2\x97\x8B", "back"}, {"\xE2\x96\xA1", "save"}, {"\xE2\x96\xB3", "open"}}, langCode);
}

// ============================================================================
//  New screens: Search / Profile / AC Wallet / Plans / Menu (△)
// ============================================================================
namespace {

const SDL_Color kPlanCol[3] = {{111, 168, 255, 255}, {155, 107, 255, 255}, {240, 194, 94, 255}};

std::string planName(int i, const std::string& lang) {
    return tr(i == 0 ? "plan_free" : (i == 1 ? "plan_epic" : "plan_legendary"), lang);
}

// Row used by Profile / Menu lists: icon tile + label (+ optional value) + chevron.
void listRow(UiEngine& e, const Lay& L, int xl, int y, int w, int h, int iconKind, SDL_Color acc,
             const std::string& label, const std::string& value, bool focused) {
    const int rx = L.X(xl, w);
    if (focused) e.drawRoundGradH(rx, y, w, h, 28, SDL_Color{30, 44, 72, 255}, SDL_Color{20, 30, 50, 255});
    else e.drawRoundRect(rx, y, w, h, 28, kSF);
    e.drawRoundBorder(rx, y, w, h, 28, 2, focused ? SDL_Color{60, 86, 130, 255} : kBD);
    const int ts = h - 40;
    const int tx = L.X(xl + 20, ts);
    e.drawRoundRect(tx, y + 20, ts, ts, 18, SDL_Color{acc.r, acc.g, acc.b, 40});
    e.drawRoundBorder(tx, y + 20, ts, ts, 18, 2, SDL_Color{acc.r, acc.g, acc.b, 90});
    const int is = ts * 52 / 100;
    const int ix = tx + (ts - is) / 2, iy = y + 20 + (ts - is) / 2;
    switch (iconKind) {
        case 0: icWallet(e, ix, iy, is, acc); break;
        case 1: icCrown(e, ix, iy, is, acc); break;
        case 2: icPerson(e, ix, iy, is, acc); break;
        case 3: icSearch(e, ix, iy, is, acc); break;
        default: icGlobe(e, ix, iy, is, acc); break;
    }
    const int labX = xl + 20 + ts + 22;
    const int reserve = value.empty() ? 70 : 70 + std::min(360, textW(e, L, value, 24) + 20);
    boxText(e, L, label, labX, w - (labX - xl) - reserve, y + (h - 36) / 2, 30, kWHT);
    if (!value.empty()) endText(e, L, value, xl, w - 60, y + (h - 28) / 2, 24, kMU);
    chevron(e, L.X(xl + w - 38, 12), y + h / 2 - 9, 9, !L.rtl, 3, SDL_Color{120, 140, 175, 255});
    if (focused) focusRing(e, rx, y, w, h, 28);
}

} // namespace

// focus: 0 search field, 1..3 filter chips, 4..8 result cards
void drawSearch(UiEngine& e, int focus, const std::string& query, int filterIndex, bool noResults,
                const std::string& langCode) {
    Lay L; L.rtl = (langCode == "ar");
    backdrop(e);
    drawTopNav(e, -1, langCode, 1250);
    const int fx = 96, fw = 1728, fy = 140;
    const int rx = L.X(fx, fw);
    e.drawRoundRect(rx, fy, fw, 100, 50, kBAR);
    e.drawRoundBorder(rx, fy, fw, 100, 50, 2, kBD);
    icSearch(e, L.X(fx + 34, 40), fy + 30, 40, kBLl);
    boxText(e, L, query.empty() ? tr("search_hint", langCode) : query, fx + 100, fw - 100 - 300, fy + 30, 34,
            query.empty() ? SDL_Color{124, 140, 168, 255} : kWHT);
    endText(e, L, tr("press_x_type", langCode), fx, fw - 36, fy + 38, 22, SDL_Color{124, 140, 168, 255});
    if (focus == 0) focusRing(e, rx, fy, fw, 100, 50);

    chipRow(e, L, 96, 268, {tr("chip_all", langCode), tr("chip_addons", langCode), tr("chip_worlds", langCode)}, filterIndex, 1, focus);

    if (noResults) {
        const int cy = 560;
        e.drawCircle(960, cy - 40, 84, kSF2);
        icSearch(e, 960 - 40, cy - 80, 80, kBLl);
        e.drawTextCentered(tr("no_results", langCode), 960, cy + 100, 48, kWHT, FontRole::Pixel, L.rtl);
        e.drawTextCentered(tr("no_results_sub", langCode), 960, cy + 160, 26, kMU, FontRole::Body, L.rtl);
    } else {
        if (query.empty()) {
            boxText(e, L, tr("popular", langCode), 96, 800, 364, 24, kGD);
            const char* terms[4] = {"Winter", "Parkour", "Castle", "Cars"};
            int cx = 96;
            for (int i = 0; i < 4; ++i) {
                const int cw = textW(e, L, terms[i], 24) + 48;
                const int crx = L.X(cx, cw);
                chipBox(e, crx, 404, cw, 50, kSF, kBD);
                e.drawTextCentered(terms[i], crx + cw / 2, 429, 24, kCHIPTX, FontRole::Body, false);
                cx += cw + 12;
            }
        } else {
            boxText(e, L, num(5) + " " + tr("results", langCode), 96, 800, 380, 26, kMU);
        }
        const int cw = 324, gap = 27, cy = 490, ch = 450;
        for (int i = 0; i < 5; ++i) card(e, L, 96 + i * (cw + gap), cy, cw, ch, 270, gridCard(i, langCode, false), focus == 4 + i, langCode);
    }
    drawLegendBar(e, {{"\xE2\x9C\x95", "select"}, {"\xE2\x97\x8B", "back"}}, langCode);
}

// focus: 0 wallet, 1 plans, 2 settings
void drawProfile(UiEngine& e, int focus, const std::string& name, int planIndex, int acBalance,
                 int downloads, int saved, int installed, const std::string& langCode) {
    Lay L; L.rtl = (langCode == "ar");
    backdrop(e);
    drawTopNav(e, -1, langCode, acBalance);
    const int px = 96, py = 140, pw = 620, ph = 660;
    const int rx = L.X(px, pw);
    e.dropShadow(rx, py, pw, ph, 36, 16, 120);
    e.drawRoundGradD(rx, py, pw, ph, 36, SDL_Color{22, 40, 78, 255}, SDL_Color{14, 22, 40, 255});
    e.drawRoundBorder(rx, py, pw, ph, 36, 2, SDL_Color{60, 90, 150, 255});
    avatarCircle(e, rx + (pw - 168) / 2, py + 52, 168, true);
    e.drawTextCentered(name.empty() ? tr("guest_short", langCode) : name, rx + pw / 2, py + 276, 38, kWHT, FontRole::Pixel, L.rtl);
    {   // plan chip
        const int pi = std::max(0, std::min(2, planIndex));
        const std::string pn = planName(pi, langCode);
        const int cw = textW(e, L, pn, 24) + 78;
        const int crx = rx + (pw - cw) / 2;
        chipBox(e, crx, py + 322, cw, 52, SDL_Color{kPlanCol[pi].r, kPlanCol[pi].g, kPlanCol[pi].b, 36},
                SDL_Color{kPlanCol[pi].r, kPlanCol[pi].g, kPlanCol[pi].b, 150});
        icCrown(e, L.rtl ? crx + cw - 46 : crx + 18, py + 334, 28, kPlanCol[pi]);
        e.drawText(pn, L.rtl ? crx + cw - 56 - textW(e, L, pn, 24) : crx + 56, py + 334, 24, kPlanCol[pi], FontRole::Body, false, L.rtl);
    }
    // stats
    const int sw = (pw - 80 - 24) / 3;
    const int vals[3] = {downloads, saved, installed};
    const char* keys[3] = {"stat_downloads", "stat_saved", "stat_installed"};
    for (int i = 0; i < 3; ++i) {
        const int sx = rx + 40 + i * (sw + 12);
        e.drawRoundRect(sx, py + 430, sw, 150, 24, SDL_Color{10, 15, 28, 150});
        e.drawRoundBorder(sx, py + 430, sw, 150, 24, 2, SDL_Color{255, 255, 255, 26});
        e.drawTextCentered(num(vals[i]), sx + sw / 2, py + 478, 44, kWHT, FontRole::Pixel, false);
        e.drawTextCentered(tr(keys[i], langCode), sx + sw / 2, py + 540, 22, kMU, FontRole::Body, L.rtl);
    }
    // rows
    const int ax = px + pw + 40, aw = 1824 - ax;
    sectionTitle(e, L, tr("profile", langCode), ax, aw, py, 38);
    listRow(e, L, ax, py + 90, aw, 120, 0, kGD, tr("wallet", langCode), fmtAC(acBalance) + " AC", focus == 0);
    listRow(e, L, ax, py + 90 + 140, aw, 120, 1, kEPICT, tr("plans", langCode), planName(std::max(0, std::min(2, planIndex)), langCode), focus == 1);
    listRow(e, L, ax, py + 90 + 280, aw, 120, 4, kBLl, tr("nav_settings", langCode), "", focus == 2);
    drawLegendBar(e, {{"\xE2\x9C\x95", "select"}, {"\xE2\x97\x8B", "back"}}, langCode);
}

// focus: highlighted history row 0..5 (-1 = none)
void drawWallet(UiEngine& e, int focus, int acBalance, const std::string& langCode) {
    Lay L; L.rtl = (langCode == "ar");
    backdrop(e);
    drawTopNav(e, -1, langCode, acBalance);
    const int px = 96, py = 140, pw = 640, ph = 800;
    const int rx = L.X(px, pw);
    e.dropShadow(rx, py, pw, ph, 36, 16, 120);
    e.drawRoundGradD(rx, py, pw, ph, 36, SDL_Color{52, 40, 14, 255}, SDL_Color{20, 18, 14, 255});
    e.drawRoundBorder(rx, py, pw, ph, 36, 2, SDL_Color{140, 106, 34, 255});
    e.drawCircle(rx + pw / 2, py + 170, 96, SDL_Color{240, 194, 94, 36});
    e.drawImage("icon_coin", rx + pw / 2 - 64, py + 106, 128, 128);
    e.drawTextCentered(tr("balance", langCode), rx + pw / 2, py + 304, 26, SDL_Color{220, 196, 140, 255}, FontRole::Body, L.rtl);
    e.drawTextCentered(fmtAC(acBalance), rx + pw / 2, py + 384, 92, kGD, FontRole::Pixel, false);
    e.drawTextCentered("AC", rx + pw / 2, py + 466, 34, SDL_Color{220, 196, 140, 255}, FontRole::Pixel, false);
    e.fillRect(rx + 50, py + 534, pw - 100, 2, SDL_Color{240, 194, 94, 60});
    paragraphAt(e, L, tr("topup_hint", langCode), px + 50, pw - 100, py + 566, 26, SDL_Color{232, 220, 190, 255}, 4, 12);

    const int ax = px + pw + 40, aw = 1824 - ax;
    sectionTitle(e, L, tr("history", langCode), ax, aw, py, 38);
    struct Tx { const char* key; int amount; const char* when; };
    const Tx txs[6] = {{"tx_purchase", -150, "12:41"}, {"tx_topup", 500, "Yesterday"}, {"tx_gift", 100, "Yesterday"},
                       {"tx_purchase", -75, "Mon"}, {"tx_topup", 1000, "Mon"}, {"tx_purchase", -200, "Sun"}};
    for (int i = 0; i < 6; ++i) {
        const int y = py + 90 + i * 118;
        const bool foc = (focus == i);
        const int rrx = L.X(ax, aw);
        if (foc) e.drawRoundGradH(rrx, y, aw, 104, 28, SDL_Color{30, 44, 72, 255}, SDL_Color{20, 30, 50, 255});
        else e.drawRoundRect(rrx, y, aw, 104, 28, kSF);
        e.drawRoundBorder(rrx, y, aw, 104, 28, 2, foc ? SDL_Color{60, 86, 130, 255} : kBD);
        const bool plus = txs[i].amount > 0;
        const SDL_Color col = plus ? kGRN2 : kERT;
        const int tx = L.X(ax + 20, 64);
        e.drawRoundRect(tx, y + 20, 64, 64, 20, SDL_Color{col.r, col.g, col.b, 36});
        e.drawRoundBorder(tx, y + 20, 64, 64, 20, 2, SDL_Color{col.r, col.g, col.b, 100});
        if (plus) fillTriUp(e, tx + 20, y + 34, 24, 22, col); else fillTriDown(e, tx + 20, y + 34, 24, 22, col);
        e.fillRect(tx + 30, plus ? y + 52 : y + 28, 4, 10, col);
        const std::string label = tr(txs[i].key, langCode) + (i % 3 == 2 ? "" : (plus ? "" : " [Addon name]"));
        boxText(e, L, label, ax + 20 + 64 + 22, aw - 20 - 64 - 22 - 250, y + 18, 28, kWHT);
        boxText(e, L, txs[i].when, ax + 20 + 64 + 22, 300, y + 58, 22, kMU);
        const std::string amt = std::string(plus ? "+" : "-") + num(std::abs(txs[i].amount)) + " AC";
        endText(e, L, amt, ax, aw - 30, y + 34, 30, col);
        if (foc) focusRing(e, rrx, y, aw, 104, 28);
    }
    drawLegendBar(e, {{"\xE2\x97\x8B", "back"}}, langCode);
}

// focus: 0..2 plan cards (Free / Epic / Legendary)
void drawPlans(UiEngine& e, int focus, int currentPlan, const std::string& langCode) {
    Lay L; L.rtl = (langCode == "ar");
    backdrop(e);
    drawTopNav(e, -1, langCode, 1250);
    sectionTitle(e, L, tr("plans", langCode), 96, 800, 130, 40);
    const int cw = 556, gap = 30, cy = 206, ch = 540;
    static const char* feats[3][3] = {
        {"pf_free_1", "pf_free_2", ""},
        {"pf_epic_1", "pf_epic_2", "pf_epic_3"},
        {"pf_leg_1", "pf_leg_2", "pf_leg_3"}};
    for (int i = 0; i < 3; ++i) {
        const int xl = 96 + i * (cw + gap);
        const int rx = L.X(xl, cw);
        const SDL_Color pc = kPlanCol[i];
        e.dropShadow(rx, cy, cw, ch, 32, 14, 110);
        e.drawRoundRect(rx, cy, cw, ch, 32, kBAR);
        e.drawRoundBorder(rx, cy, cw, ch, 32, i == currentPlan ? 3 : 2, i == currentPlan ? pc : kBD);
        e.drawRoundGradH(rx + 3, cy + 3, cw - 6, 130, 30, SDL_Color{pc.r, pc.g, pc.b, 70}, SDL_Color{pc.r, pc.g, pc.b, 14});
        e.fillRect(rx + 3, cy + 100, cw - 6, 40, kBAR);
        e.fillRect(rx + 3, cy + 120, cw - 6, 2, SDL_Color{pc.r, pc.g, pc.b, 60});
        const int ts = 72;
        e.drawRoundRect(L.X(xl + 32, ts), cy + 28, ts, ts, 22, SDL_Color{pc.r, pc.g, pc.b, 46});
        e.drawRoundBorder(L.X(xl + 32, ts), cy + 28, ts, ts, 22, 2, SDL_Color{pc.r, pc.g, pc.b, 130});
        if (i == 0) icStar(e, L.X(xl + 32, ts) + 16, cy + 44, 40, pc);
        else icCrown(e, L.X(xl + 32, ts) + 14, cy + 42, 44, pc);
        boxText(e, L, planName(i, langCode), xl + 32 + ts + 22, cw - 32 - ts - 22 - 40, cy + 40, 40, kWHT, FontRole::Pixel);
        if (i == currentPlan) {
            const std::string cur = tr("plan_current", langCode);
            const int w = textW(e, L, cur, 22) + 36;
            const int crx = L.X(xl + cw - 28 - w, w);
            chipBox(e, crx, cy + 148, w, 40, SDL_Color{pc.r, pc.g, pc.b, 36}, SDL_Color{pc.r, pc.g, pc.b, 150});
            e.drawTextCentered(cur, crx + w / 2, cy + 168, 22, pc, FontRole::Body, L.rtl);
        }
        boxText(e, L, i == 0 ? tr("plan_free_price", langCode) : "[Price]", xl + 32, cw - 64, cy + 150, 34, pc, FontRole::Pixel);
        for (int f = 0; f < 3; ++f) {
            if (!feats[i][f][0]) continue;
            const int fy = cy + 232 + f * 84;
            e.drawCircle(L.X(xl + 32, 36) + 18, fy + 22, 18, SDL_Color{pc.r, pc.g, pc.b, 40});
            icCheck(e, L.X(xl + 32, 36) + 6, fy + 10, 24, pc);
            paragraphAt(e, L, tr(feats[i][f], langCode), xl + 32 + 52, cw - 32 - 52 - 28, fy + 4, 26, kCHIPTX, 2, 8);
        }
        if (focus == i) focusRing(e, rx, cy, cw, ch, 32);
    }
    // subscribe elsewhere + QR
    const int by = 776, bh = 172;
    const int brx = L.X(96, 1728);
    e.drawRoundGradD(brx, by, 1728, bh, 30, SDL_Color{22, 40, 78, 255}, SDL_Color{16, 26, 48, 255});
    e.drawRoundBorder(brx, by, 1728, bh, 30, 2, SDL_Color{60, 90, 150, 255});
    iconPhone(e, L.X(96 + 36, 64), by + (bh - 64) / 2, 64, kBLl);
    boxText(e, L, tr("subscribe_elsewhere", langCode), 96 + 36 + 64 + 28, 1728 - 36 - 64 - 28 - 260, by + 38, 34, kWHT);
    boxText(e, L, tr("scan_to_get_app", langCode), 96 + 36 + 64 + 28, 1728 - 36 - 64 - 28 - 260, by + 94, 26, kMU);
    qrTile(e, L.rtl ? brx + 24 : brx + 1728 - 24 - 124, by + 24, 124);
    drawLegendBar(e, {{"\xE2\x97\x8B", "back"}}, langCode);
}

// △ menu — call after drawing the screen underneath. focus 0 Profile, 1 Wallet, 2 Plans, 3 Search
void drawMenuOverlay(UiEngine& e, int focus, const std::string& langCode) {
    Lay L; L.rtl = (langCode == "ar");
    e.fillRect(0, 0, 1920, 1080, kSCRIM);
    const int w = 560, xl = 1920 - 96 - w, y = 128, rowH = 100, h = 118 + 4 * (rowH + 10) + 14;
    const int rx = L.X(xl, w);
    e.dropShadow(rx, y, w, h, 34, 26, 180);
    e.drawRoundRect(rx, y, w, h, 34, kBAR);
    e.drawRoundBorder(rx, y, w, h, 34, 2, kBD);
    icMenu(e, L.X(xl + 30, 40), y + 28, 40, kBLl);
    boxText(e, L, tr("menu", langCode), xl + 88, w - 120, y + 30, 36, kWHT, FontRole::Pixel);
    const char* keys[4] = {"profile", "wallet", "plans", "search"};
    const int icons[4] = {2, 0, 1, 3};
    const SDL_Color cols[4] = {kBLl, kGD, kEPICT, kGRN2};
    for (int i = 0; i < 4; ++i)
        listRow(e, L, xl + 20, y + 112 + i * (rowH + 10), w - 40, rowH, icons[i], cols[i], tr(keys[i], langCode), "", focus == i);
    e.fillRect(0, 984, 1920, 96, kBG);
    drawLegendBar(e, {{"\xE2\x9C\x95", "select"}, {"\xE2\x97\x8B", "back"}}, langCode);
}

// ============================================================================
//  Settings (no notifications) + sign-out dialog
// ============================================================================
namespace {
void switchCtl(UiEngine& e, int x, int y, bool on, bool rtl) {
    const int w = 88, h = 46;
    e.drawRoundRect(x, y, w, h, 23, on ? kBL : SDL_Color{52, 66, 94, 255});
    e.drawRoundBorder(x, y, w, h, 23, 2, on ? kBLl : SDL_Color{74, 92, 126, 255});
    const bool right = rtl ? !on : on;
    e.drawCircle(x + (right ? w - 24 : 24), y + h / 2, 17, kWHT);
}
void settingsIcon(UiEngine& e, int row, int x, int y, int s, SDL_Color c) {
    switch (row) {
        case 0: icGlobe(e, x, y, s, c); break;
        case 1: icPerson(e, x, y, s, c); break;
        case 2: icRocket(e, x, y, s, c); break;
        case 3: icLink(e, x, y, s, c); break;
        case 4: icInfo(e, x, y, s, c); break;
        default: icExit(e, x, y, s, c); break;
    }
}
const SDL_Color kSettingsAccent[6] = {
    {111, 168, 255, 255}, {240, 194, 94, 255}, {127, 217, 154, 255},
    {86, 200, 224, 255}, {157, 176, 207, 255}, {255, 135, 145, 255}};
int langIndexOf(const std::string& code) {
    static const char* kCodes[8] = {"en", "ar", "fr", "tr", "es", "ru", "pt", "zh"};
    for (int i = 0; i < 8; ++i) if (code == kCodes[i]) return i;
    return 0;
}
} // namespace

// rows: 0 language, 1 account, 2 auto-launch, 3 link a console, 4 about, 5 sign out
void drawSettings(UiEngine& e, int focus, bool autoLaunchOn, const std::string& accountValue,
                  const std::string& langCode) {
    Lay L; L.rtl = (langCode == "ar");
    const std::string& lang = langCode;
    backdrop(e);
    const int listX = 96, listW = 800, rowH = 108, pitch = 120, top = 180;
    const int panX = listX + listW + 40, panW = 1920 - 96 - panX, panY = top, panH = 5 * pitch + rowH;
    const int f = std::max(0, std::min(5, focus));
    const bool guest = accountValue.empty();

    const std::string labels[6] = {tr("settings_language", lang), tr("settings_account", lang), tr("autolaunch", lang),
                                   tr("settings_link", lang), tr("settings_about", lang), tr("settings_signout", lang)};
    static const char* kLangNative[8] = {"English", "\xD8\xA7\xD9\x84\xD8\xB9\xD8\xB1\xD8\xA8\xD9\x8A\xD8\xA9", "Fran\xC3\xA7\x61is",
        "T\xC3\xBCrk\xC3\xA7\x65", "Espa\xC3\xB1ol", "\xD0\xA0\xD1\x83\xD1\x81\xD1\x81\xD0\xBA\xD0\xB8\xD0\xB9",
        "Portugu\xC3\xAAs", "\xE4\xB8\xAD\xE6\x96\x87"};
    const int curLang = langIndexOf(lang);
    const std::string values[6] = {kLangNative[curLang], guest ? tr("guest_short", lang) : accountValue, "",
                                   tr("one_device", lang), "v0.20", ""};

    for (int i = 0; i < 6; ++i) {
        const int y = top + i * pitch;
        const int rx = L.X(listX, listW);
        const bool foc = (i == f), danger = (i == 5);
        const SDL_Color acc = kSettingsAccent[i];
        if (foc) e.drawRoundGradH(rx, y, listW, rowH, 30, SDL_Color{30, 44, 72, 255}, SDL_Color{20, 30, 50, 255});
        else e.drawRoundRect(rx, y, listW, rowH, 30, danger ? SDL_Color{34, 22, 34, 255} : kSF);
        e.drawRoundBorder(rx, y, listW, rowH, 30, 2, foc ? SDL_Color{60, 86, 130, 255} : (danger ? SDL_Color{92, 44, 56, 255} : kBD));
        const int ts = 64;
        const int tx = L.X(listX + 22, ts);
        e.drawRoundRect(tx, y + (rowH - ts) / 2, ts, ts, 20, SDL_Color{acc.r, acc.g, acc.b, 40});
        e.drawRoundBorder(tx, y + (rowH - ts) / 2, ts, ts, 20, 2, SDL_Color{acc.r, acc.g, acc.b, 90});
        settingsIcon(e, i, tx + 16, y + (rowH - ts) / 2 + 16, 32, acc);
        const int labX = listX + 22 + ts + 22;
        const int endReserve = (i == 2) ? 130 : (i == 5 ? 70 : 250);
        boxText(e, L, labels[i], labX, listW - (labX - listX) - endReserve, y + (rowH - 36) / 2, 30, danger ? kERT : kWHT);
        if (i == 2) {
            switchCtl(e, L.X(listX + listW - 28 - 88, 88), y + (rowH - 46) / 2, autoLaunchOn, L.rtl);
        } else {
            if (!values[i].empty()) endText(e, L, values[i], listX, listW - 62, y + (rowH - 28) / 2, 24, kMU);
            if (i != 4) chevron(e, L.X(listX + listW - 34, 12), y + rowH / 2 - 9, 9, !L.rtl, 3, danger ? kERT : SDL_Color{120, 140, 175, 255});
        }
        if (foc) focusRing(e, rx, y, listW, rowH, 30);
    }

    // detail panel
    const int prx = L.X(panX, panW);
    e.drawRoundRect(prx, panY, panW, panH, 34, kBAR);
    e.drawRoundBorder(prx, panY, panW, panH, 34, 2, kBD);
    {
        const SDL_Color acc = kSettingsAccent[f];
        const int hx = L.X(panX + 36, 76);
        e.drawRoundRect(hx, panY + 32, 76, 76, 22, SDL_Color{acc.r, acc.g, acc.b, 44});
        e.drawRoundBorder(hx, panY + 32, 76, 76, 22, 2, SDL_Color{acc.r, acc.g, acc.b, 110});
        settingsIcon(e, f, hx + 16, panY + 48, 44, acc);
        const int avail = panW - 36 - 76 - 24 - 36;
        if (textW(e, L, labels[f], 34, FontRole::Pixel) <= avail)
            boxText(e, L, labels[f], panX + 36 + 76 + 24, avail, panY + 44, 34, f == 5 ? kERT : kWHT, FontRole::Pixel);
        else
            boxText(e, L, labels[f], panX + 36 + 76 + 24, avail, panY + 48, 30, f == 5 ? kERT : kWHT);
    }
    const int innerX = panX + 36, innerW = panW - 72;
    int cy = panY + 136;
    e.fillRect(prx + 36, cy - 12, panW - 72, 2, kBD);
    switch (f) {
        case 0: {
            cy = paragraphAt(e, L, tr("lang_note", lang), innerX, innerW, cy + 6, 24, kMU, 3, 10) + 12;
            const int cw = (innerW - 24) / 2;
            static const char* kFlags[8] = {"flag_en", "flag_ar", "flag_fr", "flag_tr", "flag_es", "flag_ru", "flag_pt", "flag_zh"};
            for (int i = 0; i < 8; ++i) {
                const int xl = innerX + (i % 2) * (cw + 24);
                const int yy = cy + (i / 2) * 92;
                const int rx = L.X(xl, cw);
                const bool sel = (i == curLang);
                e.drawRoundRect(rx, yy, cw, 80, 24, sel ? SDL_Color{28, 60, 118, 255} : kSF);
                e.drawRoundBorder(rx, yy, cw, 80, 24, 2, sel ? kBLl : kBD);
                e.drawCircle(L.X(xl + 18, 52) + 26, yy + 40, 27, SDL_Color{255, 255, 255, 30});
                e.drawImageRounded(kFlags[i], L.X(xl + 18, 52), yy + 14, 52, 52, 26);
                boxText(e, L, kLangNative[i], xl + 18 + 52 + 18, cw - 88 - 64, yy + 24, 28, sel ? kWHT : kCHIPTX);
                const int rcx = L.X(xl + cw - 38, 26) + 13;
                e.drawRoundBorder(rcx - 13, yy + 27, 26, 26, 13, 3, sel ? kBLl : SDL_Color{74, 92, 126, 255});
                if (sel) e.drawCircle(rcx, yy + 40, 7, kBLl);
            }
            break;
        }
        case 1: {
            avatarCircle(e, L.X(innerX, 120) + 0, cy + 22, 120, true);
            const int tX = innerX + 120 + 32, tW = innerW - 120 - 32;
            boxText(e, L, guest ? tr("guest_short", lang) : accountValue, tX, tW, cy + 32, 34, kWHT);
            const std::string st = guest ? tr("guest_short", lang) : tr("signed_in", lang);
            const int cw = textW(e, L, st, 22) + 60;
            const int crx = L.X(tX, cw);
            chipBox(e, crx, cy + 90, cw, 42, guest ? SDL_Color{240, 160, 75, 28} : SDL_Color{63, 191, 106, 30},
                    guest ? SDL_Color{240, 160, 75, 120} : SDL_Color{63, 191, 106, 120});
            e.drawCircle(L.rtl ? crx + cw - 24 : crx + 24, cy + 111, 7, guest ? kWARN : kGRN);
            boxText(e, L, st, tX + 42, cw - 52, cy + 98, 22, guest ? SDL_Color{248, 190, 130, 255} : kGRN2);
            paragraphAt(e, L, tr(guest ? "sign_in_code_sub" : "account_hint", lang), innerX, innerW, cy + 176, 26, kMU, 4, 12);
            break;
        }
        case 2: {
            const int rx = L.X(innerX, innerW);
            e.drawRoundRect(rx, cy + 8, innerW, 120, 28, kSF);
            e.drawRoundBorder(rx, cy + 8, innerW, 120, 28, 2, autoLaunchOn ? SDL_Color{46, 123, 239, 140} : kBD);
            boxText(e, L, tr(autoLaunchOn ? "autolaunch_on" : "autolaunch_off", lang), innerX + 28, innerW - 56 - 120, cy + 8 + 42, 30,
                    autoLaunchOn ? kWHT : kMU);
            switchCtl(e, L.X(innerX + innerW - 28 - 88, 88), cy + 8 + 37, autoLaunchOn, L.rtl);
            paragraphAt(e, L, tr("autolaunch_desc", lang), innerX, innerW, cy + 160, 26, kMU, 4, 12);
            break;
        }
        case 3: {
            const char* steps[3] = {"open_app_phone", "go_settings_link", "enter_code"};
            for (int i = 0; i < 3; ++i) {
                const int yy = cy + 6 + i * 100;
                const int rx = L.X(innerX, innerW);
                e.drawRoundRect(rx, yy, innerW, 86, 26, kSF);
                e.drawRoundBorder(rx, yy, innerW, 86, 26, 2, kBD);
                const int nx = L.X(innerX + 22, 50);
                e.drawRoundGradD(nx, yy + 18, 50, 50, 25, kBL, kBL2);
                e.drawTextCentered(num(i + 1), nx + 25, yy + 43, 28, kWHT, FontRole::Pixel, false);
                boxText(e, L, tr(steps[i], lang), innerX + 22 + 50 + 22, innerW - 22 - 50 - 22 - 24, yy + 26, 26, kCHIPTX);
            }
            e.drawRoundRect(L.X(innerX, innerW), cy + 330, innerW, 78, 26, SDL_Color{46, 123, 239, 40});
            e.drawRoundBorder(L.X(innerX, innerW), cy + 330, innerW, 78, 26, 2, SDL_Color{111, 168, 255, 140});
            e.drawTextCentered(tr("press_x_code", lang), L.X(innerX, innerW) + innerW / 2, cy + 369, 26, kBLl, FontRole::Body, L.rtl);
            break;
        }
        case 4: {
            boxText(e, L, "ADDONSTATION", innerX, innerW, cy + 10, 40, kWHT, FontRole::Pixel);
            boxText(e, L, tr("version", lang) + " 0.20", innerX, innerW, cy + 68, 26, kGD);
            cy = paragraphAt(e, L, tr("tagline", lang), innerX, innerW, cy + 116, 26, kMU, 3, 12) + 20;
            const int rx = L.X(innerX, innerW);
            e.drawRoundRect(rx, cy, innerW, 120, 26, kSF);
            e.drawRoundBorder(rx, cy, innerW, 120, 26, 2, kBD);
            boxText(e, L, tr("credits", lang), innerX + 26, innerW - 52, cy + 20, 22, kGD);
            boxText(e, L, "F77 Minecraft font \xC2\xB7 CC BY-ND", innerX + 26, innerW - 52, cy + 60, 26, kCHIPTX);
            break;
        }
        default: {
            const int rx = L.X(innerX, innerW);
            e.drawRoundRect(rx, cy + 8, innerW, 140, 28, SDL_Color{255, 107, 120, 22});
            e.drawRoundBorder(rx, cy + 8, innerW, 140, 28, 2, SDL_Color{255, 107, 120, 110});
            icExit(e, L.X(innerX + 28, 48), cy + 8 + 46, 48, kERT);
            paragraphAt(e, L, tr("signout_sub", lang), innerX + 28 + 48 + 22, innerW - 28 - 48 - 22 - 24, cy + 8 + 34, 26, SDL_Color{255, 170, 178, 255}, 3, 12);
            break;
        }
    }
    drawTopNav(e, 3, langCode, 1250);
    drawLegendBar(e, {{"\xE2\x9C\x95", "select"}, {"\xE2\x97\x8B", "back"}}, langCode);
}

// Deprecated: old call with the notifications toggle. Notifications row no longer exists,
// so old focus indices are remapped (0,1 -> same, 2 -> auto-launch, 3..6 -> 2..5).
void drawSettings(UiEngine& e, int focus, bool notifOn, bool autoLaunchOn, const std::string& accountValue,
                  const std::string& langCode) {
    (void)notifOn;
    const int nf = focus <= 1 ? focus : (focus == 2 ? 2 : focus - 1);
    drawSettings(e, nf, autoLaunchOn, accountValue, langCode);
}

void drawConfirmDialog(UiEngine& e, const std::string& title, const std::string& sub, const std::string& yesLabel,
                       const std::string& noLabel, int focus, const std::string& langCode) {
    Lay L; L.rtl = (langCode == "ar");
    auto localize = [&](const std::string& s, const char* key) { return s == tr(key, "en") ? tr(key, langCode) : s; };
    const std::string tTitle = localize(title, "signout_q");
    const std::string tSub = localize(sub, "signout_sub");
    const std::string tYes = localize(yesLabel, "settings_signout");
    const std::string tNo = localize(noLabel, "cancel");

    e.fillRect(0, 0, 1920, 1080, kSCRIM);
    const int w = 760, h = 640, x = 960 - w / 2, y = 540 - h / 2;
    e.dropShadow(x, y, w, h, 36, 30, 190);
    e.drawRoundRect(x, y, w, h, 36, kBAR);
    e.drawRoundBorder(x, y, w, h, 36, 2, kBD);
    e.drawCircle(960, y + 91, 55, SDL_Color{255, 107, 120, 38});
    e.drawRoundBorder(960 - 55, y + 36, 110, 110, 55, 2, SDL_Color{255, 107, 120, 110});
    icExit(e, 960 - 29, y + 36 + 26, 58, kERT);
    e.drawTextCentered(tTitle, 960, y + 196, 40, kWHT, FontRole::Pixel, L.rtl);
    {
        const auto lines = wrapLines(e, tSub, 26, w - 120, L.rtl);
        int yy = y + 246;
        for (const auto& ln : lines) { e.drawTextCentered(ln, 960, yy + 14, 26, kMU, FontRole::Body, L.rtl); yy += 38; }
    }
    const int bw = w - 96, bx = x + 48;
    e.drawRoundRect(bx, y + 380, bw, 96, 28, SDL_Color{255, 255, 255, 26});
    e.drawRoundBorder(bx, y + 380, bw, 96, 28, 3, SDL_Color{255, 255, 255, 72});
    e.drawTextCentered(tNo, 960, y + 380 + 48, 32, kWHT, FontRole::Body, L.rtl);
    if (focus == 0) focusRing(e, bx, y + 380, bw, 96, 28);          // safe choice is focused first
    e.drawRoundRect(bx, y + 500, bw, 96, 28, SDL_Color{255, 107, 120, 40});
    e.drawRoundBorder(bx, y + 500, bw, 96, 28, 3, SDL_Color{255, 107, 120, 255});
    e.drawTextCentered(tYes, 960, y + 500 + 48, 32, kERT, FontRole::Body, L.rtl);
    if (focus == 1) focusRing(e, bx, y + 500, bw, 96, 28);
}

} // namespace pkgtest
