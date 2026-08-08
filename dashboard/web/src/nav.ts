import {
  Camera,
  ClipboardCheck,
  BookOpen,
  Inbox,
  Package,
  PackageOpen,
  Printer,
  RotateCcw,
  ShoppingCart,
  Tags,
  Truck,
  Wrench,
  type LucideIcon,
} from 'lucide-react'

/** Every screen the shell can show. `capture` is phone-only and lives outside the sidebar. */
export type View =
  | 'checkout' | 'lots' | 'receiving' | 'unpacking' | 'prep'
  | 'pricing' | 'review' | 'reprint' | 'capture' | 'catalog' | 'suppliers' | 'printer-config'

export type NavItem = { view: View; label: string; icon: LucideIcon }
export type NavGroup = { label: string; items: NavItem[] }

/**
 * The whole navigation in one structure: group → screens. The sidebar renders it, and a later
 * role gate filters it — hide a group here and every way to reach its screens goes with it.
 * Suppliers sits under Configuration deliberately: it shows purchase figures, not shop-floor data.
 */
export const NAV_GROUPS: NavGroup[] = [
  {
    label: 'Checkout',
    items: [{ view: 'checkout', label: 'Till', icon: ShoppingCart }],
  },
  {
    label: 'Inventory',
    items: [
      { view: 'lots', label: 'Lots', icon: Package },
      { view: 'receiving', label: 'Receiving', icon: Inbox },
      { view: 'unpacking', label: 'Unpacking', icon: PackageOpen },
      { view: 'prep', label: 'Prep', icon: Wrench },
      { view: 'catalog', label: 'Catalog', icon: BookOpen },
    ],
  },
  {
    label: 'Pricing & Labels',
    items: [
      { view: 'pricing', label: 'Pricing', icon: Tags },
      { view: 'review', label: 'Review', icon: ClipboardCheck },
      { view: 'reprint', label: 'Reprint', icon: RotateCcw },
    ],
  },
  {
    label: 'Configuration',
    items: [
      { view: 'printer-config', label: 'Printer', icon: Printer },
      { view: 'suppliers', label: 'Suppliers', icon: Truck },
    ],
  },
]

/** The phone's bottom tab bar — the operator screens, thumb-reachable. */
export const PHONE_TABS: NavItem[] = [
  { view: 'unpacking', label: 'Unpack', icon: PackageOpen },
  { view: 'capture', label: 'Capture', icon: Camera },
  { view: 'review', label: 'Review', icon: ClipboardCheck },
]
