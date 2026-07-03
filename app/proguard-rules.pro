# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# =====================================================
# DISABLE OBFUSCATION — keep R8 shrinking only
# =====================================================
-dontobfuscate

# =====================================================
# PRESERVE ATTRIBUTES
# =====================================================
# Signature          — generic type info (Retrofit ParameterizedType resolution)
# InnerClasses       — required by JVM to use Signature on inner/nested classes
# EnclosingMethod    — required by JVM to use InnerClasses
# Annotations        — Retrofit reads @GET/@POST/@Header etc. via reflection
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault

# =====================================================
# CRASHLYTICS — readable stack traces
# =====================================================
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# =====================================================
# RETROFIT 2 — R8 FULL MODE RULES
# =====================================================
# Retrofit 2.9.0 does NOT ship these rules. They were added to
# Retrofit's repo after 2.9.0 was released and are required for
# R8 full mode (default in AGP 8.0+).
#
# In R8 full mode, generic signatures are stripped from classes
# that aren't explicitly kept. Retrofit uses generic signatures
# on return types to build ParameterizedType at runtime. Without
# these rules: "Class cannot be cast to ParameterizedType"

# Keep Kotlin Continuation's generic signature (suspend function support)
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# Keep Retrofit's Call and Response generic signatures
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# For every Retrofit interface method, keep the generic signature of
# its return type class so Retrofit can resolve Response<T> at runtime.
-if interface * { @retrofit2.http.* public *** ...(...); }
-keep,allowoptimization,allowshrinking,allowobfuscation class <3>

# Retain service method parameters when optimizing
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# =====================================================
# SUPPRESS WARNINGS — optional dependencies
# =====================================================
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn okhttp3.internal.platform.**
-dontwarn sun.misc.Unsafe
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*