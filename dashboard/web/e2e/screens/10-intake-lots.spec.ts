import { test, expect } from '@playwright/test'
import { seed } from '../seed'
import { openScreen } from '../helpers'

test('Intake: Lines Δ for the seeded lot, manual lot creation, receiving-finished, and reconcile-close with confirm', async ({ page }) => {
  await openScreen(page, 'Intake')

  // Lines tab (seeded, manifest lot): the widget's manifest reconciliation columns. By this point
  // in the suite (single worker, file order), 08-inventory-onpaper.spec.ts has already counted
  // all 5 units of this product onto the floor, so it is fully reconciled here: Expected 5,
  // Counted 5, Δ 0 — still exercising the same three columns 02-intake.spec.ts sees at 0 counted.
  await page.locator('.intake-rail-item', { hasText: seed.supplier.name }).first().click()
  await page.getByRole('button', { name: 'Lines', exact: true }).click()
  const widgetRow = page.locator('.inv-table tbody tr', { hasText: seed.products.unpackingOnly.name })
  await expect(widgetRow).toBeVisible()
  const widgetCells = widgetRow.locator('td')
  await expect(widgetCells.nth(1)).toHaveText(String(seed.products.unpackingOnly.quantityExpected))
  await expect(widgetCells.nth(2)).toHaveText('5')
  await expect(widgetCells.nth(3)).toHaveText('0')
  // D8: exactly four columns — no grade/condition, no MRP, no list-price column.
  await expect(page.locator('.inv-table th')).toHaveCount(4)
  await expect(page.locator('.inv-table th', { hasText: 'Grade' })).toHaveCount(0)
  await expect(page.locator('.inv-table th', { hasText: 'MRP' })).toHaveCount(0)

  // Manual lot creation from the rail header — manual only, no manifest-import option exposed.
  await page.getByRole('button', { name: '+ New lot' }).click()
  const modal = page.locator('.modal-content')
  await expect(modal.getByText('Manifest')).toHaveCount(0)
  await modal.locator('select').selectOption({ label: seed.supplier.name })
  await modal.locator('input[type="date"]').fill('2026-08-02')
  await modal.locator('input[type="number"]').fill('1000')
  await modal.getByRole('button', { name: 'Create', exact: true }).click()

  // The new lot is selected on the rail and lands on the Boxes tab, showing the manual framing:
  // counting is the manifest, so adding a product both discovers and counts it.
  const newLotItem = page.locator('.intake-rail-item.on')
  await expect(newLotItem).toContainText('2026-08-02')
  await page.getByPlaceholder('e.g., Face Cream').fill('E2E Intake Manual Product')
  await page.getByRole('button', { name: 'Add product', exact: true }).click()
  await expect(page.getByText('✓ E2E Intake Manual Product added')).toBeVisible()

  // Receiving-finished (a manual lot's only way out of Counting, D4) moves the step strip on.
  await page.getByRole('button', { name: 'Receiving finished' }).first().click()
  await expect(page.locator('.intake-step.active')).toHaveText('Reconcile')

  // Reconcile & close: the product just added created a synthetic, still-unopened carton —
  // closing surfaces it and only closes on a deliberate second confirm. The same synthetic
  // carton is also why per-box completeness (goods-in-reconciliation spec) reads 1 not-started.
  await page.getByRole('button', { name: 'Reconcile & close', exact: true }).click()
  await expect(page.getByText('1 / 0 / 0')).toBeVisible()
  await expect(page.locator('.intake-unopened')).toBeVisible()
  await page.getByRole('button', { name: 'Close lot', exact: true }).click()
  await page.getByRole('button', { name: /Close anyway/ }).click()

  // A closed lot leaves the rail on its own — `GET /api/lots` returns open lots only.
  await expect(page.locator('.intake-rail-item', { hasText: '2026-08-02' })).toBeHidden()
})

test('Intake: a lot with nothing unopened closes without the confirm gate, and the pricing hand-off lands on Pricing', async ({ page }) => {
  await openScreen(page, 'Intake')

  // A bare manual lot — no product added, so no synthetic carton, nothing unopened.
  await page.getByRole('button', { name: '+ New lot' }).click()
  const modal = page.locator('.modal-content')
  await modal.locator('select').selectOption({ label: seed.supplier.name })
  await modal.locator('input[type="date"]').fill('2026-08-03')
  await modal.locator('input[type="number"]').fill('500')
  await modal.getByRole('button', { name: 'Create', exact: true }).click()
  await page.getByRole('button', { name: 'Receiving finished' }).first().click()

  await page.getByRole('button', { name: 'Reconcile & close', exact: true }).click()

  // The pricing hand-off is a real navigation, not a dead link.
  await page.getByRole('button', { name: 'Pricing →' }).click()
  await expect(page.locator('.shell-title')).toHaveText('Pricing')

  // Back to the clean lot: Close goes through in one step — no unopened list, no 'Close anyway'.
  await openScreen(page, 'Intake')
  await page.locator('.intake-rail-item', { hasText: '2026-08-03' }).click()
  await page.getByRole('button', { name: 'Reconcile & close', exact: true }).click()
  await page.getByRole('button', { name: 'Close lot', exact: true }).click()
  await expect(page.getByRole('button', { name: /Close anyway/ })).toHaveCount(0)
  await expect(page.locator('.intake-rail-item', { hasText: '2026-08-03' })).toBeHidden()
})
