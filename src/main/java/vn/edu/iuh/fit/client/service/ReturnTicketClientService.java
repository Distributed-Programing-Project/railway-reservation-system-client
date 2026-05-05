package vn.edu.iuh.fit.client.service;

import java.util.List;

import vn.edu.iuh.fit.common.command.ActionType;
import vn.edu.iuh.fit.common.dto.RefundReceiptRequestDTO;
import vn.edu.iuh.fit.common.dto.ReturnTicketConfirmDTO;
import vn.edu.iuh.fit.common.dto.ReturnTicketPreviewRequestDTO;
import vn.edu.iuh.fit.common.dto.ReturnTicketSearchDTO;
import vn.edu.iuh.fit.common.dto.ReturnTicketSearchType;
import vn.edu.iuh.fit.common.request.Request;
import vn.edu.iuh.fit.common.response.Response;

public class ReturnTicketClientService {

  private final SocketRequestService socketRequestService = new SocketRequestService();

  public Response searchTicketsForReturn(String query) {
    return socketRequestService.send(new Request(ActionType.SEARCH_TICKETS_FOR_RETURN,
        ReturnTicketSearchDTO.builder()
            .query(query)
            .queryType(ReturnTicketSearchType.AUTO)
            .build()));
  }

  public Response previewReturnTickets(List<String> ticketIds) {
    return socketRequestService.send(new Request(ActionType.PREVIEW_RETURN_TICKETS,
        ReturnTicketPreviewRequestDTO.builder().ticketIds(ticketIds).build()));
  }

  public Response confirmReturnTickets(List<String> ticketIds, double refundAmount, String employeeId) {
    return socketRequestService.send(new Request(ActionType.CONFIRM_RETURN_TICKETS,
        ReturnTicketConfirmDTO.builder()
            .ticketIds(ticketIds)
            .refundAmount(refundAmount)
            .employeeId(employeeId)
            .build()));
  }

  public Response getRefundReceipt(String refundInvoiceId) {
    return socketRequestService.send(new Request(ActionType.GET_REFUND_RECEIPT,
        RefundReceiptRequestDTO.builder()
            .refundInvoiceId(refundInvoiceId)
            .build()));
  }
}
