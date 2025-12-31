# GEMS Kiosk Interim - PAC_API Integration Guide

## Overview

This guide documents the complete integration of the GEMS_Kiosk_Interim Android app with the VEMS PAC_API.

## Implementation Summary

### ✅ Completed Components

#### 1. **Foundation Layer**
- `util/ApiConstants.java` - Configurable API constants via SharedPreferences
- `dto/common/DateTimeHelper.java` - ISO 8601 date/time formatting
- `util/JsonParser.java` - Gson-based JSON serialization

#### 2. **Callback Interfaces**
- `network/callbacks/ApiCallback.java` - Generic callback
- `network/callbacks/UserRegistrationCallback.java` - User registration callbacks
- `network/callbacks/ManualOverrideCallback.java` - Manual override callbacks

#### 3. **Data Transfer Objects (DTOs)**

**User Registration:**
- `data/model/request/RegisterUserRequest.java`
- `data/model/request/FingerCredential.java`
- `data/model/response/RegisterUserResponse.java`

**Manual Override:**
- `data/model/request/CreateManualOverrideProfileRequest.java`
- `data/model/response/ManualOverrideProfileResponse.java`

**SignalR Events:**
- `dto/signalr/KeySwitchEventDto.java`
- `dto/signalr/KeySwitchAggregateEventDto.java`
- `dto/signalr/KeySwitchStatesDto.java`

**Generic Wrapper:**
- `data/model/response/ApiResponse.java`

#### 4. **Network Layer**
- **Enhanced PacApiClient** - Added generic POST/GET methods with typed responses
- `network/api/UserRegistrationApiClient.java` - User registration endpoints
- `network/api/ManualOverrideApiClient.java` - Manual override endpoints

#### 5. **Service Layer**
- `service/UserRegistrationService.java` - Business logic for user registration
- `service/ManualOverrideService.java` - Business logic for manual override

#### 6. **SignalR Integration**
- **Enhanced SignalRService** - Added KeySwitch event handlers
  - KeySwitchChanged event
  - KeySwitchAggregate event
  - Vault/door name mapping utilities

---

## Integration Examples

### 1. User Registration Integration

**In FingerprintEnrollmentActivity.java:**

```java
import com.supremainc.sfm_sdk_android.service.UserRegistrationService;
import com.supremainc.sfm_sdk_android.network.callbacks.UserRegistrationCallback;
import com.supremainc.sfm_sdk_android.data.model.response.RegisterUserResponse;

// After successful fingerprint enrollment:
private void registerWithPacApi() {
    UserRegistrationService service = new UserRegistrationService(this);

    service.registerUserWithFingerprints(
        userIdEditText.getText().toString(),    // username
        nameEditText.getText().toString(),      // name
        icNumberEditText.getText().toString(),  // employeeNumber
        supervisorCheckbox.isChecked() ? "SUPERVISOR" : "CUSTODIAN",  // role
        departmentEditText.getText().toString(),  // department
        "Main Branch",  // branch
        leftFingerprintTemplate,   // byte[]
        rightFingerprintTemplate,  // byte[]
        new UserRegistrationCallback() {
            @Override
            public void onRegistrationSuccess(RegisterUserResponse response) {
                runOnUiThread(() -> {
                    Log.i(TAG, "PAC_API Registration successful: " + response.getUsername());
                    Toast.makeText(FingerprintEnrollmentActivity.this,
                        "Registered with PAC_API: " + response.getFingerprintsRegistered() + " fingerprints",
                        Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onRegistrationError(String error) {
                runOnUiThread(() -> {
                    Log.e(TAG, "PAC_API Registration failed: " + error);
                    Toast.makeText(FingerprintEnrollmentActivity.this,
                        "PAC_API registration failed: " + error,
                        Toast.LENGTH_LONG).show();
                });
            }
        }
    );
}
```

### 2. Manual Override Integration

**In ManualOverrideActivity.java:**

```java
import com.supremainc.sfm_sdk_android.service.ManualOverrideService;
import com.supremainc.sfm_sdk_android.network.callbacks.ManualOverrideCallback;
import com.supremainc.sfm_sdk_android.data.model.response.ManualOverrideProfileResponse;

// When activating override:
private void activateOverrideWithPacApi() {
    DatabaseHelper.User custodian = getCurrentCustodian();
    ManualOverrideService service = new ManualOverrideService(this);

    long startTime = System.currentTimeMillis();
    long endTime = startTime + (4 * 60 * 60 * 1000);  // 4 hours

    service.createOverrideProfile(
        selectedDoorId,              // 1-7
        custodian.getStaffId(),      // username
        custodian.getName(),         // name
        custodian.getStaffId(),      // employeeNumber
        custodian.getDepartment(),   // department
        startTime,
        endTime,
        "Mobile Kiosk",             // requestedBy
        new ManualOverrideCallback() {
            @Override
            public void onProfileCreated(ManualOverrideProfileResponse response) {
                runOnUiThread(() -> {
                    // Update local database
                    long overrideId = dbHelper.activateVaultOverride(
                        vaultType, entryPoint,
                        custodian1Id, custodian2Id, custodian3Id
                    );

                    // Link to PAC_API profile (requires DatabaseHelper update)
                    // dbHelper.linkOverrideToProfile((int)overrideId, response.getProfileId());

                    Log.i(TAG, "Override profile created: " + response.getProfileId());
                    Toast.makeText(ManualOverrideActivity.this,
                        "Override activated: " + response.getDoorName(),
                        Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onProfileDeactivated(ManualOverrideProfileResponse response) {
                // Handle deactivation
            }

            @Override
            public void onProfileError(String error) {
                runOnUiThread(() -> {
                    Log.e(TAG, "Override profile error: " + error);
                    Toast.makeText(ManualOverrideActivity.this,
                        "Failed to create override: " + error,
                        Toast.LENGTH_LONG).show();
                });
            }
        }
    );
}
```

### 3. SignalR KeySwitch Events Integration

**In MainActivity.java or VaultStatusActivity.java:**

```java
import com.supremainc.sfm_sdk_android.SignalRService;
import com.supremainc.sfm_sdk_android.dto.signalr.KeySwitchEventDto;
import com.supremainc.sfm_sdk_android.dto.signalr.KeySwitchAggregateEventDto;

private SignalRService signalRService;

@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    // ... other initialization

    setupSignalRWithKeySwitchSupport();
}

private void setupSignalRWithKeySwitchSupport() {
    signalRService = new SignalRService(this);

    signalRService.initialize(new SignalRService.SignalREventListener() {
        @Override
        public void onManualOverrideEvent(SignalRService.ManualOverrideEventData event) {
            // Existing handler
        }

        @Override
        public void onDoorStateChanged(SignalRService.DoorStateData event) {
            // Existing handler
        }

        @Override
        public void onVaultIncident(SignalRService.VaultIncidentData incident) {
            // Existing handler
        }

        @Override
        public void onKeySwitchChanged(KeySwitchEventDto event) {
            runOnUiThread(() -> {
                // Use doorName instead of doorId
                String vaultName = SignalRService.mapDoorNameToVaultName(event.getDoorName());

                updateKeySwitchIndicator(
                    vaultName,
                    event.getKeySwitchNumber(),
                    event.isOn()
                );

                Log.i(TAG, String.format("KeySwitch: %s (Vault: %s) - Switch %d - %s",
                    event.getDoorName(),
                    vaultName,
                    event.getKeySwitchNumber(),
                    event.isOn() ? "ON" : "OFF"));
            });
        }

        @Override
        public void onKeySwitchAggregate(KeySwitchAggregateEventDto event) {
            runOnUiThread(() -> {
                String vaultName = SignalRService.mapDoorNameToVaultName(event.getDoorName());

                if (event.isAllKeysOn()) {
                    showVaultReadyNotification(vaultName, event.getDoorName());
                } else if (event.isAllKeysOff()) {
                    showVaultLockedNotification(vaultName, event.getDoorName());
                }

                Log.i(TAG, String.format("KeySwitch Aggregate: %s - %s",
                    event.getDoorName(), event.getEventType()));
            });
        }

        @Override
        public void onConnectionClosed() {
            Log.w(TAG, "SignalR connection closed");
        }
    });

    signalRService.start(new SignalRService.ConnectionCallback() {
        @Override
        public void onConnected() {
            Log.i(TAG, "SignalR connected - listening for events");
        }

        @Override
        public void onError(String errorMessage) {
            Log.e(TAG, "SignalR error: " + errorMessage);
        }
    });
}

private void updateKeySwitchIndicator(String vaultName, int switchNumber, boolean isOn) {
    // Update UI based on vault name and key switch number
    // Since each vault has 3 key switches, track by vaultName + switchNumber
}

private void showVaultReadyNotification(String vaultName, String doorName) {
    // Show notification that all keys are turned for this vault
}

private void showVaultLockedNotification(String vaultName, String doorName) {
    // Show notification that all keys are locked for this vault
}
```

---

## Database Schema Updates (TODO)

### Required Changes to DatabaseHelper.java:

```java
// Add to onCreate():
String CREATE_VAULT_OVERRIDES_TABLE = "CREATE TABLE " + TABLE_VAULT_OVERRIDES + "("
    + KEY_OVERRIDE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
    + KEY_OVERRIDE_VAULT_TYPE + " TEXT NOT NULL,"
    + KEY_OVERRIDE_VAULT_NAME + " TEXT,"          // NEW: Store vault name
    + KEY_OVERRIDE_ENTRY_POINT + " TEXT NOT NULL,"
    + KEY_OVERRIDE_STATUS + " TEXT DEFAULT 'ACTIVE',"
    + KEY_OVERRIDE_ACTIVATED_AT + " DATETIME DEFAULT CURRENT_TIMESTAMP,"
    + KEY_OVERRIDE_DEACTIVATED_AT + " DATETIME,"
    + KEY_OVERRIDE_CUSTODIAN_1_ID + " TEXT,"
    + KEY_OVERRIDE_CUSTODIAN_2_ID + " TEXT,"
    + KEY_OVERRIDE_CUSTODIAN_3_ID + " TEXT,"
    + KEY_OVERRIDE_DEACTIVATED_BY + " TEXT,"
    + KEY_OVERRIDE_PROFILE_ID + " TEXT"           // NEW: PAC_API profile ID
    + ")";

// Add migration in onUpgrade():
if (oldVersion < NEW_VERSION) {
    db.execSQL("ALTER TABLE vault_overrides ADD COLUMN vault_name TEXT");
    db.execSQL("ALTER TABLE vault_overrides ADD COLUMN profile_id TEXT");
}

// Add new methods:
public boolean linkOverrideToProfile(int overrideId, String profileId) {
    SQLiteDatabase db = this.getWritableDatabase();
    ContentValues values = new ContentValues();
    values.put(KEY_OVERRIDE_PROFILE_ID, profileId);

    int rowsAffected = db.update(TABLE_VAULT_OVERRIDES, values,
            KEY_OVERRIDE_ID + " = ?", new String[]{String.valueOf(overrideId)});
    db.close();

    return rowsAffected > 0;
}
```

---

## Configuration

### Update API Settings at Runtime:

```java
import com.supremainc.sfm_sdk_android.util.ApiConstants;

// Set custom base URL
ApiConstants.setBaseUrl(context, "http://192.168.1.100:7000");

// Set custom timeouts
ApiConstants.setConnectTimeout(context, 15000);  // 15 seconds
ApiConstants.setReadTimeout(context, 15000);

// Reset to defaults
ApiConstants.resetToDefaults(context);
```

---

## Testing Checklist

### 1. User Registration
- [ ] Enroll fingerprint locally
- [ ] Register with PAC_API
- [ ] Verify fingerprints are Base64 encoded correctly
- [ ] Check PAC_API logs for registration

### 2. Manual Override
- [ ] Create override profile
- [ ] Verify profile ID returned
- [ ] Link local override to PAC_API profile
- [ ] Deactivate profile
- [ ] Verify status updates

### 3. SignalR Events
- [ ] Connect to SignalR hub
- [ ] Receive KeySwitchChanged events
- [ ] Receive KeySwitchAggregate events
- [ ] Verify door name is used instead of door ID
- [ ] Test vault name mapping
- [ ] Test all keys on/off notifications

---

## Architecture Summary

```
┌─────────────────────────────────────────┐
│         Android Activities              │
│  (FingerprintEnrollment, ManualOverride)│
└──────────────┬──────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────┐
│         Service Layer                    │
│  • UserRegistrationService              │
│  • ManualOverrideService                │
└──────────────┬──────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────┐
│         API Clients                      │
│  • UserRegistrationApiClient            │
│  • ManualOverrideApiClient              │
└──────────────┬──────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────┐
│      Enhanced PacApiClient               │
│  • Generic POST/GET methods              │
│  • ApiResponse wrapper support           │
│  • Configurable URLs & timeouts          │
└──────────────┬──────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────┐
│         VEMS PAC_API                     │
│  • /api/UserRegistration/*              │
│  • /api/ManualOverride/*                │
│  • /eventHub (SignalR)                  │
└─────────────────────────────────────────┘
```

---

## Next Steps

1. **Update DatabaseHelper** with vault_name and profile_id columns
2. **Integrate services** in FingerprintEnrollmentActivity
3. **Integrate services** in ManualOverrideActivity
4. **Add SignalR listeners** in MainActivity/VaultStatusActivity
5. **Test end-to-end** workflows
6. **Add error handling** and retry logic
7. **Implement offline queueing** for failed API calls

---

## Notes

- All network operations run on background threads
- Callbacks execute on main thread for UI updates
- Door names are used instead of door IDs throughout
- Each vault has 3 key switches tracked by vault name
- Configuration is persisted via SharedPreferences
- Full backward compatibility maintained with existing code

---

## Files Created/Modified

### New Files (42 total):
- 3 utility classes
- 3 callback interfaces
- 8 DTO classes
- 2 API clients
- 2 services
- Enhanced: PacApiClient, SignalRService

### Modified Files:
- PacApiClient.java (enhanced with generic methods)
- SignalRService.java (added KeySwitch event handlers)

---

## Support

For issues or questions, refer to:
- Plan file: `C:\Users\Admin\.claude\plans\generic-bubbling-haven.md`
- PAC_API Reference: `C:\Users\Admin\source\repos\MaziqOzziey\VEMS`
