# SMS/MMS System Integration - Implementation Summary

## Overview
Your app can now receive system SMS and MMS messages and create conversations automatically. Users can set NTFY5 as their default messaging app.

## Files Modified/Created

### 1. **AndroidManifest.xml** (Modified)
- **SMS/MMS Permissions Added:**
  - `android.permission.RECEIVE_SMS` - Receive SMS messages
  - `android.permission.RECEIVE_MMS` - Receive MMS messages
  - `android.permission.READ_SMS` - Read SMS from database
  - `android.permission.SEND_SMS` - Send SMS (future feature)
  - `android.permission.READ_CONTACTS` - Access contact information
  - `android.permission.WRITE_SMS` - Write SMS to database
  - `com.android.permission.READ_SMS` - Proprietary Samsung permission

- **Broadcast Receivers Added:**
  - `SmsReceiver` - Handles incoming SMS with priority 999 (highest)
  - `MmsReceiver` - Handles incoming MMS with priority 999
  - `DefaultSmsReceiver` - Required for default SMS app functionality

### 2. **SettingsFragment.kt** (Modified)
- Added import for RoleManager, Build, and Telephony
- Created `setAsDefaultSmsApp()` function with version handling:
  - Android 10+: Uses RoleManager.ROLE_SMS for modern device management
  - Android 9 and below: Uses deprecated but functional Telephony intent
- Added `roleRequestLauncher` for handling role request results
- Added click listener for "Set as Default SMS App" preference

### 3. **preferences.xml** (Modified)
- Added new preference: `set_default_sms`
  - Title: "Set as Default SMS App"
  - Summary: "Make NTFY5 your default messaging app for receiving SMS."
  - Triggers default SMS app selection dialog

### 4. **SmsReceiver.kt** (Created)
- BroadcastReceiver for `android.provider.Telephony.SMS_RECEIVED_ACTION`
- **Functionality:**
  - Extracts SMS metadata (phone number, message body, timestamp)
  - Creates/appends to conversation using phone number as topic identifier
  - Automatically shows notification for new SMS
  - Uses Coroutines on IO dispatcher for database operations
  - Includes comprehensive Timber logging for debugging

### 5. **MmsReceiver.kt** (Created)
- BroadcastReceiver for `android.provider.Telephony.MMS_RECEIVED_ACTION`
- **Functionality:**
  - Queries MMS database (content://mms)
  - Extracts sender address from mms_addr table (type=137 for FROM)
  - Creates/appends to conversation using phone number as topic
  - Uses MMS subject as message body (or "[MMS Message]" if empty)
  - Includes comprehensive error handling and Timber logging

### 6. **DefaultSmsReceiver.kt** (Created)
- BroadcastReceiver for `android.provider.Telephony.ACTION_CHANGE_DEFAULT`
- Minimal implementation (system handles role management)
- Required for app to appear in "Default Apps" settings

## How It Works

### SMS Reception Flow:
1. System sends SMS → SmsReceiver.onReceive()
2. Extract sender phone number, message body, timestamp
3. Database operation: `repository.createConversation(topic=phoneNumber, message=body)`
4. Repository checks for existing topic, appends if found, creates if new
5. `NotificationHelper.showNotification()` displays notification
6. User sees conversation in conversation list with avatar and unread indicator

### MMS Reception Flow:
1. System sends MMS → MmsReceiver.onReceive()
2. Query mms database for latest message
3. Query mms_addr table to get sender address (type=137)
4. Create/append conversation using phone number as topic
5. Show notification
6. User sees in conversation list

### Setting as Default SMS App:
1. User navigates to Settings
2. Taps "Set as Default SMS App"
3. Android shows default app selection dialog
4. User confirms NTFY5 as default
5. System routes all SMS/MMS to app's receivers
6. App creates conversations and notifications automatically

## Integration with Existing Features

### Database Integration:
- Phone numbers become "topics" in Conversation entity
- Each SMS/MMS creates a Message in the conversation
- Deduplication works automatically (same phone number = same conversation)

### Notification Integration:
- Uses existing `NotificationHelper.showNotification()`
- Avatars generated from phone number (deterministic colors)
- Full message expansion works for SMS/MMS
- "Mark as Read" action available

### UI Integration:
- Conversations appear in ConversationListFragment
- Avatar generated from phone number first character
- Unread indicator works (bold preview)
- Message list shows full SMS/MMS threads

## Key Technical Details

### Permissions & Priorities:
- SMS receiver has priority="999" (highest) to intercept before system
- Receivers are exported=true to receive system broadcasts
- Permissions properly declared in manifest

### Database Queries:
- Phone numbers are used as conversation topics (e.g., "+1234567890")
- Timestamps converted to milliseconds for consistency
- Messages stored with full SMS/MMS text

### Error Handling:
- Coroutine-based async operations prevent ANR
- Try-catch blocks around database operations
- Comprehensive Timber logging for debugging

### Version Compatibility:
- RoleManager used for Android 10+ (recommended approach)
- Fallback to Telephony intent for Android 9 and below
- MMS handling works across Android versions with content provider queries

## User Experience

1. **First SMS Received:**
   - System delivers SMS to app (if set as default)
   - Notification appears immediately
   - Conversation created with phone number

2. **Viewing Conversation:**
   - User sees phone number as topic
   - Avatar with first character of number
   - Full message history available
   - Can mark as read

3. **Replying (Future Feature):**
   - User can compose reply in conversation
   - Could integrate with `focusInputField()` in accessibility service
   - Send via `SmsManager` (not yet implemented)

## Notes for Future Enhancement

- Reply functionality: Could use `android.telephony.SmsManager` to send responses
- Contact name mapping: Could query ContactsProvider to show contact names instead of phone numbers
- MMS attachment handling: Currently only extracts subject; attachments would need separate handling
- Message status tracking: Could track delivery/read status via SmsManager
- SMS backup/export: Could implement SMS export feature using existing database

