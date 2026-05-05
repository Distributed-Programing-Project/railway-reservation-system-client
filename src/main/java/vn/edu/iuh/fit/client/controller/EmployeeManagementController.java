package vn.edu.iuh.fit.client.controller;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import vn.edu.iuh.fit.client.service.SessionManager;
import vn.edu.iuh.fit.client.service.SocketRequestService;
import vn.edu.iuh.fit.common.command.ActionType;
import vn.edu.iuh.fit.common.constant.EmployeeStatus;
import vn.edu.iuh.fit.common.dto.AccountCreatedDTO;
import vn.edu.iuh.fit.common.dto.EmployeeDTO;
import vn.edu.iuh.fit.common.dto.EmployeeFilterDTO;
import vn.edu.iuh.fit.common.dto.EmployeePageDTO;
import vn.edu.iuh.fit.common.request.Request;
import vn.edu.iuh.fit.common.response.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class EmployeeManagementController {

    @FXML private TableView<EmployeeDTO> tableView;
    @FXML private TableColumn<EmployeeDTO, String> colCode;
    @FXML private TableColumn<EmployeeDTO, String> colName;
    @FXML private TableColumn<EmployeeDTO, String> colNationalId;
    @FXML private TableColumn<EmployeeDTO, String> colPhone;
    @FXML private TableColumn<EmployeeDTO, String> colEmail;
    @FXML private TableColumn<EmployeeDTO, String> colType;
    @FXML private TableColumn<EmployeeDTO, String> colStatus;
    @FXML private TableColumn<EmployeeDTO, Void> colActions;

    @FXML private ComboBox<EmployeeStatus> statusFilter;
    @FXML private ComboBox<String> accountFilter;
    @FXML private TextField txtSearch;
    @FXML private Button btnAdd;
    @FXML private Button btnPrev;
    @FXML private Button btnNext;
    @FXML private Label pageLabel;
    @FXML private StackPane loadingOverlay;

    private int clientPage = 0;
    private int clientTotalPages = 1;
    private static final int PAGE_SIZE = 20;
    private List<EmployeeDTO> allLoadedEmployees = new ArrayList<>();

    @FXML
    private void initialize() {
        setupStatusFilter();
        setupAccountFilter();
        setupSearchField();
        setupTable();
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        applyRoleAccess();
        loadData();
    }

    private void applyRoleAccess() {
        boolean isManager = SessionManager.getInstance().isManager();
        btnAdd.setVisible(isManager);
        btnAdd.setManaged(isManager);
        colActions.setVisible(isManager);
    }

    private void setupStatusFilter() {
        statusFilter.setConverter(new StringConverter<>() {
            @Override public String toString(EmployeeStatus s) {
                if (s == null) return "Tất cả";
                return switch (s) {
                    case ACTIVE -> "Đang làm";
                    case PAUSE -> "Tạm nghỉ";
                    case INACTIVE -> "Đã nghỉ làm";
                };
            }
            @Override public EmployeeStatus fromString(String s) { return null; }
        });

        statusFilter.getItems().add(null);
        statusFilter.getItems().addAll(EmployeeStatus.values());
        statusFilter.getSelectionModel().selectFirst();

        statusFilter.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, selected) -> loadData());
    }

    private void setupAccountFilter() {
        accountFilter.getItems().addAll("Tất cả", "Đã có tài khoản", "Chưa có tài khoản");
        accountFilter.getSelectionModel().selectFirst();
        accountFilter.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, selected) -> {
                    clientPage = 0;
                    applyClientFilters();
                });
    }

    private void setupSearchField() {
        txtSearch.textProperty().addListener((obs, old, text) -> {
            clientPage = 0;
            applyClientFilters();
        });
    }

    private void setupTable() {
        colCode.setCellValueFactory(c ->
                new ReadOnlyStringWrapper(nullSafe(c.getValue().getEmployeeCode())));
        colName.setCellValueFactory(c ->
                new ReadOnlyStringWrapper(nullSafe(c.getValue().getEmployeeName())));
        colNationalId.setCellValueFactory(c ->
                new ReadOnlyStringWrapper(nullSafe(c.getValue().getNationalId())));
        colPhone.setCellValueFactory(c ->
                new ReadOnlyStringWrapper(nullSafe(c.getValue().getPhoneNumber())));
        colEmail.setCellValueFactory(c ->
                new ReadOnlyStringWrapper(nullSafe(c.getValue().getEmail())));

        colType.setCellValueFactory(c -> {
            Boolean isManager = c.getValue().getIsManager();
            return new ReadOnlyStringWrapper(Boolean.TRUE.equals(isManager) ? "Quản lý" : "Nhân viên");
        });

        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                    return;
                }
                EmployeeDTO employee = (EmployeeDTO) getTableRow().getItem();
                String statusStr = nullSafe(employee.getEmployeeStatus());
                Label badge = new Label();
                switch (statusStr) {
                    case "ACTIVE" -> { badge.setText("Đang làm"); badge.getStyleClass().add("status-active"); }
                    case "PAUSE"  -> { badge.setText("Tạm nghỉ"); badge.getStyleClass().add("status-pause"); }
                    default       -> { badge.setText("Đã nghỉ làm"); badge.getStyleClass().add("status-inactive"); }
                }
                setGraphic(badge);
                setText(null);
            }
        });
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button btnGrant = new Button("Cấp Tài Khoản");
            private final Button btnReset = new Button("Đặt lại MK");
            private final Button btnDelete = new Button("Xoá");


            {
                btnGrant.setStyle("-fx-background-color: #0066cc; -fx-text-fill: white; -fx-font-size: 11px; -fx-padding: 5 8; -fx-background-radius: 6; -fx-cursor: hand;");
                btnReset.setStyle("-fx-background-color: #f39c12; -fx-text-fill: white; -fx-font-size: 11px; -fx-padding: 5 8; -fx-background-radius: 6; -fx-cursor: hand;");
                btnDelete.setStyle("-fx-background-color: #c0392b; -fx-text-fill: white; -fx-font-size: 11px; -fx-padding: 5 8; -fx-background-radius: 6; -fx-cursor: hand;");

                btnGrant.setOnAction(e -> handleGrantAccount(getTableView().getItems().get(getIndex())));
                btnReset.setOnAction(e -> handleResetPassword(getTableView().getItems().get(getIndex())));
                btnDelete.setOnAction(e -> handleDelete(getTableView().getItems().get(getIndex())));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                    return;
                }
                EmployeeDTO employee = (EmployeeDTO) getTableRow().getItem();
                boolean hasAccount = employee.getAccountId() != null;
                boolean isInactive = "INACTIVE".equals(employee.getEmployeeStatus());
                boolean currentUserIsManager = SessionManager.getInstance().isManager();

                btnGrant.setDisable(hasAccount);
                btnGrant.setVisible(!hasAccount);
                btnReset.setVisible(hasAccount);

                HBox box = new HBox(6);
                box.setAlignment(Pos.CENTER);

                Region spacer1 = new Region();
                Region spacer2 = new Region();
                Region spacer3 = new Region();

                double width = 90;
                spacer1.setPrefWidth(width);
                spacer2.setPrefWidth(width);
                spacer3.setPrefWidth(width);

                btnGrant.setPrefWidth(width);
                btnReset.setPrefWidth(width);
                btnDelete.setPrefWidth(width);

                box.getChildren().addAll(
                        (!hasAccount) ? btnGrant : spacer1,
                        (hasAccount) ? btnReset : spacer2,
                        (currentUserIsManager && !isInactive) ? btnDelete : spacer3
                );

                setGraphic(box);
            }
        });
        colCode.setPrefWidth(100);
        colName.setPrefWidth(180);
        colNationalId.setPrefWidth(150);
        colPhone.setPrefWidth(130);
        colEmail.setPrefWidth(200);
        colType.setPrefWidth(120);
        colStatus.setPrefWidth(150);

        colActions.setMinWidth(300);
        colActions.setPrefWidth(330);
        colActions.setMaxWidth(360);
    }

    private void loadData() {
        clientPage = 0;
        executeAsync(
                () -> {
                    EmployeeFilterDTO filter = EmployeeFilterDTO.builder()
                            .page(0)
                            .size(1000)
                            .statusFilter(statusFilter.getValue())
                            .build();
                    return new SocketRequestService().send(new Request(ActionType.FIND_ALL_EMPLOYEES, filter));
                },
                res -> {
                    if (res.isSuccess() && res.getData() != null) {
                        EmployeePageDTO page = (EmployeePageDTO) res.getData();
                        allLoadedEmployees = new ArrayList<>(page.getContent());
                        applyClientFilters();
                    } else {
                        allLoadedEmployees = new ArrayList<>();
                        tableView.getItems().clear();
                        showAlert(Alert.AlertType.WARNING, "Không có dữ liệu", res.getMessage());
                    }
                }
        );
    }

    private void applyClientFilters() {
        String keyword = txtSearch.getText() == null ? "" : txtSearch.getText().trim().toLowerCase();
        String accountFilterValue = accountFilter.getValue();

        List<EmployeeDTO> filtered = allLoadedEmployees.stream()
                .filter(emp -> {
                    if (!keyword.isEmpty()) {
                        String code = nullSafe(emp.getEmployeeCode()).toLowerCase();
                        String name = nullSafe(emp.getEmployeeName()).toLowerCase();
                        if (!code.contains(keyword) && !name.contains(keyword)) return false;
                    }
                    if ("Đã có tài khoản".equals(accountFilterValue)) {
                        return emp.getAccountId() != null;
                    }
                    if ("Chưa có tài khoản".equals(accountFilterValue)) {
                        return emp.getAccountId() == null;
                    }
                    return true;
                })
                .toList();

        clientTotalPages = Math.max(1, (int) Math.ceil((double) filtered.size() / PAGE_SIZE));
        clientPage = Math.min(clientPage, clientTotalPages - 1);

        int from = clientPage * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, filtered.size());

        tableView.getItems().setAll(filtered.subList(from, to));
        updatePaginationControls();
    }

    @FXML
    private void handleAdd() {
        openDialog("/client/ui/views/add-employee-dialog.fxml", "Thêm nhân viên mới", null);
    }

    private void handleGrantAccount(EmployeeDTO employee) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Cấp tài khoản cho nhân viên: " + employee.getEmployeeName() + "?",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Xác nhận cấp tài khoản");
        confirm.setHeaderText(null);
        if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;

        executeAsync(
                () -> new SocketRequestService().send(
                        new Request(ActionType.CREATE_EMPLOYEE_ACCOUNT, employee.getEmployeeId())),
                res -> {
                    if (res.isSuccess() && res.getData() != null) {
                        loadData();
                        showAccountInfoDialog((AccountCreatedDTO) res.getData());
                    } else {
                        showAlert(Alert.AlertType.ERROR, "Lỗi", res.getMessage());
                    }
                }
        );
    }

    private void handleResetPassword(EmployeeDTO employee) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Bạn có chắc chắn muốn đặt lại mật khẩu cho nhân viên: " + employee.getEmployeeName() + "?",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Xác nhận reset mật khẩu");
        confirm.setHeaderText(null);
        if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;

        executeAsync(
                () -> new SocketRequestService().send(
                        new Request(ActionType.RESET_EMPLOYEE_PASSWORD, employee.getEmployeeId())),
                res -> {
                    if (res.isSuccess() && res.getData() != null) {
                        showAccountInfoDialog((AccountCreatedDTO) res.getData());
                    } else {
                        showAlert(Alert.AlertType.ERROR, "Lỗi", res.getMessage());
                    }
                }
        );
    }

    private void handleDelete(EmployeeDTO employee) {
        if (!SessionManager.getInstance().isManager()) {
            showAlert(Alert.AlertType.ERROR, "Không có quyền", "Chỉ nhân viên quản lý mới được xoá nhân viên.");
            return;
        }

        String loggedInId = SessionManager.getInstance().getEmployeeId();
        if (loggedInId != null && loggedInId.equals(employee.getEmployeeId())) {
            showAlert(Alert.AlertType.ERROR, "Không thể xoá", "Bạn không thể xoá tài khoản của chính mình.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Bạn có chắc muốn xoá mềm nhân viên: " + employee.getEmployeeName() + "?\n"
                + "Thao tác này sẽ đặt trạng thái thành 'Đã nghỉ làm' và vô hiệu hoá tài khoản.",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Xác nhận xoá mềm");
        confirm.setHeaderText(null);
        if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;

        executeAsync(
                () -> new SocketRequestService().send(
                        new Request(ActionType.DELETE_EMPLOYEE, employee.getEmployeeId())),
                res -> {
                    if (res.isSuccess()) {
                        showAlert(Alert.AlertType.INFORMATION, "Thành công", "Đã xoá mềm nhân viên thành công.");
                        loadData();
                    } else {
                        showAlert(Alert.AlertType.ERROR, "Lỗi", res.getMessage());
                    }
                }
        );
    }

    @FXML
    private void handleRefresh() {
        txtSearch.clear();
        accountFilter.getSelectionModel().selectFirst();
        statusFilter.getSelectionModel().selectFirst();
        loadData();
    }

    @FXML
    private void handlePrevPage() {
        if (clientPage > 0) {
            clientPage--;
            applyClientFilters();
        }
    }

    @FXML
    private void handleNextPage() {
        if (clientPage < clientTotalPages - 1) {
            clientPage++;
            applyClientFilters();
        }
    }

    private void updatePaginationControls() {
        pageLabel.setText((clientPage + 1) + " / " + clientTotalPages);
        btnPrev.setDisable(clientPage == 0);
        btnNext.setDisable(clientPage >= clientTotalPages - 1);
    }

    private void openDialog(String fxmlPath, String title, Object data) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent root = loader.load();
            Stage stage = new Stage();
            stage.setTitle(title);
            stage.setScene(new Scene(root));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initOwner(btnAdd.getScene().getWindow());
            stage.showAndWait();
            loadData();
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Lỗi", "Không thể mở dialog: " + e.getMessage());
        }
    }

    private void showAccountInfoDialog(AccountCreatedDTO dto) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/ui/views/account-info-dialog.fxml"));
            Parent root = loader.load();
            AccountInfoDialogController ctrl = loader.getController();
            ctrl.initData(dto);
            Stage stage = new Stage();
            stage.setTitle("Thông tin tài khoản");
            stage.setScene(new Scene(root));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initOwner(btnAdd.getScene().getWindow());
            stage.showAndWait();
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Lỗi", "Không thể mở dialog thông tin tài khoản: " + e.getMessage());
        }
    }

    private void executeAsync(Supplier<Response> action, Consumer<Response> onDone) {
        setLoading(true);
        Task<Response> task = new Task<>() {
            @Override protected Response call() { return action.get(); }
        };
        task.setOnSucceeded(e -> Platform.runLater(() -> {
            setLoading(false);
            onDone.accept(task.getValue());
        }));
        task.setOnFailed(e -> Platform.runLater(() -> {
            setLoading(false);
            showAlert(Alert.AlertType.ERROR, "Lỗi kết nối",
                    "Kết nối thất bại: " + task.getException().getMessage());
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

    private String nullSafe(String value) {
        return value != null ? value : "";
    }
}
