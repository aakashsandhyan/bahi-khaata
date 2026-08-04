import { test, expect } from '@playwright/test'
import { seed } from '../seed'

// Deliberately does not use openScreen(): the whole point of this spec is that Dashboard is now
// the desktop landing view, reached with no click at all — see design.md D9/App.tsx's landing
// state. Every other screen spec still opens through the sidebar (see helpers.ts).
test('Dashboard: desktop lands here without clicking, tiles show seeded figures, an alert navigates', async ({ page }) => {
  await page.goto('/')

  // Header title + active sidebar entry — proof this is the landing view, not a navigation result.
  await expect(page.getByRole('heading', { name: 'Dashboard', level: 4 })).toBeVisible()
  await expect(page.locator('.sidebar-item.on')).toHaveText('Dashboard')

  // Four KPI tiles.
  const kpis = page.locator('.dash-kpi')
  await expect(kpis).toHaveCount(4)

  // Revenue today: the two seeded bills (₹998 + ₹499), and non-zero — the timestamp-format
  // tripwire from design.md D10. No other spec in this suite ever completes a sale, so this total
  // is stable regardless of run order.
  const revenueTile = kpis.filter({ hasText: 'Revenue today' })
  await expect(revenueTile.locator('.dash-kpi-value')).toHaveText('₹1,497')
  await expect(revenueTile).toContainText('2 bills')

  // GST: structurally ₹0 until gst-inclusive-pricing ships, but never a bare zero.
  const gstTile = kpis.filter({ hasText: 'GST collected' })
  await expect(gstTile.locator('.dash-kpi-value')).toHaveText('₹0')
  await expect(gstTile.getByText('Not yet computed')).toBeVisible()

  // Recent-sales rail: the seeded bill is visible, checked before navigating away below.
  await expect(page.locator('.dash-recent')).toContainText(seed.sales.first.billNoFormatted)

  // An alert row navigates to its owning screen. The unpriced-backlog alert is used because it is
  // the one signal no earlier spec in the suite ever fully clears (product d stays unpriced
  // throughout) — its exact count moves as 05-pricing.spec.ts prices the other seeded unpriced
  // product, so this asserts presence and navigation, not a specific number.
  const unpriced = page.locator('.dash-alert', { hasText: 'waiting on a shelf price' })
  await expect(unpriced).toBeVisible()
  await unpriced.click()
  await expect(page.getByRole('heading', { name: 'Pricing', level: 4 })).toBeVisible()
})
