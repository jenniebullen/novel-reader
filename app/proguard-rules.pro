# 自用包的基础混淆规则。Compose / AndroidX 自带 consumer rules，这里只保留必要项。

# 数据库、反射相关（当前没有反射用法，留作保险）
-keep class com.local.novelreader.data.** { *; }

# 保留行号，崩溃栈好定位
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
