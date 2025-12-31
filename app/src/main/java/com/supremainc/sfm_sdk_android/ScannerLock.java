/*
 * Copyright (c) 2001 - 2025. Suprema Inc. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for details.
 */

package com.supremainc.sfm_sdk_android;

/**
 * Singleton lock to ensure only one thread accesses the fingerprint scanner at a time.
 * This prevents race conditions and hardware contention when multiple activities
 * or background threads try to access the scanner simultaneously.
 */
public class ScannerLock {
    private static final Object lock = new Object();

    /**
     * Get the singleton lock object for scanner synchronization
     * @return The lock object to use in synchronized blocks
     */
    public static Object getLock() {
        return lock;
    }
}
