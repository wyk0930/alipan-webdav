# alipan-webdav

阿里云盘 WebDAV 客户端，提供 GUI 和 CLI 两种使用方式。

## 功能

- WebDAV 协议连接（HTTPS），Basic 认证
- 文件/文件夹浏览，支持目录导航
- 文件下载，实时进度显示
- HTTP 代理支持（环境变量自动检测或手动配置）

## 环境要求

- JDK 25+
- Maven 3.6+

## 项目结构

```
alipan-webdav/
├── core/    # 核心模块：WebDAV 协议、数据模型
├── fx/      # GUI 客户端（JavaFX）
└── cli/     # 命令行客户端
```

## 构建

```bash
# 编译全部模块
mvn clean package -q
```

产物：
- `fx/target/alipan-webdav-fx-0.1.0.jar` — GUI 客户端
- `cli/target/alipan-webdav-cli-0.1.0.jar` — CLI 客户端

## GUI 客户端 (fx)

基于 JavaFX 的图形界面，提供登录、文件浏览、下载等功能。

### 开发运行

```bash
mvn -pl fx javafx:run
```

### 直接运行 jar

```bash
java --add-opens=java.base/java.lang=ALL-UNNAMED -jar fx/target/alipan-webdav-fx-0.1.0.jar
```

### 打包为免安装版（app-image）

打包后内嵌 JRE，无需目标机器安装 Java。

```bash
mvn clean package -q
jpackage --name WebDAV \
  --input fx/target \
  --main-jar alipan-webdav-fx-0.1.0.jar \
  --main-class com.alipan.webdav.fx.Launcher \
  --type app-image \
  --dest target/app \
  --java-options "--add-opens=java.base/java.lang=ALL-UNNAMED" \
  --app-version 0.1.0
```

产物位于 `target/app/WebDAV/`，双击 `WebDAV.exe` 即可运行。

## CLI 客户端 (cli)

交互式命令行客户端，适合无图形界面的环境。

### 运行

```bash
java -jar cli/target/alipan-webdav-cli-0.1.0.jar
```

### 打包为免安装版（app-image）

```bash
mvn clean package -q
jpackage --name alipan-webdav \
  --input cli/target \
  --main-jar alipan-webdav-cli-0.1.0.jar \
  --main-class com.alipan.webdav.cli.Main \
  --type app-image \
  --dest target/app \
  --app-version 0.1.0
```

产物位于 `target/app/alipan-webdav/`，内嵌 JRE，目标机器无需安装 Java，直接运行即可。

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
