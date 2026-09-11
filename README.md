# Tachimanga 扩展仓库（第一批 10 个源）

这是给你自己导入 Tachimanga 用的扩展仓库。Tachimanga 不能像「阅读」那样粘贴 JSON 书源，必须用 **扩展 APK + `index.min.json`**。

发布目录在 `repo/`：里面是仓库清单 `index.min.json` 和各扩展 apk。推到 GitHub 后，在 Tachimanga 里添加：

```text
https://raw.githubusercontent.com/<你的GitHub用户名>/<仓库名>/main/repo/index.min.json
```

## 第一批站点

| 扩展 | 站点 |
| --- | --- |
| 包子漫画 | https://baozimh.org/ |
| 如漫画 | https://www.rumanhua.org/ |
| 拷贝漫画 | https://www.copy3000.com/ |
| 可乐漫画 | https://www.yoyomanga.com/ |
| Mangabz / Xmanhua | https://www.mangabz.com/ 、https://www.xmanhua.com/ |
| 极速漫画 | https://www.1kkk.com/ |
| 风之动漫 | https://www.fffdm.com/manhua/ |
| MyComic | https://mycomic.com/ |
| 咚漫 | https://www.dongmanmanhua.cn/ |

`xmanhua` 和 `mangabz` 是同一套站点，打进同一个扩展，安装后会看到两个源。

## 怎么导入 Tachimanga

### 方式 A：添加仓库地址（推荐）

1. 把本项目推到 GitHub。
2. 打开仓库 **Actions**，等 `Build Tachimanga repo` 跑完。
3. 仓库 **Settings → Pages**，Source 选 `gh-pages` 分支。
4. 在 Tachimanga 里添加仓库 URL：

```text
https://<你的GitHub用户名>.github.io/<仓库名>/index.min.json
```

5. 到 **Browse → Extensions** 安装对应扩展。

### 方式 B：直接导入 APK

1. 打开 GitHub Actions 成功记录，下载产物 `tachimanga-repo`。
2. Tachimanga：**Browse → Extensions → 右上角 + → 选择扩展文件**。
3. 选里面的 `.apk` 即可。

本机没有 Android SDK 时，不要在 Windows 上硬编，用 GitHub Actions 打 APK。

## 已知限制

- **MyComic** 有防爬（403），扩展装得上，能不能刷出列表取决于 App 里的请求能否过站。
- **拷贝漫画** 官方 API 会拦第三方，当前按网页解析，章节图可能还要再调。
- **可乐漫画** 章节图常加密，列表/详情可先用，翻页若空白再继续改解析。
- 网站改版后扩展会失效，改对应 `src/zh/<源>/` 下的 Kotlin，把 `extVersionCode` 加 1，再发一版。

## 本地构建（有 JDK 17 + Android SDK 时）

```bash
./gradlew assembleRelease
python scripts/generate_index.py
```

生成目录是 `repo-dist/`，里面有 `index.min.json` 和各 APK。
