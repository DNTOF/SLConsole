# SLDataAPI 契约模型使用 kotlinx.serialization,release 混淆时保留序列化器
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-dontnote kotlinx.serialization.**
-keepclassmembers class com.dntof.slconsole.** {
    *** Companion;
}
-keepclasseswithmembers class com.dntof.slconsole.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.dntof.slconsole.**$$serializer { *; }
-keepclassmembers @kotlinx.serialization.Serializable class com.dntof.slconsole.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# 崩溃堆栈保留行号，方便对照 mapping.txt
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# Microsoft Clarity SDK 内部用反射和 protobuf，整体保留
-keep class com.microsoft.clarity.** { *; }
-dontwarn com.microsoft.clarity.**
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }

# OkHttp 可选的 TLS 实现，Android 上用不到
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Compose、AndroidX（Biometric、Fragment、Navigation、DataStore）和 Backdrop 自带 consumer 规则，无需额外配置。
