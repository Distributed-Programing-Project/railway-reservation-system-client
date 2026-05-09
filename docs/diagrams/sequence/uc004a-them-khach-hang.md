# Diagrams: UC004A Thêm khách hàng

> Source use case: `docs/usecases/uc004a-them-khach-hang.md`  
> Distributed Java system — JavaFX Client/Server over TCP Socket

## Activity Diagram

```mermaid
flowchart TD
    Start([Start]) --> A1[Mở dialog thêm khách hàng]
    A1 --> A2[Nhập họ tên + CCCD hoặc hộ chiếu + (SĐT/email tùy chọn)]
    A2 --> D1{Validate client OK?}
    D1 -- Không --> E1[Hiển thị lỗi nhập liệu] --> A2
    D1 -- Có --> A3[Gửi tạo khách hàng]
    A3 --> D2{Server validate OK?}
    D2 -- Không --> E2[Hiển thị lỗi dữ liệu/duplicate] --> A2
    D2 -- Có --> A4[Lưu Customer isActive=true]
    A4 --> A5[Đóng dialog & reload danh sách]
    A5 --> End([End])
```

## Sequence Diagram

```mermaid
sequenceDiagram
    actor NV as Nhân viên
    participant UI as KhachHangDialogController
    participant SOCK as SocketRequestService
    participant RR as RequestRouter
    participant SVC as CustomerServiceImpl
    participant REPO as CustomerRepositoryImpl
    participant DB as MariaDB

    NV->>UI: Nhập thông tin, nhấn Lưu
    UI->>SOCK: send(Request(CREATE_CUSTOMER, CustomerDTO{customerId=null}))
    SOCK->>RR: TCP/ObjectStream Request
    RR->>SVC: createCustomer(CustomerDTO)
    SVC->>REPO: existsByIdCard(em, idCard, null)
    REPO->>DB: SELECT duplicate idCard
    DB-->>REPO: result
    alt Trùng CCCD/email hoặc dữ liệu sai
        SVC-->>RR: Response.error(...)
        RR-->>SOCK: Response.error
        SOCK-->>UI: Response.error
        UI-->>NV: Hiển thị lỗi
    else OK
        SVC->>REPO: createCustomer(em, Customer)
        REPO->>DB: INSERT customers
        DB-->>REPO: OK
        REPO-->>SVC: Customer
        SVC-->>RR: Response.success(..., CustomerDTO)
        RR-->>SOCK: Response.success
        SOCK-->>UI: Response.success
        UI-->>NV: Thông báo thành công + reload
    end
```

## Notes
- Không có uncertainty đáng kể

