# Diagrams — UC004D: Xem lịch sử khách hàng

---

## 1. System Architecture

```mermaid
graph TB
    subgraph CLIENT ["Client JavaFX"]
        UI1["CustomerManagementController"]
        UI2["CustomerHistoryController"]
        SC["SocketRequestService"]
    end

    subgraph TRANSPORT ["TCP Socket Transport"]
        OOS["ObjectOutputStream.writeObject(Request)"]
        OIS["ObjectInputStream.readObject Response"]
    end

    subgraph SERVER ["Server Java Socket Server"]
        SRV["Server.java handleClient"]
        RR["RequestRouter.route"]
        SVC["CustomerServiceImpl"]
        REPO["CustomerRepositoryImpl"]
    end

    subgraph DB ["MariaDB"]
        T_CUS["customers"]
        T_TK["tickets"]
        T_SCHD["schedule_details"]
        T_SCH["schedules"]
        T_INV["invoices"]
        T_DET["invoice_details"]
    end

    UI1 --> UI2
    UI2 --> SC
    SC --> OOS
    OOS -- "TCP 9090" --> SRV
    SRV --> RR
    RR --> SVC
    SVC --> REPO
    REPO --> T_CUS
    REPO --> T_TK
    REPO --> T_SCHD
    REPO --> T_SCH
    REPO --> T_INV
    REPO --> T_DET
    SRV --> OIS
    OIS --> SC
```

---

## 2. Sequence Diagram

```mermaid
sequenceDiagram
    actor Staff as Nhân viên
    participant UI1 as CustomerManagementController
    participant UI2 as CustomerHistoryController
    participant Socket as SocketRequestService
    participant Server as Server.java
    participant Router as RequestRouter
    participant Service as CustomerServiceImpl
    participant Repo as CustomerRepositoryImpl
    participant DB as MariaDB

    Staff->>UI1: Chọn khách và xem lịch sử
    UI1->>UI2: openCustomerHistory()
    UI2->>UI2: build CustomerHistoryRequestDTO(customerId)
    UI2->>Socket: send(Request GET_CUSTOMER_HISTORY, dto)
    Socket->>Server: writeObject(Request)
    Server->>Router: route(request)
    Router->>Service: getCustomerHistory(CustomerHistoryRequestDTO)
    Service->>Service: ValidationUtils.validate(dto)
    Service->>Repo: findCustomerTicketHistory(customerId)
    Repo->>DB: SELECT ticket history join schedule
    Service->>Repo: sumCustomerInvoiceTotalAmount(customerId)
    Repo->>DB: SELECT SUM invoices totalAmount
    Service-->>Router: Response.success(msg, CustomerHistoryResponseDTO)
    Router-->>Server: Response
    Server-->>Socket: writeObject(Response)
    Socket-->>UI2: Response
    UI2-->>Staff: Hiển thị bảng lịch sử
```

---

## 3. Class Diagram

```mermaid
classDiagram
    class ActionType {
        <<enum>>
        GET_CUSTOMER_HISTORY
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

    class CustomerHistoryRequestDTO {
        <<DTO>>
        +String customerId
    }

    class CustomerHistoryResponseDTO {
        <<DTO>>
        +List items
        +double totalAmount
    }

    class CustomerHistoryItemDTO {
        <<DTO>>
        +LocalDateTime purchaseTime
        +String ticketId
        +String trainCode
        +LocalDateTime departureTime
        +LocalDateTime arrivalTime
        +Integer carriageNumber
        +SeatType seatType
        +Integer seatNumber
        +double ticketPrice
    }

    class Customer {
        <<entity>>
        +String id
        +String name
        +int rewardPoints
    }

    class Ticket {
        <<entity>>
        +String id
        +Customer customer
        +ScheduleDetail scheduleDetail
    }

    class Invoice {
        <<entity>>
        +String id
        +Customer customer
        +double totalAmount
    }

    class CustomerServiceImpl {
        +getCustomerHistory(CustomerHistoryRequestDTO) Response
    }

    class CustomerRepositoryImpl {
        +findCustomerTicketHistory(em, String) List~CustomerHistoryItemDTO~
        +sumCustomerInvoiceTotalAmount(em, String) double
    }

    CustomerHistoryRequestDTO ..> Request : data
    Request --> Response : socket cycle
    CustomerHistoryResponseDTO ..> Response : data
    CustomerHistoryResponseDTO --> CustomerHistoryItemDTO : items
    Customer "1" --> "N" Ticket : tickets
    Customer "1" --> "N" Invoice : invoices
    CustomerServiceImpl --> CustomerRepositoryImpl
```

---

## Notes
- Không có uncertainty đáng kể.

