import { useEffect, useState } from 'react'
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
import { Suppliers } from './Suppliers'
import { PrinterConfig } from './admin/PrinterConfig'
import { ReceiptPrinterConfig } from './admin/ReceiptPrinterConfig'
import { BillSettings } from './admin/BillSettings'

type View =
  | 'checkout' | 'sales' | 'lots' | 'receiving' | 'unpacking' | 'prep'
  | 'pricing' | 'review' | 'reprint' | 'capture' | 'catalog' | 'suppliers'
  | 'printer-config' | 'receipt-config' | 'bill-settings'

/**
 * The admin dashboard shell.
 *
 * Pricing is the hub: pick a lot, bring a product in by scan or by hand, price and barcode it, and
 * print its label. Captures made from a phone land in the review queue for a desk to finish, and
 * labels not printed at pricing are caught up in bulk.
 */
export function App() {
  // Phones are operators' devices and their nav is hidden (CSS), so they land on a single screen.
  // A phone opened at #capture is a capture station; otherwise it is an unpacking station. Wider
  // screens open on the till.
  const isPhone = typeof window !== 'undefined' && window.innerWidth <= 760
  const phoneLanding: View =
    typeof window !== 'undefined' && window.location.hash === '#capture' ? 'capture' : 'unpacking'
  const [view, setView] = useState<View>(isPhone ? phoneLanding : 'checkout')

  // Badge the header when this is the sandbox instance (same app, throwaway DB copy), so nobody
  // mistakes it for the live shop. The flag comes from the backend, set by start-sandbox.bat.
  const [sandbox, setSandbox] = useState(false)
  useEffect(() => {
    fetch('/api/instance')
      .then((r) => r.json())
      .then((d) => setSandbox(!!d.sandbox))
      .catch(() => {})
  }, [])

  return (
    <>
      <nav className="topnav">
        <span className="brand" style={{ display: 'inline-flex', flexDirection: 'column', lineHeight: 1.1 }}>
          Bachat Bazaar
          {sandbox && (
            <small style={{ fontSize: 10, fontWeight: 700, letterSpacing: 1.5, color: '#b45309' }}>
              SANDBOX
            </small>
          )}
        </span>
        <button className={view === 'checkout' ? 'on' : ''} onClick={() => setView('checkout')}>Till</button>
        <button className={view === 'sales' ? 'on' : ''} onClick={() => setView('sales')}>Sales</button>
        <button className={view === 'lots' ? 'on' : ''} onClick={() => setView('lots')}>Lots</button>
        <button className={view === 'receiving' ? 'on' : ''} onClick={() => setView('receiving')}>Receiving</button>
        <button className={view === 'unpacking' ? 'on' : ''} onClick={() => setView('unpacking')}>Unpacking</button>
        <button className={view === 'prep' ? 'on' : ''} onClick={() => setView('prep')}>Prep</button>
        <button className={view === 'pricing' ? 'on' : ''} onClick={() => setView('pricing')}>Pricing</button>
        <button className={view === 'review' ? 'on' : ''} onClick={() => setView('review')}>Review</button>
        <button className={view === 'reprint' ? 'on' : ''} onClick={() => setView('reprint')}>Reprint</button>
        <button className={view === 'catalog' ? 'on' : ''} onClick={() => setView('catalog')}>Catalog</button>
        <button className={view === 'suppliers' ? 'on' : ''} onClick={() => setView('suppliers')}>Suppliers</button>
        <button className={view === 'printer-config' ? 'on' : ''} onClick={() => setView('printer-config')}>Printer</button>
        <button className={view === 'receipt-config' ? 'on' : ''} onClick={() => setView('receipt-config')}>Receipt</button>
        <button className={view === 'bill-settings' ? 'on' : ''} onClick={() => setView('bill-settings')}>Bill</button>
      </nav>
      <main>
        {view === 'checkout' ? <Checkout />
          : view === 'sales' ? <Sales />
          : view === 'lots' ? <LotManagement />
          : view === 'receiving' ? <Receiving />
          : view === 'unpacking' ? <Unpacking />
          : view === 'prep' ? <Prep />
          : view === 'pricing' ? <PricingWorkbench />
          : view === 'review' ? <ReviewQueue />
          : view === 'reprint' ? <Reprint />
          : view === 'capture' ? <MobileCapture />
          : view === 'catalog' ? <Catalog />
          : view === 'suppliers' ? <Suppliers />
          : view === 'printer-config' ? <PrinterConfig />
          : view === 'receipt-config' ? <ReceiptPrinterConfig />
          : view === 'bill-settings' ? <BillSettings />
          : <Checkout />}
      </main>
    </>
  )
}
