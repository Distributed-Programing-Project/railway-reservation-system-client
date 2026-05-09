# Use case UC002: Đổi vé

## Actor
- **Primary:** Nhân viên bán vé/quầy (`Employee`)
- **Secondary:** Khách hàng (người mua)
- **System:** JavaFX Client → TCP Socket Server → MariaDB

## Tiền điều kiện
- Vé cần đổi đang ở trạng thái `TicketStatus.PAID`.
- Vé chưa từng đổi:
  - `Ticket.isExchanged = false`
  - `Ticket.originalTicketId = null`
- Thời gian đến giờ chạy của vé cũ còn ít nhất 24h (server check theo `ChronoUnit.HOURS`).
- Nhân viên có `employeeId` để gửi trong `ExchangeTicketRequestDTO.employeeId`.

## Hậu điều kiện (khi thành công)
- Vé cũ được cập nhật:
  - `Ticket.status = TicketStatus.EXCHANGED`
  - `Ticket.isExchanged = true`
  - `Ticket.qrCode = "INVALID"`
- Vé mới được tạo:
  - `Ticket.status = TicketStatus.PAID`
  - `Ticket.qrCode = ticketId`
  - `Ticket.originalTicketId = oldTicketId`
- Tạo `Invoice` loại `InvoiceType.EXCHANGE` và `InvoiceDetail` cho các vé mới.
- Hold ghế mới được nhả khỏi `SeatHoldStore` sau khi đổi thành công (`SeatHoldStore.releaseAll(...)`).

---

## Luồng chính
### Bước 1 — Tra cứu vé đủ điều kiện đổi
1. Client (màn hình tra cứu đổi vé) gửi `Request(ActionType.SEARCH_TICKETS_FOR_EXCHANGE, ExchangeEligibleTicketSearchDTO{idCard})` (client: `ExchangeTicketClientService.searchTicketsForExchange`).
2. Server (`TicketServiceImpl.searchTicketsForExchange`):
   - Validate DTO (`ValidationUtils.validate`) và `idCard` (normalize).
   - Query `TicketRepository.findTicketsByCustomerIdCardWithStatusForExchange(em, idCard, TicketStatus.PAID)`.
   - Map sang `List<ExchangeEligibleTicketDTO>` (gồm `eligible/ineligibleReason`) và trả `Response.success(TicketMessages.EXCHANGE_SEARCH_SUCCESS, dtos)`.
3. Client hiển thị danh sách và chỉ cho chọn các vé `eligible=true`.

### Bước 2 — Chọn ghế mới và giữ chỗ
4. Client mở wizard bán vé và bật chế độ đổi vé (`SellTicketWizardController.initExchangeMode(oldTicketIds)`):
   - Khóa UI khứ hồi: chỉ đổi 1 chiều (`rbRoundTrip.setDisable(true)`).
   - Ràng buộc số ghế mới phải đúng bằng số vé cũ (`outboundCart.size() == exchangeOldTicketIds.size()`).
5. Nhân viên chọn chuyến mới và xem sơ đồ ghế mới (dùng API bán vé):
   - `GET_SEATMAP_FOR_SCHEDULE` (`SaleClientService.getSeatMap`).
6. Nhân viên chọn ghế mới và client giữ chỗ:
   - `Request(ActionType.HOLD_SEATS_FOR_SALE, SeatHoldRequestDTO{scheduleId, scheduleDetailIds, clientSessionId})`.
7. Server giữ chỗ trong `SeatHoldStore` (TTL 10 phút) và trả `SeatHoldResponseDTO`.

### Bước 3 — Xem trước phí đổi và xác nhận đổi
8. Client gửi xem trước phí đổi: `Request(ActionType.PREVIEW_EXCHANGE_TICKETS, ExchangeTicketPreviewRequestDTO{oldTicketIds, newScheduleDetailIds, clientSessionId})` (client: `ExchangeTicketClientService.previewExchangeTickets`).
9. Server (`TicketServiceImpl.previewExchangeTickets`):
   - Validate: danh sách cũ/mới cùng size, `clientSessionId` hợp lệ.
   - Load `oldTickets` qua `TicketRepository.findTicketsForExchange(...)` và check business rules (`validateBusinessRulesOrThrow`):
     - `status == PAID`, chưa đổi, chưa trả, còn >=24h.
   - Check ghế mới đang được hold bởi đúng `clientSessionId`: `SeatHoldStore.isHeldBy(sdId, sessionId, nowEpoch)`.
   - Tính giá vé cũ theo **actual paid amount**: `resolveActualPaidAmount(em, oldTicket)` (fallback về `ScheduleDetail.priceSeat` nếu không tìm thấy).
   - Tính giá vé mới: tổng `ScheduleDetail.priceSeat` của danh sách ghế mới.
   - Tính tiền phải thu:
     - `feeTotal = oldTickets.size() * EXCHANGE_FEE` với `EXCHANGE_FEE = 20_000.0`.
     - `diff = totalNewPrice - totalOldPrice`.
     - `totalAmount = feeTotal + diff`, nếu âm thì `totalAmount = feeTotal` (không hoàn phần chênh lệch âm).
   - Trả `Response.success(TicketMessages.EXCHANGE_PREVIEW_SUCCESS, ExchangeTicketPreviewDTO{...})`.
10. Nhân viên xác nhận đổi vé, client gửi `Request(ActionType.EXCHANGE_TICKET, ExchangeTicketRequestDTO{oldTicketIds, newScheduleDetailIds, employeeId, clientSessionId, taxCode, companyName})`.
11. Server (`TicketServiceImpl.exchangeTickets` → transactional `doExchangeTicketsOrThrow`):
   - Resolve nhân viên theo `employeeId` hoặc `employee_code` (`findEmployeeByIdOrCode`).
   - Re-validate vé cũ (PAID, chưa đổi, chưa trả, cutoff 24h).
   - Re-validate hold ghế mới theo `SeatHoldStore.isHeldBy(...)`.
   - Cập nhật vé cũ: `status=EXCHANGED`, `isExchanged=true`, `qrCode="INVALID"`.
   - Tạo vé mới theo từng cặp (oldTicketId → newScheduleDetailId):
     - `Ticket.originalTicketId = oldTicketId`, `status=PAID`, `qrCode=ticketId`.
     - Check trùng ghế bằng `ScheduleDetailRepository.getSoldSeatIdsWithLock(...)`.
   - Tạo `Invoice{type=EXCHANGE, totalAmount=finalAmount}` và `InvoiceDetail` cho từng vé mới (chia đều `subTotalPerTicket = finalAmount/newTickets.size()`).
12. Server trả `Response.success(String.format(TicketMessages.EXCHANGE_SUCCESS,...), ExchangeTicketResponseDTO{invoiceId, totalAmount, newTickets,...})`.
13. Client nhả hold ghế mới: `SeatHoldStore.releaseAll(newScheduleDetailIds, clientSessionId)` được gọi bên server sau khi đổi thành công.

---

## Luồng thay thế
- **[Ghế mới rẻ hơn ghế cũ]:** Server vẫn thu `EXCHANGE_FEE` và **không hoàn lại** phần chênh lệch âm (rule trong `TicketServiceImpl.previewExchangeTickets` và `doExchangeTicketsOrThrow`).
- **[Bỏ chọn ghế mới]:** Client gọi `RELEASE_HELD_SEATS_FOR_SALE` (API bán vé) để nhả ghế đã hold.

---

## Luồng lỗi tiêu biểu (tham chiếu constant)
- `TicketMessages.ID_CARD_REQUIRED`: thiếu giấy tờ (DTO `ExchangeEligibleTicketSearchDTO.idCard` rỗng).
- `TicketMessages.TICKET_NOT_PAID`: vé không phải `PAID`.
- `TicketMessages.TICKET_ALREADY_EXCHANGED`: vé đã đổi (`isExchanged=true` hoặc `originalTicketId != null`).
- `TicketMessages.TICKET_ALREADY_RETURNED`: vé đã trả.
- `TicketMessages.EXCHANGE_TIME_EXPIRED`: còn <24h trước giờ chạy.
- `TicketMessages.COUNT_MISMATCH`: số vé cũ và số ghế mới không khớp.
- `TicketMessages.SEAT_HELD_BY_OTHER`: ghế mới chưa hold hoặc hold bởi phiên khác.
- `TicketMessages.SEAT_NOT_AVAILABLE`: ghế mới đã có người mua (server check sold seat ids).
- `TicketMessages.DATA_CONFLICT`: xung đột optimistic lock khi chiếm ghế (`OptimisticLockException`).
- `TicketMessages.EXCHANGE_FAILED_PREFIX + ...`: lỗi nghiệp vụ khác trong đổi vé.

---

## Business rules
- **Chỉ đổi 1 lần:** server chặn nếu `Ticket.isExchanged=true` hoặc `Ticket.originalTicketId != null`.
- **Điều kiện trạng thái:** chỉ đổi vé `TicketStatus.PAID`; không đổi vé `RETURNED`.
- **Cutoff 24h:** `ChronoUnit.HOURS.between(now, departureTime) >= 24`.
- **Hold bắt buộc:** ghế mới phải được hold bằng `clientSessionId` (dùng chung `SeatHoldStore` với UC001).
- **Phí đổi:** `EXCHANGE_FEE = 20_000.0`/vé (cố định trong `TicketServiceImpl`).
- **Giá vé cũ:** ưu tiên lấy từ “actual paid amount” theo invoice detail (`resolveActualPaidAmount`), fallback `ScheduleDetail.priceSeat`.
- **Không hoàn tiền chênh âm:** nếu `totalNewPrice < totalOldPrice` thì vẫn thu ít nhất phí đổi.
- **Invoice:** luôn tạo `InvoiceType.EXCHANGE` cho giao dịch đổi.

---

## Dữ liệu vào/ra (I/O)

### Client → Server
| ActionType | DTO | Trường chính |
|---|---|---|
| `SEARCH_TICKETS_FOR_EXCHANGE` | `ExchangeEligibleTicketSearchDTO` | `idCard` |
| `GET_SEATMAP_FOR_SCHEDULE` | `SeatMapRequestDTO` | `scheduleId`, `clientSessionId` (dùng chung với UC001) |
| `HOLD_SEATS_FOR_SALE` | `SeatHoldRequestDTO` | `scheduleId`, `scheduleDetailIds`, `clientSessionId` |
| `PREVIEW_EXCHANGE_TICKETS` | `ExchangeTicketPreviewRequestDTO` | `oldTicketIds`, `newScheduleDetailIds`, `clientSessionId` |
| `EXCHANGE_TICKET` | `ExchangeTicketRequestDTO` | `oldTicketIds`, `newScheduleDetailIds`, `employeeId`, `clientSessionId`, `taxCode`, `companyName` |

### Server → Client
| Response.data |
|---|
| `List<ExchangeEligibleTicketDTO>` |
| `ExchangeTicketPreviewDTO` |
| `ExchangeTicketResponseDTO` |

---

## Code Trace
| Layer | File/Class | Ghi chú |
|---|---|---|
| FXML | `src/main/resources/client/ui/views/exchange-ticket-search.fxml` | Màn hình tra cứu đổi vé |
| FXML | `src/main/resources/client/ui/views/sell-ticket-wizard.fxml` | Wizard chọn ghế mới (exchange mode) |
| Controller | `src/main/java/vn/edu/iuh/fit/client/controller/ExchangeTicketSearchController.java` | Tra cứu và điều hướng sang wizard |
| Controller | `src/main/java/vn/edu/iuh/fit/client/controller/SellTicketWizardController.java` | `initExchangeMode(...)`, chọn ghế mới, gọi preview/confirm đổi |
| Client Service | `src/main/java/vn/edu/iuh/fit/client/service/ExchangeTicketClientService.java` | `SEARCH_TICKETS_FOR_EXCHANGE`, `PREVIEW_EXCHANGE_TICKETS`, `EXCHANGE_TICKET` |
| Client Service | `src/main/java/vn/edu/iuh/fit/client/service/SaleClientService.java` | Dùng lại API seatmap/hold cho đổi vé |
| Common | `vn.edu.iuh.fit.common.command.ActionType` | `SEARCH_TICKETS_FOR_EXCHANGE`, `PREVIEW_EXCHANGE_TICKETS`, `EXCHANGE_TICKET` |
| Router | `src/main/java/vn/edu/iuh/fit/server/network/RequestRouter.java` | Route sang `TicketServiceImpl.*` |
| Server Service | `src/main/java/vn/edu/iuh/fit/server/service/impl/TicketServiceImpl.java` | Core đổi vé + invoice EXCHANGE |
| Server Service | `src/main/java/vn/edu/iuh/fit/server/service/impl/SaleServiceImpl.java` | Hold/release ghế (dùng chung) |
| Repository | `.../repository/impl/TicketRepositoryImpl` | Search ticket by customer doc, resolve paid amount |
| Repository | `.../repository/impl/ScheduleDetailRepositoryImpl` | Load ghế mới + sold seat ids lock |
| Entity/Table | `.../model/Ticket` (`tickets`) | `status`, `qrCode`, `originalTicketId`, `exchanged` |
| Entity/Table | `.../model/Invoice` (`invoices`) | `type=EXCHANGE` |
| Entity/Table | `.../model/InvoiceDetail` (`invoice_details`) | `subTotal` được chia đều |

---

## Notes / Missing / Partial
- **DIFFERENT:** Phí đổi vé hiện là `20_000.0`/vé (`TicketServiceImpl.EXCHANGE_FEE`). Nếu requirement cũ là 50k/20k khác, ghi nhận khác biệt này (không sửa code).
- Đã fix: server enforce rule “giữ nguyên ga đi/ga đến” ở cả `TicketServiceImpl.previewExchangeTickets(...)` và `TicketServiceImpl.doExchangeTicketsOrThrow(...)` (reject `TicketMessages.EXCHANGE_ROUTE_MISMATCH` / `TicketMessages.EXCHANGE_ROUTE_DATA_MISSING`).
- Search UI ghi “CCCD/Hộ chiếu”, nhưng DTO tên trường là `idCard`; repository JPQL lại match cả `Customer.id`, `Customer.idCard`, `Customer.passport` ⇒ hiểu là “mã giấy tờ/định danh” (uncertainty về naming).
- Đã fix: client lưu `ExchangeTicketResponseDTO` (new tickets + invoiceId + totalAmount) và in vé mới sau đổi bằng flow in vé (không in vé cũ); lỗi in không rollback giao dịch đổi vé.
