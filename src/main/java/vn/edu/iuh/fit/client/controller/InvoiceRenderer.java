package vn.edu.iuh.fit.client.controller;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.nio.file.Files;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javafx.scene.image.Image;
import org.thymeleaf.context.Context;
import org.thymeleaf.TemplateEngine;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import org.thymeleaf.templateresolver.StringTemplateResolver;
import vn.edu.iuh.fit.client.session.SaleWizardState;
import vn.edu.iuh.fit.client.session.SaleWizardState.BuyerDraft;
import vn.edu.iuh.fit.common.dto.IssuedTicketDTO;
import vn.edu.iuh.fit.common.dto.SaleCreateResponseDTO;

final class InvoiceRenderer {

  private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
  private static final NumberFormat MONEY = NumberFormat.getNumberInstance(new Locale("vi", "VN"));
  private static final TemplateEngine TEMPLATE_ENGINE = createTemplateEngine();
  private static final String INVOICE_TEMPLATE_PATH = "/client/print/invoice-template.html";
  private static final String INVOICE_CSS_PATH = "/client/print/invoice-style.css";

  static {
    MONEY.setMaximumFractionDigits(0);
  }

  private InvoiceRenderer() {
  }

  static File renderPreviewPdf(SaleCreateResponseDTO dto, SaleWizardState state) {
    try {
      Map<String, Object> data = buildTemplateData(dto, state);
      Context context = new Context(new Locale("vi", "VN"));
      context.setVariables(data);
      context.setVariable("invoiceCss", readClasspathUtf8(INVOICE_CSS_PATH));
      String templateHtml = readClasspathUtf8(INVOICE_TEMPLATE_PATH);
      String processedHtml = TEMPLATE_ENGINE.process(templateHtml, context);

      File pdf = Files.createTempFile("invoice-preview-", ".pdf").toFile();
      pdf.deleteOnExit();
      try (FileOutputStream os = new FileOutputStream(pdf)) {
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.useFastMode();
        registerArialFonts(builder);
        String baseUri = resourceBaseUri();
        builder.withHtmlContent(processedHtml, baseUri);
        builder.toStream(os);
        builder.run();
      }
      System.err.println("[UC001] invoice pdf generated invoiceId=" + safe(dto == null ? null : dto.getInvoiceId())
          + ", file=" + pdf.getAbsolutePath());
      return pdf;
    } catch (Exception e) {
      throw new IllegalStateException("Unable to render invoice preview PDF", e);
    }
  }

  static Image renderToImage(SaleCreateResponseDTO dto, SaleWizardState state) {
    File pdf = renderPreviewPdf(dto, state);
    return PdfPreviewSupport.firstPageToImage(pdf);
  }

  private static TemplateEngine createTemplateEngine() {
    StringTemplateResolver resolver = new StringTemplateResolver();
    resolver.setTemplateMode("HTML");
    resolver.setCacheable(false);
    TemplateEngine engine = new TemplateEngine();
    engine.setTemplateResolver(resolver);
    return engine;
  }

  private static Map<String, Object> buildTemplateData(SaleCreateResponseDTO dto, SaleWizardState state) {
    Map<String, Object> data = new HashMap<>();
    LocalDate invoiceDate = dto != null && dto.getInvoiceDate() != null ? dto.getInvoiceDate().toLocalDate() : LocalDate.now();
    String yearFull = String.valueOf(invoiceDate.getYear());
    String yearShort = yearFull.substring(2);

    data.put("ngayLap", String.format("%02d", invoiceDate.getDayOfMonth()));
    data.put("thangLap", String.format("%02d", invoiceDate.getMonthValue()));
    data.put("namLap", yearFull);
    data.put("kyHieu", "1K" + yearShort + "TKH");
    data.put("soHD", formatInvoiceNumber(dto == null ? null : dto.getInvoiceId()));
    data.put("idHD", safe(dto == null ? null : dto.getInvoiceId()));

    BuyerDraft buyer = state == null ? null : state.getBuyer();
    data.put("tenNguoiMua", buyer == null ? "" : safe(buyer.getFullName()));
    data.put("sdtNguoiMua", buyer == null ? "" : safe(buyer.getPhoneNumber()));
    data.put("diaChiDonVi", "");
    data.put("tenDonVi", "");
    data.put("mstDonVi", "");
    data.put("hinhThucTT", "TM/CK");
    data.put("stkDonVi", "");

    List<Map<String, Object>> allItems = new ArrayList<>();
    int stt = 1;
    double sum8ThanhTien = 0d;
    double sum8TienThue = 0d;
    double sum8TongCong = 0d;
    double totalBaoHiem = 0d;
    double totalQty = 0d;

    List<IssuedTicketDTO> tickets = mergeTickets(dto);
    for (IssuedTicketDTO ticket : tickets) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("type", "ITEM");
      row.put("stt", stt++);
      row.put("maVe", safe(ticket.getTicketId()));
      row.put("maVeShort", shortenTicketId(safe(ticket.getTicketId())));
      row.put("tenDichVu", buildServiceName(ticket));
      row.put("dvt", "Vé");
      row.put("soLuong", "1");
      row.put("donGia", formatMoney(ticket.getPrice()));
      row.put("thanhTien", formatMoney(ticket.getPrice()));
      row.put("thueSuat", "0%");
      row.put("tienThue", formatMoney(0));
      row.put("tongCong", formatMoney(ticket.getPrice()));
      allItems.add(row);

      sum8ThanhTien += ticket.getPrice();
      sum8TongCong += ticket.getPrice();
      totalQty += 1d;
    }

    if (totalQty > 0d) {
      Map<String, Object> summary = new LinkedHashMap<>();
      summary.put("type", "SUMMARY_BY_TAX");
      summary.put("description", "Tổng theo từng loại thuế suất:");
      summary.put("thanhTien", formatMoney(sum8ThanhTien));
      summary.put("thueSuat", "0%");
      summary.put("tienThue", formatMoney(sum8TienThue));
      summary.put("tongCong", formatMoney(sum8TongCong));
      allItems.add(summary);
    }

    double finalTotal = dto == null ? 0d : dto.getTotalAmount();
    Map<String, Object> finalRow = new LinkedHashMap<>();
    finalRow.put("type", "FINAL_TOTAL");
    finalRow.put("description", "Tổng cộng:");
    finalRow.put("thanhTien", formatMoney(finalTotal - sum8TienThue));
    finalRow.put("tienThue", formatMoney(sum8TienThue));
    finalRow.put("tongCong", formatMoney(finalTotal));
    allItems.add(finalRow);

    data.put("allItems", allItems);
    data.put("tongTienBangChu", numberToWords(Math.round(finalTotal)));
    data.put("ghiChu", "");
    data.put("imgCheckUrl", checkImageUrl());
    return data;
  }

  private static String resourceBaseUri() {
    var url = InvoiceRenderer.class.getResource("/client/print/");
    return url == null ? "" : url.toExternalForm();
  }

  private static String readClasspathUtf8(String path) {
    try (InputStream in = InvoiceRenderer.class.getResourceAsStream(path)) {
      if (in == null) {
        throw new IllegalStateException("Missing resource " + path);
      }
      try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
        StringBuilder sb = new StringBuilder(4096);
        char[] buf = new char[4096];
        int read;
        while ((read = reader.read(buf)) >= 0) {
          sb.append(buf, 0, read);
        }
        return sb.toString();
      }
    } catch (java.io.IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static InputStream requireResourceStream(String path) {
    InputStream in = InvoiceRenderer.class.getResourceAsStream(path);
    if (in == null) {
      throw new IllegalStateException("Missing font resource " + path);
    }
    return in;
  }

  private static void registerArialFonts(PdfRendererBuilder builder) {
    builder.useFont(() -> requireResourceStream("/fonts/arial.ttf"), "Arial", 400, FontStyle.NORMAL, true);
    builder.useFont(() -> requireResourceStream("/fonts/arialbd.ttf"), "Arial", 700, FontStyle.NORMAL, true);
    builder.useFont(() -> requireResourceStream("/fonts/ariali.ttf"), "Arial", 400, FontStyle.ITALIC, true);
    builder.useFont(() -> requireResourceStream("/fonts/arialbi.ttf"), "Arial", 700, FontStyle.ITALIC, true);
  }

  private static String shortenTicketId(String ticketId) {
    if (ticketId == null) {
      return "";
    }
    String value = ticketId.trim();
    if (value.isEmpty()) {
      return "";
    }
    if (value.length() <= 12) {
      return value;
    }
    int dash = value.indexOf('-');
    if (dash >= 8) {
      return value.substring(0, 8);
    }
    return value.substring(0, 12);
  }

  private static String checkImageUrl() {
    try {
      BufferedImage img = new BufferedImage(25, 25, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = img.createGraphics();
      try {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 25, 25);
        g.setColor(new Color(0, 128, 0));
        g.setStroke(new BasicStroke(3.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(5, 13, 10, 18);
        g.drawLine(10, 18, 20, 6);
      } finally {
        g.dispose();
      }
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      javax.imageio.ImageIO.write(img, "png", out);
      return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
    } catch (Exception e) {
      return "";
    }
  }

  private static List<IssuedTicketDTO> mergeTickets(SaleCreateResponseDTO dto) {
    List<IssuedTicketDTO> result = new ArrayList<>();
    if (dto == null) {
      return result;
    }
    if (dto.getTickets() != null) {
      result.addAll(dto.getTickets());
    }
    if (dto.getChildVouchers() != null) {
      result.addAll(dto.getChildVouchers());
    }
    return result;
  }

  private static String buildServiceName(IssuedTicketDTO ticket) {
    if (ticket == null) {
      return "--";
    }
    StringBuilder sb = new StringBuilder();
    sb.append("Vé tàu: ");
    sb.append(safe(ticket.getDepartureStation()));
    sb.append(" - ");
    sb.append(safe(ticket.getDestinationStation()));
    if (!"--".equals(safe(ticket.getTrainCode()))) {
      sb.append(" | ").append(ticket.getTrainCode());
    }
    if (!"--".equals(safe(ticket.getCarriageName())) || !("--".equals(safe(ticket.getSeatNumber())))) {
      sb.append(" | Toa ").append(safe(ticket.getCarriageName())).append(" - Ghế ").append(safe(ticket.getSeatNumber()));
    }
    if (ticket.isChildUnder6()) {
      sb.append(" | Trẻ <6");
    }
    return sb.toString();
  }

  private static String formatInvoiceNumber(String invoiceId) {
    String raw = invoiceId == null ? "" : invoiceId.replaceAll("[^0-9]", "");
    if (raw.isBlank()) {
      return "00000000";
    }
    try {
      return String.format("%08d", Long.parseLong(raw));
    } catch (NumberFormatException e) {
      return raw;
    }
  }

  private static String formatMoney(double value) {
    return MONEY.format(Math.round(value));
  }

  private static String safe(String value) {
    return value == null || value.isBlank() ? "" : value;
  }

  private static String numberToWords(long value) {
    if (value == 0) {
      return "không đồng";
    }
    if (value < 0) {
      return "âm " + numberToWords(-value);
    }
    String[] units = {"", " nghìn", " triệu", " tỷ", " nghìn tỷ", " triệu tỷ"};
    StringBuilder result = new StringBuilder();
    int group = 0;
    while (value > 0) {
      int part = (int) (value % 1000);
      if (part != 0) {
        String words = threeDigitsToWords(part);
        if (result.length() > 0) {
          result.insert(0, " ");
        }
        result.insert(0, words + units[group]);
      }
      value /= 1000;
      group++;
    }
    return capitalizeFirst(result.toString().trim()) + " đồng";
  }

  private static String threeDigitsToWords(int number) {
    String[] digitWords = {"không", "một", "hai", "ba", "bốn", "năm", "sáu", "bảy", "tám", "chín"};
    int hundreds = number / 100;
    int tens = (number % 100) / 10;
    int ones = number % 10;

    List<String> parts = new ArrayList<>();
    if (hundreds > 0) {
      parts.add(digitWords[hundreds] + " trăm");
    }
    if (tens > 1) {
      parts.add(digitWords[tens] + " mươi");
      if (ones == 1) {
        parts.add("mốt");
      } else if (ones == 5) {
        parts.add("lăm");
      } else if (ones > 0) {
        parts.add(digitWords[ones]);
      }
    } else if (tens == 1) {
      parts.add("mười");
      if (ones == 5) {
        parts.add("lăm");
      } else if (ones > 0) {
        parts.add(digitWords[ones]);
      }
    } else if (ones > 0) {
      if (hundreds > 0) {
        parts.add("lẻ");
      }
      parts.add(digitWords[ones]);
    }
    return String.join(" ", parts).trim();
  }

  private static String capitalizeFirst(String value) {
    if (value == null || value.isBlank()) {
      return value;
    }
    return Character.toUpperCase(value.charAt(0)) + value.substring(1);
  }
}
