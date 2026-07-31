package com.patrick.fintech.loan_backend.security;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Versioned AES-256-GCM field encryption core, shared by {@link CryptoConverter} (normal
 * read/write traffic) and the PII key-rotation tool (batch re-encryption).
 *
 * <p>This exists so key rotation is actually possible. Before this class, CryptoConverter read
 * a single APP_ENCRYPTION_KEY and had no way to tell which key a given stored value was
 * encrypted under — meaning once real customer data existed, that key could never be safely
 * changed again (a compromised key was compromised forever). See SECRETS_AND_KEY_ROTATION.md.
 *
 * <h2>Stored format</h2>
 * {@code enc:<version>:<base64(12-byte IV || GCM ciphertext+tag)>} — e.g. {@code enc:2:AbCd...}.
 * Values written before this class existed have no version segment ({@code enc:<base64>}) and
 * are treated as {@link #legacyVersion()} for backward compatibility.
 *
 * <h2>Environment variables</h2>
 * <ul>
 *   <li>{@code APP_ENCRYPTION_KEY} (required for real data) — the key used for all new writes,
 *       tagged with {@code APP_ENCRYPTION_KEY_VERSION} (default {@code "1"}).</li>
 *   <li>{@code APP_ENCRYPTION_KEY_PREVIOUS} / {@code APP_ENCRYPTION_KEY_PREVIOUS_VERSION}
 *       (optional) — set these to the *old* key/version only while a rotation is in progress,
 *       so existing rows still encrypted under the old key remain readable until the
 *       re-encryption migration tool has touched every row. Remove both once that migration
 *       is complete.</li>
 * </ul>
 *
 * <h2>Rotating a key</h2>
 * <ol>
 *   <li>Move the current APP_ENCRYPTION_KEY / APP_ENCRYPTION_KEY_VERSION values into
 *       APP_ENCRYPTION_KEY_PREVIOUS / APP_ENCRYPTION_KEY_PREVIOUS_VERSION.</li>
 *   <li>Generate a new key (openssl rand -base64 32), set it as APP_ENCRYPTION_KEY, and bump
 *       APP_ENCRYPTION_KEY_VERSION (e.g. "1" -> "2"). Restart — the app can now read both old
 *       and new rows, but only ever writes new rows under the new key.</li>
 *   <li>Run the PII key-rotation tool (see PiiKeyRotationRunner). It re-encrypts every row under
 *       the new key. It is idempotent and safe to re-run or resume.</li>
 *   <li>Once it reports zero errors, remove the two APP_ENCRYPTION_KEY_PREVIOUS* variables and
 *       restart. The old key is no longer needed anywhere.</li>
 * </ol>
 * Do the same for {@code APP_INDEX_KEY} / {@code APP_INDEX_KEY_PREVIOUS} via {@link HmacIndexer}
 * — the rotation tool handles both together.
 */
public final class PiiCrypto {

    private PiiCrypto() {}

    private static final String ALGO = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PiiCrypto.class);

    private static volatile boolean warnedInsecureKey = false;

    private static SecretKeySpec currentKey;
    private static String currentVersion;
    private static SecretKeySpec previousKey;
    private static String previousVersion;
    private static volatile boolean keysLoaded = false;

    private static synchronized void loadKeysIfNeeded() {
        if (keysLoaded) return;

        String configured = System.getenv("APP_ENCRYPTION_KEY");
        if (configured != null && !configured.isBlank()) {
            currentKey = keyFromBase64(configured, "APP_ENCRYPTION_KEY");
        } else {
            if (!warnedInsecureKey) {
                log.warn("╔══════════════════════════════════════════════════════════════╗");
                log.warn("║  APP_ENCRYPTION_KEY is not set — using an INSECURE DEV-ONLY   ║");
                log.warn("║  fixed key. PII encrypted with this key is NOT actually       ║");
                log.warn("║  protected. Set APP_ENCRYPTION_KEY before handling real data. ║");
                log.warn("║  Generate one with: openssl rand -base64 32                   ║");
                log.warn("╚══════════════════════════════════════════════════════════════╝");
                warnedInsecureKey = true;
            }
            byte[] seed = Base64.getDecoder().decode("ZGV2LW9ubHktaW5zZWN1cmUta2V5LWRvLW5vdC11c2U9");
            byte[] padded = new byte[32];
            for (int i = 0; i < 32; i++) padded[i] = seed[i % seed.length];
            currentKey = new SecretKeySpec(padded, "AES");
        }
        currentVersion = envOrDefault("APP_ENCRYPTION_KEY_VERSION", "1");

        String prevConfigured = System.getenv("APP_ENCRYPTION_KEY_PREVIOUS");
        if (prevConfigured != null && !prevConfigured.isBlank()) {
            previousKey = keyFromBase64(prevConfigured, "APP_ENCRYPTION_KEY_PREVIOUS");
            previousVersion = envOrDefault("APP_ENCRYPTION_KEY_PREVIOUS_VERSION", "1");
            if (previousVersion.equals(currentVersion)) {
                throw new IllegalStateException(
                    "APP_ENCRYPTION_KEY_PREVIOUS_VERSION must differ from APP_ENCRYPTION_KEY_VERSION");
            }
        }
        keysLoaded = true;
    }

    private static SecretKeySpec keyFromBase64(String base64, String varName) {
        byte[] keyBytes = Base64.getDecoder().decode(base64);
        if (keyBytes.length != 32) {
            throw new IllegalStateException(varName + " must decode to exactly 32 bytes (AES-256) — got " + keyBytes.length);
        }
        return new SecretKeySpec(keyBytes, "AES");
    }

    private static String envOrDefault(String name, String def) {
        String v = System.getenv(name);
        return (v == null || v.isBlank()) ? def : v;
    }

    public static String legacyVersion() {
        loadKeysIfNeeded();
        return previousKey != null ? previousVersion : currentVersion;
    }

    public static boolean rotationInProgress() {
        loadKeysIfNeeded();
        return previousKey != null;
    }

    public static String currentVersionTag() {
        loadKeysIfNeeded();
        return currentVersion;
    }

    private static SecretKeySpec keyForVersion(String version) {
        loadKeysIfNeeded();
        if (currentVersion.equals(version)) return currentKey;
        if (previousKey != null && previousVersion.equals(version)) return previousKey;
        throw new IllegalStateException(
            "No encryption key configured for version '" + version + "' — set APP_ENCRYPTION_KEY_PREVIOUS" +
            "/APP_ENCRYPTION_KEY_PREVIOUS_VERSION if this data was encrypted under an older key.");
    }

    public static String encryptCurrent(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) return plaintext;
        loadKeysIfNeeded();
        return "enc:" + currentVersion + ":" + encryptRaw(plaintext, currentKey);
    }

    public static String decrypt(String stored) {
        if (stored == null || stored.isBlank()) return stored;
        if (!stored.startsWith("enc:")) return stored;
        String rest = stored.substring(4);
        String version;
        String payload;
        int firstColon = rest.indexOf(':');
        if (firstColon > 0 && isAllDigits(rest.substring(0, firstColon))) {
            version = rest.substring(0, firstColon);
            payload = rest.substring(firstColon + 1);
        } else {
            version = legacyVersion();
            payload = rest;
        }
        try {
            return decryptRaw(payload, keyForVersion(version));
        } catch (Exception e) {
            log.error("PII decryption failed for a stored value (version {}) — wrong key, or corrupted data", version, e);
            throw new IllegalStateException("Failed to decrypt sensitive field", e);
        }
    }

    private static boolean isAllDigits(String s) {
        if (s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) if (!Character.isDigit(s.charAt(i))) return false;
        return true;
    }

    private static String encryptRaw(String plaintext, SecretKeySpec key) {
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            SecureRandom.getInstanceStrong().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            log.error("PII encryption failed — refusing to store plaintext for a field that should be encrypted", e);
            throw new IllegalStateException("Failed to encrypt sensitive field", e);
        }
    }

    private static String decryptRaw(String base64Payload, SecretKeySpec key) throws Exception {
        byte[] combined = Base64.getDecoder().decode(base64Payload);
        byte[] iv = new byte[IV_LENGTH_BYTES];
        byte[] ciphertext = new byte[combined.length - IV_LENGTH_BYTES];
        System.arraycopy(combined, 0, iv, 0, IV_LENGTH_BYTES);
        System.arraycopy(combined, IV_LENGTH_BYTES, ciphertext, 0, ciphertext.length);
        Cipher cipher = Cipher.getInstance(ALGO);
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
        return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
    }
}