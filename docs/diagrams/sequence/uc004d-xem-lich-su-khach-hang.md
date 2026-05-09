# Diagrams: UC004D Xem lịch sử khách hàng

> Source use case: `docs/usecases/uc004d-xem-lich-su-khach-hang.md`  
> Distributed Java system — JavaFX Client/Server over TCP Socket

## Activity Diagram

```mermaid
flowchart TD
    Start([Start]) --> A1[Chọn khách hàng trong danh sách]
    A1 --> A2[Mở màn hình lịch sử]
    A2 --> A3[Gửi request lấy lịch sử]
    A3 --> D1{Thành công?}
    D1 -- Không --> E1[Hiển thị lỗi lấy lịch sử] --> End([End])
    D1 -- Có --> A4[Hiển thị danh sách lịch sử + tổng tiền]
    A4 --> End
```

## Sequence Diagram

```mermaid
sequenceDiagram
    actor NV as Nhân viên
    participant UI as CustomerHistoryController
    participant SOCK as SocketRequestService
    participant RR as RequestRouter
    participant SVC as CustomerServiceImpl
    participant REPO as CustomerRepositoryImpl
    participant DB as MariaDB

    NV->>UI: Mở lịch sử mua vé
    UI->>SOCK: send(Request(GET_CUSTOMER_HISTORY, CustomerHistoryRequestDTO))
    SOCK->>RR: TCP/ObjectStream Request
    RR->>SVC: getCustomerHistory(CustomerHistoryRequestDTO)
    SVC->>REPO: findCustomerTicketHistory(em, customerId)
    REPO->>DB: Query tickets/invoices history
    DB-->>REPO: rows
    REPO-->>SVC: items
    SVC->>REPO: sumCustomerInvoiceTotalAmount(em, customerId)
    REPO->>DB: SUM invoice totalAmount
    DB-->>REPO: value
    REPO-->>SVC: totalAmount
    SVC-->>RR: Response.success(..., CustomerHistoryResponseDTO)
    RR-->>SOCK: Response.success
    SOCK-->>UI: Response.success
    UI-->>NV: Render history + totalAmount
```

## Notes
- Message success/error của `CustomerServiceImpl.getCustomerHistory` đang hard-code (không theo `CustomerMessages.*`), nên trace chuỗi message cần bám theo code hiện tại.

