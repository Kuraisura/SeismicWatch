# ── Gising! ProGuard / R8 rules ──────────────────────────────────────────────
# R8 obfuscation + shrinking is enabled for release builds. These keep rules
# protect the classes that are reflected over or serialized at runtime.

# Data models are read by reflection (Room columns, Firestore (de)serialization).
-keep class com.gising.data.model.** { *; }
-keep class com.gising.data.model.firebase.** { *; }
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# ── Room ─────────────────────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.paging.**

# ── SQLCipher (net.zetetic:sqlcipher-android) ────────────────────────────────
-keep class net.zetetic.database.** { *; }
-keep class net.zetetic.database.sqlcipher.** { *; }
-dontwarn net.zetetic.database.**

# ── Firebase (Auth / Firestore / App Check / Messaging) ──────────────────────
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**
# Firestore maps documents onto these POJOs via reflection — keep no-arg ctors & fields.
-keepclassmembers class com.gising.data.model.firebase.** {
  <init>();
  <fields>;
}

# ── Networking & Ktor ────────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn org.conscrypt.**
-dontwarn org.slf4j.impl.**
-dontwarn io.ktor.util.cio.ConvertersKt

# ── Kotlin Serialization ─────────────────────────────────────────────────────
-keepattributes *Annotation*, ISO8601
-keepclassmembernames class * {
    @kotlinx.serialization.SerialName <fields>;
}
