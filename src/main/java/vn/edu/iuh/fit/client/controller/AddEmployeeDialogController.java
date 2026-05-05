package vn.edu.iuh.fit.client.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import vn.edu.iuh.fit.client.service.SocketRequestService;
import vn.edu.iuh.fit.common.command.ActionType;
import vn.edu.iuh.fit.common.dto.EmployeeDTO;
import vn.edu.iuh.fit.common.request.Request;
import vn.edu.iuh.fit.common.response.Response;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class AddEmployeeDialogController {

    @FXML private TextField txtName;
    @FXML private TextField txtNationalId;
    @FXML private DatePicker dpDateOfBirth;
    @FXML private ComboBox<String> comboGender;
    @FXML private TextField txtAddress;
    @FXML private TextField txtPhone;
    @FXML private TextField txtEmail;
    @FXML private ComboBox<String> comboType;
    @FXML private Button btnSave;
    @FXML private Button btnCancel;

    @FXML
    private void initialize() {
        comboGender.getItems().setAll("Nam", "Nữ");
        comboType.getItems().setAll("Nhân viên thường", "Quản lý");

        dpDateOfBirth.setConverter(new StringConverter<>() {
            private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            @Override public String toString(LocalDate date) {
                return date != null ? formatter.format(date) : "";
            }
            @Override public LocalDate fromString(String text) {
                return (text == null || text.isBlank()) ? null : LocalDate.parse(text, formatter);
            }
        });
    }

    @FXML
    private void handleSave() {
        List<String> errors = validateForm();
        if (!errors.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Vui lòng kiểm tra lại:\n" + String.join("\n", errors));
            return;
        }

        EmployeeDTO dto = EmployeeDTO.builder()
                .employeeName(txtName.getText().trim())
                .nationalId(txtNationalId.getText().trim())
                .dateOfBirth(dpDateOfBirth.getValue())
                .gender("Nam".equals(comboGender.getValue()))
                .address(txtAddress.getText().isBlank() ? null : txtAddress.getText().trim())
                .phoneNumber(txtPhone.getText().trim())
                .email(txtEmail.getText().trim())
                .isManager("Quản lý".equals(comboType.getValue()))
                .build();

        Request req = new Request(ActionType.CREATE_EMPLOYEE, dto);
        Response res = new SocketRequestService().send(req);

        if (res.isSuccess()) {
            showAlert(Alert.AlertType.INFORMATION, "Tạo nhân viên thành công!");
            closeDialog();
        } else {
            showAlert(Alert.AlertType.ERROR, res.getMessage());
        }
    }

    @FXML
    private void handleCancel() {
        closeDialog();
    }

    private void closeDialog() {
        ((Stage) btnCancel.getScene().getWindow()).close();
    }

    private List<String> validateForm() {
        List<String> errors = new ArrayList<>();

        String name = txtName.getText();
        if (name == null || name.isBlank()) {
            errors.add("- Họ tên không được để trống");
        }

        String nationalId = txtNationalId.getText();
        if (nationalId == null || !nationalId.trim().matches("\\d{9}|\\d{12}")) {
            errors.add("- CCCD phải là 9 hoặc 12 chữ số");
        }

        if (dpDateOfBirth.getValue() == null) {
            errors.add("- Ngày sinh không được để trống");
        }

        if (comboGender.getValue() == null) {
            errors.add("- Vui lòng chọn giới tính");
        }

        String phone = txtPhone.getText();
        if (phone == null || !phone.trim().matches("\\d{10}")) {
            errors.add("- Số điện thoại phải có đúng 10 chữ số");
        }

        String email = txtEmail.getText();
        if (email == null || email.isBlank()) {
            errors.add("- Email không được để trống");
        } else if (!email.trim().matches("^[\\w.+-]+@[\\w.-]+\\.\\w+$")) {
            errors.add("- Email không đúng định dạng");
        }

        if (comboType.getValue() == null) {
            errors.add("- Vui lòng chọn loại nhân viên");
        }

        return errors;
    }

    private void showAlert(Alert.AlertType type, String msg) {
        Alert alert = new Alert(type, msg, ButtonType.OK);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}
