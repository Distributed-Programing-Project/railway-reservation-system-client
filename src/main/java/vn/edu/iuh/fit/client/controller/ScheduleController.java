package vn.edu.iuh.fit.client.controller;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import vn.edu.iuh.fit.client.service.SessionManager;
import vn.edu.iuh.fit.client.service.SocketRequestService;
import vn.edu.iuh.fit.common.command.ActionType;
import vn.edu.iuh.fit.common.constant.StatusSchedule;
import vn.edu.iuh.fit.common.dto.ScheduleDTO;
import vn.edu.iuh.fit.common.dto.ScheduleFilterDTO;
import vn.edu.iuh.fit.common.dto.ScheduleLifecycleDTO;
import vn.edu.iuh.fit.common.dto.StationDTO;
import vn.edu.iuh.fit.common.dto.TrainDTO;
import vn.edu.iuh.fit.common.dto.TrainFilterDTO;
import vn.edu.iuh.fit.common.request.Request;
import vn.edu.iuh.fit.common.response.Response;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ScheduleController {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final int PAGE_SIZE = 20;

    // Filters
    @FXML private ComboBox<StationDTO> startStationCombo;
    @FXML private ComboBox<StationDTO> endStationCombo;
    @FXML private ComboBox<TrainDTO> trainCombo;
    @FXML private ComboBox<StatusSchedule> statusCombo;
    @FXML private DatePicker fromDatePicker;
    @FXML private DatePicker toDatePicker;
    @FXML private Button btnFilter;

    // Table
    @FXML private TableView<ScheduleDTO> scheduleTable;
    @FXML private TableColumn<ScheduleDTO, String> idColumn;
    @FXML private TableColumn<ScheduleDTO, String> trainColumn;
    @FXML private TableColumn<ScheduleDTO, String> routeColumn;
    @FXML private TableColumn<ScheduleDTO, String> departureStationColumn;
    @FXML private TableColumn<ScheduleDTO, String> destinationStationColumn;
    @FXML private TableColumn<ScheduleDTO, String> departureColumn;
    @FXML private TableColumn<ScheduleDTO, String> arrivalColumn;
    @FXML private TableColumn<ScheduleDTO, String> statusColumn;

    // Footer
    @FXML private Label lblResultCount;
    @FXML private Button prevButton;
    @FXML private Button nextButton;
    @FXML private Button btnEditSchedule;
    @FXML private Button btnPublishSchedule;
    @FXML private Button btnDisableSchedule;
    @FXML private Button btnViewSeatsDetail;
    @FXML private Label pageLabel;

    // State
    private int currentPage = 0;
    private boolean hasNextPage = false;

    @FXML
    public void initialize() {
        setupTable();
        setupStatusCombo();
        setupComboConverters();
        setupSelectionActions();
        loadStationsAsync();
        loadTrainsAsync();
        loadData();
    }

    // === Setup ===

    private void setupTable() {
        idColumn.setCellValueFactory(d ->
            new ReadOnlyStringWrapper(d.getValue().getId() != null ? d.getValue().getId() : ""));
        trainColumn.setCellValueFactory(d ->
            new ReadOnlyStringWrapper(d.getValue().getTrainName() != null ? d.getValue().getTrainName() : ""));
        routeColumn.setCellValueFactory(d ->
            new ReadOnlyStringWrapper(d.getValue().getRouteCode() != null ? d.getValue().getRouteCode() : ""));
        departureStationColumn.setCellValueFactory(d ->
            new ReadOnlyStringWrapper(
                d.getValue().getDepartureStationName() != null ? d.getValue().getDepartureStationName() : ""));
        destinationStationColumn.setCellValueFactory(d ->
            new ReadOnlyStringWrapper(
                d.getValue().getDestinationStationName() != null ? d.getValue().getDestinationStationName() : ""));
        departureColumn.setCellValueFactory(d ->
            new ReadOnlyStringWrapper(formatDateTime(d.getValue().getDepartureTime())));
        arrivalColumn.setCellValueFactory(d ->
            new ReadOnlyStringWrapper(formatDateTime(d.getValue().getArrivalTime())));

        statusColumn.setCellValueFactory(d ->
            new ReadOnlyStringWrapper(d.getValue().getStatus() != null ? d.getValue().getStatus().name() : ""));
        statusColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isBlank()) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                try {
                    StatusSchedule status = StatusSchedule.valueOf(item);
                    Label badge = new Label(statusDisplayName(status));
                    badge.getStyleClass().add(statusStyleClass(status));
                    badge.setStyle("-fx-padding: 2 8; -fx-background-radius: 4;");
                    setGraphic(badge);
                    setText(null);
                } catch (IllegalArgumentException e) {
                    setText(item);
                    setGraphic(null);
                }
            }
        });
    }

    private void setupSelectionActions() {
        scheduleTable.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, selected) ->
            updateActionButtons(selected));
        updateActionButtons(null);
    }

    private void setupStatusCombo() {
        List<StatusSchedule> options = new ArrayList<>();
        options.add(null);
        options.addAll(List.of(StatusSchedule.values()));
        statusCombo.setItems(FXCollections.observableArrayList(options));
        statusCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(StatusSchedule s) {
                return s == null ? "Tất cả trạng thái" : statusDisplayName(s);
            }
            @Override
            public StatusSchedule fromString(String s) { return null; }
        });
        statusCombo.getSelectionModel().selectFirst();
    }

    private void setupComboConverters() {
        StringConverter<StationDTO> stationConverter = new StringConverter<>() {
            @Override
            public String toString(StationDTO s) { return s == null ? "Tất cả ga" : s.getName(); }
            @Override
            public StationDTO fromString(String s) { return null; }
        };
        startStationCombo.setConverter(stationConverter);
        endStationCombo.setConverter(stationConverter);

        trainCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(TrainDTO t) { return t == null ? "Tất cả tàu" : t.getTrainCode(); }
            @Override
            public TrainDTO fromString(String s) { return null; }
        });
    }

    // === Async filter data loaders ===

    private void loadStationsAsync() {
        Task<Response> task = new Task<>() {
            @Override
            protected Response call() {
                return new SocketRequestService().send(new Request(ActionType.FIND_ALL_STATIONS, null));
            }
        };
        task.setOnSucceeded(e -> Platform.runLater(() -> {
            Response res = task.getValue();
            if (res.isSuccess() && res.getData() instanceof List<?> raw) {
                List<StationDTO> stations = raw.stream()
                    .filter(StationDTO.class::isInstance)
                    .map(StationDTO.class::cast)
                    .toList();
                List<StationDTO> withAll = new ArrayList<>();
                withAll.add(null);
                withAll.addAll(stations);
                startStationCombo.setItems(FXCollections.observableArrayList(withAll));
                endStationCombo.setItems(FXCollections.observableArrayList(withAll));
                startStationCombo.getSelectionModel().selectFirst();
                endStationCombo.getSelectionModel().selectFirst();
            }
        }));
        task.setOnFailed(e ->
            System.err.println("ERROR: Failed to load stations: " + task.getException().getMessage()));
        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    private void loadTrainsAsync() {
        Task<Response> task = new Task<>() {
            @Override
            protected Response call() {
                return new SocketRequestService().send(
                    new Request(ActionType.FIND_ALL_TRAINS, new TrainFilterDTO()));
            }
        };
        task.setOnSucceeded(e -> Platform.runLater(() -> {
            Response res = task.getValue();
            if (res.isSuccess() && res.getData() instanceof List<?> raw) {
                List<TrainDTO> trains = raw.stream()
                    .filter(TrainDTO.class::isInstance)
                    .map(TrainDTO.class::cast)
                    .toList();
                List<TrainDTO> withAll = new ArrayList<>();
                withAll.add(null);
                withAll.addAll(trains);
                trainCombo.setItems(FXCollections.observableArrayList(withAll));
                trainCombo.getSelectionModel().selectFirst();
            }
        }));
        task.setOnFailed(e ->
            System.err.println("ERROR: Failed to load trains: " + task.getException().getMessage()));
        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    // === Data loading ===

    private void loadData() {
        String employeeId = getCurrentEmployeeId();
        if (employeeId == null) {
            showAlert(Alert.AlertType.ERROR, "Lỗi phiên làm việc",
                "Không xác định được nhân viên. Vui lòng đăng nhập lại.");
            return;
        }
        ScheduleFilterDTO filter = buildFilter();
        executeAsync(
            () -> new SocketRequestService().send(new Request(ActionType.FILTER_SCHEDULE, filter)),
            res -> {
                if (res.isSuccess() && res.getData() instanceof List<?> raw) {
                    List<ScheduleDTO> schedules = raw.stream()
                        .filter(ScheduleDTO.class::isInstance)
                        .map(ScheduleDTO.class::cast)
                        .toList();
                    scheduleTable.getItems().setAll(schedules);
                    hasNextPage = schedules.size() >= PAGE_SIZE;
                    lblResultCount.setText(schedules.isEmpty()
                        ? "Không tìm thấy lịch trình phù hợp"
                        : "Tìm thấy " + schedules.size() + " lịch trình"
                            + (hasNextPage ? " (trang " + (currentPage + 1) + ")" : ""));
                } else if (res.isSuccess()) {
                    scheduleTable.getItems().clear();
                    hasNextPage = false;
                    lblResultCount.setText("Không tìm thấy lịch trình phù hợp");
                } else {
                    showAlert(Alert.AlertType.ERROR, "Lỗi", res.getMessage());
                }
                updatePaginationControls();
            }
        );
    }

    private ScheduleFilterDTO buildFilter() {
        StationDTO dep = startStationCombo.getValue();
        StationDTO dest = endStationCombo.getValue();
        TrainDTO train = trainCombo.getValue();
        return ScheduleFilterDTO.builder()
            .requestEmployeeId(getCurrentEmployeeId())
            .departureStationId(dep != null ? dep.getId() : null)
            .destinationStationId(dest != null ? dest.getId() : null)
            .trainId(train != null ? train.getId() : null)
            .status(statusCombo.getValue())
            .fromDate(fromDatePicker.getValue())
            .toDate(toDatePicker.getValue())
            .page(currentPage)
            .size(PAGE_SIZE)
            .build();
    }

    // === Filter handlers ===

    @FXML
    public void handleFilter() {
        LocalDate from = fromDatePicker.getValue();
        LocalDate to = toDatePicker.getValue();
        if (from != null && to != null && from.isAfter(to)) {
            showAlert(Alert.AlertType.WARNING, "Khoảng thời gian không hợp lệ",
                "\"Từ ngày\" phải trước hoặc bằng \"Đến ngày\".");
            return;
        }
        currentPage = 0;
        loadData();
    }

    @FXML
    public void handleRefresh() {
        startStationCombo.getSelectionModel().selectFirst();
        endStationCombo.getSelectionModel().selectFirst();
        trainCombo.getSelectionModel().selectFirst();
        statusCombo.getSelectionModel().selectFirst();
        fromDatePicker.setValue(null);
        toDatePicker.setValue(null);
        currentPage = 0;
        loadData();
    }

    // === Pagination ===

    private void updatePaginationControls() {
        prevButton.setDisable(currentPage == 0);
        nextButton.setDisable(!hasNextPage);
        pageLabel.setText("Trang " + (currentPage + 1));
    }

    @FXML
    public void handlePrevPage() {
        if (currentPage > 0) {
            currentPage--;
            loadData();
        }
    }

    @FXML
    public void handleNextPage() {
        if (hasNextPage) {
            currentPage++;
            loadData();
        }
    }

    // === Stub action handlers (implement ở các UC khác) ===

    @FXML
    public void handleAddSchedule() {
        openDialog("/client/ui/views/add-schedule-dialog.fxml", "Thêm lịch trình");
    }

    @FXML
    public void handleEditSchedule() {
        ScheduleDTO selected = scheduleTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "Sửa lịch trình", "Vui lòng chọn một lịch trình để sửa.");
            return;
        }
        if (selected.getStatus() != StatusSchedule.DRAFT) {
            showAlert(Alert.AlertType.WARNING, "Không thể sửa",
                "Chỉ được phép sửa lịch trình ở trạng thái Bản nháp (DRAFT).");
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/ui/views/add-schedule-dialog.fxml"));
            Parent root = loader.load();
            AddScheduleDialogController ctrl = loader.getController();
            ctrl.initForEdit(selected);
            Stage stage = new Stage();
            stage.setTitle("Sửa lịch trình");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.showAndWait();
            loadData();
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Không thể mở giao diện",
                "Lỗi khi load dialog: " + e.getMessage());
        }
    }

    @FXML
    public void handlePublishSchedule() {
        ScheduleDTO selected = scheduleTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "Phát triển lịch trình", "Vui lòng chọn một lịch trình.");
            return;
        }
        if (selected.getStatus() != StatusSchedule.DRAFT) {
            showAlert(Alert.AlertType.WARNING, "Không thể phát triển",
                "Chỉ được phép phát triển lịch trình ở trạng thái Bản nháp (DRAFT).");
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "Bạn có chắc muốn phát triển lịch trình [" + selected.getId() + "]?\n"
                + "Trạng thái sẽ chuyển sang Chưa khởi hành.",
            ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Xác nhận phát triển");
        confirm.setHeaderText(null);
        confirm.showAndWait();
        if (confirm.getResult() != ButtonType.YES) return;

        String employeeId = SessionManager.getInstance().getEmployeeId();
        if (employeeId == null || employeeId.isBlank()) {
            showAlert(Alert.AlertType.ERROR, "Lỗi phiên làm việc",
                "Không xác định được nhân viên. Vui lòng đăng nhập lại.");
            return;
        }

        ScheduleLifecycleDTO dto = ScheduleLifecycleDTO.builder()
            .requestEmployeeId(employeeId)
            .scheduleId(selected.getId())
            .action("PUBLISH")
            .build();

        executeAsync(
            () -> new SocketRequestService().send(
                new Request(ActionType.PUBLISH_OR_DISABLE_SCHEDULE, dto)),
            res -> {
                if (res.isSuccess()) {
                    showAlert(Alert.AlertType.INFORMATION, "Thành công",
                        "Phát triển lịch trình thành công.");
                    loadData();
                } else {
                    showAlert(Alert.AlertType.ERROR, "Lỗi", res.getMessage());
                }
            }
        );
    }

    @FXML
    public void handleDisableSchedule() {
        ScheduleDTO selected = scheduleTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "Vô hiệu hóa lịch trình", "Vui lòng chọn một lịch trình.");
            return;
        }
        if (selected.getStatus() != StatusSchedule.DRAFT
                && selected.getStatus() != StatusSchedule.NOT_STARTED) {
            showAlert(Alert.AlertType.WARNING, "Không thể vô hiệu hóa",
                "Chỉ được phép vô hiệu hóa lịch trình ở trạng thái Bản nháp hoặc Chưa khởi hành.");
            return;
        }

        String confirmMsg = selected.getStatus() == StatusSchedule.DRAFT
            ? "Lịch trình đang ở trạng thái Bản nháp.\nVô hiệu hóa sẽ XÓA VĨNH VIỄN lịch trình này. Bạn có chắc không?"
            : "Lịch trình đang ở trạng thái Chưa khởi hành.\nVô hiệu hóa sẽ chuyển sang Tạm dừng (nếu chưa có vé bán). Bạn có chắc không?";

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, confirmMsg, ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Xác nhận vô hiệu hóa");
        confirm.setHeaderText(null);
        confirm.showAndWait();
        if (confirm.getResult() != ButtonType.YES) return;

        String employeeId = SessionManager.getInstance().getEmployeeId();
        if (employeeId == null || employeeId.isBlank()) {
            showAlert(Alert.AlertType.ERROR, "Lỗi phiên làm việc",
                "Không xác định được nhân viên. Vui lòng đăng nhập lại.");
            return;
        }

        ScheduleLifecycleDTO dto = ScheduleLifecycleDTO.builder()
            .requestEmployeeId(employeeId)
            .scheduleId(selected.getId())
            .action("DISABLE")
            .build();

        executeAsync(
            () -> new SocketRequestService().send(
                new Request(ActionType.PUBLISH_OR_DISABLE_SCHEDULE, dto)),
            res -> {
                if (res.isSuccess()) {
                    showAlert(Alert.AlertType.INFORMATION, "Thành công",
                        "Vô hiệu hóa lịch trình thành công.");
                    loadData();
                } else {
                    showAlert(Alert.AlertType.ERROR, "Lỗi", res.getMessage());
                }
            }
        );
    }

    @FXML
    public void handleGenerateSchedules() {
        openDialog("/client/ui/views/generate-schedules-dialog.fxml", "Tạo lịch trình tự động");
    }

    @FXML
    public void handleViewSeatsDetail() {
        ScheduleDTO selected = scheduleTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "Chi tiết lịch trình", "Vui lòng chọn một lịch trình.");
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/ui/views/schedule-detail-price-dialog.fxml"));
            Parent root = loader.load();
            ScheduleDetailPriceDialogController ctrl = loader.getController();
            ctrl.init(selected);
            Stage stage = new Stage();
            stage.setTitle("Chi tiết giá ghế");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.showAndWait();
            loadData();
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Không thể mở giao diện",
                "Lỗi khi load dialog chi tiết: " + e.getMessage());
        }
    }

    // === Async execution ===

    private void executeAsync(Supplier<Response> action, Consumer<Response> onDone) {
        Task<Response> task = new Task<>() {
            @Override
            protected Response call() {
                return action.get();
            }
        };
        task.setOnSucceeded(e -> Platform.runLater(() -> {
            onDone.accept(task.getValue());
        }));
        task.setOnFailed(e -> Platform.runLater(() -> {
            showAlert(Alert.AlertType.ERROR, "Lỗi kết nối",
                "Không thể kết nối đến server: " + task.getException().getMessage());
        }));
        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    // === Helpers ===

    private String getCurrentEmployeeId() {
        String employeeId = SessionManager.getInstance().getEmployeeId();
        return employeeId == null || employeeId.isBlank() ? null : employeeId;
    }

    private void updateActionButtons(ScheduleDTO selected) {
        boolean none = selected == null;
        boolean draft = selected != null && selected.getStatus() == StatusSchedule.DRAFT;
        boolean notStarted = selected != null && selected.getStatus() == StatusSchedule.NOT_STARTED;

        if (btnViewSeatsDetail != null) btnViewSeatsDetail.setDisable(none);
        if (btnEditSchedule != null) btnEditSchedule.setDisable(!draft);
        if (btnPublishSchedule != null) btnPublishSchedule.setDisable(!draft);
        if (btnDisableSchedule != null) btnDisableSchedule.setDisable(!(draft || notStarted));
    }

    private String formatDateTime(LocalDateTime dt) {
        return dt == null ? "" : dt.format(DATE_TIME_FORMATTER);
    }

    private String statusDisplayName(StatusSchedule status) {
        return switch (status) {
            case DRAFT -> "Bản nháp";
            case NOT_STARTED -> "Chưa khởi hành";
            case IN_PROGRESS -> "Đang chạy";
            case PAUSED -> "Tạm dừng";
            case READY -> "Sẵn sàng";
            case COMPLETED -> "Hoàn thành";
            case CANCELLED -> "Đã hủy";
        };
    }

    private String statusStyleClass(StatusSchedule status) {
        return switch (status) {
            case DRAFT, COMPLETED -> "status-muted";
            case NOT_STARTED -> "status-info";
            case IN_PROGRESS -> "status-success";
            case PAUSED -> "status-warning";
            case READY -> "status-ready";
            case CANCELLED -> "status-danger";
        };
    }

    private void openDialog(String fxmlPath, String title) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent root = loader.load();
            Stage stage = new Stage();
            stage.setTitle(title);
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.showAndWait();
            loadData();
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Không thể mở giao diện",
                "Lỗi khi load: " + fxmlPath + "\n" + e.getMessage());
        }
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type, content, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}
