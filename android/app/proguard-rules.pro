# SQLCipher (JNI)
-keep class net.zetetic.database.** { *; }
-dontwarn net.zetetic.database.**

# Everything (de)serialised with Gson by field name: vault metadata, backup contents
# (DB entities inside backups — renaming fields would make old backups unreadable), Telegram API DTOs
-keep class com.personal.passvault.core.crypto.VaultMeta { *; }
-keep class com.personal.passvault.data.db.*Entity { *; }
-keep class com.personal.passvault.data.backup.BackupPayload { *; }
-keep class com.personal.passvault.data.backup.PendingInfo { *; }
-keep class com.personal.passvault.data.telegram.Tg* { *; }
# Phase 4: type-specific JSON stored in entries.payload (and inside backups)
-keep class com.personal.passvault.domain.EntryPayload { *; }
-keep class com.personal.passvault.domain.TotpConfig { *; }
-keep class com.personal.passvault.domain.CardData { *; }
-keep class com.personal.passvault.domain.WifiData { *; }
-dontwarn com.google.zxing.**

# Optional platform classes referenced by libraries but not present on Android
-dontwarn javax.naming.**
-dontwarn org.slf4j.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn org.bouncycastle.jsse.**
-keepattributes Signature, *Annotation*

# BouncyCastle – we only use the Argon2 lightweight API
-dontwarn org.bouncycastle.**

# Strip all android.util.Log calls from release builds (never log anything sensitive)
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}
