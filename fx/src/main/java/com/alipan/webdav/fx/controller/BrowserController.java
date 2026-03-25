package com.alipan.webdav.fx.controller;

import com.alipan.webdav.core.model.RemoteFile;
import com.alipan.webdav.core.service.WebDavService;
import com.alipan.webdav.fx.util.DownloadTask;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public class BrowserController {

    @FXML private TableView<RemoteFile> fileTable;
    @FXML private TableColumn<RemoteFile, String> nameColumn;
    @FXML private TableColumn<RemoteFile, String> sizeColumn;
    @FXML private TableColumn<RemoteFile, String> typeColumn;
    @FXML private TableColumn<RemoteFile, String> modifiedColumn;
    @FXML private Button downloadButton;
    @FXML private Button refreshButton;
    @FXML private Button backButton;
    @FXML private ProgressBar progressBar;
    @FXML private Label downloadLabel;
    @FXML private Label countLabel;
    @FXML private Label pathLabel;

    private WebDavService service;
    private String currentPath;
    private final Deque<String> pathHistory = new ArrayDeque<>();
    private DownloadTask currentDownload;

    public void init(WebDavService service) {
        this.service = service;
        this.currentPath = service.getBaseUrl();
        setupTable();
        loadDirectory(currentPath);
    }

    private void setupTable() {
        nameColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().name()));
        sizeColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().formattedSize()));
        typeColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().isDirectory() ? "文件夹" : data.getValue().contentType()));
        modifiedColumn.setCellValueFactory(data -> new SimpleStringProperty("-"));

        fileTable.setRowFactory(tv -> {
            TableRow<RemoteFile> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && event.getButton() == MouseButton.PRIMARY
                        && !row.isEmpty() && row.getItem().isDirectory()) {
                    navigateTo(row.getItem().href());
                }
            });
            return row;
        });

        fileTable.setPlaceholder(new Label("空目录"));
    }

    private void navigateTo(String path) {
        pathHistory.push(currentPath);
        loadDirectory(path);
    }

    @FXML
    private void onBack() {
        if (!pathHistory.isEmpty()) {
            loadDirectory(pathHistory.pop());
        }
    }

    @FXML
    private void onRefresh() {
        loadDirectory(currentPath);
    }

    @FXML
    private void onDownload() {
        RemoteFile selected = fileTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            downloadLabel.setText("请先选择一个文件");
            return;
        }
        if (selected.isDirectory()) {
            downloadLabel.setText("不支持下载文件夹");
            return;
        }
        if (currentDownload != null && currentDownload.isRunning()) {
            downloadLabel.setText("有下载任务进行中，请等待完成");
            return;
        }

        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("选择下载目录");
        Stage stage = (Stage) downloadButton.getScene().getWindow();
        File dir = chooser.showDialog(stage);
        if (dir == null) return;

        File localFile = new File(dir, selected.name());

        currentDownload = new DownloadTask(service, selected.href(), localFile, selected.contentLength());
        progressBar.progressProperty().bind(currentDownload.progressProperty());
        downloadLabel.textProperty().bind(currentDownload.messageProperty());

        currentDownload.setOnSucceeded(e -> {
            progressBar.progressProperty().unbind();
            downloadLabel.textProperty().unbind();
            progressBar.setProgress(1);
            downloadLabel.setText("下载完成: " + localFile.getName());
        });

        currentDownload.setOnFailed(e -> {
            progressBar.progressProperty().unbind();
            downloadLabel.textProperty().unbind();
            progressBar.setProgress(0);
            Throwable ex = currentDownload.getException();
            downloadLabel.setText("下载失败: " + (ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName()));
        });

        currentDownload.setOnCancelled(e -> {
            progressBar.progressProperty().unbind();
            downloadLabel.textProperty().unbind();
            progressBar.setProgress(0);
            downloadLabel.setText("下载已取消");
        });

        new Thread(currentDownload, "webdav-download").start();
    }

    private void loadDirectory(String path) {
        countLabel.setText("加载中...");
        fileTable.setDisable(true);

        Task<List<RemoteFile>> listTask = new Task<>() {
            @Override
            protected List<RemoteFile> call() throws Exception {
                return service.listDirectory(path);
            }
        };

        listTask.setOnSucceeded(e -> {
            currentPath = path;
            updatePathLabel();
            fileTable.setItems(FXCollections.observableArrayList(listTask.getValue()));
            fileTable.setDisable(false);
            countLabel.setText("共 " + listTask.getValue().size() + " 项");
            backButton.setDisable(pathHistory.isEmpty());
        });

        listTask.setOnFailed(e -> {
            fileTable.setDisable(false);
            Throwable ex = listTask.getException();
            countLabel.setText("加载失败: " + (ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName()));
        });

        new Thread(listTask, "webdav-list").start();
    }

    private void updatePathLabel() {
        try {
            String decoded = URLDecoder.decode(currentPath, StandardCharsets.UTF_8);
            int idx = decoded.indexOf("/dav/");
            if (idx >= 0) {
                pathLabel.setText(decoded.substring(idx + 4));
            } else {
                pathLabel.setText(decoded);
            }
        } catch (Exception e) {
            pathLabel.setText(currentPath);
        }
    }
}
