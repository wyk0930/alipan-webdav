# alipan-webdav

阿里云盘 WebDAV 客户端，提供 GUI 和 CLI 两种使用方式。

## 功能

- WebDAV 协议连接（HTTPS），Basic 认证
- 文件/文件夹浏览，支持目录导航
- 文件下载，实时进度显示
- HTTP 代理支持（环境变量自动检测或手动配置）

## 下载

从 [Releases](https://github.com/wyk0930/alipan-webdav/releases) 页面下载对应平台的压缩包，解压后直接运行，内嵌 JRE 无需安装 Java。

| 平台 | GUI | CLI |
|------|-----|-----|
| Windows x64 | `alipan-webdav-fx-*-windows-x64.zip` | `alipan-webdav-cli-*-windows-x64.zip` |
| Linux x64 | `alipan-webdav-fx-*-linux-x64.tar.gz` | `alipan-webdav-cli-*-linux-x64.tar.gz` |
| macOS Apple Silicon | `alipan-webdav-fx-*-macos-aarch64.tar.gz` | `alipan-webdav-cli-*-macos-aarch64.tar.gz` |

## 环境要求（从源码构建）

- JDK 25+
- Maven 3.6+

## 项目结构

```
alipan-webdav/
├── core/    # 核心模块：WebDAV 协议、数据模型
├── fx/      # GUI 客户端（JavaFX）
└── cli/     # 命令行客户端
```

## 构建与打包

### GUI 客户端

```bash
# 1. 克隆项目
git clone https://github.com/wyk0930/alipan-webdav.git
cd alipan-webdav

# 2. 编译
mvn clean package -q

# 3. 准备干净的输入目录（仅包含 shade 后的 fat jar）
mkdir -p dist/jpackage-fx
cp fx/target/alipan-webdav-fx-0.1.0.jar dist/jpackage-fx/

# 4. 生成 app-image（内嵌 JRE，无需目标机器安装 Java）
jpackage --name WebDAV \
  --input dist/jpackage-fx \
  --main-jar alipan-webdav-fx-0.1.0.jar \
  --main-class com.alipan.webdav.fx.Launcher \
  --type app-image \
  --dest dist \
  --java-options "--add-opens=java.base/java.lang=ALL-UNNAMED" \
  --app-version 0.1.0
```

产物位于 `dist/WebDAV/`，双击 `WebDAV.exe` 即可运行。

### CLI 客户端

```bash
# 1. 克隆项目
git clone https://github.com/wyk0930/alipan-webdav.git
cd alipan-webdav

# 2. 编译
mvn clean package -q

# 3. 准备干净的输入目录（仅包含 shade 后的 fat jar）
mkdir -p dist/jpackage-cli
cp cli/target/alipan-webdav-cli-0.1.0.jar dist/jpackage-cli/

# 4. 生成 app-image（内嵌 JRE，无需目标机器安装 Java）
jpackage --name alipan-webdav \
  --input dist/jpackage-cli \
  --main-jar alipan-webdav-cli-0.1.0.jar \
  --main-class com.alipan.webdav.cli.Main \
  --type app-image \
  --dest dist \
  --win-console \
  --app-version 0.1.0
```

产物位于 `dist/alipan-webdav/`，直接运行 `alipan-webdav.exe` 即可。

> 两种打包产物均内嵌 JRE，目标机器无需安装 Java。

### 开发运行

```bash
# GUI（需本机安装 JDK 25+）
mvn -pl fx javafx:run

# CLI（需本机安装 JDK 25+）
java -jar cli/target/alipan-webdav-cli-0.1.0.jar
```

## GUI 客户端 (fx)

基于 JavaFX 的图形界面，提供登录、文件浏览、下载等功能。

## CLI 客户端 (cli)

交互式命令行客户端，适合无图形界面的环境。

### 使用方式

启动后依次输入用户名、密码和代理地址（可选），连接成功后进入交互式 Shell：

```
webdav> help
可用命令：
  ls [path]              列出目录内容
  cd <path>              切换目录
  pwd                    显示当前路径
  download <file> [local] 下载文件（别名：dl）
  help / ?               显示帮助
  exit / quit            退出
```

路径支持相对路径、绝对路径和 `..` 回退。

## 技术栈

- Java 25 + JavaFX 25.0.1
- `java.net.http.HttpClient`（原生 HTTP，无第三方依赖）
- WebDAV PROPFIND 协议解析
- Maven 多模块 + shade 打包
