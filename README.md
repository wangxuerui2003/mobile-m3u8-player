# M3U8 Player

Android M3U8 视频播放器，基于 okzyw.net API，支持 HLS 视频流播放、搜索、收藏和下载。

## 下载

在 [Releases](../../releases) 页面下载最新 APK，安装即可使用。
首次启动会自动从 okzyw.net 同步最新视频目录。

## 功能

- 视频搜索（本地数据库）
- HLS/M3U8 视频播放（Media3 / ExoPlayer）
- 收藏 / 稍后再看 / 观看历史
- 离线下载（前台服务，支持进度通知）
- TMDB 相关推荐
- 数据增量更新（首次启动自动同步，也可在"我的"页手动更新）

## 技术栈

| 类别 | 技术 |
|------|------|
| UI | Jetpack Compose + Material 3 |
| 架构 | MVVM + Hilt DI |
| 网络 | Retrofit + OkHttp |
| 数据库 | Room |
| 播放器 | ExoPlayer (Media3) |
| 图片 | Coil |

## 构建

需要 JDK 17 和 Android SDK 34。

```bash
./gradlew assembleRelease
```

APK 输出路径：`app/build/outputs/apk/release/app-release.apk`（debug 签名，仅供个人侧载使用）。

### TMDB 推荐（可选）

推荐功能需要一个 TMDB API key。
在仓库根目录创建 `local.properties` 并加入：

```properties
TMDB_API_KEY=你的key
```

不配置也能正常构建，只是推荐列表为空。

## 项目结构

```
app/src/main/java/com/example/m3u8player/
├── data/
│   ├── api/          # Retrofit API 接口
│   ├── db/           # Room DAO 和数据库
│   ├── model/        # 数据模型和 Room Entity
│   └── repository/   # Repository 层
├── di/               # Hilt 依赖注入模块
├── service/          # 数据更新与下载前台服务
├── ui/
│   ├── detail/       # 视频详情页
│   ├── downloads/    # 下载管理页
│   ├── home/         # 首页
│   ├── navigation/   # 导航
│   ├── player/       # 播放器
│   ├── profile/      # 个人中心
│   ├── recent/       # 最近观看
│   ├── search/       # 搜索页
│   └── watchlater/   # 稍后再看页
├── MainActivity.kt
└── M3U8PlayerApplication.kt
```

## 数据策略

应用采用"先本地后网络"策略：

1. **预置数据**：APK 内置一份视频目录快照（`app/src/main/assets/preset_videos.json`），首次启动即有内容可看。
2. **首次自动同步**：首次启动在后台自动从 API 拉取最新增量，不阻塞使用。
3. **本地数据库**：Room 存储视频列表，支持快速搜索和浏览。
4. **API 详情**：点击视频时从 API 获取详情和播放链接。
5. **手动更新**：在"我的"页面可随时重新同步全量/增量列表。

## 辅助脚本

`okzyw_fetcher.py` 是独立的 Python 数据获取脚本，用于生成 APK 预置数据，与 Android 应用独立运行。

```bash
pip3 install requests
# 刷新预置 JSON（写入 app/src/main/assets/preset_videos.json）
python3 okzyw_fetcher.py fetch-json
# 或抓取到本地 SQLite
python3 okzyw_fetcher.py fetch-db --db okzyw_data.db
```

## 许可证

MIT，详见 [LICENSE](LICENSE)。
视频数据来自 okzyw.net 公开 API，本项目仅供学习和研究使用。
