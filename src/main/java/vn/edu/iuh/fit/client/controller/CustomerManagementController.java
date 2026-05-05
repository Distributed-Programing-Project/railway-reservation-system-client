package vn.edu.iuh.fit.client.controller;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Pagination;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import vn.edu.iuh.fit.client.service.SocketRequestService;
import vn.edu.iuh.fit.client.session.ClientSessionContext;
import vn.edu.iuh.fit.common.command.ActionType;
import vn.edu.iuh.fit.common.dto.CustomerDTO;
import vn.edu.iuh.fit.common.dto.CustomerDeleteRequestDTO;
import vn.edu.iuh.fit.common.dto.CustomerPageDTO;
import vn.edu.iuh.fit.common.dto.CustomerSearchDTO;
import vn.edu.iuh.fit.common.request.Request;
import vn.edu.iuh.fit.common.response.Response;

public class CustomerManagementController {

  private final SocketRequestService socketRequestService = new SocketRequestService();
  private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
    Thread t = new Thread(r);
    t.setDaemon(true);
    t.setName("customer-management-" + UUID.randomUUID());
    return t;
  });
  private final AtomicBoolean cleanupDone = new AtomicBoolean(false);
  private volatile Task<?> currentTask;

  @FXML
  private TableView<CustomerDTO> customerTable;

  @FXML
  private TextField searchField;

  @FXML
  private Button btnSearch;

  @FXML
  private Button btnShowAll;

  @FXML
  private Button btnRefresh;

  @FXML
  private Button btnCustomerHistory;

  @FXML
  private Button btnAddCustomer;

  @FXML
  private Button btnEditCustomer;

  @FXML
  private Button btnDeleteCustomer;

  // Khai báo các cột khớp với fx:id trong FXML
  @FXML
  private TableColumn<CustomerDTO, String> idColumn;
  @FXML
  private TableColumn<CustomerDTO, String> nameColumn;
  @FXML
  private TableColumn<CustomerDTO, String> cccdColumn;
  @FXML
  private TableColumn<CustomerDTO, String> passportColumn;
  @FXML
  private TableColumn<CustomerDTO, String> phoneColumn;
  @FXML
  private TableColumn<CustomerDTO, Integer> pointsColumn;

  @FXML
  private Pagination pagination;

  private static final int ROWS_PER_PAGE = 20;

  @FXML
  public void initialize() {
    // 1. Gán CellValueFactory khớp với thuộc tính trong CustomerDTO[cite: 17, 18]
    idColumn.setCellValueFactory(new PropertyValueFactory<>("customerId"));
    nameColumn.setCellValueFactory(new PropertyValueFactory<>("fullName"));
    cccdColumn.setCellValueFactory(new PropertyValueFactory<>("idCard"));
    passportColumn.setCellValueFactory(new PropertyValueFactory<>("passport"));
    phoneColumn.setCellValueFactory(new PropertyValueFactory<>("phone"));
    pointsColumn.setCellValueFactory(new PropertyValueFactory<>("rewardPoints"));

    // 2. Thiết lập sự kiện cho các nút chức năng
    if (btnCustomerHistory != null) {
      btnCustomerHistory.setDisable(true);
      btnCustomerHistory.setOnAction(e -> openCustomerHistory());
    }

    if (btnAddCustomer != null) {
      btnAddCustomer.setOnAction(e -> openAddCustomerDialog());
    }

    if (btnEditCustomer != null) {
      btnEditCustomer.setDisable(true);
      btnEditCustomer.setOnAction(e -> openEditCustomerDialog());
    }

    if (btnDeleteCustomer != null) {
      btnDeleteCustomer.setDisable(true);
      btnDeleteCustomer.setOnAction(e -> handleDeleteCustomer());
    }

    // Khi tìm kiếm hoặc làm mới, reset về trang 0[cite: 15]
    if (btnSearch != null) {
      btnSearch.setOnAction(e -> {
        pagination.setCurrentPageIndex(0);
        loadCustomers(normalize(searchField.getText()), 0);
      });
    }

    if (btnShowAll != null) {
      btnShowAll.setOnAction(e -> {
        if (searchField != null)
          searchField.setText("");
        pagination.setCurrentPageIndex(0);
        loadCustomers(null, 0);
      });
    }

    if (btnRefresh != null) {
      btnRefresh.setOnAction(e -> loadCustomers(normalize(searchField != null ? searchField.getText() : null),
          pagination.getCurrentPageIndex()));
    }

    if (searchField != null) {
      searchField.setOnAction(e -> {
        pagination.setCurrentPageIndex(0);
        loadCustomers(normalize(searchField.getText()), 0);
      });
    }

    // Quản lý trạng thái các nút khi chọn dòng trong TableView
    if (customerTable != null && btnCustomerHistory != null) {
      customerTable.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
        btnCustomerHistory.setDisable(newV == null);
        if (btnEditCustomer != null)
          btnEditCustomer.setDisable(newV == null);
        if (btnDeleteCustomer != null)
          btnDeleteCustomer.setDisable(newV == null);
      });
    }

    // 3. Thiết lập logic phân trang[cite: 15]
    if (pagination != null) {
      pagination.currentPageIndexProperty().addListener((obs, oldIdx, newIdx) -> {
        loadCustomers(normalize(searchField != null ? searchField.getText() : null), newIdx.intValue());
      });
    }

    registerCloseCleanupHook();
    loadCustomers(null, 0); // Tải trang đầu tiên khi khởi tạo
  }

  private void loadCustomers(String keyword, int pageIndex) {
    CustomerSearchDTO dto = CustomerSearchDTO.builder()
        .keyword(keyword)
        .page(pageIndex)
        .size(ROWS_PER_PAGE)
        .build();

    Task<Response> task = new Task<>() {
      @Override
      protected Response call() {
        return socketRequestService.send(new Request(ActionType.SEARCH_CUSTOMERS, dto));
      }
    };
    currentTask = task;

    task.setOnSucceeded(e -> {
      Response res = task.getValue();
      if (res != null && res.isSuccess() && res.getData() instanceof CustomerPageDTO page) {
        customerTable.getItems().setAll(page.getCustomers());
        // Cập nhật tổng số trang từ Server[cite: 17]
        if (pagination != null) {
          pagination.setPageCount(Math.max(1, page.getTotalPages()));
        }
      } else {
        showError("Khách hàng", res == null ? "Không có phản hồi từ server." : res.getMessage());
      }
    });

    task.setOnFailed(e -> showError("Khách hàng", "Đã xảy ra lỗi khi tải danh sách khách hàng."));

    start(task, "customer-search");
  }

  private void openCustomerHistory() {
    CustomerDTO selected = customerTable.getSelectionModel().getSelectedItem();
    if (selected == null) {
      showWarning("Lịch sử mua vé", "Vui lòng chọn một khách hàng trước.");
      return;
    }

    try {
      FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/ui/views/customer-history-view.fxml"));
      Parent root = loader.load();

      Object controller = loader.getController();
      if (controller instanceof CustomerHistoryController customerHistoryController) {
        customerHistoryController.initCustomer(selected);
      }

      Stage dialogStage = new Stage();
      dialogStage.setTitle("Lịch sử mua vé");
      dialogStage.initModality(Modality.WINDOW_MODAL);
      dialogStage.initOwner(customerTable.getScene().getWindow());
      dialogStage.setScene(new Scene(root, 1300, 700));
      dialogStage.setResizable(false);
      dialogStage.show();
    } catch (IOException e) {
      showError("Lịch sử mua vé", "Không thể mở màn hình lịch sử mua vé: " + e.getMessage());
    }
  }

  private void openAddCustomerDialog() {
    openCustomerDialog(null);
  }

  private void openEditCustomerDialog() {
    CustomerDTO selected = customerTable.getSelectionModel().getSelectedItem();
    if (selected == null)
      return;
    openCustomerDialog(selected);
  }

  private void openCustomerDialog(CustomerDTO selectedForEdit) {
    try {
      FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/ui/views/khach-hang-dialog.fxml"));
      Parent root = loader.load();

      Object controller = loader.getController();
      if (controller instanceof KhachHangDialogController dialogController) {
        dialogController
            .setOnSaved(() -> loadCustomers(normalize(searchField.getText()), pagination.getCurrentPageIndex()));
        if (selectedForEdit == null) {
          dialogController.initForAdd();
        } else {
          dialogController.initForEdit(selectedForEdit);
        }
      }

      Stage dialogStage = new Stage();
      dialogStage.setTitle(selectedForEdit == null ? "Thêm khách hàng" : "Cập nhật khách hàng");
      dialogStage.initModality(Modality.WINDOW_MODAL);
      dialogStage.initOwner(customerTable.getScene().getWindow());
      dialogStage.setScene(new Scene(root));
      dialogStage.setResizable(false);
      dialogStage.show();
    } catch (IOException e) {
      showError("Khách hàng", "Không thể mở form khách hàng: " + e.getMessage());
    }
  }

  private void handleDeleteCustomer() {
    CustomerDTO selected = customerTable.getSelectionModel().getSelectedItem();
    if (selected == null)
      return;

    String employeeId = ClientSessionContext.getInstance().getEmployeeId();
    if (employeeId == null || employeeId.isBlank()) {
      showError("Xóa khách hàng", "Không xác định được nhân viên đang đăng nhập.");
      return;
    }

    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
    confirm.setTitle("Xóa khách hàng");
    confirm.setHeaderText(null);
    confirm.setContentText("Bạn có chắc chắn muốn xóa/vô hiệu hóa khách hàng này?\nKH: " + selected.getFullName());

    if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
      CustomerDeleteRequestDTO dto = CustomerDeleteRequestDTO.builder()
          .customerId(selected.getCustomerId())
          .requestEmployeeId(employeeId)
          .build();

      Task<Response> task = new Task<>() {
        @Override
        protected Response call() {
          return socketRequestService.send(new Request(ActionType.DELETE_CUSTOMER, dto));
        }
      };
      currentTask = task;

      task.setOnSucceeded(e -> {
        Response res = task.getValue();
        if (res != null && res.isSuccess()) {
          showInfo("Xóa khách hàng", res.getMessage());
          loadCustomers(normalize(searchField.getText()), pagination.getCurrentPageIndex());
        } else {
          showError("Xóa khách hàng", res == null ? "Không có phản hồi từ server." : res.getMessage());
        }
      });
      task.setOnFailed(ev -> showError("Xóa khách hàng", "Đã xảy ra lỗi khi xóa khách hàng."));
      start(task, "customer-delete");
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

  private void registerCloseCleanupHook() {
    if (customerTable == null)
      return;
    customerTable.sceneProperty().addListener((obs, oldScene, newScene) -> {
      if (oldScene != null && newScene == null) {
        cleanupOnClose();
      } else if (newScene != null) {
        Window window = newScene.getWindow();
        if (window != null)
          window.addEventHandler(WindowEvent.WINDOW_HIDDEN, e -> cleanupOnClose());
        newScene.windowProperty().addListener((obsWin, oldWin, newWin) -> {
          if (newWin != null)
            newWin.addEventHandler(WindowEvent.WINDOW_HIDDEN, e -> cleanupOnClose());
        });
      }
    });
  }

  private void cleanupOnClose() {
    if (!cleanupDone.compareAndSet(false, true))
      return;
    if (currentTask != null)
      currentTask.cancel(true);
    executor.shutdown();
    if (customerTable != null)
      customerTable.getItems().clear();
  }

  private static String normalize(String value) {
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
}