# Building a POS for Bachat Bazar — a detailed report

This is a working reference document, not a spec. It explains what each piece is, why it matters for a store like Bachat Bazar specifically, and what was decided (or left open) through the discussion so far. Use it as raw material to write your own spec afterward.

---

## 1. Why this isn't a normal POS problem

Most off-the-shelf POS software (Vyapar, Marg, and similar) is designed around a fairly clean assumption: a stable catalog of products with consistent barcodes, consistent cost prices, and consistent stock flow from known suppliers. Bachat Bazar breaks that assumption in several ways at once:

- Inventory comes from liquidation, overstock, and open-box lots — meaning the same product can arrive multiple times at different cost prices, and a meaningful share of stock may have no usable barcode at all.
- The category spread is wide: home essentials, kitchen, electronics, gifting, decor, fashion. Each category needs different product attributes (a fashion item needs size/color, an electronics item needs serial number/warranty, a kitchen item needs neither).
- The core brand pitch — "cheaper than online" — makes price display and trust a first-class product concern, not an afterthought.

This combination is exactly the kind of problem a generic POS handles poorly and a custom one can handle well, which is the actual justification for building rather than buying.

---

## 2. Architecture — the foundational decisions

### 2.1 Offline-first is non-negotiable

A POS that depends on constant internet connectivity stops working the moment the connection drops — and in Bhopal, power and connectivity interruptions are a real, not hypothetical, occurrence. The correct approach is a **local-first architecture**: all reads and writes happen against a local database on the store's own machine, and any syncing to a central server (needed only once there's more than one outlet) happens asynchronously whenever a connection is available. Checkout should never be blocked by network state.

### 2.2 Stack — why Java, and why JavaFX over Electron

Two broad UI approaches were considered for the checkout terminal: a web-technology stack (Electron, wrapping a JS/React frontend in a desktop shell) or a native Java stack (JavaFX).

The deciding factors, given the specific situation — a Java engineer of 8 years, 100% of the code being written by Claude with the engineer reviewing every line before it's pushed:

- **Review quality matters more than ecosystem size right now.** Code you can deeply judge is code where you'll actually catch subtle bugs, rather than trusting Claude's output. That review strength is highest in Java. This matters most exactly where mistakes are costliest — the checkout flow, tax calculation, and hardware integration.
- **Hardware access is more direct in JavaFX.** Electron is a Chromium browser plus a Node.js runtime bundled together. To talk to a barcode scanner or thermal printer, it still routes through Node's `serialport`/`usb`-style libraries, which are native modules that frequently break across Electron/Node version upgrades — a well-known pain point in the Electron-POS space. JavaFX talks to hardware more directly through Java's own USB/serial libraries or JavaPOS drivers, no extra language bridge in between.
- **Resource footprint** — Electron ships a full Chromium instance per app (commonly 150–300MB+ idle RAM); JavaFX with a trimmed JRE is meaningfully lighter, which matters on a budget counter PC.
- **Packaging** — `jpackage` (built into the JDK) produces a native installer bundling a minimal JRE, so end users don't need Java pre-installed. Comparable in convenience to Electron's `electron-builder`, without pulling in as much separate tooling.

The one real point in Electron/React's favor: a much larger pool of developers know JS/React than JavaFX, which matters if you want outside open-source contributors later. The resolution reached: **split the system** rather than pick one language for everything —

- **JavaFX** for the checkout terminal (money + hardware, highest stakes, best reviewed in Java).
- **Spring Boot** backend serving both the terminal and a web dashboard — one source of truth for schema and business logic.
- **A separate web dashboard** (React, or even a simpler server-rendered page to start) for admin/reports/inventory views — lower stakes, and the natural place outside contributors would want to touch anyway (dashboards and reports tend to attract more casual open-source contribution than checkout/hardware code).

### 2.3 Local database

SQLite is the right choice for the terminal's local store — zero configuration, transactional, single file, well suited for a single machine. PostgreSQL is the natural target for a future central/multi-outlet database, once there's an actual second outlet to justify it.

### 2.4 Primary keys

Use UUIDs (specifically UUIDv4), not auto-incrementing integers, for every table's primary key — even though multi-terminal or multi-outlet sync isn't being built yet. The reason: if two terminals ever independently generate "order #50," those records collide the moment they need to sync. UUIDs avoid this entirely, and it costs nothing to do from day one versus a lot to retrofit later.

### 2.5 What to explicitly not build yet

The original research (a Gemini-generated architecture report) proposed real infrastructure for multi-outlet sync — tools like PowerSync/ElectricSQL or SymmetricDS-style trigger-based replication across a star topology of stores. All of that is **correctly designed for a multi-outlet chain, not a solo developer's single-store v1.** Building sync infrastructure before there's a second outlet to sync with is speculative complexity — exactly the kind of premature architecture that contributes to solo open-source maintainers burning out before shipping anything real. Defer this entirely until outlet #2 is a genuine, funded next step.

---

## 3. Data model — how the inventory actually gets represented

### 3.1 The core problem: wildly different attributes per category

A flat product table with fixed columns for everything doesn't work when an electronics item needs a serial number and warranty period, a fashion item needs size/color/fit, and a kitchen item needs neither. Three approaches exist for this:

- **EAV (Entity-Attribute-Value)**: every attribute is a separate row in an attributes table. Maximally flexible, but reconstructing a single product record requires multiple joins, which can get slow.
- **Category-specific tables**: a separate table per category, linked to a base product table. Type-safe, but awkward when you need to query across categories, and every new category means a schema change.
- **Relational columns + a JSON/JSONB column**: standard columns for the fields every product shares (SKU, barcode, name, price), plus a flexible JSON column for whatever's category-specific. This is the hybrid approach, and it's the right fit here — flexible enough for a wildly mixed catalog, while indexed JSON queries stay fast at the scale Bachat Bazar is actually operating at (a few thousand SKUs, not millions).

### 3.2 The core problem: the same product, different cost each time it arrives

Liquidation and overstock sourcing means the same barcode can show up in multiple deliveries at different supplier cost prices. This needs two separate layers:

- A **products** table — barcode, name, category, attributes — stable, rarely changes.
- A **batches/lots** table — every time stock arrives, a new batch record captures the cost price, quantity, and date for that specific delivery, linked to the product.

On top of this, every inventory movement (a purchase, a sale, an adjustment) should write a row to an **append-only stock ledger** — this is the actual source of truth for reconstructing historical stock valuation and calculating cost of goods sold at any point in time. If a backdated entry needs to go in (say, a delivery gets logged a day late), the ledger recalculates forward from that point rather than allowing a silent edit.

**Costing method — FIFO vs weighted average**: when a sale happens, which lot's cost gets used for margin calculation? FIFO (oldest lot consumed first) was chosen over weighted-average costing, mainly because it gives cleaner per-lot margin visibility — useful specifically for deciding whether to reorder from a given liquidation supplier again, which is a real recurring decision in this business.

### 3.3 Selling price lives separately from cost

This was a genuine point of confusion worth restating clearly: **selling price is a single attribute on the product, never on the batch/lot.** Physically, once units from an old lot and a new lot are sitting on the same shelf, a customer can't tell them apart, and shouldn't be charged differently for them. Cost price varies by lot (used internally for margin math); selling price is one number the customer sees, changed only when the business deliberately decides to change it — never as an automatic side effect of a new delivery arriving at a different cost. The system's job is to *flag* meaningful cost changes ("margin would shift to X% at the current price — review?"), not to silently reprice anything.

### 3.4 Invoices must be immutable once issued

Under Indian GST law, once a tax invoice has been issued, editing its values after the fact is not legally permitted — corrections have to be recorded as a separate credit or debit note, not a silent update to the original row. The data model should enforce this structurally: once an invoice is finalized, its record becomes read-only at the database level, and any return/cancellation/correction is a new, linked reversing entry. This preserves a clean audit trail and keeps the business compliant if it's ever audited.

---

## 4. GST and tax compliance

### 4.1 Where the business currently sits

Bachat Bazar is well under the ₹5 crore aggregate turnover threshold that triggers mandatory e-invoicing (real-time IRN generation through the government portal), and that requirement applies specifically to B2B transactions — a B2C retail counter like this one is exempt from IRN generation regardless of turnover, under current rules. This means the system does **not** need to integrate with the government's Invoice Registration Portal right now.

### 4.2 What the invoice does need, right now

- HSN codes aren't mandatory on B2C invoices below the ₹5 crore threshold, but it's worth storing at least a 4-digit HSN code per product anyway — cheap to capture now, useful if the business ever does B2B sales or crosses the threshold later.
- Mandatory fields on every invoice: supplier's legal name/address/GSTIN, a consecutive invoice number unique for the financial year (alphanumeric, hyphens/slashes only, 16 characters max), the invoice date, recipient details for any invoice over ₹50,000, the place-of-supply state (this determines whether CGST+SGST or IGST applies), and a full itemized tax breakdown.
- Rounding: under Section 170 of the CGST Act, tax and total amounts round to the nearest rupee — 50 paise or more rounds up.
- Discounts should appear as explicit line-item reductions on the invoice, not folded into a lump total — this matters both for correct GST taxable-value calculation and for the "you saved ₹X" trust message that's core to the brand.

### 4.3 Future-proofing without over-building

Even though e-invoicing isn't required today, the threshold has been trending downward for years, and there's been public discussion (not yet finalized) of extending mandatory e-invoicing to B2C transactions in the 2026–2027 window. The sensible middle ground: design the invoice schema with room for IRN, signed QR payload, and portal acknowledgment fields now (so a future integration is a bolt-on, not a rewrite), but don't spend actual build time on the integration itself until it's a real requirement.

### 4.4 Worth a real conversation with a CA

Tax slabs likely differ across the categories Bachat Bazar carries (electronics vs. kitchenware vs. fashion), and getting this wrong quietly compounds over every invoice. A one-time, focused conversation with an accountant to confirm the correct HSN-to-tax-rate mapping for your actual product mix is worth doing before this logic gets built, not after.

---

## 5. Barcodes and physical tagging

### 5.1 What a barcode actually is

A barcode carries no product information — it's purely a number (for EAN-13, a 13-digit code with a country/manufacturer prefix, a product code, and a check digit). Software has to look that number up in a database to know what it means. The first time a new product comes in, someone enters its details once and saves them against that barcode; every future scan is then instant. External lookup APIs (UPCitemdb, Go-UPC, etc.) can sometimes auto-fill this on first entry, but they're unreliable for unbranded or regional liquidation stock, which describes a meaningful share of this inventory.

### 5.2 Generating barcodes for stock that doesn't have one

For products with no usable barcode — common for liquidation stock — the system needs to generate its own. Code 128 is the right symbology for internally generated barcodes (dense, alphanumeric, doesn't require the registered manufacturer prefix that EAN-13 needs), with a distinct prefix so internal codes never collide with real manufacturer codes. Label generation should be a **blocking step in the goods-in process** — no stock is marked "received" in the system, and none reaches the shop floor, without its label already printed.

### 5.3 The same barcode, a different lot

When the same product arrives again, possibly at a different cost, the barcode still maps to the same product — the new delivery becomes a new batch entry, not a new product. At checkout, stock is deducted automatically from the oldest available batch (FIFO); staff never need to think about which lot a physical unit came from, and the customer sees one price regardless.

### 5.4 Two tagging models

- **Individually tagged units** — for electronics, branded fashion, boxed goods: a barcode sticker per unit showing MRP struck through, the current selling price, and a savings badge (both ₹ and % off, since different customers think in different terms).
- **Bin/shelf cards** — for loose, uniform, unbranded items (common in liquidation stock): one price card per bin rather than a sticker per unit. This is not just cheaper on labels — it makes price updates trivial (swap one card instead of re-tagging dozens or hundreds of identical units).

The choice between these two is made once per SKU at goods-in time.

### 5.5 Keeping tags trustworthy

Since the store's whole pitch depends on price being trustworthy, tag accuracy has to be enforced structurally, not left to staff discipline:

- Editing a product's selling price automatically flags that SKU as needing a reprint — no silent price changes.
- A daily report lists any SKU where the price changed but no reprint has been logged since.
- The scanned price at checkout is always the system's current price, never whatever's printed on the sticker — staff policy should be to honor the lower of the two if a customer points out a mismatch, which is standard retail practice and protects against disputes.
- Goods-in is a blocking step: a new lot can't be marked "received" until its labels are queued for print.

### 5.6 Why a dedicated "price check station" was rejected

The idea of a kiosk where customers scan an item to double-check its price sounds appealing, but it creates a second queue inside the store — exactly when calm matters most, during a rush — and its mere presence subtly signals "our prices might be wrong," undercutting the trust it's meant to build. The better fix is to make tags reliably accurate in the first place (§5.5), which removes most of the actual need to check. If a fallback is still wanted, several small self-service scan points spread around the floor beat one central station.

### 5.7 The "verify against online price" idea

The instinct — show customers proof that a price really is cheaper than buying online — is a good one, but it comes with a real risk: putting a live link to a competitor's shopping cart on your own price tag can send a customer to complete the purchase there instead, especially if even a few prices are wrong or stale (a known retail failure pattern called showrooming). If this is pursued, it should be per-SKU, verified and timestamped by the system (not a blanket store-wide claim), and any QR code should point to a page the store controls — showing "verified on [date]: was ₹X elsewhere, we charge ₹Y" — rather than linking straight to Amazon or Flipkart. Realistically low priority for v1, since matching unbranded liquidation stock against online listings is the hardest case to get right anyway.

---

## 6. Payments — UPI

Two implementation tiers, in order of complexity and cost:

- **Gateway-less dynamic QR (P2P)**: the POS constructs a standard UPI deep link (payee address, name, transaction reference, amount) and renders it as a QR code. No gateway fees, since it's a peer-to-peer transfer. The tradeoff: the system has no automatic way to know if the payment succeeded, so the cashier has to manually enter the UTR (the transaction reference number shown on the customer's payment app) against the invoice for reconciliation.
- **Gateway-integrated (Razorpay, PayU, Paytm, etc.)**: the backend requests a payment link from the gateway, renders it as a QR, and the gateway calls back via webhook once payment completes — fully automated confirmation, at the cost of gateway fees and more integration complexity (including handling webhook delivery reliably for a self-hosted setup, typically via a reverse tunnel like Cloudflare Tunnel).

Starting with the gateway-less approach is reasonable for a single-outlet business, with the gateway-integrated path as a clear upgrade once transaction volume makes manual UTR entry error-prone or tedious.

---

## 7. Hardware and Java-specific tooling

- **JavaFX**, packaged with `jpackage` — bundles a minimal JRE, so end users don't need Java installed separately.
- **JavaPOS (UnifiedPOS)** — an actual industry standard for retail peripheral drivers (printers, scanners, cash drawers, scales). Worth checking whether your chosen hardware models ship JavaPOS-compatible drivers before committing to specific models — it can save a lot of low-level integration work if available.
- **escpos-coffee** — a mature open-source Java library for ESC/POS receipt printer commands, as a fallback where JavaPOS drivers aren't available.
- **ZXing** — the standard Java barcode library, for both generating internal SKU barcodes and decoding (useful if camera-based scanning is ever wanted as a backup to USB scanners).
- **TSPL** (the label printer protocol) has no strong Java wrapper — expect to hand-construct TSPL command strings and send them as raw bytes over USB (via `usb4java` or similar). This is the one area of hardware integration likely to need the most manual back-and-forth with Claude, since it's a narrow, vendor-specific protocol with thin public documentation.
- **A note on reliability**: sending parallel commands to a single USB port (e.g., printing a label while also triggering the cash drawer) can lock up or crash the terminal. All hardware writes should be funneled through a single serialized queue, not fired independently from wherever in the code needs them.

Indicative hardware, 2026 India pricing (worth reconfirming before budgeting): TSPL label printers roughly ₹6,500–₹11,500, ESC/POS receipt printers roughly ₹5,500–₹8,500, USB-HID barcode scanners roughly ₹1,500–₹4,000.

---

## 8. Open-sourcing the project

### 8.1 License

**AGPL-3.0**, chosen specifically because it closes a gap that plain GPL leaves open: GPL only requires releasing source code when you *distribute* a modified version. If someone takes the code, modifies it, and runs it purely as a hosted service (never handing out the binary or code itself), they have no obligation to release their changes under plain GPL — this is sometimes called the SaaS loophole. AGPL adds a clause that closes this: modifying the code and letting users interact with it over a network also triggers the obligation to publish those modifications. Since a POS is exactly the kind of software that tends to get turned into a hosted SaaS product, this protection is directly relevant here. The tradeoff is that some companies are cautious about touching AGPL code for their own legal reasons, which can mean somewhat less commercial adoption than a permissive license like MIT — an acceptable tradeoff given the explicit goal of keeping the project open.

Practically: a `LICENSE` file with the full AGPL-3.0 text at the repo root, a short copyright header in source files, the license referenced in the README, and the license field set in the build configuration.

### 8.2 Structure

A monorepo-style layout with the JavaFX terminal, the Spring Boot backend, and the web dashboard as separate top-level components, sharing the backend as their single source of truth for schema and business logic. A Docker setup for the backend makes self-hosting by other retailers a one-command affair, which matters a lot for actual adoption.

### 8.3 What makes solo open-source projects survive or stall

Two cautionary examples came up in research: **Lemon POS** stalled because its C++/KDE-specific codebase was hard to build across platforms, never attracting an active community; **Chromis POS** (forked from uniCenta) stalled because manual XML-based database migrations caused different installs to drift into incompatible states, frustrating both users and developers. Neither failed because of a flawed core idea — both failed on build fragility and operational friction. The practical lessons: keep the self-hosting path simple and automated (Docker, not manual setup steps), automate schema migrations properly rather than relying on manual scripts, and keep the roadmap tightly scoped to the core (sales, tax compliance, inventory, payments) with anything else built as optional, separate modules rather than bolted onto the core.

### 8.4 A future decision, not urgent now

If there's ever a wish to offer a commercial license alongside the open-source AGPL one (a common way open-source infrastructure projects sustain themselves), that requires clean ownership of all contributed code — meaning outside contributors would need to sign a Contributor License Agreement (CLA). Not something to set up for a solo v1, but much harder to add retroactively once other people's AGPL-licensed contributions are mixed into the codebase, so worth deciding consciously before the first outside pull request is accepted.

---

## 9. Known pitfalls, and how each is addressed above

- **Data loss from browser storage eviction** — not applicable here, since JavaFX is a native app writing directly to disk rather than relying on browser storage (this risk is specific to browser/Electron-with-IndexedDB approaches).
- **Illegal post-facto invoice edits** — addressed by the append-only invoice ledger (§3.4).
- **USB port crashes from concurrent hardware writes** — addressed by serializing all hardware commands through one queue (§7).
- **Sync ID collisions** — addressed preemptively by using UUIDs everywhere, even before sync exists (§2.4).
- **Solo-maintainer burnout / project stalling** — addressed by keeping the roadmap scoped, automating self-hosting via Docker, and learning from Lemon POS/Chromis POS's specific failure modes (§8.3).

---

## 10. What's deliberately out of scope for now

- Multi-outlet sync (replication across stores) — build only once there's an actual second outlet.
- Live e-invoicing/IRP integration — schema is prepared for it, the integration itself isn't built.
- Automated (gateway-based) UPI payment confirmation — start with the manual-UTR flow.
- The "verified cheaper than online" price-comparison feature.
- Digital shelf labels (electronic shelf edge tags) — a real category worth knowing exists, but overkill for a single-outlet start.

---

## 11. Points worth more thought before locking anything in

A running list of decisions that were discussed but not fully closed:

1. **ORM/data-access approach** — Hibernate/JPA (faster to get moving, more implicit behavior) vs. plain JDBC with jOOQ (more control, likely a better fit for hand-tuned ledger and costing queries).
2. **When the JSON-attributes hybrid model would need reconsidering** — fine at a few thousand SKUs; worth a deliberate checkpoint if the catalog grows much larger.
3. **A recurring process for tracking the e-invoicing threshold/B2C rollout** — since it's been trending downward for years and B2C mandatory e-invoicing has been publicly discussed (not yet finalized) for 2026–2027. Also, a one-time CA consultation to confirm exact tax slabs across your specific product categories.
4. **Whether the "verified cheaper than online" feature is worth building at all in v1**, given the showrooming risk and the difficulty of matching unbranded liquidation stock to online listings.
5. **Manual UTR entry vs. going straight to a payment gateway integration** — depends on real transaction volume, which is easier to judge once the store is actually running on this system.
6. **Final printer/scanner hardware selection** — should be informed by checking JavaPOS driver availability for specific candidate models first, since that materially changes integration effort.
7. **Whether to require a CLA from future outside contributors**, relevant only if commercial dual-licensing is ever a real consideration.

---

This report reflects the current state of thinking, not final decisions. The next step, in your words, is to walk from here into your own spec.
