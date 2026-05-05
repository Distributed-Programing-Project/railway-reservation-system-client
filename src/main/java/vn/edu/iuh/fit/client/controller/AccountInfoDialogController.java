package vn.edu.iuh.fit.client.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import vn.edu.iuh.fit.common.dto.AccountCreatedDTO;

public class AccountInfoDialogController {

    @FXML private Label lblUsername;
    @FXML private Label lblPassword;

    public void initData(AccountCreatedDTO dto) {
        lblUsername.setText(dto.getUsername());
        lblPassword.setText(dto.getTemporaryPassword());
    }

    @FXML
    private void handleClose() {
        ((Stage) lblUsername.getScene().getWindow()).close();
    }
}
