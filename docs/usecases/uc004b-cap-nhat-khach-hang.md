# Use case UC004B: Cập nhật khách hàng

## Actor
- **Primary:** Nhân viên (`Employee`) thao tác trên màn hình Quản lý khách hàng
- **Secondary:** Khách hàng (cung cấp/cập nhật thông tin)
- **System:** JavaFX Client → TCP Socket Server → MariaDB

## Tiền điều kiện
- Khách hàng tồn tại trong DB và `Customer.isActive=true`.
- Nhân viên đã chọn 1 dòng khách hàng để sửa.

## Hậu điều kiện (khi thành công)
- Cập nhật thông tin `Customer` trong DB (tên, CCCD, SĐT, email).
- Client nhận `CustomerDTO` đã cập nhật và reload danh sách.

---

## Luồng chính
1. Client chọn khách hàng và mở dialog sửa (client: `CustomerManagementController.openEditCustomerDialog` → `KhachHangDialogController.initForEdit(CustomerDTO)`).
2. Nhân viên chỉnh sửa thông tin và nhấn Lưu.
3. Client validate nhanh (client-side) tương tự UC004A (phone/email format, giấy tờ không rỗng).
4. Client gửi `Request(ActionType.UPDATE_CUSTOMER, CustomerDTO)` với `customerId` khác null/blank (client: `KhachHangDialogController.handleSave`).
5. Server (`CustomerServiceImpl.updateCustomer`):
   - Validate DTO bằng `ValidationUtils.validate(customerDTO)`.
   - Nếu `customerId` null/blank ⇒ trả `Response.error(CustomerMessages.CUSTOMER_ID_REQUIRED)`.
   - Load `existing = customerRepository.findCustomerById(em, customerId)`:
     - Không tồn tại ⇒ `CustomerMessages.customerNotFound(customerId)`.
     - `!existing.isActive()` ⇒ `CustomerMessages.customerInactive(customerId)`.
   - Check trùng (loại trừ chính bản ghi đang sửa bằng `existing.getId()`):
     - `existsByIdCard(..., existingId)` ⇒ `CustomerMessages.ID_CARD_DUPLICATE`.
     - `existsByEmail(..., existingId)` ⇒ `CustomerMessages.EMAIL_DUPLICATE`.
   - Update field:
     - `existing.setName(customerDTO.fullName)`
     - `existing.setIdCard(customerDTO.idCard)`
     - `existing.setPhoneNumber(normalizeBlankToNull(customerDTO.phone))`
     - `existing.setEmail(normalizeBlankToNull(customerDTO.email))`
   - Persist `customerRepository.updateCustomer(em, existing)`.
   - Trả `Response.success(CustomerMessages.UPDATE_SUCCESS, CustomerDTO)`.
6. Client hiển thị message, đóng dialog, reload danh sách.

---

## Luồng thay thế
- **[Xóa SĐT/email]:** Client có thể để trống; server normalize blank → null (`normalizeBlankToNull`).

---

## Luồng lỗi tiêu biểu (tham chiếu constant)
- `CustomerMessages.DATA_INVALID_PREFIX + ...`: DTO fail validation.
- `CustomerMessages.CUSTOMER_ID_REQUIRED`: thiếu `customerId` khi update.
- `CustomerMessages.customerNotFound(id)`: không tìm thấy khách hàng.
- `CustomerMessages.customerInactive(id)`: khách hàng đã bị vô hiệu hóa.
- `CustomerMessages.ID_CARD_DUPLICATE`, `CustomerMessages.EMAIL_DUPLICATE`: trùng dữ liệu.
- `CustomerMessages.UPDATE_FAILED_PREFIX + ...`: lỗi hệ thống.

---

## Business rules
- Chỉ cập nhật khách hàng đang `isActive=true`.
- CCCD/email không được trùng với khách khác (loại trừ chính khách đang sửa).
- `customerId` bắt buộc khi update.

---

## Dữ liệu vào/ra (I/O)

### Client → Server
| ActionType | DTO | Trường chính |
|---|---|---|
| `UPDATE_CUSTOMER` | `CustomerDTO` | `customerId`, `fullName`, `idCard/passport`, `phone`, `email` |

### Server → Client
| Response.data |
|---|
| `CustomerDTO` |

---

## Code Trace
| Layer | File/Class | Ghi chú |
|---|---|---|
| FXML | `src/main/resources/client/ui/views/customer-management.fxml` | Màn hình danh sách khách hàng |
| Controller | `src/main/java/vn/edu/iuh/fit/client/controller/CustomerManagementController.java` | Mở dialog sửa |
| Controller | `src/main/java/vn/edu/iuh/fit/client/controller/KhachHangDialogController.java` | Gửi `UPDATE_CUSTOMER` |
| Common | `vn.edu.iuh.fit.common.command.ActionType.UPDATE_CUSTOMER` | ActionType |
| Common | `vn.edu.iuh.fit.common.dto.CustomerDTO` | Constraint + fields |
| Common | `vn.edu.iuh.fit.common.message.CustomerMessages` | Message constants |
| Router | `src/main/java/vn/edu/iuh/fit/server/network/RequestRouter.java` | Route `UPDATE_CUSTOMER` |
| Server Service | `src/main/java/vn/edu/iuh/fit/server/service/impl/CustomerServiceImpl.java` | `updateCustomer` |
| Entity/Table | `src/main/java/vn/edu/iuh/fit/server/model/Customer.java` (`customers`) | `isActive` |

---

## Notes / Missing / Partial
- Đã fix: `CustomerServiceImpl.updateCustomer(...)` set `existing.passport` từ `CustomerDTO.passport` và check trùng passport (`CustomerRepository.existsByPassport(...)`), không phá luồng CCCD hiện tại.
