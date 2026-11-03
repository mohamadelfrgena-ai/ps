import java.text.SimpleDateFormat
import java.util.Date
import java.util.Properties
import java.util.TimeZone

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    // الإشعارات الفورية (FCM): الإضافة مسجّلة هنا لكن **لا تُطبَّق** إلا عند وجود
    // google-services.json — فبناء الـAPK يعمل عادياً قبل إنشاء مشروع Firebase.
    id("com.google.gms.google-services") version "4.4.2" apply false
}

// قراءة قيم Supabase من gradle.properties (أو من ملف local.properties) لتمريرها كـ BuildConfig.
// إن كانت فارغة سيعرض التطبيق شاشة «إعداد أول تشغيل» لإدخالها يدوياً.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun configValue(key: String): String =
    localProps.getProperty(key)?.trim()?.takeIf(String::isNotBlank)
        ?: project.findProperty(key)?.toString()?.trim().orEmpty()

// ---------------------------------------------------------------------------
// حماية إلزامية: المفتاح السري (sb_secret_… / service_role) لا يدخل BuildConfig
// أو الـAPK إطلاقاً. إن وُجد في local.properties أو gradle.properties يتوقف
// البناء فوراً برسالة واضحة (الحماية تعمل على جهازك وعلى GitHub Actions معاً).
// ---------------------------------------------------------------------------
fun requirePublishableOnly(key: String, value: String) {
    if (value.isBlank()) return
    val lowered = value.lowercase()
    if (lowered.contains("sb_secret") || lowered.contains("service_role")) {
        throw GradleException(
            "⛔ المفتاح السري مرفوض: القيمة في «$key» تحتوي sb_secret أو service_role.\n" +
                "   المفتاح السري يبقى في لوحة Supabase وفي أسرار الخادم/السكربتات فقط،\n" +
                "   ولا يدخل التطبيق أبداً. استخدم المفتاح العام: sb_publishable_… أو anon."
        )
    }
}

val supabaseUrlValue = configValue("ADDONSTATION_SUPABASE_URL")
val supabaseAnonValue = configValue("ADDONSTATION_SUPABASE_ANON_KEY")
requirePublishableOnly("ADDONSTATION_SUPABASE_URL", supabaseUrlValue)
requirePublishableOnly("ADDONSTATION_SUPABASE_ANON_KEY", supabaseAnonValue)

android {
    namespace = "com.addonstation.mobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.addonstation.mobile"
        minSdk = 24
        targetSdk = 35
        // 1.3: استعادة كلمة المرور · الدخول التلقائي بعد التسجيل · عين كلمة المرور
        //      وحقل التأكيد · شريط التنقّل السفلي · إطار الاختيار · استقرار الشات
        versionCode = 4
        versionName = "1.3"          // AddonStation Mobile v1.3

        resourceConfigurations += listOf("en", "ar", "pt", "es", "ru", "zh", "fr", "tr")

        buildConfigField("String", "DEFAULT_SUPABASE_URL", "\"$supabaseUrlValue\"")
        buildConfigField("String", "DEFAULT_SUPABASE_ANON_KEY", "\"$supabaseAnonValue\"")

        // ختم زمني للبناء: يعرضه التطبيق داخل الإعدادات حتى يعرف صاحب التطبيق
        // أي نسخة مثبّتة عنده بالضبط (نسخة كل تغيير تختلف).
        val buildStamp = SimpleDateFormat("yyyy-MM-dd HH:mm").apply {
            timeZone = TimeZone.getTimeZone("Africa/Tripoli")
        }.format(Date())
        buildConfigField("String", "BUILD_STAMP", "\"$buildStamp\"")
        buildConfigField("long", "BUILD_EPOCH_MILLIS", "${System.currentTimeMillis()}L")
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.4")

    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-gif:2.7.0")

    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")

    // الإشعارات الفورية — مكتبة FCM (تعمل فور إضافة google-services.json)
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-messaging")

    debugImplementation("androidx.compose.ui:ui-tooling")
}

// ---------------------------------------------------------------------------
//  إشعارات FCM: يُطبَّق الإضافة فقط عند وجود ملف الإعداد من Firebase.
//  قبل إنشائه: البناء ينجح والتطبيق يعمل، والإشعارات تُسجَّل بلا عمل (بلا أخطاء).
// ---------------------------------------------------------------------------
val googleServicesFile = file("google-services.json")
if (googleServicesFile.exists()) {
    apply(plugin = "com.google.gms.google-services")
    logger.lifecycle("🔔 google-services.json موجود ⇒ الإشعارات الفورية (FCM) مفعّلة في هذا البناء.")
} else {
    logger.lifecycle(
        "ℹ️ google-services.json غير موجود ⇒ بناء عادي بلا إشعارات فورية.\n" +
        "   لتشغيلها: أنشئ مشروع Firebase وضع الملف في android/app/ (انظر docs/11-PUSH-AR.md)."
    )
}
