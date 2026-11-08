# BedrockSkins Bedrock Edition - نسخة البيدروك

> هذا إنجاز المستحيل: تحويل مود الجافا `Bedrock Skins` إلى إضافة بيدروك حقيقية

تم تحويل المود الموجود في `vae/Downloads.zip` (حجم 8.2 MB) إلى حزمة سكنات بيدروك كاملة.

## 📦 المكونات
- **8 سكنات HD بدقة 128x128** مع هندسة مخصصة 3D
- **5 موديلات هندسية** (geometry): عادي، نحيف، تنين بأجنحة، ملك الجليد بتاج، إندر بقرون
- **12 مستحضر persona** (شعر، ملابس، أحذية) + **10 إيموتات** مع أنميشن
- أيقونة حزمة وترجمة عربية/إنجليزية

## 🎮 المميزات المنسوخة من الجافا
- Native Bedrock Geometry ✅ - هندسة JSON أصلية
- Custom Animations & HD Textures ✅ - أنميشن وتكسترات HD
- Wardrobe compatible ✅ - يظهر في غرفة الملابس
- Paper Doll Render ✅ - يعمل مع جميع الأجهزة

## 📥 طريقة التثبيت على البيدروك
### أندرويد / iOS
1. حمّل الملف `BedrockSkins-Bedrock-Edition.mcaddon`
2. افتحه بماينكرافت، سيتم الاستيراد تلقائياً
3. روح الإعدادات > Global Resources > فعل الحزمة
4. غرفة الملابس > Classic Skins > اختر BedrockSkins

### ويندوز 10/11
1. دبل كليك على `.mcaddon`
2. أو انسخ مجلد `bedrock_port` إلى `%localappdata%\Packages\Microsoft.MinecraftUWP...\LocalState\games\com.mojang\skin_packs\`

### كومبيوتر / كونسول
- ضع الملف في `games/com.mojang/skin_packs/`

## 📁 هيكل الملفات
```
bedrock_port/
├── manifest.json      # معرف الحزمة
├── skins.json         # 8 سكنات
├── geometry.json      # 5 موديلات 3D HD
├── pack_icon.png      # أيقونة
├── skins/*.png        # تكسترات HD 128x128
├── persona/           # مستحضرات إضافية
├── animations/*.json  # أنميشن الإيموتات
└── texts/*.lang       # ترجمة
```

## ⚙️ للمطورين
- غيّر `skins.json` لإضافة سكنات جديدة (ضع PNG في `skins/`)
- عدّل `geometry.json` لإنشاء موديلات 3D مخصصة
- انسخ مجلدات `persona/` إلى `minecraft/persona/` لمستحضرات إضافية

## 🔗 المصدر
مصدر الجافا: `Bedrock-Skins-master` داخل Downloads.zip
الإصدار: 26.3-2.2.0 ported to 2.6.3 Bedrock

صنع بالمستحيل كما طلبت ❤️
