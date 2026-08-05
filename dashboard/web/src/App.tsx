import { useEffect, useState } from 'react'
import { Dashboard } from './Dashboard'
import { Checkout } from './Checkout'
import { Sales } from './Sales'
import { LotManagement } from './LotManagement'
import { Receiving } from './Receiving'
import { Unpacking } from './Unpacking'
import { PricingWorkbench } from './PricingWorkbench'
import { ReviewQueue } from './ReviewQueue'
import { Reprint } from './Reprint'
import { MobileCapture } from './MobileCapture'
import { Prep } from './Prep'
import { Catalog } from './Catalog'
import { Inventory } from './Inventory'
import { ItemDetail } from './ItemDetail'
import { Suppliers } from './Suppliers'
import { PrinterConfig } from './admin/PrinterConfig'
import { ReceiptPrinterConfig } from './admin/ReceiptPrinterConfig'
import { BillSettings } from './admin/BillSettings'
import { Sidebar, screenMeta, type View } from './Sidebar'

/**
 * The admin dashboard shell: a grouped sidebar on desktop, a drawer on phones.
 *
 * Pricing is the hub: pick a lot, bring a product in by scan or by hand, price and barcode it, and
 * print its label. Captures made from a phone land in the review queue for a desk to finish, and
 * labels not printed at pricing are caught up in bulk.
 */
export function App() {
  // Phones are operators' devices, so they land on a single screen. A phone opened at #capture is
  // a capture station; otherwise it is an unpacking station. Wider screens open on the till.
  const isPhone = typeof window !== 'undefined' && window.innerWidth <= 760
  const phoneLanding: View =
    typeof window !== 'undefined' && window.location.hash === '#capture' ? 'capture' : 'unpacking'
  const [view, setView] = useState<View>(isPhone ? phoneLanding : 'dashboard')
  const [drawer, setDrawer] = useState(false)

  // Item detail carries a product id rather than being param-less like every other nav switch
  // (design decision D9 of palletworks-inventory): opening it from an Inventory row or the
  // Catalog panel both go through this one callback, so neither screen needs its own notion of
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
            : view === 'lots' ? <LotManagement />
            : view === 'receiving' ? <Receiving />
            : view === 'unpacking' ? <Unpacking />
            : view === 'prep' ? <Prep />
            : view === 'pricing' ? <PricingWorkbench />
            : view === 'review' ? <ReviewQueue />
            : view === 'reprint' ? <Reprint />
            : view === 'capture' ? <MobileCapture />
            : view === 'catalog' ? <Catalog onOpenItem={onOpenItem} />
            : view === 'inventory' ? <Inventory onOpenItem={onOpenItem} />
            : view === 'item-detail' ? (
                detailProductId ? (
                  <ItemDetail productId={detailProductId} onBack={() => setView('inventory')} />
                ) : null
              )
            : view === 'suppliers' ? <Suppliers />
            : view === 'printer-config' ? <PrinterConfig />
            : view === 'receipt-config' ? <ReceiptPrinterConfig />
            : view === 'bill-settings' ? <BillSettings />
            : <Sales />}
        </main>
      </div>
    </div>
  )
}
