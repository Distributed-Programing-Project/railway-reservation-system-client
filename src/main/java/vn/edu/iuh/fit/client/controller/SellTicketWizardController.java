package vn.edu.iuh.fit.client.controller;

import java.io.ByteArrayInputStream;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import javafx.util.Duration;
import javafx.util.StringConverter;
import javafx.util.converter.IntegerStringConverter;
import vn.edu.iuh.fit.client.service.SaleClientService;
import vn.edu.iuh.fit.client.service.SocketRequestService;
import vn.edu.iuh.fit.common.command.ActionType;
import vn.edu.iuh.fit.common.constant.DocumentType;
import vn.edu.iuh.fit.common.constant.PaymentMethod;
import vn.edu.iuh.fit.common.constant.PaymentStatus;
import vn.edu.iuh.fit.common.constant.SeatAvailabilityStatus;
import vn.edu.iuh.fit.common.constant.TicketCategory;
import vn.edu.iuh.fit.common.constant.TicketType;
import vn.edu.iuh.fit.common.constant.TripDirection;
import vn.edu.iuh.fit.common.dto.CarriageSeatMapDTO;
import vn.edu.iuh.fit.common.dto.CustomerPageDTO;
import vn.edu.iuh.fit.common.dto.CustomerSearchDTO;
import vn.edu.iuh.fit.common.dto.IssuedTicketDTO;
import vn.edu.iuh.fit.common.dto.PaymentCreateResponseDTO;
import vn.edu.iuh.fit.common.dto.PaymentStatusDTO;
import vn.edu.iuh.fit.common.dto.SaleBuyerDTO;
import vn.edu.iuh.fit.common.dto.SaleChildUnder6DTO;
import vn.edu.iuh.fit.common.dto.SaleCreateRequestDTO;
import vn.edu.iuh.fit.common.dto.SaleCreateResponseDTO;
import vn.edu.iuh.fit.common.dto.SalePassengerDTO;
import vn.edu.iuh.fit.common.dto.SaleRedeemPointsDTO;
import vn.edu.iuh.fit.common.dto.SaleScheduleSearchDTO;
import vn.edu.iuh.fit.common.dto.SaleScheduleSearchResultDTO;
import vn.edu.iuh.fit.common.dto.SaleVatDTO;
import vn.edu.iuh.fit.common.dto.ScheduleSaleCardDTO;
import vn.edu.iuh.fit.common.dto.SeatHoldResponseDTO;
import vn.edu.iuh.fit.common.dto.SeatMapResponseDTO;
import vn.edu.iuh.fit.common.dto.SeatMapSeatDTO;
import vn.edu.iuh.fit.common.dto.StationDTO;
import vn.edu.iuh.fit.common.request.Request;
import vn.edu.iuh.fit.common.response.Response;

public class SellTicketWizardController {

  private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
  private static final NumberFormat MONEY = NumberFormat.getInstance(new Locale("vi", "VN"));

  private final SaleClientService saleClientService = new SaleClientService();
  private final SocketRequestService socketRequestService = new SocketRequestService();
  private final String clientSessionId = UUID.randomUUID().toString();

  private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
    Thread workerThread = new Thread(r, "sell-ticket-wizard-worker");
    workerThread.setDaemon(true);
    return workerThread;
  });
  private final AtomicBoolean cleanupDone = new AtomicBoolean(false);

  private Timeline seatMapPoller;
  private Timeline holdKeepAlive;
  private boolean seatMapRefreshInFlight;

  private int currentStep = 1;

  private List<StationDTO> stations = List.of();
  private final ToggleGroup tripGroup = new ToggleGroup();
  private final ToggleGroup paymentGroup = new ToggleGroup();

  private ScheduleSaleCardDTO selectedOutbound;
  private ScheduleSaleCardDTO selectedReturn;

  private SeatMapResponseDTO outboundSeatMap;
  private SeatMapResponseDTO returnSeatMap;

  private final ObservableList<CartItem> outboundCart = FXCollections.observableArrayList();
  private final ObservableList<CartItem> returnCart = FXCollections.observableArrayList();

  private final List<PassengerForm> outboundPassengerForms = new ArrayList<>();
  private final List<PassengerForm> returnPassengerForms = new ArrayList<>();

  private int customerPoints = 0;
  private String selectedCustomerId;

  private PaymentCreateResponseDTO currentPayment;
  private PaymentStatus status = PaymentStatus.PENDING;
  private SaleCreateResponseDTO lastSaleResult;

  // BIẾN CHO CHẾ ĐỘ ĐỔI VÉ
  private boolean isExchangeMode = false;
  private List<String> exchangeOldTicketIds = new ArrayList<>();
  private vn.edu.iuh.fit.common.dto.ExchangeTicketPreviewDTO exchangePreviewDTO;
  private final vn.edu.iuh.fit.client.service.ExchangeTicketClientService exchangeService = new vn.edu.iuh.fit.client.service.ExchangeTicketClientService();

  // STEP indicator
  @FXML
  private Label lblStep1;
  @FXML
  private Label lblStep2;
  @FXML
  private Label lblStep3;
  @FXML
  private Label lblStep4;

  // Navigation
  @FXML
  private Button btnBack;
  @FXML
  private Button btnNext;
  @FXML
  private Button btnResetAll;

  // Overlay
  @FXML
  private StackPane loadingOverlay;

  // STEP 1 controls
  @FXML
  private ComboBox<StationDTO> cbDepartureStation;
  @FXML
  private ComboBox<StationDTO> cbDestinationStation;
  @FXML
  private DatePicker dpDepartureDate;
  @FXML
  private DatePicker dpReturnDate;
  @FXML
  private RadioButton rbOneWay;
  @FXML
  private RadioButton rbRoundTrip;
  @FXML
  private Button btnSearchSchedules;
  @FXML
  private TilePane outboundCardsPane;
  @FXML
  private VBox returnCardsSection;
  @FXML
  private TilePane returnCardsPane;

  // STEP 2 controls
  @FXML
  private ComboBox<CarriageSeatMapDTO> cbOutboundCarriage;
  @FXML
  private ComboBox<CarriageSeatMapDTO> cbReturnCarriage;
  @FXML
  private javafx.scene.layout.GridPane gridOutboundSeats;
  @FXML
  private javafx.scene.layout.GridPane gridReturnSeats;
  @FXML
  private VBox returnSeatSection;
  @FXML
  private Label lblOutboundTotal;
  @FXML
  private Label lblReturnTotal;
  @FXML
  private TableView<CartItem> tblOutboundCart;
  @FXML
  private TableView<CartItem> tblReturnCart;
  @FXML
  private TableColumn<CartItem, String> colOutboundCartCarriage;
  @FXML
  private TableColumn<CartItem, String> colOutboundCartSeat;
  @FXML
  private TableColumn<CartItem, String> colOutboundCartPrice;
  @FXML
  private TableColumn<CartItem, CartItem> colOutboundCartAction;
  @FXML
  private TableColumn<CartItem, String> colReturnCartCarriage;
  @FXML
  private TableColumn<CartItem, String> colReturnCartSeat;
  @FXML
  private TableColumn<CartItem, String> colReturnCartPrice;
  @FXML
  private TableColumn<CartItem, CartItem> colReturnCartAction;

  // STEP 3 controls
  @FXML
  private VBox boxOutboundPassengers;
  @FXML
  private VBox boxReturnPassengersSection;
  @FXML
  private VBox boxReturnPassengers;
  @FXML
  private TextField txtBuyerName;
  @FXML
  private TextField txtBuyerEmail;
  @FXML
  private TextField txtBuyerPhone;
  @FXML
  private ComboBox<DocumentType> cbBuyerDocType;
  @FXML
  private TextField txtBuyerDocNumber;
  @FXML
  private CheckBox chkHasAccount;
  @FXML
  private Button btnLookupCustomer;
  @FXML
  private Label lblCustomerInfo;

  // STEP 4 controls
  @FXML
  private TableView<SummaryRow> tblSummary;
  @FXML
  private TableColumn<SummaryRow, String> colSumLeg;
  @FXML
  private TableColumn<SummaryRow, String> colSumSeat;
  @FXML
  private TableColumn<SummaryRow, String> colSumPassenger;
  @FXML
  private TableColumn<SummaryRow, String> colSumType;
  @FXML
  private TableColumn<SummaryRow, String> colSumBase;
  @FXML
  private TableColumn<SummaryRow, String> colSumDiscount;
  @FXML
  private TableColumn<SummaryRow, String> colSumFinal;
  @FXML
  private CheckBox chkRedeemPoints;
  @FXML
  private Spinner<Integer> spnPointsToRedeem;
  @FXML
  private Label lblPointsCap;
  @FXML
  private Label lblPointsDiscount;
  @FXML
  private TextField txtVatCompany;
  @FXML
  private TextField txtVatTaxCode;
  @FXML
  private TextField txtVatAddress;
  @FXML
  private RadioButton rbPayCash;
  @FXML
  private RadioButton rbPayOnline;
  @FXML
  private HBox cashBox;
  @FXML
  private VBox onlineBox;
  @FXML
  private TextField txtAmountPaid;
  @FXML
  private Label lblChangeAmount;
  @FXML
  private Button btnCreatePayment;
  @FXML
  private Button btnConfirmPayment;
  @FXML
  private Label lblPaymentStatus;
  @FXML
  private ImageView imgQr;
  @FXML
  private Label lblPaymentRef;
  @FXML
  private Label lblPaymentExpire;
  @FXML
  private Label lblPaymentOrderId;
  @FXML
  private Label lblTotalAmount;
  @FXML
  private Button btnFinishSale;
  @FXML
  private Button btnPrintTickets;
  @FXML
  private Button btnPrintInvoice;
  @FXML
  private Button btnPrintChildVouchers;

  // Step panes
  @FXML
  private VBox step1Pane;
  @FXML
  private VBox step2Pane;
  @FXML
  private VBox step3Pane;
  @FXML
  private VBox step4Pane;

  @FXML
  public void initialize() {
    rbOneWay.setToggleGroup(tripGroup);
    rbRoundTrip.setToggleGroup(tripGroup);
    dpReturnDate.setDisable(true);
    rbPayCash.setToggleGroup(paymentGroup);
    rbPayOnline.setToggleGroup(paymentGroup);
    rbPayCash.setSelected(true);
    rbPayOnline.setDisable(false);
    rbPayOnline.setVisible(true);
    rbPayOnline.setManaged(true);
    btnConfirmPayment.setVisible(false);
    btnConfirmPayment.setManaged(false);

    tripGroup.selectedToggleProperty().addListener((obs, old, value) -> updateTripUI());
    paymentGroup.selectedToggleProperty().addListener((obs, old, value) -> updatePaymentUI());

    cbBuyerDocType.setItems(FXCollections.observableArrayList(DocumentType.ID_CARD, DocumentType.PASSPORT));
    cbBuyerDocType.getSelectionModel().select(DocumentType.ID_CARD);

    setupStationCombos();
    setupCartTables();
    setupSummaryTable();
    setupPointsControls();

    updateStepUI();
    updatePaymentUI();
    loadStationsAsync();
    registerCloseCleanupHook();
  }

  private void registerCloseCleanupHook() {
    if (step1Pane == null)
      return;
    step1Pane.sceneProperty().addListener((obs, oldScene, newScene) -> {
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
    stopSeatMapPolling();
    stopHoldKeepAlive();
    releaseAllHeldSeats();
    executor.shutdown();
  }

  private void setupStationCombos() {
    StringConverter<StationDTO> converter = new StringConverter<>() {
      @Override
      public String toString(StationDTO object) {
        return object == null ? "" : object.getName();
      }

      @Override
      public StationDTO fromString(String string) {
        return null;
      }
    };
    cbDepartureStation.setConverter(converter);
    cbDestinationStation.setConverter(converter);
  }

  private void setupCartTables() {
    tblOutboundCart.setItems(outboundCart);
    tblReturnCart.setItems(returnCart);

    colOutboundCartCarriage.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().carriageLabel()));
    colOutboundCartSeat.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().seatLabel()));
    colOutboundCartPrice.setCellValueFactory(c -> new ReadOnlyStringWrapper(formatMoney(c.getValue().price())));
    colOutboundCartAction.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
    colOutboundCartAction.setCellFactory(col -> new TableCell<>() {
      private final Button btn = new Button("X");
      {
        btn.getStyleClass().add("danger-button");
        btn.setOnAction(e -> {
          CartItem item = getItem();
          if (item != null) {
            String scheduleId = selectedOutbound != null ? selectedOutbound.getScheduleId() : null;
            if (scheduleId != null) {
              releaseSeatHold(scheduleId, item.scheduleDetailId, outboundCart, item);
            } else {
              outboundCart.remove(item);
              refreshTotals();
              refreshSeatGrids();
            }
          }
        });
      }

      @Override
      protected void updateItem(CartItem item, boolean empty) {
        super.updateItem(item, empty);
        setGraphic(empty ? null : btn);
      }
    });

    colReturnCartCarriage.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().carriageLabel()));
    colReturnCartSeat.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().seatLabel()));
    colReturnCartPrice.setCellValueFactory(c -> new ReadOnlyStringWrapper(formatMoney(c.getValue().price())));
    colReturnCartAction.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
    colReturnCartAction.setCellFactory(col -> new TableCell<>() {
      private final Button btn = new Button("X");
      {
        btn.getStyleClass().add("danger-button");
        btn.setOnAction(e -> {
          CartItem item = getItem();
          if (item != null) {
            String scheduleId = selectedReturn != null ? selectedReturn.getScheduleId() : null;
            if (scheduleId != null) {
              releaseSeatHold(scheduleId, item.scheduleDetailId, returnCart, item);
            } else {
              returnCart.remove(item);
              refreshTotals();
              refreshSeatGrids();
            }
          }
        });
      }

      @Override
      protected void updateItem(CartItem item, boolean empty) {
        super.updateItem(item, empty);
        setGraphic(empty ? null : btn);
      }
    });
  }

  private void setupSummaryTable() {
    colSumLeg.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().leg));
    colSumSeat.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().seat));
    colSumPassenger.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().name));
    colSumType.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().type));
    colSumBase.setCellValueFactory(c -> new ReadOnlyStringWrapper(formatMoney(c.getValue().base)));
    colSumDiscount.setCellValueFactory(c -> new ReadOnlyStringWrapper(formatMoney(c.getValue().discount)));
    colSumFinal.setCellValueFactory(c -> new ReadOnlyStringWrapper(formatMoney(c.getValue().finalPrice)));
    tblSummary.setItems(FXCollections.observableArrayList());
  }

  private void setupPointsControls() {
    spnPointsToRedeem.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 0, 0));
    spnPointsToRedeem.setEditable(true);
    TextField editor = spnPointsToRedeem.getEditor();
    editor.setTextFormatter(
        new TextFormatter<>(new IntegerStringConverter(), 0, c -> c.getControlNewText().matches("\\d*") ? c : null));

    chkRedeemPoints.selectedProperty().addListener((obs, old, value) -> refreshTotals());
    spnPointsToRedeem.valueProperty().addListener((obs, old, value) -> refreshTotals());
  }

  private void updateTripUI() {
    boolean roundTrip = rbRoundTrip.isSelected();
    dpReturnDate.setDisable(!roundTrip);
    returnCardsSection.setVisible(roundTrip);
    returnCardsSection.setManaged(roundTrip);
    returnSeatSection.setVisible(roundTrip);
    returnSeatSection.setManaged(roundTrip);
    boxReturnPassengersSection.setVisible(roundTrip);
    boxReturnPassengersSection.setManaged(roundTrip);
    refreshTotals();
  }

  private void updatePaymentUI() {
    boolean online = rbPayOnline.isSelected();
    onlineBox.setVisible(online);
    onlineBox.setManaged(online);
    cashBox.setVisible(!online);
    cashBox.setManaged(!online);
    refreshTotals();
  }

  private void loadStationsAsync() {
    setLoading(true);
    Task<Response> task = new Task<>() {
      @Override
      protected Response call() {
        return saleClientService.findAllStations();
      }
    };
    task.setOnSucceeded(e -> {
      setLoading(false);
      Response res = task.getValue();
      if (res != null && res.isSuccess() && res.getData() instanceof List<?> list) {
        stations = list.stream().filter(StationDTO.class::isInstance).map(StationDTO.class::cast).toList();
        cbDepartureStation.setItems(FXCollections.observableArrayList(stations));
        cbDestinationStation.setItems(FXCollections.observableArrayList(stations));
      } else {
        showError("Ga", res == null ? "Không có phản hồi" : res.getMessage());
      }
    });
    task.setOnFailed(e -> {
      setLoading(false);
      showError("Ga", "Không thể tải danh sách ga");
    });
    start(task, "load-stations");
  }

  @FXML
  public void handleSearchSchedules() {
    StationDTO dep = cbDepartureStation.getValue();
    StationDTO dest = cbDestinationStation.getValue();
    LocalDate depDate = dpDepartureDate.getValue();
    LocalDate retDate = dpReturnDate.getValue();
    boolean roundTrip = rbRoundTrip.isSelected();

    if (dep == null || dest == null || dep.getId() == null || dest.getId() == null) {
      showWarning("Tìm chuyến", "Vui lòng chọn ga đi và ga đến.");
      return;
    }
    if (Objects.equals(dep.getId(), dest.getId())) {
      showWarning("Tìm chuyến", "Ga đi phải khác ga đến.");
      return;
    }
    if (depDate == null) {
      showWarning("Tìm chuyến", "Vui lòng chọn ngày đi.");
      return;
    }
    if (roundTrip && retDate == null) {
      showWarning("Tìm chuyến", "Vui lòng chọn ngày về.");
      return;
    }
    if (roundTrip && retDate.isBefore(depDate)) {
      showWarning("Tìm chuyến", "Ngày về phải >= ngày đi.");
      return;
    }

    setLoading(true);
    SaleScheduleSearchDTO dto = SaleScheduleSearchDTO.builder()
        .departureStationId(dep.getId())
        .destinationStationId(dest.getId())
        .departureDate(depDate)
        .ticketCategory(roundTrip ? TicketCategory.ROUND_TRIP : TicketCategory.ONE_WAY)
        .returnDate(retDate)
        .page(0)
        .size(20)
        .build();

    Task<Response> task = new Task<>() {
      @Override
      protected Response call() {
        return saleClientService.searchSchedulesForSale(dto);
      }
    };
    task.setOnSucceeded(e -> {
      setLoading(false);
      Response res = task.getValue();
      if (res != null && res.isSuccess() && res.getData() instanceof SaleScheduleSearchResultDTO result) {
        renderScheduleCards(result);
      } else {
        showError("Tìm chuyến", res == null ? "Không có phản hồi" : res.getMessage());
      }
    });
    task.setOnFailed(e -> {
      setLoading(false);
      showError("Tìm chuyến", "Lỗi khi tìm chuyến.");
    });
    start(task, "search-schedules");
  }

  private void renderScheduleCards(SaleScheduleSearchResultDTO result) {
    selectedOutbound = null;
    selectedReturn = null;
    outboundCardsPane.getChildren().clear();
    returnCardsPane.getChildren().clear();

    if (result.getOutboundSchedules() != null) {
      for (ScheduleSaleCardDTO card : result.getOutboundSchedules()) {
        outboundCardsPane.getChildren().add(buildCardNode(card, TripDirection.OUTBOUND));
      }
    }
    if (rbRoundTrip.isSelected() && result.getReturnSchedules() != null) {
      for (ScheduleSaleCardDTO card : result.getReturnSchedules()) {
        returnCardsPane.getChildren().add(buildCardNode(card, TripDirection.RETURN));
      }
    }
  }

  private VBox buildCardNode(ScheduleSaleCardDTO card, TripDirection dir) {
    VBox box = new VBox(6);
    box.setStyle(
        "-fx-background-color: white; -fx-border-color: #d5dde6; -fx-border-radius: 10; -fx-background-radius: 10; -fx-padding: 12;");
    Label title = new Label("Tàu " + safe(card.getTrainCode()) + " • " + safe(card.getScheduleId()));
    title.setStyle("-fx-font-weight: 800;");
    Label route = new Label(safe(card.getDepartureStationName()) + " → " + safe(card.getDestinationStationName()));
    Label time = new Label(formatDateTime(card.getDepartureTime()) + " - " + formatDateTime(card.getArrivalTime()));
    Label seats = new Label("Ghế trống: " + card.getAvailableSeats() + "/" + card.getTotalSeats());
    seats.setStyle("-fx-text-fill: #0066cc; -fx-font-weight: 700;");

    box.getChildren().addAll(title, route, time, seats);
    box.setOnMouseClicked(evt -> {
      if (dir == TripDirection.OUTBOUND) {
        selectedOutbound = card;
        highlightSelected(outboundCardsPane, box);
      } else {
        selectedReturn = card;
        highlightSelected(returnCardsPane, box);
      }
    });
    return box;
  }

  private void highlightSelected(TilePane pane, VBox selectedNode) {
    pane.getChildren().forEach(n -> n.setStyle(
        "-fx-background-color: white; -fx-border-color: #d5dde6; -fx-border-radius: 10; -fx-background-radius: 10; -fx-padding: 12;"));
    selectedNode.setStyle(
        "-fx-background-color: #f6fbff; -fx-border-color: #0066cc; -fx-border-radius: 10; -fx-background-radius: 10; -fx-padding: 12;");
  }

  @FXML
  public void handleNext() {
    if (currentStep == 1) {
      if (!validateStep1Selection())
        return;
      loadSeatMapsForSelectedSchedules();
      return;
    }
    if (currentStep == 2) {
      if (!validateStep2Selection())
        return;
      stopSeatMapPolling();
      renewHeldSeats();

      // NẾU LÀ ĐỔI VÉ: Bỏ qua nhập thông tin khách (Step 3), nhảy thẳng sang thanh
      // toán (Step 4)
      if (isExchangeMode) {
        currentStep = 4;
        loadExchangePreview(); // Tính phí đổi vé
        updateStepUI();
        return;
      }

      buildPassengerForms();
      currentStep = 3;
      updateStepUI();
      return;
    }
    if (currentStep == 3) {
      if (!validateStep3Inputs())
        return;
      buildSummary();
      currentStep = 4;
      updateStepUI();
      return;
    }
  }

  @FXML
  public void handleBack() {
    if (currentStep <= 1)
      return;
    if (currentStep == 2) {
      releaseAllHeldSeats();
      outboundCart.clear();
      returnCart.clear();
      refreshTotals();
    }

    // NẾU LÀ ĐỔI VÉ: Lùi từ thanh toán (Step 4) thẳng về chọn ghế (Step 2)
    if (currentStep == 4 && isExchangeMode) {
      currentStep = 2;
      updateStepUI();
      return;
    }

    currentStep--;
    updateStepUI();
  }

  @FXML
  public void handleResetAll() {
    releaseAllHeldSeats();
    selectedOutbound = null;
    selectedReturn = null;
    outboundSeatMap = null;
    returnSeatMap = null;
    outboundCart.clear();
    returnCart.clear();
    outboundPassengerForms.clear();
    returnPassengerForms.clear();
    boxOutboundPassengers.getChildren().clear();
    boxReturnPassengers.getChildren().clear();
    tblSummary.getItems().clear();
    customerPoints = 0;
    selectedCustomerId = null;
    lblCustomerInfo.setText("Điểm: --");
    chkHasAccount.setSelected(false);
    chkRedeemPoints.setSelected(false);
    spnPointsToRedeem.getValueFactory().setValue(0);
    currentPayment = null;
    status = PaymentStatus.PENDING;
    lastSaleResult = null;
    btnPrintTickets.setDisable(true);
    btnPrintInvoice.setDisable(true);
    btnPrintChildVouchers.setDisable(true);
    currentStep = 1;
    updateStepUI();
    refreshTotals();
  }

  private void loadExchangePreview() {
    setLoading(true);
    List<String> newSdIds = outboundCart.stream().map(CartItem::scheduleDetailId).toList();

    Task<Response> task = new Task<>() {
      @Override
      protected Response call() {
        return exchangeService.previewExchangeTickets(exchangeOldTicketIds, newSdIds, clientSessionId);
      }
    };
    task.setOnSucceeded(e -> {
      setLoading(false);
      Response res = task.getValue();
      if (res != null && res.isSuccess()
          && res.getData() instanceof vn.edu.iuh.fit.common.dto.ExchangeTicketPreviewDTO dto) {
        this.exchangePreviewDTO = dto;
        // Ép UI hiển thị tổng tiền đổi vé:
        lblTotalAmount.setText("TỔNG TIỀN ĐỔI: " + formatMoney(dto.getTotalAmount()));
        chkRedeemPoints.setDisable(true); // Cấm tích điểm khi đổi vé
      } else {
        showError("Tính tiền đổi", res == null ? "Lỗi server" : res.getMessage());
        handleBack(); // Bị lỗi thì lùi về chọn ghế lại
      }
    });
    task.setOnFailed(e -> {
      setLoading(false);
      showError("Lỗi", "Không thể kết nối Server để tính phí.");
      handleBack();
    });
    start(task, "preview-exchange");
  }

  private boolean validateStep1Selection() {
    if (selectedOutbound == null) {
      showWarning("Chọn chuyến", "Vui lòng chọn 1 chuyến chiều đi.");
      return false;
    }
    if (rbRoundTrip.isSelected() && selectedReturn == null) {
      showWarning("Chọn chuyến", "Vui lòng chọn 1 chuyến chiều về.");
      return false;
    }
    currentStep = 2;
    updateStepUI();
    startSeatMapPolling();
    startHoldKeepAlive();
    return true;
  }

  private void loadSeatMapsForSelectedSchedules() {
    setLoading(true);
    Task<Response> outboundTask = new Task<>() {
      @Override
      protected Response call() {
        return saleClientService.getSeatMap(selectedOutbound.getScheduleId(), clientSessionId);
      }
    };
    outboundTask.setOnSucceeded(e -> {
      Response res = outboundTask.getValue();
      if (res != null && res.isSuccess() && res.getData() instanceof SeatMapResponseDTO sm) {
        outboundSeatMap = sm;
        cbOutboundCarriage.setItems(FXCollections.observableArrayList(sm.getCarriages()));
        cbOutboundCarriage.getSelectionModel().selectFirst();
        cbOutboundCarriage.valueProperty().addListener((obs, old, v) -> refreshSeatGrids());
        refreshSeatGrids();
        if (!rbRoundTrip.isSelected()) {
          setLoading(false);
        }
      } else {
        setLoading(false);
        showError("Sơ đồ ghế", res == null ? "Không có phản hồi" : res.getMessage());
      }
    });
    outboundTask.setOnFailed(e -> {
      setLoading(false);
      showError("Sơ đồ ghế", "Không thể tải sơ đồ ghế chiều đi.");
    });
    start(outboundTask, "seatmap-outbound");

    if (rbRoundTrip.isSelected()) {
      Task<Response> returnTask = new Task<>() {
        @Override
        protected Response call() {
          return saleClientService.getSeatMap(selectedReturn.getScheduleId(), clientSessionId);
        }
      };
      returnTask.setOnSucceeded(e -> {
        setLoading(false);
        Response res = returnTask.getValue();
        if (res != null && res.isSuccess() && res.getData() instanceof SeatMapResponseDTO sm) {
          returnSeatMap = sm;
          cbReturnCarriage.setItems(FXCollections.observableArrayList(sm.getCarriages()));
          cbReturnCarriage.getSelectionModel().selectFirst();
          cbReturnCarriage.valueProperty().addListener((obs, old, v) -> refreshSeatGrids());
          refreshSeatGrids();
        } else {
          showError("Sơ đồ ghế", res == null ? "Không có phản hồi" : res.getMessage());
        }
      });
      returnTask.setOnFailed(e -> {
        setLoading(false);
        showError("Sơ đồ ghế", "Không thể tải sơ đồ ghế chiều về.");
      });
      start(returnTask, "seatmap-return");
    }
  }

  private void startSeatMapPolling() {
    if (seatMapPoller != null)
      return;
    seatMapPoller = new Timeline(new KeyFrame(Duration.seconds(5), e -> pollSeatMapsOnce()));
    seatMapPoller.setCycleCount(Timeline.INDEFINITE);
    seatMapPoller.play();
  }

  private void stopSeatMapPolling() {
    if (seatMapPoller != null) {
      seatMapPoller.stop();
      seatMapPoller = null;
    }
    seatMapRefreshInFlight = false;
  }

  private void pollSeatMapsOnce() {
    if (currentStep != 2)
      return;
    if (seatMapRefreshInFlight)
      return;
    if (selectedOutbound == null)
      return;

    seatMapRefreshInFlight = true;
    int totalTasks = rbRoundTrip.isSelected() ? 2 : 1;
    int[] remaining = { totalTasks };

    refreshSeatMap(selectedOutbound.getScheduleId(), true, () -> {
      remaining[0]--;
      if (remaining[0] <= 0) {
        seatMapRefreshInFlight = false;
      }
    });

    if (rbRoundTrip.isSelected() && selectedReturn != null) {
      refreshSeatMap(selectedReturn.getScheduleId(), false, () -> {
        remaining[0]--;
        if (remaining[0] <= 0) {
          seatMapRefreshInFlight = false;
        }
      });
    }
  }

  private void refreshSeatMap(String scheduleId, boolean outbound, Runnable done) {
    Task<Response> task = new Task<>() {
      @Override
      protected Response call() {
        return saleClientService.getSeatMap(scheduleId, clientSessionId);
      }
    };
    task.setOnSucceeded(e -> {
      try {
        Response res = task.getValue();
        if (res != null && res.isSuccess() && res.getData() instanceof SeatMapResponseDTO sm) {
          if (outbound) {
            outboundSeatMap = sm;
            updateCarriageCombo(cbOutboundCarriage, sm);
            reconcileCartWithSeatMap(sm, outboundCart);
          } else {
            returnSeatMap = sm;
            updateCarriageCombo(cbReturnCarriage, sm);
            reconcileCartWithSeatMap(sm, returnCart);
          }
          refreshSeatGrids();
          refreshTotals();
        }
      } finally {
        if (done != null)
          done.run();
      }
    });
    task.setOnFailed(e -> {
      if (done != null)
        done.run();
    });
    start(task, outbound ? "poll-seatmap-outbound" : "poll-seatmap-return");
  }

  private void updateCarriageCombo(ComboBox<CarriageSeatMapDTO> combo, SeatMapResponseDTO sm) {
    if (combo == null || sm == null || sm.getCarriages() == null)
      return;
    CarriageSeatMapDTO selected = combo.getValue();
    String selectedId = selected != null ? selected.getCarriageId() : null;
    combo.setItems(FXCollections.observableArrayList(sm.getCarriages()));
    if (selectedId != null) {
      sm.getCarriages().stream()
          .filter(c -> Objects.equals(c.getCarriageId(), selectedId))
          .findFirst()
          .ifPresentOrElse(combo.getSelectionModel()::select, combo.getSelectionModel()::selectFirst);
    } else {
      combo.getSelectionModel().selectFirst();
    }
  }

  private void reconcileCartWithSeatMap(SeatMapResponseDTO sm, ObservableList<CartItem> cart) {
    if (sm == null || sm.getCarriages() == null || cart == null || cart.isEmpty())
      return;
    Map<String, SeatMapSeatDTO> bySdId = new HashMap<>();
    for (CarriageSeatMapDTO c : sm.getCarriages()) {
      if (c.getSeats() == null)
        continue;
      for (SeatMapSeatDTO s : c.getSeats()) {
        bySdId.put(s.getScheduleDetailId(), s);
      }
    }

    List<CartItem> toRemove = new ArrayList<>();
    for (CartItem item : cart) {
      SeatMapSeatDTO seat = bySdId.get(item.scheduleDetailId);
      if (seat == null) {
        toRemove.add(item);
        continue;
      }
      SeatAvailabilityStatus status = seat.getSeatStatus() != null ? seat.getSeatStatus()
          : SeatAvailabilityStatus.AVAILABLE;
      if (status != SeatAvailabilityStatus.HELD || !seat.isHeldByMe()) {
        toRemove.add(item);
      }
    }

    if (!toRemove.isEmpty()) {
      cart.removeAll(toRemove);
      showWarning("Giỏ vé", "Một số ghế đã hết giữ chỗ. Vui lòng chọn lại.");
    }
  }

  private void startHoldKeepAlive() {
    if (holdKeepAlive != null)
      return;
    holdKeepAlive = new Timeline(new KeyFrame(Duration.minutes(2), e -> renewHeldSeats()));
    holdKeepAlive.setCycleCount(Timeline.INDEFINITE);
    holdKeepAlive.play();
  }

  private void stopHoldKeepAlive() {
    if (holdKeepAlive != null) {
      holdKeepAlive.stop();
      holdKeepAlive = null;
    }
  }

  private void renewHeldSeats() {
    if (selectedOutbound == null)
      return;
    if (outboundCart.isEmpty() && (!rbRoundTrip.isSelected() || returnCart.isEmpty()))
      return;

    List<String> outboundIds = outboundCart.stream().map(ci -> ci.scheduleDetailId).toList();
    if (!outboundIds.isEmpty()) {
      Task<Response> outboundKeepAliveTask = new Task<>() {
        @Override
        protected Response call() {
          return saleClientService.holdSeats(selectedOutbound.getScheduleId(), outboundIds, clientSessionId);
        }
      };
      start(outboundKeepAliveTask, "hold-keepalive-outbound");
    }

    if (rbRoundTrip.isSelected() && selectedReturn != null) {
      List<String> returnIds = returnCart.stream().map(ci -> ci.scheduleDetailId).toList();
      if (!returnIds.isEmpty()) {
        Task<Response> returnKeepAliveTask = new Task<>() {
          @Override
          protected Response call() {
            return saleClientService.holdSeats(selectedReturn.getScheduleId(), returnIds, clientSessionId);
          }
        };
        start(returnKeepAliveTask, "hold-keepalive-return");
      }
    }
  }

  private void releaseAllHeldSeats() {
    stopSeatMapPolling();
    stopHoldKeepAlive();

    if (selectedOutbound != null && !outboundCart.isEmpty()) {
      List<String> ids = outboundCart.stream().map(ci -> ci.scheduleDetailId).toList();
      Task<Response> releaseOutboundTask = new Task<>() {
        @Override
        protected Response call() {
          return saleClientService.releaseHeldSeats(selectedOutbound.getScheduleId(), ids, clientSessionId);
        }
      };
      start(releaseOutboundTask, "release-all-outbound");
    }

    if (selectedReturn != null && !returnCart.isEmpty()) {
      List<String> ids = returnCart.stream().map(ci -> ci.scheduleDetailId).toList();
      Task<Response> releaseReturnTask = new Task<>() {
        @Override
        protected Response call() {
          return saleClientService.releaseHeldSeats(selectedReturn.getScheduleId(), ids, clientSessionId);
        }
      };
      start(releaseReturnTask, "release-all-return");
    }
  }

  private void refreshSeatGrids() {
    String outboundScheduleId = selectedOutbound != null ? selectedOutbound.getScheduleId() : null;
    renderSeatGrid(gridOutboundSeats, cbOutboundCarriage.getValue(), outboundCart, outboundScheduleId);
    if (rbRoundTrip.isSelected()) {
      String returnScheduleId = selectedReturn != null ? selectedReturn.getScheduleId() : null;
      renderSeatGrid(gridReturnSeats, cbReturnCarriage.getValue(), returnCart, returnScheduleId);
    } else {
      gridReturnSeats.getChildren().clear();
    }
  }

  private void renderSeatGrid(javafx.scene.layout.GridPane grid, CarriageSeatMapDTO carriage,
      ObservableList<CartItem> cart, String scheduleId) {
    grid.getChildren().clear();
    if (carriage == null || carriage.getSeats() == null)
      return;

    int cols = 8;
    int row = 0;
    int col = 0;
    for (SeatMapSeatDTO seat : carriage.getSeats()) {
      Button btn = new Button(String.valueOf(seat.getSeatNumber()));
      btn.setMinSize(46, 34);
      btn.setMaxSize(46, 34);

      boolean isSelected = cart.stream()
          .anyMatch(ci -> Objects.equals(ci.scheduleDetailId, seat.getScheduleDetailId()));
      SeatAvailabilityStatus status = seat.getSeatStatus() != null ? seat.getSeatStatus()
          : SeatAvailabilityStatus.AVAILABLE;
      boolean heldByMe = seat.isHeldByMe();

      if (status == SeatAvailabilityStatus.SOLD) {
        btn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-weight: 700;");
        btn.setDisable(true);
      } else if (status == SeatAvailabilityStatus.HELD && !heldByMe) {
        btn.setStyle("-fx-background-color: #f39c12; -fx-text-fill: white; -fx-font-weight: 700;");
        btn.setDisable(true);
      } else if (isSelected || (status == SeatAvailabilityStatus.HELD && heldByMe)) {
        btn.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-font-weight: 700;");
      } else {
        btn.setStyle("-fx-background-color: #ecf0f1; -fx-text-fill: #1f2d3d; -fx-font-weight: 700;");
      }

      btn.setOnAction(e -> {
        if (scheduleId == null)
          return;
        SeatAvailabilityStatus current = seat.getSeatStatus() != null ? seat.getSeatStatus()
            : SeatAvailabilityStatus.AVAILABLE;
        if (current == SeatAvailabilityStatus.SOLD)
          return;
        if (current == SeatAvailabilityStatus.HELD && !seat.isHeldByMe())
          return;

        Optional<CartItem> existing = cart.stream()
            .filter(ci -> Objects.equals(ci.scheduleDetailId, seat.getScheduleDetailId()))
            .findFirst();
        if (existing.isPresent()) {
          releaseSeatHold(scheduleId, seat.getScheduleDetailId(), cart, existing.get());
        } else {
          if (cart.size() >= 10) {
            showWarning("Chọn ghế", "Mỗi giao dịch chỉ được mua tối đa 10 vé.");
            return;
          }
          holdSeat(scheduleId, seat, carriage.getCarriageNumber(), cart);
        }
      });

      grid.add(btn, col, row);
      col++;
      if (col >= cols) {
        col = 0;
        row++;
      }
    }
  }

  private void holdSeat(String scheduleId, SeatMapSeatDTO seat, int carriageNumber, ObservableList<CartItem> cart) {
    String sdId = seat != null ? seat.getScheduleDetailId() : null;
    if (scheduleId == null || sdId == null)
      return;

    setLoading(true);
    Task<Response> task = new Task<>() {
      @Override
      protected Response call() {
        return saleClientService.holdSeats(scheduleId, List.of(sdId), clientSessionId);
      }
    };
    task.setOnSucceeded(e -> {
      setLoading(false);
      Response res = task.getValue();
      boolean ok = res != null && res.isSuccess()
          && res.getData() instanceof SeatHoldResponseDTO dto
          && dto.getSuccessIds() != null
          && dto.getSuccessIds().contains(sdId);
      if (ok) {
        cart.add(new CartItem(sdId, carriageNumber, seat.getSeatNumber(), seat.getSeatPrice()));
        refreshTotals();
        pollSeatMapsOnce();
      } else {
        showWarning("Chọn ghế", res == null ? "Không có phản hồi" : res.getMessage());
        pollSeatMapsOnce();
      }
    });
    task.setOnFailed(e -> {
      setLoading(false);
      showError("Chọn ghế", "Không thể giữ ghế. Vui lòng thử lại.");
      pollSeatMapsOnce();
    });
    start(task, "hold-seat");
  }

  private void releaseSeatHold(String scheduleId, String scheduleDetailId, ObservableList<CartItem> cart,
      CartItem item) {
    if (scheduleId == null || scheduleDetailId == null)
      return;

    setLoading(true);
    Task<Response> task = new Task<>() {
      @Override
      protected Response call() {
        return saleClientService.releaseHeldSeats(scheduleId, List.of(scheduleDetailId), clientSessionId);
      }
    };
    task.setOnSucceeded(e -> {
      setLoading(false);
      Response res = task.getValue();
      if (res != null && res.isSuccess()) {
        cart.remove(item);
        refreshTotals();
        pollSeatMapsOnce();
      } else {
        showError("Chọn ghế", res == null ? "Không có phản hồi" : res.getMessage());
        pollSeatMapsOnce();
      }
    });
    task.setOnFailed(e -> {
      setLoading(false);
      showError("Chọn ghế", "Không thể nhả ghế. Vui lòng thử lại.");
      pollSeatMapsOnce();
    });
    start(task, "release-seat");
  }

  private boolean validateStep2Selection() {
    if (outboundCart.isEmpty()) {
      showWarning("Chọn ghế", "Vui lòng chọn ít nhất 1 ghế chiều đi.");
      return false;
    }
    // NẾU LÀ ĐỔI VÉ: Số ghế mới phải bằng số vé cũ
    if (isExchangeMode && outboundCart.size() != exchangeOldTicketIds.size()) {
      showWarning("Chọn ghế", "Bạn đang đổi " + exchangeOldTicketIds.size() + " vé. Bạn đã chọn " + outboundCart.size()
          + " ghế. Vui lòng chọn cho đủ.");
      return false;
    }
    if (rbRoundTrip.isSelected()) {
      if (returnCart.isEmpty()) {
        showWarning("Chọn ghế", "Vui lòng chọn ghế chiều về.");
        return false;
      }
      if (outboundCart.size() != returnCart.size()) {
        showWarning("Chọn ghế", "Số lượng ghế chiều đi phải bằng chiều về.");
        return false;
      }
    }
    return true;
  }

  private void buildPassengerForms() {
    outboundPassengerForms.clear();
    returnPassengerForms.clear();
    boxOutboundPassengers.getChildren().clear();
    boxReturnPassengers.getChildren().clear();

    int i = 0;
    for (CartItem item : outboundCart) {
      PassengerForm form = PassengerForm.create("Chiều đi", i, item);
      outboundPassengerForms.add(form);
      boxOutboundPassengers.getChildren().add(form.root);
      i++;
    }
    if (rbRoundTrip.isSelected()) {
      int j = 0;
      for (CartItem item : returnCart) {
        PassengerForm form = PassengerForm.create("Chiều về", j, item);
        returnPassengerForms.add(form);
        boxReturnPassengers.getChildren().add(form.root);
        j++;
      }
    }
  }

  private boolean validateStep3Inputs() {
    for (PassengerForm form : outboundPassengerForms) {
      String err = form.validate(selectedOutbound.getDepartureTime());
      if (err != null) {
        showWarning("Hành khách", err);
        return false;
      }
    }
    if (rbRoundTrip.isSelected()) {
      for (PassengerForm form : returnPassengerForms) {
        String err = form.validate(selectedReturn.getDepartureTime());
        if (err != null) {
          showWarning("Hành khách", err);
          return false;
        }
      }
    }

    boolean hasChild = outboundPassengerForms.stream().anyMatch(f -> f.typeBox.getValue() == TicketType.CHILD)
        || returnPassengerForms.stream().anyMatch(f -> f.typeBox.getValue() == TicketType.CHILD);
    boolean hasAdult = outboundPassengerForms.stream().anyMatch(f -> f.typeBox.getValue() != TicketType.CHILD)
        || returnPassengerForms.stream().anyMatch(f -> f.typeBox.getValue() != TicketType.CHILD);
    if (hasChild && !hasAdult) {
      showWarning("Hành khách", "Vé trẻ em bắt buộc phải có người lớn đi kèm.");
      return false;
    }

    boolean hasDiscountType = outboundPassengerForms.stream().anyMatch(f -> f.typeBox.getValue() != TicketType.NORMAL)
        || returnPassengerForms.stream().anyMatch(f -> f.typeBox.getValue() != TicketType.NORMAL)
        || outboundPassengerForms.stream().anyMatch(f -> !f.children.isEmpty())
        || returnPassengerForms.stream().anyMatch(f -> !f.children.isEmpty());
    if (chkRedeemPoints.isSelected() && hasDiscountType) {
      showWarning("Đổi điểm", "Không được đổi điểm khi có vé ưu đãi đối tượng.");
      return false;
    }

    if (normalize(txtBuyerName.getText()) == null
        || normalize(txtBuyerEmail.getText()) == null
        || normalize(txtBuyerPhone.getText()) == null
        || normalize(txtBuyerDocNumber.getText()) == null
        || cbBuyerDocType.getValue() == null) {
      showWarning("Người mua", "Vui lòng nhập đầy đủ thông tin người mua.");
      return false;
    }

    if (chkHasAccount.isSelected() && selectedCustomerId == null) {
      showWarning("Tích điểm", "Bạn đã chọn có tài khoản nhưng chưa tra cứu/chọn khách hàng.");
      return false;
    }

    return true;
  }

  private void buildSummary() {
    List<SummaryRow> rows = new ArrayList<>();
    for (PassengerForm form : outboundPassengerForms) {
      Pricing p = computePricing(form, selectedOutbound.getDepartureTime());
      rows.add(new SummaryRow("Đi", form.item.carriageLabel() + "-" + form.item.seatLabel(), form.nameField.getText(),
          String.valueOf(form.typeBox.getValue()), form.item.price(), p.discount, p.finalPrice));
      for (ChildForm child : form.children) {
        rows.add(new SummaryRow("Đi", "Không ghế", child.nameField.getText(),
            "CHILD<6", 0.0, 0.0, 0.0));
      }
    }
    if (rbRoundTrip.isSelected()) {
      for (PassengerForm form : returnPassengerForms) {
        Pricing p = computePricing(form, selectedReturn.getDepartureTime());
        rows.add(new SummaryRow("Về", form.item.carriageLabel() + "-" + form.item.seatLabel(), form.nameField.getText(),
            String.valueOf(form.typeBox.getValue()), form.item.price(), p.discount, p.finalPrice));
        for (ChildForm child : form.children) {
          rows.add(new SummaryRow("Về", "Không ghế", child.nameField.getText(),
              "CHILD<6", 0.0, 0.0, 0.0));
        }
      }
    }
    tblSummary.setItems(FXCollections.observableArrayList(rows));
    refreshTotals();
  }

  private void refreshTotals() {
    lblOutboundTotal.setText("Tổng (đi): " + formatMoney(outboundCart.stream().mapToDouble(CartItem::price).sum()));
    lblReturnTotal.setText("Tổng (về): " + formatMoney(returnCart.stream().mapToDouble(CartItem::price).sum()));

    double subtotal = computeSubtotalAfterTypeDiscount();

    boolean hasDiscountType = false;
    if (currentStep >= 3) {
      hasDiscountType = outboundPassengerForms.stream().anyMatch(f -> f.typeBox.getValue() != TicketType.NORMAL)
          || returnPassengerForms.stream().anyMatch(f -> f.typeBox.getValue() != TicketType.NORMAL)
          || outboundPassengerForms.stream().anyMatch(f -> !f.children.isEmpty())
          || returnPassengerForms.stream().anyMatch(f -> !f.children.isEmpty());
    }
    boolean paymentLocked = rbPayOnline.isSelected() && currentPayment != null;
    if (paymentLocked) {
      chkRedeemPoints.setSelected(false);
      chkRedeemPoints.setDisable(true);
      spnPointsToRedeem.setDisable(true);
      spnPointsToRedeem.getValueFactory().setValue(0);
    } else if (hasDiscountType) {
      chkRedeemPoints.setSelected(false);
      chkRedeemPoints.setDisable(true);
      spnPointsToRedeem.setDisable(true);
      spnPointsToRedeem.getValueFactory().setValue(0);
    } else {
      chkRedeemPoints.setDisable(false);
      spnPointsToRedeem.setDisable(false);
    }

    int maxPointsByRate = (int) Math.floor((subtotal * 0.10) / 1000.0);
    int maxPointsByBalance = chkHasAccount.isSelected() ? customerPoints : 0;
    int cap = (hasDiscountType || paymentLocked) ? 0 : Math.max(0, Math.min(maxPointsByRate, maxPointsByBalance));
    ((SpinnerValueFactory.IntegerSpinnerValueFactory) spnPointsToRedeem.getValueFactory()).setMax(cap);
    if (spnPointsToRedeem.getValue() > cap)
      spnPointsToRedeem.getValueFactory().setValue(cap);
    lblPointsCap.setText("Tối đa: " + cap + " điểm");

    int redeem = (chkRedeemPoints.isSelected() && cap > 0) ? spnPointsToRedeem.getValue() : 0;
    double pointsDiscount = redeem * 1000.0;
    lblPointsDiscount.setText("Giảm: " + formatMoney(pointsDiscount));

    double total = Math.max(0, subtotal - pointsDiscount);
    lblTotalAmount.setText("Tổng thanh toán: " + formatMoney(total));

    if (rbPayCash.isSelected()) {
      double amountPaid = parseMoney(txtAmountPaid.getText());
      double change = Math.max(0, amountPaid - total);
      lblChangeAmount.setText("Thối lại: " + formatMoney(change));
    }
  }

  private double computeSubtotalAfterTypeDiscount() {
    double total = 0.0;
    if (currentStep >= 3) {
      for (PassengerForm form : outboundPassengerForms) {
        total += computePricing(form, selectedOutbound.getDepartureTime()).finalPrice;
      }
      if (rbRoundTrip.isSelected()) {
        for (PassengerForm form : returnPassengerForms) {
          total += computePricing(form, selectedReturn.getDepartureTime()).finalPrice;
        }
      }
    } else {
      total += outboundCart.stream().mapToDouble(CartItem::price).sum();
      total += returnCart.stream().mapToDouble(CartItem::price).sum();
    }
    return total;
  }

  private Pricing computePricing(PassengerForm form, LocalDateTime departureTime) {
    double base = form.item.price();
    TicketType type = form.typeBox.getValue() != null ? form.typeBox.getValue() : TicketType.NORMAL;
    double discount = 0.0;
    if (type == TicketType.CHILD) {
      int age = ageAt(form.dobPicker.getValue(), departureTime);
      if (age >= 6 && age < 10)
        discount = base * 0.25;
    } else if (type == TicketType.SENIOR) {
      int age = ageAt(form.dobPicker.getValue(), departureTime);
      if (age >= 60)
        discount = base * 0.15;
    } else if (type == TicketType.STUDENT) {
      discount = base * 0.10;
    }
    return new Pricing(discount, Math.max(0, base - discount));
  }

  @FXML
  public void handleLookupCustomer() {
    if (!chkHasAccount.isSelected()) {
      showWarning("Tích điểm", "Vui lòng tick \"Có tài khoản tích điểm\" trước.");
      return;
    }
    String keyword = normalize(txtBuyerDocNumber.getText());
    if (keyword == null) {
      showWarning("Tích điểm", "Vui lòng nhập giấy tờ người mua để tra cứu.");
      return;
    }

    setLoading(true);
    CustomerSearchDTO dto = CustomerSearchDTO.builder().keyword(keyword).page(0).size(20).build();
    Task<Response> task = new Task<>() {
      @Override
      protected Response call() {
        return socketRequestService.send(new Request(ActionType.SEARCH_CUSTOMERS, dto));
      }
    };
    task.setOnSucceeded(e -> {
      setLoading(false);
      Response res = task.getValue();
      if (res != null && res.isSuccess() && res.getData() instanceof CustomerPageDTO page
          && page.getCustomers() != null) {
        if (page.getCustomers().isEmpty()) {
          showWarning("Tích điểm", "Không tìm thấy khách hàng phù hợp.");
          selectedCustomerId = null;
          customerPoints = 0;
          lblCustomerInfo.setText("Điểm: 0 (chưa có tài khoản)");
          refreshTotals();
          return;
        }
        var first = page.getCustomers().getFirst();
        selectedCustomerId = first.getCustomerId();
        customerPoints = first.getRewardPoints();
        lblCustomerInfo.setText("Điểm: " + customerPoints + " • KH: " + first.getFullName());
        refreshTotals();
      } else {
        showError("Tích điểm", res == null ? "Không có phản hồi" : res.getMessage());
      }
    });
    task.setOnFailed(e -> {
      setLoading(false);
      showError("Tích điểm", "Lỗi khi tra cứu khách hàng.");
    });
    start(task, "lookup-customer");
  }

  @FXML
  public void handleCreatePayment() {
    if (!rbPayOnline.isSelected()) {
      showWarning("Thanh toán", "Vui lòng chọn phương thức Online.");
      return;
    }

    double subtotal = computeSubtotalAfterTypeDiscount();
    int cap = ((SpinnerValueFactory.IntegerSpinnerValueFactory) spnPointsToRedeem.getValueFactory()).getMax();
    int redeem = (chkRedeemPoints.isSelected() && cap > 0) ? spnPointsToRedeem.getValue() : 0;
    double total = Math.max(0, subtotal - redeem * 1000.0);
    if (total <= 0) {
      showWarning("Online", "Tổng thanh toán không hợp lệ.");
      return;
    }
    setLoading(true);
    Task<Response> task = new Task<>() {
      @Override
      protected Response call() {
        return saleClientService.createPaymentOrder(total, "Thanh toán vé tàu", clientSessionId);
      }
    };
    task.setOnSucceeded(e -> {
      setLoading(false);
      Response res = task.getValue();
      if (res != null && res.isSuccess() && res.getData() instanceof PaymentCreateResponseDTO dto) {
        currentPayment = dto;
        lblPaymentStatus.setText("Trạng thái: " + dto.getStatus());
        lblPaymentRef.setText("REF: " + dto.getReferenceCode());
        lblPaymentOrderId.setText("Order: " + dto.getPaymentOrderId());
        lblPaymentExpire
            .setText("Hết hạn: " + (dto.getExpiresAt() == null ? "--" : dto.getExpiresAt().format(DATE_TIME)));
        imgQr.setImage(toImage(dto.getQrPng()));
        chkRedeemPoints.setDisable(true);
        spnPointsToRedeem.setDisable(true);
        openInternalPaymentDialog(dto, total);
      } else {
        showError("Online", res == null ? "Không có phản hồi" : res.getMessage());
      }
    });
    task.setOnFailed(e -> {
      setLoading(false);
      showError("Online", "Không thể tạo thanh toán.");
    });
    start(task, "create-payment");
  }

  private void openInternalPaymentDialog(PaymentCreateResponseDTO dto, double totalAmount) {
    if (dto == null)
      return;
    try {
      FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/ui/views/internal-payment-dialog.fxml"));
      Parent root = loader.load();
      InternalPaymentDialogController controller = loader.getController();

      Stage stage = new Stage();
      stage.initModality(Modality.APPLICATION_MODAL);
      if (btnFinishSale != null && btnFinishSale.getScene() != null) {
        stage.initOwner(btnFinishSale.getScene().getWindow());
      }
      stage.setTitle("Thanh toán Online (Nội bộ)");
      stage.setScene(new Scene(root));

      controller.setPayment(dto, totalAmount, () -> simulateInternalTransferSuccess(stage));
      stage.showAndWait();
    } catch (Exception e) {
      showError("Online", "Không thể mở cửa sổ thanh toán.");
    }
  }

  private void simulateInternalTransferSuccess(Stage dialogStage) {
    if (currentPayment == null)
      return;
    setLoading(true);
    Task<Response> task = new Task<>() {
      @Override
      protected Response call() {
        return saleClientService.confirmInternalPayment(currentPayment.getPaymentOrderId(), clientSessionId);
      }
    };
    task.setOnSucceeded(e -> {
      setLoading(false);
      Response res = task.getValue();
      if (res != null && res.isSuccess() && res.getData() instanceof PaymentStatusDTO dto) {
        status = dto.getStatus();
        lblPaymentStatus.setText("Trạng thái: " + status);
        if (status == PaymentStatus.SUCCESS) {
          SaleCreateRequestDTO request = buildSaleRequestForOnline();
          submitSaleAsync(request, () -> {
            if (dialogStage != null)
              dialogStage.close();
          });
        }
      } else {
        showError("Online", res == null ? "Không có phản hồi" : res.getMessage());
      }
    });
    task.setOnFailed(e -> {
      setLoading(false);
      showError("Online", "Lỗi xác nhận thanh toán.");
    });
    start(task, "confirm-internal-payment");
  }

  private SaleCreateRequestDTO buildSaleRequestForOnline() {
    int cap = ((SpinnerValueFactory.IntegerSpinnerValueFactory) spnPointsToRedeem.getValueFactory()).getMax();
    int redeem = (chkRedeemPoints.isSelected() && cap > 0) ? spnPointsToRedeem.getValue() : 0;
    return SaleCreateRequestDTO.builder()
        .clientSessionId(clientSessionId)
        .ticketCategory(rbRoundTrip.isSelected() ? TicketCategory.ROUND_TRIP : TicketCategory.ONE_WAY)
        .outboundScheduleId(selectedOutbound.getScheduleId())
        .returnScheduleId(rbRoundTrip.isSelected() ? selectedReturn.getScheduleId() : null)
        .outboundScheduleDetailIds(outboundCart.stream().map(ci -> ci.scheduleDetailId).toList())
        .returnScheduleDetailIds(
            rbRoundTrip.isSelected() ? returnCart.stream().map(ci -> ci.scheduleDetailId).toList() : List.of())
        .outboundPassengers(outboundPassengerForms.stream().map(PassengerForm::toDTO).toList())
        .returnPassengers(
            rbRoundTrip.isSelected() ? returnPassengerForms.stream().map(PassengerForm::toDTO).toList() : List.of())
        .childrenUnder6(buildChildrenDTOs())
        .buyer(buildBuyerDTO())
        .vat(buildVatDTO())
        .redeemPoints(
            SaleRedeemPointsDTO.builder().redeemRequested(chkRedeemPoints.isSelected()).pointsToRedeem(redeem).build())
        .paymentMethod(PaymentMethod.ONLINE)
        .paymentOrderId(currentPayment != null ? currentPayment.getPaymentOrderId() : null)
        .build();
  }

  private Image toImage(byte[] pngBytes) {
    if (pngBytes == null || pngBytes.length == 0)
      return null;
    try {
      return new Image(new ByteArrayInputStream(pngBytes));
    } catch (Exception e) {
      return null;
    }
  }

  private void pollPaymentStatusOnce() {
    if (currentPayment == null)
      return;
    Task<Response> task = new Task<>() {
      @Override
      protected Response call() {
        return saleClientService.getPaymentOrderStatus(currentPayment.getPaymentOrderId());
      }
    };
    task.setOnSucceeded(e -> {
      Response res = task.getValue();
      if (res != null && res.isSuccess() && res.getData() instanceof PaymentStatusDTO dto) {
        lblPaymentStatus.setText("Trạng thái: " + dto.getStatus());
        status = dto.getStatus();
      }
    });
    start(task, "poll-payment");
  }

  @FXML
  public void handleConfirmPayment() {
    if (!rbPayOnline.isSelected()) {
      showWarning("Thanh toán", "Vui lòng chọn phương thức Online.");
      return;
    }
    if (currentPayment == null) {
      showWarning("Online", "Chưa có đơn thanh toán.");
      return;
    }
    setLoading(true);
    Task<Response> task = new Task<>() {
      @Override
      protected Response call() {
        return saleClientService.confirmInternalPayment(currentPayment.getPaymentOrderId(), clientSessionId);
      }
    };
    task.setOnSucceeded(e -> {
      setLoading(false);
      Response res = task.getValue();
      if (res != null && res.isSuccess() && res.getData() instanceof PaymentStatusDTO dto) {
        status = dto.getStatus();
        lblPaymentStatus.setText("Trạng thái: " + status);
      } else {
        showError("Online", res == null ? "Không có phản hồi" : res.getMessage());
      }
    });
    task.setOnFailed(e -> {
      setLoading(false);
      showError("Online", "Lỗi xác nhận thanh toán.");
    });
    start(task, "confirm-payment");
  }

  @FXML
  public void handleFinishSale() {
    if (currentStep != 4)
      return;

    double subtotal = computeSubtotalAfterTypeDiscount();
    int cap = ((SpinnerValueFactory.IntegerSpinnerValueFactory) spnPointsToRedeem.getValueFactory()).getMax();
    int redeem = (chkRedeemPoints.isSelected() && cap > 0) ? spnPointsToRedeem.getValue() : 0;
    double total = Math.max(0, subtotal - redeem * 1000.0);

    PaymentMethod pm = rbPayOnline.isSelected() ? PaymentMethod.ONLINE : PaymentMethod.CASH;
    Double amountPaid = null;
    String paymentOrderId = null;
    if (pm == PaymentMethod.CASH) {
      double paid = parseMoney(txtAmountPaid.getText());
      if (paid < total) {
        showWarning("Thanh toán", "Tiền khách đưa chưa đủ.");
        return;
      }
      amountPaid = paid;
    } else {
      if (currentPayment == null) {
        handleCreatePayment();
        return;
      }
      if (status != PaymentStatus.SUCCESS) {
        openInternalPaymentDialog(currentPayment, total);
        return;
      }
      paymentOrderId = currentPayment.getPaymentOrderId();
    }

    if (isExchangeMode) {
      // GỌI API ĐỔI VÉ
      vn.edu.iuh.fit.common.dto.ExchangeTicketRequestDTO exReq = vn.edu.iuh.fit.common.dto.ExchangeTicketRequestDTO
          .builder()
          .oldTicketIds(exchangeOldTicketIds)
          .newScheduleDetailIds(outboundCart.stream().map(CartItem::scheduleDetailId).toList())
          .employeeId(vn.edu.iuh.fit.client.session.ClientSessionContext.getInstance().getEmployeeId())
          .clientSessionId(clientSessionId)
          .build();

      setLoading(true);
      Task<Response> exTask = new Task<>() {
        @Override
        protected Response call() {
          return exchangeService.exchangeTickets(exReq);
        }
      };
      exTask.setOnSucceeded(e -> {
        setLoading(false);
        Response res = exTask.getValue();
        if (res != null && res.isSuccess()) {
          stopHoldKeepAlive();
          showInfo("Thành công", "Giao dịch đổi vé thành công!");
          btnFinishSale.setDisable(true);
          btnPrintTickets.setDisable(false);
        } else {
          showError("Lỗi đổi vé", res != null ? res.getMessage() : "Lỗi Server.");
        }
      });
      start(exTask, "finish-exchange");

    } else {
      // GỌI API BÁN VÉ GỐC
      SaleCreateRequestDTO request = SaleCreateRequestDTO.builder()
          .clientSessionId(clientSessionId)
          .ticketCategory(rbRoundTrip.isSelected() ? TicketCategory.ROUND_TRIP : TicketCategory.ONE_WAY)
          .outboundScheduleId(selectedOutbound.getScheduleId())
          .returnScheduleId(rbRoundTrip.isSelected() ? selectedReturn.getScheduleId() : null)
          .outboundScheduleDetailIds(outboundCart.stream().map(CartItem::scheduleDetailId).toList())
          .returnScheduleDetailIds(
              rbRoundTrip.isSelected() ? returnCart.stream().map(CartItem::scheduleDetailId).toList() : List.of())
          .outboundPassengers(outboundPassengerForms.stream().map(PassengerForm::toDTO).toList())
          .returnPassengers(
              rbRoundTrip.isSelected() ? returnPassengerForms.stream().map(PassengerForm::toDTO).toList() : List.of())
          .childrenUnder6(buildChildrenDTOs())
          .buyer(buildBuyerDTO())
          .vat(buildVatDTO())
          .redeemPoints(SaleRedeemPointsDTO.builder().redeemRequested(chkRedeemPoints.isSelected())
              .pointsToRedeem(redeem).build())
          .paymentMethod(pm)
          .amountPaid(amountPaid)
          .paymentOrderId(paymentOrderId)
          .build();

      submitSaleAsync(request, null);
    }
  }

  private void submitSaleAsync(SaleCreateRequestDTO request, Runnable afterSuccess) {
    setLoading(true);
    Task<Response> task = new Task<>() {
      @Override
      protected Response call() {
        return saleClientService.createSaleTransaction(request);
      }
    };
    task.setOnSucceeded(e -> {
      setLoading(false);
      Response res = task.getValue();
      if (res != null && res.isSuccess() && res.getData() instanceof SaleCreateResponseDTO dto) {
        stopHoldKeepAlive();
        lastSaleResult = dto;
        btnPrintTickets.setDisable(false);
        btnPrintInvoice.setDisable(false);
        btnPrintChildVouchers.setDisable(dto.getChildVouchers() == null || dto.getChildVouchers().isEmpty());
        showInfo("Bán vé", "Thanh toán thành công. Hóa đơn: " + dto.getInvoiceId());
        if (afterSuccess != null) {
          afterSuccess.run();
        }
      } else {
        showError("Bán vé", res == null ? "Không có phản hồi" : res.getMessage());
      }
    });
    task.setOnFailed(e -> {
      setLoading(false);
      showError("Bán vé", "Lỗi khi bán vé.");
    });
    start(task, "finish-sale");
  }

  private SaleBuyerDTO buildBuyerDTO() {
    return SaleBuyerDTO.builder()
        .buyerName(normalize(txtBuyerName.getText()))
        .buyerEmail(normalize(txtBuyerEmail.getText()))
        .buyerPhone(normalize(txtBuyerPhone.getText()))
        .documentType(cbBuyerDocType.getValue())
        .documentNumber(normalize(txtBuyerDocNumber.getText()))
        .hasAccount(chkHasAccount.isSelected())
        .customerId(chkHasAccount.isSelected() ? selectedCustomerId : null)
        .build();
  }

  private SaleVatDTO buildVatDTO() {
    String company = normalize(txtVatCompany.getText());
    String tax = normalize(txtVatTaxCode.getText());
    String addr = normalize(txtVatAddress.getText());
    if (company == null && tax == null && addr == null)
      return null;
    return SaleVatDTO.builder().companyName(company).taxCode(tax).address(addr).build();
  }

  private List<SaleChildUnder6DTO> buildChildrenDTOs() {
    List<SaleChildUnder6DTO> list = new ArrayList<>();
    for (int i = 0; i < outboundPassengerForms.size(); i++) {
      PassengerForm adult = outboundPassengerForms.get(i);
      for (ChildForm child : adult.children) {
        list.add(SaleChildUnder6DTO.builder()
            .childName(normalize(child.nameField.getText()))
            .dateOfBirth(child.dobPicker.getValue())
            .accompanyDirection(TripDirection.OUTBOUND)
            .accompanyPassengerIndex(i)
            .build());
      }
    }
    if (rbRoundTrip.isSelected()) {
      for (int i = 0; i < returnPassengerForms.size(); i++) {
        PassengerForm adult = returnPassengerForms.get(i);
        for (ChildForm child : adult.children) {
          list.add(SaleChildUnder6DTO.builder()
              .childName(normalize(child.nameField.getText()))
              .dateOfBirth(child.dobPicker.getValue())
              .accompanyDirection(TripDirection.RETURN)
              .accompanyPassengerIndex(i)
              .build());
        }
      }
    }
    return list;
  }

  @FXML
  public void handlePrintTickets() {
    if (lastSaleResult == null || lastSaleResult.getTickets() == null)
      return;
    openPreview("In vé", lastSaleResult.getTickets());
  }

  @FXML
  public void handlePrintInvoice() {
    if (lastSaleResult == null)
      return;
    // Invoice preview hiện tại dùng 1 trang tóm tắt (không bám sát mẫu VAT ảnh), sẽ
    // nâng cấp sau nếu cần.
    IssuedTicketDTO summary = IssuedTicketDTO.builder()
        .ticketId("INVOICE-" + lastSaleResult.getInvoiceId())
        .passengerName(normalize(txtBuyerName.getText()))
        .passengerDocument(normalize(txtBuyerDocNumber.getText()))
        .trainCode(rbRoundTrip.isSelected() ? "ROUND_TRIP" : "ONE_WAY")
        .departureStation(selectedOutbound != null ? selectedOutbound.getDepartureStationName() : "")
        .destinationStation(selectedOutbound != null ? selectedOutbound.getDestinationStationName() : "")
        .departureTime(LocalDateTime.now())
        .carriageName("")
        .seatNumber("")
        .ticketType(TicketType.NORMAL)
        .price(lastSaleResult.getTotalAmount())
        .qrCode(lastSaleResult.getInvoiceId())
        .build();
    openPreview("In hóa đơn (tóm tắt)", List.of(summary));
  }

  @FXML
  public void handlePrintChildVouchers() {
    if (lastSaleResult == null || lastSaleResult.getChildVouchers() == null)
      return;
    openPreview("In phiếu trẻ <6", lastSaleResult.getChildVouchers());
  }

  private void openPreview(String title, List<IssuedTicketDTO> tickets) {
    try {
      FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/ui/views/pdf-viewer.fxml"));
      Parent root = loader.load();
      Object controller = loader.getController();
      if (controller instanceof PdfViewerController pdf) {
        Task<List<Image>> renderTask = new Task<>() {
          @Override
          protected List<Image> call() {
            return tickets.stream().map(t -> TicketRenderer.renderToImage(t, title)).toList();
          }
        };
        renderTask.setOnSucceeded(e -> pdf.setPages(renderTask.getValue(), title));
        renderTask.setOnFailed(
            e -> showError("In", "KhÃ´ng thá»ƒ render xem trÆ°á»›c: " + renderTask.getException().getMessage()));
        start(renderTask, "render-preview");
      }
      Stage stage = new Stage();
      stage.setTitle(title);
      stage.initModality(Modality.APPLICATION_MODAL);
      stage.setScene(new Scene(root, 1000, 800));
      stage.show();
    } catch (Exception e) {
      showError("In", "Không thể mở xem trước: " + e.getMessage());
    }
  }

  private void updateStepUI() {
    step1Pane.setVisible(currentStep == 1);
    step1Pane.setManaged(currentStep == 1);
    step2Pane.setVisible(currentStep == 2);
    step2Pane.setManaged(currentStep == 2);
    step3Pane.setVisible(currentStep == 3);
    step3Pane.setManaged(currentStep == 3);
    step4Pane.setVisible(currentStep == 4);
    step4Pane.setManaged(currentStep == 4);

    btnBack.setDisable(currentStep == 1);
    btnNext.setDisable(currentStep == 4);

    styleStep(lblStep1, currentStep == 1);
    styleStep(lblStep2, currentStep == 2);
    styleStep(lblStep3, currentStep == 3);
    styleStep(lblStep4, currentStep == 4);

    if (currentStep == 4) {
      refreshTotals();
    }
  }

  private void styleStep(Label label, boolean active) {
    if (active) {
      label.setStyle(
          "-fx-background-color: #0066cc; -fx-text-fill: white; -fx-padding: 6 10; -fx-background-radius: 10;");
    } else {
      label.setStyle(
          "-fx-background-color: #ecf0f1; -fx-text-fill: #1f2d3d; -fx-padding: 6 10; -fx-background-radius: 10;");
    }
  }

  private void setLoading(boolean loading) {
    if (loadingOverlay != null) {
      loadingOverlay.setVisible(loading);
      loadingOverlay.setManaged(loading);
    }
  }

  private void start(Task<?> task, String name) {
    executor.execute(() -> {
      String oldName = Thread.currentThread().getName();
      Thread.currentThread().setName(name);
      try {
        task.run();
      } finally {
        Thread.currentThread().setName(oldName);
      }
    });
  }

  private String safe(String value) {
    return value == null ? "--" : value;
  }

  private String formatDateTime(LocalDateTime dt) {
    return dt == null ? "--" : dt.format(DATE_TIME);
  }

  private String formatMoney(double v) {
    return MONEY.format(Math.round(v)) + " đ";
  }

  private double parseMoney(String text) {
    if (text == null)
      return 0;
    String cleaned = text.replaceAll("[^0-9]", "");
    if (cleaned.isEmpty())
      return 0;
    try {
      return Double.parseDouble(cleaned);
    } catch (Exception e) {
      return 0;
    }
  }

  private int ageAt(LocalDate dob, LocalDateTime departureTime) {
    if (dob == null || departureTime == null)
      return 0;
    return Period.between(dob, departureTime.toLocalDate()).getYears();
  }

  private String normalize(String value) {
    if (value == null)
      return null;
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
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

  public void initExchangeMode(List<String> oldTicketIds) {
    this.isExchangeMode = true;
    this.exchangeOldTicketIds = oldTicketIds;

    // Khóa UI: Đổi vé chỉ đổi 1 chiều, không mua khứ hồi
    rbRoundTrip.setDisable(true);
    rbOneWay.setSelected(true);

    showInfo("Chế độ Đổi vé", "Hệ thống ghi nhận bạn đang đổi " + oldTicketIds.size()
        + " vé.\nVui lòng chọn chính xác " + oldTicketIds.size() + " ghế mới.");
  }

  private record CartItem(String scheduleDetailId, int carriageNumber, int seatNumber, double price) {
    String carriageLabel() {
      return "Toa " + carriageNumber;
    }

    String seatLabel() {
      return String.valueOf(seatNumber);
    }
  }

  private record Pricing(double discount, double finalPrice) {
  }

  private static class SummaryRow {
    final String leg;
    final String seat;
    final String name;
    final String type;
    final double base;
    final double discount;
    final double finalPrice;

    SummaryRow(String leg, String seat, String name, String type, double base, double discount, double finalPrice) {
      this.leg = leg;
      this.seat = seat;
      this.name = name;
      this.type = type;
      this.base = base;
      this.discount = discount;
      this.finalPrice = finalPrice;
    }
  }

  private static class ChildForm {
    final VBox root;
    final TextField nameField;
    final DatePicker dobPicker;

    ChildForm(VBox root, TextField nameField, DatePicker dobPicker) {
      this.root = root;
      this.nameField = nameField;
      this.dobPicker = dobPicker;
    }
  }

  private static class PassengerForm {
    final VBox root;
    final CartItem item;
    final TextField nameField;
    final ComboBox<DocumentType> docTypeBox;
    final TextField docNumberField;
    final ComboBox<TicketType> typeBox;
    final DatePicker dobPicker;
    final CheckBox chkStudentVerified;
    final Button btnAddChild;
    final VBox childrenBox;
    final List<ChildForm> children = new ArrayList<>();

    PassengerForm(VBox root, CartItem item, TextField nameField, ComboBox<DocumentType> docTypeBox,
        TextField docNumberField,
        ComboBox<TicketType> typeBox, DatePicker dobPicker, CheckBox chkStudentVerified, Button btnAddChild,
        VBox childrenBox) {
      this.root = root;
      this.item = item;
      this.nameField = nameField;
      this.docTypeBox = docTypeBox;
      this.docNumberField = docNumberField;
      this.typeBox = typeBox;
      this.dobPicker = dobPicker;
      this.chkStudentVerified = chkStudentVerified;
      this.btnAddChild = btnAddChild;
      this.childrenBox = childrenBox;
    }

    static PassengerForm create(String leg, int index, CartItem item) {
      VBox root = new VBox(8);
      root.setStyle(
          "-fx-background-color: white; -fx-border-color: #d5dde6; -fx-border-radius: 10; -fx-background-radius: 10; -fx-padding: 12;");

      Label title = new Label(leg + " • " + item.carriageLabel() + " - Ghế " + item.seatLabel());
      title.setStyle("-fx-font-weight: 800;");

      GridPane grid = new GridPane();
      grid.setHgap(10);
      grid.setVgap(10);

      TextField nameField = new TextField();
      ComboBox<DocumentType> docType = new ComboBox<>(
          FXCollections.observableArrayList(DocumentType.ID_CARD, DocumentType.PASSPORT));
      docType.getSelectionModel().select(DocumentType.ID_CARD);
      TextField docNumber = new TextField();
      ComboBox<TicketType> typeBox = new ComboBox<>(FXCollections.observableArrayList(TicketType.NORMAL,
          TicketType.CHILD, TicketType.SENIOR, TicketType.STUDENT));
      typeBox.getSelectionModel().select(TicketType.NORMAL);
      DatePicker dob = new DatePicker();
      dob.setDisable(true);
      CheckBox chkStudent = new CheckBox("Đã kiểm tra thẻ HSSV còn hạn");
      chkStudent.setVisible(false);
      chkStudent.setManaged(false);

      grid.add(new Label("Họ tên:"), 0, 0);
      grid.add(nameField, 1, 0);
      grid.add(new Label("Giấy tờ:"), 2, 0);
      HBox docBox = new HBox(8, docType, docNumber);
      grid.add(docBox, 3, 0);

      grid.add(new Label("Đối tượng:"), 0, 1);
      grid.add(typeBox, 1, 1);
      grid.add(new Label("Ngày sinh:"), 2, 1);
      grid.add(dob, 3, 1);

      grid.add(chkStudent, 1, 2, 3, 1);

      Button btnAddChild = new Button("Thêm trẻ <6 đi kèm");
      btnAddChild.getStyleClass().add("secondary-button");
      VBox childrenBox = new VBox(8);
      childrenBox.setVisible(true);

      typeBox.valueProperty().addListener((obs, old, v) -> {
        boolean needDob = v == TicketType.CHILD || v == TicketType.SENIOR;
        dob.setDisable(!needDob);
        boolean isStudent = v == TicketType.STUDENT;
        chkStudent.setVisible(isStudent);
        chkStudent.setManaged(isStudent);
      });

      root.getChildren().addAll(title, grid, btnAddChild, childrenBox);

      PassengerForm form = new PassengerForm(root, item, nameField, docType, docNumber, typeBox, dob, chkStudent,
          btnAddChild, childrenBox);
      btnAddChild.setOnAction(e -> form.addChildRow());
      return form;
    }

    void addChildRow() {
      VBox row = new VBox(6);
      row.setStyle(
          "-fx-background-color: #f6f8fb; -fx-border-color: #d5dde6; -fx-border-radius: 10; -fx-background-radius: 10; -fx-padding: 10;");
      TextField name = new TextField();
      DatePicker dob = new DatePicker();
      Button remove = new Button("Xóa");
      remove.getStyleClass().add("danger-button");
      HBox header = new HBox(10, new Label("Trẻ <6:"), remove);
      row.getChildren().addAll(header, new Label("Họ tên:"), name, new Label("Ngày sinh:"), dob);
      ChildForm cf = new ChildForm(row, name, dob);
      children.add(cf);
      childrenBox.getChildren().add(row);
      remove.setOnAction(e -> {
        children.remove(cf);
        childrenBox.getChildren().remove(row);
      });
    }

    String validate(LocalDateTime departureTime) {
      String name = normalize(nameField.getText());
      if (name == null)
        return "Vui lòng nhập họ tên hành khách.";
      DocumentType docType = docTypeBox.getValue();
      String doc = normalize(docNumberField.getText());
      if (docType == null || doc == null)
        return "Vui lòng nhập giấy tờ hành khách.";
      if (docType == DocumentType.ID_CARD && !doc.matches("^\\d{9}$|^\\d{12}$"))
        return "CMND 9 số hoặc CCCD 12 số.";
      if (docType == DocumentType.PASSPORT && !doc.matches("^[A-Za-z0-9]{1,9}$"))
        return "Hộ chiếu tối đa 9 ký tự, chữ + số.";

      TicketType type = typeBox.getValue() != null ? typeBox.getValue() : TicketType.NORMAL;
      if ((type == TicketType.CHILD || type == TicketType.SENIOR) && dobPicker.getValue() == null) {
        return "Vui lòng chọn ngày sinh cho hành khách " + name + ".";
      }
      if (type == TicketType.CHILD) {
        int age = Period.between(dobPicker.getValue(), departureTime.toLocalDate()).getYears();
        if (age < 6)
          return "Trẻ <6 tuổi không được chọn ghế. Vui lòng thêm vào mục trẻ đi kèm.";
        if (age >= 10)
          return "Tuổi không hợp lệ cho vé trẻ em (6 đến <10).";
      }
      if (type == TicketType.SENIOR) {
        int age = Period.between(dobPicker.getValue(), departureTime.toLocalDate()).getYears();
        if (age < 60)
          return "Tuổi không hợp lệ cho vé người cao tuổi (>=60).";
      }
      if (type == TicketType.STUDENT && !chkStudentVerified.isSelected()) {
        return "Vui lòng xác nhận đã kiểm tra thẻ HSSV còn hạn.";
      }

      for (ChildForm child : children) {
        String cn = normalize(child.nameField.getText());
        if (cn == null)
          return "Vui lòng nhập họ tên trẻ <6 đi kèm.";
        if (child.dobPicker.getValue() == null)
          return "Vui lòng chọn ngày sinh trẻ <6 đi kèm.";
        int age = Period.between(child.dobPicker.getValue(), departureTime.toLocalDate()).getYears();
        if (age >= 6)
          return "Trẻ đi kèm phải <6 tuổi.";
      }
      return null;
    }

    SalePassengerDTO toDTO() {
      return SalePassengerDTO.builder()
          .passengerName(normalize(nameField.getText()))
          .documentType(docTypeBox.getValue())
          .documentNumber(normalize(docNumberField.getText()))
          .ticketType(typeBox.getValue())
          .dateOfBirth(dobPicker.getValue())
          .studentCardVerified(chkStudentVerified.isSelected())
          .build();
    }

    private static String normalize(String value) {
      if (value == null)
        return null;
      String trimmed = value.trim();
      return trimmed.isEmpty() ? null : trimmed;
    }
  }
}
