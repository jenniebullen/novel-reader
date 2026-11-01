# 小说阅读器（Android）

一个纯本地的安卓小说阅读器：导入自己收藏的 **TXT / EPUB**，自动分章、自由排版、翻页或滚动阅读。
**没有账号、没有广告、没有网络权限**——所有内容都在手机本地。

- 包名：`com.local.novelreader`
- 最低系统：Android 8.0（minSdk 26）
- 技术栈：Kotlin + Jetpack Compose + 自绘分页 View + 系统 SQLite
- 安装包约 1 MB（R8 压缩后）

## 功能

**导入与分章**
- 导入 TXT（UTF-8 / GBK / UTF-16 自动识别）与 EPUB
- 章节标题自动识别：`第X章/回/节/卷`、`Chapter N`、`楔子/序/番外`、`【第X章】` 等
- 自动清理书源里重复的标题行、书首目录；识别不出章节时按字数兜底切分
- 书架长按可「重新分章」：选规则 → 预览 → 应用，进度按百分比重定位

**阅读**
- 整页翻页（翻页动画：无 / 滑动 / 覆盖）或上下滚动阅读
- 点击左中右或上下半屏翻页，支持音量键以外的屏幕手势
- 字号、行距、4 套背景主题、左右页边距
- 全书进度条、目录、自动翻页（10/20/30 秒）

**辅助**
- 书签（记录章节 + 精确位置）
- 全文搜索，命中后直接跳到该位置
- 导入时净化广告行、网址行
- 书架备份 / 恢复：单个 zip 带走书籍正文、进度、书签和外观设置

## 构建

需要 JDK 17、Android SDK（platform 35、build-tools 35.0.0）和 Gradle 8.11+：

```bash
export JAVA_HOME=/path/to/jdk17          # Windows: $env:JAVA_HOME="..."
export ANDROID_HOME=/path/to/android-sdk

./gradlew assembleRelease   # 正式包（R8 压缩，约 1MB）
./gradlew testDebugUnitTest # 单元测试（Robolectric，17 个）
```

国内网络下 wrapper 下载 Gradle 可能很慢，可把 `gradle/wrapper/gradle-wrapper.properties`
里的 `distributionUrl` 换成腾讯/华为镜像。

直接用 Android Studio 打开也可以，同步后运行 `app` 即可。

依赖仓库已在 `settings.gradle.kts` 中配置了阿里云镜像并保留 Google/Maven Central 作为回退；
Robolectric 的 `android-all` 下载源同样指向了阿里云（见 `app/build.gradle.kts` 的 `testOptions`）。

## 目录结构

```
app/src/main/java/com/local/novelreader/
├── MainActivity.kt          // 入口，处理"分享文件进来"
├── NovelApp.kt              // Application，持有单例 Repository
├── data/
│   ├── Models.kt            // Book / Chapter / Bookmark / SearchHit
│   ├── AppDb.kt             // SQLite：books / chapters / bookmarks
│   ├── ChapterStore.kt      // 切章 + 章节文件读写 + 空壳章清理
│   ├── BackupManager.kt     // 备份/恢复（zip：元数据 JSON + 章节文件）
│   └── LibraryRepository.kt // 业务入口：导入 / 重新分章 / 搜索 / 备份
├── text/
│   ├── EncodingDetector.kt  // UTF-8 / GB18030 / UTF-16 探测
│   ├── ChapterSplitter.kt   // 章节标题规则表（自动 + 手动）
│   ├── EpubParser.kt        // EPUB 解包：OPF/spine/NCX → 章节纯文本
│   └── TextCleaner.kt       // 导入时清广告行、网址行
└── ui/
    ├── App.kt               // 书架 ↔ 阅读器 根导航
    ├── BookshelfScreen.kt   // 书架列表、导入、备份恢复
    ├── BookshelfDialogs.kt  // 长按菜单、重新分章（预览）
    ├── BookCard.kt          // 书架条目
    ├── ReaderScreen.kt      // 阅读页：进度条、上/下栏
    ├── ReaderView.kt        // 自绘分页 / 滚动 + 翻页动画
    ├── ReaderSettingsDialog.kt
    ├── ChapterListDialog.kt
    ├── SearchDialog.kt
    ├── BookmarksDialog.kt
    ├── ReaderTheme.kt
    └── ReaderPrefs.kt
```

## 数据

- 书籍元数据、章节索引、书签：`files/library.db`
- 正文：`files/books/<uuid>/ch_<n>.txt`，一章一个 UTF-8 文件
  - 打开书只读当前章，大文件也不会 OOM
  - 「重新分章」= 按文件顺序拼回全文 → 重新切分 → 重写文件与索引

## 加一条分章规则

编辑 `text/ChapterSplitter.kt`：

1. 准确率高的放 `defaultRules`（自动分章用）
2. 容易误判的放 `extraRules`（只在「重新分章」里供手动选择）
3. 在 `app/src/test/.../TextLayerTest.kt` 补正例/反例，跑 `testDebugUnitTest`

注意：标题可能以「？」「！」结尾、可能含逗号冒号，只有「。」和右引号是正文的标志；
匹配前会 normalize 掉不换行空格、零宽字符。

## 已知问题

- 少数书源会把同一章标题写两遍，极个别情况仍可能切出重复项
- 个别特殊排版的标题（行内混入不可见字符）可能识别不到
- EPUB 只取文字，图片、表格、脚注会被忽略
- 全文搜索是逐章扫文件，超大书会有 1～2 秒等待

## 免责声明

本软件只用于阅读**你自己合法拥有**的电子书文件，不内置任何书源、不提供任何在线内容下载。
请勿用于传播盗版内容。

## License

[MIT](LICENSE)
