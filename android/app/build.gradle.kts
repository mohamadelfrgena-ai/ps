import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// قراءة قيم Supabase من gradle.properties (أو من ملف local.properties) لتمريرها كـ BuildConfig.
// إن كانت فارغة سيعرض التطبيق شاشة «إعداد أول تشغيل» لإدخالها يدوياً.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun configValue(key: String): String =
    (localProps.getProperty(key) ?: project.findProperty(key)?.toString() ?: "").trim()

android {
    namespace = "com.addonstation.mobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.addonstation.mobile"
        minSdk = 24
        targetSdk = 35
        versionCode = 3
        versionName = "1.2"          // AddonStation Mobile v1.2

        resourceConfigurations += listOf("en", "ar", "pt", "es", "ru", "zh", "fr", "tr")

        buildConfigField("String", "DEFAULT_SUPABASE_URL", "\"${configValue("ADDONSTATION_SUPABASE_URL")}\"")
        buildConfigField("String", "DEFAULT_SUPABASE_ANON_KEY", "\"${configValue("ADDONSTATION_SUPABASE_ANON_KEY")}\"")
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

    debugImplementation("androidx.compose.ui:ui-tooling")
}
