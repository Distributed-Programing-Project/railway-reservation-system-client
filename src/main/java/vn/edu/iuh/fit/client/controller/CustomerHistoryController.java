package vn.edu.iuh.fit.client.controller;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import vn.edu.iuh.fit.client.service.SocketRequestService;
import vn.edu.iuh.fit.common.command.ActionType;
import vn.edu.iuh.fit.common.dto.CustomerDTO;
import vn.edu.iuh.fit.common.dto.CustomerHistoryItemDTO;
import vn.edu.iuh.fit.common.dto.CustomerHistoryRequestDTO;
import vn.edu.iuh.fit.common.dto.CustomerHistoryResponseDTO;
import vn.edu.iuh.fit.common.request.Request;
import vn.edu.iuh.fit.common.response.Response;

import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class CustomerHistoryController {

  private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
  private static final NumberFormat MONEY = NumberFormat.getInstance(new Locale("vi", "VN"));

  @FXML
  private StackPane root;

  @FXML
  private Button btnClose;

  @FXML
  private TextField maKhField;

  @FXML
  private TextField hoTenField;

  @FXML
  private TextField cccdField;

  @FXML
  private TextField hoChieuField;

  @FXML
  private TextField sdtField;

  @FXML
  private TextField diemTichField;

  @FXML
  private TableView<CustomerHistoryItemDTO> historyTable;

  @FXML
  private TableColumn<CustomerHistoryItemDTO, Void> sttColumn;

  @FXML
  private TableColumn<CustomerHistoryItemDTO, LocalDateTime> ngayMuaColumn;

  @FXML
  private TableColumn<CustomerHistoryItemDTO, String> maVeColumn;

  @FXML
  private TableColumn<CustomerHistoryItemDTO, String> macTauColumn;

  @FXML
  private TableColumn<CustomerHistoryItemDTO, String> hanhTrinhColumn;

  @FXML
  private TableColumn<CustomerHistoryItemDTO, String> thoiGianColumn;

  @FXML
  private TableColumn<CustomerHistoryItemDTO, String> toaColumn;

  @FXML
  private TableColumn<CustomerHistoryItemDTO, Integer> soChoColumn;

  @FXML
  private TableColumn<CustomerHistoryItemDTO, Double> giaVeColumn;

  @FXML
  private Label recordCountLabel;

  @FXML
  private TextField totalAmountField;

  @FXML
  private StackPane loadingOverlay;

  private final SocketRequestService socketRequestService = new SocketRequestService();
  private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
    Thread t = new Thread(r);
    t.setDaemon(true);
    t.setName("customer-history-" + UUID.randomUUID());
    return t;
  });

  private final AtomicBoolean cleanupDone = new AtomicBoolean(false);
  private volatile Task<?> currentTask;

  private CustomerDTO customer;

  @FXML
  public void initialize() {
    setupTable();

    if (btnClose != null) {
      btnClose.setOnAction(e -> closeWindow());
    }

    setLoading(false);
    registerCloseCleanupHook();
  }

  public void initCustomer(CustomerDTO customer) {
    this.customer = customer;
    fillCustomerInfo(customer);
    loadHistory();
  }

  private void fillCustomerInfo(CustomerDTO dto) {
    if (dto == null) return;
    if (maKhField != null) maKhField.setText(safe(dto.getCustomerId()));
    if (hoTenField != null) hoTenField.setText(safe(dto.getFullName()));
    if (cccdField != null) cccdField.setText(safe(dto.getIdCard()));
    if (hoChieuField != null) hoChieuField.setText(safe(dto.getPassport()));
    if (sdtField != null) sdtField.setText(safe(dto.getPhone()));
    if (diemTichField != null) diemTichField.setText(String.valueOf(dto.getRewardPoints()));
  }

  private void setupTable() {
    if (sttColumn != null) {
      sttColumn.setCellFactory(col -> new TableCell<>() {
        @Override
        protected void updateItem(Void item, boolean empty) {
          super.updateItem(item, empty);
          if (empty) {
            setText(null);
          } else {
            setText(String.valueOf(getIndex() + 1));
          }
        }
      });
    }

    if (ngayMuaColumn != null) {
      ngayMuaColumn.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().getPurchaseTime()));
      ngayMuaColumn.setCellFactory(col -> new TableCell<>() {
        @Override
        protected void updateItem(LocalDateTime item, boolean empty) {
          super.updateItem(item, empty);
          setText(empty || item == null ? "" : DATE_TIME.format(item));
        }
      });
    }

    if (maVeColumn != null) {
      maVeColumn.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(safe(c.getValue().getTicketId())));
    }

    if (macTauColumn != null) {
      macTauColumn.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(safe(c.getValue().getTrainCode())));
    }

    if (hanhTrinhColumn != null) {
      hanhTrinhColumn.setCellValueFactory(c -> {
        CustomerHistoryItemDTO v = c.getValue();
        String route = safe(v.getDepartureStationName()) + " - " + safe(v.getDestinationStationName());
        return new javafx.beans.property.SimpleStringProperty(route);
      });
    }

    if (thoiGianColumn != null) {
      thoiGianColumn.setCellValueFactory(c -> {
        CustomerHistoryItemDTO v = c.getValue();
        String range = formatDateTime(v.getDepartureTime()) + " - " + formatDateTime(v.getArrivalTime());
        return new javafx.beans.property.SimpleStringProperty(range);
      });
    }

    if (toaColumn != null) {
      toaColumn.setCellValueFactory(c -> {
        CustomerHistoryItemDTO v = c.getValue();
        Integer carriageNumber = v.getCarriageNumber();
        String carriage = carriageNumber != null && carriageNumber > 0 ? String.valueOf(carriageNumber) : "--";
        String seatTypeName = v.getSeatType() == null ? "--" : v.getSeatType().getName();
        String txt = "Toa " + carriage + " - " + seatTypeName;
        return new javafx.beans.property.SimpleStringProperty(txt);
      });
    }

    if (soChoColumn != null) {
      soChoColumn.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().getSeatNumber()));
    }

    if (giaVeColumn != null) {
      giaVeColumn.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().getTicketPrice()));
      giaVeColumn.setCellFactory(col -> new TableCell<>() {
        @Override
        protected void updateItem(Double item, boolean empty) {
          super.updateItem(item, empty);
          setText(empty || item == null ? "" : formatMoney(item));
        }
      });
    }
  }

  private void loadHistory() {
    if (customer == null || customer.getCustomerId() == null || customer.getCustomerId().isBlank()) {
      showWarning("Lịch sử mua vé", "Customer ID không hợp lệ.");
      return;
    }

    setLoading(true);
    CustomerHistoryRequestDTO dto = CustomerHistoryRequestDTO.builder()
        .customerId(customer.getCustomerId())
        .build();

    Task<Response> task = new Task<>() {
      @Override
      protected Response call() {
        return socketRequestService.send(new Request(ActionType.GET_CUSTOMER_HISTORY, dto));
      }
    };
    currentTask = task;

    task.setOnSucceeded(e -> {
      setLoading(false);
      Response res = task.getValue();
      if (res != null && res.isSuccess() && res.getData() instanceof CustomerHistoryResponseDTO data) {
        List<CustomerHistoryItemDTO> items = data.getItems() != null ? data.getItems() : List.of();
        historyTable.getItems().setAll(items);
        if (recordCountLabel != null) {
          recordCountLabel.setText("Số vé: " + items.size());
        }
        if (totalAmountField != null) {
          totalAmountField.setText(formatMoney(data.getTotalAmount()));
        }
      } else {
        String msg = res == null ? "Không có phản hồi từ server." : res.getMessage();
        showError("Lịch sử mua vé", msg);
      }
    });

    task.setOnFailed(e -> {
      setLoading(false);
      showError("Lịch sử mua vé", "Đã xảy ra lỗi khi tải lịch sử mua vé.");
    });

    start(task, "get-customer-history");
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

  private void setLoading(boolean loading) {
    if (loadingOverlay != null) {
      loadingOverlay.setVisible(loading);
      loadingOverlay.setManaged(loading);
    }
  }

  private void closeWindow() {
    if (btnClose == null || btnClose.getScene() == null) return;
    Window window = btnClose.getScene().getWindow();
    if (window != null) {
      window.hide();
    }
  }

  private void registerCloseCleanupHook() {
    if (root == null) return;
    root.sceneProperty().addListener((obs, oldScene, newScene) -> {
      if (oldScene != null && newScene == null) {
        cleanupOnClose();
        return;
      }
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
    Task<?> t = currentTask;
    if (t != null) {
      t.cancel(true);
    }
    executor.shutdown();
    if (historyTable != null) {
      historyTable.getItems().clear();
    }
  }

  private static String safe(String v) {
    return v == null || v.isBlank() ? "--" : v;
  }

  private static String formatDateTime(LocalDateTime v) {
    return v == null ? "--" : DATE_TIME.format(v);
  }

  private static String formatMoney(double v) {
    return MONEY.format(Math.round(v)) + " đ";
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
