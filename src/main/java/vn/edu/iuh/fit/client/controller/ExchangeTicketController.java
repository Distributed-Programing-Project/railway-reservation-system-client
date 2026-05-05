package vn.edu.iuh.fit.client.controller;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
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
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.image.Image;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import javafx.util.Duration;
import vn.edu.iuh.fit.client.service.ExchangeTicketClientService;
import vn.edu.iuh.fit.client.service.SaleClientService;
import vn.edu.iuh.fit.client.session.ClientSessionContext;
import vn.edu.iuh.fit.common.constant.SeatAvailabilityStatus;
import vn.edu.iuh.fit.common.dto.CarriageSeatMapDTO;
import vn.edu.iuh.fit.common.dto.ExchangeEligibleTicketDTO;
import vn.edu.iuh.fit.common.dto.ExchangeTicketPreviewDTO;
import vn.edu.iuh.fit.common.dto.ExchangeTicketRequestDTO;
import vn.edu.iuh.fit.common.dto.ExchangeTicketResponseDTO;
import vn.edu.iuh.fit.common.dto.IssuedTicketDTO;
import vn.edu.iuh.fit.common.dto.SaleScheduleSearchDTO;
import vn.edu.iuh.fit.common.dto.SaleScheduleSearchResultDTO;
import vn.edu.iuh.fit.common.dto.ScheduleSaleCardDTO;
import vn.edu.iuh.fit.common.dto.SeatMapResponseDTO;
import vn.edu.iuh.fit.common.dto.SeatMapSeatDTO;
import vn.edu.iuh.fit.common.dto.SeatHoldResponseDTO;
import vn.edu.iuh.fit.common.dto.StationDTO;
import vn.edu.iuh.fit.common.response.Response;

public class ExchangeTicketController {

  private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
  private static final NumberFormat MONEY = NumberFormat.getInstance(new Locale("vi", "VN"));

  private final SaleClientService saleClientService = new SaleClientService();
  private final ExchangeTicketClientService exchangeClientService = new ExchangeTicketClientService();

  private final String clientSessionId = UUID.randomUUID().toString();
  private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
    Thread workerThread = new Thread(r, "exchange-ticket-worker");
    workerThread.setDaemon(true);
    return workerThread;
  });
  private final AtomicBoolean cleanupDone = new AtomicBoolean(false);

  private Timeline seatMapPoller;
  private Timeline holdKeepAlive;
  private boolean seatMapRefreshInFlight;

  private int currentStep = 1;

  private List<StationDTO> stations = List.of();
  private ScheduleSaleCardDTO selectedSchedule;
  private SeatMapResponseDTO seatMap;
  private final ObservableList<OldTicketRow> oldTickets = FXCollections.observableArrayList();
  private final ObservableList<SeatChoice> selectedSeats = FXCollections.observableArrayList();

  private ExchangeTicketPreviewDTO lastPreview;
  private ExchangeTicketResponseDTO lastResult;

  // Step indicator
  @FXML private Label lblStep1;
  @FXML private Label lblStep2;
  @FXML private Label lblStep3;
  @FXML private Label lblStep4;

  // Navigation
  @FXML private Button btnBack;
  @FXML private Button btnNext;
  @FXML private Button btnResetAll;

  // Loading
  @FXML private StackPane loadingOverlay;

  // Step panes
  @FXML private VBox step1Pane;
  @FXML private VBox step2Pane;
  @FXML private VBox step3Pane;
  @FXML private VBox step4Pane;

  // Step 1
  @FXML private TextField txtIdCard;
  @FXML private Button btnSearchOldTickets;
  @FXML private TableView<OldTicketRow> tblOldTickets;
  @FXML private TableColumn<OldTicketRow, Boolean> colSelect;
  @FXML private TableColumn<OldTicketRow, String> colOldTicketId;
  @FXML private TableColumn<OldTicketRow, String> colOldTrain;
  @FXML private TableColumn<OldTicketRow, String> colOldRoute;
  @FXML private TableColumn<OldTicketRow, String> colOldDeparture;
  @FXML private TableColumn<OldTicketRow, String> colOldSeat;
  @FXML private TableColumn<OldTicketRow, String> colOldPrice;
  @FXML private TableColumn<OldTicketRow, String> colEligible;
  @FXML private TableColumn<OldTicketRow, String> colReason;
  @FXML private Label lblOldSelectedCount;

  // Step 2
  @FXML private ComboBox<StationDTO> cbDepartureStation;
  @FXML private ComboBox<StationDTO> cbDestinationStation;
  @FXML private DatePicker dpDepartureDate;
  @FXML private Button btnSearchSchedules;
  @FXML private TableView<ScheduleSaleCardDTO> tblSchedules;
  @FXML private TableColumn<ScheduleSaleCardDTO, String> colScheduleTrain;
  @FXML private TableColumn<ScheduleSaleCardDTO, String> colScheduleRoute;
  @FXML private TableColumn<ScheduleSaleCardDTO, String> colScheduleDeparture;
  @FXML private TableColumn<ScheduleSaleCardDTO, String> colScheduleArrival;
  @FXML private TableColumn<ScheduleSaleCardDTO, String> colScheduleSeats;
  @FXML private ComboBox<CarriageSeatMapDTO> cbCarriage;
  @FXML private GridPane gridSeats;
  @FXML private TableView<SeatChoice> tblSelectedSeats;
  @FXML private TableColumn<SeatChoice, String> colNewSeat;
  @FXML private TableColumn<SeatChoice, String> colNewPrice;
  @FXML private TableColumn<SeatChoice, SeatChoice> colNewAction;
  @FXML private Label lblSeatSelectedCount;

  // Step 3
  @FXML private Button btnPreviewFee;
  @FXML private Label lblTotalOld;
  @FXML private Label lblTotalNew;
  @FXML private Label lblFeeTotal;
  @FXML private Label lblDiff;
  @FXML private Label lblTotalAmount;
  @FXML private TextField txtVatTaxCode;
  @FXML private TextField txtVatCompanyName;
  @FXML private CheckBox chkPaymentConfirmed;
  @FXML private Button btnConfirmExchange;
  @FXML private Label lblExchangeStatus;

  // Step 4
  @FXML private Label lblInvoiceId;
  @FXML private Label lblFinalAmount;
  @FXML private Button btnPrintNewTickets;
  @FXML private Button btnPrintExchangeInvoice;

  @FXML
  public void initialize() {
    setupOldTicketsTable();
    setupSchedulesTable();
    setupSelectedSeatsTable();
    setupStationCombos();
    setupCarriageCombo();

    btnBack.setOnAction(e -> back());
    btnNext.setOnAction(e -> next());

    btnSearchOldTickets.setOnAction(e -> handleSearchOldTickets());
    btnSearchSchedules.setOnAction(e -> handleSearchSchedules());
    btnPreviewFee.setOnAction(e -> handlePreviewFee());
    btnConfirmExchange.setOnAction(e -> handleConfirmExchange());
    btnResetAll.setOnAction(e -> handleResetAll());
    btnPrintNewTickets.setOnAction(e -> handlePrintNewTickets());
    btnPrintExchangeInvoice.setOnAction(e -> handlePrintExchangeInvoice());

    dpDepartureDate.setValue(LocalDate.now());
    setLoading(false);
    updateStepUI();
    registerCloseCleanupHook();
    loadStationsAsync();
  }

  private void setupOldTicketsTable() {
    tblOldTickets.setItems(oldTickets);

    colSelect.setCellValueFactory(param -> param.getValue().selectedProperty());
    colSelect.setCellFactory(CheckBoxTableCell.forTableColumn(colSelect));
    colSelect.setEditable(true);
    tblOldTickets.setEditable(true);

    colOldTicketId.setCellValueFactory(param -> new ReadOnlyStringWrapper(param.getValue().dto.getTicketId()));
    colOldTrain.setCellValueFactory(param -> new ReadOnlyStringWrapper(safe(param.getValue().dto.getTrainCode())));
    colOldRoute.setCellValueFactory(param -> new ReadOnlyStringWrapper(
        safe(param.getValue().dto.getDepartureStation()) + " → " + safe(param.getValue().dto.getDestinationStation())));
    colOldDeparture.setCellValueFactory(param -> new ReadOnlyStringWrapper(formatDateTime(param.getValue().dto.getDepartureTime())));
    colOldSeat.setCellValueFactory(param -> new ReadOnlyStringWrapper(
        safe(param.getValue().dto.getCarriageName()) + "-" + safe(param.getValue().dto.getSeatNumber())));
    colOldPrice.setCellValueFactory(param -> new ReadOnlyStringWrapper(formatMoney(param.getValue().dto.getTicketPrice())));
    colEligible.setCellValueFactory(param -> new ReadOnlyStringWrapper(param.getValue().dto.isEligible() ? "OK" : "NO"));
    colReason.setCellValueFactory(param -> new ReadOnlyStringWrapper(safe(param.getValue().dto.getIneligibleReason())));

    oldTickets.addListener((javafx.collections.ListChangeListener<? super OldTicketRow>) c -> refreshOldSelectedCount());
  }

  private void setupSchedulesTable() {
    colScheduleTrain.setCellValueFactory(param -> new ReadOnlyStringWrapper(safe(param.getValue().getTrainCode())));
    colScheduleRoute.setCellValueFactory(param -> new ReadOnlyStringWrapper(
        safe(param.getValue().getDepartureStationName()) + " → " + safe(param.getValue().getDestinationStationName())));
    colScheduleDeparture.setCellValueFactory(param -> new ReadOnlyStringWrapper(formatDateTime(param.getValue().getDepartureTime())));
    colScheduleArrival.setCellValueFactory(param -> new ReadOnlyStringWrapper(formatDateTime(param.getValue().getArrivalTime())));
    colScheduleSeats.setCellValueFactory(param -> new ReadOnlyStringWrapper(String.valueOf(param.getValue().getAvailableSeats())));

    tblSchedules.getSelectionModel().selectedItemProperty().addListener((obs, old, value) -> {
      selectedSchedule = value;
      if (value != null) {
        loadSeatMapAsync(value.getScheduleId());
      }
    });
  }

  private void setupSelectedSeatsTable() {
    tblSelectedSeats.setItems(selectedSeats);
    colNewSeat.setCellValueFactory(param -> new ReadOnlyStringWrapper(param.getValue().label()));
    colNewPrice.setCellValueFactory(param -> new ReadOnlyStringWrapper(formatMoney(param.getValue().price())));
    colNewAction.setCellValueFactory(param -> new ReadOnlyObjectWrapper<>(param.getValue()));
    colNewAction.setCellFactory(col -> new TableCell<>() {
      private final Button btn = new Button("Bỏ");

      @Override
      protected void updateItem(SeatChoice item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
          setGraphic(null);
          return;
        }
        btn.getStyleClass().setAll("secondary-button");
        btn.setOnAction(e -> removeSeatChoice(item));
        setGraphic(btn);
      }
    });

    selectedSeats.addListener((javafx.collections.ListChangeListener<? super SeatChoice>) c -> refreshSeatSelectedCount());
  }

  private void setupStationCombos() {
    cbDepartureStation.setConverter(new StationStringConverter());
    cbDestinationStation.setConverter(new StationStringConverter());
  }

  private void setupCarriageCombo() {
    cbCarriage.setConverter(new javafx.util.StringConverter<>() {
      @Override
      public String toString(CarriageSeatMapDTO object) {
        if (object == null) return "";
        return "Toa " + object.getCarriageNumber() + " (" + (object.getCarriageType() != null ? object.getCarriageType().name() : "--") + ")";
      }

      @Override
      public CarriageSeatMapDTO fromString(String string) {
        return null;
      }
    });
    cbCarriage.valueProperty().addListener((obs, old, value) -> renderSeatGrid(value));
  }

  private void registerCloseCleanupHook() {
    if (step1Pane == null) return;
    step1Pane.sceneProperty().addListener((obs, oldScene, newScene) -> {
      if (newScene == null) return;

      Window existing = newScene.getWindow();
      if (existing != null) {
        existing.addEventHandler(WindowEvent.WINDOW_HIDDEN, e -> cleanupOnClose());
      }

      newScene.windowProperty().addListener((obsWin, oldWin, newWin) -> {
        if (newWin == null) return;
        newWin.addEventHandler(WindowEvent.WINDOW_HIDDEN, e -> cleanupOnClose());
      });
    });
  }

  private void cleanupOnClose() {
    if (!cleanupDone.compareAndSet(false, true)) return;
    stopSeatMapPolling();
    stopHoldKeepAlive();
    releaseAllHeldSeats();
    executor.shutdown();
  }

  private void handleSearchOldTickets() {
    String idCard = normalize(txtIdCard.getText());
    if (idCard == null) {
      showWarning("Đổi vé", "Vui lòng nhập CCCD/Hộ chiếu.");
      return;
    }

    setLoading(true);
    Task<Response> task = new Task<>() {
      @Override
      protected Response call() {
        return exchangeClientService.searchTicketsForExchange(idCard);
      }
    };
    task.setOnSucceeded(e -> {
      setLoading(false);
      Response res = task.getValue();
      if (res != null && res.isSuccess() && res.getData() instanceof List<?> list) {
        oldTickets.clear();
        for (Object o : list) {
          if (o instanceof ExchangeEligibleTicketDTO dto) {
            OldTicketRow row = new OldTicketRow(dto);
            row.selectedProperty().addListener((obs, old, value) -> {
              refreshOldSelectedCount();
              refreshSeatSelectedCount();
            });
            oldTickets.add(row);
          }
        }
        refreshOldSelectedCount();
      } else {
        showError("Đổi vé", res == null ? "Không nhận được phản hồi." : res.getMessage());
      }
    });
    task.setOnFailed(e -> {
      setLoading(false);
      showError("Đổi vé", "Lỗi khi tra cứu vé.");
    });
    start(task, "exchange-search-old-tickets");
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
        List<StationDTO> parsed = new ArrayList<>();
        for (Object o : list) {
          if (o instanceof StationDTO s) parsed.add(s);
        }
        stations = parsed;
        cbDepartureStation.setItems(FXCollections.observableArrayList(stations));
        cbDestinationStation.setItems(FXCollections.observableArrayList(stations));
      }
    });
    task.setOnFailed(e -> {
      setLoading(false);
      showError("Đổi vé", "Không thể tải danh sách ga.");
    });
    start(task, "exchange-load-stations");
  }

  private void handleSearchSchedules() {
    StationDTO dep = cbDepartureStation.getValue();
    StationDTO dest = cbDestinationStation.getValue();
    LocalDate date = dpDepartureDate.getValue();
    if (dep == null || dest == null || date == null) {
      showWarning("Đổi vé", "Vui lòng chọn ga đi/ga đến/ngày đi.");
      return;
    }
    if (Objects.equals(dep.getId(), dest.getId())) {
      showWarning("Đổi vé", "Ga đi và ga đến không được trùng nhau.");
      return;
    }

    setLoading(true);
    SaleScheduleSearchDTO dto = SaleScheduleSearchDTO.builder()
        .departureStationId(dep.getId())
        .destinationStationId(dest.getId())
        .departureDate(date)
        .ticketCategory(vn.edu.iuh.fit.common.constant.TicketCategory.ONE_WAY)
        .page(0)
        .size(30)
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
      if (res != null && res.isSuccess() && res.getData() instanceof SaleScheduleSearchResultDTO r) {
        tblSchedules.setItems(FXCollections.observableArrayList(r.getOutboundSchedules()));
      } else {
        showError("Đổi vé", res == null ? "Không nhận được phản hồi." : res.getMessage());
      }
    });
    task.setOnFailed(e -> {
      setLoading(false);
      showError("Đổi vé", "Lỗi khi tìm chuyến.");
    });
    start(task, "exchange-search-schedules");
  }

  private void loadSeatMapAsync(String scheduleId) {
    if (scheduleId == null) return;
    setLoading(true);
    Task<Response> task = new Task<>() {
      @Override
      protected Response call() {
        return saleClientService.getSeatMap(scheduleId, clientSessionId);
      }
    };
    task.setOnSucceeded(e -> {
      setLoading(false);
      Response res = task.getValue();
      if (res != null && res.isSuccess() && res.getData() instanceof SeatMapResponseDTO dto) {
        seatMap = dto;
        cbCarriage.setItems(FXCollections.observableArrayList(dto.getCarriages() != null ? dto.getCarriages() : List.of()));
        if (!cbCarriage.getItems().isEmpty()) {
          cbCarriage.getSelectionModel().select(0);
        }
        startSeatMapPolling();
      } else {
        showError("Đổi vé", res == null ? "Không nhận được phản hồi." : res.getMessage());
      }
    });
    task.setOnFailed(e -> {
      setLoading(false);
      showError("Đổi vé", "Lỗi khi tải sơ đồ ghế.");
    });
    start(task, "exchange-load-seatmap");
  }

  private void renderSeatGrid(CarriageSeatMapDTO carriage) {
    gridSeats.getChildren().clear();
    if (carriage == null || carriage.getSeats() == null) return;

    int cols = 8;
    int row = 0;
    int col = 0;
    for (SeatMapSeatDTO seat : carriage.getSeats()) {
      Button btn = new Button(String.valueOf(seat.getSeatNumber()));
      btn.setMinSize(46, 34);
      btn.setMaxSize(46, 34);

      boolean selected = selectedSeats.stream().anyMatch(s -> Objects.equals(s.scheduleDetailId(), seat.getScheduleDetailId()));
      SeatAvailabilityStatus status = seat.getSeatStatus() != null ? seat.getSeatStatus() : SeatAvailabilityStatus.AVAILABLE;

      if (status == SeatAvailabilityStatus.SOLD) {
        btn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-weight: 700;");
        btn.setDisable(true);
      } else if (status == SeatAvailabilityStatus.HELD && !seat.isHeldByMe()) {
        btn.setStyle("-fx-background-color: #f39c12; -fx-text-fill: white; -fx-font-weight: 700;");
        btn.setDisable(true);
      } else if (selected) {
        btn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: 700;");
      } else if (status == SeatAvailabilityStatus.HELD) {
        btn.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-font-weight: 700;");
      } else {
        btn.setStyle("-fx-background-color: #ecf0f1; -fx-text-fill: #1f2d3d; -fx-font-weight: 700;");
      }

      btn.setOnAction(e -> toggleSeat(seat));

      gridSeats.add(btn, col, row);
      col++;
      if (col >= cols) {
        col = 0;
        row++;
      }
    }
  }

  private void toggleSeat(SeatMapSeatDTO seat) {
    if (selectedSchedule == null || seat == null || seat.getScheduleDetailId() == null) return;
    int needed = getSelectedOldTicketIds().size();
    if (needed <= 0) {
      showWarning("Đổi vé", "Vui lòng chọn vé cũ trước khi chọn ghế mới.");
      return;
    }

    boolean already = selectedSeats.stream().anyMatch(s -> Objects.equals(s.scheduleDetailId(), seat.getScheduleDetailId()));
    if (already) {
      selectedSeats.stream()
          .filter(s -> Objects.equals(s.scheduleDetailId(), seat.getScheduleDetailId()))
          .findFirst()
          .ifPresent(this::removeSeatChoice);
      return;
    }

    if (selectedSeats.size() >= needed) {
      showWarning("Đổi vé", "Số ghế mới đã đủ theo số vé cần đổi.");
      return;
    }

    holdSeatAsync(seat);
  }

  private void holdSeatAsync(SeatMapSeatDTO seat) {
    setLoading(true);
    Task<Response> task = new Task<>() {
      @Override
      protected Response call() {
        return saleClientService.holdSeats(selectedSchedule.getScheduleId(), List.of(seat.getScheduleDetailId()), clientSessionId);
      }
    };
    task.setOnSucceeded(e -> {
      setLoading(false);
      Response res = task.getValue();
      if (res != null && res.isSuccess() && res.getData() instanceof SeatHoldResponseDTO dto) {
        if (dto.getSuccessIds() != null && dto.getSuccessIds().contains(seat.getScheduleDetailId())) {
          String label = "Toa " + safe(cbCarriage.getValue() != null ? String.valueOf(cbCarriage.getValue().getCarriageNumber()) : null)
              + " - Ghế " + seat.getSeatNumber();
          selectedSeats.add(new SeatChoice(seat.getScheduleDetailId(), label, seat.getSeatPrice()));
          startHoldKeepAlive();
          loadSeatMapAsync(selectedSchedule.getScheduleId());
        } else {
          showWarning("Giữ chỗ", "Không thể giữ ghế này (đã có người khác giữ/mua).");
        }
      } else {
        showError("Giữ chỗ", res == null ? "Không nhận được phản hồi." : res.getMessage());
      }
    });
    task.setOnFailed(e -> {
      setLoading(false);
      showError("Giữ chỗ", "Lỗi khi giữ ghế.");
    });
    start(task, "exchange-hold-seat");
  }

  private void removeSeatChoice(SeatChoice choice) {
    if (choice == null || selectedSchedule == null) return;
    selectedSeats.remove(choice);
    releaseSeatAsync(choice.scheduleDetailId());
  }

  private void releaseSeatAsync(String scheduleDetailId) {
    if (scheduleDetailId == null) return;
    Task<Response> task = new Task<>() {
      @Override
      protected Response call() {
        return saleClientService.releaseHeldSeats(selectedSchedule.getScheduleId(), List.of(scheduleDetailId), clientSessionId);
      }
    };
    task.setOnSucceeded(e -> loadSeatMapAsync(selectedSchedule.getScheduleId()));
    start(task, "exchange-release-seat");
  }

  private void startSeatMapPolling() {
    if (seatMapPoller != null || selectedSchedule == null) return;
    seatMapPoller = new Timeline(new KeyFrame(Duration.seconds(5), e -> pollSeatMapOnce()));
    seatMapPoller.setCycleCount(Timeline.INDEFINITE);
    seatMapPoller.play();
  }

  private void stopSeatMapPolling() {
    if (seatMapPoller != null) {
      seatMapPoller.stop();
      seatMapPoller = null;
    }
  }

  private void pollSeatMapOnce() {
    if (selectedSchedule == null) return;
    if (seatMapRefreshInFlight) return;
    seatMapRefreshInFlight = true;
    Task<Response> task = new Task<>() {
      @Override
      protected Response call() {
        return saleClientService.getSeatMap(selectedSchedule.getScheduleId(), clientSessionId);
      }
    };
    task.setOnSucceeded(e -> {
      seatMapRefreshInFlight = false;
      Response res = task.getValue();
      if (res != null && res.isSuccess() && res.getData() instanceof SeatMapResponseDTO dto) {
        seatMap = dto;
        cbCarriage.setItems(FXCollections.observableArrayList(dto.getCarriages() != null ? dto.getCarriages() : List.of()));
        CarriageSeatMapDTO selected = cbCarriage.getValue();
        if (selected == null && !cbCarriage.getItems().isEmpty()) {
          cbCarriage.getSelectionModel().select(0);
        } else {
          renderSeatGrid(cbCarriage.getValue());
        }
      }
    });
    task.setOnFailed(e -> seatMapRefreshInFlight = false);
    start(task, "exchange-poll-seatmap");
  }

  private void startHoldKeepAlive() {
    if (holdKeepAlive != null) return;
    holdKeepAlive = new Timeline(new KeyFrame(Duration.minutes(2), e -> renewHolds()));
    holdKeepAlive.setCycleCount(Timeline.INDEFINITE);
    holdKeepAlive.play();
  }

  private void stopHoldKeepAlive() {
    if (holdKeepAlive != null) {
      holdKeepAlive.stop();
      holdKeepAlive = null;
    }
  }

  private void renewHolds() {
    if (selectedSchedule == null || selectedSeats.isEmpty()) return;
    List<String> ids = selectedSeats.stream().map(SeatChoice::scheduleDetailId).toList();
    Task<Response> task = new Task<>() {
      @Override
      protected Response call() {
        return saleClientService.holdSeats(selectedSchedule.getScheduleId(), ids, clientSessionId);
      }
    };
    start(task, "exchange-hold-keepalive");
  }

  private void releaseAllHeldSeats() {
    if (selectedSchedule == null || selectedSeats.isEmpty()) return;
    List<String> ids = selectedSeats.stream().map(SeatChoice::scheduleDetailId).toList();
    Task<Response> task = new Task<>() {
      @Override
      protected Response call() {
        return saleClientService.releaseHeldSeats(selectedSchedule.getScheduleId(), ids, clientSessionId);
      }
    };
    start(task, "exchange-release-all");
  }

  private void handlePreviewFee() {
    List<String> oldIds = getSelectedOldTicketIds();
    List<String> newSdIds = selectedSeats.stream().map(SeatChoice::scheduleDetailId).toList();
    if (oldIds.isEmpty()) {
      showWarning("Đổi vé", "Vui lòng chọn vé cũ.");
      return;
    }
    if (newSdIds.size() != oldIds.size()) {
      showWarning("Đổi vé", "Vui lòng chọn đủ số ghế mới tương ứng số vé cũ.");
      return;
    }

    setLoading(true);
    Task<Response> task = new Task<>() {
      @Override
      protected Response call() {
        return exchangeClientService.previewExchangeTickets(oldIds, newSdIds, clientSessionId);
      }
    };
    task.setOnSucceeded(e -> {
      setLoading(false);
      Response res = task.getValue();
      if (res != null && res.isSuccess() && res.getData() instanceof ExchangeTicketPreviewDTO dto) {
        lastPreview = dto;
        renderPreview(dto);
        lblExchangeStatus.setText("OK");
      } else {
        showError("Đổi vé", res == null ? "Không nhận được phản hồi." : res.getMessage());
      }
    });
    task.setOnFailed(e -> {
      setLoading(false);
      showError("Đổi vé", "Lỗi khi tính phí.");
    });
    start(task, "exchange-preview-fee");
  }

  private void renderPreview(ExchangeTicketPreviewDTO dto) {
    lblTotalOld.setText(formatMoney(dto.getTotalOldPrice()));
    lblTotalNew.setText(formatMoney(dto.getTotalNewPrice()));
    lblFeeTotal.setText(formatMoney(dto.getExchangeFeeTotal()));
    lblDiff.setText(formatMoney(dto.getPriceDifference()));
    lblTotalAmount.setText(formatMoney(dto.getTotalAmount()));
  }

  private void handleConfirmExchange() {
    if (lastPreview == null) {
      showWarning("Đổi vé", "Vui lòng bấm 'Tính phí' trước.");
      return;
    }
    if (chkPaymentConfirmed != null && !chkPaymentConfirmed.isSelected()) {
      showWarning("Đổi vé", "Vui lòng xác nhận đã thu/hoàn tiền.");
      return;
    }

    String employeeId = ClientSessionContext.getInstance().getEmployeeId();
    if (employeeId == null || employeeId.isBlank()) {
      showError("Đổi vé", "Thiếu employeeId từ phiên đăng nhập. Vui lòng đăng nhập lại.");
      return;
    }

    List<String> oldIds = getSelectedOldTicketIds();
    List<String> newSdIds = selectedSeats.stream().map(SeatChoice::scheduleDetailId).toList();

    ExchangeTicketRequestDTO dto = ExchangeTicketRequestDTO.builder()
        .oldTicketIds(oldIds)
        .newScheduleDetailIds(newSdIds)
        .employeeId(employeeId)
        .clientSessionId(clientSessionId)
        .taxCode(normalize(txtVatTaxCode.getText()))
        .companyName(normalize(txtVatCompanyName.getText()))
        .build();

    setLoading(true);
    Task<Response> task = new Task<>() {
      @Override
      protected Response call() {
        return exchangeClientService.exchangeTickets(dto);
      }
    };
    task.setOnSucceeded(e -> {
      setLoading(false);
      Response res = task.getValue();
      if (res != null && res.isSuccess() && res.getData() instanceof ExchangeTicketResponseDTO r) {
        lastResult = r;
        lblInvoiceId.setText(safe(r.getInvoiceId()));
        lblFinalAmount.setText(formatMoney(r.getTotalAmount()));
        currentStep = 4;
        updateStepUI();
        stopSeatMapPolling();
        stopHoldKeepAlive();
        selectedSeats.clear();
        lblExchangeStatus.setText("Đổi vé thành công");
      } else {
        showError("Đổi vé", res == null ? "Không nhận được phản hồi." : res.getMessage());
      }
    });
    task.setOnFailed(e -> {
      setLoading(false);
      showError("Đổi vé", "Lỗi khi xác nhận đổi vé.");
    });
    start(task, "exchange-confirm");
  }

  private void handlePrintNewTickets() {
    if (lastResult == null || lastResult.getNewTickets() == null || lastResult.getNewTickets().isEmpty()) return;
    openPreview("In vé mới", lastResult.getNewTickets());
  }

  private void handlePrintExchangeInvoice() {
    if (lastResult == null) return;
    IssuedTicketDTO summary = IssuedTicketDTO.builder()
        .ticketId("EXCHANGE-" + lastResult.getInvoiceId())
        .passengerName(ClientSessionContext.getInstance().getUsername())
        .passengerDocument("")
        .trainCode("EXCHANGE")
        .departureStation("")
        .destinationStation("")
        .departureTime(LocalDateTime.now())
        .carriageName("")
        .seatNumber("")
        .ticketType(vn.edu.iuh.fit.common.constant.TicketType.NORMAL)
        .price(lastResult.getTotalAmount())
        .qrCode(lastResult.getInvoiceId())
        .build();
    openPreview("In biên lai đổi vé (tóm tắt)", List.of(summary));
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
        renderTask.setOnFailed(e -> showError("In", "Không thể render xem trước: " + renderTask.getException().getMessage()));
        start(renderTask, "exchange-render-preview");
      }
      javafx.stage.Stage stage = new javafx.stage.Stage();
      stage.setTitle(title);
      stage.initModality(Modality.APPLICATION_MODAL);
      stage.setScene(new Scene(root, 1000, 800));
      stage.show();
    } catch (Exception e) {
      showError("In", "Không thể mở xem trước: " + e.getMessage());
    }
  }

  private void back() {
    if (currentStep > 1) {
      currentStep--;
      updateStepUI();
    }
  }

  private void next() {
    if (currentStep == 1) {
      if (getSelectedOldTicketIds().isEmpty()) {
        showWarning("Đổi vé", "Vui lòng chọn ít nhất 1 vé đủ điều kiện.");
        return;
      }
    }
    if (currentStep == 2) {
      int needed = getSelectedOldTicketIds().size();
      if (selectedSeats.size() != needed) {
        showWarning("Đổi vé", "Vui lòng chọn đủ số ghế mới.");
        return;
      }
    }
    if (currentStep < 4) {
      currentStep++;
      updateStepUI();
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
  }

  private void styleStep(Label label, boolean active) {
    if (label == null) return;
    if (active) {
      label.setStyle("-fx-background-color: #0066cc; -fx-text-fill: white; -fx-padding: 6 10; -fx-background-radius: 10;");
    } else {
      label.setStyle("-fx-background-color: #ecf0f1; -fx-text-fill: #1f2d3d; -fx-padding: 6 10; -fx-background-radius: 10;");
    }
  }

  private void handleResetAll() {
    stopSeatMapPolling();
    stopHoldKeepAlive();
    releaseAllHeldSeats();
    selectedSchedule = null;
    seatMap = null;
    oldTickets.clear();
    selectedSeats.clear();
    lastPreview = null;
    lastResult = null;
    lblExchangeStatus.setText("");
    currentStep = 1;
    updateStepUI();
  }

  private List<String> getSelectedOldTicketIds() {
    return oldTickets.stream()
        .filter(OldTicketRow::isSelected)
        .filter(r -> r.dto.isEligible())
        .map(r -> r.dto.getTicketId())
        .filter(Objects::nonNull)
        .toList();
  }

  private void refreshOldSelectedCount() {
    int total = oldTickets.size();
    int selected = (int) oldTickets.stream().filter(OldTicketRow::isSelected).count();
    int eligibleSelected = getSelectedOldTicketIds().size();
    lblOldSelectedCount.setText("Đã chọn: " + selected + " (hợp lệ: " + eligibleSelected + ") / " + total);
  }

  private void refreshSeatSelectedCount() {
    int needed = getSelectedOldTicketIds().size();
    lblSeatSelectedCount.setText("Ghế đã chọn: " + selectedSeats.size() + " / " + needed);
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

  private String normalize(String value) {
    if (value == null) return null;
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
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

  private void stopSeatMapAndHold() {
    stopSeatMapPolling();
    stopHoldKeepAlive();
  }

  private static final class StationStringConverter extends javafx.util.StringConverter<StationDTO> {
    @Override
    public String toString(StationDTO object) {
      return object == null ? "" : object.getName();
    }

    @Override
    public StationDTO fromString(String string) {
      return null;
    }
  }

  private static final class OldTicketRow {
    private final ExchangeEligibleTicketDTO dto;
    private final BooleanProperty selected = new SimpleBooleanProperty(false);

    OldTicketRow(ExchangeEligibleTicketDTO dto) {
      this.dto = dto;
      if (dto != null && !dto.isEligible()) {
        selected.set(false);
      }
    }

    boolean isSelected() {
      return selected.get();
    }

    BooleanProperty selectedProperty() {
      return selected;
    }
  }

  private record SeatChoice(String scheduleDetailId, String label, double price) {
  }
}
