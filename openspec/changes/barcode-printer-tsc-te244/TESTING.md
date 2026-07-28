# TSC TE-244 Barcode Printer Integration — Testing & Verification

## Unit Tests

✅ Run: `./gradlew :backend:test --tests "*Print*Test"` or `./gradlew :backend:test`

**LabelTemplateServiceTest**
- [x] Renders label with all fields (barcode, name, category, cost, MRP, lot, expiry, date)
- [x] Renders label without expiry (omits Exp line when empty)
- [x] Generates valid Code128 barcode command in ZPL

**PrinterConnectionTesterTest**
- [x] Detects unreachable network addresses
- [x] Rejects invalid port numbers
- [x] Times out on localhost blocked ports
- [x] Rejects serial ports as not implemented
- [x] Returns non-empty error messages

**PrintExecutorServiceTest**
- [x] Polls queued jobs from DB
- [x] Marks job failed after 10 retries (max retries exhausted)
- [x] Queues job for retry on printer offline error
- [x] Batch processes up to 5 jobs per tick

## Manual Testing Checklist

### Setup
- [ ] Printer (TSC TE-244) connected via USB or network (IP:port)
- [ ] Backend running: `./gradlew :backend:bootRun`
- [ ] Frontend running: `npm run dev`

### Test 1: Printer Configuration Screen
- [ ] Navigate to Printer nav button
- [ ] Form loads with default values (address, port speed, copies default, enabled)
- [ ] Test button works: "Testing..." spinner shows
- [ ] After 3-5 sec: status badge updates (green if OK, red if UNREACHABLE/ERROR)
- [ ] Edit address field, click Test again: badge updates new status
- [ ] Save button: confirmation banner shows "✓ Printer config saved"
- [ ] Refresh page: form still shows saved values
- [ ] Disable printer, save: Print buttons should not work (optional)

### Test 2: Print from Receiving (Manifest Lots)
- [ ] Create or select a manifest lot with received boxes
- [ ] Open lot detail view
- [ ] Find a box with state=RECEIVED
- [ ] Print Label button appears (🖨 Print Label)
- [ ] Click Print Label → PrintModal opens
- [ ] Modal shows: box name, copies input (default from config)
- [ ] Click Print → "Printing..." spinner
- [ ] Wait 2-5 sec: modal shows "✓ Labels printed successfully"
- [ ] Modal auto-closes after 1.5 sec
- [ ] Check database: print_job table has entry with status=done
- [ ] On printer: label printed with barcode + product details (if printer connected)

### Test 3: Print from Receiving (Manual Lots)
- [ ] Create a manual lot (Lots screen)
- [ ] Add a product to lot
- [ ] Open lot detail in Receiving
- [ ] Box should show RECEIVED state after receiving
- [ ] Print button appears
- [ ] Repeat Test 2 flow: modal → print → success

### Test 4: Offline Fallback
- [ ] Disconnect printer (unplug USB or stop network)
- [ ] Open Receiving, click Print Label
- [ ] Modal shows "Printing..."
- [ ] Wait 5 sec: modal still shows "Printing..." (retrying)
- [ ] Reconnect printer
- [ ] Within 1-2 sec: modal shows "✓ Labels printed successfully"
- [ ] Check DB: print_job status changes from queued → done
- [ ] Repeat with printer offline for >5 sec:
  - [ ] Modal shows error: "Printer unreachable after 10 retries"
  - [ ] User can retry by clicking "Retry" button
  - [ ] Reconnect printer, retry: should succeed

### Test 5: Config Persistence
- [ ] Set printer address to test value (e.g., `192.168.1.100:9999`)
- [ ] Set copies default to 3
- [ ] Click Save
- [ ] Restart backend: `./gradlew :backend:bootRun`
- [ ] Navigate to Printer screen
- [ ] Form shows saved address and copies default
- [ ] Database query: `SELECT address, copies_default FROM printer_config`
  - [ ] Returns correct values

### Test 6: Label Content Accuracy
- [ ] Print a label and examine output on printer or in test mode
- [ ] Verify barcode scans correctly (use barcode gun or scanner app)
- [ ] Verify fields match expected values:
  - [ ] Product name matches box/batch product
  - [ ] Category matches (e.g., Kitchen, Personal Care)
  - [ ] Cost per unit = lot total cost / lot total quantity (correct)
  - [ ] MRP matches product MRP
  - [ ] Lot ID matches lot.id
  - [ ] Received date matches lot.receivedOn

### Test 7: Batch Print (Future Iteration)
- [ ] If unpacking workflow has "Print Labels" for batch:
  - [ ] Open unpacking, select a batch with multiple units
  - [ ] Click "Print Labels"
  - [ ] Modal shows copies = batch quantity (e.g., 25 labels)
  - [ ] Printer outputs 25 labels
  - [ ] All labels identical

### Test 8: Error Handling
- [ ] Invalid config (bad IP): Test button → red "UNREACHABLE" badge
- [ ] Printer offline: Print → modal shows "Printer unreachable after retries"
- [ ] No printer config: Print button should show error or use defaults
- [ ] Large batch print (100+ labels): Job queues, executor handles without timeout

## Build & Deployment Verification

✅ **Backend**: `./gradlew :backend:build -x test` 
- No compilation errors
- All print services compile
- Migrations V32, V33 clean

✅ **Frontend**: `npm run build`
- TypeScript strict mode passes
- No type errors in PrintModal, PrinterConfig
- Vite build succeeds
- dist/ bundle created

✅ **Runtime**:
- Backend starts without errors
- Frontend loads without console errors
- Printer nav button appears
- All endpoints respond (GET /api/admin/printer-config, POST /api/print-jobs, etc.)

## Sign-Off

- [ ] All unit tests pass
- [ ] Manual tests 1-6 complete (7-8 optional)
- [ ] Build verification passes
- [ ] No console errors or warnings
- [ ] Feature ready for merge

**Tested by:** ___________________  
**Date:** ___________________  
**Notes:** ___________________
