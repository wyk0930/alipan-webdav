package com.alipan.webdav.cli;

import com.alipan.webdav.core.model.RemoteFile;
import com.alipan.webdav.core.service.WebDavService;

import java.io.*;
import java.util.List;
import java.util.Scanner;
import java.io.Console;

/**
 * 阿里云盘 WebDAV 交互式 CLI 工具。
 *
 * 启动后交互式输入账号密码，连接成功后进入 shell。
 */
public class Main {

    private static WebDavService service;
    private static String currentDir = "/";

    public static void main(String[] args) {
        Console console = System.console();
        Scanner scanner = new Scanner(System.in);

        System.out.println("阿里云盘 WebDAV CLI");
        System.out.println();

        // 交互式输入账号
        String username = prompt(console, scanner, "账号: ");
        if (username == null || username.isEmpty()) {
            exitWithError("账号不能为空");
        }

        // 交互式输入密码（Console 可用时隐藏输入）
        String password;
        if (console != null) {
            char[] pw = console.readPassword("密码: ");
            password = pw != null ? new String(pw) : "";
        } else {
            password = prompt(console, scanner, "密码: ");
        }
        if (password == null || password.isEmpty()) {
            exitWithError("密码不能为空");
        }

        // 交互式输入代理（可选）
        String proxy = prompt(console, scanner, "代理 (如 127.0.0.1:7890，直连请回车): ");
        if (proxy != null && proxy.isEmpty()) {
            // 尝试从环境变量获取
            proxy = System.getenv("https_proxy");
            if (proxy == null) proxy = System.getenv("HTTPS_PROXY");
            if (proxy == null) proxy = System.getenv("http_proxy");
            if (proxy == null) proxy = System.getenv("HTTP_PROXY");
        }

        // 连接
        service = new WebDavService();
        try {
            System.out.println("正在连接...");
            service.connect(username, password, proxy);
            System.out.println("连接成功");
        } catch (Exception e) {
            exitWithError("连接失败: " + e.getMessage());
        }

        System.out.println("输入 help 查看可用命令");
        System.out.println();
        while (true) {
            System.out.print("webdav> ");
            System.out.flush();

            if (!scanner.hasNextLine()) break;
            String line = scanner.nextLine().trim();
            if (line.isEmpty()) continue;

            String[] parts = splitArgs(line);
            String cmd = parts[0];

            try {
                switch (cmd) {
                    case "ls" -> doLs(parts.length > 1 ? parts[1] : null);
                    case "cd" -> doCd(parts.length > 1 ? parts[1] : "/");
                    case "pwd" -> System.out.println(currentDir);
                    case "download", "dl" -> doDownload(
                            parts.length > 1 ? parts[1] : null,
                            parts.length > 2 ? parts[2] : null);
                    case "help", "?" -> printShellHelp();
                    case "exit", "quit" -> {
                        System.out.println("再见");
                        service.disconnect();
                        return;
                    }
                    default -> System.out.println("未知命令: " + cmd + "，输入 help 查看帮助");
                }
            } catch (Exception e) {
                System.err.println("错误: " + e.getMessage());
            }
        }
    }

    private static void doLs(String path) throws IOException, InterruptedException {
        String url = resolveUrl(path != null ? resolvePath(path) : currentDir);
        List<RemoteFile> files = service.listDirectory(url);

        if (files.isEmpty()) {
            System.out.println("(空目录)");
            return;
        }

        // 计算对齐宽度
        int maxNameLen = files.stream().mapToInt(f -> displayWidth(f.name())).max().orElse(10);
        maxNameLen = Math.min(maxNameLen, 60);

        for (RemoteFile file : files) {
            String type = file.isDirectory() ? "DIR " : "FILE";
            String name = file.isDirectory() ? file.name() + "/" : file.name();
            int padding = maxNameLen - displayWidth(name) + 2;
            if (padding < 1) padding = 1;
            System.out.printf("  %s  %s%s%s%n",
                    type,
                    name,
                    " ".repeat(padding),
                    file.isDirectory() ? "" : file.formattedSize());
        }
        System.out.printf("共 %d 项%n", files.size());
    }

    private static void doCd(String path) throws IOException, InterruptedException {
        String target = resolvePath(path);
        // 验证目录是否存在（尝试列出）
        String url = resolveUrl(target);
        service.listDirectory(url);
        currentDir = target;
    }

    private static void doDownload(String remotePath, String localPath)
            throws IOException, InterruptedException {
        if (remotePath == null || remotePath.isEmpty()) {
            System.out.println("用法: download <远程文件> [本地路径]");
            return;
        }

        String resolved = resolvePath(remotePath);
        String url = resolveUrl(resolved);

        // 确定本地文件名
        String fileName = resolved.contains("/")
                ? resolved.substring(resolved.lastIndexOf('/') + 1)
                : resolved;

        File localFile;
        if (localPath != null) {
            File target = new File(localPath);
            if (target.isDirectory()) {
                localFile = new File(target, fileName);
            } else {
                localFile = target;
            }
        } else {
            localFile = new File(fileName);
        }

        System.out.printf("正在下载: %s -> %s%n", resolved, localFile.getAbsolutePath());

        long totalSize = service.getContentLength(url);
        long bytesRead = 0;
        byte[] buffer = new byte[8192];

        try (InputStream in = service.getFileStream(url);
             FileOutputStream out = new FileOutputStream(localFile)) {

            int len;
            long lastPrint = System.currentTimeMillis();
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
                bytesRead += len;

                long now = System.currentTimeMillis();
                if (now - lastPrint >= 500) {
                    lastPrint = now;
                    printProgress(bytesRead, totalSize);
                }
            }
        }

        printProgress(bytesRead, totalSize);
        System.out.println();
        System.out.printf("下载完成: %s (%s)%n", localFile.getName(), formatSize(bytesRead));
    }

    /**
     * 解析相对/绝对路径为标准绝对路径。
     * - "/"          -> "/"
     * - "Documents"  -> currentDir + "/Documents"
     * - "/全部文件"   -> "/全部文件"
     * - ".."         -> 上级目录
     */
    private static String resolvePath(String path) {
        if (path.startsWith("/")) {
            return normalizePath(path);
        }
        String base = currentDir.endsWith("/") ? currentDir : currentDir + "/";
        return normalizePath(base + path);
    }

    private static String normalizePath(String path) {
        String[] parts = path.split("/");
        var stack = new java.util.ArrayDeque<String>();
        for (String part : parts) {
            if (part.isEmpty() || ".".equals(part)) continue;
            if ("..".equals(part)) {
                if (!stack.isEmpty()) stack.pollLast();
            } else {
                stack.addLast(part);
            }
        }
        if (stack.isEmpty()) return "/";
        StringBuilder sb = new StringBuilder();
        for (String s : stack) {
            sb.append("/").append(s);
        }
        return sb.toString();
    }

    /**
     * 将标准路径转为完整 URL。
     */
    private static String resolveUrl(String absPath) {
        String base = service.getBaseUrl();
        if ("/".equals(absPath)) {
            return base;
        }
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);

        try {
            String[] segments = absPath.split("/", -1);
            StringBuilder encoded = new StringBuilder();
            for (String seg : segments) {
                if (!seg.isEmpty()) {
                    encoded.append("/").append(java.net.URLEncoder.encode(seg, "UTF-8").replace("+", "%20"));
                }
            }
            return base + encoded;
        } catch (java.io.UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 简单分割命令行，支持引号包裹含空格的参数。
     */
    private static String[] splitArgs(String line) {
        var args = new java.util.ArrayList<String>();
        StringBuilder current = new StringBuilder();
        boolean inQuote = false;
        char quoteChar = 0;

        for (int j = 0; j < line.length(); j++) {
            char c = line.charAt(j);
            if (inQuote) {
                if (c == quoteChar) {
                    inQuote = false;
                } else {
                    current.append(c);
                }
            } else if (c == '"' || c == '\'') {
                inQuote = true;
                quoteChar = c;
            } else if (c == ' ' || c == '\t') {
                if (!current.isEmpty()) {
                    args.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (!current.isEmpty()) {
            args.add(current.toString());
        }
        return args.toArray(new String[0]);
    }

    private static void printProgress(long bytesRead, long totalSize) {
        if (totalSize > 0) {
            int percent = (int) (bytesRead * 100 / totalSize);
            System.out.printf("\r  %s / %s  (%d%%)", formatSize(bytesRead), formatSize(totalSize), percent);
        } else {
            System.out.printf("\r  %s", formatSize(bytesRead));
        }
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private static int displayWidth(String s) {
        int width = 0;
        for (int j = 0; j < s.length(); j++) {
            char c = s.charAt(j);
            if (c >= 0x4E00 && c <= 0x9FFF || c >= 0x3400 && c <= 0x4DBF
                    || c >= 0xF900 && c <= 0xFAFF || c >= 0xFF00 && c <= 0xFFEF) {
                width += 2;
            } else {
                width += 1;
            }
        }
        return width;
    }

    private static void exitWithError(String message) {
        System.err.println("错误: " + message);
        System.exit(1);
    }

    private static void printShellHelp() {
        System.out.println("""
                可用命令:
                  ls   [路径]              列出目录内容 (默认当前目录)
                  cd   <路径>              切换目录 (支持 .. 和绝对/相对路径)
                  pwd                      显示当前路径
                  download <文件> [本地]    下载文件到本地 (默认当前目录, 别名: dl)
                  help                     显示本帮助
                  exit                     退出 (别名: quit)""");
    }

    private static String prompt(Console console, Scanner scanner, String message) {
        System.out.print(message);
        System.out.flush();
        if (scanner.hasNextLine()) {
            return scanner.nextLine().trim();
        }
        return null;
    }

    private static void printUsage() {
        System.out.println("""
                阿里云盘 WebDAV CLI

                用法:
                  java -jar alipan-cli.jar

                启动后按提示输入账号、密码和代理，连接成功后进入交互式 shell。""");
    }
}
