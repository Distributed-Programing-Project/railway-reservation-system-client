# Diagrams: UC004B Cập nhật khách hàng

> Source use case: `docs/usecases/uc004b-cap-nhat-khach-hang.md`  
> Distributed Java system — JavaFX Client/Server over TCP Socket

## Activity Diagram

```mermaid
flowchart TD
    Start([Start]) --> A1[Chọn khách hàng & mở dialog sửa]
    A1 --> A2[Cập nhật thông tin]
    A2 --> D1{Validate client OK?}
    D1 -- Không --> E1[Hiển thị lỗi nhập liệu] --> A2
    D1 -- Có --> A3[Gửi cập nhật khách hàng]
    A3 --> D2{Tồn tại & active?}
    D2 -- Không --> E2[Thông báo không tìm thấy/inactive] --> End([End])
    D2 -- Có --> D3{Trùng CCCD/email?}
    D3 -- Có --> E3[Thông báo duplicate] --> A2
    D3 -- Không --> A4[Lưu cập nhật]
    A4 --> A5[Reload danh sách]
    A5 --> End
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

    NV->>UI: Sửa thông tin, nhấn Lưu
    UI->>SOCK: send(Request(UPDATE_CUSTOMER, CustomerDTO{customerId}))
    SOCK->>RR: TCP/ObjectStream Request
    RR->>SVC: updateCustomer(CustomerDTO)
    SVC->>REPO: findCustomerById(em, customerId)
    REPO->>DB: SELECT customer
    DB-->>REPO: row/null
    alt Không tồn tại / inactive
        SVC-->>RR: Response.error(...)
        RR-->>SOCK: Response.error
        SOCK-->>UI: Response.error
        UI-->>NV: Hiển thị lỗi
    else OK
        SVC->>REPO: existsByIdCard(em, idCard, existingId)
        REPO->>DB: SELECT duplicate
        DB-->>REPO: result
        alt Trùng dữ liệu
            SVC-->>RR: Response.error(...)
            RR-->>SOCK: Response.error
            SOCK-->>UI: Response.error
            UI-->>NV: Hiển thị lỗi duplicate
        else Không trùng
            SVC->>REPO: updateCustomer(em, existing)
            REPO->>DB: UPDATE customers
            DB-->>REPO: OK
            REPO-->>SVC: Customer
            SVC-->>RR: Response.success(..., CustomerDTO)
            RR-->>SOCK: Response.success
            SOCK-->>UI: Response.success
            UI-->>NV: Thông báo thành công + reload
        end
    end
```

## Notes
- Theo usecase doc: `CustomerServiceImpl.updateCustomer` không thấy set `passport` (có thể partial nếu cần cập nhật hộ chiếu).
- Không có uncertainty đáng kể khác

