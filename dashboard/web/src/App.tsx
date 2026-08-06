import { useEffect, useState } from 'react'
import { Dashboard } from './Dashboard'
import { Checkout } from './Checkout'
import { Sales } from './Sales'
import { Intake } from './Intake'
import { Unpacking } from './Unpacking'
import { PricingWorkbench } from './PricingWorkbench'
import { ReviewQueue } from './ReviewQueue'
import { Reprint } from './Reprint'
import { MobileCapture } from './MobileCapture'
import { Prep } from './Prep'
import { Inventory } from './Inventory'
import { ItemDetail } from './ItemDetail'
import { Suppliers } from './Suppliers'
import { Settings } from './Settings'
import { Sidebar, screenMeta, type View } from './Sidebar'

/**
 * The admin dashboard shell: a grouped sidebar on desktop, a drawer on phones.
 *
 * Pricing is the hub: pick a lot, bring a product in by scan or by hand, price and barcode it, and
 * print its label. Captures made from a phone land in the review queue for a desk to finish, and
 * labels not printed at pricing are caught up in bulk.
 */
// Resolved once, at load, on any viewport — never by a `hashchange` listener (design decision D7
// of palletworks-nav; Till's revival as a live-routed screen is a later phase). `#till` and
// `#capture` are the two hash back-doors to a screen unlisted in the sidebar; anything else falls
// through to the ordinary per-viewport default a phone (an unpacking station) or a desktop (the
// dashboard) already lands on.
function landingView(isPhone: boolean): View {
  const hash = typeof window !== 'undefined' ? window.location.hash : ''
  if (hash === '#till') return 'checkout'
  if (hash === '#capture') return 'capture'
  return isPhone ? 'unpacking' : 'dashboard'
}

export function App() {
  // Phones are operators' devices, so they land on a single screen by default.
  const isPhone = typeof window !== 'undefined' && window.innerWidth <= 760
  const [view, setView] = useState<View>(() => landingView(isPhone))
  const [drawer, setDrawer] = useState(false)

  // Item detail carries a product id rather than being param-less like every other nav switch
  // (design decision D9 of palletworks-inventory): opening it from any Inventory row — On floor,
  // On paper, or All (D9 of palletworks-nav, once the Catalog panel that used to share this job
  // is deleted) — goes through this one callback, so the screen needs no notion of its own for
  // "how to get to item detail".
  const [detailProductId, setDetailProductId] = useState<string | null>(null)
  const onOpenItem = (productId: string) => {
    setDetailProductId(productId)
    setView('item-detail')
  }

  // Badge the shell when this is the sandbox instance (same app, throwaway DB copy), so nobody
  // mistakes it for the live shop. The flag comes from the backend, set by start-sandbox.bat.
  const [sandbox, setSandbox] = useState(false)
  useEffect(() => {
    fetch('/api/instance')
      .then((r) => r.json())
      .then((d) => setSandbox(!!d.sandbox))
      .catch(() => {})
  }, [])

  const meta = screenMeta(view)

  return (
    <div className="shell">
      <Sidebar view={view} onNavigate={setView} sandbox={sandbox} open={drawer} onClose={() => setDrawer(false)} />
      <div className="shell-main">
        <header className="shell-head">
          <button type="button" className="shell-burger" onClick={() => setDrawer(true)} aria-label="Open navigation">
            ☰
          </button>
          <div className="shell-head-text">
            <div className="kicker">{meta.kicker}</div>
            <h4 className="shell-title">{meta.title}</h4>
          </div>
          {sandbox && <span className="shell-sandbox">SANDBOX</span>}
        </header>
        <main className="shell-content">
          {view === 'dashboard' ? <Dashboard onNavigate={setView} />
            : view === 'checkout' ? <Checkout />
            : view === 'intake' ? <Intake onNavigate={setView} />
            : view === 'unpacking' ? <Unpacking />
            : view === 'prep' ? <Prep />
            : view === 'pricing' ? <PricingWorkbench />
            : view === 'review' ? <ReviewQueue />
            : view === 'reprint' ? <Reprint />
            : view === 'capture' ? <MobileCapture />
            : view === 'inventory' ? <Inventory onOpenItem={onOpenItem} />
            : view === 'item-detail' ? (
                detailProductId ? (
                  <ItemDetail productId={detailProductId} onBack={() => setView('inventory')} />
                ) : null
              )
            : view === 'suppliers' ? <Suppliers />
            : view === 'settings' ? <Settings />
            : <Sales />}
        </main>
      </div>
    </div>
  )
}
