# Zachovat naše třídy a metody
-keep class cz.tankono.widget.** { *; }

# Zachovat Kotlin metadata
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# Zachovat Jsoup
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**

# Zachovat Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Zachovat Kotlin Coroutines
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# Zachovat WorkManager
-keep class androidx.work.** { *; }

# Zachovat DataStore
-keep class androidx.datastore.** { *; }