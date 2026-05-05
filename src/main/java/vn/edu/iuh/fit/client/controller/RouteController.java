package vn.edu.iuh.fit.client.controller;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import vn.edu.iuh.fit.client.service.SessionManager;
import vn.edu.iuh.fit.client.service.SocketRequestService;
import vn.edu.iuh.fit.common.command.ActionType;
import vn.edu.iuh.fit.common.constant.RouteStatus;
import vn.edu.iuh.fit.common.dto.RouteActionDTO;
import vn.edu.iuh.fit.common.dto.RouteDTO;
import vn.edu.iuh.fit.common.dto.RouteFilterDTO;
import vn.edu.iuh.fit.common.dto.RouteStopDTO;
import vn.edu.iuh.fit.common.dto.StationDTO;
import vn.edu.iuh.fit.common.request.Request;
import vn.edu.iuh.fit.common.response.Response;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class RouteController {

    private static final DecimalFormat MONEY_FORMAT = new DecimalFormat("#,##0");

    @FXML private ComboBox<StationDTO> startStationCombo;
    @FXML private ComboBox<StationDTO> endStationCombo;
    @FXML private ComboBox<RouteStatus> statusCombo;
    @FXML private Button btnFilter;
    @FXML private Button btnAddRoute;
    @FXML private Button btnRefresh;
    @FXML private Button btnDevelopRoute;
    @FXML private Button btnDisableRoute;
    @FXML private Button btnEditRoute;
    @FXML private Button btnDeleteRoute;
    @FXML private Label lblResultCount;
    @FXML private TableView<RouteDTO> routeTable;
    @FXML private TableColumn<RouteDTO, String> codeColumn;
    @FXML private TableColumn<RouteDTO, String> startStationColumn;
    @FXML private TableColumn<RouteDTO, String> stopsColumn;
    @FXML private TableColumn<RouteDTO, String> endStationColumn;
    @FXML private TableColumn<RouteDTO, String> segmentsColumn;
    @FXML private TableColumn<RouteDTO, String> priceColumn;
    @FXML private TableColumn<RouteDTO, String> statusColumn;

    private final List<StationDTO> stations = new ArrayList<>();
    private boolean busy;

    @FXML
    public void initialize() {
        setupTable();
        setupCombos();
        setupSelection();
        loadStationsAsync();
        loadRoutes();
    }

    @FXML
    public void handleFilter() {
        loadRoutes();
    }

    @FXML
    public void handleAddRoute() {
        openRouteDialog(null);
    }

    @FXML
    public void handleEditRoute() {
        RouteDTO selected = routeTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "Tuyến đường", "Vui lòng chọn tuyến đường cần sửa.");
            return;
        }
        if (selected.getStatus() != RouteStatus.DRAFT) {
            showAlert(Alert.AlertType.WARNING, "Tuyến đường", "Chỉ sửa tuyến đường đang ở trạng thái Nháp.");
            return;
        }
        openRouteDialog(selected);
    }

    @FXML
    public void handleDeleteRoute() {
        RouteDTO selected = routeTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "Tuyến đường", "Vui lòng chọn tuyến đường cần xoá.");
            return;
        }
        if (selected.getStatus() != RouteStatus.DRAFT) {
            showAlert(Alert.AlertType.WARNING, "Tuyến đường", "Chỉ xoá tuyến đường đang ở trạng thái Nháp.");
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Bạn có chắc muốn xoá tuyến [" + selected.getRouteCode() + "]?",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Xác nhận xoá");
        confirm.setHeaderText(null);
        confirm.showAndWait();
        if (confirm.getResult() != ButtonType.YES) return;
        sendRouteAction(ActionType.DELETE_ROUTE, selected.getId(), "Xoá tuyến đường");
    }

    @FXML
    public void handleRefresh() {
        loadStationsAsync();
        loadRoutes();
    }

    @FXML
    public void handleDevelopRoute() {
        RouteDTO selected = routeTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "Tuyến đường", "Vui lòng chọn tuyến đường cần phát triển.");
            return;
        }
        if (selected.getStatus() != RouteStatus.DRAFT) {
            showAlert(Alert.AlertType.WARNING, "Tuyến đường", "Chỉ phát triển tuyến đường đang ở trạng thái Nháp.");
            return;
        }
        sendRouteAction(ActionType.PROMOTE_ROUTE, selected.getId(), "Phát triển tuyến đường");
    }

    @FXML
    public void handleDisableRoute() {
        RouteDTO selected = routeTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "Tuyến đường", "Vui lòng chọn tuyến đường cần tạm dừng.");
            return;
        }
        if (selected.getStatus() != RouteStatus.ACTIVE) {
            showAlert(Alert.AlertType.WARNING, "Tuyến đường", "Chỉ tạm dừng tuyến đường đang hoạt động.");
            return;
        }
        sendRouteAction(ActionType.DISABLE_ROUTE, selected.getId(), "Tạm dừng tuyến đường");
    }

    private void setupTable() {
        codeColumn.setCellValueFactory(data ->
                new ReadOnlyStringWrapper(valueOrEmpty(data.getValue().getRouteCode())));
        startStationColumn.setCellValueFactory(data ->
                new ReadOnlyStringWrapper(valueOrEmpty(data.getValue().getDepartureStationName())));
        stopsColumn.setCellValueFactory(data ->
                new ReadOnlyStringWrapper(formatStops(data.getValue())));
        endStationColumn.setCellValueFactory(data ->
                new ReadOnlyStringWrapper(valueOrEmpty(data.getValue().getDestinationStationName())));
        segmentsColumn.setCellValueFactory(data ->
                new ReadOnlyStringWrapper(formatSegments(data.getValue())));
        priceColumn.setCellValueFactory(data ->
                new ReadOnlyStringWrapper(data.getValue().getPriceBasic() != null
                        ? MONEY_FORMAT.format(data.getValue().getPriceBasic())
                        : "0"));
        statusColumn.setCellValueFactory(data ->
                new ReadOnlyStringWrapper(data.getValue().getStatus() != null
                        ? data.getValue().getStatus().name()
                        : ""));
        statusColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isBlank()) {
                    setText(null);
                    return;
                }
                try {
                    setText(RouteStatus.valueOf(item).getName());
                } catch (IllegalArgumentException e) {
                    setText(item);
                }
            }
        });
    }

    private void setupCombos() {
        StringConverter<StationDTO> stationConverter = new StringConverter<>() {
            @Override
            public String toString(StationDTO station) {
                return station == null ? "Tất cả ga" : station.getName();
            }

            @Override
            public StationDTO fromString(String string) {
                return null;
            }
        };
        startStationCombo.setConverter(stationConverter);
        endStationCombo.setConverter(stationConverter);

        List<RouteStatus> statuses = new ArrayList<>();
        statuses.add(null);
        statuses.addAll(List.of(RouteStatus.values()));
        statusCombo.setItems(FXCollections.observableArrayList(statuses));
        statusCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(RouteStatus status) {
                return status == null ? "Tất cả trạng thái" : status.getName();
            }

            @Override
            public RouteStatus fromString(String string) {
                return null;
            }
        });
        statusCombo.getSelectionModel().selectFirst();
    }

    private void setupSelection() {
        routeTable.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, selected) ->
                updateActionButtons(selected));
        updateActionButtons(null);
    }

    private void loadStationsAsync() {
        executeAsync(
                () -> new SocketRequestService().send(new Request(ActionType.FIND_ALL_STATIONS, null)),
                response -> {
                    if (response.isSuccess() && response.getData() instanceof List<?> raw) {
                        stations.clear();
                        stations.addAll(raw.stream()
                                .filter(StationDTO.class::isInstance)
                                .map(StationDTO.class::cast)
                                .toList());
                        List<StationDTO> withAll = new ArrayList<>();
                        withAll.add(null);
                        withAll.addAll(stations);
                        startStationCombo.setItems(FXCollections.observableArrayList(withAll));
                        endStationCombo.setItems(FXCollections.observableArrayList(withAll));
                        if (startStationCombo.getSelectionModel().getSelectedIndex() < 0) {
                            startStationCombo.getSelectionModel().selectFirst();
                        }
                        if (endStationCombo.getSelectionModel().getSelectedIndex() < 0) {
                            endStationCombo.getSelectionModel().selectFirst();
                        }
                    } else if (!response.isSuccess()) {
                        showAlert(Alert.AlertType.ERROR, "Tải danh sách ga", response.getMessage());
                    }
                });
    }

    private void loadRoutes() {
        String employeeId = currentEmployeeId();
        if (employeeId == null) return;
        StationDTO departure = startStationCombo.getValue();
        StationDTO destination = endStationCombo.getValue();
        RouteFilterDTO filter = RouteFilterDTO.builder()
                .requestEmployeeId(employeeId)
                .departureStationId(departure != null ? departure.getId() : null)
                .destinationStationId(destination != null ? destination.getId() : null)
                .status(statusCombo.getValue())
                .build();
        setBusy(true);
        executeAsync(
                () -> new SocketRequestService().send(new Request(ActionType.SEARCH_ROUTES, filter)),
                response -> {
                    setBusy(false);
                    if (response.isSuccess() && response.getData() instanceof List<?> raw) {
                        List<RouteDTO> routes = raw.stream()
                                .filter(RouteDTO.class::isInstance)
                                .map(RouteDTO.class::cast)
                                .toList();
                        routeTable.setItems(FXCollections.observableArrayList(routes));
                        lblResultCount.setText(routes.isEmpty()
                                ? "Không tìm thấy tuyến phù hợp"
                                : "Tìm thấy " + routes.size() + " tuyến đường");
                    } else if (response.isSuccess()) {
                        routeTable.getItems().clear();
                        lblResultCount.setText("Không tìm thấy tuyến phù hợp");
                    } else {
                        showAlert(Alert.AlertType.ERROR, "Tải tuyến đường", response.getMessage());
                    }
                },
                () -> setBusy(false));
    }

    private void openRouteDialog(RouteDTO route) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/ui/views/add-route-dialog.fxml"));
            Parent root = loader.load();
            AddRouteDialogController controller = loader.getController();
            if (route == null) {
                controller.initForCreate(stations);
            } else {
                controller.initForEdit(stations, route);
            }
            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle(route == null ? "Thêm tuyến đường" : "Sửa tuyến đường");
            stage.setScene(new Scene(root));
            stage.showAndWait();
            if (controller.isSaved()) {
                loadRoutes();
            }
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Tuyến đường", "Không thể mở form tuyến đường: " + e.getMessage());
        }
    }

    private void sendRouteAction(ActionType action, String routeId, String title) {
        String employeeId = currentEmployeeId();
        if (employeeId == null) return;
        RouteActionDTO dto = RouteActionDTO.builder()
                .requestEmployeeId(employeeId)
                .routeId(routeId)
                .build();
        setBusy(true);
        executeAsync(
                () -> new SocketRequestService().send(new Request(action, dto)),
                response -> {
                    setBusy(false);
                    if (response.isSuccess()) {
                        showAlert(Alert.AlertType.INFORMATION, title, response.getMessage());
                        loadRoutes();
                    } else {
                        showAlert(Alert.AlertType.ERROR, title, response.getMessage());
                    }
                },
                () -> setBusy(false));
    }

    private void executeAsync(Supplier<Response> supplier, Consumer<Response> onSuccess) {
        executeAsync(supplier, onSuccess, null);
    }

    private void executeAsync(Supplier<Response> supplier, Consumer<Response> onSuccess, Runnable onFailedFinally) {
        Task<Response> task = new Task<>() {
            @Override
            protected Response call() {
                return supplier.get();
            }
        };
        task.setOnSucceeded(event -> Platform.runLater(() -> onSuccess.accept(task.getValue())));
        task.setOnFailed(event -> Platform.runLater(() -> {
            if (onFailedFinally != null) onFailedFinally.run();
            showAlert(Alert.AlertType.ERROR, "Kết nối server",
                    "Không thể kết nối server: " + task.getException().getMessage());
        }));
        Thread thread = new Thread(task, "route-request-thread");
        thread.setDaemon(true);
        thread.start();
    }

    private void setBusy(boolean busy) {
        this.busy = busy;
        btnFilter.setDisable(busy);
        btnAddRoute.setDisable(busy);
        btnRefresh.setDisable(busy);
        updateActionButtons(routeTable.getSelectionModel().getSelectedItem());
    }

    private void updateActionButtons(RouteDTO selected) {
        boolean none = selected == null;
        boolean draft = selected != null && selected.getStatus() == RouteStatus.DRAFT;
        boolean active = selected != null && selected.getStatus() == RouteStatus.ACTIVE;
        btnEditRoute.setDisable(busy || none || !draft);
        btnDeleteRoute.setDisable(busy || none || !draft);
        btnDevelopRoute.setDisable(busy || none || !draft);
        btnDisableRoute.setDisable(busy || none || !active);
    }

    private String currentEmployeeId() {
        String employeeId = SessionManager.getInstance().getEmployeeId();
        if (employeeId == null || employeeId.isBlank()) {
            showAlert(Alert.AlertType.ERROR, "Lỗi phiên làm việc",
                    "Không xác định được nhân viên. Vui lòng đăng nhập lại.");
            return null;
        }
        return employeeId;
    }

    private String formatStops(RouteDTO route) {
        if (route.getRouteStops() == null || route.getRouteStops().isEmpty()) {
            return "";
        }
        return route.getRouteStops().stream()
                .map(RouteStopDTO::getStationStopName)
                .filter(name -> name != null && !name.isBlank())
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
    }

    private String formatSegments(RouteDTO route) {
        List<String> path = routePathNames(route);
        List<String> segments = new ArrayList<>();
        for (int i = 0; i < path.size() - 1; i++) {
            for (int j = i + 1; j < path.size(); j++) {
                segments.add(path.get(i) + "-" + path.get(j));
            }
        }
        return String.join("; ", segments);
    }

    private List<String> routePathNames(RouteDTO route) {
        List<String> path = new ArrayList<>();
        path.add(valueOrEmpty(route.getDepartureStationName()));
        if (route.getRouteStops() != null) {
            route.getRouteStops().stream()
                    .map(RouteStopDTO::getStationStopName)
                    .filter(name -> name != null && !name.isBlank())
                    .forEach(path::add);
        }
        path.add(valueOrEmpty(route.getDestinationStationName()));
        return path;
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type, content, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}
