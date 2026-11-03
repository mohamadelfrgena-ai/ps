# AddonStation Mobile — R8 / ProGuard rules

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.addonstation.mobile.**$$serializer { *; }
-keepclassmembers class com.addonstation.mobile.** {
    *** Companion;
}
-keepclasseswithmembers class com.addonstation.mobile.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**

# Coil
-dontwarn coil.**

# Media3
-dontwarn androidx.media3.**

# جنّب تشويش أسماء نماذج البيانات (تعمل مع PostgREST)
-keep class com.addonstation.mobile.data.model.** { *; }
