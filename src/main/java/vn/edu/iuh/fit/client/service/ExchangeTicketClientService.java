package vn.edu.iuh.fit.client.service;

import java.util.List;

import vn.edu.iuh.fit.common.command.ActionType;
import vn.edu.iuh.fit.common.dto.ExchangeEligibleTicketSearchDTO;
import vn.edu.iuh.fit.common.dto.ExchangeTicketPreviewRequestDTO;
import vn.edu.iuh.fit.common.dto.ExchangeTicketRequestDTO;
import vn.edu.iuh.fit.common.request.Request;
import vn.edu.iuh.fit.common.response.Response;

public class ExchangeTicketClientService {

  private final SocketRequestService socketRequestService = new SocketRequestService();

  public Response searchTicketsForExchange(String idCard) {
    return socketRequestService.send(new Request(ActionType.SEARCH_TICKETS_FOR_EXCHANGE,
        ExchangeEligibleTicketSearchDTO.builder().idCard(idCard).build()));
  }

  public Response previewExchangeTickets(List<String> oldTicketIds, List<String> newScheduleDetailIds,
      String clientSessionId) {
    return socketRequestService.send(new Request(ActionType.PREVIEW_EXCHANGE_TICKETS,
        ExchangeTicketPreviewRequestDTO.builder()
            .oldTicketIds(oldTicketIds)
            .newScheduleDetailIds(newScheduleDetailIds)
            .clientSessionId(clientSessionId)
            .build()));
  }

  public Response exchangeTickets(ExchangeTicketRequestDTO dto) {
    return socketRequestService.send(new Request(ActionType.EXCHANGE_TICKET, dto));
  }
}
