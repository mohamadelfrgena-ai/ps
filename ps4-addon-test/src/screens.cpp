#include "screens.hpp"
#include <sys/stat.h>
#include <ctime>

// AddonStation PS4 UI v5 — data-driven screens (see screens.hpp / docs).

#include <cstdio>
#include <ctime>
#include <cstdlib>
#include <cstring>

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
        {"sign_in_continue", {{"en", "Session expired — link again to continue"},
                             {"ar", "انتهت الجلسة — أعد الربط للمتابعة"},
                             {"fr", "Session expirée — reliez à nouveau pour continuer"},
                             {"tr", "Oturum süresi doldu — devam etmek için yeniden bağlayın"},
                             {"es", "Sesión caducada — vuelve a vincular para continuar"},
                             {"ru", "Сессия истекла — свяжите аккаунт заново"},
                             {"pt", "Sessão expirada — ligue novamente para continuar"},
                             {"zh", "会话已过期 — 请重新绑定以继续"}}},
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
        {"queued_state", {{"en", "Waiting for Minecraft"}, {"ar", "بانتظار Minecraft"}, {"fr", "En attente de Minecraft"},
                          {"tr", "Minecraft bekleniyor"}, {"es", "Esperando Minecraft"}, {"ru", "Ожидание Minecraft"},
                          {"pt", "Aguardando Minecraft"}, {"zh", "等待 Minecraft"}}},
        {"queued_hint", {{"en", "Launch Minecraft, press PS and return here — it installs automatically"},
                         {"ar", "شغّل Minecraft ثم اضغط زر PS وعُد للتطبيق — يُثبَّت تلقائياً"},
                         {"fr", "Lancez Minecraft, appuyez sur PS et revenez ici — installation auto"},
                         {"tr", "Minecraft'ı başlat, PS'ye bas ve buraya dön — otomatik kurulur"},
                         {"es", "Inicia Minecraft, pulsa PS y vuelve aquí — se instala solo"},
                         {"ru", "Запустите Minecraft, нажмите PS и вернитесь — установится само"},
                         {"pt", "Inicie Minecraft, aperte PS e volte aqui — instala sozinho"},
                         {"zh", "启动 Minecraft，按 PS 键回到这里 — 自动安装"}}},
        {"need_game_first", {{"en", "Launch Minecraft first, then come back and press Download."}, {"ar", "شغّل ماينكرفت أولاً ثم ارجع واضغط تنزيل"}, {"fr", "Lance d'abord Minecraft, puis reviens et appuie sur T\u00e9l\u00e9charger"}, {"tr", "\u00d6nce Minecraft'\u0131 ba\u015Flat, sonra \u0130ndir'e bas"}, {"es", "Inicia Minecraft primero y luego pulsa Descargar"}, {"ru", "\u0421\u043d\u0430\u0447\u0430\u043b\u0430 \u0437\u0430\u043f\u0443\u0441\u0442\u0438 Minecraft, \u0437\u0430\u0442\u0435\u043c \u043d\u0430\u0436\u043c\u0438 \u00ab\u0421\u043a\u0430\u0447\u0430\u0442\u044c\u00bb"}, {"pt", "Inicie o Minecraft primeiro e depois toque em Baixar"}, {"zh", "\u5148\u542f\u52a8 Minecraft\uff0c\u518d\u6309\u4e0b\u8f7d"}}},
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
        {"up_to_50", {{"en", "50% off all addons and worlds"}, {"ar", "خصم 50٪ على كل الإضافات والعوالم"}, {"fr", "-50% sur tous les addons et mondes"}, {"tr", "Tüm eklenti ve dünyalarda %50 indirim"}, {"es", "50% de descuento en todos los addons y mundos"}, {"ru", "Скидка 50% на все аддоны и миры"}, {"pt", "50% de desconto em todos os addons e mundos"}, {"zh", "所有附加包和世界五折优惠"}}},
        {"nav_search", {{"en", "Search"}, {"ar", "\u0628\u062d\u062b"}, {"fr", "Recherche"}, {"tr", "Ara"},
                         {"es", "Buscar"}, {"ru", "\u041f\u043e\u0438\u0441\u043a"}, {"pt", "Pesquisar"}, {"zh", "\u641c\u7d22"}}},
        {"mine_title", {{"en", "Downloads"}, {"ar", "\u0627\u0644\u062a\u0646\u0632\u064a\u0644\u0627\u062a"}, {"fr", "T\u00e9l\u00e9chargements"}, {"tr", "\u0130ndirmeler"}, {"es", "Descargas"}, {"ru", "\u0417\u0430\u0433\u0440\u0443\u0437\u043a\u0438"}, {"pt", "Downloads"}, {"zh", "\u4e0b\u8f7d"}}},
        {"mine_sub", {{"en", "Every addon installed on this console \u2014 with its own pack icon"},
                      {"ar", "\u0643\u0644 \u0627\u0644\u0625\u0636\u0627\u0641\u0627\u062a \u0627\u0644\u0645\u062b\u0628\u062a\u0629 \u0639\u0644\u0649 \u0647\u0630\u0627 \u0627\u0644\u062c\u0647\u0627\u0632 \u2014 \u0628\u0623\u064a\u0642\u0648\u0646\u062a\u0647\u0627 \u0627\u0644\u0623\u0635\u0644\u064a\u0629"},
                      {"fr", "Tous les addons install\u00e9s sur cette console \u2014 avec leur propre ic\u00f4ne"},
                      {"tr", "Bu konsolda kurulmu\u015f t\u00fcm eklentiler \u2014 kendi paket simgeleriyle"},
                      {"es", "Todos los addons instalados en esta consola \u2014 con su propio icono"},
                      {"ru", "\u0412\u0441\u0435 \u0430\u0434\u0434\u043e\u043d\u044b \u043d\u0430 \u044d\u0442\u043e\u0439 \u043a\u043e\u043d\u0441\u043e\u043b\u0438 \u2014 \u0441\u043e \u0441\u0432\u043e\u0438\u043c\u0438 \u0437\u043d\u0430\u0447\u043a\u0430\u043c\u0438"},
                      {"pt", "Todos os addons instalados neste console \u2014 com o \u00edcone do pr\u00f3prio pacote"},
                      {"zh", "\u6b64\u4e3b\u673a\u4e0a\u5df2\u5b89\u88c5\u7684\u6240\u6709\u9644\u52a0\u5305 \u2014 \u4f7f\u7528\u5305\u5185\u56fe\u6807"}}},
        {"mine_check", {{"en", "Check for updates"}, {"ar", "\u0641\u062d\u0635 \u0639\u0646 \u0627\u0644\u062a\u062d\u062f\u064a\u062b\u0627\u062a"}, {"fr", "Rechercher des mises \u00e0 jour"}, {"tr", "G\u00fcncellemeleri denetle"},
                        {"es", "Buscar actualizaciones"}, {"ru", "\u041f\u0440\u043e\u0432\u0435\u0440\u0438\u0442\u044c \u043e\u0431\u043d\u043e\u0432\u043b\u0435\u043d\u0438\u044f"}, {"pt", "Procurar atualiza\u00e7\u00f5es"}, {"zh", "\u68c0\u67e5\u66f4\u65b0"}}},
        {"mine_recent", {{"en", "Installed addons"}, {"ar", "\u0627\u0644\u0625\u0636\u0627\u0641\u0627\u062a \u0627\u0644\u0645\u062b\u0628\u062a\u0629"}, {"fr", "Addons install\u00e9s"}, {"tr", "Kurulu eklentiler"},
                          {"es", "Addons instalados"}, {"ru", "\u0423\u0441\u0442\u0430\u043d\u043e\u0432\u043b\u0435\u043d\u043d\u044b\u0435 \u0430\u0434\u0434\u043e\u043d\u044b"}, {"pt", "Addons instalados"}, {"zh", "\u5df2\u5b89\u88c5\u7684\u9644\u52a0\u5305"}}},
        {"mine_modified", {{"en", "Modified"}, {"ar", "\u0622\u062e\u0631 \u062a\u0639\u062f\u064a\u0644"}, {"fr", "Modifi\u00e9"}, {"tr", "De\u011fi\u015ftirilme"},
                            {"es", "Modificado"}, {"ru", "\u0418\u0437\u043c\u0435\u043d\u0435\u043d\u043e"}, {"pt", "Modificado"}, {"zh", "\u4fee\u6539\u65e5\u671f"}}},
        {"mine_open", {{"en", "Open"}, {"ar", "\u0641\u062a\u062d"}, {"fr", "Ouvrir"}, {"tr", "A\u00e7"},
                        {"es", "Abrir"}, {"ru", "\u041e\u0442\u043a\u0440\u044b\u0442\u044c"}, {"pt", "Abrir"}, {"zh", "\u6253\u5f00"}}},
        {"mine_empty_t", {{"en", "No downloads yet"}, {"ar", "\u0644\u0627 \u062a\u0648\u062c\u062f \u062a\u0646\u0632\u064a\u0644\u0627\u062a \u0628\u0639\u062f"}, {"fr", "Aucun t\u00e9l\u00e9chargement"}, {"tr", "Hen\u00fcz indirme yok"},
                           {"es", "A\u00fan no hay descargas"}, {"ru", "\u0417\u0430\u0433\u0440\u0443\u0437\u043e\u043a \u043f\u043e\u043a\u0430 \u043d\u0435\u0442"}, {"pt", "Nenhum download ainda"}, {"zh", "\u6682\u65e0\u4e0b\u8f7d"}}},
        {"mine_empty_s", {{"en", "Install an addon and it will show up here."},
                          {"ar", "\u062b\u0628\u062a \u0625\u0636\u0627\u0641\u0629 \u0648\u0633\u062a\u0638\u0647\u0631 \u0647\u0646\u0627."},
                          {"fr", "Installez un addon et il appara\u00eetra ici."},
                          {"tr", "Bir eklenti kurun, burada g\u00f6r\u00fcnecek."},
                          {"es", "Instala un addon y aparecer\u00e1 aqu\u00ed."},
                          {"ru", "\u0423\u0441\u0442\u0430\u043d\u043e\u0432\u0438\u0442\u0435 \u0430\u0434\u0434\u043e\u043d \u2014 \u043e\u043d \u043f\u043e\u044f\u0432\u0438\u0442\u0441\u044f \u0437\u0434\u0435\u0441\u044c."},
                          {"pt", "Instale um addon e ele aparecer\u00e1 aqui."},
                          {"zh", "\u5b89\u88c5\u4e00\u4e2a\u9644\u52a0\u5305\uff0c\u5b83\u4f1a\u51fa\u73b0\u5728\u8fd9\u91cc\u3002"}}},
        {"mine_checked", {{"en", "Checked for updates \u2014 you are up to date."}, {"ar", "\u062a\u0645 \u0627\u0644\u0641\u062d\u0635 \u0639\u0646 \u0627\u0644\u062a\u062d\u062f\u064a\u062b\u0627\u062a \u2014 \u0643\u0644 \u0634\u064a\u0621 \u0645\u062d\u062f\u062b."},
                            {"fr", "Mises \u00e0 jour v\u00e9rifi\u00e9es \u2014 tout est \u00e0 jour."}, {"tr", "G\u00fcncellemeler kontrol edildi \u2014 her\u015fey g\u00fcncel."},
                            {"es", "Actualizaciones comprobadas \u2014 todo est\u00e1 al d\u00eda."}, {"ru", "\u041e\u0431\u043d\u043e\u0432\u043b\u0435\u043d\u0438\u044f \u043f\u0440\u043e\u0432\u0435\u0440\u0435\u043d\u044b \u2014 \u0432\u0441\u0451 \u0430\u043a\u0442\u0443\u0430\u043b\u044c\u043d\u043e."},
                            {"pt", "Atualiza\u00e7\u00f5es verificadas \u2014 est\u00e1 tudo atualizado."}, {"zh", "\u5df2\u68c0\u67e5\u66f4\u65b0 \u2014 \u5168\u90e8\u5df2\u662f\u6700\u65b0\u3002"}}},
        {"nav_mine", {{"en", "My Addons"}, {"ar", "\u062a\u0646\u0632\u064a\u0644\u0627\u062a\u064a"}, {"fr", "Mes addons"}, {"tr", "Eklentilerim"},
                       {"es", "Mis addons"}, {"ru", "\u041c\u043e\u0438 \u0430\u0434\u0434\u043e\u043d\u044b"}, {"pt", "Meus addons"}, {"zh", "\u6211\u7684\u9644\u52a0\u5305"}}},
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
        {"err_network", {{"en", "Network error — try again"},
                         {"ar", "خطأ في الشبكة — حاول مجدداً"},
                         {"fr", "Erreur réseau — réessayez"},
                         {"tr", "Ağ hatası — tekrar deneyin"},
                         {"es", "Error de red — inténtalo de nuevo"},
                         {"ru", "Ошибка сети — попробуйте снова"},
                         {"pt", "Erro de rede — tente novamente"},
                         {"zh", "网络错误 — 请重试"}}},
        {"err_not_enough_ac", {{"en", "Not enough AC — top up your wallet"},
                               {"ar", "رصيد AC غير كافٍ — اشحن محفظتك"},
                               {"fr", "AC insuffisants — rechargez votre portefeuille"},
                               {"tr", "AC yetersiz — cüzdanınıza yükleme yapın"},
                               {"es", "AC insuficientes — recarga tu cartera"},
                               {"ru", "Недостаточно AC — пополните кошелёк"},
                               {"pt", "AC insuficientes — carregue a sua carteira"},
                               {"zh", "AC 不足 — 请充值钱包"}}},
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
        {"search", {{"en", "Search"}, {"ar", "بحث"}, {"fr", "Rechercher"}, {"tr", "Ara"}, {"es", "Buscar"}, {"ru", "Поиск"}, {"pt", "Pesquisar"}, {"zh", "搜索"}}},
        {"lang_note", {{"en", "The app changes language right away. Arabic switches the whole layout to right-to-left."}, {"ar", "يتغير لغة التطبيق فوراً. العربية تقلب التصميم كله من اليمين لليسار."}, {"fr", "La langue change immédiatement. L'arabe inverse toute la disposition de droite à gauche."}, {"tr", "Uygulama dilini hemen değiştirir. Arapça tüm yerleşimi sağdan sola çevirir."}, {"es", "El idioma cambia al instante. El árabe invierte todo el diseño de derecha a izquierda."}, {"ru", "Язык меняется сразу. Арабский переключает весь интерфейс на справа налево."}, {"pt", "O idioma muda imediatamente. O árabe inverte todo o layout da direita para a esquerda."}, {"zh", "应用语言即时切换。阿拉伯语会将整个布局变为从右到左。"}}},
        {"guest_short", {{"en", "Guest"}, {"ar", "زائر"}, {"fr", "Invité"}, {"tr", "Misafir"}, {"es", "Invitado"}, {"ru", "Гость"}, {"pt", "Convidado"}, {"zh", "访客"}}},
        {"signed_in", {{"en", "Signed in"}, {"ar", "مسجّل الدخول"}, {"fr", "Connecté"}, {"tr", "Giriş yapıldı"}, {"es", "Sesión iniciada"}, {"ru", "Вход выполнен"}, {"pt", "Conectado"}, {"zh", "已登录"}}},
        {"account_hint", {{"en", "Your account is linked to this console. Manage it in the AddonStation app on your phone or PC."}, {"ar", "حسابك مرتبط بهذا الجهاز. تدير حسابك من تطبيق AddonStation على جوالك أو الكمبيوتر."}, {"fr", "Votre compte est lié à cette console. Gérez-le dans l'application AddonStation sur votre téléphone ou PC."}, {"tr", "Hesabınız bu konsola bağlı. Telefonunuzdaki veya bilgisayarınızdaki AddonStation uygulamasından yönetin."}, {"es", "Tu cuenta está vinculada a esta consola. Gestiónala en la app AddonStation de tu teléfono o PC."}, {"ru", "Ваш аккаунт привязан к этой консоли. Управляйте им в приложении AddonStation на телефоне или ПК."}, {"pt", "A sua conta está vinculada a este console. Gerencie no aplicativo AddonStation do seu telefone ou PC."}, {"zh", "您的账户已绑定到此主机。可在手机或电脑的 AddonStation 应用中管理。"}}},
        {"one_device", {{"en", "1 device"}, {"ar", "جهاز واحد"}, {"fr", "1 appareil"}, {"tr", "1 cihaz"}, {"es", "1 dispositivo"}, {"ru", "1 устройство"}, {"pt", "1 dispositivo"}, {"zh", "1 台设备"}}},
        {"autolaunch_desc", {{"en", "When this is on, Minecraft opens right after an addon finishes installing. It is off by default."}, {"ar", "عند التفعيل يفتح Minecraft مباشرة بعد اكتمال تثبيت الإضافة. الخيار مُعطّل افتراضياً."}, {"fr", "Quand c'est activé, Minecraft s'ouvre juste après l'installation d'un addon. Désactivé par défaut."}, {"tr", "Bu açıkken, bir eklenti kurulduktan hemen sonra Minecraft açılır. Varsayılan olarak kapalıdır."}, {"es", "Cuando está activado, Minecraft se abre justo después de instalar un addon. Está desactivado por defecto."}, {"ru", "Когда включено, Minecraft запускается сразу после установки аддона. По умолчанию выключено."}, {"pt", "Quando ativado, o Minecraft abre logo após a instalação de um addon. Desativado por padrão."}, {"zh", "开启后，安装完附加包会立即启动 Minecraft。默认关闭。"}}},
        {"open_app_phone", {{"en", "Open AddonStation on your phone or PC"}, {"ar", "افتح AddonStation على جوالك أو الكمبيوتر"}, {"fr", "Ouvrez AddonStation sur votre téléphone ou PC"}, {"tr", "Telefonunuzda veya bilgisayarınızda AddonStation'ı açın"}, {"es", "Abre AddonStation en tu teléfono o PC"}, {"ru", "Откройте AddonStation на телефоне или ПК"}, {"pt", "Abra o AddonStation no seu telefone ou PC"}, {"zh", "在手机或电脑上打开 AddonStation"}}},
        {"go_settings_link", {{"en", "Go to Settings, then “Link a console”"}, {"ar", "اذهب إلى الإعدادات ثم ربط جهاز"}, {"fr", "Allez dans Réglages, puis « Lier une console »"}, {"tr", "Ayarlar'a gidin, ardından \"Bir konsol bağla\" seçeneğine dokunun"}, {"es", "Ve a Ajustes y luego a «Vincular una consola»"}, {"ru", "Откройте Настройки, затем «Привязать консоль»"}, {"pt", "Vá em Configurações e depois em \"Vincular um console\""}, {"zh", "进入设置，然后点“绑定主机”"}}},
        {"enter_code", {{"en", "Enter this code"}, {"ar", "أدخل هذا الرمز"}, {"fr", "Entrez ce code"}, {"tr", "Bu kodu girin"}, {"es", "Introduce este código"}, {"ru", "Введите этот код"}, {"pt", "Digite este código"}, {"zh", "输入此代码"}}},
        {"press_x_code", {{"en", "Press ✕ to get a code"}, {"ar", "اضغط ✕ للحصول على رمز"}, {"fr", "Appuyez sur ✕ pour obtenir un code"}, {"tr", "Kod almak için ✕ tuşuna basın"}, {"es", "Pulsa ✕ para obtener un código"}, {"ru", "Нажмите ✕, чтобы получить код"}, {"pt", "Pressione ✕ para obter um código"}, {"zh", "按 ✕ 获取代码"}}},
        {"version", {{"en", "Version"}, {"ar", "الإصدار"}, {"fr", "Version"}, {"tr", "Sürüm"}, {"es", "Versión"}, {"ru", "Версия"}, {"pt", "Versão"}, {"zh", "版本"}}},
        {"signout_q", {{"en", "Sign out?"}, {"ar", "تسجيل الخروج؟"}, {"fr", "Se déconnecter ?"}, {"tr", "Çıkış yapılsın mı?"}, {"es", "¿Cerrar sesión?"}, {"ru", "Выйти?"}, {"pt", "Sair?"}, {"zh", "退出登录？"}}},
        {"signout_sub", {{"en", "You can sign in again any time with a code."}, {"ar", "تقدر تدخل مرة أخرى في أي وقت برمز."}, {"fr", "Vous pourrez vous reconnecter avec un code à tout moment."}, {"tr", "Dilediğiniz zaman bir kodla tekrar giriş yapabilirsiniz."}, {"es", "Puedes volver a iniciar sesión con un código cuando quieras."}, {"ru", "Вы сможете снова войти с помощью кода в любое время."}, {"pt", "Você pode entrar novamente com um código a qualquer momento."}, {"zh", "您随时可以用代码重新登录。"}}},
        {"profile", {{"en", "Profile"}, {"ar", "الملف الشخصي"}, {"fr", "Profil"}, {"tr", "Profil"}, {"es", "Perfil"}, {"ru", "Профиль"}, {"pt", "Perfil"}, {"zh", "个人资料"}}},
        {"wallet", {{"en", "AC Wallet"}, {"ar", "محفظة AC"}, {"fr", "Portefeuille AC"}, {"tr", "AC Cüzdanı"}, {"es", "Monedero AC"}, {"ru", "Кошелёк AC"}, {"pt", "Carteira AC"}, {"zh", "AC 钱包"}}},
        {"plans", {{"en", "Plans"}, {"ar", "الخطط"}, {"fr", "Abonnements"}, {"tr", "Planlar"}, {"es", "Planes"}, {"ru", "Тарифы"}, {"pt", "Planos"}, {"zh", "套餐"}}},
        {"menu", {{"en", "Menu"}, {"ar", "القائمة"}, {"fr", "Menu"}, {"tr", "Menü"}, {"es", "Menú"}, {"ru", "Меню"}, {"pt", "Menu"}, {"zh", "菜单"}}},
        {"balance", {{"en", "Your balance"}, {"ar", "رصيدك"}, {"fr", "Votre solde"}, {"tr", "Bakiyeniz"}, {"es", "Tu saldo"}, {"ru", "Ваш баланс"}, {"pt", "Seu saldo"}, {"zh", "您的余额"}}},
        {"history", {{"en", "History"}, {"ar", "السجل"}, {"fr", "Historique"}, {"tr", "Geçmiş"}, {"es", "Historial"}, {"ru", "История"}, {"pt", "Histórico"}, {"zh", "记录"}}},
        {"tx_purchase", {{"en", "Purchase"}, {"ar", "شراء"}, {"fr", "Achat"}, {"tr", "Satın alma"}, {"es", "Compra"}, {"ru", "Покупка"}, {"pt", "Compra"}, {"zh", "购买"}}},
        {"tx_topup", {{"en", "Top-up"}, {"ar", "شحن"}, {"fr", "Recharge"}, {"tr", "Yükleme"}, {"es", "Recarga"}, {"ru", "Пополнение"}, {"pt", "Recarga"}, {"zh", "充值"}}},
        {"tx_gift", {{"en", "Gift"}, {"ar", "هدية"}, {"fr", "Cadeau"}, {"tr", "Hediye"}, {"es", "Regalo"}, {"ru", "Подарок"}, {"pt", "Presente"}, {"zh", "礼物"}}},
        {"topup_hint", {{"en", "Top up your balance from the AddonStation app on your phone or PC."}, {"ar", "اشحن رصيدك من تطبيق AddonStation على جوالك أو الكمبيوتر."}, {"fr", "Rechargez votre solde depuis l'application AddonStation sur votre téléphone ou PC."}, {"tr", "Bakiyenizi telefonunuzdaki veya bilgisayarınızdaki AddonStation uygulamasından yükleyin."}, {"es", "Recarga tu saldo desde la app AddonStation de tu teléfono o PC."}, {"ru", "Пополните баланс в приложении AddonStation на телефоне или ПК."}, {"pt", "Recarregue seu saldo no aplicativo AddonStation do seu telefone ou PC."}, {"zh", "可在手机或电脑的 AddonStation 应用中充值余额。"}}},
        {"plan_free", {{"en", "Free"}, {"ar", "مجاني"}, {"fr", "Gratuit"}, {"tr", "Ücretsiz"}, {"es", "Gratis"}, {"ru", "Бесплатный"}, {"pt", "Grátis"}, {"zh", "免费"}}},
        {"plan_epic", {{"en", "Epic"}, {"ar", "Epic"}, {"fr", "Epic"}, {"tr", "Epic"}, {"es", "Epic"}, {"ru", "Epic"}, {"pt", "Épico"}, {"zh", "史诗"}}},
        {"plan_legendary", {{"en", "Legendary"}, {"ar", "Legendary"}, {"fr", "Legendary"}, {"tr", "Legendary"}, {"es", "Legendary"}, {"ru", "Legendary"}, {"pt", "Lendário"}, {"zh", "传奇"}}},
        {"plan_current", {{"en", "Current plan"}, {"ar", "خطتك الحالية"}, {"fr", "Abonnement actuel"}, {"tr", "Mevcut plan"}, {"es", "Plan actual"}, {"ru", "Текущий тариф"}, {"pt", "Plano atual"}, {"zh", "当前套餐"}}},
        {"plan_free_price", {{"en", "FREE"}, {"ar", "مجاناً"}, {"fr", "GRATUIT"}, {"tr", "ÜCRETSİZ"}, {"es", "GRATIS"}, {"ru", "БЕСПЛАТНО"}, {"pt", "GRÁTIS"}, {"zh", "免费"}}},
        {"pf_free_1", {{"en", "Free addons and worlds"}, {"ar", "إضافات وعوالم مجانية"}, {"fr", "Addons et mondes gratuits"}, {"tr", "Ücretsiz eklentiler ve dünyalar"}, {"es", "Addons y mundos gratis"}, {"ru", "Бесплатные аддоны и миры"}, {"pt", "Addons e mundos grátis"}, {"zh", "免费附加包和世界"}}},
        {"pf_free_2", {{"en", "Install straight to your PS4"}, {"ar", "تثبيت مباشر على جهاز PS4"}, {"fr", "Installation directe sur votre PS4"}, {"tr", "Doğrudan PS4'ünüze kurulum"}, {"es", "Instalación directa en tu PS4"}, {"ru", "Установка прямо на вашу PS4"}, {"pt", "Instalação direta no seu PS4"}, {"zh", "直接安装到 PS4"}}},
        {"pf_epic_1", {{"en", "Everything in Free"}, {"ar", "كل مميزات المجاني"}, {"fr", "Tout le plan Gratuit"}, {"tr", "Free planındaki her şey"}, {"es", "Todo lo del plan Gratis"}, {"ru", "Всё из тарифа «Бесплатный»"}, {"pt", "Tudo do plano Grátis"}, {"zh", "包含免费套餐全部内容"}}},
        {"pf_epic_2", {{"en", "Epic addons and worlds"}, {"ar", "إضافات وعوالم Epic"}, {"fr", "Addons et mondes Epic"}, {"tr", "Epic eklentiler ve dünyalar"}, {"es", "Addons y mundos Epic"}, {"ru", "Аддоны и миры Epic"}, {"pt", "Addons e mundos Épicos"}, {"zh", "史诗附加包和世界"}}},
        {"pf_epic_3", {{"en", "Epic badge on your profile"}, {"ar", "شارة Epic على ملفك"}, {"fr", "Badge Epic sur votre profil"}, {"tr", "Profilinizde Epic rozeti"}, {"es", "Insignia Epic en tu perfil"}, {"ru", "Значок Epic в профиле"}, {"pt", "Distintivo Épico no perfil"}, {"zh", "个人资料史诗徽章"}}},
        {"pf_leg_1", {{"en", "Everything in Epic"}, {"ar", "كل مميزات Epic"}, {"fr", "Tout le plan Epic"}, {"tr", "Epic planındaki her şey"}, {"es", "Todo lo del plan Epic"}, {"ru", "Всё из тарифа Epic"}, {"pt", "Tudo do plano Épico"}, {"zh", "包含史诗套餐全部内容"}}},
        {"pf_leg_2", {{"en", "Legendary addons and worlds"}, {"ar", "إضافات وعوالم Legendary"}, {"fr", "Addons et mondes Legendary"}, {"tr", "Legendary eklentiler ve dünyalar"}, {"es", "Addons y mundos Legendary"}, {"ru", "Аддоны и миры Legendary"}, {"pt", "Addons e mundos Lendários"}, {"zh", "传奇附加包和世界"}}},
        {"pf_leg_3", {{"en", "Exclusive releases"}, {"ar", "إصدارات حصرية"}, {"fr", "Sorties exclusives"}, {"tr", "Özel sürümler"}, {"es", "Lanzamientos exclusivos"}, {"ru", "Эксклюзивные релизы"}, {"pt", "Lançamentos exclusivos"}, {"zh", "独家发布"}}},
        {"subscribe_elsewhere", {{"en", "Subscribe from your phone or PC"}, {"ar", "الاشتراك من الجوال أو الكمبيوتر"}, {"fr", "Abonnez-vous depuis votre téléphone ou PC"}, {"tr", "Telefonunuzdan veya bilgisayarınızdan abone olun"}, {"es", "Suscríbete desde tu teléfono o PC"}, {"ru", "Оформите подписку с телефона или ПК"}, {"pt", "Assine pelo seu telefone ou PC"}, {"zh", "在手机或电脑上订阅"}}},
        {"scan_to_get_app", {{"en", "Scan the code to download the app"}, {"ar", "امسح الرمز لتحميل التطبيق"}, {"fr", "Scannez le code pour télécharger l'application"}, {"tr", "Uygulamayı indirmek için kodu tarayın"}, {"es", "Escanea el código para descargar la app"}, {"ru", "Отсканируйте код, чтобы скачать приложение"}, {"pt", "Escaneie o código para baixar o app"}, {"zh", "扫码下载应用"}}},
        {"get_app_cta", {{"en", "Get the app on your phone for the best addons"}, {"ar", "حمّل التطبيق على هاتفك للحصول على أفضل الإضافات"}, {"fr", "Obtenez l'app sur votre téléphone pour les meilleurs addons"}, {"tr", "En iyi eklentiler için uygulamayı telefonunuza alın"}, {"es", "Consigue la app en tu teléfono para los mejores addons"}, {"ru", "Установите приложение на телефон ради лучших аддонов"}, {"pt", "Tenha o app no seu telefone para os melhores addons"}, {"zh", "在手机上获取应用，畅享最佳附加包"}}},
        {"search_hint", {{"en", "Type to search addons and worlds"}, {"ar", "اكتب للبحث عن الإضافات والعوالم"}, {"fr", "Tapez pour chercher des addons et mondes"}, {"tr", "Eklenti ve dünyaları aramak için yazın"}, {"es", "Escribe para buscar addons y mundos"}, {"ru", "Введите запрос для поиска аддонов и миров"}, {"pt", "Digite para buscar addons e mundos"}, {"zh", "输入以搜索附加包和世界"}}},
        {"popular", {{"en", "Popular searches"}, {"ar", "عمليات بحث شائعة"}, {"fr", "Recherches populaires"}, {"tr", "Popüler aramalar"}, {"es", "Búsquedas populares"}, {"ru", "Популярные запросы"}, {"pt", "Pesquisas populares"}, {"zh", "热门搜索"}}},
        {"results", {{"en", "results"}, {"ar", "نتيجة"}, {"fr", "résultats"}, {"tr", "sonuç"}, {"es", "resultados"}, {"ru", "результатов"}, {"pt", "resultados"}, {"zh", "条结果"}}},
        {"no_results", {{"en", "No results"}, {"ar", "لا نتائج"}, {"fr", "Aucun résultat"}, {"tr", "Sonuç yok"}, {"es", "Sin resultados"}, {"ru", "Ничего не найдено"}, {"pt", "Nenhum resultado"}, {"zh", "无结果"}}},
        {"no_results_sub", {{"en", "Try a different search word or browse the categories."}, {"ar", "جرّب كلمة بحث ثانية أو تصفّح الأقسام."}, {"fr", "Essayez un autre mot ou parcourez les catégories."}, {"tr", "Farklı bir kelime deneyin veya kategorilere göz atın."}, {"es", "Prueba otra palabra o explora las categorías."}, {"ru", "Попробуйте другое слово или просмотрите категории."}, {"pt", "Tente outra palavra ou navegue pelas categorias."}, {"zh", "换个词试试或浏览分类。"}}},
        {"stat_downloads", {{"en", "Downloads"}, {"ar", "التنزيلات"}, {"fr", "Téléchargements"}, {"tr", "İndirmeler"}, {"es", "Descargas"}, {"ru", "Загрузки"}, {"pt", "Downloads"}, {"zh", "下载量"}}},
        {"stat_saved", {{"en", "Saved"}, {"ar", "المحفوظات"}, {"fr", "Enregistrés"}, {"tr", "Kaydedilen"}, {"es", "Guardados"}, {"ru", "Сохранено"}, {"pt", "Salvos"}, {"zh", "已收藏"}}},
        {"stat_installed", {{"en", "Installed"}, {"ar", "المثبّتة"}, {"fr", "Installés"}, {"tr", "Kurulu"}, {"es", "Instalados"}, {"ru", "Установлено"}, {"pt", "Instalados"}, {"zh", "已安装"}}},
        {"recommended", {{"en", "Recommended for you"}, {"ar", "مقترحة لك"}, {"fr", "Recommandé pour vous"}, {"tr", "Sizin için önerilen"}, {"es", "Recomendado para ti"}, {"ru", "Рекомендуем вам"}, {"pt", "Recomendado para você"}, {"zh", "为你推荐"}}},
        {"exclusive", {{"en", "EXCLUSIVE"}, {"ar", "حصري"}, {"fr", "EXCLUSIF"}, {"tr", "ÖZEL"}, {"es", "EXCLUSIVO"}, {"ru", "ЭКСКЛЮЗИВ"}, {"pt", "EXCLUSIVO"}, {"zh", "独家"}}},
        {"limited_time", {{"en", "LIMITED TIME"}, {"ar", "لفترة محدودة"}, {"fr", "TEMPS LIMITÉ"}, {"tr", "SÜRELİ"}, {"es", "TIEMPO LIMITADO"}, {"ru", "ОГРАНИЧЕННО"}, {"pt", "TEMPO LIMITADO"}, {"zh", "限时"}}},
        {"days", {{"en", "DAYS"}, {"ar", "أيام"}, {"fr", "JOURS"}, {"tr", "GÜN"}, {"es", "DÍAS"}, {"ru", "ДНЕЙ"}, {"pt", "DIAS"}, {"zh", "天"}}},
        {"hrs", {{"en", "HRS"}, {"ar", "ساعات"}, {"fr", "HRS"}, {"tr", "SAAT"}, {"es", "HRS"}, {"ru", "ЧАС"}, {"pt", "HRS"}, {"zh", "小时"}}},
        {"min", {{"en", "MIN"}, {"ar", "دقائق"}, {"fr", "MIN"}, {"tr", "DK"}, {"es", "MIN"}, {"ru", "МИН"}, {"pt", "MIN"}, {"zh", "分钟"}}},
        {"chip_all", {{"en", "All"}, {"ar", "الكل"}, {"fr", "Tout"}, {"tr", "Tümü"}, {"es", "Todo"}, {"ru", "Всё"}, {"pt", "Todos"}, {"zh", "全部"}}},
        {"chip_addons", {{"en", "Addons"}, {"ar", "الإضافات"}, {"fr", "Addons"}, {"tr", "Eklentiler"}, {"es", "Addons"}, {"ru", "Аддоны"}, {"pt", "Addons"}, {"zh", "附加包"}}},
        {"chip_worlds", {{"en", "Worlds"}, {"ar", "العوالم"}, {"fr", "Mondes"}, {"tr", "Dünyalar"}, {"es", "Mundos"}, {"ru", "Миры"}, {"pt", "Mundos"}, {"zh", "世界"}}},
        {"chip_free", {{"en", "Free"}, {"ar", "مجاني"}, {"fr", "Gratuit"}, {"tr", "Ücretsiz"}, {"es", "Gratis"}, {"ru", "Бесплатно"}, {"pt", "Grátis"}, {"zh", "免费"}}},
        {"chip_epic", {{"en", "Epic"}, {"ar", "Epic"}, {"fr", "Epic"}, {"tr", "Epic"}, {"es", "Epic"}, {"ru", "Epic"}, {"pt", "Épico"}, {"zh", "史诗"}}},
        {"chip_legendary", {{"en", "Legendary"}, {"ar", "Legendary"}, {"fr", "Legendary"}, {"tr", "Legendary"}, {"es", "Legendary"}, {"ru", "Legendary"}, {"pt", "Lendário"}, {"zh", "传奇"}}},
        {"chip_new", {{"en", "New"}, {"ar", "جديد"}, {"fr", "Nouveau"}, {"tr", "Yeni"}, {"es", "Nuevo"}, {"ru", "Новое"}, {"pt", "Novo"}, {"zh", "新"}}},
        {"chip_under100", {{"en", "Under 100 AC"}, {"ar", "أقل من 100 AC"}, {"fr", "Moins de 100 AC"}, {"tr", "100 AC altı"}, {"es", "Menos de 100 AC"}, {"ru", "До 100 AC"}, {"pt", "Menos de 100 AC"}, {"zh", "低于 100 AC"}}},
        {"chip_biggest", {{"en", "Biggest discount"}, {"ar", "أكبر خصم"}, {"fr", "Plus grosse remise"}, {"tr", "En büyük indirim"}, {"es", "Mayor descuento"}, {"ru", "Максимальная скидка"}, {"pt", "Maior desconto"}, {"zh", "最大折扣"}}},
        {"chip_newest", {{"en", "Newest"}, {"ar", "الأحدث"}, {"fr", "Plus récent"}, {"tr", "En yeni"}, {"es", "Más reciente"}, {"ru", "Новейшие"}, {"pt", "Mais recente"}, {"zh", "最新"}}},
        {"winter_sale", {{"en", "WINTER SALE"}, {"ar", "عروض الشتاء"}, {"fr", "SOLDES D'HIVER"}, {"tr", "KIŞ İNDİRİMİ"}, {"es", "REBAJAS DE INVIERNO"}, {"ru", "ЗИМНЯЯ РАСПРОДАЖА"}, {"pt", "PROMOÇÃO DE INVERNO"}, {"zh", "冬季特卖"}}},
        {"description", {{"en", "DESCRIPTION"}, {"ar", "الوصف"}, {"fr", "DESCRIPTION"}, {"tr", "AÇIKLAMA"}, {"es", "DESCRIPCIÓN"}, {"ru", "ОПИСАНИЕ"}, {"pt", "DESCRIÇÃO"}, {"zh", "描述"}}},
        {"by", {{"en", "By"}, {"ar", "بواسطة"}, {"fr", "Par"}, {"tr", "Yapımcı"}, {"es", "Por"}, {"ru", "От"}, {"pt", "Por"}, {"zh", "作者"}}},
        {"your_code", {{"en", "YOUR CODE"}, {"ar", "رمزك"}, {"fr", "VOTRE CODE"}, {"tr", "KODUNUZ"}, {"es", "TU CÓDIGO"}, {"ru", "ВАШ КОД"}, {"pt", "SEU CÓDIGO"}, {"zh", "您的代码"}}},
        {"expires_in", {{"en", "Expires in"}, {"ar", "ينتهي خلال"}, {"fr", "Expire dans"}, {"tr", "Bitiş süresi"}, {"es", "Expira en"}, {"ru", "Истекает через"}, {"pt", "Expira em"}, {"zh", "剩余时间"}}},
        {"waiting", {{"en", "Waiting for you..."}, {"ar", "بانتظارك..."}, {"fr", "En attente de vous..."}, {"tr", "Sizi bekliyoruz..."}, {"es", "Esperándote..."}, {"ru", "Ждём вас..."}, {"pt", "Esperando por você..."}, {"zh", "等待您…"}}},
        {"new_code", {{"en", "New code"}, {"ar", "رمز جديد"}, {"fr", "Nouveau code"}, {"tr", "Yeni kod"}, {"es", "Código nuevo"}, {"ru", "Новый код"}, {"pt", "Novo código"}, {"zh", "新代码"}}},
        {"linked_ok", {{"en", "LINKED!"}, {"ar", "تم الربط!"}, {"fr", "LIÉ !"}, {"tr", "BAĞLANDI!"}, {"es", "¡VINCULADO!"}, {"ru", "ПРИВЯЗАНО!"}, {"pt", "VINCULADO!"}, {"zh", "已绑定！"}}},
        {"email", {{"en", "Email"}, {"ar", "البريد الإلكتروني"}, {"fr", "E-mail"}, {"tr", "E-posta"}, {"es", "Correo"}, {"ru", "Эл. почта"}, {"pt", "E-mail"}, {"zh", "邮箱"}}},
        {"password", {{"en", "Password"}, {"ar", "كلمة المرور"}, {"fr", "Mot de passe"}, {"tr", "Şifre"}, {"es", "Contraseña"}, {"ru", "Пароль"}, {"pt", "Senha"}, {"zh", "密码"}}},
        {"press_x_type", {{"en", "Press × to type"}, {"ar", "اضغط × للكتابة"}, {"fr", "Appuyez sur × pour saisir"}, {"tr", "Yazmak için × tuşuna basın"}, {"es", "Pulsa × para escribir"}, {"ru", "Нажмите ×, чтобы ввести"}, {"pt", "Pressione × para digitar"}, {"zh", "按 × 输入"}}},
        {"wrong_login", {{"en", "Wrong email or password. Try again."}, {"ar", "البريد أو كلمة المرور غير صحيحة. حاول مرة ثانية."}, {"fr", "E-mail ou mot de passe incorrect. Réessayez."}, {"tr", "E-posta veya şifre hatalı. Tekrar deneyin."}, {"es", "Correo o contraseña incorrectos. Inténtalo de nuevo."}, {"ru", "Неверная почта или пароль. Попробуйте снова."}, {"pt", "E-mail ou senha incorretos. Tente novamente."}, {"zh", "邮箱或密码错误，请重试。"}}},
        {"forgot_pw", {{"en", "Forgot your password? Reset it on your phone."}, {"ar", "نسيت كلمة المرور؟ أعد تعيينها من جوالك."}, {"fr", "Mot de passe oublié ? Réinitialisez-le sur votre téléphone."}, {"tr", "Şifrenizi mi unuttunuz? Telefonunuzdan sıfırlayın."}, {"es", "¿Olvidaste tu contraseña? Restablécela en tu teléfono."}, {"ru", "Забыли пароль? Сбросьте его на телефоне."}, {"pt", "Esqueceu a senha? Redefina no seu telefone."}, {"zh", "忘记密码？可在手机上重置。"}}},
        {"sale_winter", {{"en", "WINTER SALE"}, {"ar", "عروض الشتاء"}, {"fr", "SOLDES D'HIVER"}, {"tr", "KIŞ İNDİRİMİ"}, {"es", "REBAJAS DE INVIERNO"}, {"ru", "ЗИМНЯЯ РАСПРОДАЖА"}, {"pt", "PROMOÇÃO DE INVERNO"}, {"zh", "冬季特卖"}}},
        {"sale_spring", {{"en", "SPRING SALE"}, {"ar", "عروض الربيع"}, {"fr", "SOLDES DE PRINTEMPS"}, {"tr", "BAHAR İNDİRİMİ"}, {"es", "REBAJAS DE PRIMAVERA"}, {"ru", "ВЕСЕННЯЯ РАСПРОДАЖА"}, {"pt", "PROMOÇÃO DA PRIMAVERA"}, {"zh", "春季特卖"}}},
        {"sale_summer", {{"en", "SUMMER SALE"}, {"ar", "عروض الصيف"}, {"fr", "SOLDES D'ÉTÉ"}, {"tr", "YAZ İNDİRİMİ"}, {"es", "REBAJAS DE VERANO"}, {"ru", "ЛЕТНЯЯ РАСПРОДАЖА"}, {"pt", "PROMOÇÃO DE VERÃO"}, {"zh", "夏季特卖"}}},
        {"sale_autumn", {{"en", "AUTUMN SALE"}, {"ar", "عروض الخريف"}, {"fr", "SOLDES D'AUTOMNE"}, {"tr", "SONBAHAR İNDİRİMİ"}, {"es", "REBAJAS DE OTOÑO"}, {"ru", "ОСЕННЯЯ РАСПРОДАЖА"}, {"pt", "PROMOÇÃO DE OUTONO"}, {"zh", "秋季特卖"}}},
        {"latest_exclusive", {{"en", "LATEST EXCLUSIVES"}, {"ar", "أحدث الحصريات"}, {"fr", "DERNIERS EXCLUSIFS"}, {"tr", "EN YENİ ÖZELLER"}, {"es", "ÚLTIMOS EXCLUSIVOS"}, {"ru", "СВЕЖИЕ ЭКСКЛЮЗИВЫ"}, {"pt", "ÚLTIMOS EXCLUSIVOS"}, {"zh", "最新独家"}}},
        {"latest_exclusive_sub", {{"en", "Fresh addons, just released"}, {"ar", "إضافات جديدة، نزلت للتو"}, {"fr", "Nouveaux addons, tout juste sortis"}, {"tr", "Yeni çıkmış taze eklentiler"}, {"es", "Addons frescos, recién lanzados"}, {"ru", "Свежие аддоны, только выпущенные"}, {"pt", "Addons novos, recém-lançados"}, {"zh", "新鲜出炉的附加包"}}},
        {"just_released", {{"en", "Just released"}, {"ar", "نزلت للتو"}, {"fr", "Tout juste sorti"}, {"tr", "Yeni çıktı"}, {"es", "Recién lanzado"}, {"ru", "Только выпущено"}, {"pt", "Recém-lançado"}, {"zh", "刚刚发布"}}},
        {"view_more", {{"en", "View more"}, {"ar", "عرض المزيد"}, {"fr", "Voir plus"}, {"tr", "Daha fazla"}, {"es", "Ver más"}, {"ru", "Ещё"}, {"pt", "Ver mais"}, {"zh", "查看更多"}}},
        {"view_addon", {{"en", "View addon"}, {"ar", "عرض الإضافة"}, {"fr", "Voir l'addon"}, {"tr", "Eklentiyi gör"}, {"es", "Ver addon"}, {"ru", "Смотреть аддон"}, {"pt", "Ver addon"}, {"zh", "查看附加包"}}},
        {"view_newest", {{"en", "View the newest"}, {"ar", "شاهد الأحدث"}, {"fr", "Voir les nouveautés"}, {"tr", "En yenileri gör"}, {"es", "Ver los más nuevos"}, {"ru", "Смотреть новейшие"}, {"pt", "Ver os mais novos"}, {"zh", "查看最新"}}},
        {"rail_profile", {{"en", "Profile"}, {"ar", "حسابي"}, {"fr", "Profil"}, {"tr", "Profil"}, {"es", "Perfil"}, {"ru", "Профиль"}, {"pt", "Perfil"}, {"zh", "我的"}}},
        {"ends_in", {{"en", "Ends in"}, {"ar", "ينتهي خلال"}, {"fr", "Fin dans"}, {"tr", "Bitişe"}, {"es", "Termina en"}, {"ru", "До конца"}, {"pt", "Termina em"}, {"zh", "结束于"}}},
        {"sort_popular", {{"en", "Popular"}, {"ar", "الأكثر شعبية"}, {"fr", "Populaires"}, {"tr", "Popüler"}, {"es", "Populares"}, {"ru", "Популярные"}, {"pt", "Populares"}, {"zh", "热门"}}},
        {"screenshots", {{"en", "Screenshots"}, {"ar", "لقطات الشاشة"}, {"fr", "Captures"}, {"tr", "Ekran görüntüleri"}, {"es", "Capturas"}, {"ru", "Скриншоты"}, {"pt", "Capturas"}, {"zh", "截图"}}},
        {"f_size", {{"en", "Size"}, {"ar", "الحجم"}, {"fr", "Taille"}, {"tr", "Boyut"}, {"es", "Tamaño"}, {"ru", "Размер"}, {"pt", "Tamanho"}, {"zh", "大小"}}},
        {"f_players", {{"en", "Players"}, {"ar", "اللاعبون"}, {"fr", "Joueurs"}, {"tr", "Oyuncu"}, {"es", "Jugadores"}, {"ru", "Игроки"}, {"pt", "Jogadores"}, {"zh", "玩家"}}},
        {"f_updated", {{"en", "Updated"}, {"ar", "آخر تحديث"}, {"fr", "Mis à jour"}, {"tr", "Güncelleme"}, {"es", "Actualizado"}, {"ru", "Обновлено"}, {"pt", "Atualizado"}, {"zh", "更新时间"}}},
        {"f_rating", {{"en", "Rating"}, {"ar", "التصنيف"}, {"fr", "Classement"}, {"tr", "Derece"}, {"es", "Clasificación"}, {"ru", "Рейтинг"}, {"pt", "Classificação"}, {"zh", "评分"}}},
        {"rating_everyone", {{"en", "Everyone"}, {"ar", "للجميع"}, {"fr", "Tous"}, {"tr", "Herkes"}, {"es", "Todos"}, {"ru", "Для всех"}, {"pt", "Livre"}, {"zh", "全年龄"}}},
        {"autolaunch_hint", {{"en", "Minecraft opens automatically after install (if enabled)"}, {"ar", "يفتح ماينكرافت تلقائياً بعد التثبيت (إذا كان مفعّلاً)"},
                             {"fr", "Minecraft s'ouvre après l'installation (si activé)"}, {"es", "Minecraft se abre tras instalar (si está activado)"}, {"tr", "Kurulumdan sonra Minecraft otomatik açılır (açıksa)"}, {"ru", "Minecraft запускается автоматически после установки (если включено)"}, {"pt", "O Minecraft abre automaticamente após instalar (se ativado)"}, {"zh", "安装后自动启动 Minecraft（若已开启）"}}},
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


// ---- v5 strings (looked up before the v4 table) -------------------------------------------
const std::map<std::string, std::map<std::string, std::string>>& stringTable2() {
    static const std::map<std::string, std::map<std::string, std::string>> kT = {
        {"nav_plans", {{"en", "Plans"}, {"ar", "الخطط"}, {"fr", "Offres"}, {"tr", "Planlar"}, {"es", "Planes"}, {"ru", "Планы"}, {"pt", "Planos"}, {"zh", "套餐"}}},
        {"signin_title", {{"en", "Sign in"}, {"ar", "تسجيل الدخول"}, {"fr", "Connexion"}, {"tr", "Giriş yap"}, {"es", "Iniciar sesión"}, {"ru", "Вход"}, {"pt", "Entrar"}, {"zh", "登录"}}},
        {"signin_sub", {{"en", "Same account as your phone — your AC, plan and avatar come with it."},
                        {"ar", "نفس حسابك على الهاتف — رصيد AC واشتراكك وصورتك تنتقل معك."},
                        {"fr", "Le même compte que sur ton téléphone — tes AC, ton offre et ton avatar te suivent."},
                        {"tr", "Telefonundaki hesapla aynı — AC, plan ve avatarın seninle gelir."},
                        {"es", "La misma cuenta que en tu móvil: tus AC, tu plan y tu avatar vienen contigo."},
                        {"ru", "Тот же аккаунт, что и на телефоне: AC, подписка и аватар переносятся."},
                        {"pt", "A mesma conta do seu celular — seus AC, plano e avatar vêm junto."},
                        {"zh", "与手机相同的账户——AC、套餐和头像都会同步。"}}},
        {"step_1", {{"en", "Open AddonStation on your phone"}, {"ar", "افتح تطبيق AddonStation على هاتفك"}, {"fr", "Ouvre AddonStation sur ton téléphone"},
                    {"tr", "Telefonunda AddonStation'ı aç"}, {"es", "Abre AddonStation en tu móvil"}, {"ru", "Открой AddonStation на телефоне"},
                    {"pt", "Abra o AddonStation no celular"}, {"zh", "在手机上打开 AddonStation"}}},
        {"step_2", {{"en", "Settings  >  Link a console"}, {"ar", "الإعدادات  >  ربط جهاز"}, {"fr", "Paramètres  >  Lier une console"},
                    {"tr", "Ayarlar  >  Konsol bağla"}, {"es", "Ajustes  >  Vincular consola"}, {"ru", "Настройки  >  Привязать консоль"},
                    {"pt", "Ajustes  >  Vincular console"}, {"zh", "设置  >  绑定主机"}}},
        {"step_3", {{"en", "Enter this code"}, {"ar", "أدخل هذا الرمز"}, {"fr", "Saisis ce code"}, {"tr", "Bu kodu gir"},
                    {"es", "Introduce este código"}, {"ru", "Введи этот код"}, {"pt", "Digite este código"}, {"zh", "输入此代码"}}},
        {"code_expires", {{"en", "Expires in"}, {"ar", "ينتهي خلال"}, {"fr", "Expire dans"}, {"tr", "Süre"}, {"es", "Caduca en"},
                          {"ru", "Истекает через"}, {"pt", "Expira em"}, {"zh", "剩余"}}},
        {"loading", {{"en", "Loading\xE2\x80\xA6"}, {"ar", "جار\xD9\x90 \xD8\xA7\xD9\x84\xD8\xAA\xD8\xAD\xD9\x85\xD9\x8A\xD9\x84\xE2\x80\xA6"},
                    {"fr", "Chargement\xE2\x80\xA6"}, {"tr", "Y\xC3\xBCkleniyor\xE2\x80\xA6"},
                    {"es", "Cargando\xE2\x80\xA6"}, {"ru", "\xD0\x97\xD0\xB0\xD0\xB3\xD1\x80\xD1\x83\xD0\xB7\xD0\xBA\xD0\xB0\xE2\x80\xA6"},
                    {"pt", "Carregando\xE2\x80\xA6"}, {"zh", "\xE5\x8A\xA0\xE8\xBD\xBD\xE4\xB8\xAD\xE2\x80\xA6"}}},
        {"code_regen", {{"en", "Regenerate the code"}, {"ar", "عاود إنشاء الرمز"},
                       {"fr", "Régénérer le code"}, {"tr", "Kodu yeniden oluştur"},
                       {"es", "Regenerar el código"}, {"ru", "Создайте код заново"},
                       {"pt", "Regenerar o código"}, {"zh", "重新生成代码"}}},
        {"st_waiting", {{"en", "Waiting for your phone…"}, {"ar", "بانتظار تأكيد هاتفك…"}, {"fr", "En attente du téléphone…"},
                        {"tr", "Telefonun bekleniyor…"}, {"es", "Esperando a tu móvil…"}, {"ru", "Ждём подтверждения…"},
                        {"pt", "Aguardando o celular…"}, {"zh", "等待手机确认…"}}},
        {"st_linked", {{"en", "Linked"}, {"ar", "تم الربط"}, {"fr", "Associé"}, {"tr", "Bağlandı"}, {"es", "Vinculado"},
                       {"ru", "Привязано"}, {"pt", "Vinculado"}, {"zh", "已绑定"}}},
        {"st_expired", {{"en", "Code expired — get a new one"}, {"ar", "انتهى الرمز — اطلب رمزاً جديداً"}, {"fr", "Code expiré — demandes-en un nouveau"},
                        {"tr", "Kodun süresi doldu — yenisini al"}, {"es", "Código caducado — pide otro"}, {"ru", "Код истёк — получи новый"},
                        {"pt", "Código expirado — gere outro"}, {"zh", "代码已过期——请获取新代码"}}},
        {"st_error", {{"en", "No connection — try again"}, {"ar", "لا يوجد اتصال — حاول مجدداً"}, {"fr", "Pas de connexion — réessaie"},
                      {"tr", "Bağlantı yok — tekrar dene"}, {"es", "Sin conexión — inténtalo de nuevo"}, {"ru", "Нет соединения — попробуй снова"},
                      {"pt", "Sem conexão — tente de novo"}, {"zh", "无网络连接——请重试"}}},
        {"st_creating", {{"en", "Creating your code…"}, {"ar", "جارٍ إنشاء الرمز…"}, {"fr", "Création du code…"}, {"tr", "Kod oluşturuluyor…"},
                         {"es", "Creando tu código…"}, {"ru", "Создаём код…"}, {"pt", "Criando o código…"}, {"zh", "正在生成代码…"}}},
        {"no_app", {{"en", "Don't have the app? Get it now"}, {"ar", "ليس لديك التطبيق؟ حمّله الآن"}, {"fr", "Pas l'appli ? Télécharge-la"},
                    {"tr", "Uygulama yok mu? Hemen indir"}, {"es", "¿No tienes la app? Descárgala"}, {"ru", "Нет приложения? Скачай его"},
                    {"pt", "Não tem o app? Baixe agora"}, {"zh", "没有应用？立即下载"}}},
        {"get_code", {{"en", "Get a code"}, {"ar", "الحصول على رمز"}, {"fr", "Obtenir un code"}, {"tr", "Kod al"},
                      {"es", "Obtener un código"}, {"ru", "Получить код"}, {"pt", "Obter um código"}, {"zh", "获取代码"}}},
        {"qr_title", {{"en", "Scan with your phone camera"}, {"ar", "امسح الرمز بكاميرا هاتفك"}, {"fr", "Scanne avec l'appareil photo"},
                      {"tr", "Telefon kameranla tara"}, {"es", "Escanea con la cámara del móvil"}, {"ru", "Отсканируй камерой телефона"},
                      {"pt", "Escaneie com a câmera do celular"}, {"zh", "用手机相机扫描"}}},
        {"qr_hint", {{"en", "Install AddonStation and sign in, then press “Get a code”."},
                     {"ar", "حمّل تطبيق AddonStation وسجّل دخولك، ثم اضغط زر الحصول على رمز."},
                     {"fr", "Installe AddonStation et connecte-toi, puis appuie sur « Obtenir un code »."},
                     {"tr", "AddonStation'ı yükle ve giriş yap, sonra “Kod al”a bas."},
                     {"es", "Instala AddonStation e inicia sesión; luego pulsa «Obtener un código»."},
                     {"ru", "Установи AddonStation и войди, затем нажми «Получить код»."},
                     {"pt", "Instale o AddonStation e entre; depois toque em “Obter um código”."},
                     {"zh", "安装 AddonStation 并登录，然后按“获取代码”。"}}},
        {"qr_step_1", {{"en", "Scan the QR code with your phone"}, {"ar", "امسح رمز QR بهاتفك"}, {"fr", "Scanne le QR code avec ton téléphone"},
                       {"tr", "QR kodu telefonunla tara"}, {"es", "Escanea el código QR con tu móvil"}, {"ru", "Отсканируй QR-код телефоном"},
                       {"pt", "Escaneie o QR code com o celular"}, {"zh", "用手机扫描二维码"}}},
        {"qr_step_2", {{"en", "Install AddonStation and sign in"}, {"ar", "حمّل AddonStation وسجّل دخولك"}, {"fr", "Installe AddonStation et connecte-toi"},
                       {"tr", "AddonStation'ı yükle ve giriş yap"}, {"es", "Instala AddonStation e inicia sesión"}, {"ru", "Установи AddonStation и войди"},
                       {"pt", "Instale o AddonStation e entre"}, {"zh", "安装 AddonStation 并登录"}}},
        {"qr_step_3", {{"en", "Press “Get a code” to link this PS4"}, {"ar", "اضغط زر الحصول على رمز لربط جهازك"},
                       {"fr", "Appuie sur « Obtenir un code » pour lier la PS4"}, {"tr", "Bu PS4'ü bağlamak için “Kod al”a bas"},
                       {"es", "Pulsa «Obtener un código» para vincular la PS4"}, {"ru", "Нажми «Получить код», чтобы привязать PS4"},
                       {"pt", "Toque em “Obter um código” para vincular o PS4"}, {"zh", "按“获取代码”绑定这台 PS4"}}},
        {"continue_guest", {{"en", "Continue as guest"}, {"ar", "المتابعة كزائر"}, {"fr", "Continuer en invité"}, {"tr", "Misafir olarak devam et"},
                            {"es", "Seguir como invitado"}, {"ru", "Продолжить как гость"}, {"pt", "Continuar como visitante"}, {"zh", "以访客身份继续"}}},
        {"same_account", {{"en", "Comes with your account"}, {"ar", "ينتقل مع حسابك"}, {"fr", "Inclus avec ton compte"}, {"tr", "Hesabınla gelir"},
                          {"es", "Viene con tu cuenta"}, {"ru", "Переносится с аккаунтом"}, {"pt", "Vem com sua conta"}, {"zh", "随账户同步"}}},
        {"sa_ac", {{"en", "AC wallet"}, {"ar", "محفظة AC"}, {"fr", "Portefeuille AC"}, {"tr", "AC cüzdanı"}, {"es", "Monedero AC"},
                   {"ru", "Кошелёк AC"}, {"pt", "Carteira AC"}, {"zh", "AC 钱包"}}},
        {"sa_plan", {{"en", "Subscription"}, {"ar", "الاشتراك"}, {"fr", "Abonnement"}, {"tr", "Abonelik"}, {"es", "Suscripción"},
                     {"ru", "Подписка"}, {"pt", "Assinatura"}, {"zh", "订阅"}}},
        {"sa_avatar", {{"en", "Avatar & profile"}, {"ar", "الصورة والملف"}, {"fr", "Avatar et profil"}, {"tr", "Avatar ve profil"},
                       {"es", "Avatar y perfil"}, {"ru", "Аватар и профиль"}, {"pt", "Avatar e perfil"}, {"zh", "头像和资料"}}},
        {"welcome_name", {{"en", "Welcome, %s"}, {"ar", "أهلاً، %s"}, {"fr", "Bienvenue, %s"}, {"tr", "Hoş geldin, %s"}, {"es", "Bienvenido, %s"},
                          {"ru", "Привет, %s"}, {"pt", "Bem-vindo, %s"}, {"zh", "欢迎，%s"}}},
        {"req_title", {{"en", "Sign in to continue"}, {"ar", "سجّل الدخول للمتابعة"}, {"fr", "Connecte-toi pour continuer"}, {"tr", "Devam etmek için giriş yap"},
                       {"es", "Inicia sesión para continuar"}, {"ru", "Войди, чтобы продолжить"}, {"pt", "Entre para continuar"}, {"zh", "登录以继续"}}},
        {"req_sub", {{"en", "Addons and worlds need your account. Sign in with a code — it takes a few seconds."},
                     {"ar", "الإضافات والعوالم تحتاج حسابك. سجّل الدخول بالرمز — يأخذ ثواني."},
                     {"fr", "Les addons et mondes nécessitent ton compte. Connecte-toi avec un code, ça prend quelques secondes."},
                     {"tr", "Eklentiler ve dünyalar hesabını gerektirir. Kodla giriş yap — birkaç saniye sürer."},
                     {"es", "Los addons y mundos necesitan tu cuenta. Entra con un código: tarda unos segundos."},
                     {"ru", "Для аддонов и миров нужен аккаунт. Войди по коду — это пара секунд."},
                     {"pt", "Addons e mundos precisam da sua conta. Entre com um código — leva poucos segundos."},
                     {"zh", "模组和世界需要账户。用代码登录，只需几秒。"}}},
        {"signin_code_btn", {{"en", "Sign in with code"}, {"ar", "تسجيل الدخول بالرمز"}, {"fr", "Connexion par code"}, {"tr", "Kodla giriş yap"},
                             {"es", "Entrar con código"}, {"ru", "Войти по коду"}, {"pt", "Entrar com código"}, {"zh", "用代码登录"}}},
        {"not_now", {{"en", "Not now"}, {"ar", "ليس الآن"}, {"fr", "Plus tard"}, {"tr", "Şimdi değil"}, {"es", "Ahora no"},
                     {"ru", "Не сейчас"}, {"pt", "Agora não"}, {"zh", "以后再说"}}},
        {"home_empty_t", {{"en", "New addons are on the way"}, {"ar", "الإضافات الجديدة في الطريق"}, {"fr", "De nouveaux addons arrivent"},
                          {"tr", "Yeni eklentiler yolda"}, {"es", "Nuevos addons en camino"}, {"ru", "Новые аддоны уже в пути"},
                          {"pt", "Novos addons a caminho"}, {"zh", "新模组即将上线"}}},
        {"home_empty_s", {{"en", "Addons and worlds appear here the moment they are published."},
                          {"ar", "الإضافات والعوالم تظهر هنا أول ما تُنشر."},
                          {"fr", "Les addons et mondes apparaissent ici dès leur publication."},
                          {"tr", "Eklentiler ve dünyalar yayınlandığı anda burada görünür."},
                          {"es", "Los addons y mundos aparecen aquí en cuanto se publican."},
                          {"ru", "Аддоны и миры появляются здесь сразу после публикации."},
                          {"pt", "Addons e mundos aparecem aqui assim que são publicados."},
                          {"zh", "模组和世界一经发布就会出现在这里。"}}},
        {"empty_t", {{"en", "Nothing here yet"}, {"ar", "لا يوجد شيء هنا بعد"}, {"fr", "Rien ici pour l'instant"}, {"tr", "Burada henüz bir şey yok"},
                     {"es", "Aún no hay nada aquí"}, {"ru", "Здесь пока пусто"}, {"pt", "Nada aqui ainda"}, {"zh", "这里暂时为空"}}},
        {"empty_s", {{"en", "New items show up here as soon as they are published."}, {"ar", "العناصر الجديدة تظهر هنا فور نشرها."},
                     {"fr", "Les nouveautés s'affichent ici dès leur publication."}, {"tr", "Yeni içerikler yayınlanınca burada görünür."},
                     {"es", "Lo nuevo aparece aquí en cuanto se publica."}, {"ru", "Новинки появятся здесь сразу после публикации."},
                     {"pt", "Novidades aparecem aqui assim que publicadas."}, {"zh", "新内容发布后会立即显示在这里。"}}},
        {"deals_empty_t", {{"en", "No deals right now"}, {"ar", "لا توجد عروض حالياً"}, {"fr", "Aucune promo pour l'instant"}, {"tr", "Şu an indirim yok"},
                           {"es", "No hay ofertas ahora"}, {"ru", "Сейчас нет скидок"}, {"pt", "Sem ofertas no momento"}, {"zh", "暂无优惠"}}},
        {"deals_empty_s", {{"en", "Sales appear here automatically when they start."}, {"ar", "العروض تظهر هنا تلقائياً عند بدايتها."},
                           {"fr", "Les promos s'affichent ici automatiquement dès leur lancement."}, {"tr", "İndirimler başladığında burada otomatik görünür."},
                           {"es", "Las ofertas aparecen aquí automáticamente al empezar."}, {"ru", "Скидки появятся здесь автоматически."},
                           {"pt", "Ofertas aparecem aqui automaticamente quando começam."}, {"zh", "优惠开始时会自动显示在这里。"}}},
        {"nav_deals_t", {{"en", "Deals"}, {"ar", "العروض"}, {"fr", "Promos"}, {"tr", "Fırsatlar"}, {"es", "Ofertas"}, {"ru", "Скидки"}, {"pt", "Ofertas"}, {"zh", "优惠"}}},
        {"most_dl", {{"en", "Most downloaded"}, {"ar", "الأكثر تنزيلاً"}, {"fr", "Les plus téléchargés"}, {"tr", "En çok indirilenler"},
                     {"es", "Más descargados"}, {"ru", "Самые популярные"}, {"pt", "Mais baixados"}, {"zh", "下载最多"}}},
        {"n_results", {{"en", "Results: %d"}, {"ar", "%d نتيجة"}, {"fr", "Résultats : %d"}, {"tr", "%d sonuç"}, {"es", "Resultados: %d"},
                       {"ru", "Найдено: %d"}, {"pt", "Resultados: %d"}, {"zh", "%d 个结果"}}},
        {"no_results_q", {{"en", "No results for \"%s\""}, {"ar", "لا نتائج لـ \"%s\""}, {"fr", "Aucun résultat pour « %s »"}, {"tr", "\"%s\" için sonuç yok"},
                          {"es", "Sin resultados para \"%s\""}, {"ru", "Нет результатов: «%s»"}, {"pt", "Nada encontrado para \"%s\""}, {"zh", "没有“%s”的结果"}}},
        {"search_type", {{"en", "Press ✕ to type a name, creator or category"}, {"ar", "اضغط ✕ لكتابة اسم أو صانع أو تصنيف"},
                         {"fr", "Appuie sur ✕ pour saisir un nom, un créateur ou une catégorie"}, {"tr", "Ad, yapımcı veya kategori yazmak için ✕"},
                         {"es", "Pulsa ✕ para escribir un nombre, creador o categoría"}, {"ru", "Нажми ✕, чтобы ввести название, автора или категорию"},
                         {"pt", "Aperte ✕ para digitar nome, criador ou categoria"}, {"zh", "按 ✕ 输入名称、作者或分类"}}},
        {"view_plans", {{"en", "View plans"}, {"ar", "عرض الخطط"}, {"fr", "Voir les offres"}, {"tr", "Planları gör"}, {"es", "Ver planes"},
                        {"ru", "Планы"}, {"pt", "Ver planos"}, {"zh", "查看套餐"}}},
        {"badge_new", {{"en", "NEW"}, {"ar", "جديد"}, {"fr", "NOUVEAU"}, {"tr", "YENİ"}, {"es", "NUEVO"}, {"ru", "НОВОЕ"}, {"pt", "NOVO"}, {"zh", "新"}}},
        {"card_dl", {{"en", "Download"}, {"ar", "نزّل"}, {"fr", "Télécharger"}, {"tr", "İndir"}, {"es", "Descargar"}, {"ru", "Скачать"}, {"pt", "Baixar"}, {"zh", "下载"}}},
        {"card_soon", {{"en", "Soon"}, {"ar", "قريباً"}, {"fr", "Bientôt"}, {"tr", "Yakında"}, {"es", "Próximamente"}, {"ru", "Скоро"}, {"pt", "Em breve"}, {"zh", "即将"}}},
        {"newest_k", {{"en", "Just released"}, {"ar", "نزلت للتو"}, {"fr", "Tout juste sorti"}, {"tr", "Yeni çıktı"}, {"es", "Recién salido"},
                      {"ru", "Только что вышло"}, {"pt", "Acabou de sair"}, {"zh", "刚刚发布"}}},
        {"open_cta", {{"en", "Open"}, {"ar", "فتح"}, {"fr", "Ouvrir"}, {"tr", "Aç"}, {"es", "Abrir"}, {"ru", "Открыть"}, {"pt", "Abrir"}, {"zh", "打开"}}},
        {"signin_dl", {{"en", "Sign in to download"}, {"ar", "سجّل الدخول للتنزيل"}, {"fr", "Connecte-toi pour télécharger"}, {"tr", "İndirmek için giriş yap"},
                       {"es", "Inicia sesión para descargar"}, {"ru", "Войди, чтобы скачать"}, {"pt", "Entre para baixar"}, {"zh", "登录后下载"}}},
        {"get_tier", {{"en", "Get %s on your phone"}, {"ar", "اشترك في %s من هاتفك"}, {"fr", "Prends %s sur ton téléphone"}, {"tr", "%s'i telefonundan al"},
                      {"es", "Consigue %s en tu móvil"}, {"ru", "Оформи %s на телефоне"}, {"pt", "Assine %s no celular"}, {"zh", "在手机上开通 %s"}}},
        {"unlock_for", {{"en", "Unlock for %s AC"}, {"ar", "افتح مقابل %s AC"}, {"fr", "Débloquer pour %s AC"}, {"tr", "%s AC ile aç"},
                        {"es", "Desbloquear por %s AC"}, {"ru", "Открыть за %s AC"}, {"pt", "Desbloquear por %s AC"}, {"zh", "用 %s AC 解锁"}}},
        {"acc_free", {{"en", "Free with your account"}, {"ar", "مجانية مع حسابك"}, {"fr", "Gratuit avec ton compte"}, {"tr", "Hesabınla ücretsiz"},
                      {"es", "Gratis con tu cuenta"}, {"ru", "Бесплатно с аккаунтом"}, {"pt", "Grátis com sua conta"}, {"zh", "登录即可免费获取"}}},
        {"acc_included", {{"en", "Included in your plan"}, {"ar", "ضمن اشتراكك"}, {"fr", "Inclus dans ton offre"}, {"tr", "Planına dahil"},
                          {"es", "Incluido en tu plan"}, {"ru", "Входит в подписку"}, {"pt", "Incluído no seu plano"}, {"zh", "已包含在你的套餐中"}}},
        {"acc_unlocked", {{"en", "Unlocked with AC"}, {"ar", "مفتوحة بـ AC"}, {"fr", "Débloqué avec des AC"}, {"tr", "AC ile açıldı"},
                          {"es", "Desbloqueado con AC"}, {"ru", "Открыто за AC"}, {"pt", "Desbloqueado com AC"}, {"zh", "已用 AC 解锁"}}},
        {"acc_need", {{"en", "Requires %s or higher"}, {"ar", "تتطلب %s أو أعلى"}, {"fr", "Nécessite %s ou plus"}, {"tr", "%s veya üstü gerekir"},
                      {"es", "Requiere %s o superior"}, {"ru", "Нужна подписка %s или выше"}, {"pt", "Requer %s ou superior"}, {"zh", "需要 %s 或更高套餐"}}},
        {"acc_need_s", {{"en", "Requires %s"}, {"ar", "تتطلب %s"}, {"fr", "Nécessite %s"}, {"tr", "%s gerekir"}, {"es", "Requiere %s"},
                        {"ru", "Нужна подписка %s"}, {"pt", "Requer %s"}, {"zh", "需要 %s"}}},
        {"acc_guest", {{"en", "Sign in to get it — same account as your phone"}, {"ar", "سجّل الدخول للحصول عليها — نفس حساب هاتفك"},
                       {"fr", "Connecte-toi pour l'obtenir — même compte que ton téléphone"}, {"tr", "Almak için giriş yap — telefonundaki hesap"},
                       {"es", "Inicia sesión para obtenerlo — la misma cuenta del móvil"}, {"ru", "Войди, чтобы получить — тот же аккаунт, что на телефоне"},
                       {"pt", "Entre para obter — a mesma conta do celular"}, {"zh", "登录即可获取——与手机同一账户"}}},
        {"bal_low", {{"en", "Your balance: %s AC — top up in the phone app"}, {"ar", "رصيدك: %s AC — اشحن من تطبيق الهاتف"},
                     {"fr", "Ton solde : %s AC — recharge dans l'appli mobile"}, {"tr", "Bakiyen: %s AC — telefondan yükle"},
                     {"es", "Tu saldo: %s AC — recarga en la app del móvil"}, {"ru", "Баланс: %s AC — пополни в приложении"},
                     {"pt", "Seu saldo: %s AC — recarregue no app"}, {"zh", "余额：%s AC——请在手机应用中充值"}}},
        {"dl_used", {{"en", "Downloads used: %d of %d"}, {"ar", "التنزيلات المستخدمة: %d من %d"}, {"fr", "Téléchargements : %d sur %d"},
                     {"tr", "Kullanılan indirme: %d / %d"}, {"es", "Descargas usadas: %d de %d"}, {"ru", "Скачано: %d из %d"},
                     {"pt", "Downloads usados: %d de %d"}, {"zh", "已用下载：%d / %d"}}},
        {"f_version", {{"en", "Version"}, {"ar", "الإصدار"}, {"fr", "Version"}, {"tr", "Sürüm"}, {"es", "Versión"}, {"ru", "Версия"}, {"pt", "Versão"}, {"zh", "版本"}}},
        {"f_downloads", {{"en", "Downloads"}, {"ar", "التنزيلات"}, {"fr", "Téléchargements"}, {"tr", "İndirme"}, {"es", "Descargas"}, {"ru", "Загрузки"}, {"pt", "Downloads"}, {"zh", "下载量"}}},
        {"f_rating2", {{"en", "Rating"}, {"ar", "التقييم"}, {"fr", "Note"}, {"tr", "Puan"}, {"es", "Valoración"}, {"ru", "Оценка"}, {"pt", "Avaliação"}, {"zh", "评分"}}},
        {"f_published", {{"en", "Published"}, {"ar", "تاريخ النشر"}, {"fr", "Publié"}, {"tr", "Yayın"}, {"es", "Publicado"}, {"ru", "Опубликовано"}, {"pt", "Publicado"}, {"zh", "发布日期"}}},
        {"type_addon", {{"en", "Addon"}, {"ar", "إضافة"}, {"fr", "Addon"}, {"tr", "Eklenti"}, {"es", "Addon"}, {"ru", "Аддон"}, {"pt", "Addon"}, {"zh", "模组"}}},
        {"type_world", {{"en", "World"}, {"ar", "عالم"}, {"fr", "Monde"}, {"tr", "Dünya"}, {"es", "Mundo"}, {"ru", "Мир"}, {"pt", "Mundo"}, {"zh", "世界"}}},
        {"cover_k", {{"en", "Cover"}, {"ar", "الغلاف"}, {"fr", "Couverture"}, {"tr", "Kapak"}, {"es", "Portada"}, {"ru", "Обложка"}, {"pt", "Capa"}, {"zh", "封面"}}},
        {"guest_t", {{"en", "You're browsing as a guest"}, {"ar", "أنت تتصفح كزائر"}, {"fr", "Tu navigues en invité"}, {"tr", "Misafir olarak geziyorsun"},
                     {"es", "Estás navegando como invitado"}, {"ru", "Ты в гостевом режиме"}, {"pt", "Você está como visitante"}, {"zh", "你正在以访客身份浏览"}}},
        {"guest_s", {{"en", "Sign in with a code to see your AC balance, subscription and downloads."},
                     {"ar", "سجّل الدخول بالرمز لتظهر محفظة AC واشتراكك وتنزيلاتك."},
                     {"fr", "Connecte-toi avec un code pour voir ton solde AC, ton abonnement et tes téléchargements."},
                     {"tr", "AC bakiyeni, aboneliğini ve indirmelerini görmek için kodla giriş yap."},
                     {"es", "Entra con un código para ver tu saldo AC, tu suscripción y tus descargas."},
                     {"ru", "Войди по коду, чтобы видеть баланс AC, подписку и загрузки."},
                     {"pt", "Entre com um código para ver seu saldo AC, assinatura e downloads."},
                     {"zh", "用代码登录以查看 AC 余额、订阅和下载。"}}},
        {"ac_wallet", {{"en", "AC wallet"}, {"ar", "محفظة AC"}, {"fr", "Portefeuille AC"}, {"tr", "AC cüzdanı"}, {"es", "Monedero AC"}, {"ru", "Кошелёк AC"}, {"pt", "Carteira AC"}, {"zh", "AC 钱包"}}},
        {"topup_phone", {{"en", "Top up and spend AC from the phone app. AC can unlock any addon instead of a subscription."},
                         {"ar", "اشحن واستخدم AC من تطبيق الهاتف. AC يفتح أي إضافة بدل الاشتراك."},
                         {"fr", "Recharge tes AC dans l'appli mobile. Les AC débloquent n'importe quel addon sans abonnement."},
                         {"tr", "AC'yi telefondan yükle. AC, abonelik yerine herhangi bir eklentiyi açar."},
                         {"es", "Recarga AC en la app del móvil. Con AC desbloqueas cualquier addon sin suscripción."},
                         {"ru", "Пополняй AC в приложении. AC открывает любой аддон без подписки."},
                         {"pt", "Recarregue AC no app. AC desbloqueia qualquer addon sem assinatura."},
                         {"zh", "在手机应用中充值 AC。AC 可代替订阅解锁任何模组。"}}},
        {"no_tx", {{"en", "No transactions yet"}, {"ar", "لا توجد عمليات بعد"}, {"fr", "Aucune transaction"}, {"tr", "Henüz işlem yok"},
                   {"es", "Aún no hay movimientos"}, {"ru", "Операций пока нет"}, {"pt", "Nenhuma transação ainda"}, {"zh", "暂无交易"}}},
        {"plan_until", {{"en", "until %s"}, {"ar", "حتى %s"}, {"fr", "jusqu'au %s"}, {"tr", "%s tarihine kadar"}, {"es", "hasta %s"}, {"ru", "до %s"}, {"pt", "até %s"}, {"zh", "至 %s"}}},
        {"pf_dl", {{"en", "%d downloads / %s"}, {"ar", "%d تنزيلات / %s"}, {"fr", "%d téléchargements / %s"}, {"tr", "%d indirme / %s"},
                   {"es", "%d descargas / %s"}, {"ru", "%d загрузок / %s"}, {"pt", "%d downloads / %s"}, {"zh", "每%s %d 次下载"}}},
        {"pf_unl", {{"en", "Unlimited downloads"}, {"ar", "تنزيلات بلا حدود"}, {"fr", "Téléchargements illimités"}, {"tr", "Sınırsız indirme"},
                    {"es", "Descargas ilimitadas"}, {"ru", "Без лимита загрузок"}, {"pt", "Downloads ilimitados"}, {"zh", "无限下载"}}},
        {"pf_free_c", {{"en", "All free addons & worlds"}, {"ar", "كل الإضافات والعوالم المجانية"}, {"fr", "Tous les addons et mondes gratuits"},
                       {"tr", "Tüm ücretsiz eklenti ve dünyalar"}, {"es", "Todos los addons y mundos gratis"}, {"ru", "Все бесплатные аддоны и миры"},
                       {"pt", "Todos os addons e mundos grátis"}, {"zh", "所有免费模组和世界"}}},
        {"pf_all_free", {{"en", "Everything in Free"}, {"ar", "كل ما في المجانية"}, {"fr", "Tout le contenu Gratuit"}, {"tr", "Ücretsiz'deki her şey"},
                         {"es", "Todo lo de Gratis"}, {"ru", "Всё из Free"}, {"pt", "Tudo do Grátis"}, {"zh", "包含免费版全部内容"}}},
        {"pf_all_epic", {{"en", "Everything in Epic"}, {"ar", "كل ما في Epic"}, {"fr", "Tout le contenu Epic"}, {"tr", "Epic'teki her şey"},
                         {"es", "Todo lo de Epic"}, {"ru", "Всё из Epic"}, {"pt", "Tudo do Epic"}, {"zh", "包含 Epic 全部内容"}}},
        {"pf_epic_c", {{"en", "Epic addons & worlds"}, {"ar", "إضافات وعوالم Epic"}, {"fr", "Addons et mondes Epic"}, {"tr", "Epic eklenti ve dünyalar"},
                       {"es", "Addons y mundos Epic"}, {"ru", "Аддоны и миры Epic"}, {"pt", "Addons e mundos Epic"}, {"zh", "Epic 模组和世界"}}},
        {"pf_leg_c", {{"en", "Legendary addons & worlds"}, {"ar", "إضافات وعوالم Legendary"}, {"fr", "Addons et mondes Legendary"}, {"tr", "Legendary eklenti ve dünyalar"},
                      {"es", "Addons y mundos Legendary"}, {"ru", "Аддоны и миры Legendary"}, {"pt", "Addons e mundos Legendary"}, {"zh", "Legendary 模组和世界"}}},
        {"pf_no_ac", {{"en", "Never pay AC to unlock"}, {"ar", "لا تحتاج AC للفتح أبداً"}, {"fr", "Jamais besoin d'AC pour débloquer"}, {"tr", "Açmak için asla AC ödemezsin"},
                      {"es", "Nunca pagas AC para desbloquear"}, {"ru", "Никогда не платишь AC за открытие"}, {"pt", "Nunca paga AC para desbloquear"}, {"zh", "解锁无需 AC"}}},
        {"per_day", {{"en", "day"}, {"ar", "يوم"}, {"fr", "jour"}, {"tr", "gün"}, {"es", "día"}, {"ru", "день"}, {"pt", "dia"}, {"zh", "天"}}},
        {"per_week", {{"en", "week"}, {"ar", "أسبوع"}, {"fr", "semaine"}, {"tr", "hafta"}, {"es", "semana"}, {"ru", "неделю"}, {"pt", "semana"}, {"zh", "周"}}},
        {"per_month", {{"en", "month"}, {"ar", "شهر"}, {"fr", "mois"}, {"tr", "ay"}, {"es", "mes"}, {"ru", "месяц"}, {"pt", "mês"}, {"zh", "月"}}},
        {"settings_appearance", {{"en", "Appearance"}, {"ar", "المظهر"}, {"fr", "Apparence"}, {"tr", "Görünüm"}, {"es", "Apariencia"}, {"ru", "Оформление"}, {"pt", "Aparência"}, {"zh", "外观"}}},
        {"theme_dark", {{"en", "Dark"}, {"ar", "داكن"}, {"fr", "Sombre"}, {"tr", "Koyu"}, {"es", "Oscuro"}, {"ru", "Тёмная"}, {"pt", "Escuro"}, {"zh", "深色"}}},
        {"theme_light", {{"en", "Light"}, {"ar", "فاتح"}, {"fr", "Clair"}, {"tr", "Açık"}, {"es", "Claro"}, {"ru", "Светлая"}, {"pt", "Claro"}, {"zh", "浅色"}}},
        {"appearance_note", {{"en", "Applies instantly and is saved on this console."}, {"ar", "يتغيّر فوراً ويُحفظ على هذا الجهاز."},
                             {"fr", "S'applique tout de suite et reste enregistré sur cette console."}, {"tr", "Anında uygulanır ve bu konsolda kaydedilir."},
                             {"es", "Se aplica al instante y se guarda en esta consola."}, {"ru", "Применяется сразу и сохраняется на консоли."},
                             {"pt", "Aplica na hora e fica salvo neste console."}, {"zh", "立即生效并保存在此主机上。"}}},
        {"lang_auto", {{"en", "Set automatically from your PS4 language. You stay here after choosing."},
                       {"ar", "تُضبط تلقائياً من لغة جهازك PS4. تبقى هنا بعد الاختيار."},
                       {"fr", "Réglée automatiquement selon la langue de ta PS4. Tu restes ici après le choix."},
                       {"tr", "PS4 dilinden otomatik ayarlanır. Seçtikten sonra burada kalırsın."},
                       {"es", "Se ajusta según el idioma de tu PS4. Te quedas aquí al elegir."},
                       {"ru", "Определяется по языку PS4. После выбора ты остаёшься здесь."},
                       {"pt", "Definido pelo idioma do seu PS4. Você continua aqui após escolher."},
                       {"zh", "根据 PS4 系统语言自动设置。选择后仍停留在此页。"}}},
        {"acct_guest_v", {{"en", "Guest"}, {"ar", "زائر"}, {"fr", "Invité"}, {"tr", "Misafir"}, {"es", "Invitado"}, {"ru", "Гость"}, {"pt", "Visitante"}, {"zh", "访客"}}},
        {"err_sign_in_required", {{"en", "Sign in first"}, {"ar", "سجّل الدخول أولاً"}, {"fr", "Connecte-toi d'abord"}, {"tr", "Önce giriş yap"},
                                  {"es", "Inicia sesión primero"}, {"ru", "Сначала войди"}, {"pt", "Entre primeiro"}, {"zh", "请先登录"}}},
        {"err_download_limit", {{"en", "Download limit reached for this period"}, {"ar", "وصلت حد التنزيل لهذه الفترة"}, {"fr", "Limite de téléchargements atteinte"},
                                {"tr", "Bu dönemin indirme sınırına ulaştın"}, {"es", "Límite de descargas alcanzado"}, {"ru", "Достигнут лимит загрузок"},
                                {"pt", "Limite de downloads atingido"}, {"zh", "本期下载次数已用完"}}},
        {"err_no_permission", {{"en", "Your plan doesn't include this item"}, {"ar", "اشتراكك لا يشمل هذا العنصر"}, {"fr", "Ton offre n'inclut pas cet élément"},
                               {"tr", "Planın bu içeriği kapsamıyor"}, {"es", "Tu plan no incluye este elemento"}, {"ru", "Подписка не включает это"},
                               {"pt", "Seu plano não inclui este item"}, {"zh", "你的套餐不包含此内容"}}},
        {"err_file_not_uploaded", {{"en", "File not available yet — try later"}, {"ar", "الملف غير متاح بعد — حاول لاحقاً"}, {"fr", "Fichier pas encore disponible"},
                                   {"tr", "Dosya henüz hazır değil"}, {"es", "Archivo aún no disponible"}, {"ru", "Файл пока недоступен"},
                                   {"pt", "Arquivo ainda indisponível"}, {"zh", "文件暂不可用，请稍后再试"}}},
        {"err_not_enough_ac", {{"en", "Not enough AC"}, {"ar", "رصيد AC غير كافٍ"}, {"fr", "Pas assez d'AC"}, {"tr", "Yeterli AC yok"},
                               {"es", "AC insuficientes"}, {"ru", "Недостаточно AC"}, {"pt", "AC insuficiente"}, {"zh", "AC 不足"}}},
        {"ok_unlocked", {{"en", "Unlocked — enjoy!"}, {"ar", "تم الفتح — استمتع!"}, {"fr", "Débloqué — amuse-toi !"}, {"tr", "Açıldı — iyi eğlenceler!"},
                         {"es", "¡Desbloqueado, disfruta!"}, {"ru", "Открыто — наслаждайся!"}, {"pt", "Desbloqueado — divirta-se!"}, {"zh", "已解锁，尽情享受！"}}},
        {"all_items", {{"en", "All"}, {"ar", "الكل"}, {"fr", "Tout"}, {"tr", "Tümü"}, {"es", "Todo"}, {"ru", "Все"}, {"pt", "Tudo"}, {"zh", "全部"}}},
        {"continue_k", {{"en", "Continue"}, {"ar", "متابعة"}, {"fr", "Continuer"}, {"tr", "Devam et"}, {"es", "Continuar"}, {"ru", "Продолжить"}, {"pt", "Continuar"}, {"zh", "继续"}}},
        {"deals_soon", {{"en", "Sale items appear here"}, {"ar", "عناصر التخفيض تظهر هنا"}, {"fr", "Les articles en promo apparaissent ici"}, {"tr", "İndirimli öğeler burada görünür"}, {"es", "Los artículos en oferta aparecen aquí"}, {"ru", "Товары со скидкой появятся здесь"}, {"pt", "Itens em promoção aparecem aqui"}, {"zh", "促销内容会显示在这里"}}},
        {"deals_soon_sub", {{"en", "Items join the sale as soon as a sale price is set for them."}, {"ar", "العناصر تنضم للتخفيض أول ما ينحط لها سعر تخفيض."}, {"fr", "Les articles rejoignent la promo dès qu'un prix promo est fixé."}, {"tr", "İndirim fiyatı belirlenen öğeler hemen eklenir."}, {"es", "Se añaden en cuanto tienen precio de oferta."}, {"ru", "Товары появятся, как только для них установят цену со скидкой."}, {"pt", "Entram assim que recebem preço promocional."}, {"zh", "设置促销价后会立即加入。"}}},
        {"rights_k", {{"en", "COPYRIGHT"}, {"ar", "الحقوق"}, {"fr", "DROITS"}, {"tr", "TELİF HAKKI"}, {"es", "DERECHOS"}, {"ru", "ПРАВА"}, {"pt", "DIREITOS"}, {"zh", "版权"}}},
        {"rights_line", {{"en", "\xC2\xA9 2026 SilverFoxInteractive. All rights reserved."},
                         {"ar", "جميع الحقوق محفوظة للثعلب الفضي \xC2\xA9 2026"},
                         {"fr", "\xC2\xA9 2026 SilverFoxInteractive. Tous droits réservés."},
                         {"tr", "\xC2\xA9 2026 SilverFoxInteractive. Tüm hakları saklıdır."},
                         {"es", "\xC2\xA9 2026 SilverFoxInteractive. Todos los derechos reservados."},
                         {"ru", "\xC2\xA9 2026 SilverFoxInteractive. Все права защищены."},
                         {"pt", "\xC2\xA9 2026 SilverFoxInteractive. Todos os direitos reservados."},
                         {"zh", "\xC2\xA9 2026 SilverFoxInteractive 版权所有"}}},
        {"sort_by", {{"en", "Sort"}, {"ar", "الترتيب"}, {"fr", "Trier"}, {"tr", "Sırala"}, {"es", "Ordenar"}, {"ru", "Сортировка"}, {"pt", "Ordenar"}, {"zh", "排序"}}},
        {"sort_popular", {{"en", "Popular"}, {"ar", "الأكثر تحميلاً"}, {"fr", "Populaires"}, {"tr", "Popüler"}, {"es", "Populares"}, {"ru", "Популярные"}, {"pt", "Populares"}, {"zh", "热门"}}},
        {"sort_newest", {{"en", "Newest"}, {"ar", "الأحدث"}, {"fr", "Récents"}, {"tr", "En yeni"}, {"es", "Recientes"}, {"ru", "Новые"}, {"pt", "Recentes"}, {"zh", "最新"}}},
        {"sort_rating", {{"en", "Top rated"}, {"ar", "الأعلى تقييماً"}, {"fr", "Mieux notés"}, {"tr", "En beğenilen"}, {"es", "Mejor valorados"}, {"ru", "Лучшие"}, {"pt", "Mais bem avaliados"}, {"zh", "评分最高"}}},
        {"items_n", {{"en", "Items: %d"}, {"ar", "%d عنصر"}, {"fr", "Éléments : %d"}, {"tr", "%d öğe"}, {"es", "Elementos: %d"}, {"ru", "Элементов: %d"}, {"pt", "Itens: %d"}, {"zh", "%d 项"}}},
        {"sale_k", {{"en", "Limited time"}, {"ar", "لفترة محدودة"}, {"fr", "Durée limitée"}, {"tr", "Sınırlı süre"}, {"es", "Tiempo limitado"}, {"ru", "Ограничено"}, {"pt", "Por tempo limitado"}, {"zh", "限时"}}},
        {"off_pct", {{"en", "-%d%%"}, {"ar", "-%d%%"}, {"fr", "-%d%%"}, {"tr", "-%d%%"}, {"es", "-%d%%"}, {"ru", "-%d%%"}, {"pt", "-%d%%"}, {"zh", "-%d%%"}}},
    };
    return kT;
}

} // namespace

std::string tr(const std::string& key, const std::string& langCode) {
    {
        const auto& t2 = stringTable2();
        auto i2 = t2.find(key);
        if (i2 != t2.end()) {
            auto l2 = i2->second.find(langCode);
            if (l2 != i2->second.end()) return l2->second;
            auto e2 = i2->second.find("en");
            if (e2 != i2->second.end()) return e2->second;
        }
    }
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

// ---- v5 runtime palette: dark (default) or light — switched by setThemeLight() ----------------
// Text / surfaces use these variables. kWHT stays pure white for text ON artwork and on accent fills.
bool gLight = false;
SDL_Color kBG{8, 19, 26, 255};       // theme-b cyan (was 11,13,16)
SDL_Color kSF{18, 34, 46, 255};      // theme-b (was 21,24,29)
SDL_Color kBD{255, 255, 255, 20};
SDL_Color kTX{243, 245, 247, 255};
SDL_Color kMU{134, 169, 181, 255}; // theme-b (was 152,161,172)
SDL_Color kGD{255, 201, 64, 255};
SDL_Color kFOCUS{255, 255, 255, 255};
SDL_Color kINKRGB{255, 255, 255, 255};   // translucent overlays: white on dark, near-black on light
constexpr SDL_Color kBL{74, 168, 255, 255};
constexpr SDL_Color kBL2{0, 112, 209, 255};
SDL_Color kBLl{74, 168, 255, 255};
constexpr SDL_Color kGRN{74, 168, 255, 255};
SDL_Color kGRN2{150, 205, 255, 255};
constexpr SDL_Color kRED{255, 90, 99, 255};
constexpr SDL_Color kWHT{255, 255, 255, 255};
SDL_Color ink(Uint8 a) { return SDL_Color{kINKRGB.r, kINKRGB.g, kINKRGB.b, a}; }
SDL_Color shade(Uint8 a) { return SDL_Color{kBG.r, kBG.g, kBG.b, a}; }

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

// v0.30: ANIMATED overlay recording. The UI redraws fully only when state changes; between
// states the main loop restores the animated rectangles from a frame snapshot and replays just
// these calls (focus ring/gleam, gem, settings icon tiles) ~30 times a second.
struct AnimCall {
    enum Kind : std::uint8_t { Ring, Mark, Tile } kind;
    int a0 = 0, a1 = 0, a2 = 0, a3 = 0, a4 = 0, a5 = 0;
    SDL_Color c{0, 0, 0, 255};
};
std::vector<AnimCall> gAnimCalls;
std::vector<int> gAnimRects;      // x, y, w, h quads (draw space, already clipped later)
bool gAnimRec = true;
bool gAnimBlock = false;   // v0.38: page animations must NOT replay over a modal (wrapper at pkgtest scope)
void animPush(const AnimCall& call, int x, int y, int w, int h) {
    if (!gAnimRec || gAnimBlock) return;
    gAnimCalls.push_back(call);
    gAnimRects.push_back(x); gAnimRects.push_back(y);
    gAnimRects.push_back(w); gAnimRects.push_back(h);
}

// v0.34 — POLISHED GOLD RIM + one quiet gleam (لمعة).
// User request (2026-10-28): remove the circling comet (الشهاب), the light sweep (الضوء)
// and every glow / aura / ripple (التوهج). What remains is ONE clean, beautiful frame
// with a single soft shine that catches the light when focus lands, then rests.
// v0.35 (2026-10-29): the gleam lives on ACTION BUTTONS only (download CTAs, the sign-in
// dialog, …). Rows / lists / catalog cards / settings tiles get the gold frame ONLY.
void focusRing(UiEngine& e, int x, int y, int w, int h, int r, bool gleam = true) {
    animPush({AnimCall::Ring, x, y, w, h, r, gleam ? 1 : 0}, x - 10, y - 10, w + 20, h + 20);

    // ---- polished gold (theme-aware): bright rim + deeper-gold inner line = metal bevel
    const SDL_Color goldHi = gLight ? SDL_Color{184, 128, 8, 255} : SDL_Color{255, 214, 90, 255};
    const SDL_Color goldLo = gLight ? SDL_Color{122, 84, 6, 255} : SDL_Color{184, 128, 8, 255};

    // 1) the frame itself: one clean 3 px AA border + a 1 px deeper line inside (bevel edge)
    const int rr = std::max(2, std::min(r, std::min(w, h) / 2));
    e.drawRoundBorder(x, y, w, h, rr, 3, goldHi);
    e.drawRoundBorder(x + 3, y + 3, w - 6, h - 6, std::max(1, rr - 3), 1, goldLo);

    // 2) THE gleam (buttons only): a soft white-gold light that catches the rim on arrival
    if (gleam) {
        static bool sInit = false;
        static int sLX = 0, sLY = 0, sLW = 0, sLH = 0;
        static Uint32 sGleamAt = 0;
        if (!sInit || x != sLX || y != sLY || w != sLW || h != sLH) {
            sInit = true; sLX = x; sLY = y; sLW = w; sLH = h;
            sGleamAt = SDL_GetTicks();
        }
        float g = static_cast<float>(SDL_GetTicks() - sGleamAt) / 420.0f;
        g = g < 0.0f ? 0.0f : (g > 1.0f ? 1.0f : g);
        g = 1.0f - g;
        g = g * g * (3.0f - 2.0f * g);          // 1 on arrival -> 0 at rest
        if (g > 0.01f) {
            const Uint8 a = static_cast<Uint8>(80.0f * g);
            e.drawRoundBorder(x - 1, y - 1, w + 2, h + 2, rr + 1, 4, SDL_Color{255, 246, 214, a});
        }
    }
}

[[maybe_unused]] void iconPhone(UiEngine& e, int x, int y, int s, SDL_Color c) {
    const int w = s * 46 / 100, h = s * 72 / 100;
    e.drawRoundBorder(x + (s - w) / 2, y + (s - h) / 2, w, h, 8, 3, c);
    e.fillRect(x + s / 2 - 5, y + (s + h) / 2 - 11, 10, 3, c);
}
[[maybe_unused]] void iconMail(UiEngine& e, int x, int y, int s, SDL_Color c) {
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


namespace {
int gIconStyle = 2;   // row icons: 0 pixel | 1 filled | 2 outline (default, chosen by the user)
} // namespace
void setIconStyle(int style) { gIconStyle = std::max(0, std::min(2, style)); }
int iconStyle() { return gIconStyle; }


// ============================================================================
//  AddonStation PS4 — UI v2 (same identity, cleaner & more modern)
//   * one solid focus ring colour (no glow)            * Home Deals Store Settings (no chat)
//   * no notification bell                              * every screen mirrors for Arabic (RTL)
//   * new screens: Search, Profile, AC Wallet, Plans, Menu (△)
// ============================================================================

namespace {

SDL_Color kBAR{13, 27, 37, 255};    // theme-b (was 17,19,24)
SDL_Color kSF2{24, 44, 58, 255};     // theme-b (was 28,32,39)
SDL_Color kCHIPTX{205, 211, 218, 255};
SDL_Color kERT{255, 128, 150, 255};
constexpr SDL_Color kEPIC{139, 92, 246, 255};
SDL_Color kEPICT{186, 164, 255, 255};
SDL_Color kWARN{251, 160, 60, 255};
SDL_Color kSCRIM{3, 4, 6, 214};

void applyTheme(bool light) {
    gLight = light;
    if (!light) {
        kBG = {8, 19, 26, 255}; kSF = {18, 34, 46, 255}; kSF2 = {24, 44, 58, 255}; kBAR = {13, 27, 37, 255};   // theme-b
        kBD = {255, 255, 255, 20}; kTX = {243, 245, 247, 255}; kMU = {134, 169, 181, 255};   // theme-b kCHIPTX = {205, 211, 218, 255};
        kGD = {255, 201, 64, 255}; kFOCUS = {255, 255, 255, 255}; kINKRGB = {255, 255, 255, 255}; kSCRIM = {3, 4, 6, 214};
        kBLl = {74, 168, 255, 255}; kGRN2 = {150, 205, 255, 255}; kERT = {255, 128, 150, 255}; kEPICT = {186, 164, 255, 255};
        kWARN = {251, 160, 60, 255};
    } else {
        kBG = {242, 244, 247, 255}; kSF = {255, 255, 255, 255}; kSF2 = {234, 238, 243, 255}; kBAR = {255, 255, 255, 255};
        kBD = {16, 20, 26, 26}; kTX = {17, 20, 24, 255}; kMU = {92, 101, 114, 255}; kCHIPTX = {52, 58, 66, 255};
        kGD = {168, 112, 0, 255}; kFOCUS = {17, 20, 24, 255}; kINKRGB = {16, 20, 26, 255}; kSCRIM = {16, 20, 26, 140};
        kBLl = {16, 105, 195, 255}; kGRN2 = {10, 85, 160, 255}; kERT = {214, 38, 74, 255}; kEPICT = {110, 66, 230, 255};
        kWARN = {196, 108, 0, 255};
    }
}

// ---- RTL layout helper -----------------------------------------------------
struct Lay {
    bool rtl = false;
    int X(int x, int w) const { return rtl ? 1920 - x - w : x; }   // LTR left edge -> real left edge
};

// v4 "Overworld": navigation is a side rail (left in LTR, right in Arabic). Content area in LTR coordinates:
constexpr int kRailW = 132;
constexpr int kCL = 180;            // content left
constexpr int kCR = 1856;           // content right
constexpr int kCW = kCR - kCL;      // 1676
bool gRail = false;                 // set by drawTopNav (rail drawn) -> legend bar aligns with the content area

// Draw an image like CSS object-fit: cover (crop, never stretch). ax/ay = focus point 0..1.
void coverImage(UiEngine& e, const std::string& name, int x, int y, int w, int h, int radius = 0,
                float ax = 0.5f, float ay = 0.5f, Uint8 alpha = 255) {
    int iw = 0, ih = 0;
    if (w <= 0 || h <= 0) return;
    if (!e.imageSize(name, iw, ih) || iw <= 0 || ih <= 0) {
        if (radius > 0) e.drawImageRounded(name, x, y, w, h, radius, alpha); else e.drawImage(name, x, y, w, h, alpha);
        return;
    }
    int sw = iw, sh = static_cast<int>(static_cast<long long>(iw) * h / w);
    if (sh > ih) { sh = ih; sw = static_cast<int>(static_cast<long long>(ih) * w / h); }
    const int sx = static_cast<int>((iw - sw) * ax), sy = static_cast<int>((ih - sh) * ay);
    e.drawImageCropped(name, sx, sy, sw, sh, x, y, w, h, alpha, radius);
}

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
[[maybe_unused]] void icPerson(UiEngine& e, int x, int y, int s, SDL_Color c) {
    e.drawCircle(x + s / 2, y + s * 32 / 100, s * 17 / 100, c);
    e.drawRoundRect(x + s * 18 / 100, y + s * 56 / 100, s * 64 / 100, s * 32 / 100, s * 15 / 100, c);
}
[[maybe_unused]] void icRocket(UiEngine& e, int x, int y, int s, SDL_Color c) {
    e.drawRoundBorder(x + s * 6 / 100, y + s * 6 / 100, s * 88 / 100, s * 88 / 100, s * 44 / 100, 3, c);
    fillTriRight(e, x + s * 38 / 100, y + s * 30 / 100, s * 30 / 100, s * 40 / 100, c);
}
[[maybe_unused]] void icLink(UiEngine& e, int x, int y, int s, SDL_Color c) {
    e.drawRoundBorder(x + s * 2 / 100, y + s * 30 / 100, s * 54 / 100, s * 40 / 100, s * 20 / 100, 4, c);
    e.drawRoundBorder(x + s * 44 / 100, y + s * 30 / 100, s * 54 / 100, s * 40 / 100, s * 20 / 100, 4, c);
}
[[maybe_unused]] void icInfo(UiEngine& e, int x, int y, int s, SDL_Color c) {
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
void shapeIcon(UiEngine& e, int kind, int x, int y, int s, SDL_Color c);   // fwd (v0.64)
void icCheck(UiEngine& e, int x, int y, int s, SDL_Color c) {
    // v0.64 («اي علامة صح في تطبيق اصلح شكلها»): the old check was drawn with 4x4
    // pixel BLOCKS (jagged stairs). Use the clean anti-aliased shape icon (kind 2).
    shapeIcon(e, 2, x, y, s, c);
}
[[maybe_unused]] void icSearch(UiEngine& e, int x, int y, int s, SDL_Color c) {
    e.drawRoundBorder(x + s * 6 / 100, y + s * 6 / 100, s * 62 / 100, s * 62 / 100, s * 31 / 100, std::max(3, s / 9), c);
    e.drawRoundRectRotated(x + s * 79 / 100, y + s * 79 / 100, s * 36 / 100, std::max(4, s / 8), 3, 45.0f, c);
}
[[maybe_unused]] void icCrown(UiEngine& e, int x, int y, int s, SDL_Color c) {
    e.fillRect(x + s * 14 / 100, y + s * 66 / 100, s * 72 / 100, s * 12 / 100, c);
    fillTriUp(e, x + s * 12 / 100, y + s * 26 / 100, s * 26 / 100, s * 42 / 100, c);
    fillTriUp(e, x + s * 37 / 100, y + s * 14 / 100, s * 26 / 100, s * 54 / 100, c);
    fillTriUp(e, x + s * 62 / 100, y + s * 26 / 100, s * 26 / 100, s * 42 / 100, c);
}
// v6.7: solid anti-aliased shape icons (design grid 24x24, 4x4 supersampled once, cached as an image)
//   kind 0 = 5-point star (rating), kind 1 = download arrow + tray line, kind 2 = check mark
void shapeIcon(UiEngine& e, int kind, int x, int y, int s, SDL_Color c) {
    if (s <= 0) return;
    char nm[48];
    std::snprintf(nm, sizeof nm, "__sh_%d_%d_%02x%02x%02x", kind, s, c.r, c.g, c.b);
    if (!e.hasImage(nm)) {
        std::vector<std::vector<float>> polys;   // x0,y0,x1,y1,... in design units
        if (kind == 0) {
            std::vector<float> st;
            const float cx = 12.f, cy = 12.9f, R = 10.6f, r = 4.6f;
            for (int i = 0; i < 10; ++i) {
                const float a = (-90.f + 36.f * i) * 3.14159265f / 180.f, rr = (i % 2) ? r : R;
                st.push_back(cx + rr * std::cos(a)); st.push_back(cy + rr * std::sin(a));
            }
            polys.push_back(st);
        } else if (kind == 1) {
            polys.push_back({10.2f, 2.6f, 13.8f, 2.6f, 13.8f, 10.f, 18.8f, 10.f, 12.f, 17.2f, 5.2f, 10.f, 10.2f, 10.f});
            polys.push_back({4.f, 19.4f, 20.f, 19.4f, 20.f, 22.4f, 4.f, 22.4f});
        } else if (kind == 3) {   // v0.36: eye lens (views)
            std::vector<float> ey;
            const int N = 14;
            for (int i = 0; i <= N; ++i) {
                const float t2 = (float)i / N;
                ey.push_back(2.5f + 19.f * t2);
                ey.push_back(12.f - 8.5f * std::sin(3.14159265f * t2));
            }
            for (int i = N; i >= 0; --i) {
                const float t2 = (float)i / N;
                ey.push_back(2.5f + 19.f * t2);
                ey.push_back(12.f + 8.5f * std::sin(3.14159265f * t2));
            }
            polys.push_back(ey);
        } else if (kind == 4) {   // v0.64: X mark (error toasts) — two balanced diagonal strokes
            polys.push_back({4.6f, 6.4f, 6.4f, 4.6f, 19.4f, 17.6f, 17.6f, 19.4f});
            polys.push_back({17.6f, 4.6f, 19.4f, 6.4f, 6.4f, 19.4f, 4.6f, 17.6f});
        } else {   // v7.0: kind 2 = clean Material-style check (short arm, long arm, thin balanced stroke)
            polys.push_back({6.7f, 11.2f, 11.3f, 15.7f, 9.1f, 17.9f, 4.5f, 13.4f});
            polys.push_back({8.7f, 16.2f, 17.4f, 6.0f, 19.8f, 8.0f, 11.0f, 18.2f});
        }
        auto inside = [&](float X, float Y) {
            for (const auto& P : polys) {
                bool in = false;
                const std::size_t n = P.size() / 2;
                for (std::size_t i = 0, j = n - 1; i < n; j = i++) {
                    const float xi = P[2 * i], yi = P[2 * i + 1], xj = P[2 * j], yj = P[2 * j + 1];
                    if (((yi > Y) != (yj > Y)) && (X < (xj - xi) * (Y - yi) / (yj - yi) + xi)) in = !in;
                }
                if (in) return true;
            }
            return false;
        };
        const float u = 24.f / s;
        std::vector<std::uint8_t> px(static_cast<std::size_t>(s) * s * 4, 0);
        for (int py = 0; py < s; ++py)
            for (int pxl = 0; pxl < s; ++pxl) {
                int hits = 0;
                for (int sy = 0; sy < 4; ++sy)
                    for (int sx = 0; sx < 4; ++sx)
                        hits += inside((pxl + (sx + 0.5f) / 4.f) * u, (py + (sy + 0.5f) / 4.f) * u) ? 1 : 0;
                std::uint8_t* q = &px[(static_cast<std::size_t>(py) * s + pxl) * 4];
                q[0] = c.r; q[1] = c.g; q[2] = c.b; q[3] = static_cast<std::uint8_t>(hits * c.a / 16);
            }
        e.addImageRGBA(nm, s, s, px.data());
    }
    e.drawImage(nm, x, y, s, s);
}
void icStar(UiEngine& e, int x, int y, int s, SDL_Color c) { shapeIcon(e, 0, x, y, s, c); }
[[maybe_unused]] void icHeart(UiEngine& e, int x, int y, int s, SDL_Color c) {
    e.drawCircle(x + s * 30 / 100, y + s * 34 / 100, s * 20 / 100, c);
    e.drawCircle(x + s * 70 / 100, y + s * 34 / 100, s * 20 / 100, c);
    fillTriDown(e, x + s * 10 / 100, y + s * 40 / 100, s * 80 / 100, s * 48 / 100, c);
}
void icDownloadArrow(UiEngine& e, int x, int y, int s, SDL_Color c) { shapeIcon(e, 1, x, y, s, c); }   // v6.7
void icEye(UiEngine& e, int x, int y, int s, SDL_Color c, SDL_Color bg) {   // v0.36: views icon
    shapeIcon(e, 3, x, y, s, c);
    e.drawCircle(x + s / 2, y + s / 2, (s * 4) / 24, bg);
    e.drawCircle(x + s / 2, y + s / 2, (s * 2) / 24, c);
}
[[maybe_unused]] void icMenu(UiEngine& e, int x, int y, int s, SDL_Color c) {
    for (int i = 0; i < 3; ++i) e.drawRoundRect(x + s * 14 / 100, y + s * (24 + i * 24) / 100, s * 72 / 100, std::max(4, s / 9), 3, c);
}

// ---- row icons v3: three selectable styles (see setIconStyle) ------------------------------
// kinds: 0 globe/language, 1 person/account, 2 launch Minecraft, 3 link console, 4 info/about,
//        5 sign out, 6 wallet, 7 plans/crown, 8 search, 9 settings/gear

constexpr float kPi = 3.14159265f;

// thick line segment with round caps (anti-aliased)
void seg(UiEngine& e, float x0, float y0, float x1, float y1, int t, SDL_Color c) {
    const float dx = x1 - x0, dy = y1 - y0;
    const float len = std::sqrt(dx * dx + dy * dy);
    const float ang = std::atan2(dy, dx) * 180.0f / kPi;
    e.drawRoundRectRotated(static_cast<int>(std::lround((x0 + x1) * 0.5f)), static_cast<int>(std::lround((y0 + y1) * 0.5f)),
                           static_cast<int>(len) + t, t, t / 2, ang, c);
}
void ring(UiEngine& e, float cx, float cy, float r, int t, SDL_Color c) {
    const int ir = static_cast<int>(r);
    e.drawRoundBorder(static_cast<int>(cx) - ir, static_cast<int>(cy) - ir, 2 * ir, 2 * ir, ir, t, c);
}
void disc(UiEngine& e, float cx, float cy, float r, SDL_Color c) {
    const int d = static_cast<int>(2 * r);
    e.drawRoundRectRotated(static_cast<int>(cx), static_cast<int>(cy), d, d, d / 2, 0.0f, c);
}

// ---- pixel sprites (Minecraft look). '.' = transparent ------------------------------------
struct Sprite { int n; const char* rows; const char* keys; const SDL_Color* cols; };
constexpr SDL_Color kOut{18, 22, 40, 255};

const char* const kSprGlobe =
    ".....kkkkkk....."
    "...kkbbggbbkk..."
    "..kbBBgggbbbbk.."
    ".kbBBbggGbbggbk."
    ".kbBbbbgbbgggGk."
    "kbbbbbbbbbggGbbk"
    "kggbbbbbbbbgbbbk"
    "kgggbbbbbbbbbbbk"
    "kgGggbbbbbggbbbk"
    "kbgGgbbbbgggGbbk"
    "kbbggbbbbggGbbbk"
    ".kbbbbbbbbgbbbk."
    ".kbbbbbbbbbbbbk."
    "..kbbbggbbbbbk.."
    "...kkbbbbbbkk..."
    ".....kkkkkk.....";
const SDL_Color kColGlobe[] = {kOut, {58, 128, 232, 255}, {118, 178, 255, 255}, {86, 176, 72, 255}, {48, 124, 52, 255}};

const char* const kSprSteve =
    "HHHHHHHH"
    "HHHHHHHH"
    "HSSSSSSH"
    "SSSSSSSS"
    "SWBSSBWS"
    "SSSNNSSS"
    "SSMMMMSS"
    "SSSSSSSS";
const SDL_Color kColSteve[] = {{70, 46, 24, 255}, {196, 138, 102, 255}, {255, 255, 255, 255}, {72, 64, 176, 255}, {140, 88, 60, 255}, {112, 62, 44, 255}};

const char* const kSprGrass =
    "gGgggGgggGgggGgg"
    "ggGgggGgggGgggGg"
    "gggggggggggggggg"
    "gdggggdggdgggdgg"
    "ddgdggddgddgdddg"
    "dddddDdddddddddd"
    "dDdddddddDdddDdd"
    "dddddddddddddddd"
    "ddddDdddddddDddd"
    "dDdddddDdddddddd"
    "ddddddddddDddddd"
    "dddDdddddddddddD"
    "dddddddDdddddddd"
    "dDdddddddddDdddd"
    "ddddddDdddddddDd"
    "dddddddddddddddd";
const SDL_Color kColGrass[] = {{104, 176, 58, 255}, {146, 208, 84, 255}, {134, 96, 67, 255}, {98, 68, 46, 255}};

const char* const kSprChain =
    ".......kkkkkkk.."
    "......kLLLLLLLk."
    "......kLkkkkkLk."
    "......kLk...kLk."
    "......kLk...kLk."
    "......kLkkkkkLk."
    ".......kLDDDLk.."
    "..kkkkkkkDDkkk.."
    ".kLLLLLLkDDk...."
    ".kLkkkkkLkk....."
    ".kLk...kLk......"
    ".kLk...kLk......"
    ".kLkkkkkLk......"
    "..kLLLLLk......."
    "...kkkkk........"
    "................";
const SDL_Color kColChain[] = {kOut, {214, 224, 240, 255}, {140, 152, 176, 255}};

const char* const kSprBook =
    "................"
    "...kkkkkkkkkkk.."
    "..kRRRRRRRRRRk.."
    "..kRrrrrrrrrRPk."
    "..kRrYYYYYYrRPk."
    "..kRrrrrrrrrRPk."
    "..kRrrYYYYrrRPk."
    "..kRrrrrrrrrRPk."
    "..kRrrrrrrrrRPk."
    "..kRrrrrrrrrRPk."
    "..kRrrrrrrrrRPk."
    "..kRRRRRRRRRRPk."
    "..kDDDDDDDDDDPk."
    "..kPPPPPPPPPPPk."
    "...kkkkkkkkkkk.."
    "................";
const SDL_Color kColBook[] = {kOut, {150, 58, 52, 255}, {182, 78, 64, 255}, {247, 196, 81, 255}, {238, 228, 204, 255}, {100, 36, 34, 255}};

const char* const kSprDoor =
    "..kkkkkkkkk....."
    "..kOOOOOOOk....."
    "..kOoOOOoOk....."
    "..kOoOOOoOk....."
    "..kOOOOOOOk....."
    "..kOOOOOOOk....."
    "..kOoOOOoOk..r.."
    "..kOoOOOYOk..rr."
    "..kOOOOOOOkrrrrr"
    "..kOOOOOOOk..rr."
    "..kOoOOOoOk..r.."
    "..kOoOOOoOk....."
    "..kOOOOOOOk....."
    "..kOOOOOOOk....."
    "..kkkkkkkkk....."
    "................";
const SDL_Color kColDoor[] = {kOut, {176, 132, 74, 255}, {134, 96, 52, 255}, {247, 196, 81, 255}, {255, 92, 110, 255}};

const char* const kSprChest =
    "................"
    "..kkkkkkkkkkkk.."
    "..kWWWWWWWWWWk.."
    "..kWwwwwwwwwWk.."
    "..kWwwwwwwwwWk.."
    "..kWwwwwwwwwWk.."
    "..kkkkkYYkkkkk.."
    "..kWwwwYYwwwWk.."
    "..kWwwwkkwwwWk.."
    "..kWwwwwwwwwWk.."
    "..kWwwwwwwwwWk.."
    "..kWwwwwwwwwWk.."
    "..kWWWWWWWWWWk.."
    "..kkkkkkkkkkkk.."
    "................"
    "................";
const SDL_Color kColChest[] = {kOut, {120, 78, 34, 255}, {172, 118, 54, 255}, {247, 196, 81, 255}};

const char* const kSprCrown =
    "................"
    "................"
    ".k.....kk.....k."
    "kYk...kYYk...kYk"
    "kYYk.kYYYYk.kYYk"
    "kYYYkYYYYYYkYYYk"
    "kYYYYYYYYYYYYYYk"
    "kYYRYYYBBYYYRYYk"
    "kYYYYYYYYYYYYYYk"
    "kyyyyyyyyyyyyyyk"
    "kYYGYYYYYYYYGYYk"
    "kYYYYYYYYYYYYYYk"
    "kkkkkkkkkkkkkkkk"
    "................"
    "................"
    "................";
const SDL_Color kColCrown[] = {kOut, {250, 204, 86, 255}, {255, 92, 110, 255}, {96, 170, 255, 255}, {200, 140, 30, 255}, {72, 200, 120, 255}};

const char* const kSprGlass =
    "................"
    "....kkkkkk......"
    "...kGGGGGGk....."
    "..kGwwBBBBGk...."
    "..kGwBBBBBGk...."
    "..kGBBBBBBGk...."
    "..kGBBBBBBGk...."
    "..kGBBBBBBGk...."
    "...kGGGGGGkk...."
    "....kkkkkkHHk..."
    "..........kHHk.."
    "...........kHHk."
    "............kHHk"
    ".............kk."
    "................"
    "................";
const SDL_Color kColGlass[] = {kOut, {214, 168, 70, 255}, {255, 255, 255, 255}, {140, 196, 255, 255}, {120, 80, 44, 255}};

const char* const kSprGear =
    "................"
    "......kkkk......"
    "...kk.kLLk.kk..."
    "..kLLkkLLkkLLk.."
    "..kLLLLLLLLLLk.."
    "...kLLLDDLLLk..."
    ".kkkLLD..DLLkkk."
    ".kLLLD....DLLLk."
    ".kLLLD....DLLLk."
    ".kkkLLD..DLLkkk."
    "...kLLLDDLLLk..."
    "..kLLLLLLLLLLk.."
    "..kLLkkLLkkLLk.."
    "...kk.kLLk.kk..."
    "......kkkk......"
    "................";
const SDL_Color kColGear[] = {kOut, {200, 210, 228, 255}, {124, 136, 160, 255}};

const Sprite kSprites[10] = {
    {16, kSprGlobe, "kbBgG", kColGlobe},  {8, kSprSteve, "HSWBNM", kColSteve}, {16, kSprGrass, "gGdD", kColGrass},
    {16, kSprChain, "kLD", kColChain},    {16, kSprBook, "kRrYPD", kColBook},  {16, kSprDoor, "kOoYr", kColDoor},
    {16, kSprChest, "kWwY", kColChest},   {16, kSprCrown, "kYRByG", kColCrown}, {16, kSprGlass, "kGwBH", kColGlass},
    {16, kSprGear, "kLD", kColGear}};

void drawSprite(UiEngine& e, const Sprite& sp, int x, int y, int s) {
    const int n = sp.n;
    for (int r = 0; r < n; ++r) {
        const int y0 = y + r * s / n, y1 = y + (r + 1) * s / n;
        for (int c = 0; c < n; ++c) {
            const char ch = sp.rows[r * n + c];
            if (ch == '.') continue;
            const char* k = std::strchr(sp.keys, ch);
            if (!k) continue;
            const int x0 = x + c * s / n, x1 = x + (c + 1) * s / n;
            e.fillRect(x0, y0, x1 - x0, y1 - y0, sp.cols[k - sp.keys]);
        }
    }
}

// ---- vector glyphs: outline (stroke) and filled (solid white on a coloured tile) -------------
// (x,y,s) = icon box. `ink` = glyph colour, `cut` = colour used to punch details into filled shapes.
void glyph(UiEngine& e, int kind, int x, int y, int s, SDL_Color ink, SDL_Color cut, bool filled) {
    const float X = static_cast<float>(x), Y = static_cast<float>(y), S = static_cast<float>(s);
    auto px = [&](float f) { return X + f * S; };
    auto py = [&](float f) { return Y + f * S; };
    const int t = std::max(3, s / 9);             // stroke
    const int tc = std::max(2, s / 14);           // cut stroke for filled shapes
    switch (kind) {
        case 0: {   // globe
            if (filled) {
                disc(e, px(0.5f), py(0.5f), S * 0.44f, ink);
                e.drawRoundBorder(static_cast<int>(px(0.33f)), static_cast<int>(py(0.08f)), static_cast<int>(S * 0.34f), static_cast<int>(S * 0.84f), static_cast<int>(S * 0.17f), tc, cut);
                seg(e, px(0.1f), py(0.5f), px(0.9f), py(0.5f), tc, cut);
                seg(e, px(0.18f), py(0.3f), px(0.82f), py(0.3f), tc, cut);
                seg(e, px(0.18f), py(0.7f), px(0.82f), py(0.7f), tc, cut);
            } else {
                ring(e, px(0.5f), py(0.5f), S * 0.44f, t, ink);
                e.drawRoundBorder(static_cast<int>(px(0.32f)), static_cast<int>(py(0.08f)), static_cast<int>(S * 0.36f), static_cast<int>(S * 0.84f), static_cast<int>(S * 0.18f), t, ink);
                seg(e, px(0.1f), py(0.5f), px(0.9f), py(0.5f), t, ink);
            }
            break;
        }
        case 1: {   // person
            if (filled) {
                disc(e, px(0.5f), py(0.31f), S * 0.2f, ink);
                e.drawRoundRect(static_cast<int>(px(0.14f)), static_cast<int>(py(0.58f)), static_cast<int>(S * 0.72f), static_cast<int>(S * 0.34f), static_cast<int>(S * 0.17f), ink);
            } else {
                ring(e, px(0.5f), py(0.31f), S * 0.19f, t, ink);
                e.drawRoundBorder(static_cast<int>(px(0.14f)), static_cast<int>(py(0.6f)), static_cast<int>(S * 0.72f), static_cast<int>(S * 0.32f), static_cast<int>(S * 0.16f), t, ink);
            }
            break;
        }
        case 2: {   // gamepad (launch Minecraft)
            const int bx = static_cast<int>(px(0.04f)), by = static_cast<int>(py(0.26f));
            const int bw = static_cast<int>(S * 0.92f), bh = static_cast<int>(S * 0.5f);
            if (filled) {
                e.drawRoundRect(bx, by, bw, bh, bh / 2, ink);
                disc(e, px(0.22f), py(0.7f), S * 0.13f, ink);
                disc(e, px(0.78f), py(0.7f), S * 0.13f, ink);
                seg(e, px(0.18f), py(0.51f), px(0.38f), py(0.51f), tc + 1, cut);
                seg(e, px(0.28f), py(0.41f), px(0.28f), py(0.61f), tc + 1, cut);
                disc(e, px(0.68f), py(0.45f), S * 0.055f, cut);
                disc(e, px(0.78f), py(0.57f), S * 0.055f, cut);
            } else {
                e.drawRoundBorder(bx, by, bw, bh, bh / 2, t, ink);
                seg(e, px(0.2f), py(0.51f), px(0.38f), py(0.51f), t, ink);
                seg(e, px(0.29f), py(0.42f), px(0.29f), py(0.6f), t, ink);
                disc(e, px(0.68f), py(0.45f), S * 0.06f, ink);
                disc(e, px(0.78f), py(0.57f), S * 0.06f, ink);
            }
            break;
        }
        case 3: {   // link (two chain links at 45 deg, drawn as capsule outlines)
            const int lt = filled ? t + 2 : t;
            auto capsule = [&](float cx, float cy) {
                const float L = S * 0.2f, R = S * 0.16f;          // half length, radius
                const float ux = 0.7071f, uy = -0.7071f;          // along
                const float nx = 0.7071f, ny = 0.7071f;           // normal
                seg(e, cx - ux * L + nx * R, cy - uy * L + ny * R, cx + ux * L + nx * R, cy + uy * L + ny * R, lt, ink);
                seg(e, cx - ux * L - nx * R, cy - uy * L - ny * R, cx + ux * L - nx * R, cy + uy * L - ny * R, lt, ink);
                for (int i = 0; i <= 8; ++i) {   // round ends
                    const float a0 = kPi * i / 8, a1 = kPi * (i + 1) / 8;
                    auto endPt = [&](float sgn, float a, float& ox, float& oy) {
                        const float ca = std::cos(a), sa = std::sin(a);
                        ox = cx + sgn * ux * L + R * (nx * ca + sgn * ux * sa);
                        oy = cy + sgn * uy * L + R * (ny * ca + sgn * uy * sa);
                    };
                    float ax, ay, bx2, by2;
                    if (i < 8) {
                        endPt(1.0f, a0, ax, ay); endPt(1.0f, a1, bx2, by2); seg(e, ax, ay, bx2, by2, lt, ink);
                        endPt(-1.0f, a0, ax, ay); endPt(-1.0f, a1, bx2, by2); seg(e, ax, ay, bx2, by2, lt, ink);
                    }
                }
            };
            capsule(px(0.36f), py(0.64f));
            capsule(px(0.64f), py(0.36f));
            break;
        }
        case 4: {   // info
            if (filled) {
                disc(e, px(0.5f), py(0.5f), S * 0.44f, ink);
                disc(e, px(0.5f), py(0.29f), S * 0.07f, cut);
                seg(e, px(0.5f), py(0.45f), px(0.5f), py(0.73f), static_cast<int>(S * 0.12f), cut);
            } else {
                ring(e, px(0.5f), py(0.5f), S * 0.44f, t, ink);
                disc(e, px(0.5f), py(0.3f), S * 0.065f, ink);
                seg(e, px(0.5f), py(0.46f), px(0.5f), py(0.72f), t + 1, ink);
            }
            break;
        }
        case 5: {   // sign out: door + arrow
            if (filled) {
                e.drawRoundRect(static_cast<int>(px(0.1f)), static_cast<int>(py(0.1f)), static_cast<int>(S * 0.46f), static_cast<int>(S * 0.8f), static_cast<int>(S * 0.08f), ink);
                seg(e, px(0.34f), py(0.5f), px(0.56f), py(0.5f), t + 1, cut);
                seg(e, px(0.56f), py(0.5f), px(0.88f), py(0.5f), t + 1, ink);
                seg(e, px(0.88f), py(0.5f), px(0.72f), py(0.34f), t + 1, ink);
                seg(e, px(0.88f), py(0.5f), px(0.72f), py(0.66f), t + 1, ink);
            } else {
                seg(e, px(0.14f), py(0.12f), px(0.14f), py(0.88f), t, ink);
                seg(e, px(0.14f), py(0.12f), px(0.54f), py(0.12f), t, ink);
                seg(e, px(0.14f), py(0.88f), px(0.54f), py(0.88f), t, ink);
                seg(e, px(0.54f), py(0.12f), px(0.54f), py(0.3f), t, ink);
                seg(e, px(0.54f), py(0.7f), px(0.54f), py(0.88f), t, ink);
                seg(e, px(0.36f), py(0.5f), px(0.88f), py(0.5f), t, ink);
                seg(e, px(0.88f), py(0.5f), px(0.72f), py(0.34f), t, ink);
                seg(e, px(0.88f), py(0.5f), px(0.72f), py(0.66f), t, ink);
            }
            break;
        }
        case 6: {   // wallet
            const int wx = static_cast<int>(px(0.08f)), wy = static_cast<int>(py(0.2f));
            const int ww = static_cast<int>(S * 0.84f), wh = static_cast<int>(S * 0.64f);
            if (filled) {
                e.drawRoundRect(wx, wy, ww, wh, static_cast<int>(S * 0.12f), ink);
                seg(e, px(0.14f), py(0.36f), px(0.86f), py(0.36f), tc, cut);
                e.drawRoundRect(static_cast<int>(px(0.58f)), static_cast<int>(py(0.46f)), static_cast<int>(S * 0.34f), static_cast<int>(S * 0.22f), static_cast<int>(S * 0.1f), cut);
                disc(e, px(0.7f), py(0.57f), S * 0.045f, ink);
            } else {
                e.drawRoundBorder(wx, wy, ww, wh, static_cast<int>(S * 0.12f), t, ink);
                seg(e, px(0.14f), py(0.36f), px(0.86f), py(0.36f), t, ink);
                e.drawRoundBorder(static_cast<int>(px(0.56f)), static_cast<int>(py(0.46f)), static_cast<int>(S * 0.36f), static_cast<int>(S * 0.22f), static_cast<int>(S * 0.1f), t, ink);
            }
            break;
        }
        case 7: {   // crown
            const float pts[7][2] = {{0.12f, 0.74f}, {0.12f, 0.3f}, {0.33f, 0.5f}, {0.5f, 0.2f}, {0.67f, 0.5f}, {0.88f, 0.3f}, {0.88f, 0.74f}};
            if (filled) {
                e.fillRect(static_cast<int>(px(0.12f)), static_cast<int>(py(0.5f)), static_cast<int>(S * 0.76f), static_cast<int>(S * 0.26f), ink);
                for (int i = 1; i < 6; ++i) seg(e, px(pts[i][0]), py(pts[i][1]), px(pts[i + 1][0]), py(pts[i + 1][1]), t + 1, ink);
                seg(e, px(pts[0][0]), py(pts[0][1]), px(pts[1][0]), py(pts[1][1]), t + 1, ink);
                fillTriUp(e, static_cast<int>(px(0.12f)), static_cast<int>(py(0.3f)), static_cast<int>(S * 0.22f), static_cast<int>(S * 0.22f), ink);
                fillTriUp(e, static_cast<int>(px(0.66f)), static_cast<int>(py(0.3f)), static_cast<int>(S * 0.22f), static_cast<int>(S * 0.22f), ink);
                fillTriUp(e, static_cast<int>(px(0.33f)), static_cast<int>(py(0.2f)), static_cast<int>(S * 0.34f), static_cast<int>(S * 0.32f), ink);
                e.drawRoundRect(static_cast<int>(px(0.12f)), static_cast<int>(py(0.8f)), static_cast<int>(S * 0.76f), std::max(3, static_cast<int>(S * 0.09f)), 2, ink);
                disc(e, px(0.5f), py(0.63f), S * 0.06f, cut);
            } else {
                for (int i = 0; i < 6; ++i) seg(e, px(pts[i][0]), py(pts[i][1]), px(pts[i + 1][0]), py(pts[i + 1][1]), t, ink);
                seg(e, px(0.12f), py(0.74f), px(0.88f), py(0.74f), t, ink);
                seg(e, px(0.16f), py(0.88f), px(0.84f), py(0.88f), t, ink);
            }
            break;
        }
        case 14: {  // download (arrow into tray) — the «تنزيلاتي» rail item
            seg(e, px(0.5f), py(0.16f), px(0.5f), py(0.58f), t, ink);
            seg(e, px(0.28f), py(0.38f), px(0.5f), py(0.6f), t, ink);
            seg(e, px(0.72f), py(0.38f), px(0.5f), py(0.6f), t, ink);
            seg(e, px(0.18f), py(0.72f), px(0.18f), py(0.86f), t, ink);
            seg(e, px(0.18f), py(0.86f), px(0.82f), py(0.86f), t, ink);
            seg(e, px(0.82f), py(0.72f), px(0.82f), py(0.86f), t, ink);
            break;
        }
        case 8: {   // search
            const int st = filled ? t + 2 : t;
            ring(e, px(0.42f), py(0.42f), S * 0.3f, st, ink);
            seg(e, px(0.64f), py(0.64f), px(0.88f), py(0.88f), st + 1, ink);
            break;
        }
        case 10: {  // home
            seg(e, px(0.1f), py(0.5f), px(0.5f), py(0.14f), t, ink);
            seg(e, px(0.5f), py(0.14f), px(0.9f), py(0.5f), t, ink);
            seg(e, px(0.22f), py(0.42f), px(0.22f), py(0.88f), t, ink);
            seg(e, px(0.78f), py(0.42f), px(0.78f), py(0.88f), t, ink);
            seg(e, px(0.22f), py(0.88f), px(0.78f), py(0.88f), t, ink);
            seg(e, px(0.42f), py(0.88f), px(0.42f), py(0.64f), t, ink);
            seg(e, px(0.42f), py(0.64f), px(0.58f), py(0.64f), t, ink);
            seg(e, px(0.58f), py(0.64f), px(0.58f), py(0.88f), t, ink);
            break;
        }
        case 11: {  // tag
            const float q[5][2] = {{0.12f, 0.5f}, {0.12f, 0.14f}, {0.48f, 0.14f}, {0.88f, 0.56f}, {0.52f, 0.9f}};
            for (int i = 0; i < 5; ++i) seg(e, px(q[i][0]), py(q[i][1]), px(q[(i + 1) % 5][0]), py(q[(i + 1) % 5][1]), t, ink);
            disc(e, px(0.32f), py(0.34f), S * 0.075f, ink);
            break;
        }
        case 12: {  // grid
            const int g = static_cast<int>(S * 0.34f), rr = std::max(3, s / 10);
            for (int i = 0; i < 4; ++i)
                e.drawRoundBorder(static_cast<int>(px(i % 2 ? 0.56f : 0.1f)), static_cast<int>(py(i / 2 ? 0.56f : 0.1f)), g, g, rr, t, ink);
            break;
        }
        case 13: {  // appearance: half sun / half moon
            ring(e, px(0.5f), py(0.5f), S * 0.27f, t, ink);
            for (int i = 0; i < 8; ++i) {
                const float a = i * kPi / 4;
                seg(e, px(0.5f) + std::cos(a) * S * 0.4f, py(0.5f) + std::sin(a) * S * 0.4f,
                    px(0.5f) + std::cos(a) * S * 0.47f, py(0.5f) + std::sin(a) * S * 0.47f, std::max(2, t - 1), ink);
            }
            disc(e, px(0.5f), py(0.5f), S * 0.13f, ink);
            (void)cut;
            break;
        }
        default: {  // gear
            const float cx = px(0.5f), cy = py(0.5f);
            for (int i = 0; i < 8; ++i) {
                const float a = i * kPi / 4;
                const float tx = cx + std::cos(a) * S * 0.38f, ty = cy + std::sin(a) * S * 0.38f;
                e.drawRoundRectRotated(static_cast<int>(tx), static_cast<int>(ty), static_cast<int>(S * 0.2f), static_cast<int>(S * 0.17f), 2, a * 180.0f / kPi, ink);
            }
            if (filled) {
                disc(e, cx, cy, S * 0.33f, ink);
                disc(e, cx, cy, S * 0.12f, cut);
            } else {
                ring(e, cx, cy, S * 0.32f, t + 1, ink);
                ring(e, cx, cy, S * 0.12f, t, ink);
            }
            break;
        }
    }
}

// Icon tile used by Settings rows, the Settings detail header, Profile rows and the triangle Menu.
// (tx,ty) = tile top-left, ts = tile size, acc = row accent colour.
[[maybe_unused]] void iconTile(UiEngine& e, int kind, int tx, int ty, int ts, SDL_Color acc) {
    const int r = ts * 26 / 100;
    if (gIconStyle == 1) {   // filled: solid accent squircle + white glyph
        const SDL_Color top{static_cast<Uint8>(std::min(255, acc.r + 30)), static_cast<Uint8>(std::min(255, acc.g + 30)), static_cast<Uint8>(std::min(255, acc.b + 30)), 255};
        const SDL_Color bot{static_cast<Uint8>(acc.r * 62 / 100), static_cast<Uint8>(acc.g * 62 / 100), static_cast<Uint8>(acc.b * 62 / 100), 255};
        e.dropShadow(tx, ty + 3, ts, ts, r, 8, 90);
        e.drawRoundGradV(tx, ty, ts, ts, r, top, bot);
        e.fillRect(tx + r, ty + 2, ts - 2 * r, 1, SDL_Color{255, 255, 255, 90});
        const int is = ts * 58 / 100;
        const SDL_Color mid{static_cast<Uint8>((top.r + bot.r) / 2), static_cast<Uint8>((top.g + bot.g) / 2), static_cast<Uint8>((top.b + bot.b) / 2), 255};
        glyph(e, kind, tx + (ts - is) / 2, ty + (ts - is) / 2, is, kWHT, mid, true);
    } else if (gIconStyle == 2) {   // outline: tinted glass tile + accent strokes
        e.drawRoundGradV(tx, ty, ts, ts, r, SDL_Color{acc.r, acc.g, acc.b, 60}, SDL_Color{acc.r, acc.g, acc.b, 18});
        e.drawRoundBorder(tx, ty, ts, ts, r, 2, SDL_Color{acc.r, acc.g, acc.b, 110});
        const int is = ts * 62 / 100;
        const SDL_Color ink{static_cast<Uint8>(std::min(255, acc.r + 40)), static_cast<Uint8>(std::min(255, acc.g + 40)), static_cast<Uint8>(std::min(255, acc.b + 40)), 255};
        glyph(e, kind, tx + (ts - is) / 2, ty + (ts - is) / 2, is, ink, ink, false);
    } else {   // pixel: dark well with accent rim + Minecraft-style sprite
        e.drawRoundGradV(tx, ty, ts, ts, r, SDL_Color{acc.r, acc.g, acc.b, 70}, SDL_Color{acc.r, acc.g, acc.b, 26});
        e.drawRoundBorder(tx, ty, ts, ts, r, 2, SDL_Color{acc.r, acc.g, acc.b, 130});
        e.fillRect(tx + r, ty + 2, ts - 2 * r, 1, SDL_Color{255, 255, 255, 40});
        const Sprite& sp = kSprites[std::max(0, std::min(9, kind))];
        const int is = std::max(sp.n, (ts * 80 / 100) / sp.n * sp.n);   // whole pixels: crisp edges
        drawSprite(e, sp, tx + (ts - is) / 2, ty + (ts - is) / 2, is);
    }
}


// ---- shared widgets ----------------------------------------------------------
// Glass surface: soft vertical gradient + hairline border + 1px top highlight.
// tone 0 = card, 1 = raised panel / dialog, 2 = sunken (fields, stat wells), 3 = focused / selected row
void surface(UiEngine& e, int x, int y, int w, int h, int r, int tone = 0, bool shadow = false) {
    if (w <= 0 || h <= 0) return;
    if (shadow) e.dropShadow(x, y, w, h, r, 16, gLight ? 50 : 130);
    if (gLight) {
        switch (tone) {
            case 1: e.drawRoundRect(x, y, w, h, r, SDL_Color{255, 255, 255, 255}); break;
            case 2: e.drawRoundRect(x, y, w, h, r, SDL_Color{16, 20, 26, 10}); break;
            case 3: e.drawRoundGradV(x, y, w, h, r, SDL_Color{232, 238, 245, 255}, SDL_Color{224, 231, 240, 255}); break;
            default: e.drawRoundRect(x, y, w, h, r, SDL_Color{255, 255, 255, 255}); break;
        }
        e.drawRoundBorder(x, y, w, h, r, 2, tone == 3 ? ink(50) : (tone == 2 ? ink(16) : kBD));
        return;
    }
    switch (tone) {
        case 1: e.drawRoundGradV(x, y, w, h, r, SDL_Color{24, 27, 33, 255}, SDL_Color{16, 18, 22, 255}); break;
        case 2: e.drawRoundRect(x, y, w, h, r, SDL_Color{5, 6, 8, 200}); break;
        case 3: e.drawRoundGradV(x, y, w, h, r, SDL_Color{38, 43, 52, 255}, SDL_Color{29, 33, 40, 255}); break;
        default: e.drawRoundGradV(x, y, w, h, r, SDL_Color{25, 28, 34, 255}, SDL_Color{19, 22, 27, 255}); break;
    }
    e.drawRoundBorder(x, y, w, h, r, 2, tone == 3 ? SDL_Color{255, 255, 255, 44} : (tone == 2 ? SDL_Color{255, 255, 255, 14} : kBD));
    if (tone != 2 && w > 2 * r + 4) e.fillRect(x + r, y + 2, w - 2 * r, 1, SDL_Color{255, 255, 255, static_cast<Uint8>(tone == 3 ? 40 : 22)});
}

// Small pixel "block" (Minecraft cube look): top face + two darker sides. Used as a bullet / section mark.
[[maybe_unused]] void pixelBlock(UiEngine& e, int x, int y, int s, SDL_Color c) {
    const int h = s / 2;
    e.fillRect(x, y, s, s, SDL_Color{static_cast<Uint8>(c.r * 55 / 100), static_cast<Uint8>(c.g * 55 / 100), static_cast<Uint8>(c.b * 55 / 100), 255});
    e.fillRect(x, y, s, h, c);
    e.fillRect(x, y, s, std::max(2, s / 8), SDL_Color{255, 255, 255, 110});
    e.fillRect(x + s / 2, y + h, s - s / 2, s - h, SDL_Color{static_cast<Uint8>(c.r * 72 / 100), static_cast<Uint8>(c.g * 72 / 100), static_cast<Uint8>(c.b * 72 / 100), 255});
}

// v7.4 — section marker: a small glowing gem (rounded diamond) with a soft breathing halo. Replaces the old
// square pixel block in section titles and kicker labels. Same box (x, y, s x s) as pixelBlock, so nothing shifts.
void sectionMark(UiEngine& e, int x, int y, int s, SDL_Color c) {
    animPush({AnimCall::Mark, x, y, s, 0, 0, 0, c}, x - 12, y - 12, s + 24, s + 24);
    const float t = static_cast<float>(SDL_GetTicks() % 600000u) / 1000.0f;
    const float br = std::sin(t * 3.1416f * 2.0f / 1.8f) * 0.5f + 0.5f;
    const int cx = x + s / 2, cy = y + s / 2;
    auto mix = [&](int k) { return SDL_Color{static_cast<Uint8>(c.r + (255 - c.r) * k / 100), static_cast<Uint8>(c.g + (255 - c.g) * k / 100), static_cast<Uint8>(c.b + (255 - c.b) * k / 100), 255}; };
    e.drawCircle(cx, cy, s * 7 / 10 + static_cast<int>(3 * br), SDL_Color{c.r, c.g, c.b, static_cast<Uint8>(gLight ? 26 + 30 * br : 30 + 36 * br)});
    e.drawCircle(cx, cy, s * 5 / 10 + 1, SDL_Color{c.r, c.g, c.b, static_cast<Uint8>(gLight ? 40 : 52)});
    const int d = std::max(8, s * 74 / 100);
    e.drawRoundRectRotated(cx, cy, d, d, std::max(2, d / 5), 45.0f, SDL_Color{static_cast<Uint8>(c.r * 70 / 100), static_cast<Uint8>(c.g * 70 / 100), static_cast<Uint8>(c.b * 70 / 100), 255});
    e.drawRoundRectRotated(cx, cy, d * 82 / 100, d * 82 / 100, std::max(2, d / 6), 45.0f, c);
    e.drawRoundRectRotated(cx - d / 14, cy - d / 5, d * 34 / 100, d * 34 / 100, std::max(1, d / 10), 45.0f, mix(62));
}

// Page background. PERFORMANCE: the old version blended 18 huge translucent circles every frame
// (~10 M pixel blends). Now it is ONE opaque image blit (bg_app_dark / bg_app_light in ui.pak),
// or a flat clear when the image is missing.
void backdrop(UiEngine& e) {
    const char* name = gLight ? "bg_app_light" : "bg_app_dark";
    int w = 0, h = 0;
    if (e.imageSize(name, w, h)) e.drawImage(name, 0, 0, 1920, 1080);
    else e.clear(kBG);
}

int chipBox(UiEngine& e, int x, int y, int w, int h, SDL_Color bg, SDL_Color border, int bw = 2) {
    e.drawRoundRect(x, y, w, h, h / 2, bg);
    if (bw > 0) e.drawRoundBorder(x, y, w, h, h / 2, bw, border);
    return w;
}

[[maybe_unused]] void panel(UiEngine& e, int x, int y, int w, int h, int r, SDL_Color bg) {
    (void)bg;
    surface(e, x, y, w, h, r, 1);
}

[[maybe_unused]] void avatarCircle(UiEngine& e, int x, int y, int s, bool ring) {
    if (ring) {   // gold -> amber ring
        e.drawCircle(x + s / 2, y + s / 2, s / 2 + 5, SDL_Color{255, 222, 130, 255});
        e.drawRoundGradV(x - 3, y - 3, s + 6, s + 6, s / 2 + 3, kGD, SDL_Color{214, 140, 30, 255});
    }
    e.drawCircle(x + s / 2, y + s / 2, s / 2, SDL_Color{48, 54, 62, 255});
    e.drawImageRounded("icon_avatar", x, y, s, s, s / 2);
}

// Section heading: pixel block mark + F77 title.
void sectionTitle(UiEngine& e, const Lay& L, const std::string& s, int x, int w, int y, int px = 34) {
    const int bs = std::max(18, px * 62 / 100);
    sectionMark(e, L.X(x, bs), y + (px - bs) / 2 + 4, bs, kBLl);
    boxText(e, L, s, x + bs + 18, w - bs - 18, y, px, kTX, FontRole::Pixel);
}

// v7.4 — style: 0 primary (green -> teal, glossy) | 1 ghost glass | 2 gold | 3 emerald outline | 4 danger outline
// Every button now has: soft coloured under-glow, diagonal gradient, glass highlight on the top half,
// 1px light rim and a darker bottom edge — same size/position as before, so layouts don't move.
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
    if (style == 0) {
        e.dropShadow(rx, y + 4, w, h, r, 14, gLight ? 70 : 120);
        under(SDL_Color{74, 168, 255, 255}, gLight ? 16 : 14, gLight ? 26 : 24);
        e.drawRoundGradD(rx, y, w, h, r, SDL_Color{74, 168, 255, 255}, SDL_Color{8, 86, 190, 255});   // v0.40 Sony blue
        glass(gLight ? 90 : 78); rim(gLight ? 120 : 90);
    } else if (style == 2) {
        e.dropShadow(rx, y + 4, w, h, r, 14, gLight ? 70 : 110);
        under(SDL_Color{255, 190, 60, 255}, gLight ? 16 : 14, gLight ? 26 : 24);
        e.drawRoundGradD(rx, y, w, h, r, SDL_Color{255, 226, 130, 255}, SDL_Color{238, 150, 30, 255});
        glass(gLight ? 90 : 80); rim(gLight ? 120 : 90);
    } else if (style == 3) {
        e.drawRoundGradV(rx, y, w, h, r, SDL_Color{74, 168, 255, 56}, SDL_Color{74, 168, 255, 20});
        e.drawRoundBorder(rx, y, w, h, r, 2, SDL_Color{74, 168, 255, 230});
        glass(gLight ? 40 : 22);
    } else if (style == 4) {
        e.drawRoundGradV(rx, y, w, h, r, SDL_Color{251, 76, 110, 54}, SDL_Color{251, 76, 110, 18});
        e.drawRoundBorder(rx, y, w, h, r, 2, SDL_Color{251, 76, 110, 230});
        glass(gLight ? 40 : 22);
    } else {
        e.dropShadow(rx, y + 3, w, h, r, 10, gLight ? 36 : 70);
        if (gLight) e.drawRoundGradV(rx, y, w, h, r, SDL_Color{255, 255, 255, 255}, SDL_Color{238, 242, 247, 255});
        else        e.drawRoundGradV(rx, y, w, h, r, ink(40), ink(14));
        e.drawRoundBorder(rx, y, w, h, r, 2, ink(gLight ? 44 : 72));
        glass(gLight ? 60 : 30);
    }
    const SDL_Color ink = style == 0 ? SDL_Color{255, 255, 255, 255} : style == 2 ? SDL_Color{44, 26, 2, 255} : (style == 3 ? kGRN2 : (style == 4 ? kERT : kTX));
    e.drawTextCentered(label, rx + w / 2, y + h / 2, px, ink, FontRole::Body, L.rtl);
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
    // placeholder: three finder patterns + dotted modules — rasterized ONCE into a
    // cached image (v0.30: this used to draw ~300 rects every single frame on Plans)
    const int q = s - 2 * pad;
    const std::string nm = "__qr_ph_" + std::to_string(q);
    if (!e.hasImage(nm)) {
        const std::uint8_t kr = 20, kg = 26, kb = 40;
        std::vector<std::uint8_t> px(static_cast<std::size_t>(q) * q * 4, 255);
        const int u = std::max(1, q / 21);
        auto dot = [&](int cx, int cy, int n, std::uint8_t r, std::uint8_t g2, std::uint8_t b2) {
            for (int py = cy * u; py < cy * u + n * u && py < q; ++py)
                for (int pxl = cx * u; pxl < cx * u + n * u && pxl < q; ++pxl) {
                    std::uint8_t* t = &px[(static_cast<std::size_t>(py) * q + pxl) * 4];
                    t[0] = r; t[1] = g2; t[2] = b2; t[3] = 255;
                }
        };
        auto finder = [&](int fx, int fy) {
            dot(fx, fy, 7, kr, kg, kb);
            dot(fx + 1, fy + 1, 5, 255, 255, 255);
            dot(fx + 2, fy + 2, 3, kr, kg, kb);
        };
        finder(0, 0); finder(14, 0); finder(0, 14);
        unsigned h2 = 2166136261u;
        for (int r = 0; r < 21; ++r) for (int c = 0; c < 21; ++c) {
            const bool inFinder = (r < 8 && c < 8) || (r < 8 && c > 12) || (r > 12 && c < 8);
            if (inFinder) continue;
            h2 = h2 * 16777619u ^ static_cast<unsigned>(r * 31 + c);
            if ((h2 >> 7) & 1u) dot(c, r, 1, kr, kg, kb);
        }
        e.addImageRGBA(nm, q, q, px.data());
    }
    e.drawImage(nm, x + pad, y + pad, q, q);
}



} // namespace

// ============================================================================
//  Toast / legend / top navigation
// ============================================================================
void drawToast(UiEngine& e, const std::string& text, const std::string& langCode, int kind) {
    // kind: 0 = success (check) | 1 = error (X, red) | 2 = info (blue 'i')
    Lay L; L.rtl = (langCode == "ar");
    const int tw = e.measureText(text, 28, FontRole::Body, L.rtl);
    const int w = tw + 150;
    const int x = 960 - w / 2, y = 1080 - 130 - 84;
    e.dropShadow(x, y + 6, w, 84, 42, 20, 170);
    e.drawRoundGradV(x, y, w, 84, 42, gLight ? SDL_Color{255, 255, 255, 255} : SDL_Color{21, 25, 30, 255}, gLight ? SDL_Color{246, 248, 250, 255} : SDL_Color{13, 15, 18, 255});
    const SDL_Color ring = kind == 1 ? SDL_Color{224, 82, 82, 180} : kind == 2 ? SDL_Color{74, 168, 255, 180} : SDL_Color{74, 168, 255, 180};
    e.drawRoundBorder(x, y, w, 84, 42, 2, ring);
    e.fillRect(x + 42, y + 2, w - 84, 1, SDL_Color{255, 255, 255, 50});
    const int cx = L.rtl ? x + w - 50 : x + 50;
    const SDL_Color disc = kind == 1 ? SDL_Color{224, 82, 82, 255} : kGRN;
    e.drawCircle(cx, y + 42, 24, disc);
    if (kind == 1) {
        shapeIcon(e, 4, cx - 13, y + 29, 26, kWHT);   // v0.64: errors show X, never a check
    } else if (kind == 2) {
        e.fillRect(cx - 3, y + 31, 6, 6, kWHT);       // info 'i'
        e.fillRect(cx - 3, y + 41, 6, 13, kWHT);
    } else {
        icCheck(e, cx - 13, y + 29, 26, kWHT);
    }
    e.drawText(text, L.rtl ? x + w - 96 : x + 96, y + 22, 28, kTX, FontRole::Body, L.rtl, L.rtl, tw + 10);
}

void drawLegendBar(UiEngine& e, const std::vector<std::pair<std::string, std::string>>& items,
                   const std::string& langCode) {
    Lay L; L.rtl = (langCode == "ar");
    const int lx0 = gRail ? kCL : 96, lw0 = gRail ? kCW : 1728;
    const int hx0 = L.X(lx0, lw0);
    e.fillGradV(gRail ? L.X(kRailW, 1920 - kRailW) : 0, 972, gRail ? 1920 - kRailW : 1920, 108, shade(0), shade(240));
    e.fillGradH(hx0, 990, lw0 / 2, 1, ink(0), ink(24));
    e.fillGradH(hx0 + lw0 / 2, 990, lw0 / 2, 1, ink(24), ink(0));
    // each item: [icon 38][16][label]  gap 44 — laid out LTR, mirrored for RTL
    std::vector<int> widths;
    int total = 0;
    for (const auto& it : items) {
        const std::string label = tr(it.second, langCode);
        const int w = 30 + 12 + e.measureText(label, 21, FontRole::Body, L.rtl);
        widths.push_back(w);
        total += w + 38;
    }
    total -= 38;
    int x = L.rtl ? 1920 - lx0 - total : lx0;
    for (std::size_t i = 0; i < items.size(); ++i) {
        const std::string label = tr(items[i].second, langCode);
        if (!L.rtl) {
            drawBtn(e, items[i].first, x + 15, 1034, 30);
            e.drawText(label, x + 42, 1022, 21, kCHIPTX, FontRole::Body, false, false);
        } else {
            const int lw = e.measureText(label, 21, FontRole::Body, true);
            e.drawText(label, x, 1022, 21, kCHIPTX, FontRole::Body, false, true);
            drawBtn(e, items[i].first, x + lw + 12 + 15, 1034, 30);
        }
        x += widths[i] + 38;
    }
    gRail = false;
}

#include <functional>
// ============================================================================
//  v5 DATA LAYER — everything shown on screen comes from here (set by main.cpp)
// ============================================================================
namespace {
Account gAcc;
std::vector<AddonItem> gCat;
SaleBanner gSale;
std::vector<WalletTx> gTx;
std::vector<PlanInfo> gPlans;
std::string gAppVersion;
std::function<void(const AddonItem&)> gCoverReq;
int gScroll[4] = {0, 0, 0, 0};
int gDetailIdx = -1;
std::time_t gTimeOffset = 0;   // server time - device time (seconds)
int gSeasonOverride = -1;      // testing only

std::time_t nowServer() { return std::time(nullptr) + gTimeOffset; }
int rankOf(Tier t) { return static_cast<int>(t); }
bool isNewItem(const AddonItem& it) { return it.createdAt > 0 && nowServer() - it.createdAt < 14 * 86400; }
bool onSale(const AddonItem& it) { return it.acPrice > 0 && it.acPriceOld > it.acPrice; }
int discountOf(const AddonItem& it) { return onSale(it) ? (100 * (it.acPriceOld - it.acPrice) + it.acPriceOld / 2) / it.acPriceOld : 0; }
void sortNewest(std::vector<AddonItem>& v) {
    std::stable_sort(v.begin(), v.end(), [](const AddonItem& a, const AddonItem& b) { return a.createdAt > b.createdAt; });
}
std::string lower(const std::string& s) {
    std::string o = s;
    for (auto& c : o) if (c >= 'A' && c <= 'Z') c = static_cast<char>(c - 'A' + 'a');
    return o;
}
std::string fmt1(const std::string& pattern, const std::string& a) {
    const auto p = pattern.find("%s");
    return p == std::string::npos ? pattern : pattern.substr(0, p) + a + pattern.substr(p + 2);
}
std::string fmtD(const std::string& pattern, int a) {
    const auto p = pattern.find("%d");
    return p == std::string::npos ? pattern : pattern.substr(0, p) + std::to_string(a) + pattern.substr(p + 2);
}
std::string fmtDD(const std::string& pattern, int a, int b) { return fmtD(fmtD(pattern, a), b); }
std::string fmtPct(int pct) { return "-" + std::to_string(pct) + "%"; }
std::string fmtDate(std::time_t t) {
    if (t <= 0) return "";
    std::tm tmv{};
    gmtime_r(&t, &tmv);
    char b[40];
    std::snprintf(b, sizeof b, "%04d-%02d-%02d", tmv.tm_year + 1900, tmv.tm_mon + 1, tmv.tm_mday);
    return b;
}
std::string fmtSize(long long bytes) {
    if (bytes <= 0) return "";
    char b[24];
    if (bytes >= 1024LL * 1024 * 1024) std::snprintf(b, sizeof b, "%.1f GB", bytes / (1024.0 * 1024 * 1024));
    else if (bytes >= 1024LL * 1024) std::snprintf(b, sizeof b, "%.1f MB", bytes / (1024.0 * 1024));
    else std::snprintf(b, sizeof b, "%lld KB", std::max(1LL, bytes / 1024));
    return b;
}
// 950 -> "950", 12400 -> "12.4K", 24000 -> "24K", 1200000 -> "1.2M"
std::string fmtCompact(long long n) {
    char b[24];
    if (n < 1000) std::snprintf(b, sizeof b, "%lld", std::max(0LL, n));
    else if (n < 1000000) { const double k = n / 1000.0; std::snprintf(b, sizeof b, k < 100 && n % 1000 >= 100 ? "%.1fK" : "%.0fK", k); }
    else { const double m = n / 1000000.0; std::snprintf(b, sizeof b, m < 100 && n % 1000000 >= 100000 ? "%.1fM" : "%.0fM", m); }
    return b;
}
int gStoreSort = 0;   // 0 popular (downloads), 1 newest, 2 top rated
std::string tierName(Tier t, const std::string& lang) {
    return tr(t == Tier::Legendary ? "plan_legendary" : t == Tier::Epic ? "plan_epic" : "plan_free", lang);
}
std::string periodName(const std::string& p, const std::string& lang) {
    return tr(p == "week" ? "per_week" : p == "month" ? "per_month" : "per_day", lang);
}
std::string i18n(const std::map<std::string, std::string>& m, const std::string& lang) {
    auto it = m.find(lang);
    if (it != m.end() && !it->second.empty()) return it->second;
    it = m.find("en");
    return it != m.end() ? it->second : std::string();
}
bool saleActive() {
    if (gSeasonOverride >= 1 && gSeasonOverride <= 4) return true;
    if (gSeasonOverride == 0) return false;
    return gSale.active && (gSale.endsAt == 0 || gSale.endsAt > nowServer());
}
} // namespace

void setAccount(const Account& a) { gAcc = a; if (!gAcc.signedIn) { gAcc = Account{}; } }
const Account& account() { return gAcc; }
void setCatalog(const std::vector<AddonItem>& items) { gCat = items; sortNewest(gCat); }
void upsertCatalogItem(const AddonItem& item) {
    for (auto& it : gCat) if (it.id == item.id) { it = item; sortNewest(gCat); return; }
    gCat.push_back(item);
    sortNewest(gCat);
}
void removeCatalogItem(const std::string& id) {
    gCat.erase(std::remove_if(gCat.begin(), gCat.end(), [&](const AddonItem& a) { return a.id == id; }), gCat.end());
}
const std::vector<AddonItem>& catalog() { return gCat; }
int findItem(const std::string& id) {
    for (std::size_t i = 0; i < gCat.size(); ++i) if (gCat[i].id == id) return static_cast<int>(i);
    return -1;
}
void setSaleBanner(const SaleBanner& s) { gSale = s; }
const SaleBanner& saleBanner() { return gSale; }
void setWalletHistory(const std::vector<WalletTx>& tx) { gTx = tx; }
void setPlans(const std::vector<PlanInfo>& plans) { gPlans = plans; }
void setAppVersion(const std::string& v) { gAppVersion = v; }
void setCoverRequestHandler(std::function<void(const AddonItem&)> fn) { gCoverReq = std::move(fn); }
void setStoreSort(int sort) { gStoreSort = std::max(0, std::min(2, sort)); }
int storeSort() { return gStoreSort; }
void setScroll(View v, int first) { gScroll[static_cast<int>(v)] = std::max(0, first); }
int scrollOf(View v) { return gScroll[static_cast<int>(v)]; }
void setDetailItem(int idx) { gDetailIdx = idx; }
std::string formatAC(int v) {
    const bool neg = v < 0;
    std::string s = std::to_string(neg ? -v : v), o;
    int n = 0;
    for (auto it = s.rbegin(); it != s.rend(); ++it) { if (n && n % 3 == 0) o.insert(o.begin(), ','); o.insert(o.begin(), *it); ++n; }
    return neg ? "-" + o : o;
}

// SCE_SYSTEM_PARAM_LANG_*: 0 ja, 1 en-US, 2 fr, 3 es, 4 de, 5 it, 6 nl, 7 pt-PT, 8 ru, 9 ko, 10 zh-TW, 11 zh-CN,
// 12 fi, 13 sv, 14 da, 15 no, 16 pl, 17 pt-BR, 18 en-GB, 19 tr, 20 es-LA, 21 ar, 22 fr-CA ... -> our 8 languages
std::string langFromSystem(int l) {
    switch (l) {
        case 21: return "ar";
        case 2: case 22: return "fr";
        case 19: return "tr";
        case 3: case 20: return "es";
        case 8: return "ru";
        case 7: case 17: return "pt";
        case 10: case 11: return "zh";
        default: return "en";
    }
}

Access accessFor(const AddonItem& it) {
#ifdef AS_BUILD_2121
    (void)it; return Access::Download;   // 2121 test build: no account needed — always downloadable
#else
    if (!gAcc.signedIn) return Access::NeedSignIn;
    if (it.unlocked || rankOf(gAcc.plan) >= rankOf(it.tier)) return Access::Download;
    return Access::NeedPlan;
#endif
}
bool acUnlockOffered(const AddonItem& it) {
    if (it.tier == Tier::Free || it.acPrice <= 0) return false;
    if (gAcc.signedIn && gAcc.plan == Tier::Legendary) return false;   // Legendary never needs AC
    return accessFor(it) != Access::Download;
}

std::vector<int> searchCatalog(const std::string& query, int filter) {
    std::vector<std::pair<int, int>> scored;   // (score, index)
    std::vector<std::string> words;
    {
        std::string q = lower(query), w;
        for (char c : q) { if (c == ' ') { if (!w.empty()) words.push_back(w); w.clear(); } else w += c; }
        if (!w.empty()) words.push_back(w);
    }
    for (std::size_t i = 0; i < gCat.size(); ++i) {
        const AddonItem& it = gCat[i];
        if (filter == 1 && it.isWorld) continue;
        if (filter == 2 && !it.isWorld) continue;
        if (words.empty()) { scored.push_back({it.downloads, static_cast<int>(i)}); continue; }
        const std::string t = lower(it.title), c = lower(it.creator), k = lower(it.category), d = lower(it.description);
        int score = 0;
        bool all = true;
        for (const auto& w : words) {
            int s = 0;
            if (t.compare(0, w.size(), w) == 0) s = 40;
            else if (t.find(w) != std::string::npos) s = 30;
            else if (c.find(w) != std::string::npos || k.find(w) != std::string::npos) s = 15;
            else if (d.find(w) != std::string::npos) s = 5;
            if (!s) { all = false; break; }
            score += s;
        }
        if (all) scored.push_back({score, static_cast<int>(i)});
    }
    std::stable_sort(scored.begin(), scored.end(), [](const std::pair<int, int>& a, const std::pair<int, int>& b) { return a.first > b.first; });
    std::vector<int> out;
    out.reserve(scored.size());
    for (const auto& s : scored) out.push_back(s.second);
    return out;
}

std::vector<int> viewItems(View v, int filter, const std::string& query) {
    if (v == View::Search) return searchCatalog(query, filter);
    std::vector<int> out;
    for (std::size_t i = 0; i < gCat.size(); ++i) {
        const AddonItem& it = gCat[i];
        bool keep = true;
        if (v == View::Home) {
            switch (filter) {
                case 1: keep = !it.isWorld; break;
                case 2: keep = it.isWorld; break;
                case 3: keep = it.tier == Tier::Free; break;
                case 4: keep = it.tier == Tier::Epic; break;
                case 5: keep = it.tier == Tier::Legendary; break;
                case 6: keep = isNewItem(it); break;
                default: break;
            }
        } else if (v == View::Deals) {
            keep = onSale(it);
            if (keep && filter == 1) keep = !it.isWorld;
            if (keep && filter == 2) keep = it.isWorld;
            if (keep && filter == 3) keep = it.acPrice < 100;
        } else if (v == View::Mine) {
            keep = it.installed;
        } else {   // Store: 0 All, 1 Addons, 2 Worlds, 3 Free, 4 Epic, 5 Legendary, 6 New
            switch (filter) {
                case 1: keep = !it.isWorld; break;
                case 2: keep = it.isWorld; break;
                case 3: keep = it.tier == Tier::Free; break;
                case 4: keep = it.tier == Tier::Epic; break;
                case 5: keep = it.tier == Tier::Legendary; break;
                case 6: keep = isNewItem(it); break;
                default: break;
            }
        }
        if (keep) out.push_back(static_cast<int>(i));
    }
    if (v == View::Store && gStoreSort == 0)   // catalog is already newest-first (sort 1)
        std::stable_sort(out.begin(), out.end(), [](int a, int b) { return gCat[a].downloads > gCat[b].downloads; });
    if (v == View::Store && gStoreSort == 2)
        std::stable_sort(out.begin(), out.end(), [](int a, int b) {
            const float ra = gCat[a].ratingCount > 0 ? gCat[a].rating : -1.f, rb = gCat[b].ratingCount > 0 ? gCat[b].rating : -1.f;
            return ra > rb; });
    if (v == View::Deals && filter == 4)
        std::stable_sort(out.begin(), out.end(), [](int a, int b) { return discountOf(gCat[a]) > discountOf(gCat[b]); });
    return out;
}
std::vector<int> homeHeroItems() {
    std::vector<int> out;
    for (std::size_t i = 0; i < gCat.size() && out.size() < 5; ++i) if (gCat[i].featured) out.push_back(static_cast<int>(i));
    if (out.empty() && !gCat.empty()) out.push_back(0);   // newest item
    return out;
}
std::vector<int> homeExclusiveItems() {
    std::vector<int> out;
    for (std::size_t i = 0; i < gCat.size(); ++i) if (gCat[i].exclusive) out.push_back(static_cast<int>(i));
    std::stable_sort(out.begin(), out.end(), [](int a, int b) {
        const int sa = gCat[a].exclusiveSlot > 0 ? gCat[a].exclusiveSlot : 99, sb = gCat[b].exclusiveSlot > 0 ? gCat[b].exclusiveSlot : 99;
        return sa < sb;
    });
    if (out.size() > 3) out.resize(3);
    return out;
}
// Home hero carousel: [-1 = the sale slide when a sale is live] + featured items + exclusives (no duplicates).
std::vector<int> homeSlides() {
    std::vector<int> out;
    if (saleActive()) out.push_back(-1);
    auto add = [&](int i) { if (std::find(out.begin(), out.end(), i) == out.end()) out.push_back(i); };
    for (std::size_t i = 0; i < gCat.size() && out.size() < 6; ++i) if (gCat[i].featured) add(static_cast<int>(i));
    for (int i : homeExclusiveItems()) add(i);
    if (out.empty() || (out.size() == 1 && out[0] == -1)) { if (!gCat.empty()) add(0); }   // newest item
    return out;
}
bool saleLive() { return saleActive(); }
int homeSlideAt(int heroIndex) {   // -1 sale slide, -2 nothing (empty catalog), else catalog index
    const std::vector<int> s = homeSlides();
    if (s.empty()) return -2;
    const int n = static_cast<int>(s.size());
    return s[static_cast<std::size_t>(((heroIndex % n) + n) % n)];
}

// ---- server-driven sales (Season API kept for old code / testing) ---------------------------
namespace {
long long daysFromCivil(long long y, unsigned m, unsigned d) {
    y -= m <= 2;
    const long long era = (y >= 0 ? y : y - 399) / 400;
    const unsigned yoe = static_cast<unsigned>(y - era * 400);
    const unsigned doy = (153 * (m + (m > 2 ? -3 : 9)) + 2) / 5 + d - 1;
    const unsigned doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
    return era * 146097 + static_cast<long long>(doe) - 719468;
}
Season seasonFromTheme(const std::string& t) {
    if (t == "winter") return Season::Winter;
    if (t == "spring") return Season::Spring;
    if (t == "summer") return Season::Summer;
    if (t == "autumn" || t == "fall") return Season::Autumn;
    return Season::None;
}
} // namespace
Season seasonForDate(int month, int day) {
    struct W { Season s; int a, b; };
    static const W kW[4] = {{Season::Winter, 125, 215}, {Season::Spring, 415, 505}, {Season::Summer, 725, 815}, {Season::Autumn, 1015, 1105}};
    const int c = month * 100 + day;
    for (const auto& w : kW) if (c >= w.a && c <= w.b) return w.s;
    (void)daysFromCivil;
    return Season::None;
}
void setServerTime(std::time_t serverUtc) { gTimeOffset = serverUtc ? serverUtc - std::time(nullptr) : 0; }
void setSeasonOverride(int s) { gSeasonOverride = s; }
Season currentSeason() {
    if (gSeasonOverride >= 0) return static_cast<Season>(gSeasonOverride);
    return saleActive() ? seasonFromTheme(gSale.theme) : Season::None;
}
bool seasonCountdown(int& days, int& hours, int& minutes) {
    if (!saleActive()) return false;
    if (gSeasonOverride >= 1) { days = 3; hours = 12; minutes = 45; return true; }   // test preview only
    if (gSale.endsAt <= 0) return false;
    const long long rem = std::max<long long>(0, gSale.endsAt - nowServer());
    days = static_cast<int>(rem / 86400); hours = static_cast<int>(rem % 86400 / 3600); minutes = static_cast<int>(rem % 3600 / 60);
    return true;
}

// ============================================================================
//  v5 drawing helpers
// ============================================================================
namespace {
const char* seasonKeyOf(Season s) {
    switch (s) { case Season::Winter: return "winter"; case Season::Spring: return "spring";
                 case Season::Summer: return "summer"; case Season::Autumn: return "autumn"; default: return ""; }
}
const char* seasonTitleKeyOf(Season s) {
    switch (s) { case Season::Winter: return "sale_winter"; case Season::Spring: return "sale_spring";
                 case Season::Summer: return "sale_summer"; default: return "sale_autumn"; }
}
// Sale artwork: server image if loaded, else bundled bn_<theme>_<kind>. Empty = no art.
// Full-screen page art with a fallback (e.g. bg_cave -> bg_welcome). Never fails: returns the fallback name.
const char* pageArt(UiEngine& e, const char* primary, const char* fallback) {
    return e.hasImage(primary) ? primary : fallback;
}
// PERFORMANCE: a page background made of full-screen art + several shade layers is composed ONCE per key
// (screen / item / art / theme / direction) and kept as one opaque image "__bg_page"; every later frame is a
// single straight copy (memcpy fast path) instead of ~4 full-screen alpha blends.
static bool gBakeFade = false;   // wrappers at pkgtest scope (link!)

void bakedBackdrop(UiEngine& e, const std::string& key, const std::function<void()>& paint) {
    // v0.38: 3-slot LRU + smooth crossfade — revisits are instant, banner
    // changes glide (old art under, new art fading in over 240ms).
    struct Slot { std::string key; Uint32 at = 0; };
    static Slot slot[8];   // v0.53 SPEED: 8 slots = every recent screen is a hit
    static int cur = -1, prev = -1;
    static Uint32 fadeAt = 0;
    const std::string want = key + (gLight ? "|L" : "|D");
    int found = -1;
    for (int i = 0; i < 8; ++i) if (slot[i].key == want) { found = i; break; }
    if (found < 0) {
        int tgt = -1;
        for (int i = 0; i < 8; ++i) if (i != cur && slot[i].key.empty()) { tgt = i; break; }
        if (tgt < 0) for (int i = 0; i < 8; ++i) if (i != cur && (tgt < 0 || slot[i].at < slot[tgt].at)) tgt = i;
        if (tgt < 0) tgt = (cur + 1) % 8;
        e.clear(kBG);
        paint();
        const auto& fb = e.framebuffer();
        std::vector<std::uint8_t> rgba(fb.size() * 4);
        for (std::size_t i = 0; i < fb.size(); ++i) {
            const std::uint32_t px = fb[i];
            rgba[i * 4] = static_cast<std::uint8_t>(px >> 16); rgba[i * 4 + 1] = static_cast<std::uint8_t>(px >> 8);
            rgba[i * 4 + 2] = static_cast<std::uint8_t>(px); rgba[i * 4 + 3] = 255;
        }
        e.addImageRGBA("__bg_" + std::to_string(tgt), 1920, 1080, rgba.data());
        slot[tgt].key = want;
        slot[tgt].at = SDL_GetTicks();
        prev = (cur >= 0 && cur != tgt) ? cur : -1;
        cur = tgt;
        fadeAt = SDL_GetTicks();
        found = tgt;
    } else {
        if (found != cur) { prev = cur; cur = found; fadeAt = SDL_GetTicks(); }
        slot[found].at = SDL_GetTicks();
    }
    const Uint32 el = SDL_GetTicks() - fadeAt;
    const Uint32 DUR = 240;
    const std::string cn = "__bg_" + std::to_string(cur);
    gBakeFade = false;
    if (prev >= 0 && el < DUR && !slot[prev].key.empty()) {
        gBakeFade = true;
        e.drawImage("__bg_" + std::to_string(prev), 0, 0, 1920, 1080);
        float t = (float)el / (float)DUR;
        t = t * t * (3.f - 2.f * t);   // smoothstep — the glide
        e.drawImage(cn, 0, 0, 1920, 1080, (Uint8)(t * 255.f));
    } else {
        e.drawImage(cn, 0, 0, 1920, 1080);
    }
}
// Sale art as the full-screen backdrop (same look as the featured hero). 16:9 art fills 1920x1080 exactly —
// nothing is cut. Taller art (16:10) is fitted to the screen width and anchored at the top (only the very bottom
// edge, which sits under the dark shelf area, goes off-screen). Wider art fades into the page at its bottom edge.
// shiftX moves the art sideways (e.g. to keep a baked-in logo clear of the exclusives column); the uncovered strip
// is filled with a soft blurred copy of the same art.
void saleBleed(UiEngine& e, const std::string& art, int shiftX = 0) {
    int iw = 16, ih = 9;
    if (!e.imageSize(art, iw, ih) || iw <= 0 || ih <= 0) { iw = 16; ih = 9; }
    const int h = static_cast<int>(1920LL * ih / iw);
    if (shiftX != 0) {
        const std::string bl = art + "#blur";
        if (!e.hasImage(bl)) e.makeBlurred(art, bl);
        coverImage(e, bl, 0, 0, 1920, 1080, 0, 0.5f, 0.5f);
    }
    if (h >= 1080) {
        coverImage(e, art, shiftX, 0, 1920, 1080, 0, 0.5f, 0.0f);
        if (shiftX < 0) e.fillGradH(1920 + shiftX - 120, 0, 120, 1080, shade(0), shade(90));
        if (shiftX > 0) e.fillGradH(shiftX, 0, 120, 1080, shade(90), shade(0));
    } else {
        coverImage(e, art, shiftX, 0, 1920, h, 0, 0.5f, 0.5f);
        e.fillGradV(0, h - 240, 1920, 240, shade(0), shade(255));
    }
}
// Sale art: server cover_url first, then ONE bundled image per season ("bn_<season>", shown whole everywhere),
// then the old per-layout crops ("bn_<season>_<hero|wide|strip>") for packs that still have them.
std::string saleArt(UiEngine& e, const char* kind) {
    int w = 0, h = 0;
    if (gSeasonOverride < 1 && !gSale.art.empty() && e.imageSize(gSale.art, w, h)) return gSale.art;
    const std::string season = std::string("bn_") + seasonKeyOf(currentSeason());
    if (e.hasImage(season)) return season;
    const std::string b = season + "_" + kind;
    return e.imageSize(b, w, h) ? b : std::string();
}
// Store strip art (wide ~5.25:1 image): server stripArt -> bundled "bn_<season>_strip" -> the normal sale art.
std::string stripArt(UiEngine& e) {
    int w = 0, h = 0;
    if (gSeasonOverride < 1 && !gSale.stripArt.empty() && e.imageSize(gSale.stripArt, w, h)) return gSale.stripArt;
    const std::string s = std::string("bn_") + seasonKeyOf(currentSeason()) + "_strip";
    if (e.imageSize(s, w, h)) return s;
    return saleArt(e, "strip");
}
// Does the artwork already show the sale title? (then no title is drawn over it)
bool saleArtHasTitle(const std::string& art) {
    if (art == "bn_summer") return true;                                         // "SUMMER SALE" ribbon in the art
    if (art.compare(0, 10, "bn_spring_") == 0 || art.compare(0, 10, "bn_summer_") == 0) return true;   // old v2 crops
    if (art.compare(0, 3, "bn_") == 0) return false;
    return gSale.artHasTitle;
}
std::string saleTitle(const std::string& lang) {
    if (gSeasonOverride < 1) { const std::string t = i18n(gSale.title, lang); if (!t.empty()) return t; }
    return currentSeason() != Season::None ? tr(seasonTitleKeyOf(currentSeason()), lang) : std::string();
}
std::string saleSubtitle(const std::string& lang) {
    if (gSeasonOverride >= 1) return tr("up_to_50", lang);
    return i18n(gSale.subtitle, lang);
}
std::string saleKicker(const std::string& lang) {
    if (gSeasonOverride < 1) { const std::string k = i18n(gSale.kicker, lang); if (!k.empty()) return k; }
    return tr("sale_k", lang);
}
int saleDiscount() { return gSeasonOverride >= 1 ? 50 : gSale.discountPercent; }

// Item cover: real image when loaded, otherwise a calm placeholder (and ask main.cpp to fetch it).
void itemCover(UiEngine& e, const AddonItem& it, int x, int y, int w, int h, int r, bool fitOnly = false) {
    int iw = 0, ih = 0;
    if (!it.cover.empty() && e.imageSize(it.cover, iw, ih)) {
        if (fitOnly) {
            // v0.39 (user order): the COMPLETE cover, only scaled down, inside the card
            // slot. v0.58 (user pick «خلفية ضبابية»): a dimmed copy of the cover fills the
            // slot behind it — wide art leaves soft glow instead of flat gap bars.
            e.drawRoundRect(x, y, w, h, r, kSF2);
            e.drawImageRounded(it.cover, x, y, w, h, r, 90);
            e.drawRoundRect(x, y, w, h, r, SDL_Color{8, 9, 11, 120});
            int cw = w, ch = static_cast<int>(static_cast<long long>(w) * ih / iw);
            if (ch > h) { ch = h; cw = static_cast<int>(static_cast<long long>(h) * iw / ih); }
            // v0.59 (user: «الصورة نازلة شوي عن المكان محدد»): flush to the TOP of the slot —
            // 16:9 covers fill the marked area exactly; wider art glows softly below, never drops.
            e.drawImageRounded(it.cover, x + (w - cw) / 2, y, cw, ch, std::min(r, 12));
            return;
        }
        // v0.36 (user request): covers are NEVER cropped — the WHOLE cover always shows,
        // centred over a dimmed fill of itself, so nothing baked into the art is ever cut.
        e.drawImageRounded(it.cover, x, y, w, h, r, 90);
        e.drawRoundRect(x, y, w, h, r, SDL_Color{8, 9, 11, 120});
        int cw = w, ch = static_cast<int>(static_cast<long long>(w) * ih / iw);
        if (ch > h) { ch = h; cw = static_cast<int>(static_cast<long long>(h) * iw / ih); }
        e.drawImageRounded(it.cover, x + (w - cw) / 2, y + (h - ch) / 2, cw, ch, std::min(r, 12));
        return;
    }
    if (gCoverReq) gCoverReq(it);
    const SDL_Color acc = it.tier == Tier::Legendary ? SDL_Color{226, 160, 40, 255} : it.tier == Tier::Epic ? kEPIC : kBLl;
    e.drawRoundGradD(x, y, w, h, r, SDL_Color{acc.r, acc.g, acc.b, 70}, kSF2);
    const int s = std::min(96, std::min(w, h) * 30 / 100);   // v0.38: never a giant ghost glyph at hero size
    glyph(e, it.isWorld ? 0 : 12, x + (w - s) / 2, y + (h - s) / 2, s, ink(70), ink(70), false);
}

// Image shown WHOLE inside (x,y,maxW,maxH) — object-fit: contain. The frame takes the image aspect
// (so nothing is cropped and no letterbox bars are drawn). lead = align to the leading edge.
// Returns the drawn rect via outX/outW/outH.
// fill = true: the whole box (maxW x image height) gets a soft blurred copy of the art behind it, so a
// narrow image (e.g. 16:9 in a wide banner) still feels full-width — and the art itself stays uncropped.
[[maybe_unused]] void containArt(UiEngine& e, const Lay& L, const std::string& name, int xl, int y, int maxW, int maxH, int r, bool lead,
                int& outXl, int& outW, int& outH, bool shadow = true, bool fill = false, int fillW = 0) {
    int iw = 16, ih = 9;
    if (!e.imageSize(name, iw, ih) || iw <= 0 || ih <= 0) { iw = 16; ih = 9; }
    int w = maxW, h = static_cast<int>(static_cast<long long>(maxW) * ih / iw);
    if (h > maxH) { h = maxH; w = static_cast<int>(static_cast<long long>(maxH) * iw / ih); }
    const int x = lead ? xl : xl + (maxW - w) / 2;
    const int rx = L.X(x, w);
    const int bw = fillW > 0 ? fillW : maxW;
    if (fill && w < bw - 8) {
        const std::string bl = name + "#blur";
        if (!e.hasImage(bl)) e.makeBlurred(name, bl);
        const int brx = L.X(xl, bw);
        if (shadow) e.dropShadow(brx, y + 8, bw, h, r, 18, gLight ? 50 : 140);
        e.drawImageRounded(bl, brx, y, bw, h, r);
        e.drawRoundRect(brx, y, bw, h, r, gLight ? SDL_Color{255, 255, 255, 70} : SDL_Color{8, 9, 11, 120});
        shadow = false;
    }
    if (shadow) e.dropShadow(rx, y + 8, w, h, r, 18, 140);
    e.drawImageRounded(name, rx, y, w, h, r);
    e.drawRoundBorder(rx, y, w, h, r, 2, ink(26));
    outXl = x; outW = w; outH = h;
}

// ---- plan icons (outline style, like the rest of the UI) -------------------------------------
//  Free = block (cube) · Epic = diamond · Legendary = crown with jewels. Each tier has its own shape.
void tierGlyph(UiEngine& e, Tier tier, int x, int y, int s, SDL_Color c) {
    const float X = static_cast<float>(x), Y = static_cast<float>(y), S = static_cast<float>(s);
    auto P = [&](float fx, float fy, float gx, float gy, int t) { seg(e, X + fx * S, Y + fy * S, X + gx * S, Y + gy * S, t, c); };
    const int t = std::max(2, s / 11);
    if (tier == Tier::Free) {            // isometric block
        const float v[6][2] = {{0.5f, 0.08f}, {0.88f, 0.29f}, {0.88f, 0.71f}, {0.5f, 0.92f}, {0.12f, 0.71f}, {0.12f, 0.29f}};
        for (int i = 0; i < 6; ++i) P(v[i][0], v[i][1], v[(i + 1) % 6][0], v[(i + 1) % 6][1], t);
        P(0.12f, 0.29f, 0.5f, 0.5f, t); P(0.88f, 0.29f, 0.5f, 0.5f, t); P(0.5f, 0.5f, 0.5f, 0.92f, t);
    } else if (tier == Tier::Epic) {     // cut diamond
        const float v[5][2] = {{0.28f, 0.16f}, {0.72f, 0.16f}, {0.92f, 0.4f}, {0.5f, 0.9f}, {0.08f, 0.4f}};
        for (int i = 0; i < 5; ++i) P(v[i][0], v[i][1], v[(i + 1) % 5][0], v[(i + 1) % 5][1], t);
        P(0.08f, 0.4f, 0.92f, 0.4f, t);
        P(0.36f, 0.4f, 0.5f, 0.16f, std::max(2, t - 1)); P(0.5f, 0.16f, 0.64f, 0.4f, std::max(2, t - 1));
        P(0.36f, 0.4f, 0.5f, 0.9f, std::max(2, t - 1)); P(0.64f, 0.4f, 0.5f, 0.9f, std::max(2, t - 1));
    } else {                             // crown + jewels
        const float v[7][2] = {{0.12f, 0.72f}, {0.12f, 0.34f}, {0.32f, 0.52f}, {0.5f, 0.24f}, {0.68f, 0.52f}, {0.88f, 0.34f}, {0.88f, 0.72f}};
        for (int i = 0; i < 6; ++i) P(v[i][0], v[i][1], v[i + 1][0], v[i + 1][1], t);
        P(0.12f, 0.72f, 0.88f, 0.72f, t);
        P(0.16f, 0.87f, 0.84f, 0.87f, t);
        disc(e, X + 0.12f * S, Y + 0.25f * S, S * 0.065f, c);
        disc(e, X + 0.5f * S, Y + 0.13f * S, S * 0.075f, c);
        disc(e, X + 0.88f * S, Y + 0.25f * S, S * 0.065f, c);
        disc(e, X + 0.5f * S, Y + 0.58f * S, S * 0.06f, c);
    }
}
// Plan icon tile (Plans page): tinted glass squircle + accent strokes
void tierTile(UiEngine& e, Tier tier, int tx, int ty, int ts, SDL_Color acc) {
    const int r = ts * 26 / 100;
    e.drawRoundGradV(tx, ty, ts, ts, r, SDL_Color{acc.r, acc.g, acc.b, 70}, SDL_Color{acc.r, acc.g, acc.b, 20});
    e.drawRoundBorder(tx, ty, ts, ts, r, 2, SDL_Color{acc.r, acc.g, acc.b, 130});
    const int is = ts * 60 / 100;
    const SDL_Color ink = gLight ? SDL_Color{static_cast<Uint8>(acc.r * 70 / 100), static_cast<Uint8>(acc.g * 70 / 100), static_cast<Uint8>(acc.b * 70 / 100), 255}
                                 : SDL_Color{static_cast<Uint8>(std::min(255, acc.r + 40)), static_cast<Uint8>(std::min(255, acc.g + 40)), static_cast<Uint8>(std::min(255, acc.b + 40)), 255};
    tierGlyph(e, tier, tx + (ts - is) / 2, ty + (ts - is) / 2, is, ink);
}

// Tier chip (Free / Epic / Legendary). Returns its width. xl = LTR left edge. Chips >= 34 px tall also show the plan icon.
// ---- v6.7: icon + text groups centred EXACTLY in their box ---------------------------------------------
// Widths come from the glyph ink (no bitmap padding / side bearings) and every piece is centred on the same
// line cy, so "LEGENDARY", "NEW", "1,250 AC", "Ends in 03 days…" etc. sit in the middle of their pill.
struct GPiece {
    int w = 0, gap = 0;                       // width of the piece, space after it (logical order)
    std::string text; int px = 0; FontRole role = FontRole::Body; SDL_Color c{}; bool ar = false; int dy = 0;
    std::function<void(int, int)> icon;       // draws an icon whose box is (x, y, w, ih)
    int ih = 0;
};
GPiece gText(UiEngine& e, const std::string& t, int px, SDL_Color c, bool ar, int gap = 0, FontRole role = FontRole::Body) {
    GPiece p; p.text = t; p.px = px; p.c = c; p.ar = ar; p.role = role; p.gap = gap; p.w = e.inkWidth(t, px, role, ar);
    return p;
}
GPiece gIcon(int w, int h, std::function<void(int, int)> fn, int gap = 0) {
    GPiece p; p.w = w; p.ih = h; p.icon = std::move(fn); p.gap = gap;
    return p;
}
int groupW(const std::vector<GPiece>& g) {
    int w = 0;
    for (std::size_t i = 0; i < g.size(); ++i) w += g[i].w + (i + 1 < g.size() ? g[i].gap : 0);
    return w;
}
// Draws the group with its visual left edge at x (screen coords). RTL = pieces in reverse visual order.
void drawGroupAt(UiEngine& e, const Lay& L, const std::vector<GPiece>& g, int x, int cy) {
    const int n = static_cast<int>(g.size());
    for (int k = 0; k < n; ++k) {
        const int i = L.rtl ? n - 1 - k : k;
        const GPiece& p = g[static_cast<std::size_t>(i)];
        if (p.icon) p.icon(x, cy - p.ih / 2);
        else if (!p.text.empty()) e.drawTextCentered(p.text, x + p.w / 2, cy + p.dy, p.px, p.c, p.role, p.ar);
        x += p.w;
        if (k + 1 < n) x += L.rtl ? g[static_cast<std::size_t>(i - 1)].gap : p.gap;
    }
}
void drawGroupCentered(UiEngine& e, const Lay& L, const std::vector<GPiece>& g, int cx, int cy) {
    drawGroupAt(e, L, g, cx - groupW(g) / 2, cy);
}

int tierChip(UiEngine& e, const Lay& L, int xl, int y, Tier t, const std::string& lang, int h = 30, bool draw = true) {
    const std::string s = t == Tier::Free ? tr("chip_free", lang) : (t == Tier::Epic ? "EPIC" : "LEGENDARY");
    const int px = h * 54 / 100;
    const bool icon = h >= 34;
    const int is = h * 56 / 100;
    const bool ar = L.rtl && t == Tier::Free;
    const int tw = e.inkWidth(s, px, FontRole::Body, ar);
    const int padX = std::max(10, h * 40 / 100);   // same space on both sides (v6.7)
    const int w = padX + (icon ? is + 8 : 0) + tw + padX;
    if (!draw) return w;
    const int rx = L.X(xl, w);
    SDL_Color tc;
    if (t == Tier::Free) {
        e.drawRoundRect(rx, y, w, h, h / 3, SDL_Color{74, 168, 255, 40});
        e.drawRoundBorder(rx, y, w, h, h / 3, 1, SDL_Color{74, 168, 255, 110});
        tc = gLight ? SDL_Color{10, 85, 160, 255} : kBLl;
    } else if (t == Tier::Epic) {
        e.drawRoundGradH(rx, y, w, h, h / 3, SDL_Color{150, 104, 255, 255}, SDL_Color{110, 66, 230, 255});
        tc = kWHT;
    } else {
        e.drawRoundGradH(rx, y, w, h, h / 3, SDL_Color{255, 214, 110, 255}, SDL_Color{226, 160, 40, 255});
        tc = SDL_Color{42, 23, 0, 255};
    }
    // v6.7: icon + text centred together as one group (exact, both directions)
    std::vector<GPiece> g;
    if (icon) g.push_back(gIcon(is, is, [&](int gx, int gy) { tierGlyph(e, t, gx, gy, is, tc); }, 8));
    g.push_back(gText(e, s, px, tc, ar));
    drawGroupCentered(e, L, g, rx + w / 2, y + h / 2);
    return w;
}

// ---- anti-aliased line icons (settings) -----------------------------------------------------
// Lucide-style outline icons described in a 24x24 design grid (segments, arcs, filled discs) and
// rasterised ONCE per kind/size/colour into an RGBA image ("__ln_<kind>_<size>_<rgb>"); every later
// frame is a single cached drawImage, so they cost nothing at 60 fps.
//   kinds: 0 globe (language), 1 contrast (appearance), 2 user (account), 3 gamepad (auto-launch),
//          4 info (about), 5 log-out (sign out)
struct LnPrim { int t; float a, b, c, d, e5, f; };   // t: 0 seg(x0,y0,x1,y1) 1 arc(cx,cy,r,deg0,deg1) 2 disc(cx,cy,r) 3 right half-disc
const std::vector<LnPrim>& lineIconPrims(int kind) {
    static std::vector<LnPrim> k[6];
    static bool init = false;
    if (!init) {
        init = true;
        auto S = [](float x0, float y0, float x1, float y1) { return LnPrim{0, x0, y0, x1, y1, 0, 0}; };
        auto A = [](float cx, float cy, float r, float d0, float d1) { return LnPrim{1, cx, cy, r, d0, d1, 0}; };
        auto D = [](float cx, float cy, float r) { return LnPrim{2, cx, cy, r, 0, 0, 0}; };
        k[0] = {A(12, 12, 10, 0, 360), S(2, 12, 22, 12), A(1.5f, 12, 14.5f, -43.6f, 43.6f), A(22.5f, 12, 14.5f, 136.4f, 223.6f)};
        k[1] = {A(12, 12, 10, 0, 360), LnPrim{3, 12, 12, 10, 0, 0, 0}};
        k[2] = {A(12, 7, 4, 0, 360), S(19, 21, 19, 19), A(15, 19, 4, -90, 0), S(15, 15, 9, 15), A(9, 19, 4, 180, 270), S(5, 19, 5, 21)};
        k[3] = {S(6, 11, 10, 11), S(8, 9, 8, 13), D(15, 12, 1.1f), D(18, 10, 1.1f),
                S(6.68f, 5, 17.32f, 5), A(17.32f, 9, 4, -90, -5), S(21.3f, 8.65f, 22, 16), A(19, 16, 3, 0, 90),
                S(19, 19, 17, 18), S(17, 18, 15.6f, 16.6f), S(15.6f, 16.6f, 14.17f, 16), S(14.17f, 16, 9.83f, 16),
                S(9.83f, 16, 8.4f, 16.6f), S(8.4f, 16.6f, 7, 18), S(7, 18, 5, 19), A(5, 16, 3, 90, 180),
                S(2, 16, 2.7f, 8.65f), A(6.68f, 9, 4, 185, 270)};
        k[4] = {A(12, 12, 10, 0, 360), S(12, 16, 12, 12), D(12, 8, 1.15f)};
        k[5] = {S(9, 21, 5, 21), A(5, 19, 2, 90, 180), S(3, 19, 3, 5), A(5, 5, 2, 180, 270), S(5, 3, 9, 3),
                S(16, 17, 21, 12), S(21, 12, 16, 7), S(21, 12, 9, 12)};
    }
    return k[std::max(0, std::min(5, kind))];
}
void lineIcon(UiEngine& e, int kind, int x, int y, int s, SDL_Color c, float angle = 0.0f, int dx = 0, int dy = 0) {
    char nm[48];
    std::snprintf(nm, sizeof nm, "__ln_%d_%d_%02x%02x%02x", kind, s, c.r, c.g, c.b);
    if (!e.hasImage(nm)) {
        const auto& prims = lineIconPrims(kind);
        const float u = 24.f / s;                // design units per pixel
        const float hw = std::max(0.9f, s / 24.f);   // stroke = 2/24 of the icon size, like lucide
        std::vector<std::uint8_t> px(static_cast<std::size_t>(s) * s * 4, 0);
        for (int py = 0; py < s; ++py)
            for (int pxl = 0; pxl < s; ++pxl) {
                const float X = (pxl + 0.5f) * u, Y = (py + 0.5f) * u;
                float cov = 0.f;
                for (const auto& p : prims) {
                    float d;   // distance in px
                    if (p.t == 0) {
                        const float dx = p.c - p.a, dy = p.d - p.b, l2 = dx * dx + dy * dy;
                        float t = l2 > 0 ? ((X - p.a) * dx + (Y - p.b) * dy) / l2 : 0.f;
                        t = std::max(0.f, std::min(1.f, t));
                        d = std::hypot(X - (p.a + t * dx), Y - (p.b + t * dy)) / u;
                        cov = std::max(cov, std::max(0.f, std::min(1.f, hw + 0.5f - d)));
                    } else if (p.t == 1) {
                        const float vx = X - p.a, vy = Y - p.b;
                        float ang = std::atan2(vy, vx) * 57.29578f;
                        bool in = p.e5 - p.d >= 359.f;
                        for (int w = -1; w <= 1 && !in; ++w) { const float a2 = ang + 360.f * w; in = a2 >= p.d && a2 <= p.e5; }
                        if (in) d = std::fabs(std::hypot(vx, vy) - p.c) / u;
                        else {
                            const float r0 = p.d / 57.29578f, r1 = p.e5 / 57.29578f;
                            d = std::min(std::hypot(X - (p.a + p.c * std::cos(r0)), Y - (p.b + p.c * std::sin(r0))),
                                         std::hypot(X - (p.a + p.c * std::cos(r1)), Y - (p.b + p.c * std::sin(r1)))) / u;
                        }
                        cov = std::max(cov, std::max(0.f, std::min(1.f, hw + 0.5f - d)));
                    } else {
                        const float sd = (std::hypot(X - p.a, Y - p.b) - p.c) / u;   // signed edge distance
                        float cv = std::max(0.f, std::min(1.f, 0.5f - sd));
                        if (p.t == 3) cv *= std::max(0.f, std::min(1.f, (X - p.a) / u + 0.5f));
                        cov = std::max(cov, cv);
                    }
                }
                std::uint8_t* q = &px[(static_cast<std::size_t>(py) * s + pxl) * 4];
                q[0] = c.r; q[1] = c.g; q[2] = c.b; q[3] = static_cast<std::uint8_t>(cov * c.a + 0.5f);
            }
        e.addImageRGBA(nm, s, s, px.data());
    }
    if (angle == 0.0f) e.drawImage(nm, x + dx, y + dy, s, s);
    else e.drawImageRotated(nm, x + s / 2 + dx, y + s / 2 + dy, s, s, angle, 255);
}
// v7.4 — Settings tile: colour-gradient glass square (glow underneath, gloss on top, accent rim) with a white line icon.
// When `focused` the tile glows/pulses and the icon moves: globe + contrast spin, user floats, gamepad and info sway,
// log-out slides. Everything is cached images + a few rounded rects, so it stays cheap on the PS4.
void lineIconTile(UiEngine& e, int kind, int x, int y, int s, SDL_Color acc, bool focused = false) {
    animPush({AnimCall::Tile, kind, x, y, s, focused ? 1 : 0, 0, acc}, x - 12, y - 12, s + 24, s + 24);
    // v0.35: the settings icons are STATIC — no spin / float / sway and no pulsing glow
    // (user: «ايقونات الاعدات لا اريدها متحركة»). Focused tile = steady soft halo only.
    const int r = s * 28 / 100;
    auto dark = [&](int k) { return SDL_Color{static_cast<Uint8>(acc.r * k / 100), static_cast<Uint8>(acc.g * k / 100), static_cast<Uint8>(acc.b * k / 100), 255}; };
    // soft glow under / around the tile
    if (focused) {
        e.drawRoundRect(x - 8, y - 4, s + 16, s + 14, r + 8, SDL_Color{acc.r, acc.g, acc.b, 18});
        e.drawRoundRect(x - 4, y - 2, s + 8, s + 8, r + 4, SDL_Color{acc.r, acc.g, acc.b, 40});
    } else {
        e.drawRoundRect(x - 2, y + 3, s + 4, s + 4, r + 2, SDL_Color{acc.r, acc.g, acc.b, static_cast<Uint8>(gLight ? 20 : 18)});
    }
    e.drawRoundGradD(x, y, s, s, r, SDL_Color{acc.r, acc.g, acc.b, static_cast<Uint8>(gLight ? 205 : 160)},
                     SDL_Color{dark(62).r, dark(62).g, dark(62).b, static_cast<Uint8>(gLight ? 170 : 90)});
    e.drawRoundBorder(x, y, s, s, r, 2, SDL_Color{acc.r, acc.g, acc.b, static_cast<Uint8>(focused ? 230 : 175)});
    if (s > 24) e.drawRoundGradV(x + 2, y + 2, s - 4, s / 2 - 2, std::max(2, r - 2), SDL_Color{255, 255, 255, 70}, SDL_Color{255, 255, 255, 0});
    const int is = s * 54 / 100;
    const int ix = x + (s - is) / 2, iy = y + (s - is) / 2;
    const float ang = 0.0f; const int dx = 0, dy = 0;   // static icons (v0.35)
    const SDL_Color sh{dark(30).r, dark(30).g, dark(30).b, 255};
    lineIcon(e, kind, ix, iy + 1, is, sh, ang, dx, dy);                         // drop shadow of the icon
    lineIcon(e, kind, ix, iy, is, SDL_Color{255, 255, 255, 255}, ang, dx, dy);  // white icon
}

// v5 card: cover (16:9), title, optional creator, tier chip (+ rating / downloads). No AC price on cards.
// v0.65 («نفس ذا»): the mockup card = cover + badges + title + tier pill (rating/stats live on the detail page).
int vcardHeight(int imgH, bool creator) { (void)creator; return imgH + 12 + 26 + 34 + 12; }
void vcard(UiEngine& e, const Lay& L, int xl, int y, int w, int imgH, const AddonItem& it, bool focused,
           const std::string& lang, bool creator, bool stats, bool dealBadge) {
    (void)creator; (void)stats;
    const int h = vcardHeight(imgH, creator);
    const int rx = L.X(xl, w), r = 18;
    e.dropShadow(rx, y + 6, w, h, r, 14, focused ? 150 : (gLight ? 40 : 90));
    const SDL_Color bg = focused ? kSF2 : kSF;
    e.drawRoundRect(rx, y, w, h, r, bg);
    itemCover(e, it, rx, y, w, imgH, r, true);   // v0.59: cover sits IN its marked slot (was imgH+r -> looked dropped)
    e.fillRect(rx, y + imgH, w, r, bg);
    e.drawRoundBorder(rx, y, w, h, r, 2, focused ? ink(40) : kBD);
    // ---- cover badges (v0.36 marketplace look): sale % top-left, NEW top-right, type tag bottom-left ----
    if (dealBadge && onSale(it)) {
        const std::string b = fmtPct(discountOf(it));
        const int bw = textW(e, L, b, 16) + 20;
        e.drawRoundRect(L.X(xl + 12, bw), y + 12, bw, 30, 8, kRED);
        e.drawTextCentered(b, L.X(xl + 12, bw) + bw / 2, y + 27, 16, kWHT, FontRole::Body, false);
    }
    if (isNewItem(it)) {
        const std::string b = tr("badge_new", lang);
        const int bw = textW(e, L, b, 15) + 20;
        e.drawRoundRect(L.X(xl + w - 12 - bw, bw), y + 12, bw, 30, 8, SDL_Color{16, 185, 129, 255});
        e.drawTextCentered(b, L.X(xl + w - 12 - bw, bw) + bw / 2, y + 27, 15, kWHT, FontRole::Body, L.rtl);
    }
    {   // yellow type tag on the cover (Add-On / World)
        const std::string tg = tr(it.isWorld ? "type_world" : "type_addon", lang);
        const int tw = textW(e, L, tg, 15) + 22;
        const int tyy = y + imgH - 12 - 30;
        e.drawRoundRect(L.X(xl + 12, tw), tyy, tw, 30, 8, kGD);
        e.drawTextCentered(tg, L.X(xl + 12, tw) + tw / 2, tyy + 15, 15, SDL_Color{30, 22, 4, 255}, FontRole::Body, L.rtl);
    }
    if (it.installed) {
        const int s2 = 30, ix = L.X(xl + w - 12 - s2, s2), iy = y + imgH - 12 - 30;
        e.drawCircle(ix + s2 / 2, iy + s2 / 2, s2 / 2, kGRN);
        icCheck(e, ix + 7, iy + 7, 16, kWHT);
    }
    // ---- title (pixel — v0.30: names always in Minecraft F77) ----
    const int ty = y + imgH + 12;
    boxText(e, L, it.title, xl + 16, w - 32, ty, 21, kTX, FontRole::Pixel);
    // ---- tier pill under the title (v0.65 mockup card) ----
    tierChip(e, L, xl + 16, y + imgH + 46, it.tier, lang, 28);
    if (focused) focusRing(e, rx, y, w, h, r, false);   // v0.35: card = list item, frame only
}

// v0.65 («وفي النهاية مستطيل عرض المزيد»): the gold tile at the end of the home strip —
// choosing it goes to the Store.
void viewMoreTile(UiEngine& e, const Lay& L, int xl, int y, int w, int h, bool focused,
                  const std::string& lang) {
    const int rx = L.X(xl, w), r = 18;
    e.dropShadow(rx, y + 6, w, h, r, 14, focused ? 150 : (gLight ? 40 : 90));
    e.drawRoundRect(rx, y, w, h, r, focused ? kSF2 : ink(gLight ? 10 : 12));
    e.drawRoundBorder(rx, y, w, h, r, 2, focused ? kGD : SDL_Color{255, 214, 90, 150});
    const std::string t = tr("view_more", lang);
    const int cy = y + h / 2;
    // big » chevron above the label (rotated round bars; points left in RTL)
    {
        const int ax = rx + w / 2, ay = cy - 50;
        const float sgn = L.rtl ? -1.f : 1.f;
        for (int i = 0; i < 2; ++i) {
            const int bx = ax + (int)((-14 + i * 24) * sgn);
            e.drawRoundRectRotated(bx, ay + 8, 30, 8, 4, 45.f * sgn, kGD);
            e.drawRoundRectRotated(bx, ay + 28, 30, 8, 4, -45.f * sgn, kGD);
        }
    }
    e.drawTextCentered(t, rx + w / 2, cy + 6, 24, kGD, FontRole::Pixel, L.rtl);
    if (focused) focusRing(e, rx, y, w, h, r, false);
}

// Empty state panel (no fake templates): icon + title + one line.
void emptyState(UiEngine& e, const Lay& L, int xl, int y, int w, int h, int kind, const std::string& title, const std::string& sub) {
    const int rx = L.X(xl, w);
    e.drawRoundRect(rx, y, w, h, 24, ink(gLight ? 8 : 10));
    e.drawRoundBorder(rx, y, w, h, 24, 2, ink(gLight ? 30 : 26));
    const int s = std::min(84, h / 3);
    const int ty = y + (h - s - 20 - 34 - 12 - 26) / 2;
    e.drawCircle(rx + w / 2, ty + s / 2, s / 2 + 14, ink(gLight ? 10 : 14));
    glyph(e, kind, rx + w / 2 - s / 2, ty, s, kMU, kMU, false);
    e.drawTextCentered(title, rx + w / 2, ty + s + 20 + 17, 30, kTX, FontRole::Body, L.rtl);
    const auto lines = wrapLines(e, sub, 22, w - 80, L.rtl);
    int yy = ty + s + 20 + 34 + 12 + 13;
    for (std::size_t i = 0; i < lines.size() && i < 2; ++i) { e.drawTextCentered(lines[i], rx + w / 2, yy, 22, kMU, FontRole::Body, L.rtl); yy += 32; }
}

void kickerText(UiEngine& e, const Lay& L, int xl, int y, const std::string& text, SDL_Color c) {
    sectionMark(e, L.X(xl, 16), y + 2, 16, c);
    boxText(e, L, text, xl + 26, 1100, y, 18, c);
}
// v0.30: the sign-in kicker uses the golden gem (not the blue mascot)
void kickerGem(UiEngine& e, const Lay& L, int xl, int y, const std::string& text, SDL_Color c) {
    sectionMark(e, L.X(xl, 30), y - 2, 30, SDL_Color{255, 214, 90, 255});
    boxText(e, L, text, xl + 44, 1100, y, 18, c);
}
void pageTitle(UiEngine& e, const Lay& L, const std::string& t, int xl, int y) {
    if (L.rtl) { boxText(e, L, t, xl, 900, y + 4, 54, kTX); return; }
    std::string u = t;
    for (auto& ch : u) if (ch >= 'a' && ch <= 'z') ch = static_cast<char>(ch - 32);
    boxText(e, L, u, xl, 900, y, 64, kTX, FontRole::Pixel);
}
// Big pixel title (shrinks to fit). onArt = drawn over artwork -> white with shadow in both themes.
void bigTitle(UiEngine& e, const Lay& L, const std::string& tIn, int xl, int maxW, int y, int px, bool onArt) {
    const FontRole role = FontRole::Pixel;
    std::string t = tIn;
    if (!L.rtl) for (auto& ch : t) if (ch >= 'a' && ch <= 'z') ch = static_cast<char>(ch - 32);
    while (px > 44 && e.measureText(t, px, role, L.rtl) > maxW) px -= 4;
    if (onArt || !gLight) boxText(e, L, t, xl + 4, maxW, y + 6, px, SDL_Color{0, 0, 0, 90}, role);
    boxText(e, L, t, xl, maxW, y, px, onArt ? kWHT : kTX, role);
}
void tabRow(UiEngine& e, const Lay& L, int xl, int y, const std::vector<std::string>& labels, int active,
            int focusBase, int focus, int px = 24) {
    int x = xl;
    for (std::size_t i = 0; i < labels.size(); ++i) {
        const int tw = textW(e, L, labels[i], px);
        const int rx = L.X(x, tw);
        const bool on = static_cast<int>(i) == active;
        const bool foc = focusBase >= 0 && focus == focusBase + static_cast<int>(i);
        boxText(e, L, labels[i], x, tw + 6, y, px, on ? kTX : kMU);
        // v0.30: tags are marked with an underline bar only (no selection frame)
        if (foc) e.drawRoundRect(rx, y + px + 12, tw, 7, 4, SDL_Color{255, 214, 90, 255});
        else if (on) e.drawRoundRect(rx, y + px + 14, tw, 5, 3, kBLl);
        x += tw + 44;
    }
}
void clockIcon(UiEngine& e, int x, int y, int s, SDL_Color c) {
    ring(e, x + s * 0.5f, y + s * 0.5f, s * 0.42f, 2, c);
    seg(e, x + s * 0.5f, y + s * 0.5f, x + s * 0.5f, y + s * 0.26f, 2, c);
    seg(e, x + s * 0.5f, y + s * 0.5f, x + s * 0.66f, y + s * 0.6f, 2, c);
}
// "Ends in 03 days 12 hrs 45 min" — returns width (0 when the sale has no end date).
int countdownBox(UiEngine& e, const Lay& L, const std::string& lang, int xl, int y, int h, bool withLabel, bool draw = true) {
    int dd = 0, hh = 0, mm = 0;
    if (!seasonCountdown(dd, hh, mm)) return 0;
    char v[3][16];
    std::snprintf(v[0], sizeof v[0], "%02d", std::max(0, std::min(dd, 99)));
    std::snprintf(v[1], sizeof v[1], "%02d", std::max(0, std::min(hh, 99)));
    std::snprintf(v[2], sizeof v[2], "%02d", std::max(0, std::min(mm, 99)));
    const char* units[3] = {"days", "hrs", "min"};
    const int vpx = 28, upx = 15;
    // v6.7: one group (clock · label · 03 DAYS · 05 HRS · 00 MIN) centred in the pill with equal side padding.
    // Numbers are centred on the pill's middle line; the small units sit on the numbers' baseline.
    std::vector<GPiece> g;
    g.push_back(gIcon(24, 24, [&](int gx, int gy) { clockIcon(e, gx, gy, 24, kMU); }, 10));
    if (withLabel) {
        GPiece lb = gText(e, tr("ends_in", lang), 19, kMU, L.rtl, 14);
        g.push_back(lb);
    }
    const int capV = e.inkCapHeight("00", vpx, FontRole::Pixel);
    for (int i = 0; i < 3; ++i) {
        g.push_back(gText(e, v[i], vpx, kTX, false, 8, FontRole::Pixel));
        GPiece u = gText(e, tr(units[i], lang), upx, kMU, L.rtl, 18);
        if (!L.rtl) u.dy = (capV - e.inkCapHeight(u.text, upx) + 1) / 2;
        g.push_back(u);
    }
    const int w = 22 + groupW(g) + 22;
    if (!draw) return w;
    const int rx = L.X(xl, w);
    e.drawRoundRect(rx, y, w, h, 18, gLight ? SDL_Color{255, 255, 255, 230} : SDL_Color{10, 12, 15, 170});
    e.drawRoundBorder(rx, y, w, h, 18, 2, ink(24));
    drawGroupCentered(e, L, g, rx + w / 2, y + h / 2);
    return w;
}
int discountPill(UiEngine& e, const Lay& L, int xl, int y, int h, int pct) {
    if (pct <= 0) return 0;
    const std::string s = fmtPct(pct);
    const int w = textW(e, L, s, h / 2) + 44;
    const int rx = L.X(xl, w);
    e.drawRoundGradH(rx, y, w, h, 16, SDL_Color{244, 63, 94, 255}, SDL_Color{219, 39, 119, 255});
    e.drawTextCentered(s, rx + w / 2, y + h / 2, h / 2, kWHT, FontRole::Body, false);
    return w;
}
// Leading-side shade over full-bleed art towards the page colour (works in dark and light).
void leadShade(UiEngine& e, const Lay& L, int h, bool strong) {
    const Uint8 a1 = strong ? 242 : 232, a2 = strong ? 180 : 140, a3 = strong ? 64 : 0;
    const int w1 = strong ? 650 : 420, w2 = strong ? 400 : 440, w3 = strong ? 870 : 480;
    const SDL_Color c0 = shade(255), c1 = shade(a1), c2 = shade(a2), c3 = shade(a3);
    if (!L.rtl) {
        e.fillGradH(0, 0, w1, h, c0, c1);
        e.fillGradH(w1, 0, w2, h, c1, c2);
        e.fillGradH(w1 + w2, 0, w3, h, c2, c3);
        if (strong) e.fillRect(w1 + w2 + w3, 0, 1920 - (w1 + w2 + w3), h, c3);
    } else {
        e.fillGradH(1920 - w1, 0, w1, h, c1, c0);
        e.fillGradH(1920 - w1 - w2, 0, w2, h, c2, c1);
        e.fillGradH(1920 - w1 - w2 - w3, 0, w3, h, c3, c2);
        if (strong) e.fillRect(0, 0, 1920 - (w1 + w2 + w3), h, c3);
    }
}
void bottomShade(UiEngine& e, int from = 454) {
    e.fillGradV(0, from, 1920, 799 - from, shade(0), shade(245));
    e.fillRect(0, 799, 1920, 281, shade(245));
}
void playIcon(UiEngine& e, int x, int y, int s, SDL_Color c) {
    fillTriRight(e, x + s * 30 / 100, y + s * 20 / 100, s * 50 / 100, s * 60 / 100, c);
}
void lockIcon(UiEngine& e, int x, int y, int s, SDL_Color c) {
    const int t = std::max(3, s / 10);
    e.drawRoundBorder(x + s * 28 / 100, y + s * 8 / 100, s * 44 / 100, s * 50 / 100, s * 22 / 100, t, c);
    e.drawRoundRect(x + s * 16 / 100, y + s * 40 / 100, s * 68 / 100, s * 52 / 100, s / 8, c);
}
// Avatar: account image if loaded, otherwise the person glyph.
void accountAvatar(UiEngine& e, int x, int y, int s, bool ring) {
    int iw = 0, ih = 0;
    if (ring && gAcc.signedIn) e.drawRoundGradV(x - 4, y - 4, s + 8, s + 8, s / 2 + 4, kGD, SDL_Color{214, 140, 30, 255});
    if (gAcc.signedIn && !gAcc.avatar.empty() && e.imageSize(gAcc.avatar, iw, ih)) { e.drawImageRounded(gAcc.avatar, x, y, s, s, s / 2); return; }
    e.drawRoundGradV(x, y, s, s, s / 2, gLight ? SDL_Color{226, 230, 236, 255} : SDL_Color{43, 49, 59, 255},
                     gLight ? SDL_Color{210, 215, 223, 255} : SDL_Color{27, 31, 38, 255});
    e.drawRoundBorder(x, y, s, s, s / 2, 2, ink(36));
    const int g = s * 50 / 100;   // thin line "user" icon (same family as the settings icons)
    lineIcon(e, 2, x + (s - g) / 2, y + (s - g) / 2, g, gLight ? SDL_Color{92, 101, 114, 255} : SDL_Color{170, 178, 192, 255});
}
} // namespace

// ============================================================================
//  Rail + status cluster
// ============================================================================
namespace {
// v0.29: the on-screen clock must be the CONSOLE clock (time & timezone follow the device).
#if defined(__ORBIS__)
#  include <orbis/Sysmodule.h>
extern "C" {
int32_t sceRtcGetCurrentClockLocalTime(void* dateTime);   // not declared by the OpenOrbis headers
}
struct OrbisRtcDateTime { unsigned short year, month, day, hour, minute, second; unsigned int microsecond; };
static int gRtcTried = 0;
static bool gRtcOk = false;
static bool deviceHHMM(char* buf, std::size_t n) {
    if (!gRtcTried) {
        gRtcTried = 1;
        sceSysmoduleLoadModule(static_cast<OrbisSysModule>(0x00A1));   // SCE_SYSMODULE_RTC -> loads libSceRtc
        gRtcOk = true;
    }
    if (gRtcOk) {
        OrbisRtcDateTime dt{};
        if (sceRtcGetCurrentClockLocalTime(&dt) == 0 && dt.hour <= 23 && dt.minute <= 59) {
            std::snprintf(buf, n, "%02d:%02d", dt.hour, dt.minute);
            return true;
        }
    }
    return false;
}
#else
static bool deviceHHMM(char* buf, std::size_t n) { (void)buf; (void)n; return false; }
#endif
bool gShowClock = true;
// railIdx: 0 Home, 1 Deals, 2 Store, 3 Plans, 4 Profile, 5 Settings, -1 none
// v0.43 (user order): the app logo ALWAYS sits in a COMPLETE rounded tile —
// same frame on every screen (rail, sign-in), never a cropped/offset shadow box.
static void appLogoTile(UiEngine& e, int cx, int y, int tile) {
    const int x = cx - tile / 2;
    e.dropShadow(x, y + 6, tile, tile, 24, 16, gLight ? 58 : 130);
    e.drawRoundRect(x, y, tile, tile, 24, gLight ? SDL_Color{255, 255, 255, 242} : SDL_Color{18, 34, 46, 242});
    e.drawRoundBorder(x, y, tile, tile, 24, 2, ink(30));
    const int is = tile * 72 / 104;
    e.drawImageRounded("icon_app", x + (tile - is) / 2, y + (tile - is) / 2, is, is, 16);
}

static void drawStatusRow(UiEngine& e, const Lay& L);   // fwd (v0.59)
void drawRail(UiEngine& e, int railIdx, const std::string& langCode) {
    Lay L; L.rtl = (langCode == "ar");
    gRail = true;
    const int RW = kRailW;
    const int rx = L.X(0, RW);
    const SDL_Color r0 = gLight ? SDL_Color{255, 255, 255, 250} : SDL_Color{8, 9, 12, 248};
    const SDL_Color r1 = gLight ? SDL_Color{255, 255, 255, 215} : SDL_Color{8, 9, 12, 205};
    if (L.rtl) e.fillGradH(rx, 0, RW, 1080, r1, r0); else e.fillGradH(rx, 0, RW, 1080, r0, r1);
    e.fillRect(L.X(RW - 1, 1), 0, 1, 1080, ink(18));
    const int cx = rx + RW / 2;
    appLogoTile(e, cx, 22, 96);   // v0.43: complete frame, matches the sign-in screen
    struct It { int kind; const char* key; };
    static const It items[7] = {{10, "nav_home"}, {11, "nav_deals"}, {12, "nav_store"}, {14, "nav_mine"},
                                {7, "nav_plans"}, {1, "rail_profile"}, {9, "nav_settings"}};
    const int bw = 72, bh = 60, itemH = bh + 7 + 18, gap = 14;   // v0.56: 7 rail items (Search removed)
    auto item = [&](int i, int y) {
        const bool on = (i == railIdx);
        const int bx = cx - bw / 2;
        if (on) {
            e.dropShadow(bx, y + 4, bw, bh, 20, 10, gLight ? 60 : 120);
            e.drawRoundRect(bx, y, bw, bh, 20, kTX);              // ink box (white in dark, near-black in light)
            e.drawRoundRect(L.X(0, 5), y + (bh - 34) / 2, 5, 34, 2, kBLl);
        }
        const SDL_Color in = on ? kBG : kMU;
        const int is = 30;
        if (i == 5 && gAcc.signedIn && !gAcc.avatar.empty()) {   // profile item
            int iw = 0, ih = 0;
            if (e.imageSize(gAcc.avatar, iw, ih)) e.drawImageRounded(gAcc.avatar, cx - 17, y + (bh - 34) / 2, 34, 34, 17);
            else glyph(e, items[i].kind, cx - is / 2, y + (bh - is) / 2, is, in, in, false);
        } else {
            glyph(e, items[i].kind, cx - is / 2, y + (bh - is) / 2, is, in, in, false);
        }
        const std::string lab = tr(items[i].key, langCode);
        int px = 16;
        while (px > 11 && e.measureText(lab, px, FontRole::Body, L.rtl) > RW - 14) --px;
        e.drawTextCentered(lab, cx, y + bh + 7 + 9, px, on ? kTX : kMU, FontRole::Body, L.rtl);
    };
    const int navTop = 540 - (4 * itemH + 3 * gap) / 2;
    for (int i = 0; i < 4; ++i) item(i, navTop + i * (itemH + gap));
    const int botTop = 1080 - 30 - (3 * itemH + 12);
    for (int i = 0; i < 3; ++i) item(4 + i, botTop + i * (itemH + 12));

    drawStatusRow(e, L);
}

// v0.59: the status row (avatar · clock · AC) lives WITHOUT the side rail too — detail
// pages keep it while the nav strip is hidden (user: «الشريط الجنبي لا اريده»).
static void drawStatusRow(UiEngine& e, const Lay& L) {
    const int top = 34;
    const int av = 56, avX = 1920 - 64 - av;
    accountAvatar(e, L.X(avX, av), top, av, false);
    int x = avX - 18;
    if (gShowClock) {
        char buf[8];
        if (!deviceHHMM(buf, sizeof buf)) {                    // device clock first
            const std::time_t t = std::time(nullptr);
            std::tm lt{};
            localtime_r(&t, &lt);
            std::snprintf(buf, sizeof buf, "%02d:%02d", lt.tm_hour, lt.tm_min);
        }
        const int cw = e.measureText(buf, 23, FontRole::Body, false);
        x -= cw;
        e.drawText(buf, L.X(x, cw), top + (av - 26) / 2, 23, kTX, FontRole::Body, false, false);
        x -= 18;
    }
    const std::string bal = formatAC(gAcc.signedIn ? gAcc.acBalance : 0);
    // v6.7: coin + amount + "AC" centred as one group, same padding on both sides; "AC" sits on the digits' baseline
    std::vector<GPiece> g;
    g.push_back(gIcon(28, 28, [&](int gx, int gy) { e.drawImage("icon_coin", gx, gy, 28, 28); }, 10));
    g.push_back(gText(e, bal, 23, kGD, false, 8));
    GPiece ac = gText(e, "AC", 16, kMU, false);
    ac.dy = (e.inkCapHeight(bal, 23) - e.inkCapHeight("AC", 16) + 1) / 2;
    g.push_back(ac);
    const int pillH = 52, pillW = 18 + groupW(g) + 20;
    const int pillX = x - pillW, py = top + (av - pillH) / 2;
    const int prx = L.X(pillX, pillW);
    e.drawRoundRect(prx, py, pillW, pillH, pillH / 2, gLight ? SDL_Color{255, 255, 255, 235} : SDL_Color{10, 12, 15, 190});
    e.drawRoundBorder(prx, py, pillW, pillH, pillH / 2, 2, SDL_Color{255, 201, 64, static_cast<Uint8>(gLight ? 160 : 90)});
    drawGroupCentered(e, L, g, prx + pillW / 2, py + pillH / 2);
}
} // namespace

void drawTopNav(UiEngine& e, int activeTab, const std::string& langCode, int /*acBalance: from account()*/, bool rail) {
    // activeTab: 0 Home, 1 Deals, 2 Store, 3 Settings, 4 Plans, 5 Profile, 6 Mine, 7 Search(none)
    static const int kMap[8] = {0, 1, 2, 6, 4, 5, 3, -1};
    if (rail) { drawRail(e, (activeTab >= 0 && activeTab < 8) ? kMap[activeTab] : -1, langCode); return; }
    // v0.59 (user): on the addon page the side rail is hidden — status row only
    Lay L; L.rtl = (langCode == "ar");
    gRail = false;
    drawStatusRow(e, L);
}

void setThemeLight(bool light) { applyTheme(light); }
bool themeLight() { return gLight; }

// ============================================================================
//  Sign-in by code (this IS the first page — no welcome page, no email)
// ============================================================================
void drawSignIn(UiEngine& e, int focus, const std::string& code, int secondsLeft, int status,
                const std::string& langCode, bool backLabel, bool qrView) {
    Lay L; L.rtl = (langCode == "ar");
    const std::string& lang = langCode;
    e.clear(kBG);
    coverImage(e, pageArt(e, "bg_cave", "bg_welcome"), 0, 0, 1920, 1080, 0, 0.5f, 0.5f);
    leadShade(e, L, 1080, true);
    e.fillRect(0, 0, 1920, 1080, shade(gLight ? 120 : 70));
    gRail = false;

    // ---- leading column: brand, title, steps, what comes with the account ----
    const int x0 = 150, lw = 860;
    const int lg = 104;   // v0.41/0.43: COMPLETE rounded tile frame around the logo
    const int lrx = L.X(x0, lg);
    appLogoTile(e, lrx + lg / 2, 90, lg);
    boxText(e, L, "ADDONSTATION", x0 + lg + 22, 600, 112, 34, kTX, FontRole::Pixel);
    bigTitle(e, L, tr("signin_title", lang), x0, lw, 236, L.rtl ? 92 : 100, false);
    int y = paragraphAt(e, L, tr("signin_sub", lang), x0, 760, 364, 27, kCHIPTX, 3, 12) + 34;
    if (status == 1) qrView = false;
    const char* stepsCode[3] = {"step_1", "step_2", "step_3"};
    const char* stepsQr[3] = {"qr_step_1", "qr_step_2", "qr_step_3"};
    const char* const* steps = qrView ? stepsQr : stepsCode;
    for (int i = 0; i < 3; ++i) {
        const int nx = L.X(x0, 56);
        e.drawRoundGradV(nx, y, 56, 56, 16, kBL, kBL2);
        e.drawTextCentered(num(i + 1), nx + 28, y + 28, 28, SDL_Color{6, 24, 14, 255}, FontRole::Pixel, false);
        boxText(e, L, tr(steps[i], lang), x0 + 56 + 22, lw - 80, y + 14, 27, kTX);
        if (i < 2) e.fillRect(L.X(x0 + 27, 2), y + 60, 2, 22, ink(40));
        y += 86;
    }
    y += 24;
    kickerGem(e, L, x0, y, tr("same_account", lang), kGD);
    y += 44;
    {
        int x = x0;
        auto chip = [&](int kind, const std::string& t, SDL_Color c) {
            const int w = textW(e, L, t, 21) + 70;
            const int rx = L.X(x, w);
            e.drawRoundRect(rx, y, w, 50, 25, gLight ? SDL_Color{255, 255, 255, 220} : SDL_Color{10, 12, 15, 170});
            e.drawRoundBorder(rx, y, w, 50, 25, 2, ink(26));
            if (kind < 0) e.drawImage("icon_coin", L.X(x + 14, 28), y + 11, 28, 28);
            else glyph(e, kind, L.X(x + 14, 28), y + 11, 28, c, c, false);
            boxText(e, L, t, x + 52, w - 60, y + 13, 21, kTX);
            x += w + 12;
        };
        chip(-1, tr("sa_ac", lang), kGD);
        chip(7, tr("sa_plan", lang), kEPICT);
        chip(1, tr("sa_avatar", lang), kBLl);
    }

    // ---- trailing card: the code ----
    const int cw = 720, ch = 700, cxl = 1920 - 150 - cw, cy = 190;
    const int crx = L.X(cxl, cw);
    e.dropShadow(crx, cy + 12, cw, ch, 32, 30, gLight ? 70 : 190);
    surface(e, crx, cy, cw, ch, 32, 1);
    const int ix = cxl + 48, iw = cw - 96;
    if (qrView) {        // "no app yet": the code card becomes a QR card (download page of the phone app)
        kickerText(e, L, ix, cy + 50, tr("qr_title", lang), kBLl);
        const int qs = 340;
        e.dropShadow(crx + (cw - qs) / 2, cy + 104, qs, qs, 16, 16, gLight ? 40 : 110);
        qrTile(e, crx + (cw - qs) / 2, cy + 98, qs);
        const std::string hint = tr("qr_hint", lang);
        const int hw = std::min(iw, textW(e, L, hint, 24) + 4);
        paragraphAt(e, L, hint, cxl + (cw - hw) / 2, hw, cy + 470, 24, kCHIPTX, 2, 10);
    } else if (status == 1) {   // linked: show the account that just signed in
        const int s = 150;
        accountAvatar(e, crx + (cw - s) / 2, cy + 70, s, true);
        const std::string nm = gAcc.displayName.empty() ? gAcc.username : gAcc.displayName;
        e.drawTextCentered(fmt1(tr("welcome_name", lang), nm), crx + cw / 2, cy + 280, 36, kTX, FontRole::Body, L.rtl);
        const int tw = tierChip(e, L, 0, 0, gAcc.plan, lang, 40, false);
        tierChip(e, L, L.rtl ? 1920 - (crx + (cw + tw) / 2) : crx + (cw - tw) / 2, cy + 330, gAcc.plan, lang, 40);
        {   // v7.0: coin + balance = one exact-centred group inside the pill
            std::vector<GPiece> g;
            g.push_back(gIcon(36, 36, [&](int gx, int gy) { e.drawImage("icon_coin", gx, gy, 36, 36); }, 12));
            g.push_back(gText(e, formatAC(gAcc.acBalance) + " AC", 34, kGD, L.rtl));
            const int bw = groupW(g) + 2 * 17;
            const int bx = crx + (cw - bw) / 2;
            e.drawRoundRect(bx, cy + 400, bw, 70, 35, gLight ? SDL_Color{255, 248, 225, 255} : SDL_Color{40, 30, 8, 200});
            e.drawRoundBorder(bx, cy + 400, bw, 70, 35, 2, SDL_Color{255, 201, 64, 150});
            drawGroupAt(e, L, g, bx + 17, cy + 435);
        }
        {   // v7.0: check badge (clean geometric check) + text = one exact-centred group
            const SDL_Color col = gLight ? SDL_Color{10, 85, 160, 255} : kGRN2;
            std::vector<GPiece> g;
            g.push_back(gIcon(24, 24, [&](int gx, int gy) {
                e.drawCircle(gx + 12, gy + 12, 12, kGRN);
                shapeIcon(e, 2, gx + 2, gy + 2, 20, kWHT);
            }, 12));
            g.push_back(gText(e, tr("st_linked", lang), 24, col, L.rtl));
            const int pw = groupW(g) + 2 * 20;
            const int px = crx + (cw - pw) / 2;
            e.drawRoundRect(px, cy + 494, pw, 56, 28, SDL_Color{74, 168, 255, 40});
            e.drawRoundBorder(px, cy + 494, pw, 56, 28, 2, SDL_Color{74, 168, 255, 160});
            drawGroupAt(e, L, g, px + 20, cy + 522);
        }
    } else {
        boxText(e, L, tr("your_code", lang), ix, iw, cy + 48, 24, kMU);
        // code boxes (6 characters; spaces ignored)
        std::string c;
        for (char ch2 : code) if (ch2 != ' ' && ch2 != '-') c += ch2;
        const int n = 6, bwid = 86, gap = 14, total = n * bwid + (n - 1) * gap;
        const int bx0 = crx + (cw - total) / 2, by = cy + 100, bh = 120;
        const bool dim = (status == 2 || status == 3);
        for (int i = 0; i < n; ++i) {
            const int bx = bx0 + i * (bwid + gap);
            e.drawRoundRect(bx, by, bwid, bh, 18, gLight ? SDL_Color{240, 243, 247, 255} : SDL_Color{8, 10, 13, 255});
            e.drawRoundBorder(bx, by, bwid, bh, 18, 2, dim ? ink(30) : SDL_Color{74, 168, 255, 140});
            if (i < static_cast<int>(c.size()) && status != 4) {
                const std::string g(1, c[static_cast<std::size_t>(i)]);
                e.drawTextCentered(g, bx + bwid / 2, by + bh / 2, 64, dim ? kMU : kTX, FontRole::Pixel, false);
            } else {
                e.drawRoundRect(bx + bwid / 2 - 14, by + bh / 2 - 3, 28, 6, 3, ink(40));
            }
        }
        // expiry bar
        const int ey = by + bh + 40;
        const int total600 = 600;
        const int left = std::max(0, std::min(total600, secondsLeft));
        const int barW = iw, fillW = status == 0 ? barW * left / total600 : 0;
        const int brx = L.X(ix, barW);
        e.drawRoundRect(brx, ey, barW, 10, 5, ink(24));
        if (fillW > 0) e.drawRoundGradH(L.rtl ? brx + barW - fillW : brx, ey, fillW, 10, 5, kBL, kBL2);
        if (status == 0) {
            char t[16];
            std::snprintf(t, sizeof t, "%d:%02d", left / 60, left % 60);
            boxText(e, L, tr("code_expires", lang), ix, iw / 2, ey + 26, 22, kMU);
            endText(e, L, t, ix, iw, ey + 22, 28, kTX, FontRole::Pixel);
            // v0.29: with one minute left the screen tells the user to regenerate the code
            if (left <= 60) {
                const int nw = textW(e, L, tr("code_regen", lang), 21) + 48;
                const int nx = L.X(ix + (iw - nw) / 2, nw);
                e.drawRoundRect(nx, ey + 62, nw, 42, 21, SDL_Color{255, 201, 64, 40});
                e.drawRoundBorder(nx, ey + 62, nw, 42, 21, 2, SDL_Color{255, 201, 64, 170});
                e.drawTextCentered(tr("code_regen", lang), nx + nw / 2, ey + 83, 21,
                                   gLight ? SDL_Color{150, 96, 10, 255} : SDL_Color{255, 201, 64, 255}, FontRole::Body, L.rtl);
            }
        }
        // status line
        const char* sk = status == 0 ? "st_waiting" : status == 2 ? "st_expired" : status == 3 ? "st_error" : "st_creating";
        const SDL_Color sc = status == 2 ? kWARN : status == 3 ? kERT : kBLl;
        const int sy = ey + 90;
        // v6.7: dot + text centred together, equal padding on both sides
        std::vector<GPiece> g;
        g.push_back(gIcon(16, 16, [&](int gx, int gy) { e.drawCircle(gx + 8, gy + 8, 8, sc); }, 12));
        g.push_back(gText(e, tr(sk, lang), 23, kTX, L.rtl));
        const int sw = 26 + groupW(g) + 26;
        const int sx = crx + (cw - sw) / 2;
        e.drawRoundRect(sx, sy, sw, 56, 28, SDL_Color{sc.r, sc.g, sc.b, 34});
        e.drawRoundBorder(sx, sy, sw, 56, 28, 2, SDL_Color{sc.r, sc.g, sc.b, 130});
        drawGroupCentered(e, L, g, sx + sw / 2, sy + 28);
    }
    // buttons — code view: 0 "New code", 1 "Don't have the app?"  ·  QR view: 0 "Get a code"  ·  linked: 0 "Continue"
    const int b0y = cy + ch - 48 - 76 - 16 - 76, b1y = cy + ch - 48 - 76;
    if (qrView) {
        button(e, L, ix, b1y, iw, 76, tr("get_code", lang), 0, 27, 20);
        if (focus == 0) focusRing(e, L.X(ix, iw), b1y, iw, 76, 20);
    } else if (status == 1) {
        button(e, L, ix, b1y, iw, 76, tr("continue_k", lang), 0, 27, 20);
        focusRing(e, L.X(ix, iw), b1y, iw, 76, 20);
    } else {
        button(e, L, ix, b0y, iw, 76, tr("new_code", lang), status == 2 || status == 3 ? 0 : 1, 27, 20);
        if (focus == 0) focusRing(e, L.X(ix, iw), b0y, iw, 76, 20);
        button(e, L, ix, b1y, iw, 76, tr("no_app", lang), 1, 25, 20);
        if (focus == 1) focusRing(e, L.X(ix, iw), b1y, iw, 76, 20);
    }
    // ○: linked -> continue · QR view -> back to the code · code view -> continue as guest (or Back inside the app)
    drawLegendBar(e, {{"\xE2\x9C\x95", "select"},
                      {"\xE2\x97\x8B", status == 1 ? "continue_k" : (qrView || backLabel) ? "back" : "continue_guest"}}, langCode);
}

void drawSignInRequired(UiEngine& e, int focus, const std::string& langCode) {
    Lay L; L.rtl = (langCode == "ar");
    e.fillRect(0, 0, 1920, 1080, kSCRIM);
    const int w = 780, h = 600, x = 960 - w / 2, y = 540 - h / 2;
    e.dropShadow(x, y + 10, w, h, 32, 30, 200);
    surface(e, x, y, w, h, 32, 1);
    e.drawCircle(960, y + 96, 58, SDL_Color{74, 168, 255, 36});
    e.drawRoundBorder(960 - 58, y + 38, 116, 116, 58, 2, SDL_Color{74, 168, 255, 130});
    lockIcon(e, 960 - 28, y + 66, 56, kBLl);
    e.drawTextCentered(tr("req_title", langCode), 960, y + 206, 38, kTX, FontRole::Body, L.rtl);
    {
        const auto lines = wrapLines(e, tr("req_sub", langCode), 25, w - 120, L.rtl);
        int yy = y + 258;
        for (std::size_t i = 0; i < lines.size() && i < 3; ++i) { e.drawTextCentered(lines[i], 960, yy + 13, 25, kMU, FontRole::Body, L.rtl); yy += 36; }
    }
    const int bw = w - 96, bx = x + 48;
    button(e, L, L.rtl ? 1920 - bx - bw : bx, y + 384, bw, 84, tr("signin_code_btn", langCode), 0, 28, 20);
    if (focus == 0) focusRing(e, bx, y + 384, bw, 84, 20);
    button(e, L, L.rtl ? 1920 - bx - bw : bx, y + 484, bw, 84, tr("not_now", langCode), 1, 28, 20);
    if (focus == 1) focusRing(e, bx, y + 484, bw, 84, 20);
}

// ============================================================================
//  Home — server sale banner (whole image) / featured item / empty state
// ============================================================================
namespace {
// Shelf of up to 5 cards from `items` starting at scroll; focus base fb. Empty -> empty state.
// v0.58 (user pick «أ: نصف + بهتة ناعمة»): the bottom cut of a grid window always peeks the
// next row — half a card fading softly into the background («باهت»), never a hard edge. When
// focus walks down, the peeked row scrolls fully into view (win = one full row in main.cpp).
// v0.65d: gridFade removed — the dark erase box was ruining the cards (user request)

void cardGrid(UiEngine& e, const Lay& L, const std::vector<int>& items, int scroll, int fb, int focus,
              int xl, int yTop, int w, int yMax, const std::string& lang, bool dealBadge) {
    if (items.empty()) {
        emptyState(e, L, xl, yTop, w, yMax - yTop, 12, tr(gCat.empty() ? "home_empty_t" : "empty_t", lang),
                   tr(gCat.empty() ? "home_empty_s" : "empty_s", lang));
        return;
    }
    // v0.36 «شبكة البطاقات»: 5 whole cards per row (never cropped), rows continue below the fold
    // and the window scrolls (clampScroll). Each card carries its action word under it:
    // «نزّل» when the file is available now, «قريباً» when it is not released yet.
    const int cols = 5, gap = 26;
    const int cw = (w - gap * (cols - 1)) / cols;
    const int ih = cw * 9 / 16;
    const int ch = vcardHeight(ih, false);
    const int first = std::max(0, std::min(scroll, static_cast<int>(items.size()) - 1));
    for (int i = 0; i < cols * 4 && first + i < static_cast<int>(items.size()); ++i) {
        const int ry = yTop + (i / cols) * (ch + gap);
        if (ry >= yMax) break;
        const int x = xl + (i % cols) * (cw + gap);
        const AddonItem& it = gCat[static_cast<std::size_t>(items[static_cast<std::size_t>(first + i)])];
        vcard(e, L, x, ry, cw, ih, it, focus == fb + i, lang, false, false, dealBadge);
    }
    // v0.65d: no dark fade box («الغيه الذي مخرب البطاقات»)
}

// v0.65 («شريط واحد يعرض اخر عشر بطاقات»): ONE horizontal strip = the 10 newest
// filtered cards + a gold "View more" rectangle («وفي النهاية مستطيل عرض المزيد»).
// Fix per «بس المستطيل العرض بعد آخر بطاقة»: the rectangle is the LAST tile of the
// row — always visible AFTER the last card (the half-peek included), never before
// it. The 10 cards scroll behind the row, one tile at a time («يتمشى يسار ويمين»).
static void cardStrip(UiEngine& e, const Lay& L, const std::vector<int>& items, int scroll, int fb,
                      int focus, int xl, int yTop, const std::string& lang) {
    const int gap = 24;
    const int cw = 284;
    const int ih = cw * 9 / 16;
    const int ch = vcardHeight(ih, false);
    const int nCards = (int)std::min<std::size_t>(10, items.size());
    const int vmw = 284, vmx = kCR - vmw;   // the rectangle sits at the row end
    const int first = std::max(0, std::min(scroll, std::max(0, nCards - 1)));
    for (int i = 0; i < 5 && first + i < nCards; ++i) {   // 4 full cards + a half-peek (runs under the rectangle)
        const int x = xl + i * (cw + gap);
        const AddonItem& it = gCat[(std::size_t)items[(std::size_t)(first + i)]];
        vcard(e, L, x, yTop, cw, ih, it, focus == fb + i, lang, false, false, saleActive());
    }
    viewMoreTile(e, L, vmx, yTop, vmw, ch, focus == fb + nCards, lang);   // drawn last = covers the peek
}
} // namespace

void drawHome(UiEngine& e, int focus, int heroIndex, int filterIndex, const std::string& langCode) {
    Lay L; L.rtl = (langCode == "ar");
    const std::string& lang = langCode;
    const bool sale = saleActive();
    // ONE hero carousel (concept look): the live sale + featured + exclusive items, switched with L2/R2.
    const std::vector<int> slides = homeSlides();
    const int nS = static_cast<int>(slides.size());
    const int cur = nS ? ((heroIndex % nS) + nS) % nS : 0;
    const int slide = nS ? slides[static_cast<std::size_t>(cur)] : -2;   // -1 sale, -2 empty catalog
    const AddonItem* hero = slide >= 0 ? &gCat[static_cast<std::size_t>(slide)] : nullptr;
    // v0.38: the hero banner follows the focused card — image + name change live.
    // It KEEPS the card when focus moves up to the CTA («عرض الإضافة» opens it),
    // and hands the banner back to the carousel when a tab is focused.
    const std::vector<int> homeItems = viewItems(View::Home, filterIndex);
    static int sFollow = -1, sFollowFilter = -2;
    if (sFollowFilter != filterIndex) { sFollowFilter = filterIndex; sFollow = -1; }
    if (focus >= 12) {
        // v0.63: + scroll offset — the banner followed the WRONG card once the grid
        // scrolled (it read focus-12 as an absolute index into the full list).
        const std::size_t gi = static_cast<std::size_t>(scrollOf(View::Home)) +
                               static_cast<std::size_t>(focus - 12);
        const int nCards = (int)std::min<std::size_t>(10, homeItems.size());
        if (gi < homeItems.size() && focus - 12 < nCards) sFollow = homeItems[gi];   // v0.65: not the View-more tile
    } else if (focus >= 2 && focus <= 11) {
        sFollow = -1;
    }
    if ((focus <= 1 || focus >= 12) && sFollow >= 0 && sFollow < (int)gCat.size())
        hero = &gCat[static_cast<std::size_t>(sFollow)];
    const int cx = kCL + 20;
    const int textMax = 1180;

    int ctaY = 470;   // v0.65 («نفس ذا»): tall hero — CTA sits low, above the chips   // v0.45 (option D): compact hero — two whole rows below
    std::string b0, b1;
    if (!hero && slide == -1) {
        // ---- sale: the season artwork is the full-screen backdrop (whole 16:9 image), text on the leading side ----
        const std::string art = saleArt(e, "hero");
        const bool baked = !art.empty() && saleArtHasTitle(art);
        bakedBackdrop(e, "home|sale|" + art + (baked ? "|b" : "|f") + (L.rtl ? "|R" : "|N"), [&] {
            backdrop(e);
            if (baked) {   // art with its own title: full screen too, but NO text and only a light shade (logo stays clean)
                saleBleed(e, art);
                bottomShade(e, 330);   // bright art: the page colour starts earlier so tabs/cards stay readable
                e.fillGradV(0, 0, 1920, 120, shade(90), shade(0));
            } else if (!art.empty()) {
                saleBleed(e, art);
                leadShade(e, L, 1080, false);
                bottomShade(e);
                e.fillGradV(0, 0, 1920, 150, shade(140), shade(0));
            }
        });
        if (baked) {
            ctaY = 470;   // v0.65: tall hero CTA row
        } else {   // baked art already says it — no text over the logo
            kickerText(e, L, cx, 36, saleKicker(lang), kGD);
            bigTitle(e, L, saleTitle(lang), cx, textMax, 56, L.rtl ? 62 : 70, false);
            const std::string sub = saleSubtitle(lang);
            if (!sub.empty()) paragraphAt(e, L, sub, cx, std::min(820, textMax), 138, 20, kCHIPTX, 2, 10);
        }
        b0 = tr("shop_sale", lang);
        b1 = tr("nav_store", lang);
    } else if (hero) {
        // ---- featured / exclusive / newest item: its own cover full-bleed (covers are 16:9) ----
        const AddonItem& h = *hero;
        std::string kick;
        if (sale && onSale(h)) kick = saleKicker(lang) + "  \xC2\xB7  " + saleTitle(lang);
        else {
            if (h.exclusive) kick = tr("exclusive", lang) + "  \xC2\xB7  ";
            kick += isNewItem(h) ? tr("newest_k", lang) : tr(h.isWorld ? "type_world" : "type_addon", lang);
        }
        const std::string lead = !h.description.empty() ? h.description : (h.creator.empty() ? std::string() : tr("by", lang) + " " + h.creator);
        // v0.38: hero text bakes WITH the art (incl. lang + sale state) so banner changes glide
        bakedBackdrop(e, "home|item|" + h.id + (!h.cover.empty() && e.hasImage(h.cover) ? "|1" : "|0") + (L.rtl ? "|R" : "|N") + "|" + lang + (sale ? "|S" : ""), [&] {
            backdrop(e);
            itemCover(e, h, 0, 0, 1920, 1080, 0);
            leadShade(e, L, 1080, false);
            bottomShade(e, 560);   // v0.65: the tall hero fades lower
            e.fillGradV(0, 0, 1920, 150, shade(140), shade(0));
            kickerText(e, L, cx, 130, kick, kGD);
            bigTitle(e, L, h.title, cx, textMax, 150, L.rtl ? 74 : 84, false);
            if (!lead.empty()) paragraphAt(e, L, lead, cx, std::min(820, textMax), 290, 22, kCHIPTX, 2, 12);
            {   // meta row — only real values: downloads · rating · category · type
                int mx = cx;
                const int my = 410;
                auto mitem = [&](void (*ic)(UiEngine&, int, int, int, SDL_Color), SDL_Color icc, const std::string& t, SDL_Color tc) {
                    if (ic) { ic(e, L.X(mx, 19), my + 1, 19, icc); mx += 26; }
                    const int tw = textW(e, L, t, 17);
                    boxText(e, L, t, mx, tw + 4, my, 17, tc);
                    mx += tw + 26;
                };
                if (h.downloads > 0) mitem(icDownloadArrow, kCHIPTX, fmtCompact(h.downloads), kTX);
                if (h.ratingCount > 0) { char b[16]; std::snprintf(b, sizeof b, "%.1f", h.rating); mitem(icStar, kGD, b, kTX); }
                std::string ct = (!h.category.empty() && h.category != "other") ? h.category + "  \xC2\xB7  " : std::string();
                ct += tr(h.isWorld ? "type_world" : "type_addon", lang);
                mitem(nullptr, kMU, ct, kCHIPTX);
            }
        });
        b0 = tr("view_addon", lang);
    } else {
        // ---- empty catalog: no templates, just a calm welcome ----
        bakedBackdrop(e, std::string("home|empty|") + (L.rtl ? "R" : "N"), [&] {
            backdrop(e);
            coverImage(e, pageArt(e, "bg_splash", "bg_welcome"), 0, 0, 1920, 1080, 0, 0.5f, 0.4f);
            leadShade(e, L, 1080, true);
            bottomShade(e);
        });
        kickerText(e, L, cx, 36, "ADDONSTATION", kGD);
        bigTitle(e, L, tr("home_empty_t", lang), cx, 1300, 56, L.rtl ? 60 : 68, false);
        paragraphAt(e, L, tr("home_empty_s", lang), cx, 900, 138, 20, kCHIPTX, 2, 10);
        b0 = gAcc.signedIn ? tr("view_plans", lang) : tr("signin_code_btn", lang);
        ctaY = 470;
    }
    drawTopNav(e, 0, langCode);

    // ---- CTAs (focus 0-1) + slide dots ----
    {
        const int bh = 64;
        const int w0 = std::max(250, textW(e, L, b0, 24) + 110);
        if (hero) {   // v6.7: play icon + label centred together in the button
            const SDL_Color dk{6, 24, 14, 255};
            button(e, L, cx, ctaY, w0, bh, "", 0, 24, 20);
            std::vector<GPiece> g;
            g.push_back(gIcon(13, 26, [&](int gx, int gy) { playIcon(e, gx - 7, gy, 26, dk); }, 14));
            g.push_back(gText(e, b0, 24, dk, L.rtl));
            drawGroupCentered(e, L, g, L.X(cx, w0) + w0 / 2, ctaY + bh / 2);
        } else {
            button(e, L, cx, ctaY, w0, bh, b0, 0, 24, 20);
        }
        if (focus == 0) focusRing(e, L.X(cx, w0), ctaY, w0, bh, 20);
        int after = cx + w0 + 16;
        if (!b1.empty()) {
            const int w1 = std::max(170, textW(e, L, b1, 24) + 72);
            button(e, L, after, ctaY, w1, bh, b1, 1, 24, 20);
            if (focus == 1) focusRing(e, L.X(after, w1), ctaY, w1, bh, 20);
            after += w1 + 16;
        }
        if (slide == -1) {
            after += discountPill(e, L, after, ctaY, bh, saleDiscount()) + 16;
            countdownBox(e, L, lang, after, ctaY, bh, true);
        } else if (hero) {
            after += tierChip(e, L, after, ctaY + 17, hero->tier, lang, 38) + 16;
            if (sale && onSale(*hero)) {
                after += discountPill(e, L, after, ctaY, bh, discountOf(*hero)) + 16;
                countdownBox(e, L, lang, after, ctaY, bh, true);
            }
        }
        if (nS > 1) {
            int dx = cx;
            for (int i = 0; i < nS; ++i) {
                const bool on = i == cur;
                const int dw = on ? 36 : 12;
                e.drawRoundRect(L.X(dx, dw), ctaY + bh + 26, dw, 8, 4, on ? kTX : ink(70));
                dx += dw + 10;
            }
        }
    }

    // ---- tabs (focus 5-11) + shelf (focus 12-16): newest first ----
    // v0.45 (option D, user pick): compact hero + two WHOLE rows at the current card size
    tabRow(e, L, cx, 630, {tr("chip_all", lang), tr("chip_addons", lang), tr("chip_worlds", lang), tr("chip_free", lang),
                           tr("chip_epic", lang), tr("chip_legendary", lang), tr("chip_new", lang)}, filterIndex, 5, focus);
    {   // v0.65 («نفس ذا»): ONE strip = the 10 newest cards + the gold "View more" tile
        cardStrip(e, L, homeItems, scrollOf(View::Home), 12, focus, cx, 700, lang);
    }

    std::vector<std::pair<std::string, std::string>> leg = {{"\xE2\x9C\x95", "select"}, {"\xE2\x96\xB3", "search"}};
    if (nS > 1) { leg.push_back({"L2", "prev_offer"}); leg.push_back({"R2", "next_offer"}); }
    drawLegendBar(e, leg, langCode);
}

// ============================================================================
//  Deals — server sale (whole artwork) + discounted items, or a clean empty state
// ============================================================================
void drawDeals(UiEngine& e, int focus, int filterIndex, const std::string& langCode) {
    Lay L; L.rtl = (langCode == "ar");
    const std::string& lang = langCode;
    const bool sale = saleActive();
    // sale artwork = full-screen backdrop (whole 16:9 image); shelves sit on the dark lower part
    const std::string art = sale ? saleArt(e, "wide") : std::string();
    const bool baked = !art.empty() && saleArtHasTitle(art);   // logo art: shown whole in a panel (text can't sit on it)
    if (!art.empty()) {
        bakedBackdrop(e, "deals|" + art + (baked ? "|b" : "|f") + (L.rtl ? "|R" : "|N"), [&] {
            backdrop(e);
            saleBleed(e, art);
            if (!baked) leadShade(e, L, 1080, false);   // art with its own title keeps a light shade only
            bottomShade(e, baked ? 330 : 300);
            e.fillGradV(0, 0, 1920, 150, shade(baked ? 120 : 150), shade(0));
        });
    } else {
        backdrop(e);
    }
    drawTopNav(e, 1, langCode);
    const int cx = kCL + 20, cw = kCR - cx;
    std::vector<int> any = viewItems(View::Deals, 0);
    int y = 44;
    if (sale) {
        if (baked) {
            y = 520;   // the artwork already says it: no title over it, pills low under its logo
        } else {
            // text sits low (close to the shelf) so the top of the artwork stays clear
            kickerText(e, L, cx, 318, saleKicker(lang), kGD);
            bigTitle(e, L, saleTitle(lang), cx, 1300, 358, L.rtl ? 88 : 96, false);
            y = 496;
        }
        pageTitle(e, L, tr("nav_deals_t", lang), cx, 30);
        int x = cx;
        x += discountPill(e, L, x, y, 64, saleDiscount());
        if (x > cx) x += 16;
        const int cwid = countdownBox(e, L, lang, x, y, 64, true);
        if (cwid) x += cwid + 22;
        const std::string sub = saleSubtitle(lang);
        if (!sub.empty()) boxText(e, L, sub, x, kCR - x, y + 18, 24, kCHIPTX);
    } else {
        pageTitle(e, L, tr("nav_deals_t", lang), cx, 44);
    }
    if (!sale && any.empty()) {
        emptyState(e, L, cx, 200, cw, 600, 11, tr("deals_empty_t", lang), tr("deals_empty_s", lang));
        drawLegendBar(e, {{"\xE2\x97\x8B", "back"}, {"\xE2\x96\xB3", "search"}}, langCode);
        return;
    }
    tabRow(e, L, cx, 636, {tr("chip_all", lang), tr("chip_addons", lang), tr("chip_worlds", lang),
                           tr("chip_under100", lang), tr("chip_biggest", lang)}, filterIndex, 0, focus);
    const std::vector<int> items = viewItems(View::Deals, filterIndex);
    if (items.empty()) emptyState(e, L, cx, 694, cw, 290, 11, tr(any.empty() ? "deals_soon" : "empty_t", lang),
                                  tr(any.empty() ? "deals_soon_sub" : "empty_s", lang));
    else cardGrid(e, L, items, scrollOf(View::Deals), 5, focus, cx, 684, cw, 1010, lang, true);
    drawLegendBar(e, {{"\xE2\x9C\x95", "open"}, {"\xE2\x97\x8B", "back"}, {"\xE2\x96\xB3", "search"}}, langCode);
}

// ============================================================================
//  Store — sort pill, banner, 7 categories, 2x4 grid (no app/QR tile)
// ============================================================================
void drawStore(UiEngine& e, int focus, int filterIndex, const std::string& langCode) {
    Lay L; L.rtl = (langCode == "ar");
    const std::string& lang = langCode;
    backdrop(e);
    drawTopNav(e, 2, langCode);
    const bool sale = saleActive();
    const int cx = kCL + 20, cw = kCR - cx;
    pageTitle(e, L, tr("nav_store", lang), cx, 44);
    {   // ---- sort pill (focus 16): Popular / Newest / Top rated — ✕ cycles it (setStoreSort) ----
        static const char* kSortKeys[3] = {"sort_popular", "sort_newest", "sort_rating"};
        const std::string lab = tr("sort_by", lang) + ":  " + tr(kSortKeys[storeSort()], lang);
        // v6.7: label + chevron centred together, equal padding on both sides
        std::vector<GPiece> g;
        g.push_back(gText(e, lab, 21, kTX, L.rtl, 14));
        g.push_back(gIcon(17, 10, [&](int vx, int vy) {   // small chevron pointing down
            for (int i = 0; i <= 7; ++i) { e.fillRect(vx + i, vy + i, 3, 3, kMU); e.fillRect(vx + 14 - i, vy + i, 3, 3, kMU); }
        }));
        const int sw = 26 + groupW(g) + 26, sh = 52, sxl = 1490 - sw, sy = 46;
        const int srx = L.X(sxl, sw);
        e.drawRoundRect(srx, sy, sw, sh, sh / 2, ink(gLight ? 8 : 12));
        e.drawRoundBorder(srx, sy, sw, sh, sh / 2, 2, ink(gLight ? 30 : 34));
        drawGroupCentered(e, L, g, srx + sw / 2, sy + sh / 2);
        if (focus == 18) focusRing(e, srx, sy, sw, sh, sh / 2);
    }

    // ---- banner (focus 8): sale strip (art as backdrop of the strip) or the newest item ----
    int bnY = 132, bnH = 0;
    if (sale) {
        const std::string art = stripArt(e);
        if (!art.empty()) {
            bnH = 128;                              // fixed strip; wide art is cover-cropped around 40% height
            const int brx = L.X(cx, cw);
            e.dropShadow(brx, bnY + 6, cw, bnH, 22, 14, gLight ? 50 : 120);
            coverImage(e, art, brx, bnY, cw, bnH, 22, 0.5f, 0.4f);
            const int gw0 = cw * 45 / 100;          // shade only behind the text, the rest of the art stays bright
            if (L.rtl) e.drawRoundGradH(brx + cw - gw0, bnY, gw0, bnH, 22, SDL_Color{8, 9, 11, 0}, SDL_Color{8, 9, 11, 215});
            else e.drawRoundGradH(brx, bnY, gw0, bnH, 22, SDL_Color{8, 9, 11, 215}, SDL_Color{8, 9, 11, 0});
            const int ty = bnY + bnH / 2 - 34;
            kickerText(e, L, cx + 36, ty, saleTitle(lang), SDL_Color{255, 214, 102, 255});
            const std::string sub = saleSubtitle(lang).empty() ? saleKicker(lang) : saleSubtitle(lang);
            boxText(e, L, sub, cx + 36, 760, ty + 36, 26, kWHT);
            const int w = countdownBox(e, L, lang, 0, 0, 52, false, false);
            if (w) countdownBox(e, L, lang, cx + cw - 30 - w, bnY + (bnH - 52) / 2, 52, false);
            e.drawRoundBorder(brx, bnY, cw, bnH, 22, 2, ink(26));
        }
    } else if (!gCat.empty()) {
        const AddonItem& nw = gCat.front();
        bnH = 128;
        const int brx = L.X(cx, cw);
        e.dropShadow(brx, bnY + 6, cw, bnH, 22, 14, gLight ? 50 : 120);
        // v0.39 (user order): NO square cover thumbnail in the strip — clean surface + text + button
        e.drawRoundRect(brx, bnY, cw, bnH, 22, kSF);
        if (L.rtl) e.drawRoundGradH(brx, bnY, cw, bnH, 22, SDL_Color{74, 168, 255, 30}, SDL_Color{74, 168, 255, 0});
        else e.drawRoundGradH(brx, bnY, cw, bnH, 22, SDL_Color{74, 168, 255, 0}, SDL_Color{74, 168, 255, 30});
        kickerText(e, L, cx + 36, bnY + 30, tr("newest_k", lang), SDL_Color{255, 214, 102, 255});
        boxText(e, L, nw.title, cx + 36, 900, bnY + 64, 30, kWHT);
        const std::string vt = tr("view_addon", lang);
        const int vw = textW(e, L, vt, 22) + 70;
        button(e, L, cx + cw - 30 - vw, bnY + (bnH - 60) / 2, vw, 60, vt, 0, 22, 18);
        e.drawRoundBorder(brx, bnY, cw, bnH, 22, 2, ink(26));
    }
    if (bnH > 0 && focus == 10) focusRing(e, L.X(cx, cw), bnY, cw, bnH, 22, false);   // v0.35: frame only

    // ---- categories (focus 9-15) with real counts: All Addons Worlds Free Epic Legendary New ----
    const int catX = cx, catW = 270, catY = bnH > 0 ? bnY + bnH + 34 : 150;
    const std::string cats[7] = {tr("chip_all", lang), tr("chip_addons", lang), tr("chip_worlds", lang), tr("chip_free", lang),
                                 tr("chip_epic", lang), tr("chip_legendary", lang), tr("chip_new", lang)};
    for (int i = 0; i < 7; ++i) {
        const int y = catY + i * 68;
        const bool on = i == filterIndex;
        const bool foc = focus == 11 + i;
        boxText(e, L, cats[i], catX + 22, catW - 90, y + 16, 22, on || foc ? kTX : kCHIPTX);
        const int n = static_cast<int>(viewItems(View::Store, i).size());
        endText(e, L, num(n), catX, catW - 20, y + 18, 19, on || foc ? kTX : kMU);
        // v0.30: category tags are marked with an underline bar only (no selection frame)
        if (foc) e.drawRoundRect(L.X(catX + 18, catW - 36), y + 50, catW - 36, 6, 3, SDL_Color{255, 214, 90, 255});
        else if (on) e.drawRoundRect(L.X(catX + 18, catW - 36), y + 51, catW - 36, 4, 2, kBLl);
    }

    // ---- grid 2 x 5 (focus 0-9) — v0.36 five whole cards per row ----
    const int gx = cx + catW + 32, gw = kCR - gx, gap = 26, cols = 5;
    const int gcw = (gw - gap * (cols - 1)) / cols, ih = gcw * 9 / 16;
    const int ch = vcardHeight(ih, true);
    const std::vector<int> items = viewItems(View::Store, filterIndex);
    const int first = std::max(0, std::min(scrollOf(View::Store), std::max(0, static_cast<int>(items.size()) - 1)));
    const int shown = std::min(10, static_cast<int>(items.size()) - first);
    if (items.empty()) {
        emptyState(e, L, gx, catY, gw, std::min(560, 960 - catY), 12, tr(gCat.empty() ? "home_empty_t" : "empty_t", lang),
                   tr(gCat.empty() ? "home_empty_s" : "empty_s", lang));
    } else {
        const int gyMax = catY + (ch + gap) + ch / 2;   // v0.58: full row + soft-faded half peek
        for (int i = 0; i < shown; ++i) {
            const int col = i % cols, row = i / cols;
            const int ry = catY + row * (ch + gap);
            if (ry >= gyMax) break;
            const AddonItem& it = gCat[static_cast<std::size_t>(items[static_cast<std::size_t>(first + i)])];
            vcard(e, L, gx + col * (gcw + gap), ry, gcw, ih, it, focus == i, lang, true, true, sale);
        }
        // v0.65d («شوف هذا الظل الغيه الذي مخرب البطاقات»): NO dark fade box — cards show fully
    }
    drawLegendBar(e, {{"\xE2\x9C\x95", "open"}, {"\xE2\x97\x8B", "back"}, {"\xE2\x96\xB3", "search"}}, langCode);
}

// ============================================================================
//  Addon / world page — tier access first, AC unlock as the alternative (replaces Share)
// ============================================================================
void drawAddonDetail(UiEngine& e, int focus, int idx, DemoState state, int percent, const std::string& langCode,
                     const std::string& notice) {
    Lay L; L.rtl = (langCode == "ar");
    const std::string& lang = langCode;
    if (idx < 0 || idx >= static_cast<int>(gCat.size())) {   // item disappeared (unpublished) -> calm empty page
        backdrop(e);
        drawTopNav(e, 2, langCode, -1, false);   // v0.59: no side rail on the addon page
        emptyState(e, L, kCL + 20, 200, kCR - kCL - 20, 600, 12, tr("empty_t", lang), tr("empty_s", lang));
        drawLegendBar(e, {{"\xE2\x97\x8B", "back"}}, langCode);
        return;
    }
    const AddonItem& it = gCat[static_cast<std::size_t>(idx)];
    const Access acc = accessFor(it);
    const bool acOffer = acUnlockOffered(it);
    {   // PERFORMANCE: the full-screen cover + 3 shade layers are composed ONCE per item/theme/direction and
        // kept as one opaque image ("__bg_detail"); later frames are a single straight copy.
        static std::string bakedFor;
        const std::string want = it.id + (gLight ? "|L" : "|D") + (L.rtl ? "|R" : "|N") +
                                 (!it.cover.empty() && e.hasImage(it.cover) ? "|1" : "|0");
        if (bakedFor == want && e.hasImage("__bg_detail")) {
            e.drawImage("__bg_detail", 0, 0, 1920, 1080);
        } else {
            e.clear(kBG);
            itemCover(e, it, 0, 0, 1920, 1080, 0);
            leadShade(e, L, 1080, true);
            bottomShade(e);
            e.fillGradV(0, 0, 1920, 150, shade(140), shade(0));
            const auto& fb = e.framebuffer();
            std::vector<std::uint8_t> rgba(fb.size() * 4);
            for (std::size_t i = 0; i < fb.size(); ++i) {
                const std::uint32_t p = fb[i];
                rgba[i * 4] = static_cast<std::uint8_t>(p >> 16); rgba[i * 4 + 1] = static_cast<std::uint8_t>(p >> 8);
                rgba[i * 4 + 2] = static_cast<std::uint8_t>(p); rgba[i * 4 + 3] = 255;
            }
            e.addImageRGBA("__bg_detail", 1920, 1080, rgba.data());
            bakedFor = want;
        }
    }
    drawTopNav(e, 2, langCode, -1, false);   // v0.59: no side rail on the addon page
    const int x0 = kCL + 20, lw = 860;
    int y = 124;
    {   // breadcrumb
        const std::string a = tr("nav_store", lang), b = tr(it.isWorld ? "chip_worlds" : "chip_addons", lang);
        const int aw = textW(e, L, a, 19);
        boxText(e, L, a, x0, aw + 4, y, 19, kMU);
        chevron(e, L.X(x0 + aw + 14, 8), y + 5, 7, !L.rtl, 2, kMU);
        boxText(e, L, b, x0 + aw + 34, 400, y, 19, kCHIPTX);
    }
    y += 40;
    {   // title (pixel, up to 2 lines)
        std::string t = it.title;
        if (!L.rtl) for (auto& ch : t) if (ch >= 'a' && ch <= 'z') ch = static_cast<char>(ch - 32);
        int px = L.rtl ? 84 : 92;
        auto lines = wrapLines(e, t, px, lw, L.rtl, FontRole::Pixel);
        while (lines.size() > 2 && px > 52) { px -= 8; lines = wrapLines(e, t, px, lw, L.rtl, FontRole::Pixel); }
        for (std::size_t i = 0; i < lines.size() && i < 2; ++i) {
            if (!gLight) boxText(e, L, lines[i], x0 + 4, lw, y + 6, px, SDL_Color{0, 0, 0, 90}, FontRole::Pixel);
            boxText(e, L, lines[i], x0, lw, y, px, kTX, FontRole::Pixel);
            y += px + 10;
        }
    }
    y += 10;
    if (!it.creator.empty()) {   // creator: avatar + name — v0.59: the studio shows the FOX logo
        const int s = 44, ax = L.X(x0, s);
        std::string low = it.creator;
        for (char& c : low) if (c >= 'A' && c <= 'Z') c = static_cast<char>(c + 32);
        const bool studio = low.find("sliver fox") != std::string::npos ||
                            low.find("silverfoxstudio") != std::string::npos;
        if (studio && e.hasImage("fox_avatar")) {
            e.drawRoundRect(ax, y, s, s, 12, SDL_Color{198, 92, 42, 255});
            e.drawImageRounded("fox_avatar", ax, y, s, s, 12);
        } else {
            unsigned hsh = 0;
            for (unsigned char ch : it.creator) hsh = hsh * 31u + ch;
            static const SDL_Color kAv[5] = {{20, 184, 166, 255}, {59, 130, 246, 255}, {168, 85, 247, 255}, {245, 158, 11, 255}, {239, 68, 68, 255}};
            e.drawRoundRect(ax, y, s, s, 12, kAv[hsh % 5]);
            std::string ini;
            for (std::size_t i = 0; i < it.creator.size() && ini.size() < 2; ++i) {
                const unsigned char ch = static_cast<unsigned char>(it.creator[i]);
                const bool up = ch >= 'A' && ch <= 'Z', lo = ch >= 'a' && ch <= 'z', dg = ch >= '0' && ch <= '9';
                if ((up || lo || dg) && (i == 0 || it.creator[i - 1] == ' ' || up))
                    ini += static_cast<char>(lo ? ch - 32 : ch);
            }
            if (ini.empty()) ini = "@";
            e.drawTextCentered(ini, ax + s / 2, y + s / 2, 19, kWHT, FontRole::Body, false);
        }
        boxText(e, L, it.creator, x0 + s + 14, 600, y + 9, 23, kTX);
        y += 62;
    }
    {   // meta row — only real values: tier · rating · downloads · size · version · new
        int mx = x0;
        mx += tierChip(e, L, mx, y - 4, it.tier, lang, 34) + 24;
        // v6.7: every piece of the row (chip, icons, numbers, NEW pill) is centred on ONE line: cy
        const int cy = y + 13;
        auto meta = [&](void (*ic)(UiEngine&, int, int, int, SDL_Color), SDL_Color icc, const std::string& t) {
            if (ic) { ic(e, L.X(mx, 22), cy - 11, 22, icc); mx += 22 + 8; }
            const int tw = e.inkWidth(t, 20, FontRole::Body, L.rtl);
            e.drawTextCentered(t, L.X(mx, tw) + tw / 2, cy, 20, kCHIPTX, FontRole::Body, L.rtl);
            mx += tw + 30;
        };
        if (it.ratingCount > 0) { char b[24]; std::snprintf(b, sizeof b, "%.1f", it.rating); meta(icStar, kGD, b); }
        if (it.downloads > 0) meta(icDownloadArrow, kBLl, fmtCompact(it.downloads));
        if (it.fileSize > 0) meta(nullptr, kMU, fmtSize(it.fileSize));
        if (!it.version.empty()) meta(nullptr, kMU, "v" + it.version);
        if (isNewItem(it)) {
            const std::string b = tr("badge_new", lang);
            const int bw = e.inkWidth(b, 16, FontRole::Body, L.rtl) + 24;
            e.drawRoundRect(L.X(mx, bw), cy - 15, bw, 30, 8, SDL_Color{16, 185, 129, 255});
            e.drawTextCentered(b, L.X(mx, bw) + bw / 2, cy, 16, kWHT, FontRole::Body, L.rtl);
        }
    }
    y += 50;
    if (!it.description.empty()) y = paragraphAt(e, L, it.description, x0, 780, y, 24, kCHIPTX, 3, 12) + 14;
    {   // tags: type + category
        int tx = x0;
        std::vector<std::string> tags = {tr(it.isWorld ? "type_world" : "type_addon", lang)};
        if (!it.category.empty() && it.category != "other") tags.push_back(it.category);
        for (const auto& t : tags) {
            const int w = textW(e, L, t, 18) + 32;
            chipBox(e, L.X(tx, w), y, w, 38, ink(14), ink(30));
            e.drawTextCentered(t, L.X(tx, w) + w / 2, y + 19, 18, kCHIPTX, FontRole::Body, L.rtl);
            tx += w + 10;
        }
    }
    y += 38 + 26;

    // ---- action box ----
    const int bxW = 660, pad = 28;
    const bool lowBal = acOffer && gAcc.signedIn && gAcc.acBalance < it.acPrice;
    const bool dlLine = gAcc.signedIn && acc == Access::Download && gAcc.downloadLimit > 0;
    // v6.6: the panel is exactly as tall as its content (no empty strip when there is no hint line)
    const bool hintLine = lowBal || dlLine || acc == Access::Download || acc == Access::NeedSignIn;
    int bxH = pad + 34 + 18 + 80 + (acOffer ? 14 + 72 : 0) + (hintLine ? 16 + 26 : 0) + pad;
    if (lowBal && dlLine) bxH += 32;
    const int brx = L.X(x0, bxW);
    e.dropShadow(brx, y + 10, bxW, bxH, 26, 24, gLight ? 60 : 170);
    e.drawRoundRect(brx, y, bxW, bxH, 26, gLight ? SDL_Color{255, 255, 255, 240} : SDL_Color{21, 24, 29, 235});
    e.drawRoundBorder(brx, y, bxW, bxH, 26, 2, ink(24));
    int yy = y + pad;
    {   // access line
        std::string t;
        SDL_Color c = kCHIPTX;
        if (acc == Access::NeedSignIn) { t = it.tier == Tier::Free ? tr("acc_free", lang) : fmt1(tr("acc_need_s", lang), tierName(it.tier, lang)); c = kTX; }
        else if (acc == Access::Download) { t = it.unlocked && rankOf(gAcc.plan) < rankOf(it.tier) ? tr("acc_unlocked", lang) : (it.tier == Tier::Free ? tr("acc_free", lang) : tr("acc_included", lang)); c = gLight ? SDL_Color{10, 85, 160, 255} : kGRN2; }
        else { t = fmt1(tr("acc_need", lang), tierName(it.tier, lang)); c = it.tier == Tier::Legendary ? kGD : kEPICT; }
        if (acc == Access::Download) { e.drawCircle(L.X(x0 + pad, 28) + 14, yy + 15, 14, kGRN); icCheck(e, L.X(x0 + pad, 28) + 6, yy + 7, 16, kWHT); }
        else lockIcon(e, L.X(x0 + pad, 28), yy + 1, 28, c);
        boxText(e, L, t, x0 + pad + 42, bxW - 2 * pad - 42, yy + 3, 22, c);
    }
    yy += 34 + 18;
    const int bw = bxW - 2 * pad, bh = 80, bx = x0 + pad;
    if (acc == Access::NeedSignIn) {
        button(e, L, bx, yy, bw, bh, tr("signin_dl", lang), 0, 28, 20);
    } else if (acc == Access::NeedPlan) {
        button(e, L, bx, yy, bw, bh, fmt1(tr("get_tier", lang), tierName(it.tier, lang)), it.tier == Tier::Legendary ? 2 : 0, 27, 20);
    } else if (state == DemoState::Downloading) {
        button(e, L, bx, yy, bw, bh, tr("downloading", lang) + "  " + num(percent) + "%", 0, 26, 20);
        const int pbw = bw - 40, pw = std::max(8, pbw * std::max(0, std::min(100, percent)) / 100);
        const int pbx = L.X(bx + 20, pbw);
        e.drawRoundRect(pbx, yy + bh - 16, pbw, 6, 3, SDL_Color{0, 0, 0, 70});
        e.drawRoundRect(L.rtl ? pbx + pbw - pw : pbx, yy + bh - 16, pw, 6, 3, SDL_Color{6, 24, 14, 200});
    } else if (state == DemoState::Installing) {
        button(e, L, bx, yy, bw, bh, tr("installing", lang), 2, 28, 20);
    } else if (state == DemoState::Queued) {
        button(e, L, bx, yy, bw, bh, tr("queued_state", lang), 3, 26, 20);   // v0.41: honest "waiting for Minecraft"
    } else if (state == DemoState::Installed || it.installed) {
        button(e, L, bx, yy, bw, bh, tr("installed", lang), 3, 28, 20);
    } else {
        button(e, L, bx, yy, bw, bh, "", 0, 28, 20);   // v6.7: arrow + label centred together in the button
        const SDL_Color dk{6, 24, 14, 255};
        std::vector<GPiece> g;
        g.push_back(gIcon(28, 28, [&](int gx, int gy) { icDownloadArrow(e, gx, gy, 28, dk); }, 12));
        g.push_back(gText(e, tr("download", lang), 28, dk, L.rtl));
        drawGroupCentered(e, L, g, L.X(bx, bw) + bw / 2, yy + bh / 2);
    }
    if (focus == 0 || (!acOffer && focus == 1)) focusRing(e, L.X(bx, bw), yy, bw, bh, 20);
    yy += bh;
    if (acOffer) {   // AC alternative (replaces Share). Hidden for Legendary and when the item has no AC price.
        yy += 14;
        const int ah = 72, arx = L.X(bx, bw);
        e.drawRoundRect(arx, yy, bw, ah, 20, gLight ? SDL_Color{255, 246, 220, 255} : SDL_Color{48, 36, 10, 220});
        e.drawRoundBorder(arx, yy, bw, ah, 20, 2, SDL_Color{255, 201, 64, static_cast<Uint8>(lowBal ? 90 : 190)});
        const std::string lab = fmt1(tr("unlock_for", lang), formatAC(it.acPrice));
        // v6.7: coin + label (+ old price struck through) centred together as one group
        std::vector<GPiece> g;
        g.push_back(gIcon(36, 36, [&](int gx, int gy) { e.drawImage("icon_coin", gx, gy, 36, 36); }, 12));
        g.push_back(gText(e, lab, 26, kGD, L.rtl, 14));
        if (it.acPriceOld > it.acPrice) {
            const std::string old = formatAC(it.acPriceOld);
            const int ow = e.inkWidth(old, 20), cyy = yy + ah / 2;
            g.push_back(gIcon(ow, 22, [&, old, ow, cyy](int gx, int) {
                e.drawTextCentered(old, gx + ow / 2, cyy, 20, kMU, FontRole::Body, false);
                e.fillRect(gx, cyy, ow, 2, kMU);
            }));
        }
        drawGroupCentered(e, L, g, arx + bw / 2, yy + ah / 2);
        if (focus == 1) focusRing(e, arx, yy, bw, ah, 20);
        yy += ah;
    }
    yy += 16;
    {   // hints (only real values)
        if (lowBal) { boxText(e, L, fmt1(tr("bal_low", lang), formatAC(gAcc.acBalance)), bx, bw, yy, 19, kWARN); yy += 32; }
        if (dlLine) boxText(e, L, fmtDD(tr("dl_used", lang), gAcc.downloadsUsed, gAcc.downloadLimit) + " / " + periodName("day", lang), bx, bw, yy, 19, kMU);
        else if (!lowBal && acc == Access::Download) {
            glyph(e, 2, L.X(bx, 24), yy - 1, 24, kMU, kMU, false);
            boxText(e, L, tr("autolaunch_hint", lang), bx + 34, bw - 34, yy, 18, kMU);
        } else if (!lowBal && acc == Access::NeedSignIn) {
            boxText(e, L, tr("acc_guest", lang), bx, bw, yy, 18, kMU);
        }
    }
    if (!notice.empty()) {
        const int nw = textW(e, L, notice, 21) + 60, ny = y + bxH + 18;
        const int nrx = L.X(x0, nw);
        e.drawRoundRect(nrx, ny, nw, 50, 25, SDL_Color{251, 76, 110, 34});
        e.drawRoundBorder(nrx, ny, nw, 50, 25, 2, SDL_Color{251, 76, 110, 160});
        boxText(e, L, notice, x0 + 30, nw - 40, ny + 13, 21, kERT);
    }

    // ---- trailing column: facts from the data ----
    const int rw = 720, rxl = kCR - rw;
    std::vector<std::pair<std::string, std::string>> facts;
    if (it.fileSize > 0) facts.push_back({tr("f_size", lang), fmtSize(it.fileSize)});
    if (!it.version.empty()) facts.push_back({tr("f_version", lang), it.version});
    if (it.createdAt > 0) facts.push_back({tr("f_published", lang), fmtDate(it.createdAt)});
    if (it.downloads > 0) facts.push_back({tr("f_downloads", lang), formatAC(it.downloads)});
    const int fY = 1080 - 112 - 86;   // the cover is already the full-screen backdrop — no second copy
    if (!facts.empty()) {
        const int frx = L.X(rxl, rw);
        e.drawRoundRect(frx, fY, rw, 86, 20, gLight ? SDL_Color{255, 255, 255, 235} : SDL_Color{21, 24, 29, 220});
        e.drawRoundBorder(frx, fY, rw, 86, 20, 2, ink(22));
        const int n = static_cast<int>(facts.size()), cwn = rw / n;
        for (int i = 0; i < n; ++i) {
            const int fx = rxl + i * cwn;
            if (i) e.fillRect(L.X(fx, 1), fY + 18, 1, 50, ink(22));
            boxText(e, L, facts[static_cast<std::size_t>(i)].first, fx + 22, cwn - 30, fY + 16, 16, kMU);
            boxText(e, L, facts[static_cast<std::size_t>(i)].second, fx + 22, cwn - 30, fY + 42, 22, kTX);
        }
    }
    drawLegendBar(e, {{"\xE2\x9C\x95", "select"}, {"\xE2\x97\x8B", "back"}, {"\xE2\x96\xB3", "search"}}, langCode);
}

// ============================================================================
//  Search (△) — real catalog search
// ============================================================================
void drawSearch(UiEngine& e, int focus, const std::string& query, int filterIndex, const std::string& langCode) {
    Lay L; L.rtl = (langCode == "ar");
    const std::string& lang = langCode;
    backdrop(e);
    drawTopNav(e, 7, langCode);
    const int cx = kCL + 20, fw = 1460 - cx, fh = 84, fy = 34;
    {
        const int frx = L.X(cx, fw);
        e.drawRoundRect(frx, fy, fw, fh, 26, gLight ? SDL_Color{255, 255, 255, 255} : SDL_Color{8, 10, 13, 230});
        // v0.35: NO selection frame and NO gleam on the search field — the gleam belongs to
        // buttons only. The blinking-caret bar marks the focus instead.
        e.drawRoundBorder(frx, fy, fw, fh, 26, 2, ink(30));
        // v6.9: icon + text + caret as ONE group, flush to the start edge (left in LTR, right in RTL), centred vertically
        const bool empty = query.empty();
        const std::string& t   = empty ? tr("search_type", lang) : query;
        const int        tpx   = empty ? 25 : 30;
        const SDL_Color  tc    = empty ? kMU : kTX;
        std::vector<GPiece> g;
        g.push_back(gIcon(34, 34, [&](int gx, int gy) { glyph(e, 8, gx, gy, 34, kBLl, kBLl, false); }, 18));
        if (empty) {
            g.push_back(gText(e, t, tpx, tc, L.rtl));
        } else {
            g.push_back(gText(e, t, tpx, tc, L.rtl, 6));
        }
        if (!empty || focus == 0)
            g.push_back(gIcon(3, 40, [&](int gx, int gy) { e.fillRect(gx, gy, 3, 40, kBLl); }));   // caret
        const int gw = groupW(g);
        int sx = L.rtl ? frx + fw - 24 - gw : frx + 24;                 // start edge (pay tight to it)
        if (gw > fw - 48) sx = L.rtl ? frx + 24 : frx + fw - 24 - gw;   // long text: keep the end visible
        drawGroupAt(e, L, g, sx, fy + fh / 2);
    }
    tabRow(e, L, cx, 160, {tr("chip_all", lang), tr("chip_addons", lang), tr("chip_worlds", lang)}, filterIndex, 1, focus);
    const std::vector<int> res = searchCatalog(query, filterIndex);
    if (gCat.empty()) {
        emptyState(e, L, cx, 260, kCR - cx, 600, 8, tr("home_empty_t", lang), tr("home_empty_s", lang));
    } else if (res.empty()) {
        emptyState(e, L, cx, 260, kCR - cx, 600, 8, fmt1(tr("no_results_q", lang), query), tr("no_results_sub", lang));
    } else {
        const std::string head = query.empty() ? tr("most_dl", lang) : fmtD(tr("n_results", lang), static_cast<int>(res.size()));
        sectionTitle(e, L, head, cx, 900, 250, 30);
        const int gap = 22, w = (kCR - cx - 4 * gap) / 5, ih = w * 9 / 16;
        const int first = std::max(0, std::min(scrollOf(View::Search), static_cast<int>(res.size()) - 1));
        for (int i = 0; i < 5 && first + i < static_cast<int>(res.size()); ++i)
            vcard(e, L, cx + i * (w + gap), 310, w, ih, gCat[static_cast<std::size_t>(res[static_cast<std::size_t>(first + i)])], focus == 4 + i, lang, true, true, false);
    }
    drawLegendBar(e, {{"\xE2\x9C\x95", focus == 0 ? "press_x_type" : "select"}, {"\xE2\x97\x8B", "back"}}, langCode);
}

// ============================================================================
//  Profile + AC wallet (one place)
// ============================================================================
void drawMine(UiEngine& e, int focus, const std::string& langCode) {
    // v0.57: «التحديثات والتنزيلات» — the Windows-Updates-style list the user
    // asked for: icon FROM the pack itself (pack_icon.png captured at install),
    // name + studio, type, modified date, and an «فتح» action per row.
    Lay L; L.rtl = (langCode == "ar");
    const std::string& lang = langCode;
    backdrop(e);
    drawTopNav(e, 6, langCode);
    const int cx = kCL + 20, cw = kCR - cx;
    // v0.65c («التي في الدوائر الزرقاء تحذفها» + «DOWNLOADS بس ذي خليها»):
    // the title stays but says ONLY «Downloads» — no subtitle, no «Check for updates».
    pageTitle(e, L, tr("mine_title", lang), cx, 36);
    // section label
    e.drawText(tr("mine_recent", lang), L.X(cx, e.measureText(tr("mine_recent", lang), 26, FontRole::Body, L.rtl)),
               118, 26, kTX, FontRole::Body, L.rtl);

    const std::vector<int> items = viewItems(View::Mine, 0);
    const int rowH = 100, pitch = 110, top = 168;
    const int scroll = scrollOf(View::Mine);
    const int visible = 7;
    if (items.empty()) {
        emptyState(e, L, cx, top, cw, 964 - top, 12, tr("mine_empty_t", lang), tr("mine_empty_s", lang));
        drawLegendBar(e, {{"\xE2\x9C\x95", "select"}, {"\xE2\x97\x8B", "back"}}, langCode);
        return;
    }
    for (int r = 0; r < visible; ++r) {
        const int k = scroll + r;
        if (k >= (int)items.size()) break;
        const AddonItem& it = gCat[(std::size_t)items[(std::size_t)k]];
        const int y = top + r * pitch;
        const int rx = L.X(cx, cw);
        const bool foc = (focus == k);   // v0.65c: rows start at focus 0 (no check button)
        surface(e, rx, y, cw, rowH, 20, foc ? 3 : 0);
        // --- pack icon (the addon's own art), fallback = studio tile ---
        // icon slug = dest_folder from pack_targets minus its _uuid8 suffix —
        // exactly what the installer names the captured pack_icon.png.
        std::string islug;
        {
            const std::string& j = it.packTargetsRaw;
            const std::size_t pos = j.find("\"dest_folder\"");
            if (pos != std::string::npos) {
                const std::size_t c = j.find(':', pos);
                const std::size_t q1 = (c == std::string::npos) ? std::string::npos : j.find('"', c + 1);
                const std::size_t q2 = (q1 == std::string::npos) ? std::string::npos : j.find('"', q1 + 1);
                if (q2 != std::string::npos) {
                    islug = j.substr(q1 + 1, q2 - q1 - 1);
                    if (islug.size() >= 9 && islug[islug.size() - 9] == '_')
                        islug = islug.substr(0, islug.size() - 9);
                }
            }
        }
        const std::string inm = "ic_" + islug;
        const int ix = rx + 18, iy = y + (rowH - 64) / 2;
        if (!islug.empty() && !e.hasImage(inm)) {
            e.loadImageFile(inm, "/data/AddonStation/icons/" + islug + ".png", 128, 128);
        }
        if (!islug.empty() && e.hasImage(inm)) {
            e.drawImageRounded(inm, ix, iy, 64, 64, 16);
        } else {
            const SDL_Color tc = it.tier == Tier::Legendary ? SDL_Color{255, 201, 64, 255}
                                 : it.tier == Tier::Epic ? SDL_Color{176, 132, 255, 255}
                                 : SDL_Color{74, 168, 255, 255};
            e.drawRoundRect(ix, iy, 64, 64, 16, tc);
            const std::string lt = it.title.substr(0, 1);
            const int tw = e.measureText(lt, 30, FontRole::Pixel, false);
            e.drawText(lt, ix + (64 - tw) / 2, iy + 14, 30, SDL_Color{12, 14, 18, 255}, FontRole::Pixel, false, false);
        }
        // --- name + studio ---
        const int tx = ix + 64 + 20;
        int nameW = cw * 40 / 100;
        std::string nm = it.title;
        while (nm.size() > 4 && e.measureText(nm, 25, FontRole::Body, L.rtl) > nameW) nm.pop_back();
        e.drawText(nm, L.X(tx, nameW), y + 22, 25, kTX, FontRole::Body, L.rtl);
        const std::string studio = it.creator.empty() ? "silverfoxstudio" : it.creator;
        e.drawText(studio, L.X(tx, nameW), y + 58, 19, kMU, FontRole::Body, L.rtl);
        // --- type ---
        const int colX = rx + cw * 52 / 100, colW = cw * 16 / 100;
        e.drawText(tr(it.isWorld ? "chip_worlds" : "chip_addons", lang),
                   L.X(colX, colW), y + (rowH - 22) / 2, 21, kMU, FontRole::Body, L.rtl);
        // --- modified date (icon file mtime = install moment) ---
        const int datX = rx + cw * 68 / 100, datW = cw * 18 / 100;
        {
            std::time_t mt = 0;
            struct stat stt {};
            if (!islug.empty() &&
                ::stat(("/data/AddonStation/icons/" + islug + ".png").c_str(), &stt) == 0) mt = stt.st_mtime;
            std::string dt = tr("mine_modified", lang);
            if (mt > 0) dt += " " + fmtDate(mt);
            e.drawText(dt, L.X(datX, datW), y + (rowH - 21) / 2, 20, kMU, FontRole::Body, L.rtl);
        }
        // --- «فتح» action button ---
        {
            const int bw = 132, bh = 52, bx = rx + cw - 24 - bw, by = y + (rowH - bh) / 2;
            const int brx = L.X(bx, bw);
            e.drawRoundRect(brx, by, bw, bh, 14, foc ? SDL_Color{74, 168, 255, 255} : ink(16));
            if (foc) e.drawRoundBorder(brx, by, bw, bh, 14, 2, SDL_Color{255, 255, 255, 60});
            const std::string bl = tr("mine_open", lang);
            const int tw = e.measureText(bl, 21, FontRole::Body, L.rtl);
            e.drawText(bl, brx + (bw - tw) / 2, by + (bh - 24) / 2, 21,
                       foc ? SDL_Color{12, 16, 22, 255} : kTX, FontRole::Body, L.rtl);
        }
        if (foc) focusRing(e, rx, y, cw, rowH, 20);
    }
    drawLegendBar(e, {{"\xE2\x9C\x95", "select"}, {"\xE2\x97\x8B", "back"}}, langCode);
}

void drawProfile(UiEngine& e, int focus, const std::string& langCode) {
    Lay L; L.rtl = (langCode == "ar");
    const std::string& lang = langCode;
    // Profile (guest AND signed in): the cards sit on the bg_splash artwork (baked once, then a single blit)
    bakedBackdrop(e, "profile|splash", [&] {
        backdrop(e);
        coverImage(e, pageArt(e, "bg_splash", "bg_welcome"), 0, 0, 1920, 1080, 0, 0.5f, 0.5f);
        e.fillRect(0, 0, 1920, 1080, shade(gLight ? 90 : 110));        // soft dim: art stays visible, cards stay crisp
        e.fillGradV(0, 0, 1920, 170, shade(190), shade(0));           // readable page title / top bar
        e.fillGradV(0, 900, 1920, 180, shade(0), shade(200));         // readable legend bar
    });
    drawTopNav(e, 5, langCode);
    const int cx = kCL + 20;
    pageTitle(e, L, tr("profile", lang), cx, 44);
    if (!gAcc.signedIn) {
        const int w = 1080, h = 640, xl = cx + (kCR - cx - w) / 2, y = 170;
        const int rx = L.X(xl, w);
        e.dropShadow(rx, y + 10, w, h, 30, 20, gLight ? 50 : 130);
        surface(e, rx, y, w, h, 30, 1);
        accountAvatar(e, rx + (w - 150) / 2, y + 56, 150, false);
        e.drawTextCentered(tr("guest_t", lang), rx + w / 2, y + 260, 38, kTX, FontRole::Body, L.rtl);
        const auto lines = wrapLines(e, tr("guest_s", lang), 25, w - 200, L.rtl);
        int yy = y + 314;
        for (std::size_t i = 0; i < lines.size() && i < 2; ++i) { e.drawTextCentered(lines[i], rx + w / 2, yy, 25, kMU, FontRole::Body, L.rtl); yy += 36; }
        {   // honest zero balance for guests
            const std::string bal = "0 AC";
            const int bw = e.measureText(bal, 28, FontRole::Body, false) + 70, bx = rx + (w - bw) / 2;
            e.drawRoundRect(bx, y + 400, bw, 56, 28, ink(12));
            e.drawImage("icon_coin", bx + 16, y + 412, 32, 32);
            e.drawText(bal, bx + 56, y + 413, 28, kGD, FontRole::Body, false, false);
        }
        const int bw = 520, bx = rx + (w - bw) / 2;
        button(e, L, L.rtl ? 1920 - bx - bw : bx, y + 500, bw, 84, tr("signin_code_btn", lang), 0, 28, 20);
        if (focus == 0) focusRing(e, bx, y + 500, bw, 84, 20);
        drawLegendBar(e, {{"\xE2\x9C\x95", "select"}, {"\xE2\x97\x8B", "back"}}, langCode);
        return;
    }
    // ---- identity card ----
    const int lw = 540, ly = 150, lh = 790;
    const int lrx = L.X(cx, lw);
    e.dropShadow(lrx, ly + 10, lw, lh, 30, 20, gLight ? 50 : 130);
    surface(e, lrx, ly, lw, lh, 30, 1);
    accountAvatar(e, lrx + (lw - 160) / 2, ly + 50, 160, true);
    const std::string nm = gAcc.displayName.empty() ? gAcc.username : gAcc.displayName;
    e.drawTextCentered(nm, lrx + lw / 2, ly + 262, 38, kTX, FontRole::Body, L.rtl);
    if (!gAcc.username.empty() && gAcc.username != nm) e.drawTextCentered("@" + gAcc.username, lrx + lw / 2, ly + 306, 22, kMU, FontRole::Body, false);
    {
        const int tw = tierChip(e, L, 0, 0, gAcc.plan, lang, 40, false);
        const int tx = lrx + (lw - tw) / 2;
        tierChip(e, L, L.rtl ? 1920 - tx - tw : tx, ly + 342, gAcc.plan, lang, 40);
        if (gAcc.planExpiresAt > 0 && gAcc.plan != Tier::Free)
            e.drawTextCentered(fmt1(tr("plan_until", lang), fmtDate(gAcc.planExpiresAt)), lrx + lw / 2, ly + 410, 20, kMU, FontRole::Body, L.rtl);
    }
    {
        const int sv[3] = {gAcc.downloads, gAcc.favorites, gAcc.installed};
        const char* sk[3] = {"stat_downloads", "stat_saved", "stat_installed"};
        const int sw = (lw - 2 * 36 - 2 * 14) / 3;
        for (int i = 0; i < 3; ++i) {
            const int sx = lrx + 36 + i * (sw + 14), sy = ly + 470;
            surface(e, sx, sy, sw, 150, 20, 2);
            e.drawTextCentered(formatAC(sv[i]), sx + sw / 2, sy + 58, 44, kTX, FontRole::Pixel, false);
            e.drawTextCentered(tr(sk[i], lang), sx + sw / 2, sy + 112, 19, kMU, FontRole::Body, L.rtl);
        }
    }
    // ---- wallet (merged here) ----
    const int wx = cx + lw + 36, ww = kCR - wx;
    const int wrx = L.X(wx, ww);
    e.dropShadow(wrx, ly + 10, ww, lh, 30, 20, gLight ? 50 : 130);
    surface(e, wrx, ly, ww, lh, 30, 1);
    kickerText(e, L, wx + 40, ly + 40, tr("ac_wallet", lang), kGD);
    {
        const std::string bal = formatAC(gAcc.acBalance);
        e.drawImage("icon_coin", L.X(wx + 40, 72), ly + 86, 72, 72);
        const int bw = e.measureText(bal, 80, FontRole::Pixel, false);
        e.drawText(bal, L.X(wx + 40 + 90, bw), ly + 80, 80, kGD, FontRole::Pixel, false, false);
        boxText(e, L, "AC", wx + 40 + 90 + bw + 14, 80, ly + 124, 26, kMU);
    }
    paragraphAt(e, L, tr("topup_phone", lang), wx + 40, ww - 80, ly + 190, 22, kMU, 2, 10);
    e.fillRect(wrx + 40, ly + 268, ww - 80, 1, ink(30));
    boxText(e, L, tr("history", lang), wx + 40, 600, ly + 290, 24, kTX);
    if (gTx.empty()) {
        e.drawTextCentered(tr("no_tx", lang), wrx + ww / 2, ly + 520, 24, kMU, FontRole::Body, L.rtl);
    } else {
        for (int i = 0; i < 6 && i < static_cast<int>(gTx.size()); ++i) {
            const WalletTx& t = gTx[static_cast<std::size_t>(i)];
            const int ry = ly + 340 + i * 72, rh = 62;
            const int rrx = L.X(wx + 30, ww - 60);
            if (focus == i) surface(e, rrx, ry, ww - 60, rh, 16, 3);
            const bool plus = t.amount >= 0;
            const SDL_Color c = plus ? kGRN : kRED;
            const int icx = L.X(wx + 46, 36);
            e.drawCircle(icx + 18, ry + rh / 2, 18, SDL_Color{c.r, c.g, c.b, 40});
            e.drawTextCentered(plus ? "+" : "-", icx + 18, ry + rh / 2, 24, c, FontRole::Body, false);
            boxText(e, L, t.label.empty() ? t.kind : t.label, wx + 100, ww - 400, ry + 8, 22, kTX);
            boxText(e, L, fmtDate(t.at), wx + 100, 300, ry + 36, 16, kMU);
            const std::string amt = (plus ? "+" : "") + formatAC(t.amount) + " AC";
            endText(e, L, amt, wx + 30, ww - 80, ry + 18, 24, plus ? (gLight ? SDL_Color{10, 85, 160, 255} : kGRN2) : kERT);
            if (focus == i) focusRing(e, rrx, ry, ww - 60, rh, 16, false);   // v0.35: row, frame only
        }
    }
    drawLegendBar(e, {{"\xE2\x97\x8B", "back"}, {"\xE2\x96\xB3", "search"}}, langCode);
}

// ============================================================================
//  Plans (view only — subscribe on the phone). Prices / limits from subscription_plans
// ============================================================================
void drawPlans(UiEngine& e, int focus, const std::string& langCode) {
    (void)focus;   // v6.6: Plans is a view-only page (look + scan the code) — no focus frame
    Lay L; L.rtl = (langCode == "ar");
    const std::string& lang = langCode;
    bakedBackdrop(e, std::string("plans|bg") + (gLight ? "|L" : "|D") + (L.rtl ? "|R" : "|N"), [&] {   // v7.3: your cave artwork as the Plans backdrop (baked once -> one blit per frame)
        backdrop(e);                                                                                  //        theme base under the art
        coverImage(e, pageArt(e, "bg_plans", "bg_splash"), 0, 0, 1920, 1080, 0, 0.5f, 0.5f);
        e.fillRect(0, 0, 1920, 1080, SDL_Color{static_cast<Uint8>(gLight ? 244 : 8), static_cast<Uint8>(gLight ? 248 : 10), static_cast<Uint8>(gLight ? 252 : 14), static_cast<Uint8>(gLight ? 118 : 170)});   // readability scrim
    });
    drawTopNav(e, 4, langCode);
    const int cx = kCL + 20;
    pageTitle(e, L, tr("plans", lang), cx, 44);
    const int gap = 28, w = (kCR - cx - 2 * gap) / 3, y = 150, h = 590;
    const SDL_Color accs[3] = {kBLl, SDL_Color{155, 107, 255, 255}, SDL_Color{247, 196, 81, 255}};
    for (int i = 0; i < 3; ++i) {
        const Tier t = static_cast<Tier>(i);
        const PlanInfo* p = nullptr;
        for (const auto& q : gPlans) if (q.tier == t) p = &q;
        const int xl = cx + i * (w + gap), rx = L.X(xl, w);
        const bool cur = gAcc.signedIn && gAcc.plan == t;
        e.dropShadow(rx, y, w, h, 28, 20, gLight ? 50 : 130);   // v6.6: card top is translucent -> shadow rect = card rect
        e.drawRoundGradV(rx, y, w, h, 28, SDL_Color{accs[i].r, accs[i].g, accs[i].b, static_cast<Uint8>(gLight ? 30 : 40)}, kSF);
        e.drawRoundBorder(rx, y, w, h, 28, cur ? 3 : 2, cur ? accs[i] : ink(26));
        tierTile(e, t, L.X(xl + 32, 64), y + 32, 64, accs[i]);
        boxText(e, L, tierName(t, lang), xl + 32 + 64 + 20, w - 140, y + 44, 36, kTX, FontRole::Pixel);
        if (cur) {
            const std::string c = tr("plan_current", lang);
            const int cw = textW(e, L, c, 17) + 28;
            e.drawRoundRect(L.X(xl + w - 28 - cw, cw), y + 50, cw, 32, 16, SDL_Color{accs[i].r, accs[i].g, accs[i].b, 60});
            e.drawTextCentered(c, L.X(xl + w - 28 - cw, cw) + cw / 2, y + 66, 17, kTX, FontRole::Body, L.rtl);
        }
        if (p && !p->priceLabel.empty()) boxText(e, L, p->priceLabel, xl + 32, w - 64, y + 130, 48, gLight ? kTX : accs[i], FontRole::Pixel);
        std::vector<std::string> feats;
        if (i == 0) feats.push_back(tr("pf_free_c", lang));
        if (i == 1) { feats.push_back(tr("pf_all_free", lang)); feats.push_back(tr("pf_epic_c", lang)); }
        if (i == 2) { feats.push_back(tr("pf_all_epic", lang)); feats.push_back(tr("pf_leg_c", lang)); }
        if (p) {
            if (p->downloadLimit < 0) feats.push_back(tr("pf_unl", lang));
            else if (p->downloadLimit > 0) {
                std::string f = tr("pf_dl", lang);
                if (lang == "zh") f = fmtD(fmt1(f, periodName(p->limitPeriod, lang)), p->downloadLimit);
                else f = fmt1(fmtD(f, p->downloadLimit), periodName(p->limitPeriod, lang));
                feats.push_back(f);
            }
        }
        if (i == 2) feats.push_back(tr("pf_no_ac", lang));
        int fy = y + 230;
        for (const auto& f : feats) {
            e.drawCircle(L.X(xl + 32, 26) + 14, fy + 14, 15, SDL_Color{accs[i].r, accs[i].g, accs[i].b, 90});
            e.drawCircle(L.X(xl + 32, 26) + 14, fy + 14, 11, accs[i]);
            shapeIcon(e, 2, L.X(xl + 32, 26) + 5, fy + 5, 18, kWHT);
            fy = paragraphAt(e, L, f, xl + 72, w - 104, fy, 23, kCHIPTX, 2, 8) + 18;
        }
    }
    {   // subscribe from phone
        const int qy = y + h + 28, qh = 170, qw = kCR - cx;
        const int qrx = L.X(cx, qw);
        e.drawRoundGradH(qrx, qy, qw, qh, 24, gLight ? SDL_Color{222, 246, 233, 255} : SDL_Color{18, 44, 32, 255}, kSF);
        e.drawRoundBorder(qrx, qy, qw, qh, 24, 2, SDL_Color{74, 168, 255, 70});
        const int qs = qh - 36;
        qrTile(e, L.X(cx + qw - 18 - qs, qs), qy + 18, qs);
        glyph(e, 3, L.X(cx + 36, 44), qy + 36, 44, kBLl, kBLl, false);
        boxText(e, L, tr("subscribe_elsewhere", lang), cx + 100, qw - qs - 160, qy + 40, 30, kTX);
        boxText(e, L, tr("scan_to_get_app", lang), cx + 100, qw - qs - 160, qy + 92, 22, kMU);
    }
    drawLegendBar(e, {{"\xE2\x97\x8B", "back"}, {"\xE2\x96\xB3", "search"}}, langCode);
}

// ============================================================================
//  Settings — 0 Language · 1 Appearance · 2 Account · 3 Auto-launch · 4 About · 5 Sign out (signed in)
// ============================================================================
namespace {
void switchCtl(UiEngine& e, int x, int y, bool on, bool rtl) {
    const int w = 88, h = 46;
    if (on) e.drawRoundGradH(x, y, w, h, 23, kBL, kBL2);
    else e.drawRoundRect(x, y, w, h, 23, gLight ? SDL_Color{208, 213, 221, 255} : SDL_Color{41, 48, 57, 255});
    e.drawRoundBorder(x, y, w, h, 23, 2, on ? SDL_Color{204, 208, 214, 160} : ink(40));
    const bool right = rtl ? !on : on;
    e.drawCircle(x + (right ? w - 24 : 24), y + h / 2, 17, kWHT);
}
int langIndexOf(const std::string& code) {
    static const char* kCodes[8] = {"en", "ar", "fr", "tr", "es", "ru", "pt", "zh"};
    for (int i = 0; i < 8; ++i) if (code == kCodes[i]) return i;
    return 0;
}
// mini UI preview used by the Appearance cards
void themePreview(UiEngine& e, int x, int y, int w, int h, bool light) {
    const SDL_Color bg = light ? SDL_Color{242, 244, 247, 255} : SDL_Color{11, 13, 16, 255};
    const SDL_Color sf = light ? SDL_Color{255, 255, 255, 255} : SDL_Color{28, 32, 39, 255};
    const SDL_Color tx = light ? SDL_Color{17, 20, 24, 255} : SDL_Color{243, 245, 247, 255};
    e.drawRoundRect(x, y, w, h, 14, bg);
    e.fillRect(x + 10, y + 10, 22, h - 20, sf);
    e.drawRoundRect(x + 14, y + 22, 14, 14, 4, tx);
    e.drawRoundRect(x + 46, y + 18, w * 45 / 100, 14, 4, tx);
    e.drawRoundRect(x + 46, y + 40, w * 30 / 100, 8, 4, SDL_Color{tx.r, tx.g, tx.b, 110});
    const int cw = (w - 46 - 10 - 2 * 8) / 3;
    for (int i = 0; i < 3; ++i) {
        e.drawRoundRect(x + 46 + i * (cw + 8), y + h - 66, cw, 52, 8, sf);
        e.drawRoundRect(x + 46 + i * (cw + 8), y + h - 66, cw, 26, 8, SDL_Color{74, 168, 255, static_cast<Uint8>(140 - i * 30)});
    }
    e.drawRoundBorder(x, y, w, h, 14, 2, light ? SDL_Color{16, 20, 26, 30} : SDL_Color{255, 255, 255, 30});
}
} // namespace

void drawSettings(UiEngine& e, int focus, int subFocus, bool autoLaunchOn, const std::string& langCode) {
    Lay L; L.rtl = (langCode == "ar");
    const std::string& lang = langCode;
    backdrop(e);
    pageTitle(e, L, tr("nav_settings", lang), kCL + 20, 44);
    const bool guest = !gAcc.signedIn;
    const int rows = 6;   // v0.67 («والخيار الذي في الاعداات ارجعه»): {lang, theme, account, autolaunch, about, signout}
    const int listX = kCL + 20, listW = 760, rowH = 104, pitch = 116, top = 150;
    const int panX = listX + listW + 40, panW = kCR - panX, panY = top, panH = 6 * pitch + rowH - 12;
    const int f = std::max(0, std::min(rows - 1, focus));
    // line icons (lineIconTile): 0 globe, 1 contrast, 2 user, 3 gamepad, 4 info, 5 log-out — saturated accents
    static const int kKinds[6] = {0, 1, 2, 3, 4, 5};
    static const SDL_Color kAccD[6] = {{88, 156, 255, 255}, {176, 132, 255, 255}, {250, 196, 64, 255},
                                       {76, 214, 124, 255}, {160, 170, 188, 255}, {255, 96, 112, 255}};
    static const SDL_Color kAccL[6] = {{29, 104, 222, 255}, {124, 74, 222, 255}, {184, 128, 8, 255},
                                       {22, 150, 78, 255}, {92, 101, 114, 255}, {214, 40, 64, 255}};
    const SDL_Color* kAcc = gLight ? kAccL : kAccD;
    const std::string labels[6] = {tr("settings_language", lang), tr("settings_appearance", lang), tr("settings_account", lang),
                                   tr("autolaunch", lang), tr("settings_about", lang), tr("settings_signout", lang)};
    static const char* kLangNative[8] = {"English", "\xD8\xA7\xD9\x84\xD8\xB9\xD8\xB1\xD8\xA8\xD9\x8A\xD8\xA9", "Fran\xC3\xA7\x61is",
        "T\xC3\xBC" "rk\xC3\xA7" "e", "Espa\xC3\xB1ol", "\xD0\xA0\xD1\x83\xD1\x81\xD1\x81\xD0\xBA\xD0\xB8\xD0\xB9",
        "Portugu\xC3\xAAs", "\xE4\xB8\xAD\xE6\x96\x87"};
    const int curLang = langIndexOf(lang);
    const std::string nm = gAcc.displayName.empty() ? gAcc.username : gAcc.displayName;
    const std::string values[6] = {kLangNative[curLang], tr(gLight ? "theme_light" : "theme_dark", lang),
                                   guest ? tr("acct_guest_v", lang) : nm, "",
                                   gAppVersion.empty() ? "" : "v" + gAppVersion, ""};
    for (int i = 0; i < rows; ++i) {
        const int y = top + i * pitch;
        const int rx = L.X(listX, listW);
        const bool foc = (i == f), danger = (i == 5);   // v0.67: signout is row 5 again
        if (danger && !foc) {
            e.drawRoundRect(rx, y, listW, rowH, 22, SDL_Color{251, 76, 110, static_cast<Uint8>(gLight ? 18 : 30)});
            e.drawRoundBorder(rx, y, listW, rowH, 22, 2, SDL_Color{251, 76, 110, 90});
        } else {
            surface(e, rx, y, listW, rowH, 22, foc ? 3 : 0);
        }
        const int ts = 64;
        lineIconTile(e, kKinds[i], L.X(listX + 22, ts), y + (rowH - ts) / 2, ts, kAcc[i], foc);
        const int labX = listX + 22 + ts + 22;
        const int endReserve = (i == 3) ? 130 : (i == 5 ? 70 : 250);
        boxText(e, L, labels[i], labX, listW - (labX - listX) - endReserve, y + (rowH - 32) / 2, 26, danger ? kERT : kTX);
        if (i == 3) switchCtl(e, L.X(listX + listW - 28 - 88, 88), y + (rowH - 46) / 2, autoLaunchOn, L.rtl);
        else {
            if (!values[i].empty()) endText(e, L, values[i], listX, listW - 62, y + (rowH - 28) / 2, 24, kMU);
            if (i != 3) chevron(e, L.X(listX + listW - 34, 12), y + rowH / 2 - 9, 9, !L.rtl, 3, danger ? kERT : kMU);
        }
        if (foc && subFocus < 0) focusRing(e, rx, y, listW, rowH, 22, false);   // v0.35: row, frame only
    }

    // ---- detail panel ----
    const int prx = L.X(panX, panW);
    e.dropShadow(prx, panY + 8, panW, panH, 30, 18, gLight ? 40 : 120);
    surface(e, prx, panY, panW, panH, 30, 1);
    const int avail = panW - 36 - 76 - 24 - 36;
    lineIconTile(e, kKinds[f], L.X(panX + 36, 76), panY + 32, 76, kAcc[f], true);
    boxText(e, L, labels[f], panX + 36 + 76 + 24, avail, panY + 34, 32, f == 5 ? kERT : kTX);
    const char* noteKey = f == 0 ? "lang_auto" : f == 1 ? "appearance_note" : nullptr;
    if (noteKey) paragraphAt(e, L, tr(noteKey, lang), panX + 36 + 76 + 24, avail, panY + 80, 19, kMU, 2, 6);
    const int innerX = panX + 36, innerW = panW - 72;
    int cy = panY + 150;
    switch (f) {
        case 0: {
            const int cw = (innerW - 16) / 2, chh = 76;
            for (int i = 0; i < 8; ++i) {
                const int xl = innerX + (i % 2) * (cw + 16), yy = cy + (i / 2) * (chh + 14);
                const int rx = L.X(xl, cw);
                const bool sel = (i == curLang);
                e.drawRoundRect(rx, yy, cw, chh, 18, sel ? SDL_Color{74, 168, 255, 30} : kSF2);
                e.drawRoundBorder(rx, yy, cw, chh, 18, 2, sel ? SDL_Color{74, 168, 255, 150} : kBD);
                const int rcx = L.X(xl + 22, 26) + 13;
                e.drawRoundBorder(rcx - 13, yy + chh / 2 - 13, 26, 26, 13, 2, sel ? kBLl : kMU);
                if (sel) e.drawCircle(rcx, yy + chh / 2, 7, kBLl);
                boxText(e, L, kLangNative[i], xl + 22 + 26 + 18, cw - 90, yy + (chh - 26) / 2, 22, sel ? kTX : kCHIPTX);
                if (subFocus == i) focusRing(e, rx, yy, cw, chh, 18, false);   // v0.35: tile, frame only
            }
            break;
        }
        case 1: {
            const int cw = (innerW - 24) / 2, chh = 300;
            for (int i = 0; i < 2; ++i) {
                const bool light = i == 1, sel = light == gLight;
                const int xl = innerX + i * (cw + 24), rx = L.X(xl, cw);
                surface(e, rx, cy, cw, chh, 22, sel ? 3 : 0);
                themePreview(e, rx + 20, cy + 20, cw - 40, chh - 110, light);
                const int rcx = L.X(xl + 26, 26) + 13, ry = cy + chh - 58;
                e.drawRoundBorder(rcx - 13, ry, 26, 26, 13, 2, sel ? kBLl : kMU);
                if (sel) e.drawCircle(rcx, ry + 13, 7, kBLl);
                boxText(e, L, tr(light ? "theme_light" : "theme_dark", lang), xl + 66, cw - 90, ry - 2, 26, kTX);
                if (subFocus == i) focusRing(e, rx, cy, cw, chh, 22, false);   // v0.35: tile, frame only
            }
            break;
        }
        case 2: {
            accountAvatar(e, L.X(innerX, 120), cy + 10, 120, !guest);
            const int tX = innerX + 120 + 32, tW = innerW - 120 - 32;
            boxText(e, L, guest ? tr("acct_guest_v", lang) : nm, tX, tW, cy + 22, 34, kTX);
            if (guest) {
                boxText(e, L, tr("guest_s", lang), tX, tW, cy + 76, 19, kMU);
                const char* steps[3] = {"step_1", "step_2", "step_3"};
                for (int i = 0; i < 3; ++i) {
                    const int yy = cy + 158 + i * 86;
                    const int rx = L.X(innerX, innerW);
                    surface(e, rx, yy, innerW, 72, 20, 0);
                    const int nx = L.X(innerX + 20, 46);
                    e.drawRoundGradH(nx, yy + 13, 46, 46, 14, kBL, kBL2);
                    e.drawTextCentered(num(i + 1), nx + 23, yy + 36, 26, SDL_Color{6, 24, 14, 255}, FontRole::Pixel, false);
                    boxText(e, L, tr(steps[i], lang), innerX + 20 + 46 + 20, innerW - 110, yy + 22, 24, kCHIPTX);
                }
                // v0.35: the green CTA sits fully INSIDE the card (it used to reach the card's
                // bottom edge so its rounded corners poked outside). Its ✕ is the pixel button
                // icon — IBM Plex has no U+2715 and rendered a tofu box instead.
                const int bY = cy + 418, bH = 88, bMid = bY + bH / 2;
                const int brx = L.X(innerX, innerW);
                e.drawRoundRect(brx, bY, innerW, bH, 24, SDL_Color{74, 168, 255, 44});
                e.drawRoundBorder(brx, bY, innerW, bH, 24, 3, SDL_Color{74, 168, 255, 190});
                {
                    std::string s = tr("press_x_code", lang), a, b;
                    const std::size_t p = s.find("\xE2\x9C\x95");           // U+2715
                    if (p != std::string::npos) {
                        a = s.substr(0, p);
                        b = s.substr(p + 3);
                        while (!a.empty() && a.back() == ' ') a.pop_back();
                        while (!b.empty() && b.front() == ' ') b.erase(b.begin());
                    } else {
                        a = s;
                    }
                    const int fsz = 28, iw = 32, gap = 12;
                    const int wA = a.empty() ? 0 : e.inkWidth(a, fsz, FontRole::Body, L.rtl);
                    const int wB = b.empty() ? 0 : e.inkWidth(b, fsz, FontRole::Body, L.rtl);
                    const int total = wA + (b.empty() ? 0 : gap + iw + gap) + wB;
                    const int gx = brx + (innerW - total) / 2;
                    const SDL_Color cta = gLight ? SDL_Color{10, 85, 160, 255} : kBLl;
                    if (L.rtl) {
                        if (!a.empty()) e.drawTextCentered(a, gx + total - wA / 2, bMid, fsz, cta, FontRole::Body, true);
                        if (!b.empty()) e.drawTextCentered(b, gx + wB / 2, bMid, fsz, cta, FontRole::Body, true);
                        if (!b.empty()) drawBtn(e, "\xE2\x9C\x95", gx + total - wA - gap - iw / 2, bMid, iw);
                    } else {
                        if (!a.empty()) e.drawTextCentered(a, gx + wA / 2, bMid, fsz, cta, FontRole::Body, false);
                        if (!b.empty()) e.drawTextCentered(b, gx + wA + gap + iw + gap + wB / 2, bMid, fsz, cta, FontRole::Body, false);
                        if (!b.empty()) drawBtn(e, "\xE2\x9C\x95", gx + wA + gap + iw / 2, bMid, iw);
                    }
                }
            } else {
                int x = tX;
                x += tierChip(e, L, x, cy + 76, gAcc.plan, lang, 36) + 14;
                if (gAcc.planExpiresAt > 0 && gAcc.plan != Tier::Free)
                    boxText(e, L, fmt1(tr("plan_until", lang), fmtDate(gAcc.planExpiresAt)), x, tX + tW - x, cy + 82, 20, kMU);
                const int rx = L.X(innerX, innerW);
                surface(e, rx, cy + 170, innerW, 110, 22, 2);
                e.drawImage("icon_coin", L.X(innerX + 28, 52), cy + 199, 52, 52);
                boxText(e, L, formatAC(gAcc.acBalance) + " AC", innerX + 100, innerW - 130, cy + 204, 40, kGD);
                paragraphAt(e, L, tr("account_hint", lang), innerX, innerW, cy + 320, 23, kMU, 4, 10);
            }
            break;
        }
        case 3: {
            const int rx = L.X(innerX, innerW);
            surface(e, rx, cy, innerW, 120, 22, autoLaunchOn ? 3 : 0);
            boxText(e, L, tr(autoLaunchOn ? "autolaunch_on" : "autolaunch_off", lang), innerX + 28, innerW - 56, cy + 42, 30,
                    autoLaunchOn ? kTX : kMU);
            paragraphAt(e, L, tr("autolaunch_desc", lang), innerX, innerW, cy + 152, 26, kMU, 4, 12);
            break;
        }
        case 4: {
            boxText(e, L, "ADDONSTATION", innerX, innerW, cy, 40, kTX, FontRole::Pixel);
            if (!gAppVersion.empty()) boxText(e, L, tr("version", lang) + " " + gAppVersion, innerX, innerW, cy + 58, 26, kGD);
            int yy = paragraphAt(e, L, tr("tagline", lang), innerX, innerW, cy + 106, 26, kMU, 3, 12) + 20;
            const int rx = L.X(innerX, innerW);
            surface(e, rx, yy, innerW, 120, 20, 2);
            // rights box: SilverFox logo + "All rights reserved — SilverFoxInteractive" (requested by the owner)
            const int fh = 72, fw = fh * 900 / 1061;
            e.drawImage("logo_fox", L.X(innerX + 26, fw), yy + (120 - fh) / 2, fw, fh);
            const int tx = innerX + 26 + fw + 22, tw = innerW - (tx - innerX) - 26;
            boxText(e, L, tr("rights_k", lang), tx, tw, yy + 22, 22, kGD);
            boxText(e, L, tr("rights_line", lang), tx, tw, yy + 60, 24, kCHIPTX);
            break;
        }
        default: {
            const int rx = L.X(innerX, innerW);
            e.drawRoundRect(rx, cy, innerW, 140, 28, SDL_Color{251, 76, 110, 22});
            e.drawRoundBorder(rx, cy, innerW, 140, 28, 2, SDL_Color{251, 76, 110, 110});
            paragraphAt(e, L, tr("signout_sub", lang), innerX + 28, innerW - 56 - 24, cy + 34, 26, kERT, 3, 12);   // v7.0: no icon inside the notice
            break;
        }
    }
    drawTopNav(e, 3, langCode);
    drawLegendBar(e, {{"\xE2\x9C\x95", "select"}, {"\xE2\x97\x8B", "back"}}, langCode);
}

// ============================================================================
//  Deprecated entry points (forward to v5)
// ============================================================================
void setServerContent(const HeroInfo*, const CardInfo*, const CardInfo*) {}
void setDetailTitle(const std::string&) {}
void drawWelcome(UiEngine& e, int focusIndex, const std::string& langCode) { drawSignIn(e, focusIndex == 2 ? 1 : 0, "", 600, 4, langCode); }
void drawCodeLink(UiEngine& e, const std::string& code, int secondsLeft, bool linked, const std::string& langCode) {
    drawSignIn(e, 0, code, secondsLeft, linked ? 1 : (secondsLeft <= 0 ? 2 : 0), langCode);
}
void drawEmailSignin(UiEngine& e, int, const std::string&, const std::string&, bool, const std::string& langCode) {
    drawSignIn(e, 0, "", 600, 4, langCode);
}
void drawInstallDemo(UiEngine& e, DemoState state, int percent, const std::string& langCode) {
    drawAddonDetail(e, 0, gDetailIdx >= 0 ? gDetailIdx : 0, state, percent, langCode);
}
void drawSearch(UiEngine& e, int focus, const std::string& query, int filterIndex, bool, const std::string& langCode) {
    drawSearch(e, focus, query, filterIndex, langCode);
}
void drawProfile(UiEngine& e, int focus, const std::string&, int, int, int, int, int, const std::string& langCode) {
    drawProfile(e, focus, langCode);
}
void drawWallet(UiEngine& e, int focus, int, const std::string& langCode) { drawProfile(e, focus, langCode); }
void drawPlans(UiEngine& e, int focus, int, const std::string& langCode) { drawPlans(e, focus, langCode); }
void drawMenuOverlay(UiEngine&, int, const std::string&) {}
void drawSettings(UiEngine& e, int focus, bool autoLaunchOn, const std::string&, const std::string& langCode) {
    drawSettings(e, focus, -1, autoLaunchOn, langCode);
}
void drawSettings(UiEngine& e, int focus, bool, bool autoLaunchOn, const std::string&, const std::string& langCode) {
    drawSettings(e, focus, -1, autoLaunchOn, langCode);
}

// ============================================================================
//  Legal splash with fade (v5.5): black -> fade in -> hold -> fade out -> app
// ============================================================================
int legalSplashTotalMs() { return kSplashFadeInMs + kSplashHoldMs + kSplashFadeOutMs; }
float legalSplashOpacity(int ms) {
    if (ms <= 0) return 0.f;
    if (ms < kSplashFadeInMs) return static_cast<float>(ms) / kSplashFadeInMs;
    ms -= kSplashFadeInMs;
    if (ms < kSplashHoldMs) return 1.f;
    ms -= kSplashHoldMs;
    if (ms < kSplashFadeOutMs) return 1.f - static_cast<float>(ms) / kSplashFadeOutMs;
    return 0.f;
}
// v0.35 (2026-10-29): the splash loader is the Minecraft Bedrock "autosave" box
// with the "Loading… NN%" text as one group — bottom-right in Arabic, bottom-left
// in LTR. v0.39 (user order): the EXACT Bedrock autosave indicator from the
// user's GIF — 9-frame pixel animation (arrow into the chest), blue keyed out.
static void drawLoadingBoxIcon(UiEngine& e, int x, int y, int a, int elapsedMs) {
    const int frame = (elapsedMs / 130) % 9;
    char nm[32];
    std::snprintf(nm, sizeof nm, "load_box_%d", frame);
    if (!e.hasImage(nm)) return;
    e.drawImage(nm, x, y, 64, 120, static_cast<Uint8>(std::min(255, a)));
}
void splashLoadingBox(UiEngine& e, const std::string& langCode, int elapsedMs, int a) {
    if (a <= 0) return;
    Lay L; L.rtl = (langCode == "ar");
    auto A = [&](int v) { return static_cast<Uint8>(std::min(255, v * a / 255)); };
    const float p = std::min(1.0f, static_cast<float>(elapsedMs) / static_cast<float>(std::max(1, legalSplashTotalMs())));
    // v0.47 (user order): ONE tight group — the box sits IMMEDIATELY next to the
    // «loading» text (was: fixed slots with a ~200px gap). Widths measured live.
    char pct[16];
    std::snprintf(pct, sizeof pct, "%d%%", static_cast<int>(p * 100.0f + 0.5f));
    const std::string lt = tr("loading", langCode);
    const int tw = std::max(120, e.measureText(lt, 24, FontRole::Body, L.rtl));
    const int pw = std::max(50, e.measureText(pct, 22, FontRole::Pixel, L.rtl));
    int lx = 118;
    // v0.60 (user: «مكان الصندوق جنب الكلمة التحميل»): the CHEST (lower band of the
    // 64x120 icon, centre ≈ +90) sits BESIDE the word — icon raised so both centres
    // line up (was y=898: only the arrow was beside the text, the chest hung below).
    drawLoadingBoxIcon(e, L.X(lx, 64), 938 + 15 - 90, a, elapsedMs);
    lx += 64 + 12;
    e.drawText(lt, L.X(lx, tw), 938, 24,
               SDL_Color{A(236), A(236), A(236), A(255)}, FontRole::Body, false, false);
    lx += tw + 14;
    e.drawText(pct, L.X(lx, pw), 940, 22, SDL_Color{A(255), A(214), A(90), A(255)}, FontRole::Pixel, false, false);
}

bool drawLegalSplashFade(UiEngine& e, const std::string& langCode, int elapsedMs) {
    // PERFORMANCE: the splash (4 logos + text) is composed ONCE into "__splash"; every fade frame is a
    // single image copy with alpha over black. Call releaseLegalSplash(e) afterwards to free it (8 MB).
    if (!e.hasImage("__splash")) {
        drawLegalSplash(e, langCode);
        const auto& fb = e.framebuffer();
        std::vector<std::uint8_t> rgba(fb.size() * 4);
        for (std::size_t i = 0; i < fb.size(); ++i) {
            const std::uint32_t p = fb[i];
            rgba[i * 4] = static_cast<std::uint8_t>(p >> 16); rgba[i * 4 + 1] = static_cast<std::uint8_t>(p >> 8);
            rgba[i * 4 + 2] = static_cast<std::uint8_t>(p); rgba[i * 4 + 3] = 255;
        }
        e.addImageRGBA("__splash", 1920, 1080, rgba.data());
    }
    float op = legalSplashOpacity(elapsedMs);
    // smoothstep easing -> softer start and end of each fade
    op = op * op * (3.f - 2.f * op);
    const int a = static_cast<int>(op * 255.f + 0.5f);
    if (a <= 0) { e.clear(SDL_Color{0, 0, 0, 255}); return elapsedMs < legalSplashTotalMs(); }
    e.drawImage("__splash", 0, 0, 1920, 1080);              // opaque straight copy
    splashLoadingBox(e, langCode, elapsedMs, 255);
    if (a < 255) e.fadeToBlack(static_cast<Uint8>(a));     // one fast pass towards black (splash bg is black)
    return elapsedMs < legalSplashTotalMs();
}
// v0.30: smooth disappearance — the splash drawn OVER the live Home frame, fading out
bool drawLegalSplashOver(UiEngine& e, const std::string& langCode, int elapsedMs) {
    if (!e.hasImage("__splash")) return false;
    float op = legalSplashOpacity(elapsedMs);
    op = op * op * (3.f - 2.f * op);
    const int a = static_cast<int>(op * 255.f + 0.5f);
    if (a <= 0) return false;
    e.drawImage("__splash", 0, 0, 1920, 1080, static_cast<Uint8>(a));
    splashLoadingBox(e, langCode, elapsedMs, a);
    return true;
}
void releaseLegalSplash(UiEngine& e) { e.unloadImage("__splash"); }

void drawLanguageDialog(UiEngine& e, int selectedLang, const std::string& langCode) {
    Lay L; L.rtl = (langCode == "ar");
    backdrop(e);
    e.fillRect(0, 0, 1920, 1080, kSCRIM);
    const int w = 760, h = 860, x = 960 - w / 2, y = 540 - h / 2;
    e.dropShadow(x, y + 10, w, h, 32, 30, 200);
    surface(e, x, y, w, h, 32, 1);
    icGlobe(e, L.X(x + 44, 44), y + 40, 44, kBLl);
    boxText(e, L, tr("choose_language", langCode), x + 44 + 62, w - 88 - 62, y + 44, 36, kTX);

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
        surface(e, rx, ry, rw, rowH - 10, 20, sel ? 3 : 0);
        e.drawCircle(L.X(x + 44 + 18, 52) + 26, ry + 34, 27, ink(30));
        e.drawImageRounded(rows[i].flag, L.X(x + 44 + 18, 52), ry + 8, 52, 52, 26);
        boxText(e, L, rows[i].native, x + 44 + 90, rw - 90 - 70, ry + 8, 28, kTX);
        boxText(e, L, rows[i].eng, x + 44 + 90, rw - 90 - 70, ry + 42, 22, kMU);
        const int cx = L.X(x + 44 + rw - 52, 28) + 14;
        e.drawRoundBorder(cx - 14, ry + 20, 28, 28, 14, 3, sel ? kBLl : SDL_Color{70, 76, 86, 255});
        if (sel) e.drawCircle(cx, ry + 34, 8, kBLl);
        if (sel) focusRing(e, rx, ry, rw, rowH - 10, 20);
    }
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
    e.dropShadow(x, y + 10, w, h, 32, 30, 200);
    surface(e, x, y, w, h, 32, 1);
    lineIconTile(e, 5, 960 - 55, y + 36, 110, kERT);
    e.drawTextCentered(tTitle, 960, y + 196, 40, kTX, FontRole::Pixel, L.rtl);
    {
        const auto lines = wrapLines(e, tSub, 26, w - 120, L.rtl);
        int yy = y + 246;
        for (const auto& ln : lines) { e.drawTextCentered(ln, 960, yy + 14, 26, kMU, FontRole::Body, L.rtl); yy += 38; }
    }
    const int bw = w - 96, bx = x + 48;
    e.drawRoundRect(bx, y + 380, bw, 96, 20, ink(22));
    e.drawRoundBorder(bx, y + 380, bw, 96, 20, 2, ink(64));
    e.drawTextCentered(tNo, 960, y + 380 + 48, 32, kTX, FontRole::Body, L.rtl);
    if (focus == 0) focusRing(e, bx, y + 380, bw, 96, 20);          // safe choice is focused first
    e.drawRoundRect(bx, y + 500, bw, 96, 20, SDL_Color{251, 76, 110, 34});
    e.drawRoundBorder(bx, y + 500, bw, 96, 20, 2, SDL_Color{251, 76, 110, 230});
    e.drawTextCentered(tYes, 960, y + 500 + 48, 32, kERT, FontRole::Body, L.rtl);
    if (focus == 1) focusRing(e, bx, y + 500, bw, 96, 20);
}


// ============================================================================
//  Legal / publisher splash (black screen, logo row, small print) — shown once at start-up
// ============================================================================
namespace {
// Same text in every language (legal line). Brackets removed on purpose.
const char* const kLegalText =
    "AddonStation\xE2\x84\xA2: THE COMPLETE ECOSYSTEM software \xC2\xA9 2026 SilverFoxInteractive Publishing Ltd. "
    "AddonStationps5, the AddonStationps4 logo, the Mod configurations and the User Profile configurations are "
    "trademarks of the AddonStation Group. \xC2\xA9 2026 The AddonStation Group. "
    "Connected via Supabase Realtime Database. All rights reserved.";
// Text shown next to the small fox head (second legal line).
const char* const kLegalLine2 = "AddonStation LOGO, APP SHIELD/ICON: \xE2\x84\xA2 & \xC2\xA9 2026 SilverFoxInteractive.";

// Draw a logo from ui.pak keeping its aspect ratio; returns the drawn width.
int logoFitH(UiEngine& e, const char* name, int x, int cy, int h) {
    int iw = 0, ih = 0;
    if (!e.imageSize(name, iw, ih) || ih <= 0) return h;
    const int w = iw * h / ih;
    e.drawImage(name, x, cy - h / 2, w, h);
    return w;
}
int logoFitW(UiEngine& e, const char* name, int x, int cy, int w) {
    int iw = 0, ih = 0;
    if (!e.imageSize(name, iw, ih) || iw <= 0) return w;
    const int h = ih * w / iw;
    e.drawImage(name, x, cy - h / 2, w, h);
    return h;
}
} // namespace

void drawLegalSplash(UiEngine& e, const std::string& /*langCode*/) {
    e.clear(SDL_Color{0, 0, 0, 255});
    const int left = 303;                       // text and first logo share this left edge
    // logo row: AddonStation block, fox, bear, Minecraft — 270 px tall (bear 290, it is slim), Minecraft wordmark 560 px wide
    const int cy = 450, gap = 96;
    int x = left;
    x += logoFitH(e, "logo_app", x, cy, 270) + gap;
    x += logoFitH(e, "logo_fox", x, cy, 270) + gap;
    x += logoFitH(e, "logo_bear", x, cy, 290) + gap;   // a little taller: the bear is a slim shape
    logoFitW(e, "logo_minecraft", x, cy, 560);

    // small print (wrapped, bold, light grey)
    {
        const SDL_Color ink{236, 236, 236, 255};
        const auto lines = wrapLines(e, kLegalText, 22, 1370, false);
        int y = 640;
        for (const auto& ln : lines) { e.drawText(ln, left, y, 22, ink, FontRole::Body, false, false); y += 28; }
    }
    // small fox head + its own line of text
    logoFitH(e, "logo_fox", left, 850, 88);
    if (kLegalLine2[0]) {
        const auto lines = wrapLines(e, kLegalLine2, 22, 1250, false);
        int y = 836 - static_cast<int>(lines.size() > 1 ? 14 : 0);
        for (const auto& ln : lines) { e.drawText(ln, left + 112, y, 22, SDL_Color{236, 236, 236, 255}, FontRole::Body, false, false); y += 28; }
    }
}

// v0.30: public animation API (pkgtest scope) — used by the main loop
// v0.38: these three MUST be defined here (pkgtest scope), not in the anon
// namespace above — internal linkage made the OpenOrbis link fail.
bool bakedFadeActive() { return gBakeFade; }
void bakedFadeReset() { gBakeFade = false; }
void animBlock(bool b) { gAnimBlock = b; }
void animReset() { gAnimCalls.clear(); gAnimRects.clear(); }
bool animHas() { return !gAnimCalls.empty(); }
void animRects(std::vector<int>& out) { out = gAnimRects; }
void animReplay(UiEngine& e) {
    gAnimRec = false;
    for (const AnimCall& a : gAnimCalls) {
        switch (a.kind) {
            case AnimCall::Ring:   focusRing(e, a.a0, a.a1, a.a2, a.a3, a.a4, a.a5 != 0); break;
            case AnimCall::Mark:   sectionMark(e, a.a0, a.a1, a.a2, a.c); break;
            case AnimCall::Tile:   lineIconTile(e, a.a0, a.a1, a.a2, a.a3, a.c, a.a4 != 0); break;
        }
    }
    gAnimRec = true;
}

} // namespace pkgtest
