package vn.edu.iuh.fit.client.controller;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import vn.edu.iuh.fit.client.service.SocketRequestService;
import vn.edu.iuh.fit.common.command.ActionType;
import vn.edu.iuh.fit.common.dto.CustomerDTO;
import vn.edu.iuh.fit.common.request.Request;
import vn.edu.iuh.fit.common.response.Response;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class KhachHangDialogController {

  private final SocketRequestService socketRequestService = new SocketRequestService();
  private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
    Thread t = new Thread(r);
    t.setDaemon(true);
    t.setName("customer-dialog-" + UUID.randomUUID());
    return t;
  });

  private final AtomicBoolean cleanupDone = new AtomicBoolean(false);
  private volatile Task<?> currentTask;

  private boolean editMode = false;
  private Runnable onSaved;

  @FXML
  private Label titleLabel;

  @FXML
  private Label customerIdLabel;

  @FXML
  private TextField customerIdField;

  @FXML
  private TextField nameField;

  @FXML
  private CheckBox isForeignerCheckBox;

  @FXML
  private Label idLabel;

  @FXML
  private TextField idField;

  @FXML
  private TextField phoneField;

  @FXML
  private TextField emailField;

  @FXML
  private Button btnCancel;

  @FXML
  private Button btnSave;

  @FXML
  public void initialize() {
    if (btnCancel != null) btnCancel.setOnAction(e -> closeWindow());
    if (btnSave != null) btnSave.setOnAction(e -> handleSave());

    if (isForeignerCheckBox != null) {
      isForeignerCheckBox.selectedProperty().addListener((obs, oldV, newV) -> updateDocumentLabel());
    }
    updateDocumentLabel();

    registerCloseCleanupHook();
  }

  public void setOnSaved(Runnable onSaved) {
    this.onSaved = onSaved;
  }

  public void initForAdd() {
    editMode = false;
    if (titleLabel != null) titleLabel.setText("Thêm khách hàng");
    setCustomerIdRowVisible(false);
    if (nameField != null) nameField.setText("");
    if (isForeignerCheckBox != null) isForeignerCheckBox.setSelected(false);
    if (idField != null) idField.setText("");
    if (phoneField != null) phoneField.setText("");
    if (emailField != null) emailField.setText("");
    updateDocumentLabel();
  }

  public void initForEdit(CustomerDTO dto) {
    editMode = true;
    if (titleLabel != null) titleLabel.setText("Cập nhật khách hàng");
    setCustomerIdRowVisible(true);

    if (dto != null) {
      if (customerIdField != null) customerIdField.setText(safe(dto.getCustomerId(), ""));
      if (nameField != null) nameField.setText(safe(dto.getFullName(), ""));

      boolean foreigner = dto.getPassport() != null && !dto.getPassport().isBlank();
      if (isForeignerCheckBox != null) isForeignerCheckBox.setSelected(foreigner);
      if (idField != null) {
        idField.setText(foreigner ? safe(dto.getPassport(), "") : safe(dto.getIdCard(), ""));
      }
      if (phoneField != null) phoneField.setText(safe(dto.getPhone(), ""));
      if (emailField != null) emailField.setText(safe(dto.getEmail(), ""));
    }

    updateDocumentLabel();
  }

  private void setCustomerIdRowVisible(boolean visible) {
    if (customerIdLabel != null) {
      customerIdLabel.setVisible(visible);
      customerIdLabel.setManaged(visible);
    }
    if (customerIdField != null) {
      customerIdField.setVisible(visible);
      customerIdField.setManaged(visible);
      customerIdField.setEditable(false);
      customerIdField.setDisable(true);
    }
  }

  private void updateDocumentLabel() {
    boolean foreigner = isForeignerCheckBox != null && isForeignerCheckBox.isSelected();
    if (idLabel != null) idLabel.setText(foreigner ? "Số hộ chiếu:" : "Số CCCD:");
    if (idField != null) idField.setPromptText(foreigner ? "Nhập số hộ chiếu" : "Nhập số CCCD");
  }

  private void handleSave() {
    String fullName = normalize(nameField != null ? nameField.getText() : null);
    boolean foreigner = isForeignerCheckBox != null && isForeignerCheckBox.isSelected();
    String document = normalize(idField != null ? idField.getText() : null);
    String phone = normalize(phoneField != null ? phoneField.getText() : null);
    String email = normalize(emailField != null ? emailField.getText() : null);

    if (fullName == null) {
      showWarning("Khách hàng", "Họ và tên không được để trống.");
      return;
    }
    if (document == null) {
      showWarning("Khách hàng", foreigner ? "Vui lòng nhập số hộ chiếu." : "Vui lòng nhập số CCCD.");
      return;
    }

    if (phone != null && !phone.matches("^\\d{10}$")) {
      showWarning("Khách hàng", "Số điện thoại phải đúng 10 chữ số.");
      return;
    }
    if (email != null && !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
      showWarning("Khách hàng", "Email không đúng định dạng.");
      return;
    }

    CustomerDTO dto = CustomerDTO.builder()
        .customerId(editMode ? normalize(customerIdField != null ? customerIdField.getText() : null) : null)
        .fullName(fullName)
        .idCard(foreigner ? null : document)
        .passport(foreigner ? document : null)
        .phone(phone)
        .email(email)
        .build();

    ActionType action = editMode ? ActionType.UPDATE_CUSTOMER : ActionType.CREATE_CUSTOMER;
    Task<Response> task = new Task<>() {
      @Override
      protected Response call() {
        return socketRequestService.send(new Request(action, dto));
      }
    };
    currentTask = task;

    task.setOnSucceeded(e -> {
      Response res = task.getValue();
      if (res != null && res.isSuccess()) {
        if (onSaved != null) {
          onSaved.run();
        }
        showInfo("Khách hàng", res.getMessage());
        closeWindow();
      } else {
        showError("Khách hàng", res == null ? "Không có phản hồi từ server." : res.getMessage());
      }
    });

    task.setOnFailed(e -> showError("Khách hàng", "Đã xảy ra lỗi khi lưu khách hàng."));

    start(task, editMode ? "customer-update" : "customer-create");
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

  private void closeWindow() {
    if (btnCancel == null || btnCancel.getScene() == null) return;
    Window window = btnCancel.getScene().getWindow();
    if (window != null) {
      window.hide();
    }
  }

  private void registerCloseCleanupHook() {
    if (btnCancel == null) return;
    btnCancel.sceneProperty().addListener((obs, oldScene, newScene) -> {
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
  }

  private static String normalize(String value) {
    if (value == null) return null;
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private static String safe(String v, String fallback) {
    return v == null ? fallback : v;
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

