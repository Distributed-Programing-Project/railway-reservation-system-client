package vn.edu.iuh.fit.client.controller;

import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import vn.edu.iuh.fit.client.service.ExchangeTicketClientService;
import vn.edu.iuh.fit.client.service.SaleClientService;
import vn.edu.iuh.fit.client.session.SaleWizardState;
import vn.edu.iuh.fit.client.session.SaleWizardState.BuyerDraft;
import vn.edu.iuh.fit.client.session.SaleWizardState.PassengerDraft;
import vn.edu.iuh.fit.client.session.SaleWizardState.SelectedSeatDraft;
import vn.edu.iuh.fit.common.constant.PaymentMethod;
import vn.edu.iuh.fit.common.constant.TicketCategory;
import vn.edu.iuh.fit.common.constant.TicketType;
import vn.edu.iuh.fit.common.constant.TripDirection;
import vn.edu.iuh.fit.common.dto.ExchangeTicketRequestDTO;
import vn.edu.iuh.fit.common.dto.ExchangeTicketResponseDTO;
import vn.edu.iuh.fit.common.dto.IssuedTicketDTO;
import vn.edu.iuh.fit.common.dto.SaleBuyerDTO;
import vn.edu.iuh.fit.common.dto.SaleChildUnder6DTO;
import vn.edu.iuh.fit.common.dto.SaleCreateRequestDTO;
import vn.edu.iuh.fit.common.dto.SaleCreateResponseDTO;
import vn.edu.iuh.fit.common.dto.SalePassengerDTO;
import vn.edu.iuh.fit.common.dto.SaleRedeemPointsDTO;
import vn.edu.iuh.fit.common.dto.ScheduleSaleCardDTO;
import vn.edu.iuh.fit.common.response.Response;

public class Step4PaymentController {
  private static final double INSURANCE_FEE = 2000d;
  private static final double EXCHANGE_FEE_PER_TICKET = 20_000d;
  private static final int VND_PER_POINT = 1000;
  private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
  private static final NumberFormat MONEY = NumberFormat.getInstance(new Locale("vi", "VN"));

  @FXML
  private VBox mainContainer;

  @FXML
  private VBox leftSection;

  @FXML
  private HBox ticketHeaderRow;

  @FXML
  private Label headerChuyenTau;

  @FXML
  private Label headerToaCho;

  @FXML
  private Label headerHanhKhach;

  @FXML
  private Label headerLoaiVe;

  @FXML
  private Label headerDonGia;

  @FXML
  private ScrollPane scrollPaneVe;

  @FXML
  private VBox containerVe;

  @FXML
  private GridPane gridPaymentDetails;

  @FXML
  private Label lblTitleTongTien;

  @FXML
  private Label lblDetailTongTienVe;

  @FXML
  private Label lblTitleGiamDoiTuong;

  @FXML
  private Label lblDetailGiamDoiTuong;

  @FXML
  private Label lblDetailGiamDiem;

  @FXML
  private Label lblTitleBaoHiem;

  @FXML
  private Label lblDetailBaoHiem;

  @FXML
  private Label lblDetailTongThanhToan;

  @FXML
  private Button btnXuatHoaDon;

  @FXML
  private Button btnDoiDiem;

  @FXML
  private Button btnTichDiem;

  @FXML
  private VBox rightSection;

  @FXML
  private VBox calculatorBox;

  @FXML
  private Label lblDisplayTongThanhToan;

  @FXML
  private TextField txtTienKhachDua;

  @FXML
  private FlowPane flowPaneSuggestions;

  @FXML
  private Label lblTienThoiLai;

  @FXML
  private Button btnXacNhanVaIn;

  @FXML
  private Button btnHoanTat;

  @FXML
  private Button btnQuayLai;

  private BanVeController coordinator;
  private final SaleClientService saleClientService = new SaleClientService();
  private final ExchangeTicketClientService exchangeTicketClientService = new ExchangeTicketClientService();
  private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
    Thread t = new Thread(r, "step4-payment-worker");
    t.setDaemon(true);
    return t;
  });

  private double previewTotalToPay;
  private int pointsToRedeem;
  private boolean pointsApplied;
  private boolean submitting;
  private SaleCreateResponseDTO lastSaleResult;
  private ExchangeTicketResponseDTO lastExchangeResult;

  public void setCoordinator(BanVeController coordinator) {
    this.coordinator = coordinator;
  }

  public void initData() {
    refreshFromState();
  }

  @FXML
  public void initialize() {
    setupMoneyInput();
  }

  @FXML
  private void handleXuatHoaDon() {
    if (coordinator != null && coordinator.getState().isExchangeMode()) {
      if (lastExchangeResult == null || lastExchangeResult.getInvoiceId() == null
          || lastExchangeResult.getInvoiceId().isBlank()) {
        showAlert(Alert.AlertType.WARNING, "Hóa đơn", "Chưa có giao dịch đổi vé nào được hoàn tất để xuất hóa đơn.");
        return;
      }
      openExchangeReceiptPreview("In biên lai đổi vé");
      return;
    }
    if (lastSaleResult == null || lastSaleResult.getInvoiceId() == null || lastSaleResult.getInvoiceId().isBlank()) {
      showAlert(Alert.AlertType.WARNING, "Hóa đơn", "Chưa có giao dịch nào được hoàn tất để xuất hóa đơn.");
      return;
    }
    openInvoicePreviewSummary();
  }

  @FXML
  private void handleDoiDiem() {
    toggleRedeemPoints();
  }

  @FXML
  private void handleTichDiem() {
    if (previewTotalToPay <= 0) {
      showAlert(Alert.AlertType.INFORMATION, "Tích điểm", "Chưa có dữ liệu thanh toán.");
      return;
    }
    int earned = (int) Math.floor(previewTotalToPay / 10000d);
    showAlert(Alert.AlertType.INFORMATION, "Tích điểm", "Dự kiến tích: " + earned + " điểm.");
  }

  @FXML
  private void handleXacNhanVaIn() {
    if (lastExchangeResult != null) {
      openPrintListDialog(lastExchangeResult.getNewTickets());
      return;
    }
    if (lastSaleResult != null) {
      openPrintListDialog(lastSaleResult.getTickets());
      return;
    }
    submitCashSaleAndPrintTickets();
  }

  @FXML
  private void handleHoanTat() {
    if (coordinator == null) {
      return;
    }
    coordinator.resetAfterSaleSuccess();
  }

  @FXML
  private void handleQuayLai() {
    if (coordinator != null) {
      coordinator.backFromStep4();
      return;
    }
    showAlert(Alert.AlertType.WARNING, "Điều hướng", "Không tìm thấy coordinator.");
  }

  private void refreshFromState() {
    if (coordinator == null) {
      return;
    }
    SaleWizardState state = coordinator.getState();

    if (state != null && state.isExchangeMode()) {
      lblTitleTongTien.setText("Price Diff");
      lblTitleGiamDoiTuong.setText("Exchange Fee");
      lblTitleBaoHiem.setText("Old Ticket Total");
      btnDoiDiem.setDisable(true);
      btnTichDiem.setDisable(true);
    } else {
      // Defaults from FXML are used for normal sale flow.
      lblTitleTongTien.setText("Tổng tiền vé");
      lblTitleGiamDoiTuong.setText("Giảm đối tượng");
      lblTitleBaoHiem.setText("Bảo hiểm");
    }

    if (lastSaleResult == null && lastExchangeResult == null) {
      btnHoanTat.setVisible(false);
      btnHoanTat.setManaged(false);
      btnXacNhanVaIn.setVisible(true);
      btnXacNhanVaIn.setManaged(true);
      btnXacNhanVaIn.setDisable(true);
      btnQuayLai.setDisable(false);
      btnXuatHoaDon.setDisable(true);
    }

    headerChuyenTau.setPrefWidth(180);
    headerToaCho.setPrefWidth(150);
    headerLoaiVe.setPrefWidth(150);
    headerDonGia.setPrefWidth(120);

    renderTicketSummary(state);
    computeAndRenderTotals(state);
    generateSuggestionButtons();
    calculateChange();

    if (lastSaleResult != null) {
      applySaleSuccessUi(lastSaleResult, false);
    } else if (lastExchangeResult != null) {
      applyExchangeSuccessUi(lastExchangeResult, false);
    }
  }

  private void setupMoneyInput() {
    if (txtTienKhachDua == null) {
      return;
    }
    txtTienKhachDua.textProperty().addListener((obs, oldVal, newVal) -> {
      if (newVal == null) {
        return;
      }
      if (!newVal.matches("\\d*")) {
        txtTienKhachDua.setText(oldVal);
        return;
      }
      if (newVal.length() > 10) {
        txtTienKhachDua.setText(oldVal);
        return;
      }
      calculateChange();
    });
  }

  private void renderTicketSummary(SaleWizardState state) {
    containerVe.getChildren().clear();
    if (state == null || state.getPassengers() == null || state.getPassengers().isEmpty()) {
      containerVe.getChildren().add(new Label("Chưa có dữ liệu hành khách. Vui lòng quay lại bước trước."));
      return;
    }

    List<Node> rows = new ArrayList<>();
    ScheduleSaleCardDTO outbound = state.getSelectedOutboundSchedule();
    ScheduleSaleCardDTO ret = state.getSelectedReturnSchedule();
    boolean roundTrip = state.getTicketCategory() == TicketCategory.ROUND_TRIP && ret != null;

    for (PassengerDraft p : state.getPassengers()) {
      if (p == null) {
        continue;
      }
      if (p.isHasSeat()) {
        if (p.getOutboundSeat() != null) {
          rows.add(createTicketRow(outbound, p.getOutboundSeat(), p, TripDirection.OUTBOUND));
        }
        if (roundTrip && p.getReturnSeat() != null) {
          rows.add(createTicketRow(ret, p.getReturnSeat(), p, TripDirection.RETURN));
        }
      } else if (p.isChildUnder6()) {
        rows.add(createChildUnder6Row(outbound, p));
      }
    }

    if (rows.isEmpty()) {
      containerVe.getChildren().add(new Label("Không có vé nào để thanh toán."));
      return;
    }

    containerVe.getChildren().addAll(rows);
  }

  private Node createTicketRow(ScheduleSaleCardDTO schedule, SelectedSeatDraft seat, PassengerDraft passenger,
      TripDirection dir) {
    HBox row = new HBox(10.0);
    row.setAlignment(Pos.CENTER_LEFT);
    row.setStyle("-fx-padding: 8px 0; -fx-border-color: #eee; -fx-border-width: 0 0 1px 0;");

    VBox col1 = new VBox(2);
    col1.setPrefWidth(headerChuyenTau.getPrefWidth());
    String train = schedule == null ? "--" : safe(schedule.getTrainCode());
    LocalDateTime dt = schedule == null ? null : schedule.getDepartureTime();
    col1.getChildren().addAll(
        new Label(labelForDirection(dir) + ": Tàu " + train),
        new Label(dt == null ? "--" : dt.format(DATE_TIME)) {
          {
            setStyle("-fx-font-size: 11px;");
          }
        });

    String carriage = seat != null && seat.getCarriageNumber() != null ? String.valueOf(seat.getCarriageNumber())
        : "--";
    String seatNo = seat != null && seat.getSeatNumber() != null ? String.valueOf(seat.getSeatNumber()) : "--";
    Label col2 = new Label("Toa " + carriage + " - Ghế " + seatNo);
    col2.setPrefWidth(headerToaCho.getPrefWidth());

    VBox col3 = new VBox(2);
    HBox.setHgrow(col3, Priority.ALWAYS);
    col3.getChildren().addAll(
        new Label(safe(passenger.getFullName())),
        new Label("ID: " + safe(passenger.getDocumentNumber())) {
          {
            setStyle("-fx-font-size: 11px;");
          }
        });

    Label col4 = new Label(ticketTypeVi(passenger.getTicketType()));
    col4.setPrefWidth(headerLoaiVe.getPrefWidth());

    double base = seat != null && seat.getPrice() != null ? seat.getPrice() : 0d;
    double discountRate = discountRate(passenger.getTicketType());
    double unit = (base * (1 - discountRate)) + INSURANCE_FEE;

    Label col5 = new Label(formatMoney(unit));
    col5.setPrefWidth(headerDonGia.getPrefWidth());
    col5.setAlignment(Pos.CENTER_RIGHT);
    col5.setMaxWidth(Double.MAX_VALUE);

    row.getChildren().addAll(col1, col2, col3, col4, col5);
    return row;
  }

  private Node createChildUnder6Row(ScheduleSaleCardDTO outbound, PassengerDraft passenger) {
    HBox row = new HBox(10.0);
    row.setAlignment(Pos.CENTER_LEFT);
    row.setStyle("-fx-padding: 8px 0; -fx-border-color: #eee; -fx-border-width: 0 0 1px 0;");

    VBox col1 = new VBox(2);
    col1.setPrefWidth(headerChuyenTau.getPrefWidth());
    String train = outbound == null ? "--" : safe(outbound.getTrainCode());
    LocalDateTime dt = outbound == null ? null : outbound.getDepartureTime();
    col1.getChildren().addAll(
        new Label("Trẻ <6 (không ghế) - Tàu " + train),
        new Label(dt == null ? "--" : dt.format(DATE_TIME)) {
          {
            setStyle("-fx-font-size: 11px;");
          }
        });

    Label col2 = new Label("Không chiếm ghế");
    col2.setPrefWidth(headerToaCho.getPrefWidth());

    VBox col3 = new VBox(2);
    HBox.setHgrow(col3, Priority.ALWAYS);
    col3.getChildren().addAll(
        new Label(safe(passenger.getFullName())),
        new Label("DOB: " + (passenger.getDateOfBirth() == null ? "--" : passenger.getDateOfBirth())) {
          {
            setStyle("-fx-font-size: 11px;");
          }
        });

    Label col4 = new Label("Miễn vé");
    col4.setPrefWidth(headerLoaiVe.getPrefWidth());

    Label col5 = new Label(formatMoney(0));
    col5.setPrefWidth(headerDonGia.getPrefWidth());
    col5.setAlignment(Pos.CENTER_RIGHT);
    col5.setMaxWidth(Double.MAX_VALUE);

    row.getChildren().addAll(col1, col2, col3, col4, col5);
    return row;
  }

  private void computeAndRenderTotals(SaleWizardState state) {
    if (state != null && state.isExchangeMode()) {
      computeAndRenderExchangeTotals(state);
      return;
    }
    previewTotalToPay = 0;
    if (state == null || state.getPassengers() == null) {
      updateTotalLabels(0, 0, 0, 0);
      return;
    }

    ScheduleSaleCardDTO ret = state.getSelectedReturnSchedule();
    boolean roundTrip = state.getTicketCategory() == TicketCategory.ROUND_TRIP && ret != null;

    double tongTienVeGoc = 0;
    double tongGiamDoiTuong = 0;
    double tongBaoHiem = 0;

    for (PassengerDraft p : state.getPassengers()) {
      if (p == null || !p.isHasSeat()) {
        continue;
      }
      SelectedSeatDraft o = p.getOutboundSeat();
      if (o != null) {
        double base = o.getPrice() == null ? 0d : o.getPrice();
        tongTienVeGoc += base;
        tongGiamDoiTuong += base * discountRate(p.getTicketType());
        tongBaoHiem += INSURANCE_FEE;
      }
      if (roundTrip) {
        SelectedSeatDraft r = p.getReturnSeat();
        if (r != null) {
          double base = r.getPrice() == null ? 0d : r.getPrice();
          tongTienVeGoc += base;
          tongGiamDoiTuong += base * discountRate(p.getTicketType());
          tongBaoHiem += INSURANCE_FEE;
        }
      }
    }

    double subtotal = tongTienVeGoc - tongGiamDoiTuong + tongBaoHiem;
    double redeemMoney = pointsApplied && pointsToRedeem > 0 ? pointsToRedeem * (double) VND_PER_POINT : 0;
    double afterPoints = subtotal - redeemMoney;
    if (afterPoints < 0) {
      afterPoints = 0;
    }
    previewTotalToPay = roundUpToThousand(afterPoints);

    updateTotalLabels(tongTienVeGoc, tongGiamDoiTuong, redeemMoney, tongBaoHiem);
    lblDetailTongThanhToan.setText(formatMoney(previewTotalToPay));
    lblDisplayTongThanhToan.setText(formatMoney(previewTotalToPay));

    refreshPointsButtonsState(state, subtotal);
  }

  private void computeAndRenderExchangeTotals(SaleWizardState state) {
    previewTotalToPay = 0;
    pointsApplied = false;
    pointsToRedeem = 0;
    lblDetailGiamDiem.setText(formatMoney(0));

    if (state == null || state.getPassengers() == null) {
      updateTotalLabels(0, 0, 0, 0);
      return;
    }
    List<PassengerDraft> seatPassengers = state.getPassengers().stream()
        .filter(Objects::nonNull)
        .filter(PassengerDraft::isHasSeat)
        .toList();
    int ticketCount = seatPassengers.size();
    if (ticketCount <= 0) {
      updateTotalLabels(0, 0, 0, 0);
      return;
    }

    double totalNewPrice = 0d;
    for (PassengerDraft p : seatPassengers) {
      SelectedSeatDraft o = p.getOutboundSeat();
      if (o != null && o.getPrice() != null) {
        totalNewPrice += o.getPrice();
      }
    }

    double totalOldPrice = 0d;
    if (state.getExchangeOldTickets() != null) {
      for (int i = 0; i < Math.min(ticketCount, state.getExchangeOldTickets().size()); i++) {
        var t = state.getExchangeOldTickets().get(i);
        if (t != null) {
          totalOldPrice += t.getTicketPrice();
        }
      }
    }

    double feeTotal = EXCHANGE_FEE_PER_TICKET * ticketCount;
    double diff = totalNewPrice - totalOldPrice;
    double priceDiffToPay = Math.max(0d, diff);
    previewTotalToPay = priceDiffToPay + feeTotal;

    // Re-map detail labels for exchange mode:
    // - "Chênh lệch giá" => priceDiffToPay
    // - "Phí đổi vé" => feeTotal
    // - "Giá vé cũ" => totalOldPrice
    updateTotalLabels(priceDiffToPay, feeTotal, 0, totalOldPrice);
    lblDetailTongThanhToan.setText(formatMoney(previewTotalToPay));
    lblDisplayTongThanhToan.setText(formatMoney(previewTotalToPay));
  }

  private void refreshPointsButtonsState(SaleWizardState state, double subtotalBeforePoints) {
    boolean anyDiscountTicket = state.getPassengers().stream()
        .filter(Objects::nonNull)
        .filter(PassengerDraft::isHasSeat)
        .anyMatch(p -> p.getTicketType() != null && p.getTicketType() != TicketType.NORMAL);
    boolean hasChildUnder6 = state.getChildrenUnder6() != null && !state.getChildrenUnder6().isEmpty();
    boolean eligible = state.getRewardPoints() != null && state.getRewardPoints() > 0 && !anyDiscountTicket
        && !hasChildUnder6;

    if (!eligible) {
      pointsApplied = false;
      pointsToRedeem = 0;
      lblDetailGiamDiem.setText(formatMoney(0));
    }

    btnDoiDiem.setDisable(!eligible || lastSaleResult != null);
    btnTichDiem.setDisable(lastSaleResult != null);
    btnDoiDiem.setText(pointsApplied ? "Bỏ đổi điểm" : "Đổi điểm tích lũy");

    if (eligible && pointsApplied) {
      int capped = capRedeemPoints(state.getRewardPoints(), subtotalBeforePoints);
      if (pointsToRedeem > capped) {
        pointsToRedeem = capped;
      }
    }
  }

  private void toggleRedeemPoints() {
    if (coordinator == null) {
      return;
    }
    SaleWizardState state = coordinator.getState();
    if (state.getRewardPoints() == null || state.getRewardPoints() <= 0) {
      showAlert(Alert.AlertType.INFORMATION, "Đổi điểm", "Khách hàng không có điểm để đổi.");
      return;
    }
    boolean anyDiscountTicket = state.getPassengers().stream()
        .filter(Objects::nonNull)
        .filter(PassengerDraft::isHasSeat)
        .anyMatch(p -> p.getTicketType() != null && p.getTicketType() != TicketType.NORMAL);
    if (anyDiscountTicket) {
      showAlert(Alert.AlertType.WARNING, "Đổi điểm", "Không áp dụng đổi điểm khi có vé ưu đãi theo đối tượng.");
      return;
    }
    if (state.getChildrenUnder6() != null && !state.getChildrenUnder6().isEmpty()) {
      showAlert(Alert.AlertType.WARNING, "Đổi điểm", "Không áp dụng đổi điểm khi có trẻ dưới 6 tuổi.");
      return;
    }

    double subtotal = computeSubtotalBeforePoints(state);
    int maxPoints = capRedeemPoints(state.getRewardPoints(), subtotal);
    if (maxPoints <= 0) {
      showAlert(Alert.AlertType.INFORMATION, "Đổi điểm", "Không đủ điều kiện đổi điểm (tối đa 10% tổng tiền).");
      return;
    }

    pointsApplied = !pointsApplied;
    pointsToRedeem = pointsApplied ? maxPoints : 0;
    computeAndRenderTotals(state);
    generateSuggestionButtons();
    calculateChange();
  }

  private static int capRedeemPoints(int rewardPoints, double subtotalBeforePoints) {
    int maxByRate = (int) Math.floor((subtotalBeforePoints * 0.10d) / VND_PER_POINT);
    return Math.max(0, Math.min(rewardPoints, maxByRate));
  }

  private double computeSubtotalBeforePoints(SaleWizardState state) {
    ScheduleSaleCardDTO ret = state.getSelectedReturnSchedule();
    boolean roundTrip = state.getTicketCategory() == TicketCategory.ROUND_TRIP && ret != null;

    double tongTienVeGoc = 0;
    double tongGiamDoiTuong = 0;
    double tongBaoHiem = 0;

    for (PassengerDraft p : state.getPassengers()) {
      if (p == null || !p.isHasSeat()) {
        continue;
      }
      SelectedSeatDraft o = p.getOutboundSeat();
      if (o != null) {
        double base = o.getPrice() == null ? 0d : o.getPrice();
        tongTienVeGoc += base;
        tongGiamDoiTuong += base * discountRate(p.getTicketType());
        tongBaoHiem += INSURANCE_FEE;
      }
      if (roundTrip) {
        SelectedSeatDraft r = p.getReturnSeat();
        if (r != null) {
          double base = r.getPrice() == null ? 0d : r.getPrice();
          tongTienVeGoc += base;
          tongGiamDoiTuong += base * discountRate(p.getTicketType());
          tongBaoHiem += INSURANCE_FEE;
        }
      }
    }
    return tongTienVeGoc - tongGiamDoiTuong + tongBaoHiem;
  }

  private void updateTotalLabels(double tongTienVeGoc, double tongGiam, double giamDiem, double baoHiem) {
    lblDetailTongTienVe.setText(formatMoney(tongTienVeGoc));
    lblDetailGiamDoiTuong.setText(formatMoney(tongGiam));
    lblDetailGiamDiem.setText(formatMoney(giamDiem));
    lblDetailBaoHiem.setText(formatMoney(baoHiem));
  }

  private void generateSuggestionButtons() {
    flowPaneSuggestions.getChildren().clear();
    if (previewTotalToPay <= 0) {
      return;
    }
    double[] suggestions = calculateSmartSuggestions(previewTotalToPay);
    for (double amount : suggestions) {
      Button btn = new Button(formatMoney(amount));
      btn.getStyleClass().add("money-suggestion-button");
      btn.setOnAction(e -> {
        txtTienKhachDua.setText(String.valueOf((long) amount));
        calculateChange();
      });
      flowPaneSuggestions.getChildren().add(btn);
    }
  }

  private double[] calculateSmartSuggestions(double total) {
    long totalLong = (long) total;
    Set<Long> suggestions = new TreeSet<>();
    suggestions.add(totalLong);
    suggestions.add(roundUpTo(totalLong, 10000));
    suggestions.add(roundUpTo(totalLong, 50000));
    suggestions.add(roundUpTo(totalLong, 100000));
    suggestions.add(roundUpTo(totalLong, 500000));
    suggestions.add(roundUpTo(totalLong, 1000000));

    return suggestions.stream()
        .filter(v -> v >= totalLong)
        .limit(6)
        .mapToDouble(Long::doubleValue)
        .toArray();
  }

  private static long roundUpTo(long value, long multiple) {
    if (multiple == 0) {
      return value;
    }
    long remainder = value % multiple;
    if (remainder == 0) {
      return value;
    }
    return value + multiple - remainder;
  }

  private void calculateChange() {
    if (lastSaleResult != null || lastExchangeResult != null) {
      return;
    }
    double tienKhachDua = parseMoney(txtTienKhachDua.getText());
    if (tienKhachDua <= 0) {
      lblTienThoiLai.setText(formatMoney(0));
      btnXacNhanVaIn.setDisable(true);
      return;
    }
    double tienThoi = tienKhachDua - previewTotalToPay;
    if (tienThoi >= 0) {
      lblTienThoiLai.setText(formatMoney(tienThoi));
      btnXacNhanVaIn.setDisable(previewTotalToPay <= 0);
    } else {
      lblTienThoiLai.setText("Chưa đủ");
      btnXacNhanVaIn.setDisable(true);
    }
  }

  private void submitCashSaleAndPrintTickets() {
    if (coordinator == null) {
      return;
    }
    if (submitting) {
      return;
    }
    SaleWizardState state = coordinator.getState();
    String validationError = validateBeforeSubmit(state);
    if (validationError != null) {
      showAlert(Alert.AlertType.WARNING, "Thanh toán", validationError);
      return;
    }

    submitting = true;
    btnXacNhanVaIn.setDisable(true);
    btnQuayLai.setDisable(true);

    double amountPaid = parseMoney(txtTienKhachDua.getText());
    if (state != null && state.isExchangeMode()) {
      submitCashExchangeAndPrintTickets(state, amountPaid);
      return;
    }
    SaleCreateRequestDTO req = buildRequest(state, amountPaid);

    Task<Response> task = new Task<>() {
      @Override
      protected Response call() {
        return saleClientService.createSaleTransaction(req);
      }
    };
    task.setOnSucceeded(e -> {
      submitting = false;
      btnQuayLai.setDisable(false);
      Response res = task.getValue();
      if (res != null && res.isSuccess() && res.getData() instanceof SaleCreateResponseDTO dto) {
        lastSaleResult = dto;
        applySaleSuccessUi(dto, true);
      } else {
        btnXacNhanVaIn.setDisable(false);
        showAlert(Alert.AlertType.ERROR, "Thanh toán",
            res == null ? "Không có phản hồi từ server." : safe(res.getMessage()));
      }
    });
    task.setOnFailed(e -> {
      submitting = false;
      btnQuayLai.setDisable(false);
      btnXacNhanVaIn.setDisable(false);
      showAlert(Alert.AlertType.ERROR, "Thanh toán",
          "Lỗi khi gọi server: " + (task.getException() == null ? "Unknown" : task.getException().getMessage()));
    });
    executor.submit(task);
  }

  private void submitCashExchangeAndPrintTickets(SaleWizardState state, double amountPaid) {
    String employeeId = vn.edu.iuh.fit.client.service.SessionManager.getInstance().getEmployeeId();
    if (employeeId == null || employeeId.isBlank()) {
      submitting = false;
      btnQuayLai.setDisable(false);
      btnXacNhanVaIn.setDisable(false);
      showAlert(Alert.AlertType.ERROR, "Đổi vé", "Thiếu employeeId từ phiên đăng nhập. Vui lòng đăng nhập lại.");
      return;
    }

    List<String> oldIds = state.getExchangeOldTickets() == null ? List.of()
        : state.getExchangeOldTickets().stream()
            .filter(Objects::nonNull)
            .map(vn.edu.iuh.fit.common.dto.ReturnTicketTicketDTO::getId)
            .filter(Objects::nonNull)
            .toList();
    List<String> newSdIds = state.getPassengers().stream()
        .filter(Objects::nonNull)
        .filter(PassengerDraft::isHasSeat)
        .map(PassengerDraft::getOutboundSeat)
        .filter(Objects::nonNull)
        .map(SelectedSeatDraft::getScheduleDetailId)
        .filter(Objects::nonNull)
        .toList();

    ExchangeTicketRequestDTO req = ExchangeTicketRequestDTO.builder()
        .oldTicketIds(oldIds)
        .newScheduleDetailIds(newSdIds)
        .employeeId(employeeId)
        .clientSessionId(state.getClientSessionId())
        .build();

    Task<Response> task = new Task<>() {
      @Override
      protected Response call() {
        return exchangeTicketClientService.exchangeTickets(req);
      }
    };
    task.setOnSucceeded(e -> {
      submitting = false;
      btnQuayLai.setDisable(false);
      Response res = task.getValue();
      if (res != null && res.isSuccess() && res.getData() instanceof ExchangeTicketResponseDTO dto) {
        System.out.println("[EXCHANGE] exchangeTickets success invoiceId=" + safe(dto.getInvoiceId())
            + ", newTickets=" + (dto.getNewTickets() == null ? 0 : dto.getNewTickets().size())
            + ", oldTickets=" + (state.getExchangeOldTickets() == null ? 0 : state.getExchangeOldTickets().size()));
        lastExchangeResult = dto;
        applyExchangeSuccessUi(dto, true);
      } else {
        btnXacNhanVaIn.setDisable(false);
        showAlert(Alert.AlertType.ERROR, "Đổi vé",
            res == null ? "Không có phản hồi từ server." : safe(res.getMessage()));
      }
    });
    task.setOnFailed(e -> {
      submitting = false;
      btnQuayLai.setDisable(false);
      btnXacNhanVaIn.setDisable(false);
      showAlert(Alert.AlertType.ERROR, "Đổi vé",
          "Lỗi khi gọi server: " + (task.getException() == null ? "Unknown" : task.getException().getMessage()));
    });
    executor.submit(task);
  }

  private String validateBeforeSubmit(SaleWizardState state) {
    if (state == null) {
      return "Thiếu dữ liệu wizard state.";
    }
    if (state.getSelectedOutboundSchedule() == null) {
      return "Thiếu chuyến chiều đi.";
    }
    if (state.getPassengers() == null || state.getPassengers().isEmpty()) {
      return "Chưa có danh sách hành khách. Vui lòng quay lại Bước 3.";
    }

    List<PassengerDraft> seatPassengers = state.getPassengers().stream()
        .filter(Objects::nonNull)
        .filter(PassengerDraft::isHasSeat)
        .toList();
    if (seatPassengers.isEmpty()) {
      return "Giao dịch không hợp lệ: không có vé có ghế để thanh toán.";
    }

    boolean roundTrip = !state.isExchangeMode() && state.getTicketCategory() == TicketCategory.ROUND_TRIP;
    if (roundTrip && state.getSelectedReturnSchedule() == null) {
      return "Thiếu chuyến chiều về.";
    }
    if (roundTrip) {
      for (PassengerDraft p : seatPassengers) {
        if (p.getReturnSeat() == null || p.getReturnSeat().getScheduleDetailId() == null
            || p.getReturnSeat().getScheduleDetailId().isBlank()) {
          return "Thiếu thông tin ghế chiều về cho hành khách: " + safe(p.getFullName());
        }
      }
    }

    for (PassengerDraft p : state.getPassengers()) {
      if (p == null) {
        continue;
      }
      if (p.isChildUnder6() && !p.isSeatsReleased()) {
        return "Có trẻ <6 nhưng ghế chưa được release. Vui lòng quay lại Bước 3 để kiểm tra.";
      }
      if (p.isHasSeat()) {
        if (p.getOutboundSeat() == null || p.getOutboundSeat().getScheduleDetailId() == null
            || p.getOutboundSeat().getScheduleDetailId().isBlank()) {
          return "Thiếu scheduleDetailId ghế chiều đi cho hành khách: " + safe(p.getFullName());
        }
      }
    }

    if (roundTrip) {
      // Prevent duplicate entry constraints: outbound/return scheduleDetailIds must
      // be distinct.
      Set<String> outboundIds = new HashSet<>();
      Set<String> returnIds = new HashSet<>();
      for (PassengerDraft p : seatPassengers) {
        SelectedSeatDraft out = p.getOutboundSeat();
        SelectedSeatDraft ret = p.getReturnSeat();
        String outId = out == null ? null : out.getScheduleDetailId();
        String retId = ret == null ? null : ret.getScheduleDetailId();
        if (outId != null) {
          if (!outboundIds.add(outId)) {
            return "Ghế chiều đi bị trùng (duplicate scheduleDetailId). Vui lòng chọn lại ghế.";
          }
        }
        if (retId != null) {
          if (!returnIds.add(retId)) {
            return "Ghế chiều về bị trùng (duplicate scheduleDetailId). Vui lòng chọn lại ghế.";
          }
        }
      }
      for (String id : outboundIds) {
        if (returnIds.contains(id)) {
          return "Ghế chiều đi và chiều về đang trùng nhau (duplicate scheduleDetailId). Vui lòng chọn lại ghế.";
        }
      }
    }

    BuyerDraft buyer = state.getBuyer();
    if (buyer == null || buyer.getFullName() == null || buyer.getFullName().isBlank()
        || buyer.getDocumentNumber() == null || buyer.getDocumentNumber().isBlank()) {
      return "Thiếu thông tin người mua. Vui lòng quay lại Bước 3.";
    }

    double amountPaid = parseMoney(txtTienKhachDua.getText());
    if (amountPaid < previewTotalToPay) {
      return "Tiền khách đưa chưa đủ để thanh toán.";
    }
    if (previewTotalToPay <= 0) {
      return "Tổng thanh toán không hợp lệ.";
    }
    return null;
  }

  private void applyExchangeSuccessUi(ExchangeTicketResponseDTO dto, boolean autoOpenPrint) {
    if (coordinator != null) {
      coordinator.cleanupAfterSaleSuccess();
    }

    btnXacNhanVaIn.setDisable(true);
    btnQuayLai.setDisable(true);

    btnHoanTat.setVisible(true);
    btnHoanTat.setManaged(true);
    btnXacNhanVaIn.setVisible(false);
    btnXacNhanVaIn.setManaged(false);

    btnXuatHoaDon.setDisable(false);
    btnDoiDiem.setDisable(true);
    btnTichDiem.setDisable(true);

    lblDetailTongThanhToan.setText(formatMoney(dto == null ? 0 : dto.getTotalAmount()));
    lblDisplayTongThanhToan.setText(formatMoney(dto == null ? 0 : dto.getTotalAmount()));

    double amountPaid = parseMoney(txtTienKhachDua.getText());
    double change = amountPaid - (dto == null ? 0 : dto.getTotalAmount());
    lblTienThoiLai.setText(change >= 0 ? formatMoney(change) : "Chưa đủ");

    String invoiceShort = abbreviateInvoiceId(dto == null ? null : dto.getInvoiceId());
    String content = "Giao dịch đổi vé đã hoàn tất.\nBạn có thể in vé mới hoặc in biên lai theo yêu cầu khách.";
    if (!invoiceShort.isBlank()) {
      content += "\nMã hóa đơn: " + invoiceShort;
    }
    showAlert(Alert.AlertType.INFORMATION, "Đổi vé thành công", content);

    if (autoOpenPrint && dto != null && dto.getNewTickets() != null && !dto.getNewTickets().isEmpty()) {
      openPrintListDialog(dto.getNewTickets());
    }
  }

  private SaleCreateRequestDTO buildRequest(SaleWizardState state, double amountPaid) {
    boolean roundTrip = state.getTicketCategory() == TicketCategory.ROUND_TRIP
        && state.getSelectedReturnSchedule() != null;

    List<PassengerDraft> seatPassengers = state.getPassengers().stream()
        .filter(Objects::nonNull)
        .filter(PassengerDraft::isHasSeat)
        .toList();

    List<String> outboundIds = seatPassengers.stream()
        .map(PassengerDraft::getOutboundSeat)
        .filter(Objects::nonNull)
        .map(SelectedSeatDraft::getScheduleDetailId)
        .filter(Objects::nonNull)
        .toList();

    List<String> returnIds = roundTrip
        ? seatPassengers.stream()
            .map(PassengerDraft::getReturnSeat)
            .filter(Objects::nonNull)
            .map(SelectedSeatDraft::getScheduleDetailId)
            .filter(Objects::nonNull)
            .toList()
        : List.of();

    List<SalePassengerDTO> outboundPassengers = seatPassengers.stream().map(this::toPassengerDto).toList();
    List<SalePassengerDTO> returnPassengers = roundTrip ? outboundPassengers : List.of();

    BuyerDraft buyer = state.getBuyer();
    SaleBuyerDTO buyerDto = SaleBuyerDTO.builder()
        .buyerName(safe(buyer.getFullName()).trim())
        .documentType(buyer.getDocumentType())
        .documentNumber(safe(buyer.getDocumentNumber()).trim())
        .buyerEmail(safe(buyer.getEmail()).trim())
        .buyerPhone(safe(buyer.getPhoneNumber()).trim())
        .hasAccount(buyer.isHasAccount())
        .customerId(buyer.getCustomerId())
        .build();

    SaleRedeemPointsDTO redeem = SaleRedeemPointsDTO.builder()
        .redeemRequested(pointsApplied && pointsToRedeem > 0)
        .pointsToRedeem(pointsApplied ? pointsToRedeem : 0)
        .build();

    List<SaleChildUnder6DTO> children = state.getChildrenUnder6() == null ? List.of()
        : new ArrayList<>(state.getChildrenUnder6());

    // LẤY ID NHÂN VIÊN ĐANG ĐĂNG NHẬP
    String currentEmployeeId = vn.edu.iuh.fit.client.service.SessionManager.getInstance().getEmployeeId();

    return SaleCreateRequestDTO.builder()
        .clientSessionId(state.getClientSessionId())
        .ticketCategory(state.getTicketCategory())
        .outboundScheduleId(state.getSelectedOutboundSchedule().getScheduleId())
        .returnScheduleId(roundTrip ? state.getSelectedReturnSchedule().getScheduleId() : null)
        .outboundScheduleDetailIds(outboundIds)
        .returnScheduleDetailIds(roundTrip ? returnIds : null)
        .outboundPassengers(outboundPassengers)
        .returnPassengers(roundTrip ? returnPassengers : null)
        .childrenUnder6(children.isEmpty() ? null : children)
        .buyer(buyerDto)
        .redeemPoints(redeem)
        .paymentMethod(PaymentMethod.CASH)
        .amountPaid(amountPaid)
        .employeeId(currentEmployeeId) // TRUYỀN ID NHÂN VIÊN XUỐNG SERVER
        .build();
  }

  private SalePassengerDTO toPassengerDto(PassengerDraft p) {
    return SalePassengerDTO.builder()
        .passengerName(safe(p.getFullName()).trim())
        .documentType(p.getDocumentType())
        .documentNumber(safe(p.getDocumentNumber()).trim())
        .ticketType(p.getTicketType())
        .dateOfBirth(p.getDateOfBirth())
        .studentCardVerified(p.isStudentCardVerified())
        .build();
  }

  private void applySaleSuccessUi(SaleCreateResponseDTO dto, boolean autoOpenPrint) {
    if (coordinator != null) {
      coordinator.cleanupAfterSaleSuccess();
    }

    btnXacNhanVaIn.setDisable(true);
    btnQuayLai.setDisable(true);

    btnHoanTat.setVisible(true);
    btnHoanTat.setManaged(true);
    btnXacNhanVaIn.setVisible(false);
    btnXacNhanVaIn.setManaged(false);

    btnXuatHoaDon.setDisable(false);
    btnDoiDiem.setDisable(true);
    btnTichDiem.setDisable(true);

    lblDetailTongThanhToan.setText(formatMoney(dto.getTotalAmount()));
    lblDisplayTongThanhToan.setText(formatMoney(dto.getTotalAmount()));
    lblTienThoiLai.setText(formatMoney(dto.getChangeAmount()));

    double diff = Math.abs(dto.getTotalAmount() - previewTotalToPay);
    if (diff >= 1) {
      System.err.println(
          "[WARN] UC001 preview total mismatch. preview=" + previewTotalToPay + ", server=" + dto.getTotalAmount());
    }

    String invoiceShort = abbreviateInvoiceId(dto.getInvoiceId());
    String content = "Giao dịch bán vé đã hoàn tất.\nBạn có thể in vé hoặc in hóa đơn nếu khách yêu cầu.";
    if (!invoiceShort.isBlank()) {
      content += "\nMã hóa đơn: " + invoiceShort;
    }
    showAlert(Alert.AlertType.INFORMATION, "Thanh toán thành công", content);

    if (autoOpenPrint) {
      openPrintListDialog(dto.getTickets());
    }
  }

  private void openPrintListDialog(List<IssuedTicketDTO> tickets) {
    if (tickets == null || tickets.isEmpty()) {
      showAlert(Alert.AlertType.INFORMATION, "In vé", "Server không trả về danh sách vé để in.");
      return;
    }
    try {
      FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/ui/views/print-list-view.fxml"));
      Parent root = loader.load();
      Object controller = loader.getController();
      if (controller instanceof PrintListController pl) {
        Stage stage = new Stage();
        stage.setTitle("Danh sách vé đã xuất");
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setScene(new Scene(root));
        pl.setDialogStage(stage);
        pl.setTickets(tickets);
        stage.showAndWait();
      } else {
        showAlert(Alert.AlertType.ERROR, "In vé", "Controller print list không đúng kiểu.");
      }
    } catch (IOException e) {
      showAlert(Alert.AlertType.ERROR, "In vé", "Không thể mở danh sách vé: " + e.getMessage());
    }
  }

  private void openInvoicePreviewSummary() {
    if (lastSaleResult == null) {
      return;
    }
    System.err.println("[UC001] invoice preview request invoiceId=" + safe(lastSaleResult.getInvoiceId())
        + ", total=" + formatMoney(lastSaleResult.getTotalAmount())
        + ", tickets=" + (lastSaleResult.getTickets() == null ? 0 : lastSaleResult.getTickets().size())
        + ", childVouchers="
        + (lastSaleResult.getChildVouchers() == null ? 0 : lastSaleResult.getChildVouchers().size()));
    openInvoicePreview("In hóa đơn");
  }

  private void openInvoicePreview(String title) {
    runOnFxThread(() -> {
      try {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/ui/views/pdf-viewer.fxml"));
        Parent root = loader.load();
        Object controller = loader.getController();
        if (controller instanceof PdfViewerController pdf) {
          File pdfFile = InvoiceRenderer.renderPreviewPdf(lastSaleResult,
              coordinator == null ? null : coordinator.getState());
          pdf.loadDocument(pdfFile);
        }
        Stage stage = new Stage();
        stage.setTitle(title);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setScene(new Scene(root, 1000, 800));
        stage.show();
      } catch (Exception e) {
        showAlert(Alert.AlertType.ERROR, "In", "Không thể mở xem trước hóa đơn: " + e.getMessage());
      }
    });
  }

  private void openExchangeReceiptPreview(String title) {
    runOnFxThread(() -> {
      try {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/ui/views/pdf-viewer.fxml"));
        Parent root = loader.load();
        Object controller = loader.getController();
        if (controller instanceof PdfViewerController pdf) {
          System.out.println("[EXCHANGE] openExchangeReceiptPreview invoiceId="
              + safe(lastExchangeResult == null ? null : lastExchangeResult.getInvoiceId()));
          File pdfFile = ExchangeReceiptRenderer.renderPreviewPdf(lastExchangeResult,
              coordinator == null ? null : coordinator.getState());
          pdf.loadDocument(pdfFile);
        }
        Stage stage = new Stage();
        stage.setTitle(title);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setScene(new Scene(root, 1000, 800));
        stage.show();
      } catch (Exception e) {
        showAlert(Alert.AlertType.ERROR, "In", "Không thể mở xem trước biên lai đổi vé: " + e.getMessage());
      }
    });
  }

  private void openPreview(String title, List<IssuedTicketDTO> tickets) {
    runOnFxThread(() -> {
      try {
        List<IssuedTicketDTO> previewTickets = tickets == null ? List.of() : tickets;
        System.err.println("[UC001] ticket preview request title=" + title + ", tickets=" + previewTickets.size());
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/ui/views/pdf-viewer.fxml"));
        Parent root = loader.load();
        Object controller = loader.getController();
        if (controller instanceof PdfViewerController pdf) {
          File pdfFile = TicketRenderer.renderPreviewPdf(previewTickets, title);
          pdf.loadDocument(pdfFile);
        }
        Stage stage = new Stage();
        stage.setTitle(title);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setScene(new Scene(root, 1000, 800));
        stage.show();
      } catch (Exception e) {
        showAlert(Alert.AlertType.ERROR, "In", "Không thể mở xem trước: " + e.getMessage());
      }
    });
  }

  private static double roundUpToThousand(double value) {
    if (value % 1000 == 0) {
      return value;
    }
    return Math.ceil(value / 1000.0) * 1000;
  }

  private static double parseMoney(String text) {
    if (text == null) {
      return 0d;
    }
    String digits = text.replaceAll("[^0-9]", "");
    if (digits.isBlank()) {
      return 0d;
    }
    try {
      return Double.parseDouble(digits);
    } catch (NumberFormatException e) {
      return 0d;
    }
  }

  private static String safe(String v) {
    return v == null ? "" : v;
  }

  private static String labelForDirection(TripDirection d) {
    return d == TripDirection.RETURN ? "Chiều về" : "Chiều đi";
  }

  private static double discountRate(TicketType t) {
    if (t == null) {
      return 0d;
    }
    return switch (t) {
      case CHILD -> 0.25d;
      case SENIOR -> 0.15d;
      case STUDENT -> 0.10d;
      default -> 0d;
    };
  }

  private static String ticketTypeVi(TicketType t) {
    if (t == null) {
      return "Vé người lớn";
    }
    return switch (t) {
      case NORMAL -> "Vé người lớn";
      case CHILD -> "Vé trẻ em";
      case SENIOR -> "Vé người lớn tuổi";
      case STUDENT -> "Vé học sinh - sinh viên";
    };
  }

  private static String formatMoney(double v) {
    return MONEY.format(Math.round(v)) + " đ";
  }

  private static String abbreviateInvoiceId(String id) {
    if (id == null) {
      return "";
    }
    String trimmed = id.trim();
    if (trimmed.isEmpty()) {
      return "";
    }
    if (trimmed.length() <= 8) {
      return trimmed;
    }
    return trimmed.substring(0, 8).toUpperCase();
  }

  private void showAlert(Alert.AlertType type, String title, String message) {
    Alert alert = new Alert(type);
    alert.setTitle(title);
    alert.setHeaderText(null);
    alert.setContentText(message);
    if (Platform.isFxApplicationThread()) {
      alert.showAndWait();
    } else {
      Platform.runLater(alert::showAndWait);
    }
  }

  private static void runOnFxThread(Runnable r) {
    if (Platform.isFxApplicationThread()) {
      r.run();
    } else {
      Platform.runLater(r);
    }
  }
}
