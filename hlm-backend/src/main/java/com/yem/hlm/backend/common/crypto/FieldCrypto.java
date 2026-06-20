package com.yem.hlm.backend.common.crypto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption for sensitive column values at rest (DA-010 — CNDP / Loi 09-08).
 *
 * <p>Encrypts CNDP-sensitive identity fields (CIN, passport numbers) so a DB-level disclosure does
 * not leak directly reportable personal data. Used through {@link EncryptedStringConverter}.
 *
 * <h3>Format</h3>
 * Ciphertext is stored as {@code "enc::" + base64(iv ‖ ciphertext+tag)} with a fresh random 12-byte
 * IV per write (GCM, 128-bit tag). The {@code enc::} marker lets reads tell encrypted values from
 * <b>legacy plaintext</b> written before this change — those are returned verbatim, so the rollout
 * needs no bulk re-encryption (new writes are encrypted; old rows stay readable and get encrypted
 * the next time they are saved).
 *
 * <h3>Key</h3>
 * The 256-bit key is provided as base64 via {@code app.security.field-encryption-key} (env
 * {@code FIELD_ENCRYPTION_KEY}) and installed once at startup by {@link FieldCryptoInitializer}.
 * If <b>no key</b> is configured the helper passes values through in plaintext (with a one-time
 * startup warning) so local/dev/test still work — a real key MUST be set before go-live.
 */
public final class FieldCrypto {

    private static final Logger log = LoggerFactory.getLogger(FieldCrypto.class);
    private static final String  PREFIX   = "enc::";
    private static final String  ALGO     = "AES/GCM/NoPadding";
    private static final int     IV_LEN   = 12;
    private static final int     TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Installed once at startup; null = encryption disabled (pass-through). */
    private static volatile SecretKeySpec key;
    private static volatile boolean warned;

    private FieldCrypto() {}

    /** Installs the AES key from a base64 string (32 bytes → AES-256). Blank/invalid → disabled. */
    static void init(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            key = null;
            log.warn("[FIELD-CRYPTO] No app.security.field-encryption-key set — sensitive fields "
                    + "(CIN/passport) are stored in PLAINTEXT. Set FIELD_ENCRYPTION_KEY before go-live (DA-010).");
            return;
        }
        byte[] raw = Base64.getDecoder().decode(base64Key.trim());
        if (raw.length != 16 && raw.length != 24 && raw.length != 32) {
            throw new IllegalStateException(
                    "app.security.field-encryption-key must decode to 16/24/32 bytes (got " + raw.length + ")");
        }
        key = new SecretKeySpec(raw, "AES");
        log.info("[FIELD-CRYPTO] Field encryption enabled (AES-{}).", raw.length * 8);
    }

    /** Encrypts a value for storage. Null/empty → null (keeps {@code IS NOT NULL}/{@code <> ''} semantics). */
    public static String encrypt(String plain) {
        if (plain == null || plain.isEmpty()) return null;
        if (key == null) {
            warnPlaintextOnce();
            return plain;
        }
        try {
            byte[] iv = new byte[IV_LEN];
            RANDOM.nextBytes(iv);
            Cipher c = Cipher.getInstance(ALGO);
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("Field encryption failed", e);
        }
    }

    /** Decrypts a stored value. Non-prefixed input is treated as legacy plaintext and returned as-is. */
    public static String decrypt(String stored) {
        if (stored == null) return null;
        if (!stored.startsWith(PREFIX)) return stored;     // legacy plaintext, pre-DA-010
        if (key == null) {
            throw new IllegalStateException("Encrypted field present but no field-encryption key is configured");
        }
        try {
            byte[] raw = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            byte[] iv = new byte[IV_LEN];
            System.arraycopy(raw, 0, iv, 0, IV_LEN);
            Cipher c = Cipher.getInstance(ALGO);
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] pt = c.doFinal(raw, IV_LEN, raw.length - IV_LEN);
            return new String(pt, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Field decryption failed", e);
        }
    }

    private static void warnPlaintextOnce() {
        if (!warned) {
            warned = true;
            log.warn("[FIELD-CRYPTO] Writing a sensitive field in PLAINTEXT (no key configured).");
        }
    }
}
