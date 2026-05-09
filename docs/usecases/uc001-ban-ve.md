# Use case UC001: Bán vé

## Actor
- **Primary:** Nhân viên bán vé/quầy (`Employee`)
- **Secondary:** Khách hàng (người mua, hành khách), dịch vụ thanh toán online nội bộ (`InternalPaymentOrderStore`)
- **System:** JavaFX Client → TCP Socket Server → MariaDB

## Tiền điều kiện
- Nhân viên đã đăng nhập trên JavaFX client và có định danh nhân viên trong session client (ưu tiên `ClientSessionContext.employeeId`, fallback `username/employeeCode`).
- Lịch trình bán vé tồn tại và có trạng thái phù hợp để bán (server filter theo `StatusSchedule.NOT_STARTED`).

## Hậu điều kiện (khi thành công)
- Tạo các `Ticket` mới với `Ticket.status = TicketStatus.PAID`, `Ticket.qrCode = ticketId`.
- Tạo `Invoice` loại `InvoiceType.SALE` và các `InvoiceDetail` tương ứng.
- Cập nhật điểm tích lũy cho người mua (`Customer.rewardPoints`).
- Ghế đã được “hold” bởi `clientSessionId` được nhả sau khi bán thành công (server gọi `SaleServiceImpl.releaseHeldSeatsForSale(...)`; đồng thời client cũng có luồng nhả hold khi reset/deselect).

---

## Luồng chính
### Bước 1 — Tra cứu lịch trình bán vé
1. Client tải danh sách ga: gửi `Request(ActionType.FIND_ALL_STATIONS, "ALL")` (client: `SaleClientService.findAllStations`).
2. Server (`SaleServiceImpl.findAllStations`):
   - Query `StationRepository.findAllStations`.
   - Map → `List<StationDTO>` và trả `Response.success(SaleMessages.STATION_LIST_SUCCESS, stations)`.
3. Nhân viên chọn ga đi/ga đến/ngày đi và loại vé `TicketCategory.ONE_WAY` hoặc `TicketCategory.ROUND_TRIP`.
4. Client gửi `Request(ActionType.SEARCH_SCHEDULES_FOR_SALE, SaleScheduleSearchDTO)` (client: `SaleClientService.searchSchedulesForSale`).
5. Server (`SaleServiceImpl.searchSchedulesForSale`):
   - Validate ga đi/ga đến và ngày đi/ngày về.
   - Lấy danh sách chuyến theo ngày qua `ScheduleRepositoryImpl.filterSchedules(...)` với `StatusSchedule.NOT_STARTED`.
   - Trả `Response.success(SaleMessages.SEARCH_SCHEDULE_SUCCESS, SaleScheduleSearchResultDTO{outboundSchedules, returnSchedules})`.

### Bước 2 — Xem sơ đồ ghế và giữ chỗ (hold/release)
6. Nhân viên chọn chuyến (outbound/return nếu khứ hồi).
7. Client lấy sơ đồ ghế: gửi `Request(ActionType.GET_SEATMAP_FOR_SCHEDULE, SeatMapRequestDTO{scheduleId, clientSessionId})` (client: `SaleClientService.getSeatMap`).
8. Server (`SaleServiceImpl.getSeatMapForSchedule`):
   - Load `Schedule` và `List<ScheduleDetail>` theo `scheduleId`.
   - Tính trạng thái ghế:
     - SOLD nếu `ScheduleDetail` đã có `Ticket` với `status` không thuộc `{CANCELLED, EXCHANGED, RETURNED}`.
     - HELD nếu `SeatHoldStore.getActiveHold(scheduleDetailId)` tồn tại.
     - AVAILABLE nếu không SOLD/HELD.
   - Trả `SeatMapResponseDTO{carriages: List<CarriageSeatMapDTO{seats: List<SeatMapSeatDTO{seatStatus, heldByMe, holdExpiresAtEpochMillis, seatPrice, scheduleDetailId,...}>}>}` với `Response.success(SaleMessages.SEATMAP_SUCCESS, data)`.
9. Khi chọn ghế, client giữ chỗ: gửi `Request(ActionType.HOLD_SEATS_FOR_SALE, SeatHoldRequestDTO{scheduleId, scheduleDetailIds, clientSessionId})` (client: `SaleClientService.holdSeats`).
10. Server (`SaleServiceImpl.holdSeatsForSale`):
    - Validate `scheduleId`, `clientSessionId`, danh sách `scheduleDetailIds`.
    - Không cho hold ghế đã bán (query `Ticket` theo `scheduleDetailId`, loại trừ `{CANCELLED, EXCHANGED, RETURNED}`).
    - Gọi `SeatHoldStore.tryHold(sdId, sessionId, expiresAt)` (TTL cố định 10 phút) và trả `SeatHoldResponseDTO{successIds, failedIds, expiresAtEpochMillis}` với `Response.success(SaleMessages.HOLD_SUCCESS, ...)`.
11. Khi bỏ chọn ghế hoặc reset, client nhả hold: gửi `Request(ActionType.RELEASE_HELD_SEATS_FOR_SALE, SeatHoldRequestDTO{scheduleId, scheduleDetailIds, clientSessionId})` (client: `SaleClientService.releaseHeldSeats`).
12. Server (`SaleServiceImpl.releaseHeldSeatsForSale`) gọi `SeatHoldStore.releaseHold(...)` và trả `Response.success(SaleMessages.RELEASE_HOLD_SUCCESS, SeatHoldResponseDTO{...})`.

### Bước 3 — Nhập hành khách / người mua / VAT / điểm
13. Client thu thập:
    - Danh sách hành khách theo ghế: `List<SalePassengerDTO>` (outbound/return).
    - Trẻ em dưới 6 tuổi không chiếm ghế: `List<SaleChildUnder6DTO{childName, dateOfBirth, accompanyDirection, accompanyPassengerIndex}>`.
    - Người mua: `SaleBuyerDTO{buyerName, documentType, documentNumber, buyerPhone, buyerEmail, hasAccount, customerId}`.
    - Thông tin VAT (nếu nhập): `SaleVatDTO{companyName, taxCode, address}`.
    - Quy đổi điểm (nếu chọn): `SaleRedeemPointsDTO{redeemRequested, pointsToRedeem}`.

### Bước 4 — Thanh toán CASH hoặc ONLINE và chốt bán
14. Nếu thanh toán ONLINE, client tạo đơn thanh toán: gửi `Request(ActionType.CREATE_PAYMENT_ORDER, PaymentCreateRequestDTO{amount, description, clientSessionId})`.
15. Server (`PaymentOrderServiceImpl.createPaymentOrder`) tạo `InternalPaymentOrderStore.PaymentOrder`, sinh QR payload/PNG và trả `PaymentCreateResponseDTO`.
16. Client có thể poll: `Request(ActionType.GET_PAYMENT_ORDER_STATUS, PaymentStatusRequestDTO{paymentOrderId})`.
17. Khi người mua chuyển khoản xong, client xác nhận nội bộ: `Request(ActionType.CONFIRM_INTERNAL_PAYMENT, PaymentStatusRequestDTO{paymentOrderId, clientSessionId})` (router map sang `PaymentOrderServiceImpl.confirmPaymentOrder`).
18. Client gửi chốt bán: `Request(ActionType.CREATE_SALE_TRANSACTION, SaleCreateRequestDTO{...})` (client: `SaleClientService.createSaleTransaction`).
19. Server (`SaleServiceImpl.createSaleTransaction` → transactional `doCreateSale`):
    - Validate:
      - Giới hạn số vé: `MAX_TICKETS_PER_LEG = 10` cho mỗi chiều.
      - Khứ hồi: số ghế chiều đi = chiều về; không trùng ID trong cùng danh sách.
      - Ghế phải đang được hold bởi `clientSessionId` (`SeatHoldStore.getActiveHold(...).clientSessionId`).
      - Quy tắc trẻ em:
        - Vé `TicketType.CHILD` (6–<10) cần `dateOfBirth` và kiểm tra tuổi thực tế theo giờ chạy.
        - Trẻ <6 tuổi phải khai trong `childrenUnder6` (phiếu “không ghế”), tối đa 2 trẻ/1 người lớn theo `(accompanyDirection, accompanyPassengerIndex)`.
        - Tổng số vé người lớn >= tổng số vé `TicketType.CHILD` trong giao dịch.
    - Tự động tạo/cập nhật hồ sơ khách:
      - `ensureCustomerRecord(...)` tìm theo `Customer.idCard` hoặc `Customer.passport` và update `phoneNumber/email` nếu có; nếu không có thì tạo `Customer{isActive=true, rewardPoints=0}`.
      - Áp dụng cho cả người mua và từng hành khách (nếu tạo không được thì fallback về người mua).
    - Tạo `Ticket` và `InvoiceDetail` cho từng ghế (`createSeatTicketsForLeg`):
      - `Ticket.status = TicketStatus.PAID`, `Ticket.qrCode = ticketId`.
      - Giá tính theo `ScheduleDetail.priceSeat` + `insurance=2000`, áp dụng giảm theo `TicketType` và làm tròn lên bội số 1.000.
      - Vé chiều về được giảm thêm 10% (`isReturnTicket`).
    - Tạo “phiếu trẻ <6” (`createChildVoucher`):
      - Tạo `Ticket` không có `scheduleDetail`, `subTotal=0`, `price=0`, `seatNumber="Không ghế"`.
    - Quy đổi điểm (nếu có):
      - Chỉ phân bổ giảm giá lên các `InvoiceDetail` có `TicketType.NORMAL` và `subTotal > 0`.
      - 1 điểm = 1.000 VND (`POINT_REDEEM_VALUE`), tối đa 10% tổng vé hợp lệ (`MAX_REDEEM_RATE`).
    - Thanh toán:
      - CASH: yêu cầu `amountPaid >= totalAmount`.
      - ONLINE: kiểm tra `InternalPaymentOrderStore` có `PaymentStatus.SUCCESS`, session khớp, số tiền khớp (round), và chưa bị “consume”.
    - Tạo `Invoice` (`InvoiceType.SALE`) + `InvoiceMetadata` (VAT address, paymentMethod, paymentOrderId, referenceCode).
    - Persist `InvoiceDetail` và cập nhật điểm tích lũy: `earnedPoints = floor(totalAmount / 10.000)`; `rewardPoints = rewardPoints - redeemedPoints + earnedPoints`.
20. Server trả `Response.success(SaleMessages.SALE_SUCCESS, SaleCreateResponseDTO{invoiceId, totalAmount, amountPaid, changeAmount, earnedPoints, redeemedPoints, tickets, childVouchers})`.
21. Client hiển thị kết quả và cho phép in xem trước:
    - Vé: render từ `IssuedTicketDTO` qua `TicketRenderer.renderToImage(...)` và hiển thị `pdf-viewer.fxml`.
    - Hóa đơn: client hiện in “tóm tắt” bằng `IssuedTicketDTO` tự build (không phải mẫu VAT đầy đủ).

---

## Luồng thay thế
- **[Khứ hồi]:** Client gửi `TicketCategory.ROUND_TRIP` + `returnScheduleId` + `returnScheduleDetailIds` + `returnPassengers`. Server bắt buộc số ghế 2 chiều bằng nhau và kiểm tra thời gian: `returnSchedule.departureTime` phải sau `outboundSchedule.arrivalTime`.
- **[Thanh toán ONLINE]:** Thêm bước tạo đơn + xác nhận (`CREATE_PAYMENT_ORDER` → `GET_PAYMENT_ORDER_STATUS` → `CONFIRM_INTERNAL_PAYMENT`) trước khi `CREATE_SALE_TRANSACTION`.
- **[Trẻ dưới 6 tuổi]:** Không chọn ghế; client gửi `childrenUnder6`. Server tạo “phiếu trẻ <6” (`Ticket` không có `scheduleDetail`).
- **[Quy đổi điểm]:** Client gửi `SaleRedeemPointsDTO`. Server chỉ áp dụng lên vé `TicketType.NORMAL` và giới hạn 10% tổng vé hợp lệ.

---

## Luồng lỗi tiêu biểu (tham chiếu constant)
- `SaleMessages.INVALID_REQUEST`: thiếu/không hợp lệ `scheduleId`, `clientSessionId`, danh sách ghế, scheduleId...
- `SaleMessages.INVALID_STATIONS`: ga đi/ga đến rỗng hoặc trùng nhau.
- `SaleMessages.INVALID_DATES`: thiếu ngày đi hoặc ngày về không hợp lệ (khứ hồi).
- `SaleMessages.SEAT_HELD_BY_OTHER`: ghế không được hold bởi đúng `clientSessionId` tại thời điểm chốt bán.
- `SaleMessages.SEAT_ALREADY_SOLD`: ghế vừa bị giao dịch khác bán (có `OptimisticLockException` hoặc check sold).
- `SaleMessages.PRICE_NOT_CONFIGURED`: `ScheduleDetail.priceSeat <= 0`.
- `SaleMessages.TOO_MANY_TICKETS_PER_LEG`: >10 vé mỗi chiều.
- `SaleMessages.SEAT_CONSTRAINT_MISMATCH`: khứ hồi nhưng số ghế 2 chiều không bằng nhau.
- `SaleMessages.CUSTOMER_DOCUMENT_REQUIRED`: người mua không có CCCD/hộ chiếu hợp lệ để tạo/lookup `Customer`.
- `SaleMessages.PAYMENT_NOT_READY`: cash `amountPaid < totalAmount` hoặc `amountPaid < 0`.
- `SaleMessages.ONLINE_PAYMENT_*`: các lỗi đơn thanh toán online (không tìm thấy, chưa confirm, đã dùng, session mismatch, amount mismatch, consume failed).
- Error message hard-code trong `SaleServiceImpl.applyPassengerPricing(...)` khi sai tuổi/thiếu `dateOfBirth` cho `TicketType.CHILD/SENIOR`.

---

## Business rules
- **Giới hạn số lượng:** `MAX_TICKETS_PER_LEG = 10` (mỗi chiều).
- **Hold ghế:** TTL = 10 phút (`SeatHoldStore.HOLD_TTL_MILLIS`); ghế SOLD/HELD không thể hold bởi phiên khác.
- **Chống trùng giấy tờ hành khách:** cùng 1 chiều (outbound/return) không được mua >1 vé cho cùng `documentNumber` (message hard-code trong `SaleServiceImpl.doCreateSale`).
- **Ràng buộc vé trẻ em (6–<10):** phải có `dateOfBirth`; nếu <6 phải khai qua `childrenUnder6`; nếu >=10 không được mua `TicketType.CHILD`.
- **Ràng buộc vé người cao tuổi:** `TicketType.SENIOR` yêu cầu tuổi >=60 (check theo giờ chạy).
- **Trẻ dưới 6 tuổi:** tối đa 2 trẻ/1 người lớn theo `(accompanyDirection, accompanyPassengerIndex)`; tạo “phiếu không ghế”.
- **Bắt buộc có người lớn kèm trẻ em:** tổng vé người lớn >= tổng vé `TicketType.CHILD` trong 1 giao dịch.
- **Giá + phí:** `insurance = 2000`/vé; làm tròn giá cuối lên bội số 1.000.
- **Giảm theo đối tượng:** `CHILD=25%`, `SENIOR=15%`, `STUDENT=10%`, `NORMAL=0%` (trên `base+insurance`); vé chiều về giảm thêm 10%.
- **Điểm tích lũy/đổi điểm:**
  - Earn: `floor(totalAmount / 10.000)`.
  - Redeem: 1 điểm = 1.000; trần 10% tổng vé hợp lệ; chỉ phân bổ lên vé `TicketType.NORMAL`.

---

## Dữ liệu vào/ra (I/O)

### Client → Server
| ActionType | DTO | Trường chính |
|---|---|---|
| `FIND_ALL_STATIONS` | `String` | `"ALL"` (client gửi cố định) |
| `SEARCH_SCHEDULES_FOR_SALE` | `SaleScheduleSearchDTO` | `departureStationId`, `destinationStationId`, `departureDate`, `ticketCategory`, `returnDate`, `page`, `size` |
| `GET_SEATMAP_FOR_SCHEDULE` | `SeatMapRequestDTO` | `scheduleId`, `clientSessionId` |
| `HOLD_SEATS_FOR_SALE` | `SeatHoldRequestDTO` | `scheduleId`, `scheduleDetailIds`, `clientSessionId` |
| `RELEASE_HELD_SEATS_FOR_SALE` | `SeatHoldRequestDTO` | `scheduleId`, `scheduleDetailIds`, `clientSessionId` |
| `CREATE_PAYMENT_ORDER` | `PaymentCreateRequestDTO` | `amount`, `description`, `clientSessionId` |
| `GET_PAYMENT_ORDER_STATUS` | `PaymentStatusRequestDTO` | `paymentOrderId` |
| `CONFIRM_INTERNAL_PAYMENT` | `PaymentStatusRequestDTO` | `paymentOrderId`, `clientSessionId` |
| `CREATE_SALE_TRANSACTION` | `SaleCreateRequestDTO` | `clientSessionId`, `ticketCategory`, `outboundScheduleId`, `returnScheduleId`, `outboundScheduleDetailIds`, `returnScheduleDetailIds`, `outboundPassengers`, `returnPassengers`, `childrenUnder6`, `buyer`, `vat`, `redeemPoints`, `paymentMethod`, `amountPaid`, `paymentOrderId`, `employeeId` |

### Server → Client
| Response.data |
|---|
| `List<StationDTO>` |
| `SaleScheduleSearchResultDTO` |
| `SeatMapResponseDTO` |
| `SeatHoldResponseDTO` |
| `PaymentCreateResponseDTO` / `PaymentStatusDTO` |
| `SaleCreateResponseDTO` |

---

## Code Trace
| Layer | File/Class | Ghi chú |
|---|---|---|
| FXML | `src/main/resources/client/ui/views/sell-ticket-wizard.fxml` | Wizard bán vé |
| Controller | `src/main/java/vn/edu/iuh/fit/client/controller/SellTicketWizardController.java` | Quản lý flow, giữ ghế, thanh toán, in preview |
| Client Service | `src/main/java/vn/edu/iuh/fit/client/service/SaleClientService.java` | Wrap các `ActionType` của UC001 |
| Socket client | `src/main/java/vn/edu/iuh/fit/client/service/SocketRequestService.java` | TCP `ObjectOutputStream/ObjectInputStream` tới `127.0.0.1:9090` |
| Common | `vn.edu.iuh.fit.common.command.ActionType` | `FIND_ALL_STATIONS`, `SEARCH_SCHEDULES_FOR_SALE`, ... |
| Common | `vn.edu.iuh.fit.common.dto.*` | `SaleCreateRequestDTO`, `SeatHoldRequestDTO`, ... |
| Common | `vn.edu.iuh.fit.common.message.SaleMessages`, `PaymentMessages` | Message constants |
| Router | `src/main/java/vn/edu/iuh/fit/server/network/RequestRouter.java` | Map `ActionType` → service |
| Server Service | `src/main/java/vn/edu/iuh/fit/server/service/impl/SaleServiceImpl.java` | Core nghiệp vụ bán vé + hold |
| Server Service | `src/main/java/vn/edu/iuh/fit/server/service/impl/PaymentOrderServiceImpl.java` | Tạo/xác nhận thanh toán online nội bộ |
| Repository | `.../ScheduleRepositoryImpl`, `.../ScheduleDetailRepositoryImpl`, `.../TicketRepositoryImpl` | Query lịch trình/ghế/vé |
| Entity/Table | `.../model/Ticket` (`tickets`) | `status`, `qrCode`, `originalTicketId`, `exchanged` |
| Entity/Table | `.../model/Invoice` (`invoices`) | `type=SALE`, `totalAmount`, `taxCode`, `companyName` |
| Entity/Table | `.../model/InvoiceDetail` (`invoice_details`) | `subTotal`, `discount`, `insurance`, `isReturned`, `refundAmount` |
| Entity/Table | `.../model/Customer` (`customers`) | `isActive`, `rewardPoints`, `idCard/passport` |

---

## Notes / Missing / Partial
- Đã fix: client set `SaleCreateRequestDTO.employeeId` từ `ClientSessionContext` và server (`SaleServiceImpl`) resolve nhân viên theo `employee_id` hoặc `employee_code`; nếu không resolve được trả lỗi `SaleMessages.EMPLOYEE_REQUIRED` (không tạo invoice với `employee=null`).
- Đã fix: in hóa đơn sau bán vé dùng `InvoiceRenderer` + template VAT/HTML (`/client/print/invoice-template.html`, `/client/print/invoice-style.css`) để render PDF preview; lỗi in không rollback giao dịch bán vé.
- Đã fix: server enforce rule không cho dùng điểm đồng thời với ưu đãi đối tượng (reject `SaleMessages.POINTS_NOT_ALLOWED_WITH_DISCOUNT` khi `redeemPoints>0` và có vé `TicketType != NORMAL`).
