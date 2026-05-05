package vn.edu.iuh.fit.client.controller;

import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

final class PreviewRenderSupport {

  private PreviewRenderSupport() {
  }

  static <T> T callOnFxThread(FxCallable<T> callable, String debugLabel) {
    if (Platform.isFxApplicationThread()) {
      try {
        return callable.call();
      } catch (Exception e) {
        throw new RuntimeException("FX call failed: " + debugLabel, e);
      }
    }

    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<T> result = new AtomicReference<>();
    AtomicReference<Throwable> error = new AtomicReference<>();
    Platform.runLater(() -> {
      try {
        result.set(callable.call());
      } catch (Throwable t) {
        error.set(t);
      } finally {
        latch.countDown();
      }
    });

    try {
      if (!latch.await(20, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Timeout waiting for FX task: " + debugLabel);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted waiting for FX task: " + debugLabel, e);
    }

    if (error.get() != null) {
      Throwable t = error.get();
      if (t instanceof RuntimeException re) {
        throw re;
      }
      throw new RuntimeException("FX call failed: " + debugLabel, t);
    }
    return result.get();
  }

  static WritableImage snapshotNode(Node node, String debugLabel) {
    if (node == null) {
      throw new IllegalArgumentException("node must not be null");
    }
    if (!Platform.isFxApplicationThread()) {
      throw new IllegalStateException("Snapshot must run on JavaFX Application Thread");
    }

    StackPane host = new StackPane(node);
    host.setPadding(Insets.EMPTY);
    host.setStyle("-fx-background-color: transparent;");
    Scene scene = new Scene(host);
    scene.setFill(Color.TRANSPARENT);

    host.applyCss();
    host.layout();
    node.applyCss();
    node.autosize();

    if (node instanceof Region region) {
      double prefWidth = Math.max(1d, region.prefWidth(-1));
      double prefHeight = Math.max(1d, region.prefHeight(-1));
      region.setMinSize(prefWidth, prefHeight);
      region.setPrefSize(prefWidth, prefHeight);
      region.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
      host.applyCss();
      host.layout();
      node.applyCss();
      node.autosize();
    }

    Bounds bounds = node.getLayoutBounds();
    SnapshotParameters params = new SnapshotParameters();
    params.setFill(Color.TRANSPARENT);
    WritableImage image = node.snapshot(params, null);
    System.err.println("[UC001] render " + debugLabel
        + " node=" + round(bounds.getWidth()) + "x" + round(bounds.getHeight())
        + " image=" + round(image.getWidth()) + "x" + round(image.getHeight()));
    return image;
  }

  private static String round(double value) {
    return String.valueOf(Math.round(value));
  }

  @FunctionalInterface
  interface FxCallable<T> {
    T call() throws Exception;
  }
}
