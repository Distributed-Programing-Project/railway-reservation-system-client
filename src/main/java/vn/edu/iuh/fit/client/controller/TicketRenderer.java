package vn.edu.iuh.fit.client.controller;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javafx.scene.image.Image;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JREmptyDataSource;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.JasperExportManager;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.io.MemoryUsageSetting;
import vn.edu.iuh.fit.common.constant.TicketType;
import vn.edu.iuh.fit.common.dto.IssuedTicketDTO;

final class TicketRenderer {

  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
  private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
  private static final NumberFormat MONEY = NumberFormat.getInstance(new Locale("vi", "VN"));
  private static volatile JasperReport cachedReport;

  private TicketRenderer() {
  }

  static File renderPreviewPdf(List<IssuedTicketDTO> tickets, String title) {
    List<IssuedTicketDTO> previewTickets = tickets == null ? List.of() : tickets;
    if (previewTickets.isEmpty()) {
      throw new IllegalArgumentException("tickets must not be empty");
    }
    try {
      List<File> pages = new ArrayList<>();
      for (IssuedTicketDTO ticket : previewTickets) {
        pages.add(renderSingleTicketPdf(ticket));
      }
      File merged = Files.createTempFile("ticket-preview-", ".pdf").toFile();
      merged.deleteOnExit();
      PDFMergerUtility merger = new PDFMergerUtility();
      merger.setDestinationFileName(merged.getAbsolutePath());
      for (File page : pages) {
        merger.addSource(page);
      }
      merger.mergeDocuments(MemoryUsageSetting.setupTempFileOnly());
      System.err.println("[UC001] ticket pdf generated title=" + safe(title) + ", pages=" + pages.size()
          + ", file=" + merged.getAbsolutePath());
      return merged;
    } catch (Exception e) {
      throw new IllegalStateException("Unable to render ticket preview PDF", e);
    }
  }

  static Image renderToImage(IssuedTicketDTO dto, String title) {
    File pdf = renderPreviewPdf(List.of(dto), title);
    return PdfPreviewSupport.firstPageToImage(pdf);
  }

  private static File renderSingleTicketPdf(IssuedTicketDTO dto) throws IOException, JRException {
    JasperReport report = compileReport();
    Map<String, Object> params = toParams(dto);
    JasperPrint print = JasperFillManager.fillReport(report, params, new JREmptyDataSource());
    File pdf = Files.createTempFile("ticket-", ".pdf").toFile();
    pdf.deleteOnExit();
    JasperExportManager.exportReportToPdfFile(print, pdf.getAbsolutePath());
    return pdf;
  }

  private static JasperReport compileReport() throws IOException, JRException {
    JasperReport local = cachedReport;
    if (local != null) {
      return local;
    }
    synchronized (TicketRenderer.class) {
      if (cachedReport != null) {
        return cachedReport;
      }
      try (InputStream in = TicketRenderer.class.getResourceAsStream("/client/print/ticket_template.xml")) {
        if (in == null) {
          throw new IOException("Missing JRXML resource: /client/print/ticket_template.xml");
        }
        cachedReport = JasperCompileManager.compileReport(in);
        return cachedReport;
      }
    }
  }

  private static Map<String, Object> toParams(IssuedTicketDTO dto) {
    Map<String, Object> params = new HashMap<>();
    String ticketId = safe(dto == null ? null : dto.getTicketId());
    String qrPayload = safe(dto == null ? null : dto.getQrCode());
    if ("--".equals(qrPayload)) {
      qrPayload = ticketId;
    }

    params.put("maVe", ticketId);
    params.put("gaDi", safe(dto == null ? null : dto.getDepartureStation()));
    params.put("gaDen", safe(dto == null ? null : dto.getDestinationStation()));
    params.put("macTau", safe(dto == null ? null : dto.getTrainCode()));
    params.put("ngayDi", dto != null && dto.getDepartureTime() != null ? dto.getDepartureTime().format(DATE) : "--");
    params.put("gioDi", dto != null && dto.getDepartureTime() != null ? dto.getDepartureTime().format(TIME) : "--");
    params.put("toa", safe(dto == null ? null : dto.getCarriageName()));
    params.put("cho", safe(dto == null ? null : dto.getSeatNumber()));
    params.put("loaiCho", dto != null && dto.getSeatType() != null ? dto.getSeatType().getName() : "--");
    params.put("loaiVe", ticketTypeLabel(dto));
    params.put("hoTen", safe(dto == null ? null : dto.getPassengerName()));
    params.put("giayTo", ticketDocument(dto));
    params.put("giaVe", formatMoney(dto == null ? 0d : dto.getPrice()));
    params.put("qrCodeData", qrPayload);
    return params;
  }

  private static String ticketTypeLabel(IssuedTicketDTO dto) {
    if (dto == null) {
      return "--";
    }
    if (dto.isChildUnder6()) {
      return "Trẻ <6";
    }
    TicketType type = dto.getTicketType();
    if (type == null) {
      return "--";
    }
    return type.getName();
  }

  private static String ticketDocument(IssuedTicketDTO dto) {
    if (dto == null) {
      return "--";
    }
    if (dto.isChildUnder6()) {
      return safe(dto.getAccompanyAdultTicketId());
    }
    return safe(dto.getPassengerDocument());
  }

  private static String formatMoney(double value) {
    return MONEY.format(Math.round(value)) + " đ";
  }

  private static String safe(String value) {
    return value == null || value.isBlank() ? "--" : value;
  }
}
