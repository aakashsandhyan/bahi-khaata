import { test, expect } from '@playwright/test'
import { seed } from '../seed'

// `#till` resolves to the checkout screen at load, no `hashchange` listener (D7). Since
// palletworks-selling the modern checkout is register-gated: with no session open it offers
// opening the register inline rather than a scan field — never a dead end (selling-screens
// spec). This spec walks that gate the way an opening cashier does, then sells.
test('Till: reached via #till, absent from the sidebar, and keying a seeded barcode adds a cart line', async ({ page }) => {
  await page.goto('/#till')

  // The gate: the register is closed, so checkout offers to open it inline.
  await expect(page.getByText('is not open — open it to start selling')).toBeVisible()
  await page.getByLabel('Operator').fill('Probe')
  await page.getByLabel('Float counted in (₹)').fill('2000')
  await page.getByRole('button', { name: /^Open Register .* and start selling$/ }).click()

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
