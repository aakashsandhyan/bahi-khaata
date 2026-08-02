import { useEffect, useState } from 'react'
import { sales, BackendError } from './api'
import type { SaleSummary, SaleView } from './types'
import { rupees } from './money'

/**
 * The record of sales: recent bills newest-first, a search by bill number, and a reprint on any of
 * them. A reprint re-renders from the stored, immutable sale — the old bill comes out the same
 * however prices have moved since — so this is also where a missed print at the till is recovered.
 */
export function Sales() {
  const [rows, setRows] = useState<SaleSummary[]>([])
  const [query, setQuery] = useState('')
  const [found, setFound] = useState<SaleView | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [note, setNote] = useState<string | null>(null)
  const [busy, setBusy] = useState<string | null>(null)

  const load = () => {
    sales.recent(50).then(setRows).catch(() => setError('Cannot reach the sales record.'))
  }

  useEffect(load, [])

  const search = async () => {
    const billNo = parseInt(query.replace(/\D/g, ''), 10)
    if (!billNo) return
    setError(null)
    setFound(null)
    try {
      setFound(await sales.byBillNo(billNo))
    } catch (e) {
      setError(e instanceof BackendError ? e.message : `No bill numbered ${billNo}.`)
    }
  }

  const reprint = async (saleId: string) => {
    setBusy(saleId)
    setNote(null)
    setError(null)
    try {
      const s = await sales.reprint(saleId)
      setNote(s.printFailed ? `${s.billNoFormatted} did not print — check the receipt printer.` : `${s.billNoFormatted} sent to the printer.`)
    } catch (e) {
      setError(e instanceof BackendError ? e.message : 'Cannot reach the printer.')
    } finally {
      setBusy(null)
    }
  }

  return (
    <div className="sales">
      <h1>Sales</h1>

      <div className="sales-search">
        <input
          placeholder="Find a bill by number (e.g. 42 or BB-000042)"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && search()}
        />
        <button onClick={search}>Find</button>
      </div>

      {error && <div className="banner stop">{error}</div>}
      {note && <div className="banner ok">{note}</div>}

      {found && (
        <div className="sales-found">
          <SaleDetail sale={found} />
          <button disabled={busy === found.saleId} onClick={() => reprint(found.saleId)}>
            {busy === found.saleId ? 'Printing…' : 'Reprint this bill'}
          </button>
        </div>
      )}

      <table className="sales-table">
        <thead>
          <tr>
            <th>Bill</th>
            <th>When</th>
            <th>Items</th>
            <th>Method</th>
            <th className="num">Total</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {rows.length === 0 && (
            <tr>
              <td colSpan={6} className="sales-empty">No sales yet.</td>
            </tr>
          )}
          {rows.map((s) => (
            <tr key={s.saleId}>
              <td>{s.billNoFormatted}</td>
              <td>{new Date(s.createdAt).toLocaleString()}</td>
              <td>{s.itemCount}</td>
              <td>{s.paymentMethod}</td>
              <td className="num">{rupees(s.totalPaise)}</td>
              <td>
                <button disabled={busy === s.saleId} onClick={() => reprint(s.saleId)}>
                  {busy === s.saleId ? '…' : 'Reprint'}
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function SaleDetail({ sale }: { sale: SaleView }) {
  return (
    <div className="sales-detail">
      <div className="sales-detail-head">
        <strong>{sale.billNoFormatted}</strong>
        <span>{new Date(sale.createdAt).toLocaleString()}</span>
        <span>Paid {sale.paymentMethod}</span>
        {sale.operatorName && <span>by {sale.operatorName}</span>}
      </div>
      {sale.lines.map((l, i) => (
        <div className="sales-detail-line" key={i}>
          <span className="name">{l.name}</span>
          <span className="qty">{l.quantity} × {rupees(l.unitPricePaise)}</span>
          <span className="num">{rupees(l.lineTotalPaise)}</span>
        </div>
      ))}
      <div className="sales-detail-total">
        <span>Total</span>
        <span className="num">{rupees(sale.totalPaise)}</span>
      </div>
    </div>
  )
}
