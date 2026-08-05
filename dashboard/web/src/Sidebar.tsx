import { useEffect, useState } from 'react'

export type View =
  | 'dashboard' | 'checkout' | 'sales' | 'lots' | 'receiving' | 'unpacking' | 'prep'
  | 'pricing' | 'review' | 'reprint' | 'capture' | 'catalog' | 'suppliers'
  | 'printer-config' | 'receipt-config' | 'bill-settings'

type NavItem = { view: View; label: string; kicker: string }
type NavGroup = { label: string; items: NavItem[] }

/**
 * One config drives both the sidebar entries and the per-screen header
 * (kicker + title), so the two can never disagree. Capture is phone-only
 * and deliberately absent.
 */
export const NAV_GROUPS: NavGroup[] = [
  {
    label: 'Operations',
    items: [
      { view: 'dashboard', label: 'Dashboard', kicker: 'Overview' },
      { view: 'receiving', label: 'Receiving', kicker: 'Warehouse' },
      { view: 'unpacking', label: 'Unpacking', kicker: 'Warehouse' },
      { view: 'prep', label: 'Prep', kicker: 'Remediation' },
      { view: 'pricing', label: 'Pricing', kicker: 'Shelf pricing' },
      { view: 'lots', label: 'Lots', kicker: 'Deliveries' },
      { view: 'review', label: 'Review', kicker: 'Capture queue' },
    ],
  },
  {
    label: 'Selling',
    items: [
      { view: 'checkout', label: 'Till', kicker: 'Point of sale' },
      { view: 'sales', label: 'Sales', kicker: 'Sale history' },
      { view: 'reprint', label: 'Reprint', kicker: 'Labels' },
    ],
  },
  {
    label: 'Back office',
    items: [
      { view: 'catalog', label: 'Catalog', kicker: 'All products' },
      { view: 'suppliers', label: 'Suppliers', kicker: 'Sourcing' },
      { view: 'printer-config', label: 'Printer', kicker: 'Admin' },
      { view: 'receipt-config', label: 'Receipt printer', kicker: 'Admin' },
      { view: 'bill-settings', label: 'Bill settings', kicker: 'Admin' },
    ],
  },
]

export function screenMeta(view: View): { kicker: string; title: string } {
  for (const g of NAV_GROUPS) {
    const hit = g.items.find((i) => i.view === view)
    if (hit) return { kicker: hit.kicker, title: hit.label }
  }
  return { kicker: 'Phone', title: 'Capture' }
}

export function Sidebar({
  view, onNavigate, sandbox, open, onClose,
}: {
  view: View
  onNavigate: (v: View) => void
  sandbox: boolean
  open: boolean
  onClose: () => void
}) {
  // The operator name is set on the Pricing screen and shared app-wide.
  const [operator, setOperator] = useState(() => localStorage.getItem('pricing.operator') ?? '')
  useEffect(() => {
    const read = () => setOperator(localStorage.getItem('pricing.operator') ?? '')
    window.addEventListener('storage', read)
    window.addEventListener('focus', read)
    return () => { window.removeEventListener('storage', read); window.removeEventListener('focus', read) }
  }, [])

  return (
    <>
      {open && <div className="sidebar-scrim" onClick={onClose} />}
      <aside className={open ? 'sidebar open' : 'sidebar'}>
        <div className="sidebar-brand">
          <div className="sidebar-name">BACHAT BAZAAR</div>
          <div className="sidebar-sub">{sandbox ? 'SANDBOX — throwaway copy' : 'Bhopal · liquidation retail'}</div>
        </div>
        <nav className="sidebar-nav">
          {NAV_GROUPS.map((g) => (
            <div key={g.label}>
              <div className="sidebar-group">{g.label}</div>
              {g.items.map((i) => (
                <button
                  key={i.view}
                  type="button"
                  className={view === i.view ? 'sidebar-item on' : 'sidebar-item'}
                  onClick={() => { onNavigate(i.view); onClose() }}
                >
                  {i.label}
                </button>
              ))}
            </div>
          ))}
        </nav>
        <div className="sidebar-foot">
          {operator
            ? <>Signed in as <strong>{operator}</strong></>
            : <span className="text-muted">No operator set — see Pricing</span>}
        </div>
      </aside>
    </>
  )
}
