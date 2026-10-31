# Règles ProGuard/R8 — v1
# Moshi + réflexion Kotlin sur les data classes sérialisées
-keepclassmembers class io.selene.app.** {
    @com.squareup.moshi.FromJson <methods>;
    @com.squareup.moshi.ToJson <methods>;
}
-keep @com.squareup.moshi.JsonClass class * { *; }

# Room génère du code référencé par réflexion via les DAO
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# kotlin-reflect + Moshi réfléctif : sans ces métadonnées, les adaptateurs DTO cassent
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisibleAnnotations, KotlinMetadata
-keep class kotlin.Metadata { *; }
-keep class io.selene.app.imports.** { *; }
-keep class io.selene.app.resolver.ExternalVariant { *; }
-keep class io.selene.app.resolver.ExternalResponse { *; }
-keep class io.selene.app.resolver.ExternalSubtitle { *; }

-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
