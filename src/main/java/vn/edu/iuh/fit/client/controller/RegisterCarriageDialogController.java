package vn.edu.iuh.fit.client.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import vn.edu.iuh.fit.client.service.SocketRequestService;
import vn.edu.iuh.fit.common.command.ActionType;
import vn.edu.iuh.fit.common.constant.CarriageType;
import vn.edu.iuh.fit.common.dto.CreateCarriageDTO;
import vn.edu.iuh.fit.common.request.Request;
import vn.edu.iuh.fit.common.response.Response;

public class RegisterCarriageDialogController {

    @FXML private ComboBox<CarriageType> cmbCarriageType;
    @FXML private Label lblSeatPreview;
    @FXML private Button btnSave;
    @FXML private Button btnCancel;

    @FXML
    private void initialize() {
        cmbCarriageType.getItems().addAll(CarriageType.values());
        cmbCarriageType.setConverter(new StringConverter<>() {
            @Override public String toString(CarriageType t) { return t == null ? "" : t.getName(); }
            @Override public CarriageType fromString(String s) { return null; }
        });
        cmbCarriageType.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (selected != null) {
                lblSeatPreview.setText("Số ghế sẽ được sinh: " + selected.getSeatCount() + " ghế (" + selected.getSeatType().name() + ")");
            } else {
                lblSeatPreview.setText("Chọn loại toa để xem số ghế");
            }
        });
        lblSeatPreview.setText("Chọn loại toa để xem số ghế");
    }

    @FXML
    private void handleSave() {
        CarriageType selected = cmbCarriageType.getValue();
        if (selected == null) {
            showAlert("Vui lòng chọn loại toa.");
            return;
        }

        Request req = new Request(ActionType.CREATE_CARRIAGE, CreateCarriageDTO.builder().carriageType(selected).build());
        Response res = new SocketRequestService().send(req);

        if (res.isSuccess()) {
            showInfo("Toa mới đã được đăng ký vào hệ thống.");
            closeDialog();
        } else {
            showAlert(res.getMessage());
        }
    }

    @FXML
    private void handleCancel() {
        closeDialog();
    }

    private void closeDialog() {
        ((Stage) btnCancel.getScene().getWindow()).close();
    }

    private void showAlert(String msg) {
        Alert alert = new Alert(Alert.AlertType.WARNING, msg, ButtonType.OK);
        alert.setTitle("Thông báo");
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    private void showInfo(String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        alert.setTitle("Thành công");
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}