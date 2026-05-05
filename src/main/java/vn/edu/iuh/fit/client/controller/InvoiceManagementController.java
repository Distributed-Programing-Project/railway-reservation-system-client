package vn.edu.iuh.fit.client.controller;

import java.io.IOException;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
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
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import vn.edu.iuh.fit.client.service.SessionManager;
import vn.edu.iuh.fit.client.service.SocketRequestService;
import vn.edu.iuh.fit.common.command.ActionType;
import vn.edu.iuh.fit.common.constant.InvoiceType;
import vn.edu.iuh.fit.common.dto.InvoiceFilterDTO;
import vn.edu.iuh.fit.common.dto.InvoicePageDTO;
import vn.edu.iuh.fit.common.dto.InvoiceSummaryDTO;
import vn.edu.iuh.fit.common.request.Request;
import vn.edu.iuh.fit.common.response.Response;

public class InvoiceManagementController {

    @FXML
    private TextField txtKeyword;
    @FXML
    private ComboBox<Integer> cmbDay;
    @FXML
    private ComboBox<Integer> cmbMonth;
    @FXML
    private ComboBox<Integer> cmbYear;
    @FXML
    private ComboBox<InvoiceType> cmbType;

    @FXML
    private TableView<InvoiceSummaryDTO> tableView;
    @FXML
    private TableColumn<InvoiceSummaryDTO, String> colId;
    @FXML
    private TableColumn<InvoiceSummaryDTO, String> colCustomer;
    @FXML
    private TableColumn<InvoiceSummaryDTO, String> colEmployee;
    @FXML
    private TableColumn<InvoiceSummaryDTO, String> colDate;
    @FXML
    private TableColumn<InvoiceSummaryDTO, String> colType;
    @FXML
    private TableColumn<InvoiceSummaryDTO, String> colCount;
    @FXML
    private TableColumn<InvoiceSummaryDTO, String> colAmount;
    @FXML
    private TableColumn<InvoiceSummaryDTO, Void> colActions;

    @FXML
    private Button btnPrev;
    @FXML
    private Button btnNext;
    @FXML
    private Label lblPage;
    @FXML
    private StackPane loadingOverlay;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final NumberFormat MONEY_FORMAT = NumberFormat.getNumberInstance(new Locale("vi", "VN"));
    private static final int PAGE_SIZE = 20;

    private final SocketRequestService socketRequestService = new SocketRequestService();
    private int currentPage = 0;
    private int totalPages = 1;

    @FXML
    private void initialize() {
        setupFilters();
        setupTable();
        loadData();
    }

    private void setupFilters() {
        int currentYear = LocalDate.now().getYear();

        cmbDay.getItems().add(null);
        for (int d = 1; d <= 31; d++)
            cmbDay.getItems().add(d);
        cmbDay.setConverter(nullableIntConverter("Ngày"));

        cmbMonth.getItems().add(null);
        for (int m = 1; m <= 12; m++)
            cmbMonth.getItems().add(m);
        cmbMonth.setConverter(nullableIntConverter("Tháng"));

        cmbYear.getItems().add(null);
        for (int y = currentYear - 4; y <= currentYear + 1; y++)
            cmbYear.getItems().add(y);
        cmbYear.setConverter(nullableIntConverter("Năm"));

        cmbType.getItems().add(null);
        cmbType.getItems().addAll(InvoiceType.values());
        cmbType.setConverter(new StringConverter<>() {
            @Override
            public String toString(InvoiceType t) {
                if (t == null)
                    return "Tất cả loại";
                return switch (t) {
                    case SALE -> "Bán vé (SALE)";
                    case REFUND -> "Trả vé (REFUND)";
                    case EXCHANGE -> "Đổi vé (EXCHANGE)";
                };
            }

            @Override
            public InvoiceType fromString(String s) {
                return null;
            }
        });
        cmbType.getSelectionModel().selectFirst();
    }

    private void setupTable() {
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        colId.setCellValueFactory(cell -> {
            String invoiceId = cell.getValue().getId();
            if (invoiceId == null)
                return new ReadOnlyStringWrapper("");
            return new ReadOnlyStringWrapper(
                    invoiceId.length() > 13 ? invoiceId.substring(0, 13) + "…" : invoiceId);
        });
        colCustomer.setCellValueFactory(cell -> new ReadOnlyStringWrapper(orEmpty(cell.getValue().getCustomerName())));
        colEmployee.setCellValueFactory(cell -> new ReadOnlyStringWrapper(orEmpty(cell.getValue().getEmployeeName())));
        colDate.setCellValueFactory(cell -> {
            var issueDate = cell.getValue().getIssueDate();
            return new ReadOnlyStringWrapper(issueDate != null ? DATE_FORMAT.format(issueDate) : "");
        });
        colType.setCellValueFactory(cell -> new ReadOnlyStringWrapper(formatInvoiceType(cell.getValue().getType())));
        colCount.setCellValueFactory(
                cell -> new ReadOnlyStringWrapper(String.valueOf(cell.getValue().getTicketCount())));
        colAmount.setCellValueFactory(
                cell -> new ReadOnlyStringWrapper(MONEY_FORMAT.format(cell.getValue().getTotalAmount()) + " đ"));

        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button btnView = new Button("🔍 Xem");
            private final Button btnPrint = new Button("🖨 In HĐ");

            {
                btnView.setStyle(
                        "-fx-background-color:#0066cc;-fx-text-fill:white;-fx-font-size:12px;-fx-font-weight:700;-fx-padding:6 14;-fx-background-radius:8;-fx-cursor:hand;");
                btnPrint.setStyle(
                        "-fx-background-color:#008000;-fx-text-fill:white;-fx-font-size:12px;-fx-font-weight:700;-fx-padding:6 14;-fx-background-radius:8;-fx-cursor:hand;");
                btnView.setOnAction(e -> openDetailDialog(getTableView().getItems().get(getIndex()), false));
                btnPrint.setOnAction(e -> openDetailDialog(getTableView().getItems().get(getIndex()), true));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    HBox box = new HBox(8, btnView, btnPrint);
                    box.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                    setGraphic(box);
                }
            }
        });
    }

    private void loadData() {
        InvoiceFilterDTO invoiceFilterDTO = buildFilter();
        executeAsync(
                () -> {
                    Request req = new Request(ActionType.FILTER_INVOICES, invoiceFilterDTO);
                    return socketRequestService.send(req);
                },
                res -> {
                    if (res.isSuccess() && res.getData() instanceof InvoicePageDTO invoicePageDTO) {
                        tableView.getItems().setAll(invoicePageDTO.getContent());
                        totalPages = Math.max(1, invoicePageDTO.getTotalPages());
                        updatePaginationControls();
                    } else {
                        tableView.getItems().clear();
                        showAlert(Alert.AlertType.INFORMATION, "Không có dữ liệu",
                                res.getMessage() != null ? res.getMessage() : "Không tìm thấy hoá đơn phù hợp");
                    }
                });
    }

    private InvoiceFilterDTO buildFilter() {
        String keyword = txtKeyword.getText();
        return InvoiceFilterDTO.builder()
                .requestEmployeeId(SessionManager.getInstance().getEmployeeId())
                .keyword(keyword != null && !keyword.isBlank() ? keyword.trim() : null)
                .day(cmbDay.getValue())
                .month(cmbMonth.getValue())
                .year(cmbYear.getValue())
                .type(cmbType.getValue())
                .page(currentPage)
                .size(PAGE_SIZE)
                .build();
    }

    @FXML
    private void handleSearch() {
        currentPage = 0;
        loadData();
    }

    @FXML
    private void handleRefresh() {
        txtKeyword.clear();
        cmbDay.getSelectionModel().selectFirst();
        cmbMonth.getSelectionModel().selectFirst();
        cmbYear.getSelectionModel().selectFirst();
        cmbType.getSelectionModel().selectFirst();
        currentPage = 0;
        loadData();
    }

    @FXML
    private void handlePrevPage() {
        if (currentPage > 0) {
            currentPage--;
            loadData();
        }
    }

    @FXML
    private void handleNextPage() {
        if (currentPage < totalPages - 1) {
            currentPage++;
            loadData();
        }
    }

    private void openDetailDialog(InvoiceSummaryDTO invoiceSummaryDTO, boolean autoPrint) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/client/ui/views/invoice-detail-dialog.fxml"));
            Parent root = loader.load();
            InvoiceDetailDialogController ctrl = loader.getController();

            Stage stage = new Stage();
            stage.setTitle("Chi tiết Hoá đơn – " + invoiceSummaryDTO.getId());
            stage.setScene(new Scene(root));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initOwner(tableView.getScene().getWindow());
            stage.setMinWidth(960);
            stage.setMinHeight(640);
            stage.setOnShown(e -> ctrl.initData(invoiceSummaryDTO.getId(), autoPrint));
            stage.showAndWait();
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Lỗi",
                    "Không thể mở chi tiết hoá đơn: " + e.getMessage());
        }
    }

    private void updatePaginationControls() {
        lblPage.setText((currentPage + 1) + " / " + totalPages);
        btnPrev.setDisable(currentPage == 0);
        btnNext.setDisable(currentPage >= totalPages - 1);
    }

    private void executeAsync(Supplier<Response> action, Consumer<Response> onDone) {
        setLoading(true);
        Task<Response> task = new Task<>() {
            @Override
            protected Response call() {
                return action.get();
            }
        };
        task.setOnSucceeded(e -> Platform.runLater(() -> {
            setLoading(false);
            onDone.accept(task.getValue());
        }));
        task.setOnFailed(e -> Platform.runLater(() -> {
            setLoading(false);
            showAlert(Alert.AlertType.ERROR, "Lỗi kết nối",
                    "Không thể kết nối đến server: " + task.getException().getMessage());
        }));
        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    private void setLoading(boolean loading) {
        loadingOverlay.setVisible(loading);
        if (loading)
            loadingOverlay.toFront();
    }

    private void showAlert(Alert.AlertType type, String title, String msg) {
        Alert alert = new Alert(type, msg, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    private String formatInvoiceType(InvoiceType type) {
        if (type == null)
            return "";
        return switch (type) {
            case SALE -> "Bán vé";
            case REFUND -> "Trả vé";
            case EXCHANGE -> "Đổi vé";
        };
    }

    private String orEmpty(String value) {
        return value != null ? value : "";
    }

    private StringConverter<Integer> nullableIntConverter(String placeholder) {
        return new StringConverter<>() {
            @Override
            public String toString(Integer i) {
                return i == null ? placeholder : String.valueOf(i);
            }

            @Override
            public Integer fromString(String s) {
                return null;
            }
        };
    }
}