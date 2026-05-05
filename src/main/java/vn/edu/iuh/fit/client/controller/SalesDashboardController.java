package vn.edu.iuh.fit.client.controller;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Duration;
import vn.edu.iuh.fit.client.service.SessionManager;
import vn.edu.iuh.fit.common.dto.AccountDTO;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class SalesDashboardController {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    @FXML
    private Button btnDashboard;

    @FXML
    private Button btnBanVe;

    @FXML
    private Button btnDoiVe;

    @FXML
    private Button btnTraVe;

    @FXML
    private Button btnKhachHang;

    @FXML
    private Button btnHoaDon;

    @FXML
    private Button btnThongKe;

    @FXML
    private Button btnLogout;

    @FXML
    private Label dateTimeLabel;

    @FXML
    private StackPane contentPane;

    private Timeline clockTimeline;
    private AccountDTO accountDTO;

    @FXML
    public void initialize() {
        bindEvents();
        startClock();
        openHomeView();
    }

    public void setAccount(AccountDTO accountDTO) {
        this.accountDTO = accountDTO;
    }

    private void bindEvents() {
        btnDashboard.setOnAction(event -> openHomeView());
        btnBanVe.setOnAction(event -> loadContent("/client/ui/views/step-1.fxml"));
        btnDoiVe.setOnAction(event -> loadContent("/client/ui/views/doi-ve.fxml"));
        btnTraVe.setOnAction(event -> loadContent("/client/ui/views/tra-ve.fxml"));
        btnKhachHang.setOnAction(event -> loadContent("/client/ui/views/customer-management.fxml"));
        btnHoaDon.setOnAction(event -> loadContent("/client/ui/views/invoice-management.fxml"));
        btnThongKe.setOnAction(event -> loadContent("/client/ui/views/statistics-management.fxml"));
        btnLogout.setOnAction(event -> logout());
    }

    private void openHomeView() {
        loadContent("/client/ui/views/employee-dashboard-content.fxml");
    }

    private void loadContent(String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent view = loader.load();
            contentPane.getChildren().setAll(view);
        } catch (IOException e) {
            showError("Không thể mở màn hình", "Lỗi khi load " + fxmlPath + "\n" + e.getMessage());
        }
    }

    private void startClock() {
        updateClock();
        clockTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> updateClock()));
        clockTimeline.setCycleCount(Timeline.INDEFINITE);
        clockTimeline.play();
    }

    private void updateClock() {
        if (dateTimeLabel != null) {
            dateTimeLabel.setText(DATE_TIME_FORMATTER.format(LocalDateTime.now()));
        }
    }

    private void logout() {
        try {
            if (clockTimeline != null) {
                clockTimeline.stop();
            }
            SessionManager.getInstance().clear();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/ui/views/login.fxml"));
            Parent root = loader.load();

            Stage stage = (Stage) contentPane.getScene().getWindow();
            stage.setScene(new Scene(root, 1200, 700));
            stage.setTitle("Train Station - Login");
            stage.show();
        } catch (IOException e) {
            showError("Đăng xuất", "Không thể quay lại màn hình đăng nhập: " + e.getMessage());
        }
    }

    private void showError(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
