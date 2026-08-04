import { useState } from 'react'
import { capture, BackendError } from './api'

/**
 * The phone capture screen. Anyone on the shop network keys in what they can read off a product —
 * a name, and the MRP if it is printed — and it lands in the review queue. No pricing here; that is
 * decided later at a desk. Kept deliberately plain and one field at a time for a thumb.
 */
export function MobileCapture() {
  const [name, setName] = useState('')
  const [mrp, setMrp] = useState('')
  const [description, setDescription] = useState('')
  const [saved, setSaved] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const submit = (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)
    if (!name.trim()) return setError('Enter a name.')
    const mrpNum = Number(mrp)
    const mrpPaise = mrp.trim() && Number.isFinite(mrpNum) && mrpNum > 0 ? Math.round(mrpNum * 100) : null
    capture
      .submit({ name: name.trim(), mrpPaise, description: description.trim() || null, lotId: null })
      .then(() => {
        setSaved(name.trim())
        setName(''); setMrp(''); setDescription('')
      })
      .catch((e) => setError(e instanceof BackendError ? e.message : 'Could not save.'))
  }

  return (
    <div className="pad" style={{ maxWidth: 480, margin: '0 auto' }}>
      <h1>Capture a product</h1>
      <p style={{ color: 'var(--color-neutral-500)', fontSize: 14 }}>
        Type what you can read off the item. Someone will price it at the desk.
      </p>
      {saved && <p className="good">✓ Sent “{saved}” for review.</p>}
      <form onSubmit={submit} style={{ display: 'grid', gap: 'var(--space-3)', marginTop: 'var(--space-3)' }}>
        <label style={{ fontWeight: 600 }}>
          Name
          <input autoFocus value={name} onChange={(e) => setName(e.target.value)}
            style={{ width: '100%', padding: 12, fontSize: 16, marginTop: 4 }} />
        </label>
        <label style={{ fontWeight: 600 }}>
          MRP (if printed)
          <input value={mrp} onChange={(e) => setMrp(e.target.value)} inputMode="decimal" placeholder="₹"
            style={{ width: '100%', padding: 12, fontSize: 16, marginTop: 4 }} />
        </label>
        <label style={{ fontWeight: 600 }}>
          Notes (optional)
          <input value={description} onChange={(e) => setDescription(e.target.value)}
            style={{ width: '100%', padding: 12, fontSize: 16, marginTop: 4 }} />
        </label>
        {error && <p className="stop">{error}</p>}
        <button className="btn-primary" type="submit" style={{ padding: 16, fontSize: 18 }}>Send for review</button>
      </form>
    </div>
  )
}
