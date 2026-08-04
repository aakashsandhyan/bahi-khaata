import { test, expect } from '@playwright/test'
import { seed } from '../seed'
import { openScreen } from '../helpers'

test('Reprint: scanning a seeded barcode finds the product and queues a label', async ({ page }) => {
  await openScreen(page, 'Reprint')

  const scan = page.getByPlaceholder('Scan or type a barcode…')
  await scan.fill(seed.products.pricedGood.barcode)
  await scan.press('Enter')

  await expect(page.getByText(seed.products.pricedGood.name)).toBeVisible()

  // Queuing is safe under test: printer_config.enabled = 0 makes the driver refuse before any
  // network is touched (see UsbPrinterDriver.sendLabel) — the poller just marks the job failed.
  await page.getByRole('button', { name: /Queue \d+ label/ }).click()
  await expect(page.getByText(/label.*queued/i)).toBeVisible()
})
