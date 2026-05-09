# Use case UC003: Trả vé

## Actor
- **Primary:** Nhân viên bán vé/quầy (`Employee`)
- **Secondary:** Khách hàng (người mua)
- **System:** JavaFX Client → TCP Socket Server → MariaDB

## Tiền điều kiện
- Vé cần trả ở trạng thái `TicketStatus.PAID`.
- Còn ít nhất 4 giờ trước giờ chạy của vé (`MINUTES_4H = 4*60` trong server).
- Nhân viên đã đăng nhập và client lấy được `employeeId` để gửi trong `ReturnTicketConfirmDTO.employeeId`.

## Hậu điều kiện (khi thành công)
- Vé được cập nhật:
  - `Ticket.status = TicketStatus.RETURNED`
  - `Ticket.qrCode = "INVALID"`
- Tạo `Invoice` loại `InvoiceType.REFUND` và `InvoiceDetail` hoàn tiền cho từng vé.
- Cập nhật `InvoiceDetail` của `InvoiceType.SALE` tương ứng:
  - `InvoiceDetail.isReturned = true`
  - `InvoiceDetail.refundAmount = ...` theo tính toán

---

## Luồng chính
### Bước 1 — Tra cứu vé trả
1. Client gửi `Request(ActionType.SEARCH_TICKETS_FOR_RETURN, ReturnTicketSearchDTO{query, queryType=AUTO})` (client: `ReturnTicketClientService.searchTicketsForReturn`).
2. Server (`TicketServiceImpl.searchTicketsForReturn`):
   - Nếu `query` rỗng/null: load mặc định danh sách vé `TicketStatus.PAID` bằng `TicketRepository.findTicketsByStatusWithSchedule(...)`.
   - Nếu có `query`:
     - `ReturnTicketSearchType.TICKET_ID`: tìm theo `Ticket.id` hoặc `Ticket.qrCode` (`findTicketByIdOrQrWithSchedule`).
     - `BUYER_DOCUMENT`: tìm theo định danh người mua (`findTicketsByCustomerIdCardWithStatus`), JPQL match `Customer.id` / `Customer.idCard` / `Customer.passport`.
     - `PASSENGER_DOCUMENT`: tìm theo `Ticket.passengerIdCard`.
     - `AUTO`: thử theo `Ticket.id/qrCode` trước, nếu không có thì merge kết quả BUYER_DOCUMENT + PASSENGER_DOCUMENT.
   - Filter lại `status==PAID` và trả `Response.success(TicketMessages.FIND_SUCCESS, List<ReturnTicketTicketDTO>)`.
3. Client hiển thị danh sách và cho phép chọn 1 hoặc nhiều vé để trả.

### Bước 2 — Xem trước tiền hoàn
4. Khi người dùng chọn vé, client gọi xem trước: `Request(ActionType.PREVIEW_RETURN_TICKETS, ReturnTicketPreviewRequestDTO{ticketIds})` (client: `ReturnTicketClientService.previewReturnTickets`).
5. Server (`TicketServiceImpl.previewReturnTickets`):
   - Validate DTO (`ValidationUtils.validate`), loại trùng id (`TICKET_IDS_DUPLICATE`) và đảm bảo đủ số vé load được (`SOME_TICKETS_INVALID`).
   - Tính toán refund qua `doComputeReturn(em, ticketIds)`:
     - Mỗi vé phải `status==PAID`, có `scheduleDetail.schedule.departureTime`.
     - Cutoff: `Duration.between(now, departureTime).toMinutes() >= MINUTES_4H`.
     - Giá gốc dùng **actual paid amount**: `resolveActualPaidAmount(em, ticket)` (fallback `ScheduleDetail.priceSeat`).
     - Phí trả vé: `computeReturnFee(price, exchanged, minutesToDeparture)`:
       - Nếu vé đã đổi (`ticket.originalTicketId != null` hoặc `ticket.isExchanged==true`) ⇒ 30%.
       - Else nếu còn <24h ⇒ 20%; còn >=24h ⇒ 10%.
       - Min fee mỗi vé: `10_000` (`MIN_RETURN_FEE_PER_TICKET`).
       - Làm tròn phí lên bội số 1.000 (`ceil(fee/1000)*1000`), và không vượt quá giá vé.
     - `refundAmount = price - fee` (không âm).
   - Rule batch: tất cả vé phải thuộc cùng 1 `Customer` (`validateSameCustomer`), nếu không trả `TicketMessages.CUSTOMER_MISMATCH`.
   - Trả `Response.success(TicketMessages.PREVIEW_SUCCESS, ReturnTicketPreviewDTO{totalTicketPrice, refundFee, refundAmount})`.

### Bước 3 — Xác nhận trả vé và in biên lai (tuỳ chọn)
6. Nhân viên xác nhận trả, client gửi `Request(ActionType.CONFIRM_RETURN_TICKETS, ReturnTicketConfirmDTO{ticketIds, refundAmount, employeeId})`.
7. Server (`TicketServiceImpl.confirmReturnTickets` → transactional `doConfirmReturnTickets`):
   - Re-compute refund bằng `doComputeReturn(...)`.
   - Chống “đổi số tiền”: nếu `abs(confirmDTO.refundAmount - computed.totalRefundAmount) > REFUND_TOLERANCE (1.0)` ⇒ lỗi `TicketMessages.REFUND_AMOUNT_MISMATCH`.
   - Resolve nhân viên theo `employeeId` hoặc `employee_code` (`findEmployeeByIdOrCode`).
   - Tạo `Invoice{type=REFUND, totalAmount=totalRefundAmount}` và persist.
   - Tạo `InvoiceDetail` refund cho từng vé:
     - `subTotal = ticketPrice`, `isReturned=true`, `refundAmount=...`, `insurance=0` (non-refundable theo comment).
   - Update các `InvoiceDetail` của invoice SALE theo ticketIds:
     - `setReturned(true)` và set `refundAmount`.
   - Update vé: `status=RETURNED`, `qrCode="INVALID"`.
   - Trả `Response.success(TicketMessages.RETURN_SUCCESS, refundInvoiceId)`.
8. Client xóa vé vừa trả khỏi bảng, reset panel preview, reload danh sách vé PAID, và hỏi in biên lai.
9. Nếu chọn in, client gửi `Request(ActionType.GET_REFUND_RECEIPT, RefundReceiptRequestDTO{refundInvoiceId})`.
10. Server (`TicketServiceImpl.getRefundReceipt`) build `RefundReceiptDTO` từ `Invoice` REFUND và trả `Response.success("Lấy dữ liệu biên lai hoàn tiền thành công.", dto)`.
11. Client tạo Jasper report từ template `/client/print/bien-lai-tra-ve.xml` và hiển thị preview/in (`ReturnTicketController.createRefundReceiptReport` → `JasperPrintManager.printReport`).

---

## Luồng thay thế
- **[Tra cứu không nhập query]:** client gửi `query=""` để server load danh sách vé `PAID` mặc định.
- **[Trả nhiều vé một lần]:** client gửi danh sách `ticketIds`. Server bắt buộc cùng `Customer` và sẽ tạo 1 `InvoiceType.REFUND` cho cả lô.
- **[Không in biên lai]:** client bỏ qua bước `GET_REFUND_RECEIPT`.

---

## Luồng lỗi tiêu biểu (tham chiếu constant)
- `TicketMessages.TICKET_IDS_REQUIRED`: danh sách vé rỗng khi preview/confirm.
- `TicketMessages.TICKET_IDS_DUPLICATE`: danh sách ticketIds có trùng.
- `TicketMessages.SOME_TICKETS_INVALID`: có vé không tồn tại/không load đủ.
- `TicketMessages.TICKET_NOT_RETURNABLE`: vé không ở trạng thái `PAID`.
- `TicketMessages.NOT_ELIGIBLE_BY_TIME`: còn <4h trước giờ chạy.
- `TicketMessages.SCHEDULE_NOT_FOUND`: thiếu schedule/departureTime.
- `TicketMessages.CUSTOMER_MISMATCH`: trả theo lô nhưng khác khách hàng.
- `TicketMessages.REFUND_AMOUNT_MISMATCH`: client gửi số tiền hoàn không khớp tính toán server.
- `TicketMessages.DATA_CONFLICT`: xung đột optimistic lock khi confirm (`OptimisticLockException`).
- `TicketMessages.SEARCH_FAILED_PREFIX`, `PREVIEW_FAILED_PREFIX`, `RETURN_FAILED_PREFIX`: lỗi hệ thống khi search/preview/confirm.

---

## Business rules
- **Cutoff trả vé:** tối thiểu 4 giờ trước giờ chạy (`MINUTES_4H`).
- **Fee rate:**
  - Vé đã đổi ⇒ 30% (ưu tiên theo flag exchanged).
  - Vé thường: <24h ⇒ 20%, >=24h ⇒ 10% (`MINUTES_24H`).
- **Min fee:** 10.000/ vé; **round up** bội số 1.000.
- **Refund base amount:** ưu tiên “actual paid amount” theo invoice SALE detail (`resolveActualPaidAmount`), fallback `ScheduleDetail.priceSeat` (server có log cảnh báo).
- **Chống double refund:** vé phải `PAID` tại thời điểm confirm; sau confirm sẽ chuyển `RETURNED` và `qrCode="INVALID"`.
- **Refund invoice:** luôn tạo `InvoiceType.REFUND` và lưu `InvoiceDetail.isReturned=true`.
- **Batch constraint:** tất cả vé trong 1 lần trả phải cùng `Customer`.

---

## Dữ liệu vào/ra (I/O)

### Client → Server
| ActionType | DTO | Trường chính |
|---|---|---|
| `SEARCH_TICKETS_FOR_RETURN` | `ReturnTicketSearchDTO` | `query`, `queryType` (`AUTO/TICKET_ID/BUYER_DOCUMENT/PASSENGER_DOCUMENT`) |
| `PREVIEW_RETURN_TICKETS` | `ReturnTicketPreviewRequestDTO` | `ticketIds` |
| `CONFIRM_RETURN_TICKETS` | `ReturnTicketConfirmDTO` | `ticketIds`, `refundAmount`, `employeeId` |
| `GET_REFUND_RECEIPT` | `RefundReceiptRequestDTO` | `refundInvoiceId` |

### Server → Client
| Response.data |
|---|
| `List<ReturnTicketTicketDTO>` |
| `ReturnTicketPreviewDTO` |
| `refundInvoiceId` (String) |
| `RefundReceiptDTO` |

---

## Code Trace
| Layer | File/Class | Ghi chú |
|---|---|---|
| FXML | `src/main/resources/client/ui/views/tra-ve.fxml` | Màn hình trả vé |
| Controller | `src/main/java/vn/edu/iuh/fit/client/controller/ReturnTicketController.java` | Search/preview/confirm/in biên lai |
| Client Service | `src/main/java/vn/edu/iuh/fit/client/service/ReturnTicketClientService.java` | Wrap các `ActionType` trả vé |
| Socket client | `src/main/java/vn/edu/iuh/fit/client/service/SocketRequestService.java` | TCP ObjectStream |
| Common | `vn.edu.iuh.fit.common.command.ActionType` | `SEARCH_TICKETS_FOR_RETURN`, `PREVIEW_RETURN_TICKETS`, ... |
| Common | `vn.edu.iuh.fit.common.message.TicketMessages` | Message constants chính |
| Router | `src/main/java/vn/edu/iuh/fit/server/network/RequestRouter.java` | Route sang `TicketServiceImpl.*` |
| Server Service | `src/main/java/vn/edu/iuh/fit/server/service/impl/TicketServiceImpl.java` | Core trả vé + receipt DTO |
| Repository | `.../repository/impl/TicketRepositoryImpl` | Search ticket theo id/qr/buyer/passenger |
| Repository | `.../repository/impl/InvoiceDetailRepositoryImpl` | Update SALE details + tạo REFUND details (uncertainty: method name không mở trong task) |
| Entity/Table | `.../model/Ticket` (`tickets`) | `status`, `qrCode` |
| Entity/Table | `.../model/Invoice` (`invoices`) | `type=REFUND` |
| Entity/Table | `.../model/InvoiceDetail` (`invoice_details`) | `isReturned`, `refundAmount` |
| Print template | `src/main/resources/client/print/bien-lai-tra-ve.xml` | JasperReports template |

---

## Notes / Missing / Partial
- Đã fix: `TicketServiceImpl.buildRefundReceiptDTO(...)` build biên lai hoàn tiền từ **tất cả** `invoice.details` và trả về `RefundReceiptDTO.items` (backward-compatible: vẫn set các field single-item theo item đầu tiên).
- Đã fix: `TicketServiceImpl.doConfirmReturnTickets(...)` revoke điểm tích lũy theo phần vé trả (không hoàn điểm đã redeem; không để âm).
