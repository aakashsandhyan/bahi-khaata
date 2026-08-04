import { useEffect, useState } from 'react'
import { dashboard, BackendError } from './api'
import type { DashboardAlert, DashboardFunnelPoint, DashboardKpis, DashboardView, SaleSummary } from './types'
import type { View } from './Sidebar'
import { rupees } from './money'

/**
 * The desktop landing screen: one glance at revenue today, the received → priced → sold pipeline,
 * what needs a decision, and the till's most recent activity. Everything here is read-only — a
 * single `GET /api/dashboard` call — so a failing aggregate shows a plain error without ever
 * blocking the sidebar.
 */
export function Dashboard({ onNavigate }: { onNavigate: (v: View) => void }) {
  const [view, setView] = useState<DashboardView | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    dashboard
      .get()
      .then(setView)
      .catch((e) => setError(e instanceof BackendError ? e.message : 'Cannot reach the backend.'))
  }, [])

  if (error) {
    return (
      <div className="pad">
        <p className="banner stop">{error}</p>
      </div>
    )
  }
  if (!view) return <div className="pad">Loading…</div>

  return (
    <div className="dash">
      <KpiStrip kpis={view.kpis} />
      <div className="dash-body">
        <Funnel funnel={view.funnel} />
        <aside className="dash-aside">
          <Alerts alerts={view.alerts} onNavigate={onNavigate} />
          <RecentSales sales={view.recentSales} onNavigate={onNavigate} />
        </aside>
      </div>
    </div>
  )
}

function KpiStrip({ kpis }: { kpis: DashboardKpis }) {
  const { revenueToday, receivedVsPriced, recovery, gst } = kpis
  return (
    <div className="dash-kpis">
      <div className="dash-kpi">
        <div className="dash-kpi-label">Revenue today</div>
        <div className="dash-kpi-value">{rupees(revenueToday.totalPaise)}</div>
        <div className="dash-kpi-sub">
          {revenueToday.billCount} bill{revenueToday.billCount === 1 ? '' : 's'}
          {revenueToday.averagePaise != null ? ` · avg ${rupees(revenueToday.averagePaise)}` : ''}
        </div>
      </div>

      <div className="dash-kpi">
        <div className="dash-kpi-label">Received vs priced</div>
        <div className="dash-kpi-value">
          {receivedVsPriced.pricedUnits}/{receivedVsPriced.receivedUnits}
        </div>
        <div className="dash-kpi-sub">{receivedVsPriced.unpricedBacklogUnits} units still unpriced</div>
      </div>

      <div className="dash-kpi">
        <div className="dash-kpi-label">Recovery</div>
        <div className="dash-kpi-value">
          {recovery.ratio != null ? `${Math.round(recovery.ratio * 100)}%` : '—'}
        </div>
        <div className="dash-kpi-sub">
          {rupees(recovery.revenuePaise)} of {rupees(recovery.paidPaise)} paid
        </div>
      </div>

      <div className="dash-kpi">
        <div className="dash-kpi-label">GST collected</div>
        <div className="dash-kpi-value">{rupees(gst.taxAllTimePaise)}</div>
        <div className="dash-kpi-sub">
          {!gst.computed && <span className="tag est">Not yet computed</span>}
        </div>
      </div>
    </div>
  )
}

function Funnel({ funnel }: { funnel: DashboardFunnelPoint[] }) {
  const receivedUnits = funnel.find((f) => f.stage === 'RECEIVED')?.units ?? 0
  return (
    <section className="dash-funnel">
      <div className="dash-funnel-head">
        <h2>Received → Priced → Sold</h2>
      </div>
      <div className="dash-funnel-rows">
        {funnel.map((f) => {
          const pct = receivedUnits > 0 ? Math.min(100, (f.units / receivedUnits) * 100) : 0
          const fillClass = f.stage === 'PRICED' ? 'priced' : f.stage === 'SOLD' ? 'sold' : ''
          return (
            <div key={f.stage}>
              <div className="dash-funnel-row-head">
                <span className="dash-funnel-row-label">{f.stage.toLowerCase()}</span>
                <span className="dash-funnel-row-right">
                  {f.units.toLocaleString('en-IN')} units · {rupees(f.mrpPaise)} MRP
                </span>
              </div>
              <div className="dash-funnel-track">
                <div className={`dash-funnel-fill ${fillClass}`} style={{ width: `${pct}%` }} />
              </div>
            </div>
          )
        })}
      </div>
    </section>
  )
}

function Alerts({ alerts, onNavigate }: { alerts: DashboardAlert[]; onNavigate: (v: View) => void }) {
  return (
    <div>
      <h2 className="dash-section-title">Needs a decision</h2>
      {alerts.length === 0 ? (
        <p className="dash-empty">Nothing needs a decision right now.</p>
      ) : (
        <div className="dash-alerts">
          {alerts.map((a) => (
            <button
              key={a.signal}
              type="button"
              className="dash-alert"
              onClick={() => onNavigate(a.targetView as View)}
            >
              <div className="dash-alert-count">{a.count.toLocaleString('en-IN')}</div>
              <div className="dash-alert-message">{a.message}</div>
            </button>
          ))}
        </div>
      )}
    </div>
  )
}

function RecentSales({ sales, onNavigate }: { sales: SaleSummary[]; onNavigate: (v: View) => void }) {
  return (
    <div>
      <h2 className="dash-section-title">Recent sales</h2>
      <div className="dash-recent">
        {sales.length === 0 ? (
          <div className="dash-recent-row">
            <span className="dash-empty">No sales yet.</span>
          </div>
        ) : (
          sales.map((s) => (
            <div key={s.saleId} className="dash-recent-row">
              <span className="dash-recent-bill">{s.billNoFormatted}</span>
              <span className="dash-recent-meta">
                {new Date(s.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })} ·{' '}
                {s.paymentMethod}
              </span>
              <span className="dash-recent-total">{rupees(s.totalPaise)}</span>
            </div>
          ))
        )}
      </div>
      <button type="button" className="btn-ghost dash-recent-foot" onClick={() => onNavigate('sales')}>
        All sales →
      </button>
    </div>
  )
}
