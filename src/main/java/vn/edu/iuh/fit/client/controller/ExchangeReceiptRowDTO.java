package vn.edu.iuh.fit.client.controller;

public class ExchangeReceiptRowDTO {
  private String oldRoute;
  private double oldPrice;
  private String newRoute;
  private double newPrice;

  public ExchangeReceiptRowDTO() {
  }

  public ExchangeReceiptRowDTO(String oldRoute, double oldPrice, String newRoute, double newPrice) {
    this.oldRoute = oldRoute;
    this.oldPrice = oldPrice;
    this.newRoute = newRoute;
    this.newPrice = newPrice;
  }

  public String getOldRoute() {
    return oldRoute;
  }

  public void setOldRoute(String oldRoute) {
    this.oldRoute = oldRoute;
  }

  public double getOldPrice() {
    return oldPrice;
  }

  public void setOldPrice(double oldPrice) {
    this.oldPrice = oldPrice;
  }

  public String getNewRoute() {
    return newRoute;
  }

  public void setNewRoute(String newRoute) {
    this.newRoute = newRoute;
  }

  public double getNewPrice() {
    return newPrice;
  }

  public void setNewPrice(double newPrice) {
    this.newPrice = newPrice;
  }
}

