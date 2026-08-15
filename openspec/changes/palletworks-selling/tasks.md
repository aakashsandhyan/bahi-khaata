# Tasks — Palletworks Selling + Dual UX

## 1. Register sessions (backend)

- [x] 1.1 Migration V50 (verify ceiling first): `register_session` + `register_cash_movement` tables; nullable `register_session_id` on `sale`
- [x] 1.2 Entities + repositories; one-open-session-per-register enforced in the service
- [x] 1.3 `RegisterService`: open (float, operator), cash movement (open session required), close (expected = float + cash sales + in − out; pin over/short + closed_at)
- [x] 1.4 Endpoints: `GET /api/registers`, `POST /api/registers/{name}/open`, `/cash-movements`, `/close`; contracts for session state and close summary
- [x] 1.5 Unit tests: double-open refused, movement without session refused, close math (cash-only expected, UPI excluded), pinned figure immutable, sessionless sale untouched

## 2. Sale linkage

- [x] 2.1 `complete-sale` request gains optional `registerSessionId`; recorded on the sale; absent = sessionless (classic path unchanged)
- [x] 2.2 `GET /api/sales` accepts a session filter for the close-drawer review
- [x] 2.3 Tests: modern sale references session; classic sale does not; session filter returns exactly its bills

## 3. Classic UX restored

- [ ] 3.1 Restore `LotManagement.tsx`, `Receiving.tsx`, `Catalog.tsx` from main into `src/classic/` with a restore-commit note in each header; wire their api/types needs
- [ ] 3.2 `ClassicShell.tsx`: the sixteen-entry top bar (Till listed, admin configs separate) rendering shared screens + the restored trio; `.topnav` styles scoped under `.classic`
- [ ] 3.3 Verify every classic screen renders and functions against the running app

## 4. Dual-UX switch

- [ ] 4.1 `uxMode` in `App.tsx` (localStorage, default modern) mounting ClassicShell or ModernShell; no reload on switch
- [ ] 4.2 Switch controls: "Classic UX" in the modern sidebar footer, "Modern UX" in the classic top bar
- [ ] 4.3 `#till` / `#capture` landings honored in both modes; phone stations unaffected
- [ ] 4.4 e2e: switch flips whole shell both ways and persists across reload

## 5. Modern selling screens

- [ ] 5.1 Register screen per the artifact: both registers' state, open (float entry), cash in/out, close flow with counted amount → over/short summary + session bill list
- [ ] 5.2 Modern Checkout per the artifact: session gate with inline open, scan/cart/pay through the existing engine
- [ ] 5.3 Invoices per the artifact: newest-first list, find by bill number, per-row reprint; replaces Sales in the modern sidebar (Selling group: Register, Checkout, Invoices)
- [ ] 5.4 Sidebar + screenMeta entries; classic keeps its old Sales screen

## 6. Verify

- [ ] 6.1 Full backend suite green
- [ ] 6.2 e2e suite green (including the rewritten shell probes: Till listed under Classic, unlisted under Modern)
- [ ] 6.3 Browser walk: ring a sale in Classic, see it in Modern Invoices; open→sell→close a register, over/short correct on screen
- [ ] 6.4 `openspec validate palletworks-selling`; bundle-size check on `vite build`
