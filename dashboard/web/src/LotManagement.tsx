import { useEffect, useState } from 'react'
import type { CSSProperties } from 'react'
import { receiving, suppliers as suppliersApi, shelfPricing, BackendError } from './api'
import type { CostAnchor, CostBasisFields, CostBasisStrategy, LotSummary, MultiplierBase, Supplier } from './types'

const emptyCreateForm = {
  supplierId: '',
  receivedOn: new Date().toISOString().split('T')[0],
  amountPaidPaise: '',
  categoryCode: '',
  type: 'manifest',
}

// The cost-basis section of a create/edit form, held as strings so every input is controlled —
// parsed to the request shape only at submit time. An empty rate-band row (no min or cost typed
// yet) is dropped rather than sent as a malformed band.
interface CostBasisFormState {
  strategy: CostBasisStrategy | ''
  anchor: CostAnchor | ''
  flatUnitCostPaise: string
  percentBp: string
  multiplierMilli: string
  multiplierBase: MultiplierBase | ''
  rateBands: { minMrpPaise: string; maxMrpPaise: string; costPaise: string }[]
}

const emptyCostBasisForm: CostBasisFormState = {
  strategy: '',
  anchor: '',
  flatUnitCostPaise: '',
  percentBp: '',
  multiplierMilli: '',
  multiplierBase: '',
  rateBands: [],
}

/** Reads a lot's declared cost basis back into editable form state, for the edit modal to open pre-filled. */
function costBasisFormFromLot(lot: LotSummary): CostBasisFormState {
  return {
    strategy: lot.costBasisStrategy ?? '',
    anchor: lot.costAnchor ?? '',
    flatUnitCostPaise: lot.flatUnitCostPaise != null ? String(lot.flatUnitCostPaise) : '',
    percentBp: lot.percentBp != null ? String(lot.percentBp) : '',
    multiplierMilli: lot.multiplierMilli != null ? String(lot.multiplierMilli) : '',
    multiplierBase: lot.multiplierBase ?? '',
    rateBands: (lot.rateBands ?? []).map((b) => ({
      minMrpPaise: String(b.minMrpPaise),
      maxMrpPaise: b.maxMrpPaise != null ? String(b.maxMrpPaise) : '',
      costPaise: String(b.costPaise),
    })),
  }
}

/**
 * Converts form state to the request shape, or null when no strategy is chosen — which on create
 * means "no declared basis" and on update means "leave the basis unchanged", matching the
 * backend's null-means-untouched contract for this field group.
 */
function costBasisFieldsFromForm(form: CostBasisFormState): CostBasisFields | null {
  if (!form.strategy) return null
  return {
    costBasisStrategy: form.strategy,
    costAnchor: form.anchor || null,
    flatUnitCostPaise: form.flatUnitCostPaise ? parseInt(form.flatUnitCostPaise, 10) : null,
    percentBp: form.percentBp ? parseInt(form.percentBp, 10) : null,
    multiplierMilli: form.multiplierMilli ? parseInt(form.multiplierMilli, 10) : null,
    multiplierBase: form.multiplierBase || null,
    rateBands: form.rateBands
      .filter((b) => b.minMrpPaise !== '' && b.costPaise !== '')
      .map((b) => ({
        minMrpPaise: parseInt(b.minMrpPaise, 10),
        maxMrpPaise: b.maxMrpPaise ? parseInt(b.maxMrpPaise, 10) : null,
        costPaise: parseInt(b.costPaise, 10),
      })),
  }
}

const fieldLabelStyle: CSSProperties = {
  display: 'block',
  fontSize: '13px',
  fontWeight: 600,
  marginBottom: 'var(--s1)',
}

const fieldInputStyle: CSSProperties = {
  width: '100%',
  padding: '8px',
  border: '1px solid var(--line)',
  borderRadius: 'var(--r1)',
  fontSize: '14px',
  fontFamily: 'inherit',
}

/**
 * The cost-basis section shared by the create and edit modals: a strategy picker, the anchor —
 * shown only for the strategies that read one — and each strategy's own parameter inputs, plus a
 * small add/remove editor for an MRP rate card. Nothing here is required: leaving the strategy
 * blank declares no basis (create) or leaves the existing one alone (edit).
 */
function CostBasisEditor({
  value,
  onChange,
}: {
  value: CostBasisFormState
  onChange: (next: CostBasisFormState) => void
}) {
  const needsAnchor =
    value.strategy === 'PERCENT_OF_ANCHOR' ||
    value.strategy === 'MRP_RATE_RANGE' ||
    (value.strategy === 'MULTIPLIER' && value.multiplierBase === 'ANCHOR')
  const anchorLocked = value.strategy === 'MRP_RATE_RANGE' // this strategy only ever anchors to MRP

  const addBand = () =>
    onChange({
      ...value,
      rateBands: [...value.rateBands, { minMrpPaise: '', maxMrpPaise: '', costPaise: '' }],
    })
  const removeBand = (index: number) =>
    onChange({ ...value, rateBands: value.rateBands.filter((_, i) => i !== index) })
  const updateBand = (index: number, field: 'minMrpPaise' | 'maxMrpPaise' | 'costPaise', text: string) =>
    onChange({
      ...value,
      rateBands: value.rateBands.map((b, i) => (i === index ? { ...b, [field]: text } : b)),
    })

  return (
    <div style={{ marginTop: 'var(--s3)', paddingTop: 'var(--s3)', borderTop: '1px solid var(--line-soft)' }}>
      <label style={fieldLabelStyle}>Cost Basis (optional)</label>
      <select
        value={value.strategy}
        onChange={(e) => {
          const strategy = e.target.value as CostBasisStrategy | ''
          onChange({
            ...value,
            strategy,
            anchor: strategy === 'MRP_RATE_RANGE' ? 'MRP' : value.anchor,
          })
        }}
        style={{ ...fieldInputStyle, marginBottom: 'var(--s2)' }}
      >
        <option value="">No declared basis — use the manifest rate / apportionment</option>
        <option value="FLAT_PER_UNIT">Flat per unit</option>
        <option value="PERCENT_OF_ANCHOR">Percent of anchor (MRP or ASP)</option>
        <option value="MRP_RATE_RANGE">MRP rate card</option>
        <option value="MULTIPLIER">Multiplier on a base</option>
      </select>

      {value.strategy && needsAnchor && (
        <div style={{ marginBottom: 'var(--s2)' }}>
          <label style={fieldLabelStyle}>Anchor</label>
          <select
            value={value.anchor}
            disabled={anchorLocked}
            onChange={(e) => onChange({ ...value, anchor: e.target.value as CostAnchor | '' })}
            style={fieldInputStyle}
          >
            <option value="">Select an anchor…</option>
            <option value="MRP">MRP (batch's recorded maximum retail price)</option>
            <option value="ASP">ASP (product's observed online selling price)</option>
          </select>
        </div>
      )}

      {value.strategy === 'FLAT_PER_UNIT' && (
        <div style={{ marginBottom: 'var(--s2)' }}>
          <label style={fieldLabelStyle}>Flat unit cost (paise)</label>
          <input
            type="number"
            value={value.flatUnitCostPaise}
            onChange={(e) => onChange({ ...value, flatUnitCostPaise: e.target.value })}
            placeholder="e.g. 800 for ₹8.00"
            style={fieldInputStyle}
          />
        </div>
      )}

      {value.strategy === 'PERCENT_OF_ANCHOR' && (
        <div style={{ marginBottom: 'var(--s2)' }}>
          <label style={fieldLabelStyle}>Percent, in basis points (30% = 3000)</label>
          <input
            type="number"
            value={value.percentBp}
            onChange={(e) => onChange({ ...value, percentBp: e.target.value })}
            placeholder="e.g. 3000 for 30%"
            style={fieldInputStyle}
          />
        </div>
      )}

      {value.strategy === 'MRP_RATE_RANGE' && (
        <div style={{ marginBottom: 'var(--s2)' }}>
          <label style={fieldLabelStyle}>Rate bands (min inclusive, max exclusive, blank max = open top)</label>
          {value.rateBands.map((band, i) => (
            <div key={i} style={{ display: 'flex', gap: 'var(--s1)', marginBottom: 'var(--s1)', alignItems: 'center' }}>
              <input
                type="number"
                value={band.minMrpPaise}
                onChange={(e) => updateBand(i, 'minMrpPaise', e.target.value)}
                placeholder="min (paise)"
                style={{ ...fieldInputStyle, flex: 1 }}
              />
              <input
                type="number"
                value={band.maxMrpPaise}
                onChange={(e) => updateBand(i, 'maxMrpPaise', e.target.value)}
                placeholder="max (blank = open)"
                style={{ ...fieldInputStyle, flex: 1 }}
              />
              <input
                type="number"
                value={band.costPaise}
                onChange={(e) => updateBand(i, 'costPaise', e.target.value)}
                placeholder="cost (paise)"
                style={{ ...fieldInputStyle, flex: 1 }}
              />
              <button
                type="button"
                onClick={() => removeBand(i)}
                style={{ padding: '8px', background: 'transparent', border: '1px solid var(--line)', borderRadius: 'var(--r1)', cursor: 'pointer' }}
              >
                ✕
              </button>
            </div>
          ))}
          <button
            type="button"
            onClick={addBand}
            style={{ padding: '4px 10px', background: 'transparent', border: '1px solid var(--line)', borderRadius: 'var(--r1)', cursor: 'pointer', fontSize: '13px' }}
          >
            + Add band
          </button>
        </div>
      )}

      {value.strategy === 'MULTIPLIER' && (
        <>
          <div style={{ marginBottom: 'var(--s2)' }}>
            <label style={fieldLabelStyle}>Multiplier, in milli-units (1.25× = 1250)</label>
            <input
              type="number"
              value={value.multiplierMilli}
              onChange={(e) => onChange({ ...value, multiplierMilli: e.target.value })}
              placeholder="e.g. 1250 for 1.25x"
              style={fieldInputStyle}
            />
          </div>
          <div style={{ marginBottom: 'var(--s2)' }}>
            <label style={fieldLabelStyle}>Base</label>
            <select
              value={value.multiplierBase}
              onChange={(e) => onChange({ ...value, multiplierBase: e.target.value as MultiplierBase | '' })}
              style={fieldInputStyle}
            >
              <option value="">Select a base…</option>
              <option value="ENTERED_UNIT_COST">Entered unit cost</option>
              <option value="ANCHOR">Anchor (MRP or ASP)</option>
              <option value="STATED_VALUE">Manifest stated value</option>
            </select>
          </div>
          {value.multiplierBase === 'ENTERED_UNIT_COST' && (
            <div style={{ marginBottom: 'var(--s2)' }}>
              <label style={fieldLabelStyle}>Entered unit cost (paise)</label>
              <input
                type="number"
                value={value.flatUnitCostPaise}
                onChange={(e) => onChange({ ...value, flatUnitCostPaise: e.target.value })}
                placeholder="e.g. 800 for ₹8.00"
                style={fieldInputStyle}
              />
            </div>
          )}
        </>
      )}
    </div>
  )
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
  const [costBasisForm, setCostBasisForm] = useState<CostBasisFormState>(emptyCostBasisForm)
  const [editFormData, setEditFormData] = useState({
    supplierId: '',
    receivedOn: '',
    amountPaidPaise: '',
    freightPaise: '',
    allocationMethod: 'RELATIVE_MRP',
    categoryCode: '',
  })
  const [editCostBasisForm, setEditCostBasisForm] = useState<CostBasisFormState>(emptyCostBasisForm)

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
        costBasisFieldsFromForm(costBasisForm),
      )
      setMessage({ text: `✓ ${supplierName} created`, tone: 'ok' })
      setShowCreateModal(false)
      setFormData(emptyCreateForm)
      setCostBasisForm(emptyCostBasisForm)
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
  // has to re-key from scratch — including its cost basis, if it has declared one.
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
    setEditCostBasisForm(costBasisFormFromLot(lot))
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
        // Leaving the strategy blank in the form leaves the lot's basis untouched — see
        // costBasisFieldsFromForm; a chosen strategy replaces the whole basis and re-pins.
        ...costBasisFieldsFromForm(editCostBasisForm),
      })
      setMessage({ text: '✓ Lot updated', tone: 'ok' })
      setEditingLot(null)
      await loadLots()
    } catch (err) {
      // A 409 here is the freeze rule: stock from this lot has already sold, so its costs are
      // load-bearing on recorded sales and the message says as much. A 400 is an incomplete or
      // invalid cost basis, naming what's missing.
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
                      {/* The amount-paid cross-check: only present once a cost basis is declared
                          (see LotClosing.crossCheckCost). Reported, never blocking — a mismatch
                          is a flag to look at, not a reason receiving or pricing stops. */}
                      {lot.costVariancePaise != null && (
                        <div
                          style={{
                            marginTop: 'var(--s2)',
                            fontSize: '12px',
                            color: lot.costReconciles ? 'var(--ink-faint)' : 'var(--stop, #b45309)',
                          }}
                        >
                          {lot.costReconciles
                            ? 'Cost basis reconciles with amount paid'
                            : `Amount paid vs. derived cost differs by ₹${(Math.abs(lot.costVariancePaise) / 100).toFixed(2)}`}
                        </div>
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

              {formData.type === 'manual' && (
                <CostBasisEditor value={costBasisForm} onChange={setCostBasisForm} />
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

              <CostBasisEditor value={editCostBasisForm} onChange={setEditCostBasisForm} />
              {editCostBasisForm.strategy && (
                <p style={{ fontSize: '12px', color: 'var(--ink-faint)', marginTop: 'var(--s1)' }}>
                  Saving replaces the whole cost basis and re-costs every not-yet-sold unit in this lot.
                </p>
              )}
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
