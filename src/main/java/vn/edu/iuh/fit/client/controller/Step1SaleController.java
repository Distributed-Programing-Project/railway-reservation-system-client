package vn.edu.iuh.fit.client.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DateCell;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import javafx.beans.value.ChangeListener;
import vn.edu.iuh.fit.client.service.SaleClientService;
import vn.edu.iuh.fit.common.constant.TicketCategory;
import vn.edu.iuh.fit.common.constant.TripDirection;
import vn.edu.iuh.fit.common.dto.SaleScheduleSearchDTO;
import vn.edu.iuh.fit.common.dto.SaleScheduleSearchResultDTO;
import vn.edu.iuh.fit.common.dto.ScheduleSaleCardDTO;
import vn.edu.iuh.fit.common.dto.StationDTO;
import vn.edu.iuh.fit.common.response.Response;

public class Step1SaleController {
  private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

  @FXML
  private VBox contentArea;

  @FXML
  private ComboBox<StationDTO> comboGaDi;

  @FXML
  private ComboBox<StationDTO> comboGaDen;

  @FXML
  private DatePicker datePickerNgayKhoiHanh;

  @FXML
  private DatePicker dateNgayVe;

  @FXML
  private javafx.scene.control.RadioButton radioMotChieu;

  @FXML
  private javafx.scene.control.RadioButton radioKhuHoi;

  @FXML
  private ToggleGroup tripTypeGroup;

  @FXML
  private Button btnTimChuyen;

  @FXML
  private Pane paneDanhSachChuyenTau;

  @FXML
  private Button btnQuayLai;

  @FXML
  private Button btnTiepTheo;

  private BanVeController coordinator;

  private final SaleClientService saleClientService = new SaleClientService();
  private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
    Thread t = new Thread(r, "step1-sale-worker");
    t.setDaemon(true);
    return t;
  });

  private final ObservableList<StationDTO> stationItems = FXCollections.observableArrayList();
  private volatile boolean stationsLoadStarted;

  private List<ScheduleSaleCardDTO> lastOutboundSchedules = List.of();
  private List<ScheduleSaleCardDTO> lastReturnSchedules = List.of();

  private final Map<String, Node> outboundCardByScheduleId = new HashMap<>();
  private final Map<String, Node> returnCardByScheduleId = new HashMap<>();

  private ScheduleSaleCardDTO selectedOutbound;
  private ScheduleSaleCardDTO selectedReturn;

  private ChangeListener<StationDTO> comboGaDiListener;
  private ChangeListener<StationDTO> comboGaDenListener;

  public void setCoordinator(BanVeController coordinator) {
    this.coordinator = coordinator;
  }

  public void initData() {
    restoreStateToInputs();
    ensureStationsLoaded();
    restoreSelectionHighlights();

    // KIỂM TRA MODE ĐỂ KHÓA/MỞ NÚT QUAY LẠI
    if (coordinator != null && coordinator.getState().isExchangeMode()) {
      btnQuayLai.setDisable(false); // Đổi vé thì cho phép lùi về màn hình Tra Cứu
    } else {
      btnQuayLai.setDisable(true); // Bán vé bình thường thì khóa nút này lại
    }
  }

  @FXML
  public void initialize() {
    setupStationComboBox(comboGaDi);
    setupStationComboBox(comboGaDen);
    comboGaDi.setItems(stationItems);
    comboGaDen.setItems(stationItems);

    datePickerNgayKhoiHanh.setDayCellFactory(picker -> new DateCell() {
      @Override
      public void updateItem(LocalDate date, boolean empty) {
        super.updateItem(date, empty);
        if (empty || date == null) {
          return;
        }
        if (date.isBefore(LocalDate.now())) {
          setDisable(true);
        }
      }
    });

    dateNgayVe.setDayCellFactory(picker -> new DateCell() {
      @Override
      public void updateItem(LocalDate date, boolean empty) {
        super.updateItem(date, empty);
        if (empty || date == null) {
          return;
        }
        LocalDate dep = datePickerNgayKhoiHanh.getValue();
        if (dep == null) {
          dep = LocalDate.now();
        }
        if (date.isBefore(dep)) {
          setDisable(true);
        }
      }
    });

    radioKhuHoi.selectedProperty().addListener((obs, oldVal, isRoundTrip) -> {
      applyTripMode(isRoundTrip ? TicketCategory.ROUND_TRIP : TicketCategory.ONE_WAY);
      persistInputsToState();
    });

    datePickerNgayKhoiHanh.valueProperty().addListener((obs, oldVal, newVal) -> {
      if (newVal == null) {
        return;
      }
      if (radioKhuHoi.isSelected() && dateNgayVe.getValue() != null && dateNgayVe.getValue().isBefore(newVal)) {
        dateNgayVe.setValue(newVal);
      }
      persistInputsToState();
    });

    dateNgayVe.valueProperty().addListener((obs, oldVal, newVal) -> persistInputsToState());
    comboGaDiListener = (obs, oldVal, newVal) -> persistInputsToState();
    comboGaDenListener = (obs, oldVal, newVal) -> persistInputsToState();
    comboGaDi.valueProperty().addListener(comboGaDiListener);
    comboGaDen.valueProperty().addListener(comboGaDenListener);

    if (datePickerNgayKhoiHanh.getValue() == null) {
      datePickerNgayKhoiHanh.setValue(LocalDate.now());
    }

    applyTripMode(TicketCategory.ONE_WAY);
    ensureStationsLoaded();
  }

  @FXML
  private void handleTimKiem() {
    if (!validateInputs()) {
      return;
    }
    persistInputsToState();
    searchSchedulesAsync();
  }

  @FXML
  private void handleQuayLai() {
    if (coordinator != null && coordinator.getState().isExchangeMode()) {
      coordinator.backFromStep1(); // Sẽ gọi hàm showExchangeSearch() ở BanVeController
    }
  }

  @FXML
  private void handleTiepTheo() {
    if (!validateSelectionBeforeNext()) {
      return;
    }
    persistSelectionToState();
    if (coordinator != null) {
      coordinator.showStep2();
    }
  }

  private void ensureStationsLoaded() {
    if (stationsLoadStarted || !stationItems.isEmpty()) {
      return;
    }
    stationsLoadStarted = true;

    Task<Response> task = new Task<>() {
      @Override
      protected Response call() {
        return saleClientService.findAllStations();
      }
    };

    task.setOnSucceeded(e -> {
      Response res = task.getValue();
      if (res != null && res.isSuccess() && res.getData() instanceof List<?> list) {
        List<StationDTO> stations = new ArrayList<>();
        for (Object o : list) {
          if (o instanceof StationDTO s) {
            stations.add(s);
          }
        }
        stations.sort(Comparator.comparing(StationDTO::getName, Comparator.nullsLast(String::compareToIgnoreCase)));
        stationItems.setAll(stations);
        if (stations.isEmpty()) {
          showError("Danh sách ga", "Server trả về danh sách ga rỗng.");
        }
        restoreStateToInputs();
        if (coordinator == null || !coordinator.getState().isExchangeMode()) {
          autoSearchIfPossible();
        }
      } else {
        showError("Danh sách ga", res == null ? "Không có phản hồi" : res.getMessage());
      }
    });

    task.setOnFailed(e -> showError("Danh sách ga", "Lỗi khi tải danh sách ga."));
    executor.submit(task);
  }

  private void autoSearchIfPossible() {
    if (coordinator == null) {
      return;
    }
    if (!lastOutboundSchedules.isEmpty()) {
      return;
    }
    if (comboGaDi.getValue() == null || comboGaDen.getValue() == null || datePickerNgayKhoiHanh.getValue() == null) {
      return;
    }
    // Mimic old UX: if inputs already restored, auto search to re-populate cards.
    searchSchedulesAsync();
  }

  private boolean validateInputs() {
    StationDTO dep = comboGaDi.getValue();
    StationDTO dest = comboGaDen.getValue();
    LocalDate depDate = datePickerNgayKhoiHanh.getValue();

    if (dep == null || dest == null || depDate == null) {
      showWarning("Tìm chuyến", "Vui lòng chọn đầy đủ thông tin (Ga đi, Ga đến, Ngày đi).");
      return false;
    }
    if (Objects.equals(dep.getId(), dest.getId())) {
      showWarning("Tìm chuyến", "Ga đi và ga đến không được trùng nhau!");
      return false;
    }
    if (depDate.isBefore(LocalDate.now())) {
      showWarning("Tìm chuyến", "Ngày đi không được trước ngày hiện tại.");
      return false;
    }

    if (radioKhuHoi.isSelected()) {
      LocalDate retDate = dateNgayVe.getValue();
      if (retDate == null) {
        showWarning("Tìm chuyến", "Vui lòng chọn ngày về cho vé khứ hồi.");
        return false;
      }
      if (retDate.isBefore(depDate)) {
        showWarning("Tìm chuyến", "Ngày về không được trước ngày đi.");
        return false;
      }
    }

    return true;
  }

  private void searchSchedulesAsync() {
    StationDTO dep = comboGaDi.getValue();
    StationDTO dest = comboGaDen.getValue();
    LocalDate depDate = datePickerNgayKhoiHanh.getValue();
    boolean roundTrip = radioKhuHoi.isSelected();
    LocalDate retDate = roundTrip ? dateNgayVe.getValue() : null;

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
      Response res = task.getValue();
      if (res != null && res.isSuccess() && res.getData() instanceof SaleScheduleSearchResultDTO result) {
        lastOutboundSchedules = result.getOutboundSchedules() == null ? List.of() : result.getOutboundSchedules();
        lastReturnSchedules = result.getReturnSchedules() == null ? List.of() : result.getReturnSchedules();
        clearSelection();
        renderSchedules(lastOutboundSchedules, roundTrip ? lastReturnSchedules : null);
        restoreSelectionHighlights();
      } else {
        clearSelection();
        renderPlaceholder("Không thể tìm chuyến: " + (res == null ? "Không có phản hồi" : res.getMessage()));
      }
    });

    task.setOnFailed(e -> {
      clearSelection();
      renderPlaceholder("Lỗi khi tìm chuyến.");
    });

    executor.submit(task);
  }

  private void renderSchedules(List<ScheduleSaleCardDTO> outbound, List<ScheduleSaleCardDTO> returns) {
    Node oldResultBox = paneDanhSachChuyenTau.lookup("#result-container");
    if (oldResultBox != null) {
      paneDanhSachChuyenTau.getChildren().remove(oldResultBox);
    }

    outboundCardByScheduleId.clear();
    returnCardByScheduleId.clear();

    VBox resultContainer = new VBox(10);
    resultContainer.setId("result-container");
    resultContainer.setLayoutY(50);
    resultContainer.prefWidthProperty().bind(paneDanhSachChuyenTau.widthProperty());
    resultContainer.prefHeightProperty().bind(paneDanhSachChuyenTau.heightProperty().subtract(50));

    if (returns != null) {
      Label lblOutbound = new Label("Chọn tàu cho chiều đi");
      lblOutbound.getStyleClass().add("trip-direction-label");
      VBox.setMargin(lblOutbound, new Insets(0, 0, 0, 15));

      ScrollPane scrollOutbound = createTrainCardScrollPane(outbound, TripDirection.OUTBOUND);
      VBox.setVgrow(scrollOutbound, Priority.ALWAYS);

      Label lblReturn = new Label("Chọn tàu cho chiều về");
      lblReturn.getStyleClass().add("trip-direction-label");
      VBox.setMargin(lblReturn, new Insets(0, 0, 0, 15));

      ScrollPane scrollReturn = createTrainCardScrollPane(returns, TripDirection.RETURN);
      VBox.setVgrow(scrollReturn, Priority.ALWAYS);

      resultContainer.getChildren().addAll(lblOutbound, scrollOutbound, lblReturn, scrollReturn);
    } else {
      ScrollPane scrollOutbound = createTrainCardScrollPane(outbound, TripDirection.OUTBOUND);
      VBox.setVgrow(scrollOutbound, Priority.ALWAYS);
      resultContainer.getChildren().add(scrollOutbound);
    }

    paneDanhSachChuyenTau.getChildren().add(resultContainer);

    if ((outbound == null || outbound.isEmpty()) && (returns == null || returns.isEmpty())) {
      renderPlaceholder("Không tìm thấy chuyến tàu nào phù hợp!");
    } else if (outbound == null || outbound.isEmpty()) {
      renderPlaceholder("Không tìm thấy chuyến tàu cho chiều đi!");
    } else if (returns != null && returns.isEmpty()) {
      renderPlaceholder("Không tìm thấy chuyến tàu cho chiều về!");
    }
  }

  private ScrollPane createTrainCardScrollPane(List<ScheduleSaleCardDTO> schedules, TripDirection direction) {
    TilePane tilePane = new TilePane();
    tilePane.setPadding(new Insets(15));
    tilePane.setHgap(15);
    tilePane.setVgap(15);

    if (schedules != null) {
      for (ScheduleSaleCardDTO dto : schedules) {
        Node trainCard = createTrainCard(dto, direction);
        tilePane.getChildren().add(trainCard);
      }
    }

    ScrollPane scrollPane = new ScrollPane(tilePane);
    scrollPane.setFitToWidth(true);
    scrollPane.getStyleClass().add("no-border-scroll-pane");
    scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    return scrollPane;
  }

  private Node createTrainCard(ScheduleSaleCardDTO dto, TripDirection direction) {
    GridPane card = new GridPane();
    card.setHgap(10);
    card.setVgap(8);
    card.setPadding(new Insets(10));
    card.getStyleClass().add("train-card");
    card.setUserData(dto);

    Label lblTenTau = new Label(safe(dto.getTrainCode()));
    lblTenTau.getStyleClass().add("train-card-title");
    card.add(lblTenTau, 0, 0, 4, 1);

    card.add(new Label("Thời gian đi:"), 0, 1);
    card.add(new Label(formatDateTime(dto.getDepartureTime())), 1, 1);
    card.add(new Label("Số lượng chỗ đặt:"), 2, 1);
    int bookedSeats = Math.max(0, dto.getTotalSeats() - dto.getAvailableSeats());
    Label lblBooked = new Label(String.valueOf(bookedSeats));
    lblBooked.getStyleClass().add("train-card-booked");
    card.add(lblBooked, 3, 1);

    card.add(new Label("Thời gian đến:"), 0, 2);
    card.add(new Label(formatDateTime(dto.getArrivalTime())), 1, 2);
    card.add(new Label("Số lượng chỗ trống:"), 2, 2);
    Label lblAvail = new Label(String.valueOf(dto.getAvailableSeats()));
    lblAvail.getStyleClass().add("train-card-available");
    card.add(lblAvail, 3, 2);

    Tooltip.install(card,
        new Tooltip(safe(dto.getDepartureStationName()) + " → " + safe(dto.getDestinationStationName())));

    if (direction == TripDirection.OUTBOUND) {
      outboundCardByScheduleId.put(dto.getScheduleId(), card);
    } else {
      returnCardByScheduleId.put(dto.getScheduleId(), card);
    }

    card.setOnMouseClicked(event -> {
      if (direction == TripDirection.OUTBOUND) {
        selectedOutbound = dto;
        highlightSelected(outboundCardByScheduleId, dto.getScheduleId());
      } else {
        selectedReturn = dto;
        highlightSelected(returnCardByScheduleId, dto.getScheduleId());
      }
      persistSelectionToState();
    });

    return card;
  }

  private void highlightSelected(Map<String, Node> cardMap, String selectedScheduleId) {
    for (Map.Entry<String, Node> e : cardMap.entrySet()) {
      e.getValue().getStyleClass().remove("train-card-selected");
    }
    Node selectedNode = cardMap.get(selectedScheduleId);
    if (selectedNode != null && !selectedNode.getStyleClass().contains("train-card-selected")) {
      selectedNode.getStyleClass().add("train-card-selected");
    }
  }

  private void clearSelection() {
    selectedOutbound = null;
    selectedReturn = null;
    persistSelectionToState();
  }

  private void renderPlaceholder(String message) {
    Platform.runLater(() -> {
      Node oldResultBox = paneDanhSachChuyenTau.lookup("#result-container");
      if (oldResultBox != null) {
        paneDanhSachChuyenTau.getChildren().remove(oldResultBox);
      }

      VBox container = new VBox(10);
      container.setId("result-container");
      container.setLayoutY(50);
      container.prefWidthProperty().bind(paneDanhSachChuyenTau.widthProperty());
      container.prefHeightProperty().bind(paneDanhSachChuyenTau.heightProperty().subtract(50));

      Label lbl = new Label(message);
      lbl.setStyle("-fx-font-size: 16px; -fx-text-fill: #999; -fx-padding: 20;");
      container.getChildren().add(lbl);

      paneDanhSachChuyenTau.getChildren().add(container);
    });
  }

  private void applyTripMode(TicketCategory category) {
    boolean roundTrip = category == TicketCategory.ROUND_TRIP;

    dateNgayVe.setDisable(!roundTrip);
    if (!roundTrip) {
      dateNgayVe.setValue(null);
      selectedReturn = null;
      persistSelectionToState();

      if (coordinator != null) {
        coordinator.getState().setSelectedReturnSchedule(null);
        coordinator.getState().getReturnSeats().clear();
      }

      // If the UI is currently showing round-trip results, re-render to outbound-only
      // using the last search result.
      Node resultContainer = paneDanhSachChuyenTau.lookup("#result-container");
      if (resultContainer != null && !lastOutboundSchedules.isEmpty()) {
        renderSchedules(lastOutboundSchedules, null);
        restoreSelectionHighlights();
      }
    } else {
      if (dateNgayVe.getValue() == null && datePickerNgayKhoiHanh.getValue() != null) {
        dateNgayVe.setValue(datePickerNgayKhoiHanh.getValue());
      }
    }

    if (coordinator != null) {
      coordinator.getState().setTicketCategory(category);
      coordinator.getState().setReturnDate(roundTrip ? dateNgayVe.getValue() : null);
    }
  }

  private void persistInputsToState() {
    if (coordinator == null) {
      return;
    }
    coordinator.getState().setDepartureStation(comboGaDi.getValue());
    coordinator.getState().setDestinationStation(comboGaDen.getValue());
    coordinator.getState().setDepartureDate(datePickerNgayKhoiHanh.getValue());
    coordinator.getState().setReturnDate(radioKhuHoi.isSelected() ? dateNgayVe.getValue() : null);
    coordinator.getState()
        .setTicketCategory(radioKhuHoi.isSelected() ? TicketCategory.ROUND_TRIP : TicketCategory.ONE_WAY);
  }

  private void persistSelectionToState() {
    if (coordinator == null) {
      return;
    }
    coordinator.getState().setSelectedOutboundSchedule(selectedOutbound);
    coordinator.getState().setSelectedReturnSchedule(radioKhuHoi.isSelected() ? selectedReturn : null);
  }

  private void restoreStateToInputs() {
    if (coordinator == null)
      return;

    var state = coordinator.getState();
    boolean isExchange = state.isExchangeMode();

    // 1. XỬ LÝ LOẠI VÉ: Đổi vé chỉ cho phép Một chiều
    if (isExchange) {
      radioMotChieu.setSelected(true);
      radioKhuHoi.setDisable(true);
      radioMotChieu.setDisable(true);
      // Cập nhật state luôn để handleTimKiem lấy đúng loại vé
      state.setTicketCategory(TicketCategory.ONE_WAY);
    } else {
      boolean roundTrip = state.getTicketCategory() == TicketCategory.ROUND_TRIP;
      radioKhuHoi.setSelected(roundTrip);
      radioMotChieu.setSelected(!roundTrip);
      radioKhuHoi.setDisable(false);
      radioMotChieu.setDisable(false);
    }

    // 2. ĐIỀN VÀ KHÓA GA ĐI/GA ĐẾN
    if (!stationItems.isEmpty()) {
      // Emergency: avoid listener side-effects while restoring (e.g., one combo clears the other)
      if (comboGaDiListener != null) {
        comboGaDi.valueProperty().removeListener(comboGaDiListener);
      }
      if (comboGaDenListener != null) {
        comboGaDen.valueProperty().removeListener(comboGaDenListener);
      }
      try {
      if (state.getDepartureStation() != null) {
        // Dùng hàm tìm kiếm linh hoạt (ID hoặc Tên)
        StationDTO dep = findStationInItems(state.getDepartureStation());
        comboGaDi.setValue(dep);
      }

      if (state.getDestinationStation() != null) {
        // Quan trọng: Ép tìm ga đến theo tên để tránh lỗi null
        StationDTO dest = findStationInItems(state.getDestinationStation());
        comboGaDen.setValue(dest);
      }

      if (isExchange && comboGaDen.getValue() == null && state.getDestinationStation() != null) {
        // CRITICAL fallback: simple string search in stationItems
        String targetName = normalizeStationName(state.getDestinationStation().getName());
        if (targetName != null) {
          for (StationDTO s : stationItems) {
            if (s == null) {
              continue;
            }
            String stationName = normalizeStationName(s.getName());
            if (stationName == null) {
              continue;
            }
            if (stationName.contains(targetName) || targetName.contains(stationName)) {
              comboGaDen.setValue(s);
              break;
            }
          }
        }
      }
      } finally {
        if (comboGaDiListener != null) {
          comboGaDi.valueProperty().addListener(comboGaDiListener);
        }
        if (comboGaDenListener != null) {
          comboGaDen.valueProperty().addListener(comboGaDenListener);
        }
      }

      // Nếu đang đổi vé: Khóa cứng và tự động tìm kiếm
      if (isExchange) {
        // Always allow staff to click search after changing date
        if (btnTimChuyen != null) {
          btnTimChuyen.setDisable(false);
        }

        // Lock only what is successfully pre-filled. If Ga đến is null, keep it enabled for manual selection.
        comboGaDi.setDisable(comboGaDi.getValue() != null);
        comboGaDen.setDisable(comboGaDen.getValue() != null);

        // Auto-search chỉ khi cả 2 ComboBox đã có giá trị
        if (comboGaDi.getValue() != null && comboGaDen.getValue() != null) {
          Platform.runLater(this::handleTimKiem);
        }
      }
    }

    // 3. PHỤC HỒI NGÀY ĐI & THÔNG TIN CHỌN TÀU CŨ (Nếu có)
    if (state.getDepartureDate() != null) {
      datePickerNgayKhoiHanh.setValue(state.getDepartureDate());
    } else {
      LocalDate fallback = LocalDate.now();
      if (isExchange && state.getExchangeOldTickets() != null && !state.getExchangeOldTickets().isEmpty()
          && state.getExchangeOldTickets().get(0) != null
          && state.getExchangeOldTickets().get(0).getDepartureTime() != null) {
        fallback = state.getExchangeOldTickets().get(0).getDepartureTime().toLocalDate();
      }
      datePickerNgayKhoiHanh.setValue(fallback);
    }

    // Đồng bộ lại biến cục bộ để highlight đúng thẻ tàu khi render xong
    selectedOutbound = state.getSelectedOutboundSchedule();
  }

  private StationDTO findStationInItems(StationDTO target) {
    if (target == null)
      return null;
    String targetId = safeTrim(target.getId());
    if (targetId != null) {
      StationDTO idMatch = stationItems.stream()
          .filter(s -> s != null && targetId.equals(safeTrim(s.getId())))
          .findFirst()
          .orElse(null);
      if (idMatch != null) {
        return idMatch;
      }
    }

    String targetName = normalizeStationName(target.getName());
    if (targetName == null) {
      return null;
    }

    StationDTO bestExact = null;
    StationDTO bestContains = null;
    int bestContainsScore = Integer.MIN_VALUE;

    for (StationDTO s : stationItems) {
      if (s == null) {
        continue;
      }
      String stationName = normalizeStationName(s.getName());
      if (stationName == null) {
        continue;
      }

      System.out.println("Comparing: [" + targetName + "] with [" + stationName + "]");

      if (stationName.equals(targetName)) {
        bestExact = s;
        break;
      }

      // Flexible matching: contains either way to handle "Ga Sài Gòn" vs "Sài Gòn"
      boolean containsEitherWay = stationName.contains(targetName) || targetName.contains(stationName);
      if (containsEitherWay) {
        int overlap = Math.min(stationName.length(), targetName.length());
        int extra = Math.abs(stationName.length() - targetName.length());
        int score = (overlap * 10) - extra;
        if (score > bestContainsScore) {
          bestContainsScore = score;
          bestContains = s;
        }
      }
    }

    return bestExact != null ? bestExact : bestContains;
  }

  private static String safeTrim(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private static String normalizeStationName(String value) {
    String trimmed = safeTrim(value);
    if (trimmed == null) {
      return null;
    }
    String lowered = trimmed.toLowerCase();
    return Normalizer.normalize(lowered, Normalizer.Form.NFC);
  }

  private void restoreSelectionHighlights() {
    if (coordinator == null) {
      return;
    }

    var state = coordinator.getState();
    ScheduleSaleCardDTO outboundState = state.getSelectedOutboundSchedule();
    ScheduleSaleCardDTO returnState = state.getSelectedReturnSchedule();

    if (outboundState != null && outboundState.getScheduleId() != null) {
      highlightSelected(outboundCardByScheduleId, outboundState.getScheduleId());
    }
    if (radioKhuHoi.isSelected() && returnState != null && returnState.getScheduleId() != null) {
      highlightSelected(returnCardByScheduleId, returnState.getScheduleId());
    }
  }

  private boolean validateSelectionBeforeNext() {
    if (selectedOutbound == null) {
      showWarning("Bước 1", "Vui lòng chọn một chuyến tàu trước khi tiếp tục.");
      return false;
    }
    if (radioKhuHoi.isSelected() && selectedReturn == null) {
      showWarning("Bước 1", "Vui lòng chọn chuyến tàu cho cả chiều đi và chiều về.");
      return false;
    }
    return true;
  }

  private void setupStationComboBox(ComboBox<StationDTO> comboBox) {
    comboBox.setConverter(new StringConverter<>() {
      @Override
      public String toString(StationDTO object) {
        return object == null ? "" : safe(object.getName());
      }

      @Override
      public StationDTO fromString(String string) {
        return null;
      }
    });

    comboBox.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
      @Override
      protected void updateItem(StationDTO item, boolean empty) {
        super.updateItem(item, empty);
        setText(empty || item == null ? null : safe(item.getName()));
      }
    });

    comboBox.setButtonCell(new javafx.scene.control.ListCell<>() {
      @Override
      protected void updateItem(StationDTO item, boolean empty) {
        super.updateItem(item, empty);
        setText(empty || item == null ? null : safe(item.getName()));
      }
    });
  }

  private static String safe(String s) {
    return s == null ? "" : s;
  }

  private static String formatDateTime(LocalDateTime dt) {
    return dt == null ? "" : DATE_TIME.format(dt);
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
