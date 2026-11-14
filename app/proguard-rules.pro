# Zeyao ProGuard rules for release minify
-keep class com.wusper.zeyao.** { *; }
-keepclassmembers class com.wusper.zeyao.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn okhttp3.**
-dontwarn okio.**
-keepattributes Signature
-keepattributes *Annotation*
-keep class androidx.security.crypto.** { *; }
