import { useEffect, useState } from 'react'
import { receiving, unpacking, intake, BackendError } from './api'
import type { DeliveryProgress, LotIntakeStats, LotSummary } from './types'
import type { View } from './Sidebar'
import { IntakeRail } from './intake/IntakeRail'
import { IntakeHeader } from './intake/IntakeHeader'
import { IntakeSteps } from './intake/IntakeSteps'
import { BoxesTab } from './intake/BoxesTab'
import { LinesTab } from './intake/LinesTab'
import { ReconcileCloseTab } from './intake/ReconcileCloseTab'
import { LotMathRail } from './intake/LotMathRail'
import { CreateLotModal } from './intake/CreateLotModal'

type Tab = 'boxes' | 'lines' | 'reconcile'

const TABS: { value: Tab; label: string }[] = [
  { value: 'boxes', label: 'Boxes' },
  { value: 'lines', label: 'Lines' },
  { value: 'reconcile', label: 'Reconcile & close' },
]

/**
 * The Intake hub: one screen for a lot's whole state, replacing the retired Receiving and Lots
 * screens (design decision D1 of palletworks-intake). A state-switched orchestrator (no router —
 * phase-1 decision, carried forward) that fetches the rail's two list sources and the selected
 * lot's stats, and hands each tab its own lot to fetch the rest from.
 *
 * `receiving.lots()` already filters to open lots only (D2), so a closed lot disappears from the
 * rail on its own — `loadAll` below re-picks a remaining lot (or none) whenever the previously
 * selected one is no longer in the list, which is what a close naturally causes.
 */
export function Intake({ onNavigate }: { onNavigate: (v: View) => void }) {
  const [lots, setLots] = useState<LotSummary[] | null>(null)
  const [deliveries, setDeliveries] = useState<DeliveryProgress[]>([])
  const [error, setError] = useState<string | null>(null)
  const [selectedLotId, setSelectedLotId] = useState<string | null>(null)
  const [tab, setTab] = useState<Tab>('boxes')
  const [showCreateModal, setShowCreateModal] = useState(false)

  const [stats, setStats] = useState<LotIntakeStats | null>(null)
  const [statsLoading, setStatsLoading] = useState(false)
  const [statsFailed, setStatsFailed] = useState(false)

  const loadAll = () => {
    setError(null)
    Promise.all([receiving.lots(), unpacking.deliveries()])
      .then(([lotsData, deliveriesData]) => {
        setLots(lotsData)
        setDeliveries(deliveriesData)
        setSelectedLotId((current) => {
          if (current && lotsData.some((l) => l.id === current)) return current
          return lotsData[0]?.id ?? null
        })
      })
      .catch((e) => setError(e instanceof BackendError ? e.message : 'Cannot reach the backend.'))
  }
  useEffect(loadAll, [])

  useEffect(() => {
    if (!selectedLotId) {
      setStats(null)
      return
    }
    setStatsLoading(true)
    setStatsFailed(false)
    intake
      .stats(selectedLotId)
      .then((s) => setStats(s))
      .catch(() => setStatsFailed(true))
      .finally(() => setStatsLoading(false))
  }, [selectedLotId])

  const onChanged = () => {
    loadAll()
    if (selectedLotId) intake.stats(selectedLotId).then(setStats).catch(() => setStatsFailed(true))
  }

  const selectedLot = lots?.find((l) => l.id === selectedLotId) ?? null
  const delivery = deliveries.find((d) => d.lotId === selectedLotId) ?? null

  if (error) {
    return (
      <div className="pad">
        <p className="banner stop">{error}</p>
      </div>
    )
  }

  return (
    <div className="intake">
      <div className="intake-shell">
        <IntakeRail
          lots={lots}
          deliveries={deliveries}
          selectedLotId={selectedLotId}
          onSelect={(id) => { setSelectedLotId(id); setTab('boxes') }}
          onNewLot={() => setShowCreateModal(true)}
        />

        <div>
          {!selectedLot ? (
            <p className="intake-rail-empty">Select a lot, or create one to get started.</p>
          ) : (
            <>
              <h2>
                {selectedLot.supplier}
                {selectedLot.isManual && <span className="text-muted"> (Manual)</span>}
              </h2>
              <IntakeHeader stats={stats} loading={statsLoading} failed={statsFailed} />
              <IntakeSteps lot={selectedLot} delivery={delivery} />

              <div className="mode-toggle">
                {TABS.map((t) => (
                  <button key={t.value} type="button" className={tab === t.value ? 'on' : ''} onClick={() => setTab(t.value)}>
                    {t.label}
                  </button>
                ))}
              </div>

              {tab === 'boxes' && <BoxesTab lot={selectedLot} stats={stats} onChanged={onChanged} />}
              {tab === 'lines' && <LinesTab lot={selectedLot} />}
              {tab === 'reconcile' && (
                <ReconcileCloseTab
                  lot={selectedLot}
                  stats={stats}
                  onGoToPricing={() => onNavigate('pricing')}
                  onChanged={onChanged}
                />
              )}
            </>
          )}
        </div>

        {selectedLot && (
          <LotMathRail
            lot={selectedLot}
            delivery={delivery}
            stats={stats}
            onGoToReconcile={() => setTab('reconcile')}
            onChanged={onChanged}
          />
        )}
      </div>

      <CreateLotModal
        open={showCreateModal}
        onClose={() => setShowCreateModal(false)}
        onCreated={(lot) => {
          setShowCreateModal(false)
          setSelectedLotId(lot.id)
          setTab('boxes')
          loadAll()
        }}
      />
    </div>
  )
}
