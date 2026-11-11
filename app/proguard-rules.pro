# Keep kotlinx.serialization generated serializers.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class io.github.hansfbaier.modpoll.** {
    *** Companion;
}
-keepclasseswithmembers class io.github.hansfbaier.modpoll.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class io.github.hansfbaier.modpoll.**$$serializer { *; }
