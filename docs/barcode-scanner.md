# Barcode Scanner Framework

Reusable frontend scanner for USB/BT keyboard wedges, manual entry, and camera (ZXing).

## Location

`FlowLedgerUI/src/lib/barcode/`

## Components

| Module | Role |
|--------|------|
| `BarcodeScannerService` | Wedge timing, duplicates, enable/disable/suspend |
| `BarcodeEventBus` | Pub/sub for `BARCODE_SCANNED` |
| `KeyboardProvider` | Hidden input + global key capture |
| `ManualProvider` | Programmatic / form submit |
| `CameraProvider` | Stub; see `MobileScannerPage` for ZXing |
| `BarcodeScannerProvider` | React context (ACTIVE / DISABLED / SUSPENDED) |
| `HiddenBarcodeInput` | Focus target for wedges |
| `ScanFeedbackOverlay` | Visual scan flash |
| `useBarcodeScanner` | Subscribe to events in any feature |

## Settings (localStorage)

- `scanTimeout` 50ms — flush buffer after idle
- `maxScanInterval` 30ms — ignore human typing gaps
- `duplicateDelay` 300ms — debounce same barcode
- `minBarcodeLength` 4
- `ignoreHumanTyping` true
- `autoFocus` true

## Subscriber pattern (POS example)

```tsx
import { usePosBarcodeScan } from '@/features/retail/usePosBarcodeScan'

usePosBarcodeScan({
  enabled: true,
  warehouseId: store.warehouseId,
  ensureDraft: async () => draftSale,
  onSaleUpdate: setSale,
  onConflict: openBatchModal,
  onError: toast.error,
})
```

Disable while modals are open:

```tsx
const scanner = useBarcodeScannerContext()
useEffect(() => {
  if (modalOpen) scanner.disable()
  else scanner.enable()
}, [modalOpen])
```

## Backend integration

| Use case | API |
|----------|-----|
| Resolve only (GRN, transfer, count) | `POST /api/v1/scan/resolve` |
| POS add-to-cart | `POST /api/v1/cart/scan` |
| Legacy POS (unchanged) | `POST /api/v1/retail/pos/scan` |

Org ID is always from JWT — never send `organizationId` in the body.

## Testing

```bash
cd FlowLedgerUI && npm test
```

Vitest covers wedge/manual paths, duplicate suppression, disable/suspend.

## Audio feedback

Success/error uses Web Audio oscillator beeps (no external asset files required).
