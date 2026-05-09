# Use case UC004E: Xóa mềm khách hàng

## Actor
- **Primary:** Nhân viên quản lý (`Employee` với `isManager = true`)
- **Secondary:** —
- **System:** JavaFX Client → TCP Socket Server → MariaDB

## Tiền điều kiện
- Nhân viên đang đăng nhập trên client và có `employeeId` trong `ClientSessionContext`.
- Khách hàng tồn tại trong DB.

## Hậu điều kiện (khi thành công)
- Nếu khách hàng đã phát sinh vé hoặc hóa đơn:
  - Server “xóa mềm” bằng cách set `Customer.isActive = false` và update DB.
  - Client nhận `CustomerDTO` (đã inactive) hoặc reload danh sách.
- Nếu khách hàng **chưa** phát sinh vé và **chưa** có hóa đơn:
  - Server có thể xóa cứng (`customerRepository.deleteCustomer(...)`) và trả về `customerId`.

---

## Luồng chính
1. Client chọn khách hàng và nhấn Xóa/Vô hiệu hóa (client: `CustomerManagementController.handleDeleteCustomer`).
2. Client lấy `employeeId = ClientSessionContext.getInstance().getEmployeeId()`.
3. Client gửi `Request(ActionType.DELETE_CUSTOMER, CustomerDeleteRequestDTO{customerId, requestEmployeeId})`.
4. Server (`CustomerServiceImpl.deleteCustomer`):
   - Validate DTO (`ValidationUtils.validate`), lỗi trả `Response.error(CustomerMessages.DATA_INVALID_PREFIX + ...)`.
   - Kiểm tra người yêu cầu:
     - `EmployeeRepository.findEmployeeById(requestEmployeeId)`:
       - null ⇒ `CustomerMessages.requestEmployeeNotFound(id)`.
       - `employeeStatus != EmployeeStatus.ACTIVE` ⇒ `CustomerMessages.requestEmployeeInactive(id)`.
       - `!Boolean.TRUE.equals(requester.getIsManager())` ⇒ `CustomerMessages.MANAGER_ONLY`.
   - Load khách hàng:
     - null ⇒ `CustomerMessages.customerNotFound(customerId)`.
   - Chặn xóa nếu có vé sắp khởi hành:
     - `customerRepository.hasUpcomingPaidTicket(em, customerId, LocalDateTime.now())` ⇒ `CustomerMessages.CUSTOMER_HAS_UPCOMING_TICKET`.
   - Quyết định xóa mềm/xóa cứng:
     - Nếu `hasAnyTicket(customerId)` hoặc `hasAnyInvoice(customerId)`:
       - set `customer.setActive(false)` và `customerRepository.updateCustomer(...)`.
       - trả `Response.success(CustomerMessages.DELETE_SUCCESS, CustomerDTO)`.
     - Ngược lại:
       - `customerRepository.deleteCustomer(...)`.
       - trả `Response.success(CustomerMessages.DELETE_SUCCESS, customerId)`.
5. Client hiển thị message và reload danh sách khách hàng (client: `loadCustomers(...)`).

---

## Luồng thay thế
- **[Không đủ quyền]:** server trả `CustomerMessages.MANAGER_ONLY` (client hiển thị alert).

---

## Luồng lỗi tiêu biểu (tham chiếu constant)
- `CustomerMessages.DATA_INVALID_PREFIX + ...`: thiếu `customerId` hoặc `requestEmployeeId`.
- `CustomerMessages.requestEmployeeNotFound(id)`: không tìm thấy nhân viên yêu cầu.
- `CustomerMessages.requestEmployeeInactive(id)`: nhân viên không active.
- `CustomerMessages.MANAGER_ONLY`: không phải quản lý.
- `CustomerMessages.customerNotFound(id)`: không tìm thấy khách hàng.
- `CustomerMessages.CUSTOMER_HAS_UPCOMING_TICKET`: khách đang có vé `PAID` sắp chạy (cutoff cụ thể nằm ở repository ⇒ uncertainty).
- `CustomerMessages.DELETE_FAILED_PREFIX + ...`: lỗi hệ thống khi xóa/update.

---

## Business rules
- Chỉ quản lý (`isManager=true`) mới được xóa/vô hiệu hóa.
- Không cho vô hiệu hóa nếu khách đang có vé sắp khởi hành (`hasUpcomingPaidTicket`).
- Nếu khách đã phát sinh dữ liệu liên quan (vé/hóa đơn) ⇒ ưu tiên xóa mềm (`isActive=false`) để tránh phá khóa ngoại/trace lịch sử.

---

## Dữ liệu vào/ra (I/O)

### Client → Server
| ActionType | DTO | Trường chính |
|---|---|---|
| `DELETE_CUSTOMER` | `CustomerDeleteRequestDTO` | `customerId`, `requestEmployeeId` |

### Server → Client
| Response.data |
|---|
| `CustomerDTO` (khi xóa mềm) hoặc `customerId` (String, khi xóa cứng) |

---

## Code Trace
| Layer | File/Class | Ghi chú |
|---|---|---|
| FXML | `src/main/resources/client/ui/views/customer-management.fxml` | Nút xóa khách hàng |
| Controller | `src/main/java/vn/edu/iuh/fit/client/controller/CustomerManagementController.java` | Build `CustomerDeleteRequestDTO` và gửi request |
| Common | `vn.edu.iuh.fit.common.command.ActionType.DELETE_CUSTOMER` | ActionType |
| Common | `vn.edu.iuh.fit.common.dto.CustomerDeleteRequestDTO` | DTO |
| Common | `vn.edu.iuh.fit.common.message.CustomerMessages` | Message constants |
| Router | `src/main/java/vn/edu/iuh/fit/server/network/RequestRouter.java` | Route `DELETE_CUSTOMER` |
| Server Service | `src/main/java/vn/edu/iuh/fit/server/service/impl/CustomerServiceImpl.java` | `deleteCustomer` |
| Entity/Table | `src/main/java/vn/edu/iuh/fit/server/model/Customer.java` (`customers`) | `isActive` |
| Entity/Table | `src/main/java/vn/edu/iuh/fit/server/model/Employee.java` (`employees`) | `isManager`, `employeeStatus` |

---

## Notes / Missing / Partial
- Đây là use case “xóa mềm” nhưng server có nhánh xóa cứng nếu khách không có vé/hóa đơn. Nếu yêu cầu nghiệp vụ bắt buộc luôn soft delete thì luồng hiện tại là «different».

