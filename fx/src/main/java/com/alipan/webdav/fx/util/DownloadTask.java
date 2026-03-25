package com.alipan.webdav.fx.util;

import com.alipan.webdav.core.service.WebDavService;
import javafx.concurrent.Task;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class DownloadTask extends Task<Void> {

    private final WebDavService service;
    private final String remoteUrl;
    private final File localFile;
    private final long totalSize;

    public DownloadTask(WebDavService service, String remoteUrl, File localFile, long totalSize) {
        this.service = service;
        this.remoteUrl = remoteUrl;
        this.localFile = localFile;
        this.totalSize = totalSize;
    }

    @Override
    protected Void call() throws Exception {
        updateMessage("正在下载...");
        long bytesRead = 0;
        byte[] buffer = new byte[8192];

        try (InputStream in = service.getFileStream(remoteUrl);
             FileOutputStream out = new FileOutputStream(localFile)) {

            int len;
            while ((len = in.read(buffer)) != -1) {
                if (isCancelled()) {
                    updateMessage("下载已取消");
                    return null;
                }
                out.write(buffer, 0, len);
                bytesRead += len;
                if (totalSize > 0) {
                    updateProgress(bytesRead, totalSize);
                    updateMessage(String.format("正在下载... %s / %s",
                            formatSize(bytesRead), formatSize(totalSize)));
                } else {
                    updateProgress(-1, -1);
                    updateMessage(String.format("正在下载... %s", formatSize(bytesRead)));
                }
            }
        }

        updateMessage("下载完成: " + localFile.getName());
        updateProgress(1, 1);
        return null;
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
