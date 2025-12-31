# SignalR Fingerprint Auto-Sync Integration

## Overview

This document explains the integration between the PAC API and Android Kiosk app for automatic fingerprint synchronization when new employees are enrolled.

## Architecture

### Backend (PAC API - .NET)

When a new employee fingerprint is enrolled to BioStar devices:

1. **PacSyncMessageHandler.cs** (Line 773-784)
   - Receives fingerprint enrollment approval from SQS
   - Enrolls employee to BioStar devices
   - **Broadcasts SignalR event** "FingerprintEnrollmentCompleted"

2. **FingerprintEnrollmentBroadcastService.cs**
   - Generic SignalR broadcast service
   - Sends event to all connected kiosk clients via EventHub

3. **EventHub.cs**
   - SignalR Hub that manages real-time connections
   - New method: `BroadcastFingerprintEnrollmentCompleted()`

4. **FingerprintEnrollmentEventDto.cs**
   - DTO containing:
     - Employee ID, Staff ID, Full Name
     - Fingerprint count
     - Enrollment timestamp
     - Message for kiosks

### Frontend (Android Kiosk App - Java)

When SignalR event is received:

1. **SignalRService.java**
   - Listens for "FingerprintEnrollmentCompleted" event
   - Triggers callback to registered listener

2. **FingerprintAutoSyncService.java** (NEW)
   - Background Android Service
   - Maintains persistent SignalR connection
   - When event received → calls `FingerprintSyncService.syncAllFingerprintsIncremental()`

3. **FingerprintSyncService.java**
   - `syncAllFingerprintsIncremental()` method
   - Only enrolls NEW fingerprints (skips existing ones)
   - Enrolls to local fingerprint scanner hardware

## Data Flow

```
PAC API (Backend)
    ↓
[Employee Fingerprint Enrolled to BioStar]
    ↓
[PacSyncMessageHandler] → Creates FingerprintEnrollmentEventDto
    ↓
[FingerprintEnrollmentBroadcastService] → Broadcasts via SignalR
    ↓
[EventHub] → "FingerprintEnrollmentCompleted" event
    ↓
━━━━━━━━━━ SignalR Connection ━━━━━━━━━━
    ↓
Android Kiosk App
    ↓
[SignalRService] → Receives event
    ↓
[FingerprintAutoSyncService] → Triggers auto-sync
    ↓
[FingerprintSyncService.syncAllFingerprintsIncremental()]
    ↓
[Enrolls NEW fingerprints to local scanner hardware]
    ↓
✓ Kiosk scanner can now verify the new employee
```

## Integration Steps

### 1. Service Registration (DONE)

**AndroidManifest.xml** - Service is already registered:
```xml
<service
    android:name=".service.FingerprintAutoSyncService"
    android:enabled="true"
    android:exported="false" />
```

### 2. Start the Service

Add to **MainActivity.java** or **MainMenuActivity.java** in `onCreate()`:

```java
@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    // Start fingerprint auto-sync service
    Intent intent = new Intent(this, FingerprintAutoSyncService.class);
    startService(intent);

    Log.d("MainActivity", "Fingerprint Auto-Sync Service started");
}
```

### 3. Stop the Service (Optional)

Add to **MainActivity.java** or **MainMenuActivity.java** in `onDestroy()`:

```java
@Override
protected void onDestroy() {
    super.onDestroy();

    // Stop fingerprint auto-sync service
    Intent intent = new Intent(this, FingerprintAutoSyncService.class);
    stopService(intent);
}
```

**Note**: Since the service uses `START_STICKY`, it will automatically restart if killed by the system. Only explicitly stop it when the app is completely closing.

## Files Created/Modified

### Backend (.NET - C#)

| File | Status | Purpose |
|------|--------|---------|
| `FingerprintEnrollmentEventDto.cs` | ✅ Created | Event DTO with employee details |
| `IFingerprintEnrollmentBroadcast.cs` | ✅ Created | Interface for broadcast service |
| `FingerprintEnrollmentBroadcastService.cs` | ✅ Created | SignalR broadcast implementation |
| `EventHub.cs` | ✅ Modified | Added broadcast method |
| `PacSyncMessageHandler.cs` | ✅ Modified | Added SignalR broadcast call |
| `Program.cs` | ✅ Modified | Registered broadcast service in DI |

### Frontend (Android - Java)

| File | Status | Purpose |
|------|--------|---------|
| `FingerprintEnrollmentEventDto.java` | ✅ Created | Event DTO (matches backend) |
| `SignalRService.java` | ✅ Modified | Added event handler & callback |
| `FingerprintAutoSyncService.java` | ✅ Created | Background service for auto-sync |
| `ManualOverrideActivity.java` | ✅ Modified | Added no-op callback |
| `AndroidManifest.xml` | ✅ Modified | Registered new service |

## Testing

### 1. Verify SignalR Connection

Check logcat for:
```
I/FingerprintAutoSync: ✓ SignalR connected - Auto-sync service active
```

### 2. Trigger a Fingerprint Enrollment

Enroll a new employee fingerprint in the main system.

### 3. Verify Event Received

Check logcat for:
```
I/FingerprintAutoSync: ╔════════════════════════════════════════════════════════════
I/FingerprintAutoSync: ║ FINGERPRINT ENROLLMENT EVENT RECEIVED
I/FingerprintAutoSync: ╠════════════════════════════════════════════════════════════
I/FingerprintAutoSync: ║ Employee: John Doe
I/FingerprintAutoSync: ║ Staff ID: EMP001
I/FingerprintAutoSync: ║ Fingerprints: 2
I/FingerprintAutoSync: ║ Message: Fingerprint data updated. Please refresh.
I/FingerprintAutoSync: ╚════════════════════════════════════════════════════════════
```

### 4. Verify Auto-Sync Triggered

Check logcat for:
```
D/FingerprintAutoSync: Triggering incremental fingerprint sync...
D/FingerprintAutoSync: Auto-sync started: X fingerprints to check
D/FingerprintAutoSync: Auto-enrolled: John Doe (1/X)
I/FingerprintAutoSync: ╔════════════════════════════════════════════════════════════
I/FingerprintAutoSync: ║ AUTO-SYNC COMPLETED
I/FingerprintAutoSync: ╠════════════════════════════════════════════════════════════
I/FingerprintAutoSync: ║ New enrollments: 2
I/FingerprintAutoSync: ║ Failed: 0
I/FingerprintAutoSync: ╚════════════════════════════════════════════════════════════
```

### 5. Verify Scanner Enrollment

Place enrolled finger on the kiosk scanner - should successfully verify.

## Configuration

### SignalR Hub URL

Configure in **System Settings** screen or `ApiConstants.java`:

```java
public static String getSignalRHubUrl(Context context) {
    // Example: http://192.2.2.253:7000/eventHub
    return baseUrl + "/eventHub";
}
```

## Troubleshooting

### Service Not Starting

1. Check `AndroidManifest.xml` - service must be registered
2. Verify service is started in `MainActivity.onCreate()`
3. Check logcat for service lifecycle events

### SignalR Not Connecting

1. Verify SignalR Hub URL in settings
2. Check network connectivity to PAC API server
3. Review logcat for connection errors
4. Service will auto-retry every 5 seconds

### Auto-Sync Not Triggered

1. Verify SignalR is connected
2. Check that event name matches: "FingerprintEnrollmentCompleted"
3. Verify backend is broadcasting the event
4. Check logcat for event received confirmation

### Fingerprints Not Enrolling to Scanner

1. Check scanner is connected via USB
2. Verify `FingerprintSyncService` has proper SDK initialization
3. Review incremental sync logs for failures
4. Check fingerprint template data is valid

## Performance Considerations

- **Incremental Sync**: Only new fingerprints are enrolled, existing ones are skipped
- **Background Thread**: Scanner operations run on dedicated executor to avoid UI blocking
- **Auto-Reconnect**: Service automatically reconnects if SignalR connection drops
- **START_STICKY**: Service restarts if killed by Android system

## Security

- Service is **not exported** (only accessible within app)
- SignalR connection uses configured base URL from settings
- No authentication tokens stored in service (handled by ApiConstants)

## Future Enhancements

- [ ] Add notification when auto-sync completes
- [ ] Add UI indicator for auto-sync service status
- [ ] Add manual trigger button in System Settings
- [ ] Add sync statistics dashboard
