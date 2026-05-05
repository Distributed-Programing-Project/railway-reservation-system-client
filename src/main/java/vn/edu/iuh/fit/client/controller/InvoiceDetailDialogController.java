package vn.edu.iuh.fit.client.controller;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.print.PrinterJob;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import vn.edu.iuh.fit.client.service.SocketRequestService;
import vn.edu.iuh.fit.common.command.ActionType;
import vn.edu.iuh.fit.common.constant.InvoiceType;
import vn.edu.iuh.fit.common.constant.TicketType;
import vn.edu.iuh.fit.common.dto.InvoiceDetailResponseDTO;
import vn.edu.iuh.fit.common.dto.InvoiceLineItemDTO;
import vn.edu.iuh.fit.common.dto.OriginalTicketInfoDTO;
import vn.edu.iuh.fit.common.request.Request;
import vn.edu.iuh.fit.common.response.Response;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

public class InvoiceDetailDialogController {

    @FXML private Label lblInvoiceId;
    @FXML private Label lblIssueDate;
    @FXML private Label lblInvoiceType;
    @FXML private Label lblCustomer;
    @FXML private Label lblEmployee;
    @FXML private Label lblPhone;
    @FXML private Label lblTotalAmount;
    @FXML private Label lblVatLabel;
    @FXML private Label lblVatInfo;

    @FXML private TableView<InvoiceLineItemDTO> tableItems;
    @FXML private TableColumn<InvoiceLineItemDTO, String> colPassenger;
    @FXML private TableColumn<InvoiceLineItemDTO, String> colIdCard;
    @FXML private TableColumn<InvoiceLineItemDTO, String> colTicketType;
    @FXML private TableColumn<InvoiceLineItemDTO, String> colTrain;
    @FXML private TableColumn<InvoiceLineItemDTO, String> colSeat;
    @FXML private TableColumn<InvoiceLineItemDTO, String> colRoute;
    @FXML private TableColumn<InvoiceLineItemDTO, String> colDepartTime;
    @FXML private TableColumn<InvoiceLineItemDTO, String> colSubTotal;
    @FXML private TableColumn<InvoiceLineItemDTO, String> colDiscount;
    @FXML private TableColumn<InvoiceLineItemDTO, String> colInsurance;
    @FXML private TableColumn<InvoiceLineItemDTO, String> colFinal;
    @FXML private TableColumn<InvoiceLineItemDTO, String> colStatus;

    @FXML private VBox paneExchange;
    @FXML private Label lblOrigTrain;
    @FXML private Label lblOrigSeat;
    @FXML private Label lblOrigRoute;
    @FXML private Label lblOrigDepartTime;

    @FXML private Button btnPrint;
    @FXML private Button btnClose;
    @FXML private StackPane loadingOverlay;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final NumberFormat MONEY_FORMAT = NumberFormat.getNumberInstance(new Locale("vi", "VN"));

    private final SocketRequestService socketRequestService = new SocketRequestService();
    private InvoiceDetailResponseDTO currentInvoice;
    private boolean autoPrint;

    public void initData(String invoiceId, boolean autoPrint) {
        this.autoPrint = autoPrint;
        setupTable();
        loadInvoiceDetail(invoiceId);
    }

    private void setupTable() {
        colPassenger.setCellValueFactory(c -> new ReadOnlyStringWrapper(orEmpty(c.getValue().getPassengerName())));
        colIdCard.setCellValueFactory(c -> new ReadOnlyStringWrapper(orEmpty(c.getValue().getPassengerIdCard())));
        colTicketType.setCellValueFactory(c -> new ReadOnlyStringWrapper(formatTicketType(c.getValue().getTicketType())));
        colTrain.setCellValueFactory(c -> new ReadOnlyStringWrapper(orEmpty(c.getValue().getTrainName())));
        colSeat.setCellValueFactory(c -> {
            String carriage = orEmpty(c.getValue().getCarriageName());
            String seat = orEmpty(c.getValue().getSeatCode());
            return new ReadOnlyStringWrapper(carriage.isEmpty() ? seat : "T" + carriage + "-G" + seat);
        });
        colRoute.setCellValueFactory(c -> {
            String dep = orEmpty(c.getValue().getDepartureStation());
            String arr = orEmpty(c.getValue().getArrivalStation());
            return new ReadOnlyStringWrapper(dep + " → " + arr);
        });
        colDepartTime.setCellValueFactory(c -> {
            LocalDateTime dt = c.getValue().getDepartureTime();
            return new ReadOnlyStringWrapper(dt != null ? DATE_FORMAT.format(dt) : "");
        });
        colSubTotal.setCellValueFactory(c -> {
            Double sub = c.getValue().getSubTotal();
            return new ReadOnlyStringWrapper(sub != null ? MONEY_FORMAT.format(sub) + " đ" : "");
        });
        colDiscount.setCellValueFactory(c ->
            new ReadOnlyStringWrapper(MONEY_FORMAT.format(c.getValue().getDiscount()) + " đ"));
        colInsurance.setCellValueFactory(c ->
            new ReadOnlyStringWrapper(MONEY_FORMAT.format(c.getValue().getInsurance()) + " đ"));
        colFinal.setCellValueFactory(c ->
            new ReadOnlyStringWrapper(MONEY_FORMAT.format(c.getValue().getFinalAmount()) + " đ"));
        colStatus.setCellValueFactory(c ->
            new ReadOnlyStringWrapper(c.getValue().isReturned() ? "Đã hoàn" : "Bình thường"));
    }

    private void loadInvoiceDetail(String invoiceId) {
        setLoading(true);
        Task<Response> task = new Task<>() {
            @Override
            protected Response call() {
                Request req = new Request(ActionType.GET_INVOICE_DETAIL_BY_ID, invoiceId);
                return socketRequestService.send(req);
            }
        };
        task.setOnSucceeded(e -> Platform.runLater(() -> {
            setLoading(false);
            Response res = task.getValue();
            if (res.isSuccess() && res.getData() instanceof InvoiceDetailResponseDTO invoiceDetailResponseDTO) {
                currentInvoice = invoiceDetailResponseDTO;
                populateDialog(invoiceDetailResponseDTO);
                if (autoPrint) handlePrint();
            } else {
                showAlert(Alert.AlertType.ERROR, "Lỗi",
                    res.getMessage() != null ? res.getMessage() : "Không thể tải chi tiết hoá đơn");
                handleClose();
            }
        }));
        task.setOnFailed(e -> Platform.runLater(() -> {
            setLoading(false);
            showAlert(Alert.AlertType.ERROR, "Lỗi kết nối",
                "Không thể kết nối đến server: " + task.getException().getMessage());
            handleClose();
        }));
        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    private void populateDialog(InvoiceDetailResponseDTO dto) {
        lblInvoiceId.setText(orEmpty(dto.getId()));
        lblIssueDate.setText(dto.getIssueDate() != null ? DATE_FORMAT.format(dto.getIssueDate()) : "");
        lblCustomer.setText(orEmpty(dto.getCustomerName()));
        lblEmployee.setText(orEmpty(dto.getEmployeeName()));
        lblPhone.setText(orEmpty(dto.getCustomerPhoneNumber()));
        lblTotalAmount.setText(MONEY_FORMAT.format(dto.getTotalAmount()) + " đ");

        String typeLabel = formatInvoiceType(dto.getType());
        String typeStyle = switch (dto.getType() != null ? dto.getType() : InvoiceType.SALE) {
            case SALE     -> "-fx-background-color:#e7f5ee;-fx-text-fill:#0f6d3f;-fx-padding:4 12;-fx-background-radius:999;-fx-font-weight:800;-fx-font-size:12px;";
            case REFUND   -> "-fx-background-color:#fde8e8;-fx-text-fill:#c0392b;-fx-padding:4 12;-fx-background-radius:999;-fx-font-weight:800;-fx-font-size:12px;";
            case EXCHANGE -> "-fx-background-color:#fff3e6;-fx-text-fill:#8a4b00;-fx-padding:4 12;-fx-background-radius:999;-fx-font-weight:800;-fx-font-size:12px;";
        };
        lblInvoiceType.setText(typeLabel);
        lblInvoiceType.setStyle(typeStyle);

        if (dto.getTaxCode() != null && !dto.getTaxCode().isBlank()) {
            lblVatLabel.setVisible(true);
            lblVatLabel.setManaged(true);
            lblVatInfo.setVisible(true);
            lblVatInfo.setManaged(true);
            lblVatInfo.setText("MST: " + dto.getTaxCode()
                + (dto.getCompanyName() != null ? " — " + dto.getCompanyName() : ""));
        }

        List<InvoiceLineItemDTO> invoiceLineItems = dto.getDetails();
        if (invoiceLineItems != null) {
            tableItems.getItems().setAll(invoiceLineItems);
        }

        if (dto.getType() == InvoiceType.EXCHANGE) {
            populateExchangeSection(invoiceLineItems);
        }
    }

    private void populateExchangeSection(List<InvoiceLineItemDTO> invoiceLineItems) {
        if (invoiceLineItems == null || invoiceLineItems.isEmpty()) return;
        OriginalTicketInfoDTO originalTicketInfoDTO = invoiceLineItems.stream()
            .map(InvoiceLineItemDTO::getOriginalTicketInfo)
            .filter(info -> info != null)
            .findFirst()
            .orElse(null);

        if (originalTicketInfoDTO == null) return;

        paneExchange.setVisible(true);
        paneExchange.setManaged(true);
        lblOrigTrain.setText(orEmpty(originalTicketInfoDTO.getOriginalTrainName()));
        lblOrigSeat.setText("Toa " + orEmpty(originalTicketInfoDTO.getOriginalCarriageName())
            + " — Ghế " + orEmpty(originalTicketInfoDTO.getOriginalSeatCode()));
        lblOrigRoute.setText(orEmpty(originalTicketInfoDTO.getOriginalDepartureStation())
            + " → " + orEmpty(originalTicketInfoDTO.getOriginalArrivalStation()));
        LocalDateTime origDep = originalTicketInfoDTO.getOriginalDepartureTime();
        lblOrigDepartTime.setText(origDep != null ? DATE_FORMAT.format(origDep) : "");
    }

    @FXML
    private void handlePrint() {
        if (currentInvoice == null) {
            showAlert(Alert.AlertType.WARNING, "Chưa có dữ liệu", "Vui lòng đợi dữ liệu tải xong.");
            return;
        }
        try {
            String html = buildPrintHtml(currentInvoice);
            WebView printWebView = new WebView();
            WebEngine webEngine = printWebView.getEngine();

            webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
                if (newState == javafx.concurrent.Worker.State.SUCCEEDED) {
                    PrinterJob printerJob = PrinterJob.createPrinterJob();
                    if (printerJob == null) {
                        showAlert(Alert.AlertType.ERROR, "Lỗi máy in",
                            "Không tìm thấy máy in. Vui lòng kiểm tra cài đặt.");
                        return;
                    }
                    boolean proceed = printerJob.showPrintDialog(btnPrint.getScene().getWindow());
                    if (proceed) {
                        printWebView.getEngine().print(printerJob);
                        printerJob.endJob();
                        showAlert(Alert.AlertType.INFORMATION, "Thành công", "In hoá đơn thành công.");
                    } else {
                        printerJob.cancelJob();
                    }
                }
            });
            webEngine.loadContent(html, "text/html");

        } catch (Exception e) {
            System.err.println("ERROR: Failed to render print template: " + e.getMessage());
            showAlert(Alert.AlertType.ERROR, "Lỗi in",
                "Không thể tạo bản in. Vui lòng thử lại.");
        }
    }

    @FXML
    private void handleClose() {
        if (btnClose.getScene() == null) return;
        Stage stage = (Stage) btnClose.getScene().getWindow();
        if (stage != null) stage.close();
    }

    private String buildPrintHtml(InvoiceDetailResponseDTO dto) {
        String css = readCss();
        NumberFormat moneyFmt = NumberFormat.getNumberInstance(new Locale("vi", "VN"));

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'><style>")
          .append(css).append("</style></head><body>");
        sb.append("<div class='invoice-container'>");

        // Header
        sb.append("<table class='header-table'><tr>")
          .append("<td class='title-cell'><h1>HOÁ ĐƠN BÁN VÉ TÀU</h1>")
          .append("<p>Loại: ").append(formatInvoiceType(dto.getType())).append("</p></td>")
          .append("<td class='info-cell'><p>Mã HĐ: ").append(orEmpty(dto.getId())).append("</p>")
          .append("<p>Ngày: ").append(dto.getIssueDate() != null ? DATE_FORMAT.format(dto.getIssueDate()) : "").append("</p></td>")
          .append("</tr></table>");

        // Party info
        sb.append("<div class='party-info'>")
          .append("<p><b>Khách hàng:</b> ").append(orEmpty(dto.getCustomerName()))
          .append(" &nbsp;|&nbsp; SĐT: ").append(orEmpty(dto.getCustomerPhoneNumber())).append("</p>")
          .append("<p><b>Nhân viên:</b> ").append(orEmpty(dto.getEmployeeName())).append("</p>");
        if (dto.getTaxCode() != null && !dto.getTaxCode().isBlank()) {
            sb.append("<p><b>MST:</b> ").append(dto.getTaxCode());
            if (dto.getCompanyName() != null) sb.append(" — ").append(dto.getCompanyName());
            sb.append("</p>");
        }
        sb.append("</div><hr class='separator'/>");

        // Items table
        sb.append("<div class='items-table'><table><thead><tr>")
          .append("<th>STT</th><th>Hành khách</th><th>CCCD</th><th>Đối tượng</th>")
          .append("<th>Tàu/Toa/Ghế</th><th>Tuyến đường</th><th>Giờ khởi hành</th>")
          .append("<th>Giá gốc</th><th>Giảm giá</th><th>Bảo hiểm</th><th>Thành tiền</th>")
          .append("</tr></thead><tbody>");

        List<InvoiceLineItemDTO> invoiceLineItems = dto.getDetails();
        if (invoiceLineItems != null) {
            int stt = 1;
            for (InvoiceLineItemDTO lineItem : invoiceLineItems) {
                sb.append("<tr>")
                  .append("<td>").append(stt++).append("</td>")
                  .append("<td>").append(orEmpty(lineItem.getPassengerName())).append("</td>")
                  .append("<td>").append(orEmpty(lineItem.getPassengerIdCard())).append("</td>")
                  .append("<td>").append(formatTicketType(lineItem.getTicketType())).append("</td>")
                  .append("<td>").append(orEmpty(lineItem.getTrainName()))
                  .append(" T").append(orEmpty(lineItem.getCarriageName()))
                  .append("-G").append(orEmpty(lineItem.getSeatCode())).append("</td>")
                  .append("<td>").append(orEmpty(lineItem.getDepartureStation()))
                  .append(" → ").append(orEmpty(lineItem.getArrivalStation())).append("</td>")
                  .append("<td>").append(lineItem.getDepartureTime() != null ? DATE_FORMAT.format(lineItem.getDepartureTime()) : "").append("</td>")
                  .append("<td>").append(lineItem.getSubTotal() != null ? moneyFmt.format(lineItem.getSubTotal()) : "").append("</td>")
                  .append("<td>").append(moneyFmt.format(lineItem.getDiscount())).append("</td>")
                  .append("<td>").append(moneyFmt.format(lineItem.getInsurance())).append("</td>")
                  .append("<td><b>").append(moneyFmt.format(lineItem.getFinalAmount())).append("</b></td>")
                  .append("</tr>");
                if (lineItem.isReturned()) {
                    sb.append("<tr><td colspan='11' style='color:#c0392b;font-style:italic;'>")
                      .append("  → Vé này đã hoàn — Số tiền hoàn: ")
                      .append(moneyFmt.format(lineItem.getRefundAmount())).append(" đ</td></tr>");
                }
            }
        }
        sb.append("</tbody></table></div>");

        // Summary
        sb.append("<div class='summary-section'><table>")
          .append("<tr><td class='no-border'></td><td><b>TỔNG CỘNG:</b></td>")
          .append("<td><b>").append(moneyFmt.format(dto.getTotalAmount())).append(" đ</b></td></tr>")
          .append("</table></div>");

        // Signature
        sb.append("<table class='signature-table'><tr>")
          .append("<td><p class='sign-title'>Khách hàng</p><p class='sign-note'>(Ký, ghi rõ họ tên)</p></td>")
          .append("<td><p class='sign-title'>Nhân viên lập</p><p class='sign-note'>(Ký, ghi rõ họ tên)</p></td>")
          .append("</tr></table>");

        sb.append("</div></body></html>");
        return sb.toString();
    }

    private String readCss() {
        try (InputStream in = getClass().getResourceAsStream("/client/ui/css/invoice-style.css")) {
            if (in == null) return "";
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                StringBuilder sb = new StringBuilder();
                char[] buf = new char[2048];
                int read;
                while ((read = reader.read(buf)) >= 0) sb.append(buf, 0, read);
                return sb.toString();
            }
        } catch (Exception e) {
            System.err.println("WARN: Could not read invoice-style.css: " + e.getMessage());
            return "";
        }
    }

    private void setLoading(boolean loading) {
        loadingOverlay.setVisible(loading);
        loadingOverlay.setManaged(loading);
        if (loading) loadingOverlay.toFront();
    }

    private void showAlert(Alert.AlertType type, String title, String msg) {
        Alert alert = new Alert(type, msg, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    private String formatInvoiceType(InvoiceType type) {
        if (type == null) return "";
        return switch (type) {
            case SALE -> "Bán vé";
            case REFUND -> "Trả vé";
            case EXCHANGE -> "Đổi vé";
        };
    }

    private String formatTicketType(TicketType type) {
        if (type == null) return "";
        return switch (type) {
            case NORMAL -> "Người lớn";
            case CHILD -> "Trẻ em";
            case SENIOR -> "Người cao tuổi";
            case STUDENT -> "Học sinh/SV";
        };
    }

    private String orEmpty(String value) {
        return value != null ? value : "";
    }
}
