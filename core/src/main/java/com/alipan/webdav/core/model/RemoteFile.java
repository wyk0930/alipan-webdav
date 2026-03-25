package com.alipan.webdav.core.model;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public record RemoteFile(
        String name,
        String href,
        boolean isDirectory,
        long contentLength,
        String contentType
) {
    public String formattedSize() {
        if (isDirectory) return "-";
        if (contentLength < 1024) return contentLength + " B";
        if (contentLength < 1024 * 1024) return String.format("%.1f KB", contentLength / 1024.0);
        if (contentLength < 1024 * 1024 * 1024) return String.format("%.1f MB", contentLength / (1024.0 * 1024));
        return String.format("%.2f GB", contentLength / (1024.0 * 1024 * 1024));
    }

    public static String decodeName(String encoded) {
        return URLDecoder.decode(encoded, StandardCharsets.UTF_8);
    }
}
