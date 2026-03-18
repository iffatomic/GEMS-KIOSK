/*
 * Copyright (c) 2001 - 2025. Suprema Inc. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for details.
 */

package com.supremainc.sfm_sdk_android.service;

import android.content.Context;
import android.util.Log;

import com.supremainc.sfm_sdk_android.DatabaseHelper;
import com.supremainc.sfm_sdk_android.data.model.response.AllEmployeesFingerprintsResponse;
import com.supremainc.sfm_sdk_android.network.api.FingerprintDownloadApiClient;
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
    private final FingerprintDownloadApiClient apiClient;
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
        this.apiClient = new FingerprintDownloadApiClient(context);
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

                apiClient.getAllEmployeesWithFingerprints(new ApiCallback<AllEmployeesFingerprintsResponse>() {
                    @Override
                    public void onSuccess(AllEmployeesFingerprintsResponse response) {
                        if (response != null) {
                            Log.i(TAG, "✓ API call successful");
                            Log.i(TAG, "  - Total employees: " + response.getTotalEmployees());
                            Log.i(TAG, "  - Total fingerprints: " + response.getTotalFingerprints());

                            // STEP 4 & 5: Process fingerprints (insert to DB, enroll to scanner)
                            processAllFingerprints(response, callback);

                        } else {
                            String error = "API returned null response";
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

            apiClient.getAllEmployeesWithFingerprints(new ApiCallback<AllEmployeesFingerprintsResponse>() {
                @Override
                public void onSuccess(AllEmployeesFingerprintsResponse response) {
                    if (response != null) {
                        Log.i(TAG, "✓ API call successful");
                        Log.i(TAG, "  - Total employees: " + response.getTotalEmployees());
                        Log.i(TAG, "  - Total fingerprints: " + response.getTotalFingerprints());

                        // Process fingerprints incrementally (skip existing ones)
                        processAllFingerprintsIncremental(response, callback);

                    } else {
                        String error = "API returned null response";
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
     * NOW USING: FingerprintDownloadApi with templateDataString (LIKE SystemSettingsActivity!)
     */
    private void processAllFingerprintsIncremental(AllEmployeesFingerprintsResponse data, SyncCallback callback) {
        if (data.getEmployees() == null || data.getEmployees().isEmpty()) {
            Log.w(TAG, "No employees with fingerprints found");
            if (callback != null) {
                callback.onSyncCompleted(0, 0);
            }
            return;
        }

        // Count total fingerprints
        int totalFingerprints = 0;
        for (AllEmployeesFingerprintsResponse.EmployeeFingerprintData employee : data.getEmployees()) {
            if (employee.getFingerprints() != null) {
                totalFingerprints += employee.getFingerprints().size();
            }
        }

        Log.d(TAG, "Processing " + totalFingerprints + " fingerprints from " + data.getEmployees().size() + " employees");

        if (callback != null) {
            callback.onSyncStarted(totalFingerprints);
        }

        int successCount = 0;
        int skippedCount = 0;
        int failCount = 0;
        int current = 0;

        // Process each employee's fingerprints
        for (AllEmployeesFingerprintsResponse.EmployeeFingerprintData employee : data.getEmployees()) {
            if (employee.getFingerprints() == null || employee.getFingerprints().isEmpty()) {
                continue;
            }

            // OPTIMIZATION: Check once per employee if they're enrolled locally
            // If employee exists in local users table with fingerprints, skip ALL their fingerprints from API sync
            // This prevents duplicate enrollment (same employee in both local and synced tables)
            boolean isEnrolledLocally = dbHelper.isUserEnrolledLocally(employee.getStaffID());
            if (isEnrolledLocally) {
                Log.d(TAG, "  ⊘ Skipped employee (already enrolled locally): " + employee.getFullName() + " (" + employee.getStaffID() + ") - " + employee.getFingerprints().size() + " fingerprint(s)");
                skippedCount += employee.getFingerprints().size();
                current += employee.getFingerprints().size();
                continue; // Skip all fingerprints for this employee
            }

            for (AllEmployeesFingerprintsResponse.FingerprintData fp : employee.getFingerprints()) {
                current++;

                try {
                    // Check if fingerprint already exists in synced_fingerprints table
                    DatabaseHelper.SyncedFingerprint existing = dbHelper.getSyncedFingerprintByApiId(String.valueOf(fp.getId()));

                    if (existing != null && existing.isEnrolledToScanner()) {
                        // Already enrolled to synced range, skip
                        Log.d(TAG, "  ⊘ Skipped (already synced): " + employee.getFullName() + " - Scanner ID: " + existing.getScannerId());
                        skippedCount++;
                        continue;
                    }

                    // CRITICAL FIX: Use templateDataString directly (like SystemSettingsActivity!)
                    // NO Base64 decoding needed!
                    String templateString = fp.getTemplateDataString();

                    if (templateString == null || templateString.isEmpty()) {
                        Log.e(TAG, "✗ Empty template data for: " + employee.getFullName());
                        failCount++;
                        continue;
                    }

                    // Insert or update in database (gets scanner ID)
                    int scannerId = dbHelper.insertOrUpdateSyncedFingerprint(
                            String.valueOf(fp.getId()),
                            employee.getStaffID(),
                            employee.getStaffID(),  // Username = StaffID
                            employee.getFullName(),
                            employee.getRole() != null ? employee.getRole() : "N/A",  // FIXED: Use role, not department!
                            employee.isAllowedOverride(),
                            templateString,  // Pass template string DIRECTLY (no conversion needed!)
                            fp.getLeftRight(),
                            fp.getFingerIndex(),
                            String.valueOf(fp.getFingerType())
                    );

                    if (scannerId < 0) {
                        Log.e(TAG, "✗ Failed to store in database: " + employee.getFullName());
                        failCount++;
                        continue;
                    }

                    // Enroll to scanner (enrollToScanner will parse the string and handle it properly)
                    boolean enrolled = enrollToScanner(scannerId, null, employee.getFullName());

                    if (enrolled) {
                        // Mark as enrolled in database
                        dbHelper.markSyncedFingerprintAsEnrolled(scannerId);
                        successCount++;

                        if (callback != null) {
                            callback.onFingerprintEnrolled(current, totalFingerprints, employee.getFullName());
                        }

                        Log.i(TAG, "✓ Enrolled: " + employee.getFullName() + " (Scanner ID: " + scannerId + ")");
                    } else {
                        failCount++;
                        Log.e(TAG, "✗ Failed to enroll: " + employee.getFullName());
                    }

                } catch (Exception e) {
                    Log.e(TAG, "✗ Error processing: " + employee.getFullName(), e);
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
     * Process all fingerprints from API response (TRUNCATE mode)
     * NOW USING: FingerprintDownloadApi with templateDataString (LIKE SystemSettingsActivity!)
     */
    private void processAllFingerprints(AllEmployeesFingerprintsResponse data, SyncCallback callback) {
        if (data.getEmployees() == null || data.getEmployees().isEmpty()) {
            Log.w(TAG, "No employees with fingerprints found");
            if (callback != null) {
                callback.onSyncCompleted(0, 0);
            }
            return;
        }

        // Count total fingerprints
        int totalFingerprints = 0;
        for (AllEmployeesFingerprintsResponse.EmployeeFingerprintData employee : data.getEmployees()) {
            if (employee.getFingerprints() != null) {
                totalFingerprints += employee.getFingerprints().size();
            }
        }

        Log.d(TAG, "Processing " + totalFingerprints + " fingerprints from " + data.getEmployees().size() + " employees");

        if (callback != null) {
            callback.onSyncStarted(totalFingerprints);
        }

        int successCount = 0;
        int failCount = 0;
        int current = 0;

        // Process each employee's fingerprints
        for (AllEmployeesFingerprintsResponse.EmployeeFingerprintData employee : data.getEmployees()) {
            if (employee.getFingerprints() == null || employee.getFingerprints().isEmpty()) {
                continue;
            }

            Log.d(TAG, "Processing employee: " + employee.getFullName() + " (" + employee.getStaffID() + ")");

            for (AllEmployeesFingerprintsResponse.FingerprintData fp : employee.getFingerprints()) {
                current++;

                try {
                    // CRITICAL FIX: Use templateDataString directly (like SystemSettingsActivity!)
                    // NO Base64 decoding needed!
                    String templateString = fp.getTemplateDataString();

                    if (templateString == null || templateString.isEmpty()) {
                        Log.e(TAG, "✗ Empty template data for: " + employee.getFullName());
                        failCount++;
                        continue;
                    }

                    // Step 1: Store in database and get assigned scanner ID
                    int scannerId = dbHelper.insertOrUpdateSyncedFingerprint(
                            String.valueOf(fp.getId()),
                            employee.getStaffID(),
                            employee.getStaffID(),  // Username = StaffID
                            employee.getFullName(),
                            employee.getRole() != null ? employee.getRole() : "N/A",  // FIXED: Use role, not department!
                            employee.isAllowedOverride(),
                            templateString,  // Pass template string DIRECTLY (no conversion needed!)
                            fp.getLeftRight(),
                            fp.getFingerIndex(),
                            String.valueOf(fp.getFingerType())
                    );

                    if (scannerId < 0) {
                        Log.e(TAG, "✗ Failed to store in database: " + employee.getFullName());
                        failCount++;
                        continue;
                    }

                    // OPTIMIZATION: Check if already enrolled to scanner
                    if (dbHelper.isSyncedFingerprintEnrolled(scannerId)) {
                        Log.d(TAG, "⊙ Already enrolled: " + employee.getFullName() + " (Scanner ID: " + scannerId + ") - SKIPPING");
                        successCount++;

                        if (callback != null) {
                            callback.onFingerprintEnrolled(current, totalFingerprints, employee.getFullName());
                        }
                        continue;
                    }

                    // Step 2: Enroll to scanner (enrollToScanner will read from database and handle properly)
                    boolean enrolled = enrollToScanner(scannerId, null, employee.getFullName());

                    if (enrolled) {
                        // Mark as enrolled in database
                        dbHelper.markSyncedFingerprintAsEnrolled(scannerId);
                        successCount++;

                        if (callback != null) {
                            callback.onFingerprintEnrolled(current, totalFingerprints, employee.getFullName());
                        }

                        Log.i(TAG, "✓ Enrolled: " + employee.getFullName() + " (Scanner ID: " + scannerId + ", API ID: " + fp.getId() + ")");
                    } else {
                        failCount++;
                        Log.e(TAG, "✗ Failed to enroll: " + employee.getFullName());
                    }

                } catch (Exception e) {
                    Log.e(TAG, "✗ Error processing: " + employee.getFullName(), e);
                    failCount++;
                }
            }
        }

        // Step 3: Fix provisional templates (like reference app)
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
     * Based on SystemSettingsActivity.java (THE WORKING METHOD)
     *
     * CRITICAL: This method MUST match SystemSettingsActivity's exact approach:
     * 1. Read template from database (database round-trip for verification)
     * 2. LIMIT to FIRST 384 bytes only (API sends 768 bytes = 2 templates, we only enroll first)
     * 3. Enroll using the limited template
     *
     * @param scannerId Integer scanner ID (auto-assigned by database)
     * @param template Fingerprint template bytes (may be 768 bytes, but we only use first 384)
     * @param name User name (for logging)
     * @return true if successful
     */
    private boolean enrollToScanner(int scannerId, byte[] template, String name) {
        Log.d(TAG, "╔════════════════════════════════════════════════════════════");
        Log.d(TAG, "║ Enrolling to scanner: " + name);
        Log.d(TAG, "║ Scanner ID: " + scannerId);
        if (template != null) {
            Log.d(TAG, "║ Template size received: " + template.length + " bytes");
        } else {
            Log.d(TAG, "║ Template will be loaded from database");
        }
        Log.d(TAG, "╚════════════════════════════════════════════════════════════");

        try {
            // STEP 1: Database round-trip (like SystemSettingsActivity does)
            // Read template back from database to verify integrity
            Log.d(TAG, "  → STEP 1: Database round-trip verification");
            String templateStringFromDb = dbHelper.getTemplateDataByScannerId(scannerId);

            if (templateStringFromDb == null || templateStringFromDb.isEmpty()) {
                Log.e(TAG, "  ✗ Failed to read template from database");
                return false;
            }

            Log.d(TAG, "  → DB string length: " + templateStringFromDb.length() + " chars");

            byte[] templateBytesFromDb = DatabaseHelper.parseStringToByteArray(templateStringFromDb);

            if (templateBytesFromDb == null || templateBytesFromDb.length == 0) {
                Log.e(TAG, "  ✗ Failed to parse template from database");
                return false;
            }

            Log.d(TAG, "  → Parsed from DB: " + templateBytesFromDb.length + " bytes");

            // STEP 2: CRITICAL - Limit to FIRST 384 bytes only
            // This is THE KEY DIFFERENCE that makes SystemSettingsActivity work!
            // API sends 768 bytes (2 templates), but we only enroll the first 384 bytes
            int STANDARD_TEMPLATE_SIZE = 384;
            int enrollSize = Math.min(templateBytesFromDb.length, STANDARD_TEMPLATE_SIZE);

            byte[] enrollTemplate = new byte[enrollSize];
            System.arraycopy(templateBytesFromDb, 0, enrollTemplate, 0, enrollSize);

            Log.d(TAG, "  → STEP 2: Limited to FIRST " + enrollSize + " bytes (standard size)");

            // Log first 20 bytes for debugging
            StringBuilder hexPreview = new StringBuilder();
            for (int i = 0; i < Math.min(20, enrollTemplate.length); i++) {
                hexPreview.append(String.format("%02X ", enrollTemplate[i]));
            }
            Log.d(TAG, "  → First 20 bytes (HEX): " + hexPreview.toString());

            // STEP 3: Enroll to scanner
            Log.d(TAG, "  → STEP 3: Enrolling to scanner hardware");

            int[] enrollID = new int[1];
            int[] templateSize = new int[1];

            enrollID[0] = 0;
            templateSize[0] = enrollSize;  // Use limited size (384 bytes)

            // Cancel any previous operations to ensure scanner is ready
            sdk.UF_Cancel(false);
            Log.d(TAG, "  → UF_Cancel called");

            long startTime = System.currentTimeMillis();

            // CRITICAL: Use assigned scanner ID and LIMITED template (384 bytes)
            UF_RET_CODE ret = sdk.UF_EnrollTemplate(
                    scannerId,                        // Use assigned scanner ID
                    UF_ENROLL_OPTION.UF_ENROLL_NONE,  // Don't auto-generate ID
                    templateSize[0],                  // 384 bytes (limited size)
                    enrollTemplate,                   // LIMITED template (first 384 bytes)
                    enrollID
            );

            long elapsed = System.currentTimeMillis() - startTime;
            Log.d(TAG, "  → Enrollment completed in " + elapsed + "ms");
            Log.d(TAG, "  → Result: " + ret);
            Log.d(TAG, "  → Returned enrollID: " + enrollID[0]);

            if (ret == UF_RET_CODE.UF_RET_SUCCESS) {
                Log.d(TAG, "  ✓ Enroll SUCCESS");
                Log.d(TAG, "╚════════════════════════════════════════════════════════════");
                return true;
            } else if (ret == UF_RET_CODE.UF_ERR_EXIST_ID) {
                Log.d(TAG, "  ⊙ Already enrolled (ID exists)");
                Log.d(TAG, "╚════════════════════════════════════════════════════════════");
                return true;  // Treat as success
            } else if (ret == UF_RET_CODE.UF_ERR_TIME_OUT) {
                Log.e(TAG, "  ✗ Enroll TIMEOUT");
                Log.d(TAG, "╚════════════════════════════════════════════════════════════");
                return false;
            } else {
                Log.e(TAG, "  ✗ Enroll FAILED: " + ret);
                Log.d(TAG, "╚════════════════════════════════════════════════════════════");
                return false;
            }

        } catch (Exception e) {
            Log.e(TAG, "  ✗ Exception during enroll", e);
            Log.d(TAG, "╚════════════════════════════════════════════════════════════");
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
     * Sync fingerprints for a single employee (incremental sync)
     * Call this immediately after enrolling an employee to make them available for verification
     *
     * NEW APPROACH: Fetches ALL employees and filters for the specific employee
     * This uses the same FingerprintDownloadApi endpoint as full sync
     *
     * @param employeeNumber Employee number of the newly enrolled employee
     * @param callback Progress callback (optional)
     */
    public void syncSingleUser(String employeeNumber, SyncCallback callback) {
        Log.d(TAG, "╔════════════════════════════════════════════════════════════");
        Log.d(TAG, "║         INCREMENTAL SYNC - SINGLE EMPLOYEE                 ");
        Log.d(TAG, "╠════════════════════════════════════════════════════════════");
        Log.d(TAG, "║ Employee Number: " + employeeNumber);
        Log.d(TAG, "╚════════════════════════════════════════════════════════════");

        // Run on scanner executor (background thread)
        scannerExecutor.execute(() -> {
            Log.d(TAG, "  → Scanner thread: " + Thread.currentThread().getName());

            apiClient.getAllEmployeesWithFingerprints(new ApiCallback<AllEmployeesFingerprintsResponse>() {
                @Override
                public void onSuccess(AllEmployeesFingerprintsResponse response) {
                    if (response != null && response.getEmployees() != null) {
                        Log.i(TAG, "✓ API call successful");
                        Log.i(TAG, "  - Total employees in response: " + response.getTotalEmployees());

                        // Filter for the specific employee
                        AllEmployeesFingerprintsResponse.EmployeeFingerprintData targetEmployee = null;
                        for (AllEmployeesFingerprintsResponse.EmployeeFingerprintData employee : response.getEmployees()) {
                            if (employeeNumber.equals(employee.getStaffID())) {
                                targetEmployee = employee;
                                break;
                            }
                        }

                        if (targetEmployee == null) {
                            String error = "Employee not found: " + employeeNumber;
                            Log.e(TAG, "✗ " + error);
                            if (callback != null) {
                                callback.onSyncError(error);
                            }
                            return;
                        }

                        Log.i(TAG, "  - Found employee: " + targetEmployee.getFullName());
                        Log.i(TAG, "  - Total fingerprints: " + (targetEmployee.getFingerprints() != null ? targetEmployee.getFingerprints().size() : 0));

                        // Process this employee's fingerprints
                        processSingleEmployeeFingerprints(targetEmployee, callback);

                    } else {
                        String error = "API returned null or empty response";
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
     * Process fingerprints for a single employee
     * NOW USING: templateDataString directly (LIKE SystemSettingsActivity!)
     */
    private void processSingleEmployeeFingerprints(AllEmployeesFingerprintsResponse.EmployeeFingerprintData employee,
                                                     SyncCallback callback) {
        if (employee.getFingerprints() == null || employee.getFingerprints().isEmpty()) {
            Log.w(TAG, "No fingerprints found for employee: " + employee.getFullName());
            if (callback != null) {
                callback.onSyncCompleted(0, 0);
            }
            return;
        }

        int totalFingerprints = employee.getFingerprints().size();
        Log.d(TAG, "Processing " + totalFingerprints + " fingerprint(s) for: " + employee.getFullName());

        if (callback != null) {
            callback.onSyncStarted(totalFingerprints);
        }

        int successCount = 0;
        int failCount = 0;
        int current = 0;

        // Process each fingerprint
        for (AllEmployeesFingerprintsResponse.FingerprintData fp : employee.getFingerprints()) {
            current++;

            try {
                // CRITICAL FIX: Use templateDataString directly (like SystemSettingsActivity!)
                // NO Base64 decoding needed!
                String templateString = fp.getTemplateDataString();

                if (templateString == null || templateString.isEmpty()) {
                    Log.e(TAG, "✗ Empty template data for: " + employee.getFullName());
                    failCount++;
                    continue;
                }

                // Step 1: Store in database and get assigned scanner ID
                int scannerId = dbHelper.insertOrUpdateSyncedFingerprint(
                        String.valueOf(fp.getId()),
                        employee.getStaffID(),
                        employee.getStaffID(),  // Username = StaffID
                        employee.getFullName(),
                        employee.getRole() != null ? employee.getRole() : "N/A",  // FIXED: Use role, not department!
                        employee.isAllowedOverride(),
                        templateString,  // Pass template string DIRECTLY (no conversion needed!)
                        fp.getLeftRight(),
                        fp.getFingerIndex(),
                        String.valueOf(fp.getFingerType())
                );

                if (scannerId < 0) {
                    Log.e(TAG, "✗ Failed to store in database: " + employee.getFullName());
                    failCount++;
                    continue;
                }

                // Step 2: Enroll to scanner (enrollToScanner will read from database and handle properly)
                boolean enrolled = enrollToScanner(scannerId, null, employee.getFullName());

                if (enrolled) {
                    // Mark as enrolled in database
                    dbHelper.markSyncedFingerprintAsEnrolled(scannerId);
                    successCount++;

                    if (callback != null) {
                        callback.onFingerprintEnrolled(current, totalFingerprints, employee.getFullName());
                    }

                    Log.i(TAG, "✓ Enrolled: " + employee.getFullName() + " (Scanner ID: " + scannerId + ", API ID: " + fp.getId() + ")");
                } else {
                    failCount++;
                    Log.e(TAG, "✗ Failed to enroll: " + employee.getFullName());
                }

            } catch (Exception e) {
                Log.e(TAG, "✗ Error processing fingerprint for: " + employee.getFullName(), e);
                failCount++;
            }
        }

        // Fix provisional templates
        sdk.UF_FixProvisionalTemplate();

        Log.d(TAG, "╔════════════════════════════════════════════════════════════");
        Log.d(TAG, "║         INCREMENTAL SYNC COMPLETE                          ");
        Log.d(TAG, "╠════════════════════════════════════════════════════════════");
        Log.d(TAG, "║ Employee: " + employee.getFullName());
        Log.d(TAG, "║ Success: " + successCount);
        Log.d(TAG, "║ Failed: " + failCount);
        Log.d(TAG, "╚════════════════════════════════════════════════════════════");

        if (callback != null) {
            callback.onSyncCompleted(successCount, failCount);
        }
    }
}
