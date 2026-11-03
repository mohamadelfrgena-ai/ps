# 03 — البناء والتركيب

## 1. المتطلبات

- Python **3.9+** فقط (لا `pip install`، لا `PIL`، لا `numpy`).
- مجلد مصدر المود (`Bedrock-Skins-master`) أو مجلد `assets/bedrockskins` مباشرة.
- للتشغيل على الكونسول: نسخة Bedrock معدّلة مع وصول إلى مجلد `com.mojang`.

## 2. البناء

```bash
# كامل
python3 tools/build_addon.py --source ~/src/Bedrock-Skins-master --out dist

# سكنات فقط (الأسرع، بدون حركات)
python3 tools/build_addon.py --source ~/src/Bedrock-Skins-master --only skins

# الملابس المدمجة (قطعة واحدة لكل سكن)
python3 tools/build_addon.py --source ~/src/Bedrock-Skins-master \
        --cosmetics --cosmetics-per-skin 4 --only skins

# تراكب متعدد: حتى 3 قطع في السكن الواحد، بحد 9 تركيبات لكل حزمة
python3 tools/build_addon.py --source ~/src/Bedrock-Skins-master \
        --cosmetics --cosmetics-combos 3 --combo-limit 9 --only skins

# بدون واجهة شاشة التوقف
python3 tools/build_addon.py --source ~/src/Bedrock-Skins-master --no-ui
```

زمن البناء المتوقع على جهاز عادي: **< 10 ثوانٍ** بدون ملابس، و**1–3 دقائق** مع `--cosmetics` كامل (المعالجة بكسل-بكسل في بايثون).

### ما يحدث داخليًا

```
1. find_assets()               يحدد assets/bedrockskins
2. geo.load_geometry_file()    يحمّل هندسة الفانيلا (custom / customSlim / cape)
3. skinpacks.discover()        يقرأ 13 حزمة من skin_packs/
4. skinpacks.build_pack()      لكل حزمة:
       - ينسخ الصور والكيب
       - يرمّم المراجع الناقصة (هندسة غير موجودة → هندسة الفانيلا)
       - [اختياري] يدمج كل قطعة Persona في صورة السكن + هندسة منسوخة
       - يكتب skins.json + geometry.json + texts/
5. emotes.discover()           يقرأ persona/emote_*/ و*.animation.json
6. emotes.normalise()          hip→waist، إسقاط العظام الغريبة، ضبط المدة
7. addon.build_bp()/build_rp() manifest + player.entity.json + functions + main.js
```

## 3. مخرجات مجلد `dist/`

```
dist/
├── BedrockSkins.mcaddon            154 KB  ← سلوك + موارد
├── BedrockSkins_BP.mcpack
├── BedrockSkins_RP.mcpack
├── skins/
│   ├── battleandbeasts/            + battleandbeasts.mcskinpack
│   ├── villains/  legacy/  vanilla/ …
├── BedrockSkins_BP/
│   ├── manifest.json               data + script modules
│   ├── functions/bs/emote/*.mcfunction   (15 ملفًا)
│   ├── scripts/main.js             عجلة + scriptevent
│   └── items/                      [اختياري] أجنحة قابلة للارتداء
└── BedrockSkins_RP/
    ├── entity/player.entity.json   نسخة الفانيلا + حركاتنا
    ├── animations/bs_emotes.animation.json
    └── attachables/                [اختياري]
```

## 4. التركيب على PS4 (نسخة معدّلة)

1. فكّ كل ملف إلى **مجلد** (الكونسول لا يقرأ `.mcpack` غالبًا).
2. المسارات:
   ```
   /data/minecraft/…/com.mojang/resource_packs/BedrockSkins_RP/
   /data/minecraft/…/com.mojang/behavior_packs/BedrockSkins_BP/
   /data/minecraft/…/com.mojang/skin_packs/battleandbeasts/
   ```
   > بعض إصدارات PS4 تستخدم `development_resource_packs` / `development_behavior_packs` بدلًا من المجلدات العادية؛ إن لم يظهر الباك، انسخه إلى الفرعين.
3. شغّل اللعبة:
   - **Settings → Global Resources**: فعّل `Bedrock Skins (Offline) RP`.
   - **Profile → Skins → Owned → <اسم الحزمة>**: اختر السكن (الأجنحة تأتي معه تلقائيًا).
4. للحركات: عالم جديد/موجود → **Behavior Pack** مفعّل → جرّب `/function bs/emotes`.

### إن أردت العجلة على الكونسول
السكربتات تحتاج عالمًا يسمح بها:
- `Settings → World → Experiments` أو `GameTest Framework` حسب إصدارك.
- بعد الدخول: `/scriptevent bs:wheel` (صلاحية OP مطلوبة).

### مطابقة الإصدار (أهم خطوة قبل التركيب)

| ما تُصدّره | الافتراضي | ماذا تفعل |
|---|---|---|
| `min_engine_version` (BP/RP) | 1.21.0 | اضبطه على إصدار لعبتك: `--game-version 1.21.90` |
| `format_version` في `player.entity.json` | 1.26.0 (قالب موجان) | يُثبَّت تلقائيًا على `--game-version` |
| `@minecraft/server` / `server-ui` | 1.13.0 / 1.2.0 | إن رفض العميل التحميل، جرّب إصدارًا أقل |

> تعريف كيان عميل أحدث من إصدار اللعبة يُتجاهل أو يكسر رسم اللاعب — لهذا يطبع المولّد
> تحذيرًا صريحًا عند التثبيت.

## 5. مشاكل شائعة

| العرض | السبب | الحل |
|---|---|---|
| الحزمة تظهر لكن السكن ليس له نموذج | `geometry` مفقود في الحزمة | المولّد يرمّمه تلقائيًا من هندسة الفانيلا ويطبع تحذيرًا |
| الأذرع النحيفة تظهر عريضة | السكن يستخدم `customSlim` والهندسة نسخت `custom` | تحقق من `geometry` في `skins.json` |
| الحركة لا تُنفَّذ بالأمر | الحركة غير مسجّلة على `minecraft:player` | تأكد أن RP مفعّل (فهو من يوفّر `player.entity.json`) |
| `playanimation` يقول غير موجودة | اسم الحركة بلا مساحة الأسماء | استخدم `"animation.bs.emote.<name>"` بين علامتي تنصيص |
| السكربت لا يعمل على الكونسول | السكربتات موقوفة أو إصدار المحرك قديم | استخدم `/function bs/emote/<name>` |
| الملابس المدمجة تُغيّر لون الذراع | القطعة تحتوي خيوطًا مرسومة فعليًا على الذراع في الأطلس | سلوك مطابق للجافا؛ استخدم `--cosmetics-per-skin` لاختيار قطع محددة |
| رسالة `missing geometry 'geometry.defaultconsole.steve'` | حزمة `legacy` تشير إلى هندسة كونسول غير موجودة | متوقع: تم استبدالها بهندسة الفانيلا العريضة |
| لوحة الحركات لا تظهر في شاشة التوقف | حزمة موارد أخرى تعيد تعريف `pause_screen.json` | فعّل RP الخاص بنا فوقها (أعلى أولوية) |
| أزرار اللوحة لا تُنفّذ الحركة | متوقع: JSON-UI لا ينفّذ أوامر | استخدم `bs:emote_console` (تفاعل) أو `/function bs/emote/<id>` |
| الكيان `bs:emote_console` لا يظهر | لم يُستدعَ | `/function bs/summon_console` |

## 6. الأمان والقانون

- المولّد لا ينزّل شيئًا من الإنترنت ولا يرفع شيئًا.
- `dist/` مستثنى من Git لأن صور DLC مملوكة لـ Mojang؛ أنت تعيد تجميع ما تملكه على جهازك فقط.
- لا تشارك `.mcskinpack` الناتج علنًا.

## 7. التحقق قبل التركيب

```bash
python3 tools/validate.py dist
```
يفحص: صحة كل JSON، وجود الهندسة لكل سكن، وجود الصور، تَسجيل كل حركة في `player.entity.json`، تطابق ما يستدعيه الـBP مع ما هو معرّف، وسلامة `main.js`.

## 8. التوسّع لاحقًا

- **لوحة عجلة رسومية**: بناء `ui/bs_emote_wheel.json` (تخطيط) + فتحها من السكربت عبر `@minecraft/server-ui` غير ممكن للرسم الكامل؛ الحل العملي هو `ActionFormData` كما هو مُنفّذ.
- **`.pck` (Legacy Console)**: شغّل `PckImporter` من الجافا مرة واحدة على الحاسوب لتوليد مجلدات، ثم وجّه المولّد إلى `skin_packs/` بعد الاستيراد.
- **قصّات الشخصية (slim/wide)**: مدعومة تلقائيًا من `geometry.humanoid.customSlim`، والقطع التي تحتوي أذرعًا نحيفة تُرشَّح في `skinpacks.py`.
