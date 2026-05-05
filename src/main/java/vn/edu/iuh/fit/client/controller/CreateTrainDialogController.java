package vn.edu.iuh.fit.client.controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import vn.edu.iuh.fit.client.service.SocketRequestService;
import vn.edu.iuh.fit.common.command.ActionType;
import vn.edu.iuh.fit.common.constant.CarriageType;
import vn.edu.iuh.fit.common.dto.CarriageDTO;
import vn.edu.iuh.fit.common.dto.CreateTrainDTO;
import vn.edu.iuh.fit.common.request.Request;
import vn.edu.iuh.fit.common.response.Response;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CreateTrainDialogController {

    @FXML private TextField txtTrainCode;
    @FXML private ComboBox<CarriageType> cmbPoolFilter;
    @FXML private ListView<CarriageDTO> poolList;
    @FXML private ListView<CarriageDTO> selectedList;
    @FXML private Label lblCarriageCount;
    @FXML private Button btnCancel;

    private List<CarriageDTO> allPoolCarriages = new ArrayList<>();
    private final ObservableList<CarriageDTO> selectedCarriages = FXCollections.observableArrayList();
    private final Set<String> selectedIds = new HashSet<>();

    @FXML
    private void initialize() {
        setupCarriageTypeCells(poolList);
        setupCarriageTypeCells(selectedList);
        selectedList.setItems(selectedCarriages);

        setupPoolFilter();
        loadPool();

        selectedCarriages.addListener((javafx.collections.ListChangeListener<CarriageDTO>) c -> updateCountLabel());
    }

    private void setupCarriageTypeCells(ListView<CarriageDTO> listView) {
        listView.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(CarriageDTO item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); return; }
                setText(item.getType() != null
                    ? item.getType().getName() + " — " + carriageSeatInfo(item)
                    : item.getId());
            }
        });
    }

    private String carriageSeatInfo(CarriageDTO carriage) {
        if (carriage.getType() == null) return "";
        return carriage.getType().getSeatCount() + " ghế";
    }

    private void setupPoolFilter() {
        cmbPoolFilter.getItems().add(null);
        cmbPoolFilter.getItems().addAll(CarriageType.values());
        cmbPoolFilter.setConverter(new StringConverter<>() {
            @Override public String toString(CarriageType t) { return t == null ? "Tất cả loại" : t.getName(); }
            @Override public CarriageType fromString(String s) { return null; }
        });
        cmbPoolFilter.getSelectionModel().selectFirst();
        cmbPoolFilter.getSelectionModel().selectedItemProperty().addListener((obs, old, val) -> refreshPoolDisplay());
    }

    private void loadPool() {
        Response res = new SocketRequestService().send(new Request(ActionType.FIND_UNASSIGNED_CARRIAGES, null));
        if (res.isSuccess() && res.getData() != null) {
            allPoolCarriages = (List<CarriageDTO>) res.getData();
        } else {
            allPoolCarriages = new ArrayList<>();
        }
        refreshPoolDisplay();
    }

    private void refreshPoolDisplay() {
        CarriageType filter = cmbPoolFilter.getValue();
        List<CarriageDTO> visible = allPoolCarriages.stream()
            .filter(c -> !selectedIds.contains(c.getId()))
            .filter(c -> filter == null || c.getType() == filter)
            .toList();
        poolList.setItems(FXCollections.observableArrayList(visible));
    }

    private void updateCountLabel() {
        int count = selectedCarriages.size();
        lblCarriageCount.setText(count + " / 16 toa");
        lblCarriageCount.getStyleClass().removeAll("count-badge-warn", "count-badge-error");
        if (count > 16) lblCarriageCount.getStyleClass().add("count-badge-error");
        else if (count < 3) lblCarriageCount.getStyleClass().add("count-badge-warn");
    }

    @FXML
    private void handleAddCarriage() {
        CarriageDTO selected = poolList.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        selectedIds.add(selected.getId());
        selectedCarriages.add(selected);
        refreshPoolDisplay();
    }

    @FXML
    private void handleRemoveCarriage() {
        CarriageDTO selected = selectedList.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        selectedIds.remove(selected.getId());
        selectedCarriages.remove(selected);
        refreshPoolDisplay();
    }

    @FXML
    private void handleMoveUp() {
        int idx = selectedList.getSelectionModel().getSelectedIndex();
        if (idx <= 0) return;
        CarriageDTO item = selectedCarriages.remove(idx);
        selectedCarriages.add(idx - 1, item);
        selectedList.getSelectionModel().select(idx - 1);
    }

    @FXML
    private void handleMoveDown() {
        int idx = selectedList.getSelectionModel().getSelectedIndex();
        if (idx < 0 || idx >= selectedCarriages.size() - 1) return;
        CarriageDTO item = selectedCarriages.remove(idx);
        selectedCarriages.add(idx + 1, item);
        selectedList.getSelectionModel().select(idx + 1);
    }

    @FXML
    private void handleSave() {
        List<String> errors = validateForm();
        if (!errors.isEmpty()) {
            showAlert(String.join("\n", errors));
            return;
        }

        List<String> carriageIds = selectedCarriages.stream().map(CarriageDTO::getId).toList();
        CreateTrainDTO dto = CreateTrainDTO.builder()
            .trainCode(txtTrainCode.getText().trim())
            .carriageIds(carriageIds)
            .build();

        Response res = new SocketRequestService().send(new Request(ActionType.CREATE_TRAIN, dto));

        if (res.isSuccess()) {
            showInfo("Tàu " + dto.getTrainCode() + " đã được tạo thành công.");
            closeDialog();
        } else {
            showAlert(res.getMessage());
        }
    }

    @FXML
    private void handleCancel() {
        closeDialog();
    }

    private List<String> validateForm() {
        List<String> errors = new ArrayList<>();
        String code = txtTrainCode.getText();
        if (code == null || code.isBlank()) {
            errors.add("- Mác tàu không được để trống");
        } else if (!code.trim().matches("^[A-Za-z0-9]{1,10}$")) {
            errors.add("- Mác tàu chỉ chứa chữ cái và số, tối đa 10 ký tự");
        }
        if (selectedCarriages.size() < 3) {
            errors.add("- Tàu phải có tối thiểu 3 toa");
        }
        if (selectedCarriages.size() > 16) {
            errors.add("- Tàu không được vượt quá 16 toa");
        }
        return errors;
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