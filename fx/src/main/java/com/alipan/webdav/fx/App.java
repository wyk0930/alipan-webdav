package com.alipan.webdav.fx;

import com.alipan.webdav.core.service.WebDavService;
import com.alipan.webdav.fx.controller.BrowserController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class App extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("login-view.fxml"));
        Parent root = loader.load();
        Scene scene = new Scene(root, 440, 460);
        scene.getStylesheets().add(getClass().getResource("styles.css").toExternalForm());
        primaryStage.setTitle("WebDAV");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void switchToBrowser(Stage stage, WebDavService service) {
        try {
            FXMLLoader loader = new FXMLLoader(App.class.getResource("browser-view.fxml"));
            Parent root = loader.load();
            BrowserController controller = loader.getController();
            controller.init(service);
            Scene scene = new Scene(root, 900, 600);
            scene.getStylesheets().add(App.class.getResource("styles.css").toExternalForm());
            stage.setScene(scene);
            stage.setTitle("WebDAV");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
