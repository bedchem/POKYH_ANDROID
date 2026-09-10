# Add project specific ProGuard rules here.
# kotlinx.serialization keeps its own consumer rules; nothing extra needed for
# reflection-free (@Serializable) models. OkHttp/Okio ship their own R8 rules too.

# Keep Hilt-generated entry points across ProGuard's stricter default rules.
-keep class dagger.hilt.internal.GeneratedComponentManager { *; }
