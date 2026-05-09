# Diagrams — UC001: Bán vé

---

## 1. System Architecture

```mermaid
graph TB
    subgraph CLIENT ["Client JavaFX"]
        UI1["Step1SaleController"]
        UI2["Step2SeatSelectionController"]
        UI3["Step3PassengerController"]
        UI4["Step4PaymentController"]
        CS["SaleClientService"]
        SC["SocketRequestService"]
    end

    subgraph TRANSPORT ["TCP Socket Transport"]
        OOS["ObjectOutputStream.writeObject(Request)"]
        OIS["ObjectInputStream.readObject Response"]
    end

    subgraph SERVER ["Server Java Socket Server"]
        SRV["Server.java handleClient"]
        RR["RequestRouter.route"]
        SVC["SaleServiceImpl"]
        PAY["PaymentOrderServiceImpl"]
        REPO1["ScheduleRepositoryImpl"]
        REPO2["ScheduleDetailRepositoryImpl"]
        REPO3["TicketRepositoryImpl"]
        REPO4["InvoiceRepositoryImpl"]
        REPO5["InvoiceDetailRepositoryImpl"]
    end

    subgraph DB ["MariaDB"]
        T_ST["stations"]
        T_SCH["schedules"]
        T_SCHD["schedule_details"]
        T_SEAT["seats carriages"]
        T_CUS["customers"]
        T_TK["tickets"]
        T_INV["invoices"]
        T_DET["invoice_details"]
    end

    UI1 --> CS
    UI2 --> CS
    UI3 --> CS
    UI4 --> CS
    CS --> SC
    SC --> OOS
    OOS -- "TCP 9090" --> SRV
    SRV --> RR
    RR --> SVC
    RR --> PAY

    SVC --> T_ST
    REPO1 --> T_SCH
    REPO2 --> T_SCHD
    SVC --> T_SEAT
    SVC --> T_CUS
    REPO3 --> T_TK
    REPO4 --> T_INV
    REPO5 --> T_DET

    SRV --> OIS
    OIS --> SC
    SC --> CS
    CS --> UI4
```

---

## 2. Sequence Diagram

```mermaid
sequenceDiagram
    actor Staff as Nhân viên bán vé
    participant UI1 as Step1SaleController
    participant UI2 as Step2SeatSelectionController
    participant UI3 as Step3PassengerController
    participant UI4 as Step4PaymentController
    participant CS as SaleClientService
    participant Socket as SocketRequestService
    participant Server as Server.java
    participant Router as RequestRouter
    participant Service as SaleServiceImpl
    participant Pay as PaymentOrderServiceImpl
    participant DB as MariaDB

    Staff->>UI1: Mở bán vé
    UI1->>CS: findAllStations()
    CS->>Socket: send(Request FIND_ALL_STATIONS, "ALL")
    Socket->>Server: writeObject(Request)
    Server->>Router: route(request)
    Router->>Service: findAllStations()
    Service->>DB: SELECT stations
    Service-->>Router: Response.success(SaleMessages.STATION_LIST_SUCCESS, List StationDTO)
    Router-->>Server: Response
    Server-->>Socket: writeObject(Response)
    Socket-->>UI1: Response

    Staff->>UI1: Chọn ga ngày loại vé
    UI1->>CS: searchSchedulesForSale(SaleScheduleSearchDTO)
    CS->>Socket: send(Request SEARCH_SCHEDULES_FOR_SALE, dto)
    Socket->>Server: writeObject(Request)
    Server->>Router: route(request)
    Router->>Service: searchSchedulesForSale(dto)
    Service->>DB: SELECT schedules NOT_STARTED
    Service-->>Router: Response.success(SaleMessages.SEARCH_SCHEDULE_SUCCESS, SaleScheduleSearchResultDTO)
    Router-->>Server: Response
    Server-->>Socket: writeObject(Response)
    Socket-->>UI1: Response

    Staff->>UI2: Chọn chuyến và ghế
    UI2->>CS: getSeatMap(scheduleId, clientSessionId)
    CS->>Socket: send(Request GET_SEATMAP_FOR_SCHEDULE, SeatMapRequestDTO)
    Socket->>Server: writeObject(Request)
    Server->>Router: route(request)
    Router->>Service: getSeatMapForSchedule(dto)
    Service->>DB: SELECT schedule_details JOIN seat carriage
    Service-->>Router: Response.success(SaleMessages.SEATMAP_SUCCESS, SeatMapResponseDTO)
    Router-->>Server: Response
    Server-->>Socket: writeObject(Response)
    Socket-->>UI2: Response

    UI2->>CS: holdSeats(scheduleId, scheduleDetailIds, clientSessionId)
    CS->>Socket: send(Request HOLD_SEATS_FOR_SALE, SeatHoldRequestDTO)
    Socket->>Server: writeObject(Request)
    Server->>Router: route(request)
    Router->>Service: holdSeatsForSale(dto)
    Service->>DB: SELECT sold tickets by scheduleDetailId
    Service-->>Router: Response.success(SaleMessages.HOLD_SUCCESS, SeatHoldResponseDTO)
    Router-->>Server: Response
    Server-->>Socket: writeObject(Response)
    Socket-->>UI2: Response

    Staff->>UI3: Nhập hành khách người mua
    UI3->>CS: searchCustomers(CustomerSearchDTO) [optional]
    CS->>Socket: send(Request SEARCH_CUSTOMERS, dto)
    Socket->>Server: writeObject(Request)
    Server->>Router: route(request)
    Router->>Service: (customerService) searchCustomers(dto)
    Service-->>Router: Response.success(CustomerMessages.SEARCH_SUCCESS, CustomerPageDTO)
    Router-->>Server: Response
    Server-->>Socket: writeObject(Response)
    Socket-->>UI3: Response

    alt Thanh toán ONLINE
        UI4->>CS: createPaymentOrder(amount, desc, clientSessionId)
        CS->>Socket: send(Request CREATE_PAYMENT_ORDER, PaymentCreateRequestDTO)
        Socket->>Server: writeObject(Request)
        Server->>Router: route(request)
        Router->>Pay: createPaymentOrder(dto)
        Pay-->>Router: Response.success(PaymentMessages.CREATE_SUCCESS, PaymentCreateResponseDTO)
        Router-->>Server: Response
        Server-->>Socket: writeObject(Response)
        Socket-->>UI4: Response

        UI4->>CS: confirmInternalPayment(paymentOrderId, clientSessionId)
        CS->>Socket: send(Request CONFIRM_INTERNAL_PAYMENT, PaymentStatusRequestDTO)
        Socket->>Server: writeObject(Request)
        Server->>Router: route(request)
        Router->>Pay: confirmPaymentOrder(dto)
        Pay-->>Router: Response.success(PaymentMessages.CONFIRM_SUCCESS, PaymentStatusDTO)
        Router-->>Server: Response
        Server-->>Socket: writeObject(Response)
        Socket-->>UI4: Response
    else Thanh toán CASH
        UI4->>UI4: parseMoney(amountPaid)
    end

    Staff->>UI4: Xác nhận bán vé
    UI4->>CS: createSaleTransaction(SaleCreateRequestDTO)
    CS->>Socket: send(Request CREATE_SALE_TRANSACTION, SaleCreateRequestDTO)
    Socket->>Server: writeObject(Request)
    Server->>Router: route(request)
    Router->>Service: createSaleTransaction(dto)
    note over Service: transactional doCreateSale
    Service->>DB: INSERT tickets
    Service->>DB: INSERT invoices SALE
    Service->>DB: INSERT invoice_details
    Service->>DB: UPDATE customers reward_points
    Service-->>Router: Response.success(SaleMessages.SALE_SUCCESS, SaleCreateResponseDTO)
    Router-->>Server: Response
    Server-->>Socket: writeObject(Response)
    Socket-->>UI4: Response

    UI4-->>Staff: Hiển thị kết quả
    opt In vé hóa đơn
        UI4->>UI4: openPrintListDialog(List IssuedTicketDTO)
    end
```

---

## 3. Class Diagram

```mermaid
classDiagram
    class ActionType {
        <<enum>>
        FIND_ALL_STATIONS
        SEARCH_SCHEDULES_FOR_SALE
        GET_SEATMAP_FOR_SCHEDULE
        HOLD_SEATS_FOR_SALE
        RELEASE_HELD_SEATS_FOR_SALE
        CREATE_PAYMENT_ORDER
        CONFIRM_INTERNAL_PAYMENT
        CREATE_SALE_TRANSACTION
        SEARCH_CUSTOMERS
    }

    class Request {
        <<common>>
        +ActionType action
        +Object data
    }

    class Response {
        <<common>>
        +boolean success
        +String message
        +Object data
        +success(message, data)$
        +error(message)$
    }

    class SaleScheduleSearchDTO {
        <<DTO>>
        +String departureStationId
        +String destinationStationId
        +LocalDate departureDate
        +TicketCategory ticketCategory
        +LocalDate returnDate
        +int page
        +int size
    }

    class SeatMapRequestDTO {
        <<DTO>>
        +String scheduleId
        +String clientSessionId
    }

    class SeatHoldRequestDTO {
        <<DTO>>
        +String scheduleId
        +List scheduleDetailIds
        +String clientSessionId
    }

    class SalePassengerDTO {
        <<DTO>>
        +String passengerName
        +DocumentType documentType
        +String documentNumber
        +TicketType ticketType
        +LocalDate dateOfBirth
        +boolean studentCardVerified
    }

    class SaleBuyerDTO {
        <<DTO>>
        +String buyerName
        +DocumentType documentType
        +String documentNumber
        +String buyerEmail
        +String buyerPhone
        +boolean hasAccount
        +String customerId
    }

    class SaleChildUnder6DTO {
        <<DTO>>
        +String childName
        +LocalDate dateOfBirth
        +TripDirection accompanyDirection
        +int accompanyPassengerIndex
    }

    class SaleRedeemPointsDTO {
        <<DTO>>
        +boolean redeemRequested
        +int pointsToRedeem
    }

    class SaleCreateRequestDTO {
        <<DTO>>
        +String clientSessionId
        +TicketCategory ticketCategory
        +String outboundScheduleId
        +String returnScheduleId
        +List outboundScheduleDetailIds
        +List returnScheduleDetailIds
        +List outboundPassengers
        +List returnPassengers
        +List childrenUnder6
        +SaleBuyerDTO buyer
        +SaleRedeemPointsDTO redeemPoints
        +PaymentMethod paymentMethod
        +Double amountPaid
        +String paymentOrderId
        +String employeeId
    }

    class SaleCreateResponseDTO {
        <<DTO>>
        +String invoiceId
        +LocalDateTime invoiceDate
        +double totalAmount
        +double amountPaid
        +double changeAmount
        +int earnedPoints
        +int redeemedPoints
        +List tickets
        +List childVouchers
    }

    class IssuedTicketDTO {
        <<DTO>>
        +String ticketId
        +String passengerName
        +String passengerDocument
        +String scheduleId
        +String trainCode
        +LocalDateTime departureTime
        +String carriageName
        +String seatNumber
        +TicketType ticketType
        +double price
        +String qrCode
    }

    class Customer {
        <<entity>>
        +String id
        +String name
        +String idCard
        +String passport
        +boolean isActive
        +int rewardPoints
    }

    class Employee {
        <<entity>>
        +String id
        +String employeeCode
        +String employeeName
        +Boolean isManager
    }

    class Ticket {
        <<entity>>
        +String id
        +TicketType type
        +TicketStatus status
        +String qrCode
        +String passengerName
        +String passengerIdCard
    }

    class Invoice {
        <<entity>>
        +String id
        +LocalDateTime issueDate
        +double totalAmount
        +InvoiceType type
    }

    class InvoiceDetail {
        <<entity>>
        +String id
        +Double subTotal
        +double discount
        +double insurance
        +boolean isReturned
        +double refundAmount
    }

    class Schedule {
        <<entity>>
        +String id
        +LocalDateTime departureTime
        +LocalDateTime arrivalTime
        +StatusSchedule status
    }

    class ScheduleDetail {
        <<entity>>
        +String id
        +BigDecimal priceSeat
        +int version
    }

    class Seat {
        <<entity>>
        +String id
        +int number
        +SeatType type
    }

    Customer "1" --> "N" Ticket : tickets
    Ticket "N" --> "1" Customer : customer
    Ticket "N" --> "1" ScheduleDetail : scheduleDetail
    ScheduleDetail "N" --> "1" Schedule : schedule
    ScheduleDetail "N" --> "1" Seat : seat
    Invoice "N" --> "1" Customer : customer
    Invoice "N" --> "1" Employee : employee
    Invoice "1" --> "N" InvoiceDetail : details
    InvoiceDetail "N" --> "1" Ticket : ticket

    SaleCreateRequestDTO ..> Request : data
    Request --> Response : socket cycle
    ActionType ..> Request : action
    SaleCreateResponseDTO ..> Response : data
```

---

## Notes
- Không có uncertainty đáng kể.

