# Diagrams: UC001 Bán vé

> Source use case: `docs/usecases/uc001-ban-ve.md`  
> Distributed Java system — JavaFX Client/Server over TCP Socket

## Activity Diagram

```mermaid
flowchart TD
    Start([Start]) --> A1[Tải danh sách ga]
    A1 --> A2[Nhập tiêu chí: ga đi/đến, ngày, loại vé]
    A2 --> D1{Có lịch trình phù hợp?}
    D1 -- Không --> E1[Thông báo không có chuyến] --> End([End])
    D1 -- Có --> A3[Chọn chuyến đi (và chuyến về nếu khứ hồi)]

    subgraph SEAT["Chọn ghế & giữ chỗ"]
        A3 --> A4[Xem sơ đồ ghế]
        A4 --> A5[Chọn ghế]
        A5 --> A6[Hold ghế theo clientSessionId]
        A6 --> D2{Hold đủ ghế?}
        D2 -- Không --> E2[Thông báo ghế đã giữ/bán] --> A5
        D2 -- Có --> A7[Tiếp tục]
    end

    A7 --> A8[Nhập người mua & hành khách]
    A8 --> D3{Dữ liệu hợp lệ?}
    D3 -- Không --> E3[Hiển thị lỗi nhập liệu] --> A8
    D3 -- Có --> A9[Preview / tính giá]

    A9 --> D4{Phương thức thanh toán?}
    D4 -- Tiền mặt --> A10[Xác nhận thanh toán]
    D4 -- Online --> A11[Tạo payment order]
    A11 --> A12[Kiểm tra trạng thái / xác nhận thanh toán]
    A12 --> D5{Thanh toán OK?}
    D5 -- Không --> E4[Thông báo lỗi thanh toán] --> D6{Hủy giao dịch?}
    D6 -- Có --> A13[Release hold ghế] --> End
    D6 -- Không --> A11
    D5 -- Có --> A10

    A10 --> A14[Tạo Ticket + Invoice + InvoiceDetail]
    A14 --> A15[Release hold ghế]
    A15 --> D7{In vé/hóa đơn?}
    D7 -- Có --> A16[Render/Print vé & hóa đơn]
    D7 -- Không --> A17[Hoàn tất]
    A16 --> End
    A17 --> End
```

## Sequence Diagram

```mermaid
sequenceDiagram
    actor NV as Nhân viên
    participant UI as SellTicketWizardController
    participant CS as SaleClientService
    participant SOCK as SocketRequestService
    participant RR as RequestRouter
    participant SVC as SaleServiceImpl
    participant REPO as Repository/JPA
    participant DB as MariaDB

    NV->>UI: Mở wizard bán vé
    UI->>CS: findAllStations()
    CS->>SOCK: send(Request(FIND_ALL_STATIONS,"ALL"))
    SOCK->>RR: TCP/ObjectStream Request
    RR->>SVC: findAllStations()
    SVC->>REPO: StationRepositoryImpl.findAllStations(em)
    REPO->>DB: SELECT stations
    DB-->>REPO: rows
    REPO-->>SVC: List<Station>
    SVC-->>RR: Response.success(..., List<StationDTO>)
    RR-->>SOCK: Response.success
    SOCK-->>CS: Response
    CS-->>UI: Response.data: stations
    UI-->>NV: Hiển thị danh sách ga

    NV->>UI: Nhập tiêu chí & tìm chuyến
    UI->>CS: searchSchedulesForSale(SaleScheduleSearchDTO)
    CS->>SOCK: send(Request(SEARCH_SCHEDULES_FOR_SALE, dto))
    SOCK->>RR: TCP/ObjectStream Request
    RR->>SVC: searchSchedulesForSale(dto)
    SVC->>REPO: ScheduleRepositoryImpl.filterSchedules(...)
    REPO->>DB: Query schedules NOT_STARTED
    DB-->>REPO: rows
    REPO-->>SVC: SaleScheduleSearchResultDTO parts
    SVC-->>RR: Response.success(..., SaleScheduleSearchResultDTO)
    RR-->>SOCK: Response.success
    SOCK-->>CS: Response
    CS-->>UI: Response.data: schedules
    UI-->>NV: Chọn chuyến

    NV->>UI: Xem sơ đồ ghế
    UI->>CS: getSeatMap(scheduleId, clientSessionId)
    CS->>SOCK: send(Request(GET_SEATMAP_FOR_SCHEDULE, SeatMapRequestDTO))
    SOCK->>RR: TCP/ObjectStream Request
    RR->>SVC: getSeatMapForSchedule(dto)
    SVC->>REPO: ScheduleDetailRepositoryImpl (load details)
    REPO->>DB: SELECT schedule + details
    DB-->>REPO: rows
    REPO-->>SVC: seat data
    SVC-->>RR: Response.success(..., SeatMapResponseDTO)
    RR-->>SOCK: Response.success
    SOCK-->>CS: Response
    CS-->>UI: Response.data: SeatMapResponseDTO
    UI-->>NV: Hiển thị seatmap

    NV->>UI: Chọn ghế
    UI->>CS: holdSeats(scheduleId, detailIds, clientSessionId)
    CS->>SOCK: send(Request(HOLD_SEATS_FOR_SALE, SeatHoldRequestDTO))
    SOCK->>RR: TCP/ObjectStream Request
    RR->>SVC: holdSeatsForSale(dto)
    SVC->>REPO: TicketRepositoryImpl (check sold)
    REPO->>DB: Query tickets by scheduleDetailId
    DB-->>REPO: rows
    REPO-->>SVC: sold/available
    SVC-->>RR: Response.success(..., SeatHoldResponseDTO)
    RR-->>SOCK: Response.success
    SOCK-->>CS: Response
    CS-->>UI: Response.data: SeatHoldResponseDTO
    UI-->>NV: Giữ chỗ thành công/không thành công
```

```mermaid
sequenceDiagram
    actor NV as Nhân viên
    participant UI as SellTicketWizardController
    participant CS as SaleClientService
    participant SOCK as SocketRequestService
    participant RR as RequestRouter
    participant PAY as PaymentOrderServiceImpl
    participant SVC as SaleServiceImpl
    participant REPO as Repository/JPA
    participant DB as MariaDB
    participant PRINT as Print/Renderer

    NV->>UI: Nhập hành khách/người mua, xem preview
    Note over UI: UI tự tính/hiển thị preview từ dữ liệu seatmap + passenger

    alt Thanh toán online (internal)
        UI->>CS: createPaymentOrder(amount, desc, clientSessionId)
        CS->>SOCK: send(Request(CREATE_PAYMENT_ORDER, PaymentCreateRequestDTO))
        SOCK->>RR: TCP/ObjectStream Request
        RR->>PAY: createPaymentOrder(dto)
        PAY-->>RR: Response.success(..., PaymentCreateResponseDTO)
        RR-->>SOCK: Response.success
        SOCK-->>CS: Response
        CS-->>UI: paymentOrderId

        loop kiểm tra trạng thái
            UI->>CS: getPaymentOrderStatus(paymentOrderId)
            CS->>SOCK: send(Request(GET_PAYMENT_ORDER_STATUS, PaymentStatusRequestDTO))
            SOCK->>RR: TCP/ObjectStream Request
            RR->>PAY: getPaymentOrderStatus(dto)
            PAY-->>RR: Response.success(..., PaymentStatusDTO)
            RR-->>SOCK: Response.success
            SOCK-->>CS: Response
            CS-->>UI: status
        end

        UI->>CS: confirmInternalPayment(paymentOrderId, clientSessionId)
        CS->>SOCK: send(Request(CONFIRM_INTERNAL_PAYMENT, PaymentStatusRequestDTO))
        SOCK->>RR: TCP/ObjectStream Request
        RR->>PAY: confirmPaymentOrder(dto)
        PAY-->>RR: Response.success / Response.error
        RR-->>SOCK: Response
        SOCK-->>CS: Response
        CS-->>UI: Response
    else Thanh toán tiền mặt
        Note over UI: Không gọi payment service
    end

    UI->>CS: createSaleTransaction(SaleCreateRequestDTO)
    CS->>SOCK: send(Request(CREATE_SALE_TRANSACTION, SaleCreateRequestDTO))
    SOCK->>RR: TCP/ObjectStream Request
    RR->>SVC: createSaleTransaction(dto)
    Note over SVC: transactional (tạo Ticket/Invoice/InvoiceDetail, release hold)
    SVC->>REPO: TicketRepositoryImpl / InvoiceRepositoryImpl / InvoiceDetailRepositoryImpl
    REPO->>DB: INSERT/UPDATE
    DB-->>REPO: OK
    REPO-->>SVC: entities
    SVC-->>RR: Response.success(..., SaleCreateResponseDTO)
    RR-->>SOCK: Response.success
    SOCK-->>CS: Response
    CS-->>UI: Response.data: SaleCreateResponseDTO
    UI-->>NV: Hiển thị kết quả bán vé

    opt In vé/hóa đơn
        UI->>PRINT: render(IssuedTicketDTO...)
        PRINT-->>UI: file/preview
        UI-->>NV: In/hiển thị
    end
```

## Notes

- `SellTicketWizardController` hiện không set `SaleCreateRequestDTO.employeeId`; server có thể lưu `Invoice.employee` là `null` trong UC001.
- Không có uncertainty đáng kể khác
