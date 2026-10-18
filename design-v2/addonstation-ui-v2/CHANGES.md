# AddonStation PS4 — تصميم v2 (C++ فقط)

انسخ `src/screens.cpp` و`src/screens.hpp` و`src/ui_engine.cpp` فوق ملفاتك. الصور في `assets/`، ومعاينات كل الشاشات (EN + AR) في `previews/`.

## اللي تغيّر
- **إطار التحديد:** لون واحد صلب (#9CC1FF)، بدون توهج، على كل الشاشات.
- **الشريط العلوي:** Home / Deals / Store / Settings فقط. لا شات ولا جرس. الشريط والشاشات كلها تنقلب للعربي (RTL).
- **الخلفيات:** `bg_splash` (صورتك رقم 2) للترحيب والإيميل. `bg_winter` (صورتك رقم 1) للافتة عروض الشتاء في Home وDeals وStore.
- **صفحة الترحيب:** صورة Alex وSteve (`bg_welcome`) بنفس اتجاهها الأصلي في كل اللغات، الصورة فقط ما تنعكس. باقي التصميم (النص والأزرار والتدريج الغامق) يتبع اللغة: يسار في الإنجليزي، يمين في العربي. `bg_splash` (الكريبر) تبقى لشاشة الدخول بالإيميل.
- **bg_cave** (صورة الكهف): خلفية شاشة الحصول على الرمز.
- **QR:** كرت في آخر Home، وشريط في شاشة الخطط. يقرأ صورة `qr_app` من `ui.pak`، وإذا ما لقاها يرسم QR وهمي للمعاينة فقط.
- **شاشات جديدة:** البحث، الملف الشخصي، محفظة AC (عرض فقط)، الخطط (عرض فقط)، وقائمة △.
- **الإعدادات:** 6 صفوف بدون الإشعارات (اللغة، الحساب، التشغيل التلقائي، ربط جهاز، عن التطبيق، تسجيل الخروج)، ولوحة تفاصيل لكل صف.
- **رمز الربط:** يظهر كمربعات منفصلة مع شريط للوقت المتبقي.
- **ui_engine:** إصلاح `drawImageRounded`، كان يتجاهل الـ radius فتطلع الصور مربعة.

## إضافة الصور للـ ui.pak
```
python3 tools/pak_set.py assets/ui.pak bg_winter=assets/bg_winter.jpg bg_splash=assets/bg_splash.png bg_cave=assets/bg_cave.jpg bg_welcome=assets/bg_welcome.jpg qr_app=qr.png
```
`qr.png` صورة QR لصفحة تحميل التطبيق، مربعة 512×512 أو أكبر. أنا ما أعرف الرابط، فسوّها أنت.

## الدوال الجديدة (screens.hpp)
```cpp
drawSearch(e, focus, query, filterIndex, noResults, lang);   // focus: 0 حقل، 1-3 فلاتر، 4-8 نتائج
drawProfile(e, focus, name, planIndex, ac, downloads, saved, installed, lang); // focus 0 محفظة، 1 خطط، 2 إعدادات
drawWallet(e, focus, ac, lang);        // عرض فقط
drawPlans(e, focus, currentPlan, lang); // focus 0-2
drawMenuOverlay(e, focus, lang);       // ارسم الشاشة الحالية أولاً ثم هذي فوقها. 0 ملف، 1 محفظة، 2 خطط، 3 بحث
drawSettings(e, focus, autoLaunchOn, accountValue, lang); // 6 صفوف
```

## ما تغيّر عليك في main.cpp
1. احذف كل ما يخص الشات: `drawChat` و`Screen::Chat` و`roomOpen`. الدالة انحذفت من screens.
2. `drawSettings` الجديدة بدون `notifOn`. النسخة القديمة (7 صفوف) باقية للتوافق وتعيد ترقيم التركيز، لكن الأفضل تنقل للجديدة.
3. التبويبات صارت 4: `{Home, Deals, Store, Settings}`.
4. اربط □ بالبحث و△ بقائمة `drawMenuOverlay`، ووصّل الملف الشخصي والمحفظة والخطط بحالات التطبيق.
5. أرقام التركيز في Welcome وEmail وLanguage وHome وDeals وStore بقيت نفسها، فالتنقل الحالي يشتغل بدون تعديل.
6. `tools/ps4_design_preview.cpp` يستدعي `drawChat`، عدّله قبل ما تبنيه.

## نصوص مؤقتة (غيّرها لما تبي)
- مميزات الخطط (`pf_*`) والأسعار `[Price]`.
- صفوف سجل المحفظة والأرقام في الملف الشخصي (24 / 8 / 5)، وكلمات البحث الشائعة.
- النصوص الجديدة عربي وإنجليزي فقط، وباقي اللغات تطلع إنجليزي لين تترجمها.

## ملاحظة
جربت الكود بتجميعه على الكمبيوتر ورسمه لصور (الأيقونات والأغلفة في المعاينات بدائل). ما بنيت PKG ولا جربته على PS4.
