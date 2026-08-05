import { useState } from 'react'
import { PrinterConfig } from './admin/PrinterConfig'
import { ReceiptPrinterConfig } from './admin/ReceiptPrinterConfig'
import { BillSettings } from './admin/BillSettings'

type Tab = 'label' | 'receipt' | 'bill'

const TABS: { value: Tab; label: string }[] = [
  { value: 'label', label: 'Label printer' },
  { value: 'receipt', label: 'Receipt printer' },
  { value: 'bill', label: 'Bill' },
]

/**
 * The Back-office configuration screen: one place for the three admin concerns that used to be
 * three separate nav entries — the label printer, the receipt printer, and the text on a bill
 * (design decision D1 of palletworks-nav).
 *
 * Each tab mounts its existing component unchanged, one at a time. Per-tab conditional mount
 * (not a keep-alive with CSS-hidden panes) means clicking a tab is exactly the same navigation
 * every other screen already gives that component: a fresh mount, a fresh fetch, the saved
 * values as they are right now — never a stale form left over from an earlier visit in the same
 * session. Admin screens are opened rarely, so the cost of a re-fetch on every click is nothing
 * against the benefit of never showing stale state.
 */
export function Settings() {
  const [tab, setTab] = useState<Tab>('label')

  return (
    <div className="page">
      <div className="mode-toggle">
        {TABS.map((t) => (
          <button
            key={t.value}
            type="button"
            className={tab === t.value ? 'on' : ''}
            onClick={() => setTab(t.value)}
          >
            {t.label}
          </button>
        ))}
      </div>

      {tab === 'label' && <PrinterConfig />}
      {tab === 'receipt' && <ReceiptPrinterConfig />}
      {tab === 'bill' && <BillSettings />}
    </div>
  )
}
