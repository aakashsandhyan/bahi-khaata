# Tasks — Nav Shell on shadcn/ui

## 1. Toolchain

- [ ] 1.1 Add Tailwind v4 to `dashboard/web` (vite plugin, `@import "tailwindcss"` layer) with `styles.css` imported after it, and verify every existing screen still renders unchanged in dev
- [ ] 1.2 Initialise shadcn/ui (components.json, cn util, CSS variables) and vendor the `Sidebar`, `Button`, `Tooltip`, `Separator` components
- [ ] 1.3 Run `deploy/build-release.sh` and confirm the bundle builds, the jar serves it, and size growth is acceptable

## 2. Navigation structure

- [ ] 2.1 Define `NAV_GROUPS` in one module: group name → items (label, icon, `View` id), covering Checkout / Inventory / Pricing & Labels / Configuration with Suppliers under Configuration
- [ ] 2.2 Delete dead `Pricing.tsx` and `BulkPrint.tsx`; confirm no imports break

## 3. Desktop shell

- [ ] 3.1 Replace the `topnav` in `App.tsx` with the shadcn Sidebar rendering `NAV_GROUPS`; the existing `View` state switch drives the content area unchanged
- [ ] 3.2 Implement till focus mode: selecting Till collapses the sidebar to the icon rail, any other selection restores it
- [ ] 3.3 Remove retired `.topnav` rules from `styles.css`

## 4. Phone shell

- [ ] 4.1 Bottom tab bar at phone widths (Unpacking, Capture, Review) with 16px+ text and 12px+ padding targets; desktop sidebar hidden there
- [ ] 4.2 Preserve entry behavior: phones land on Unpacking, `#capture` opens Capture; add bottom safe-area padding so the bar never covers screen content

## 5. Verify

- [ ] 5.1 Walk every routed screen through the new nav and compare against the old flat bar (content and behavior identical)
- [ ] 5.2 Check the real shop window sizes: till monitor (focus mode, rail width) and phone (tab bar reach, no covered controls)
- [ ] 5.3 `openspec validate --change nav-shell-shadcn` and mark tasks done
