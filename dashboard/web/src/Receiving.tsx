import { useEffect, useState } from 'react'
import { receiving, shelfPricing, BackendError } from './api'
import type { LotSummary, ReceivingBoxes } from './types'
import { QtyInput } from './QtyInput'

export function Receiving() {
  const [lots, setLots] = useState<LotSummary[] | null>(null)
  const [selectedLot, setSelectedLot] = useState<LotSummary | null>(null)
  const [boxes, setBoxes] = useState<ReceivingBoxes | null>(null)
  const [cartonId, setCartonId] = useState('')
  const [message, setMessage] = useState<{ text: string; tone: string } | null>(null)
  const [state, setState] = useState<'in-progress' | 'complete'>('in-progress')
  const [categoryOptions, setCategoryOptions] = useState<string[]>([])
  const [productForm, setProductForm] = useState({ code: '', name: '', quantity: 1, categoryCode: '', estimatedCost: '' })

  useEffect(() => {
    loadLots()
    loadCategories()
  }, [])

  const loadCategories = async () => {
    try {
      const codes = await shelfPricing.categories()
      setCategoryOptions(codes)
      setProductForm((form) => (form.categoryCode ? form : { ...form, categoryCode: codes[0] ?? '' }))
    } catch (err) {
      setMessage({
        text: err instanceof BackendError ? err.message : 'Cannot load categories.',
        tone: 'stop',
      })
    }
  }

  const loadLots = async () => {
    try {
      const data = await receiving.lots()
      setLots(data)
    } catch (err) {
      setMessage({
        text: err instanceof BackendError ? err.message : 'Cannot reach the system.',
        tone: 'stop',
      })
    }
  }

  const openLot = async (lot: LotSummary) => {
    setSelectedLot(lot)
    try {
      const data = await receiving.boxes(lot.id)
      setBoxes(data)
      setMessage(null)
    } catch (err) {
      setMessage({
        text: err instanceof BackendError ? err.message : 'Cannot load boxes.',
        tone: 'stop',
      })
    }
  }

  const receiveBox = async () => {
    if (!selectedLot || !cartonId) return
    try {
      await receiving.receiveBox(selectedLot.id, cartonId)
      setMessage({ text: `✓ ${cartonId} received`, tone: 'ok' })
      setCartonId('')
      await openLot(selectedLot)
      await loadLots()
    } catch (err) {
      setMessage({
        text: err instanceof BackendError ? err.message : 'Error receiving box.',
        tone: 'stop',
      })
    }
  }

  const markNotReceived = async () => {
    if (!selectedLot || !cartonId) return
    try {
      await receiving.markNotReceived(selectedLot.id, cartonId)
      setMessage({ text: `✓ ${cartonId} marked not received`, tone: 'warn' })
      setCartonId('')
      await openLot(selectedLot)
      await loadLots()
    } catch (err) {
      setMessage({
        text: err instanceof BackendError ? err.message : 'Error.',
        tone: 'stop',
      })
    }
  }

  const rejectBox = async () => {
    if (!selectedLot || !cartonId) return
    try {
      await receiving.rejectBox(selectedLot.id, cartonId, 'Damaged at dock')
      setMessage({ text: `✓ ${cartonId} marked damaged`, tone: 'warn' })
      setCartonId('')
      await openLot(selectedLot)
      await loadLots()
    } catch (err) {
      setMessage({
        text: err instanceof BackendError ? err.message : 'Error.',
        tone: 'stop',
      })
    }
  }

  const addProductToManualLot = async () => {
    if (!selectedLot || !productForm.name || productForm.quantity <= 0) {
      setMessage({ text: 'Fill required fields', tone: 'stop' })
      return
    }

    try {
      await receiving.addProduct(
        selectedLot.id,
        productForm.code || null,
        productForm.name,
        productForm.quantity,
        productForm.categoryCode,
        productForm.estimatedCost ? Math.round(parseFloat(productForm.estimatedCost) * 100) : null
      )
      setMessage({ text: `✓ ${productForm.name} added`, tone: 'ok' })
      setProductForm({ code: '', name: '', quantity: 1, categoryCode: 'KITCHEN', estimatedCost: '' })
      await openLot(selectedLot)
      await loadLots()
    } catch (err) {
      setMessage({
        text: err instanceof BackendError ? err.message : 'Error adding product.',
        tone: 'stop',
      })
    }
  }

  const closeLot = () => {
    setSelectedLot(null)
    setBoxes(null)
    setCartonId('')
    setProductForm({ code: '', name: '', quantity: 1, categoryCode: 'KITCHEN', estimatedCost: '' })
    setMessage(null)
    loadLots()
  }

  if (!selectedLot) {
    const inProgress = lots?.filter((l) => !l.receivingComplete) ?? []
    const complete = lots?.filter((l) => l.receivingComplete) ?? []
    const filtered = state === 'in-progress' ? inProgress : complete

    return (
      <div className="receiving">
        <h1>Receive Boxes</h1>
        {message && <div className={`banner ${message.tone}`}>{message.text}</div>}

        {lots === null ? (
          <p>Loading lots…</p>
        ) : lots.length === 0 ? (
          <p>No open lots to receive.</p>
        ) : (
          <>
            <div className="tab-group" style={{ display: 'flex', gap: 'var(--s2)', marginBottom: 'var(--s3)', borderBottom: '1px solid var(--line-soft)' }}>
              <button
                onClick={() => setState('in-progress')}
                style={{
                  padding: '8px 16px',
                  borderBottom: state === 'in-progress' ? '2px solid var(--brand)' : 'none',
                  background: 'transparent',
                  cursor: 'pointer',
                  fontSize: '14px',
                  fontWeight: state === 'in-progress' ? '600' : '400',
                  color: state === 'in-progress' ? 'var(--brand)' : 'var(--ink-faint)',
                }}
              >
                In Progress ({inProgress.length})
              </button>
              <button
                onClick={() => setState('complete')}
                style={{
                  padding: '8px 16px',
                  borderBottom: state === 'complete' ? '2px solid var(--brand)' : 'none',
                  background: 'transparent',
                  cursor: 'pointer',
                  fontSize: '14px',
                  fontWeight: state === 'complete' ? '600' : '400',
                  color: state === 'complete' ? 'var(--brand)' : 'var(--ink-faint)',
                }}
              >
                Complete ({complete.length})
              </button>
            </div>

            {filtered.length === 0 ? (
              <p style={{ color: 'var(--ink-faint)' }}>No lots in this category.</p>
            ) : (
              <div className="overview-cards">
                {filtered.map((lot) => {
                  const done = lot.received + lot.unpacked + lot.rejected + lot.notReceived
                  const pct = lot.expected ? Math.round((done / lot.expected) * 100) : 0
                  return (
                    <div key={lot.id} className="ov">
                      <div className="ov-row" onClick={() => openLot(lot)}>
                        <div style={{ flex: 1, minWidth: 0 }}>
                          <div className="ov-name">
                            {lot.supplier}
                            {lot.isManual && <span style={{ marginLeft: 'var(--s2)', fontSize: '12px', color: 'var(--ink-faint)' }}>(M)</span>}
                          </div>
                          <div className="ov-stat">{lot.receivedOn}</div>
                          <div className="ov-bar">
                            <i style={{ width: `${pct}%` }} />
                          </div>
                        </div>
                        <div style={{ marginLeft: 'var(--s3)', textAlign: 'right', fontSize: '13px' }}>
                          {lot.isManual ? `${done} products` : `${done}/${lot.expected}`}
                        </div>
                      </div>
                    </div>
                  )
                })}
              </div>
            )}
          </>
        )}
      </div>
    )
  }

  return (
    <div className="receiving">
      <h1>
        {selectedLot.supplier}
        {selectedLot.isManual && <span style={{ marginLeft: 'var(--s2)', fontSize: '14px', color: 'var(--ink-faint)' }}>(Manual)</span>}
        <button
          className="back"
          onClick={closeLot}
          style={{ marginLeft: 'auto', display: 'inline-block' }}
        >
          ← Back
        </button>
      </h1>

      {message && <div className={`banner ${message.tone}`}>{message.text}</div>}

      {selectedLot.isManual ? (
        <>
          {boxes && (
            <div style={{ fontSize: '15px', marginBottom: 'var(--s3)', color: 'var(--brand)' }}>
              {boxes.counts.received} products, {boxes.counts.received * productForm.quantity || 0} units
            </div>
          )}

          <div style={{ marginBottom: 'var(--s3)', padding: 'var(--s3)', background: 'var(--line-soft)', borderRadius: 'var(--r1)' }}>
            <div style={{ marginBottom: 'var(--s2)' }}>
              <label style={{ display: 'block', fontSize: '13px', fontWeight: 600, marginBottom: 'var(--s1)' }}>Product Name *</label>
              <input
                type="text"
                placeholder="e.g., Face Cream"
                value={productForm.name}
                onChange={(e) => setProductForm({ ...productForm, name: e.target.value })}
                style={{
                  width: '100%',
                  padding: '8px',
                  border: '1px solid var(--line)',
                  borderRadius: 'var(--r1)',
                  fontSize: '14px',
                  fontFamily: 'inherit',
                }}
              />
            </div>

            <div style={{ marginBottom: 'var(--s2)', display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--s2)' }}>
              <div>
                <label style={{ display: 'block', fontSize: '13px', fontWeight: 600, marginBottom: 'var(--s1)' }}>Category *</label>
                <select
                  value={productForm.categoryCode}
                  onChange={(e) => setProductForm({ ...productForm, categoryCode: e.target.value })}
                  style={{
                    width: '100%',
                    padding: '8px',
                    border: '1px solid var(--line)',
                    borderRadius: 'var(--r1)',
                    fontSize: '14px',
                    fontFamily: 'inherit',
                  }}
                >
                  {categoryOptions.map((code) => (
                    <option key={code} value={code}>{code}</option>
                  ))}
                </select>
              </div>
              <div>
                <label style={{ display: 'block', fontSize: '13px', fontWeight: 600, marginBottom: 'var(--s1)' }}>Qty *</label>
                <QtyInput
                  min={1}
                  value={productForm.quantity}
                  onChange={(n) => setProductForm({ ...productForm, quantity: n })}
                  style={{
                    width: '100%',
                    padding: '8px',
                    border: '1px solid var(--line)',
                    borderRadius: 'var(--r1)',
                    fontSize: '14px',
                    fontFamily: 'inherit',
                  }}
                />
              </div>
            </div>

            <div style={{ marginBottom: 'var(--s2)' }}>
              <label style={{ display: 'block', fontSize: '13px', fontWeight: 600, marginBottom: 'var(--s1)' }}>Est. Cost (₹)</label>
              <input
                type="number"
                placeholder="Optional"
                value={productForm.estimatedCost}
                onChange={(e) => setProductForm({ ...productForm, estimatedCost: e.target.value })}
                style={{
                  width: '100%',
                  padding: '8px',
                  border: '1px solid var(--line)',
                  borderRadius: 'var(--r1)',
                  fontSize: '14px',
                  fontFamily: 'inherit',
                }}
              />
            </div>

            <button
              onClick={addProductToManualLot}
              style={{
                width: '100%',
                padding: '8px',
                background: 'var(--brand)',
                color: 'white',
                border: 'none',
                borderRadius: 'var(--r1)',
                cursor: 'pointer',
                fontWeight: 600,
              }}
            >
              Add Product
            </button>
          </div>
        </>
      ) : (
        <>
          {boxes && (
            <div style={{ fontSize: '15px', marginBottom: 'var(--s3)', color: 'var(--brand)' }}>
              {boxes.counts.received + boxes.counts.unpacked + boxes.counts.rejected + boxes.counts.notReceived} /{' '}
              {boxes.counts.expected} boxes
            </div>
          )}

          <input
            className="scan"
            placeholder="Scan carton ID"
            value={cartonId}
            onChange={(e) => setCartonId(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && receiveBox()}
            autoFocus
          />

          <div className="button-group" style={{ display: 'flex', gap: 'var(--s2)', marginTop: 'var(--s3)' }}>
            <button className="btn-primary" onClick={receiveBox} style={{ flex: 1 }}>
              📦 Receive
            </button>
            <button className="btn-warn" onClick={markNotReceived} style={{ flex: 1 }}>
              ✗ Not Received
            </button>
            <button className="btn-warn" onClick={rejectBox} style={{ flex: 1 }}>
              📦 Damaged
            </button>
          </div>
        </>
      )}

      {boxes && (
        <div className="items" style={{ marginTop: 'var(--s3)' }}>
          {boxes.boxes.map((box) => (
            <div key={box.manifestCartonId}>
              <div className="item">
                <div className="who">
                  <div>{box.manifestCartonId}</div>
                  {box.receivedAt && <div className="meta">{new Date(box.receivedAt).toLocaleTimeString()}</div>}
                </div>
                <div className="countcol">
                  <span
                    className={`flag ${
                      box.state === 'EXPECTED' || box.state === 'RECEIVED' || box.state === 'UNPACKING'
                        ? 'ok'
                        : 'stop'
                    }${box.state === 'EXPECTED' ? '.neutral' : ''}`}
                    style={{
                      background:
                        box.state === 'EXPECTED'
                          ? 'var(--line-soft)'
                          : box.state === 'RECEIVED' || box.state === 'UNPACKING'
                            ? 'var(--good-tint)'
                            : 'var(--stop-tint)',
                      color:
                        box.state === 'EXPECTED'
                          ? 'var(--ink-faint)'
                          : box.state === 'RECEIVED' || box.state === 'UNPACKING'
                            ? 'var(--good)'
                            : 'var(--stop)',
                      padding: '2px 8px',
                      borderRadius: '10px',
                      fontSize: '11px',
                      fontWeight: '600',
                    }}
                  >
                    {box.state}
                  </span>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {boxes && boxes.allTerminal && (
        <div className="actions" style={{ marginTop: 'var(--s3)' }}>
          <button className="btn-primary" onClick={closeLot} style={{ flex: 1 }}>
            Done
          </button>
        </div>
      )}

    </div>
  )
}
