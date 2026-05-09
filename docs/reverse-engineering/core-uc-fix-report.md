# Core UC Fix Report (UC001–UC004)

Ngày thực hiện: 2026-05-08

## 1. Summary
- Đã fix các điểm **missing/partial** cốt lõi liên quan đến bán vé/đổi vé/trả vé/quản lý khách hàng theo danh sách issue.
- Các rule quan trọng được **enforce ở server** (employee bắt buộc khi lập hóa đơn SALE, chặn dùng điểm + ưu đãi đối tượng, đổi vé giữ nguyên ga đi/ga đến, rollback điểm khi trả vé).
- Luồng in ấn được cải thiện theo hướng **không rollback giao dịch** nếu lỗi in.

## 2. Files changed

### railway-reservation-system-client
- `src/main/java/vn/edu/iuh/fit/client/controller/SellTicketWizardController.java`
- `src/main/java/vn/edu/iuh/fit/client/controller/ReturnTicketController.java`
- `src/main/resources/client/ui/views/exchange-ticket-search.fxml`
- `docs/usecases/uc001-ban-ve.md`
- `docs/usecases/uc002-doi-ve.md`
- `docs/usecases/uc003-tra-ve.md`
- `docs/usecases/uc004b-cap-nhat-khach-hang.md`
- `docs/reverse-engineering/core-uc-fix-plan.md`
- `docs/reverse-engineering/core-uc-fix-report.md`

### railway-reservation-system-common
- `src/main/java/vn/edu/iuh/fit/common/message/SaleMessages.java`
- `src/main/java/vn/edu/iuh/fit/common/message/TicketMessages.java`
- `src/main/java/vn/edu/iuh/fit/common/message/CustomerMessages.java`
- `src/main/java/vn/edu/iuh/fit/common/dto/RefundReceiptDTO.java`
- `src/main/java/vn/edu/iuh/fit/common/dto/RefundReceiptItemDTO.java`

### railway-reservation-system-server
- `src/main/java/vn/edu/iuh/fit/server/service/impl/SaleServiceImpl.java`
- `src/main/java/vn/edu/iuh/fit/server/service/impl/TicketServiceImpl.java`
- `src/main/java/vn/edu/iuh/fit/server/service/impl/CustomerServiceImpl.java`
- `src/main/java/vn/edu/iuh/fit/server/repository/CustomerRepository.java`
- `src/main/java/vn/edu/iuh/fit/server/repository/impl/CustomerRepositoryImpl.java`

## 3. Issues fixed

### UC001 — Bán vé
- UC001-FIX-01: Client set `SaleCreateRequestDTO.employeeId` từ `ClientSessionContext` (fallback `username/employeeCode`); server resolve nhân viên theo `employee_id` hoặc `employee_code`, không tạo invoice SALE nếu không resolve được (`SaleMessages.EMPLOYEE_REQUIRED`).
- UC001-FIX-02: In hóa đơn sau bán vé dùng template VAT/HTML thông qua `InvoiceRenderer` (không dùng summary “fake”); lỗi in không rollback giao dịch.
- UC001-FIX-03: Enforce rule “không dùng điểm đồng thời với ưu đãi đối tượng” ở server (`SaleMessages.POINTS_NOT_ALLOWED_WITH_DISCOUNT`).

### UC002 — Đổi vé
- UC002-FIX-01: Enforce “giữ nguyên ga đi/ga đến” ở cả preview/confirm (`TicketServiceImpl.previewExchangeTickets` + `TicketServiceImpl.doExchangeTicketsOrThrow`) với lỗi rõ ràng (`TicketMessages.EXCHANGE_ROUTE_MISMATCH` / `TicketMessages.EXCHANGE_ROUTE_DATA_MISSING`).
- UC002-FIX-02: UI placeholder tra cứu vé đổi được đổi về dạng keyword tổng quát (mã vé/QR/CCCD/hộ chiếu/mã KH). Server vẫn giữ DTO field `idCard` nhưng treat như keyword.
- UC002-FIX-03: Client lưu `ExchangeTicketResponseDTO` và in được vé mới sau đổi (không in vé cũ); lỗi in không rollback giao dịch.
- UC002-FIX-04: Verify `EXCHANGE_FEE = 20_000.0`/vé (aligned).

### UC003 — Trả vé
- UC003-FIX-01: `GET_REFUND_RECEIPT` trả dữ liệu nhiều vé: thêm `RefundReceiptDTO.items` + `RefundReceiptItemDTO`; server build từ **tất cả** `invoice.details`, client preview/print theo nhiều trang.
- UC003-FIX-02: Rollback điểm tích lũy khi trả vé ở server (trừ theo phần vé trả; không hoàn điểm đã redeem; không để âm).

### UC004 — Quản lý khách hàng
- UC004-FIX-01: `CustomerServiceImpl.updateCustomer` set `passport` + check trùng passport (`CustomerRepository.existsByPassport`); create customer cũng check trùng passport.
- UC004-FIX-02: `deleteCustomer` chuyển sang **soft delete** nhất quán (`isActive=false`), không hard delete trong flow UI.

## 4. Issues confirmed not applicable
- Không phát hiện case cần sửa `EXCHANGE_FEE` (đã là 20.000đ/vé).

## 5. Remaining risks / uncertainties
- `CustomerRepository.searchActiveCustomers(...)` vẫn chỉ search khách `isActive=true`. Nếu cần UI/Service cho “include inactive” thì vẫn còn «missing».
- Rollback điểm khi trả vé đang dùng cách tính `floor(totalTicketPrice / 10_000)` theo phần vé trả; có thể lệch nhẹ so với cách tính điểm ở SALE nếu SALE làm tròn theo tổng hóa đơn (rủi ro do rounding).

## 6. Manual test checklist

### UC001
- Login `QL001`/`NV001`.
- Bán vé thành công; kiểm tra `invoices.employee_id` không null.
- Thử dùng điểm + vé `STUDENT/SENIOR/CHILD` (nếu UI có redeem) phải bị chặn với `SaleMessages.POINTS_NOT_ALLOWED_WITH_DISCOUNT`.
- In hóa đơn dùng template VAT/HTML; lỗi in không rollback sale.

### UC002
- Search vé đổi bằng mã vé/QR/CCCD/hộ chiếu/mã KH.
- Đổi vé **cùng ga đi/ga đến** thành công; đổi vé **khác ga đi/ga đến** bị chặn.
- Đổi vé xong in được vé mới.
- Phí đổi = 20.000đ/vé; vé cũ `EXCHANGED` + QR invalid; vé mới `PAID`, `originalTicketId` set.

### UC003
- Trả 1 vé in biên lai đúng.
- Trả nhiều vé: in biên lai đủ tất cả vé (nhiều trang).
- Refund tạo Invoice `REFUND` + details đầy đủ; vé vừa trả không còn trong danh sách `PAID`.
- Reward points giảm đúng (hoặc tối thiểu không âm).

### UC004
- Update passport thành công; không trùng passport.
- Delete customer là soft delete; search active không hiện inactive.
- Tạo mới khách trùng CCCD/passport bị chặn (không tạo trùng).

## 7. Build / test result
- `railway-reservation-system-common`: `mvn -q test` ✅
- `railway-reservation-system-server`: `mvn -q test` ✅
- `railway-reservation-system-client`: `mvn -q test` ✅

