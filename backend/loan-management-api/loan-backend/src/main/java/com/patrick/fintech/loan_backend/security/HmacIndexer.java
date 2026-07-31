package com.patrick.fintech.loan_backend.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Deterministic HMAC-SHA256 "blind index" for fields that are encrypted
 * (via CryptoConverter, so not directly searchable) but still need exact-
 * match lookups — e.g. finding a returning borrower by phone number.
 *
 * Unlike CryptoConverter's AES-GCM (randomized, different ciphertext every
 * time), the same input always produces the same HMAC output — that's what
 * makes it usable as a lookup key. It does NOT reveal the original value
 * (HMAC is one-way), only whether two values are equal, which is exactly
 * what an indexed lookup needs and no more.
 *
 * Store the plaintext-derived index alongside the encrypted column (e.g.
 * Borrower.phoneHash next to Borrower.phone) and query by the index, never
 * by the encrypted column directly.
 *
 * <h2>Key rotation</h2>
 * {@code index()} always uses the current key (APP_INDEX_KEY) — that's what
 * every row is written with. During a key rotation, set APP_INDEX_KEY_PREVIOUS
 * to the old key so lookups can still match rows the re-encryption migration
 * tool hasn't reached yet: use {@link #candidates} for any query that needs to
 * find an existing row (not for computing the value to store — always store
 * {@link #index}). Remove APP_INDEX_KEY_PREVIOUS once the migration tool
 * reports 100% complete, same as APP_ENCRYPTION_KEY_PREVIOUS.
 */
public final class HmacIndexer {

    private HmacIndexer() {}

    private static volatile SecretKeySpec KEY;
    private static volatile SecretKeySpec PREVIOUS_KEY;
    private static volatile boolean previousLoaded = false;

    /** The index value to store — always computed with the current key. */
    public static String index(String plaintext) {
        return indexWith(plaintext, key());
    }

    /** The index value under the previous key, or null if no rotation is in progress. */
    public static String indexPrevious(String plaintext) {
        SecretKeySpec prev = previousKey();
        return prev == null ? null : indexWith(plaintext, prev);
    }

    /**
     * All hash values worth querying by for this plaintext — current key, plus the
     * previous key's hash if a rotation is in progress. Use this for lookups (WHERE
     * hash IN (...)); use {@link #index} alone for the value to persist.
     */
    public static List<String> candidates(String plaintext) {
        List<String> out = new ArrayList<>(2);
        String current = index(plaintext);
        if (current != null) out.add(current);
        String previous = indexPrevious(plaintext);
        if (previous != null && !previous.equals(current)) out.add(previous);
        return out;
    }

    private static String indexWith(String plaintext, SecretKeySpec key) {
        if (plaintext == null || plaintext.isBlank()) return null;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            byte[] result = mac.doFinal(plaintext.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(result);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute blind index", e);
        }
    }

    private static SecretKeySpec key() {
        if (KEY != null) return KEY;
        synchronized (HmacIndexer.class) {
            if (KEY != null) return KEY;
            String configured = System.getenv("APP_INDEX_KEY");
            byte[] keyBytes;
            if (configured != null && !configured.isBlank()) {
                keyBytes = Base64.getDecoder().decode(configured);
            } else {
                byte[] seed = Base64.getDecoder().decode("ZGV2LW9ubHktaW5kZXgta2V5LWRvLW5vdC11c2U9");
                keyBytes = new byte[32];
                for (int i = 0; i < 32; i++) keyBytes[i] = seed[i % seed.length];
            }
            KEY = new SecretKeySpec(keyBytes, "HmacSHA256");
            return KEY;
        }
    }

    private static SecretKeySpec previousKey() {
        if (previousLoaded) return PREVIOUS_KEY;
        synchronized (HmacIndexer.class) {
            if (previousLoaded) return PREVIOUS_KEY;
            String configured = System.getenv("APP_INDEX_KEY_PREVIOUS");
            if (configured != null && !configured.isBlank()) {
                PREVIOUS_KEY = new SecretKeySpec(Base64.getDecoder().decode(configured), "HmacSHA256");
            }
            previousLoaded = true;
            return PREVIOUS_KEY;
        }
    }
}