# 01 — تحليل كود مود الجافا (الهندسة العكسية)

المصدر: `Bedrock-Skins-master` (Fabric، `io.github.brandonitaly.bedrockskins`، 1060 ملف، ~17 ألف سطر Java).

## 1. خريطة الحِزم (ماذا يفعل كل مجلد)

| الحزمة | المسؤولية | عدد الملفات |
|---|---|---|
| `bedrock/` | نموذج بيانات صيغة بيدروك: `BedrockFile`, `BedrockGeometry`, `BedrockBone`, `BedrockCube`, `BedrockPolyMesh`, `GeometryDescription` | 6 |
| `pack/loader` | `SkinPackLoader`: يقرأ الحزم من القرص/الموارد ويبني `LoadedSkin` | 1 |
| `pack/pck` | استيراد أرشيفات **Legacy Console** (`.pck`) وتحويل نماذجها | 4 |
| `pack/persona` | محمّل قطع Persona: الأطلس، الـ tint، الأشعة فوق البنفسجية، الحركات | 11 |
| `pack/model` | سجلات البيانات: `LoadedSkin`, `LoadedCosmetic`, `LoadedEmote`, `SkinEntry`, `SkinPackManifest` | 7 |
| `client/render/model` | تحويل الهندسة إلى نموذج Java قابل للرسم (`BedrockModelBuilder`, `BedrockPlayerModel`, `BedrockGeometryBaker`) | 20 |
| `client/appearance` | منطق المظهر: `SkinManager`, `CapeManager`, `EmoteManager`, `PersonaManager` | 10 |
| `client/gui` | واجهات: `SkinSelectionScreen`, `EmoteWheelScreen`, `CosmeticGridWidget` … | 25 |
| `mixin/` | 25 Mixin لحقن الرسم والجلد داخل اللعبة | 25 |
| `network/`, `server/` | مزامنة المظهر بين اللاعبين عبر الشبكة + API عام | 5 |

**الخلاصة:** كل ما يخص «القراءة من الملفات المحلية» يقع في `pack/` + `client/render/model/`، وهو بالضبط ما يجب تحويله. أما `mixin/` و`network/` و`server/` فهي **إسقاط كامل** (لا مقابل لها في بيدروك، والبديل هو الـ Skin Pack والسكربتات).

## 2. كيف يقرأ المود السكنات؟ (`SkinPackLoader.loadPacks`)

```java
loadPacks()
 ├─ clear caches / translations / icons
 ├─ loadVanillaGeometry()                    // geometry.humanoid.custom + customSlim + geometry.cape
 ├─ for each dir in <game>/skin_packs:
 │     ├─ *.pck  → PckImporter.importPck()   // أرشيف كونسول قديم → مجلد عادي
 │     └─ dir     → loadExternalPack(dir)
 │           ├─ skins.json       → SkinPackManifest (Codec)
 │           ├─ geometry.json    → JsonObject (اختياري)
 │           └─ registerPackSkins(manifest, geometry, path -> resolveAsset(...))
 ├─ loadInternalPacks(resourceManager)       // الحزم المدمجة داخل الجار
 └─ loadPackOrder()
```

### حقول `skins.json` التي يقرأها فعليًا

| الحقل | الاستخدام | ملاحظة |
|---|---|---|
| `localization_name` | اسم السكن + مفتاح الترجمة `skin.<pack>.<name>` | إلزامي |
| `geometry` | معرّف الهندسة، مثل `geometry.humanoid.custom` | يُبحث في `geometry.json` المحلي ثم في هندسة الفانيلا |
| `texture` | مسار صورة أو `namespace:path` | قد يكون `minecraft:...` (نسخة اللعبة) |
| `type` | `free` / `paid` | يوضع كما هو |
| `cape` | صورة الكيب الخاصة بالسكن | **موجود في الصيغة نفسها** |
| `animations` | خريطة مثل `{"humanoid_base_pose": "animation.player.base_pose.upside_down"}` | يُستخدم حاليًا لدعم الوضع المقلوب فقط (`hasUpsideDownAnimation`) |
| `unfair` | علم تجميلي | يُنسخ |

### نقاط دقيقة في التحميل

- `resolveExternalAsset` يحوّل `namespace:path` إلى مورد داخل اللعبة، وأي مسار آخر إلى ملف على القرص **بحروف صغيرة** (`path.toLowerCase()`)، لذلك أسماء ملفات DLC كلها lowercase.
- `resolveGeometry` يبحث أولًا في `geometry.json` المحلي ثم في `vanillaGeometryJson` — أي أن أي سكن يمكنه الاعتماد على هندسة الفانيلا بدون نسخها.
- `SkinId.of(serializeName, skinDisplayName)` هو المفتاح الفريد؛ المكافئ في بيدروك هو `localization_name` داخل الحزمة.

## 3. كيف تُرسم الهندسة؟ (`client/render/model/BedrockModelBuilder`)

هذا أهم ملف للتحويل، لأنه يحدد **قواعد تحويل الإحداثيات** بين بيدروك وجافا:

```java
addMissingParts();                       // يكمل العظام الناقصة (head/hat/body/jacket/arms/legs)
localX = pivotX;
localY = 24f - pivotY;                   // ← بيدروك Y يصعد للأعلى من القدمين، جافا Y ينزل من الرأس
localZ = pivotZ;
if (parent != null) { localX -= parent.pivotX; localY -= (24 - parent.pivotY); localZ -= parent.pivotZ; }
PartPose.offsetAndRotation(localX, localY, localZ, toRadians(rotX), ...);
```

- **`24 - pivotY`** هو قانون التحويل الأساسي: في بيدروك أصل النموذج عند القدمين والمحور Y موجب للأعلى حتى 24؛ في جافا المحور Y موجب للأسفل من أعلى الرأس.
- العظام الأصلية (`head`, `body`, `rightArm`, `leftArm`, `rightLeg`, `leftLeg`) تُثبَّت دائمًا كأبناء مباشرين للجذر (`VANILLA_ROOT_PARTS`)، والباقي يُبنى هرميًا.
- زوايا الدوران تُحوَّل إلى راديان (بيدروك يستخدم درجات — لا حاجة للتحويل عند التصدير).
- أعلام `animationArmsDown`, `animationStatueOfLibertyArms`, `animationHeadDisabled` … إلخ (في `BedrockGeometry`) تُترجم إلى **إخفاء/تثبيت عظام** داخل اللعبة.

### ماذا يعني هذا للتحويل؟

> لا نحتاج إلى تحويل إحداثيات على الإطلاق! بيدروك يستخدم نفس ملف `geometry.json` بنفس القوانين. التحويل الوحيد المطلوب هو **إكمال العظام الناقصة** (`ensure_player_scaffold`) و**تعديل `inflate`** عند دمج الملابس.

## 4. كيف تعمل الملابس/القطع (Persona)؟ (`PersonaPieceLoader`)

القطع ليست هندسة كاملة، بل **طبقة نسيج** تُرسم فوق هيكل اللاعب:

1. **البحث**: كل مجلد فيه `*.meta.json` يُقرأ؛ `piece_type` يجب أن يبدأ بـ`persona_` وألا يكون `persona_emote`.
2. **رسم النسيج** (`drawTextureLayers`) بدلالة `texture_sources`:
   - `use_face_uv: true` → `drawPersonaFaceLayer` (مصدر 32×32: الرأس عند (0,0)، القبعة عند (0,16)).
   - خلاف ذلك → `drawPersonaBodyLayer` (مصدر 128×128 يمثل ورقة Persona كاملة، تُقتطع منها صناديق بيدروك القياسية).
3. **خريطة الصناديق** (`copyPersonaBox`) — وهي الجوهر، منقولة حرفيًا إلى `tools/bsport/persona.py`:

   | العضو | مصدر (128×128) | هدف (64×64) | أبعاد |
   |---|---|---|---|
   | body | (16,0) | (16,16) | 8×12×4 |
   | jacket | (16,16) | (16,32) | 8×12×4 |
   | right arm | (0,0) | (40,16) | 4×12×4 |
   | right sleeve | (0,16) | (40,32) | 4×12×4 |
   | left arm | (40,0) | (32,48) | 4×12×4 |
   | left sleeve | (40,16) | (48,48) | 4×12×4 |
   | right leg | (16,32) | (0,16) | 4×12×4 |
   | right pants | (0,32) | (0,32) | 4×12×4 |
   | left leg | (32,32) | (16,48) | 4×12×4 |
   | left pants | (48,32) | (0,48) | 4×12×4 |

   النسخة النحيفة (slim) تأخذ الأذرع من منطقة أخرى: `(14,48)`, `(0,48)`, `(28,48)`, `(42,48)` بعرض 3.
4. **التلوين (tint)**: `tint_base_color` + `tint_map` (TGA)؛ الكود يرفع خريطة القناع إلى نفس الأطلس ثم يستخدمها كـ mask.
5. **دمج الهندسة**: كل `geometry_sources` تُدمج عظمًا بعظم (`mergeBone`)، مع إعادة كتابة UVs عبر `PersonaAtlasPacker` (تخصيص صناديق 128×128 داخل أطلس 1024) و`inflate + 0.01` على الهيكل الأساسي.
6. **المناطق (`zone`)**: تُجمع من `zone` في الميتا ومن كل `geometry_sources[].zone`، وتُستخدم لاحقًا لتقرير أي عظم يرتدي القطعة.

> **قرار التحويل:** بيدروك لا يملك واجهة لإضافة قطع Persona. لذلك نُنفّذ الخوارزمية نفسها **وقت البناء** (bake): النسيج يُدمج في صورة السكن، والهندسة تُنسخ مع `inflate`، والنتيجة سكن جديد باسم «السكن + القطعة».

## 5. الحركات (`PersonaEmoteLoader` + `EmoteManager`)

- **الاكتشاف**: `piece_type == "persona_emote"` + `animation_sources[0]` يحمل `name` و`animationFile`.
- **المدة**: `animation_length` أو أكبر مفتاح زمني.
- **التشغيل**: `EmoteManager` يحتفظ بـ **6 خانات** (`SLOT_COUNT = 6`) وتُحفظ في `LocalSkinConfig.emoteSlots()`.
- **إعادة الاستهداف** — الجزء الأهم:

  ```java
  case "root" -> model.root();
  case "head" -> customHead;  case "body" -> customBody;
  case "rightarm"|"left_arm" …
  case "hip", "waist" -> applyVirtualParent(delta, 0, 12, 0, body, head, rightArm, leftArm);
  ```

  أي أن عظم `hip` في Persona يجب أن يتحول إلى **`waist`** في بيدروك (نفس التسلسل `root → waist → body → head/arms`). هذا مثبت في `tools/bsport/emotes.py::BONE_REMAP`.
- **`groundRootTransform`**: دوران الجذر في Persona يحدث حول القدمين (y=0)، بينما جذر جافا عند الرأس (y=24)؛ لذلك يُعاد حساب الموضع. في بيدروك أصل النموذج **أصلًا** عند القدمين، فالمشكلة غير موجودة ولا نحتاج هذا التصحيح.
- **حركات غرفة القياس** (`animations/dressing_room/*.anim.json`): 11 حركة "رد فعل" تُشغّل في شاشة الاختيار؛ تُنقل كما هي إلى `animation.bs.dressing_room.*` ويمكن تشغيلها في واجهة مماثلة.

## 6. ما الذي نتجاهله عن قصد؟

| الملف/النظام | لماذا |
|---|---|
| `mixin/render/*` (25 ملفًا) | حقن في خط أنابيب الرسم الخاص بجافا؛ لا مقابل في بيدروك |
| `network/`, `server/`, `CapeManager.fetchOwnedCapes` | HTTP إلى `api.minecraftservices.com` — يخالف شرط «أوفلاين 100%» |
| `gui/*` | يُعاد بناؤها كـ `@minecraft/server-ui` أو تُستبدل بواجهة اللعبة الأصلية |
| `pack/pck` | يعمل وقت التحميل في الجافا؛ يمكن تشغيله مسبقًا على الحاسوب ثم بناء الناتج (انظر الحدود في الوثيقة 03) |
| `AnimatedSkinTextures`, `HeadIconTextures` | تحريك/رسم وقت التشغيل؛ في بيدروك يُستبدل بـ `flipbook_textures.json` أو يُهمل |
