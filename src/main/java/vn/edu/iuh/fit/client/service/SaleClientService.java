package vn.edu.iuh.fit.client.service;

import java.util.List;

import vn.edu.iuh.fit.common.command.ActionType;
import vn.edu.iuh.fit.common.dto.PaymentCreateRequestDTO;
import vn.edu.iuh.fit.common.dto.PaymentStatusRequestDTO;
import vn.edu.iuh.fit.common.dto.SaleCreateRequestDTO;
import vn.edu.iuh.fit.common.dto.SaleScheduleSearchDTO;
import vn.edu.iuh.fit.common.dto.CustomerSearchDTO;
import vn.edu.iuh.fit.common.dto.SeatHoldRequestDTO;
import vn.edu.iuh.fit.common.dto.SeatMapRequestDTO;
import vn.edu.iuh.fit.common.request.Request;
import vn.edu.iuh.fit.common.response.Response;

public class SaleClientService {

  private final SocketRequestService socketRequestService = new SocketRequestService();

  public Response findAllStations() {
    return socketRequestService.send(new Request(ActionType.FIND_ALL_STATIONS, "ALL"));
  }

  public Response searchSchedulesForSale(SaleScheduleSearchDTO dto) {
    return socketRequestService.send(new Request(ActionType.SEARCH_SCHEDULES_FOR_SALE, dto));
  }

  public Response getSeatMap(String scheduleId, String clientSessionId) {
    return socketRequestService.send(new Request(ActionType.GET_SEATMAP_FOR_SCHEDULE,
        SeatMapRequestDTO.builder().scheduleId(scheduleId).clientSessionId(clientSessionId).build()));
  }

  public Response holdSeats(String scheduleId, List<String> scheduleDetailIds, String clientSessionId) {
    return socketRequestService.send(new Request(ActionType.HOLD_SEATS_FOR_SALE,
        SeatHoldRequestDTO.builder()
            .scheduleId(scheduleId)
            .scheduleDetailIds(scheduleDetailIds)
            .clientSessionId(clientSessionId)
            .build()));
  }

  public Response releaseHeldSeats(String scheduleId, List<String> scheduleDetailIds, String clientSessionId) {
    return socketRequestService.send(new Request(ActionType.RELEASE_HELD_SEATS_FOR_SALE,
        SeatHoldRequestDTO.builder()
            .scheduleId(scheduleId)
            .scheduleDetailIds(scheduleDetailIds)
            .clientSessionId(clientSessionId)
            .build()));
  }

  public Response searchCustomers(CustomerSearchDTO dto) {
    return socketRequestService.send(new Request(ActionType.SEARCH_CUSTOMERS, dto));
  }

  public Response createPaymentOrder(double amount, String description, String clientSessionId) {
    return socketRequestService.send(new Request(ActionType.CREATE_PAYMENT_ORDER,
        PaymentCreateRequestDTO.builder()
            .clientSessionId(clientSessionId)
            .amount(amount)
            .description(description)
            .build()));
  }

  public Response getPaymentOrderStatus(String paymentOrderId) {
    return socketRequestService.send(new Request(ActionType.GET_PAYMENT_ORDER_STATUS,
        PaymentStatusRequestDTO.builder().paymentOrderId(paymentOrderId).build()));
  }

  public Response confirmInternalPayment(String paymentOrderId, String clientSessionId) {
    return socketRequestService.send(new Request(ActionType.CONFIRM_INTERNAL_PAYMENT,
        PaymentStatusRequestDTO.builder()
            .clientSessionId(clientSessionId)
            .paymentOrderId(paymentOrderId)
            .build()));
  }

  public Response createSaleTransaction(SaleCreateRequestDTO dto) {
    return socketRequestService.send(new Request(ActionType.CREATE_SALE_TRANSACTION, dto));
  }
}
