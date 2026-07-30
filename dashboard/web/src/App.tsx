import { useState } from 'react'
import { Checkout } from './Checkout'
import { LotManagement } from './LotManagement'
import { Receiving } from './Receiving'
import { Unpacking } from './Unpacking'
import { PricingWorkbench } from './PricingWorkbench'
import { ReviewQueue } from './ReviewQueue'
import { BulkPrint } from './BulkPrint'
import { MobileCapture } from './MobileCapture'
import { Prep } from './Prep'
import { Catalog } from './Catalog'
import { Suppliers } from './Suppliers'
import { PrinterConfig } from './admin/PrinterConfig'

type View =
  | 'checkout' | 'lots' | 'receiving' | 'unpacking' | 'prep'
  | 'pricing' | 'review' | 'labels' | 'capture' | 'catalog' | 'suppliers' | 'printer-config'

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

  return (
    <>
      <nav className="topnav">
        <span className="brand">Bachat Baazar</span>
        <button className={view === 'checkout' ? 'on' : ''} onClick={() => setView('checkout')}>Till</button>
        <button className={view === 'lots' ? 'on' : ''} onClick={() => setView('lots')}>Lots</button>
        <button className={view === 'receiving' ? 'on' : ''} onClick={() => setView('receiving')}>Receiving</button>
        <button className={view === 'unpacking' ? 'on' : ''} onClick={() => setView('unpacking')}>Unpacking</button>
        <button className={view === 'prep' ? 'on' : ''} onClick={() => setView('prep')}>Prep</button>
        <button className={view === 'pricing' ? 'on' : ''} onClick={() => setView('pricing')}>Pricing</button>
        <button className={view === 'review' ? 'on' : ''} onClick={() => setView('review')}>Review</button>
        <button className={view === 'labels' ? 'on' : ''} onClick={() => setView('labels')}>Labels</button>
        <button className={view === 'catalog' ? 'on' : ''} onClick={() => setView('catalog')}>Catalog</button>
        <button className={view === 'suppliers' ? 'on' : ''} onClick={() => setView('suppliers')}>Suppliers</button>
        <button className={view === 'printer-config' ? 'on' : ''} onClick={() => setView('printer-config')}>Printer</button>
      </nav>
      <main>
        {view === 'checkout' ? <Checkout />
          : view === 'lots' ? <LotManagement />
          : view === 'receiving' ? <Receiving />
          : view === 'unpacking' ? <Unpacking />
          : view === 'prep' ? <Prep />
          : view === 'pricing' ? <PricingWorkbench />
          : view === 'review' ? <ReviewQueue />
          : view === 'labels' ? <BulkPrint />
          : view === 'capture' ? <MobileCapture />
          : view === 'catalog' ? <Catalog />
          : view === 'suppliers' ? <Suppliers />
          : <PrinterConfig />}
      </main>
    </>
  )
}
