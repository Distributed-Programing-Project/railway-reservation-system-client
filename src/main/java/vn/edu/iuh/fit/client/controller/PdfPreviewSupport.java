package vn.edu.iuh.fit.client.controller;

import java.io.File;
import java.io.IOException;

import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.embed.swing.SwingFXUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

final class PdfPreviewSupport {

  private PdfPreviewSupport() {
  }

  static Image firstPageToImage(File pdfFile) {
    if (pdfFile == null) {
      throw new IllegalArgumentException("pdfFile must not be null");
    }
    try (PDDocument document = PDDocument.load(pdfFile)) {
      if (document.getNumberOfPages() == 0) {
        return new WritableImage(1, 1);
      }
      PDFRenderer renderer = new PDFRenderer(document);
      return SwingFXUtils.toFXImage(renderer.renderImageWithDPI(0, 150), null);
    } catch (IOException e) {
      throw new IllegalStateException("Unable to render preview image from PDF: " + pdfFile, e);
    }
  }
}
