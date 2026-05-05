package vn.edu.iuh.fit.client.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.stage.Stage;
import vn.edu.iuh.fit.client.service.SessionManager;
import vn.edu.iuh.fit.client.service.SocketRequestService;
import vn.edu.iuh.fit.common.command.ActionType;
import vn.edu.iuh.fit.common.constant.StatisticsPeriod;
import vn.edu.iuh.fit.common.constant.StatusSchedule;
import vn.edu.iuh.fit.common.dto.DailyRevenueDTO;
import vn.edu.iuh.fit.common.dto.RouteDTO;
import vn.edu.iuh.fit.common.dto.ScheduleDTO;
import vn.edu.iuh.fit.common.dto.ScheduleFilterDTO;
import vn.edu.iuh.fit.common.dto.StatisticsRequestDTO;
import vn.edu.iuh.fit.common.dto.StatisticsResultDTO;
import vn.edu.iuh.fit.common.dto.TrainDTO;
import vn.edu.iuh.fit.common.dto.TrainFilterDTO;
import vn.edu.iuh.fit.common.request.Request;
import vn.edu.iuh.fit.common.response.Response;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class DashboardStatisticsController {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter SHORT_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM");
    private static final NumberFormat VND = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));

    private final AtomicLong refreshSeq = new AtomicLong(0);

    @FXML
    private Label welcomeSubtitle;
    @FXML
    private Label totalRoutesLabel;
    @FXML
    private Label todaySchedulesLabel;
    @FXML
    private Label totalTrainsLabel;
    @FXML
    private Label revenueLabel;
    @FXML
    private BarChart<String, Number> revenueChart;
    @FXML
    private PieChart scheduleStatusChart;
    @FXML
    private ListView<String> activitiesList;

    private Runnable onOpenSchedule;

    @FXML
    public void initialize() {
        Platform.runLater(() -> {
            updateSubtitle("Dữ liệu cập nhật lúc " + DATE_TIME_FORMATTER.format(LocalDateTime.now()));
            fetchAllDataAsync();
        });
    }

    public void setLoggedInUsername(String username) {
        if (username == null || username.isBlank())
            return;
        Platform.runLater(() -> updateSubtitle(
                "Xin chào " + username + " - cập nhật lúc " + DATE_TIME_FORMATTER.format(LocalDateTime.now())));
    }

    @FXML
    public void handleRefreshData() {
        Platform.runLater(() -> updateSubtitle("Đang làm mới dữ liệu..."));
        fetchAllDataAsync();
    }

    @FXML
    public void handleOpenSchedule() {
        if (onOpenSchedule != null) {
            onOpenSchedule.run();
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/ui/views/schedule-management.fxml"));
            Parent root = loader.load();

            Stage stage = (Stage) welcomeSubtitle.getScene().getWindow();
            stage.setScene(new Scene(root, 1200, 700));
            stage.setTitle("Train Station - Schedule Management");
            stage.show();
        } catch (IOException e) {
            showError("Chuyển màn hình", "Không thể mở giao diện lịch trình: " + e.getMessage());
        }
    }

    public void setOnOpenSchedule(Runnable onOpenSchedule) {
        this.onOpenSchedule = onOpenSchedule;
    }

    private void fetchAllDataAsync() {
        final long seq = refreshSeq.incrementAndGet();

        String employeeId = SessionManager.getInstance().getEmployeeId();
        if (employeeId == null || employeeId.isBlank()) {
            Platform.runLater(() -> showError("Phiên đăng nhập",
                    "Không tìm thấy mã nhân viên trong phiên làm việc. Vui lòng đăng nhập lại."));
            return;
        }

        Task<DashboardSnapshot> task = new Task<>() {
            @Override
            protected DashboardSnapshot call() {
                DashboardSnapshot snapshot = new DashboardSnapshot();
                snapshot.fetchTime = LocalDateTime.now();
                snapshot.activities = new ArrayList<>();

                snapshot.totalRoutes = fetchTotalRoutes(snapshot.activities);
                snapshot.totalTrains = fetchTotalTrains(snapshot.activities);
                snapshot.monthRevenue = fetchMonthRevenue(employeeId, snapshot.activities);
                snapshot.weeklyRevenue = fetchWeeklyRevenue(employeeId, snapshot.activities);

                if (SessionManager.getInstance().isManager()) {
                    snapshot.todaySchedules = fetchTodaySchedules(employeeId, snapshot.activities);
                    snapshot.scheduleStatusCounts = fetchTodayScheduleStatus(employeeId, snapshot.activities);
                } else {
                    snapshot.todaySchedules = null;
                    snapshot.scheduleStatusCounts = null;
                    snapshot.activities.add("• Quyền truy cập: không đủ quyền xem thống kê lịch trình (chỉ quản lý).");
                }

                snapshot.activities.add(0,
                        "• Dữ liệu được lấy từ server lúc " + DATE_TIME_FORMATTER.format(snapshot.fetchTime));
                return snapshot;
            }
        };

        task.setOnSucceeded(evt -> Platform.runLater(() -> {
            if (seq != refreshSeq.get())
                return;
            applySnapshot(task.getValue());
        }));

        task.setOnFailed(evt -> Platform.runLater(() -> {
            if (seq != refreshSeq.get())
                return;
            Throwable ex = task.getException();
            showError("Lỗi tải dữ liệu",
                    "Không thể lấy thống kê từ server. Vui lòng thử lại.\n\nChi tiết: "
                            + (ex == null ? "(không rõ)" : ex.getMessage()));
        }));

        Thread thread = new Thread(task, "dashboard-stats-loader");
        thread.setDaemon(true);
        thread.start();
    }

    private int fetchTotalRoutes(List<String> activities) {
        Response res = new SocketRequestService().send(new Request(ActionType.FIND_ALL_ROUTES, null));
        if (!res.isSuccess()) {
            activities.add("• Tuyến đường: lỗi tải (" + safeMsg(res) + ").");
            return 0;
        }
        if (!(res.getData() instanceof List<?> raw)) {
            activities.add("• Tuyến đường: dữ liệu trả về không hợp lệ.");
            return 0;
        }
        long count = raw.stream().filter(RouteDTO.class::isInstance).count();
        activities.add("• Tuyến đường: " + count + " tuyến.");
        return Math.toIntExact(count);
    }

    private int fetchTotalTrains(List<String> activities) {
        Response res = new SocketRequestService().send(new Request(ActionType.FIND_ALL_TRAINS, new TrainFilterDTO()));
        if (!res.isSuccess()) {
            activities.add("• Tàu: lỗi tải (" + safeMsg(res) + ").");
            return 0;
        }
        if (!(res.getData() instanceof List<?> raw)) {
            activities.add("• Tàu: dữ liệu trả về không hợp lệ.");
            return 0;
        }
        long count = raw.stream().filter(TrainDTO.class::isInstance).count();
        activities.add("• Tàu: " + count + " tàu.");
        return Math.toIntExact(count);
    }

    private BigDecimal fetchMonthRevenue(String requestEmployeeId, List<String> activities) {
        StatisticsRequestDTO dto = StatisticsRequestDTO.builder()
                .periodType(StatisticsPeriod.MONTH)
                .targetDate(LocalDate.now())
                .requestEmployeeId(requestEmployeeId)
                .employeeId(null)
                .build();

        Response res = new SocketRequestService().send(new Request(ActionType.GET_STATISTICS, dto));
        if (!res.isSuccess()) {
            activities.add("• Doanh thu tháng: lỗi tải (" + safeMsg(res) + ").");
            return BigDecimal.ZERO;
        }
        if (!(res.getData() instanceof StatisticsResultDTO result)) {
            activities.add("• Doanh thu tháng: dữ liệu trả về không hợp lệ.");
            return BigDecimal.ZERO;
        }
        BigDecimal revenue = orZero(result.getNetRevenue());
        activities.add("• Doanh thu tháng (" + result.getPeriodLabel() + "): " + VND.format(revenue) + ".");
        return revenue;
    }

    private List<DailyRevenueDTO> fetchWeeklyRevenue(String requestEmployeeId, List<String> activities) {
        StatisticsRequestDTO dto = StatisticsRequestDTO.builder()
                .periodType(StatisticsPeriod.WEEK)
                .targetDate(LocalDate.now())
                .requestEmployeeId(requestEmployeeId)
                .employeeId(null)
                .build();

        Response res = new SocketRequestService().send(new Request(ActionType.GET_STATISTICS, dto));
        if (!res.isSuccess()) {
            activities.add("• Doanh thu 7 ngày: lỗi tải (" + safeMsg(res) + ").");
            return List.of();
        }
        if (!(res.getData() instanceof StatisticsResultDTO result)) {
            activities.add("• Doanh thu 7 ngày: dữ liệu trả về không hợp lệ.");
            return List.of();
        }
        List<DailyRevenueDTO> breakdown = result.getBreakdown();
        if (breakdown == null)
            breakdown = List.of();
        activities.add("• Doanh thu 7 ngày (" + result.getPeriodLabel() + "): " + breakdown.size() + " điểm dữ liệu.");
        return breakdown;
    }

    private Integer fetchTodaySchedules(String requestEmployeeId, List<String> activities) {
        LocalDate today = LocalDate.now();
        ScheduleFilterDTO filter = ScheduleFilterDTO.builder()
                .requestEmployeeId(requestEmployeeId)
                .fromDate(today)
                .toDate(today)
                .page(0)
                .size(2000)
                .build();

        Response res = new SocketRequestService().send(new Request(ActionType.FILTER_SCHEDULE, filter));
        if (!res.isSuccess()) {
            activities.add("• Lịch trình hôm nay: lỗi tải (" + safeMsg(res) + ").");
            return 0;
        }
        if (!(res.getData() instanceof List<?> raw)) {
            activities.add("• Lịch trình hôm nay: dữ liệu trả về không hợp lệ.");
            return 0;
        }
        long count = raw.stream().filter(ScheduleDTO.class::isInstance).count();
        activities.add("• Lịch trình hôm nay: " + count + " chuyến.");
        return Math.toIntExact(count);
    }

    private Map<StatusSchedule, Long> fetchTodayScheduleStatus(String requestEmployeeId, List<String> activities) {
        LocalDate today = LocalDate.now();
        ScheduleFilterDTO filter = ScheduleFilterDTO.builder()
                .requestEmployeeId(requestEmployeeId)
                .fromDate(today)
                .toDate(today)
                .page(0)
                .size(2000)
                .build();

        Response res = new SocketRequestService().send(new Request(ActionType.FILTER_SCHEDULE, filter));
        if (!res.isSuccess()) {
            activities.add("• Trạng thái lịch trình: lỗi tải (" + safeMsg(res) + ").");
            return Map.of();
        }
        if (!(res.getData() instanceof List<?> raw)) {
            activities.add("• Trạng thái lịch trình: dữ liệu trả về không hợp lệ.");
            return Map.of();
        }

        Map<StatusSchedule, Long> counts = raw.stream()
                .filter(ScheduleDTO.class::isInstance)
                .map(ScheduleDTO.class::cast)
                .map(ScheduleDTO::getStatus)
                .filter(s -> s != null)
                .collect(Collectors.groupingBy(s -> s, () -> new EnumMap<>(StatusSchedule.class),
                        Collectors.counting()));

        activities.add("• Trạng thái lịch trình: " + counts.size() + " nhóm.");
        return counts;
    }

    private void applySnapshot(DashboardSnapshot snapshot) {
        if (snapshot == null)
            return;

        updateSubtitle("Dữ liệu cập nhật lúc " + DATE_TIME_FORMATTER.format(snapshot.fetchTime));

        if (totalRoutesLabel != null)
            totalRoutesLabel.setText(String.valueOf(Math.max(0, snapshot.totalRoutes)));
        if (totalTrainsLabel != null)
            totalTrainsLabel.setText(String.valueOf(Math.max(0, snapshot.totalTrains)));

        if (todaySchedulesLabel != null) {
            todaySchedulesLabel.setText(
                    snapshot.todaySchedules == null ? "—" : String.valueOf(Math.max(0, snapshot.todaySchedules)));
        }

        if (revenueLabel != null)
            revenueLabel.setText(VND.format(orZero(snapshot.monthRevenue)));

        fillRevenueChart(snapshot.weeklyRevenue);
        fillScheduleStatusChart(snapshot.scheduleStatusCounts);

        if (activitiesList != null) {
            activitiesList.setItems(FXCollections.observableArrayList(snapshot.activities));
        }
    }

    private void fillRevenueChart(List<DailyRevenueDTO> breakdown) {
        if (revenueChart == null)
            return;

        revenueChart.getData().clear();
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Doanh thu");

        if (breakdown != null) {
            List<DailyRevenueDTO> sorted = breakdown.stream()
                    .filter(d -> d != null && d.getDay() != null)
                    .sorted(Comparator.comparing(DailyRevenueDTO::getDay))
                    .toList();

            for (DailyRevenueDTO daily : sorted) {
                String label = daily.getDay().format(SHORT_DATE_FORMATTER);
                double valueMillions = orZero(daily.getRevenue()).doubleValue() / 1_000_000.0;
                series.getData().add(new XYChart.Data<>(label, valueMillions));
            }
        }

        revenueChart.getData().add(series);
    }

    private void fillScheduleStatusChart(Map<StatusSchedule, Long> counts) {
        if (scheduleStatusChart == null)
            return;

        scheduleStatusChart.getData().clear();
        if (counts == null) {
            scheduleStatusChart.setData(FXCollections.observableArrayList());
            return;
        }

        List<PieChart.Data> slices = new ArrayList<>();
        for (StatusSchedule status : StatusSchedule.values()) {
            long count = counts.getOrDefault(status, 0L);
            if (count <= 0)
                continue;
            slices.add(new PieChart.Data(statusDisplayName(status), count));
        }
        scheduleStatusChart.setData(FXCollections.observableArrayList(slices));
    }

    private String statusDisplayName(StatusSchedule status) {
        if (status == null)
            return "";
        return switch (status) {
            case DRAFT -> "Bản nháp";
            case NOT_STARTED -> "Chưa khởi hành";
            case IN_PROGRESS -> "Đang chạy";
            case PAUSED -> "Tạm dừng";
            case READY -> "Sẵn sàng";
            case COMPLETED -> "Hoàn thành";
            case CANCELLED -> "Đã hủy";
        };
    }

    private void updateSubtitle(String text) {
        if (welcomeSubtitle != null) {
            welcomeSubtitle.setText(text);
        }
    }

    private BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String safeMsg(Response res) {
        if (res == null)
            return "không rõ";
        String msg = res.getMessage();
        return msg == null || msg.isBlank() ? "không rõ" : msg;
    }

    private void showError(String title, String content) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> showError(title, content));
            return;
        }
        Alert alert = new Alert(Alert.AlertType.ERROR, content, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    private static final class DashboardSnapshot {
        private LocalDateTime fetchTime;
        private int totalRoutes;
        private Integer todaySchedules;
        private int totalTrains;
        private BigDecimal monthRevenue = BigDecimal.ZERO;
        private List<DailyRevenueDTO> weeklyRevenue = List.of();
        private Map<StatusSchedule, Long> scheduleStatusCounts = Map.of();
        private List<String> activities = List.of();
    }
}
