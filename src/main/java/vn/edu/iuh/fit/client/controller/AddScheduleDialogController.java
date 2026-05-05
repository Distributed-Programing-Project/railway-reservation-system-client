package vn.edu.iuh.fit.client.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import vn.edu.iuh.fit.client.service.SessionManager;
import vn.edu.iuh.fit.client.service.SocketRequestService;
import vn.edu.iuh.fit.common.command.ActionType;
import vn.edu.iuh.fit.common.constant.RouteStatus;
import vn.edu.iuh.fit.common.constant.TrainStatus;
import vn.edu.iuh.fit.common.dto.RouteDTO;
import vn.edu.iuh.fit.common.dto.ScheduleCreateDTO;
import vn.edu.iuh.fit.common.dto.ScheduleDTO;
import vn.edu.iuh.fit.common.dto.ScheduleUpdateDTO;
import vn.edu.iuh.fit.common.dto.TrainDTO;
import vn.edu.iuh.fit.common.dto.TrainFilterDTO;
import vn.edu.iuh.fit.common.request.Request;
import vn.edu.iuh.fit.common.response.Response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

public class AddScheduleDialogController {

    @FXML private Label dialogTitle;
    @FXML private ComboBox<RouteDTO> routeCombo;
    @FXML private ComboBox<TrainDTO> trainCombo;
    @FXML private DatePicker departureDatePicker;
    @FXML private ComboBox<Integer> hourCombo;
    @FXML private ComboBox<Integer> minuteCombo;
    @FXML private DatePicker arrivalDatePicker;
    @FXML private ComboBox<Integer> arrivalHourCombo;
    @FXML private ComboBox<Integer> arrivalMinuteCombo;
    @FXML private Button btnSave;
    @FXML private Button btnCancel;

    private String editingScheduleId = null;
    private String preSelectedTrainId = null;
    private String preSelectedRouteId = null;

    @FXML
    public void initialize() {
        setupTimeComboBoxes();
        setupComboConverters();
        loadRoutesAsync();
        loadTrainsAsync();
    }

    public void initForEdit(ScheduleDTO dto) {
        editingScheduleId = dto.getId();
        preSelectedTrainId = dto.getTrainId();
        preSelectedRouteId = dto.getRouteId();

        if (dialogTitle != null) {
            dialogTitle.setText("Sửa Lịch Trình");
        }

        if (dto.getDepartureTime() != null) {
            departureDatePicker.setValue(dto.getDepartureTime().toLocalDate());
            hourCombo.getSelectionModel().select(dto.getDepartureTime().getHour());
            selectNearestMinute(minuteCombo, dto.getDepartureTime().getMinute());
        }
        if (dto.getArrivalTime() != null) {
            arrivalDatePicker.setValue(dto.getArrivalTime().toLocalDate());
            arrivalHourCombo.getSelectionModel().select(dto.getArrivalTime().getHour());
            selectNearestMinute(arrivalMinuteCombo, dto.getArrivalTime().getMinute());
        }
        selectPreselectedRoute();
        selectPreselectedTrain();
    }

    private void selectNearestMinute(ComboBox<Integer> combo, int minute) {
        int nearest = List.of(0, 15, 30, 45).stream()
                .min((a, b) -> Math.abs(a - minute) - Math.abs(b - minute))
                .orElse(0);
        combo.getSelectionModel().select(Integer.valueOf(nearest));
    }

    private void setupTimeComboBoxes() {
        List<Integer> hours = IntStream.rangeClosed(0, 23).boxed().toList();
        List<Integer> minutes = List.of(0, 15, 30, 45);
        hourCombo.setItems(FXCollections.observableArrayList(hours));
        minuteCombo.setItems(FXCollections.observableArrayList(minutes));
        arrivalHourCombo.setItems(FXCollections.observableArrayList(hours));
        arrivalMinuteCombo.setItems(FXCollections.observableArrayList(minutes));
        hourCombo.getSelectionModel().select(Integer.valueOf(8));
        minuteCombo.getSelectionModel().select(Integer.valueOf(0));
        arrivalHourCombo.getSelectionModel().select(Integer.valueOf(12));
        arrivalMinuteCombo.getSelectionModel().select(Integer.valueOf(0));
    }

    private void setupComboConverters() {
        routeCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(RouteDTO r) {
                if (r == null) return "";
                String dep = r.getDepartureStationName() != null ? r.getDepartureStationName() : r.getDepartureStationId();
                String dest = r.getDestinationStationName() != null ? r.getDestinationStationName() : r.getDestinationStationId();
                return r.getRouteCode() + " (" + dep + " → " + dest + ")";
            }
            @Override
            public RouteDTO fromString(String s) { return null; }
        });

        trainCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(TrainDTO t) { return t == null ? "" : t.getTrainCode(); }
            @Override
            public TrainDTO fromString(String s) { return null; }
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
                        .filter(route -> route.getStatus() == RouteStatus.ACTIVE
                                || (preSelectedRouteId != null && preSelectedRouteId.equals(route.getId())))
                        .toList();
                routeCombo.setItems(FXCollections.observableArrayList(routes));
                selectPreselectedRoute();
            }
        }));
        task.setOnFailed(e -> System.err.println("ERROR: Failed to load routes: " + task.getException().getMessage()));
        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    private void loadTrainsAsync() {
        Task<Response> task = new Task<>() {
            @Override
            protected Response call() {
                return new SocketRequestService().send(new Request(ActionType.FIND_ALL_TRAINS, new TrainFilterDTO()));
            }
        };
        task.setOnSucceeded(e -> Platform.runLater(() -> {
            Response res = task.getValue();
            if (res.isSuccess() && res.getData() instanceof List<?> raw) {
                List<TrainDTO> trains = raw.stream()
                        .filter(TrainDTO.class::isInstance)
                        .map(TrainDTO.class::cast)
                        .filter(train -> train.getStatus() == TrainStatus.ACTIVE
                                || (preSelectedTrainId != null && preSelectedTrainId.equals(train.getId())))
                        .toList();
                trainCombo.setItems(FXCollections.observableArrayList(trains));
                selectPreselectedTrain();
            }
        }));
        task.setOnFailed(e -> System.err.println("ERROR: Failed to load trains: " + task.getException().getMessage()));
        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    @FXML
    private void handleSave() {
        List<String> errors = validateForm();
        if (!errors.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Dữ liệu không hợp lệ", String.join("\n", errors));
            return;
        }

        String employeeId = SessionManager.getInstance().getEmployeeId();
        if (employeeId == null || employeeId.isBlank()) {
            showAlert(Alert.AlertType.ERROR, "Lỗi phiên làm việc", "Không xác định được nhân viên. Vui lòng đăng nhập lại.");
            return;
        }

        LocalDateTime departureTime = buildDateTime(
                departureDatePicker.getValue(), hourCombo.getValue(), minuteCombo.getValue());
        LocalDateTime arrivalTime = buildDateTime(
                arrivalDatePicker.getValue(), arrivalHourCombo.getValue(), arrivalMinuteCombo.getValue());

        btnSave.setDisable(true);

        Task<Response> task;
        if (editingScheduleId != null) {
            ScheduleUpdateDTO dto = ScheduleUpdateDTO.builder()
                    .requestEmployeeId(employeeId)
                    .scheduleId(editingScheduleId)
                    .trainId(trainCombo.getValue().getId())
                    .routeId(routeCombo.getValue().getId())
                    .departureTime(departureTime)
                    .arrivalTime(arrivalTime)
                    .build();
            task = new Task<>() {
                @Override
                protected Response call() {
                    return new SocketRequestService().send(new Request(ActionType.UPDATE_SCHEDULE, dto));
                }
            };
        } else {
            ScheduleCreateDTO dto = ScheduleCreateDTO.builder()
                    .requestEmployeeId(employeeId)
                    .trainId(trainCombo.getValue().getId())
                    .routeId(routeCombo.getValue().getId())
                    .departureTime(departureTime)
                    .arrivalTime(arrivalTime)
                    .build();
            task = new Task<>() {
                @Override
                protected Response call() {
                    return new SocketRequestService().send(new Request(ActionType.CREATE_SCHEDULE, dto));
                }
            };
        }

        task.setOnSucceeded(e -> Platform.runLater(() -> {
            btnSave.setDisable(false);
            Response res = task.getValue();
            if (res.isSuccess()) {
                String msg = editingScheduleId != null
                        ? "Cập nhật lịch trình thành công."
                        : "Tạo lịch trình thành công (trạng thái: Bản nháp).";
                showAlert(Alert.AlertType.INFORMATION, "Thành công", msg);
                closeDialog();
            } else {
                showAlert(Alert.AlertType.ERROR, "Lỗi", res.getMessage());
            }
        }));
        task.setOnFailed(e -> Platform.runLater(() -> {
            btnSave.setDisable(false);
            showAlert(Alert.AlertType.ERROR, "Lỗi kết nối", "Không thể kết nối đến server: " + task.getException().getMessage());
        }));
        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    @FXML
    private void handleCancel() {
        closeDialog();
    }

    private List<String> validateForm() {
        List<String> errors = new ArrayList<>();
        if (routeCombo.getValue() == null) errors.add("- Vui lòng chọn tuyến đường.");
        if (trainCombo.getValue() == null) errors.add("- Vui lòng chọn tàu.");
        if (departureDatePicker.getValue() == null) errors.add("- Vui lòng chọn ngày khởi hành.");
        if (hourCombo.getValue() == null) errors.add("- Vui lòng chọn giờ khởi hành.");
        if (minuteCombo.getValue() == null) errors.add("- Vui lòng chọn phút khởi hành.");
        if (arrivalDatePicker.getValue() == null) errors.add("- Vui lòng chọn ngày đến dự kiến.");
        if (arrivalHourCombo.getValue() == null) errors.add("- Vui lòng chọn giờ đến.");
        if (arrivalMinuteCombo.getValue() == null) errors.add("- Vui lòng chọn phút đến.");

        if (errors.isEmpty()) {
            LocalDateTime departure = buildDateTime(
                    departureDatePicker.getValue(), hourCombo.getValue(), minuteCombo.getValue());
            LocalDateTime arrival = buildDateTime(
                    arrivalDatePicker.getValue(), arrivalHourCombo.getValue(), arrivalMinuteCombo.getValue());

            if (editingScheduleId != null) {
                // Edit mode: chỉ cần > now()
                if (!departure.isAfter(LocalDateTime.now())) {
                    errors.add("- Ngày giờ khởi hành phải ở tương lai.");
                }
            } else {
                // Create mode: phải cách ít nhất 1 ngày
                if (departure.isBefore(LocalDateTime.now().plusDays(1))) {
                    errors.add("- Ngày khởi hành phải cách ít nhất 1 ngày so với hôm nay.");
                }
            }
            if (!arrival.isAfter(departure)) {
                errors.add("- Ngày giờ đến dự kiến phải sau ngày giờ khởi hành.");
            }
        }
        return errors;
    }

    private LocalDateTime buildDateTime(LocalDate date, Integer hour, Integer minute) {
        return LocalDateTime.of(date, LocalTime.of(hour != null ? hour : 0, minute != null ? minute : 0));
    }

    private void closeDialog() {
        Stage stage = (Stage) btnCancel.getScene().getWindow();
        stage.close();
    }

    private void selectPreselectedRoute() {
        if (preSelectedRouteId == null || routeCombo.getItems() == null) {
            return;
        }
        routeCombo.getItems().stream()
                .filter(route -> preSelectedRouteId.equals(route.getId()))
                .findFirst()
                .ifPresent(routeCombo.getSelectionModel()::select);
    }

    private void selectPreselectedTrain() {
        if (preSelectedTrainId == null || trainCombo.getItems() == null) {
            return;
        }
        trainCombo.getItems().stream()
                .filter(train -> preSelectedTrainId.equals(train.getId()))
                .findFirst()
                .ifPresent(trainCombo.getSelectionModel()::select);
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type, content, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}
