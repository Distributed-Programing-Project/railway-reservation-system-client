# Use case UC004D: Xem lịch sử khách hàng

## Actor
- **Primary:** Nhân viên (`Employee`)
- **Secondary:** —
- **System:** JavaFX Client → TCP Socket Server → MariaDB

## Tiền điều kiện
- Nhân viên đã tra cứu và chọn được 1 khách hàng (`CustomerDTO.customerId`) trên màn hình quản lý khách hàng.

## Hậu điều kiện (khi thành công)
- Client nhận `CustomerHistoryResponseDTO` gồm:
  - Danh sách lịch sử vé: `List<CustomerHistoryItemDTO>`
  - Tổng tiền mua: `totalAmount`

---

## Luồng chính
1. Nhân viên chọn khách hàng và nhấn “Lịch sử mua vé” (client: `CustomerManagementController.openCustomerHistory`).
2. Client mở view lịch sử và khởi tạo dữ liệu (client: `CustomerHistoryController.initCustomer(CustomerDTO)` → `loadHistory()`).
3. Client gửi `Request(ActionType.GET_CUSTOMER_HISTORY, CustomerHistoryRequestDTO{customerId})`.
4. Server (`CustomerServiceImpl.getCustomerHistory`):
   - Validate DTO (`ValidationUtils.validate`), nếu lỗi trả `Response.error(CustomerMessages.DATA_INVALID_PREFIX + ...)`.
   - Query:
     - `customerRepository.findCustomerTicketHistory(em, customerId)`
     - `customerRepository.sumCustomerInvoiceTotalAmount(em, customerId)`
   - Trả `Response.success("Lấy lịch sử mua vé thành công", CustomerHistoryResponseDTO{items, totalAmount})` (message hiện hard-code).
5. Client render bảng lịch sử và hiển thị `totalAmount`.

---

## Luồng thay thế
- —

---

## Luồng lỗi tiêu biểu (tham chiếu constant)
- `CustomerMessages.DATA_INVALID_PREFIX + ...`: thiếu `customerId`.
- Error message hard-code:
  - `"Lỗi khi lấy lịch sử mua vé: " + e.getMessage()` từ server.

---

## Business rules
- History được truy vấn theo `customerId` (không thấy filter theo `isActive` trong service).
- Tổng tiền lấy từ tổng hóa đơn của khách (`sumCustomerInvoiceTotalAmount`) (chi tiết cách tính nằm ở repository ⇒ uncertainty).

---

## Dữ liệu vào/ra (I/O)

### Client → Server
| ActionType | DTO | Trường chính |
|---|---|---|
| `GET_CUSTOMER_HISTORY` | `CustomerHistoryRequestDTO` | `customerId` |

### Server → Client
| Response.data |
|---|
| `CustomerHistoryResponseDTO` (`items: List<CustomerHistoryItemDTO>`, `totalAmount`) |

---

## Code Trace
| Layer | File/Class | Ghi chú |
|---|---|---|
| FXML | `src/main/resources/client/ui/views/customer-history-view.fxml` | View lịch sử mua vé |
| Controller | `src/main/java/vn/edu/iuh/fit/client/controller/CustomerHistoryController.java` | Gửi `GET_CUSTOMER_HISTORY` và render |
| Controller | `src/main/java/vn/edu/iuh/fit/client/controller/CustomerManagementController.java` | Điều hướng sang view lịch sử |
| Common | `vn.edu.iuh.fit.common.command.ActionType.GET_CUSTOMER_HISTORY` | ActionType |
| Common | `vn.edu.iuh.fit.common.dto.CustomerHistoryRequestDTO`, `CustomerHistoryResponseDTO`, `CustomerHistoryItemDTO` | DTO |
| Router | `src/main/java/vn/edu/iuh/fit/server/network/RequestRouter.java` | Route `GET_CUSTOMER_HISTORY` |
| Server Service | `src/main/java/vn/edu/iuh/fit/server/service/impl/CustomerServiceImpl.java` | `getCustomerHistory` |
| Entity/Table | `.../model/Ticket` (`tickets`), `.../model/Invoice` (`invoices`) | Nguồn dữ liệu history (query ở repository) |

---

## Notes / Missing / Partial
- Message success/error trong `CustomerServiceImpl.getCustomerHistory` hiện hard-code, không dùng `CustomerMessages.*` ⇒ ghi nhận để trace lỗi đúng chuỗi (không sửa code).

