# Core UC Fix Plan (Reverse Engineering → Implementation)

> Scope: Fix all Missing/Partial/Different items found in core UC reverse docs (UC001–UC004).

## UC001 — Bán vé

### UC001-FIX-01 — Ensure `SaleCreateRequestDTO.employeeId` is set + server resolves employee safely
- **Issue:** Client may not set `employeeId`; server may create `Invoice(SALE)` with `employee=null`.
- **Files to change:**
  - Client: `src/main/java/vn/edu/iuh/fit/client/controller/SellTicketWizardController.java`
  - Server: `src/main/java/vn/edu/iuh/fit/server/service/impl/SaleServiceImpl.java`
- **Fix:**
  - Client: set `SaleCreateRequestDTO.employeeId` from `ClientSessionContext.getInstance().getEmployeeId()`; fallback to `employeeCode` if available.
  - Server: resolve employee by `employee_id` or `employee_code` (reuse logic style from `TicketServiceImpl.findEmployeeByIdOrCode`); reject if cannot resolve (no invoice with null employee).
- **Risk:** Client session data missing/incorrect → must fail fast with clear message.

### UC001-FIX-02 — Print invoice using existing VAT/HTML template
- **Issue:** Client prints invoice as “summary” fake.
- **Files to change:**
  - Client: `src/main/java/vn/edu/iuh/fit/client/controller/SellTicketWizardController.java`
- **Fix:**
  - Replace summary printing with `InvoiceRenderer` (already uses `/client/print/invoice-template.html` + `/client/print/invoice-style.css`).
  - Render PDF file and open in `PdfViewerController.loadDocument(...)`.
- **Risk:** Template/font missing in runtime → must surface error but never rollback sale.

### UC001-FIX-03 — Disallow redeem points with any passenger-type discount
- **Issue:** `SaleMessages.POINTS_NOT_ALLOWED_WITH_DISCOUNT` exists but not enforced.
- **Files to change:**
  - Server: `src/main/java/vn/edu/iuh/fit/server/service/impl/SaleServiceImpl.java`
- **Fix:**
  - If `redeemRequested=true` and any created seat ticket has discounted type (`TicketType != NORMAL`) or `InvoiceDetail.discount > 0` → reject with `SaleMessages.POINTS_NOT_ALLOWED_WITH_DISCOUNT`.
- **Risk:** Needs to run after pricing is computed (server source-of-truth).

## UC002 — Đổi vé

### UC002-FIX-01 — Enforce same departure/destination when exchanging
- **Issue:** Server does not guarantee route endpoints match between old ticket and new seat.
- **Files to change:**
  - Server: `src/main/java/vn/edu/iuh/fit/server/service/impl/TicketServiceImpl.java`
- **Fix:**
  - Validate for each pair (oldTicketId ↔ newScheduleDetailId, by index) that old/new schedule route endpoints (departure station + destination station) match; enforce in both preview and confirm.
- **Risk:** Legacy data may have null route/station → return clear error instead of allowing blind exchange.

### UC002-FIX-02 — Exchange search keyword supports ticketId/QR/customerId/passport/passengerDoc
- **Issue:** DTO field named `idCard` but behavior should be broader.
- **Files to change:**
  - Server: `src/main/java/vn/edu/iuh/fit/server/service/impl/TicketServiceImpl.java`
  - Client: `src/main/resources/client/ui/views/exchange-ticket-search.fxml`
  - Client: `src/main/java/vn/edu/iuh/fit/client/controller/ExchangeTicketSearchController.java`
- **Fix:**
  - Server: treat `ExchangeEligibleTicketSearchDTO.idCard` as `searchKeyword`; search by:
    - `Ticket.id` / `Ticket.qrCode`
    - Customer id / idCard / passport
    - Passenger document (`Ticket.passengerIdCard`)
  - Client: update label/placeholder text to reflect supported search keys.
- **Risk:** Keep backward compatible validation/message constant.

### UC002-FIX-03 — Print new tickets after exchange
- **Issue:** Client does not store `ExchangeTicketResponseDTO` for printing.
- **Files to change:**
  - Client: `src/main/java/vn/edu/iuh/fit/client/controller/SellTicketWizardController.java`
- **Fix:**
  - Store last exchange response and print `newTickets` via existing ticket printing flow (`TicketRenderer` + `PdfViewerController`); prompt user to print after success.
- **Risk:** Never rollback exchange if printing fails.

### UC002-FIX-04 — Verify exchange fee = 20,000
- **Issue:** Alignment verification only.
- **Files to check:** `src/main/java/vn/edu/iuh/fit/server/service/impl/TicketServiceImpl.java`
- **Fix:** No code change expected if `EXCHANGE_FEE = 20_000.0`.

## UC003 — Trả vé

### UC003-FIX-01 — Refund receipt supports multiple tickets
- **Issue:** `GET_REFUND_RECEIPT` builds receipt using first invoice detail only.
- **Files to change:**
  - Common: `src/main/java/vn/edu/iuh/fit/common/dto/RefundReceiptDTO.java` (+ new `RefundReceiptItemDTO`)
  - Server: `src/main/java/vn/edu/iuh/fit/server/service/impl/TicketServiceImpl.java`
  - Client: `src/main/java/vn/edu/iuh/fit/client/controller/ReturnTicketController.java`
- **Fix:**
  - Server: build `RefundReceiptDTO.items` from all `invoice.details`, compute totals (sum).
  - Client: render 1 receipt page per item using existing JRXML and merge to a single PDF (no need to redesign template now).
- **Risk:** Keep single-ticket fields populated for backward compatibility.

### UC003-FIX-02 — Adjust reward points on refund (revoke earned points only)
- **Issue:** Sale accrues points; refund does not adjust.
- **Files to change:**
  - Server: `src/main/java/vn/edu/iuh/fit/server/service/impl/TicketServiceImpl.java`
- **Fix:**
  - After confirm refund, reduce `Customer.rewardPoints` by `floor(totalOriginalPaid/10_000)` for returned tickets; never negative.
  - Do not restore redeemed points (per requirement).
- **Risk:** Rounding/edge-cases; use conservative approach and log (if logger exists).

## UC004 — Quản lý khách hàng

### UC004-FIX-01 — Update customer passport + prevent passport duplicates
- **Issue:** `updateCustomer` does not set passport; create/update do not check passport duplicates.
- **Files to change:**
  - Common: `src/main/java/vn/edu/iuh/fit/common/message/CustomerMessages.java`
  - Server: `src/main/java/vn/edu/iuh/fit/server/service/impl/CustomerServiceImpl.java`
  - Server: `src/main/java/vn/edu/iuh/fit/server/repository/CustomerRepository.java`
  - Server: `src/main/java/vn/edu/iuh/fit/server/repository/impl/CustomerRepositoryImpl.java`
- **Fix:**
  - Add `existsByPassport(...)`.
  - Update create/update to set passport and reject duplicates using new message constant.
- **Risk:** DB may not have unique constraint; enforce at service layer.

### UC004-FIX-02 — Always soft delete (no hard delete in UI delete flow)
- **Issue:** `deleteCustomer` hard-deletes when no tickets/invoices.
- **Files to change:**
  - Server: `src/main/java/vn/edu/iuh/fit/server/service/impl/CustomerServiceImpl.java`
- **Fix:** Always set `Customer.isActive=false` and update; never call `deleteCustomer(...)` here.
- **Risk:** None; preserves audit/history better.

## Validation / Test
- Build/compile all 3 modules after changes (client/common/server).
- If no automated tests, provide manual checklist in report.

