package com.winlator.star.core;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * Optional PIN lock for the WinNext app itself (independent of any Windows-side
 * account). Security design:
 *  - The PIN is NEVER stored in plain text: only salt + SHA-256(salt + PIN).
 *  - Failed attempts are limited: after 5 failures the lockout doubles (30s base),
 *    which makes repeated guessing impractical.
 *  - No sensitive values are written to logs.
 */
public final class AppLockManager {
    public static final String PREF_ENABLED = "app_lock_enabled";
    private static final String PREF_SALT = "app_lock_salt";
    private static final String PREF_HASH = "app_lock_pin_hash";
    private static final String PREF_FAILS = "app_lock_fail_count";
    private static final String PREF_LOCKOUT_UNTIL = "app_lock_lockout_until";
    private static final int MAX_ATTEMPTS = 5;
    private static final long BASE_LOCKOUT_MS = 30_000L;

    private AppLockManager() {}

    public static boolean isEnabled(Context context) {
        SharedPreferences p = prefs(context);
        return p.getBoolean(PREF_ENABLED, false)
                && p.getString(PREF_HASH, null) != null;
    }

    public static boolean isPinSet(Context context) {
        return prefs(context).getString(PREF_HASH, null) != null;
    }

    /** Returns true if the caller must wait; the remaining wait is written to outSeconds[0]. */
    public static boolean isLockedOut(Context context, long[] outSeconds) {
        SharedPreferences p = prefs(context);
        long until = p.getLong(PREF_LOCKOUT_UNTIL, 0);
        long now = System.currentTimeMillis();
        if (until > now) {
            if (outSeconds != null && outSeconds.length > 0) outSeconds[0] = (until - now + 999) / 1000;
            return true;
        }
        if (outSeconds != null && outSeconds.length > 0) outSeconds[0] = 0;
        return false;
    }

    /** Sets or changes the PIN. Returns false on internal error. The PIN is not persisted. */
    public static boolean setPin(Context context, String pin) {
        if (pin == null || !pin.matches("\\d{4,8}")) return false;
        try {
            SecureRandom random = new SecureRandom();
            byte[] salt = new byte[16];
            random.nextBytes(salt);
            String saltHex = toHex(salt);
            String hash = sha256Hex(saltHex + pin);
            prefs(context).edit()
                    .putString(PREF_SALT, saltHex)
                    .putString(PREF_HASH, hash)
                    .putInt(PREF_FAILS, 0)
                    .putLong(PREF_LOCKOUT_UNTIL, 0)
                    .putBoolean(PREF_ENABLED, true)
                    .apply();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Verifies a PIN attempt; on failure records it and applies lockout. */
    public static boolean verifyPin(Context context, String pin) {
        SharedPreferences p = prefs(context);
        String saltHex = p.getString(PREF_SALT, null);
        String storedHash = p.getString(PREF_HASH, null);
        if (saltHex == null || storedHash == null || pin == null) return false;
        try {
            String hash = sha256Hex(saltHex + pin);
            if (MessageDigest.isEqual(hash.getBytes(StandardCharsets.US_ASCII),
                    storedHash.getBytes(StandardCharsets.US_ASCII))) {
                p.edit().putInt(PREF_FAILS, 0).putLong(PREF_LOCKOUT_UNTIL, 0).apply();
                return true;
            }
            int fails = p.getInt(PREF_FAILS, 0) + 1;
            long lockout = 0;
            if (fails >= MAX_ATTEMPTS) {
                int over = fails - MAX_ATTEMPTS;
                lockout = BASE_LOCKOUT_MS * (1L << Math.min(over, 5));
                p.edit().putLong(PREF_LOCKOUT_UNTIL, System.currentTimeMillis() + lockout).apply();
            }
            p.edit().putInt(PREF_FAILS, fails).apply();
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public static void disable(Context context) {
        prefs(context).edit()
                .putBoolean(PREF_ENABLED, false)
                .putString(PREF_HASH, null)
                .putString(PREF_SALT, null)
                .putInt(PREF_FAILS, 0)
                .putLong(PREF_LOCKOUT_UNTIL, 0)
                .apply();
    }

    private static SharedPreferences prefs(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static String sha256Hex(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return toHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
