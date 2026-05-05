package vn.edu.iuh.fit.client.controller;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import vn.edu.iuh.fit.client.service.SessionManager;
import vn.edu.iuh.fit.client.service.SocketRequestService;
import vn.edu.iuh.fit.common.command.ActionType;
import vn.edu.iuh.fit.common.constant.RouteStatus;
import vn.edu.iuh.fit.common.constant.TrainStatus;
import vn.edu.iuh.fit.common.dto.RouteDTO;
import vn.edu.iuh.fit.common.dto.ScheduleGenerateDTO;
import vn.edu.iuh.fit.common.dto.ScheduleGenerateResultDTO;
import vn.edu.iuh.fit.common.dto.TrainDTO;
import vn.edu.iuh.fit.common.dto.TrainFilterDTO;
import vn.edu.iuh.fit.common.request.Request;
import vn.edu.iuh.fit.common.response.Response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

public class GenerateSchedulesDialogController {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("H:mm");

    @FXML private ComboBox<RouteDTO> routeCombo;
    @FXML private Label routeInfoLabel;
    @FXML private ComboBox<TrainDTO> trainCombo;
    @FXML private DatePicker startDatePicker;
    @FXML private TextField departureTimeField;
    @FXML private RadioButton radio7Days;
    @FXML private RadioButton radio30Days;
    @FXML private CheckBox roundTripCheckbox;
    @FXML private Button btnGenerate;

    @FXML
    public void initialize() {
        setupConverters();
        startDatePicker.setValue(LocalDate.now().plusDays(1));
        departureTimeField.setText("08:00");
        loadRoutesAsync();
        loadTrainsAsync();
    }

    @FXML
    public void handleGenerate(ActionEvent event) {
        ScheduleGenerateDTO dto = buildDto();
        if (dto == null) {
            return;
        }

        btnGenerate.setDisable(true);
        Task<Response> task = new Task<>() {
            @Override
            protected Response call() {
                return new SocketRequestService().send(new Request(ActionType.GENERATE_SCHEDULES, dto));
            }
        };
        task.setOnSucceeded(e -> Platform.runLater(() -> {
            btnGenerate.setDisable(false);
            Response res = task.getValue();
            if (res.isSuccess()) {
                showInfo("Tạo lịch trình", successMessage(res.getData()));
                closeDialog(event);
            } else {
                showError("Tạo lịch trình", res.getMessage());
            }
        }));
        task.setOnFailed(e -> Platform.runLater(() -> {
            btnGenerate.setDisable(false);
            showError("Tạo lịch trình", "Không thể kết nối đến server: " + task.getException().getMessage());
        }));
        Thread thread = new Thread(task, "generate-schedules-thread");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    public void handleCancel(ActionEvent event) {
        closeDialog(event);
    }

    private void closeDialog(ActionEvent event) {
        Node source = (Node) event.getSource();
        Stage stage = (Stage) source.getScene().getWindow();
        stage.close();
    }

    private void setupConverters() {
        routeCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(RouteDTO route) {
                if (route == null) return "";
                return route.getRouteCode() + " (" + route.getDepartureStationName()
                        + " -> " + route.getDestinationStationName() + ")";
            }

            @Override
            public RouteDTO fromString(String string) {
                return null;
            }
        });
        routeCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldRoute, route) -> {
            if (route == null) {
                routeInfoLabel.setText("");
            } else {
                routeInfoLabel.setText(route.getDepartureStationName() + " -> " + route.getDestinationStationName());
            }
        });

        trainCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(TrainDTO train) {
                return train == null ? "" : train.getTrainCode();
            }

            @Override
            public TrainDTO fromString(String string) {
                return null;
            }
        });
    }

    private void loadRoutesAsync() {
        Task<Response> task = new Task<>() {
            @Override
            protected Response call() {
                return new SocketRequestService().send(new Request(ActionType.FIND_ALL_ROUTES, null));
            }
        };
        task.setOnSucceeded(e -> Platform.runLater(() -> {
            Response res = task.getValue();
            if (res.isSuccess() && res.getData() instanceof List<?> raw) {
                List<RouteDTO> routes = raw.stream()
                        .filter(RouteDTO.class::isInstance)
                        .map(RouteDTO.class::cast)
                        .filter(route -> route.getStatus() == RouteStatus.ACTIVE)
                        .toList();
                routeCombo.setItems(FXCollections.observableArrayList(routes));
                if (!routes.isEmpty()) {
                    routeCombo.getSelectionModel().selectFirst();
                }
            }
        }));
        task.setOnFailed(e -> showError("Tải tuyến", task.getException().getMessage()));
        Thread thread = new Thread(task, "load-generate-routes-thread");
        thread.setDaemon(true);
        thread.start();
    }

    private void loadTrainsAsync() {
        Task<Response> task = new Task<>() {
            @Override
            protected Response call() {
                TrainFilterDTO filter = TrainFilterDTO.builder()
                        .statusFilter(TrainStatus.ACTIVE)
                        .build();
                return new SocketRequestService().send(new Request(ActionType.FIND_ALL_TRAINS, filter));
            }
        };
        task.setOnSucceeded(e -> Platform.runLater(() -> {
            Response res = task.getValue();
            if (res.isSuccess() && res.getData() instanceof List<?> raw) {
                List<TrainDTO> trains = raw.stream()
                        .filter(TrainDTO.class::isInstance)
                        .map(TrainDTO.class::cast)
                        .toList();
                trainCombo.setItems(FXCollections.observableArrayList(trains));
                if (!trains.isEmpty()) {
                    trainCombo.getSelectionModel().selectFirst();
                }
            }
        }));
        task.setOnFailed(e -> showError("Tải tàu", task.getException().getMessage()));
        Thread thread = new Thread(task, "load-generate-trains-thread");
        thread.setDaemon(true);
        thread.start();
    }

    private ScheduleGenerateDTO buildDto() {
        RouteDTO route = routeCombo.getValue();
        TrainDTO train = trainCombo.getValue();
        LocalDate startDate = startDatePicker.getValue();
        LocalTime departureTime;
        try {
            departureTime = LocalTime.parse(departureTimeField.getText().trim(), TIME_FORMATTER);
        } catch (DateTimeParseException | NullPointerException e) {
            showWarning("Dữ liệu không hợp lệ", "Giờ khởi hành phải có dạng HH:mm, ví dụ 08:30.");
            return null;
        }

        if (route == null) {
            showWarning("Dữ liệu không hợp lệ", "Vui lòng chọn tuyến đường.");
            return null;
        }
        if (train == null) {
            showWarning("Dữ liệu không hợp lệ", "Vui lòng chọn tàu.");
            return null;
        }
        if (startDate == null) {
            showWarning("Dữ liệu không hợp lệ", "Vui lòng chọn ngày bắt đầu.");
            return null;
        }
        if (LocalDateTime.of(startDate, departureTime).isBefore(LocalDateTime.now().plusDays(1))) {
            showWarning("Dữ liệu không hợp lệ", "Ngày khởi hành đầu tiên phải cách ít nhất 1 ngày so với hôm nay.");
            return null;
        }

        String employeeId = SessionManager.getInstance().getEmployeeId();
        if (employeeId == null || employeeId.isBlank()) {
            showError("Lỗi phiên làm việc", "Không xác định được nhân viên. Vui lòng đăng nhập lại.");
            return null;
        }

        return ScheduleGenerateDTO.builder()
                .requestEmployeeId(employeeId)
                .routeId(route.getId())
                .trainId(train.getId())
                .startDate(startDate)
                .departureTime(departureTime)
                .days(radio30Days.isSelected() ? 30 : 7)
                .roundTrip(roundTripCheckbox.isSelected())
                .build();
    }

    private String successMessage(Object data) {
        if (data instanceof ScheduleGenerateResultDTO result) {
            return "Đã tạo " + result.getCreatedCount() + " lịch trình"
                    + " (" + result.getOutboundCount() + " chiều đi"
                    + (result.getReturnCount() > 0 ? ", " + result.getReturnCount() + " chiều về" : "")
                    + ").";
        }
        return "Tạo lịch trình tự động thành công.";
    }

    private void showInfo(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
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
