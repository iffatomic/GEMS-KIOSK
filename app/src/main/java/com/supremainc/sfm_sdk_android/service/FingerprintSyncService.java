/*
 * Copyright (c) 2001 - 2025. Suprema Inc. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for details.
 */

package com.supremainc.sfm_sdk_android.service;

import android.content.Context;
import android.util.Log;

import com.supremainc.sfm_sdk_android.DatabaseHelper;
import com.supremainc.sfm_sdk_android.data.model.response.AllFingerprintsResponse;
import com.supremainc.sfm_sdk_android.data.model.response.ApiResponse;
import com.supremainc.sfm_sdk_android.network.api.StaffEnrollmentApiClient;
import com.supremainc.sfm_sdk_android.network.callbacks.ApiCallback;
import com.supremainc.sfm_sdk_android.utils.FingerprintUtils;
import com.supremainc.sfm_sdk.SFM_SDK_ANDROID;
import com.supremainc.sfm_sdk.enumeration.UF_ENROLL_OPTION;
import com.supremainc.sfm_sdk.enumeration.UF_RET_CODE;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Service for syncing fingerprints from API to local scanner
 *
 * KEY CONCEPTS:
 * 1. API stores fingerprints with ULID string IDs (e.g., "01HQXYZ...")
 * 2. We download these and assign auto-increment integer scanner IDs (starting at 10000)
 * 3. We enroll to scanner using the integer scanner ID
 * 4. When scanner identifies, it returns the integer scanner ID
 * 5. We look up the scanner ID to get user info
 *
 * This enables cross-device fingerprint validation!
 */
public class FingerprintSyncService {

    private static final String TAG = "FingerprintSyncService";

    private final Context context;
    private final DatabaseHelper dbHelper;
    private final SFM_SDK_ANDROID sdk;
    private final StaffEnrollmentApiClient apiClient;
    private final ExecutorService scannerExecutor;  // For scanner operations (must run on background thread)

    /**
     * Sync callback interface
     */
    public interface SyncCallback {
        void onSyncStarted(int totalFingerprints);
        void onFingerprintEnrolled(int current, int total, String employeeName);
        void onSyncCompleted(int successCount, int failCount);
        void onSyncError(String error);
    }

    public FingerprintSyncService(Context context, SFM_SDK_ANDROID sdk) {
        this.context = context;
        this.dbHelper = new DatabaseHelper(context);
        this.sdk = sdk;
        this.apiClient = new StaffEnrollmentApiClient(context);
        this.scannerExecutor = Executors.newSingleThreadExecutor();  // Single thread for scanner operations
    }

    /**
     * Sync all fingerprints from API using TRUNCATE approach
     *
     * TRUNCATE APPROACH (Clean Re-sync):
     * 1. CLEAR all synced fingerprints from database
     * 2. CLEAR all synced scanner IDs from hardware (10000-99999)
     * 3. FETCH fresh fingerprints from API
     * 4. INSERT fresh to database (assigns new scanner IDs)
     * 5. ENROLL fresh to scanner hardware
     *
     * Benefits:
     * - No duplicates possible
     * - Database and scanner always in sync
     * - Fixes any corruption automatically
     * - Idempotent (can re-run safely)
     *
     * @param callback Progress callback
     */
    public void syncFingerprintsFromAPI(SyncCallback callback) {
        Log.d(TAG, "╔════════════════════════════════════════════════════════════");
        Log.d(TAG, "║         START FINGERPRINT SYNC (TRUNCATE MODE)             ");
        Log.d(TAG, "╚════════════════════════════════════════════════════════════");

        // Execute cleanup and sync on scanner executor (background thread)
        scannerExecutor.execute(() -> {
            try {
                // STEP 1: Clear database
                Log.d(TAG, "");
                Log.d(TAG, "╔════════════════════════════════════════════════════════════");
                Log.d(TAG, "║ STEP 1: CLEARING DATABASE");
                Log.d(TAG, "╚════════════════════════════════════════════════════════════");
                int clearedFromDb = dbHelper.clearAllSyncedFingerprints();
                Log.i(TAG, "✓ Cleared " + clearedFromDb + " fingerprint(s) from database");

                // STEP 2: Clear scanner memory (synced IDs: 10000-99999)
                Log.d(TAG, "");
                Log.d(TAG, "╔════════════════════════════════════════════════════════════");
                Log.d(TAG, "║ STEP 2: CLEARING SCANNER MEMORY");
                Log.d(TAG, "╚════════════════════════════════════════════════════════════");
                int clearedFromScanner = clearScannerMemoryForSyncedIDs();
                Log.i(TAG, "✓ Cleared " + clearedFromScanner + " fingerprint(s) from scanner");

                // STEP 3: Fetch fresh from API
                Log.d(TAG, "");
                Log.d(TAG, "╔════════════════════════════════════════════════════════════");
                Log.d(TAG, "║ STEP 3: FETCHING FROM API");
                Log.d(TAG, "╚════════════════════════════════════════════════════════════");

                apiClient.getAllFingerprints(new ApiCallback<ApiResponse<AllFingerprintsResponse>>() {
                    @Override
                    public void onSuccess(ApiResponse<AllFingerprintsResponse> response) {
                        if (response.isFlag() && response.hasData()) {
                            AllFingerprintsResponse data = response.getData();

                            Log.i(TAG, "✓ API call successful");
                            Log.i(TAG, "  - Total fingerprints: " + data.getTotalFingerprints());
                            Log.i(TAG, "  - Total users: " + (data.getUsers() != null ? data.getUsers().size() : 0));

                            // STEP 4 & 5: Process fingerprints (insert to DB, enroll to scanner)
                            processAllFingerprints(data, callback);

                        } else {
                            String error = "API returned error: " + response.getMessage();
                            Log.e(TAG, "✗ " + error);
                            if (callback != null) {
                                callback.onSyncError(error);
                            }
                        }
                    }

                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "✗ API call failed: " + error);
                        if (callback != null) {
                            callback.onSyncError("Network error: " + error);
                        }
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "✗ Error during sync preparation", e);
                if (callback != null) {
                    callback.onSyncError("Sync preparation failed: " + e.getMessage());
                }
            }
        });
    }

    /**
     * Sync all fingerprints from API using INCREMENTAL approach
     *
     * INCREMENTAL APPROACH (Smart Sync):
     * 1. FETCH all fingerprints from API
     * 2. For each fingerprint:
     *    - Check if already exists in database by API ID
     *    - Check if already enrolled to scanner
     *    - SKIP if already synced and enrolled
     *    - ENROLL only if new or not yet enrolled
     *
     * Benefits:
     * - Much faster when most fingerprints already exist
     * - Only enrolls new/changed fingerprints
     * - Doesn't disrupt existing enrollments
     *
     * @param callback Progress callback
     */
    public void syncAllFingerprintsIncremental(SyncCallback callback) {
        Log.d(TAG, "╔════════════════════════════════════════════════════════════");
        Log.d(TAG, "║     START FINGERPRINT SYNC (INCREMENTAL MODE)              ");
        Log.d(TAG, "╚════════════════════════════════════════════════════════════");

        // Fetch from API on scanner executor (background thread)
        scannerExecutor.execute(() -> {
            Log.d(TAG, "");
            Log.d(TAG, "╔════════════════════════════════════════════════════════════");
            Log.d(TAG, "║ FETCHING ALL FINGERPRINTS FROM API");
            Log.d(TAG, "╚════════════════════════════════════════════════════════════");

            apiClient.getAllFingerprints(new ApiCallback<ApiResponse<AllFingerprintsResponse>>() {
                @Override
                public void onSuccess(ApiResponse<AllFingerprintsResponse> response) {
                    if (response.isFlag() && response.hasData()) {
                        AllFingerprintsResponse data = response.getData();

                        Log.i(TAG, "✓ API call successful");
                        Log.i(TAG, "  - Total fingerprints: " + data.getTotalFingerprints());
                        Log.i(TAG, "  - Total users: " + (data.getUsers() != null ? data.getUsers().size() : 0));

                        // Process fingerprints incrementally (skip existing ones)
                        processAllFingerprintsIncremental(data, callback);

                    } else {
                        String error = "API returned error: " + response.getMessage();
                        Log.e(TAG, "✗ " + error);
                        if (callback != null) {
                            callback.onSyncError(error);
                        }
                    }
                }

                @Override
                public void onError(String error) {
                    Log.e(TAG, "✗ API call failed: " + error);
                    if (callback != null) {
                        callback.onSyncError("Network error: " + error);
                    }
                }
            });
        });
    }

    /**
     * Process all fingerprints incrementally - skips already enrolled fingerprints
     */
    private void processAllFingerprintsIncremental(AllFingerprintsResponse data, SyncCallback callback) {
        if (data.getUsers() == null || data.getUsers().isEmpty()) {
            Log.w(TAG, "No users with fingerprints found");
            if (callback != null) {
                callback.onSyncCompleted(0, 0);
            }
            return;
        }

        // Count total fingerprints
        int totalFingerprints = 0;
        for (AllFingerprintsResponse.UserFingerprintData user : data.getUsers()) {
            if (user.getFingerprints() != null) {
                totalFingerprints += user.getFingerprints().size();
            }
        }

        Log.d(TAG, "Processing " + totalFingerprints + " fingerprints from " + data.getUsers().size() + " users");

        if (callback != null) {
            callback.onSyncStarted(totalFingerprints);
        }

        int successCount = 0;
        int skippedCount = 0;
        int failCount = 0;
        int current = 0;

        // Process each user's fingerprints
        for (AllFingerprintsResponse.UserFingerprintData user : data.getUsers()) {
            if (user.getFingerprints() == null || user.getFingerprints().isEmpty()) {
                continue;
            }

            // OPTIMIZATION: Check once per user if they're enrolled locally
            // If user exists in local users table with fingerprints, skip ALL their fingerprints from API sync
            // This prevents duplicate enrollment (same user in both local and synced tables)
            boolean isEnrolledLocally = dbHelper.isUserEnrolledLocally(user.getEmployeeNumber());
            if (isEnrolledLocally) {
                Log.d(TAG, "  ⊘ Skipped user (already enrolled locally): " + user.getName() + " (" + user.getEmployeeNumber() + ") - " + user.getFingerprints().size() + " fingerprint(s)");
                skippedCount += user.getFingerprints().size();
                current += user.getFingerprints().size();
                continue; // Skip all fingerprints for this user
            }

            for (AllFingerprintsResponse.FingerCredentialWithId fp : user.getFingerprints()) {
                current++;

                try {
                    // Check if fingerprint already exists in synced_fingerprints table
                    DatabaseHelper.SyncedFingerprint existing = dbHelper.getSyncedFingerprintByApiId(fp.getId());

                    if (existing != null && existing.isEnrolledToScanner()) {
                        // Already enrolled to synced range, skip
                        Log.d(TAG, "  ⊘ Skipped (already synced): " + user.getName() + " - Scanner ID: " + existing.getScannerId());
                        skippedCount++;
                        continue;
                    }

                    // Insert or update in database (gets scanner ID)
                    int scannerId = dbHelper.insertOrUpdateSyncedFingerprint(
                            fp.getId(),
                            user.getEmployeeNumber(),
                            user.getUsername(),
                            user.getName(),
                            user.getRole(),
                            fp.getFingerPrintBase64(),
                            fp.getLeftRight(),
                            fp.getFingerIndex(),
                            fp.getFingerType()
                    );

                    if (scannerId < 0) {
                        Log.e(TAG, "✗ Failed to store in database: " + user.getName());
                        failCount++;
                        continue;
                    }

                    // Convert template string to byte array
                    byte[] templateBytes = FingerprintUtils.convertStringToByteArray(fp.getFingerPrintBase64());

                    if (templateBytes == null || templateBytes.length == 0) {
                        Log.e(TAG, "✗ Invalid template for: " + user.getName());
                        failCount++;
                        continue;
                    }

                    // Enroll to scanner
                    boolean enrolled = enrollToScanner(scannerId, templateBytes, user.getName());

                    if (enrolled) {
                        // Mark as enrolled in database
                        dbHelper.markSyncedFingerprintAsEnrolled(scannerId);
                        successCount++;

                        if (callback != null) {
                            callback.onFingerprintEnrolled(current, totalFingerprints, user.getName());
                        }

                        Log.i(TAG, "✓ Enrolled: " + user.getName() + " (Scanner ID: " + scannerId + ")");
                    } else {
                        failCount++;
                        Log.e(TAG, "✗ Failed to enroll: " + user.getName());
                    }

                } catch (Exception e) {
                    Log.e(TAG, "✗ Error processing: " + user.getName(), e);
                    failCount++;
                }
            }
        }

        // Fix provisional templates
        sdk.UF_FixProvisionalTemplate();

        Log.d(TAG, "╔════════════════════════════════════════════════════════════");
        Log.d(TAG, "║              INCREMENTAL SYNC COMPLETE                     ");
        Log.d(TAG, "╠════════════════════════════════════════════════════════════");
        Log.d(TAG, "║ Newly enrolled: " + successCount);
        Log.d(TAG, "║ Skipped (already synced): " + skippedCount);
        Log.d(TAG, "║ Failed: " + failCount);
        Log.d(TAG, "╚════════════════════════════════════════════════════════════");

        if (callback != null) {
            callback.onSyncCompleted(successCount, failCount);
        }
    }

    /**
     * Clear scanner memory for synced fingerprint IDs (10000-99999)
     * This removes all synced fingerprints from scanner hardware
     * Local user fingerprints (ID < 10000) are NOT affected
     *
     * @return Number of fingerprints cleared from scanner
     */
    private int clearScannerMemoryForSyncedIDs() {
        int clearedCount = 0;
        int totalAttempts = 0;

        Log.d(TAG, "Clearing scanner IDs in range: 10000-99999");

        // Cancel any pending operations
        sdk.UF_Cancel(false);

        // Get total templates in scanner
        int[] numOfTemplate = new int[1];
        UF_RET_CODE ret = sdk.UF_GetNumOfTemplate(numOfTemplate);

        if (ret != UF_RET_CODE.UF_RET_SUCCESS) {
            Log.e(TAG, "✗ Failed to get template count: " + ret);
            return 0;
        }

        Log.d(TAG, "  → Total templates in scanner before clear: " + numOfTemplate[0]);

        // Delete scanner IDs in synced range (10000-99999)
        // We must try ALL IDs in range, not exit early
        // Scanner will return NOT_FOUND for non-existent IDs (this is OK)
        for (int scannerId = 10000; scannerId < 10100; scannerId++) {
            ret = sdk.UF_Delete(scannerId);
            totalAttempts++;

            if (ret == UF_RET_CODE.UF_RET_SUCCESS) {
                clearedCount++;
                Log.d(TAG, "  → Deleted scanner ID: " + scannerId + " (" + clearedCount + " cleared so far)");
            } else if (ret == UF_RET_CODE.UF_ERR_NOT_FOUND) {
                // This is fine - ID didn't exist
                // Log every 20 attempts to show progress
                if (totalAttempts % 20 == 0) {
                    Log.d(TAG, "  → Checked " + totalAttempts + " IDs, cleared " + clearedCount + " so far...");
                }
            } else {
                Log.e(TAG, "  ✗ Error deleting scanner ID " + scannerId + ": " + ret);
            }
        }

        // Verify final count
        ret = sdk.UF_GetNumOfTemplate(numOfTemplate);
        if (ret == UF_RET_CODE.UF_RET_SUCCESS) {
            Log.d(TAG, "  → Total templates in scanner after clear: " + numOfTemplate[0]);
        }

        Log.i(TAG, "Cleared " + clearedCount + " scanner IDs (checked " + totalAttempts + " total)");

        return clearedCount;
    }

    /**
     * Process all fingerprints from API response
     */
    private void processAllFingerprints(AllFingerprintsResponse data, SyncCallback callback) {
        if (data.getUsers() == null || data.getUsers().isEmpty()) {
            Log.w(TAG, "No users with fingerprints found");
            if (callback != null) {
                callback.onSyncCompleted(0, 0);
            }
            return;
        }

        // Count total fingerprints
        int totalFingerprints = 0;
        for (AllFingerprintsResponse.UserFingerprintData user : data.getUsers()) {
            if (user.getFingerprints() != null) {
                totalFingerprints += user.getFingerprints().size();
            }
        }

        Log.d(TAG, "Processing " + totalFingerprints + " fingerprints from " + data.getUsers().size() + " users");

        if (callback != null) {
            callback.onSyncStarted(totalFingerprints);
        }

        int successCount = 0;
        int failCount = 0;
        int current = 0;

        // Process each user's fingerprints
        for (AllFingerprintsResponse.UserFingerprintData user : data.getUsers()) {
            if (user.getFingerprints() == null || user.getFingerprints().isEmpty()) {
                continue;
            }

            Log.d(TAG, "Processing user: " + user.getName() + " (" + user.getEmployeeNumber() + ")");

            for (AllFingerprintsResponse.FingerCredentialWithId fp : user.getFingerprints()) {
                current++;

                try {
                    // Step 1: Store in database and get assigned scanner ID
                    int scannerId = dbHelper.insertOrUpdateSyncedFingerprint(
                            fp.getId(),                  // ULID string
                            user.getEmployeeNumber(),
                            user.getUsername(),
                            user.getName(),
                            user.getRole(),              // User role (ADMIN or CUSTODIAN)
                            fp.getFingerPrintBase64(),   // Already in Arrays.toString() format
                            fp.getLeftRight(),
                            fp.getFingerIndex(),
                            fp.getFingerType()
                    );

                    if (scannerId < 0) {
                        Log.e(TAG, "✗ Failed to store in database: " + user.getName());
                        failCount++;
                        continue;
                    }

                    // OPTIMIZATION: Check if already enrolled to scanner
                    if (dbHelper.isSyncedFingerprintEnrolled(scannerId)) {
                        Log.d(TAG, "⊙ Already enrolled: " + user.getName() + " (Scanner ID: " + scannerId + ") - SKIPPING");
                        successCount++;

                        if (callback != null) {
                            callback.onFingerprintEnrolled(current, totalFingerprints, user.getName());
                        }
                        continue;
                    }

                    // Step 2: Convert template string to byte array
                    byte[] templateBytes = FingerprintUtils.convertStringToByteArray(fp.getFingerPrintBase64());

                    if (templateBytes == null || templateBytes.length == 0) {
                        Log.e(TAG, "✗ Invalid template for: " + user.getName());
                        failCount++;
                        continue;
                    }

                    // Step 3: Enroll to scanner using assigned scanner ID
                    boolean enrolled = enrollToScanner(scannerId, templateBytes, user.getName());

                    if (enrolled) {
                        // Mark as enrolled in database
                        dbHelper.markSyncedFingerprintAsEnrolled(scannerId);
                        successCount++;

                        if (callback != null) {
                            callback.onFingerprintEnrolled(current, totalFingerprints, user.getName());
                        }

                        Log.i(TAG, "✓ Enrolled: " + user.getName() + " (Scanner ID: " + scannerId + ", API ID: " + fp.getId() + ")");
                    } else {
                        failCount++;
                        Log.e(TAG, "✗ Failed to enroll: " + user.getName());
                    }

                } catch (Exception e) {
                    Log.e(TAG, "✗ Error processing: " + user.getName(), e);
                    failCount++;
                }
            }
        }

        // Step 4: Fix provisional templates (like reference app)
        sdk.UF_FixProvisionalTemplate();

        Log.d(TAG, "╔════════════════════════════════════════════════════════════");
        Log.d(TAG, "║              SYNC COMPLETE                                 ");
        Log.d(TAG, "╠════════════════════════════════════════════════════════════");
        Log.d(TAG, "║ Success: " + successCount);
        Log.d(TAG, "║ Failed: " + failCount);
        Log.d(TAG, "╚════════════════════════════════════════════════════════════");

        if (callback != null) {
            callback.onSyncCompleted(successCount, failCount);
        }
    }

    /**
     * Enroll a fingerprint template to the scanner
     * Based on reference app: SettingActivity.java line 326-370
     *
     * @param scannerId Integer scanner ID (auto-assigned by database)
     * @param template Fingerprint template bytes
     * @param name User name (for logging)
     * @return true if successful
     */
    private boolean enrollToScanner(int scannerId, byte[] template, String name) {
        Log.d(TAG, "Enrolling to scanner: " + name + " (Scanner ID: " + scannerId + ")");

        try {
            int[] enrollID = new int[1];
            int[] templateSize = new int[1];

            enrollID[0] = 0;
            templateSize[0] = template.length;

            // Cancel any previous operations to ensure scanner is ready
            sdk.UF_Cancel(false);
            Log.d(TAG, "  → UF_Cancel called before enrolling ID: " + scannerId);

            // CRITICAL: Use assigned scanner ID
            UF_RET_CODE ret = sdk.UF_EnrollTemplate(
                    scannerId,                        // Use assigned scanner ID
                    UF_ENROLL_OPTION.UF_ENROLL_NONE,  // Don't auto-generate ID
                    templateSize[0],
                    template,
                    enrollID
            );

            if (ret == UF_RET_CODE.UF_RET_SUCCESS) {
                Log.d(TAG, "  ✓ Enroll SUCCESS, scanner enrollID: " + enrollID[0]);
                return true;
            } else if (ret == UF_RET_CODE.UF_ERR_TIME_OUT) {
                Log.e(TAG, "  ✗ Enroll TIMEOUT");
                return false;
            } else {
                Log.e(TAG, "  ✗ Enroll FAILED: " + ret);
                return false;
            }

        } catch (Exception e) {
            Log.e(TAG, "  ✗ Exception during enroll", e);
            return false;
        }
    }

    /**
     * Get sync statistics
     * @return Array: [total, enrolled, pending]
     */
    public int[] getSyncStats() {
        int total = dbHelper.getSyncedFingerprintCount();
        int enrolled = dbHelper.getEnrolledSyncedFingerprintCount();
        int pending = total - enrolled;

        return new int[]{total, enrolled, pending};
    }

    /**
     * Clear all synced fingerprints (for complete re-sync)
     * Clears BOTH database AND scanner memory
     * @return Number of fingerprints cleared from database
     */
    public int clearSyncedFingerprints() {
        Log.w(TAG, "Clearing all synced fingerprints from database AND scanner");

        // Clear database
        int dbCount = dbHelper.clearAllSyncedFingerprints();

        // Clear scanner memory
        int scannerCount = clearScannerMemoryForSyncedIDs();

        Log.i(TAG, "✓ Cleared " + dbCount + " from database, " + scannerCount + " from scanner");

        return dbCount;
    }

    /**
     * Enroll pending fingerprints to scanner
     * This can be called if some fingerprints failed to enroll during sync
     * @param callback Progress callback
     */
    public void enrollPendingFingerprints(SyncCallback callback) {
        Log.d(TAG, "Enrolling pending fingerprints");

        List<DatabaseHelper.SyncedFingerprint> pending = dbHelper.getAllUnenrolledFingerprints();

        if (pending.isEmpty()) {
            Log.d(TAG, "No pending fingerprints to enroll");
            if (callback != null) {
                callback.onSyncCompleted(0, 0);
            }
            return;
        }

        Log.d(TAG, "Found " + pending.size() + " pending fingerprints");

        if (callback != null) {
            callback.onSyncStarted(pending.size());
        }

        int successCount = 0;
        int failCount = 0;

        for (int i = 0; i < pending.size(); i++) {
            DatabaseHelper.SyncedFingerprint fp = pending.get(i);

            try {
                byte[] templateBytes = FingerprintUtils.convertStringToByteArray(fp.getTemplateData());

                if (templateBytes == null || templateBytes.length == 0) {
                    Log.e(TAG, "Invalid template data for: " + fp.getName());
                    failCount++;
                    continue;
                }

                boolean enrolled = enrollToScanner(fp.getScannerId(), templateBytes, fp.getName());

                if (enrolled) {
                    dbHelper.markSyncedFingerprintAsEnrolled(fp.getScannerId());
                    successCount++;

                    if (callback != null) {
                        callback.onFingerprintEnrolled(i + 1, pending.size(), fp.getName());
                    }
                } else {
                    failCount++;
                }

            } catch (Exception e) {
                Log.e(TAG, "Error enrolling pending fingerprint: " + fp.getName(), e);
                failCount++;
            }
        }

        sdk.UF_FixProvisionalTemplate();

        Log.d(TAG, "Pending enrollment complete: Success " + successCount + ", Failed " + failCount);

        if (callback != null) {
            callback.onSyncCompleted(successCount, failCount);
        }
    }

    /**
     * Sync fingerprints for a single user (incremental sync)
     * Call this immediately after enrolling a user to make them available for verification
     *
     * @param employeeNumber Employee number of the newly enrolled user
     * @param callback Progress callback (optional)
     */
    public void syncSingleUser(String employeeNumber, SyncCallback callback) {
        Log.d(TAG, "╔════════════════════════════════════════════════════════════");
        Log.d(TAG, "║         INCREMENTAL SYNC - SINGLE USER                     ");
        Log.d(TAG, "╠════════════════════════════════════════════════════════════");
        Log.d(TAG, "║ Employee Number: " + employeeNumber);
        Log.d(TAG, "╚════════════════════════════════════════════════════════════");

        apiClient.getFingerprintsByEmployeeNumber(employeeNumber,
                new ApiCallback<ApiResponse<com.supremainc.sfm_sdk_android.data.model.response.FingerprintCredentialsResponse>>() {
            @Override
            public void onSuccess(ApiResponse<com.supremainc.sfm_sdk_android.data.model.response.FingerprintCredentialsResponse> response) {
                if (response.isFlag() && response.hasData()) {
                    com.supremainc.sfm_sdk_android.data.model.response.FingerprintCredentialsResponse data = response.getData();

                    Log.i(TAG, "✓ API call successful");
                    Log.i(TAG, "  - User: " + data.getName());
                    Log.i(TAG, "  - Total fingerprints: " + data.getTotalFingerprints());
                    Log.i(TAG, "  - Current thread: " + Thread.currentThread().getName());

                    // CRITICAL: Scanner operations MUST run on background thread, not network callback thread
                    // Run on scanner executor to avoid crashes
                    Log.d(TAG, "  → Switching to scanner executor thread for hardware operations");
                    scannerExecutor.execute(() -> {
                        try {
                            Log.d(TAG, "  → Scanner thread: " + Thread.currentThread().getName());
                            // Process this user's fingerprints
                            processSingleUserFingerprints(data, callback);
                        } catch (Exception e) {
                            Log.e(TAG, "✗ Error in scanner thread: " + e.getMessage());
                            e.printStackTrace();
                            if (callback != null) {
                                callback.onSyncError("Scanner error: " + e.getMessage());
                            }
                        }
                    });

                } else {
                    String error = "API returned error: " + response.getMessage();
                    Log.e(TAG, "✗ " + error);
                    if (callback != null) {
                        callback.onSyncError(error);
                    }
                }
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "✗ API call failed: " + error);
                if (callback != null) {
                    callback.onSyncError("Network error: " + error);
                }
            }
        });
    }

    /**
     * Process fingerprints for a single user
     */
    private void processSingleUserFingerprints(com.supremainc.sfm_sdk_android.data.model.response.FingerprintCredentialsResponse data,
                                                SyncCallback callback) {
        if (data.getFingerprints() == null || data.getFingerprints().isEmpty()) {
            Log.w(TAG, "No fingerprints found for user: " + data.getName());
            if (callback != null) {
                callback.onSyncCompleted(0, 0);
            }
            return;
        }

        int totalFingerprints = data.getFingerprints().size();
        Log.d(TAG, "Processing " + totalFingerprints + " fingerprint(s) for: " + data.getName());

        if (callback != null) {
            callback.onSyncStarted(totalFingerprints);
        }

        int successCount = 0;
        int failCount = 0;
        int current = 0;

        // Process each fingerprint
        for (com.supremainc.sfm_sdk_android.data.model.response.FingerprintCredentialsResponse.FingerCredentialWithId fp : data.getFingerprints()) {
            current++;

            try {
                // Step 1: Store in database and get assigned scanner ID
                int scannerId = dbHelper.insertOrUpdateSyncedFingerprint(
                        fp.getId(),                  // ULID string
                        data.getEmployeeNumber(),
                        data.getUsername(),
                        data.getName(),
                        data.getRole(),              // User role (ADMIN or CUSTODIAN)
                        fp.getFingerPrintBase64(),   // Already in Arrays.toString() format
                        fp.getLeftRight(),
                        fp.getFingerIndex(),
                        fp.getFingerType()
                );

                if (scannerId < 0) {
                    Log.e(TAG, "✗ Failed to store in database: " + data.getName());
                    failCount++;
                    continue;
                }

                // Step 2: Convert template string to byte array
                byte[] templateBytes = FingerprintUtils.convertStringToByteArray(fp.getFingerPrintBase64());

                if (templateBytes == null || templateBytes.length == 0) {
                    Log.e(TAG, "✗ Invalid template for: " + data.getName());
                    failCount++;
                    continue;
                }

                // Step 3: Enroll to scanner using assigned scanner ID
                boolean enrolled = enrollToScanner(scannerId, templateBytes, data.getName());

                if (enrolled) {
                    // Mark as enrolled in database
                    dbHelper.markSyncedFingerprintAsEnrolled(scannerId);
                    successCount++;

                    if (callback != null) {
                        callback.onFingerprintEnrolled(current, totalFingerprints, data.getName());
                    }

                    Log.i(TAG, "✓ Enrolled: " + data.getName() + " (Scanner ID: " + scannerId + ", API ID: " + fp.getId() + ")");
                } else {
                    failCount++;
                    Log.e(TAG, "✗ Failed to enroll: " + data.getName());
                }

            } catch (Exception e) {
                Log.e(TAG, "✗ Error processing fingerprint for: " + data.getName(), e);
                failCount++;
            }
        }

        // Fix provisional templates
        sdk.UF_FixProvisionalTemplate();

        Log.d(TAG, "╔════════════════════════════════════════════════════════════");
        Log.d(TAG, "║         INCREMENTAL SYNC COMPLETE                          ");
        Log.d(TAG, "╠════════════════════════════════════════════════════════════");
        Log.d(TAG, "║ User: " + data.getName());
        Log.d(TAG, "║ Success: " + successCount);
        Log.d(TAG, "║ Failed: " + failCount);
        Log.d(TAG, "╚════════════════════════════════════════════════════════════");

        if (callback != null) {
            callback.onSyncCompleted(successCount, failCount);
        }
    }
}
