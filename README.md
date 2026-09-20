# PochiPay (p-pay)

Android payment automation bridge and verification gateway. PochiPay bridges central backend servers to on-device M-PESA workflows through two subsystems:

1. **Business STK Push Automation Engine:** Dispatches customer STK prompts programmatically via Android Accessibility Service interactions with the Safaricom M-PESA for Business application (`com.safaricom.mpesa.orgapp`).
2. **SMS Payment Verification Bridge:** Ingests, parses, and stores incoming M-PESA confirmation SMS messages in a local Room database, validating transaction references and amounts on demand.

Communication with central infrastructure runs over real-time Socket.io channels with automatic HTTP REST polling fallback.

---

## Architecture Overview

```
                          Central Backend
                         (Socket.io / REST)
                               │   ▲
          request_stk_push     │   │  stk_push_result
          request_verification │   │  verification_result
                               ▼   │
┌─────────────────────────────────────────────────────────────┐
│ PochiPay Android Runtime (com.pochipay)                     │
│                                                             │
│  ┌───────────────────────┐       ┌───────────────────────┐  │
│  │ PaymentMonitorService │       │  DefaultSmsReceiver   │  │
│  │ (Socket.io + REST)    │       │  (Incoming M-PESA SMS)│  │
│  └──────────┬────────────┘       └──────────┬────────────┘  │
│             │                               │               │
│             ▼                               ▼               │
│  ┌───────────────────────┐       ┌───────────────────────┐  │
│  │ BusinessStkAutomation │       │ AppDatabase (Room DB) │  │
│  │ (UI Automation)       │       │ (Receipts & Records)  │  │
│  └──────────┬────────────┘       └──────────▲────────────┘  │
│             │                               │               │
└─────────────┼───────────────────────────────┼───────────────┘
              │ Accessibility Events          │ Local Query
              ▼                               │
┌───────────────────────────┐                 │
│ M-PESA for Business App   │                 │
│ com.safaricom.mpesa.orgapp│─────────────────┘
└───────────────────────────┘  (SMS dispatched to device)
```

---

## Key Modules

| Module | Source Location | Responsibility |
|---|---|---|
| **STK Automation Engine** | [`BusinessStkAutomation.kt`](app/src/main/java/com/pochipay/automation/BusinessStkAutomation.kt) | Orchestrates the multi-step UI flow: app launch, PIN unlock, sale prompt selection, phone/amount entry, customer name extraction, and confirmation. |
| **Accessibility Service** | [`MyAccessibilityService.kt`](app/src/main/java/com/pochipay/MyAccessibilityService.kt) | Inspects node hierarchies, performs tap gestures, injects text, and dispatches UI automation commands without root access. |
| **Gateway Service** | [`PaymentMonitorService.kt`](app/src/main/java/com/pochipay/services/PaymentMonitorService.kt) | Foreground service keeping persistent Socket.io connection active and polling fallback REST endpoints. |
| **SMS Ingestion** | [`DefaultSmsReceiver.kt`](app/src/main/java/com/pochipay/receivers/DefaultSmsReceiver.kt) | Receives, parses, and persists incoming SMS messages into Room DB. |
| **On-Device STK Console** | [`AutomationFragment.kt`](app/src/main/java/com/pochipay/AutomationFragment.kt) | Manual testing interface for dispatching STK prompts and viewing live execution logs. |
| **Settings & App Picker** | [`SettingsFragment.kt`](app/src/main/java/com/pochipay/SettingsFragment.kt), [`AppPickerActivity.kt`](app/src/main/java/com/pochipay/AppPickerActivity.kt) | Configures 4-digit PIN, target business app package, and backend server URL. |

---

## Business STK Automation Flow

When an STK prompt request is received, the automation engine executes the following pipeline:

1. **Launch Target Package:** Resolves and launches the configured business app (default: `com.safaricom.mpesa.orgapp`).
2. **PIN Authentication:** Detects numeric keypad / PIN challenge and injects the 4-digit PIN configured in PochiPay settings.
3. **Trigger New Sale:** Clicks the green `"NEW SALE"` action button on the home dashboard.
4. **Select M-PESA Prompt:** Selects `"M-PESA Prompt"` from the bottom sheet modal.
5. **Entry Screen Selection:** Selects `"M-PESA PROMPT"` from the list view.
6. **Input Dispatch:** Fills the customer mobile number (`07...` or `254...`) and transaction amount.
7. **Confirmation & Extraction:** Reads and extracts the resolved customer name from the confirmation screen, then confirms by tapping `"CONTINUE"`.
8. **Result Capture & Return:** Awaits confirmation dialog (`"Prompt successfully initiated"`), taps `"DONE"`, returns PochiPay to foreground, and reports status back to backend.

---

## Server API Protocol

### 1. Business STK Push API

#### Socket.io Event: `request_stk_push`
Payload from server:
```json
{
  "requestId": "req_987654321",
  "phone": "0712345678",
  "amount": 100.0,
  "reference": "ORDER-1002"
}
```

Response event from PochiPay: `stk_push_result`
```json
{
  "requestId": "req_987654321",
  "success": true,
  "customerName": "JOHN DOE",
  "errorMessage": null
}
```

#### REST Fallback Endpoints
* **Poll Pending STK Requests:**
  ```http
  GET /api/pending-stk/
  ```
  Returns: `[{ "id": "1", "phone": "0712345678", "amount": 100.0, "reference": "ORDER-1002" }]`

* **Submit STK Result:**
  ```http
  POST /api/stk-result/
  Content-Type: application/json

  {
    "requestId": "1",
    "success": true,
    "customerName": "JOHN DOE",
    "errorMessage": null
  }
  ```

---

### 2. Payment Verification API

#### Socket.io Event: `request_verification`
Payload from server:
```json
{
  "reference": "QA12BC34DE",
  "amount": 1500.0
}
```

Response event from PochiPay: `verification_result`
```json
{
  "reference": "QA12BC34DE",
  "verified": true,
  "amount": 1500.0,
  "sender": "0712345678",
  "timestamp": 1726857600000
}
```

#### REST Fallback Endpoints
* **Poll Pending Verifications:**
  ```http
  GET /api/pending-verifications/
  ```

* **Submit Verification Result:**
  ```http
  POST /api/verify/
  Content-Type: application/json

  {
    "reference": "QA12BC34DE",
    "verified": true,
    "amount": 1500.0
  }
  ```

---

## Device Setup & Permissions

To operate as a payment gateway on a physical Android device:

1. **Install Build:**
   ```bash
   adb install -r app/release/app-release.apk
   ```

2. **Grant System Permissions:**
   * **Accessibility Service:** Android Settings > Accessibility > Installed Apps > Toggle **PochiPay** ON.
   * **Display Over Other Apps:** Allow `SYSTEM_ALERT_WINDOW` permission.
   * **SMS & Notifications:** Allow SMS reception (or set as default SMS handler in Settings).
   * **Battery Optimization:** Exclude PochiPay from battery optimization to keep background sync persistent.

3. **Configure Settings (in PochiPay):**
   * **M-PESA Business App PIN:** Set the 4-digit PIN used to unlock the business application.
   * **Target Business App:** Defaults to `com.safaricom.mpesa.orgapp`. Tap to change via installed app selector if using a different variant or dual-app profile.
   * **Central Server URL:** Configure backend URL (e.g., `https://your-server.com`).

---

## Building from Source

### Prerequisites
* JDK 17 or higher
* Android SDK Platform 36 (minSdk 24, targetSdk 36)

### Gradle Commands

```bash
# Run unit tests
./gradlew testDebugUnitTest

# Assemble Debug APK
./gradlew assembleDebug

# Assemble Release APK
./gradlew assembleRelease
```

Build outputs are generated in:
* Debug: `app/build/outputs/apk/debug/app-debug.apk`
* Release: `app/release/app-release.apk`
