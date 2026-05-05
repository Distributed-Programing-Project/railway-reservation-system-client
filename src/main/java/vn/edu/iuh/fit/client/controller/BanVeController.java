package vn.edu.iuh.fit.client.controller;

import java.io.IOException;
import java.util.List;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.layout.StackPane;
import vn.edu.iuh.fit.client.session.SaleWizardState;
import vn.edu.iuh.fit.client.session.SaleWizardState.BuyerDraft;
import vn.edu.iuh.fit.client.session.SaleWizardState.PassengerDraft;
import vn.edu.iuh.fit.client.session.SaleWizardState.SelectedSeatDraft;

public class BanVeController {

  @FXML
  private StackPane contentPane;

  // --- CACHE CÁC STEP CỦA BÁN VÉ ---
  private Parent step1Root;
  private Step1SaleController step1Controller;
  private Parent step2Root;
  private Step2SeatSelectionController step2Controller;
  private Parent step3Root;
  private Step3PassengerController step3Controller;
  private Parent step4Root;
  private Step4PaymentController step4Controller;

  // --- THÊM CACHE CHO MÀN HÌNH TRA CỨU (TRẢ / ĐỔI) ---
  private Parent exchangeSearchRoot;
  private ReturnTicketController exchangeSearchController;

  private SaleWizardState state = new SaleWizardState();

  @FXML
  public void initialize() {
    showStep1();
  }

  public SaleWizardState getState() {
    return state;
  }

  public void initData() {
  }

  // --- HÀM LOAD MÀN HÌNH TRA CỨU VÉ ---
  public void showExchangeSearch() {
    ensureExchangeSearchLoaded();
    contentPane.getChildren().setAll(exchangeSearchRoot);
    // Lưu ý: Không reset state ở đây để giữ lại danh sách tìm kiếm cũ nếu có
  }

  public void showStep1() {
    ensureStep1Loaded();
    contentPane.getChildren().setAll(step1Root);
    step1Controller.initData();
  }

  public void showStep2() {
    ensureStep2Loaded();
    contentPane.getChildren().setAll(step2Root);
    step2Controller.initData();
  }

  public void showStep3() {
    if (state.isExchangeMode()) {
      prepareExchangePassengerDrafts();
      showStep4();
      return;
    }
    ensureStep3Loaded();
    contentPane.getChildren().setAll(step3Root);
    step3Controller.initData();
  }

  public void showStep4() {
    ensureStep4Loaded();
    contentPane.getChildren().setAll(step4Root);
    step4Controller.initData();
  }

  void cleanupAfterSaleSuccess() {
    if (step2Controller != null) {
      step2Controller.stopBackgroundJobsAfterSale();
    }
  }

  void resetAfterSaleSuccess() {
    cleanupAfterSaleSuccess();
    clearStepCache();
    // Khởi tạo state mới sạch sẽ sau khi giao dịch thành công
    state = new SaleWizardState();
    showStep1();
  }

  private void clearStepCache() {
    step1Root = null;
    step1Controller = null;
    step2Root = null;
    step2Controller = null;
    step3Root = null;
    step3Controller = null;
    step4Root = null;
    step4Controller = null;
    // Không clear exchangeSearchRoot để giữ lại giao diện tra cứu nếu user muốn
    // đổi/trả tiếp
  }

  // =========================================================================
  // LOGIC ĐIỀU HƯỚNG (BẺ LÁI CHO CHẾ ĐỘ ĐỔI VÉ)
  // =========================================================================

  public void backFromStep1() {
    if (state.isExchangeMode()) {
      showExchangeSearch(); // Lùi về màn hình tra cứu
    }
  }

  void backFromStep2() {
    showStep1(); // Lùi về chọn chuyến
  }

  void nextFromStep2() {
    if (state.isExchangeMode()) {
      // NẾU ĐANG ĐỔI VÉ: Bỏ qua Step 3 (Nhập TT Hành Khách), nhảy thẳng qua Step 4
      prepareExchangePassengerDrafts();
      showStep4();
    } else {
      // BÁN VÉ BÌNH THƯỜNG: Đi tuần tự qua Step 3
      showStep3();
    }
  }

  void backFromStep3() {
    showStep2();
  }

  void nextFromStep3() {
    showStep4();
  }

  void backFromStep4() {
    if (state.isExchangeMode()) {
      // NẾU ĐANG ĐỔI VÉ: Từ Step 4 lùi một phát về lại Step 2
      showStep2();
    } else {
      showStep3();
    }
  }

  // =========================================================================
  // CÁC HÀM LOAD FXML BÊN DƯỚI
  // =========================================================================

  private void ensureExchangeSearchLoaded() {
    if (exchangeSearchRoot != null) {
      return;
    }
    try {
      FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/ui/views/tra-ve.fxml"));
      exchangeSearchRoot = loader.load();

      exchangeSearchController = loader.getController();
      exchangeSearchController.setMainController(this);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load /client/ui/views/tra-ve.fxml", e);
    }
  }

  private void ensureStep1Loaded() {
    if (step1Root != null) {
      return;
    }
    try {
      FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/ui/views/step-1.fxml"));
      step1Root = loader.load();
      step1Controller = loader.getController();
      step1Controller.setCoordinator(this);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load /client/ui/views/step-1.fxml", e);
    }
  }

  private void ensureStep2Loaded() {
    if (step2Root != null) {
      return;
    }
    try {
      FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/ui/views/step-2.fxml"));
      step2Root = loader.load();
      step2Controller = loader.getController();
      step2Controller.setCoordinator(this);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load /client/ui/views/step-2.fxml", e);
    }
  }

  private void ensureStep3Loaded() {
    if (step3Root != null) {
      return;
    }
    try {
      FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/ui/views/step-3.fxml"));
      step3Root = loader.load();
      step3Controller = loader.getController();
      step3Controller.setCoordinator(this);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load /client/ui/views/step-3.fxml", e);
    }
  }

  private void ensureStep4Loaded() {
    if (step4Root != null) {
      return;
    }
    try {
      FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/ui/views/step-4.fxml"));
      step4Root = loader.load();
      step4Controller = loader.getController();
      step4Controller.setCoordinator(this);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load /client/ui/views/step-4.fxml", e);
    }
  }

  private void prepareExchangePassengerDrafts() {
    if (!state.isExchangeMode()) {
      return;
    }
    if (state.getExchangeOldTickets() == null || state.getExchangeOldTickets().isEmpty()) {
      return;
    }
    if (state.getOutboundSeats() == null || state.getOutboundSeats().isEmpty()) {
      return;
    }

    List<vn.edu.iuh.fit.common.dto.ReturnTicketTicketDTO> oldTickets = state.getExchangeOldTickets();
    List<SelectedSeatDraft> newSeats = state.getOutboundSeats();

    state.getPassengers().clear();

    int count = Math.min(oldTickets.size(), newSeats.size());
    for (int i = 0; i < count; i++) {
      vn.edu.iuh.fit.common.dto.ReturnTicketTicketDTO old = oldTickets.get(i);
      SelectedSeatDraft seat = newSeats.get(i);

      PassengerDraft p = new PassengerDraft();
      p.setHasSeat(true);
      p.setChildUnder6(false);
      if (old != null) {
        p.setFullName(old.getPassengerName());
        p.setDocumentNumber(old.getPassengerIdCard());
        p.setTicketType(old.getType() == null ? vn.edu.iuh.fit.common.constant.TicketType.NORMAL : old.getType());
      }
      p.setOutboundSeat(seat);
      state.getPassengers().add(p);
    }

    // Buyer: seed from first old ticket for Step 4 validation/UI.
    vn.edu.iuh.fit.common.dto.ReturnTicketTicketDTO first = oldTickets.get(0);
    BuyerDraft buyer = new BuyerDraft();
    if (first != null) {
      buyer.setFullName(first.getPassengerName());
      buyer.setDocumentNumber(first.getPassengerIdCard());
    }
    state.setBuyer(buyer);
  }
}
