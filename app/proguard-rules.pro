# Keep kotlinx.serialization generated serializers.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.modpoll.rtu.** {
    *** Companion;
}
-keepclasseswithmembers class com.modpoll.rtu.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.modpoll.rtu.**$$serializer { *; }
