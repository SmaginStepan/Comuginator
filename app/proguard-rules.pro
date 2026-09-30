# R8 rules for the release build (minify + obfuscate + resource shrinking).
#
# Retrofit, OkHttp, Coil, WorkManager, Firebase, Material, Compose, zxing and
# kotlinx.coroutines ship their own consumer rules, so only what THIS app
# reaches through reflection needs to be kept here.

# Generic signatures and annotations are read at runtime by Retrofit (service
# method return types, @GET/@POST/...) and Gson (TypeToken<List<...>>).
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes *Annotation*

# Readable stack traces in Play Console / Crashlytics (mapping file is uploaded
# with the bundle): keep line numbers, hide original source file names.
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# ── Gson models ──────────────────────────────────────────────────────────────
# Gson maps JSON keys to Kotlin property names via reflection (there are no
# @SerializedName annotations), so field names must survive obfuscation. The
# constructors are kept too so Gson keeps using the synthetic no-arg constructor
# of all-default data classes (which applies the Kotlin default values).
#
# api:     every request/response DTO (also cached to disk as JSON, and passed
#          between activities as JSON extras).
# storage: FamilyEntry, NotificationRule and the Pending* offline-queue classes
#          are persisted to SharedPreferences / files as JSON.
-keep class com.an0obis.comuginator.api.** {
    <init>(...);
    <fields>;
}
-keep class com.an0obis.comuginator.storage.** {
    <init>(...);
    <fields>;
}
