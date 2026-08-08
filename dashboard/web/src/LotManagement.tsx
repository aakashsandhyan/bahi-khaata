import { useEffect, useState } from 'react'
import { receiving, suppliers as suppliersApi, shelfPricing, BackendError } from './api'
import type { LotSummary, Supplier } from './types'

const emptyCreateForm = {
  supplierId: '',
  receivedOn: new Date().toISOString().split('T')[0],
  amountPaidPaise: '',
  categoryCode: '',
  type: 'manifest',
}

export function LotManagement() {
  const [lots, setLots] = useState<LotSummary[] | null>(null)
  const [supplierOptions, setSupplierOptions] = useState<Supplier[]>([])
  const [categoryOptions, setCategoryOptions] = useState<string[]>([])
  const [state, setState] = useState<'in-progress' | 'complete'>('in-progress')
  const [showCreateModal, setShowCreateModal] = useState(false)
  const [editingLot, setEditingLot] = useState<LotSummary | null>(null)
  const [message, setMessage] = useState<{ text: string; tone: string } | null>(null)
  const [formData, setFormData] = useState(emptyCreateForm)
  const [editFormData, setEditFormData] = useState({
    supplierId: '',
    receivedOn: '',
    amountPaidPaise: '',
    freightPaise: '',
    allocationMethod: 'RELATIVE_MRP',
    categoryCode: '',
  })

  useEffect(() => {
    loadLots()
    loadSuppliers()
    loadCategories()
  }, [])

  const loadSuppliers = async () => {
    try {
      // Only active suppliers can receive stock, so the pick-list offers just those.
      setSupplierOptions(await suppliersApi.list(true))
    } catch (err) {
      setMessage({
        text: err instanceof BackendError ? err.message : 'Cannot load suppliers.',
        tone: 'stop',
      })
    }
  }

  const loadCategories = async () => {
    try {
      setCategoryOptions(await shelfPricing.categories())
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

  const handleCreateLot = async () => {
    if (!formData.supplierId || !formData.receivedOn || !formData.amountPaidPaise) {
      setMessage({ text: 'Fill all fields', tone: 'stop' })
      return
    }

    if (formData.type === 'manifest') {
      setMessage({ text: 'Manifest import not yet supported here', tone: 'warn' })
      return
    }

    const supplierName = supplierOptions.find((s) => s.id === formData.supplierId)?.name ?? 'Lot'
    try {
      await receiving.createManualLot(
        formData.supplierId,
        formData.receivedOn,
        parseInt(formData.amountPaidPaise),
        formData.categoryCode || null,
      )
      setMessage({ text: `✓ ${supplierName} created`, tone: 'ok' })
      setShowCreateModal(false)
      setFormData(emptyCreateForm)
      await loadLots()
    } catch (err) {
      setMessage({
        text: err instanceof BackendError ? err.message : 'Error creating lot.',
        tone: 'stop',
      })
    }
  }

  // LotSummary now carries the lot's real supplierId/amount/freight/allocation method, so the
  // form opens pre-filled with what is actually stored rather than blank fields the operator
  // has to re-key from scratch.
  const openEditModal = (lot: LotSummary) => {
    setEditingLot(lot)
    setEditFormData({
      supplierId: lot.supplierId ?? '',
      receivedOn: lot.receivedOn,
      amountPaidPaise: String(lot.amountPaidPaise),
      freightPaise: String(lot.freightPaise),
      allocationMethod: lot.allocationMethod,
      categoryCode: lot.categoryCode ?? '',
    })
  }

  const handleUpdateLot = async () => {
    if (!editingLot) return
    try {
      await receiving.updateLot(editingLot.id, {
        supplierId: editFormData.supplierId || null,
        receivedOn: editFormData.receivedOn || null,
        amountPaidPaise: editFormData.amountPaidPaise ? parseInt(editFormData.amountPaidPaise) : null,
        freightPaise: editFormData.freightPaise ? parseInt(editFormData.freightPaise) : null,
        allocationMethod: editFormData.allocationMethod || null,
        categoryCode: editFormData.categoryCode,
      })
      setMessage({ text: '✓ Lot updated', tone: 'ok' })
      setEditingLot(null)
      await loadLots()
    } catch (err) {
      // A 409 here is the freeze rule: stock from this lot has already sold, so its costs are
      // load-bearing on recorded sales and the message says as much.
      setMessage({
        text: err instanceof BackendError ? err.message : 'Error updating lot.',
        tone: 'stop',
      })
    }
  }

  const inProgress = lots?.filter((l) => !l.receivingComplete) ?? []
  const complete = lots?.filter((l) => l.receivingComplete) ?? []
  const filtered = state === 'in-progress' ? inProgress : complete

  return (
    <div className="lot-management">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 'var(--s4)' }}>
        <h1>Lot Management</h1>
        <button
          onClick={() => setShowCreateModal(true)}
          style={{
            padding: '8px 16px',
            background: 'var(--brand)',
            color: 'white',
            border: 'none',
            borderRadius: 'var(--r2)',
            cursor: 'pointer',
            fontWeight: 600,
            fontSize: '14px',
          }}
        >
          + Create Lot
        </button>
      </div>

      {message && <div className={`banner ${message.tone}`}>{message.text}</div>}

      {lots === null ? (
        <p>Loading lots…</p>
      ) : lots.length === 0 ? (
        <p>No lots yet.</p>
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
                    <div
                      style={{
                        padding: 'var(--s3)',
                        borderRadius: 'var(--r1)',
                        background: 'var(--card)',
                        border: '1px solid var(--line)',
                      }}
                    >
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'start', marginBottom: 'var(--s2)' }}>
                        <div style={{ flex: 1 }}>
                          <div style={{ fontWeight: 600, marginBottom: '2px' }}>
                            {lot.supplier}
                            {lot.isManual && <span style={{ marginLeft: 'var(--s2)', fontSize: '12px', color: 'var(--ink-faint)' }}>(M)</span>}
                          </div>
                          <div style={{ fontSize: '13px', color: 'var(--ink-faint)' }}>
                            {lot.receivedOn}
                            {lot.categoryCode && <span> · {lot.categoryCode}</span>}
                          </div>
                        </div>
                        <div style={{ textAlign: 'right', fontSize: '13px' }}>
                          {lot.isManual ? 'Manual' : 'Manifest'}
                          <div>
                            <button
                              onClick={() => openEditModal(lot)}
                              style={{
                                marginTop: 'var(--s1)',
                                padding: '2px 8px',
                                background: 'transparent',
                                border: '1px solid var(--line)',
                                borderRadius: 'var(--r1)',
                                cursor: 'pointer',
                                fontSize: '12px',
                                color: 'var(--ink-soft)',
                              }}
                            >
                              Edit
                            </button>
                          </div>
                        </div>
                      </div>
                      {lot.expected > 0 && (
                        <>
                          <div style={{ height: '4px', background: 'var(--line-soft)', borderRadius: '2px', marginBottom: 'var(--s2)', overflow: 'hidden' }}>
                            <div style={{ height: '100%', width: `${pct}%`, background: 'var(--brand)', borderRadius: '2px' }} />
                          </div>
                          <div style={{ fontSize: '12px', color: 'var(--ink-soft)' }}>
                            {done}/{lot.expected} boxes
                          </div>
                        </>
                      )}
                    </div>
                  </div>
                )
              })}
            </div>
          )}
        </>
      )}

      {showCreateModal && (
        <div className="modal-overlay" onClick={() => setShowCreateModal(false)}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">Create Lot</div>
            <div className="modal-body">
              <div style={{ marginBottom: 'var(--s3)' }}>
                <label style={{ display: 'block', fontSize: '13px', fontWeight: 600, marginBottom: 'var(--s1)' }}>Supplier</label>
                <select
                  value={formData.supplierId}
                  onChange={(e) => setFormData({ ...formData, supplierId: e.target.value })}
                  style={{
                    width: '100%',
                    padding: '8px',
                    border: '1px solid var(--line)',
                    borderRadius: 'var(--r1)',
                    fontSize: '14px',
                    fontFamily: 'inherit',
                  }}
                >
                  <option value="">
                    {supplierOptions.length === 0 ? 'No active suppliers — add one first' : 'Select a supplier…'}
                  </option>
                  {supplierOptions.map((s) => (
                    <option key={s.id} value={s.id}>{s.name}</option>
                  ))}
                </select>
              </div>

              <div style={{ marginBottom: 'var(--s3)' }}>
                <label style={{ display: 'block', fontSize: '13px', fontWeight: 600, marginBottom: 'var(--s1)' }}>Received On</label>
                <input
                  type="date"
                  value={formData.receivedOn}
                  onChange={(e) => setFormData({ ...formData, receivedOn: e.target.value })}
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

              <div style={{ marginBottom: 'var(--s3)' }}>
                <label style={{ display: 'block', fontSize: '13px', fontWeight: 600, marginBottom: 'var(--s1)' }}>Amount Paid (₹)</label>
                <input
                  type="number"
                  value={formData.amountPaidPaise}
                  onChange={(e) => setFormData({ ...formData, amountPaidPaise: e.target.value })}
                  placeholder="0"
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

              <div style={{ marginBottom: 'var(--s3)' }}>
                <label style={{ display: 'block', fontSize: '13px', fontWeight: 600, marginBottom: 'var(--s1)' }}>Type</label>
                <select
                  value={formData.type}
                  onChange={(e) => setFormData({ ...formData, type: e.target.value })}
                  style={{
                    width: '100%',
                    padding: '8px',
                    border: '1px solid var(--line)',
                    borderRadius: 'var(--r1)',
                    fontSize: '14px',
                    fontFamily: 'inherit',
                  }}
                >
                  <option value="manifest">Manifest-based</option>
                  <option value="manual">Manual Entry</option>
                </select>
              </div>

              {formData.type === 'manual' && (
                <div style={{ marginBottom: 'var(--s3)' }}>
                  <label style={{ display: 'block', fontSize: '13px', fontWeight: 600, marginBottom: 'var(--s1)' }}>
                    Default Category
                  </label>
                  <select
                    value={formData.categoryCode}
                    onChange={(e) => setFormData({ ...formData, categoryCode: e.target.value })}
                    style={{
                      width: '100%',
                      padding: '8px',
                      border: '1px solid var(--line)',
                      borderRadius: 'var(--r1)',
                      fontSize: '14px',
                      fontFamily: 'inherit',
                    }}
                  >
                    <option value="">No default</option>
                    {categoryOptions.map((code) => (
                      <option key={code} value={code}>{code}</option>
                    ))}
                  </select>
                </div>
              )}
            </div>

            <div className="modal-footer">
              <button
                onClick={() => setShowCreateModal(false)}
                style={{
                  padding: '8px 16px',
                  background: 'var(--line-soft)',
                  border: 'none',
                  borderRadius: 'var(--r1)',
                  cursor: 'pointer',
                  fontSize: '14px',
                }}
              >
                Cancel
              </button>
              <button
                onClick={handleCreateLot}
                style={{
                  padding: '8px 16px',
                  background: 'var(--brand)',
                  color: 'white',
                  border: 'none',
                  borderRadius: 'var(--r1)',
                  cursor: 'pointer',
                  fontWeight: 600,
                  fontSize: '14px',
                }}
              >
                Create
              </button>
            </div>
          </div>
        </div>
      )}

      {editingLot && (
        <div className="modal-overlay" onClick={() => setEditingLot(null)}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">Edit Lot</div>
            <div className="modal-body">
              <div style={{ marginBottom: 'var(--s3)' }}>
                <label style={{ display: 'block', fontSize: '13px', fontWeight: 600, marginBottom: 'var(--s1)' }}>Supplier</label>
                <select
                  value={editFormData.supplierId}
                  onChange={(e) => setEditFormData({ ...editFormData, supplierId: e.target.value })}
                  style={{
                    width: '100%',
                    padding: '8px',
                    border: '1px solid var(--line)',
                    borderRadius: 'var(--r1)',
                    fontSize: '14px',
                    fontFamily: 'inherit',
                  }}
                >
                  <option value="">Unchanged ({editingLot.supplier})</option>
                  {supplierOptions.map((s) => (
                    <option key={s.id} value={s.id}>{s.name}</option>
                  ))}
                </select>
              </div>

              <div style={{ marginBottom: 'var(--s3)' }}>
                <label style={{ display: 'block', fontSize: '13px', fontWeight: 600, marginBottom: 'var(--s1)' }}>Received On</label>
                <input
                  type="date"
                  value={editFormData.receivedOn}
                  onChange={(e) => setEditFormData({ ...editFormData, receivedOn: e.target.value })}
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

              <div style={{ marginBottom: 'var(--s3)' }}>
                <label style={{ display: 'block', fontSize: '13px', fontWeight: 600, marginBottom: 'var(--s1)' }}>
                  Amount Paid (₹) — leave blank to keep unchanged
                </label>
                <input
                  type="number"
                  value={editFormData.amountPaidPaise}
                  onChange={(e) => setEditFormData({ ...editFormData, amountPaidPaise: e.target.value })}
                  placeholder="Unchanged"
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

              <div style={{ marginBottom: 'var(--s3)' }}>
                <label style={{ display: 'block', fontSize: '13px', fontWeight: 600, marginBottom: 'var(--s1)' }}>
                  Freight (₹) — leave blank to keep unchanged
                </label>
                <input
                  type="number"
                  value={editFormData.freightPaise}
                  onChange={(e) => setEditFormData({ ...editFormData, freightPaise: e.target.value })}
                  placeholder="Unchanged"
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

              <div style={{ marginBottom: 'var(--s3)' }}>
                <label style={{ display: 'block', fontSize: '13px', fontWeight: 600, marginBottom: 'var(--s1)' }}>Allocation Method</label>
                <select
                  value={editFormData.allocationMethod}
                  onChange={(e) => setEditFormData({ ...editFormData, allocationMethod: e.target.value })}
                  style={{
                    width: '100%',
                    padding: '8px',
                    border: '1px solid var(--line)',
                    borderRadius: 'var(--r1)',
                    fontSize: '14px',
                    fontFamily: 'inherit',
                  }}
                >
                  <option value="RELATIVE_MRP">Relative MRP</option>
                  <option value="FULLY_PINNED">Fully pinned</option>
                  <option value="IMPORTED">Imported cost list</option>
                </select>
              </div>

              <div style={{ marginBottom: 'var(--s3)' }}>
                <label style={{ display: 'block', fontSize: '13px', fontWeight: 600, marginBottom: 'var(--s1)' }}>Default Category</label>
                <select
                  value={editFormData.categoryCode}
                  onChange={(e) => setEditFormData({ ...editFormData, categoryCode: e.target.value })}
                  style={{
                    width: '100%',
                    padding: '8px',
                    border: '1px solid var(--line)',
                    borderRadius: 'var(--r1)',
                    fontSize: '14px',
                    fontFamily: 'inherit',
                  }}
                >
                  <option value="">No default</option>
                  {categoryOptions.map((code) => (
                    <option key={code} value={code}>{code}</option>
                  ))}
                </select>
              </div>
            </div>

            <div className="modal-footer">
              <button
                onClick={() => setEditingLot(null)}
                style={{
                  padding: '8px 16px',
                  background: 'var(--line-soft)',
                  border: 'none',
                  borderRadius: 'var(--r1)',
                  cursor: 'pointer',
                  fontSize: '14px',
                }}
              >
                Cancel
              </button>
              <button
                onClick={handleUpdateLot}
                style={{
                  padding: '8px 16px',
                  background: 'var(--brand)',
                  color: 'white',
                  border: 'none',
                  borderRadius: 'var(--r1)',
                  cursor: 'pointer',
                  fontWeight: 600,
                  fontSize: '14px',
                }}
              >
                Save
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
