# Diagrams — UC004E: Xóa mềm khách hàng

---

## 1. System Architecture

```mermaid
graph TB
    subgraph CLIENT ["Client JavaFX"]
        UI["CustomerManagementController"]
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
        CRepo["CustomerRepositoryImpl"]
        ERepo["EmployeeRepositoryImpl"]
    end

    subgraph DB ["MariaDB"]
        T_EMP["employees"]
        T_CUS["customers"]
        T_TK["tickets"]
        T_SCHD["schedule_details"]
        T_SCH["schedules"]
    end

    UI --> SC
    SC --> OOS
    OOS -- "TCP 9090" --> SRV
    SRV --> RR
    RR --> SVC
    SVC --> ERepo
    SVC --> CRepo
    ERepo --> T_EMP
    CRepo --> T_CUS
    CRepo --> T_TK
    CRepo --> T_SCHD
    CRepo --> T_SCH
    SRV --> OIS
    OIS --> SC
```

---

## 2. Sequence Diagram

```mermaid
sequenceDiagram
    actor Manager as Nhân viên quản lý
    participant UI as CustomerManagementController
    participant Socket as SocketRequestService
    participant Server as Server.java
    participant Router as RequestRouter
    participant Service as CustomerServiceImpl
    participant EmpRepo as EmployeeRepositoryImpl
    participant CusRepo as CustomerRepositoryImpl
    participant DB as MariaDB

    Manager->>UI: Chọn khách và nhấn Xóa
    UI->>UI: build CustomerDeleteRequestDTO(customerId, requestEmployeeId)
    UI->>Socket: send(Request DELETE_CUSTOMER, CustomerDeleteRequestDTO)
    Socket->>Server: writeObject(Request)
    Server->>Router: route(request)
    Router->>Service: deleteCustomer(CustomerDeleteRequestDTO)
    Service->>Service: ValidationUtils.validate(dto)
    Service->>EmpRepo: findEmployeeById(requestEmployeeId)
    EmpRepo->>DB: SELECT employees
    alt không có quyền
        Service-->>Router: Response.error(CustomerMessages.MANAGER_ONLY)
    else đủ quyền
        Service->>CusRepo: findCustomerById(customerId)
        CusRepo->>DB: SELECT customers
        Service->>CusRepo: hasUpcomingPaidTicket(customerId, now)
        CusRepo->>DB: SELECT tickets PAID and schedules in future
        alt có vé sắp chạy
            Service-->>Router: Response.error(CustomerMessages.CUSTOMER_HAS_UPCOMING_TICKET)
        else không có vé sắp chạy
            Service->>CusRepo: updateCustomer(active=false)
            CusRepo->>DB: UPDATE customers set is_active = false
            Service-->>Router: Response.success(CustomerMessages.DELETE_SUCCESS, CustomerDTO)
        end
    end
    Router-->>Server: Response
    Server-->>Socket: writeObject(Response)
    Socket-->>UI: Response
    UI-->>Manager: Hiển thị kết quả và refresh danh sách
```

---

## 3. Class Diagram

```mermaid
classDiagram
    class ActionType {
        <<enum>>
        DELETE_CUSTOMER
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

    class CustomerDeleteRequestDTO {
        <<DTO>>
        +String customerId
        +String requestEmployeeId
    }

    class CustomerDTO {
        <<DTO>>
        +String customerId
        +String fullName
        +Boolean isActive
    }

    class Customer {
        <<entity>>
        +String id
        +boolean isActive
    }

    class Employee {
        <<entity>>
        +String id
        +EmployeeStatus employeeStatus
        +Boolean isManager
    }

    class Ticket {
        <<entity>>
        +String id
        +TicketStatus status
    }

    class CustomerServiceImpl {
        +deleteCustomer(CustomerDeleteRequestDTO) Response
    }

    class CustomerRepositoryImpl {
        +findCustomerById(em, String) Customer
        +hasUpcomingPaidTicket(em, String, LocalDateTime) boolean
        +updateCustomer(em, Customer)
    }

    class EmployeeRepositoryImpl {
        +findEmployeeById(em, String) Employee
    }

    CustomerDeleteRequestDTO ..> Request : data
    Request --> Response : socket cycle
    CustomerServiceImpl --> CustomerRepositoryImpl
    CustomerServiceImpl --> EmployeeRepositoryImpl
    Customer "1" --> "N" Ticket : tickets
```

---

## Notes
- Không có uncertainty đáng kể.

