/*
 * Copyright (c) 2001 - 2025. Suprema Inc. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for details.
 */

package com.supremainc.sfm_sdk_android;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * DatabaseHelper manages the SQLite database for our fingerprint application
 *
 * DATABASE STRUCTURE:
 * - users table: stores user information and fingerprint templates
 * - attendance table: stores attendance records when users scan their fingerprints
 *
 * KEY CONCEPT: We store both the fingerprint template data AND the scanner user IDs
 * This allows us to match between what the scanner returns and our user records
 */
public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String TAG = "DatabaseHelper";

    // Database configuration
    private static final String DATABASE_NAME = "FingerprintDB";
    private static final int DATABASE_VERSION = 13;  // Updated to version 13 for is_allowed_override column in synced_fingerprints

    // Table names
    private static final String TABLE_USERS = "users";
    private static final String TABLE_ATTENDANCE = "attendance";
    private static final String TABLE_REGISTERED_PERSONNEL = "registered_personnel";
    private static final String TABLE_VAULT_OVERRIDES = "vault_overrides";
    private static final String TABLE_SYNCED_FINGERPRINTS = "synced_fingerprints";

    // Vault Override table columns
    private static final String KEY_OVERRIDE_ID = "id";
    private static final String KEY_OVERRIDE_VAULT_TYPE = "vault_type";  // "MAIN" or "DAY"
    private static final String KEY_OVERRIDE_ENTRY_POINT = "entry_point";  // "MAIN_GRILL", "COMPARTMENT_1", "COMPARTMENT_2", "COMPARTMENT_3"
    private static final String KEY_OVERRIDE_STATUS = "status";  // "ACTIVE", "DEACTIVATED"
    private static final String KEY_OVERRIDE_PROFILE_ID = "profile_id";  // PAC API profile ID (UUID from server)
    private static final String KEY_OVERRIDE_ACTIVATED_AT = "activated_at";
    private static final String KEY_OVERRIDE_DEACTIVATED_AT = "deactivated_at";
    private static final String KEY_OVERRIDE_CUSTODIAN_1_ID = "custodian_1_id";
    private static final String KEY_OVERRIDE_CUSTODIAN_2_ID = "custodian_2_id";
    private static final String KEY_OVERRIDE_CUSTODIAN_3_ID = "custodian_3_id";
    private static final String KEY_OVERRIDE_DEACTIVATED_BY = "deactivated_by";

    // Synced Fingerprints table columns (for fingerprints downloaded from API)
    private static final String KEY_SYNCED_ID = "id";                           // Local auto-increment ID
    private static final String KEY_SYNCED_API_ID = "api_fingerprint_id";      // ULID string from PAC API
    private static final String KEY_SYNCED_SCANNER_ID = "scanner_id";           // Integer scanner ID for UF_EnrollTemplate
    private static final String KEY_SYNCED_EMPLOYEE_NUMBER = "employee_number"; // Employee number
    private static final String KEY_SYNCED_USERNAME = "username";               // Username
    private static final String KEY_SYNCED_NAME = "name";                       // Full name
    private static final String KEY_SYNCED_ROLE = "role";                       // "ADMIN" or "CUSTODIAN"
    private static final String KEY_SYNCED_TEMPLATE_DATA = "template_data";     // Template as TEXT (Arrays.toString representation)
    private static final String KEY_SYNCED_LEFT_RIGHT = "left_right";           // 0 = Left, 1 = Right
    private static final String KEY_SYNCED_FINGER_INDEX = "finger_index";       // 1-10
    private static final String KEY_SYNCED_FINGER_TYPE = "finger_type";         // "Thumb", "Index", etc.
    private static final String KEY_SYNCED_ENROLLED_TO_SCANNER = "enrolled_to_scanner"; // 1 if enrolled to scanner, 0 if not
    private static final String KEY_SYNCED_IS_ALLOWED_OVERRIDE = "is_allowed_override"; // 1 if allowed to perform manual override, 0 if not
    private static final String KEY_SYNCED_AT = "synced_at";                    // When synced from API

    // Registered Personnel table columns
    private static final String KEY_PERSONNEL_ID = "id";
    private static final String KEY_PERSONNEL_EMPLOYEE_ID = "employee_id";
    private static final String KEY_PERSONNEL_NAME = "name";
    private static final String KEY_PERSONNEL_ROLE = "role";  // "ADMIN" or "CUSTODIAN"
    private static final String KEY_PERSONNEL_IS_ENROLLED = "is_enrolled";  // 0 = not enrolled, 1 = enrolled
    private static final String KEY_PERSONNEL_ENROLLED_USER_ID = "enrolled_user_id";  // References users table after enrollment

    // Users table columns
    private static final String KEY_USER_ID = "id";                    // Primary key
    private static final String KEY_IC_NUMBER = "ic_number";           // IC number (unique)
    private static final String KEY_STAFF_ID = "staff_id";             // Staff ID (unique)
    private static final String KEY_NAME = "name";                     // User's name
    private static final String KEY_DEPARTMENT = "department";         // User's department
    private static final String KEY_PASSWORD = "password";             // Admin password (if admin)
    private static final String KEY_LEFT_FINGERPRINT = "left_fingerprint";   // Left finger template data
    private static final String KEY_RIGHT_FINGERPRINT = "right_fingerprint"; // Right finger template data

    private static final String KEY_LEFT_FINGERPRINT_2 = "left_fingerprint_2";
    private static final String KEY_RIGHT_FINGERPRINT_2 = "right_fingerprint_2";
    private static final String KEY_LEFT_SCANNER_ID_2 = "left_scanner_id_2";
    private static final String KEY_RIGHT_SCANNER_ID_2 = "right_scanner_id_2";
    private static final String KEY_LEFT_SCANNER_ID = "left_scanner_id";     // Scanner ID for left finger
    private static final String KEY_RIGHT_SCANNER_ID = "right_scanner_id";   // Scanner ID for right finger
    private static final String KEY_CREATED_AT = "created_at";         // When record was created
    private static final String KEY_UPDATED_AT = "updated_at";         // When record was last updated
    private static final String KEY_IS_ACTIVE = "is_active";           // Whether user is active (1) or deleted (0)
    private static final String KEY_IS_ADMIN = "is_admin";             // Whether user is admin (1) or regular user (0)

    // Attendance table columns
    private static final String KEY_ATTENDANCE_ID = "attendance_id";       // Primary key
    private static final String KEY_ATTENDANCE_USER_ID = "user_id";        // Foreign key to users table
    private static final String KEY_ATTENDANCE_TYPE = "attendance_type";   // Type: "check_in", "check_out", "vault_access"
    private static final String KEY_ATTENDANCE_TIMESTAMP = "timestamp";    // When attendance was recorded
    private static final String KEY_FINGER_USED = "finger_used";           // Which finger was used: "left" or "right"

    // =================== CONSTRUCTOR ===================

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    // =================== DATABASE CREATION ===================

    /**
     * Called when database is created for the first time
     * Creates all tables with their structure
     */
    @Override
    public void onCreate(SQLiteDatabase db) {
        // Create Users table
        String CREATE_USERS_TABLE = "CREATE TABLE " + TABLE_USERS + "("
                + KEY_USER_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"  // Auto-incrementing ID
                + KEY_IC_NUMBER + " TEXT UNIQUE NOT NULL,"             // IC number must be unique
                + KEY_STAFF_ID + " TEXT UNIQUE NOT NULL,"              // Staff ID must be unique
                + KEY_NAME + " TEXT NOT NULL,"                         // Name is required
                + KEY_DEPARTMENT + " TEXT NOT NULL,"                   // Department is required
                + KEY_PASSWORD + " TEXT,"                              // Password (optional, for admin)
                + KEY_LEFT_FINGERPRINT + " BLOB,"                     // Left fingerprint template (binary data)
                + KEY_RIGHT_FINGERPRINT + " BLOB,"                    // Right fingerprint template (binary data)
                + KEY_LEFT_SCANNER_ID + " INTEGER,"                   // Scanner ID for left finger
                + KEY_RIGHT_SCANNER_ID + " INTEGER,"                  // Scanner ID for right finger
                + KEY_LEFT_FINGERPRINT_2 + " BLOB,"                    // Third finger template
                + KEY_RIGHT_FINGERPRINT_2 + " BLOB,"                   // Fourth finger template
                + KEY_LEFT_SCANNER_ID_2 + " INTEGER,"                  // Scanner ID for third finger
                + KEY_RIGHT_SCANNER_ID_2 + " INTEGER,"                 // Scanner ID for fourth finger
                + KEY_CREATED_AT + " DATETIME DEFAULT CURRENT_TIMESTAMP,"  // Auto-set creation time
                + KEY_UPDATED_AT + " DATETIME DEFAULT CURRENT_TIMESTAMP,"  // Auto-set update time
                + KEY_IS_ACTIVE + " INTEGER DEFAULT 1,"               // Active by default
                + KEY_IS_ADMIN + " INTEGER DEFAULT 0"                 // Regular user by default
                + ")";

        // Create Attendance table
        String CREATE_ATTENDANCE_TABLE = "CREATE TABLE " + TABLE_ATTENDANCE + "("
                + KEY_ATTENDANCE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"  // Auto-incrementing ID
                + KEY_ATTENDANCE_USER_ID + " INTEGER NOT NULL,"              // Must reference a user
                + KEY_ATTENDANCE_TYPE + " TEXT NOT NULL,"                    // Type of attendance record
                + KEY_ATTENDANCE_TIMESTAMP + " DATETIME DEFAULT CURRENT_TIMESTAMP,"  // Auto-set timestamp
                + KEY_FINGER_USED + " TEXT,"                                 // Which finger was used
                + "FOREIGN KEY(" + KEY_ATTENDANCE_USER_ID + ") REFERENCES " // Foreign key constraint
                + TABLE_USERS + "(" + KEY_USER_ID + ")"                     // References users table
                + ")";

        // Create Registered Personnel table (pre-registered admins and custodians)
        String CREATE_REGISTERED_PERSONNEL_TABLE = "CREATE TABLE " + TABLE_REGISTERED_PERSONNEL + "("
                + KEY_PERSONNEL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + KEY_PERSONNEL_EMPLOYEE_ID + " TEXT UNIQUE NOT NULL,"
                + KEY_PERSONNEL_NAME + " TEXT NOT NULL,"
                + KEY_PERSONNEL_ROLE + " TEXT NOT NULL,"  // "ADMIN" or "CUSTODIAN"
                + KEY_PERSONNEL_IS_ENROLLED + " INTEGER DEFAULT 0,"
                + KEY_PERSONNEL_ENROLLED_USER_ID + " INTEGER,"
                + "FOREIGN KEY(" + KEY_PERSONNEL_ENROLLED_USER_ID + ") REFERENCES "
                + TABLE_USERS + "(" + KEY_USER_ID + ")"
                + ")";

        // Create Vault Overrides table
        String CREATE_VAULT_OVERRIDES_TABLE = "CREATE TABLE " + TABLE_VAULT_OVERRIDES + "("
                + KEY_OVERRIDE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + KEY_OVERRIDE_VAULT_TYPE + " TEXT NOT NULL,"
                + KEY_OVERRIDE_ENTRY_POINT + " TEXT NOT NULL,"
                + KEY_OVERRIDE_STATUS + " TEXT DEFAULT 'ACTIVE',"
                + KEY_OVERRIDE_PROFILE_ID + " TEXT,"
                + KEY_OVERRIDE_ACTIVATED_AT + " DATETIME DEFAULT CURRENT_TIMESTAMP,"
                + KEY_OVERRIDE_DEACTIVATED_AT + " DATETIME,"
                + KEY_OVERRIDE_CUSTODIAN_1_ID + " TEXT,"
                + KEY_OVERRIDE_CUSTODIAN_2_ID + " TEXT,"
                + KEY_OVERRIDE_CUSTODIAN_3_ID + " TEXT,"
                + KEY_OVERRIDE_DEACTIVATED_BY + " TEXT"
                + ")";

        // Create Synced Fingerprints table (for cross-device fingerprint sync)
        String CREATE_SYNCED_FINGERPRINTS_TABLE = "CREATE TABLE " + TABLE_SYNCED_FINGERPRINTS + "("
                + KEY_SYNCED_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + KEY_SYNCED_API_ID + " TEXT UNIQUE NOT NULL,"     // ULID string from API (unique)
                + KEY_SYNCED_SCANNER_ID + " INTEGER UNIQUE,"       // Integer scanner ID (auto-assigned)
                + KEY_SYNCED_EMPLOYEE_NUMBER + " TEXT NOT NULL,"
                + KEY_SYNCED_USERNAME + " TEXT NOT NULL,"
                + KEY_SYNCED_NAME + " TEXT NOT NULL,"
                + KEY_SYNCED_ROLE + " TEXT,"                       // "ADMIN" or "CUSTODIAN"
                + KEY_SYNCED_TEMPLATE_DATA + " TEXT NOT NULL,"     // Arrays.toString representation
                + KEY_SYNCED_LEFT_RIGHT + " INTEGER,"
                + KEY_SYNCED_FINGER_INDEX + " INTEGER,"
                + KEY_SYNCED_FINGER_TYPE + " TEXT,"
                + KEY_SYNCED_ENROLLED_TO_SCANNER + " INTEGER DEFAULT 0,"
                + KEY_SYNCED_IS_ALLOWED_OVERRIDE + " INTEGER DEFAULT 0,"
                + KEY_SYNCED_AT + " DATETIME DEFAULT CURRENT_TIMESTAMP"
                + ")";

        // Execute SQL commands to create tables
        db.execSQL(CREATE_USERS_TABLE);
        db.execSQL(CREATE_ATTENDANCE_TABLE);
        db.execSQL(CREATE_REGISTERED_PERSONNEL_TABLE);
        db.execSQL(CREATE_VAULT_OVERRIDES_TABLE);
        db.execSQL(CREATE_SYNCED_FINGERPRINTS_TABLE);

        Log.d(TAG, "Database tables created successfully");

        // Insert sample pre-registered personnel for testing
        insertSamplePersonnel(db);
    }

    /**
     * Insert sample pre-registered admins and custodians for testing
     */
    private void insertSamplePersonnel(SQLiteDatabase db) {
        // Sample Admins
        insertPersonnel(db, "ADM001", "Ahmad bin Abdullah", "ADMIN");
        insertPersonnel(db, "ADM002", "Siti Aminah binti Hassan", "ADMIN");
        insertPersonnel(db, "ADM003", "Raj Kumar", "ADMIN");

        // Sample Custodians
        insertPersonnel(db, "CUS001", "Muhammad Faiz bin Osman", "CUSTODIAN");
        insertPersonnel(db, "CUS002", "Nurul Aina binti Yusof", "CUSTODIAN");
        insertPersonnel(db, "CUS003", "Lee Wei Ming", "CUSTODIAN");
        insertPersonnel(db, "CUS004", "Priya Devi", "CUSTODIAN");

        Log.d(TAG, "Sample personnel inserted successfully");
    }

    /**
     * Helper method to insert a single personnel record
     */
    private void insertPersonnel(SQLiteDatabase db, String employeeId, String name, String role) {
        ContentValues values = new ContentValues();
        values.put(KEY_PERSONNEL_EMPLOYEE_ID, employeeId);
        values.put(KEY_PERSONNEL_NAME, name);
        values.put(KEY_PERSONNEL_ROLE, role);
        values.put(KEY_PERSONNEL_IS_ENROLLED, 0);
        db.insert(TABLE_REGISTERED_PERSONNEL, null, values);
    }

    /**
     * Called when database needs to be upgraded to a new version
     * Handles adding new columns or modifying structure
     */
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.d(TAG, "Upgrading database from version " + oldVersion + " to " + newVersion);

        // Upgrade from version 3 to 4 (add 3rd and 4th finger fields)
        if (oldVersion < 2) {
            try {
                db.execSQL("ALTER TABLE " + TABLE_USERS + " ADD COLUMN " + KEY_PASSWORD + " TEXT");
                db.execSQL("ALTER TABLE " + TABLE_USERS + " ADD COLUMN " + KEY_IS_ADMIN + " INTEGER DEFAULT 0");
                Log.d(TAG, "Successfully upgraded to version 2");
            } catch (Exception e) {
                Log.e(TAG, "Error upgrading to version 2", e);
                // If upgrade fails, recreate entire database
                recreateDatabase(db);
            }
        }

        // Upgrade from version 2 to 3 (add scanner ID fields)
        if (oldVersion < 3) {
            try {
                db.execSQL("ALTER TABLE " + TABLE_USERS + " ADD COLUMN " + KEY_LEFT_SCANNER_ID + " INTEGER");
                db.execSQL("ALTER TABLE " + TABLE_USERS + " ADD COLUMN " + KEY_RIGHT_SCANNER_ID + " INTEGER");
                Log.d(TAG, "Successfully upgraded to version 3");
            } catch (Exception e) {
                Log.e(TAG, "Error upgrading to version 3", e);
                // If upgrade fails, recreate entire database
                recreateDatabase(db);
            }
        }

        // Upgrade from version 3 to 4 (add 3rd and 4th finger fields)
        if (oldVersion < 4) {
            try {
                db.execSQL("ALTER TABLE " + TABLE_USERS + " ADD COLUMN " + KEY_LEFT_FINGERPRINT_2 + " BLOB");
                db.execSQL("ALTER TABLE " + TABLE_USERS + " ADD COLUMN " + KEY_RIGHT_FINGERPRINT_2 + " BLOB");
                db.execSQL("ALTER TABLE " + TABLE_USERS + " ADD COLUMN " + KEY_LEFT_SCANNER_ID_2 + " INTEGER");
                db.execSQL("ALTER TABLE " + TABLE_USERS + " ADD COLUMN " + KEY_RIGHT_SCANNER_ID_2 + " INTEGER");
                Log.d(TAG, "Successfully upgraded to version 4");
            } catch (Exception e) {
                Log.e(TAG, "Error upgrading to version 4", e);
                recreateDatabase(db);
            }
        }

        // Upgrade from version 4 to 5 (add registered personnel table)
        if (oldVersion < 5) {
            try {
                String CREATE_REGISTERED_PERSONNEL_TABLE = "CREATE TABLE " + TABLE_REGISTERED_PERSONNEL + "("
                        + KEY_PERSONNEL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + KEY_PERSONNEL_EMPLOYEE_ID + " TEXT UNIQUE NOT NULL,"
                        + KEY_PERSONNEL_NAME + " TEXT NOT NULL,"
                        + KEY_PERSONNEL_ROLE + " TEXT NOT NULL,"
                        + KEY_PERSONNEL_IS_ENROLLED + " INTEGER DEFAULT 0,"
                        + KEY_PERSONNEL_ENROLLED_USER_ID + " INTEGER,"
                        + "FOREIGN KEY(" + KEY_PERSONNEL_ENROLLED_USER_ID + ") REFERENCES "
                        + TABLE_USERS + "(" + KEY_USER_ID + ")"
                        + ")";
                db.execSQL(CREATE_REGISTERED_PERSONNEL_TABLE);
                insertSamplePersonnel(db);
                Log.d(TAG, "Successfully upgraded to version 5");
            } catch (Exception e) {
                Log.e(TAG, "Error upgrading to version 5", e);
                recreateDatabase(db);
            }
        }

        // Upgrade from version 5 to 6 (add vault overrides table)
        if (oldVersion < 6) {
            try {
                String CREATE_VAULT_OVERRIDES_TABLE = "CREATE TABLE " + TABLE_VAULT_OVERRIDES + "("
                        + KEY_OVERRIDE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + KEY_OVERRIDE_VAULT_TYPE + " TEXT NOT NULL,"
                        + KEY_OVERRIDE_ENTRY_POINT + " TEXT NOT NULL,"
                        + KEY_OVERRIDE_STATUS + " TEXT DEFAULT 'ACTIVE',"
                        + KEY_OVERRIDE_ACTIVATED_AT + " DATETIME DEFAULT CURRENT_TIMESTAMP,"
                        + KEY_OVERRIDE_DEACTIVATED_AT + " DATETIME,"
                        + KEY_OVERRIDE_CUSTODIAN_1_ID + " TEXT,"
                        + KEY_OVERRIDE_CUSTODIAN_2_ID + " TEXT,"
                        + KEY_OVERRIDE_CUSTODIAN_3_ID + " TEXT,"
                        + KEY_OVERRIDE_DEACTIVATED_BY + " TEXT"
                        + ")";
                db.execSQL(CREATE_VAULT_OVERRIDES_TABLE);
                Log.d(TAG, "Successfully upgraded to version 6");
            } catch (Exception e) {
                Log.e(TAG, "Error upgrading to version 6", e);
                recreateDatabase(db);
            }
        }

        // Upgrade from version 6 to 7 (add profile_id column to vault_overrides)
        if (oldVersion < 7) {
            try {
                db.execSQL("ALTER TABLE " + TABLE_VAULT_OVERRIDES + " ADD COLUMN " + KEY_OVERRIDE_PROFILE_ID + " TEXT");
                Log.d(TAG, "Successfully upgraded to version 7 - Added profile_id column");
            } catch (Exception e) {
                Log.e(TAG, "Error upgrading to version 7", e);
                recreateDatabase(db);
            }
        }

        // Upgrade from version 7 to 8 (add synced_fingerprints table)
        if (oldVersion < 8) {
            try {
                String CREATE_SYNCED_FINGERPRINTS_TABLE = "CREATE TABLE " + TABLE_SYNCED_FINGERPRINTS + "("
                        + KEY_SYNCED_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + KEY_SYNCED_API_ID + " INTEGER UNIQUE NOT NULL,"
                        + KEY_SYNCED_EMPLOYEE_NUMBER + " TEXT NOT NULL,"
                        + KEY_SYNCED_USERNAME + " TEXT NOT NULL,"
                        + KEY_SYNCED_NAME + " TEXT NOT NULL,"
                        + KEY_SYNCED_TEMPLATE_DATA + " TEXT NOT NULL,"
                        + KEY_SYNCED_LEFT_RIGHT + " INTEGER,"
                        + KEY_SYNCED_FINGER_INDEX + " INTEGER,"
                        + KEY_SYNCED_FINGER_TYPE + " TEXT,"
                        + KEY_SYNCED_ENROLLED_TO_SCANNER + " INTEGER DEFAULT 0,"
                        + KEY_SYNCED_AT + " DATETIME DEFAULT CURRENT_TIMESTAMP"
                        + ")";
                db.execSQL(CREATE_SYNCED_FINGERPRINTS_TABLE);
                Log.d(TAG, "Successfully upgraded to version 8 - Added synced_fingerprints table");
            } catch (Exception e) {
                Log.e(TAG, "Error upgrading to version 8", e);
                recreateDatabase(db);
            }
        }

        // Upgrade from version 8 to 9 (change API ID to TEXT for ULID support)
        if (oldVersion < 9) {
            try {
                // Drop old table and recreate with new schema
                db.execSQL("DROP TABLE IF EXISTS " + TABLE_SYNCED_FINGERPRINTS);
                String CREATE_SYNCED_FINGERPRINTS_TABLE = "CREATE TABLE " + TABLE_SYNCED_FINGERPRINTS + "("
                        + KEY_SYNCED_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + KEY_SYNCED_API_ID + " TEXT UNIQUE NOT NULL,"
                        + KEY_SYNCED_SCANNER_ID + " INTEGER UNIQUE,"
                        + KEY_SYNCED_EMPLOYEE_NUMBER + " TEXT NOT NULL,"
                        + KEY_SYNCED_USERNAME + " TEXT NOT NULL,"
                        + KEY_SYNCED_NAME + " TEXT NOT NULL,"
                        + KEY_SYNCED_TEMPLATE_DATA + " TEXT NOT NULL,"
                        + KEY_SYNCED_LEFT_RIGHT + " INTEGER,"
                        + KEY_SYNCED_FINGER_INDEX + " INTEGER,"
                        + KEY_SYNCED_FINGER_TYPE + " TEXT,"
                        + KEY_SYNCED_ENROLLED_TO_SCANNER + " INTEGER DEFAULT 0,"
                        + KEY_SYNCED_AT + " DATETIME DEFAULT CURRENT_TIMESTAMP"
                        + ")";
                db.execSQL(CREATE_SYNCED_FINGERPRINTS_TABLE);
                Log.d(TAG, "Successfully upgraded to version 9 - Updated synced_fingerprints for ULID support");
            } catch (Exception e) {
                Log.e(TAG, "Error upgrading to version 9", e);
                recreateDatabase(db);
            }
        }

        // Upgrade from version 9 to 10 (add role column to synced_fingerprints)
        if (oldVersion < 10) {
            try {
                db.execSQL("ALTER TABLE " + TABLE_SYNCED_FINGERPRINTS + " ADD COLUMN " + KEY_SYNCED_ROLE + " TEXT");
                Log.d(TAG, "Successfully upgraded to version 10 - Added role column to synced_fingerprints");
            } catch (Exception e) {
                Log.e(TAG, "Error upgrading to version 10", e);
                recreateDatabase(db);
            }
        }

        // Upgrade from version 12 to 13 (add is_allowed_override column to synced_fingerprints)
        if (oldVersion < 13) {
            try {
                db.execSQL("ALTER TABLE " + TABLE_SYNCED_FINGERPRINTS + " ADD COLUMN " + KEY_SYNCED_IS_ALLOWED_OVERRIDE + " INTEGER DEFAULT 0");
                Log.d(TAG, "Successfully upgraded to version 13 - Added is_allowed_override column to synced_fingerprints");
            } catch (Exception e) {
                Log.e(TAG, "Error upgrading to version 13", e);
                recreateDatabase(db);
            }
        }

        // Upgrade from version 10 to 11 (change template_data from TEXT to BLOB)
        if (oldVersion < 11) {
            try {
                // SQLite doesn't support ALTER COLUMN, so we need to recreate the table
                Log.d(TAG, "Upgrading to version 11 - Changing template_data to BLOB...");

                // Drop old table (data will be re-synced from server)
                db.execSQL("DROP TABLE IF EXISTS " + TABLE_SYNCED_FINGERPRINTS);

                // Create new table with BLOB column
                String CREATE_SYNCED_FINGERPRINTS_TABLE = "CREATE TABLE " + TABLE_SYNCED_FINGERPRINTS + "("
                        + KEY_SYNCED_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + KEY_SYNCED_API_ID + " TEXT UNIQUE NOT NULL,"
                        + KEY_SYNCED_SCANNER_ID + " INTEGER UNIQUE,"
                        + KEY_SYNCED_EMPLOYEE_NUMBER + " TEXT NOT NULL,"
                        + KEY_SYNCED_USERNAME + " TEXT NOT NULL,"
                        + KEY_SYNCED_NAME + " TEXT NOT NULL,"
                        + KEY_SYNCED_ROLE + " TEXT,"
                        + KEY_SYNCED_TEMPLATE_DATA + " BLOB NOT NULL,"     // Changed to BLOB
                        + KEY_SYNCED_LEFT_RIGHT + " INTEGER,"
                        + KEY_SYNCED_FINGER_INDEX + " INTEGER,"
                        + KEY_SYNCED_FINGER_TYPE + " TEXT,"
                        + KEY_SYNCED_ENROLLED_TO_SCANNER + " INTEGER DEFAULT 0,"
                        + KEY_SYNCED_AT + " DATETIME DEFAULT CURRENT_TIMESTAMP"
                        + ")";
                db.execSQL(CREATE_SYNCED_FINGERPRINTS_TABLE);

                Log.d(TAG, "Successfully upgraded to version 11 - template_data is now BLOB");
                Log.d(TAG, "Note: Synced fingerprints cleared - please re-sync from server");
            } catch (Exception e) {
                Log.e(TAG, "Error upgrading to version 11", e);
                recreateDatabase(db);
            }
        }
    }

    /**
     * Helper method to recreate database if upgrade fails
     */
    private void recreateDatabase(SQLiteDatabase db) {
        Log.w(TAG, "Recreating database due to upgrade failure");
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_USERS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_ATTENDANCE);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_REGISTERED_PERSONNEL);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_VAULT_OVERRIDES);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SYNCED_FINGERPRINTS);
        onCreate(db);
    }

    /**
     * Reset the entire database - USE WITH CAUTION
     * This will delete all data and recreate the database with sample personnel
     * Call this method to clear all enrolled fingerprints and start fresh
     */
    public void resetDatabase() {
        SQLiteDatabase db = this.getWritableDatabase();
        Log.w(TAG, "RESETTING DATABASE - All data will be deleted!");
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_USERS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_ATTENDANCE);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_REGISTERED_PERSONNEL);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_VAULT_OVERRIDES);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SYNCED_FINGERPRINTS);
        onCreate(db);
        db.close();
        Log.d(TAG, "Database reset complete");
    }

    // =================== ADMIN USER METHODS ===================

    /**
     * Create an admin user if one doesn't exist
     * This is typically called when app starts for the first time
     */
    public void initializeAdminUser(String staffId, String password) {
        SQLiteDatabase db = this.getWritableDatabase();

        // Check if admin user already exists
        String query = "SELECT " + KEY_USER_ID + " FROM " + TABLE_USERS + " WHERE " + KEY_STAFF_ID + " = ?";
        Cursor cursor = db.rawQuery(query, new String[]{staffId});

        if (cursor.getCount() == 0) {
            // Admin user doesn't exist, create one
            ContentValues values = new ContentValues();
            values.put(KEY_IC_NUMBER, "ADMIN000000000");        // Default IC for admin
            values.put(KEY_STAFF_ID, staffId);                  // Admin staff ID
            values.put(KEY_NAME, "System Administrator");       // Admin name
            values.put(KEY_DEPARTMENT, "IT");                   // Admin department
            values.put(KEY_PASSWORD, password);                 // Admin password
            values.put(KEY_CREATED_AT, getCurrentDateTime());   // Creation timestamp
            values.put(KEY_UPDATED_AT, getCurrentDateTime());   // Update timestamp
            values.put(KEY_IS_ACTIVE, 1);                       // Active
            values.put(KEY_IS_ADMIN, 1);                        // Is admin

            long result = db.insert(TABLE_USERS, null, values);
            Log.d(TAG, "Admin user created with ID: " + result);
        } else {
            Log.d(TAG, "Admin user already exists");
        }

        cursor.close();
        db.close();
    }

    /**
     * Check if admin login credentials are valid
     */
    public boolean authenticateAdmin(String staffId, String password) {
        SQLiteDatabase db = this.getReadableDatabase();

        // Query for admin user with matching credentials
        String query = "SELECT " + KEY_USER_ID + " FROM " + TABLE_USERS
                + " WHERE " + KEY_STAFF_ID + " = ? AND " + KEY_PASSWORD + " = ? AND " + KEY_IS_ACTIVE + " = 1";

        Cursor cursor = db.rawQuery(query, new String[]{staffId, password});
        boolean isAuthenticated = cursor.getCount() > 0;

        cursor.close();
        db.close();

        return isAuthenticated;
    }

    // =================== USER MANAGEMENT METHODS ===================

    /**
     * Insert a new user with fingerprint data and scanner IDs
     * This is called from FingerprintEnrollmentActivity after successful enrollment
     */
    public long insertUserWithScannerMapping(String icNumber, String staffId, String name, String department,
                                             byte[] leftFingerprint, byte[] rightFingerprint,
                                             int leftScannerUserId, int rightScannerUserId,
                                             byte[] leftFingerprint2, byte[] rightFingerprint2,
                                             int leftScannerUserId2, int rightScannerUserId2,
                                             boolean isSupervisor) {

        Log.d(TAG, "=== START insertUserWithScannerMapping ===");
        Log.d(TAG, "IC Number: " + icNumber);
        Log.d(TAG, "Staff ID: " + staffId);
        Log.d(TAG, "Name: " + name);
        Log.d(TAG, "Department: " + department);
        Log.d(TAG, "Is Supervisor: " + isSupervisor);
        Log.d(TAG, "Left finger 1: " + (leftFingerprint != null ? leftFingerprint.length + " bytes" : "null"));
        Log.d(TAG, "Right finger 2: " + (rightFingerprint != null ? rightFingerprint.length + " bytes" : "null"));
        Log.d(TAG, "Left finger 3: " + (leftFingerprint2 != null ? leftFingerprint2.length + " bytes" : "null"));
        Log.d(TAG, "Right finger 4: " + (rightFingerprint2 != null ? rightFingerprint2.length + " bytes" : "null"));

        SQLiteDatabase db = null;
        long result = -1;

        try {
            db = this.getWritableDatabase();
            Log.d(TAG, "✓ Got writable database");

            // Check if database is really writable
            if (db.isReadOnly()) {
                Log.e(TAG, "❌ DATABASE IS READ-ONLY!");
                return -1;
            }

            // Prepare data for insertion
            ContentValues values = new ContentValues();
            values.put(KEY_IC_NUMBER, icNumber);
            values.put(KEY_STAFF_ID, staffId);
            values.put(KEY_NAME, name);
            values.put(KEY_DEPARTMENT, department);
            values.put(KEY_LEFT_FINGERPRINT, leftFingerprint);
            values.put(KEY_RIGHT_FINGERPRINT, rightFingerprint);
            values.put(KEY_LEFT_SCANNER_ID, leftScannerUserId);
            values.put(KEY_RIGHT_SCANNER_ID, rightScannerUserId);
            values.put(KEY_LEFT_FINGERPRINT_2, leftFingerprint2);
            values.put(KEY_RIGHT_FINGERPRINT_2, rightFingerprint2);
            values.put(KEY_LEFT_SCANNER_ID_2, leftScannerUserId2);
            values.put(KEY_RIGHT_SCANNER_ID_2, rightScannerUserId2);
            values.put(KEY_CREATED_AT, getCurrentDateTime());
            values.put(KEY_UPDATED_AT, getCurrentDateTime());
            values.put(KEY_IS_ACTIVE, 1);
            values.put(KEY_IS_ADMIN, isSupervisor ? 1 : 0);

            Log.d(TAG, "✓ ContentValues prepared");

            // Insert into database
            result = db.insert(TABLE_USERS, null, values);

            if (result == -1) {
                Log.e(TAG, "❌ INSERT FAILED! Result: -1");
                Log.e(TAG, "This usually means:");
                Log.e(TAG, "1. UNIQUE constraint violation (IC Number or Staff ID already exists)");
                Log.e(TAG, "2. NOT NULL constraint violation");
                Log.e(TAG, "3. Database corruption");

                // Check if user already exists
                Cursor checkCursor = db.rawQuery(
                        "SELECT " + KEY_IC_NUMBER + ", " + KEY_STAFF_ID + " FROM " + TABLE_USERS +
                                " WHERE " + KEY_IC_NUMBER + " = ? OR " + KEY_STAFF_ID + " = ?",
                        new String[]{icNumber, staffId}
                );

                if (checkCursor.getCount() > 0) {
                    checkCursor.moveToFirst();
                    String existingIC = checkCursor.getString(0);
                    String existingStaffID = checkCursor.getString(1);
                    Log.e(TAG, "❌ DUPLICATE FOUND! Existing IC: " + existingIC + ", Existing Staff ID: " + existingStaffID);
                } else {
                    Log.e(TAG, "❌ No duplicate found, must be another constraint issue");
                }
                checkCursor.close();
            } else {
                String userType = isSupervisor ? "Supervisor" : "User";
                Log.d(TAG, "✅ " + userType + " inserted successfully! Row ID: " + result);
                Log.d(TAG, "IC Number: " + icNumber + ", Staff ID: " + staffId);
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ EXCEPTION during insertUserWithScannerMapping", e);
            Log.e(TAG, "Exception message: " + e.getMessage());
            Log.e(TAG, "Exception class: " + e.getClass().getName());
            e.printStackTrace();
            result = -1;
        } finally {
            if (db != null) {
                db.close();
                Log.d(TAG, "Database closed");
            }
        }

        Log.d(TAG, "=== END insertUserWithScannerMapping, result: " + result + " ===");
        return result;
    }

    // Add this new method to check if a user is a supervisor

    /**
     * Check if a user is a supervisor by their staff ID
     */
    public boolean isSupervisor(String staffId) {
        SQLiteDatabase db = this.getReadableDatabase();

        String query = "SELECT " + KEY_IS_ADMIN + " FROM " + TABLE_USERS +
                " WHERE " + KEY_STAFF_ID + " = ? AND " + KEY_IS_ACTIVE + " = 1";

        Cursor cursor = db.rawQuery(query, new String[]{staffId});

        boolean result = false;
        if (cursor.moveToFirst()) {
            result = cursor.getInt(0) == 1;
        }

        cursor.close();
        db.close();

        return result;
    }

    // This will be useful for ManualOverrideActivity verification

    /**
     * Get supervisor user by staff ID
     * Returns null if user doesn't exist or is not a supervisor
     */
    public User getSupervisorByStaffId(String staffId) {
        SQLiteDatabase db = this.getReadableDatabase();

        String query = "SELECT * FROM " + TABLE_USERS +
                " WHERE " + KEY_STAFF_ID + " = ? AND " +
                KEY_IS_ADMIN + " = 1 AND " +
                KEY_IS_ACTIVE + " = 1";

        Cursor cursor = db.rawQuery(query, new String[]{staffId});

        User user = null;
        if (cursor.moveToFirst()) {
            user = cursorToUser(cursor);
        }

        cursor.close();
        db.close();

        return user;
    }

    /**
     * Check if a user already exists by IC number or Staff ID
     * This prevents duplicate users
     */
    public boolean userExists(String icNumber, String staffId) {
        SQLiteDatabase db = this.getReadableDatabase();

        String query = "SELECT " + KEY_USER_ID + " FROM " + TABLE_USERS
                + " WHERE " + KEY_IC_NUMBER + " = ? OR " + KEY_STAFF_ID + " = ?";

        Cursor cursor = db.rawQuery(query, new String[]{icNumber, staffId});
        boolean exists = cursor.getCount() > 0;

        cursor.close();
        db.close();

        return exists;
    }

    /**
     * CRITICAL METHOD: Find user by scanner ID
     * This is called from ActivateVaultActivity when a fingerprint is identified
     * The scanner returns an ID, and we need to find which user it belongs to
     */
    public User getUserByScannerUserId(int scannerUserId) {
        SQLiteDatabase db = this.getReadableDatabase();

        // Don't search for scanner ID 0 or negative values (invalid IDs)
        if (scannerUserId <= 0) {
            Log.w(TAG, "Invalid scanner user ID: " + scannerUserId);
            db.close();
            return null;
        }

        // Look for user with matching scanner ID in either left or right finger
        String query = "SELECT * FROM " + TABLE_USERS + " WHERE ("
                + KEY_LEFT_SCANNER_ID + " = ? OR "
                + KEY_RIGHT_SCANNER_ID + " = ? OR "
                + KEY_LEFT_SCANNER_ID_2 + " = ? OR "
                + KEY_RIGHT_SCANNER_ID_2 + " = ?) AND "
                + KEY_IS_ACTIVE + " = 1";

        Cursor cursor = db.rawQuery(query, new String[]{
                String.valueOf(scannerUserId),
                String.valueOf(scannerUserId),
                String.valueOf(scannerUserId),
                String.valueOf(scannerUserId)
        });

        User user = null;
        if (cursor.moveToFirst()) {
            user = cursorToUser(cursor);
            // Determine which finger was used
            if (user.getLeftScannerUserId() == scannerUserId) {
                user.setLastFingerUsed("left");
            } else if (user.getRightScannerUserId() == scannerUserId) {
                user.setLastFingerUsed("right");
            }
            else if (user.getLeftScannerUserId2() == scannerUserId) {
                user.setLastFingerUsed("right");
            }
            else if (user.getRightScannerUserId2()== scannerUserId) {
                user.setLastFingerUsed("right");
            }
        }

        cursor.close();
        db.close();

        return user;
    }

    /**
     * Get user by IC number
     */
    public User getUserByIcNumber(String icNumber) {
        SQLiteDatabase db = this.getReadableDatabase();

        String query = "SELECT * FROM " + TABLE_USERS + " WHERE " + KEY_IC_NUMBER + " = ? AND " + KEY_IS_ACTIVE + " = 1";
        Cursor cursor = db.rawQuery(query, new String[]{icNumber});

        User user = null;
        if (cursor.moveToFirst()) {
            user = cursorToUser(cursor);
        }

        cursor.close();
        db.close();

        return user;
    }

    /**
     * Get user by Staff ID
     */
    public User getUserByStaffId(String staffId) {
        SQLiteDatabase db = this.getReadableDatabase();

        String query = "SELECT * FROM " + TABLE_USERS + " WHERE " + KEY_STAFF_ID + " = ? AND " + KEY_IS_ACTIVE + " = 1";
        Cursor cursor = db.rawQuery(query, new String[]{staffId});

        User user = null;
        if (cursor.moveToFirst()) {
            user = cursorToUser(cursor);
        }

        cursor.close();
        db.close();

        return user;
    }

    /**
     * Get user by database ID
     */
    public User getUserById(int userId) {
        SQLiteDatabase db = this.getReadableDatabase();

        String query = "SELECT * FROM " + TABLE_USERS + " WHERE " + KEY_USER_ID + " = ? AND " + KEY_IS_ACTIVE + " = 1";
        Cursor cursor = db.rawQuery(query, new String[]{String.valueOf(userId)});

        User user = null;
        if (cursor.moveToFirst()) {
            user = cursorToUser(cursor);
        }

        cursor.close();
        db.close();

        return user;
    }

    /**
     * Get all active users
     */
    public List<User> getAllActiveUsers() {
        List<User> users = new ArrayList<>();

        String query = "SELECT * FROM " + TABLE_USERS + " WHERE " + KEY_IS_ACTIVE + " = 1 ORDER BY " + KEY_NAME;
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(query, null);

        if (cursor.moveToFirst()) {
            do {
                users.add(cursorToUser(cursor));
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();

        return users;
    }

    // =================== REGISTERED PERSONNEL METHODS ===================

    /**
     * Get all unenrolled personnel by role (ADMIN or CUSTODIAN)
     * Only returns personnel who haven't been fingerprint enrolled yet
     */
    public List<RegisteredPersonnel> getUnenrolledPersonnelByRole(String role) {
        List<RegisteredPersonnel> personnel = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        String query = "SELECT * FROM " + TABLE_REGISTERED_PERSONNEL
                + " WHERE " + KEY_PERSONNEL_ROLE + " = ? AND " + KEY_PERSONNEL_IS_ENROLLED + " = 0"
                + " ORDER BY " + KEY_PERSONNEL_NAME;

        Cursor cursor = db.rawQuery(query, new String[]{role});

        if (cursor.moveToFirst()) {
            do {
                personnel.add(cursorToPersonnel(cursor));
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();

        return personnel;
    }

    /**
     * Get all admins who haven't been enrolled yet
     */
    public List<RegisteredPersonnel> getUnenrolledAdmins() {
        return getUnenrolledPersonnelByRole("ADMIN");
    }

    /**
     * Check if any admin has enrolled fingerprints
     * This is used to determine if the app should force first-time admin enrollment
     * @return true if at least one admin has fingerprints enrolled, false otherwise
     */
    // might need to check if it's possible to check if it is possible to check admin on api instead of database
    public boolean hasAnyAdminWithFingerprint() {
        SQLiteDatabase db = this.getReadableDatabase();

        // First, let's see ALL admins in the database
        String debugQuery = "SELECT " + KEY_NAME + ", " + KEY_STAFF_ID + ", " + KEY_IS_ACTIVE + ", "
                + KEY_LEFT_SCANNER_ID + ", " + KEY_RIGHT_SCANNER_ID + ", "
                + KEY_LEFT_SCANNER_ID_2 + ", " + KEY_RIGHT_SCANNER_ID_2
                + " FROM " + TABLE_USERS
                + " WHERE " + KEY_IS_ADMIN + " = 1";

        Cursor debugCursor = db.rawQuery(debugQuery, null);
        Log.d(TAG, "=== ALL ADMIN USERS IN DATABASE ===");
        if (debugCursor.moveToFirst()) {
            do {
                String name = debugCursor.getString(0);
                String staffId = debugCursor.getString(1);
                int isActive = debugCursor.getInt(2);
                int leftId = debugCursor.getInt(3);
                int rightId = debugCursor.getInt(4);
                int leftId2 = debugCursor.getInt(5);
                int rightId2 = debugCursor.getInt(6);
                Log.d(TAG, "Admin: " + name + " (ID: " + staffId + ")");
                Log.d(TAG, "  Active: " + isActive);
                Log.d(TAG, "  Scanner IDs - Left1: " + leftId + ", Right1: " + rightId + ", Left2: " + leftId2 + ", Right2: " + rightId2);
            } while (debugCursor.moveToNext());
        } else {
            Log.d(TAG, "NO ADMINS FOUND IN DATABASE AT ALL!");
        }
        debugCursor.close();
        Log.d(TAG, "=================================");

        // Query for users who are admins AND have at least one scanner ID (meaning fingerprint enrolled)
        String query = "SELECT COUNT(*) FROM " + TABLE_USERS
                + " WHERE " + KEY_IS_ADMIN + " = 1"
                + " AND " + KEY_IS_ACTIVE + " = 1"
                + " AND (" + KEY_LEFT_SCANNER_ID + " > 0"
                + " OR " + KEY_RIGHT_SCANNER_ID + " > 0"
                + " OR " + KEY_LEFT_SCANNER_ID_2 + " > 0"
                + " OR " + KEY_RIGHT_SCANNER_ID_2 + " > 0)";

        Cursor cursor = db.rawQuery(query, null);

        int count = 0;
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0);
        }

        cursor.close();
        db.close();

        Log.d(TAG, "Admin count with fingerprints: " + count);
        return count > 0;
    }

    /**
     * Update a user's admin status
     * @param staffId The staff ID of the user
     * @param isAdmin Whether the user should be an admin (true) or not (false)
     * @return Number of rows updated
     */
    public int updateUserAdminStatus(String staffId, boolean isAdmin) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(KEY_IS_ADMIN, isAdmin ? 1 : 0);
        values.put(KEY_UPDATED_AT, getCurrentDateTime());

        int rowsAffected = db.update(TABLE_USERS, values,
                KEY_STAFF_ID + " = ?", new String[]{staffId});

        db.close();

        Log.d(TAG, "Updated admin status for " + staffId + " to " + isAdmin + " (" + rowsAffected + " rows)");
        return rowsAffected;
    }

    /**
     * Update all enrolled users to be admins
     * UTILITY METHOD: Use this to fix existing data if users were enrolled without admin flag
     * @return Number of users updated
     */
    public int updateAllEnrolledUsersToAdmin() {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(KEY_IS_ADMIN, 1);
        values.put(KEY_UPDATED_AT, getCurrentDateTime());

        // Update all users who have at least one fingerprint enrolled
        String whereClause = "(" + KEY_LEFT_SCANNER_ID + " > 0 OR "
                + KEY_RIGHT_SCANNER_ID + " > 0 OR "
                + KEY_LEFT_SCANNER_ID_2 + " > 0 OR "
                + KEY_RIGHT_SCANNER_ID_2 + " > 0) AND "
                + KEY_IS_ACTIVE + " = 1";

        int rowsAffected = db.update(TABLE_USERS, values, whereClause, null);

        db.close();

        Log.i(TAG, "Updated " + rowsAffected + " enrolled users to admin status");
        return rowsAffected;
    }

    /**
     * Count active admins (to prevent deleting the last admin)
     * @return Number of active admin users
     */
    public int countActiveAdmins() {
        SQLiteDatabase db = this.getReadableDatabase();

        String query = "SELECT COUNT(*) FROM " + TABLE_USERS
                + " WHERE " + KEY_IS_ADMIN + " = 1 AND " + KEY_IS_ACTIVE + " = 1";

        Cursor cursor = db.rawQuery(query, null);
        int count = 0;
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0);
        }

        cursor.close();
        db.close();

        Log.d(TAG, "Active admin count: " + count);
        return count;
    }

    /**
     * Soft delete a user (set is_active = 0)
     * Also updates registered_personnel table if the user was pre-registered
     * @param employeeId Employee ID / Staff ID
     * @return true if deletion succeeded
     */
    public boolean softDeleteUser(String employeeId) {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor userCursor = null;
        Cursor adminCursor = null;

        try {
            // Get user info before deleting (for logging) - query directly to avoid closing db
            String userQuery = "SELECT * FROM " + TABLE_USERS + " WHERE " + KEY_STAFF_ID + " = ? AND " + KEY_IS_ACTIVE + " = 1";
            userCursor = db.rawQuery(userQuery, new String[]{employeeId});

            if (!userCursor.moveToFirst()) {
                Log.i(TAG, "User not found in local users table: " + employeeId + " (This is expected for synced users from API)");
                return false;
            }

            // Extract user info from cursor
            String userName = userCursor.getString(userCursor.getColumnIndex(KEY_NAME));
            int isAdmin = userCursor.getInt(userCursor.getColumnIndex(KEY_IS_ADMIN));
            boolean userIsAdmin = (isAdmin == 1);

            Log.d(TAG, "╔═══════════════════════════════════════════");
            Log.d(TAG, "║ SOFT DELETE USER FROM DATABASE");
            Log.d(TAG, "╠═══════════════════════════════════════════");
            Log.d(TAG, "║ Employee ID: " + employeeId);
            Log.d(TAG, "║ Name: " + userName);
            Log.d(TAG, "║ Is Admin: " + userIsAdmin);
            Log.d(TAG, "╚═══════════════════════════════════════════");

            // Prevent deleting last admin - query directly to avoid closing db
            if (userIsAdmin) {
                String adminQuery = "SELECT COUNT(*) FROM " + TABLE_USERS
                        + " WHERE " + KEY_IS_ADMIN + " = 1 AND " + KEY_IS_ACTIVE + " = 1";
                adminCursor = db.rawQuery(adminQuery, null);
                int activeAdminCount = 0;
                if (adminCursor.moveToFirst()) {
                    activeAdminCount = adminCursor.getInt(0);
                }

                Log.d(TAG, "Active admin count: " + activeAdminCount);

                if (activeAdminCount <= 1) {
                    Log.e(TAG, "❌ Cannot delete last admin!");
                    return false;
                }
            }

            // Update users table - set is_active = 0
            ContentValues userValues = new ContentValues();
            userValues.put(KEY_IS_ACTIVE, 0);
            userValues.put(KEY_UPDATED_AT, getCurrentDateTime());

            int rowsAffected = db.update(TABLE_USERS, userValues,
                    KEY_STAFF_ID + " = ?", new String[]{employeeId});

            if (rowsAffected > 0) {
                // Update registered_personnel table if exists
                ContentValues personnelValues = new ContentValues();
                personnelValues.put(KEY_PERSONNEL_IS_ENROLLED, 0);
                personnelValues.putNull(KEY_PERSONNEL_ENROLLED_USER_ID);

                db.update(TABLE_REGISTERED_PERSONNEL, personnelValues,
                        KEY_PERSONNEL_EMPLOYEE_ID + " = ?", new String[]{employeeId});

                Log.i(TAG, "✓ User soft deleted successfully");
                return true;
            } else {
                Log.e(TAG, "❌ No rows affected during soft delete");
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error during soft delete", e);
            return false;
        } finally {
            if (userCursor != null) {
                userCursor.close();
            }
            if (adminCursor != null) {
                adminCursor.close();
            }
            db.close();
        }
    }

    /**
     * Get all custodians who haven't been enrolled yet
     */
    public List<RegisteredPersonnel> getUnenrolledCustodians() {
        return getUnenrolledPersonnelByRole("CUSTODIAN");
    }

    /**
     * Get a specific personnel by employee ID
     */
    public RegisteredPersonnel getPersonnelByEmployeeId(String employeeId) {
        SQLiteDatabase db = this.getReadableDatabase();

        String query = "SELECT * FROM " + TABLE_REGISTERED_PERSONNEL
                + " WHERE " + KEY_PERSONNEL_EMPLOYEE_ID + " = ?";

        Cursor cursor = db.rawQuery(query, new String[]{employeeId});

        RegisteredPersonnel personnel = null;
        if (cursor.moveToFirst()) {
            personnel = cursorToPersonnel(cursor);
        }

        cursor.close();
        db.close();

        return personnel;
    }

    /**
     * Mark a personnel as enrolled after successful fingerprint registration
     * @param employeeId The employee ID of the personnel
     * @param enrolledUserId The user ID from the users table after enrollment
     */
    public boolean markPersonnelAsEnrolled(String employeeId, int enrolledUserId) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(KEY_PERSONNEL_IS_ENROLLED, 1);
        values.put(KEY_PERSONNEL_ENROLLED_USER_ID, enrolledUserId);

        int rowsAffected = db.update(TABLE_REGISTERED_PERSONNEL, values,
                KEY_PERSONNEL_EMPLOYEE_ID + " = ?", new String[]{employeeId});

        db.close();

        Log.d(TAG, "Marked personnel " + employeeId + " as enrolled: " + (rowsAffected > 0));
        return rowsAffected > 0;
    }

    /**
     * Add a new pre-registered personnel (admin or custodian)
     */
    public long addRegisteredPersonnel(String employeeId, String name, String role) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(KEY_PERSONNEL_EMPLOYEE_ID, employeeId);
        values.put(KEY_PERSONNEL_NAME, name);
        values.put(KEY_PERSONNEL_ROLE, role);
        values.put(KEY_PERSONNEL_IS_ENROLLED, 0);

        long result = db.insert(TABLE_REGISTERED_PERSONNEL, null, values);
        db.close();

        Log.d(TAG, "Added registered personnel: " + name + " (" + role + ") with ID: " + result);
        return result;
    }

    /**
     * Convert database cursor to RegisteredPersonnel object
     */
    private RegisteredPersonnel cursorToPersonnel(Cursor cursor) {
        RegisteredPersonnel personnel = new RegisteredPersonnel();
        personnel.setId(getIntSafely(cursor, KEY_PERSONNEL_ID));
        personnel.setEmployeeId(getStringSafely(cursor, KEY_PERSONNEL_EMPLOYEE_ID));
        personnel.setName(getStringSafely(cursor, KEY_PERSONNEL_NAME));
        personnel.setRole(getStringSafely(cursor, KEY_PERSONNEL_ROLE));
        personnel.setEnrolled(getIntSafely(cursor, KEY_PERSONNEL_IS_ENROLLED) == 1);
        personnel.setEnrolledUserId(getIntSafely(cursor, KEY_PERSONNEL_ENROLLED_USER_ID));
        return personnel;
    }

    // =================== VAULT OVERRIDE METHODS ===================

    /**
     * Activate a vault override
     * @param vaultType "MAIN" or "DAY"
     * @param entryPoint "MAIN_GRILL", "COMPARTMENT_1", "COMPARTMENT_2", "COMPARTMENT_3"
     * @param custodian1Id Employee ID of first custodian
     * @param custodian2Id Employee ID of second custodian
     * @param custodian3Id Employee ID of third custodian
     * @return The override ID or -1 if failed
     */
    public long activateVaultOverride(String vaultType, String entryPoint,
                                       String custodian1Id, String custodian2Id, String custodian3Id) {
        // First, deactivate any existing active override for this vault/entry point
        deactivateExistingOverride(vaultType, entryPoint);

        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(KEY_OVERRIDE_VAULT_TYPE, vaultType);
        values.put(KEY_OVERRIDE_ENTRY_POINT, entryPoint);
        values.put(KEY_OVERRIDE_STATUS, "ACTIVE");
        values.put(KEY_OVERRIDE_CUSTODIAN_1_ID, custodian1Id);
        values.put(KEY_OVERRIDE_CUSTODIAN_2_ID, custodian2Id);
        values.put(KEY_OVERRIDE_CUSTODIAN_3_ID, custodian3Id);

        long result = db.insert(TABLE_VAULT_OVERRIDES, null, values);
        db.close();

        Log.d(TAG, "Vault override activated: " + vaultType + " - " + entryPoint + " (ID: " + result + ")");
        return result;
    }

    /**
     * Deactivate any existing active override for a vault/entry point
     */
    private void deactivateExistingOverride(String vaultType, String entryPoint) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(KEY_OVERRIDE_STATUS, "DEACTIVATED");
        values.put(KEY_OVERRIDE_DEACTIVATED_AT, getCurrentTimestamp());

        db.update(TABLE_VAULT_OVERRIDES, values,
                KEY_OVERRIDE_VAULT_TYPE + " = ? AND " + KEY_OVERRIDE_ENTRY_POINT + " = ? AND " + KEY_OVERRIDE_STATUS + " = ?",
                new String[]{vaultType, entryPoint, "ACTIVE"});
        db.close();
    }

    /**
     * Deactivate a vault override by ID
     * @param overrideId The override ID
     * @param deactivatedBy Employee ID of custodian who deactivated
     * @return true if successful
     */
    public boolean deactivateVaultOverride(int overrideId, String deactivatedBy) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(KEY_OVERRIDE_STATUS, "DEACTIVATED");
        values.put(KEY_OVERRIDE_DEACTIVATED_AT, getCurrentTimestamp());
        values.put(KEY_OVERRIDE_DEACTIVATED_BY, deactivatedBy);

        int rowsAffected = db.update(TABLE_VAULT_OVERRIDES, values,
                KEY_OVERRIDE_ID + " = ?", new String[]{String.valueOf(overrideId)});
        db.close();

        Log.d(TAG, "Vault override deactivated: ID " + overrideId + " by " + deactivatedBy);
        return rowsAffected > 0;
    }

    /**
     * Deactivate all active vault overrides at cutoff time (8PM)
     * Called when interim cutoff time is reached
     * @return Number of overrides deactivated
     */
    public int deactivateAllActiveOverridesAtCutoff() {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(KEY_OVERRIDE_STATUS, "DEACTIVATED");
        values.put(KEY_OVERRIDE_DEACTIVATED_AT, getCurrentTimestamp());
        values.put(KEY_OVERRIDE_DEACTIVATED_BY, "System - Cutoff Time");

        int rowsAffected = db.update(TABLE_VAULT_OVERRIDES, values,
                KEY_OVERRIDE_STATUS + " = ?", new String[]{"ACTIVE"});
        db.close();

        Log.d(TAG, "Cutoff time: Deactivated " + rowsAffected + " active vault overrides");
        return rowsAffected;
    }

    /**
     * Link PAC API profile ID to local override record
     * Called after PAC API responds with profile ID
     * @param overrideId Local database override ID
     * @param profileId PAC API profile ID (UUID)
     * @return True if update successful
     */
    public boolean linkOverrideToProfile(int overrideId, String profileId) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(KEY_OVERRIDE_PROFILE_ID, profileId);

        int rowsAffected = db.update(TABLE_VAULT_OVERRIDES, values,
                KEY_OVERRIDE_ID + " = ?", new String[]{String.valueOf(overrideId)});
        db.close();

        Log.d(TAG, "Linked override ID " + overrideId + " to profile ID: " + profileId);
        return rowsAffected > 0;
    }

    /**
     * Get PAC API profile ID for a local override record
     * Used when deactivating to send request to PAC API
     * @param overrideId Local database override ID
     * @return PAC API profile ID (UUID) or null if not linked
     */
    public String getProfileIdForOverride(int overrideId) {
        SQLiteDatabase db = this.getReadableDatabase();
        String profileId = null;

        String query = "SELECT " + KEY_OVERRIDE_PROFILE_ID + " FROM " + TABLE_VAULT_OVERRIDES
                + " WHERE " + KEY_OVERRIDE_ID + " = ?";
        Cursor cursor = db.rawQuery(query, new String[]{String.valueOf(overrideId)});

        if (cursor.moveToFirst()) {
            profileId = cursor.getString(cursor.getColumnIndexOrThrow(KEY_OVERRIDE_PROFILE_ID));
        }

        cursor.close();
        db.close();
        return profileId;
    }

    /**
     * Get the active override for a specific vault and entry point
     * @return VaultOverride object or null if no active override
     */
    public VaultOverride getActiveOverride(String vaultType, String entryPoint) {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT * FROM " + TABLE_VAULT_OVERRIDES
                + " WHERE " + KEY_OVERRIDE_VAULT_TYPE + " = ?"
                + " AND " + KEY_OVERRIDE_ENTRY_POINT + " = ?"
                + " AND " + KEY_OVERRIDE_STATUS + " = 'ACTIVE'"
                + " ORDER BY " + KEY_OVERRIDE_ACTIVATED_AT + " DESC LIMIT 1";

        Cursor cursor = db.rawQuery(query, new String[]{vaultType, entryPoint});

        VaultOverride override = null;
        if (cursor.moveToFirst()) {
            override = cursorToVaultOverride(cursor);
        }

        cursor.close();
        db.close();
        return override;
    }

    /**
     * Get all active overrides for a vault type (MAIN or DAY)
     */
    public List<VaultOverride> getActiveOverridesForVault(String vaultType) {
        List<VaultOverride> overrides = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        String query = "SELECT * FROM " + TABLE_VAULT_OVERRIDES
                + " WHERE " + KEY_OVERRIDE_VAULT_TYPE + " = ?"
                + " AND " + KEY_OVERRIDE_STATUS + " = 'ACTIVE'"
                + " ORDER BY " + KEY_OVERRIDE_ACTIVATED_AT + " DESC";

        Cursor cursor = db.rawQuery(query, new String[]{vaultType});

        if (cursor.moveToFirst()) {
            do {
                overrides.add(cursorToVaultOverride(cursor));
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();
        return overrides;
    }

    /**
     * Get all active overrides
     */
    public List<VaultOverride> getAllActiveOverrides() {
        List<VaultOverride> overrides = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        String query = "SELECT * FROM " + TABLE_VAULT_OVERRIDES
                + " WHERE " + KEY_OVERRIDE_STATUS + " = 'ACTIVE'"
                + " ORDER BY " + KEY_OVERRIDE_ACTIVATED_AT + " DESC";

        Cursor cursor = db.rawQuery(query, null);

        if (cursor.moveToFirst()) {
            do {
                overrides.add(cursorToVaultOverride(cursor));
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();
        return overrides;
    }

    /**
     * Get all vault overrides (both active and deactivated)
     */
    public List<VaultOverrideRecord> getAllVaultOverrides() {
        List<VaultOverrideRecord> records = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        String query = "SELECT * FROM " + TABLE_VAULT_OVERRIDES
                + " ORDER BY " + KEY_OVERRIDE_ACTIVATED_AT + " DESC LIMIT 100";

        Cursor cursor = db.rawQuery(query, null);

        if (cursor.moveToFirst()) {
            do {
                VaultOverrideRecord record = new VaultOverrideRecord();
                record.setId(getIntSafely(cursor, KEY_OVERRIDE_ID));
                record.setVaultType(getStringSafely(cursor, KEY_OVERRIDE_VAULT_TYPE));
                record.setEntryPoint(getStringSafely(cursor, KEY_OVERRIDE_ENTRY_POINT));
                record.setStatus(getStringSafely(cursor, KEY_OVERRIDE_STATUS));
                record.setProfileId(getStringSafely(cursor, KEY_OVERRIDE_PROFILE_ID));
                record.setActivatedAt(getStringSafely(cursor, KEY_OVERRIDE_ACTIVATED_AT));
                record.setDeactivatedAt(getStringSafely(cursor, KEY_OVERRIDE_DEACTIVATED_AT));
                record.setCustodian1Id(getStringSafely(cursor, KEY_OVERRIDE_CUSTODIAN_1_ID));
                record.setCustodian2Id(getStringSafely(cursor, KEY_OVERRIDE_CUSTODIAN_2_ID));
                record.setCustodian3Id(getStringSafely(cursor, KEY_OVERRIDE_CUSTODIAN_3_ID));
                record.setDeactivatedBy(getStringSafely(cursor, KEY_OVERRIDE_DEACTIVATED_BY));

                records.add(record);
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();
        return records;
    }

    /**
     * Check if an entry point has an active override
     */
    public boolean isOverrideActive(String vaultType, String entryPoint) {
        return getActiveOverride(vaultType, entryPoint) != null;
    }

    /**
     * Get current timestamp for database
     */
    private String getCurrentTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date());
    }

    /**
     * Convert cursor to VaultOverride object
     */
    private VaultOverride cursorToVaultOverride(Cursor cursor) {
        VaultOverride override = new VaultOverride();
        override.setId(getIntSafely(cursor, KEY_OVERRIDE_ID));
        override.setVaultType(getStringSafely(cursor, KEY_OVERRIDE_VAULT_TYPE));
        override.setEntryPoint(getStringSafely(cursor, KEY_OVERRIDE_ENTRY_POINT));
        override.setStatus(getStringSafely(cursor, KEY_OVERRIDE_STATUS));
        override.setProfileId(getStringSafely(cursor, KEY_OVERRIDE_PROFILE_ID));
        override.setActivatedAt(getStringSafely(cursor, KEY_OVERRIDE_ACTIVATED_AT));
        override.setDeactivatedAt(getStringSafely(cursor, KEY_OVERRIDE_DEACTIVATED_AT));
        override.setCustodian1Id(getStringSafely(cursor, KEY_OVERRIDE_CUSTODIAN_1_ID));
        override.setCustodian2Id(getStringSafely(cursor, KEY_OVERRIDE_CUSTODIAN_2_ID));
        override.setCustodian3Id(getStringSafely(cursor, KEY_OVERRIDE_CUSTODIAN_3_ID));
        override.setDeactivatedBy(getStringSafely(cursor, KEY_OVERRIDE_DEACTIVATED_BY));
        return override;
    }

    /**
     * Get all enrolled custodians (for fingerprint verification)
     */
    public List<RegisteredPersonnel> getEnrolledCustodians() {
        List<RegisteredPersonnel> personnel = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        String query = "SELECT * FROM " + TABLE_REGISTERED_PERSONNEL
                + " WHERE " + KEY_PERSONNEL_ROLE + " = 'CUSTODIAN' AND " + KEY_PERSONNEL_IS_ENROLLED + " = 1"
                + " ORDER BY " + KEY_PERSONNEL_NAME;

        Cursor cursor = db.rawQuery(query, null);

        if (cursor.moveToFirst()) {
            do {
                personnel.add(cursorToPersonnel(cursor));
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();
        return personnel;
    }

    // =================== ATTENDANCE METHODS ===================

    /**
     * Record attendance when user scans fingerprint
     */
    public long insertAttendance(int userId, String attendanceType, String fingerUsed) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(KEY_ATTENDANCE_USER_ID, userId);
        values.put(KEY_ATTENDANCE_TYPE, attendanceType);        // "check_in", "check_out", "vault_access", etc.
        values.put(KEY_ATTENDANCE_TIMESTAMP, getCurrentDateTime());
        values.put(KEY_FINGER_USED, fingerUsed);               // "left" or "right"

        long result = db.insert(TABLE_ATTENDANCE, null, values);
        db.close();

        Log.d(TAG, "Attendance record inserted with ID: " + result);
        return result;
    }

    /**
     * Get attendance records for a user on a specific date
     */
    public List<AttendanceRecord> getUserAttendance(int userId, String date) {
        List<AttendanceRecord> records = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        String query = "SELECT * FROM " + TABLE_ATTENDANCE
                + " WHERE " + KEY_ATTENDANCE_USER_ID + " = ? AND DATE(" + KEY_ATTENDANCE_TIMESTAMP + ") = ? "
                + " ORDER BY " + KEY_ATTENDANCE_TIMESTAMP + " DESC";

        Cursor cursor = db.rawQuery(query, new String[]{String.valueOf(userId), date});

        if (cursor.moveToFirst()) {
            do {
                AttendanceRecord record = new AttendanceRecord();
                record.setId(getIntSafely(cursor, KEY_ATTENDANCE_ID));
                record.setUserId(getIntSafely(cursor, KEY_ATTENDANCE_USER_ID));
                record.setAttendanceType(getStringSafely(cursor, KEY_ATTENDANCE_TYPE));
                record.setTimestamp(getStringSafely(cursor, KEY_ATTENDANCE_TIMESTAMP));
                record.setFingerUsed(getStringSafely(cursor, KEY_FINGER_USED));

                records.add(record);
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();

        return records;
    }

    /**
     * Get all attendance records
     */
    public List<AttendanceRecord> getAllAttendanceRecords() {
        List<AttendanceRecord> records = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        String query = "SELECT * FROM " + TABLE_ATTENDANCE
                + " ORDER BY " + KEY_ATTENDANCE_TIMESTAMP + " DESC LIMIT 100";

        Cursor cursor = db.rawQuery(query, null);

        if (cursor.moveToFirst()) {
            do {
                AttendanceRecord record = new AttendanceRecord();
                record.setId(getIntSafely(cursor, KEY_ATTENDANCE_ID));
                record.setUserId(getIntSafely(cursor, KEY_ATTENDANCE_USER_ID));
                record.setAttendanceType(getStringSafely(cursor, KEY_ATTENDANCE_TYPE));
                record.setTimestamp(getStringSafely(cursor, KEY_ATTENDANCE_TIMESTAMP));
                record.setFingerUsed(getStringSafely(cursor, KEY_FINGER_USED));

                records.add(record);
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();

        return records;
    }

    // =================== UTILITY METHODS ===================

    /**
     * Get total count of active users
     */
    public int getUserCount() {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT COUNT(*) FROM " + TABLE_USERS + " WHERE " + KEY_IS_ACTIVE + " = 1";
        Cursor cursor = db.rawQuery(query, null);

        int count = 0;
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0);
        }

        cursor.close();
        db.close();

        return count;
    }

    /**
     * Check if there are any enrolled users (either local or synced)
     * Used to determine if first-time setup is needed
     * @return true if there are any enrolled users, false if database is empty
     */
    public boolean hasAnyEnrolledUsers() {
        SQLiteDatabase db = this.getReadableDatabase();
        boolean hasUsers = false;

        try {
            // Check local users table
            String localUsersQuery = "SELECT COUNT(*) FROM " + TABLE_USERS + " WHERE " + KEY_IS_ACTIVE + " = 1";
            Cursor localCursor = db.rawQuery(localUsersQuery, null);

            int localCount = 0;
            if (localCursor.moveToFirst()) {
                localCount = localCursor.getInt(0);
            }
            localCursor.close();

            // Check synced fingerprints table
            String syncedQuery = "SELECT COUNT(*) FROM " + TABLE_SYNCED_FINGERPRINTS;
            Cursor syncedCursor = db.rawQuery(syncedQuery, null);

            int syncedCount = 0;
            if (syncedCursor.moveToFirst()) {
                syncedCount = syncedCursor.getInt(0);
            }
            syncedCursor.close();

            hasUsers = (localCount > 0 || syncedCount > 0);

            Log.d("DatabaseHelper", "hasAnyEnrolledUsers: local=" + localCount + ", synced=" + syncedCount + ", total=" + hasUsers);

        } catch (Exception e) {
            Log.e("DatabaseHelper", "Error checking for enrolled users", e);
            hasUsers = false;
        } finally {
            db.close();
        }

        return hasUsers;
    }

    /**
     * Convert database cursor to User object
     * This safely reads data from cursor and creates User object
     */
    private User cursorToUser(Cursor cursor) {
        User user = new User();

        user.setId(getIntSafely(cursor, KEY_USER_ID));
        user.setIcNumber(getStringSafely(cursor, KEY_IC_NUMBER));
        user.setStaffId(getStringSafely(cursor, KEY_STAFF_ID));
        user.setName(getStringSafely(cursor, KEY_NAME));
        user.setDepartment(getStringSafely(cursor, KEY_DEPARTMENT));
        user.setPassword(getStringSafely(cursor, KEY_PASSWORD));
        user.setLeftFingerprint(getBlobSafely(cursor, KEY_LEFT_FINGERPRINT));
        user.setRightFingerprint(getBlobSafely(cursor, KEY_RIGHT_FINGERPRINT));
        user.setLeftScannerUserId(getIntSafely(cursor, KEY_LEFT_SCANNER_ID));
        user.setRightScannerUserId(getIntSafely(cursor, KEY_RIGHT_SCANNER_ID));
        user.setLeftFingerprint2(getBlobSafely(cursor, KEY_LEFT_FINGERPRINT_2));
        user.setRightFingerprint2(getBlobSafely(cursor, KEY_RIGHT_FINGERPRINT_2));
        user.setLeftScannerUserId2(getIntSafely(cursor, KEY_LEFT_SCANNER_ID_2));
        user.setRightScannerUserId2(getIntSafely(cursor, KEY_RIGHT_SCANNER_ID_2));
        user.setCreatedAt(getStringSafely(cursor, KEY_CREATED_AT));
        user.setUpdatedAt(getStringSafely(cursor, KEY_UPDATED_AT));
        user.setActive(getIntSafely(cursor, KEY_IS_ACTIVE) == 1);
        user.setAdmin(getIntSafely(cursor, KEY_IS_ADMIN) == 1);

        return user;
    }

    // =================== SYNCED FINGERPRINTS METHODS ===================
    // Methods for managing fingerprints downloaded from API

    /**
     * Insert or update a synced fingerprint from API
     * @param apiId ULID string from API
     * @param employeeNumber Employee number
     * @param username Username
     * @param name Full name
     * @param role User role (ADMIN or CUSTODIAN)
     * @param templateDataString Template as Arrays.toString() format string (e.g., "[69, 27, 145, ...]")
     * @param leftRight 0 = Left, 1 = Right
     * @param fingerIndex Finger index 1-10
     * @param fingerType Finger type (Thumb, Index, etc.)
     * @return Scanner ID (integer) if successful, -1 if failed
     */
    public int insertOrUpdateSyncedFingerprint(String apiId, String employeeNumber, String username, String name,
                                                 String role, boolean isAllowedOverride, String templateDataString, int leftRight, int fingerIndex, String fingerType) {
        SQLiteDatabase db = this.getWritableDatabase();

        // Check if this API ID already exists
        String query = "SELECT " + KEY_SYNCED_SCANNER_ID + " FROM " + TABLE_SYNCED_FINGERPRINTS
                + " WHERE " + KEY_SYNCED_API_ID + " = ?";
        Cursor cursor = db.rawQuery(query, new String[]{apiId});

        int scannerId = -1;

        if (cursor.moveToFirst()) {
            // Already exists, get existing scanner ID
            scannerId = cursor.getInt(0);
            cursor.close();

            // Update existing record
            ContentValues values = new ContentValues();
            values.put(KEY_SYNCED_EMPLOYEE_NUMBER, employeeNumber);
            values.put(KEY_SYNCED_USERNAME, username);
            values.put(KEY_SYNCED_NAME, name);
            values.put(KEY_SYNCED_ROLE, role);
            values.put(KEY_SYNCED_IS_ALLOWED_OVERRIDE, isAllowedOverride ? 1 : 0);
            values.put(KEY_SYNCED_TEMPLATE_DATA, templateDataString);  // Store original string directly (unsigned format)
            values.put(KEY_SYNCED_LEFT_RIGHT, leftRight);
            values.put(KEY_SYNCED_FINGER_INDEX, fingerIndex);
            values.put(KEY_SYNCED_FINGER_TYPE, fingerType);
            values.put(KEY_SYNCED_ENROLLED_TO_SCANNER, 0); // Reset enrollment status
            values.put(KEY_SYNCED_AT, getCurrentDateTime());

            int updated = db.update(TABLE_SYNCED_FINGERPRINTS, values,
                    KEY_SYNCED_API_ID + " = ?", new String[]{apiId});

            if (updated > 0) {
                Log.d(TAG, "Updated fingerprint: API ID " + apiId + ", Scanner ID " + scannerId);
            }
        } else {
            cursor.close();

            // New record - need to assign a scanner ID
            // Get next available scanner ID
            String maxQuery = "SELECT MAX(" + KEY_SYNCED_SCANNER_ID + ") FROM " + TABLE_SYNCED_FINGERPRINTS;
            Cursor maxCursor = db.rawQuery(maxQuery, null);
            int maxScannerId = 10000; // Start synced fingerprints at 10000 to avoid conflicts

            if (maxCursor.moveToFirst() && !maxCursor.isNull(0)) {
                maxScannerId = Math.max(maxScannerId, maxCursor.getInt(0));
            }
            maxCursor.close();

            scannerId = maxScannerId + 1;

            // Insert new record
            ContentValues values = new ContentValues();
            values.put(KEY_SYNCED_API_ID, apiId);
            values.put(KEY_SYNCED_SCANNER_ID, scannerId);
            values.put(KEY_SYNCED_EMPLOYEE_NUMBER, employeeNumber);
            values.put(KEY_SYNCED_USERNAME, username);
            values.put(KEY_SYNCED_NAME, name);
            values.put(KEY_SYNCED_ROLE, role);
            values.put(KEY_SYNCED_IS_ALLOWED_OVERRIDE, isAllowedOverride ? 1 : 0);
            values.put(KEY_SYNCED_TEMPLATE_DATA, templateDataString);  // Store original string directly (unsigned format)
            values.put(KEY_SYNCED_LEFT_RIGHT, leftRight);
            values.put(KEY_SYNCED_FINGER_INDEX, fingerIndex);
            values.put(KEY_SYNCED_FINGER_TYPE, fingerType);
            values.put(KEY_SYNCED_ENROLLED_TO_SCANNER, 0);
            values.put(KEY_SYNCED_AT, getCurrentDateTime());

            long result = db.insert(TABLE_SYNCED_FINGERPRINTS, null, values);

            if (result > 0) {
                Log.d(TAG, "Inserted fingerprint: API ID " + apiId + ", assigned Scanner ID " + scannerId);
            } else {
                scannerId = -1;
            }
        }

        db.close();
        return scannerId;
    }

    /**
     * Get user info by scanner ID (integer ID used in UF_IdentifyTemplate)
     * This is called when scanner identifies a fingerprint
     * @param scannerId Scanner ID (integer)
     * @return SyncedFingerprint object or null if not found
     */
    public SyncedFingerprint getUserByScannerId(int scannerId) {
        SQLiteDatabase db = this.getReadableDatabase();

        String query = "SELECT * FROM " + TABLE_SYNCED_FINGERPRINTS
                + " WHERE " + KEY_SYNCED_SCANNER_ID + " = ?";

        Log.d(TAG, "DEBUG - getUserByScannerId called with scannerId: " + scannerId);
        Log.d(TAG, "DEBUG - Query: " + query);

        // DEBUG: Show all scanner IDs in database
        Cursor allIdsCursor = db.rawQuery("SELECT " + KEY_SYNCED_SCANNER_ID + ", " + KEY_SYNCED_NAME + " FROM " + TABLE_SYNCED_FINGERPRINTS, null);
        Log.d(TAG, "DEBUG - All scanner IDs in database:");
        while (allIdsCursor.moveToNext()) {
            int id = allIdsCursor.getInt(0);
            String name = allIdsCursor.getString(1);
            Log.d(TAG, "  - Scanner ID: " + id + ", Name: " + name);
        }
        allIdsCursor.close();

        Cursor cursor = db.rawQuery(query, new String[]{String.valueOf(scannerId)});

        Log.d(TAG, "DEBUG - Cursor count for scannerId " + scannerId + ": " + cursor.getCount());

        SyncedFingerprint fingerprint = null;
        if (cursor.moveToFirst()) {
            fingerprint = cursorToSyncedFingerprint(cursor);
            Log.d(TAG, "DEBUG - Found fingerprint: " + (fingerprint != null ? fingerprint.getName() : "NULL"));
        } else {
            Log.d(TAG, "DEBUG - No fingerprint found for scanner ID: " + scannerId);
        }

        cursor.close();
        db.close();

        return fingerprint;
    }

    /**
     * Mark a synced fingerprint as enrolled to scanner
     * @param scannerId Scanner ID (integer)
     * @return true if successful
     */
    public boolean markSyncedFingerprintAsEnrolled(int scannerId) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(KEY_SYNCED_ENROLLED_TO_SCANNER, 1);

        int rowsAffected = db.update(TABLE_SYNCED_FINGERPRINTS, values,
                KEY_SYNCED_SCANNER_ID + " = ?", new String[]{String.valueOf(scannerId)});

        db.close();

        Log.d(TAG, "Marked fingerprint Scanner ID " + scannerId + " as enrolled: " + (rowsAffected > 0));
        return rowsAffected > 0;
    }

    /**
     * Update the is_allowed_override flag for all rows belonging to an employee number.
     * Call this after a fresh API fetch to keep the local DB in sync without a full reset.
     * @param employeeNumber Employee number whose rows should be updated
     * @param isAllowed New value for is_allowed_override
     * @return number of rows updated
     */
    public int updateIsAllowedOverrideByEmployeeNumber(String employeeNumber, boolean isAllowed) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(KEY_SYNCED_IS_ALLOWED_OVERRIDE, isAllowed ? 1 : 0);
        int rowsAffected = db.update(TABLE_SYNCED_FINGERPRINTS, values,
                KEY_SYNCED_EMPLOYEE_NUMBER + " = ?", new String[]{employeeNumber});
        db.close();
        Log.d(TAG, "updateIsAllowedOverride: employee=" + employeeNumber
                + " isAllowed=" + isAllowed + " rows=" + rowsAffected);
        return rowsAffected;
    }

    /**
     * Check if a synced fingerprint is already enrolled to scanner
     * @param scannerId The scanner ID to check
     * @return true if enrolled, false otherwise
     */
    public boolean isSyncedFingerprintEnrolled(int scannerId) {
        SQLiteDatabase db = this.getReadableDatabase();

        String query = "SELECT " + KEY_SYNCED_ENROLLED_TO_SCANNER + " FROM " + TABLE_SYNCED_FINGERPRINTS
                + " WHERE " + KEY_SYNCED_SCANNER_ID + " = ?";

        Cursor cursor = db.rawQuery(query, new String[]{String.valueOf(scannerId)});

        boolean isEnrolled = false;
        if (cursor.moveToFirst()) {
            isEnrolled = cursor.getInt(0) == 1;
        }

        cursor.close();
        db.close();

        return isEnrolled;
    }

    /**
     * Get all unenrolled synced fingerprints
     * These need to be enrolled to the scanner
     * @return List of SyncedFingerprint objects
     */
    public List<SyncedFingerprint> getAllUnenrolledFingerprints() {
        List<SyncedFingerprint> fingerprints = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        String query = "SELECT * FROM " + TABLE_SYNCED_FINGERPRINTS
                + " WHERE " + KEY_SYNCED_ENROLLED_TO_SCANNER + " = 0"
                + " ORDER BY " + KEY_SYNCED_API_ID;

        Cursor cursor = db.rawQuery(query, null);

        if (cursor.moveToFirst()) {
            do {
                fingerprints.add(cursorToSyncedFingerprint(cursor));
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();

        Log.d(TAG, "Found " + fingerprints.size() + " unenrolled fingerprints");
        return fingerprints;
    }

    /**
     * Clear all synced fingerprints (for complete re-sync)
     * @return Number of rows deleted
     */
    public int clearAllSyncedFingerprints() {
        SQLiteDatabase db = this.getWritableDatabase();
        int rowsDeleted = db.delete(TABLE_SYNCED_FINGERPRINTS, null, null);
        db.close();

        Log.d(TAG, "Cleared " + rowsDeleted + " synced fingerprints");
        return rowsDeleted;
    }

    /**
     * Get count of synced fingerprints
     * @return Total count
     */
    public int getSyncedFingerprintCount() {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT COUNT(*) FROM " + TABLE_SYNCED_FINGERPRINTS;
        Cursor cursor = db.rawQuery(query, null);

        int count = 0;
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0);
        }

        cursor.close();
        db.close();

        return count;
    }

    /**
     * Get count of enrolled synced fingerprints
     * @return Count of fingerprints enrolled to scanner
     */
    public int getEnrolledSyncedFingerprintCount() {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT COUNT(*) FROM " + TABLE_SYNCED_FINGERPRINTS
                + " WHERE " + KEY_SYNCED_ENROLLED_TO_SCANNER + " = 1";
        Cursor cursor = db.rawQuery(query, null);

        int count = 0;
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0);
        }

        cursor.close();
        db.close();

        return count;
    }

    /**
     * Get all synced fingerprints from database
     * @return List of all synced fingerprints
     */
    public List<SyncedFingerprint> getAllSyncedFingerprints() {
        List<SyncedFingerprint> fingerprints = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        String query = "SELECT * FROM " + TABLE_SYNCED_FINGERPRINTS
                + " ORDER BY " + KEY_SYNCED_NAME + " ASC";

        Cursor cursor = db.rawQuery(query, null);

        if (cursor.moveToFirst()) {
            do {
                SyncedFingerprint fp = cursorToSyncedFingerprint(cursor);
                fingerprints.add(fp);
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();

        return fingerprints;
    }

    /**
     * Get ALL synced fingerprints for a specific employee number
     * Used for deletion - returns ALL fingerprint records (left + right + any extras)
     * @param employeeNumber Employee number
     * @return List of synced fingerprints for this employee
     */
    public List<SyncedFingerprint> getSyncedFingerprintsByEmployeeNumber(String employeeNumber) {
        List<SyncedFingerprint> fingerprints = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        String query = "SELECT * FROM " + TABLE_SYNCED_FINGERPRINTS
                + " WHERE " + KEY_SYNCED_EMPLOYEE_NUMBER + " = ?";

        Cursor cursor = db.rawQuery(query, new String[]{employeeNumber});

        if (cursor.moveToFirst()) {
            do {
                SyncedFingerprint fp = cursorToSyncedFingerprint(cursor);
                fingerprints.add(fp);
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();

        return fingerprints;
    }

    /**
     * Get a single synced user by employee number (returns first match)
     * Used for employee ID verification in manual override
     * @param employeeNumber The employee number to search for
     * @return SyncedFingerprint object if found, null otherwise
     */
    public SyncedFingerprint getSyncedUserByEmployeeNumber(String employeeNumber) {
        SQLiteDatabase db = this.getReadableDatabase();
        SyncedFingerprint syncedUser = null;

        String query = "SELECT * FROM " + TABLE_SYNCED_FINGERPRINTS
                + " WHERE " + KEY_SYNCED_EMPLOYEE_NUMBER + " = ? LIMIT 1";

        Cursor cursor = db.rawQuery(query, new String[]{employeeNumber});

        if (cursor.moveToFirst()) {
            syncedUser = cursorToSyncedFingerprint(cursor);
        }

        cursor.close();
        db.close();

        return syncedUser;
    }

    /**
     * Check if a user is already enrolled locally (in users table with fingerprints)
     * Used during sync to prevent duplicate enrollment
     * @param employeeNumber The employee number to check
     * @return true if user exists locally with at least one fingerprint enrolled
     */
    public boolean isUserEnrolledLocally(String employeeNumber) {
        SQLiteDatabase db = this.getReadableDatabase();

        String query = "SELECT " +
                KEY_LEFT_SCANNER_ID + ", " +
                KEY_RIGHT_SCANNER_ID + ", " +
                KEY_LEFT_SCANNER_ID_2 + ", " +
                KEY_RIGHT_SCANNER_ID_2 +
                " FROM " + TABLE_USERS +
                " WHERE " + KEY_STAFF_ID + " = ? AND " + KEY_IS_ACTIVE + " = 1";

        Cursor cursor = db.rawQuery(query, new String[]{employeeNumber});

        boolean hasFingerprints = false;
        if (cursor.moveToFirst()) {
            int leftId = cursor.getInt(0);
            int rightId = cursor.getInt(1);
            int leftId2 = cursor.getInt(2);
            int rightId2 = cursor.getInt(3);

            // User is enrolled locally if they have at least one scanner ID
            hasFingerprints = (leftId > 0 || rightId > 0 || leftId2 > 0 || rightId2 > 0);
        }

        cursor.close();
        db.close();

        return hasFingerprints;
    }

    /**
     * Get a synced fingerprint by API ID
     * Used to check if a fingerprint already exists before enrolling
     * @param apiId The API ID (ULID string)
     * @return SyncedFingerprint object or null if not found
     */
    public SyncedFingerprint getSyncedFingerprintByApiId(String apiId) {
        SQLiteDatabase db = this.getReadableDatabase();

        String query = "SELECT * FROM " + TABLE_SYNCED_FINGERPRINTS
                + " WHERE " + KEY_SYNCED_API_ID + " = ?";

        Cursor cursor = db.rawQuery(query, new String[]{apiId});

        SyncedFingerprint fingerprint = null;
        if (cursor.moveToFirst()) {
            fingerprint = cursorToSyncedFingerprint(cursor);
        }

        cursor.close();
        db.close();

        return fingerprint;
    }

    /**
     * Delete ALL synced fingerprints for a specific employee number
     * Used when deleting a user - removes ALL their fingerprint records
     * @param employeeNumber Employee number
     * @return Number of records deleted
     */
    public int deleteSyncedFingerprintsByEmployeeNumber(String employeeNumber) {
        SQLiteDatabase db = this.getWritableDatabase();

        int deletedCount = db.delete(
                TABLE_SYNCED_FINGERPRINTS,
                KEY_SYNCED_EMPLOYEE_NUMBER + " = ?",
                new String[]{employeeNumber}
        );

        db.close();

        Log.d(TAG, "Deleted " + deletedCount + " synced fingerprint record(s) for employee: " + employeeNumber);

        return deletedCount;
    }

    /**
     * Get the maximum scanner ID currently in use across ALL sources
     * Checks both users table and synced_fingerprints table to prevent ID collisions
     *
     * @return Maximum scanner ID in use, or 10000 if no IDs found
     */
    /**
     * Get the maximum scanner ID in the LOCAL user range (1-9999)
     * Used for local enrollment to find the next available local ID
     * Excludes synced fingerprints (10000-99999)
     */
    public int getMaxLocalScannerIdInUse() {
        SQLiteDatabase db = this.getReadableDatabase();
        int maxId = 0; // Start from 0 (before local range starts at 1)

        try {
            // Check users table - all 4 scanner ID columns
            // Only consider IDs in local range (1-9999)
            String usersQuery = "SELECT " +
                    "MAX(CASE WHEN " + KEY_LEFT_SCANNER_ID + " > 0 AND " + KEY_LEFT_SCANNER_ID + " < 10000 THEN " + KEY_LEFT_SCANNER_ID + " ELSE 0 END) AS max_left, " +
                    "MAX(CASE WHEN " + KEY_RIGHT_SCANNER_ID + " > 0 AND " + KEY_RIGHT_SCANNER_ID + " < 10000 THEN " + KEY_RIGHT_SCANNER_ID + " ELSE 0 END) AS max_right, " +
                    "MAX(CASE WHEN " + KEY_LEFT_SCANNER_ID_2 + " > 0 AND " + KEY_LEFT_SCANNER_ID_2 + " < 10000 THEN " + KEY_LEFT_SCANNER_ID_2 + " ELSE 0 END) AS max_left2, " +
                    "MAX(CASE WHEN " + KEY_RIGHT_SCANNER_ID_2 + " > 0 AND " + KEY_RIGHT_SCANNER_ID_2 + " < 10000 THEN " + KEY_RIGHT_SCANNER_ID_2 + " ELSE 0 END) AS max_right2 " +
                    "FROM " + TABLE_USERS +
                    " WHERE " + KEY_IS_ACTIVE + " = 1";

            Cursor usersCursor = db.rawQuery(usersQuery, null);
            if (usersCursor.moveToFirst()) {
                int maxLeft = usersCursor.getInt(0);
                int maxRight = usersCursor.getInt(1);
                int maxLeft2 = usersCursor.getInt(2);
                int maxRight2 = usersCursor.getInt(3);

                maxId = Math.max(maxId, Math.max(Math.max(maxLeft, maxRight), Math.max(maxLeft2, maxRight2)));
            }
            usersCursor.close();

            Log.d(TAG, "Max LOCAL scanner ID in use (range 1-9999): " + maxId);
        } catch (Exception e) {
            Log.e(TAG, "Error getting max local scanner ID", e);
        } finally {
            db.close();
        }

        return maxId;
    }

    /**
     * Get the maximum scanner ID across ALL sources (local users + synced fingerprints)
     * Range: 1-99999 (includes both local 1-9999 and synced 10000-99999)
     */
    public int getMaxScannerIdInUse() {
        SQLiteDatabase db = this.getReadableDatabase();
        int maxId = 0; // Start from 0

        try {
            // Check users table - all 4 scanner ID columns
            String usersQuery = "SELECT " +
                    "MAX(CASE WHEN " + KEY_LEFT_SCANNER_ID + " > 0 THEN " + KEY_LEFT_SCANNER_ID + " ELSE 0 END) AS max_left, " +
                    "MAX(CASE WHEN " + KEY_RIGHT_SCANNER_ID + " > 0 THEN " + KEY_RIGHT_SCANNER_ID + " ELSE 0 END) AS max_right, " +
                    "MAX(CASE WHEN " + KEY_LEFT_SCANNER_ID_2 + " > 0 THEN " + KEY_LEFT_SCANNER_ID_2 + " ELSE 0 END) AS max_left2, " +
                    "MAX(CASE WHEN " + KEY_RIGHT_SCANNER_ID_2 + " > 0 THEN " + KEY_RIGHT_SCANNER_ID_2 + " ELSE 0 END) AS max_right2 " +
                    "FROM " + TABLE_USERS +
                    " WHERE " + KEY_IS_ACTIVE + " = 1";

            Cursor usersCursor = db.rawQuery(usersQuery, null);
            if (usersCursor.moveToFirst()) {
                int maxLeft = usersCursor.getInt(0);
                int maxRight = usersCursor.getInt(1);
                int maxLeft2 = usersCursor.getInt(2);
                int maxRight2 = usersCursor.getInt(3);

                maxId = Math.max(maxId, Math.max(Math.max(maxLeft, maxRight), Math.max(maxLeft2, maxRight2)));
            }
            usersCursor.close();

            // Check synced_fingerprints table
            String syncedQuery = "SELECT MAX(" + KEY_SYNCED_SCANNER_ID + ") FROM " + TABLE_SYNCED_FINGERPRINTS;
            Cursor syncedCursor = db.rawQuery(syncedQuery, null);
            if (syncedCursor.moveToFirst()) {
                int syncedMax = syncedCursor.getInt(0);
                maxId = Math.max(maxId, syncedMax);
            }
            syncedCursor.close();

            Log.d(TAG, "╔════════════════════════════════════════");
            Log.d(TAG, "║ MAX SCANNER ID CHECK");
            Log.d(TAG, "╠════════════════════════════════════════");
            Log.d(TAG, "║ Maximum scanner ID in use: " + maxId);
            Log.d(TAG, "╚════════════════════════════════════════");

        } catch (Exception e) {
            Log.e(TAG, "Error getting max scanner ID", e);
        } finally {
            db.close();
        }

        return maxId;
    }

    /**
     * Convert cursor to SyncedFingerprint object
     */
    private SyncedFingerprint cursorToSyncedFingerprint(Cursor cursor) {
        SyncedFingerprint fp = new SyncedFingerprint();
        fp.setId(getIntSafely(cursor, KEY_SYNCED_ID));
        fp.setApiId(getStringSafely(cursor, KEY_SYNCED_API_ID));
        fp.setScannerId(getIntSafely(cursor, KEY_SYNCED_SCANNER_ID));
        fp.setEmployeeNumber(getStringSafely(cursor, KEY_SYNCED_EMPLOYEE_NUMBER));
        fp.setUsername(getStringSafely(cursor, KEY_SYNCED_USERNAME));
        fp.setName(getStringSafely(cursor, KEY_SYNCED_NAME));
        fp.setRole(getStringSafely(cursor, KEY_SYNCED_ROLE));
        fp.setTemplateData(getStringSafely(cursor, KEY_SYNCED_TEMPLATE_DATA));
        fp.setLeftRight(getIntSafely(cursor, KEY_SYNCED_LEFT_RIGHT));
        fp.setFingerIndex(getIntSafely(cursor, KEY_SYNCED_FINGER_INDEX));
        fp.setFingerType(getStringSafely(cursor, KEY_SYNCED_FINGER_TYPE));
        fp.setEnrolledToScanner(getIntSafely(cursor, KEY_SYNCED_ENROLLED_TO_SCANNER) == 1);
        fp.setAllowedOverride(getIntSafely(cursor, KEY_SYNCED_IS_ALLOWED_OVERRIDE) == 1);
        fp.setSyncedAt(getStringSafely(cursor, KEY_SYNCED_AT));
        return fp;
    }

    // =================== SAFE CURSOR ACCESS METHODS ===================
    // These methods prevent crashes when database columns don't exist

    /**
     * Safely get integer value from cursor
     */
    private int getIntSafely(Cursor cursor, String columnName) {
        int columnIndex = cursor.getColumnIndex(columnName);
        return columnIndex >= 0 ? cursor.getInt(columnIndex) : 0;
    }

    /**
     * Safely get string value from cursor
     */
    private String getStringSafely(Cursor cursor, String columnName) {
        int columnIndex = cursor.getColumnIndex(columnName);
        return columnIndex >= 0 ? cursor.getString(columnIndex) : null;
    }

    /**
     * Safely get blob (binary) value from cursor
     */
    private byte[] getBlobSafely(Cursor cursor, String columnName) {
        int columnIndex = cursor.getColumnIndex(columnName);
        return columnIndex >= 0 ? cursor.getBlob(columnIndex) : null;
    }

    /**
     * Get current date time as string
     */
    private String getCurrentDateTime() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        Date date = new Date();
        return dateFormat.format(date);
    }

    // =================== DATA CLASSES ===================

    /**
     * User class represents a user record from the database
     */
    public static class User {
        private int id;
        private String icNumber;
        private String staffId;
        private String name;
        private String department;
        private String password;
        private byte[] leftFingerprint;
        private byte[] rightFingerprint;
        private byte[] leftFingerprint2;
        private byte[] rightFingerprint2;
        private int leftScannerUserId2;
        private int rightScannerUserId2;
        private int leftScannerUserId;      // NEW: Scanner ID for left finger
        private int rightScannerUserId;     // NEW: Scanner ID for right finger
        private String createdAt;
        private String updatedAt;
        private boolean isActive;
        private boolean isAdmin;
        private String lastFingerUsed;      // Helper field: which finger was used in last scan

        // Getters and Setters
        public int getId() { return id; }
        public void setId(int id) { this.id = id; }

        public String getIcNumber() { return icNumber; }
        public void setIcNumber(String icNumber) { this.icNumber = icNumber; }

        public String getStaffId() { return staffId; }
        public void setStaffId(String staffId) { this.staffId = staffId; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getDepartment() { return department; }
        public void setDepartment(String department) { this.department = department; }

        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }

        public byte[] getLeftFingerprint() { return leftFingerprint; }
        public void setLeftFingerprint(byte[] leftFingerprint) { this.leftFingerprint = leftFingerprint; }

        public byte[] getLeftFingerprint2() { return leftFingerprint2; }
        public void setLeftFingerprint2(byte[] leftFingerprint2) { this.leftFingerprint2 = leftFingerprint2; }

        public byte[] getRightFingerprint2() { return rightFingerprint2; }
        public void setRightFingerprint2(byte[] rightFingerprint2) { this.rightFingerprint2 = rightFingerprint2; }

        public int getLeftScannerUserId2() { return leftScannerUserId2; }
        public void setLeftScannerUserId2(int leftScannerUserId2) { this.leftScannerUserId2 = leftScannerUserId2; }

        public int getRightScannerUserId2() { return rightScannerUserId2; }
        public void setRightScannerUserId2(int rightScannerUserId2) { this.rightScannerUserId2 = rightScannerUserId2; }
        public byte[] getRightFingerprint() { return rightFingerprint; }
        public void setRightFingerprint(byte[] rightFingerprint) { this.rightFingerprint = rightFingerprint; }

        // NEW: Scanner ID getters and setters
        public int getLeftScannerUserId() { return leftScannerUserId; }
        public void setLeftScannerUserId(int leftScannerUserId) { this.leftScannerUserId = leftScannerUserId; }

        public int getRightScannerUserId() { return rightScannerUserId; }
        public void setRightScannerUserId(int rightScannerUserId) { this.rightScannerUserId = rightScannerUserId; }

        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

        public String getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

        public boolean isActive() { return isActive; }
        public void setActive(boolean active) { isActive = active; }

        public boolean isAdmin() { return isAdmin; }
        public void setAdmin(boolean admin) { isAdmin = admin; }

        public String getLastFingerUsed() { return lastFingerUsed; }
        public void setLastFingerUsed(String lastFingerUsed) { this.lastFingerUsed = lastFingerUsed; }
    }

    /**
     * AttendanceRecord class represents an attendance record from the database
     */

    public static class AttendanceRecord {
        private int id;
        private int userId;
        private String attendanceType;
        private String timestamp;
        private String fingerUsed;

        // Getters and Setters
        public int getId() { return id; }
        public void setId(int id) { this.id = id; }

        public int getUserId() { return userId; }
        public void setUserId(int userId) { this.userId = userId; }

        public String getAttendanceType() { return attendanceType; }
        public void setAttendanceType(String attendanceType) { this.attendanceType = attendanceType; }

        public String getTimestamp() { return timestamp; }
        public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

        public String getFingerUsed() { return fingerUsed; }
        public void setFingerUsed(String fingerUsed) { this.fingerUsed = fingerUsed; }
    }

    /**
     * RegisteredPersonnel class represents a pre-registered admin or custodian
     * These are personnel who are authorized to enroll fingerprints
     */
    public static class RegisteredPersonnel {
        private int id;
        private String employeeId;
        private String name;
        private String role;  // "ADMIN" or "CUSTODIAN"
        private boolean isEnrolled;
        private int enrolledUserId;

        // Getters and Setters
        public int getId() { return id; }
        public void setId(int id) { this.id = id; }

        public String getEmployeeId() { return employeeId; }
        public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }

        public boolean isEnrolled() { return isEnrolled; }
        public void setEnrolled(boolean enrolled) { isEnrolled = enrolled; }

        public int getEnrolledUserId() { return enrolledUserId; }
        public void setEnrolledUserId(int enrolledUserId) { this.enrolledUserId = enrolledUserId; }

        @Override
        public String toString() {
            return name + " (" + employeeId + ")";
        }
    }

    /**
     * VaultOverride class represents a vault override record
     */
    public static class VaultOverride {
        private int id;
        private String vaultType;      // "MAIN" or "DAY"
        private String entryPoint;     // "MAIN_GRILL", "COMPARTMENT_1", "COMPARTMENT_2", "COMPARTMENT_3"
        private String status;         // "ACTIVE" or "DEACTIVATED"
        private String profileId;      // PAC API profile ID (UUID from server)
        private String activatedAt;
        private String deactivatedAt;
        private String custodian1Id;
        private String custodian2Id;
        private String custodian3Id;
        private String deactivatedBy;

        // Getters and Setters
        public int getId() { return id; }
        public void setId(int id) { this.id = id; }

        public String getVaultType() { return vaultType; }
        public void setVaultType(String vaultType) { this.vaultType = vaultType; }

        public String getEntryPoint() { return entryPoint; }
        public void setEntryPoint(String entryPoint) { this.entryPoint = entryPoint; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public String getProfileId() { return profileId; }
        public void setProfileId(String profileId) { this.profileId = profileId; }

        public String getActivatedAt() { return activatedAt; }
        public void setActivatedAt(String activatedAt) { this.activatedAt = activatedAt; }

        public String getDeactivatedAt() { return deactivatedAt; }
        public void setDeactivatedAt(String deactivatedAt) { this.deactivatedAt = deactivatedAt; }

        public String getCustodian1Id() { return custodian1Id; }
        public void setCustodian1Id(String custodian1Id) { this.custodian1Id = custodian1Id; }

        public String getCustodian2Id() { return custodian2Id; }
        public void setCustodian2Id(String custodian2Id) { this.custodian2Id = custodian2Id; }

        public String getCustodian3Id() { return custodian3Id; }
        public void setCustodian3Id(String custodian3Id) { this.custodian3Id = custodian3Id; }

        public String getDeactivatedBy() { return deactivatedBy; }
        public void setDeactivatedBy(String deactivatedBy) { this.deactivatedBy = deactivatedBy; }

        public boolean isActive() {
            return "ACTIVE".equals(status);
        }

        /**
         * Get display name for entry point
         */
        public String getEntryPointDisplayName() {
            switch (entryPoint) {
                case "MAIN_GRILL": return "Main Grill";
                case "COMPARTMENT_1": return "Compartment 1";
                case "COMPARTMENT_2": return "Compartment 2";
                case "COMPARTMENT_3": return "Compartment 3";
                default: return entryPoint;
            }
        }

        /**
         * Get display name for vault type
         */
        public String getVaultTypeDisplayName() {
            return "MAIN".equals(vaultType) ? "Main Vault" : "Day Vault";
        }

        @Override
        public String toString() {
            return getVaultTypeDisplayName() + " - " + getEntryPointDisplayName();
        }
    }

    /**
     * VaultOverrideRecord class for activity log display
     */
    public static class VaultOverrideRecord {
        private int id;
        private String vaultType;
        private String entryPoint;
        private String status;
        private String profileId;
        private String activatedAt;
        private String deactivatedAt;
        private String custodian1Id;
        private String custodian2Id;
        private String custodian3Id;
        private String deactivatedBy;

        // Getters and Setters
        public int getId() { return id; }
        public void setId(int id) { this.id = id; }

        public String getVaultType() { return vaultType; }
        public void setVaultType(String vaultType) { this.vaultType = vaultType; }

        public String getEntryPoint() { return entryPoint; }
        public void setEntryPoint(String entryPoint) { this.entryPoint = entryPoint; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public String getProfileId() { return profileId; }
        public void setProfileId(String profileId) { this.profileId = profileId; }

        public String getActivatedAt() { return activatedAt; }
        public void setActivatedAt(String activatedAt) { this.activatedAt = activatedAt; }

        public String getDeactivatedAt() { return deactivatedAt; }
        public void setDeactivatedAt(String deactivatedAt) { this.deactivatedAt = deactivatedAt; }

        public String getCustodian1Id() { return custodian1Id; }
        public void setCustodian1Id(String custodian1Id) { this.custodian1Id = custodian1Id; }

        public String getCustodian2Id() { return custodian2Id; }
        public void setCustodian2Id(String custodian2Id) { this.custodian2Id = custodian2Id; }

        public String getCustodian3Id() { return custodian3Id; }
        public void setCustodian3Id(String custodian3Id) { this.custodian3Id = custodian3Id; }

        public String getDeactivatedBy() { return deactivatedBy; }
        public void setDeactivatedBy(String deactivatedBy) { this.deactivatedBy = deactivatedBy; }
    }

    /**
     * SyncedFingerprint class represents a fingerprint synced from API
     * These are fingerprints downloaded from PAC_API for cross-device validation
     */
    public static class SyncedFingerprint {
        private int id;                    // Local database ID
        private String apiId;              // ULID string from API
        private int scannerId;             // Integer scanner ID for UF_EnrollTemplate
        private String employeeNumber;     // Employee number
        private String username;           // Username
        private String name;               // Full name
        private String role;               // "ADMIN" or "CUSTODIAN"
        private String templateData;       // Template in Arrays.toString() format
        private int leftRight;             // 0 = Left, 1 = Right
        private int fingerIndex;           // 1-10
        private String fingerType;         // "Thumb", "Index", etc.
        private boolean enrolledToScanner; // Whether enrolled to scanner
        private boolean isAllowedOverride; // Whether this employee is permitted to perform manual override
        private String syncedAt;           // When synced from API

        // Getters and Setters
        public int getId() { return id; }
        public void setId(int id) { this.id = id; }

        public String getApiId() { return apiId; }
        public void setApiId(String apiId) { this.apiId = apiId; }

        public int getScannerId() { return scannerId; }
        public void setScannerId(int scannerId) { this.scannerId = scannerId; }

        public String getEmployeeNumber() { return employeeNumber; }
        public void setEmployeeNumber(String employeeNumber) { this.employeeNumber = employeeNumber; }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }

        public String getTemplateData() { return templateData; }
        public void setTemplateData(String templateData) { this.templateData = templateData; }

        public int getLeftRight() { return leftRight; }
        public void setLeftRight(int leftRight) { this.leftRight = leftRight; }

        public int getFingerIndex() { return fingerIndex; }
        public void setFingerIndex(int fingerIndex) { this.fingerIndex = fingerIndex; }

        public String getFingerType() { return fingerType; }
        public void setFingerType(String fingerType) { this.fingerType = fingerType; }

        public boolean isEnrolledToScanner() { return enrolledToScanner; }
        public void setEnrolledToScanner(boolean enrolledToScanner) { this.enrolledToScanner = enrolledToScanner; }

        public boolean isAllowedOverride() { return isAllowedOverride; }
        public void setAllowedOverride(boolean allowedOverride) { this.isAllowedOverride = allowedOverride; }

        public String getSyncedAt() { return syncedAt; }
        public void setSyncedAt(String syncedAt) { this.syncedAt = syncedAt; }

        @Override
        public String toString() {
            return "SyncedFingerprint{" +
                    "apiId='" + apiId + '\'' +
                    ", scannerId=" + scannerId +
                    ", name='" + name + '\'' +
                    ", employeeNumber='" + employeeNumber + '\'' +
                    ", fingerType='" + fingerType + '\'' +
                    ", enrolled=" + enrolledToScanner +
                    '}';
        }
    }

    /**
     * Get template data string by scanner ID
     * @param scannerId Scanner ID
     * @return Template data as string (Arrays.toString format), or null if not found
     */
    public String getTemplateDataByScannerId(int scannerId) {
        SQLiteDatabase db = this.getReadableDatabase();
        String templateString = null;

        String query = "SELECT " + KEY_SYNCED_TEMPLATE_DATA + " FROM " + TABLE_SYNCED_FINGERPRINTS
                + " WHERE " + KEY_SYNCED_SCANNER_ID + " = ?";

        Cursor cursor = db.rawQuery(query, new String[]{String.valueOf(scannerId)});

        if (cursor.moveToFirst()) {
            templateString = cursor.getString(0);
        }

        cursor.close();
        db.close();
        return templateString;
    }

    /**
     * Convert byte array to unsigned format string (like API sends)
     * Converts byte[] {69, 27, 14, 21, -111, ...} -> "[69, 27, 14, 21, 145, ...]"
     *
     * IMPORTANT: Outputs UNSIGNED representation (0-255), not Java's signed representation
     * This matches the format from the PAC_API templateDataByteArraysString field
     *
     * @param bytes Byte array to convert
     * @return String in unsigned format, or null if input is null
     */
    public static String byteArrayToUnsignedString(byte[] bytes) {
        if (bytes == null) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < bytes.length; i++) {
            // Convert signed byte to unsigned int (0-255)
            int unsignedValue = bytes[i] & 0xFF;
            sb.append(unsignedValue);
            if (i < bytes.length - 1) {
                sb.append(", ");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Convert Arrays.toString() format back to byte array
     * Converts "[69, 29, 18, 24, 162, ...]" -> byte[] {69, 29, 18, 24, -94, ...}
     *
     * IMPORTANT: Uses Integer.parseInt() then casts to byte to handle unsigned byte values (0-255)
     * Values > 127 become negative when cast to signed byte (e.g., 162 -> -94)
     *
     * @param arrayString String representation from Arrays.toString()
     * @return byte array, or null if parsing fails
     */
    public static byte[] parseStringToByteArray(String arrayString) {
        try {
            Log.d(TAG, "parseStringToByteArray: Input length = " + arrayString.length());

            // Remove brackets and whitespace
            String cleaned = arrayString.trim();
            if (cleaned.startsWith("[") && cleaned.endsWith("]")) {
                cleaned = cleaned.substring(1, cleaned.length() - 1);
            }

            // Handle empty array
            if (cleaned.isEmpty()) {
                return new byte[0];
            }

            // Split by comma
            String[] parts = cleaned.split(",");
            Log.d(TAG, "parseStringToByteArray: Found " + parts.length + " parts");

            byte[] result = new byte[parts.length];

            // Parse each byte (handles 0-255 range by parsing as int first)
            for (int i = 0; i < parts.length; i++) {
                int value = Integer.parseInt(parts[i].trim()); // 0-255
                result[i] = (byte) value;                      // Cast to signed byte
            }

            Log.d(TAG, "parseStringToByteArray: Successfully parsed " + result.length + " bytes");

            // Log first 20 bytes for debugging
            if (result.length >= 20) {
                StringBuilder hex = new StringBuilder();
                for (int i = 0; i < 20; i++) {
                    hex.append(String.format("%02X ", result[i]));
                }
                Log.d(TAG, "parseStringToByteArray: First 20 bytes (HEX) = " + hex.toString());
            }

            return result;
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse string to byte array: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}