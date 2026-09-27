# Keep Supabase DTOs (kotlinx.serialization works via reflection-free generated code,
# but keep model names for PostgREST debugging).
-keep class com.addonstation.app.core.** { *; }
-keepclassmembers class com.addonstation.app.core.** { *; }

# Coil
-keep class coil.** { *; }
-dontwarn coil.**

# Ktor / OkHttp / Okio
-dontwarn io.ktor.**
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
