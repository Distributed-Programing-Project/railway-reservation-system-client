package vn.edu.iuh.fit.client.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import vn.edu.iuh.fit.client.service.SocketRequestService;
import vn.edu.iuh.fit.common.command.ActionType;
import vn.edu.iuh.fit.common.constant.TrainStatus;
import vn.edu.iuh.fit.common.dto.TrainDTO;
import vn.edu.iuh.fit.common.dto.UpdateTrainStatusDTO;
import vn.edu.iuh.fit.common.request.Request;
import vn.edu.iuh.fit.common.response.Response;

public class UpdateTrainStatusDialogController {

    @FXML private Label lblTrainCode;
    @FXML private Label lblCurrentStatus;
    @FXML private ComboBox<TrainStatus> cmbNewStatus;
    @FXML private Button btnSave;
    @FXML private Button btnCancel;

    private TrainDTO train;

    @FXML
    private void initialize() {
        cmbNewStatus.getItems().addAll(TrainStatus.values());
        cmbNewStatus.setConverter(new StringConverter<>() {
            @Override public String toString(TrainStatus s) { return s == null ? "" : s.getName(); }
            @Override public TrainStatus fromString(String s) { return null; }
        });
    }

    public void initForTrain(TrainDTO train) {
        this.train = train;
        lblTrainCode.setText("Tàu: " + train.getTrainCode());
        lblCurrentStatus.setText("Trạng thái hiện tại: " + (train.getStatus() != null ? train.getStatus().getName() : "—"));
        cmbNewStatus.setValue(train.getStatus());
    }

    @FXML
    private void handleSave() {
        TrainStatus newStatus = cmbNewStatus.getValue();
        if (newStatus == null) {
            showAlert("Vui lòng chọn trạng thái mới.");
            return;
        }
        if (newStatus == train.getStatus()) {
            showAlert("Trạng thái mới phải khác trạng thái hiện tại.");
            return;
        }

        UpdateTrainStatusDTO dto = UpdateTrainStatusDTO.builder()
            .trainId(train.getId())
            .status(newStatus)
            .build();

        Response res = new SocketRequestService().send(new Request(ActionType.UPDATE_TRAIN_STATUS, dto));

        if (res.isSuccess()) {
            showInfo("Trạng thái tàu " + train.getTrainCode() + " đã được cập nhật thành " + newStatus.getName() + ".");
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