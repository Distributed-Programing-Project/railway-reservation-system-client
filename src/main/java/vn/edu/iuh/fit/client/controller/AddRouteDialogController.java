package vn.edu.iuh.fit.client.controller;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import vn.edu.iuh.fit.client.service.SessionManager;
import vn.edu.iuh.fit.client.service.SocketRequestService;
import vn.edu.iuh.fit.common.command.ActionType;
import vn.edu.iuh.fit.common.dto.RouteDTO;
import vn.edu.iuh.fit.common.dto.RouteStopDTO;
import vn.edu.iuh.fit.common.dto.StationDTO;
import vn.edu.iuh.fit.common.request.Request;
import vn.edu.iuh.fit.common.response.Response;

import java.util.ArrayList;
import java.util.List;

public class AddRouteDialogController {

    @FXML private Label titleLabel;
    @FXML private Label errorLabel;
    @FXML private Label pathPreviewLabel;
    @FXML private TextField routeCodeField;
    @FXML private TextField priceBasicField;
    @FXML private ComboBox<StationDTO> departureStationCombo;
    @FXML private ComboBox<StationDTO> destinationStationCombo;
    @FXML private ComboBox<StationDTO> stopStationCombo;
    @FXML private Button btnAddStop;
    @FXML private Button btnRemoveStop;
    @FXML private Button btnMoveStopUp;
    @FXML private Button btnMoveStopDown;
    @FXML private Button btnSave;
    @FXML private Button btnCancel;
    @FXML private TableView<StationDTO> stopsTable;
    @FXML private TableColumn<StationDTO, String> stopOrderColumn;
    @FXML private TableColumn<StationDTO, String> stopStationColumn;

    private final ObservableList<StationDTO> stopStations = FXCollections.observableArrayList();
    private final List<StationDTO> stations = new ArrayList<>();
    private RouteDTO editingRoute;
    private boolean saved;

    @FXML
    public void initialize() {
        setupStationConverters();
        setupStopsTable();
        stopsTable.setItems(stopStations);
        departureStationCombo.valueProperty().addListener((obs, oldValue, newValue) -> updatePathPreview());
        destinationStationCombo.valueProperty().addListener((obs, oldValue, newValue) -> updatePathPreview());
        stopStations.addListener((javafx.collections.ListChangeListener<StationDTO>) change -> updatePathPreview());
    }

    public void initForCreate(List<StationDTO> stations) {
        this.stations.clear();
        this.stations.addAll(stations);
        titleLabel.setText("Thêm tuyến đường");
        applyStationItems();
        updatePathPreview();
    }

    public void initForEdit(List<StationDTO> stations, RouteDTO route) {
        this.stations.clear();
        this.stations.addAll(stations);
        this.editingRoute = route;
        titleLabel.setText("Sửa tuyến đường");
        applyStationItems();
        routeCodeField.setText(route.getRouteCode());
        priceBasicField.setText(route.getPriceBasic() != null ? String.valueOf(route.getPriceBasic()) : "0");
        departureStationCombo.setValue(findStation(route.getDepartureStationId()));
        destinationStationCombo.setValue(findStation(route.getDestinationStationId()));
        stopStations.setAll(route.getRouteStops() == null
                ? List.of()
                : route.getRouteStops().stream()
                        .map(stop -> findStation(stop.getStationStopId()))
                        .filter(station -> station != null)
                        .toList());
        updatePathPreview();
    }

    public boolean isSaved() {
        return saved;
    }

    @FXML
    public void handleAddStop() {
        StationDTO station = stopStationCombo.getValue();
        if (station == null) {
            showError("Vui lòng chọn ga dừng.");
            return;
        }
        if (isEndpoint(station) || stopStations.stream().anyMatch(existing -> existing.getId().equals(station.getId()))) {
            showError("Ga dừng không được trùng ga đi, ga đến hoặc ga dừng khác.");
            return;
        }
        stopStations.add(station);
        stopStationCombo.setValue(null);
        errorLabel.setText("");
    }

    @FXML
    public void handleRemoveStop() {
        StationDTO selected = stopsTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            stopStations.remove(selected);
        }
    }

    @FXML
    public void handleMoveStopUp() {
        int index = stopsTable.getSelectionModel().getSelectedIndex();
        if (index > 0) {
            StationDTO item = stopStations.remove(index);
            stopStations.add(index - 1, item);
            stopsTable.getSelectionModel().select(index - 1);
        }
    }

    @FXML
    public void handleMoveStopDown() {
        int index = stopsTable.getSelectionModel().getSelectedIndex();
        if (index >= 0 && index < stopStations.size() - 1) {
            StationDTO item = stopStations.remove(index);
            stopStations.add(index + 1, item);
            stopsTable.getSelectionModel().select(index + 1);
        }
    }

    @FXML
    public void handleSave() {
        RouteDTO dto = buildRouteDto();
        if (dto == null) return;
        setBusy(true);
        ActionType action = editingRoute == null ? ActionType.CREATE_ROUTE : ActionType.UPDATE_ROUTE;
        Task<Response> task = new Task<>() {
            @Override
            protected Response call() {
                return new SocketRequestService().send(new Request(action, dto));
            }
        };
        task.setOnSucceeded(event -> Platform.runLater(() -> {
            setBusy(false);
            Response response = task.getValue();
            if (response.isSuccess()) {
                saved = true;
                close();
            } else {
                showError(response.getMessage());
            }
        }));
        task.setOnFailed(event -> Platform.runLater(() -> {
            setBusy(false);
            showError("Không thể kết nối server: " + task.getException().getMessage());
        }));
        Thread thread = new Thread(task, "save-route-thread");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    public void handleCancel() {
        close();
    }

    private void setupStationConverters() {
        StringConverter<StationDTO> converter = new StringConverter<>() {
            @Override
            public String toString(StationDTO station) {
                return station == null ? "" : station.getName();
            }

            @Override
            public StationDTO fromString(String string) {
                return null;
            }
        };
        departureStationCombo.setConverter(converter);
        destinationStationCombo.setConverter(converter);
        stopStationCombo.setConverter(converter);
    }

    private void setupStopsTable() {
        stopOrderColumn.setCellValueFactory(data ->
                new ReadOnlyStringWrapper(String.valueOf(stopStations.indexOf(data.getValue()) + 1)));
        stopStationColumn.setCellValueFactory(data ->
                new ReadOnlyStringWrapper(data.getValue().getName()));
    }

    private void applyStationItems() {
        departureStationCombo.setItems(FXCollections.observableArrayList(stations));
        destinationStationCombo.setItems(FXCollections.observableArrayList(stations));
        stopStationCombo.setItems(FXCollections.observableArrayList(stations));
    }

    private RouteDTO buildRouteDto() {
        String employeeId = SessionManager.getInstance().getEmployeeId();
        if (employeeId == null || employeeId.isBlank()) {
            showError("Không xác định được nhân viên. Vui lòng đăng nhập lại.");
            return null;
        }
        String routeCode = routeCodeField.getText() != null ? routeCodeField.getText().trim() : "";
        StationDTO departure = departureStationCombo.getValue();
        StationDTO destination = destinationStationCombo.getValue();
        Double price = parsePrice(priceBasicField.getText());
        if (routeCode.isBlank()) {
            showError("Vui lòng nhập mã tuyến.");
            return null;
        }
        if (departure == null || destination == null) {
            showError("Vui lòng chọn ga đi và ga đến.");
            return null;
        }
        if (departure.getId().equals(destination.getId())) {
            showError("Ga đi và ga đến không được trùng nhau.");
            return null;
        }
        if (price == null || price < 0) {
            showError("Giá cơ bản không hợp lệ.");
            return null;
        }
        for (StationDTO stop : stopStations) {
            if (stop.getId().equals(departure.getId()) || stop.getId().equals(destination.getId())) {
                showError("Ga dừng không được trùng ga đi hoặc ga đến.");
                return null;
            }
        }

        List<RouteStopDTO> stopDtos = new ArrayList<>();
        for (int i = 0; i < stopStations.size(); i++) {
            stopDtos.add(RouteStopDTO.builder()
                    .orderStop(i + 1)
                    .stationStopId(stopStations.get(i).getId())
                    .build());
        }

        return RouteDTO.builder()
                .requestEmployeeId(employeeId)
                .id(editingRoute != null ? editingRoute.getId() : null)
                .routeCode(routeCode)
                .departureStationId(departure.getId())
                .destinationStationId(destination.getId())
                .priceBasic(price)
                .routeStops(stopDtos)
                .build();
    }

    private Double parsePrice(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(text.trim().replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean isEndpoint(StationDTO station) {
        StationDTO departure = departureStationCombo.getValue();
        StationDTO destination = destinationStationCombo.getValue();
        return (departure != null && departure.getId().equals(station.getId()))
                || (destination != null && destination.getId().equals(station.getId()));
    }

    private StationDTO findStation(String stationId) {
        if (stationId == null) return null;
        return stations.stream()
                .filter(station -> stationId.equals(station.getId()))
                .findFirst()
                .orElse(null);
    }

    private void updatePathPreview() {
        List<String> path = new ArrayList<>();
        StationDTO departure = departureStationCombo.getValue();
        StationDTO destination = destinationStationCombo.getValue();
        if (departure != null) path.add(departure.getName());
        stopStations.stream().map(StationDTO::getName).forEach(path::add);
        if (destination != null) path.add(destination.getName());
        pathPreviewLabel.setText(path.isEmpty() ? "" : String.join(" -> ", path));
        stopsTable.refresh();
    }

    private void setBusy(boolean busy) {
        btnSave.setDisable(busy);
        btnCancel.setDisable(busy);
        btnAddStop.setDisable(busy);
        btnRemoveStop.setDisable(busy);
        btnMoveStopUp.setDisable(busy);
        btnMoveStopDown.setDisable(busy);
    }

    private void showError(String message) {
        errorLabel.setText(message);
    }

    private void close() {
        Stage stage = (Stage) btnCancel.getScene().getWindow();
        stage.close();
    }
}
