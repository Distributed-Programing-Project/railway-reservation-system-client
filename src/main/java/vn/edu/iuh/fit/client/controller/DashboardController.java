package vn.edu.iuh.fit.client.controller;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

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

public class DashboardController {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    @FXML
    private Label lblUsername;

    @FXML
    private Label lblRole;

    @FXML
    private Label dateTimeLabel;

    @FXML
    private StackPane contentPane;

    @FXML
    private Button btnHome;

    @FXML
    private Button btnInvoice;

    @FXML
    private Button btnSellTicket;

    @FXML
    private Button btnExchangeTicket;

    @FXML
    private Button btnReturnTicket;

    @FXML
    private Button btnSchedule;

    @FXML
    private Button btnRoute;

    @FXML
    private Button btnTrain;

    @FXML
    private Button btnEmployee;

    @FXML
    private Button btnCustomer;

    @FXML
    private Button btnStatistics;

    @FXML
    private Button btnLogout;

    private Timeline clockTimeline;
    private String loggedInUsername = "Username";

    @FXML
    public void initialize() {
        bindEvents();
        startClock();
        setActiveMenu(btnHome);
        openHomeView();
    }

    public void setLoggedInUsername(String username) {
        if (username == null || username.isBlank()) {
            return;
        }
        loggedInUsername = username;
        if (lblUsername != null) {
            lblUsername.setText(username);
        }
        openHomeView();
    }

    public void setAccount(AccountDTO accountDTO) {
        if (accountDTO == null) {
            return;
        }
        setLoggedInUsername(accountDTO.getUsername());
        if (lblRole != null) {
            lblRole.setText(accountDTO.isManager() ? "Vai trò: Quản lí" : "Vai trò: Nhân viên");
        }
    }

    private void bindEvents() {
        btnHome.setOnAction(event -> {
            setActiveMenu(btnHome);
            openHomeView();
        });
        btnInvoice.setOnAction(event -> {
            setActiveMenu(btnInvoice);
            loadContent("/client/ui/views/invoice-management.fxml");
        });
        btnSellTicket.setOnAction(event -> {
            setActiveMenu(btnSellTicket);
            openSellTicketView();
        });
        btnExchangeTicket.setOnAction(event -> {
            setActiveMenu(btnExchangeTicket);
            openExchangeTicketView();
        });
        btnReturnTicket.setOnAction(event -> {
            setActiveMenu(btnReturnTicket);
            loadContent("/client/ui/views/tra-ve.fxml");
        });
        btnCustomer.setOnAction(event -> {
            setActiveMenu(btnCustomer);
            loadContent("/client/ui/views/customer-management.fxml");
        });
        btnSchedule.setOnAction(event -> {
            setActiveMenu(btnSchedule);
            openScheduleView();
        });
        btnRoute.setOnAction(event -> {
            setActiveMenu(btnRoute);
            loadContent("/client/ui/views/route-management.fxml");
        });
        btnTrain.setOnAction(event -> {
            setActiveMenu(btnTrain);
            loadContent("/client/ui/views/train-management.fxml");
        });
        btnEmployee.setOnAction(event -> {
            setActiveMenu(btnEmployee);
            loadContent("/client/ui/views/employee-management.fxml");
        });
        btnStatistics.setOnAction(event -> {
            setActiveMenu(btnStatistics);
            loadContent("/client/ui/views/statistics-management.fxml");
        });
        btnLogout.setOnAction(event -> logout());
    }

    private void openExchangeTicketView() {
        try {
            // Tải cái khung Wizard Bán Vé
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/ui/views/ban-ve.fxml"));
            javafx.scene.Parent root = loader.load();

            vn.edu.iuh.fit.client.controller.BanVeController controller = loader.getController();

            // Bật chế độ Đổi vé và show màn hình Tra cứu
            controller.getState().setExchangeMode(true);
            controller.showExchangeSearch();
            contentPane.getChildren().setAll(root);

        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
    }

    private void openHomeView() {
        loadContent("/client/ui/views/dashboard_statistics.fxml", controller -> {
            if (controller instanceof DashboardStatisticsController dashboardStatisticsController) {
                dashboardStatisticsController.setLoggedInUsername(loggedInUsername);
                dashboardStatisticsController.setOnOpenSchedule(() -> {
                    setActiveMenu(btnSchedule);
                    openScheduleView();
                });
            }
        });
    }

    private void openScheduleView() {
        loadContent("/client/ui/views/schedule-management.fxml");
    }

    private void openSellTicketView() {
        if (tryLoadContent("/client/ui/views/ban-ve.fxml")) {
            return;
        }
        loadContent("/client/ui/views/sell-ticket-wizard.fxml");
    }

    private boolean tryLoadContent(String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent view = loader.load();
            contentPane.getChildren().setAll(view);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private void loadContent(String fxmlPath) {
        loadContent(fxmlPath, controller -> {
        });
    }

    private void loadContent(String fxmlPath, ControllerInitializer initializer) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent view = loader.load();
            initializer.initialize(loader.getController());
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
        dateTimeLabel.setText(DATE_TIME_FORMATTER.format(LocalDateTime.now()));
    }

    private void setActiveMenu(Button activeButton) {
        clearActiveMenu();
        if (!activeButton.getStyleClass().contains("menu-item-active")) {
            activeButton.getStyleClass().add("menu-item-active");
        }
    }

    private void clearActiveMenu() {
        btnHome.getStyleClass().remove("menu-item-active");
        btnInvoice.getStyleClass().remove("menu-item-active");
        btnSellTicket.getStyleClass().remove("menu-item-active");
        btnExchangeTicket.getStyleClass().remove("menu-item-active");
        btnReturnTicket.getStyleClass().remove("menu-item-active");
        btnSchedule.getStyleClass().remove("menu-item-active");
        btnRoute.getStyleClass().remove("menu-item-active");
        btnTrain.getStyleClass().remove("menu-item-active");
        btnEmployee.getStyleClass().remove("menu-item-active");
        btnStatistics.getStyleClass().remove("menu-item-active");
        btnCustomer.getStyleClass().remove("menu-item-active");
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
            stage.setTitle("Train Station - Schedule Management");
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

    @FunctionalInterface
    private interface ControllerInitializer {
        void initialize(Object controller);
    }
}
