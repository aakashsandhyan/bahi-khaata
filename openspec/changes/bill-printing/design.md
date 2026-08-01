# Bill printing — design

Full brainstorming design: `docs/superpowers/specs/2026-08-02-bill-printing-design.md`. This condenses the decisions that shape the specs and tasks.

## Context

The till (`/api/checkout`) opens a cart, scans, edits lines, clears — but has no completion step. No sale is persisted, stock is never decremented (`MovementType.SALE` exists but is unused by checkout), and `CartView.taxIsPlaceholder` states no invoice is issued from it. The only printer today is the TSC TE-244 **label** printer, driven by `PrinterDriver.sendLabel` (TSPL). A bill needs a completed sale and a different printer.

## Goals / Non-Goals

**Goals:**
- Complete a sale: persist an immutable `Sale`, decrement stock through the ledger, and produce a printed bill.
- Selling is never blocked by stock counts; negatives are legitimate records.
- A bill printed on an 80mm ESC/POS receipt printer, reprintable from the stored sale.
- Bill wording (title, declaration, GSTIN) is settings-driven so the CA's GST decision is a config change, not code.

**Non-Goals:**
- Real GST **tax invoice** with per-line HSN / CGST-SGST computation (pending the CA; likely a Bill of Supply under composition, which carries no tax).
- Cash tendered / change calculation (method only).
- Any change to the label (TSPL) printer.
- Payment-gateway / card-terminal integration; the method is a recorded label only.

## Decisions

- **Approach A — synchronous receipt print, dedicated ESC/POS driver.** A bill must come out at the counter immediately and the operator must see it worked. The async label queue (hold-and-pair, spacing, retry) is the wrong tool for single immediate bills, so bills do **not** go through `PrintExecutorService`. A new `ReceiptPrinterDriver` (`printReceipt(byte[])`) sits parallel to the label `PrinterDriver`; TSPL and ESC/POS stay separate.
- **Immutable sale + snapshots.** `Sale` + `SaleLine` snapshot name/barcode/mrp/unitPrice/qty/lineTotal/saving at completion (same principle as the self-contained `PrintJob`), so a later price change never alters a past bill. Reprint always re-renders from the stored sale, never a live cart.
- **Running bill number.** `bill_no = MAX(bill_no)+1` inside the completion transaction — safe under the single serialized SQLite connection; rendered `BB-000123`.
- **No oversell guard; negatives allowed.** Completion validates only a non-empty cart with every line priced. `SALE` movements are written FIFO across the product's batches, capturing COGS; units beyond the counted stock attribute to the newest batch and drive it negative. A negative on-hand is a true, timestamped fact and the trigger for a later recount — the ledger bends to the sale, not the reverse.
- **Print after commit.** The sale + ledger commit first; the receipt prints afterward. A print failure (offline / jam / unconfigured) flags `printFailed` on the response but never rolls back the recorded sale — the operator reprints from the sale.
- **Second printer, its own config.** `receipt_printer_config` mirrors `printer_config` (address, transport, enabled, test_status), transport LAN raw-9100 by default with USB (javax.print) as the alternative, plus an admin screen + test print.
- **Settings-driven bill.** `bill_settings` (shop name/address, GSTIN, bill title `Bill of Supply`|`Tax Invoice`, declaration text, footer) is read by `ReceiptTemplateService`, which renders a `Sale` to ESC/POS bytes for an 80mm roll. Under composition the bill is a Bill of Supply: no tax lines, GSTIN + the mandatory *"Composition taxable person, not eligible to collect tax on supplies"* declaration.
- **Module placement.** `checkout` owns the sale (`Checkout.complete`, `Sale`/`SaleLine`, sales queries); `print` owns the receipt driver, template, config, and bill settings; `inventory` provides FIFO `SALE` consumption + COGS. Dependencies: `checkout → print`, `checkout → inventory`. ArchUnit guards only the top-level modules, so these are fine.

## Risks / Trade-offs

- **Negative on-hand accumulates silently** until a reconciliation surface exists. Accepted deliberately (counts are rough; the sale is truth); a "negative on-hand" report is a natural follow-up, out of scope here.
- **ESC/POS printer variability** (cut command, code page, chars/line) differs by model. Mitigated by keeping the template driven by a small command set and the config holding transport details; verified against the actual printer at rollout.
- **Composition declaration / GSTIN correctness** is legal wording pending the CA. Mitigated by making it editable settings, not baked-in text.
- **COGS on overshoot** uses the newest batch's pinned cost as a best estimate when stock is exceeded — margin on those units is approximate, which is acceptable for a liquidation shop and only affects internal reporting, never the bill.
