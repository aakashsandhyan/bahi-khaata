import { useEffect, useState } from 'react'
import { receiving, suppliers as suppliersApi, BackendError } from '../api'
import type { LotSummary, Supplier } from '../types'

const TODAY = new Date().toISOString().split('T')[0]

/**
 * Lot creation, extracted from the retired LotManagement screen, manual lots only (design
 * decision D3 of palletworks-intake). Manifest import has no dashboard path — manifest lots
 * arrive pre-imported via the backend importer — so, unlike the old modal, there is no
 * "Manifest-based" option here at all: nothing to drop into a dead radio that only ever said
 * "not yet supported here".
 */
export function CreateLotModal({
  open,
  onClose,
  onCreated,
}: {
  open: boolean
  onClose: () => void
  onCreated: (lot: LotSummary) => void
}) {
  const [supplierOptions, setSupplierOptions] = useState<Supplier[]>([])
  const [supplierId, setSupplierId] = useState('')
  const [receivedOn, setReceivedOn] = useState(TODAY)
  const [amountPaidPaise, setAmountPaidPaise] = useState('')
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!open) return
    // Only active suppliers can receive stock, so the pick-list offers just those.
    suppliersApi.list(true).then(setSupplierOptions).catch(() => setSupplierOptions([]))
  }, [open])

  if (!open) return null

  const create = async () => {
    if (!supplierId || !receivedOn || !amountPaidPaise) {
      setError('Fill all fields')
      return
    }
    try {
      const lot = await receiving.createManualLot(supplierId, receivedOn, parseInt(amountPaidPaise, 10))
      if (lot) {
        setSupplierId('')
        setAmountPaidPaise('')
        setError(null)
        onCreated(lot)
      }
    } catch (err) {
      setError(err instanceof BackendError ? err.message : 'Error creating lot.')
    }
  }

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-content" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">Create manual lot</div>
        <div className="modal-body">
          {error && <div className="banner stop">{error}</div>}

          <div className="field">
            <label>Supplier</label>
            <select value={supplierId} onChange={(e) => setSupplierId(e.target.value)}>
              <option value="">
                {supplierOptions.length === 0 ? 'No active suppliers — add one first' : 'Select a supplier…'}
              </option>
              {supplierOptions.map((s) => (
                <option key={s.id} value={s.id}>{s.name}</option>
              ))}
            </select>
          </div>

          <div className="field">
            <label>Received on</label>
            <input type="date" value={receivedOn} onChange={(e) => setReceivedOn(e.target.value)} />
          </div>

          <div className="field">
            <label>Amount paid (₹)</label>
            <input
              type="number"
              value={amountPaidPaise}
              onChange={(e) => setAmountPaidPaise(e.target.value)}
              placeholder="0"
            />
          </div>
        </div>

        <div className="modal-footer">
          <button type="button" className="btn-ghost" onClick={onClose}>Cancel</button>
          <button type="button" className="btn-primary" onClick={create}>Create</button>
        </div>
      </div>
    </div>
  )
}
