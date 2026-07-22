import { useState } from 'react'
import { Unpacking } from './Unpacking'
import { Pricing } from './Pricing'

/**
 * The admin dashboard shell.
 *
 * Two views for now: unpacking, which several people run at once to get stock counted fast, and
 * pricing, which one manager runs to decide what it sells for. They are separate pages
 * deliberately — pricing shows cost and margin, and when roles arrive the two will live behind
 * different doors. Keeping them apart now makes that a move rather than a untangling.
 */
export function App() {
  const [view, setView] = useState<'unpacking' | 'pricing'>('unpacking')
  return (
    <>
      <nav className="topnav">
        <span className="brand">Bachat Baazar</span>
        <button className={view === 'unpacking' ? 'on' : ''} onClick={() => setView('unpacking')}>
          Unpacking
        </button>
        <button className={view === 'pricing' ? 'on' : ''} onClick={() => setView('pricing')}>
          Pricing
        </button>
      </nav>
      <main>{view === 'unpacking' ? <Unpacking /> : <Pricing />}</main>
    </>
  )
}
