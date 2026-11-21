# Native code (JNI) creates and calls these classes by name; R8 must not rename or strip them in release.
# LiteRT-LM 0.19.1 ships no consumer rules. LiteRT (TFLite Interpreter) 1.4.2 ships its own.
-keep class com.google.ai.edge.litertlm.** { *; }
