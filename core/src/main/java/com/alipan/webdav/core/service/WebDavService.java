package com.alipan.webdav.core.service;

import com.alipan.webdav.core.model.RemoteFile;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class WebDavService {

    private static final String HOST = "openapi.alipan.com";
    private static final int PORT = 443;
    private static final String[] CANDIDATE_PATHS = {
//            "/dav/全部文件/Downloads",
//            "/dav/Downloads",
            "/dav",
//            "/Downloads",
//            "/"
    };

    private HttpClient httpClient;
    private String authHeader;
    private String baseUrl;

    public void connect(String username, String password, String proxyAddr) throws IOException, InterruptedException {
        var builder = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NORMAL);

        // 配置代理（格式: host:port）
        if (proxyAddr != null && !proxyAddr.isEmpty()) {
            proxyAddr = proxyAddr.replaceFirst("^https?://", "");
            String[] parts = proxyAddr.split(":");
            if (parts.length == 2) {
                builder.proxy(ProxySelector.of(
                        new InetSocketAddress(parts[0], Integer.parseInt(parts[1]))));
            }
        }

        httpClient = builder.build();

        authHeader = "Basic " + Base64.getEncoder().encodeToString(
                (username + ":" + password).getBytes(java.nio.charset.StandardCharsets.UTF_8));

        // 并行探测所有候选路径（每个最多5秒超时）
        var results = new ConcurrentHashMap<String, String>();
        var futures = new ArrayList<CompletableFuture<Void>>();

        for (String path : CANDIDATE_PATHS) {
            String url = encodeUrl(path);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .method("PROPFIND", HttpRequest.BodyPublishers.ofString(PROPFIND_BODY))
                    .header("Authorization", authHeader)
                    .header("Content-Type", "application/xml; charset=utf-8")
                    .header("Depth", "1")
                    .timeout(Duration.ofSeconds(5))
                    .build();

            futures.add(httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(resp -> results.put(path, "HTTP " + resp.statusCode()))
                    .exceptionally(ex -> { results.put(path, ex.getMessage()); return null; }));
        }

        // 等待所有请求完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // 按优先级选择第一个成功的路径
        for (String path : CANDIDATE_PATHS) {
            String result = results.get(path);
            if ("HTTP 207".equals(result) || "HTTP 200".equals(result)) {
                baseUrl = encodeUrl(path);
                return;
            }
            if ("HTTP 401".equals(result)) {
                throw new IOException("认证失败：用户名或密码错误");
            }
        }

        // 全部失败，拼接详情
        StringBuilder tried = new StringBuilder();
        for (String path : CANDIDATE_PATHS) {
            tried.append("\n  ").append(path).append(" -> ").append(results.get(path));
        }
        throw new IOException("无法找到可用路径，尝试结果:" + tried);
    }

    public List<RemoteFile> listDirectory(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .method("PROPFIND", HttpRequest.BodyPublishers.ofString(PROPFIND_BODY))
                .header("Authorization", authHeader)
                .header("Content-Type", "application/xml; charset=utf-8")
                .header("Depth", "1")
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 207) {
            throw new IOException("列目录失败，HTTP状态码: " + response.statusCode());
        }
        String body = response.body();

        return parseMultiStatus(new java.io.ByteArrayInputStream(
                body.getBytes(java.nio.charset.StandardCharsets.UTF_8)), url);
    }

    public InputStream getFileStream(String fileUrl) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(fileUrl))
                .GET()
                .header("Authorization", authHeader)
                .build();

        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new IOException("下载失败，HTTP状态码: " + response.statusCode());
        }
        return response.body();
    }

    /**
     * 获取文件的 Content-Length（通过 HEAD 请求），用于无法从 PROPFIND 获取大小时的回退。
     * 返回 -1 表示无法获取。
     */
    public long getContentLength(String fileUrl) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(fileUrl))
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .header("Authorization", authHeader)
                .build();

        HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() == 200) {
            return response.headers().firstValueAsLong("Content-Length").orElse(-1);
        }
        return -1;
    }

    public void disconnect() {
        // HttpClient doesn't need explicit shutdown
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    private String encodeUrl(String path) {
        try {
            URI uri = new URI("https", null, HOST, PORT, path, null, null);
            return uri.toASCIIString();
        } catch (URISyntaxException e) {
            throw new RuntimeException("URL编码失败: " + path, e);
        }
    }

    private List<RemoteFile> parseMultiStatus(InputStream xml, String parentUrl) throws IOException {
        List<RemoteFile> files = new ArrayList<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(xml);

            // 从 parentUrl 提取路径部分，用于跳过目录自身
            String parentPath = URI.create(parentUrl).getRawPath();
            if (!parentPath.endsWith("/")) parentPath += "/";

            NodeList responses = doc.getElementsByTagNameNS("DAV:", "response");
            for (int i = 0; i < responses.getLength(); i++) {
                Element resp = (Element) responses.item(i);

                String href = getTextContent(resp, "DAV:", "href");
                if (href == null) continue;

                // 跳过目录自身
                String hrefNorm = href.endsWith("/") ? href : href + "/";
                if (hrefNorm.equals(parentPath)) continue;

                // 解析属性
                boolean isDir = false;
                long contentLength = 0;
                String contentType = "";

                NodeList resourceTypes = resp.getElementsByTagNameNS("DAV:", "collection");
                if (resourceTypes.getLength() > 0) isDir = true;

                String clStr = getTextContent(resp, "DAV:", "getcontentlength");
                if (clStr != null && !clStr.isEmpty()) {
                    try { contentLength = Long.parseLong(clStr); } catch (NumberFormatException ignored) {}
                }

                String ct = getTextContent(resp, "DAV:", "getcontenttype");
                if (ct != null) contentType = ct;

                // 从 href 提取文件名
                String decodedHref = RemoteFile.decodeName(href);
                if (decodedHref.endsWith("/")) decodedHref = decodedHref.substring(0, decodedHref.length() - 1);
                int lastSlash = decodedHref.lastIndexOf('/');
                String name = lastSlash >= 0 ? decodedHref.substring(lastSlash + 1) : decodedHref;

                // 构建完整 URL：直接用服务器返回的 href 拼接
                String fullUrl = "https://" + HOST + ":" + PORT + href;

                files.add(new RemoteFile(name, fullUrl, isDir, contentLength, contentType));
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("解析WebDAV响应失败: " + e.getMessage(), e);
        }
        return files;
    }

    private String getTextContent(Element parent, String ns, String localName) {
        NodeList nodes = parent.getElementsByTagNameNS(ns, localName);
        if (nodes.getLength() > 0) {
            return nodes.item(0).getTextContent().trim();
        }
        return null;
    }

    private static final String PROPFIND_BODY = """
            <?xml version="1.0" encoding="utf-8"?>
            <D:propfind xmlns:D="DAV:">
                <D:prop>
                    <D:resourcetype/>
                    <D:getcontentlength/>
                    <D:getcontenttype/>
                </D:prop>
            </D:propfind>
            """;
}
