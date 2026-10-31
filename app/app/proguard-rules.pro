# Règles ProGuard/R8 — v1
# Moshi + réflexion Kotlin sur les data classes sérialisées
-keepclassmembers class io.selene.app.** {
    @com.squareup.moshi.FromJson <methods>;
    @com.squareup.moshi.ToJson <methods>;
}
-keep @com.squareup.moshi.JsonClass class * { *; }

# Room génère du code référencé par réflexion via les DAO
-keep class * extends androidx.room.RoomDatabase { <init>(); }
