import { useState } from 'react'
import { Checkout } from './Checkout'
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
  // Phones are operators' devices and their nav is hidden, so they must land on unpacking, not
  // the till they cannot navigate away from. Wider screens — a counter tablet or a desk — are
  // where the till belongs, so they open on it.
  const [view, setView] = useState<'checkout' | 'unpacking' | 'pricing'>(
    typeof window !== 'undefined' && window.innerWidth <= 760 ? 'unpacking' : 'checkout',
  )
  return (
    <>
      <nav className="topnav">
        <span className="brand">Bachat Baazar</span>
        <button className={view === 'checkout' ? 'on' : ''} onClick={() => setView('checkout')}>
          Till
        </button>
        <button className={view === 'unpacking' ? 'on' : ''} onClick={() => setView('unpacking')}>
          Unpacking
        </button>
        <button className={view === 'pricing' ? 'on' : ''} onClick={() => setView('pricing')}>
          Pricing
        </button>
      </nav>
      <main>{view === 'checkout' ? <Checkout /> : view === 'unpacking' ? <Unpacking /> : <Pricing />}</main>
    </>
  )
}
