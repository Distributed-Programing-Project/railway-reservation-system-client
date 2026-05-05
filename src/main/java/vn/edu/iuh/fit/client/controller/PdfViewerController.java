package vn.edu.iuh.fit.client.controller;

import java.awt.image.BufferedImage;
import java.awt.print.PrinterJob;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

import javax.print.PrintServiceLookup;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.printing.PDFPageable;
import org.apache.pdfbox.rendering.PDFRenderer;

import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * PDF/Image previewer used by UC001 printing flows.
 *
 * Supports 2 modes:
 * - PDF mode: {@link #loadDocument(File)} backed by PDFBox + AWT printing.
 * - Image mode: {@link #setPages(List, String)} for TicketRenderer previews.
 */
public class PdfViewerController implements Initializable {

  private static final double DEFAULT_ZOOM_FACTOR = 1.0;
  private static final double ZOOM_INCREMENT = 0.10;

  @FXML
  private ScrollPane scrollPane;

  @FXML
  private VBox pageContainer;

  @FXML
  private ImageView pageImageView;

  @FXML
  private Label pageLabel;

  @FXML
  private Label zoomLabel;

  @FXML
  private Button prevPageButton;

  @FXML
  private Button nextPageButton;

  @FXML
  private Button btnZoomOut;

  @FXML
  private Button btnZoomIn;

  @FXML
  private Button btnPrint;

  private Stage stage;
  private PDDocument document;
  private List<Image> renderedPages = List.of();
  private String jobTitle = "In";
  private int currentPageIndex;
  private double currentZoomFactor = DEFAULT_ZOOM_FACTOR;

  @Override
  public void initialize(URL location, ResourceBundle resources) {
    pageImageView.setImage(null);
    pageImageView.setPreserveRatio(true);
    pageImageView.setSmooth(true);
    if (pageContainer != null) {
      pageContainer.setFillWidth(true);
    }

    scrollPane.viewportBoundsProperty().addListener((obs, oldVal, newVal) -> applyZoom());

    scrollPane.addEventFilter(ScrollEvent.SCROLL, event -> {
      if (!event.isControlDown()) {
        return;
      }
      if (event.getDeltaY() > 0) {
        handleZoomIn();
      } else {
        handleZoomOut();
      }
      event.consume();
    });
  }

  public void setStage(Stage stage) {
    this.stage = stage;
  }

  public void setPages(List<Image> pages, String title) {
    closeDocument();
    this.document = null;
    this.renderedPages = pages == null ? List.of() : new ArrayList<>(pages);
    this.jobTitle = title == null ? "In" : title;
    this.currentZoomFactor = DEFAULT_ZOOM_FACTOR;
    System.err.println("[UC001] viewer setPages title=" + this.jobTitle
        + ", pages=" + this.renderedPages.size()
        + pageDebug(this.renderedPages));
    displayPage(0);
  }

  public void loadDocument(File pdfFile) throws IOException {
    closeDocument();
    this.document = PDDocument.load(pdfFile);
    this.jobTitle = pdfFile != null ? pdfFile.getName() : "In";
    this.renderedPages = new ArrayList<>();
    this.currentZoomFactor = DEFAULT_ZOOM_FACTOR;

    PDFRenderer pdfRenderer = new PDFRenderer(document);
    for (int i = 0; i < document.getNumberOfPages(); ++i) {
      BufferedImage bim = pdfRenderer.renderImageWithDPI(i, 150);
      Image fxImage = SwingFXUtils.toFXImage(bim, null);
      this.renderedPages.add(fxImage);
    }
    System.err.println("[UC001] viewer loadDocument title=" + this.jobTitle
        + ", pages=" + this.renderedPages.size()
        + pageDebug(this.renderedPages));

    displayPage(0);
  }

  @FXML
  private void handlePrev() {
    displayPage(currentPageIndex - 1);
  }

  @FXML
  private void handleNext() {
    displayPage(currentPageIndex + 1);
  }

  @FXML
  private void handleZoomIn() {
    if (currentZoomFactor < 2.5) {
      currentZoomFactor += ZOOM_INCREMENT;
      applyZoom();
    }
  }

  @FXML
  private void handleZoomOut() {
    if (currentZoomFactor > 0.25) {
      currentZoomFactor -= ZOOM_INCREMENT;
      applyZoom();
    }
  }

  @FXML
  private void handlePrint() {
    if (document != null) {
      printPdfDocument();
      return;
    }
    if (renderedPages == null || renderedPages.isEmpty()) {
      showAlert(Alert.AlertType.WARNING, "In", "Không có dữ liệu để in.");
      return;
    }
    printCurrentPageImage();
  }

  public void closeDocument() {
    try {
      if (document != null) {
        document.close();
      }
    } catch (IOException ignored) {
    } finally {
      document = null;
    }
  }

  private void displayPage(int pageIndex) {
    if (renderedPages == null || renderedPages.isEmpty()) {
      pageImageView.setImage(null);
      pageLabel.setText("Trang 0 / 0");
      updateZoomLabel();
      System.err.println("[UC001] viewer displayPage empty title=" + jobTitle);
      return;
    }
    if (pageIndex < 0 || pageIndex >= renderedPages.size()) {
      return;
    }

    this.currentPageIndex = pageIndex;
    Image pageImage = renderedPages.get(pageIndex);
    pageImageView.setImage(pageImage);
    System.err.println("[UC001] viewer displayPage index=" + (currentPageIndex + 1)
        + "/" + renderedPages.size()
        + ", image=" + round(pageImage.getWidth()) + "x" + round(pageImage.getHeight())
        + ", title=" + jobTitle);
    pageLabel.setText("Trang " + (currentPageIndex + 1) + " / " + renderedPages.size());
    applyZoom();
  }

  private void applyZoom() {
    if (pageImageView.getImage() != null) {
      double viewportWidth = scrollPane == null || scrollPane.getViewportBounds() == null
          ? 0d
          : scrollPane.getViewportBounds().getWidth();
      double targetWidth = viewportWidth > 0 ? Math.max(1d, viewportWidth - 36d) : pageImageView.getImage().getWidth();
      pageImageView.setFitWidth(targetWidth * currentZoomFactor);
      pageImageView.setPreserveRatio(true);
    }
    updateZoomLabel();
  }

  private void updateZoomLabel() {
    long percentage = Math.round(currentZoomFactor * 100);
    zoomLabel.setText(percentage + "%");
  }

  private static String pageDebug(List<Image> pages) {
    if (pages == null || pages.isEmpty()) {
      return ", firstPage=none";
    }
    Image first = pages.get(0);
    if (first == null) {
      return ", firstPage=null";
    }
    return ", firstPage=" + round(first.getWidth()) + "x" + round(first.getHeight());
  }

  private static String round(double value) {
    return String.valueOf(Math.round(value));
  }

  private void printPdfDocument() {
    try {
      if (PrintServiceLookup.lookupDefaultPrintService() == null) {
        showAlert(Alert.AlertType.ERROR, "Lỗi in ấn", "Không tìm thấy máy in nào.");
        return;
      }
      PrinterJob job = PrinterJob.getPrinterJob();
      job.setJobName(jobTitle);
      job.setPageable(new PDFPageable(document));

      if (job.printDialog()) {
        job.print();
        showAlert(Alert.AlertType.INFORMATION, "Thành công", "Đã gửi tín hiệu in đến máy in!");
      } else {
        showAlert(Alert.AlertType.INFORMATION, "Thông báo", "Tác vụ in đã được hủy.");
      }
    } catch (Exception e) {
      showAlert(Alert.AlertType.ERROR, "Lỗi in ấn", "Đã xảy ra lỗi: " + e.getMessage());
    }
  }

  private void printCurrentPageImage() {
    javafx.print.PrinterJob job = javafx.print.PrinterJob.createPrinterJob();
    if (job == null) {
      showAlert(Alert.AlertType.ERROR, "Lỗi in ấn", "Không khởi tạo được PrinterJob.");
      return;
    }

    Stage owner = this.stage;
    if (owner == null && scrollPane != null && scrollPane.getScene() != null) {
      owner = (Stage) scrollPane.getScene().getWindow();
    }
    boolean proceed = job.showPrintDialog(owner);
    if (!proceed) {
      job.endJob();
      return;
    }

    Node printable = pageImageView;
    boolean success = job.printPage(printable);
    if (success) {
      job.endJob();
    } else {
      showAlert(Alert.AlertType.ERROR, "Lỗi in ấn", "Không thể in trang hiện tại.");
    }
  }

  private void showAlert(Alert.AlertType type, String title, String message) {
    Alert alert = new Alert(type);
    alert.setTitle(title);
    alert.setHeaderText(null);
    alert.setContentText(message);
    alert.showAndWait();
  }
}
