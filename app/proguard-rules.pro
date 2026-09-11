# JavaTavern R8 rules (release builds)
#
# 目标：让 minify 后的 release 包与 debug 包行为一致。这里只保留「运行时靠反射
# 或 XML inflate 才能找到」的东西，不做过度 keep，以免抵消瘦身效果。

# —— 通过 XML 布局 inflate 的 View 需要保留 (Context, AttributeSet) 构造 ——
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# —— ViewModelProvider 通过反射实例化 ViewModel ——
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# —— 枚举的 values()/valueOf() 在代码里直接使用（ChatMessage.Role/Kind/ActionState）——
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# —— org.json 由平台提供，屏蔽 R8 的缺失引用告警（本地单测用的是独立 jar）——
-dontwarn org.json.**

# —— 保留注解与行号，便于线上崩溃栈定位 ——
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
