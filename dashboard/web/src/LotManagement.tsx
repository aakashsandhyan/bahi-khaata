import { useEffect, useState } from 'react'
import { receiving, BackendError } from './api'
import type { LotSummary } from './types'

export function LotManagement() {
  const [lots, setLots] = useState<LotSummary[] | null>(null)
  const [state, setState] = useState<'in-progress' | 'complete'>('in-progress')
  const [showCreateModal, setShowCreateModal] = useState(false)
  const [message, setMessage] = useState<{ text: string; tone: string } | null>(null)
  const [formData, setFormData] = useState({
    supplier: '',
    receivedOn: new Date().toISOString().split('T')[0],
    amountPaidPaise: '',
    type: 'manifest',
  })

  useEffect(() => {
    loadLots()
  }, [])

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
    if (!formData.supplier || !formData.receivedOn || !formData.amountPaidPaise) {
      setMessage({ text: 'Fill all fields', tone: 'stop' })
      return
    }

    if (formData.type === 'manifest') {
      setMessage({ text: 'Manifest import not yet supported here', tone: 'warn' })
      return
    }

    try {
      await receiving.createManualLot(formData.supplier, formData.receivedOn, parseInt(formData.amountPaidPaise))
      setMessage({ text: `✓ ${formData.supplier} created`, tone: 'ok' })
      setShowCreateModal(false)
      setFormData({ supplier: '', receivedOn: new Date().toISOString().split('T')[0], amountPaidPaise: '', type: 'manifest' })
      await loadLots()
    } catch (err) {
      setMessage({
        text: err instanceof BackendError ? err.message : 'Error creating lot.',
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
                          <div style={{ fontSize: '13px', color: 'var(--ink-faint)' }}>{lot.receivedOn}</div>
                        </div>
                        <div style={{ textAlign: 'right', fontSize: '13px' }}>
                          {lot.isManual ? 'Manual' : 'Manifest'}
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
                <input
                  type="text"
                  value={formData.supplier}
                  onChange={(e) => setFormData({ ...formData, supplier: e.target.value })}
                  placeholder="Supplier name"
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
    </div>
  )
}
