package vn.edu.iuh.fit.client.controller;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;
import vn.edu.iuh.fit.client.service.ExchangeTicketClientService;
import vn.edu.iuh.fit.common.dto.ExchangeEligibleTicketDTO;
import vn.edu.iuh.fit.common.response.Response;

public class ExchangeTicketSearchController {

  @FXML
  private TextField txtSearchCCCD;
  @FXML
  private Button btnTimKiem;
  @FXML
  private Button btnTienHanhDoi;

  @FXML
  private TableView<ExchangeEligibleTicketDTO> tblDanhSachVe;
  @FXML
  private TableColumn<ExchangeEligibleTicketDTO, String> colMaVe;
  @FXML
  private TableColumn<ExchangeEligibleTicketDTO, String> colTau;
  @FXML
  private TableColumn<ExchangeEligibleTicketDTO, String> colHanhTrinh;
  @FXML
  private TableColumn<ExchangeEligibleTicketDTO, String> colGhe;
  @FXML
  private TableColumn<ExchangeEligibleTicketDTO, Double> colGiaVe;
  @FXML
  private TableColumn<ExchangeEligibleTicketDTO, String> colLyDo;

  private final ExchangeTicketClientService service = new ExchangeTicketClientService();
  private final ExecutorService executor = Executors.newSingleThreadExecutor();

  @FXML
  public void initialize() {
    tblDanhSachVe.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

    colMaVe.setCellValueFactory(new PropertyValueFactory<>("ticketId"));
    colTau.setCellValueFactory(new PropertyValueFactory<>("trainCode"));
    colGhe.setCellValueFactory(new PropertyValueFactory<>("seatNumber"));
    colGiaVe.setCellValueFactory(new PropertyValueFactory<>("ticketPrice"));
    colLyDo.setCellValueFactory(new PropertyValueFactory<>("ineligibleReason"));

    colHanhTrinh.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
        c.getValue().getDepartureStation() + " -> " + c.getValue().getDestinationStation()));

    btnTimKiem.setOnAction(e -> doSearch());
    txtSearchCCCD.setOnAction(e -> doSearch());
    btnTienHanhDoi.setOnAction(e -> handleTienHanhDoi());

    tblDanhSachVe.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
      boolean hasIneligible = tblDanhSachVe.getSelectionModel().getSelectedItems().stream()
          .anyMatch(t -> !t.isEligible());
      btnTienHanhDoi.setDisable(tblDanhSachVe.getSelectionModel().isEmpty() || hasIneligible);
    });
  }

  private void doSearch() {
    String query = txtSearchCCCD.getText().trim();
    if (query.isEmpty()) {
      showAlert(Alert.AlertType.WARNING, "Vui lòng nhập CCCD người mua.");
      return;
    }

    btnTimKiem.setDisable(true);
    Task<Response> task = new Task<>() {
      @Override
      protected Response call() {
        return service.searchTicketsForExchange(query);
      }
    };

    task.setOnSucceeded(e -> {
      btnTimKiem.setDisable(false);
      Response res = task.getValue();
      if (res.isSuccess() && res.getData() instanceof List<?> list) {
        List<ExchangeEligibleTicketDTO> items = list.stream()
            .map(o -> (ExchangeEligibleTicketDTO) o).toList();
        tblDanhSachVe.setItems(FXCollections.observableArrayList(items));
      } else {
        showAlert(Alert.AlertType.ERROR, res.getMessage());
      }
    });
    task.setOnFailed(e -> {
      btnTimKiem.setDisable(false);
      showAlert(Alert.AlertType.ERROR, "Lỗi kết nối Server.");
    });

    executor.execute(task);
  }

  private void handleTienHanhDoi() {
    List<String> oldTicketIds = tblDanhSachVe.getSelectionModel().getSelectedItems().stream()
        .map(ExchangeEligibleTicketDTO::getTicketId).toList();

    try {
      // Mở màn hình Bán vé (Wizard) và kích hoạt chế độ ĐỔI VÉ
      FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/ui/views/sell-ticket-wizard.fxml"));
      Parent root = loader.load();

      SellTicketWizardController wizardController = loader.getController();
      wizardController.initExchangeMode(oldTicketIds);

      // Tùy cách điều hướng của sếp, sếp có thể nhúng 'root' vào Main Pane thay vì mở
      // Stage mới
      Stage stage = new Stage();
      stage.setTitle("Chọn vé mới");
      stage.setScene(new Scene(root, 1200, 800));
      stage.show();

    } catch (Exception e) {
      e.printStackTrace();
      showAlert(Alert.AlertType.ERROR, "Không thể tải giao diện chọn vé mới.");
    }
  }

  private void showAlert(Alert.AlertType type, String message) {
    Alert alert = new Alert(type);
    alert.setContentText(message);
    alert.showAndWait();
  }
}