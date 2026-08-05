import { test, expect } from '@playwright/test'
import { seed } from '../seed'

// Till is unlisted (design decision D8 of palletworks-nav): no sidebar entry, reached only by the
// `#till` hash, resolved once at load with no `hashchange` listener (D7). This spec reaches it the
// way the JavaFX terminal's web back-door does — a direct hash load, never a sidebar click.
test('Till: reached via #till, absent from the sidebar, and keying a seeded barcode adds a cart line', async ({ page }) => {
  await page.goto('/#till')

  const scan = page.getByPlaceholder('Scan barcode or product code')
  await expect(scan).toBeVisible()
  await scan.fill(seed.products.pricedGood.barcode)
  await scan.press('Enter')

  await expect(page.getByText(seed.products.pricedGood.name)).toBeVisible()
  // 49900 paise — scoped to the line total, since the price also appears struck/unstruck
  // elsewhere on the line and in the subtotal row.
  await expect(page.locator('.till-line-total')).toHaveText('₹499')
  await expect(page.getByText('Subtotal')).toBeVisible()

  // No sidebar entry names it — Till is a deliberate back-door, not a daily destination.
  await expect(page.getByRole('button', { name: 'Till', exact: true })).toHaveCount(0)
})

// D7's landingView() reads the hash the same param-less way for every hash it knows, on any
// viewport — not only inside the phone branch `#capture` used to be scoped to. This is the
// desktop half of that generalization; 12-capture.spec.ts covers the phone half (and is the one
// that actually submits a capture, so this deliberately does not, to avoid a second pending
// capture skewing the Review screen's count for another spec).
test('Capture hash also resolves on desktop, via the same landing mechanism as #till', async ({ page }) => {
  await page.goto('/#capture')
  await expect(page.getByRole('heading', { name: 'Capture a product' })).toBeVisible()
})
