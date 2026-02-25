# DOOM Android - ProGuard rules
# Keep JNI methods
-keepclasseswithmembernames class * {
    native <methods>;
}
