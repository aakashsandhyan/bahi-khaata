# Design — Nav Shell on shadcn/ui

## Context

All routing lives in `dashboard/web/src/App.tsx`: a `useState<View>` switch, a flat `<nav class="topnav">` of twelve buttons, and `styles.css` hiding that bar entirely under 760px. Phones land on Unpacking (or Capture via `#capture`); desktops land on the Till. Screens style themselves with inline styles plus `styles.css`; there is no component library, no Tailwind, no router. The app ships as static files baked into the backend fat jar and runs on an offline shop LAN — every asset must bundle.

The operator/admin gate (a follow-up change) needs a nav structure it can attach to; today there is nothing to hide or show but a flat row.

## Goals / Non-Goals

**Goals:**
- Grouped, collapsible sidebar on desktop: Checkout / Inventory / Pricing & Labels / Configuration.
- Bottom tab bar on phones with the operator screens, replacing "nav hidden by CSS".
- Till focus mode — sidebar collapses to icon rail when the Till is active.
- shadcn/ui + Tailwind v4 installed, applied to the shell only.
- Delete dead `Pricing.tsx` and `BulkPrint.tsx`.

**Non-Goals:**
- No authentication, PIN, or role-based hiding (follow-up change; the group structure is its seam).
- No redesign of any screen's internals — they render inside the shell as-is.
- No router library; the `View` state switch stays.

## Decisions

1. **Shell-only adoption (option B).** Full shadcn migration would re-style twelve working screens at once; pattern-copying without the library would re-implement collapse, focus management, and accessibility that shadcn's `Sidebar` gives free. The shell is the highest-leverage slice: template UX now, screens migrate opportunistically later.
2. **shadcn `Sidebar` block over a hand-rolled two-tier tab bar.** Grouped sidebar is the current template standard, scales past twelve screens, collapses to an icon rail (which *is* the till focus mode), and its `SidebarGroup` maps one-to-one onto the future gate unit. Two-tier tabs would need custom responsive and collapse behavior for no gain.
3. **Tailwind v4 scoped by coexistence, not conversion.** Tailwind's preflight reset can fight `styles.css`. Import order pins the cascade: preflight first, `styles.css` after, so existing screen rules win where they collide. Screens keep inline styles; only shell components use utility classes.
4. **Group → screen mapping** (the sidebar's content):
   - Checkout: Till
   - Inventory: Lots, Receiving, Unpacking, Prep, Catalog
   - Pricing & Labels: Pricing, Review, Reprint
   - Configuration: Printer, Suppliers
   Suppliers moves under Configuration because it exposes purchase figures and supplier PII — not a shop-floor screen.
5. **Phone = bottom tab bar, not the sidebar.** Three tabs (Unpacking, Capture, Review) — thumb-reachable, MobileCapture's touch sizing (16px+ text, 12px+ padding). Width breakpoint stays 760px; `#capture` hash entry is preserved.
6. **View state stays; no router.** The shell renders nav from a single `NAV_GROUPS` structure (group → items → `View` id) so the future gate filters one data structure, not JSX.

## Risks / Trade-offs

- [Tailwind preflight resets existing screen styling] → import `styles.css` after Tailwind layers; visual smoke-check every screen against the running app before merge.
- [Bundle grows (Radix + Tailwind)] → all local, no CDN; verify `vite build` output and jar size in `build-release.sh` still acceptable.
- [Sidebar eats horizontal space on the 760–1024px window the shop uses] → collapsible rail by default on narrow desktop; Till auto-collapses.
- [Phone bottom bar covers content] → reserve safe-area padding at screen bottom on phone widths.

## Migration Plan

Ship as one frontend change; no data, no API, no migrations. Rollback = revert the commit (screens themselves untouched). Deploy via the normal jar build.

## Open Questions

- Which three phone tabs exactly — Unpacking / Capture / Review assumed; Reprint may deserve a slot.
- Whether the narrow-desktop default is expanded or rail — decide against the real till monitor during verify.
