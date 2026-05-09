package vn.edu.iuh.fit.client.controller;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.text.Normalizer;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import net.sf.jasperreports.engine.JREmptyDataSource;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperPrintManager;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.engine.xml.JRXmlLoader;
import vn.edu.iuh.fit.client.service.ReturnTicketClientService;
import vn.edu.iuh.fit.common.dto.RefundReceiptDTO;
import vn.edu.iuh.fit.common.dto.RefundReceiptItemDTO;
import vn.edu.iuh.fit.common.dto.ReturnTicketPreviewDTO;
import vn.edu.iuh.fit.common.dto.ReturnTicketTicketDTO;
import vn.edu.iuh.fit.common.dto.StationDTO;
import vn.edu.iuh.fit.common.response.Response;

public class ReturnTicketController {

  private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
  private static final NumberFormat MONEY = NumberFormat.getInstance(new Locale("vi", "VN"));
  private static final long MINUTES_24H = 24 * 60;

  private BanVeController mainController;

  private final ReturnTicketClientService returnTicketClientService = new ReturnTicketClientService();

  private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
    Thread t = new Thread(r, "return-ticket-worker");
    t.setDaemon(true);
    return t;
  });
  private final AtomicBoolean cleanupDone = new AtomicBoolean(false);
  private final AtomicLong searchSeq = new AtomicLong(0);
  private final AtomicLong previewSeq = new AtomicLong(0);
  private final AtomicLong confirmSeq = new AtomicLong(0);

  private Timeline countdownTimeline;
  private ReturnTicketPreviewDTO lastPreview;
  private String lastSearchIdCard;

  @FXML
  private TextField txtSearchCCCD;
  @FXML
  private Button btnTimKiem;

  @FXML
  private TableView<ReturnTicketTicketDTO> tblDanhSachVe;
  @FXML
  private TableColumn<ReturnTicketTicketDTO, String> colMaVe;
  @FXML
  private TableColumn<ReturnTicketTicketDTO, String> colTau;
  @FXML
  private TableColumn<ReturnTicketTicketDTO, String> colHanhTrinh;
  @FXML
  private TableColumn<ReturnTicketTicketDTO, String> colNgayDi;
  @FXML
  private TableColumn<ReturnTicketTicketDTO, String> colGhe;
  @FXML
  private TableColumn<ReturnTicketTicketDTO, String> colGiaVe;
  @FXML
  private TableColumn<ReturnTicketTicketDTO, String> colTrangThai;

  @FXML
  private Label lblTieuDe;
  @FXML
  private Label lblMaVeChon;
  @FXML
  private Label lblTau;
  @FXML
  private Label lblHanhTrinh;
  @FXML
  private Label lblNgayDi;
  @FXML
  private Label lblGhe;
  @FXML
  private Label lblThoiGianConLai;

  @FXML
  private Label lblThongBaoLoi;
  @FXML
  private Label lblDieuKienVe;
  @FXML
  private Label lblGiaVeGoc;
  @FXML
  private Label lblPhiTraVe;
  @FXML
  private Label lblTienHoanLai;

  @FXML
  private Button btnXacNhanTra;
  @FXML
  private Button btnDoiVe;

  @FXML
  private VBox boxTinhTien;

  public void setMainController(BanVeController mainController) {
    this.mainController = mainController;

    // NẾU CÓ MAIN CONTROLLER TRUYỀN VÀO => CHẮC CHẮN ĐANG Ở LUỒNG ĐỔI VÉ
    if (this.mainController != null) {

      if (btnDoiVe != null) {
        btnDoiVe.setVisible(true);
        btnDoiVe.setManaged(true);
      }

      if (btnXacNhanTra != null) {
        btnXacNhanTra.setVisible(false);
        btnXacNhanTra.setManaged(false);
      }

      if (lblTieuDe != null) {
        lblTieuDe.setText("TRA CỨU VÉ ĐỂ ĐỔI");
      }

      // --- THÊM ĐOẠN NÀY ĐỂ ẨN KHUNG TÍNH TIỀN TRẢ VÉ ---
      if (boxTinhTien != null) {
        boxTinhTien.setVisible(false);
        boxTinhTien.setManaged(false);
      }

    }
  }

  @FXML
  private void handleDoiVe() {
    if (mainController == null) {
      showError("Lỗi hệ thống", "Không tìm thấy luồng xử lý chính. Không thể đổi vé.");
      return;
    }

    List<ReturnTicketTicketDTO> oldTickets = getSelectedTickets();
    if (oldTickets.isEmpty()) {
      showWarning("Đổi vé", "Vui lòng chọn vé cần đổi từ danh sách.");
      return;
    }

    // 1. NGHIỆP VỤ: Kiểm tra trước 24h & Chưa từng được đổi
    LocalDateTime now = LocalDateTime.now();
    for (ReturnTicketTicketDTO t : oldTickets) {
      if (t.getOriginalTicketId() != null && !t.getOriginalTicketId().isBlank()) {
        showError("Lỗi", "Vé " + t.getId() + " là vé đã đổi, không được phép đổi thêm lần nào nữa.");
        return;
      }
      if (t.getDepartureTime() == null) {
        showError("Lỗi", "Vé " + t.getId() + " bị lỗi ngày khởi hành.");
        return;
      }
      long hoursDiff = Duration.between(now, t.getDepartureTime()).toHours();
      if (hoursDiff < 24) {
        showError("Lỗi",
            "Vé " + t.getId() + " không đủ điều kiện (Phải đổi trước 24h). Thời gian còn lại: " + hoursDiff + "h");
        return;
      }
    }

    // 2. LƯU THÔNG TIN VÀO SESSION CHUNG
    vn.edu.iuh.fit.client.session.SaleWizardState state = mainController.getState();
    state.setExchangeMode(true);
    state.setExchangeOldTickets(oldTickets);

    // Lấy thông tin ga từ vé đầu tiên (quy định là cùng ga đi - ga đến)
    ReturnTicketTicketDTO firstTicket = oldTickets.get(0);
    state.setDepartureStation(StationDTO.builder()
        .name(normalizeStationName(firstTicket.getDepartureStation()))
        .build());
    state.setDestinationStation(StationDTO.builder()
        .name(normalizeStationName(firstTicket.getDestinationStation()))
        .build());
    if (firstTicket.getDepartureTime() != null) {
      LocalDate oldDate = firstTicket.getDepartureTime().toLocalDate();
      LocalDate nowDate = LocalDate.now();
      state.setDepartureDate(oldDate.isBefore(nowDate) ? nowDate : oldDate);
    }

    // Đổi vé theo quy định: xử lý như vé đơn (One Way)
    state.setTicketCategory(vn.edu.iuh.fit.common.constant.TicketCategory.ONE_WAY);

    // 3. CHUYỂN TRANG VỀ STEP 1 CỦA BÁN VÉ
    mainController.showStep1();
  }

  @FXML
  public void initialize() {
    setupTable();
    registerCloseCleanupHook();

    btnTimKiem.setOnAction(e -> handleSearch());
    txtSearchCCCD.setOnAction(e -> handleSearch());
    btnXacNhanTra.setOnAction(e -> handleConfirmReturn());

    resetDetail();
    resetPreview();
    setBusy(false);
    doSearchAsync("");
  }

  private void setupTable() {
    if (tblDanhSachVe == null)
      return;
    tblDanhSachVe.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

    colMaVe.setCellValueFactory(c -> s(c.getValue() != null ? c.getValue().getId() : null));
    colTau.setCellValueFactory(c -> s(c.getValue() != null ? c.getValue().getTrainCode() : null));
    colHanhTrinh.setCellValueFactory(c -> {
      ReturnTicketTicketDTO dto = c.getValue();
      if (dto == null)
        return s(null);
      String from = safe(dto.getDepartureStation());
      String to = safe(dto.getDestinationStation());
      if ("--".equals(from) && "--".equals(to))
        return s("--");
      return s(from + " → " + to);
    });
    colNgayDi.setCellValueFactory(c -> {
      LocalDateTime dt = c.getValue() != null ? c.getValue().getDepartureTime() : null;
      return s(dt == null ? null : DATE_TIME.format(dt));
    });
    colGhe.setCellValueFactory(c -> {
      ReturnTicketTicketDTO dto = c.getValue();
      if (dto == null)
        return s(null);
      String carriage = safe(dto.getCarriageName());
      String seat = safe(dto.getSeatNumber());
      if ("--".equals(carriage) && "--".equals(seat))
        return s("--");
      return s("Toa " + carriage + " - Ghế " + seat);
    });
    colGiaVe.setCellValueFactory(c -> s(formatMoney(c.getValue() != null ? c.getValue().getTicketPrice() : 0)));
    colTrangThai.setCellValueFactory(c -> {
      ReturnTicketTicketDTO dto = c.getValue();
      return s(dto != null && dto.getStatus() != null ? dto.getStatus().getName() : null);
    });

    tblDanhSachVe.getSelectionModel().getSelectedItems()
        .addListener((ListChangeListener<ReturnTicketTicketDTO>) change -> {
          handleSelectionChanged();
        });
    tblDanhSachVe.setItems(FXCollections.observableArrayList());
  }

  private void handleSearch() {
    String query = txtSearchCCCD != null ? txtSearchCCCD.getText() : "";
    query = query == null ? "" : query.trim();

    // Cho phép rỗng để reload toàn bộ vé PAID/có thể xét trả.
    lastSearchIdCard = query;
    doSearchAsync(query);
  }

  private void doSearchAsync(String query) {
    final long seq = searchSeq.incrementAndGet();
    setBusy(true);
    setInlineError(null);
    resetDetail();
    resetPreview();

    Task<Response> task = new Task<>() {
      @Override
      protected Response call() {
        return returnTicketClientService.searchTicketsForReturn(query);
      }
    };

    task.setOnSucceeded(e -> {
      if (seq != searchSeq.get())
        return;
      setBusy(false);
      Response res = task.getValue();
      if (res != null && res.isSuccess() && res.getData() instanceof List<?> list) {
        List<ReturnTicketTicketDTO> items = new ArrayList<>();
        for (Object o : list) {
          if (o instanceof ReturnTicketTicketDTO dto) {
            items.add(dto);
          }
        }
        tblDanhSachVe.getItems().setAll(items);
        if (items.isEmpty()) {
          setInlineError(query == null || query.isBlank()
              ? "Không có vé nào đang có thể trả."
              : "Không tìm thấy vé phù hợp với thông tin đã nhập.");
        }
      } else {
        setInlineError(res == null ? "Không nhận được phản hồi." : res.getMessage());
      }
    });

    task.setOnFailed(e -> {
      if (seq != searchSeq.get())
        return;
      setBusy(false);
      setInlineError("Lỗi khi tra cứu vé.");
    });

    start(task, "return-ticket-search");
  }

  private void handleSelectionChanged() {
    List<ReturnTicketTicketDTO> selected = getSelectedTickets();
    renderDetail(selected);
    startOrStopCountdown(selected);

    if (selected.isEmpty()) {
      resetPreview();
      return;
    }

    if (mainController != null) {
      return;
    }

    doPreviewAsync(selected.stream().map(ReturnTicketTicketDTO::getId).toList(), selected);
  }

  private void doPreviewAsync(List<String> ticketIds, List<ReturnTicketTicketDTO> selectionSnapshot) {
    final long seq = previewSeq.incrementAndGet();
    setBusy(true);
    setInlineError(null);
    btnXacNhanTra.setDisable(true);

    Task<Response> task = new Task<>() {
      @Override
      protected Response call() {
        return returnTicketClientService.previewReturnTickets(ticketIds);
      }
    };

    task.setOnSucceeded(e -> {
      if (seq != previewSeq.get())
        return;
      setBusy(false);
      Response res = task.getValue();
      if (res != null && res.isSuccess() && res.getData() instanceof ReturnTicketPreviewDTO dto) {
        lastPreview = dto;
        lblGiaVeGoc.setText(formatMoney(dto.getTotalTicketPrice()));
        lblPhiTraVe.setText(formatMoney(dto.getRefundFee()));
        lblTienHoanLai.setText(formatMoney(dto.getRefundAmount()));
        lblDieuKienVe.setText(buildConditionText(selectionSnapshot));
        btnXacNhanTra.setDisable(false);
      } else {
        lastPreview = null;
        resetPreviewAmounts();
        setInlineError(res == null ? "Không nhận được phản hồi." : res.getMessage());
      }
    });

    task.setOnFailed(e -> {
      if (seq != previewSeq.get())
        return;
      setBusy(false);
      lastPreview = null;
      resetPreviewAmounts();
      setInlineError("Lỗi khi tính toán hoàn tiền.");
    });

    start(task, "return-ticket-preview");
  }

  private void handleConfirmReturn() {
    List<String> ticketIds = getSelectedTickets().stream().map(ReturnTicketTicketDTO::getId).toList();
    if (ticketIds.isEmpty()) {
      showWarning("Trả vé", "Vui lòng chọn ít nhất 1 vé.");
      return;
    }
    if (lastPreview == null) {
      showWarning("Trả vé", "Vui lòng tính toán hoàn tiền trước khi xác nhận.");
      return;
    }
    String employeeId = vn.edu.iuh.fit.client.service.SessionManager.getInstance().getEmployeeId();
    if (employeeId == null || employeeId.isBlank()) {
      showError("Trả vé", "Không xác định được nhân viên đang đăng nhập.");
      return;
    }

    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
    confirm.setTitle("Trả vé");
    confirm.setHeaderText(null);
    confirm.setContentText(
        "Xác nhận trả " + ticketIds.size() + " vé?\nSố tiền hoàn: " + formatMoney(lastPreview.getRefundAmount()));
    if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK)
      return;

    doConfirmAsync(ticketIds, lastPreview.getRefundAmount(), employeeId);
  }

  private void doConfirmAsync(List<String> ticketIds, double refundAmount, String employeeId) {
    final long seq = confirmSeq.incrementAndGet();
    setBusy(true);
    setInlineError(null);
    btnXacNhanTra.setDisable(true);

    Task<Response> task = new Task<>() {
      @Override
      protected Response call() {
        return returnTicketClientService.confirmReturnTickets(ticketIds, refundAmount, employeeId);
      }
    };

    task.setOnSucceeded(e -> {
      if (seq != confirmSeq.get()) {
        return;
      }

      setBusy(false);
      Response res = task.getValue();

      if (res == null || !res.isSuccess()) {
        btnXacNhanTra.setDisable(false);
        setInlineError(res == null ? "Không nhận được phản hồi." : res.getMessage());
        return;
      }

      String refundInvoiceId = res.getData() != null ? String.valueOf(res.getData()) : null;

      // 1. Xóa vé vừa trả khỏi bảng và ép giao diện cập nhật ngay lập tức
      if (tblDanhSachVe != null && tblDanhSachVe.getItems() != null) {
        tblDanhSachVe.getItems().removeIf(t -> t != null && ticketIds.contains(t.getId()));
        tblDanhSachVe.getSelectionModel().clearSelection();
        tblDanhSachVe.refresh();
      }

      // 2. Reset toàn bộ panel detail và preview
      resetDetail();
      resetPreview();
      lastPreview = null;
      btnXacNhanTra.setDisable(true);
      setInlineError(null);

      // 3. Clear ô search và kích hoạt ngầm luồng gọi danh sách vé mới
      // (Việc này chạy bất đồng bộ nên không lo bị đơ màn hình)
      if (txtSearchCCCD != null) {
        txtSearchCCCD.clear();
      }
      lastSearchIdCard = "";
      doSearchAsync("");

      // 4. HIỆN DIALOG THÀNH CÔNG VÀ HỎI IN BIÊN LAI (Nó sẽ block UI ở bước này)
      Alert done = new Alert(Alert.AlertType.CONFIRMATION);
      done.setTitle("Trả vé thành công");
      done.setHeaderText(null);
      done.setContentText(
          "Trả vé thành công.\n"
              + "Số tiền hoàn: " + formatMoney(refundAmount) + "\n\n"
              + "Bạn có muốn in biên lai hoàn tiền không?");

      ButtonType btnPrint = new ButtonType("In biên lai");
      ButtonType btnSkip = new ButtonType("Không in", ButtonBar.ButtonData.CANCEL_CLOSE);
      done.getButtonTypes().setAll(btnPrint, btnSkip);

      ButtonType choice = done.showAndWait().orElse(btnSkip);

      if (choice == btnPrint) {
        if (refundInvoiceId == null || refundInvoiceId.isBlank()) {
          showError("In biên lai", "Trả vé thành công nhưng server không trả mã biên lai hoàn tiền.");
        } else {
          doLoadAndPreviewRefundReceipt(refundInvoiceId); // Gọi hàm in biên lai
        }
      }
    });

    task.setOnFailed(e -> {
      if (seq != confirmSeq.get())
        return;
      setBusy(false);
      btnXacNhanTra.setDisable(false);
      setInlineError("Lỗi khi xác nhận trả vé.");
    });

    start(task, "return-ticket-confirm");
  }

  private void doLoadAndPreviewRefundReceipt(String invoiceId) {
    if (invoiceId == null || invoiceId.isBlank()) {
      showError("In biên lai", "Không tìm thấy mã biên lai hoàn tiền.");
      return;
    }

    setBusy(true);

    Task<Response> task = new Task<>() {
      @Override
      protected Response call() {
        return returnTicketClientService.getRefundReceipt(invoiceId);
      }
    };

    task.setOnSucceeded(e -> {
      setBusy(false);
      Response res = task.getValue();

      if (res == null || !res.isSuccess()) {
        showError("In biên lai", res == null ? "Không nhận được phản hồi." : res.getMessage());
        return;
      }

      if (!(res.getData() instanceof RefundReceiptDTO dto)) {
        showError("In biên lai", "Dữ liệu biên lai trả về không hợp lệ.");
        return;
      }

      JasperPrint jasperPrint = createRefundReceiptReport(dto);
      if (jasperPrint == null) {
        showError("In biên lai", "Không thể tạo biên lai hoàn tiền.");
        return;
      }

      showRefundReceiptPreview(jasperPrint);
    });

    task.setOnFailed(e -> {
      setBusy(false);
      Throwable ex = task.getException();
      showError("In biên lai", "Không thể lấy dữ liệu biên lai: " + (ex == null ? "" : ex.getMessage()));
    });

    start(task, "return-ticket-load-refund-receipt");
  }

  private JasperPrint createRefundReceiptReport(RefundReceiptDTO dto) {
    try {
      InputStream reportStream = getClass().getResourceAsStream("/client/print/bien-lai-tra-ve.xml");
      if (reportStream == null) {
        showError("In biên lai", "Không tìm thấy template: /client/print/bien-lai-tra-ve.xml");
        return null;
      }

      JasperDesign jasperDesign = JRXmlLoader.load(reportStream);
      JasperReport jasperReport = JasperCompileManager.compileReport(jasperDesign);

      List<RefundReceiptItemDTO> items = dto.getItems();
      if (items == null || items.isEmpty()) {
        Map<String, Object> parameters = buildRefundReceiptParams(dto, null);
        return JasperFillManager.fillReport(jasperReport, parameters, new JREmptyDataSource());
      }

      JasperPrint merged = null;
      for (RefundReceiptItemDTO item : items) {
        Map<String, Object> parameters = buildRefundReceiptParams(dto, item);
        JasperPrint jp = JasperFillManager.fillReport(jasperReport, parameters, new JREmptyDataSource());
        if (merged == null) {
          merged = jp;
        } else {
          merged.getPages().addAll(jp.getPages());
        }
      }
      return merged;
    } catch (JRException e) {
      e.printStackTrace();
      showError("In biên lai", "Lỗi tạo biên lai: " + e.getMessage());
      return null;
    }
  }

  private Map<String, Object> buildRefundReceiptParams(RefundReceiptDTO dto, RefundReceiptItemDTO item) {
    Map<String, Object> parameters = new HashMap<>();
    parameters.put("p_MaGiaoDich", safe(dto.getTransactionCode()));
    parameters.put("p_NgayTra", dto.getRefundDate() == null ? "--" : DATE_TIME.format(dto.getRefundDate()));
    parameters.put("p_NhanVien", safe(dto.getEmployeeName()));

    if (item == null) {
      parameters.put("p_MaVe", safe(dto.getTicketId()));
      parameters.put("p_KhachHang", safe(dto.getCustomerName()));
      parameters.put("p_SoGiayTo", safe(dto.getCustomerDocument()));

      parameters.put("p_Tau", safe(dto.getTrainCode()));
      parameters.put("p_GaDi", safe(dto.getDepartureStation()));
      parameters.put("p_GaDen", safe(dto.getDestinationStation()));
      parameters.put("p_NgayDi", dto.getDepartureTime() == null ? "--" : DATE_TIME.format(dto.getDepartureTime()));

      parameters.put("p_Toa", safe(dto.getCarriageName()));
      parameters.put("p_Ghe", safe(dto.getSeatNumber()));

      parameters.put("p_GiaVeGoc", dto.getOriginalAmount());
      parameters.put("p_LePhi", dto.getRefundFee());
      parameters.put("p_ThucNhan", dto.getRefundAmount());
      return parameters;
    }

    parameters.put("p_MaVe", safe(item.getTicketId()));
    parameters.put("p_KhachHang", safe(item.getPassengerName() == null || item.getPassengerName().isBlank()
        ? dto.getCustomerName()
        : item.getPassengerName()));
    parameters.put("p_SoGiayTo", safe(item.getPassengerDocument() == null || item.getPassengerDocument().isBlank()
        ? dto.getCustomerDocument()
        : item.getPassengerDocument()));

    parameters.put("p_Tau", safe(item.getTrainCode()));
    parameters.put("p_GaDi", safe(item.getDepartureStation()));
    parameters.put("p_GaDen", safe(item.getDestinationStation()));
    parameters.put("p_NgayDi", item.getDepartureTime() == null ? "--" : DATE_TIME.format(item.getDepartureTime()));

    parameters.put("p_Toa", safe(item.getCarriageName()));
    parameters.put("p_Ghe", safe(item.getSeatNumber()));

    parameters.put("p_GiaVeGoc", item.getOriginalAmount());
    parameters.put("p_LePhi", item.getRefundFee());
    parameters.put("p_ThucNhan", item.getRefundAmount());
    return parameters;
  }

  private void showRefundReceiptPreview(JasperPrint jasperPrint) {
    try {
      Dialog<ButtonType> dialog = new Dialog<>();
      dialog.setTitle("Xem trước biên lai hoàn tiền");
      dialog.setHeaderText("Kiểm tra biên lai trước khi in.");

      VBox pagesBox = new VBox(10);
      pagesBox.setPadding(new Insets(10));
      pagesBox.setStyle("-fx-alignment: center; -fx-background-color: #eeeeee;");

      int pageCount = jasperPrint.getPages() == null ? 0 : jasperPrint.getPages().size();
      for (int i = 0; i < Math.max(1, pageCount); i++) {
        BufferedImage bufferedImage = (BufferedImage) JasperPrintManager.printPageToImage(jasperPrint, i, 1.45f);
        Image fxImage = SwingFXUtils.toFXImage(bufferedImage, null);

        ImageView imageView = new ImageView(fxImage);
        imageView.setPreserveRatio(true);
        imageView.setFitWidth(520);
        pagesBox.getChildren().add(imageView);
      }

      ScrollPane scrollPane = new ScrollPane(pagesBox);
      scrollPane.setFitToWidth(true);
      scrollPane.setPrefViewportHeight(650);

      VBox content = new VBox(scrollPane);
      content.setPadding(new Insets(10));
      dialog.getDialogPane().setContent(content);

      ButtonType btnTypePrint = new ButtonType("In biên lai", ButtonData.OTHER);
      ButtonType btnTypeClose = new ButtonType("Đóng", ButtonData.OK_DONE);
      dialog.getDialogPane().getButtonTypes().addAll(btnTypePrint, btnTypeClose);

      Button btnPrint = (Button) dialog.getDialogPane().lookupButton(btnTypePrint);
      btnPrint.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
        event.consume();
        try {
          JasperPrintManager.printReport(jasperPrint, true);
          showInfo("In biên lai", "Đã gửi lệnh in.");
        } catch (JRException e) {
          showError("In biên lai", "Lỗi máy in: " + e.getMessage());
        }
      });

      dialog.showAndWait();
    } catch (JRException e) {
      e.printStackTrace();
      showError("In biên lai", "Không thể hiển thị preview biên lai: " + e.getMessage());
    }
  }

  private void renderDetail(List<ReturnTicketTicketDTO> selected) {
    if (selected == null || selected.isEmpty()) {
      resetDetail();
      return;
    }
    if (selected.size() == 1) {
      ReturnTicketTicketDTO dto = selected.get(0);
      lblMaVeChon.setText(safe(dto.getId()));
      lblTau.setText(safe(dto.getTrainCode()));
      lblHanhTrinh.setText(safe(dto.getDepartureStation()) + " → " + safe(dto.getDestinationStation()));
      lblNgayDi.setText(dto.getDepartureTime() == null ? "--" : DATE_TIME.format(dto.getDepartureTime()));
      lblGhe.setText("Toa " + safe(dto.getCarriageName()) + " - Ghế " + safe(dto.getSeatNumber()));
      return;
    }
    lblMaVeChon.setText(selected.size() + " vé");
    lblTau.setText("Nhiều vé");
    lblHanhTrinh.setText("Nhiều hành trình");
    lblNgayDi.setText("--");
    lblGhe.setText("--");
  }

  private void startOrStopCountdown(List<ReturnTicketTicketDTO> selected) {
    stopCountdown();
    if (selected == null || selected.isEmpty()) {
      lblThoiGianConLai.setText("--");
      return;
    }
    LocalDateTime nearest = selected.stream()
        .map(ReturnTicketTicketDTO::getDepartureTime)
        .filter(d -> d != null)
        .min(Comparator.naturalOrder())
        .orElse(null);
    if (nearest == null) {
      lblThoiGianConLai.setText("--");
      return;
    }
    updateCountdownLabel(nearest);
    countdownTimeline = new Timeline(new KeyFrame(javafx.util.Duration.seconds(1), e -> updateCountdownLabel(nearest)));
    countdownTimeline.setCycleCount(Timeline.INDEFINITE);
    countdownTimeline.play();
  }

  private void updateCountdownLabel(LocalDateTime departureTime) {
    if (departureTime == null) {
      lblThoiGianConLai.setText("--");
      return;
    }
    long minutes = Duration.between(LocalDateTime.now(), departureTime).toMinutes();
    if (minutes <= 0) {
      lblThoiGianConLai.setText("Đã khởi hành");
      return;
    }
    long hours = minutes / 60;
    long mins = minutes % 60;
    lblThoiGianConLai.setText(hours + "h " + mins + "p");
  }

  private void stopCountdown() {
    if (countdownTimeline != null) {
      countdownTimeline.stop();
      countdownTimeline = null;
    }
  }

  private String buildConditionText(List<ReturnTicketTicketDTO> selected) {
    if (selected == null || selected.isEmpty())
      return "--";
    boolean exchanged = selected.stream()
        .anyMatch(t -> t != null && t.getOriginalTicketId() != null && !t.getOriginalTicketId().isBlank());
    if (exchanged) {
      return "Vé đã đổi: phí 30%";
    }
    LocalDateTime nearest = selected.stream()
        .map(ReturnTicketTicketDTO::getDepartureTime)
        .filter(d -> d != null)
        .min(Comparator.naturalOrder())
        .orElse(null);
    if (nearest == null)
      return "--";
    long minutes = Duration.between(LocalDateTime.now(), nearest).toMinutes();
    return minutes < MINUTES_24H ? "Phí 20% (dưới 24h)" : "Phí 10% (từ 24h)";
  }

  private List<ReturnTicketTicketDTO> getSelectedTickets() {
    if (tblDanhSachVe == null)
      return List.of();
    return List.copyOf(tblDanhSachVe.getSelectionModel().getSelectedItems());
  }

  private void resetDetail() {
    lblMaVeChon.setText("--");
    lblTau.setText("--");
    lblHanhTrinh.setText("--");
    lblNgayDi.setText("--");
    lblGhe.setText("--");
    lblThoiGianConLai.setText("--");
    stopCountdown();
  }

  private void resetPreview() {
    lastPreview = null;
    lblDieuKienVe.setText("--");
    resetPreviewAmounts();
    btnXacNhanTra.setDisable(true);
  }

  private void resetPreviewAmounts() {
    lblGiaVeGoc.setText(formatMoney(0));
    lblPhiTraVe.setText(formatMoney(0));
    lblTienHoanLai.setText(formatMoney(0));
  }

  private void setInlineError(String message) {
    if (lblThongBaoLoi == null)
      return;
    lblThongBaoLoi.setText(message == null ? "" : message);
  }

  private void setBusy(boolean busy) {
    if (btnTimKiem != null)
      btnTimKiem.setDisable(busy);
    if (txtSearchCCCD != null)
      txtSearchCCCD.setDisable(busy);
  }

  private void registerCloseCleanupHook() {
    if (tblDanhSachVe == null)
      return;
    tblDanhSachVe.sceneProperty().addListener((obs, oldScene, newScene) -> {
      if (oldScene != null && newScene == null) {
        cleanupOnClose();
        return;
      }
      if (newScene == null)
        return;

      Window existing = newScene.getWindow();
      if (existing != null) {
        existing.addEventHandler(WindowEvent.WINDOW_HIDDEN, e -> cleanupOnClose());
      }

      newScene.windowProperty().addListener((obsWin, oldWin, newWin) -> {
        if (newWin == null)
          return;
        newWin.addEventHandler(WindowEvent.WINDOW_HIDDEN, e -> cleanupOnClose());
      });
    });
  }

  private void cleanupOnClose() {
    if (!cleanupDone.compareAndSet(false, true))
      return;
    stopCountdown();
    executor.shutdown();
    if (tblDanhSachVe != null) {
      tblDanhSachVe.getItems().clear();
    }
  }

  private void start(Task<?> task, String name) {
    executor.execute(() -> {
      String oldName = Thread.currentThread().getName();
      Thread.currentThread().setName(name + "-" + UUID.randomUUID());
      try {
        task.run();
      } finally {
        Thread.currentThread().setName(oldName);
      }
    });
  }

  private static javafx.beans.property.SimpleStringProperty s(String v) {
    return new javafx.beans.property.SimpleStringProperty(v == null ? "" : v);
  }

  private static String normalize(String value) {
    if (value == null)
      return null;
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private static String safe(String v) {
    return v == null || v.isBlank() ? "--" : v;
  }

  private static String formatMoney(double v) {
    return MONEY.format(Math.round(v)) + " đ";
  }

  private static String normalizeStationName(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    if (trimmed.isEmpty()) {
      return null;
    }
    String lowered = trimmed.toLowerCase();
    return Normalizer.normalize(lowered, Normalizer.Form.NFC);
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
