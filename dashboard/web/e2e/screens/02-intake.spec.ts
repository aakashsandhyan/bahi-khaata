import { test, expect } from '@playwright/test'
import { seed } from '../seed'
import { openScreen } from '../helpers'

test('Intake: the seeded lot drives the header stats, step strip, and the Boxes flow', async ({ page }) => {
  await openScreen(page, 'Intake')

  // Selecting the seeded lot in the rail drives the header stats, step strip, and tabs.
  const railItem = page.locator('.intake-rail-item', { hasText: seed.supplier.name })
  await expect(railItem).toBeVisible()
  await railItem.click()

  // Header stats: real figures, not fabricated ones — the lot was paid ₹5,000, and its batches
  // already carry a recorded MRP, so MRP found and cost-of-MRP% are real values, not dashes.
  // Matched by the label's exact text, not a substring: "MRP found"'s own sub-note ("cumulative,
  // over counted units") would otherwise also match a loose "Counted" filter.
  const kpiByLabel = (label: string) =>
    page.locator('.intake-kpi').filter({ has: page.getByText(label, { exact: true }) })
  await expect(kpiByLabel('Amount paid').locator('.intake-kpi-value')).toHaveText('₹5,000')
  await expect(kpiByLabel('MRP found').locator('.intake-kpi-value')).not.toHaveText('—')
  await expect(kpiByLabel('Cost of MRP').locator('.intake-kpi-value')).not.toHaveText('—')
  // The seeded expected_line (E2E Unpacking Widget) is 5 expected, 0 counted — nothing else on
  // this lot goes through expected_line, so counted x of y is exactly this line's numbers. (This
  // spec runs before 08-inventory-onpaper.spec.ts, which is what later counts this product.)
  await expect(kpiByLabel('Counted').locator('.intake-kpi-value')).toHaveText('0 of 5')

  // Step strip: one seeded box is still EXPECTED and nothing is fully counted, so Counting is active.
  await expect(page.locator('.intake-step.active')).toHaveText('Counting')

  // Boxes tab (the default): both seeded boxes are listed with their real states.
  await expect(page.getByText(seed.boxReceipt.received.manifestCartonId)).toBeVisible()
  await expect(page.getByText(seed.boxReceipt.expected.manifestCartonId)).toBeVisible()
  await expect(page.getByText('RECEIVED', { exact: true })).toBeVisible()
  await expect(page.getByText('EXPECTED', { exact: true })).toBeVisible()

  // Receiving the second box transitions it — the same `receiving.*` endpoint the retired
  // Receiving screen called, now reached through the Boxes tab.
  await page.getByPlaceholder('Scan carton ID').fill(seed.boxReceipt.expected.manifestCartonId)
  await page.getByRole('button', { name: 'Receive', exact: true }).click()
  const row = page.locator('.item', { hasText: seed.boxReceipt.expected.manifestCartonId })
  await expect(row.getByText('RECEIVED', { exact: true })).toBeVisible()
})
