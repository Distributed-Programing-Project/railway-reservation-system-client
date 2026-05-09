# Diagrams — UC004B: Cập nhật khách hàng

---

## 1. System Architecture

```mermaid
graph TB
    subgraph CLIENT ["Client JavaFX"]
        UI1["CustomerManagementController"]
        UI2["KhachHangDialogController"]
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

    UI1 --> UI2
    UI2 --> SC
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
    participant UI1 as CustomerManagementController
    participant UI2 as KhachHangDialogController
    participant Socket as SocketRequestService
    participant Server as Server.java
    participant Router as RequestRouter
    participant Service as CustomerServiceImpl
    participant Repo as CustomerRepositoryImpl
    participant DB as MariaDB

    Staff->>UI1: Chọn khách và nhấn Sửa
    UI1->>UI2: openEditCustomerDialog()
    Staff->>UI2: Chỉnh sửa và Lưu
    UI2->>UI2: build CustomerDTO with customerId
    UI2->>Socket: send(Request UPDATE_CUSTOMER, CustomerDTO)
    Socket->>Server: writeObject(Request)
    Server->>Router: route(request)
    Router->>Service: updateCustomer(CustomerDTO)
    Service->>Service: ValidationUtils.validate(customerDTO)
    alt thiếu customerId
        Service-->>Router: Response.error(CustomerMessages.CUSTOMER_ID_REQUIRED)
    else có customerId
        Service->>Repo: findCustomerById(customerId)
        Repo->>DB: SELECT customers by customer_id
        alt không tồn tại
            Service-->>Router: Response.error(CustomerMessages.customerNotFound(id))
        else inactive
            Service-->>Router: Response.error(CustomerMessages.customerInactive(id))
        else hợp lệ
            Service->>Repo: existsByIdCard(idCard, excludeId)
            Repo->>DB: SELECT customers by id_card
            alt trùng giấy tờ
                Service-->>Router: Response.error(CustomerMessages.ID_CARD_DUPLICATE)
            else không trùng
                Service->>Repo: updateCustomer(Customer)
                Repo->>DB: UPDATE customers
                Service-->>Router: Response.success(CustomerMessages.UPDATE_SUCCESS, CustomerDTO)
            end
        end
    end
    Router-->>Server: Response
    Server-->>Socket: writeObject(Response)
    Socket-->>UI2: Response
    UI2-->>Staff: Thông báo và đóng dialog
    UI2-->>UI1: callback reload danh sách
```

---

## 3. Class Diagram

```mermaid
classDiagram
    class ActionType {
        <<enum>>
        UPDATE_CUSTOMER
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

    class CustomerDTO {
        <<DTO>>
        +String customerId
        +String fullName
        +String idCard
        +String passport
        +String phone
        +String email
    }

    class Customer {
        <<entity>>
        +String id
        +String name
        +String idCard
        +String passport
        +String phoneNumber
        +String email
        +boolean isActive
    }

    class CustomerServiceImpl {
        +updateCustomer(CustomerDTO) Response
    }

    class CustomerRepositoryImpl {
        +findCustomerById(em, String) Customer
        +updateCustomer(em, Customer)
        +existsByIdCard(em, String, String)
        +existsByPassport(em, String, String)
        +existsByEmail(em, String, String)
    }

    CustomerDTO ..> Request : data
    Request --> Response : socket cycle
    CustomerServiceImpl --> CustomerRepositoryImpl
    CustomerRepositoryImpl --> Customer
```

---

## Notes
- Không có uncertainty đáng kể.

