package vn.edu.iuh.fit.client.controller;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import net.sf.jasperreports.engine.DefaultJasperReportsContext;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRPropertiesUtil;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import vn.edu.iuh.fit.client.session.ClientSessionContext;
import vn.edu.iuh.fit.client.session.SaleWizardState;
import vn.edu.iuh.fit.client.session.SaleWizardState.PassengerDraft;
import vn.edu.iuh.fit.client.session.SaleWizardState.SelectedSeatDraft;
import vn.edu.iuh.fit.common.dto.ExchangeTicketResponseDTO;
import vn.edu.iuh.fit.common.dto.IssuedTicketDTO;
import vn.edu.iuh.fit.common.dto.ReturnTicketTicketDTO;

final class ExchangeReceiptRenderer {

  private static final double EXCHANGE_FEE_PER_TICKET = 20_000d;
  private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
  private static final NumberFormat MONEY = NumberFormat.getInstance(new Locale("vi", "VN"));

  private static volatile JasperReport cachedReport;

  private ExchangeReceiptRenderer() {
  }

  static File renderPreviewPdf(ExchangeTicketResponseDTO dto, SaleWizardState state) {
    try {
      // ÉP FONT UNICODE
      JRPropertiesUtil properties = JRPropertiesUtil.getInstance(DefaultJasperReportsContext.getInstance());
      properties.setProperty("net.sf.jasperreports.default.font.name", "Arial");
      properties.setProperty("net.sf.jasperreports.default.pdf.encoding", "Identity-H");
      properties.setProperty("net.sf.jasperreports.default.pdf.font.name", "fonts/arial.ttf");

      JasperReport report = compileReport();
      Map<String, Object> params = toParams(dto, state);
      List<ExchangeReceiptRowDTO> rows = buildRows(dto, state);
      System.out.println("Data rows size: " + (rows == null ? 0 : rows.size()));
      if (rows != null && !rows.isEmpty()) {
        System.out.println("First row data: " + rows.get(0).getOldRoute() + " - " + rows.get(0).getOldPrice());
      }
      if (rows.isEmpty()) {
        // Ensure at least one record so Detail band renders.
        rows = List.of(new ExchangeReceiptRowDTO("--", 0d, "--", 0d));
      }
      JasperPrint print = JasperFillManager.fillReport(report, params, new JRBeanCollectionDataSource(rows));
      File pdf = Files.createTempFile("exchange-receipt-", ".pdf").toFile();
      pdf.deleteOnExit();
      JasperExportManager.exportReportToPdfFile(print, pdf.getAbsolutePath());
      System.err
          .println("[UC001] exchange receipt pdf generated invoiceId=" + safe(dto == null ? null : dto.getInvoiceId())
              + ", file=" + pdf.getAbsolutePath());
      return pdf;
    } catch (Exception e) {
      throw new IllegalStateException("Unable to render exchange receipt PDF", e);
    }
  }

  private static JasperReport compileReport() throws IOException, JRException {
    JasperReport local = cachedReport;
    if (local != null) {
      return local;
    }
    synchronized (ExchangeReceiptRenderer.class) {
      if (cachedReport != null) {
        return cachedReport;
      }
      try (InputStream in = ExchangeReceiptRenderer.class.getResourceAsStream("/client/print/bien-lai-doi-ve.xml")) {
        if (in == null) {
          throw new IOException("Missing JRXML resource: /client/print/bien-lai-doi-ve.xml");
        }
        cachedReport = JasperCompileManager.compileReport(in);
        return cachedReport;
      }
    }
  }

  private static Map<String, Object> toParams(ExchangeTicketResponseDTO dto, SaleWizardState state) {
    Map<String, Object> params = new HashMap<>();
    params.put("p_MaGiaoDich", safe(dto == null ? null : dto.getInvoiceId()));
    params.put("p_NgayLap", DATE_TIME.format(LocalDateTime.now()));
    params.put("p_NhanVien", safe(ClientSessionContext.getInstance().getUsername()));

    int ticketCount = 0;
    double totalNew = 0d;
    if (state != null && state.getPassengers() != null) {
      List<PassengerDraft> seatPassengers = state.getPassengers().stream()
          .filter(Objects::nonNull)
          .filter(PassengerDraft::isHasSeat)
          .toList();
      ticketCount = seatPassengers.size();
      for (PassengerDraft p : seatPassengers) {
        SelectedSeatDraft seat = p.getOutboundSeat();
        if (seat != null && seat.getPrice() != null) {
          totalNew += seat.getPrice();
        }
      }
    }

    double totalOld = 0d;
    if (state != null && state.getExchangeOldTickets() != null) {
      for (ReturnTicketTicketDTO t : state.getExchangeOldTickets()) {
        if (t == null) {
          continue;
        }
        totalOld += t.getTicketPrice();
      }
    }

    double feeTotal = EXCHANGE_FEE_PER_TICKET * ticketCount;
    double diff = Math.max(0d, totalNew - totalOld);
    double payable = dto != null ? dto.getTotalAmount() : (feeTotal + diff);

    params.put("p_SoLuongVe", ticketCount);
    params.put("p_TongVeCu", totalOld);
    params.put("p_TongVeMoi", totalNew);
    params.put("p_PhiDoiMoiVe", EXCHANGE_FEE_PER_TICKET);
    params.put("p_TongPhiDoi", feeTotal);
    params.put("p_ChenhLechGia", diff);
    params.put("p_ThanhToan", payable);
    return params;
  }

  private static List<ExchangeReceiptRowDTO> buildRows(ExchangeTicketResponseDTO dto, SaleWizardState state) {
    List<ReturnTicketTicketDTO> oldTickets = state != null && state.getExchangeOldTickets() != null
        ? state.getExchangeOldTickets()
        : List.of();

    // Preferred source for "new ticket info": server-issued tickets (has route +
    // price).
    List<IssuedTicketDTO> newTickets = dto != null && dto.getNewTickets() != null ? dto.getNewTickets() : List.of();
    if (!oldTickets.isEmpty() && !newTickets.isEmpty()) {
      int count = Math.min(oldTickets.size(), newTickets.size());
      List<ExchangeReceiptRowDTO> rows = new ArrayList<>();
      for (int i = 0; i < count; i++) {
        ReturnTicketTicketDTO old = oldTickets.get(i);
        IssuedTicketDTO nt = newTickets.get(i);

        String oldRoute = old == null ? "--"
            : safe(old.getDepartureStation()) + " - " + safe(old.getDestinationStation());
        double oldPrice = old == null ? 0d : old.getTicketPrice();

        String newRoute = nt == null ? "--" : safe(nt.getDepartureStation()) + " - " + safe(nt.getDestinationStation());
        double newPrice = nt == null ? 0d : nt.getPrice();

        rows.add(new ExchangeReceiptRowDTO(oldRoute, oldPrice, newRoute, newPrice));
      }
      return rows;
    }

    // Fallback: build from wizard state seats (price only) + state stations for
    // route.
    if (state == null) {
      return List.of();
    }
    List<PassengerDraft> passengers = state.getPassengers() == null ? List.of() : state.getPassengers();
    List<PassengerDraft> seatPassengers = passengers.stream()
        .filter(Objects::nonNull)
        .filter(PassengerDraft::isHasSeat)
        .toList();
    int count = Math.min(oldTickets.size(), seatPassengers.size());
    if (count <= 0) {
      return List.of();
    }
    String stateRoute = "--";
    if (state.getDepartureStation() != null && state.getDestinationStation() != null) {
      stateRoute = safe(state.getDepartureStation().getName()) + " - " + safe(state.getDestinationStation().getName());
    }
    List<ExchangeReceiptRowDTO> rows = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      ReturnTicketTicketDTO old = oldTickets.get(i);
      PassengerDraft p = seatPassengers.get(i);
      SelectedSeatDraft seat = p == null ? null : p.getOutboundSeat();

      String oldRoute = old == null ? "--"
          : safe(old.getDepartureStation()) + " - " + safe(old.getDestinationStation());
      double oldPrice = old == null ? 0d : old.getTicketPrice();

      double newPrice = seat != null && seat.getPrice() != null ? seat.getPrice() : 0d;
      rows.add(new ExchangeReceiptRowDTO(oldRoute, oldPrice, stateRoute, newPrice));
    }
    return rows;
  }

  private static String safe(String value) {
    return value == null || value.isBlank() ? "--" : value;
  }
}
