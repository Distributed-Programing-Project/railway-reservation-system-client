package vn.edu.iuh.fit.client.controller;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import vn.edu.iuh.fit.client.service.SessionManager;
import vn.edu.iuh.fit.client.service.SocketRequestService;
import vn.edu.iuh.fit.common.command.ActionType;
import vn.edu.iuh.fit.common.constant.TrainStatus;
import vn.edu.iuh.fit.common.dto.TrainDTO;
import vn.edu.iuh.fit.common.dto.TrainFilterDTO;
import vn.edu.iuh.fit.common.request.Request;
import vn.edu.iuh.fit.common.response.Response;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class TrainManagementController {

    @FXML private TableView<TrainDTO> tableView;
    @FXML private TableColumn<TrainDTO, String> colTrainCode;
    @FXML private TableColumn<TrainDTO, String> colStatus;
    @FXML private TableColumn<TrainDTO, String> colTotalCarriages;
    @FXML private TableColumn<TrainDTO, String> colTotalSeats;
    @FXML private TableColumn<TrainDTO, Void> colActions;

    @FXML private TextField txtSearch;
    @FXML private ComboBox<TrainStatus> statusFilter;
    @FXML private Button btnCreateTrain;
    @FXML private Button btnRegisterCarriage;
    @FXML private StackPane loadingOverlay;

    @FXML
    private void initialize() {
        setupStatusFilter();
        setupTable();
        applyRoleAccess();
        loadData();
    }

    private void applyRoleAccess() {
        boolean isManager = SessionManager.getInstance().isManager();
        btnCreateTrain.setVisible(isManager);
        btnCreateTrain.setManaged(isManager);
        btnRegisterCarriage.setVisible(isManager);
        btnRegisterCarriage.setManaged(isManager);
        colActions.setVisible(isManager);
    }

    private void setupStatusFilter() {
        statusFilter.getItems().add(null);
        statusFilter.getItems().addAll(TrainStatus.values());
        statusFilter.setConverter(new StringConverter<>() {
            @Override public String toString(TrainStatus s) { return s == null ? "Tất cả" : s.getName(); }
            @Override public TrainStatus fromString(String s) { return null; }
        });
        statusFilter.getSelectionModel().selectFirst();
        statusFilter.getSelectionModel().selectedItemProperty().addListener((obs, old, val) -> loadData());
    }

    private void setupTable() {
        colTrainCode.setCellValueFactory(c ->
            new ReadOnlyStringWrapper(c.getValue().getTrainCode() != null ? c.getValue().getTrainCode() : ""));

        colStatus.setCellValueFactory(c -> {
            TrainStatus status = c.getValue().getStatus();
            return new ReadOnlyStringWrapper(status != null ? status.getName() : "");
        });
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                if (empty || item == null || item.isBlank()) { setGraphic(null); return; }
                Label badge = new Label(item);
                TrainDTO train = getTableView().getItems().get(getIndex());
                badge.getStyleClass().add(statusBadgeClass(train.getStatus()));
                setGraphic(badge);
            }
        });

        colTotalCarriages.setCellValueFactory(c ->
            new ReadOnlyStringWrapper(String.valueOf(c.getValue().getTotalCarriages())));
        colTotalSeats.setCellValueFactory(c ->
            new ReadOnlyStringWrapper(String.valueOf(c.getValue().getTotalSeats())));

        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button btnCfg = new Button("Cấu hình");
            private final Button btnSts = new Button("Đổi thông tin");
            {
                btnCfg.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-size: 11px; -fx-padding: 4 8; -fx-background-radius: 5; -fx-cursor: hand;");
                btnSts.setStyle("-fx-background-color: #8e44ad; -fx-text-fill: white; -fx-font-size: 11px; -fx-padding: 4 8; -fx-background-radius: 5; -fx-cursor: hand;");
                btnCfg.setOnAction(e -> handleConfigure(getTableView().getItems().get(getIndex())));
                btnSts.setOnAction(e -> handleUpdateStatus(getTableView().getItems().get(getIndex())));
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : new HBox(6, btnCfg, btnSts));
            }
        });
    }

    private String statusBadgeClass(TrainStatus status) {
        if (status == null) return "status-muted";
        return switch (status) {
            case ACTIVE -> "status-success";
            case MAINTENANCE -> "status-warning";
            case INACTIVE -> "status-muted";
        };
    }

    // === Data loading ===

    private void loadData() {
        String keyword = txtSearch.getText();
        if (keyword != null && !keyword.isBlank()) {
            searchByCode(keyword);
        } else {
            loadAllTrains();
        }
    }

    private void loadAllTrains() {
        executeAsync(
            () -> {
                TrainFilterDTO filter = TrainFilterDTO.builder()
                    .statusFilter(statusFilter.getValue())
                    .build();
                return new SocketRequestService().send(new Request(ActionType.FIND_ALL_TRAINS, filter));
            },
            res -> updateTable(res)
        );
    }

    private void searchByCode(String keyword) {
        executeAsync(
            () -> new SocketRequestService().send(new Request(ActionType.FIND_TRAIN_BY_CODE, keyword)),
            res -> updateTable(res)
        );
    }

    @SuppressWarnings("unchecked")
    private void updateTable(Response res) {
        if (res.isSuccess() && res.getData() != null) {
            tableView.getItems().setAll((List<TrainDTO>) res.getData());
        } else {
            tableView.getItems().clear();
            if (!res.isSuccess()) showAlert(Alert.AlertType.ERROR, "Lỗi", res.getMessage());
        }
    }

    // === Event handlers ===

    @FXML private void handleSearch() { loadData(); }

    @FXML
    private void handleShowAll() {
        txtSearch.clear();
        statusFilter.getSelectionModel().selectFirst();
        loadAllTrains();
    }

    @FXML
    private void handleCreateTrain() {
        openSimpleDialog("/client/ui/views/create-train-dialog.fxml", "Lập tàu mới");
    }

    @FXML
    private void handleRegisterCarriage() {
        openSimpleDialog("/client/ui/views/register-carriage-dialog.fxml", "Đăng ký toa mới");
    }

    private void handleConfigure(TrainDTO train) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/ui/views/configure-train-dialog.fxml"));
            Parent root = loader.load();
            ConfigureTrainDialogController ctrl = loader.getController();
            ctrl.initForTrain(train);
            showDialog(root, "Cấu hình tàu — " + train.getTrainCode());
            loadData();
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Lỗi", "Không thể mở dialog cấu hình: " + e.getMessage());
        }
    }

    private void handleUpdateStatus(TrainDTO train) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/ui/views/update-train-status-dialog.fxml"));
            Parent root = loader.load();
            UpdateTrainStatusDialogController ctrl = loader.getController();
            ctrl.initForTrain(train);
            showDialog(root, "Đổi trạng thái tàu — " + train.getTrainCode());
            loadData();
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Lỗi", "Không thể mở dialog đổi trạng thái: " + e.getMessage());
        }
    }

    private void openSimpleDialog(String fxmlPath, String title) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource(fxmlPath));
            showDialog(root, title);
            loadData();
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Lỗi", "Không thể mở dialog: " + e.getMessage());
        }
    }

    private void showDialog(Parent root, String title) {
        Stage stage = new Stage();
        stage.setTitle(title);
        stage.setScene(new Scene(root));
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initOwner(btnCreateTrain.getScene().getWindow());
        stage.showAndWait();
    }

    // === Async ===

    private void executeAsync(Supplier<Response> action, Consumer<Response> onDone) {
        setLoading(true);
        Task<Response> task = new Task<>() {
            @Override protected Response call() { return action.get(); }
        };
        task.setOnSucceeded(e -> Platform.runLater(() -> { setLoading(false); onDone.accept(task.getValue()); }));
        task.setOnFailed(e -> Platform.runLater(() -> {
            setLoading(false);
            showAlert(Alert.AlertType.ERROR, "Lỗi kết nối", "Kết nối thất bại: " + task.getException().getMessage());
        }));
        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    private void setLoading(boolean loading) {
        loadingOverlay.setVisible(loading);
        if (loading) loadingOverlay.toFront();
    }

    private void showAlert(Alert.AlertType type, String title, String msg) {
        Alert alert = new Alert(type, msg, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}