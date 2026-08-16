import { test, expect } from '@playwright/test'
import { seed } from './seed'

// The harness's own self-check: before trusting any screen test, prove the backend answers, the
// shell renders against it, and the seed migration actually landed in the scratch database.

test('backend answers /api/instance', async ({ request }) => {
  const response = await request.get('/api/instance')
  expect(response.status()).toBe(200)
  const body = await response.json()
  expect(body).toHaveProperty('sandbox')
})

test('the dashboard shell renders the sidebar', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByText('BACHAT BAZAAR')).toBeVisible()

  // palletworks-inventory (dashboard-shell spec, MODIFIED): Inventory closes the Operations
  // group, immediately after Review.
  const operationsItems = page
    .locator('.sidebar-group', { hasText: 'Operations' })
    .locator('xpath=..')
    .locator('.sidebar-item')
  const count = await operationsItems.count()
  await expect(operationsItems.last()).toHaveText('Inventory')
  await expect(operationsItems.nth(count - 2)).toHaveText('Review')

  // palletworks-nav folded to eleven entries; palletworks-selling adds Register and the modern
  // register-gated Checkout to the Selling group — thirteen. Catalog/Receiving/Lots stay gone
  // from the MODERN sidebar (they live in the classic shell now), Settings stays the one admin
  // entry.
  // customer-capture adds Customers under Back office — fourteen.
  await expect(page.locator('.sidebar-item')).toHaveCount(14)
  await expect(page.getByRole('button', { name: 'Customers', exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Register', exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Checkout', exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Invoices', exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Catalog', exact: true })).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Receiving', exact: true })).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Lots', exact: true })).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Intake', exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Printer', exact: true })).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Receipt printer', exact: true })).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Bill settings', exact: true })).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Settings', exact: true })).toBeVisible()
  // Phone-only and hash-only screens stay unlisted on desktop. "Till" as a label exists only in
  // the classic shell; the modern Selling entry is the register-gated "Checkout".
  await expect(page.getByRole('button', { name: 'Capture', exact: true })).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Till', exact: true })).toHaveCount(0)
})

test('one click switches the whole shell between Modern and Classic, and it sticks', async ({ page }) => {
  await page.goto('/')
  await expect(page.locator('.sidebar')).toBeVisible()

  // Modern → Classic: the flat top bar appears, with the restored screens listed — Till included.
  await page.getByRole('button', { name: 'Classic UX', exact: true }).click()
  await expect(page.locator('.classic .topnav')).toBeVisible()
  await expect(page.locator('.sidebar')).toHaveCount(0)
  for (const label of ['Till', 'Lots', 'Receiving', 'Catalog']) {
    await expect(page.locator('.classic .topnav').getByRole('button', { name: label, exact: true })).toBeVisible()
  }

  // The restored trio actually renders with data, not just buttons (dual-ux-shell spec:
  // "function as they did before the palletworks fold").
  await page.locator('.classic .topnav').getByRole('button', { name: 'Lots', exact: true }).click()
  await expect(page.locator('.classic').getByRole('heading', { name: 'Lot Management' })).toBeVisible()
  await page.locator('.classic .topnav').getByRole('button', { name: 'Receiving', exact: true }).click()
  await expect(page.locator('.classic').getByRole('heading', { name: /Receive/ })).toBeVisible()
  await page.locator('.classic .topnav').getByRole('button', { name: 'Catalog', exact: true }).click()
  await expect(page.locator('.classic').getByRole('heading', { name: /Catalog/ })).toBeVisible()

  // The choice persists across a reload (dual-ux-shell spec).
  await page.reload()
  await expect(page.locator('.classic .topnav')).toBeVisible()

  // Classic → Modern: the sidebar returns.
  await page.getByRole('button', { name: 'Modern UX', exact: true }).click()
  await expect(page.locator('.sidebar')).toBeVisible()
  await expect(page.locator('.classic')).toHaveCount(0)
})

test('the seed landed: the seeded lot is visible through the API', async ({ request }) => {
  const response = await request.get('/api/lots')
  expect(response.status()).toBe(200)
  const lots = await response.json()
  const seeded = lots.find((l: { id: string }) => l.id === seed.lot.id)
  expect(seeded, `seeded lot ${seed.lot.id} not found among ${JSON.stringify(lots)}`).toBeTruthy()
  expect(seeded.supplier).toBe(seed.supplier.name)
  expect(seeded.expected).toBe(2) // the two seeded box_receipt rows
})
