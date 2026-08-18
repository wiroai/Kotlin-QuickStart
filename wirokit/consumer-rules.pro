# Preserve metadata used by Kotlin and kotlinx.serialization without keeping
# the entire SDK. Referenced entry points remain reachable through normal R8
# analysis.
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations
-keepattributes AnnotationDefault,Signature,InnerClasses,EnclosingMethod
