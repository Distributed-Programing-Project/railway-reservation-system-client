# Diagrams: UC003 Trả vé

> Source use case: `docs/usecases/uc003-tra-ve.md`  
> Distributed Java system — JavaFX Client/Server over TCP Socket

## Activity Diagram

```mermaid
flowchart TD
    Start([Start]) --> A1[Nhập từ khóa (tùy chọn) để tra cứu vé PAID]
    A1 --> A2[Hiển thị danh sách vé có thể trả]
    A2 --> D1{Có vé PAID?}
    D1 -- Không --> E1[Thông báo không có vé phù hợp] --> End([End])
    D1 -- Có --> A3[Chọn 1..n vé để trả]

    A3 --> A4[Xem trước tiền hoàn]
    A4 --> D2{Đủ điều kiện trả?}
    D2 -- Không --> E2[Thông báo: dưới 4h/đã trả/khác khách hàng] --> End
    D2 -- Có --> A5[Xác nhận trả vé]
    A5 --> D3{Số tiền khớp tính toán server?}
    D3 -- Không --> E3[Lỗi mismatch số tiền] --> A4
    D3 -- Có --> A6[Cập nhật Ticket RETURNED + QR INVALID]
    A6 --> A7[Tạo Invoice REFUND + InvoiceDetail]
    A7 --> D4{In biên lai?}
    D4 -- Có --> A8[Lấy dữ liệu biên lai & in] --> A9[Reload danh sách vé PAID]
    D4 -- Không --> A9[Reload danh sách vé PAID]
    A9 --> End
```

## Sequence Diagram

```mermaid
sequenceDiagram
    actor NV as Nhân viên
    participant UI as ReturnTicketController
    participant CS as ReturnTicketClientService
    participant SOCK as SocketRequestService
    participant RR as RequestRouter
    participant SVC as TicketServiceImpl
    participant REPO as Repository/JPA
    participant DB as MariaDB

    NV->>UI: Nhập từ khóa, nhấn Tra cứu
    UI->>CS: searchTicketsForReturn(query)
    CS->>SOCK: send(Request(SEARCH_TICKETS_FOR_RETURN, ReturnTicketSearchDTO))
    SOCK->>RR: TCP/ObjectStream Request
    RR->>SVC: searchTicketsForReturn(dto)
    SVC->>REPO: TicketRepositoryImpl (find by id/qr/buyer/passenger)
    REPO->>DB: Query tickets + schedule
    DB-->>REPO: rows
    REPO-->>SVC: List<ReturnTicketTicketDTO>
    SVC-->>RR: Response.success(..., List<ReturnTicketTicketDTO>)
    RR-->>SOCK: Response.success
    SOCK-->>CS: Response
    CS-->>UI: ticket list
    UI-->>NV: Hiển thị danh sách vé

    NV->>UI: Chọn vé, xem trước
    UI->>CS: previewReturnTickets(ticketIds)
    CS->>SOCK: send(Request(PREVIEW_RETURN_TICKETS, ReturnTicketPreviewRequestDTO))
    SOCK->>RR: TCP/ObjectStream Request
    RR->>SVC: previewReturnTickets(dto)
    SVC->>REPO: TicketRepositoryImpl (load + resolveActualPaidAmount)
    REPO->>DB: Query invoice sale details
    DB-->>REPO: rows
    REPO-->>SVC: computed
    SVC-->>RR: Response.success(..., ReturnTicketPreviewDTO)
    RR-->>SOCK: Response.success
    SOCK-->>CS: Response
    CS-->>UI: preview data
    UI-->>NV: Hiển thị refund/fee
```

```mermaid
sequenceDiagram
    actor NV as Nhân viên
    participant UI as ReturnTicketController
    participant CS as ReturnTicketClientService
    participant SOCK as SocketRequestService
    participant RR as RequestRouter
    participant SVC as TicketServiceImpl
    participant REPO as Repository/JPA
    participant DB as MariaDB
    participant PRINT as Print/Renderer

    NV->>UI: Xác nhận trả vé
    UI->>CS: confirmReturnTickets(ticketIds, refundAmount, employeeId)
    CS->>SOCK: send(Request(CONFIRM_RETURN_TICKETS, ReturnTicketConfirmDTO))
    SOCK->>RR: TCP/ObjectStream Request
    RR->>SVC: confirmReturnTickets(dto)
    Note over SVC: transactional (re-compute + update Ticket + create invoice REFUND)
    SVC->>REPO: TicketRepositoryImpl / InvoiceRepositoryImpl / InvoiceDetailRepositoryImpl
    REPO->>DB: UPDATE/INSERT
    DB-->>REPO: OK
    REPO-->>SVC: refundInvoiceId

    alt refundAmount mismatch / không đủ điều kiện
        SVC-->>RR: Response.error(...)
        RR-->>SOCK: Response.error
        SOCK-->>CS: Response.error
        CS-->>UI: lỗi
        UI-->>NV: Hiển thị lỗi
    else Thành công
        SVC-->>RR: Response.success(..., refundInvoiceId)
        RR-->>SOCK: Response.success
        SOCK-->>CS: Response
        CS-->>UI: refundInvoiceId
        UI-->>NV: Hiển thị hoàn tiền thành công

        opt In biên lai hoàn tiền
            UI->>CS: getRefundReceipt(refundInvoiceId)
            CS->>SOCK: send(Request(GET_REFUND_RECEIPT, RefundReceiptRequestDTO))
            SOCK->>RR: TCP/ObjectStream Request
            RR->>SVC: getRefundReceipt(dto)
            SVC-->>RR: Response.success(..., RefundReceiptDTO)
            RR-->>SOCK: Response.success
            SOCK-->>CS: Response
            CS-->>UI: receipt data
            UI->>PRINT: render(RefundReceiptDTO)
            PRINT-->>UI: preview
            UI-->>NV: In/hiển thị
        end
    end
```

## Notes
- `GET_REFUND_RECEIPT` trên server có thể chỉ lấy detail đầu tiên của invoice REFUND; nếu trả nhiều vé trong một giao dịch thì biên lai có thể partial.
- Không thấy logic hoàn/điều chỉnh `Customer.rewardPoints` khi trả vé trong `TicketServiceImpl` (theo usecase doc).

