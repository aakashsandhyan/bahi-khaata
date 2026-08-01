import { useEffect, useState } from 'react'
import { receiptPrinter, BackendError } from '../api'
import type { ReceiptPrinterConfig as ReceiptConfig } from '../types'

/**
 * The ESC/POS receipt printer — the second printer, separate from the label printer. Address is a
 * host:port (raw-9100) when the transport is LAN, or the OS printer name when USB. "Test print"
 * sends a real slip, which the connection alone cannot prove (a queue reports ready while the
 * printer is out of paper or off).
 */
export function ReceiptPrinterConfig() {
  const [config, setConfig] = useState<ReceiptConfig | null>(null)
  const [form, setForm] = useState({ address: '', transport: 'LAN', enabled: false })
  const [saving, setSaving] = useState(false)
  const [testing, setTesting] = useState(false)
  const [message, setMessage] = useState<{ text: string; tone: string } | null>(null)

  useEffect(() => {
    load()
  }, [])

  const load = async () => {
    try {
      const cfg = await receiptPrinter.getConfig()
      setConfig(cfg)
      setForm({ address: cfg.address, transport: cfg.transport, enabled: cfg.enabled })
    } catch (err) {
      setMessage({ text: err instanceof BackendError ? err.message : 'Failed to load config', tone: 'stop' })
    }
  }

  const save = async () => {
    setSaving(true)
    try {
      const updated = await receiptPrinter.saveConfig(form)
      if (updated) setConfig(updated)
      setMessage({ text: '✓ Receipt printer config saved', tone: 'ok' })
      setTimeout(() => setMessage(null), 3000)
    } catch (err) {
      setMessage({ text: err instanceof BackendError ? err.message : 'Failed to save', tone: 'stop' })
    } finally {
      setSaving(false)
    }
  }

  const test = async () => {
    setTesting(true)
    try {
      const result = await receiptPrinter.testPrint()
      if (result) {
        setMessage({ text: result.message, tone: result.testStatus === 'OK' ? 'ok' : 'stop' })
        load()
      }
    } catch (err) {
      setMessage({ text: err instanceof BackendError ? err.message : 'Test failed', tone: 'stop' })
    } finally {
      setTesting(false)
    }
  }

  const statusColor = !config?.testStatus ? 'var(--ink-faint)' : config.testStatus === 'OK' ? 'var(--good)' : 'var(--stop)'
  const statusBg = !config?.testStatus ? 'var(--line-soft)' : config.testStatus === 'OK' ? 'var(--good-tint)' : 'var(--stop-tint)'

  const field: React.CSSProperties = {
    width: '100%', padding: '8px', border: '1px solid var(--line)',
    borderRadius: 'var(--r1)', fontSize: '14px', fontFamily: 'inherit',
  }
  const label: React.CSSProperties = {
    display: 'block', fontSize: '13px', fontWeight: 600, marginBottom: 'var(--s1)',
  }

  return (
    <div style={{ maxWidth: '560px', margin: '0 auto', padding: 'var(--s4)' }}>
      <h1>Receipt Printer</h1>

      {message && <div className={`banner ${message.tone}`}>{message.text}</div>}

      <div style={{ padding: 'var(--s3)', background: statusBg, borderRadius: 'var(--r1)', marginBottom: 'var(--s3)' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--s2)' }}>
          <span style={{ width: 12, height: 12, borderRadius: '50%', background: statusColor }} />
          <span style={{ fontWeight: 600, color: statusColor }}>{config?.testStatus || 'Not tested'}</span>
        </div>
        {config?.lastTestedAt && (
          <div style={{ fontSize: 12, color: statusColor, marginTop: 'var(--s1)' }}>
            Last tested: {new Date(config.lastTestedAt).toLocaleString()}
          </div>
        )}
        {config?.testError && (
          <div style={{ fontSize: 12, color: statusColor, marginTop: 'var(--s1)' }}>{config.testError}</div>
        )}
      </div>

      <div style={{ padding: 'var(--s3)', background: 'var(--line-soft)', borderRadius: 'var(--r1)' }}>
        <div style={{ marginBottom: 'var(--s3)' }}>
          <label style={label}>Transport</label>
          <select
            value={form.transport}
            onChange={(e) => setForm({ ...form, transport: e.target.value })}
            style={field}
          >
            <option value="LAN">LAN (network, raw port 9100)</option>
            <option value="USB">USB (OS printer name)</option>
          </select>
        </div>

        <div style={{ marginBottom: 'var(--s3)' }}>
          <label style={label}>Printer Address</label>
          <input
            type="text"
            placeholder={form.transport === 'USB' ? '"EPSON TM-T82" (Windows printer name)' : '192.168.1.50:9100'}
            value={form.address}
            onChange={(e) => setForm({ ...form, address: e.target.value })}
            style={field}
          />
        </div>

        <div style={{ marginBottom: 'var(--s3)' }}>
          <label style={{ display: 'flex', alignItems: 'center', gap: 'var(--s2)', cursor: 'pointer' }}>
            <input
              type="checkbox"
              checked={form.enabled}
              onChange={(e) => setForm({ ...form, enabled: e.target.checked })}
            />
            <span style={{ fontSize: 14 }}>Enable receipt printing</span>
          </label>
        </div>

        <div style={{ display: 'flex', gap: 'var(--s2)' }}>
          <button onClick={save} disabled={saving} className="btn-primary" style={{ flex: 1 }}>
            {saving ? 'Saving…' : 'Save Config'}
          </button>
          <button onClick={test} disabled={testing || !form.address} style={{ flex: 1, opacity: testing || !form.address ? 0.5 : 1 }}>
            {testing ? 'Testing…' : 'Test Print'}
          </button>
        </div>
      </div>
    </div>
  )
}
