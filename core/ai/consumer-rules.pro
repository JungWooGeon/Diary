# Native code (JNI) creates and calls these classes by name; R8 must not rename or strip them in release.
# LiteRT-LM 0.19.1 ships no consumer rules; ONNX Runtime's only cover telemetry.
-keep class com.google.ai.edge.litertlm.** { *; }
-keep class ai.onnxruntime.** { *; }
