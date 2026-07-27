import { useEffect, useState } from 'react'
import { printer, BackendError } from '../api'
import type { PrinterConfig as PrinterConfigType } from '../types'

export function PrinterConfig() {
  const [config, setConfig] = useState<PrinterConfigType | null>(null)
  const [formData, setFormData] = useState({
    address: '',
    portSpeed: 9600,
    paperSize: '4x6',
    copiesDefault: 1,
    enabled: true,
  })
  const [testLoading, setTestLoading] = useState(false)
  const [saveLoading, setSaveLoading] = useState(false)
  const [message, setMessage] = useState<{ text: string; tone: string } | null>(null)

  useEffect(() => {
    loadConfig()
  }, [])

  const loadConfig = async () => {
    try {
      const cfg = await printer.getConfig()
      setConfig(cfg)
      setFormData({
        address: cfg.address,
        portSpeed: cfg.portSpeed,
        paperSize: cfg.paperSize,
        copiesDefault: cfg.copiesDefault,
        enabled: cfg.enabled,
      })
    } catch (err) {
      setMessage({
        text: err instanceof BackendError ? err.message : 'Failed to load printer config',
        tone: 'stop',
      })
    }
  }

  const handleTest = async () => {
    setTestLoading(true)
    try {
      const result = await printer.testPrinter()
      if (result) {
        setConfig((prev) => (prev ? { ...prev, testStatus: result.testStatus as any, lastTestedAt: result.testedAt } : null))
        setMessage({
          text: result.message,
          tone: result.testStatus === 'OK' ? 'ok' : 'stop',
        })
      }
    } catch (err) {
      setMessage({
        text: err instanceof BackendError ? err.message : 'Test failed',
        tone: 'stop',
      })
    } finally {
      setTestLoading(false)
    }
  }

  const handleSave = async () => {
    setSaveLoading(true)
    try {
      const updated = await printer.saveConfig(formData)
      setConfig(updated)
      setMessage({
        text: '✓ Printer config saved',
        tone: 'ok',
      })
      setTimeout(() => setMessage(null), 3000)
    } catch (err) {
      setMessage({
        text: err instanceof BackendError ? err.message : 'Failed to save config',
        tone: 'stop',
      })
    } finally {
      setSaveLoading(false)
    }
  }

  const getStatusBadgeColor = () => {
    if (!config?.testStatus) return 'var(--ink-faint)'
    if (config.testStatus === 'OK') return 'var(--good)'
    return 'var(--stop)'
  }

  const getStatusBadgeBg = () => {
    if (!config?.testStatus) return 'var(--line-soft)'
    if (config.testStatus === 'OK') return 'var(--good-tint)'
    return 'var(--stop-tint)'
  }

  return (
    <div style={{ maxWidth: '560px', margin: '0 auto', padding: 'var(--s4)' }}>
      <h1>Printer Configuration</h1>

      {message && <div className={`banner ${message.tone}`}>{message.text}</div>}

      <div
        style={{
          padding: 'var(--s3)',
          background: getStatusBadgeBg(),
          borderRadius: 'var(--r1)',
          marginBottom: 'var(--s3)',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--s2)', marginBottom: 'var(--s1)' }}>
          <span
            style={{
              width: '12px',
              height: '12px',
              borderRadius: '50%',
              background: getStatusBadgeColor(),
            }}
          />
          <span style={{ fontWeight: '600', color: getStatusBadgeColor() }}>
            {config?.testStatus || 'Not tested'}
          </span>
        </div>
        {config?.lastTestedAt && (
          <div style={{ fontSize: '12px', color: getStatusBadgeColor() }}>
            Last tested: {new Date(config.lastTestedAt).toLocaleTimeString()}
          </div>
        )}
        {config?.testError && (
          <div style={{ fontSize: '12px', color: getStatusBadgeColor(), marginTop: 'var(--s1)' }}>
            {config.testError}
          </div>
        )}
      </div>

      <div style={{ marginBottom: 'var(--s3)', padding: 'var(--s3)', background: 'var(--line-soft)', borderRadius: 'var(--r1)' }}>
        <div style={{ marginBottom: 'var(--s3)' }}>
          <label style={{ display: 'block', fontSize: '13px', fontWeight: '600', marginBottom: 'var(--s1)' }}>
            Printer Address
          </label>
          <input
            type="text"
            placeholder="192.168.1.100:9100 or /dev/ttyUSB0"
            value={formData.address}
            onChange={(e) => setFormData({ ...formData, address: e.target.value })}
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

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--s2)', marginBottom: 'var(--s3)' }}>
          <div>
            <label style={{ display: 'block', fontSize: '13px', fontWeight: '600', marginBottom: 'var(--s1)' }}>
              Port Speed
            </label>
            <input
              type="number"
              value={formData.portSpeed}
              onChange={(e) => setFormData({ ...formData, portSpeed: parseInt(e.target.value) || 9600 })}
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
          <div>
            <label style={{ display: 'block', fontSize: '13px', fontWeight: '600', marginBottom: 'var(--s1)' }}>
              Default Copies
            </label>
            <input
              type="number"
              min="1"
              max="5"
              value={formData.copiesDefault}
              onChange={(e) => setFormData({ ...formData, copiesDefault: Math.max(1, parseInt(e.target.value) || 1) })}
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

        <div style={{ marginBottom: 'var(--s3)' }}>
          <label style={{ display: 'flex', alignItems: 'center', gap: 'var(--s2)', cursor: 'pointer' }}>
            <input
              type="checkbox"
              checked={formData.enabled}
              onChange={(e) => setFormData({ ...formData, enabled: e.target.checked })}
            />
            <span style={{ fontSize: '14px' }}>Enable Printer</span>
          </label>
        </div>

        <div style={{ display: 'flex', gap: 'var(--s2)' }}>
          <button
            onClick={handleSave}
            disabled={saveLoading}
            className="btn-primary"
            style={{ flex: 1 }}
          >
            {saveLoading ? 'Saving...' : 'Save Config'}
          </button>
          <button
            onClick={handleTest}
            disabled={testLoading || !formData.address}
            style={{ flex: 1, opacity: testLoading || !formData.address ? 0.5 : 1 }}
          >
            {testLoading ? 'Testing...' : 'Test Print'}
          </button>
        </div>
      </div>
    </div>
  )
}
