package vn.edu.iuh.fit.client.controller;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.stage.Stage;
import vn.edu.iuh.fit.client.service.SessionManager;
import vn.edu.iuh.fit.client.service.SocketRequestService;
import vn.edu.iuh.fit.common.command.ActionType;
import vn.edu.iuh.fit.common.constant.StatusSchedule;
import vn.edu.iuh.fit.common.dto.ScheduleDTO;
import vn.edu.iuh.fit.common.dto.ScheduleDetailDTO;
import vn.edu.iuh.fit.common.dto.ScheduleDetailPriceUpdateDTO;
import vn.edu.iuh.fit.common.request.Request;
import vn.edu.iuh.fit.common.response.Response;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ScheduleDetailPriceDialogController {

    private static final DecimalFormat MONEY_FORMAT = new DecimalFormat("#,##0");

    @FXML private Label titleLabel;
    @FXML private Label scheduleInfoLabel;
    @FXML private TextField bulkPriceField;
    @FXML private Button btnApplyBulk;
    @FXML private Button btnSave;
    @FXML private Button btnRefresh;
    @FXML private TableView<ScheduleDetailDTO> detailTable;
    @FXML private TableColumn<ScheduleDetailDTO, String> segmentColumn;
    @FXML private TableColumn<ScheduleDetailDTO, String> carriageColumn;
    @FXML private TableColumn<ScheduleDetailDTO, String> seatColumn;
    @FXML private TableColumn<ScheduleDetailDTO, String> typeColumn;
    @FXML private TableColumn<ScheduleDetailDTO, String> priceColumn;
    @FXML private TableColumn<ScheduleDetailDTO, String> statusColumn;

    private ScheduleDTO schedule;

    @FXML
    public void initialize() {
        setupTable();
    }

    public void init(ScheduleDTO schedule) {
        this.schedule = schedule;
        titleLabel.setText("Chi tiết giá ghế");
        scheduleInfoLabel.setText(schedule.getTrainName() + " - " + schedule.getRouteCode()
                + " - " + statusDisplayName(schedule.getStatus()));
        boolean editable = schedule.getStatus() == StatusSchedule.DRAFT;
        detailTable.setEditable(editable);
        bulkPriceField.setDisable(!editable);
        btnApplyBulk.setDisable(!editable);
        btnSave.setDisable(!editable);
        loadDetails();
    }

    @FXML
    public void handleApplyBulk() {
        BigDecimal price = parsePrice(bulkPriceField.getText());
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            showAlert(Alert.AlertType.WARNING, "Giá vé", "Giá vé phải lớn hơn 0.");
            return;
        }
        for (ScheduleDetailDTO detail : detailTable.getItems()) {
            if (!detail.isSold()) {
                detail.setSeatPrice(price.doubleValue());
            }
        }
        detailTable.refresh();
    }

    @FXML
    public void handleSave() {
        if (schedule == null || schedule.getStatus() != StatusSchedule.DRAFT) {
            showAlert(Alert.AlertType.WARNING, "Cập nhật giá vé",
                    "Chỉ được cập nhật giá vé khi lịch trình đang ở trạng thái Bản nháp.");
            return;
        }

        Map<String, BigDecimal> prices = new LinkedHashMap<>();
        for (ScheduleDetailDTO detail : detailTable.getItems()) {
            BigDecimal price = BigDecimal.valueOf(detail.getSeatPrice());
            if (price.compareTo(BigDecimal.ZERO) <= 0) {
                showAlert(Alert.AlertType.WARNING, "Cập nhật giá vé",
                        "Vui lòng nhập giá lớn hơn 0 cho tất cả các ghế.");
                return;
            }
            prices.put(detail.getId(), price);
        }

        String employeeId = SessionManager.getInstance().getEmployeeId();
        if (employeeId == null || employeeId.isBlank()) {
            showAlert(Alert.AlertType.ERROR, "Lỗi phiên làm việc",
                    "Không xác định được nhân viên. Vui lòng đăng nhập lại.");
            return;
        }

        ScheduleDetailPriceUpdateDTO dto = ScheduleDetailPriceUpdateDTO.builder()
                .requestEmployeeId(employeeId)
                .scheduleId(schedule.getId())
                .pricesByScheduleDetailId(prices)
                .build();
        setBusy(true);
        Task<Response> task = new Task<>() {
            @Override
            protected Response call() {
                return new SocketRequestService().send(new Request(ActionType.UPDATE_SCHEDULE_DETAIL_PRICES, dto));
            }
        };
        task.setOnSucceeded(e -> Platform.runLater(() -> {
            setBusy(false);
            Response res = task.getValue();
            if (res.isSuccess()) {
                showAlert(Alert.AlertType.INFORMATION, "Cập nhật giá vé", "Cập nhật giá vé thành công.");
                loadDetails();
            } else {
                showAlert(Alert.AlertType.ERROR, "Cập nhật giá vé", res.getMessage());
            }
        }));
        task.setOnFailed(e -> Platform.runLater(() -> {
            setBusy(false);
            showAlert(Alert.AlertType.ERROR, "Cập nhật giá vé",
                    "Không thể kết nối đến server: " + task.getException().getMessage());
        }));
        Thread thread = new Thread(task, "update-schedule-detail-prices-thread");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    public void handleRefresh() {
        loadDetails();
    }

    @FXML
    public void handleClose() {
        Stage stage = (Stage) detailTable.getScene().getWindow();
        stage.close();
    }

    private void setupTable() {
        segmentColumn.setCellValueFactory(data ->
                new ReadOnlyStringWrapper(formatSegment(data.getValue())));
        carriageColumn.setCellValueFactory(data ->
                new ReadOnlyStringWrapper(String.valueOf(data.getValue().getCarriageNumber())));
        seatColumn.setCellValueFactory(data ->
                new ReadOnlyStringWrapper(String.valueOf(data.getValue().getSeatNumber())));
        typeColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(
                data.getValue().getSeatType() != null ? data.getValue().getSeatType().getName() : ""));
        priceColumn.setCellValueFactory(data ->
                new ReadOnlyStringWrapper(MONEY_FORMAT.format(data.getValue().getSeatPrice())));
        priceColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        priceColumn.setOnEditCommit(event -> {
            ScheduleDetailDTO detail = event.getRowValue();
            BigDecimal price = parsePrice(event.getNewValue());
            if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
                showAlert(Alert.AlertType.WARNING, "Giá vé", "Giá vé phải lớn hơn 0.");
                detailTable.refresh();
                return;
            }
            detail.setSeatPrice(price.doubleValue());
            detailTable.refresh();
        });
        statusColumn.setCellValueFactory(data ->
                new ReadOnlyStringWrapper(data.getValue().isSold() ? "Đã bán" : "Trống"));
        statusColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                setText(item);
                getStyleClass().removeAll("status-danger-text", "status-success-text");
                getStyleClass().add("Đã bán".equals(item) ? "status-danger-text" : "status-success-text");
            }
        });
    }

    private void loadDetails() {
        if (schedule == null) {
            return;
        }
        String employeeId = SessionManager.getInstance().getEmployeeId();
        if (employeeId == null || employeeId.isBlank()) {
            showAlert(Alert.AlertType.ERROR, "Lỗi phiên làm việc",
                    "Không xác định được nhân viên. Vui lòng đăng nhập lại.");
            return;
        }

        ScheduleDetailPriceUpdateDTO dto = ScheduleDetailPriceUpdateDTO.builder()
                .requestEmployeeId(employeeId)
                .scheduleId(schedule.getId())
                .build();
        setBusy(true);
        Task<Response> task = new Task<>() {
            @Override
            protected Response call() {
                return new SocketRequestService().send(new Request(ActionType.FIND_SCHEDULE_DETAILS, dto));
            }
        };
        task.setOnSucceeded(e -> Platform.runLater(() -> {
            setBusy(false);
            Response res = task.getValue();
            if (res.isSuccess() && res.getData() instanceof List<?> raw) {
                List<ScheduleDetailDTO> details = raw.stream()
                        .filter(ScheduleDetailDTO.class::isInstance)
                        .map(ScheduleDetailDTO.class::cast)
                        .toList();
                detailTable.setItems(FXCollections.observableArrayList(details));
            } else if (res.isSuccess()) {
                detailTable.getItems().clear();
            } else {
                showAlert(Alert.AlertType.ERROR, "Chi tiết giá ghế", res.getMessage());
            }
        }));
        task.setOnFailed(e -> Platform.runLater(() -> {
            setBusy(false);
            showAlert(Alert.AlertType.ERROR, "Chi tiết giá ghế",
                    "Không thể kết nối đến server: " + task.getException().getMessage());
        }));
        Thread thread = new Thread(task, "load-schedule-details-thread");
        thread.setDaemon(true);
        thread.start();
    }

    private BigDecimal parsePrice(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(text.trim().replace(",", "").replace(".", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String formatSegment(ScheduleDetailDTO detail) {
        String departure = detail.getSegmentDepartureStationName();
        String destination = detail.getSegmentDestinationStationName();
        if (departure == null || departure.isBlank() || destination == null || destination.isBlank()) {
            return "Toàn tuyến";
        }
        return departure + " -> " + destination;
    }

    private void setBusy(boolean busy) {
        btnSave.setDisable(busy || schedule == null || schedule.getStatus() != StatusSchedule.DRAFT);
        btnRefresh.setDisable(busy);
        btnApplyBulk.setDisable(busy || schedule == null || schedule.getStatus() != StatusSchedule.DRAFT);
        bulkPriceField.setDisable(busy || schedule == null || schedule.getStatus() != StatusSchedule.DRAFT);
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

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type, content, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}
