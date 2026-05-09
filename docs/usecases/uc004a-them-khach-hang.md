# Use case UC004A: Thêm khách hàng

## Actor
- **Primary:** Nhân viên (`Employee`) thao tác trên màn hình Quản lý khách hàng
- **Secondary:** Khách hàng (cung cấp thông tin)
- **System:** JavaFX Client → TCP Socket Server → MariaDB

## Tiền điều kiện
- Nhân viên đang mở màn hình quản lý khách hàng và có thể mở dialog thêm mới.

## Hậu điều kiện (khi thành công)
- Tạo mới `Customer` trong DB với `isActive=true`, `rewardPoints` khởi tạo theo entity (mặc định 0).
- Client nhận `CustomerDTO` mới tạo và reload danh sách.

---

## Luồng chính
1. Client mở dialog thêm khách hàng (client: `CustomerManagementController.openAddCustomerDialog` → `KhachHangDialogController.initForAdd`).
2. Nhân viên nhập thông tin:
   - `fullName`
   - CCCD hoặc Hộ chiếu (client toggle `isForeignerCheckBox` để map vào `CustomerDTO.idCard` hoặc `CustomerDTO.passport`)
   - `phone` (tuỳ chọn), `email` (tuỳ chọn)
3. Client validate nhanh (client-side) trước khi gửi:
   - `fullName` không rỗng
   - giấy tờ không rỗng
   - `phone` nếu có phải đúng 10 số; `email` nếu có phải đúng regex (client: `KhachHangDialogController.handleSave`).
4. Client gửi `Request(ActionType.CREATE_CUSTOMER, CustomerDTO)` với `customerId=null` (client: `KhachHangDialogController.handleSave`).
5. Server (`CustomerServiceImpl.createCustomer`):
   - Validate DTO bằng `ValidationUtils.validate(customerDTO)` (bao gồm các constraint trong `CustomerDTO`: pattern CCCD/hộ chiếu, email, `isDocumentPresent()`).
   - Nếu `customerDTO.customerId` khác null/blank ⇒ trả `Response.error(CustomerMessages.CUSTOMER_ID_MUST_BE_NULL)`.
   - Check trùng:
     - `CustomerRepository.existsByIdCard(em, customerDTO.idCard, null)` ⇒ `CustomerMessages.ID_CARD_DUPLICATE`.
     - Nếu có email: `CustomerRepository.existsByEmail(em, customerDTO.email, null)` ⇒ `CustomerMessages.EMAIL_DUPLICATE`.
   - Map DTO → Entity (`CustomerMapper.INSTANCE.toEntity`) và set `customer.setActive(true)`.
   - Persist `customerRepository.createCustomer(em, customer)`.
   - Trả `Response.success(CustomerMessages.CREATE_SUCCESS, CustomerDTO)`.
6. Client hiển thị message và đóng dialog; gọi callback `onSaved` để reload danh sách.

---

## Luồng thay thế
- **[Không nhập SĐT/email]:** Client vẫn gửi DTO với `phone/email=null`; server chấp nhận nếu các field khác hợp lệ.
- **[Nhập hộ chiếu]:** Client set `passport` và để `idCard=null`; server vẫn pass `CustomerDTO.isDocumentPresent()`.

---

## Luồng lỗi tiêu biểu (tham chiếu constant)
- `CustomerMessages.DATA_INVALID_PREFIX + ...`: DTO fail validation (pattern CCCD/hộ chiếu, email, thiếu giấy tờ...).
- `CustomerMessages.CUSTOMER_ID_MUST_BE_NULL`: client gửi `customerId` khi tạo mới.
- `CustomerMessages.ID_CARD_DUPLICATE`: trùng CCCD.
- `CustomerMessages.EMAIL_DUPLICATE`: trùng email.
- `CustomerMessages.CREATE_FAILED_PREFIX + ...`: lỗi hệ thống khi persist.

---

## Business rules
- `CustomerDTO.customerId` phải để trống khi tạo mới.
- Bắt buộc có CCCD hoặc hộ chiếu (`CustomerDTO.isDocumentPresent()`).
- Không cho trùng CCCD; email nếu nhập cũng không được trùng.
- Khách hàng tạo mới luôn `isActive=true`.

---

## Dữ liệu vào/ra (I/O)

### Client → Server
| ActionType | DTO | Trường chính |
|---|---|---|
| `CREATE_CUSTOMER` | `CustomerDTO` | `customerId=null`, `fullName`, `idCard` hoặc `passport`, `phone`, `email` |

### Server → Client
| Response.data |
|---|
| `CustomerDTO` |

---

## Code Trace
| Layer | File/Class | Ghi chú |
|---|---|---|
| FXML | `src/main/resources/client/ui/views/customer-management.fxml` | Màn hình danh sách khách hàng |
| Controller | `src/main/java/vn/edu/iuh/fit/client/controller/CustomerManagementController.java` | Mở dialog thêm |
| Controller | `src/main/java/vn/edu/iuh/fit/client/controller/KhachHangDialogController.java` | Build DTO và gửi request |
| Socket client | `src/main/java/vn/edu/iuh/fit/client/service/SocketRequestService.java` | Gửi `Request(ActionType, DTO)` |
| Common | `vn.edu.iuh.fit.common.command.ActionType.CREATE_CUSTOMER` | ActionType |
| Common | `vn.edu.iuh.fit.common.dto.CustomerDTO` | Constraint + fields |
| Common | `vn.edu.iuh.fit.common.message.CustomerMessages` | Message constants |
| Router | `src/main/java/vn/edu/iuh/fit/server/network/RequestRouter.java` | Route `CREATE_CUSTOMER` |
| Server Service | `src/main/java/vn/edu/iuh/fit/server/service/impl/CustomerServiceImpl.java` | `createCustomer` |
| Entity/Table | `src/main/java/vn/edu/iuh/fit/server/model/Customer.java` (`customers`) | `isActive`, `rewardPoints` |

---

## Notes / Missing / Partial
- Không thấy field “địa chỉ khách hàng” trong `CustomerDTO`/`Customer` hiện tại ⇒ nếu requirement cần thì «missing» (không suy đoán thêm).

