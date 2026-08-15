import { useState } from 'react'
import { Checkout } from '../Checkout'
import { Sales } from '../Sales'
import { LotManagement } from './LotManagement'
import { Receiving } from './Receiving'
import { Unpacking } from '../Unpacking'
import { PricingWorkbench } from '../PricingWorkbench'
import { ReviewQueue } from '../ReviewQueue'
import { Reprint } from '../Reprint'
import { MobileCapture } from '../MobileCapture'
import { Prep } from '../Prep'
import { Catalog } from './Catalog'
import { Suppliers } from '../Suppliers'
import { PrinterConfig } from '../admin/PrinterConfig'
import { ReceiptPrinterConfig } from '../admin/ReceiptPrinterConfig'
import { BillSettings } from '../admin/BillSettings'
import { GstRates } from '../GstRates'
import './classic.css'

type ClassicView =
  | 'checkout' | 'sales' | 'lots' | 'receiving' | 'unpacking' | 'prep'
  | 'pricing' | 'review' | 'reprint' | 'capture' | 'catalog' | 'suppliers'
  | 'printer-config' | 'receipt-config' | 'bill-settings' | 'gst-rates'

const TABS: { view: ClassicView; label: string }[] = [
  { view: 'checkout', label: 'Till' },
  { view: 'sales', label: 'Sales' },
  { view: 'lots', label: 'Lots' },
  { view: 'receiving', label: 'Receiving' },
  { view: 'unpacking', label: 'Unpacking' },
  { view: 'prep', label: 'Prep' },
  { view: 'pricing', label: 'Pricing' },
  { view: 'review', label: 'Review' },
  { view: 'reprint', label: 'Reprint' },
  { view: 'catalog', label: 'Catalog' },
  { view: 'suppliers', label: 'Suppliers' },
  { view: 'printer-config', label: 'Printer' },
  { view: 'receipt-config', label: 'Receipt' },
  { view: 'bill-settings', label: 'Bill' },
  { view: 'gst-rates', label: 'GST' },
]

/**
 * The pre-palletworks dashboard, whole: the flat top bar and its screens, exactly as main had
 * them, mounted when the device's UX mode is Classic. Shared screens are the same component
 * instances the modern shell uses; only LotManagement, Receiving, and Catalog are classic-only
 * restorations. Styling is scoped under `.classic` (see classic.css) so nothing here can leak
 * into the modern shell.
 */
export function ClassicShell({ sandbox, onSwitchUx }: { sandbox: boolean; onSwitchUx: () => void }) {
  // Same landing rules as the shell always had: phones are stations, desktops open the till;
  // the #till/#capture backdoors keep working in this mode too.
  const isPhone = typeof window !== 'undefined' && window.innerWidth <= 760
  const hash = typeof window !== 'undefined' ? window.location.hash : ''
  const landing: ClassicView =
    hash === '#capture' ? 'capture' : hash === '#till' ? 'checkout' : isPhone ? 'unpacking' : 'checkout'
  const [view, setView] = useState<ClassicView>(landing)

  return (
    <div className="classic">
      <nav className="topnav">
        <span className="brand" style={{ display: 'inline-flex', flexDirection: 'column', lineHeight: 1.1 }}>
          Bachat Bazaar
          {sandbox && (
            <small style={{ fontSize: 10, fontWeight: 700, letterSpacing: 1.5, color: '#b45309' }}>
              SANDBOX
            </small>
          )}
        </span>
        {TABS.map((t) => (
          <button key={t.view} className={view === t.view ? 'on' : ''} onClick={() => setView(t.view)}>
            {t.label}
          </button>
        ))}
        <button className="ux-switch" onClick={onSwitchUx}>Modern UX</button>
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
          : <GstRates />}
      </main>
    </div>
  )
}
