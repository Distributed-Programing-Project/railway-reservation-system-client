package vn.edu.iuh.fit.client.controller;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.util.StringConverter;
import vn.edu.iuh.fit.client.service.SessionManager;
import vn.edu.iuh.fit.client.service.SocketRequestService;
import vn.edu.iuh.fit.common.command.ActionType;
import vn.edu.iuh.fit.common.constant.EmployeeStatus;
import vn.edu.iuh.fit.common.constant.StatisticsPeriod;
import vn.edu.iuh.fit.common.dto.*;
import vn.edu.iuh.fit.common.request.Request;
import vn.edu.iuh.fit.common.response.Response;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class StatisticsManagementController {

    // === Day tab ===
    @FXML private DatePicker dpDay;
    @FXML private HBox empRowDay;
    @FXML private ComboBox<EmployeeDTO> cbEmployeeDay;

    // === Week tab ===
    @FXML private DatePicker dpWeek;
    @FXML private Label lblWeekRange;
    @FXML private HBox empRowWeek;
    @FXML private ComboBox<EmployeeDTO> cbEmployeeWeek;

    // === Month tab ===
    @FXML private ComboBox<Integer> cbMonth;
    @FXML private ComboBox<Integer> cbYear;
    @FXML private HBox empRowMonth;
    @FXML private ComboBox<EmployeeDTO> cbEmployeeMonth;

    // === Period label ===
    @FXML private Label lblPeriod;

    // === KPI ===
    @FXML private VBox kpiPane;
    @FXML private Label lblTicketsSold;
    @FXML private Label lblTicketsRefunded;
    @FXML private Label lblExchangeCount;
    @FXML private Label lblGrossRevenue;
    @FXML private Label lblDiscount;
    @FXML private Label lblInsurance;
    @FXML private Label lblRefundAmount;
    @FXML private Label lblNetRevenue;

    // === Breakdown ===
    @FXML private VBox breakdownPane;
    @FXML private BarChart<String, Number> barChart;
    @FXML private TableView<DailyRevenueDTO> breakdownTable;
    @FXML private TableColumn<DailyRevenueDTO, String> colBreakdownDay;
    @FXML private TableColumn<DailyRevenueDTO, String> colBreakdownSold;
    @FXML private TableColumn<DailyRevenueDTO, String> colBreakdownRefunded;
    @FXML private TableColumn<DailyRevenueDTO, String> colBreakdownRevenue;

    // === Loading ===
    @FXML private StackPane loadingOverlay;

    private final ObservableList<EmployeeDTO> allEmployees = FXCollections.observableArrayList();
    private static final NumberFormat VND = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter FULL_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @FXML
    private void initialize() {
        boolean isManager = SessionManager.getInstance().isManager();

        setupDayTab(isManager);
        setupWeekTab(isManager);
        setupMonthTab(isManager);
        setupBreakdownTable();

        kpiPane.setVisible(false);
        kpiPane.setManaged(false);
        breakdownPane.setVisible(false);
        breakdownPane.setManaged(false);

        if (isManager) {
            loadEmployeeList();
        }
    }

    // === Tab setup ===

    private void setupDayTab(boolean isManager) {
        dpDay.setValue(LocalDate.now());
        empRowDay.setVisible(isManager);
        empRowDay.setManaged(isManager);
        if (isManager) setupEmployeeAutocomplete(cbEmployeeDay);
    }

    private void setupWeekTab(boolean isManager) {
        dpWeek.setValue(LocalDate.now());
        updateWeekRangeLabel(LocalDate.now());
        dpWeek.valueProperty().addListener((obs, old, date) -> {
            if (date != null) updateWeekRangeLabel(date);
        });
        empRowWeek.setVisible(isManager);
        empRowWeek.setManaged(isManager);
        if (isManager) setupEmployeeAutocomplete(cbEmployeeWeek);
    }

    private void setupMonthTab(boolean isManager) {
        for (int m = 1; m <= 12; m++) cbMonth.getItems().add(m);
        cbMonth.setConverter(new StringConverter<>() {
            @Override public String toString(Integer m) { return m == null ? "" : "Tháng " + m; }
            @Override public Integer fromString(String s) { return null; }
        });
        cbMonth.setValue(LocalDate.now().getMonthValue());

        int currentYear = LocalDate.now().getYear();
        for (int y = currentYear - 5; y <= currentYear + 1; y++) cbYear.getItems().add(y);
        cbYear.setValue(currentYear);

        empRowMonth.setVisible(isManager);
        empRowMonth.setManaged(isManager);
        if (isManager) setupEmployeeAutocomplete(cbEmployeeMonth);
    }

    private void setupEmployeeAutocomplete(ComboBox<EmployeeDTO> combo) {
        FilteredList<EmployeeDTO> filtered = new FilteredList<>(allEmployees, e -> true);
        combo.setItems(filtered);
        combo.setEditable(true);
        StringConverter<EmployeeDTO> converter = new StringConverter<>() {
            @Override public String toString(EmployeeDTO e) {
                return e == null ? "Tất cả nhân viên" : e.getEmployeeName() + " (" + e.getEmployeeCode() + ")";
            }
            @Override public EmployeeDTO fromString(String s) {
                if (s == null || s.isBlank() || "Tất cả nhân viên".equals(s)) return null;
                return allEmployees.stream()
                    .filter(e -> e != null && toString(e).equals(s))
                    .findFirst().orElse(null);
            }
        };
        combo.setConverter(converter);
        combo.getEditor().textProperty().addListener((obs, old, text) -> {
            EmployeeDTO selected = combo.getSelectionModel().getSelectedItem();
            if (selected != null && converter.toString(selected).equals(text)) return;
            filtered.setPredicate(e -> {
                if (e == null) return true;
                if (text == null || text.isBlank()) return true;
                String lower = text.toLowerCase();
                return e.getEmployeeName().toLowerCase().contains(lower)
                    || (e.getEmployeeCode() != null && e.getEmployeeCode().toLowerCase().contains(lower));
            });
            if (!filtered.isEmpty()) combo.show();
        });
    }

    private void setupBreakdownTable() {
        colBreakdownDay.setCellValueFactory(c ->
            new ReadOnlyStringWrapper(c.getValue().getDay() != null
                ? c.getValue().getDay().format(FULL_DATE) : ""));
        colBreakdownSold.setCellValueFactory(c ->
            new ReadOnlyStringWrapper(String.valueOf(c.getValue().getTicketsSold())));
        colBreakdownRefunded.setCellValueFactory(c ->
            new ReadOnlyStringWrapper(String.valueOf(c.getValue().getTicketsRefunded())));
        colBreakdownRevenue.setCellValueFactory(c ->
            new ReadOnlyStringWrapper(VND.format(orZero(c.getValue().getRevenue()))));
    }

    // === Event handlers ===

    @FXML
    private void handleViewDay() {
        if (dpDay.getValue() == null) { showAlert("Vui lòng chọn ngày."); return; }
        loadStatistics(StatisticsPeriod.DAY, dpDay.getValue(), getSelectedEmployeeId(cbEmployeeDay));
    }

    @FXML
    private void handleViewWeek() {
        if (dpWeek.getValue() == null) { showAlert("Vui lòng chọn ngày trong tuần."); return; }
        loadStatistics(StatisticsPeriod.WEEK, dpWeek.getValue(), getSelectedEmployeeId(cbEmployeeWeek));
    }

    @FXML
    private void handleViewMonth() {
        if (cbMonth.getValue() == null || cbYear.getValue() == null) {
            showAlert("Vui lòng chọn tháng và năm.");
            return;
        }
        LocalDate target = LocalDate.of(cbYear.getValue(), cbMonth.getValue(), 1);
        loadStatistics(StatisticsPeriod.MONTH, target, getSelectedEmployeeId(cbEmployeeMonth));
    }

    // === Data loading ===

    private void loadStatistics(StatisticsPeriod period, LocalDate targetDate, String filterEmployeeId) {
        StatisticsRequestDTO req = StatisticsRequestDTO.builder()
            .periodType(period)
            .targetDate(targetDate)
            .requestEmployeeId(SessionManager.getInstance().getEmployeeId())
            .employeeId(filterEmployeeId)
            .build();

        executeAsync(
            () -> new SocketRequestService().send(new Request(ActionType.GET_STATISTICS, req)),
            res -> {
                if (res.isSuccess() && res.getData() instanceof StatisticsResultDTO result) {
                    displayResult(result);
                } else {
                    showAlert(res.getMessage() != null ? res.getMessage() : "Không có dữ liệu.");
                }
            }
        );
    }

    private void loadEmployeeList() {
        Task<Response> task = new Task<>() {
            @Override protected Response call() {
                EmployeeFilterDTO filter = EmployeeFilterDTO.builder()
                    .page(0).size(1000)
                    .statusFilter(EmployeeStatus.ACTIVE)
                    .build();
                return new SocketRequestService().send(new Request(ActionType.FIND_ALL_EMPLOYEES, filter));
            }
        };
        task.setOnSucceeded(e -> Platform.runLater(() -> {
            Response res = task.getValue();
            if (res.isSuccess() && res.getData() instanceof EmployeePageDTO page) {
                allEmployees.clear();
                allEmployees.add(null); // "Tất cả nhân viên"
                allEmployees.addAll(page.getContent());
            }
        }));
        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    // === Display result ===

    private void displayResult(StatisticsResultDTO result) {
        lblPeriod.setText("Kỳ: " + result.getPeriodLabel());

        lblTicketsSold.setText(String.valueOf(result.getTotalTicketsSold()));
        lblTicketsRefunded.setText(String.valueOf(result.getTotalTicketsRefunded()));
        lblExchangeCount.setText(String.valueOf(result.getExchangeCount()));
        lblGrossRevenue.setText(VND.format(orZero(result.getGrossRevenue())));
        lblDiscount.setText(VND.format(orZero(result.getTotalDiscount())));
        lblInsurance.setText(VND.format(orZero(result.getTotalInsurance())));
        lblRefundAmount.setText(VND.format(orZero(result.getRefundAmount())));
        lblNetRevenue.setText(VND.format(orZero(result.getNetRevenue())));

        kpiPane.setVisible(true);
        kpiPane.setManaged(true);

        boolean hasBreakdown = result.getBreakdown() != null && !result.getBreakdown().isEmpty();
        breakdownPane.setVisible(hasBreakdown);
        breakdownPane.setManaged(hasBreakdown);
        if (hasBreakdown) fillBreakdown(result.getBreakdown());
    }

    private void fillBreakdown(List<DailyRevenueDTO> breakdown) {
        breakdownTable.getItems().setAll(breakdown);

        barChart.getData().clear();
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Doanh thu");
        for (DailyRevenueDTO daily : breakdown) {
            String label = daily.getDay() != null ? daily.getDay().format(SHORT_DATE) : "";
            double valueMillions = orZero(daily.getRevenue()).doubleValue() / 1_000_000.0;
            series.getData().add(new XYChart.Data<>(label, valueMillions));
        }
        barChart.getData().add(series);
    }

    // === Helpers ===

    private void updateWeekRangeLabel(LocalDate date) {
        LocalDate monday = date.with(DayOfWeek.MONDAY);
        LocalDate sunday = monday.plusDays(6);
        int week = date.get(WeekFields.ISO.weekOfWeekBasedYear());
        int year = date.get(WeekFields.ISO.weekBasedYear());
        lblWeekRange.setText(String.format("Tuần %d/%d:  %s – %s",
            week, year,
            monday.format(SHORT_DATE),
            sunday.format(FULL_DATE)));
    }

    private String getSelectedEmployeeId(ComboBox<EmployeeDTO> combo) {
        EmployeeDTO selected = combo.getSelectionModel().getSelectedItem();
        return (selected != null) ? selected.getEmployeeId() : null;
    }

    private BigDecimal orZero(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }

    private void executeAsync(Supplier<Response> action, Consumer<Response> onDone) {
        setLoading(true);
        Task<Response> task = new Task<>() {
            @Override protected Response call() { return action.get(); }
        };
        task.setOnSucceeded(e -> Platform.runLater(() -> { setLoading(false); onDone.accept(task.getValue()); }));
        task.setOnFailed(e -> Platform.runLater(() -> {
            setLoading(false);
            showAlert("Kết nối thất bại: " + task.getException().getMessage());
        }));
        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    private void setLoading(boolean loading) {
        loadingOverlay.setVisible(loading);
        if (loading) loadingOverlay.toFront();
    }

    private void showAlert(String msg) {
        Alert alert = new Alert(Alert.AlertType.WARNING, msg, ButtonType.OK);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}
