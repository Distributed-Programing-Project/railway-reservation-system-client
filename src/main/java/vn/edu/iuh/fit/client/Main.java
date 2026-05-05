package vn.edu.iuh.fit.client;

import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class Main extends Application {
    @Override
    public void start(Stage stage) throws Exception {
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/ui/views/login.fxml"));
        StackPane root = loader.load();

        Scene scene = new Scene(root, 1200, 700);
        stage.setTitle("Train Station - Schedule Management");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
