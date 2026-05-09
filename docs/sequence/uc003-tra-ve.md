# Diagrams — UC003: Trả vé

---

## 1. System Architecture

```mermaid
graph TB
    subgraph CLIENT ["Client JavaFX"]
        UI["ReturnTicketController"]
        CS["ReturnTicketClientService"]
        SC["SocketRequestService"]
        PR["Jasper Print Preview"]
    end

    subgraph TRANSPORT ["TCP Socket Transport"]
        OOS["ObjectOutputStream.writeObject(Request)"]
        OIS["ObjectInputStream.readObject Response"]
    end

    subgraph SERVER ["Server Java Socket Server"]
        SRV["Server.java handleClient"]
        RR["RequestRouter.route"]
        SVC["TicketServiceImpl"]
        REPO1["TicketRepositoryImpl"]
        REPO2["InvoiceRepositoryImpl"]
        REPO3["InvoiceDetailRepositoryImpl"]
        REPO4["EmployeeRepositoryImpl"]
    end

    subgraph DB ["MariaDB"]
        T_TK["tickets"]
        T_SCHD["schedule_details"]
        T_SCH["schedules"]
        T_SEAT["seats carriages"]
        T_CUS["customers"]
        T_INV["invoices"]
        T_DET["invoice_details"]
        T_EMP["employees"]
    end

    UI --> CS
    CS --> SC
    SC --> OOS
    OOS -- "TCP 9090" --> SRV
    SRV --> RR
    RR --> SVC

    SVC --> T_TK
    SVC --> T_INV
    SVC --> T_DET
    SVC --> T_EMP
    SVC --> T_CUS
    SVC --> T_SCHD
    SVC --> T_SCH
    SVC --> T_SEAT

    SRV --> OIS
    OIS --> SC
    SC --> UI
    UI --> PR
```

---

## 2. Sequence Diagram

```mermaid
sequenceDiagram
    actor Staff as Nhân viên bán vé
    participant UI as ReturnTicketController
    participant CS as ReturnTicketClientService
    participant Socket as SocketRequestService
    participant Server as Server.java
    participant Router as RequestRouter
    participant Service as TicketServiceImpl
    participant DB as MariaDB
    participant Print as Jasper Preview

    Staff->>UI: Mở trả vé
    UI->>CS: searchTicketsForReturn(query)
    CS->>Socket: send(Request SEARCH_TICKETS_FOR_RETURN, ReturnTicketSearchDTO)
    Socket->>Server: writeObject(Request)
    Server->>Router: route(request)
    Router->>Service: searchTicketsForReturn(searchDTO)
    Service->>DB: SELECT tickets status PAID
    Service-->>Router: Response.success(TicketMessages.FIND_SUCCESS, List ReturnTicketTicketDTO)
    Router-->>Server: Response
    Server-->>Socket: writeObject(Response)
    Socket-->>UI: Response

    Staff->>UI: Chọn vé cần trả
    UI->>CS: previewReturnTickets(ticketIds)
    CS->>Socket: send(Request PREVIEW_RETURN_TICKETS, ReturnTicketPreviewRequestDTO)
    Socket->>Server: writeObject(Request)
    Server->>Router: route(request)
    Router->>Service: previewReturnTickets(previewRequestDTO)
    Service->>DB: SELECT tickets by ids with schedule
    Service->>DB: SELECT actual paid amount by ticket
    Service-->>Router: Response.success(TicketMessages.PREVIEW_SUCCESS, ReturnTicketPreviewDTO)
    Router-->>Server: Response
    Server-->>Socket: writeObject(Response)
    Socket-->>UI: Response

    Staff->>UI: Xác nhận trả vé
    UI->>CS: confirmReturnTickets(ticketIds, refundAmount, employeeId)
    CS->>Socket: send(Request CONFIRM_RETURN_TICKETS, ReturnTicketConfirmDTO)
    Socket->>Server: writeObject(Request)
    Server->>Router: route(request)
    Router->>Service: confirmReturnTickets(confirmDTO)
    note over Service: transactional doConfirmReturnTickets
    Service->>DB: INSERT invoices REFUND
    Service->>DB: INSERT invoice_details refundAmount
    Service->>DB: UPDATE invoice_details SALE set returned true
    Service->>DB: UPDATE tickets set RETURNED and qr INVALID
    Service->>DB: UPDATE customers reward_points revoke
    Service-->>Router: Response.success(TicketMessages.RETURN_SUCCESS, refundInvoiceId)
    Router-->>Server: Response
    Server-->>Socket: writeObject(Response)
    Socket-->>UI: Response

    opt In biên lai hoàn tiền
        UI->>CS: getRefundReceipt(refundInvoiceId)
        CS->>Socket: send(Request GET_REFUND_RECEIPT, RefundReceiptRequestDTO)
        Socket->>Server: writeObject(Request)
        Server->>Router: route(request)
        Router->>Service: getRefundReceipt(requestDTO)
        Service->>DB: SELECT invoices REFUND JOIN details tickets schedule
        Service-->>Router: Response.success(msg, RefundReceiptDTO)
        Router-->>Server: Response
        Server-->>Socket: writeObject(Response)
        Socket-->>UI: Response
        UI->>Print: render bien-lai-tra-ve.xml
        Print-->>Staff: Preview và in
    end
```

---

## 3. Class Diagram

```mermaid
classDiagram
    class ActionType {
        <<enum>>
        SEARCH_TICKETS_FOR_RETURN
        PREVIEW_RETURN_TICKETS
        CONFIRM_RETURN_TICKETS
        GET_REFUND_RECEIPT
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
    }

    class ReturnTicketSearchDTO {
        <<DTO>>
        +String query
        +ReturnTicketSearchType queryType
    }

    class ReturnTicketTicketDTO {
        <<DTO>>
        +String id
        +LocalDateTime departureTime
        +String trainCode
        +String departureStation
        +String destinationStation
        +String carriageName
        +String seatNumber
        +double ticketPrice
        +TicketStatus status
        +String originalTicketId
        +String passengerName
        +String passengerIdCard
    }

    class ReturnTicketPreviewRequestDTO {
        <<DTO>>
        +List ticketIds
    }

    class ReturnTicketPreviewDTO {
        <<DTO>>
        +double totalTicketPrice
        +double refundFee
        +double refundAmount
    }

    class ReturnTicketConfirmDTO {
        <<DTO>>
        +List ticketIds
        +double refundAmount
        +String employeeId
    }

    class RefundReceiptRequestDTO {
        <<DTO>>
        +String refundInvoiceId
    }

    class RefundReceiptDTO {
        <<DTO>>
        +String refundInvoiceId
        +LocalDateTime refundDate
        +String employeeName
        +List items
        +double totalOriginalAmount
        +double totalRefundFee
        +double totalRefundAmount
    }

    class Ticket {
        <<entity>>
        +String id
        +TicketStatus status
        +String qrCode
        +String originalTicketId
        +boolean exchanged
    }

    class Invoice {
        <<entity>>
        +String id
        +InvoiceType type
        +LocalDateTime issueDate
        +double totalAmount
    }

    class InvoiceDetail {
        <<entity>>
        +String id
        +Double subTotal
        +boolean isReturned
        +double refundAmount
    }

    class TicketStatus {
        <<enum>>
        PAID
        RETURNED
        EXCHANGED
    }

    class InvoiceType {
        <<enum>>
        REFUND
        SALE
    }

    Invoice "1" --> "N" InvoiceDetail : details
    InvoiceDetail "N" --> "1" Ticket : ticket
    Ticket --> TicketStatus : status
    Invoice --> InvoiceType : type

    ReturnTicketSearchDTO ..> Request : data
    ReturnTicketPreviewRequestDTO ..> Request : data
    ReturnTicketConfirmDTO ..> Request : data
    RefundReceiptRequestDTO ..> Request : data
    Request --> Response : socket cycle
    RefundReceiptDTO ..> Response : data
```

---

## Notes
- Không có uncertainty đáng kể.

