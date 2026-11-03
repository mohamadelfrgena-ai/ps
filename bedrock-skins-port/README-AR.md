# Bedrock Skins → بيدروك أوفلاين (Port Toolkit)

تحويل مود الجافا **Bedrock Skins** (`brandonitaly/Bedrock-Skins`) إلى حزمة بيدروك تعمل **بدون إنترنت 100%**، مناسبة لأجهزة الكونسول المعدّلة (PS4 Homebrew) وللأجهزة العادية.

> **الخلاصة في سطر واحد:** مود الجافا يقرأ أصلًا صيغة بيدروك (`skins.json` + `geometry.json` + `persona/*.meta.json` + `*.animation.json`)، لذلك التحويل الصحيح ليس "إعادة كتابة" بل **إعادة تجميع (repackaging) + إسقاط الأجزاء التي لا وجود لها في بيدروك** (الشبكة، الـ Mixins، الـ GUI المخصص) واستبدالها بما يملكه بيدروك فعلًا: `skin packs` أصلية، `attachables`، `functions`، و`@minecraft/server`.

---

## 1. ما الذي ينتجه هذا المستودع؟

```
dist/
├── BedrockSkins.mcaddon          ← سلوك + موارد (الحركات، الأجنحة الديناميكية، العجلة)
├── BedrockSkins_BP.mcpack
├── BedrockSkins_RP.mcpack
├── skins/<pack>.mcskinpack       ← 13 حزمة سكنات جاهزة (سكنات + كيب مدمج)
└── REPORT.txt
```

| المكوّن | ماذا يفعل | يحتاج سكربتات؟ |
|---|---|---|
| `skins/*.mcskinpack` | سكنات ثلاثية الأبعاد + أجنحة مدمجة، تُركّب من شاشة البروفايل | لا |
| `BedrockSkins_RP` | تسجيل الحركات على `minecraft:player` + **لوحة JSON-UI في شاشة التوقف** + أجنحة ديناميكية | لا |
| `BedrockSkins_BP` | `functions` لتنفيذ أي حركة بأمر واحد + كيان `bs:emote_console` بدون سكربتات | لا |
| سكربت `scripts/main.js` | عجلة الحركات (server-ui) + حفظ الاختيارات Offline | نعم (اختياري) |

---

## 2. تشغيل سريع

```bash
# 1) فكّ ضغط مصدر المود
unzip Bedrock-Skins-master.zip -d ~/src

# 2) ابنِ كل شيء
python3 tools/build_addon.py --source ~/src/Bedrock-Skins-master

# 3) تحقّق

python3 tools/validate.py dist
```

لا يحتاج المولّد إلى أي مكتبة خارجية (Python 3.9+ فقط)، **ولا يتصل بالشبكة إطلاقًا**.

### خيارات مفيدة

| الخيار | المعنى |
|---|---|
| `--cosmetics` | دمج قطع الملابس (Persona) داخل السكنات كنسخ إضافية "سكن + قطعة" |
| `--cosmetics-per-skin 4` | تحديد سقف للنسخ المدمجة لكل سكن (يمنع الانفجار التوافقي) |
| `--dynamic-capes` | توليد أجنحة كعناصر قابلة للارتداء (`attachable` + `item`) |
| `--only skins` / `--only addon` | بناء جزء واحد |
| `--min-engine 1.21.0` | أقل إصدار محرك مدعوم |
| `--no-zip` | إخراج مجلدات فقط بدون `.mcpack` |

---

## 3. التركيب

**كمبيوتر / أندرويد:** افتح `.mcaddon` و`.mcskinpack` بالنقر المزدوج → تُستورد تلقائيًا.

**PS4/PS5 (نسخة معدّلة):**
1. انسخ الملفات إلى الجهاز عبر FTP/USB إلى:
   `.../com.mojang/resource_packs/` و`.../com.mojang/behavior_packs/` و`.../com.mojang/skin_packs/`
   (فك الضغط كمجلد، الملف المضغوط لا يعمل غالبًا على الكونسول).
2. من اللعبة: **Settings → Global Resources** لتفعيل BP/RP، و**Profile → Skins** لاختيار السكن.
3. الحركات لا تحتاج فتح عالم بوضع التجارب؛ إن أردت لوحة العجلة فتأكد أن السكربتات مفعّلة في العالم (**World Settings → Experiments/GameTest** حسب إصدارك).

**أوامر جاهزة داخل اللعبة:**

```
/function bs/emotes                 ← قائمة كل الحركات
/function bs/emote/overhead_wave    ← تنفيذ حركة (بدون سكربتات)
/scriptevent bs:wheel               ← عجلة الخانات الست
/scriptevent bs:bind overhead_wave 3 ← تثبيت حركة في الخانة 3
/function bs/give_wheel             ← عنصر الربط (RT = العجلة)
/function bs/give_slots             ← 6 عناصر: كل واحد = حركة بضغطة
```

---

## 4. الوثائق

| الملف | المحتوى |
|---|---|
| [`docs/01-TAHLIL-AL-KOD-AR.md`](docs/01-TAHLIL-AL-KOD-AR.md) | تحليل هندسي كامل لكود الجافا: من يقرأ ماذا، وأين توجد القرارات المهمة |
| [`docs/02-KHARITAT-AL-TAHSIL-AR.md`](docs/02-KHARITAT-AL-TAHSIL-AR.md) | خريطة التحويل: كل ملف/دالة في الجافا → مكافئها في بيدروك + جداول الإحداثيات والـ UV |
| [`docs/03-BINA-WA-TARKIB-AR.md`](docs/03-BINA-WA-TARKIB-AR.md) | البناء خطوة بخطوة، حدوده، ومشاكل شائعة وحلولها |

---

## 5. حدود يجب معرفتها مسبقًا (بصراحة)

1. **بيدروك لا يسمح لطرف ثالث بتغيير هندسة اللاعب الحقيقي في اللعب**؛ الطريقة الشرعية الوحيدة هي **Skin Pack** (ملف `.mcskinpack`) — وهذا هو ما ينتجه المشروع. النتيجة: السكن يُختار من شاشة البروفايل، وليس من داخل العالم.
2. **الملابس (Persona) ليست واجهة قابلة للإضافة** في بيدروك؛ لذلك تُدمج رياضيًا في السكن (نفس خوارزمية `PersonaPieceLoader` في الجافا) أو تُقدَّم كـ `attachable` على خانة درع.
3. **عجلة الحركات** الرسمية غير قابلة للتعديل، و**JSON-UI لا يستطيع تنفيذ أوامر** (انظر الوثيقة 04). لذلك: لوحة `pause_screen.json` للعرض والتنقّل، والتنفيذ عبر كيان `bs:emote_console` (تفاعل باليد) أو `function`/`scriptevent`.
4. **أصول DLC محمية**؛ المولّد لا يوزّع أي صورة، بل يعيد تجميع ما تملكه محليًا. مجلد `dist/` مستثنى من Git.
