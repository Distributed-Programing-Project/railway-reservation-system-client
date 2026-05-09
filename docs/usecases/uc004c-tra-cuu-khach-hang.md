# Use case UC004C: Tra cứu khách hàng

## Actor
- **Primary:** Nhân viên (`Employee`)
- **Secondary:** —
- **System:** JavaFX Client → TCP Socket Server → MariaDB

## Tiền điều kiện
- Nhân viên đang ở màn hình Quản lý khách hàng.

## Hậu điều kiện (khi thành công)
- Client nhận danh sách khách hàng đang hoạt động (`isActive=true`) theo từ khóa và phân trang.

---

## Luồng chính
1. Nhân viên nhập từ khóa (tên/CCCD/hộ chiếu/SĐT/email) và nhấn Tìm kiếm hoặc đổi trang (client: `CustomerManagementController.loadCustomers(keyword, pageIndex)`).
2. Client gửi `Request(ActionType.SEARCH_CUSTOMERS, CustomerSearchDTO{keyword, page, size})`.
3. Server (`CustomerServiceImpl.searchCustomers`):
   - Normalize request: nếu `searchDTO==null` thì dùng `new CustomerSearchDTO()`.
   - Validate DTO (`ValidationUtils.validate`), nếu lỗi trả `Response.error(String.join(", ", errors))`.
   - Chuẩn hóa phân trang: `size<=0` → 20; `page<0` → 0.
   - Query:
     - `CustomerRepository.searchActiveCustomers(em, keyword, page, size)`
     - `CustomerRepository.countActiveCustomers(em, keyword)`
   - Build `CustomerPageDTO{customers, totalElements, totalPages, currentPage}`.
   - Trả `Response.success(CustomerMessages.SEARCH_SUCCESS, CustomerPageDTO)`.
4. Client hiển thị danh sách và cập nhật `Pagination.pageCount` theo `totalPages`.

---

## Luồng thay thế
- **[Không nhập keyword]:** client gửi `keyword=null`; server trả danh sách active customers theo trang.

---

## Luồng lỗi tiêu biểu (tham chiếu constant)
- Validation error message (không có prefix): ví dụ `"Page size phải lớn hơn 0"` từ `CustomerSearchDTO`.
- `CustomerMessages.SEARCH_FAILED_PREFIX + ...`: lỗi hệ thống khi query.

---

## Business rules
- Chỉ trả về khách hàng đang hoạt động (`searchActiveCustomers`/`countActiveCustomers`).
- `page` tối thiểu 0; `size` tối thiểu 1 (server fallback về 20 nếu size<=0).

---

## Dữ liệu vào/ra (I/O)

### Client → Server
| ActionType | DTO | Trường chính |
|---|---|---|
| `SEARCH_CUSTOMERS` | `CustomerSearchDTO` | `keyword`, `page`, `size` |

### Server → Client
| Response.data |
|---|
| `CustomerPageDTO` (`customers: List<CustomerDTO>`, `totalElements`, `totalPages`, `currentPage`) |

---

## Code Trace
| Layer | File/Class | Ghi chú |
|---|---|---|
| FXML | `src/main/resources/client/ui/views/customer-management.fxml` | Màn hình tra cứu + phân trang |
| Controller | `src/main/java/vn/edu/iuh/fit/client/controller/CustomerManagementController.java` | `loadCustomers(...)` gửi `SEARCH_CUSTOMERS` |
| Socket client | `src/main/java/vn/edu/iuh/fit/client/service/SocketRequestService.java` | TCP ObjectStream |
| Common | `vn.edu.iuh.fit.common.command.ActionType.SEARCH_CUSTOMERS` | ActionType |
| Common | `vn.edu.iuh.fit.common.dto.CustomerSearchDTO`, `CustomerPageDTO`, `CustomerDTO` | DTO/response |
| Common | `vn.edu.iuh.fit.common.message.CustomerMessages` | `SEARCH_SUCCESS`, `SEARCH_FAILED_PREFIX` |
| Router | `src/main/java/vn/edu/iuh/fit/server/network/RequestRouter.java` | Route `SEARCH_CUSTOMERS` |
| Server Service | `src/main/java/vn/edu/iuh/fit/server/service/impl/CustomerServiceImpl.java` | `searchCustomers` |
| Entity/Table | `src/main/java/vn/edu/iuh/fit/server/model/Customer.java` (`customers`) | `isActive` |

---

## Notes / Missing / Partial
- Server side chỉ search active customers; nếu cần tra cứu cả khách đã “xóa mềm” (`isActive=false`) thì hiện «missing».

