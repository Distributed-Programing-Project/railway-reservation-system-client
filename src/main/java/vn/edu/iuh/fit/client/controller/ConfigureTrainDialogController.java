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
import vn.edu.iuh.fit.common.dto.TrainDTO;
import vn.edu.iuh.fit.common.dto.UpdateTrainCarriagesDTO;
import vn.edu.iuh.fit.common.request.Request;
import vn.edu.iuh.fit.common.response.Response;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ConfigureTrainDialogController {

    @FXML private Label lblTrainTitle;
    @FXML private ComboBox<CarriageType> cmbPoolFilter;
    @FXML private ListView<CarriageDTO> poolList;
    @FXML private ListView<CarriageDTO> currentList;
    @FXML private Label lblCarriageCount;
    @FXML private Button btnCancel;

    private TrainDTO train;
    private List<CarriageDTO> allPoolCarriages = new ArrayList<>();
    private final ObservableList<CarriageDTO> currentCarriages = FXCollections.observableArrayList();
    private final Set<String> currentIds = new HashSet<>();

    @FXML
    private void initialize() {
        setupCarriageCells(poolList);
        setupCarriageCells(currentList);
        currentList.setItems(currentCarriages);
        currentCarriages.addListener((javafx.collections.ListChangeListener<CarriageDTO>) c -> updateCountLabel());
    }

    public void initForTrain(TrainDTO train) {
        this.train = train;
        lblTrainTitle.setText("Tàu: " + train.getTrainCode());

        if (train.getCarriages() != null) {
            List<CarriageDTO> sorted = train.getCarriages().stream()
                .sorted(Comparator.comparingInt(CarriageDTO::getNumber))
                .toList();
            currentCarriages.setAll(sorted);
            sorted.forEach(c -> currentIds.add(c.getId()));
        }

        setupPoolFilter();
        loadPool();
    }

    private void setupCarriageCells(ListView<CarriageDTO> listView) {
        listView.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(CarriageDTO item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); return; }
                String position = item.getNumber() > 0 ? "Toa #" + item.getNumber() + " — " : "";
                String typeName = item.getType() != null ? item.getType().getName() : "Không rõ";
                String seats = item.getType() != null ? " (" + item.getType().getSeatCount() + " ghế)" : "";
                setText(position + typeName + seats);
            }
        });
    }

    private void setupPoolFilter() {
        cmbPoolFilter.getItems().clear();
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
            .filter(c -> !currentIds.contains(c.getId()))
            .filter(c -> filter == null || c.getType() == filter)
            .toList();
        poolList.setItems(FXCollections.observableArrayList(visible));
    }

    private void updateCountLabel() {
        int count = currentCarriages.size();
        lblCarriageCount.setText(count + " / 16 toa");
        lblCarriageCount.getStyleClass().removeAll("count-badge-warn", "count-badge-error");
        if (count > 16) lblCarriageCount.getStyleClass().add("count-badge-error");
        else if (count < 3) lblCarriageCount.getStyleClass().add("count-badge-warn");
    }

    @FXML
    private void handleAddCarriage() {
        CarriageDTO selected = poolList.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        currentIds.add(selected.getId());
        currentCarriages.add(selected);
        refreshPoolDisplay();
    }

    @FXML
    private void handleRemoveCarriage() {
        CarriageDTO selected = currentList.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        currentIds.remove(selected.getId());
        currentCarriages.remove(selected);
        refreshPoolDisplay();
    }

    @FXML
    private void handleMoveUp() {
        int idx = currentList.getSelectionModel().getSelectedIndex();
        if (idx <= 0) return;
        CarriageDTO item = currentCarriages.remove(idx);
        currentCarriages.add(idx - 1, item);
        currentList.getSelectionModel().select(idx - 1);
    }

    @FXML
    private void handleMoveDown() {
        int idx = currentList.getSelectionModel().getSelectedIndex();
        if (idx < 0 || idx >= currentCarriages.size() - 1) return;
        CarriageDTO item = currentCarriages.remove(idx);
        currentCarriages.add(idx + 1, item);
        currentList.getSelectionModel().select(idx + 1);
    }

    @FXML
    private void handleSave() {
        if (currentCarriages.size() < 3) {
            showAlert("Tàu phải có tối thiểu 3 toa.");
            return;
        }
        if (currentCarriages.size() > 16) {
            showAlert("Tàu không được vượt quá 16 toa.");
            return;
        }

        List<String> carriageIds = currentCarriages.stream().map(CarriageDTO::getId).toList();
        UpdateTrainCarriagesDTO dto = UpdateTrainCarriagesDTO.builder()
            .trainId(train.getId())
            .carriageIds(carriageIds)
            .build();

        Response res = new SocketRequestService().send(new Request(ActionType.UPDATE_TRAIN_CARRIAGES, dto));

        if (res.isSuccess()) {
            showInfo("Cấu hình tàu " + train.getTrainCode() + " đã được cập nhật.");
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