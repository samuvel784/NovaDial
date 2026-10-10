# NovaDial release (R8) rules.
# The app reads/writes its data models with Moshi's Kotlin REFLECTION adapter and stores them in Room.
# Without these rules a minified release build crashes when it first reads a contact or setting.

-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, Exceptions
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, RuntimeVisibleTypeAnnotations
-keepattributes SourceFile, LineNumberTable

# Kotlin metadata used by moshi-kotlin reflection
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }
-dontwarn kotlin.reflect.**

# Moshi
-keep class com.squareup.moshi.** { *; }
-keep @com.squareup.moshi.JsonClass class * { *; }
-dontwarn com.squareup.moshi.**

# App data models (Room entities and the JSON-serialised classes)
-keep class com.example.data.model.** { *; }
-keepclassmembers class com.example.data.model.** { <init>(...); }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**
