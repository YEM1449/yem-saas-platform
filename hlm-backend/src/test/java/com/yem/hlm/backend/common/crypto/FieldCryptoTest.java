package com.yem.hlm.backend.common.crypto;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/** DA-010 — AES-256-GCM field encryption: round-trip, null/empty, legacy-plaintext tolerance. */
class FieldCryptoTest {

    @BeforeAll
    static void installKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        FieldCrypto.init(Base64.getEncoder().encodeToString(key)); // package-private — same package
    }

    @Test
    void encryptThenDecrypt_roundTrips() {
        String plain = "AB123456";
        String stored = FieldCrypto.encrypt(plain);

        assertThat(stored).startsWith("enc::");      // marker present
        assertThat(stored).doesNotContain(plain);    // ciphertext does not leak the CIN
        assertThat(FieldCrypto.decrypt(stored)).isEqualTo(plain);
    }

    @Test
    void freshIvPerEncryption_producesDifferentCiphertext() {
        assertThat(FieldCrypto.encrypt("AB123456"))
                .isNotEqualTo(FieldCrypto.encrypt("AB123456"));   // random IV → no deterministic leak
    }

    @Test
    void nullAndEmpty_storeAsNull() {
        // keeps the "IS NOT NULL AND <> ''" filter semantics used by findWithNationalId
        assertThat(FieldCrypto.encrypt(null)).isNull();
        assertThat(FieldCrypto.encrypt("")).isNull();
    }

    @Test
    void legacyPlaintext_isReturnedAsIs() {
        // rows written before DA-010 carry no "enc::" marker — must stay readable, no bulk rewrite
        assertThat(FieldCrypto.decrypt("AB123456")).isEqualTo("AB123456");
        assertThat(FieldCrypto.decrypt(null)).isNull();
    }
}
