# Diagrams: UC002 Đổi vé

> Source use case: `docs/usecases/uc002-doi-ve.md`  
> Distributed Java system — JavaFX Client/Server over TCP Socket

## Activity Diagram

```mermaid
flowchart TD
    Start([Start]) --> A1[Nhập CCCD/Hộ chiếu để tra cứu vé]
    A1 --> A2[Tải danh sách vé đủ điều kiện đổi]
    A2 --> D1{Có vé eligible?}
    D1 -- Không --> E1[Thông báo không có vé đủ điều kiện] --> End([End])
    D1 -- Có --> A3[Chọn vé cần đổi]

    A3 --> A4[Mở wizard chọn ghế mới (exchange mode)]
    A4 --> A5[Chọn chuyến mới & xem seatmap]
    A5 --> A6[Chọn ghế mới & hold ghế]
    A6 --> D2{Hold đủ ghế?}
    D2 -- Không --> E2[Ghế mới không còn/đang giữ] --> A5
    D2 -- Có --> A7[Xem trước phí đổi/chênh lệch]
    A7 --> D3{Đủ điều kiện đổi?}
    D3 -- Không --> E3[Thông báo lý do không đủ điều kiện] --> A8[Release hold] --> End
    D3 -- Có --> A9[Xác nhận đổi vé]
    A9 --> A10[Cập nhật vé cũ EXCHANGED + tạo vé mới PAID]
    A10 --> A11[Tạo Invoice EXCHANGE]
    A11 --> D4{In vé mới?}
    D4 -- Có --> A12[Render/Print vé mới] --> End
    D4 -- Không --> End
```

## Sequence Diagram

```mermaid
sequenceDiagram
    actor NV as Nhân viên
    participant UI as ExchangeTicketSearchController
    participant CS as ExchangeTicketClientService
    participant SOCK as SocketRequestService
    participant RR as RequestRouter
    participant SVC as TicketServiceImpl
    participant REPO as Repository/JPA
    participant DB as MariaDB

    NV->>UI: Nhập giấy tờ, nhấn Tra cứu
    UI->>CS: searchTicketsForExchange(idCard)
    CS->>SOCK: send(Request(SEARCH_TICKETS_FOR_EXCHANGE, ExchangeEligibleTicketSearchDTO))
    SOCK->>RR: TCP/ObjectStream Request
    RR->>SVC: searchTicketsForExchange(dto)
    SVC->>REPO: TicketRepositoryImpl.findTicketsByCustomerIdCardWithStatusForExchange(...)
    REPO->>DB: Query tickets PAID
    DB-->>REPO: rows
    REPO-->>SVC: tickets
    SVC-->>RR: Response.success(..., List<ExchangeEligibleTicketDTO>)
    RR-->>SOCK: Response.success
    SOCK-->>CS: Response
    CS-->>UI: eligible tickets
    UI-->>NV: Chọn vé eligible
```

```mermaid
sequenceDiagram
    actor NV as Nhân viên
    participant UI as SellTicketWizardController
    participant CS as SaleClientService
    participant EX as ExchangeTicketClientService
    participant SOCK as SocketRequestService
    participant RR as RequestRouter
    participant SVC as TicketServiceImpl
    participant SALE as SaleServiceImpl
    participant REPO as Repository/JPA
    participant DB as MariaDB
    participant PRINT as Print/Renderer

    NV->>UI: Chọn chuyến/ghế mới
    UI->>CS: getSeatMap(scheduleId, clientSessionId)
    CS->>SOCK: send(Request(GET_SEATMAP_FOR_SCHEDULE, SeatMapRequestDTO))
    SOCK->>RR: TCP/ObjectStream Request
    RR->>SALE: getSeatMapForSchedule(dto)
    SALE-->>RR: Response.success(..., SeatMapResponseDTO)
    RR-->>SOCK: Response.success
    SOCK-->>CS: Response
    CS-->>UI: seatmap

    UI->>CS: holdSeats(scheduleId, newDetailIds, clientSessionId)
    CS->>SOCK: send(Request(HOLD_SEATS_FOR_SALE, SeatHoldRequestDTO))
    SOCK->>RR: TCP/ObjectStream Request
    RR->>SALE: holdSeatsForSale(dto)
    SALE-->>RR: Response.success(..., SeatHoldResponseDTO)
    RR-->>SOCK: Response.success
    SOCK-->>CS: Response
    CS-->>UI: hold result

    NV->>UI: Xem trước phí đổi
    UI->>EX: previewExchangeTickets(oldTicketIds, newDetailIds, clientSessionId)
    EX->>SOCK: send(Request(PREVIEW_EXCHANGE_TICKETS, ExchangeTicketPreviewRequestDTO))
    SOCK->>RR: TCP/ObjectStream Request
    RR->>SVC: previewExchangeTickets(dto)
    SVC->>REPO: TicketRepositoryImpl.findTicketsForExchange(...)
    REPO->>DB: Query old tickets + schedule details
    DB-->>REPO: rows
    REPO-->>SVC: data
    SVC-->>RR: Response.success(..., ExchangeTicketPreviewDTO)
    RR-->>SOCK: Response.success
    SOCK-->>EX: Response
    EX-->>UI: preview

    alt Không đủ điều kiện đổi / hold không hợp lệ
        SVC-->>RR: Response.error(...)
        RR-->>SOCK: Response.error
        SOCK-->>EX: Response.error
        EX-->>UI: Hiển thị lỗi
    else Xác nhận đổi
        UI->>EX: exchangeTickets(ExchangeTicketRequestDTO)
        EX->>SOCK: send(Request(EXCHANGE_TICKET, ExchangeTicketRequestDTO))
        SOCK->>RR: TCP/ObjectStream Request
        RR->>SVC: exchangeTickets(dto)
        Note over SVC: transactional (update vé cũ, tạo vé mới, tạo invoice EXCHANGE)
        SVC->>REPO: TicketRepositoryImpl / InvoiceRepositoryImpl / InvoiceDetailRepositoryImpl
        REPO->>DB: UPDATE/INSERT
        DB-->>REPO: OK
        REPO-->>SVC: entities
        SVC-->>RR: Response.success(..., ExchangeTicketResponseDTO)
        RR-->>SOCK: Response.success
        SOCK-->>EX: Response
        EX-->>UI: Response.data
        UI-->>NV: Hiển thị kết quả

        opt In vé mới
            UI->>PRINT: render(...)
            PRINT-->>UI: preview
            UI-->>NV: In/hiển thị
        end
    end
```

## Notes
- Luồng kiểm tra “giữ nguyên ga đi/ga đến” không thấy thể hiện rõ ở server; nếu có yêu cầu nghiệp vụ này thì hiện đang thiếu/partial theo usecase doc.
- In vé sau đổi có thể partial do `SellTicketWizardController` không gán dữ liệu in từ `ExchangeTicketResponseDTO` (theo usecase doc).

