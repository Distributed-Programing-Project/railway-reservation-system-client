package vn.edu.iuh.fit.client.controller;

import java.text.NumberFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import vn.edu.iuh.fit.client.service.SaleClientService;
import vn.edu.iuh.fit.client.session.SaleWizardState;
import vn.edu.iuh.fit.client.session.SaleWizardState.SelectedSeatDraft;
import vn.edu.iuh.fit.common.constant.SeatAvailabilityStatus;
import vn.edu.iuh.fit.common.constant.TicketCategory;
import vn.edu.iuh.fit.common.constant.TripDirection;
import vn.edu.iuh.fit.common.dto.CarriageSeatMapDTO;
import vn.edu.iuh.fit.common.dto.ScheduleSaleCardDTO;
import vn.edu.iuh.fit.common.dto.SeatHoldResponseDTO;
import vn.edu.iuh.fit.common.dto.SeatMapResponseDTO;
import vn.edu.iuh.fit.common.dto.SeatMapSeatDTO;
import vn.edu.iuh.fit.common.response.Response;

public class Step2SeatSelectionController {
  private static final NumberFormat MONEY = NumberFormat.getInstance(new Locale("vi", "VN"));

  private static final String STYLE_AVAILABLE = "-fx-background-color: white; -fx-border-color: black; -fx-border-width: 0.5px; -fx-text-fill: black;";
  private static final String STYLE_SOLD = "-fx-background-color: #D90000; -fx-text-fill: white;";
  private static final String STYLE_SELECTED = "-fx-background-color: #008000; -fx-text-fill: white;";
  private static final String STYLE_HELD_OTHER = "-fx-background-color: #9e9e9e; -fx-text-fill: white;";

  @FXML
  private VBox toaContainer;

  @FXML
  private VBox leftSection;

  @FXML
  private Label labelTenTauDi;

  @FXML
  private Label labelKhoiHanhDi;

  @FXML
  private Label labelDenNoiDi;

  @FXML
  private VBox boxThongTinVe;

  @FXML
  private Label labelTenTauVe;

  @FXML
  private Label labelKhoiHanhVe;

  @FXML
  private Label labelDenNoiVe;

  @FXML
  private VBox toaSection;

  @FXML
  private ComboBox<CarriageSeatMapDTO> comboToa;

  @FXML
  private GridPane gridSeats;

  @FXML
  private VBox rightSection;

  @FXML
  private VBox ticketListContainer;

  @FXML
  private Label labelTongTien;

  @FXML
  private Button btnMuaVe;

  @FXML
  private Button btnQuayLai;

  private BanVeController coordinator;

  private final SaleClientService saleClientService = new SaleClientService();
  private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
    Thread t = new Thread(r, "step2-seat-worker");
    t.setDaemon(true);
    return t;
  });

  private Timeline seatMapPoller;
  private Timeline holdKeepAlive;
  private boolean seatMapRefreshInFlight;

  private ToggleButton btnOutbound;
  private ToggleButton btnReturn;

  private TextField txtQuickSelect;
  private Button btnQuickSelect;
  private Button btnClearAllActive;

  private SeatMapResponseDTO outboundSeatMap;
  private SeatMapResponseDTO returnSeatMap;

  private TripDirection activeDirection = TripDirection.OUTBOUND;

  private final Map<String, SeatMapSeatDTO> seatByScheduleDetailId = new HashMap<>();

  public void setCoordinator(BanVeController coordinator) {
    this.coordinator = coordinator;
  }

  public void initData() {
    setupTripInfo();
    setupCarriageCombo();
    ensureDynamicControls();
    loadSeatMapsForSelectedSchedules();
    refreshCartUI();
    startHoldKeepAlive();
    startSeatMapPolling();
  }

  @FXML
  public void initialize() {
    // no-op; initData is called by coordinator after view is shown
  }

  @FXML
  private void handleQuayLai() {
    releaseAllHeldSeatsAndClearState(() -> {
      if (coordinator != null) {
        coordinator.backFromStep2();
      }
    });
  }

  @FXML
  private void handleTiepTheo() {
    if (!validateBeforeNext()) {
      return;
    }
    // Step 3 is not rendering seat map; stop polling to avoid unnecessary refreshes.
    stopSeatMapPolling();
    if (coordinator != null) {
      // Step 3 remains stub in this phase.
      coordinator.nextFromStep2();
    }
  }

  private void setupTripInfo() {
    if (coordinator == null) {
      return;
    }
    SaleWizardState state = coordinator.getState();
    ScheduleSaleCardDTO outbound = state.getSelectedOutboundSchedule();
    ScheduleSaleCardDTO ret = state.getSelectedReturnSchedule();

    if (outbound != null) {
      labelTenTauDi.setText("Chiều đi: Tàu " + safe(outbound.getTrainCode()));
      labelKhoiHanhDi.setText(safe(outbound.getDepartureStationName()));
      labelDenNoiDi.setText(safe(outbound.getDestinationStationName()));
    }

    boolean roundTrip = state.getTicketCategory() == TicketCategory.ROUND_TRIP && ret != null;
    boxThongTinVe.setManaged(roundTrip);
    boxThongTinVe.setVisible(roundTrip);
    if (roundTrip) {
      labelTenTauVe.setText("Chiều về: Tàu " + safe(ret.getTrainCode()));
      labelKhoiHanhVe.setText(safe(ret.getDepartureStationName()));
      labelDenNoiVe.setText(safe(ret.getDestinationStationName()));
    }
  }

  private void setupCarriageCombo() {
    comboToa.setConverter(new StringConverter<>() {
      @Override
      public String toString(CarriageSeatMapDTO object) {
        if (object == null) {
          return "";
        }
        String typeName = object.getCarriageType() != null ? object.getCarriageType().getName() : "";
        if (typeName == null || typeName.isBlank()) {
          return "Toa " + object.getCarriageNumber();
        }
        return "Toa " + object.getCarriageNumber() + " - " + typeName;
      }

      @Override
      public CarriageSeatMapDTO fromString(String string) {
        return null;
      }
    });

    comboToa.valueProperty().addListener((obs, oldVal, newVal) -> {
      if (newVal == null) {
        gridSeats.getChildren().clear();
        return;
      }
      renderSeatsForActiveDirection(newVal);
    });
  }

  private void ensureDynamicControls() {
    if (btnOutbound != null) {
      return;
    }

    ToggleGroup dirGroup = new ToggleGroup();
    btnOutbound = new ToggleButton("Chiều đi");
    btnReturn = new ToggleButton("Chiều về");
    btnOutbound.setToggleGroup(dirGroup);
    btnReturn.setToggleGroup(dirGroup);
    btnOutbound.setSelected(true);

    btnOutbound.setOnAction(e -> switchDirection(TripDirection.OUTBOUND));
    btnReturn.setOnAction(e -> switchDirection(TripDirection.RETURN));

    HBox dirBox = new HBox(10, btnOutbound, btnReturn);
    dirBox.setAlignment(Pos.CENTER_LEFT);
    dirBox.setPadding(new Insets(0, 0, 10, 0));

    txtQuickSelect = new TextField();
    txtQuickSelect.setPromptText("SL");
    txtQuickSelect.setPrefWidth(50);

    btnQuickSelect = new Button("Chọn nhanh");
    btnQuickSelect.getStyleClass().add("btn-action-secondary");
    btnQuickSelect.setOnAction(e -> handleQuickSelect());

    btnClearAllActive = new Button("Hủy chọn (Xóa hết)");
    btnClearAllActive.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white;");
    btnClearAllActive.setOnAction(e -> clearAllSeatsForActiveDirection());

    HBox quickBox = new HBox(10,
        new Label("Chọn nhanh:"),
        txtQuickSelect,
        btnQuickSelect,
        btnClearAllActive);
    quickBox.setAlignment(Pos.CENTER_LEFT);

    // Insert at the top of the seat section.
    toaSection.getChildren().add(0, quickBox);
    toaSection.getChildren().add(0, dirBox);
  }

  private void switchDirection(TripDirection direction) {
    this.activeDirection = direction;
    boolean roundTrip = coordinator != null && coordinator.getState().getTicketCategory() == TicketCategory.ROUND_TRIP;
    btnReturn.setDisable(!roundTrip || returnSeatMap == null);

    List<CarriageSeatMapDTO> carriages = getActiveSeatMapCarriages();
    comboToa.setItems(FXCollections.observableArrayList(carriages));
    comboToa.getSelectionModel().selectFirst();
  }

  private void loadSeatMapsForSelectedSchedules() {
    if (coordinator == null) {
      return;
    }

    SaleWizardState state = coordinator.getState();
    ScheduleSaleCardDTO outbound = state.getSelectedOutboundSchedule();
    if (outbound == null) {
      showWarning("Sơ đồ ghế", "Chưa chọn chuyến chiều đi.");
      return;
    }

    String clientSessionId = state.getClientSessionId();

    Task<Response> outboundTask = new Task<>() {
      @Override
      protected Response call() {
        return saleClientService.getSeatMap(outbound.getScheduleId(), clientSessionId);
      }
    };

    outboundTask.setOnSucceeded(e -> {
      Response res = outboundTask.getValue();
      if (res != null && res.isSuccess() && res.getData() instanceof SeatMapResponseDTO sm) {
        outboundSeatMap = sm;
        // Default direction
        switchDirection(TripDirection.OUTBOUND);
      } else {
        showError("Sơ đồ ghế", res == null ? "Không có phản hồi" : res.getMessage());
      }
    });
    outboundTask.setOnFailed(e -> showError("Sơ đồ ghế", "Không thể tải sơ đồ ghế chiều đi."));
    executor.submit(outboundTask);

    boolean roundTrip = state.getTicketCategory() == TicketCategory.ROUND_TRIP && state.getSelectedReturnSchedule() != null;
    if (roundTrip) {
      ScheduleSaleCardDTO ret = state.getSelectedReturnSchedule();
      Task<Response> returnTask = new Task<>() {
        @Override
        protected Response call() {
          return saleClientService.getSeatMap(ret.getScheduleId(), clientSessionId);
        }
      };
      returnTask.setOnSucceeded(e -> {
        Response res = returnTask.getValue();
        if (res != null && res.isSuccess() && res.getData() instanceof SeatMapResponseDTO sm) {
          returnSeatMap = sm;
          btnReturn.setDisable(false);
        } else {
          // Keep outbound usable even if return fails.
          showError("Sơ đồ ghế", res == null ? "Không có phản hồi" : res.getMessage());
        }
      });
      returnTask.setOnFailed(e -> showError("Sơ đồ ghế", "Không thể tải sơ đồ ghế chiều về."));
      executor.submit(returnTask);
    }
  }

  private void renderSeatsForActiveDirection(CarriageSeatMapDTO carriage) {
    if (carriage == null) {
      return;
    }

    seatByScheduleDetailId.clear();
    List<SeatMapSeatDTO> seats = carriage.getSeats() == null ? List.of() : carriage.getSeats();
    seats.stream().filter(s -> s.getScheduleDetailId() != null).forEach(s -> seatByScheduleDetailId.put(s.getScheduleDetailId(), s));

    populateGridPane(gridSeats, seats);
  }

  private void populateGridPane(GridPane grid, List<SeatMapSeatDTO> seats) {
    grid.getChildren().clear();
    if (seats == null || seats.isEmpty()) {
      return;
    }

    List<SeatMapSeatDTO> sorted = new ArrayList<>(seats);
    sorted.sort(Comparator.comparingInt(SeatMapSeatDTO::getSeatNumber));

    final int NUM_ROWS_PER_BLOCK = 2;
    final int H_GAP_ROW_INDEX = 2;
    final int V_GAP_COL_WIDTH = 2;

    int totalSeats = sorted.size();
    int seatsLeft = (int) Math.ceil(totalSeats / 2.0);
    int seatsRight = totalSeats - seatsLeft;
    int q1Seats = (int) Math.ceil(seatsLeft / 2.0);
    int q2Seats = seatsLeft - q1Seats;
    int q3Seats = (int) Math.ceil(seatsRight / 2.0);
    int q4Seats = seatsRight - q3Seats;

    int colsQ1 = (int) Math.ceil((double) q1Seats / NUM_ROWS_PER_BLOCK);
    int colsQ2 = (int) Math.ceil((double) q2Seats / NUM_ROWS_PER_BLOCK);
    int colsLeft = Math.max(colsQ1, colsQ2);
    int colsQ3 = (int) Math.ceil((double) q3Seats / NUM_ROWS_PER_BLOCK);
    int colsQ4 = (int) Math.ceil((double) q4Seats / NUM_ROWS_PER_BLOCK);
    int colsRight = Math.max(colsQ3, colsQ4);

    if (q1Seats == 0 && q2Seats == 0) {
      colsLeft = 0;
    }
    if (q3Seats == 0 && q4Seats == 0) {
      colsRight = 0;
    }

    for (int index = 0; index < sorted.size(); index++) {
      SeatMapSeatDTO seat = sorted.get(index);
      Button btn = createSeatButton(seat);
      int idx = index;

      int r;
      int c;
      if (idx < q1Seats) {
        r = (colsLeft == 0) ? 0 : (idx / colsLeft);
        c = (colsLeft == 0) ? 0 : (idx % colsLeft);
      } else if (idx < q1Seats + q2Seats) {
        int qIdx = idx - q1Seats;
        r = (colsLeft == 0) ? H_GAP_ROW_INDEX : (qIdx / colsLeft) + H_GAP_ROW_INDEX;
        c = (colsLeft == 0) ? 0 : (qIdx % colsLeft);
      } else if (idx < q1Seats + q2Seats + q3Seats) {
        int qIdx = idx - q1Seats - q2Seats;
        r = (colsRight == 0) ? 0 : (qIdx / colsRight);
        c = (colsRight == 0) ? (colsLeft + V_GAP_COL_WIDTH) : (qIdx % colsRight) + colsLeft + V_GAP_COL_WIDTH;
      } else {
        int qIdx = idx - q1Seats - q2Seats - q3Seats;
        r = (colsRight == 0) ? H_GAP_ROW_INDEX : (qIdx / colsRight) + H_GAP_ROW_INDEX;
        c = (colsRight == 0) ? (colsLeft + V_GAP_COL_WIDTH) : (qIdx % colsRight) + colsLeft + V_GAP_COL_WIDTH;
      }

      grid.add(btn, c, r);
    }
  }

  private Button createSeatButton(SeatMapSeatDTO seat) {
    Button btn = new Button(String.valueOf(seat.getSeatNumber()));
    btn.setPrefSize(40, 40);

    SeatAvailabilityStatus status = seat.getSeatStatus() != null ? seat.getSeatStatus() : SeatAvailabilityStatus.AVAILABLE;
    boolean inCart = isSeatInCart(seat.getScheduleDetailId(), activeDirection);

    if (status == SeatAvailabilityStatus.SOLD) {
      btn.setStyle(STYLE_SOLD);
      btn.setDisable(true);
      return btn;
    }

    if (status == SeatAvailabilityStatus.HELD && !seat.isHeldByMe() && !inCart) {
      btn.setStyle(STYLE_HELD_OTHER);
      btn.setDisable(true);
      return btn;
    }

    btn.setStyle(inCart ? STYLE_SELECTED : STYLE_AVAILABLE);
    btn.setOnAction(e -> toggleSeatSelection(seat, btn));
    return btn;
  }

  private void toggleSeatSelection(SeatMapSeatDTO seat, Button btn) {
    if (coordinator == null || seat == null || seat.getScheduleDetailId() == null) {
      return;
    }

    if (isSeatInCart(seat.getScheduleDetailId(), activeDirection)) {
      releaseSeatAsync(seat, btn);
    } else {
      holdSeatAsync(seat, btn);
    }
  }

  private void holdSeatAsync(SeatMapSeatDTO seat, Button btn) {
    SaleWizardState state = coordinator.getState();
    ScheduleSaleCardDTO schedule = scheduleFor(activeDirection);
    if (schedule == null) {
      showWarning("Giữ chỗ", "Chưa chọn chuyến.");
      return;
    }

    List<SelectedSeatDraft> cart = cartFor(activeDirection);
    if (cart.size() >= 10) {
      showWarning("Giữ chỗ", "Mỗi lượt (" + (activeDirection == TripDirection.OUTBOUND ? "chiều đi" : "chiều về") + ") chỉ được mua tối đa 10 vé.");
      return;
    }

    String sdId = seat.getScheduleDetailId();
    Task<Response> task = new Task<>() {
      @Override
      protected Response call() {
        return saleClientService.holdSeats(schedule.getScheduleId(), List.of(sdId), state.getClientSessionId());
      }
    };

    task.setOnSucceeded(e -> {
      Response res = task.getValue();
      if (res != null && res.isSuccess() && res.getData() instanceof SeatHoldResponseDTO dto) {
        if (dto.getSuccessIds() != null && dto.getSuccessIds().contains(sdId)) {
          addSeatToCart(seat, dto.getExpiresAtEpochMillis());
          btn.setStyle(STYLE_SELECTED);
          refreshCartUI();
          return;
        }
        showWarning("Giữ chỗ", "Không thể giữ ghế đã chọn.");
      } else {
        showWarning("Giữ chỗ", res == null ? "Không có phản hồi" : res.getMessage());
      }
    });
    task.setOnFailed(e -> showWarning("Giữ chỗ", "Lỗi khi giữ ghế."));
    executor.submit(task);
  }

  private void releaseSeatAsync(SeatMapSeatDTO seat, Button btn) {
    SaleWizardState state = coordinator.getState();
    ScheduleSaleCardDTO schedule = scheduleFor(activeDirection);
    if (schedule == null) {
      showWarning("Bỏ giữ chỗ", "Chưa chọn chuyến.");
      return;
    }

    String sdId = seat.getScheduleDetailId();
    Task<Response> task = new Task<>() {
      @Override
      protected Response call() {
        return saleClientService.releaseHeldSeats(schedule.getScheduleId(), List.of(sdId), state.getClientSessionId());
      }
    };

    task.setOnSucceeded(e -> {
      Response res = task.getValue();
      if (res != null && res.isSuccess()) {
        removeSeatFromCart(sdId, activeDirection);
        btn.setStyle(STYLE_AVAILABLE);
        refreshCartUI();
      } else {
        showWarning("Bỏ giữ chỗ", res == null ? "Không có phản hồi" : res.getMessage());
      }
    });
    task.setOnFailed(e -> showWarning("Bỏ giữ chỗ", "Lỗi khi bỏ giữ ghế."));
    executor.submit(task);
  }

  private void handleQuickSelect() {
    if (coordinator == null) {
      return;
    }
    CarriageSeatMapDTO carriage = comboToa.getValue();
    if (carriage == null) {
      showWarning("Chọn nhanh", "Vui lòng chọn toa trước.");
      return;
    }

    String raw = txtQuickSelect.getText() == null ? "" : txtQuickSelect.getText().trim();
    if (!raw.matches("\\d+")) {
      showWarning("Chọn nhanh", "Vui lòng nhập số lượng hợp lệ.");
      return;
    }
    int count = Integer.parseInt(raw);
    if (count <= 0) {
      showWarning("Chọn nhanh", "Số lượng phải > 0.");
      return;
    }

    List<SelectedSeatDraft> cart = cartFor(activeDirection);
    if (cart.size() >= 10) {
      showWarning("Chọn nhanh", "Mỗi lượt chỉ được mua tối đa 10 vé.");
      return;
    }
    if (cart.size() + count > 10) {
      showWarning("Chọn nhanh", "Vượt quá giới hạn 10 vé. Hiện tại đã có: " + cart.size() + " vé.");
      return;
    }

    List<SeatMapSeatDTO> seats = carriage.getSeats() == null ? List.of() : new ArrayList<>(carriage.getSeats());
    seats.sort(Comparator.comparingInt(SeatMapSeatDTO::getSeatNumber));

    List<SeatMapSeatDTO> run = findContiguousAvailableRun(seats, count, activeDirection);
    if (run.size() < count) {
      showWarning("Chọn nhanh", "Không đủ ghế liền kề phù hợp trong toa này.");
      return;
    }

    ScheduleSaleCardDTO schedule = scheduleFor(activeDirection);
    if (schedule == null) {
      showWarning("Chọn nhanh", "Chưa chọn chuyến.");
      return;
    }

    List<String> sdIds = run.stream().map(SeatMapSeatDTO::getScheduleDetailId).filter(Objects::nonNull).toList();
    Task<Response> task = new Task<>() {
      @Override
      protected Response call() {
        return saleClientService.holdSeats(schedule.getScheduleId(), sdIds, coordinator.getState().getClientSessionId());
      }
    };

    task.setOnSucceeded(e -> {
      Response res = task.getValue();
      if (res != null && res.isSuccess() && res.getData() instanceof SeatHoldResponseDTO dto) {
        List<String> successIds = dto.getSuccessIds() == null ? List.of() : dto.getSuccessIds();
        if (successIds.isEmpty()) {
          showWarning("Chọn nhanh", "Không thể giữ ghế. Vui lòng thử lại.");
          return;
        }
        for (SeatMapSeatDTO s : run) {
          if (successIds.contains(s.getScheduleDetailId())) {
            addSeatToCart(s, dto.getExpiresAtEpochMillis());
          }
        }
        // Refresh grid styles by re-rendering current carriage
        renderSeatsForActiveDirection(carriage);
        refreshCartUI();
        if (successIds.size() < count) {
          showWarning("Chọn nhanh", "Chỉ giữ được " + successIds.size() + "/" + count + " ghế.");
        }
      } else {
        showWarning("Chọn nhanh", res == null ? "Không có phản hồi" : res.getMessage());
      }
    });
    task.setOnFailed(e -> showWarning("Chọn nhanh", "Lỗi khi chọn nhanh."));
    executor.submit(task);
  }

  private List<SeatMapSeatDTO> findContiguousAvailableRun(List<SeatMapSeatDTO> sortedSeats, int count, TripDirection direction) {
    List<SeatMapSeatDTO> best = List.of();
    List<SeatMapSeatDTO> current = new ArrayList<>();
    int lastSeatNumber = -1;

    for (SeatMapSeatDTO seat : sortedSeats) {
      if (seat == null || seat.getScheduleDetailId() == null) {
        continue;
      }
      boolean available = seat.getSeatStatus() == SeatAvailabilityStatus.AVAILABLE;
      boolean already = isSeatInCart(seat.getScheduleDetailId(), direction);
      if (!available || already) {
        current.clear();
        lastSeatNumber = -1;
        continue;
      }

      if (current.isEmpty()) {
        current.add(seat);
        lastSeatNumber = seat.getSeatNumber();
      } else if (seat.getSeatNumber() == lastSeatNumber + 1) {
        current.add(seat);
        lastSeatNumber = seat.getSeatNumber();
      } else {
        current.clear();
        current.add(seat);
        lastSeatNumber = seat.getSeatNumber();
      }

      if (current.size() == count) {
        return new ArrayList<>(current);
      }
    }
    return best;
  }

  private void clearAllSeatsForActiveDirection() {
    if (coordinator == null) {
      return;
    }
    List<SelectedSeatDraft> cart = cartFor(activeDirection);
    if (cart.isEmpty()) {
      return;
    }
    ScheduleSaleCardDTO schedule = scheduleFor(activeDirection);
    if (schedule == null) {
      return;
    }
    List<String> ids = cart.stream().map(SelectedSeatDraft::getScheduleDetailId).filter(Objects::nonNull).toList();
    Task<Response> task = new Task<>() {
      @Override
      protected Response call() {
        return saleClientService.releaseHeldSeats(schedule.getScheduleId(), ids, coordinator.getState().getClientSessionId());
      }
    };
    task.setOnSucceeded(e -> {
      Response res = task.getValue();
      if (res != null && res.isSuccess()) {
        cart.clear();
        refreshCartUI();
        CarriageSeatMapDTO carriage = comboToa.getValue();
        if (carriage != null) {
          renderSeatsForActiveDirection(carriage);
        }
      } else {
        showWarning("Hủy chọn", res == null ? "Không có phản hồi" : res.getMessage());
      }
    });
    task.setOnFailed(e -> showWarning("Hủy chọn", "Lỗi khi hủy chọn."));
    executor.submit(task);
  }

  private void addSeatToCart(SeatMapSeatDTO seat, Long expiresAtEpochMillis) {
    if (coordinator == null || seat == null || seat.getScheduleDetailId() == null) {
      return;
    }
    SaleWizardState state = coordinator.getState();
    ScheduleSaleCardDTO schedule = scheduleFor(activeDirection);
    if (schedule == null) {
      return;
    }

    CarriageSeatMapDTO carriage = comboToa.getValue();

    SelectedSeatDraft draft = new SelectedSeatDraft(activeDirection, schedule.getScheduleId(), seat.getScheduleDetailId());
    draft.setCarriageId(carriage != null ? carriage.getCarriageId() : null);
    draft.setCarriageNumber(carriage != null ? carriage.getCarriageNumber() : null);
    draft.setSeatId(seat.getSeatId());
    draft.setSeatNumber(seat.getSeatNumber());
    draft.setSeatType(seat.getSeatType());
    draft.setPrice(seat.getSeatPrice());
    draft.setHoldExpiresAtEpochMillis(expiresAtEpochMillis);

    List<SelectedSeatDraft> cart = cartFor(activeDirection);
    if (!containsScheduleDetailId(cart, seat.getScheduleDetailId())) {
      cart.add(draft);
    }
    // Keep selection in state for downstream steps.
    state.setSelectedOutboundSchedule(state.getSelectedOutboundSchedule());
  }

  private void removeSeatFromCart(String scheduleDetailId, TripDirection direction) {
    if (coordinator == null || scheduleDetailId == null) {
      return;
    }
    List<SelectedSeatDraft> cart = cartFor(direction);
    cart.removeIf(d -> Objects.equals(scheduleDetailId, d.getScheduleDetailId()));
  }

  private boolean isSeatInCart(String scheduleDetailId, TripDirection direction) {
    if (coordinator == null || scheduleDetailId == null) {
      return false;
    }
    return containsScheduleDetailId(cartFor(direction), scheduleDetailId);
  }

  private static boolean containsScheduleDetailId(List<SelectedSeatDraft> cart, String scheduleDetailId) {
    if (cart == null || cart.isEmpty()) {
      return false;
    }
    for (SelectedSeatDraft d : cart) {
      if (Objects.equals(scheduleDetailId, d.getScheduleDetailId())) {
        return true;
      }
    }
    return false;
  }

  private List<SelectedSeatDraft> cartFor(TripDirection direction) {
    SaleWizardState state = coordinator.getState();
    return direction == TripDirection.OUTBOUND ? state.getOutboundSeats() : state.getReturnSeats();
  }

  private ScheduleSaleCardDTO scheduleFor(TripDirection direction) {
    SaleWizardState state = coordinator.getState();
    return direction == TripDirection.OUTBOUND ? state.getSelectedOutboundSchedule() : state.getSelectedReturnSchedule();
  }

  private List<CarriageSeatMapDTO> getActiveSeatMapCarriages() {
    SeatMapResponseDTO sm = activeDirection == TripDirection.OUTBOUND ? outboundSeatMap : returnSeatMap;
    return sm != null && sm.getCarriages() != null ? sm.getCarriages() : List.of();
  }

  private void refreshCartUI() {
    if (coordinator == null) {
      return;
    }

    ticketListContainer.getChildren().clear();

    List<SelectedSeatDraft> outboundCart = coordinator.getState().getOutboundSeats();
    List<SelectedSeatDraft> returnCart = coordinator.getState().getReturnSeats();

    if (!outboundCart.isEmpty()) {
      ticketListContainer.getChildren().add(buildCartHeader("Chiều đi", TripDirection.OUTBOUND));
      for (SelectedSeatDraft draft : outboundCart) {
        ticketListContainer.getChildren().add(buildCartItem(draft, TripDirection.OUTBOUND));
      }
    }

    if (!returnCart.isEmpty()) {
      ticketListContainer.getChildren().add(buildCartHeader("Chiều về", TripDirection.RETURN));
      for (SelectedSeatDraft draft : returnCart) {
        ticketListContainer.getChildren().add(buildCartItem(draft, TripDirection.RETURN));
      }
    }

    updateTotalLabel();
  }

  private Node buildCartHeader(String title, TripDirection direction) {
    Label lbl = new Label(title);
    lbl.setStyle("-fx-font-weight: bold; -fx-padding: 8 0 2 0; -fx-text-fill: #333;");

    Button btnClearAll = new Button("Xóa tất cả");
    btnClearAll.setStyle("-fx-text-fill: white; -fx-font-size: 12px; -fx-padding: 5 10px; -fx-background-color: #c0392b");
    btnClearAll.setOnAction(e -> releaseAllInDirection(direction));

    HBox box = new HBox(10, lbl, btnClearAll);
    box.setAlignment(Pos.CENTER_LEFT);
    return box;
  }

  private Node buildCartItem(SelectedSeatDraft draft, TripDirection direction) {
    String seatLabel = "Toa " + safeInt(draft.getCarriageNumber()) + " - Ghế " + safeInt(draft.getSeatNumber());
    Label lblSeat = new Label(seatLabel);
    lblSeat.setStyle("-fx-font-weight: bold;");

    Label lblPrice = new Label(formatMoney(draft.getPrice()));
    lblPrice.setStyle("-fx-text-fill: #0066cc; -fx-font-weight: bold;");

    Button btnRemove = new Button("X");
    btnRemove.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-weight: bold;");
    btnRemove.setOnAction(e -> releaseSingleDraft(direction, draft));

    HBox box = new HBox(10, lblSeat, lblPrice, btnRemove);
    box.setAlignment(Pos.CENTER_LEFT);
    box.setPadding(new Insets(8));
    box.setStyle("-fx-background-color: white; -fx-border-color: #ddd; -fx-border-radius: 5; -fx-background-radius: 5;");
    return box;
  }

  private void releaseSingleDraft(TripDirection direction, SelectedSeatDraft draft) {
    if (draft == null || draft.getScheduleDetailId() == null || coordinator == null) {
      return;
    }
    ScheduleSaleCardDTO schedule = scheduleFor(direction);
    if (schedule == null) {
      return;
    }
    Task<Response> task = new Task<>() {
      @Override
      protected Response call() {
        return saleClientService.releaseHeldSeats(schedule.getScheduleId(), List.of(draft.getScheduleDetailId()), coordinator.getState().getClientSessionId());
      }
    };
    task.setOnSucceeded(e -> {
      Response res = task.getValue();
      if (res != null && res.isSuccess()) {
        removeSeatFromCart(draft.getScheduleDetailId(), direction);
        refreshCartUI();
        if (direction == activeDirection) {
          CarriageSeatMapDTO carriage = comboToa.getValue();
          if (carriage != null) {
            renderSeatsForActiveDirection(carriage);
          }
        }
      } else {
        showWarning("Bỏ giữ chỗ", res == null ? "Không có phản hồi" : res.getMessage());
      }
    });
    task.setOnFailed(e -> showWarning("Bỏ giữ chỗ", "Lỗi khi bỏ giữ ghế."));
    executor.submit(task);
  }

  private void releaseAllInDirection(TripDirection direction) {
    if (coordinator == null) {
      return;
    }
    List<SelectedSeatDraft> cart = cartFor(direction);
    if (cart.isEmpty()) {
      return;
    }
    ScheduleSaleCardDTO schedule = scheduleFor(direction);
    if (schedule == null) {
      return;
    }
    List<String> ids = cart.stream().map(SelectedSeatDraft::getScheduleDetailId).filter(Objects::nonNull).toList();
    Task<Response> task = new Task<>() {
      @Override
      protected Response call() {
        return saleClientService.releaseHeldSeats(schedule.getScheduleId(), ids, coordinator.getState().getClientSessionId());
      }
    };
    task.setOnSucceeded(e -> {
      Response res = task.getValue();
      if (res != null && res.isSuccess()) {
        cart.clear();
        refreshCartUI();
        if (direction == activeDirection) {
          CarriageSeatMapDTO carriage = comboToa.getValue();
          if (carriage != null) {
            renderSeatsForActiveDirection(carriage);
          }
        }
      } else {
        showWarning("Hủy chọn", res == null ? "Không có phản hồi" : res.getMessage());
      }
    });
    task.setOnFailed(e -> showWarning("Hủy chọn", "Lỗi khi hủy chọn."));
    executor.submit(task);
  }

  private void updateTotalLabel() {
    if (coordinator == null) {
      return;
    }
    double sum = 0.0;
    for (SelectedSeatDraft d : coordinator.getState().getOutboundSeats()) {
      sum += d.getPrice() != null ? d.getPrice() : 0.0;
    }
    for (SelectedSeatDraft d : coordinator.getState().getReturnSeats()) {
      sum += d.getPrice() != null ? d.getPrice() : 0.0;
    }
    labelTongTien.setText("Tổng tiền: " + formatMoney(sum));
  }

  private boolean validateBeforeNext() {
    if (coordinator == null) {
      return false;
    }
    SaleWizardState state = coordinator.getState();
    if (state.getOutboundSeats().isEmpty()) {
      showWarning("Bước 2", "Vui lòng chọn ít nhất một chỗ cho chiều đi.");
      return false;
    }

    boolean roundTrip = state.getTicketCategory() == TicketCategory.ROUND_TRIP && state.getSelectedReturnSchedule() != null;
    if (roundTrip) {
      if (state.getReturnSeats().isEmpty()) {
        showWarning("Bước 2", "Vui lòng chọn ít nhất một chỗ cho chiều về.");
        return false;
      }
      if (state.getOutboundSeats().size() != state.getReturnSeats().size()) {
        showWarning("Bước 2",
            "Số lượng vé không tương xứng.\nVui lòng chọn số lượng vé bằng nhau cho cả hai chiều.");
        return false;
      }
    }
    return true;
  }

  private void startSeatMapPolling() {
    if (seatMapPoller != null) {
      return;
    }
    seatMapPoller = new Timeline(new KeyFrame(javafx.util.Duration.seconds(5), e -> pollSeatMapsOnce()));
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
    if (coordinator == null) {
      return;
    }
    if (seatMapRefreshInFlight) {
      return;
    }
    ScheduleSaleCardDTO outbound = coordinator.getState().getSelectedOutboundSchedule();
    if (outbound == null) {
      return;
    }
    seatMapRefreshInFlight = true;

    boolean roundTrip = coordinator.getState().getTicketCategory() == TicketCategory.ROUND_TRIP
        && coordinator.getState().getSelectedReturnSchedule() != null;
    int tasks = roundTrip ? 2 : 1;
    int[] remaining = { tasks };

    refreshSeatMap(outbound.getScheduleId(), TripDirection.OUTBOUND, () -> {
      remaining[0]--;
      if (remaining[0] <= 0) {
        seatMapRefreshInFlight = false;
      }
    });

    if (roundTrip) {
      refreshSeatMap(coordinator.getState().getSelectedReturnSchedule().getScheduleId(), TripDirection.RETURN, () -> {
        remaining[0]--;
        if (remaining[0] <= 0) {
          seatMapRefreshInFlight = false;
        }
      });
    }
  }

  private void refreshSeatMap(String scheduleId, TripDirection direction, Runnable done) {
    if (scheduleId == null) {
      if (done != null) {
        done.run();
      }
      return;
    }

    Task<Response> task = new Task<>() {
      @Override
      protected Response call() {
        return saleClientService.getSeatMap(scheduleId, coordinator.getState().getClientSessionId());
      }
    };

    task.setOnSucceeded(e -> {
      try {
        Response res = task.getValue();
        if (res != null && res.isSuccess() && res.getData() instanceof SeatMapResponseDTO sm) {
          if (direction == TripDirection.OUTBOUND) {
            outboundSeatMap = sm;
            reconcileCartWithSeatMap(sm, coordinator.getState().getOutboundSeats(), direction);
          } else {
            returnSeatMap = sm;
            reconcileCartWithSeatMap(sm, coordinator.getState().getReturnSeats(), direction);
          }

          if (direction == activeDirection) {
            List<CarriageSeatMapDTO> carriages = getActiveSeatMapCarriages();
            CarriageSeatMapDTO selected = comboToa.getValue();
            String selectedId = selected != null ? selected.getCarriageId() : null;
            comboToa.setItems(FXCollections.observableArrayList(carriages));
            if (selectedId != null) {
              carriages.stream()
                  .filter(c -> Objects.equals(c.getCarriageId(), selectedId))
                  .findFirst()
                  .ifPresentOrElse(comboToa.getSelectionModel()::select, comboToa.getSelectionModel()::selectFirst);
            } else {
              comboToa.getSelectionModel().selectFirst();
            }
          }

          refreshCartUI();
        }
      } finally {
        if (done != null) {
          done.run();
        }
      }
    });

    task.setOnFailed(e -> {
      if (done != null) {
        done.run();
      }
    });
    executor.submit(task);
  }

  private void reconcileCartWithSeatMap(SeatMapResponseDTO sm, List<SelectedSeatDraft> cart, TripDirection direction) {
    if (sm == null || sm.getCarriages() == null || cart == null || cart.isEmpty()) {
      return;
    }

    Map<String, SeatMapSeatDTO> bySdId = new HashMap<>();
    for (CarriageSeatMapDTO c : sm.getCarriages()) {
      if (c.getSeats() == null) {
        continue;
      }
      for (SeatMapSeatDTO s : c.getSeats()) {
        if (s.getScheduleDetailId() != null) {
          bySdId.put(s.getScheduleDetailId(), s);
        }
      }
    }

    List<SelectedSeatDraft> toRemove = new ArrayList<>();
    for (SelectedSeatDraft item : cart) {
      SeatMapSeatDTO seat = bySdId.get(item.getScheduleDetailId());
      if (seat == null) {
        toRemove.add(item);
        continue;
      }
      SeatAvailabilityStatus status = seat.getSeatStatus() != null ? seat.getSeatStatus() : SeatAvailabilityStatus.AVAILABLE;
      if (status != SeatAvailabilityStatus.HELD || !seat.isHeldByMe()) {
        toRemove.add(item);
      }
    }

    if (!toRemove.isEmpty()) {
      cart.removeAll(toRemove);
      showWarning("Giỏ vé", "Một số ghế đã hết giữ chỗ (" + (direction == TripDirection.OUTBOUND ? "chiều đi" : "chiều về") + "). Vui lòng chọn lại.");
    }
  }

  private void startHoldKeepAlive() {
    if (holdKeepAlive != null) {
      return;
    }
    holdKeepAlive = new Timeline(new KeyFrame(javafx.util.Duration.minutes(2), e -> renewHeldSeats()));
    holdKeepAlive.setCycleCount(Timeline.INDEFINITE);
    holdKeepAlive.play();
  }

  private void stopHoldKeepAlive() {
    if (holdKeepAlive != null) {
      holdKeepAlive.stop();
      holdKeepAlive = null;
    }
  }

  void stopBackgroundJobsAfterSale() {
    stopSeatMapPolling();
    stopHoldKeepAlive();
  }

  private void renewHeldSeats() {
    if (coordinator == null) {
      return;
    }
    SaleWizardState state = coordinator.getState();
    ScheduleSaleCardDTO outbound = state.getSelectedOutboundSchedule();
    if (outbound == null) {
      return;
    }

    List<String> outboundIds = state.getOutboundSeats().stream().map(SelectedSeatDraft::getScheduleDetailId).filter(Objects::nonNull).toList();
    if (!outboundIds.isEmpty()) {
      Task<Response> task = new Task<>() {
        @Override
        protected Response call() {
          return saleClientService.holdSeats(outbound.getScheduleId(), outboundIds, state.getClientSessionId());
        }
      };
      executor.submit(task);
    }

    boolean roundTrip = state.getTicketCategory() == TicketCategory.ROUND_TRIP && state.getSelectedReturnSchedule() != null;
    if (roundTrip) {
      ScheduleSaleCardDTO ret = state.getSelectedReturnSchedule();
      List<String> returnIds = state.getReturnSeats().stream().map(SelectedSeatDraft::getScheduleDetailId).filter(Objects::nonNull).toList();
      if (!returnIds.isEmpty()) {
        Task<Response> task = new Task<>() {
          @Override
          protected Response call() {
            return saleClientService.holdSeats(ret.getScheduleId(), returnIds, state.getClientSessionId());
          }
        };
        executor.submit(task);
      }
    }
  }

  private void releaseAllHeldSeatsAndClearState(Runnable after) {
    if (coordinator == null) {
      if (after != null) {
        after.run();
      }
      return;
    }

    stopSeatMapPolling();
    stopHoldKeepAlive();

    SaleWizardState state = coordinator.getState();

    ScheduleSaleCardDTO outbound = state.getSelectedOutboundSchedule();
    if (outbound != null && !state.getOutboundSeats().isEmpty()) {
      List<String> ids = state.getOutboundSeats().stream().map(SelectedSeatDraft::getScheduleDetailId).filter(Objects::nonNull).toList();
      Task<Response> task = new Task<>() {
        @Override
        protected Response call() {
          return saleClientService.releaseHeldSeats(outbound.getScheduleId(), ids, state.getClientSessionId());
        }
      };
      executor.submit(task);
    }

    ScheduleSaleCardDTO ret = state.getSelectedReturnSchedule();
    if (ret != null && !state.getReturnSeats().isEmpty()) {
      List<String> ids = state.getReturnSeats().stream().map(SelectedSeatDraft::getScheduleDetailId).filter(Objects::nonNull).toList();
      Task<Response> task = new Task<>() {
        @Override
        protected Response call() {
          return saleClientService.releaseHeldSeats(ret.getScheduleId(), ids, state.getClientSessionId());
        }
      };
      executor.submit(task);
    }

    // UX choice for Phase 3: back to Step 1 clears cart to avoid leaking holds / mismatched schedules.
    state.getOutboundSeats().clear();
    state.getReturnSeats().clear();

    Platform.runLater(() -> {
      refreshCartUI();
      if (after != null) {
        after.run();
      }
    });
  }

  private static String safe(String s) {
    return s == null ? "" : s;
  }

  private static String formatMoney(Double amount) {
    double v = amount == null ? 0.0 : amount;
    return MONEY.format(v) + " VND";
  }

  private static String safeInt(Integer v) {
    return v == null ? "--" : String.valueOf(v);
  }

  private void showWarning(String title, String message) {
    Alert alert = new Alert(Alert.AlertType.WARNING);
    alert.setTitle(title);
    alert.setHeaderText(null);
    alert.setContentText(message);
    alert.showAndWait();
  }

  private void showError(String title, String message) {
    Alert alert = new Alert(Alert.AlertType.ERROR);
    alert.setTitle(title);
    alert.setHeaderText(null);
    alert.setContentText(message);
    alert.showAndWait();
  }
}
