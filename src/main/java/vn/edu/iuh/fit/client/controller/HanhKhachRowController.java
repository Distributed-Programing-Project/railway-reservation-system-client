package vn.edu.iuh.fit.client.controller;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import vn.edu.iuh.fit.client.session.SaleWizardState.SelectedSeatDraft;
import vn.edu.iuh.fit.common.constant.TicketType;

public class HanhKhachRowController {
    private static final NumberFormat MONEY = NumberFormat.getInstance(new Locale("vi", "VN"));
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final double INSURANCE_FEE = 2_000d;

    private static final double DISCOUNT_CHILD_6_TO_LT10 = 0.25d;
    private static final double DISCOUNT_SENIOR_GTE60 = 0.15d;
    private static final double DISCOUNT_STUDENT = 0.10d;
    @FXML
    private HBox rowContainer;

    @FXML
    private VBox columnHanhKhach;

    @FXML
    private TextField txtHoTen;

    @FXML
    private TextField txtSoGiayTo;

    @FXML
    private ComboBox<TicketType> comboDoiTuong;

    @FXML
    private Button btnChonNgaySinh;

    @FXML
    private HBox boxMaVeNguoiLon;

    @FXML
    private TextField txtMaVeNguoiLon;

    @FXML
    private Label lblNoSeatStatus;

    @FXML
    private VBox columnChuyenTau;

    @FXML
    private Label lblTenTauDi;

    @FXML
    private Label lblThoiGianDi;

    @FXML
    private Label lblTenTauVe;

    @FXML
    private Label lblThoiGianVe;

    @FXML
    private VBox columnChoNgoi;

    @FXML
    private Label lblThongTinChoDi;

    @FXML
    private Label lblLoaiToaDi;

    @FXML
    private Label lblThongTinChoVe;

    @FXML
    private Label lblLoaiToaVe;

    @FXML
    private VBox columnGiaVe;

    @FXML
    private Label lblGiaVe;

    @FXML
    private VBox columnGiamGia;

    @FXML
    private Label lblGiamGia;

    @FXML
    private VBox columnBaoHiem;

    @FXML
    private Label lblBaoHiem;

    @FXML
    private VBox columnThanhTien;

    @FXML
    private Label lblThanhTien;

    private BanVeController coordinator;
    private Step3PassengerController parentController;

    private SelectedSeatDraft outboundSeat;
    private SelectedSeatDraft returnSeat;

    private LocalDate dateOfBirth;

    private boolean freeChildUnder6;
    private boolean childUnder6NoSeat;
    private boolean seatReleasedForChildUnder6;
    private boolean seatReleaseInProgress;
    private boolean requiresAdultTicket;
    private boolean studentCardVerified;

    private int passengerIndex = -1;

    private Runnable onDataChange;

    public void setCoordinator(BanVeController coordinator) {
        this.coordinator = coordinator;
    }

    public void setOnDataChange(Runnable onDataChange) {
        this.onDataChange = onDataChange;
    }

    @FXML
    public void initialize() {
        comboDoiTuong.setItems(FXCollections.observableArrayList(TicketType.values()));
        comboDoiTuong.setValue(TicketType.NORMAL);
        comboDoiTuong.setConverter(new StringConverter<>() {
            @Override
            public String toString(TicketType object) {
                return ticketTypeLabel(object);
            }

            @Override
            public TicketType fromString(String string) {
                return null;
            }
        });
        comboDoiTuong.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(TicketType item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? "" : ticketTypeLabel(item));
            }
        });
        comboDoiTuong.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(TicketType item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? "" : ticketTypeLabel(item));
            }
        });

        comboDoiTuong.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            applyTicketTypeVisibility();
            validateAgeAndApplyPolicy();
            recalcPricePreview();
            notifyDataChange();
        });

        txtHoTen.textProperty().addListener((obs, oldVal, newVal) -> notifyDataChange());
        txtSoGiayTo.textProperty().addListener((obs, oldVal, newVal) -> notifyDataChange());

        hideExtraControls();
        applyTicketTypeVisibility();
    }

    public void initData() {
        updateTicketInfoLabels();
        recalcPricePreview();
    }

    public void setData(SelectedSeatDraft outboundSeat, SelectedSeatDraft returnSeat,
            Step3PassengerController parentController,
            int passengerIndex) {
        this.outboundSeat = outboundSeat;
        this.returnSeat = returnSeat;
        this.parentController = parentController;
        this.passengerIndex = passengerIndex;
        initData();
    }

    public void restorePassengerDraft(String fullName, String documentNumber, TicketType ticketType,
            LocalDate dateOfBirth,
            boolean studentCardVerified, String adultTicketCode) {
        if (fullName != null) {
            txtHoTen.setText(fullName);
        }
        if (documentNumber != null) {
            txtSoGiayTo.setText(documentNumber);
        }
        if (ticketType != null) {
            comboDoiTuong.setValue(ticketType);
        }
        this.studentCardVerified = studentCardVerified;
        if (adultTicketCode != null) {
            txtMaVeNguoiLon.setText(adultTicketCode);
        }
        this.dateOfBirth = dateOfBirth;
        if (dateOfBirth != null) {
            btnChonNgaySinh.setText("\uD83D\uDCC5 " + dateOfBirth);
        }

        applyTicketTypeVisibility();
        validateAgeAndApplyPolicy();
        recalcPricePreview();
    }

    public VBox getColumnHanhKhach() {
        return columnHanhKhach;
    }

    public VBox getColumnChuyenTau() {
        return columnChuyenTau;
    }

    public VBox getColumnChoNgoi() {
        return columnChoNgoi;
    }

    public VBox getColumnGiaVe() {
        return columnGiaVe;
    }

    public VBox getColumnGiamGia() {
        return columnGiamGia;
    }

    public VBox getColumnBaoHiem() {
        return columnBaoHiem;
    }

    public VBox getColumnThanhTien() {
        return columnThanhTien;
    }

    public String getFullName() {
        return txtHoTen != null ? txtHoTen.getText() : null;
    }

    public String getDocumentNumber() {
        return txtSoGiayTo != null ? txtSoGiayTo.getText() : null;
    }

    public TicketType getTicketType() {
        return comboDoiTuong != null ? comboDoiTuong.getValue() : TicketType.NORMAL;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public boolean isFreeChildUnder6() {
        return freeChildUnder6;
    }

    public boolean isChildUnder6NoSeat() {
        return childUnder6NoSeat;
    }

    public boolean isSeatReleasedForChildUnder6() {
        return seatReleasedForChildUnder6;
    }

    public boolean hasSeat() {
        return !childUnder6NoSeat && (outboundSeat != null || returnSeat != null);
    }

    public boolean isRequiresAdultTicket() {
        return requiresAdultTicket;
    }

    public String getAdultTicketCode() {
        return txtMaVeNguoiLon != null ? txtMaVeNguoiLon.getText() : null;
    }

    public boolean isStudentCardVerified() {
        return studentCardVerified;
    }

    public int getPassengerIndex() {
        return passengerIndex;
    }

    public SelectedSeatDraft getOutboundSeat() {
        return outboundSeat;
    }

    public SelectedSeatDraft getReturnSeat() {
        return returnSeat;
    }

    public double getPreviewTotal() {
        if (freeChildUnder6 || childUnder6NoSeat) {
            return 0d;
        }
        return computePreviewTotal();
    }

    public void setPassengerNameFromLookup(String name) {
        if (name == null || name.isBlank()) {
            return;
        }
        txtHoTen.setText(name);
    }

    static int ageAt(LocalDate dateOfBirth, LocalDate atDate) {
        if (dateOfBirth == null || atDate == null) {
            return 0;
        }
        return Period.between(dateOfBirth, atDate).getYears();
    }

    @FXML
    private void handleChonNgaySinh() {
        showDatePickerDialog();
    }

    private void notifyDataChange() {
        if (onDataChange != null) {
            onDataChange.run();
        }
    }

    private void updateTicketInfoLabels() {
        if (parentController == null) {
            return;
        }

        if (childUnder6NoSeat) {
            lblNoSeatStatus.setVisible(true);
            lblNoSeatStatus.setManaged(true);
            lblNoSeatStatus.setText("Trẻ dưới 6 tuổi - miễn vé, không chiếm ghế");
        } else {
            lblNoSeatStatus.setVisible(false);
            lblNoSeatStatus.setManaged(false);
        }

        if (!childUnder6NoSeat && outboundSeat != null) {
            var schedule = parentController.getOutboundSchedule();
            if (schedule != null) {
                lblTenTauDi.setText("Tàu " + safe(schedule.getTrainCode()) + " (Đi)");
                lblThoiGianDi
                        .setText(schedule.getDepartureTime() != null ? schedule.getDepartureTime().format(TIME) : "");
            } else {
                lblTenTauDi.setText("Chiều đi");
                lblThoiGianDi.setText("");
            }

            String carriageText = outboundSeat.getCarriageNumber() != null ? ("Toa " + outboundSeat.getCarriageNumber())
                    : "Toa -";
            String seatText = outboundSeat.getSeatNumber() != null ? ("Ghế " + outboundSeat.getSeatNumber()) : "Ghế -";
            lblThongTinChoDi.setText(carriageText + " - " + seatText);
            lblLoaiToaDi.setText(outboundSeat.getSeatType() != null ? outboundSeat.getSeatType().getName() : "");
        } else {
            var schedule = parentController.getOutboundSchedule();
            if (schedule != null) {
                lblTenTauDi.setText("Tàu " + safe(schedule.getTrainCode()) + " (Đi)");
                lblThoiGianDi
                        .setText(schedule.getDepartureTime() != null ? schedule.getDepartureTime().format(TIME) : "");
            } else {
                lblTenTauDi.setText("Chiều đi");
                lblThoiGianDi.setText("");
            }
            lblThongTinChoDi.setText(childUnder6NoSeat ? "Không chiếm ghế" : "-");
            lblLoaiToaDi.setText(childUnder6NoSeat ? "" : "-");
        }

        boolean hasReturnSeat = !childUnder6NoSeat && returnSeat != null;
        lblTenTauVe.setVisible(hasReturnSeat);
        lblTenTauVe.setManaged(hasReturnSeat);
        lblThoiGianVe.setVisible(hasReturnSeat);
        lblThoiGianVe.setManaged(hasReturnSeat);
        lblThongTinChoVe.setVisible(hasReturnSeat);
        lblThongTinChoVe.setManaged(hasReturnSeat);
        lblLoaiToaVe.setVisible(hasReturnSeat);
        lblLoaiToaVe.setManaged(hasReturnSeat);

        if (hasReturnSeat) {
            var schedule = parentController.getReturnSchedule();
            if (schedule != null) {
                lblTenTauVe.setText("Tàu " + safe(schedule.getTrainCode()) + " (Về)");
                lblThoiGianVe
                        .setText(schedule.getDepartureTime() != null ? schedule.getDepartureTime().format(TIME) : "");
            } else {
                lblTenTauVe.setText("Chiều về");
                lblThoiGianVe.setText("");
            }

            String carriageText = returnSeat.getCarriageNumber() != null ? ("Toa " + returnSeat.getCarriageNumber())
                    : "Toa -";
            String seatText = returnSeat.getSeatNumber() != null ? ("Ghế " + returnSeat.getSeatNumber()) : "Ghế -";
            lblThongTinChoVe.setText(carriageText + " - " + seatText);
            lblLoaiToaVe.setText(returnSeat.getSeatType() != null ? returnSeat.getSeatType().getName() : "");
        }
    }

    private void applyTicketTypeVisibility() {
        TicketType type = getTicketType();
        boolean needsDob = type == TicketType.CHILD || type == TicketType.SENIOR;

        btnChonNgaySinh.setVisible(needsDob);
        btnChonNgaySinh.setManaged(needsDob);
        btnChonNgaySinh.setDisable(!needsDob);

        if (!needsDob) {
            dateOfBirth = null;
            btnChonNgaySinh.setText("\uD83D\uDCC5 Ngày sinh");
        }

        if (type != TicketType.CHILD) {
            requiresAdultTicket = false;
            boxMaVeNguoiLon.setVisible(false);
            boxMaVeNguoiLon.setManaged(false);
            txtMaVeNguoiLon.clear();
        }

        if ((freeChildUnder6 || childUnder6NoSeat) && type != TicketType.CHILD) {
            freeChildUnder6 = false;
            childUnder6NoSeat = false;
            seatReleasedForChildUnder6 = false;
            lblNoSeatStatus.setVisible(false);
            lblNoSeatStatus.setManaged(false);
        }
    }

    private void showDatePickerDialog() {
        Dialog<LocalDate> dialog = new Dialog<>();
        dialog.setTitle("Chọn ngày sinh");
        dialog.setHeaderText("Vui lòng chọn ngày sinh của hành khách.");

        DatePicker datePicker = new DatePicker();
        datePicker.setValue(dateOfBirth != null ? dateOfBirth : LocalDate.now().minusYears(10));

        VBox content = new VBox(10, datePicker);
        content.setPadding(new Insets(20));
        dialog.getDialogPane().setContent(content);

        ButtonType okButtonType = ButtonType.OK;
        dialog.getDialogPane().getButtonTypes().addAll(okButtonType, ButtonType.CANCEL);
        dialog.setResultConverter(dialogButton -> dialogButton == okButtonType ? datePicker.getValue() : null);

        Optional<LocalDate> result = dialog.showAndWait();
        result.ifPresent(date -> {
            dateOfBirth = date;
            btnChonNgaySinh.setText("\uD83D\uDCC5 " + date);
            validateAgeAndApplyPolicy();
            recalcPricePreview();
            notifyDataChange();
        });
    }

    private void validateAgeAndApplyPolicy() {
        TicketType type = getTicketType();
        if (type != TicketType.CHILD && type != TicketType.SENIOR) {
            return;
        }
        if (dateOfBirth == null || parentController == null) {
            return;
        }

        LocalDate departDate = parentController.getDepartureDateForAgeCheck();
        int age = ageAt(dateOfBirth, departDate);

        hideExtraControls();

        if (type == TicketType.CHILD) {
            if (age < 6) {
                startChildUnder6NoSeatFlow();
                return;
            }
            if (age >= 6 && age < 10) {
                requiresAdultTicket = true;
                boxMaVeNguoiLon.setVisible(true);
                boxMaVeNguoiLon.setManaged(true);
                return;
            }
            showAlert(Alert.AlertType.WARNING, "Cảnh báo", "Vui lòng kiểm tra độ tuổi trẻ em (6 đến <10).");
            comboDoiTuong.setValue(TicketType.NORMAL);
        } else if (type == TicketType.SENIOR) {
            if (age < 60) {
                showAlert(Alert.AlertType.WARNING, "Cảnh báo", "Vui lòng kiểm tra độ tuổi người cao tuổi (>=60).");
                comboDoiTuong.setValue(TicketType.NORMAL);
            }
        }
    }

    private void startChildUnder6NoSeatFlow() {
        if (childUnder6NoSeat) {
            return;
        }
        if (seatReleaseInProgress) {
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Miễn vé");
        confirm.setHeaderText(null);
        confirm.setContentText(
                "Trẻ em dưới 6 tuổi không chiếm chỗ và được miễn vé. Bạn có muốn nhả ghế đã chọn cho hành khách này?");
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return;
        }

        if (parentController == null) {
            showAlert(Alert.AlertType.ERROR, "Lỗi", "Không thể nhả ghế: thiếu Step3 controller.");
            return;
        }

        setSeatReleaseInProgress(true);
        parentController.releaseSeatsForChildUnder6Async(this);
    }

    void applyChildUnder6NoSeatModeAfterRelease() {
        childUnder6NoSeat = true;
        seatReleasedForChildUnder6 = true;
        freeChildUnder6 = true;
        requiresAdultTicket = false;
        outboundSeat = null;
        returnSeat = null;

        comboDoiTuong.setDisable(true);
        btnChonNgaySinh.setDisable(true);
        hideExtraControls();

        lblNoSeatStatus.setVisible(true);
        lblNoSeatStatus.setManaged(true);
        lblNoSeatStatus.setText("Trẻ dưới 6 tuổi - miễn vé, không chiếm ghế");

        lblGiaVe.setText("Miễn phí");
        lblGiamGia.setText("-");
        lblBaoHiem.setText("-");
        lblThanhTien.setText("Miễn phí");
        lblThanhTien.setStyle("-fx-font-weight: bold; -fx-text-fill: #2ecc71;");

        updateTicketInfoLabels();
        notifyDataChange();
    }

    void applyChildUnder6NoSeatModeFromState() {
        childUnder6NoSeat = true;
        seatReleasedForChildUnder6 = true;
        freeChildUnder6 = true;
        requiresAdultTicket = false;
        outboundSeat = null;
        returnSeat = null;
        comboDoiTuong.setValue(TicketType.CHILD);
        comboDoiTuong.setDisable(true);
        btnChonNgaySinh.setDisable(true);
        hideExtraControls();
        lblNoSeatStatus.setVisible(true);
        lblNoSeatStatus.setManaged(true);
        lblNoSeatStatus.setText("Trẻ dưới 6 tuổi - miễn vé, không chiếm ghế");
        recalcPricePreview();
        updateTicketInfoLabels();
    }

    void setSeatReleaseInProgress(boolean inProgress) {
        seatReleaseInProgress = inProgress;
        comboDoiTuong.setDisable(inProgress);
        btnChonNgaySinh.setDisable(inProgress);
    }

    private void hideExtraControls() {
        boxMaVeNguoiLon.setVisible(false);
        boxMaVeNguoiLon.setManaged(false);
    }

    private void recalcPricePreview() {
        if (freeChildUnder6 || childUnder6NoSeat) {
            lblGiaVe.setText("Miễn phí");
            lblGiamGia.setText("-");
            lblBaoHiem.setText("-");
            lblThanhTien.setText("Miễn phí");
            lblThanhTien.setStyle("-fx-font-weight: bold; -fx-text-fill: #2ecc71;");
            return;
        }

        double baseSum = 0d;
        if (outboundSeat != null && outboundSeat.getPrice() != null) {
            baseSum += outboundSeat.getPrice();
        }
        if (returnSeat != null && returnSeat.getPrice() != null) {
            baseSum += returnSeat.getPrice();
        }

        int seatCount = (outboundSeat != null ? 1 : 0) + (returnSeat != null ? 1 : 0);
        double insuranceSum = seatCount * INSURANCE_FEE;

        double discountRate = resolveDiscountRate();
        double discountAmount = baseSum * discountRate;

        // Làm tròn lên hàng nghìn cho giao diện
        double rawTotal = baseSum - discountAmount + insuranceSum;
        double total = Math.ceil(rawTotal / 1000.0) * 1000.0;

        lblGiaVe.setText(MONEY.format(baseSum + insuranceSum) + " VNĐ");
        lblBaoHiem.setText(MONEY.format(insuranceSum) + " VNĐ");
        if (discountAmount > 0) {
            lblGiamGia.setText("- " + MONEY.format(discountAmount) + " VNĐ");
        } else {
            lblGiamGia.setText("-");
        }
        lblThanhTien.setText(MONEY.format(total) + " VNĐ");
        lblThanhTien.setStyle("-fx-font-weight: bold; -fx-text-fill: #c0392b;");
    }

    private double computePreviewTotal() {
        double baseSum = 0d;
        if (outboundSeat != null && outboundSeat.getPrice() != null) {
            baseSum += outboundSeat.getPrice();
        }
        if (returnSeat != null && returnSeat.getPrice() != null) {
            baseSum += returnSeat.getPrice();
        }
        int seatCount = (outboundSeat != null ? 1 : 0) + (returnSeat != null ? 1 : 0);
        double insuranceSum = seatCount * INSURANCE_FEE;
        double discount = baseSum * resolveDiscountRate();

        double rawTotal = baseSum - discount + insuranceSum;
        return Math.ceil(rawTotal / 1000.0) * 1000.0; // Trả về giá trị đã làm tròn
    }

    private double resolveDiscountRate() {
        TicketType type = getTicketType();
        if (type == null) {
            return 0d;
        }
        if (type == TicketType.NORMAL) {
            return 0d;
        }

        if (type == TicketType.STUDENT) {
            return DISCOUNT_STUDENT;
        }

        if (dateOfBirth == null || parentController == null) {
            return 0d;
        }

        int age = ageAt(dateOfBirth, parentController.getDepartureDateForAgeCheck());
        if (type == TicketType.CHILD) {
            if (age >= 6 && age < 10) {
                return DISCOUNT_CHILD_6_TO_LT10;
            }
            return 0d;
        }

        if (type == TicketType.SENIOR) {
            if (age >= 60) {
                return DISCOUNT_SENIOR_GTE60;
            }
            return 0d;
        }

        return 0d;
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    static String ticketTypeLabel(TicketType type) {
        if (type == null) {
            return "";
        }
        return switch (type) {
            case NORMAL -> "Vé người lớn";
            case CHILD -> "Vé trẻ em";
            case SENIOR -> "Vé người lớn tuổi";
            case STUDENT -> "Vé học sinh - sinh viên";
        };
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private void showPhase1Warning() {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Phase 1");
        alert.setHeaderText(null);
        alert.setContentText("Phase 1: chức năng này chưa được migrate");
        alert.showAndWait();
    }
}
