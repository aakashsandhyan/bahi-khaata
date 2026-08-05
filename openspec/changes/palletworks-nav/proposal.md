## Why

Sixteen desktop screens have accumulated across three Palletworks phases plus the upstream merges, and the sidebar now carries entries the shop does not use: three separate admin screens for what is one configuration concern, a Catalog screen whose jobs Inventory and Item detail have largely absorbed, and a Till that has recorded zero sales because the JavaFX terminal is the register (its web replacement is a later phase, decided 2026-08-05). Phase 4 consolidates navigation to twelve entries so every remaining entry is something the shop actually opens.

## What Changes

- **Settings**: one Back-office screen with three tabs — Label printer, Receipt printer, Bill — each tab hosting the existing component unchanged. The three separate admin entries are removed.
- **Catalog folds into Inventory**: Inventory gains a scope control (On floor / On paper / All) so the on-paper gap list — products known from a manifest but never counted — lives beside the stock view; category editing joins Item detail's actions; the product-centric counting grid opens from Item detail instead of Catalog. The Catalog screen and its nav entry are then deleted.
- **Till hidden from desktop navigation**: entry removed; the screen stays in code, reachable by `#till` hash (same pattern as `#capture`) until the web-till-parity phase revives it.
- Affected smoke tests rewritten rather than dropped; suite size stays about the same.

## Capabilities

### New Capabilities

None. Every change re-homes existing capability behavior.

### Modified Capabilities

- `dashboard-shell`: nav groups shrink to twelve entries; Till becomes hash-reachable instead of listed; Settings replaces the three admin entries (requirement-level changes to the navigation lists and screen-visibility rules).
- `inventory-view`: gains the scope control and the on-paper listing (requirement-level addition to the view's filters).
- `item-detail`: gains category editing and the counting-grid entry point (requirement-level addition to actions).
- `product-catalog`: browsing/search/gap-finding requirements move to their new homes; the standalone Catalog screen requirement is removed with migration notes.
- `dashboard-smoke-tests`: per-screen coverage list reworded for the new nav (twelve listed screens plus hash-reachable Till and phone Capture).

## Impact

- Frontend only plus one small backend addition if category editing needs an endpoint (check: Catalog's existing edit endpoint is reused, not duplicated). No schema change, no printer paths, no money paths.
- `Sidebar.tsx`/`App.tsx` (nav config, hash landing), new `Settings.tsx` (tab shell), `Inventory.tsx` (scope seg), `ItemDetail.tsx` (category edit, counting entry), `Catalog.tsx` deleted last, e2e specs 01/08/11/15/16 rewritten.
- Risk: counting and gap-finding flows breaking mid-fold — mitigated by re-homing entry points first, deleting Catalog only after the rewritten suite is green.
