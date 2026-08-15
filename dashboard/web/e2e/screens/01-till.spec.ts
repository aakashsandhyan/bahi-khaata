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

  const scan = page.getByPlaceholder('Scan barcode or type SKU / product name…')
  await expect(scan).toBeVisible()
  await scan.fill(seed.products.pricedGood.barcode)
  await scan.press('Enter')

  // The seeded kettle lands as a cart line in the POS cart pane (design-artifact layout).
  await expect(page.locator('.pos-line').getByText(seed.products.pricedGood.name)).toBeVisible()
  // 49900 paise — scoped to the line's own total, since the price also appears per-unit.
  await expect(page.locator('.pos-line strong').first()).toHaveText('₹499')
  await expect(page.locator('.pos-topay')).toContainText('₹499')

  // Manual entry: a keyed name and price joins the cart beside the scanned line (no product
  // record, GST at the default rate, no stock touched — asserted API-side in CheckoutTest).
  await page.getByRole('button', { name: 'Manual entry', exact: true }).click()
  await page.getByLabel('What is being sold').fill('Loose glass jar')
  await page.getByLabel('Price (₹)').fill('100')
  await page.getByRole('button', { name: 'Add to cart', exact: true }).click()
  await expect(page.locator('.pos-line').getByText('Loose glass jar')).toBeVisible()
  await expect(page.locator('.pos-topay')).toContainText('₹599')

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

// The counter's policy: ask every sale; Walk-in declines in one tap; a keyed mobile saves and
// attaches, and the cart header names the catch (customer-capture specs).
test('Payment leads with the customer ask: attach by mobile, and the header follows', async ({ page }) => {
  await page.goto('/#till')

  // Register was opened by the first spec in this file (serial within a worker on one DB)…
  // …but be self-sufficient: open it if the gate shows.
  const gate = page.getByRole('button', { name: /^Open Register .* and start selling$/ })
  if (await gate.isVisible().catch(() => false)) {
    await page.getByLabel('Operator').fill('Probe')
    await page.getByLabel('Float counted in (₹)').fill('2000')
    await gate.click()
  }

  const scan = page.getByPlaceholder('Scan barcode or type SKU / product name…')
  await expect(scan).toBeVisible()
  await scan.fill(seed.products.pricedGood.barcode)
  await scan.press('Enter')
  await expect(page.locator('.pos-line').getByText(seed.products.pricedGood.name)).toBeVisible()

  // Take payment → the ask. Key a new customer's mobile; the name field reveals; save attaches.
  await page.getByRole('button', { name: 'Take payment', exact: true }).click()
  await page.getByPlaceholder('Mobile number').fill('98214 55120')
  await page.getByPlaceholder("Customer's name").fill('Meera Joshi')
  await page.getByRole('button', { name: 'Save and attach', exact: true }).click()

  // The header names the catch, and the method step is up.
  await expect(page.locator('.pos-cart').getByText('Meera Joshi ·')).toBeVisible()
  await expect(page.locator('.pos-methods')).toBeVisible()

  // Back out and clear — the attachment clears with the cart.
  await page.getByRole('button', { name: 'Back', exact: true }).click()
  await page.locator('.pos-cart').getByRole('button', { name: 'Clear', exact: true }).click()
  await expect(page.locator('.pos-cart').getByText('Walk-in customer ·')).toBeVisible()
})

test('Walk-in declines in one tap and the method step is up at once', async ({ page }) => {
  // Deliberately stops at the method step: actually completing would move revenue and stock
  // that 13-dashboard and 18-item-detail assert from the seed. The unreferenced-completion
  // fact is proven API-side (CheckoutTest: walk-in stays null); this spec proves the one-tap.
  await page.goto('/#till')
  const scan = page.getByPlaceholder('Scan barcode or type SKU / product name…')
  await expect(scan).toBeVisible()
  await scan.fill(seed.products.pricedGood.barcode)
  await scan.press('Enter')
  await expect(page.locator('.pos-line').getByText(seed.products.pricedGood.name)).toBeVisible()

  await page.getByRole('button', { name: 'Take payment', exact: true }).click()
  await page.getByRole('button', { name: 'Walk-in', exact: true }).click()
  await expect(page.locator('.pos-methods')).toBeVisible()

  // Leave the shared DB as found: back out and clear the cart.
  await page.getByRole('button', { name: 'Back', exact: true }).click()
  await page.locator('.pos-cart').getByRole('button', { name: 'Clear', exact: true }).click()
})
