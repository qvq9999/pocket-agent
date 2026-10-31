# kotlinx.serialization：@Serializable 的 serializer 由编译器生成，R8 不知道它被反射式使用
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keep,includedescriptorclasses class com.pocketagent.app.**$$serializer { *; }
-keepclassmembers class com.pocketagent.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.pocketagent.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# 数据模型整体保留：字段名参与 JSON 键名，混淆后读写会错位
-keep class com.pocketagent.app.data.** { *; }
# 工具调用的数据结构会随会话一起落盘，同样整包保留
-keep class com.pocketagent.app.agent.** { *; }

# 枚举被 @SerialName 序列化，保留枚举常量名
-keepclassmembers enum com.pocketagent.app.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
