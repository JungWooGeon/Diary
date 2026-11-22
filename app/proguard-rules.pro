# ML Kit finds its components by reflection (manifest meta-data -> Class.getDeclaredConstructor()). R8 full mode keeps the
# registrar class names (the manifest names them) but drops their no-arg constructors, so every ML Kit client fails to
# start in release (RemoteModelManager.getInstance() == null -> crash opening 쓰기). Keep the constructors.
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }

# play:review-ktx references a compile-time-only annotation from play-services-basement (not shipped at runtime).
-dontwarn com.google.android.gms.common.annotation.NoNullnessRewrite
