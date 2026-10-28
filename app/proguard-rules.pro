# 演示项目默认关闭混淆；如开启混淆，请保留以下规则。

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class com.cloudtune.player.** {
    *** Companion;
}
-keepclasseswithmembers class com.cloudtune.player.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room
-keep class * extends androidx.room.RoomDatabase { <init>(); }
