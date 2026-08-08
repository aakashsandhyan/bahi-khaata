# Nav Shell on shadcn/ui

## Why

The dashboard has outgrown its single flat top bar: twelve screens in one row of buttons, no grouping, nothing tuned for the phone (the nav is simply hidden there), and no place for a future admin gate to hook into. Operators hunt through unrelated tabs; sensitive screens (suppliers, printer config) sit beside shop-floor ones. The nav needs structure before the operator/admin split can land on top of it.

## What Changes

- Adopt Tailwind v4 + shadcn/ui for the **app shell only** — existing screens render inside the new shell unchanged, and migrate to shadcn components only opportunistically later.
- Replace the flat top bar with a **grouped collapsible sidebar** (desktop): Checkout / Inventory / Pricing & Labels / Configuration, sub-items per group.
- **Suppliers moves under Configuration** (it exposes purchase figures; it is not a shop-floor screen).
- **Till focus mode**: opening the Till collapses the sidebar to icons so scanning owns the screen.
- **Phone gets a bottom tab bar** with operator screens only (Unpacking, Capture, Review), replacing "nav hidden by CSS"; touch targets follow the MobileCapture pattern (16px+ font, fat padding).
- Delete the dead, unrouted screens `Pricing.tsx` and `BulkPrint.tsx`.
- Explicitly **out of scope**: authentication, PIN gate, role-based hiding, and any per-screen redesign — those are follow-up changes. The sidebar's group structure is the seam the gate will later attach to.

## Capabilities

### New Capabilities
- `dashboard-shell`: the dashboard's navigation shell — grouped sidebar on desktop, bottom tab bar on phones, till focus mode, and the group-to-screen mapping.

### Modified Capabilities

(none — every existing capability keeps its requirements; only how its screen is reached changes, which is `dashboard-shell`'s concern)

## Impact

- `dashboard/web/` gains dependencies: `tailwindcss` v4, shadcn/ui components (copied in, Radix under the hood). All assets bundle — nothing loads from a CDN, so the offline shop LAN and the fat-jar build (`deploy/build-release.sh`) are unaffected beyond bundle size.
- `dashboard/web/src/App.tsx` — the view switch and top bar are replaced by the shell; the `View` union and screen components stay.
- `dashboard/web/src/styles.css` — shell rules (`.topnav`, phone hiding) retire; screen-level styles stay untouched.
- `dashboard/web/src/Pricing.tsx`, `BulkPrint.tsx` — deleted (unrouted dead code).
- Phone entry behavior (`#capture` hash, width-based landing) is preserved but re-expressed through the shell.
