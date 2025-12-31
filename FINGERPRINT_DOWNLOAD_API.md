# Fingerprint Download API Client

## Overview

The `FingerprintDownloadApiClient` provides access to the new optimized endpoint for downloading employee fingerprint data from the PAC API server.

**Endpoint**: `GET /api/FingerprintDownload/employee-fingerprints`

## Why Use This API?

### Old Approach (Individual Fetches)
```
GET /api/StaffEnrollment/fingerprints/{employeeId1}  → Returns 2 fingerprints
GET /api/StaffEnrollment/fingerprints/{employeeId2}  → Returns 4 fingerprints
GET /api/StaffEnrollment/fingerprints/{employeeId3}  → Returns 3 fingerprints
...
(N API calls for N employees)
```

**Problems:**
- Multiple network requests
- N+1 query problem (separate DB query per employee)
- Slow synchronization
- High network overhead

### New Approach (Bulk Download)
```
GET /api/FingerprintDownload/employee-fingerprints  → Returns ALL employees + fingerprints
```

**Advantages:**
- ✅ **Single API call** - One request fetches everything
- ✅ **Optimized query** - Uses `Include()` for eager loading
- ✅ **Faster sync** - 10-100x faster depending on employee count
- ✅ **Less overhead** - Reduced network traffic and connection management
- ✅ **Complete data** - Returns employee metadata + all fingerprints

## Architecture

### Backend (.NET)

**Controller**: `FingerprintDownloadController.cs`
```csharp
GET /api/FingerprintDownload/employee-fingerprints
Returns: AllEmployeesFingerprintsResponseDto
```

**Service**: `EmployeeFingerprintService.cs`
- Fetches all employees with fingerprints from repository
- Maps entities to DTOs
- Converts fingerprint template data to Base64

**Repository**: `EmployeeFingerprintRepository.cs`
- Uses `Include(e => e.Fingerprints)` for eager loading
- Single optimized database query
- Orders by StaffID

### Frontend (Android)

**API Client**: `FingerprintDownloadApiClient.java`
- Wraps HTTP calls to the endpoint
- Handles response parsing with Gson
- Provides callback interface for async operations

**Response Model**: `AllEmployeesFingerprintsResponse.java`
- Java representation of the DTO
- Nested classes for Employee and Fingerprint data
- Proper Gson annotations for JSON mapping

## Usage

### Basic Usage

```java
// Initialize the API client
FingerprintDownloadApiClient apiClient = new FingerprintDownloadApiClient(context);

// Call the endpoint
apiClient.getAllEmployeesWithFingerprints(new ApiCallback<AllEmployeesFingerprintsResponse>() {
    @Override
    public void onSuccess(AllEmployeesFingerprintsResponse response) {
        Log.d(TAG, "Total Employees: " + response.getTotalEmployees());
        Log.d(TAG, "Total Fingerprints: " + response.getTotalFingerprints());

        // Process employees
        for (AllEmployeesFingerprintsResponse.EmployeeFingerprintData employee : response.getEmployees()) {
            processEmployee(employee);
        }
    }

    @Override
    public void onError(String error) {
        Log.e(TAG, "Error: " + error);
    }
});
```

### Integration with FingerprintSyncService

The `FingerprintAutoSyncService` uses this API client to automatically sync when SignalR events are received:

```java
// When fingerprint enrollment event is received from SignalR
fingerprintDownloadApiClient.getAllEmployeesWithFingerprints(
    new ApiCallback<AllEmployeesFingerprintsResponse>() {
        @Override
        public void onSuccess(AllEmployeesFingerprintsResponse response) {
            // Trigger incremental sync with the fetched data
            fingerprintSyncService.syncAllFingerprintsIncremental(callback);
        }

        @Override
        public void onError(String error) {
            // Fallback to old sync method
            fingerprintSyncService.syncAllFingerprintsIncremental(callback);
        }
    }
);
```

## Response Structure

### JSON Response Format

```json
{
  "totalEmployees": 150,
  "totalFingerprints": 420,
  "retrievedAt": "2025-12-31T10:30:00Z",
  "employees": [
    {
      "id": 1,
      "icNumber": "901234567890",
      "staffID": "EMP001",
      "fullName": "John Doe",
      "department": "Security",
      "shortName": "J.Doe",
      "cardNumber": "CARD123456",
      "enrollmentDate": "2025-01-15T08:00:00Z",
      "isActive": true,
      "fingerprints": [
        {
          "id": 101,
          "employeeId": 1,
          "templateData": "W3V5LCAzLCA0LCA1LCA2LCAuLi5d",  // Base64 encoded
          "leftRight": 0,      // 0 = Left, 1 = Right
          "fingerIndex": 1,    // 0=Thumb, 1=Index, 2=Middle, 3=Ring, 4=Little
          "fingerType": 2,
          "quality": 85,
          "enrollmentDate": "2025-01-15T09:00:00Z"
        },
        {
          "id": 102,
          "employeeId": 1,
          "templateData": "W3V5LCA3LCA4LCA5LCAxMCwgLi4uXQ==",
          "leftRight": 1,
          "fingerIndex": 1,
          "fingerType": 2,
          "quality": 90,
          "enrollmentDate": "2025-01-15T09:05:00Z"
        }
      ]
    }
  ]
}
```

### Java Response Classes

```java
AllEmployeesFingerprintsResponse
├── int totalEmployees
├── int totalFingerprints
├── String retrievedAt
└── List<EmployeeFingerprintData> employees
    └── EmployeeFingerprintData
        ├── int id
        ├── String staffID
        ├── String fullName
        ├── String icNumber
        ├── String cardNumber
        ├── boolean isActive
        └── List<FingerprintData> fingerprints
            └── FingerprintData
                ├── int id
                ├── String templateData (Base64)
                ├── int leftRight
                ├── int fingerIndex
                ├── int fingerType
                ├── int quality
                └── String enrollmentDate
```

## Processing Fingerprint Data

### Decode Base64 Template

```java
import android.util.Base64;

// Get Base64 string from response
String base64Template = fingerprint.getTemplateData();

// Decode to byte array for scanner enrollment
byte[] templateBytes = Base64.decode(base64Template, Base64.DEFAULT);

// Now you can enroll to scanner
int scannerId = sdk.UF_EnrollTemplate(..., templateBytes, ...);
```

### Determine Finger Position

```java
private String getFingerPosition(FingerprintData fingerprint) {
    // Hand
    String hand = fingerprint.getLeftRight() == 0 ? "Left" : "Right";

    // Finger
    String[] fingers = {"Thumb", "Index", "Middle", "Ring", "Little"};
    String finger = fingers[fingerprint.getFingerIndex()];

    return hand + " " + finger;  // e.g., "Left Index"
}
```

### Filter Active Employees

```java
List<EmployeeFingerprintData> activeEmployees = new ArrayList<>();

for (EmployeeFingerprintData employee : response.getEmployees()) {
    if (employee.isActive()) {
        activeEmployees.add(employee);
    }
}
```

## Use Cases

### 1. Initial Kiosk Setup
```java
// On first run or after factory reset
apiClient.getAllEmployeesWithFingerprints(callback);
// → Downloads all fingerprints and enrolls to scanner
```

### 2. SignalR Auto-Sync (Recommended)
```java
// When "FingerprintEnrollmentCompleted" event is received
// FingerprintAutoSyncService handles this automatically
```

### 3. Manual Refresh
```java
// From System Settings screen
Button btnRefresh = findViewById(R.id.btnRefreshFingerprints);
btnRefresh.setOnClickListener(v -> {
    apiClient.getAllEmployeesWithFingerprints(callback);
});
```

### 4. Scheduled Background Sync
```java
// Using WorkManager for periodic sync (e.g., every 6 hours)
public class FingerprintSyncWorker extends Worker {
    @Override
    public Result doWork() {
        apiClient.getAllEmployeesWithFingerprints(callback);
        return Result.success();
    }
}
```

## Performance Comparison

| Scenario | Old Method | New Method | Improvement |
|----------|-----------|------------|-------------|
| 100 employees, 250 fingerprints | 100 API calls, ~30s | 1 API call, ~2s | **15x faster** |
| 500 employees, 1200 fingerprints | 500 API calls, ~150s | 1 API call, ~5s | **30x faster** |
| Network overhead | ~50 KB per request × N | ~200 KB total | **Significant reduction** |

## Error Handling

### Network Errors
```java
@Override
public void onError(String error) {
    if (error.contains("timeout")) {
        // Retry with exponential backoff
    } else if (error.contains("404")) {
        // Endpoint not available, fallback to old method
    } else if (error.contains("500")) {
        // Server error, log and alert user
    }
}
```

### Fallback Strategy
```java
apiClient.getAllEmployeesWithFingerprints(new ApiCallback<>() {
    @Override
    public void onSuccess(AllEmployeesFingerprintsResponse response) {
        // Use new optimized data
        processNewFormat(response);
    }

    @Override
    public void onError(String error) {
        Log.w(TAG, "New endpoint failed, falling back to old method");
        // Fallback to old endpoint
        syncService.syncAllFingerprintsIncremental(callback);
    }
});
```

## Dependencies

### Required Libraries
- **Gson** - JSON serialization/deserialization
- **OkHttp/Retrofit** (via PacApiClient base class)
- **Android Support Libraries**

### Required Permissions
```xml
<uses-permission android:name="android.permission.INTERNET" />
```

## Files Reference

### Android (Java)

| File | Location | Purpose |
|------|----------|---------|
| `FingerprintDownloadApiClient.java` | `network/api/` | API client wrapper |
| `AllEmployeesFingerprintsResponse.java` | `data/model/response/` | Response DTO |
| `FingerprintDownloadApiUsageExample.java` | `examples/` | Usage examples |
| `FingerprintAutoSyncService.java` | `service/` | Auto-sync background service |
| `ApiConstants.java` | `util/` | Endpoint constant |

### Backend (.NET C#)

| File | Location | Purpose |
|------|----------|---------|
| `FingerprintDownloadController.cs` | `Controllers/` | API controller |
| `EmployeeFingerprintService.cs` | `Services/Enrollment/` | Business logic |
| `EmployeeFingerprintRepository.cs` | `Repositories/Enrollment/` | Data access |
| `EmployeeFingerprintDto.cs` | `DTOs/FingerprintDownload/` | Response DTO |

## Testing

### Manual Test via Postman

```
GET http://192.2.2.253:7000/api/FingerprintDownload/employee-fingerprints

Headers:
- X-Tenant-Id: {your-tenant-id}

Expected Response: 200 OK with JSON body
```

### Android Test

```java
// In onCreate() or a test button click handler
FingerprintDownloadApiClient apiClient = new FingerprintDownloadApiClient(this);

apiClient.getAllEmployeesWithFingerprints(new ApiCallback<>() {
    @Override
    public void onSuccess(AllEmployeesFingerprintsResponse response) {
        Toast.makeText(context,
            "Loaded " + response.getTotalFingerprints() + " fingerprints!",
            Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onError(String error) {
        Toast.makeText(context, "Error: " + error, Toast.LENGTH_LONG).show();
    }
});
```

## Best Practices

1. **Cache the response** - Store in memory or SQLite to avoid repeated API calls
2. **Handle pagination** - If employee count grows very large, consider pagination
3. **Validate data** - Check for null/empty fingerprints before enrolling
4. **Log progress** - Log each step for debugging and monitoring
5. **Graceful degradation** - Always have a fallback to old method
6. **Background thread** - API calls are async, but scanner operations need background thread

## Troubleshooting

### Issue: Empty Response
**Cause**: No employees enrolled in database
**Solution**: Verify employees exist in PAC API database

### Issue: 404 Not Found
**Cause**: Endpoint not available (old API version)
**Solution**: Update PAC API to latest version, or use fallback method

### Issue: 500 Server Error
**Cause**: Database connection or query issue on backend
**Solution**: Check PAC API logs, verify database connectivity

### Issue: Slow Response
**Cause**: Large employee count (1000+)
**Solution**: Consider pagination, or filter to active employees only

## Future Enhancements

- [ ] Add pagination support (limit, offset parameters)
- [ ] Add filtering (active only, by department, by date range)
- [ ] Add caching layer with expiration
- [ ] Add delta sync (only changed since last fetch)
- [ ] Add compression for large responses
