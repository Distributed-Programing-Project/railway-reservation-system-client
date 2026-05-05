package vn.edu.iuh.fit.client.controller;

import java.io.IOException;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import vn.edu.iuh.fit.client.service.SaleClientService;
import vn.edu.iuh.fit.client.session.SaleWizardState;
import vn.edu.iuh.fit.client.session.SaleWizardState.BuyerDraft;
import vn.edu.iuh.fit.client.session.SaleWizardState.PassengerDraft;
import vn.edu.iuh.fit.client.session.SaleWizardState.SelectedSeatDraft;
import vn.edu.iuh.fit.common.constant.DocumentType;
import vn.edu.iuh.fit.common.constant.TicketCategory;
import vn.edu.iuh.fit.common.constant.TicketType;
import vn.edu.iuh.fit.common.constant.TripDirection;
import vn.edu.iuh.fit.common.dto.CustomerDTO;
import vn.edu.iuh.fit.common.dto.CustomerPageDTO;
import vn.edu.iuh.fit.common.dto.CustomerSearchDTO;
import vn.edu.iuh.fit.common.dto.SaleChildUnder6DTO;
import vn.edu.iuh.fit.common.dto.ScheduleSaleCardDTO;
import vn.edu.iuh.fit.common.response.Response;

public class Step3PassengerController {
    private static final NumberFormat MONEY = NumberFormat.getInstance(new Locale("vi", "VN"));
    @FXML
    private HBox headerRow;

    @FXML
    private Label headerHanhKhach;

    @FXML
    private Label headerChuyenTau;

    @FXML
    private Label headerChoNgoi;

    @FXML
    private Label headerGiaVe;

    @FXML
    private Label headerGiamGia;

    @FXML
    private Label headerBaoHiem;

    @FXML
    private Label headerThanhTien;

    @FXML
    private ScrollPane scrollPaneHanhKhach;

    @FXML
    private VBox containerHanhKhach;

    @FXML
    private HBox totalRow;

    @FXML
    private Label lblTongThanhTien;

    @FXML
    private TextField txtNguoiMuaHoTen;

    @FXML
    private TextField txtNguoiMuaSoGiayTo;

    @FXML
    private TextField txtNguoiMuaEmail;

    @FXML
    private TextField txtNguoiMuaSDT;

    @FXML
    private Button btnQuayLai;

    @FXML
    private Button btnTiepTheo;

    private BanVeController coordinator;

    private final SaleClientService saleClientService = new SaleClientService();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "step3-passenger-worker");
        t.setDaemon(true);
        return t;
    });

    private final List<HanhKhachRowController> rowControllers = new ArrayList<>();
    private boolean syncBuyerFromFirstPassenger = true;
    private String selectedCustomerId;
    private Integer rewardPoints;

    public void setCoordinator(BanVeController coordinator) {
        this.coordinator = coordinator;
    }

    @FXML
    public void initialize() {
        txtNguoiMuaHoTen.textProperty().addListener((obs, oldVal, newVal) -> {
            if (txtNguoiMuaHoTen.isFocused() || txtNguoiMuaSoGiayTo.isFocused()) {
                syncBuyerFromFirstPassenger = false;
                persistSyncState();
            }
        });
        txtNguoiMuaSoGiayTo.textProperty().addListener((obs, oldVal, newVal) -> {
            if (txtNguoiMuaHoTen.isFocused() || txtNguoiMuaSoGiayTo.isFocused()) {
                syncBuyerFromFirstPassenger = false;
                persistSyncState();
            }
        });
    }

    public void initData() {
        rowControllers.clear();
        containerHanhKhach.getChildren().clear();
        selectedCustomerId = null;
        rewardPoints = null;

        if (coordinator == null) {
            showAlert(Alert.AlertType.ERROR, "Lỗi", "Không tìm thấy coordinator (BanVeController).");
            btnTiepTheo.setDisable(true);
            return;
        }

        SaleWizardState state = coordinator.getState();
        syncBuyerFromFirstPassenger = state.isSyncBuyerFromFirstPassenger();

        restoreBuyerFromState(state);
        if (state.getPassengers() != null && !state.getPassengers().isEmpty()) {
            buildPassengerRowsFromDrafts(state);
        } else {
            buildPassengerRowsFromCart(state);
        }
        if (syncBuyerFromFirstPassenger && !rowControllers.isEmpty()) {
            syncFirstPassengerToBuyer(rowControllers.get(0), true);
        }
        updateTongThanhTien();
    }

    @FXML
    private void handleQuayLai() {
        if (coordinator != null) {
            coordinator.backFromStep3();
            return;
        }
        showPhase1Warning();
    }

    @FXML
    private void handleTiepTheo() {
        if (coordinator == null) {
            showPhase1Warning();
            return;
        }

        if (!validateBeforeNext()) {
            return;
        }

        // --- BẮT ĐẦU LUỒNG KIỂM TRA DB TRƯỚC KHI SANG BƯỚC 4 ---
        String buyerDoc = safe(txtNguoiMuaSoGiayTo.getText()).trim();

        // Nếu người mua không có giấy tờ
        if (buyerDoc.isBlank()) {
            return;
        }

        Task<Response> task = new Task<>() {
            @Override
            protected Response call() {
                // Tra cứu DB 1 lần duy nhất lúc chuyển bước
                return saleClientService
                        .searchCustomers(CustomerSearchDTO.builder().keyword(buyerDoc).page(0).size(5).build());
            }
        };

        task.setOnSucceeded(e -> {
            Response response = task.getValue();
            if (response != null && response.isSuccess()) {
                CustomerDTO customer = extractFirstCustomer(response.getData());
                if (customer != null) {
                    // Đã là khách hàng cũ -> Hốt ID và Điểm tích luỹ mang sang Step 4
                    selectedCustomerId = customer.getCustomerId();
                    rewardPoints = customer.getRewardPoints();
                } else {
                    // Khách lạ -> Sang Step 4 điểm = 0
                    selectedCustomerId = null;
                    rewardPoints = null;
                }
            } else {
                selectedCustomerId = null;
                rewardPoints = null;
            }

            // Sau khi đã chốt xong Điểm & ID, lưu State và đẩy sang Step 4
            persistStateForStep4();
            coordinator.nextFromStep3();
        });

        task.setOnFailed(e -> {
            // Lỗi mạng rớt DB thì vẫn cho qua bán vé bình thường (Khách lạ)
            selectedCustomerId = null;
            rewardPoints = null;
            persistStateForStep4();
            coordinator.nextFromStep3();
        });

        executor.submit(task);
    }

    ScheduleSaleCardDTO getOutboundSchedule() {
        return coordinator != null ? coordinator.getState().getSelectedOutboundSchedule() : null;
    }

    ScheduleSaleCardDTO getReturnSchedule() {
        return coordinator != null ? coordinator.getState().getSelectedReturnSchedule() : null;
    }

    LocalDate getDepartureDateForAgeCheck() {
        if (coordinator == null) {
            return LocalDate.now();
        }
        SaleWizardState state = coordinator.getState();
        if (state.getDepartureDate() != null) {
            return state.getDepartureDate();
        }
        ScheduleSaleCardDTO schedule = state.getSelectedOutboundSchedule();
        if (schedule != null && schedule.getDepartureTime() != null) {
            return schedule.getDepartureTime().toLocalDate();
        }
        return LocalDate.now();
    }

    void releaseSeatsForChildUnder6Async(HanhKhachRowController rowController) {
        if (coordinator == null || rowController == null) {
            return;
        }

        SaleWizardState state = coordinator.getState();
        int index = rowController.getPassengerIndex();
        if (index < 0 || index >= rowControllers.size()) {
            return;
        }

        SelectedSeatDraft outSeat = rowController.getOutboundSeat();
        SelectedSeatDraft retSeat = rowController.getReturnSeat();

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                if (outSeat != null) {
                    releaseSeatIfPresent(outSeat, state);
                }
                if (retSeat != null) {
                    releaseSeatIfPresent(retSeat, state);
                }
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            if (outSeat != null) {
                state.getOutboundSeats().remove(outSeat);
            }
            if (retSeat != null) {
                state.getReturnSeats().remove(retSeat);
            }

            Platform.runLater(() -> {
                rowController.setSeatReleaseInProgress(false);
                rowController.applyChildUnder6NoSeatModeAfterRelease();
                updateTongThanhTien();
            });
        });

        task.setOnFailed(e -> Platform.runLater(() -> {
            rowController.setSeatReleaseInProgress(false);
            showAlert(Alert.AlertType.ERROR, "Lỗi",
                    "Không thể nhả ghế: " + (task.getException() != null ? task.getException().getMessage() : ""));
        }));

        executor.submit(task);
    }

    private void releaseSeatIfPresent(SelectedSeatDraft seat, SaleWizardState state) {
        if (seat == null) {
            return;
        }
        Response response = saleClientService.releaseHeldSeats(
                seat.getScheduleId(),
                List.of(seat.getScheduleDetailId()),
                state.getClientSessionId());
        if (response == null || !response.isSuccess()) {
            throw new IllegalStateException(response != null ? response.getMessage() : "No response");
        }
    }

    private void buildPassengerRowsFromCart(SaleWizardState state) {
        List<SelectedSeatDraft> outbound = state.getOutboundSeats();
        if (outbound == null || outbound.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Thiếu dữ liệu",
                    "Không có ghế chiều đi trong giỏ vé. Vui lòng quay lại bước 2 để chọn ghế.");
            btnTiepTheo.setDisable(true);
            return;
        }

        boolean roundTrip = state.getTicketCategory() == TicketCategory.ROUND_TRIP;
        List<SelectedSeatDraft> ret = state.getReturnSeats();
        if (roundTrip) {
            if (ret == null || ret.isEmpty()) {
                showAlert(Alert.AlertType.WARNING, "Thiếu dữ liệu",
                        "Vé khứ hồi cần có ghế chiều về. Vui lòng quay lại bước 2 để chọn ghế.");
                btnTiepTheo.setDisable(true);
                return;
            }
            if (outbound.size() != ret.size()) {
                showAlert(Alert.AlertType.WARNING, "Dữ liệu không hợp lệ", "Số ghế chiều đi và chiều về không khớp.");
                btnTiepTheo.setDisable(true);
                return;
            }
        }

        btnTiepTheo.setDisable(false);

        boolean first = true;
        for (int i = 0; i < outbound.size(); i++) {
            SelectedSeatDraft outSeat = outbound.get(i);
            SelectedSeatDraft retSeat = (roundTrip ? ret.get(i) : null);
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/ui/views/hanhkhach-row.fxml"));
                Parent rowNode = loader.load();
                HanhKhachRowController rowController = loader.getController();
                rowController.setCoordinator(coordinator);
                rowController.setData(outSeat, retSeat, this, i);
                rowController.setOnDataChange(() -> {
                    updateTongThanhTien();
                    if (syncBuyerFromFirstPassenger && !rowControllers.isEmpty()
                            && rowControllers.get(0) == rowController) {
                        syncFirstPassengerToBuyer(rowController, true);
                    }
                });

                containerHanhKhach.getChildren().add(rowNode);
                rowControllers.add(rowController);

                if (first) {
                    syncHeaderWidths(rowController);
                    first = false;
                }

            } catch (IOException e) {
                showAlert(Alert.AlertType.ERROR, "Lỗi", "Không thể tải hanhkhach-row.fxml: " + e.getMessage());
            }
        }

        restorePassengersFromState(state);
    }

    private void buildPassengerRowsFromDrafts(SaleWizardState state) {
        List<PassengerDraft> passengers = state.getPassengers();
        if (passengers == null || passengers.isEmpty()) {
            return;
        }

        btnTiepTheo.setDisable(false);

        boolean first = true;
        for (int i = 0; i < passengers.size(); i++) {
            PassengerDraft draft = passengers.get(i);
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/ui/views/hanhkhach-row.fxml"));
                Parent rowNode = loader.load();
                HanhKhachRowController rowController = loader.getController();
                rowController.setCoordinator(coordinator);
                rowController.setData(draft.getOutboundSeat(), draft.getReturnSeat(), this, i);
                rowController.restorePassengerDraft(draft.getFullName(), draft.getDocumentNumber(),
                        draft.getTicketType(), draft.getDateOfBirth(),
                        draft.isStudentCardVerified(), draft.getAdultTicketCode());
                if (draft.isChildUnder6() || !draft.isHasSeat()) {
                    rowController.applyChildUnder6NoSeatModeFromState();
                }
                rowController.setOnDataChange(() -> {
                    updateTongThanhTien();
                    if (syncBuyerFromFirstPassenger && !rowControllers.isEmpty()
                            && rowControllers.get(0) == rowController) {
                        syncFirstPassengerToBuyer(rowController, true);
                    }
                });

                containerHanhKhach.getChildren().add(rowNode);
                rowControllers.add(rowController);

                if (first) {
                    syncHeaderWidths(rowController);
                    first = false;
                }

            } catch (IOException e) {
                showAlert(Alert.AlertType.ERROR, "Lỗi", "Không thể tải hanhkhach-row.fxml: " + e.getMessage());
            }
        }
    }

    private void restorePassengersFromState(SaleWizardState state) {
        if (state.getPassengers() == null || state.getPassengers().isEmpty()) {
            return;
        }
        if (state.getPassengers().size() != rowControllers.size()) {
            return;
        }
        for (int i = 0; i < rowControllers.size(); i++) {
            PassengerDraft draft = state.getPassengers().get(i);
            HanhKhachRowController row = rowControllers.get(i);
            row.restorePassengerDraft(draft.getFullName(), draft.getDocumentNumber(), draft.getTicketType(),
                    draft.getDateOfBirth(),
                    draft.isStudentCardVerified(), draft.getAdultTicketCode());
        }
    }

    private void restoreBuyerFromState(SaleWizardState state) {
        BuyerDraft buyer = state.getBuyer();
        if (buyer == null) {
            txtNguoiMuaHoTen.clear();
            txtNguoiMuaSoGiayTo.clear();
            txtNguoiMuaEmail.clear();
            txtNguoiMuaSDT.clear();
            return;
        }
        txtNguoiMuaHoTen.setText(safe(buyer.getFullName()));
        txtNguoiMuaSoGiayTo.setText(safe(buyer.getDocumentNumber()));
        txtNguoiMuaEmail.setText(safe(buyer.getEmail()));
        txtNguoiMuaSDT.setText(safe(buyer.getPhoneNumber()));

        selectedCustomerId = buyer.getCustomerId();
        rewardPoints = state.getRewardPoints();
    }

    private void persistSyncState() {
        if (coordinator == null) {
            return;
        }
        coordinator.getState().setSyncBuyerFromFirstPassenger(syncBuyerFromFirstPassenger);
    }

    private void syncFirstPassengerToBuyer(HanhKhachRowController firstRow, boolean silentLookup) {
        if (!syncBuyerFromFirstPassenger || firstRow == null) {
            return;
        }

        String name = safe(firstRow.getFullName()).trim();
        String doc = safe(firstRow.getDocumentNumber()).trim();

        if (!name.isBlank()) {
            txtNguoiMuaHoTen.setText(name);
        }
        if (!doc.isBlank()) {
            txtNguoiMuaSoGiayTo.setText(doc);
        }
    }

    private void syncHeaderWidths(HanhKhachRowController firstRowController) {
        if (firstRowController == null || headerRow == null) {
            return;
        }
        bindWidth(headerHanhKhach, firstRowController.getColumnHanhKhach());
        bindWidth(headerChuyenTau, firstRowController.getColumnChuyenTau());
        bindWidth(headerChoNgoi, firstRowController.getColumnChoNgoi());
        bindWidth(headerGiaVe, firstRowController.getColumnGiaVe());
        bindWidth(headerGiamGia, firstRowController.getColumnGiamGia());
        bindWidth(headerBaoHiem, firstRowController.getColumnBaoHiem());
        bindWidth(headerThanhTien, firstRowController.getColumnThanhTien());
    }

    private void bindWidth(Label headerLabel, Node columnNode) {
        if (headerLabel == null || columnNode == null) {
            return;
        }
        headerLabel.prefWidthProperty().bind(Bindings.createDoubleBinding(
                () -> columnNode.getLayoutBounds().getWidth(),
                columnNode.layoutBoundsProperty()));
    }

    private void updateTongThanhTien() {
        double total = 0d;
        for (HanhKhachRowController row : rowControllers) {
            total += row.getPreviewTotal();
        }
        lblTongThanhTien.setText(MONEY.format(total) + " VNĐ");
    }

    private boolean validateBeforeNext() {
        SaleWizardState state = coordinator.getState();

        if (rowControllers.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Thiếu dữ liệu", "Không có hành khách.");
            return false;
        }

        for (HanhKhachRowController row : rowControllers) {
            String name = safe(row.getFullName()).trim();
            if (name.isBlank()) {
                showAlert(Alert.AlertType.WARNING, "Thiếu thông tin", "Họ tên hành khách không được để trống.");
                return false;
            }
            if (!name.matches("^[\\p{L}\\s]+$")) {
                showAlert(Alert.AlertType.WARNING, "Sai định dạng", "Họ tên hành khách không hợp lệ: " + name);
                return false;
            }

            String doc = safe(row.getDocumentNumber()).trim();
            boolean isChildUnder6NoSeat = row.isChildUnder6NoSeat();
            if (!isChildUnder6NoSeat) {
                if (doc.isBlank()) {
                    showAlert(Alert.AlertType.WARNING, "Thiếu thông tin", "Số giấy tờ hành khách không được để trống.");
                    return false;
                }
                if (!isValidDocumentNumber(doc)) {
                    showAlert(Alert.AlertType.WARNING, "Sai định dạng",
                            "Giấy tờ hành khách không hợp lệ (CCCD/CMND 9 hoặc 12 số; Hộ chiếu 1-9 ký tự chữ/số): "
                                    + doc);
                    return false;
                }
            } else {
                // Child under 6: document can be empty; validate only if provided.
                if (!doc.isBlank() && !isValidDocumentNumber(doc)) {
                    showAlert(Alert.AlertType.WARNING, "Sai định dạng",
                            "Giấy tờ hành khách không hợp lệ (CCCD/CMND 9 hoặc 12 số; Hộ chiếu 1-9 ký tự chữ/số): "
                                    + doc);
                    return false;
                }
            }

            TicketType ticketType = row.getTicketType();
            if (ticketType == TicketType.CHILD || ticketType == TicketType.SENIOR) {
                if (row.getDateOfBirth() == null) {
                    showAlert(Alert.AlertType.WARNING, "Thiếu thông tin",
                            "Vui lòng chọn ngày sinh cho hành khách " + name);
                    return false;
                }
            }

            if (ticketType == TicketType.CHILD) {
                int age = HanhKhachRowController.ageAt(row.getDateOfBirth(), getDepartureDateForAgeCheck());
                if (age < 6) {
                    if (!row.isChildUnder6NoSeat()) {
                        showAlert(Alert.AlertType.WARNING, "Chưa hợp lệ",
                                "Trẻ em dưới 6 tuổi không được chiếm ghế. Vui lòng xác nhận nhả ghế để chuyển sang miễn vé.");
                        return false;
                    }
                }
                if (age >= 10) {
                    showAlert(Alert.AlertType.WARNING, "Chưa hợp lệ",
                            "Trẻ em từ 10 tuổi trở lên không hợp lệ cho loại vé trẻ em.");
                    return false;
                }
            }

            if (ticketType == TicketType.SENIOR) {
                int age = HanhKhachRowController.ageAt(row.getDateOfBirth(), getDepartureDateForAgeCheck());
                if (age < 60) {
                    showAlert(Alert.AlertType.WARNING, "Chưa hợp lệ",
                            "Hành khách chưa đủ 60 tuổi cho loại vé người cao tuổi.");
                    return false;
                }
            }
        }

        boolean hasAdult = rowControllers.stream().anyMatch(r -> {
            TicketType t = r.getTicketType();
            return t == TicketType.NORMAL || t == TicketType.SENIOR || t == TicketType.STUDENT;
        });

        for (HanhKhachRowController row : rowControllers) {
            if (!row.isRequiresAdultTicket()) {
                continue;
            }
            if (hasAdult) {
                continue;
            }
            String adultTicketCode = safe(row.getAdultTicketCode()).trim();
            if (adultTicketCode.isBlank()) {
                showAlert(Alert.AlertType.WARNING, "Thiếu thông tin",
                        "Trẻ 6-10 tuổi cần có người lớn đi cùng hoặc nhập mã vé người lớn bảo lãnh.");
                return false;
            }
            // TODO server-side validate adult ticket code (no API in Phase 4)
        }

        String buyerName = safe(txtNguoiMuaHoTen.getText()).trim();
        if (buyerName.isBlank()) {
            showAlert(Alert.AlertType.WARNING, "Thiếu thông tin", "Họ tên người mua không được để trống.");
            return false;
        }
        if (!buyerName.matches("^[\\p{L}\\s]+$")) {
            showAlert(Alert.AlertType.WARNING, "Sai định dạng", "Họ tên người mua không hợp lệ: " + buyerName);
            return false;
        }
        String buyerDoc = safe(txtNguoiMuaSoGiayTo.getText()).trim();
        if (buyerDoc.isBlank()) {
            showAlert(Alert.AlertType.WARNING, "Thiếu thông tin", "Số giấy tờ người mua không được để trống.");
            return false;
        }
        if (!isValidDocumentNumber(buyerDoc)) {
            showAlert(Alert.AlertType.WARNING, "Sai định dạng", "Giấy tờ người mua không hợp lệ.");
            return false;
        }

        String email = safe(txtNguoiMuaEmail.getText()).trim();
        if (!email.isBlank() && !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            showAlert(Alert.AlertType.WARNING, "Sai định dạng", "Email không hợp lệ.");
            return false;
        }

        String phone = safe(txtNguoiMuaSDT.getText()).trim();
        if (!phone.isBlank() && !phone.matches("^\\d{10}$")) {
            showAlert(Alert.AlertType.WARNING, "Sai định dạng", "Số điện thoại không hợp lệ (10 chữ số).");
            return false;
        }

        long seatPassengers = rowControllers.stream().filter(HanhKhachRowController::hasSeat).count();
        if (state.getTicketCategory() == TicketCategory.ROUND_TRIP) {
            if (state.getOutboundSeats().size() != seatPassengers || state.getReturnSeats().size() != seatPassengers) {
                showAlert(Alert.AlertType.WARNING, "Dữ liệu không hợp lệ",
                        "Số hành khách có ghế phải khớp với số ghế đã chọn (chiều đi/về).");
                return false;
            }
        } else {
            if (state.getOutboundSeats().size() != seatPassengers) {
                showAlert(Alert.AlertType.WARNING, "Dữ liệu không hợp lệ",
                        "Số hành khách có ghế phải khớp với số ghế đã chọn.");
                return false;
            }
        }

        for (HanhKhachRowController row : rowControllers) {
            if (row.isChildUnder6NoSeat() && !row.isSeatReleasedForChildUnder6()) {
                showAlert(Alert.AlertType.WARNING, "Dữ liệu không hợp lệ",
                        "Có hành khách trẻ < 6 nhưng ghế chưa được nhả.");
                return false;
            }
            if (row.hasSeat()) {
                SelectedSeatDraft outSeat = row.getOutboundSeat();
                if (outSeat == null || outSeat.getScheduleDetailId() == null
                        || outSeat.getScheduleDetailId().isBlank()) {
                    showAlert(Alert.AlertType.WARNING, "Dữ liệu không hợp lệ",
                            "Có hành khách đang chiếm ghế nhưng thiếu scheduleDetailId.");
                    return false;
                }
                if (state.getTicketCategory() == TicketCategory.ROUND_TRIP) {
                    SelectedSeatDraft retSeat = row.getReturnSeat();
                    if (retSeat == null || retSeat.getScheduleDetailId() == null
                            || retSeat.getScheduleDetailId().isBlank()) {
                        showAlert(Alert.AlertType.WARNING, "Dữ liệu không hợp lệ",
                                "Có hành khách khứ hồi nhưng thiếu scheduleDetailId chiều về.");
                        return false;
                    }
                }
            }
        }

        return true;
    }

    private void persistStateForStep4() {
        SaleWizardState state = coordinator.getState();
        state.getPassengers().clear();
        state.getChildrenUnder6().clear();

        int escortIndex = findEscortPassengerIndex();

        for (HanhKhachRowController row : rowControllers) {
            PassengerDraft draft = new PassengerDraft();
            draft.setFullName(safe(row.getFullName()).trim());
            draft.setDocumentNumber(safe(row.getDocumentNumber()).trim());
            draft.setDocumentType(inferDocumentType(draft.getDocumentNumber()));
            draft.setTicketType(row.getTicketType());
            draft.setDateOfBirth(row.getDateOfBirth());
            draft.setStudentCardVerified(row.isStudentCardVerified());
            draft.setAdultTicketCode(safe(row.getAdultTicketCode()).trim());
            draft.setHasSeat(row.hasSeat());
            draft.setChildUnder6(row.isChildUnder6NoSeat());
            draft.setSeatsReleased(row.isChildUnder6NoSeat());
            draft.setOutboundSeat(row.hasSeat() ? row.getOutboundSeat() : null);
            draft.setReturnSeat(row.hasSeat() ? row.getReturnSeat() : null);
            draft.setPreviewTotal(row.getPreviewTotal());
            state.getPassengers().add(draft);

            if (row.isChildUnder6NoSeat()) {
                SaleChildUnder6DTO child = SaleChildUnder6DTO.builder()
                        .childName(safe(row.getFullName()).trim())
                        .dateOfBirth(row.getDateOfBirth())
                        .accompanyDirection(TripDirection.OUTBOUND)
                        .accompanyPassengerIndex(escortIndex)
                        .build();
                state.getChildrenUnder6().add(child);
            }
        }

        BuyerDraft buyer = new BuyerDraft();
        buyer.setFullName(safe(txtNguoiMuaHoTen.getText()).trim());
        buyer.setDocumentNumber(safe(txtNguoiMuaSoGiayTo.getText()).trim());
        buyer.setDocumentType(inferDocumentType(buyer.getDocumentNumber()));
        buyer.setEmail(safe(txtNguoiMuaEmail.getText()).trim());
        buyer.setPhoneNumber(safe(txtNguoiMuaSDT.getText()).trim());
        buyer.setCustomerId(selectedCustomerId);
        buyer.setHasAccount(selectedCustomerId != null);
        state.setBuyer(buyer);

        state.setSyncBuyerFromFirstPassenger(syncBuyerFromFirstPassenger);
        state.setSelectedCustomerId(selectedCustomerId);
        state.setRewardPoints(rewardPoints);
        state.setPreviewSubtotal(parseMoney(lblTongThanhTien.getText()));
    }

    private int findEscortPassengerIndex() {
        for (int i = 0; i < rowControllers.size(); i++) {
            HanhKhachRowController row = rowControllers.get(i);
            if (!row.hasSeat()) {
                continue;
            }
            TicketType type = row.getTicketType();
            if (type == TicketType.NORMAL || type == TicketType.SENIOR || type == TicketType.STUDENT) {
                return i;
            }
        }
        return 0;
    }

    private static double parseMoney(String formatted) {
        if (formatted == null) {
            return 0d;
        }
        String digits = formatted.replaceAll("[^0-9]", "");
        if (digits.isBlank()) {
            return 0d;
        }
        try {
            return Double.parseDouble(digits);
        } catch (NumberFormatException e) {
            return 0d;
        }
    }

    private static boolean isValidDocumentNumber(String doc) {
        if (doc == null) {
            return false;
        }
        String d = doc.trim();
        boolean isIdCard = d.matches("^\\d{9}$") || d.matches("^\\d{12}$");
        boolean isPassport = d.matches("^[A-Za-z0-9]{1,9}$");
        return isIdCard || isPassport;
    }

    private static DocumentType inferDocumentType(String doc) {
        if (doc == null) {
            return null;
        }
        String d = doc.trim();
        if (d.matches("^\\d{9}$") || d.matches("^\\d{12}$")) {
            return DocumentType.ID_CARD;
        }
        return DocumentType.PASSPORT;
    }

    private static CustomerDTO extractFirstCustomer(Object data) {
        if (data instanceof CustomerPageDTO page && page.getCustomers() != null && !page.getCustomers().isEmpty()) {
            return page.getCustomers().get(0);
        }
        if (data instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof CustomerDTO dto) {
            return dto;
        }
        return null;
    }

    private static void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
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
