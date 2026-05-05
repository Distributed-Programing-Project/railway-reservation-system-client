package vn.edu.iuh.fit.client.controller;

import java.io.ByteArrayInputStream;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
import vn.edu.iuh.fit.common.dto.PaymentCreateResponseDTO;

public class InternalPaymentDialogController {

  private static final NumberFormat MONEY = NumberFormat.getInstance(new Locale("vi", "VN"));
  private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

  @FXML private ImageView imgQr;
  @FXML private Label lblAmount;
  @FXML private Label lblRef;
  @FXML private Label lblExpire;
  @FXML private Label lblOrder;
  @FXML private TextArea txtPayload;

  private Runnable onSimulateSuccess;

  public void setPayment(PaymentCreateResponseDTO dto, double totalAmount, Runnable onSimulateSuccess) {
    this.onSimulateSuccess = onSimulateSuccess;
    if (dto == null) return;

    lblAmount.setText("Số tiền: " + MONEY.format(totalAmount) + " đ");
    lblRef.setText("REF: " + safe(dto.getReferenceCode()));
    lblOrder.setText("Order: " + safe(dto.getPaymentOrderId()));
    lblExpire.setText("Hết hạn: " + (dto.getExpiresAt() == null ? "--" : dto.getExpiresAt().format(DATE_TIME)));
    txtPayload.setText(safe(dto.getQrPayload()));
    imgQr.setImage(toImage(dto.getQrPng()));
  }

  @FXML
  public void handleSimulateSuccess() {
    if (onSimulateSuccess != null) {
      onSimulateSuccess.run();
    }
  }

  @FXML
  public void handleClose() {
    Stage stage = (Stage) imgQr.getScene().getWindow();
    stage.close();
  }

  private Image toImage(byte[] pngBytes) {
    if (pngBytes == null || pngBytes.length == 0) return null;
    try {
      return new Image(new ByteArrayInputStream(pngBytes));
    } catch (Exception e) {
      return null;
    }
  }

  private String safe(String value) {
    return value == null ? "--" : value;
  }
}
