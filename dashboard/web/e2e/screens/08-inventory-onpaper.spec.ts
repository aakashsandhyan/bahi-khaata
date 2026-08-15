import { test, expect } from '@playwright/test'
import { seed } from '../seed'
import { openScreen } from '../helpers'

// Catalog is deleted (design decision D9 of palletworks-nav): browsing, the found/on-paper gap,
// and opening a product to its detail all moved to Inventory's On paper scope and Item detail.
// This replaces the old 08-catalog.spec.ts (its own on-paper/detail/count smoke) with the new
// homes for the same jobs, against the same seeded manifest-only product 03-unpacking.spec.ts
// proves is expected but uncounted.
//
// Runs before the counting test below, which counts this product onto the floor — everything
// here depends on it still being on-paper (0/5), so scope/filter coverage comes first.
test('Inventory — On paper/All: totals hidden outside On floor, department filter narrows every scope, and All lists found and on-paper together', async ({ page }) => {
  await openScreen(page, 'Inventory')
  // On floor is the default scope, and its totals footer is what 17-inventory.spec.ts already
  // exercises in full; this only needs the negative half of "Totals footer only in On floor".
  await expect(page.locator('.inv-totals')).toBeVisible()

  await page.locator('.seg-opt', { hasText: 'On paper' }).click()
  await expect(page.locator('.inv-totals')).toHaveCount(0)

  // Department filter narrows the On paper scope (inventory-view spec: "Search and department
  // filter work in every scope"). The seeded on-paper product is KITCHEN; filtering to a
  // different department hides it, filtering to its own department (or "All departments") shows
  // it again.
  const row = page.locator('tr.inv-row', { hasText: seed.products.unpackingOnly.name })
  await page.locator('.inv-depts .chip', { hasText: 'WIRELESS' }).click()
  await expect(row).not.toBeVisible()
  await page.locator('.inv-depts .chip', { hasText: seed.products.unpackingOnly.category }).click()
  await expect(row).toBeVisible()
  await page.locator('.inv-depts .chip', { hasText: 'All departments' }).click()

  // All lists both found and on-paper products together (inventory-view spec: "All lists found
  // and on-paper together"), via the same catalog columns.
  await page.locator('.seg-opt', { hasText: 'All' }).click()
  await expect(page.locator('.inv-totals')).toHaveCount(0)

  await page.getByPlaceholder('Search by product name').fill(seed.products.unpackingOnly.name)
  const onPaperRow = page.locator('tr.inv-row', { hasText: seed.products.unpackingOnly.name })
  await expect(onPaperRow.locator('.cat-badge').first()).toHaveText('On paper')

  await page.getByPlaceholder('Search by product name').fill(seed.products.pricedGood.name)
  const foundRow = page.locator('tr.inv-row', { hasText: seed.products.pricedGood.name })
  await expect(foundRow.locator('.cat-badge').first()).toHaveText('Found')
})

test('Inventory — On paper: the seeded on-paper product is found by name, its gap is visible, and Item detail opens with a Count entry', async ({ page }) => {
  await openScreen(page, 'Inventory')

  // Scope control swaps both the dataset and the column set (D3) — On floor is the default. The
  // underlying radio is visually hidden (`.seg-opt input`), so a real click lands on its label,
  // same as an operator's.
  await page.locator('.seg-opt', { hasText: 'On paper' }).click()

  await page.getByPlaceholder('Search by product name').fill(seed.products.unpackingOnly.name)

  const row = page.locator('tr.inv-row', { hasText: seed.products.unpackingOnly.name })
  await expect(row).toBeVisible()

  // Catalog columns, not fabricated stock cells: department, status, priced, and the
  // counted-of-expected gap fact this scope exists to surface.
  await expect(row.locator('td').nth(1)).toHaveText(seed.products.unpackingOnly.category)
  await expect(row.locator('.cat-badge').first()).toHaveText('On paper')
  await expect(row.locator('td').last()).toHaveText(`0/${seed.products.unpackingOnly.quantityExpected}`)

  // An on-paper row opens Item detail the same way a floor row does (D6).
  await row.click()
  await expect(page.getByRole('heading', { name: seed.products.unpackingOnly.name })).toBeVisible()

  // Identity renders; stock-dependent sections show their own honest empty states rather than
  // fabricated rows, since nothing has been counted onto this product yet.
  await expect(page.getByText('No batches recorded.')).toBeVisible()
  await expect(page.getByText('No stock movements recorded.')).toBeVisible()

  // The Count entry is the point of opening an on-paper product (D6): the seed's one open
  // delivery owes this product, so it is preselected rather than left for the operator to find.
  await expect(page.getByText('No open delivery to count into.')).not.toBeVisible()
  const lotSelect = page.getByLabel('Delivery')
  await expect(lotSelect).toHaveValue(seed.lot.id)
  await expect(page.getByRole('button', { name: 'Count', exact: true })).toBeEnabled()

  // Activating it opens the existing ProductCountPane, unchanged, scoped to the preselected lot
  // (item-detail spec: "Count entry opens the count pane with a picked lot") — the seeded box
  // still owes all 5 units, since nothing before this spec ever counts this product.
  await page.getByRole('button', { name: 'Count', exact: true }).click()
  await expect(page.getByRole('heading', { name: seed.products.unpackingOnly.name })).toBeVisible()
  const boxRow = page.locator('.pcc-row', { hasText: seed.box.trackingNumber })
  await expect(boxRow).toContainText('5 to find')

  await boxRow.locator('.pcc-qty-in').fill('5')
  await page.getByRole('button', { name: 'Submit' }).click()
  await expect(page.getByText('Counted 5 units across 1 box.')).toBeVisible()
})
