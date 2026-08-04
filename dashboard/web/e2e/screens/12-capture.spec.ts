import { test, expect } from '@playwright/test'

// Capture is phone-only: App.tsx decides that from window width at mount, so this file runs at a
// phone-sized viewport, and reaches the screen the way a phone does — the #capture hash — rather
// than through the (desktop-only) sidebar. Numbered last among the screen specs so the extra
// pending capture it creates cannot make the Review screen's capture list ambiguous for an
// earlier test.
test.use({ viewport: { width: 375, height: 800 } })

test('Capture: submitting a name sends it for review', async ({ page }) => {
  await page.goto('/#capture')
  await expect(page.getByRole('heading', { name: 'Capture a product' })).toBeVisible()

  await page.getByLabel('Name').fill('E2E Playwright Capture')
  await page.getByRole('button', { name: 'Send for review' }).click()

  await expect(page.getByText(/Sent .*E2E Playwright Capture.*for review/)).toBeVisible()
})
