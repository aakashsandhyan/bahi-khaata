import { useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { suppliers as api, BackendError } from './api'
import { rupees } from './money'
import type { Supplier, SupplierInput, SupplierLot } from './types'

// The Indian GSTIN format, mirrored from the backend so a malformed one is caught before the
// round-trip. Empty is allowed — many small vendors are unregistered.
const GSTIN = /^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$/

const EMPTY_FORM = {
  name: '',
  gstin: '',
  phone: '',
  address: '',
  contactPerson: '',
  notes: '',
}

const inputStyle = {
  width: '100%',
  padding: '8px',
  border: '1px solid var(--color-divider)',
  borderRadius: 'var(--radius-md)',
  fontSize: '14px',
  fontFamily: 'inherit',
} as const

export function Suppliers() {
  const [list, setList] = useState<Supplier[] | null>(null)
  const [search, setSearch] = useState('')
  const [activeOnly, setActiveOnly] = useState(false)
  const [message, setMessage] = useState<{ text: string; tone: string } | null>(null)

  const [selected, setSelected] = useState<Supplier | null>(null)
  const [selectedLots, setSelectedLots] = useState<SupplierLot[] | null>(null)

  const [showForm, setShowForm] = useState(false)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [form, setForm] = useState({ ...EMPTY_FORM })

  useEffect(() => {
    load()
    // Re-fetch when the filter changes; search filters the loaded list client-side below.
  }, [activeOnly])

  const load = async () => {
    try {
      setList(await api.list(activeOnly))
    } catch (err) {
      setMessage({ text: reason(err, 'Cannot reach the system.'), tone: 'stop' })
    }
  }

  const openDetail = async (s: Supplier) => {
    setSelected(s)
    setSelectedLots(null)
    try {
      setSelectedLots(await api.lots(s.id))
    } catch (err) {
      setMessage({ text: reason(err, 'Cannot load lots.'), tone: 'stop' })
    }
  }

  const openCreate = () => {
    setEditingId(null)
    setForm({ ...EMPTY_FORM })
    setShowForm(true)
  }

  const openEdit = (s: Supplier) => {
    setEditingId(s.id)
    setForm({
      name: s.name,
      gstin: s.gstin ?? '',
      phone: s.phone ?? '',
      address: s.address ?? '',
      contactPerson: s.contactPerson ?? '',
      notes: s.notes ?? '',
    })
    setShowForm(true)
  }

  const save = async () => {
    if (!form.name.trim()) {
      setMessage({ text: 'Name is required', tone: 'stop' })
      return
    }
    if (form.gstin.trim() && !GSTIN.test(form.gstin.trim())) {
      setMessage({ text: 'GSTIN is not a valid 15-character GSTIN', tone: 'stop' })
      return
    }
    const body: SupplierInput = {
      name: form.name.trim(),
      gstin: blankToNull(form.gstin),
      phone: blankToNull(form.phone),
      address: blankToNull(form.address),
      contactPerson: blankToNull(form.contactPerson),
      notes: blankToNull(form.notes),
    }
    try {
      const saved = editingId ? await api.update(editingId, body) : await api.create(body)
      setMessage({ text: `✓ ${body.name} ${editingId ? 'updated' : 'created'}`, tone: 'ok' })
      setShowForm(false)
      await load()
      if (saved && selected && selected.id === saved.id) setSelected(saved)
    } catch (err) {
      setMessage({ text: reason(err, 'Could not save supplier.'), tone: 'stop' })
    }
  }

  const toggleActive = async (s: Supplier) => {
    try {
      const updated = s.active ? await api.deactivate(s.id) : await api.reactivate(s.id)
      setMessage({ text: `✓ ${s.name} ${s.active ? 'deactivated' : 'reactivated'}`, tone: 'ok' })
      await load()
      if (updated && selected && selected.id === updated.id) setSelected(updated)
    } catch (err) {
      setMessage({ text: reason(err, 'Could not change status.'), tone: 'stop' })
    }
  }

  const needle = search.trim().toLowerCase()
  const filtered = (list ?? []).filter(
    (s) =>
      !needle ||
      s.name.toLowerCase().includes(needle) ||
      (s.gstin?.toLowerCase().includes(needle) ?? false),
  )

  // --- detail view ---
  if (selected) {
    return (
      <div className="suppliers">
        <button onClick={() => setSelected(null)} className="btn-ghost">← All suppliers</button>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', margin: 'var(--space-3) 0' }}>
          <h1 style={{ margin: 0 }}>
            {selected.name}
            {!selected.active && <span style={{ marginLeft: 'var(--space-2)', fontSize: '13px', color: 'var(--color-neutral-500)' }}>(inactive)</span>}
          </h1>
          <div style={{ display: 'flex', gap: 'var(--space-2)' }}>
            <button onClick={() => openEdit(selected)}>Edit</button>
            <button onClick={() => toggleActive(selected)}>
              {selected.active ? 'Deactivate' : 'Reactivate'}
            </button>
          </div>
        </div>

        {message && <div className={`banner ${message.tone}`}>{message.text}</div>}

        <div style={{ padding: 'var(--space-3)', borderRadius: 'var(--radius-md)', background: 'var(--color-surface)', border: '1px solid var(--color-divider)', marginBottom: 'var(--space-4)' }}>
          <Field label="GSTIN" value={selected.gstin} />
          <Field label="Phone" value={selected.phone} />
          <Field label="Address" value={selected.address} />
          <Field label="Contact person" value={selected.contactPerson} />
          <Field label="Notes" value={selected.notes} />
        </div>

        <h2 style={{ fontSize: '16px' }}>Lots received</h2>
        {selectedLots === null ? (
          <p>Loading lots…</p>
        ) : selectedLots.length === 0 ? (
          <p style={{ color: 'var(--color-neutral-500)' }}>No lots from this supplier yet.</p>
        ) : (
          <div className="overview-cards">
            {selectedLots.map((lot) => (
              <div key={lot.id} className="ov">
                <div style={{ padding: 'var(--space-3)', borderRadius: 'var(--radius-md)', background: 'var(--color-surface)', border: '1px solid var(--color-divider)' }}>
                  <div style={{ fontWeight: 600 }}>
                    {lot.categoryCode ? `${lot.categoryCode} · ` : ''}{rupees(lot.amountPaidPaise)}
                  </div>
                  <div style={{ fontSize: '13px', color: 'var(--color-neutral-500)' }}>{lot.receivedOn}</div>
                  <div style={{ fontSize: '12px', color: 'var(--color-neutral-700)', marginTop: '2px' }}>
                    {lot.isManual ? 'Manual' : 'Manifest'} · {lot.receivingComplete ? 'Complete' : 'In progress'}
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}

        {showForm && renderForm()}
      </div>
    )
  }

  // --- list view ---
  return (
    <div className="suppliers">
      <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 'var(--space-4)' }}>
        <button onClick={openCreate} className="btn-primary">+ Add Supplier</button>
      </div>

      {message && <div className={`banner ${message.tone}`}>{message.text}</div>}

      <div style={{ display: 'flex', gap: 'var(--space-3)', alignItems: 'center', marginBottom: 'var(--space-3)' }}>
        <input
          type="text"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Search name or GSTIN"
          style={{ ...inputStyle, maxWidth: '320px' }}
        />
        <label style={{ display: 'flex', gap: 'var(--space-1)', alignItems: 'center', fontSize: '14px', color: 'var(--color-neutral-700)' }}>
          <input type="checkbox" checked={activeOnly} onChange={(e) => setActiveOnly(e.target.checked)} />
          Active only
        </label>
      </div>

      {list === null ? (
        <p>Loading suppliers…</p>
      ) : filtered.length === 0 ? (
        <p style={{ color: 'var(--color-neutral-500)' }}>No suppliers{needle ? ' match that search' : ' yet'}.</p>
      ) : (
        <div className="overview-cards">
          {filtered.map((s) => (
            <div key={s.id} className="ov">
              <div
                onClick={() => openDetail(s)}
                style={{ padding: 'var(--space-3)', borderRadius: 'var(--radius-md)', background: 'var(--color-surface)', border: '1px solid var(--color-divider)', cursor: 'pointer' }}
              >
                <div style={{ fontWeight: 600, marginBottom: '2px' }}>
                  {s.name}
                  {!s.active && <span style={{ marginLeft: 'var(--space-2)', fontSize: '12px', color: 'var(--color-neutral-500)' }}>(inactive)</span>}
                </div>
                <div style={{ fontSize: '13px', color: 'var(--color-neutral-500)' }}>{s.gstin ?? 'No GSTIN'}</div>
              </div>
            </div>
          ))}
        </div>
      )}

      {showForm && renderForm()}
    </div>
  )

  function renderForm() {
    return (
      <div className="modal-overlay" onClick={() => setShowForm(false)}>
        <div className="modal-content" onClick={(e) => e.stopPropagation()}>
          <div className="modal-header">{editingId ? 'Edit Supplier' : 'Add Supplier'}</div>
          <div className="modal-body">
            <FormRow label="Name" required>
              <input type="text" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} placeholder="Supplier name" style={inputStyle} />
            </FormRow>
            <FormRow label="GSTIN">
              <input type="text" value={form.gstin} onChange={(e) => setForm({ ...form, gstin: e.target.value.toUpperCase() })} placeholder="15-character GSTIN (optional)" style={inputStyle} />
            </FormRow>
            <FormRow label="Phone">
              <input type="text" value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} placeholder="Optional" style={inputStyle} />
            </FormRow>
            <FormRow label="Address">
              <input type="text" value={form.address} onChange={(e) => setForm({ ...form, address: e.target.value })} placeholder="Optional" style={inputStyle} />
            </FormRow>
            <FormRow label="Contact person">
              <input type="text" value={form.contactPerson} onChange={(e) => setForm({ ...form, contactPerson: e.target.value })} placeholder="Optional" style={inputStyle} />
            </FormRow>
            <FormRow label="Notes">
              <input type="text" value={form.notes} onChange={(e) => setForm({ ...form, notes: e.target.value })} placeholder="Optional" style={inputStyle} />
            </FormRow>
          </div>
          <div className="modal-footer">
            <button onClick={() => setShowForm(false)}>Cancel</button>
            <button onClick={save} className="btn-primary">{editingId ? 'Save' : 'Create'}</button>
          </div>
        </div>
      </div>
    )
  }
}

function Field({ label, value }: { label: string; value: string | null }) {
  return (
    <div style={{ marginBottom: 'var(--space-2)' }}>
      <span style={{ fontSize: '12px', color: 'var(--color-neutral-500)' }}>{label}: </span>
      <span style={{ fontSize: '14px' }}>{value ?? '—'}</span>
    </div>
  )
}

function FormRow({ label, required, children }: { label: string; required?: boolean; children: ReactNode }) {
  return (
    <div style={{ marginBottom: 'var(--space-3)' }}>
      <label style={{ display: 'block', fontSize: '13px', fontWeight: 600, marginBottom: 'var(--space-1)' }}>
        {label}{required && <span style={{ color: 'var(--stop, #c0392b)' }}> *</span>}
      </label>
      {children}
    </div>
  )
}

function blankToNull(value: string): string | null {
  const trimmed = value.trim()
  return trimmed === '' ? null : trimmed
}

function reason(err: unknown, fallback: string): string {
  return err instanceof BackendError ? err.message : fallback
}

