package com.alipan.webdav.fx.controller;

import com.alipan.webdav.core.service.WebDavService;
import com.alipan.webdav.fx.App;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class LoginController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private TextField proxyField;
    @FXML private Button loginButton;
    @FXML private Label statusLabel;

    @FXML
    private void initialize() {
        // 自动填充环境变量中的代理
        String proxy = System.getenv("https_proxy");
        if (proxy == null) proxy = System.getenv("HTTPS_PROXY");
        if (proxy == null) proxy = System.getenv("http_proxy");
        if (proxy == null) proxy = System.getenv("HTTP_PROXY");
        if (proxy != null && !proxy.isEmpty()) {
            // 去掉 http:// 前缀，只保留 host:port
            proxy = proxy.replaceFirst("^https?://", "");
            proxyField.setText(proxy);
        }
    }

    @FXML
    private void onLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        String proxyText = proxyField.getText().trim();

        if (username.isEmpty() || password.isEmpty()) {
            statusLabel.setText("请输入用户名和密码");
            statusLabel.setStyle("-fx-text-fill: red;");
            return;
        }

        loginButton.setDisable(true);
        statusLabel.setText("连接中...");
        statusLabel.setStyle("-fx-text-fill: #333;");

        WebDavService service = new WebDavService();

        Task<Void> connectTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                service.connect(username, password, proxyText);
                return null;
            }
        };

        connectTask.setOnSucceeded(e -> {
            Stage stage = (Stage) loginButton.getScene().getWindow();
            App.switchToBrowser(stage, service);
        });

        connectTask.setOnFailed(e -> {
            loginButton.setDisable(false);
            Throwable ex = connectTask.getException();
            String msg = ex.getMessage();
            if (msg == null) msg = ex.getClass().getSimpleName();
            statusLabel.setText("连接失败: " + msg);
            statusLabel.setStyle("-fx-text-fill: red;");
        });

        new Thread(connectTask, "webdav-connect").start();
    }
}
