# WebDAV 文件浏览器

一个基于 JavaFX 的 WebDAV 客户端，用于浏览和下载远程文件。

## 功能

- WebDAV 协议连接，支持 HTTPS
- 文件/文件夹浏览，双击进入子目录
- 文件下载，支持进度显示
- HTTP 代理支持（自动检测环境变量或手动配置）

## 环境要求

**开发环境：**
- JDK 25+
- Maven 3.6+

**运行环境：**
- 打包后的 app-image 已内嵌 JRE，无需额外安装

## 构建

```bash
# 编译运行
mvn javafx:run

# 打包为便携版
mvn clean package -q
jpackage --name WebDAV \
  --input target \
  --main-jar alipan-webdav-1.0-SNAPSHOT.jar \
  --main-class com.alipan.webdav.Launcher \
  --type app-image \
  --dest target/app \
  --java-options "--add-opens=java.base/java.lang=ALL-UNNAMED" \
  --app-version 1.0.0
```

打包产物位于 `target/app/WebDAV/`，双击 `WebDAV.exe` 即可运行。

## 项目结构

```
src/main/java/com/alipan/webdav/
├── App.java                  # 应用入口
├── Launcher.java             # 启动器（绕过 JavaFX 模块检查）
├── controller/
│   ├── LoginController.java  # 登录页
│   └── BrowserController.java # 文件浏览器
├── service/
│   └── WebDavService.java    # WebDAV 操作（java.net.http）
├── model/
│   └── RemoteFile.java       # 文件数据模型
└── util/
    └── DownloadTask.java     # 下载任务

src/main/resources/com/alipan/webdav/
├── login-view.fxml           # 登录界面
├── browser-view.fxml         # 浏览器界面
└── styles.css                # 样式
```

## 技术栈

- Java 25 + JavaFX 25.0.1
- `java.net.http.HttpClient`（原生 HTTP 客户端，无第三方依赖）
- WebDAV PROPFIND 协议解析
