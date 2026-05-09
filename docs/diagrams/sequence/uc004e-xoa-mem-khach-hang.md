# Diagrams: UC004E Xóa mềm khách hàng

> Source use case: `docs/usecases/uc004e-xoa-mem-khach-hang.md`  
> Distributed Java system — JavaFX Client/Server over TCP Socket

## Activity Diagram

```mermaid
flowchart TD
    Start([Start]) --> A1[Chọn khách hàng & nhấn Xóa/Vô hiệu hóa]
    A1 --> A2[Kiểm tra quyền quản lý]
    A2 --> D1{isManager=true?}
    D1 -- Không --> E1[Thông báo không đủ quyền] --> End([End])
    D1 -- Có --> A3[Kiểm tra khách tồn tại]
    A3 --> D2{Tồn tại?}
    D2 -- Không --> E2[Thông báo không tìm thấy] --> End
    D2 -- Có --> A4[Check vé PAID sắp khởi hành]
    A4 --> D3{Có vé sắp chạy?}
    D3 -- Có --> E3[Chặn xóa: có vé sắp chạy] --> End
    D3 -- Không --> D4{Có phát sinh vé/hóa đơn?}
    D4 -- Có --> A5[Soft delete: set isActive=false]
    D4 -- Không --> A6[Hard delete: xóa bản ghi]
    A5 --> A7[Reload danh sách] --> End
    A6 --> A7 --> End
```

## Sequence Diagram

```mermaid
sequenceDiagram
    actor NV as Nhân viên
    participant UI as CustomerManagementController
    participant SOCK as SocketRequestService
    participant RR as RequestRouter
    participant SVC as CustomerServiceImpl
    participant REPO as CustomerRepositoryImpl
    participant DB as MariaDB

    NV->>UI: Nhấn Xóa/Vô hiệu hóa
    UI->>SOCK: send(Request(DELETE_CUSTOMER, CustomerDeleteRequestDTO))
    SOCK->>RR: TCP/ObjectStream Request
    RR->>SVC: deleteCustomer(CustomerDeleteRequestDTO)
    SVC->>REPO: EmployeeRepositoryImpl.findEmployeeById(...)
    REPO->>DB: SELECT employee
    DB-->>REPO: row/null
    alt Không tìm thấy / không active / không phải manager
        SVC-->>RR: Response.error(...)
        RR-->>SOCK: Response.error
        SOCK-->>UI: Response.error
        UI-->>NV: Hiển thị lỗi quyền
    else OK
        SVC->>REPO: hasUpcomingPaidTicket(em, customerId, now)
        REPO->>DB: Query upcoming PAID tickets
        DB-->>REPO: boolean
        alt Có vé sắp khởi hành
            SVC-->>RR: Response.error(CustomerMessages.CUSTOMER_HAS_UPCOMING_TICKET)
            RR-->>SOCK: Response.error
            SOCK-->>UI: Response.error
            UI-->>NV: Hiển thị lỗi chặn xóa
        else Không có vé sắp chạy
            SVC->>REPO: hasAnyTicket(...) / hasAnyInvoice(...)
            REPO->>DB: Query existence
            DB-->>REPO: boolean
            alt Có phát sinh dữ liệu
                SVC->>REPO: updateCustomer(em, setActive(false))
                REPO->>DB: UPDATE customers isActive=false
                DB-->>REPO: OK
                SVC-->>RR: Response.success(..., CustomerDTO)
            else Không phát sinh dữ liệu
                SVC->>REPO: deleteCustomer(em, customer)
                REPO->>DB: DELETE customers
                DB-->>REPO: OK
                SVC-->>RR: Response.success(..., customerId)
            end
            RR-->>SOCK: Response.success
            SOCK-->>UI: Response.success
            UI-->>NV: Thông báo + reload danh sách
        end
    end
```

## Notes
- Server có nhánh hard delete khi khách chưa phát sinh vé/hóa đơn; nếu nghiệp vụ yêu cầu luôn soft delete thì hiện tại khác (different) theo usecase doc.

