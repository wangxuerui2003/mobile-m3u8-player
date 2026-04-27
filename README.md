# M3U8 Player

Android M3U8 视频播放器，基于 okzyw.net API，支持 HLS 视频流播放、搜索、收藏和下载。

## 功能

- 🔍 视频搜索（本地数据库）
- 📺 HLS/M3U8 视频播放（ExoPlayer）
- ⭐ 收藏/稍后再看
- 📥 离线下载（WorkManager）
- 📺 Google Cast 投屏
- 🔄 数据增量更新

## 技术栈

| 类别 | 技术 |
|------|------|
| UI | Jetpack Compose + Material 3 |
| 架构 | MVVM + Hilt DI |
| 网络 | Retrofit + OkHttp |
| 数据库 | Room |
| 播放器 | ExoPlayer (Media3) |
| 图片 | Coil |
| 后台任务 | WorkManager |
| 投屏 | Google Cast SDK |

## 项目结构

```
app/src/main/java/com/example/m3u8player/
├── data/
│   ├── api/          # Retrofit API 接口
│   ├── db/           # Room DAO 和数据库
│   ├── model/        # 数据模型和 Room Entity
│   └── repository/   # Repository 层
├── di/               # Hilt 依赖注入模块
├── service/          # WorkManager 下载服务
├── ui/
│   ├── cast/         # Cast 投屏管理
│   ├── detail/       # 视频详情页
│   ├── downloads/    # 下载管理页
│   ├── home/         # 首页
│   ├── navigation/   # 导航
│   ├── player/       # 播放器
│   ├── profile/      # 个人中心
│   ├── search/       # 搜索页
│   └── watchlater/   # 稍后再看页
├── MainActivity.kt
└── M3U8PlayerApplication.kt
```

## 构建

需要 JDK 17 和 Android SDK 34。

```bash
# 构建 Debug APK
JAVA_HOME=/opt/homebrew/opt/openjdk@17 gradle assembleDebug

# 或使用 Android Studio 打开项目直接运行
```

APK 输出路径：`app/build/outputs/apk/debug/app-debug.apk`

## 数据策略

应用采用"先本地后网络"策略：

1. **本地数据库**：Room 存储视频列表，支持快速搜索和浏览
2. **API 详情**：点击视频时从 API 获取详情和播放链接
3. **数据更新**：在"我的"页面点击"更新数据"从 API 拉取全量列表

## 辅助脚本

`okzyw_fetcher.py` 是独立的 Python 数据获取脚本，可用于预抓取数据或批量更新数据库，与 Android 应用独立运行。

```bash
pip3 install requests
python3 okzyw_fetcher.py --db okzyw_data.db
```

## 许可证

仅供学习和研究使用。
