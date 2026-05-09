# Diagrams — UC004C: Tra cứu khách hàng

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
        REPO["CustomerRepositoryImpl"]
    end

    subgraph DB ["MariaDB"]
        T_CUS["customers"]
    end

    UI --> SC
    SC --> OOS
    OOS -- "TCP 9090" --> SRV
    SRV --> RR
    RR --> SVC
    SVC --> REPO
    REPO --> T_CUS
    SRV --> OIS
    OIS --> SC
```

---

## 2. Sequence Diagram

```mermaid
sequenceDiagram
    actor Staff as Nhân viên
    participant UI as CustomerManagementController
    participant Socket as SocketRequestService
    participant Server as Server.java
    participant Router as RequestRouter
    participant Service as CustomerServiceImpl
    participant Repo as CustomerRepositoryImpl
    participant DB as MariaDB

    Staff->>UI: Nhập từ khóa và tìm
    UI->>UI: build CustomerSearchDTO(keyword, page, size)
    UI->>Socket: send(Request SEARCH_CUSTOMERS, CustomerSearchDTO)
    Socket->>Server: writeObject(Request)
    Server->>Router: route(request)
    Router->>Service: searchCustomers(CustomerSearchDTO)
    Service->>Service: ValidationUtils.validate(request)
    Service->>Repo: searchActiveCustomers(keyword, page, size)
    Repo->>DB: SELECT customers where is_active = true
    Service->>Repo: countActiveCustomers(keyword)
    Repo->>DB: SELECT COUNT customers
    Service-->>Router: Response.success(CustomerMessages.SEARCH_SUCCESS, CustomerPageDTO)
    Router-->>Server: Response
    Server-->>Socket: writeObject(Response)
    Socket-->>UI: Response
    UI-->>Staff: Hiển thị bảng và phân trang
```

---

## 3. Class Diagram

```mermaid
classDiagram
    class ActionType {
        <<enum>>
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
    }

    class CustomerSearchDTO {
        <<DTO>>
        +String keyword
        +int page
        +int size
    }

    class CustomerPageDTO {
        <<DTO>>
        +List customers
        +long totalElements
        +int totalPages
        +int currentPage
    }

    class CustomerDTO {
        <<DTO>>
        +String customerId
        +String fullName
        +String idCard
        +String passport
        +String phone
        +int rewardPoints
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

    class CustomerServiceImpl {
        +searchCustomers(CustomerSearchDTO) Response
    }

    class CustomerRepositoryImpl {
        +searchActiveCustomers(em, String, int, int) List~CustomerDTO~
        +countActiveCustomers(em, String) long
    }

    CustomerSearchDTO ..> Request : data
    Request --> Response : socket cycle
    CustomerPageDTO ..> Response : data
    CustomerPageDTO --> CustomerDTO : customers
    CustomerServiceImpl --> CustomerRepositoryImpl
```

---

## Notes
- Không có uncertainty đáng kể.

