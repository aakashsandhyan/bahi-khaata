import { useEffect, useState } from 'react'
import { billSettings, BackendError } from '../api'
import type { BillSettings as BillSettingsType } from '../types'

/**
 * The text printed on every bill — shop identity, GSTIN, the title, the declaration, the footer.
 * This is the whole GST posture: under the composition scheme the title is "Bill of Supply" and the
 * declaration is the mandatory composition wording; registering under regular GST is an edit here
 * (title → "Tax Invoice", clear the declaration), not a code change.
 */
export function BillSettings() {
  const [form, setForm] = useState<BillSettingsType | null>(null)
  const [saving, setSaving] = useState(false)
  const [message, setMessage] = useState<{ text: string; tone: string } | null>(null)

  useEffect(() => {
    billSettings
      .get()
      .then(setForm)
      .catch((err) =>
        setMessage({ text: err instanceof BackendError ? err.message : 'Failed to load settings', tone: 'stop' }),
      )
  }, [])

  const save = async () => {
    if (!form) return
    setSaving(true)
    try {
      const updated = await billSettings.save(form)
      if (updated) setForm(updated)
      setMessage({ text: '✓ Bill settings saved', tone: 'ok' })
      setTimeout(() => setMessage(null), 3000)
    } catch (err) {
      setMessage({ text: err instanceof BackendError ? err.message : 'Failed to save', tone: 'stop' })
    } finally {
      setSaving(false)
    }
  }

  if (!form) {
    return (
      <div style={{ maxWidth: 560, margin: '0 auto', padding: 'var(--s4)' }}>
        <h1>Bill Settings</h1>
        {message && <div className={`banner ${message.tone}`}>{message.text}</div>}
      </div>
    )
  }

  const field: React.CSSProperties = {
    width: '100%', padding: '8px', border: '1px solid var(--line)',
    borderRadius: 'var(--r1)', fontSize: '14px', fontFamily: 'inherit', boxSizing: 'border-box',
  }
  const label: React.CSSProperties = {
    display: 'block', fontSize: '13px', fontWeight: 600, marginBottom: 'var(--s1)', marginTop: 'var(--s3)',
  }
  const set = (patch: Partial<BillSettingsType>) => setForm({ ...form, ...patch })

  return (
    <div style={{ maxWidth: 560, margin: '0 auto', padding: 'var(--s4)' }}>
      <h1>Bill Settings</h1>

      {message && <div className={`banner ${message.tone}`}>{message.text}</div>}

      <label style={{ ...label, marginTop: 0 }}>Shop name</label>
      <input style={field} value={form.shopName} onChange={(e) => set({ shopName: e.target.value })} />

      <label style={label}>Address</label>
      <input style={field} value={form.address} onChange={(e) => set({ address: e.target.value })} />

      <label style={label}>GSTIN</label>
      <input style={field} value={form.gstin} onChange={(e) => set({ gstin: e.target.value })} />

      <label style={label}>Bill title</label>
      <input
        style={field}
        value={form.billTitle}
        placeholder="Bill of Supply"
        onChange={(e) => set({ billTitle: e.target.value })}
      />

      <label style={label}>Declaration</label>
      <textarea
        style={{ ...field, minHeight: 64, resize: 'vertical' }}
        value={form.declaration}
        placeholder="Composition taxable person, not eligible to collect tax on supplies"
        onChange={(e) => set({ declaration: e.target.value })}
      />

      <label style={label}>Footer</label>
      <input style={field} value={form.footer} onChange={(e) => set({ footer: e.target.value })} />

      <button onClick={save} disabled={saving} className="btn-primary" style={{ marginTop: 'var(--s4)' }}>
        {saving ? 'Saving…' : 'Save Settings'}
      </button>
    </div>
  )
}
