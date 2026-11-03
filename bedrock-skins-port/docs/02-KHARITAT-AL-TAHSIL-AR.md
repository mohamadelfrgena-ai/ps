# 02 — خريطة التحويل (Java → Bedrock)

## 1. جدول الملفات

| في مود الجافا | في بيدروك | الحالة |
|---|---|---|
| `skin_packs/<pack>/skins.json` | `skins.json` داخل `.mcskinpack` | ✅ نفس الصيغة |
| `skin_packs/<pack>/geometry.json` | `geometry.json` داخل نفس الحزمة | ✅ نفس الصيغة (`format_version: 1.12.0`) |
| `skin_packs/<pack>/*.png` | `textures/*.png` | ✅ نقل مباشر |
| `cape` في `skins.json` | `cape` في `skins.json` | ✅ مدعوم أصلًا |
| `skin_packs/<pack>/texts/*.lang` | `texts/*.lang` + `languages.json` | ✅ |
| `persona/<piece>/*.meta.json` | (أ) نسخة سكن مدمجة — (ب) `attachables/*.json` + `items/*.json` | 🔁 إعادة بناء |
| `persona/emote_*/*.animation.json` | `animations/bs_emotes.animation.json` + تسجيل في `entity/player.entity.json` | 🔁 تسجيل |
| `animations/dressing_room/*.anim.json` | `animation.bs.dressing_room.*` | ✅ كما هو |
| `EmoteWheelScreen.java` | `scripts/main.js` (server-ui) + `functions/bs/emote/*` | 🔁 إعادة بناء |
| `LocalSkinConfig` / `StateManager` | `player.setDynamicProperty` في السكربت | 🔁 إعادة بناء |
| `mixin/*`, `network/*`, `server/*` | — | ❌ إسقاط |

## 2. القوانين التي يجب ألا تُخطئ فيها

### 2.1 الإحداثيات
```
بيدروك:  y = 0 عند القدمين، موجب للأعلى، النموذج بارتفاع 24 بكسل
جافا  :  y = 0 عند أعلى الرأس، موجب للأسفل
التحويل المستخدم في المود:  localY = 24 - pivotY
```
عندما تتعامل مع `geometry.json` جاهز، **لا تحوّل شيئًا**؛ بيدروك يفهمه كما هو. التحويل مطلوب فقط إذا كنت تحوّل نموذجًا من مصدر آخر (مثل نماذج Java blocks).

### 2.2 الـ UV
- بيدروك: `uv` بوحدات البكسل، أعلى-يسار، ومعامل تكبير = `texture_width/height` في `description`.
- أوراق Persona بحجم 128×128 بينما السكن القياسي 64×64 → لذلك نستخدم جدول `copyPersonaBox` أعلاه.
- `inflate` يوسّع الصندوق خارج النسيج؛ المود يستخدم `+0.01` لمنع التشابك، والمولّد يستخدم `+0.02` (أوضح قليلًا على شاشات الكونسول).

### 2.3 العظام
الحد الأدنى الذي يحتاجه هيكل اللاعب في بيدروك:
```
root → waist → body → { head → hat, jacket, leftArm → leftSleeve, rightArm → rightSleeve }
             → { leftLeg → leftPants, rightLeg → rightPants }
```
`ensure_player_scaffold()` تضيف أي عظم ناقص **بدون مكعبات** حتى لا تنكسر الحركات والدر وعات.

## 3. استراتيجيات الملابس والأجنحة (ثلاث طبقات)

### الطبقة أ — داخل السكن (الأفضل للكونسول)، مع تراكب متعدد
الملابس تُدمج في صورة السكن + هندسة منسوخة بـ`inflate`. النتيجة: سكنات إضافية باسم «السكن + القطعة».
- ✅ تعمل بدون سكربتات، على PS4، في كل الأوضاع.
- ✅ أعلى جودة: نفس بكسل الجافا.
- ❌ انفجار توافقي (`سكن × قطعة`)، لذلك السقف `--cosmetics-per-skin`.
- ✅ منذ ترقية `layering.py` يمكن دمج **عدة قطع في سكن واحد** (قميص + بنطال + حذاء + شعر).

#### خوارزمية التراكب المتعدد (`tools/bsport/layering.py`)

```
ترتيب الرسم (من الأسفل للأعلى):
  skin → legs → high_pants → bottom → feet → arms → hand →
  top → outerwear → jacket → facial_hair → hair → eyes → mouth → face_accessory

الدمج:
  canvas = AlphaCompositing لكل قطعة بالترتيب
  bone_layers[bone] += 1 لكل عظم تغطيه القطعة
  inflate(bone) = 0.01 * bone_layers[bone] + 0.01      ← تراكمي + هامش الأمان
```

- **اختيار التركيبات** (`generate_combos`): قطعة واحدة لكل خانة (لا قبعتان)، بحد أقصى
  `--cosmetics-combos`، والأولوية كما يلي:
  1. نفس الشخصية (`alex_shirt` مع `alex_pants`) قبل الخلط بين الشخصيات،
  2. ترتيب الخانات: `top → legs → feet → hair → outerwear → face → arms`,
  3. سقف `--combo-limit` لمنع الانفجار.
- **لماذا `inflate` تراكمي؟** بيدروك يرسم الطبقة المدمجة على نفس مكعبات الهيكل الأساسي؛
  كل طبقة إضافية تحتاج هامشًا أكبر لئلا تتقاطع مع الطبقة التي تحتها (z-fighting). القيمة
  `0.01/طبقة` هي نفس قيمة الجافا (`inflate + 0.01`) موسّعة للتراكب.
- ❌ الاختيار من شاشة البروفايل لا من داخل العالم.

### الطبقة ب — `attachable` + عنصر قابل للارتداء
```json
{ "minecraft:attachable": { "description": {
    "identifier": "bs:cape_dlccape00000821",
    "materials": { "default": "entity_alphatest" },
    "textures": { "default": "textures/bs/capes/dlccape00000821.png" },
    "geometry": { "default": "geometry.bs.cape" },
    "render_controllers": ["controller.render.armor"] } } }
```
مع عنصر في الـBP: `minecraft:wearable` في `slot.armor.chest`.
- ✅ يعمل فوق **أي** سكن (كيب مستقل عن السكن).
- ✅ ديناميكي: السكربت/الأمر يعطي العنصر ويلبسه.
- ❌ يشغل خانة الدرع، ويحتاج إدارة عبر السكربتات.

### الطبقة ج — حسابي فقط (JSON-UI)
شاشة `ui/*.json` ممكنة، لكن أزرار JSON-UI **لا تنفّذ أوامر**؛ لذلك لا تُستخدم لتنفيذ الحركات، بل للعرض فقط. الحركات تُنفّذ بـ`function` أو `scriptevent`.

## 4. الحركات: سلسلة التشغيل الكاملة

```
animation.json  →  entity/player.entity.json ("animations": {"bs_overhead_wave": "animation.bs.emote.overhead_wave"})
                     │
                     ├── /playanimation @s "animation.bs.emote.overhead_wave" 0 0.15   (أمر، يعمل على الكونسول)
                     ├── /function bs/emote/overhead_wave                              (ملف mcfunction مولّد)
                     ├── /scriptevent bs:emote overhead_wave                           (سكربت)
                     └── عجلة server-ui: player.playAnimation(...)                     (سكربت + واجهة)
```

قواعد إعادة الاستهداف المطبّقة في `emotes.normalise()`:
1. `hip` → `waist`.
2. حذف أي عظم غير موجود في هيكل اللاعب (مع تحذير في السجل).
3. `loop = false` افتراضيًا + ضبط `animation_length` من المحتوى.
4. أسماء الحركات تحت مساحة `animation.bs.emote.<piece_name>`.

## 5. الفروق السلوكية المتوقعة (ماذا سيتغير أمام اللاعب)

| السلوك في الجافا | في بيدروك |
|---|---|
| تبديل السكن من داخل العالم (K) | من شاشة البروفايل فقط |
| الملابس تُلبس/تُخلع لحظيًا | سكنات مدمجة جاهزة (أو `attachable` مع سكربت) |
| 6 خانات حركات تُختار بالسحب | نموذج server-ui بنفس الترتيب + حفظ في `dynamic properties` |
| ترجمة فورية لكل اللغات | `texts/<lang>.lang` (اللغات نفسها المنقولة من الحزمة) |
| كيب Mojang عبر حساب مايكروسوفت | ❌ (يخالف شرط الأوفلاين) — الكيب يأتي من الحزمة فقط |

## 6. الأداء

- كل سكن مدمج يضيف صورة 64×64 (~2KB) وتعريف هندسة صغير؛ 13 حزمة × 45 سكنًا تبقى تحت 10MB.
- الحركات لا تُضيف أي عبء زمن تنفيذ (تُنفَّذ بمحرك الرسوم الأصلي).
- السكربت مستمع واحد فقط على `scriptEventReceive` + حدث `playerSpawn`: لا Tick loop، لا استعلامات لكل إطار.
