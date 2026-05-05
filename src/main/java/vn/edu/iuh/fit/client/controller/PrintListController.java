package vn.edu.iuh.fit.client.controller;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import vn.edu.iuh.fit.common.dto.IssuedTicketDTO;

public class PrintListController {

  @FXML
  private TableView<IssuedTicketDTO> tblVe;

  @FXML
  private TableColumn<IssuedTicketDTO, String> colMaVe;

  @FXML
  private TableColumn<IssuedTicketDTO, String> colHanhTrinh;

  @FXML
  private TableColumn<IssuedTicketDTO, String> colCho;

  @FXML
  private TableColumn<IssuedTicketDTO, String> colTenKhach;

  @FXML
  private TableColumn<IssuedTicketDTO, Void> colAction;

  @FXML
  private Button btnDong;

  private Stage dialogStage;
  private final ObservableList<IssuedTicketDTO> items = FXCollections.observableArrayList();

  public void setDialogStage(Stage dialogStage) {
    this.dialogStage = dialogStage;
  }

  public void setTickets(List<IssuedTicketDTO> tickets) {
    runOnFxThread(() -> items.setAll(tickets == null ? List.of() : new ArrayList<>(tickets)));
  }

  @FXML
  public void initialize() {
    tblVe.setItems(items);

    colMaVe.setCellValueFactory(cell -> new SimpleStringProperty(safe(cell.getValue() == null ? null : cell.getValue().getTicketId())));
    colHanhTrinh.setCellValueFactory(cell -> {
      IssuedTicketDTO t = cell.getValue();
      String journey = safe(t == null ? null : t.getDepartureStation()) + " -> " + safe(t == null ? null : t.getDestinationStation());
      return new SimpleStringProperty(journey);
    });
    colCho.setCellValueFactory(cell -> {
      IssuedTicketDTO t = cell.getValue();
      String seat = safe(t == null ? null : t.getCarriageName()) + " - Ghế " + safe(t == null ? null : t.getSeatNumber());
      return new SimpleStringProperty(seat);
    });
    colTenKhach.setCellValueFactory(cell -> new SimpleStringProperty(safe(cell.getValue() == null ? null : cell.getValue().getPassengerName())));

    colAction.setCellFactory(col -> new TableCell<>() {
      private final Button btn = new Button("In Vé");

      {
        btn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-size: 11px;");
        btn.setOnAction(e -> {
          IssuedTicketDTO t = getTableView().getItems().get(getIndex());
          if (t == null) {
            return;
          }
          openPreview("In vé " + safe(t.getTicketId()), List.of(t));
        });
      }

      @Override
      protected void updateItem(Void item, boolean empty) {
        super.updateItem(item, empty);
        setGraphic(empty ? null : btn);
      }
    });
  }

  @FXML
  private void closeDialog() {
    if (dialogStage != null) {
      dialogStage.close();
      return;
    }
    if (btnDong != null && btnDong.getScene() != null) {
      ((Stage) btnDong.getScene().getWindow()).close();
    }
  }

  private void openPreview(String title, List<IssuedTicketDTO> tickets) {
    runOnFxThread(() -> {
      try {
        List<IssuedTicketDTO> previewTickets = tickets == null ? List.of() : tickets;
        System.err.println("[UC001] print list preview title=" + title + ", tickets=" + previewTickets.size());
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
      } catch (IOException e) {
        showAlert(Alert.AlertType.ERROR, "In vé", "Không thể mở xem trước: " + e.getMessage());
      } catch (Exception e) {
        showAlert(Alert.AlertType.ERROR, "In vé", "Không thể render xem trước: " + e.getMessage());
      }
    });
  }

  private static String safe(String v) {
    return v == null || v.isBlank() ? "--" : v;
  }

  private void showAlert(Alert.AlertType type, String title, String message) {
    runOnFxThread(() -> {
      Alert alert = new Alert(type);
      alert.setTitle(title);
      alert.setHeaderText(null);
      alert.setContentText(message);
      alert.showAndWait();
    });
  }

  private static void runOnFxThread(Runnable r) {
    if (Platform.isFxApplicationThread()) {
      r.run();
    } else {
      Platform.runLater(r);
    }
  }
}
