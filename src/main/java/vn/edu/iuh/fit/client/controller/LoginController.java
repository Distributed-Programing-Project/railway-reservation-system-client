package vn.edu.iuh.fit.client.controller;

import java.io.IOException;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import vn.edu.iuh.fit.client.service.LoginClientService;
import vn.edu.iuh.fit.client.service.SessionManager;
import vn.edu.iuh.fit.common.dto.AccountDTO;
import vn.edu.iuh.fit.common.response.Response;

public class LoginController {

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private StackPane loadingOverlay;

    private final LoginClientService loginClientService = new LoginClientService();

    @FXML
    public void initialize() {
        if (loadingOverlay != null) {
            loadingOverlay.setVisible(false);
        }
    }

    @FXML
    public void handleLogin() {
        String username = normalize(usernameField.getText());
        String password = passwordField.getText();

        if (username == null || password == null || password.isBlank()) {
            showWarning("Đăng nhập", "Vui lòng nhập đầy đủ tên đăng nhập và mật khẩu.");
            return;
        }

        setLoading(true);
        Task<Response> loginTask = new Task<>() {
            @Override
            protected Response call() {
                return loginClientService.login(username, password);
            }
        };

        loginTask.setOnSucceeded(event -> {
            setLoading(false);
            Response response = loginTask.getValue();
            if (response != null && response.isSuccess()) {
                openDashboard(response.getData());
            } else {
                String message = response == null ? "Không nhận được phản hồi từ server." : response.getMessage();
                showError("Đăng nhập thất bại", message);
            }
        });

        loginTask.setOnFailed(event -> {
            setLoading(false);
            showError("Đăng nhập thất bại", "Đã xảy ra lỗi khi xử lý đăng nhập.");
        });

        Thread thread = new Thread(loginTask, "login-request-thread");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    public void handleHelp() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/ui/views/help-login-dialog.fxml"));
            Parent root = loader.load();

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Trợ giúp đăng nhập");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(usernameField.getScene().getWindow());
            dialogStage.setScene(new Scene(root));
            dialogStage.setResizable(false);
            dialogStage.show();
        } catch (IOException e) {
            showError("Trợ giúp", "Không thể mở cửa sổ trợ giúp: " + e.getMessage());
        }
    }

    private void openDashboard(Object responseData) {
        AccountDTO accountDTO = responseData instanceof AccountDTO dto ? dto : null;
        boolean isManager = accountDTO != null && accountDTO.isManager();
        if (accountDTO != null) {
            SessionManager.getInstance().setSession(
                    accountDTO.getEmployeeId(),
                    accountDTO.getUsername(),
                    accountDTO.isManager());

        }
        String dashboardPath = isManager
                ? "/client/ui/views/dashboard.fxml"
                : "/client/ui/views/ban-ve.fxml";

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(dashboardPath));
            Parent root = loader.load();

            Object controller = loader.getController();
            if (accountDTO != null) {
                if (controller instanceof DashboardController dashboardController) {
                    dashboardController.setAccount(accountDTO);
                } else if (controller instanceof SalesDashboardController salesDashboardController) {
                    salesDashboardController.setAccount(accountDTO);
                }
            }

            Stage stage = (Stage) usernameField.getScene().getWindow();
            stage.setScene(new Scene(root, 1200, 700));
            stage.setTitle(isManager
                    ? "Train Station - Admin Dashboard"
                    : "Train Station - Ticket Sales Dashboard");
            stage.show();
        } catch (IOException e) {
            showError("Đăng nhập", "Không thể mở dashboard: " + e.getMessage());
        }
    }

    private void setLoading(boolean loading) {
        if (loadingOverlay != null) {
            loadingOverlay.setVisible(loading);
            loadingOverlay.setManaged(loading);
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void showWarning(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private void showError(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
