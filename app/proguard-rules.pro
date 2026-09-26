# SLDataAPI 契约模型使用 kotlinx.serialization,release 混淆时保留序列化器
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.dntof.slconsole.** {
    *** Companion;
}
-keepclasseswithmembers class com.dntof.slconsole.** {
    kotlinx.serialization.KSerializer serializer(...);
}
